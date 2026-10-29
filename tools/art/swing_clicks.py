"""Swing Studio's fast-click judge (round 10): the hands as the client shows them under real clicking, not the chain
judge's ideal presses (swing_chain: each strike on its earliest press or mid-window, its clip on time).

Euan 2026-10-29: "when I do the whole combo smoothly... it actually looks great. But it's when you click fast, it starts
losing its form". Clicking fast, a click is still down when its strike begins (it waits at its set, then rushes), a
landed blow holds the hands (hit-stop) and bites; each leaves the hands' clock behind the chain's, so the next strike
began while the last one's clip was short of where that strike starts. This plays ArmsPredict's clock (press, buffer,
wait and rush, the server's chain shift, hit-stop, bite) and ArmsHands' sources and SwingBlend, frame by frame at
60 fps, and measures each blade's step a frame off its blow and how near the two blades come.

Round 10 found with it: a buffered press takes over a tick of the first strike's clip early (swing_studio.chain_links),
and ArmsFeel.paused ran the hands' clock on by a whole hit-stop between its end and the next tick (BUGGY_HOLD plays that).

  python3 tools/art/swing_clicks.py report STYLE [--swings PATH] [--every T] [--down T] [--hits] [--clicks N] [--mastery]
  python3 tools/art/swing_clicks.py sheet OUT STYLE T0 T1 [same options] [--cell PX] [--cols N]
  python3 tools/art/swing_clicks.py film OUT.mp4 STYLE LABEL=SWINGS [LABEL=SWINGS ...]   slow, then fast: a video
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_lab as L  # noqa: E402
import swing_chain as C  # noqa: E402
import swing_studio as S  # noqa: E402

FPS = 60
DT = 20 / FPS
RUSH = 1.6  # ArmsPredict.RUSH
BUGGY_HOLD = False  # ArmsFeel before round 10 (see paused)
LATENCY = 0.5  # ticks from the server's hit to its packet in hand


class Clicks:
    """The client's strike clock (ArmsPredict) for one style, its hands (ArmsHands.lab) and their blend."""

    def __init__(self, style, swings, mastery=False):
        self.row = L.table()[style]
        self.sid = style
        self.sw = swings
        self.chain = self.row["chain"] + (self.row.get("masteryChain", []) if mastery else [])
        self.moves = self.row["moves"]
        self.heavy = self.moves.get("heavy", {}).get("charge")
        self.ticks = 0
        self.move = None
        self.move_start = 0
        self.step = -1
        self.buffered = None  # (move, step, start at, seq)
        self.seq = 0
        self.hold_open = self.press_held = self.wait_hit = False
        self.move_wall = 0.0
        self.move_paused = 0.0
        self.wait_total = 0.0
        self.rush_at, self.rush_to = -1.0, 0.0
        self.bite_at, self.bite_rate, self.bite_ticks = -1.0, 1.0, 0.0
        self.paused_total, self.hold_start, self.hold_until = 0.0, 0.0, 0.0
        self.shown_move, self.shown_wall = "_", None
        self.blends = {"main": C.Blend(), "off": C.Blend()}
        self.hits = []  # (wall the packet lands, move)
        self.log = []  # (wall, what)

    # ---- the clocks --------------------------------------------------------------------------------------------------

    def paused(self, now):
        """ArmsFeel.paused: hit-stop held so far, an ended hold counted before the tick settles it (round 10: it was
        not, and the hands' clock jumped on by the whole hold until the next tick, then back)."""
        if not self.hold_until:
            return self.paused_total
        if BUGGY_HOLD and now >= self.hold_until:
            return self.paused_total
        return self.paused_total + min(now, self.hold_until) - self.hold_start

    def hold(self, now, ms):
        t = ms / 50.0
        if now < self.hold_until:
            self.hold_until = max(self.hold_until, now + t)
            return
        if self.hold_until and not BUGGY_HOLD:  # an ended hold the tick has not counted yet
            self.paused_total += self.hold_until - self.hold_start
        self.hold_start, self.hold_until = now, now + t

    def settle_holds(self, now):
        if self.hold_until and now >= self.hold_until:
            self.paused_total += self.hold_until - self.hold_start
            self.hold_until = 0.0

    def raw(self, now):
        return now - self.move_wall - (self.paused(now) - self.move_paused) - self.wait_total

    def wait_at(self, mv):
        clip = self.sw[self.sid]["moves"].get(mv, {})
        s = clip.get("set", -1)
        hit = self.moves[mv]["windup"] - 0.05
        return hit if s < 0 else min(hit, s)

    def move_time(self, now):
        t = self.raw(now)
        if self.wait_hit and self.move:
            return min(t, self.wait_at(self.move))
        if self.rush_at < 0 or t <= self.rush_at:
            return t
        span, e = self.rush_to - self.rush_at, t - self.rush_at
        return self.rush_at + e * RUSH if e < span / RUSH else t + span * (1 - 1 / RUSH)

    def bitten(self, now):
        t = self.move_time(now)
        if self.bite_at < 0 or t <= self.bite_at or not self.move:
            return t
        e, lag = t - self.bite_at, (1 - self.bite_rate) * self.bite_ticks
        if e < self.bite_ticks:
            return t - (1 - self.bite_rate) * e
        m = self.moves[self.move]
        rest = max(1.0, m["windup"] + m["active"] + m["recovery"] - self.bite_at - self.bite_ticks)
        return t - lag * max(0.0, 1 - (e - self.bite_ticks) / rest)

    def chain_time(self, now):
        """The chain's clock for the running strike: ticks since its start as the client counts them for the next press
        (ArmsPredict moveStart, shifted on for each tick a strike waits past its wind-up), with the frame's fraction."""
        return (self.ticks - self.move_start) + (now - self.ticks)

    def view_time(self, now):
        """ArmsPredict.viewTime: the move's clock with the bite."""
        return self.bitten(now) if self.move else 0.0

    # ---- presses -----------------------------------------------------------------------------------------------------

    def tap_rule(self, now_tick):
        cur = self.moves.get(self.move) if self.move else None
        late = self.row.get("late", 6)
        buf = self.row.get("buffer", 4)
        if cur is None or now_tick >= self.move_start + cur["windup"] + cur["active"] + cur["recovery"] + late:
            return (self.chain[0], 0, now_tick)
        rel = now_tick - self.move_start
        nxt = self.step + 1 if self.step >= 0 and self.step + 1 < len(self.chain) else 0
        if self.step >= 0 and nxt != 0 and cur.get("cadence"):
            c0, c1 = cur["cadence"]
            if rel < c0:
                return (self.chain[nxt], nxt, self.move_start + c0) if c0 - rel <= buf else None
            return (self.chain[nxt], nxt, now_tick)
        if self.step >= 0 and nxt != 0:  # a finisher mastery carries on: from the end of its blow and the link
            link = self.move_start + cur["windup"] + cur["active"] + C.MASTERY_LINK
            if now_tick >= link:
                return (self.chain[nxt], nxt, now_tick)
            return (self.chain[nxt], nxt, link) if link - now_tick <= buf else None
        end = self.move_start + cur["windup"] + cur["active"] + cur["recovery"]
        if now_tick >= end:
            return (self.chain[0], 0, now_tick)
        return (self.chain[0], 0, end) if end - now_tick <= buf else None

    def press(self, now):
        self.seq += 1
        self.press_held = True
        self.hold_open = self.heavy is not None
        d = self.tap_rule(self.ticks)
        if d is None:
            return
        if d[2] > self.ticks:
            self.buffered = (d[0], d[1], d[2], self.seq)
            return
        self.begin(now, d[0], d[1], self.seq)

    def begin(self, now, mv, step, s):
        self.log.append((now, f"begin {mv} (lag {self.lag_note(now)})"))
        self.wait_hit = self.hold_open and s == self.seq
        self.move, self.step, self.move_start = mv, step, self.ticks
        self.move_wall, self.move_paused = now, self.paused(now)
        self.wait_total, self.rush_at, self.bite_at = 0.0, -1.0, -1.0
        self.buffered = None
        m = self.moves[mv]
        self.pending_hit = None if self.wait_hit else (self.move_start + m["windup"], mv)

    def lag_note(self, now):
        if not self.move:
            return "-"
        return f"{self.move} at {self.view_time(now):.2f} of {self.chain_time(now):.2f}"

    def settle_wait(self, now):
        if not self.wait_hit or not self.move:
            return
        over = self.raw(now) - self.wait_at(self.move)
        if over > 0:
            self.wait_total += over

    def release(self, now):
        self.press_held = False
        if not self.hold_open:
            return
        self.hold_open = False
        self.settle_wait(now)
        at = self.move_time(now)
        waited, self.wait_hit = self.wait_hit, False
        if waited and self.move and self.moves[self.move]["windup"] > at + 0.1:
            self.rush_at, self.rush_to = at, float(self.moves[self.move]["windup"])
        if self.move and waited:  # the server's blow: on its wind-up's tick, or on the tap after it
            m = self.moves[self.move]
            self.pending_hit = (self.move_start + max(m["windup"], math.ceil(now - self.move_wall)), self.move)

    def tick(self, now):
        self.ticks += 1
        self.settle_holds(now)
        if self.buffered and self.ticks >= self.buffered[2]:
            mv, step, _, s = self.buffered
            self.begin(now, mv, step, s)
        if self.move:
            m = self.moves[self.move]
            if self.view_time(now) > m["windup"] + m["active"] + m["recovery"] + self.row.get("late", 6):
                self.move, self.step = None, -1
        if self.wait_hit and self.move:
            self.settle_wait(now)
            if self.ticks - self.move_start > self.moves[self.move]["windup"]:
                self.move_start += 1

    # ---- the hands ---------------------------------------------------------------------------------------------------

    def hands(self, now):
        """Each hand's shown pose (d, R) this frame, authored for its own hand (ArmsHands.lab)."""
        rest = (np.zeros(3), np.eye(3))
        mv = self.move
        hands_now = now - self.paused(now)
        clip = self.sw[self.sid]["moves"].get(mv) if mv else None
        if clip is None:
            src = {"main": rest, "off": rest}
            vel = {"main": (np.zeros(3), np.zeros(3)), "off": (np.zeros(3), np.zeros(3))}
            ticks = C.REST_BLEND
            striking = {"main", "off"}
        else:
            t = self.view_time(now)
            d, R = C.sample(self.sid, mv, t, self.sw)
            hand = clip.get("hand", "main")
            idle = C.idle_of(self.sid, mv, t, self.sw)
            d2, R2 = C.sample(self.sid, mv, t + 0.1, self.sw)
            v = ((d2 - d) / 0.1, C.rot_vec(R2 @ R.T) / 0.1)
            z = (np.zeros(3), np.zeros(3))
            if hand == "both":
                src = {"main": (d, R), "off": idle if idle is not None else (d, R)}
                vel = {"main": v, "off": z if idle is not None else v}
                striking = {"main", "off"}
            else:
                other = "off" if hand == "main" else "main"
                src = {hand: (d, R), other: idle if idle is not None else rest}
                vel = {hand: v, other: z}
                striking = {hand}
            m = self.moves[mv]
            ticks = max(1.0, min(2.5, m["windup"] * 0.8))
        key = (mv, self.move_wall if mv else None)
        if key != (self.shown_move, self.shown_wall):  # ArmsHands: a new source blends in from where the hands are
            self.shown_move, self.shown_wall = key
            for h, b in self.blends.items():
                b.begin(hands_now, *src[h], *vel[h], ticks)
        return {h: b.apply(hands_now, *src[h]) for h, b in self.blends.items()}


def play(style, swings, every=3.0, down=1.2, clicks=12, hits=False, start=0.3, mastery=False):
    """Frames [(wall ticks, move, view t, chain t, {hand: (d, R)})] for {@code clicks} clicks every {@code every} ticks,
    each held {@code down}; with hits, every blow lands (its hit-stop and bite, from the row and the clip)."""
    c = Clicks(style, swings, mastery)
    c.pending_hit = None
    presses = [start + i * every for i in range(clicks)]
    releases = [p + down for p in presses]
    frames = []
    now, end = 0.0, presses[-1] + 24
    next_tick = 1.0
    pi = 0
    while now <= end:
        while next_tick <= now + 1e-9:
            t_tick = next_tick
            while pi < len(presses) and presses[pi] <= t_tick:  # the key's press reaches the client tick
                c.press(t_tick)
                pi += 1
            c.tick(t_tick)
            if hits and c.pending_hit and c.ticks >= c.pending_hit[0]:
                c.hits.append((t_tick + LATENCY, c.pending_hit[1]))
                c.pending_hit = None
            for h in [h for h in c.hits if h[0] <= t_tick]:
                c.hits.remove(h)
                if h[1] == c.move:
                    clip = swings[style]["moves"][h[1]]
                    c.hold(t_tick, c.moves[h[1]].get("hitstop", 0))
                    bite = clip.get("bite")
                    if bite and c.bite_at < 0:
                        c.bite_at, c.bite_rate, c.bite_ticks = c.move_time(t_tick), bite[0], bite[1]
                    c.log.append((t_tick, f"hit-stop {h[1]} {c.moves[h[1]].get('hitstop')} ms"))
            next_tick += 1.0
        # ArmsPredict.frame: a press let go is read every frame from the key itself (a click between two ticks is let go
        # on the frame after its press is taken up)
        if c.press_held and c.hold_open and not any(p <= now < r for p, r in zip(presses, releases)):
            c.release(now)
        shown = c.hands(now)
        frames.append((now, c.move, c.view_time(now) if c.move else 0.0, c.chain_time(now) if c.move else 0.0, shown))
        now += DT
    return frames, c.log


H0 = np.array([L.HX, L.HY, L.HZ])


def blade(held, pose, mirror):
    d, R = pose
    tip, grip = H0 + d + R @ held.tip, H0 + d + R @ held.grip
    out = []
    for a in np.linspace(0.0, 1.0, 6):
        p = grip + (tip - grip) * a
        n = S.ndc(p) if p[2] < 0 else np.array([9.0, 9.0])
        out.append(np.array([-n[0], n[1]]) if mirror else n)
    return out


def measure(style, frames, held, swings):
    """Per frame: each blade's middle step (ndc; a striking blade's own blow left out, fast by design: swing_chain's
    jump) and how near the two blades come (ndc, both in view)."""
    st = L.table()[style]
    prev, out = {}, []
    for now, mv, vt, ct, shown in frames:
        b = {h: blade(held, shown[h], h == "off") for h in ("main", "off")}
        hand = C.hand_of(swings, style, mv) if mv else None
        steps = {}
        for h in ("main", "off"):
            mid = b[h][3]
            blow = False
            if mv and (hand == h or hand == "both"):
                hit = L.hit_tick(st["moves"][mv])
                blow = hit * 0.5 <= vt <= hit + 0.67
            ok = h in prev and abs(mid[0]) < 5 and abs(prev[h][0]) < 5 and not blow
            steps[h] = float(np.linalg.norm(mid - prev[h])) if ok else 0.0
            prev[h] = mid
        seen = [p for p in b["main"] if np.all(np.abs(p) < 1.05)], [p for p in b["off"] if np.all(np.abs(p) < 1.05)]
        gap = min((float(np.linalg.norm(p - q)) for p in seen[0] for q in seen[1]), default=9.0)
        out.append((now, mv, vt, ct, steps, gap))
    return out


def report(style, swings, **kw):
    held = L.Held("gatewrought_" + style)
    st = L.table()[style]
    frames, log = play(style, swings, **kw)
    rows = measure(style, frames, held, swings)
    big = sum(1 for r in rows for s in r[4].values() if s > 0.2)
    worst = max(s for r in rows for s in r[4].values())
    print(f"steps over 0.2 off the blow: {big}, worst {worst:.2f}; nearest the blades come {min(r[5] for r in rows):.2f}")
    for k in range(1, len(rows)):
        a, b = rows[k - 1], rows[k]
        if b[1] != a[1] and a[1]:
            m = st["moves"][a[1]]
            cad = m.get("cadence")
            due = cad[0] if cad else m["windup"] + m["active"] + C.MASTERY_LINK
            after = [r for r in rows[k:] if r[0] <= b[0] + 2.0]
            sm = max(r[4]["main"] for r in after)
            so = max(r[4]["off"] for r in after)
            gp = min(r[5] for r in after)
            print(f"   {b[0]:6.2f} {a[1]} -> {b[1] or 'rest'}: left at {a[2]:.2f} of {due} (behind {due - a[2]:+.2f});"
                  f" next 2 ticks: main {sm:.2f} off {so:.2f} gap {gp:.2f}")


def sheet(out, style, swings, t0, t1, cell=320, cols=6, **kw):
    from PIL import Image, ImageChops, ImageDraw, ImageOps
    held = L.Held("gatewrought_" + style)
    frames, _ = play(style, swings, **kw)
    rows = measure(style, frames, held, swings)
    W, H = cell, cell * 9 // 16
    cells = []
    for (now, mv, vt, ct, shown), r in zip(frames, rows):
        if now < t0 or now > t1:
            continue
        off = ImageChops.multiply(ImageOps.mirror(L.frame(held, *shown["off"])), Image.new("RGB", (L.W, L.H), (255, 150, 120)))
        im = ImageChops.lighter(L.frame(held, *shown["main"]), off)
        im = im.resize((W, H), Image.LANCZOS)
        dr = ImageDraw.Draw(im)
        dr.text((4, 2), f"{now:.2f} {mv or '-'} {vt:.2f}/{ct:.2f}  gap {r[5]:.2f}", fill=(255, 110, 110) if r[5] < 0.12 else (210, 210, 220))
        cells.append(im)
    img = Image.new("RGB", (W * cols, ((len(cells) + cols - 1) // cols) * H), (14, 15, 20))
    for k, c in enumerate(cells):
        img.paste(c, ((k % cols) * W, (k // cols) * H))
    img.save(out)
    print(out, len(cells))


def film(out, style, takes, **kw):
    """An MP4 at 60 fps (ffmpeg): each take a run of clicks, every swings file in it shown one above the other, both
    blades as the game shows them. {@code takes}: [(caption, play options)]; kw["files"]: [(label, swings)]."""
    import shutil
    import subprocess
    import tempfile
    from PIL import Image, ImageChops, ImageDraw, ImageOps
    held = L.Held("gatewrought_" + style)
    files = kw.pop("files")
    tmp = tempfile.mkdtemp(prefix="clicks-film-", dir=os.path.dirname(os.path.abspath(out)))
    n = 0
    try:
        for caption, opts in takes:
            runs = [play(style, sw, **opts)[0] for _, sw in files]
            for k in range(min(len(r) for r in runs)):
                img = Image.new("RGB", (L.W, L.H * len(files)), (14, 15, 20))
                for j, (label, _) in enumerate(files):
                    now, mv, vt, ct, shown = runs[j][k]
                    off = ImageOps.mirror(L.frame(held, *shown["off"]))
                    im = ImageChops.lighter(L.frame(held, *shown["main"]), off)
                    dr = ImageDraw.Draw(im)
                    dr.text((12, 10), f"{label}   {caption}", fill=(225, 225, 235))
                    dr.text((12, 26), f"{mv or 'rest'}", fill=(150, 150, 165))
                    img.paste(im, (0, L.H * j))
                img.save(os.path.join(tmp, f"{n:05d}.png"))
                n += 1
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", os.path.join(tmp, "%05d.png"),
                        "-pix_fmt", "yuv420p", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", out], check=True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(out, n, "frames")


def main(argv):
    if not argv:
        raise SystemExit(__doc__)
    kw, swings = {}, None
    for flag, key, conv in (("--every", "every", float), ("--down", "down", float), ("--clicks", "clicks", int),
                            ("--start", "start", float), ("--cell", "cell", int), ("--cols", "cols", int)):
        if flag in argv:
            i = argv.index(flag)
            kw[key] = conv(argv[i + 1])
            argv = argv[:i] + argv[i + 2:]
    if "--mastery" in argv:
        kw["mastery"] = True
        argv.remove("--mastery")
    if "--hits" in argv:
        kw["hits"] = True
        argv.remove("--hits")
    if "--swings" in argv:
        i = argv.index("--swings")
        swings = json.load(open(argv[i + 1]))["styles"]
        argv = argv[:i] + argv[i + 2:]
    swings = swings or L.load_swings()
    if argv[0] == "report":
        report(argv[1], swings, **kw)
    elif argv[0] == "film":  # film OUT STYLE LABEL=SWINGS... : slow clicks, then fast with landed blows, mastery on
        files = [(a.split("=", 1)[0], json.load(open(a.split("=", 1)[1]))["styles"]) for a in argv[3:]]
        takes = [("clicked slowly", dict(every=10.0, down=0.6, clicks=5, mastery=True)),
                 ("clicked fast, held, every blow landing", dict(every=3.0, down=1.2, clicks=10, hits=True, mastery=True))]
        film(argv[1], argv[2], takes, files=files)
    elif argv[0] == "sheet":
        sheet(argv[1], argv[2], swings, float(argv[3]), float(argv[4]), **kw)
    else:
        raise SystemExit(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
