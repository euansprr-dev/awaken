"""The chain as the game plays it (SPEC §25 "Swing Studio", round 3): each strike of a style's chain started on the
press the runtime would take, carried from the pose on screen by SwingBlend, at 60 fps. The studio's report judges a
move alone; this judges what a player sees mashing or keeping time (Euan 2026-10-28: "when you're doing combos, a lot of
the time the weapon, the second slice, is not even visible... it looks like a mess of movement and trails").

    python3 tools/art/swing_chain.py report [style ...] [--rhythm mash|steady] [--json OUT]
    python3 tools/art/swing_chain.py strip OUT.png STYLE [--rhythm mash|steady]
    python3 tools/art/swing_chain.py twist [style ...]      every move's turn of the blade about its own length
    python3 tools/art/swing_chain.py like [style ...]       every set move beside the rapier's stab or the longsword's cut
    python3 tools/art/swing_chain.py calm [style ...]       every set move of a calm style held to round 14's rules
    (any with --swings PATH: a trial bake in place of the jar's file)

Per chained strike (the second on): how much of its wind-up and blow the blade is on screen, where its set sits, the
biggest jump of the tip between two frames (the trail's "teleport"), and how often the tip turns back on itself before
the hit (the "tiny movements").
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import anim_lab as L  # noqa: E402
import swing_studio as S  # noqa: E402

FPS = 60
DT = 20 / FPS  # ticks a frame
MASTERY_LINK = 2  # ArmsRules.MASTERY_LINK
REST_BLEND = 3.0
EPS = 1e-4


# ---- SwingBlend, as the runtime has it ---------------------------------------------------------------------------------

class Curve:
    def __init__(self):
        self.on = False

    def set(self, x0, v0, max_ticks):
        if x0 < EPS or max_ticks <= 0:
            self.on = False
            return
        v = min(0.0, v0)
        t = max_ticks
        if v < 0:
            t = min(t, -5 * x0 / v)
        t2 = t * t
        self.a0 = max(0.0, (-8 * v * t - 20 * x0) / t2)
        self.a = -(self.a0 * t2 + 6 * v * t + 12 * x0) / (2 * t2 * t2 * t)
        self.b = (3 * self.a0 * t2 + 16 * v * t + 30 * x0) / (2 * t2 * t2)
        self.c = -(3 * self.a0 * t2 + 12 * v * t + 20 * x0) / (2 * t2 * t)
        self.v0, self.x0, self.length, self.on = v, x0, t, True

    def at(self, t):
        if not self.on or t >= self.length:
            return 0.0
        t = max(0.0, t)
        return (((self.a * t + self.b) * t + self.c) * t + self.a0 * 0.5) * t * t + self.v0 * t + self.x0


def rot_vec(R):
    """Axis * angle of a rotation matrix."""
    ang = math.acos(max(-1.0, min(1.0, (np.trace(R) - 1) / 2)))
    if ang < 1e-7:
        return np.zeros(3)
    ax = np.array([R[2, 1] - R[1, 2], R[0, 2] - R[2, 0], R[1, 0] - R[0, 1]]) / (2 * math.sin(ang))
    return ax / np.linalg.norm(ax) * ang


class Blend:
    def __init__(self):
        self.pos, self.rot = Curve(), Curve()
        self.last = None  # (t, d, R)
        self.vel = (np.zeros(3), np.zeros(3))

    def begin(self, now, d, R, vd, w, ticks):
        self.start = now
        if self.last is None:
            self.pos.on = self.rot.on = False
            return
        _, ld, lR = self.last
        dv = ld - d
        x = float(np.linalg.norm(dv))
        if x > EPS:
            self.dir = dv / x
            self.pos.set(x, float((self.vel[0] - vd) @ self.dir), ticks)
        else:
            self.pos.on = False
        rv = rot_vec(lR @ R.T)
        ang = float(np.linalg.norm(rv))
        if ang > EPS:
            self.axis = rv / ang
            self.rot.set(ang, float((self.vel[1] - w) @ self.axis), ticks)
        else:
            self.rot.on = False

    def apply(self, now, d, R):
        t = now - self.start
        p = self.pos.at(t)
        if p:
            d = d + p * self.dir
        r = self.rot.at(t)
        if r:
            R = L.axis_rot(self.axis, math.degrees(r)) @ R
        if self.last is not None and 1e-3 < now - self.last[0] <= 2:
            dt = now - self.last[0]
            self.vel = ((d - self.last[1]) / dt, rot_vec(R @ self.last[2].T) / dt)
        elif self.last is None or now - self.last[0] > 2:
            self.vel = (np.zeros(3), np.zeros(3))
        self.last = (now, d, R)
        return d, R


# ---- the chain ---------------------------------------------------------------------------------------------------------

def schedule(st, rhythm):
    """[(move, start tick)] for one pass of the chain at the rhythm: mash takes the earliest press the runtime chains
    (a buffered press waits for the window), steady presses mid-window."""
    chain = st.get("chain", []) + st.get("masteryChain", [])
    out, t = [], 0.0
    for i, mv in enumerate(chain):
        out.append((mv, t))
        m = st["moves"][mv]
        if i + 1 >= len(chain):
            break
        cad = m.get("cadence")
        if cad:
            # a chain's first strike starts on its press, a tick into the chain's count: a buffered press takes over a
            # tick of its clip early (swing_studio.chain_links)
            t += cad[0] - (1 if i == 0 else 0) if rhythm == "mash" else (cad[0] + cad[1]) / 2
        else:
            t += m["windup"] + m["active"] + MASTERY_LINK
    return out


def sample(sid, mv, t, swings):
    p = L.new_pose(sid, mv, t, swings)
    return (np.zeros(3), np.eye(3)) if p is None else p


def hand_of(swings, sid, mv):
    return swings[sid]["moves"].get(mv, {}).get("hand", "main")


def idle_of(sid, mv, t, swings):
    """The hand not striking (the clip's idle track, round 4), or None: it rests."""
    keys = swings[sid]["moves"].get(mv, {}).get("idle")
    if not keys:
        return None
    clip = {"keys": [k + [0.0, 0.0, 0.0] for k in keys]}
    return L.new_pose(sid, "_idle", t, {sid: {"moves": {"_idle": clip}}})


def play(sid, swings=None, rhythm="mash", wid=None):
    """Frames [(t, move index, the striking blade's tip ndc, mid ndc, mid z, local t)] through one pass of the chain."""
    swings = swings or L.load_swings()
    st = L.table()[sid]
    held = L.Held(wid or "gatewrought_" + sid)
    H0 = np.array([L.HX, L.HY, L.HZ])
    sch = schedule(st, rhythm)
    ends = [s for _, s in sch[1:]] + [sch[-1][1] + sum(st["moves"][sch[-1][0]][k] for k in ("windup", "active", "recovery"))]
    blends = {"main": Blend(), "off": Blend()}
    frames = []
    cur = -1
    t = 0.0
    while t <= ends[-1] + 1e-6:
        i = max(j for j, (_, s) in enumerate(sch) if s <= t + 1e-9)
        mv, s0 = sch[i]
        lt = t - s0
        hand = hand_of(swings, sid, mv)
        d, R = sample(sid, mv, lt, swings)
        # the hand not striking; in a both-hands move, the off hand's own way into it when it has one (round 9)
        idle = idle_of(sid, mv, lt, swings)
        other = "off" if hand in ("main", "both") else "main"
        if i != cur:
            cur = i
            m = st["moves"][mv]
            ticks = max(1.0, min(2.5, m["windup"] * 0.8))
            d2, R2 = sample(sid, mv, lt + 0.1, swings)
            vd, w = (d2 - d) / 0.1, rot_vec(R2 @ R.T) / 0.1
            for h, b in blends.items():
                moves = (hand == "both" and (h == "main" or idle is None)) or h == hand
                if moves:
                    b.begin(t, d, R, vd, w, ticks)
                elif idle is not None and h == other:
                    b.begin(t, *idle, np.zeros(3), np.zeros(3), ticks)
                else:
                    b.begin(t, np.zeros(3), np.eye(3), np.zeros(3), np.zeros(3), ticks)
        shown = {}
        for h, b in blends.items():
            moves = (hand == "both" and (h == "main" or idle is None)) or h == hand
            src = (d, R) if moves else idle if idle is not None and h == other else (np.zeros(3), np.eye(3))
            shown[h] = b.apply(t, *src)
        sd, sR = shown["off" if hand == "off" else "main"]
        tip, grip = S.lead_points(held, swings[sid]["moves"][mv].get("far"), sd, sR)
        mid = grip + (tip - grip) * L.MID
        mir = -1 if hand == "off" else 1
        tn, mn = S.ndc(tip), S.ndc(mid)
        tn[0] *= mir
        mn[0] *= mir
        frames.append((round(t, 4), i, tn, mn, float(mid[2]), lt, shown))
        t += DT
    return sch, frames, held


def onscreen(n, z, margin=0.95):
    return z < 0 and abs(n[0]) < margin and abs(n[1]) < margin


def judge(sid, swings=None, rhythm="mash"):
    swings = swings or L.load_swings()
    st = L.table()[sid]
    sch, frames, _ = play(sid, swings, rhythm)
    out = []
    for i, (mv, s0) in enumerate(sch):
        m = st["moves"][mv]
        hit = L.hit_tick(m)
        span = [f for f in frames if f[1] == i and f[5] <= hit + 0.34]
        if not span:
            continue
        vis = sum(1 for f in span if onscreen(f[3], f[4])) / len(span)
        # the drive: the last third of the way to the hit
        drv = [f for f in span if f[5] >= hit * 0.5]
        dvis = sum(1 for f in drv if onscreen(f[3], f[4])) / max(1, len(drv))
        allf = [f for f in frames if f[1] == i]
        # a jump: the tip's move between two frames away from the blow itself (the drive and the cut through are fast
        # by design; anywhere else a big step reads as a teleport, and the ribbon draws it)
        # a double weapon carried on round end for end off the blow (round 5's figure-eight) is the blow going on: its
        # far end comes over from out of the view by design
        nx = S.chain_next(st, mv)
        spin_to = (m.get("cadence") or [0])[0] if nx and S.far_led(st, nx) != S.far_led(st, mv) else 0
        jumps = [float(np.linalg.norm(allf[k][2] - allf[k - 1][2])) for k in range(1, len(allf))
                 if not (hit * 0.5 <= allf[k][5] <= max(hit + 0.67, spin_to))
                 and (onscreen(allf[k][2], allf[k][4], 1.2) or onscreen(allf[k - 1][2], allf[k - 1][4], 1.2))]
        # turns back on itself before the hit beyond the one draw back: the tip's screen velocity reversing by more
        # than 120 degrees (the "tiny movements")
        vs = [span[k][2] - span[k - 1][2] for k in range(1, len(span))]
        rev = 0
        for k in range(1, len(vs)):
            a, b = vs[k - 1], vs[k]
            na, nb = np.linalg.norm(a), np.linalg.norm(b)
            if na > 0.01 and nb > 0.01 and a @ b / (na * nb) < -0.5:
                rev += 1
        flips = max(0, rev - 1)
        lowest = min(float(f[3][1]) for f in span)
        out.append(dict(style=sid, move=mv, start=s0, hit=hit, visible=round(vis, 2), drive_visible=round(dvis, 2),
                        jump=round(max(jumps, default=0.0), 2), flips=flips, lowest=round(lowest, 2)))
    return out


GATES = dict(visible=0.9, drive_visible=0.9, jump=0.3, flips=0)


def report(styles=None, rhythm="mash", js=None, swings=None):
    swings = swings or L.load_swings()
    T = L.table()
    allr = []
    for sid in styles or list(T):
        if sid not in swings:
            continue
        for r in judge(sid, swings, rhythm):
            bad = [k for k, g in GATES.items() if (r[k] > g if k in ("jump", "flips") else r[k] < g)]
            if r["start"] == 0:
                bad = [k for k in bad if k in ("jump", "flips")]  # the first strike starts from rest by itself
            r["out"] = bad
            allr.append(r)
            print(f"{sid:11s} {r['move']:9s} @{r['start']:5.1f}  visible {r['visible']:.2f}  drive {r['drive_visible']:.2f}  "
                  f"jump {r['jump']:.2f}  flips {r['flips']}  lowest {r['lowest']:+.2f}" + ("   OUT: " + ", ".join(bad) if bad else ""))
    print(f"\n{len(allr)} strikes ({rhythm}); out: " + ", ".join(f"{k} {sum(1 for r in allr if k in r['out'])}" for k in GATES))
    if js:
        json.dump(allr, open(js, "w"), indent=1)
    return allr


def strip(out, sid, rhythm="mash", swings=None):
    """The chain at 60 fps, one cell a frame, each strike's hit ringed red, the striking blade's tip traced."""
    from PIL import Image, ImageChops, ImageDraw, ImageOps
    swings = swings or L.load_swings()
    st = L.table()[sid]
    sch, frames, held = play(sid, swings, rhythm)
    cells = []
    trail = []
    for f in frames:
        t, i, tn, mn, z, lt, shown = f
        mv = sch[i][0]
        hand = hand_of(swings, sid, mv)
        d, R = shown["main"]
        im = L.frame(held, d, R)
        if swings[sid].get("twin"):  # the off hand's blade too: drawn as a right hand, the frame mirrored
            im = ImageChops.lighter(im, ImageOps.mirror(L.frame(held, *shown["off"])))
        im = im.resize((192, 108), Image.LANCZOS)
        dr = ImageDraw.Draw(im)
        trail = (trail + [tn])[-6:]
        pts = [((p[0] + 1) * 96, (1 - p[1]) * 54) for p in trail]
        if len(pts) > 1:
            dr.line(pts, fill=(120, 200, 255), width=2)
        on = abs(lt - L.hit_tick(st["moves"][mv])) < 1 / 6
        dr.text((4, 2), f"{t * 50:.0f}ms {mv[-1]}" + (" HIT" if on else ""), fill=(255, 110, 110) if on else (200, 205, 215))
        if on:
            dr.rectangle([0, 0, 191, 107], outline=(255, 90, 90), width=2)
        cells.append(im)
    per = 12
    rows_ = (len(cells) + per - 1) // per
    sheet = Image.new("RGB", (192 * per, rows_ * 108 + 18), (14, 15, 20))
    ImageDraw.Draw(sheet).text((6, 3), f"{sid} chain, {rhythm}, 60 fps", fill=(230, 232, 240))
    for k, c in enumerate(cells):
        sheet.paste(c, ((k % per) * 192, 18 + (k // per) * 108))
    sheet.save(out)
    return out


def twist(styles=None, swings=None):
    """Per move, degrees the blade turns about its own length from start to end (frame to frame at 60 fps, the swing
    of its direction taken out). Round 12 (Euan 2026-10-29: "it feels more like they are spinning and making circles
    than stabbing and slashing"): no judge measured it, and every round's rules turned the daggers' blades 80-250
    degrees a move. A stab or chop needs none, a cut only its edge turned toward the cut and back."""
    swings = swings or L.load_swings()
    out = {}
    for sid, st in L.table().items():
        if styles and sid not in styles:
            continue
        held = L.Held(L.REVIEW.get(sid, "gatewrought_" + sid))
        row = {}
        for mv, m in st["moves"].items():
            if mv not in swings.get(sid, {}).get("moves", {}):
                continue
            ts = np.arange(0, m["windup"] + m["active"] + m["recovery"] + 1e-6, DT)
            P = [L.new_pose(sid, mv, float(t), swings)[1] for t in ts]
            tw = 0.0
            for R0, R1 in zip(P, P[1:]):
                a0, a1 = R0 @ held.axis, R1 @ held.axis
                e, e1 = L.turn_to(a0, a1) @ (R0 @ held.edge), R1 @ held.edge
                tw += abs(math.degrees(math.atan2(float(np.cross(e, e1) @ a1), float(e @ e1))))
            row[mv] = round(tw)
        out[sid] = row
        print(f"{sid:11s} " + "  ".join(f"{mv} {v}" for mv, v in row.items()))
    return out


# Round 13 (Euan 2026-10-29: "it's less about turning the wrist, and more about the weapon not doing clean stabs and
# swipes like other weapons do"): each form held to the move that reads clean in game. A stab is the rapier's opening:
# drawn back toward the eye (the blade grows), out along its own line with its point held on its mark (it shrinks), and
# back in at once. A slash is the longsword's opening, a chop its overhead: set high in view, then one sweep across the
# screen through the target, fastest at the hit.
LIKE = {"stab": ("rapier", "strike_1"), "slash": ("longsword", "strike_1"), "rise": ("longsword", "strike_2"),
        "chop": ("longsword", "strike_3")}
# a stab's bars: drawn back at least this big (of the stance's size on the screen), out at most this big, back in (as
# big as the stance) within this many ticks of the hit, its point off the blade's own line on the screen through the
# drive (sliding along it is the stab) and off the crosshair at the hit at most this far
LIKE_STAB = dict(pull=1.3, out=0.8, back=1.0, aim=0.08, mark=0.15)
# a cut's: its sweep across the screen and its fastest frame within these shares of the reference's, fastest within
# this of the hit, set no lower (a rising cut no higher) than the reference's by more than this, its middle this near
# the crosshair at the hit
LIKE_CUT = dict(span=(0.75, 1.35), peak=(0.7, 1.35), when=0.34, set=0.25, mark=0.25)


def shape(sid, mv, swings):
    """A move's look, as the forms above measure it (ndc and ticks, the striking hand as baked)."""
    held = L.Held(L.REVIEW.get(sid, "gatewrought_" + sid))
    m = L.table()[sid]["moves"][mv]
    form = L.form_of(m)[0]
    hit = L.hit_tick(m)
    H0 = np.array([L.HX, L.HY, L.HZ])
    ts = np.arange(0, m["windup"] + m["active"] + m["recovery"] + 1e-6, DT)

    def seen(d, R):
        g, tip = H0 + d + R @ held.grip, H0 + d + R @ held.tip
        return S.ndc(g), S.ndc(tip), S.ndc(g + (tip - g) * L.MID)
    fr = [seen(*L.new_pose(sid, mv, float(t), swings)) for t in ts]
    rest = seen(np.zeros(3), np.eye(3))
    size = np.array([np.linalg.norm(f[1] - f[0]) for f in fr]) / np.linalg.norm(rest[1] - rest[0])
    k = int(round(hit / DT))
    mark = S.ndc(L.TARGET)
    if form == "stab":
        j0 = int(np.argmax(size[:k])) if k else 0
        back = next(((j - k) * DT for j in range(k + 1, len(fr)) if size[j] >= 0.95), math.inf)
        way = (fr[k][1] - fr[k][0]) / np.linalg.norm(fr[k][1] - fr[k][0])
        side = lambda p: abs(float((p - fr[k][1]) @ np.array([-way[1], way[0]])))
        return form, dict(pull=float(size[:k].max()) if k else 1.0, out=float(size[k]), back=back,
                          aim=max(side(fr[j][1]) for j in range(j0, k + 1)), mark=float(np.linalg.norm(fr[k][1] - mark)))
    ax = 1 if form == "chop" else 0
    lo, hi = max(0, k - int(round(1 / DT))), min(len(fr) - 1, k + int(round(1 / DT)))
    tips = [fr[j][1] for j in range(lo, hi + 1)]
    steps = [float(np.linalg.norm(fr[j + 1][1] - fr[j][1])) for j in range(lo, hi)]
    top = int(np.argmax(steps))
    if form == "slash" and fr[hi][1][1] > fr[lo][1][1] + 0.3:
        form = "rise"
    # the set: the wind-up's highest point (a rising cut's lowest) in the tick before the hit
    ys = [float(fr[j][1][1]) for j in range(lo, k)] or [float(fr[k][1][1])]
    return form, dict(span=max(t_[ax] for t_ in tips) - min(t_[ax] for t_ in tips), peak=steps[top],
                      when=(lo + top + 0.5) * DT - hit, set=min(ys) if form == "rise" else max(ys),
                      mark=float(np.linalg.norm(fr[k][2] - mark)))


def like(styles=None, swings=None):
    """Every set move (swing_choreo) beside the move that reads clean for its form; returns the misses."""
    import swing_choreo as SC
    swings = swings or L.load_swings()
    bad, refs = [], {}
    for form, (rs, rm) in LIKE.items():
        refs[form] = shape(rs, rm, swings)[1]
        print(f"{form:5s} like {rs} {rm}: " + "  ".join(f"{k} {v:.2f}" for k, v in refs[form].items()))
    for sid, c in SC.CHORE.items():
        if styles and sid not in styles or sid in CALM:
            continue
        for mv in c["moves"]:
            form, got = shape(sid, mv, swings)
            ref = refs.get(form)
            if ref is None:
                continue
            if form == "stab":
                miss = [k for k, v in got.items() if (v < LIKE_STAB[k] if k == "pull" else v > LIKE_STAB[k])]
            else:
                miss = [k for k in ("span", "peak") if not LIKE_CUT[k][0] * ref[k] <= got[k] <= LIKE_CUT[k][1] * ref[k]]
                miss += [k for k in ("when", "mark") if abs(got[k]) > LIKE_CUT[k]]
                low = got["set"] > ref["set"] + LIKE_CUT["set"] if form == "rise" else got["set"] < ref["set"] - LIKE_CUT["set"]
                miss += ["set"] if low else []
            print(f"  {sid} {mv:10s} {form:5s} " + "  ".join(f"{k} {v:.2f}{' !' if k in miss else ''}" for k, v in got.items()))
            if miss:
                bad.append((sid, mv, miss))
    return bad


# Round 14 (Euan 2026-10-29, after seeing round 13 in game: "when you slash, your left hand goes up. It's like there's
# too much going on at once... it's as if you're slashing and it arrives, like, under your left arm"; the stabs pulled
# the arm in at the camera; one ribbon for both daggers drew bands between them and loops on the way home). A calm
# style's set moves (in place of `like` for it): the hand not striking holds still (its point moves no more than
# `still` on the screen, a both-hands move aside); the striking grip never nearer the eye than the stance's by more than
# `near` (an arm drawn in at the camera); a stab never grows past `pull` of the stance's size before the hit (no
# pull-back) and lands within `mark` of the crosshair; a slash ends its stroke (the ribbon's last tick) no further past
# the middle than `past`, at least `gap` from the other blade's point; the ribbon covers the hit and stops within
# `after` ticks of it (never the way home). Round 15 (Euan, after the round 14 film: "keep the fist lower in the jab, so
# the forearm doesn't swing into the corner"): a stab's fist rises no more than `rise` of the screen over its stance's,
# the forearm drawn up to it covers no more than `arm` of the screen (`forearm`), the blade drives away (its size on
# the screen at the hit at most `go` of the stance's) and its point still lands on the target, within `point` of the
# crosshair. The rise and the forearm are shown for every move.
CALM = {"daggers": dict(still=0.03, near=0.04, pull=1.05, rise=0.22, arm=0.11, go=0.85, point=0.5, past=0.25, gap=0.2,
                        after=0.45)}

# Hands' first-person forearm (anims WorldBody.drawFist): drawn REACH up to the fist from under the corner at ANCHOR
# (view space, a right arm), THICK across, at its true size: a fist risen into the view brings the forearm with it
FOREARM_ANCHOR, FOREARM_REACH, FOREARM_THICK = np.array([0.48, -0.84, -0.53]), 0.6, 0.25


def forearm(fist):
    """How much of the screen (ndc area, the screen is 4) the forearm up to a right fist at {@code fist} covers."""
    d = fist - FOREARM_ANCHOR
    d = d / np.linalg.norm(d)
    root, area, prev = fist - d * FOREARM_REACH, 0.0, None
    for u in np.linspace(0, 1, 41):
        q = root + (fist - root) * u
        if -q[2] < 0.05:
            prev = None
            continue
        n = S.ndc(q)
        if prev is not None and abs(n[0]) < 1.3 and abs(n[1]) < 1.3:
            inside = abs(n[0]) <= 1 and abs(n[1]) <= 1
            area += float(np.linalg.norm(n - prev)) * FOREARM_THICK / -q[2] * S.F_ * (1.0 if inside else 0.5)
        prev = n
    return area


def calm(styles=None, swings=None):
    """Every set move of a calm style held to CALM's rules; returns the misses."""
    import swing_choreo as SC
    swings = swings or L.load_swings()
    bad = []
    for sid, bars in CALM.items():
        if styles and sid not in styles:
            continue
        held = L.Held(L.REVIEW.get(sid, "gatewrought_" + sid))
        H0 = np.array([L.HX, L.HY, L.HZ])
        flip = np.array([-1.0, 1.0])

        def seen(d, R):
            g, tip = H0 + d + R @ held.grip, H0 + d + R @ held.tip
            return S.ndc(g), S.ndc(tip), float(-g[2]), forearm(g)
        rest = seen(np.zeros(3), np.eye(3))
        print(f"{sid}: stance grip depth {rest[2]:.2f}, point {rest[1].round(2)}")
        for mv in SC.CHORE[sid]["moves"]:
            m, clip = L.table()[sid]["moves"][mv], swings[sid]["moves"][mv]
            form, hit = L.form_of(m)[0], L.hit_tick(m)
            ts = np.arange(0, m["windup"] + m["active"] + m["recovery"] + 1e-6, DT)
            # a right hand's tracks: the striking blade as authored, the other mirrored (an off-hand move mirrors both)
            fr = [seen(*L.new_pose(sid, mv, float(t), swings)) for t in ts]
            oth = [seen(*(idle_of(sid, mv, float(t), swings) or (np.zeros(3), np.eye(3)))) for t in ts]
            both = clip.get("hand") == "both"
            got, miss = {}, []
            got["still"] = 0.0 if both else max(float(np.linalg.norm(o[1] - rest[1])) for o in oth)
            got["near"] = rest[2] - min(f[2] for f in fr)
            tr = clip.get("trail")
            got["after"] = (tr[1] - hit) if tr and tr[0] <= hit else math.inf
            k = int(round(hit / DT))
            hands = fr + (oth if both else [])
            got["rise"] = max(f[0][1] for f in hands) - rest[0][1]
            got["arm"] = max(f[3] for f in hands)
            if form == "stab":
                size = [float(np.linalg.norm(f[1] - f[0])) / float(np.linalg.norm(rest[1] - rest[0])) for f in fr]
                got["pull"] = max(size[:k]) if k else 1.0
                got["go"] = size[k]
                got["point"] = float(np.linalg.norm(fr[k][1]))
            else:
                e = min(len(fr) - 1, int(round((tr[1] if tr else hit) / DT)))
                got["past"] = -float(fr[e][1][0])
                got["gap"] = math.inf if both else float(np.linalg.norm(fr[e][1] - oth[e][1] * flip))
            for key, v in got.items():
                if form != "stab" and key in ("rise", "arm"):
                    continue  # shown, held for the jabs (round 15)
                if (v < bars[key]) if key == "gap" else (v > bars[key]):
                    miss.append(key)
            print(f"  {mv:10s} {form:5s} " + "  ".join(f"{key} {v:.2f}{' !' if key in miss else ''}" for key, v in got.items()))
            if miss:
                bad.append((sid, mv, miss))
    print(f"{len(bad)} set moves out of the calm rules (rounds 14-15)")
    return bad


def main(argv):
    if not argv:
        raise SystemExit(__doc__)
    rhythm = "mash"
    if "--rhythm" in argv:
        i = argv.index("--rhythm")
        rhythm = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    js = None
    if "--json" in argv:
        i = argv.index("--json")
        js = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    swings = None
    if "--swings" in argv:  # a trial bake (anim_lab.py bake --out PATH) in place of the jar's file
        i = argv.index("--swings")
        swings = json.load(open(argv[i + 1]))["styles"]
        argv = argv[:i] + argv[i + 2:]
    if argv[0] == "report":
        report(argv[1:] or None, rhythm, js, swings)
        return 0
    if argv[0] == "strip":
        print(strip(argv[1], argv[2], rhythm, swings))
        return 0
    if argv[0] == "twist":
        twist(argv[1:] or None, swings)
        return 0
    if argv[0] == "like":
        return 1 if like(argv[1:] or None, swings) else 0
    if argv[0] == "calm":
        return 1 if calm(argv[1:] or None, swings) else 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
