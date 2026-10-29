"""The chain as the game plays it (SPEC §25 "Swing Studio", round 3): each strike of a style's chain started on the
press the runtime would take, carried from the pose on screen by SwingBlend, at 60 fps. The studio's report judges a
move alone; this judges what a player sees mashing or keeping time (Euan 2026-10-28: "when you're doing combos, a lot of
the time the weapon, the second slice, is not even visible... it looks like a mess of movement and trails").

    python3 tools/art/swing_chain.py report [style ...] [--rhythm mash|steady] [--json OUT]
    python3 tools/art/swing_chain.py strip OUT.png STYLE [--rhythm mash|steady]
    (either with --swings PATH: a trial bake in place of the jar's file)

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
            t += cad[0] if rhythm == "mash" else (cad[0] + cad[1]) / 2
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
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
