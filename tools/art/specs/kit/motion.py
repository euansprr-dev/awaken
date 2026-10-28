"""The Creature Lab in motion (docs/bosses/REWORK.md R0, R2): each move and each string played as the game plays it, as
an animated preview, and measured, so motion can be judged before anyone launches the game.

    python3 tools/art/specs/kit_bodies.py motion DIR [ids...]

It plays a sequence the way KitRenderer and KitBlend do (SPEC §35.10):
- the idle always runs underneath, and the move's one clip (`move_<id>`) adds on top at its base timing;
- in a string the next move begins at the last one's link point (a quarter into its recovery), and a lone move plays
  to its end and lets go;
- every change of what plays (a move begins, a string links on, a move ends) keeps last frame's pose as an offset that
  eases out on a critically damped spring (half-life 0.07 s), so nothing snaps;
- the rig's secondary parts (a chain, tassets, veils) lag their parents' turn on their springs and settle.
There are no holds and no enrage.

It writes one GIF per move and per string (`then` in the fight script), and motion.json with the numbers. The meter
samples the whole pose 60 times a second and reports:
- **still**: seconds inside the move when no bone of the move's own layer turns faster than 15°/s, counted in spells
  of 0.15 s or more (a whole-body freeze);
- **kinks**: frames where a bone's speed changes by more than 3° a frame from one frame to the next (an abrupt start
  or stop), and the largest, outside the strike: in the wind-up, the hold, the follow-through, the recovery and every
  hand-over from one move to the next (R2's target: none over 4°);
- **strike**: the largest change of speed inside a strike (from just before its peak to just after its last active
  tick), where a fast blow has to snap: reported, not a fault;
- **held**: the share of the move's time when at least 70% of the bones it poses are still (under 15°/s): a pose
  held while one or two parts drift;
- **dip** (strings only): how far the move layer falls back toward rest between two blows, 100% meaning all the way
  (angles taken the short way round, so a part that spins whole turns doesn't count them);
- **peak**: the fastest bone, °/s.
"""
import json
import math
import os

import fauna_rig as R

FADE = 0.15
HALF_LIFE = 0.07  # KitBlend.HALF_LIFE
SWING = 0.7  # KitBlend.SWING
STEP = 1 / 120  # KitBlend.STEP
FPS = 60
GIF_FPS = 20
STILL_SPEED = 15.0  # deg/s
STILL_SPELL = 0.15  # s
KINK = 3.0  # deg/frame change of speed at 60 fps


def schedule(moves_by_id, seq, lead=0.5, tail=0.6):
    """The sequence as timed segments: [(id, start s, windup s, active s, recover s, end s)], and the total length.
    A move ends where the next one links on, or at its last tick."""
    out, t = [], lead
    for i, mid in enumerate(seq):
        m = moves_by_id[mid]
        w, a, r = m.windup / 20, m.active / 20, m.recover / 20
        link = getattr(m, "link", 0)
        if i + 1 < len(seq) and 0 < link < m.recover:
            end = t + w + a + link / 20
        else:
            end = t + w + a + r + 1 / 20  # the server lets go the tick after its last
        out.append((mid, t, w, a, r, end))
        t = end
    return out, t + tail


def layers_at(rig, segs, t):
    """The layers the runtime plays at time t: the idle and the move playing; and what plays, as a key."""
    ls = [("idle", t, 1.0)]
    for i, s in enumerate(segs):
        if s[1] <= t < s[5]:
            mid, st, w, a, r, _ = s
            since = t - st
            ls.append(("move_" + mid, min(since, w + a + r), 1.0))
            return ls, mid, since, i
    return ls, None, 0.0, -1


def stage_of(since, w, a, r):
    return "windup" if since < w else "active" if since < w + a else "recover"


class Blend:
    """KitBlend in Python: the transition offset (inertialization) and the secondary springs, on sampled poses."""

    def __init__(self, rig):
        self.rig, self.key, self.out, self.off, self.vel, self.ov = rig, None, {}, {}, {}, {}
        self.springs = list(getattr(rig, "springs", []))
        self.sx, self.sv, self.up = {}, {}, {}
        self.seeded = False

    def step(self, pose, key, dt, link=0.0):
        """link: seconds to the new move's peak when it links on from the last (a string): the offset then eases out on
        a cubic that starts at the last pose and speed and reaches nothing exactly at the peak."""
        bones = set(pose) | set(self.out)
        cur = {b: list(pose.get(b, {}).get("rot", (0, 0, 0))) + list(pose.get(b, {}).get("pos", (0, 0, 0))) for b in bones}
        if self.seeded and key != self.key:
            self.link, self.since = link, 0.0
            for b in bones:
                last = self.out.get(b, [0.0] * 6)
                self.off[b] = [last[c] - cur[b][c] for c in range(6)]
                for c in range(3):  # turn the short way round, as KitBlend does
                    self.off[b][c] = (self.off[b][c] + math.pi) % (2 * math.pi) - math.pi
                self.vel[b] = list(self.ov.get(b, [0.0] * 6))  # still moving the way it moved
        self.key = key
        lk = getattr(self, "link", 0.0)
        if lk > 0:
            self.since = getattr(self, "since", 0.0) + dt
            a = min(1.0, self.since / lk)
            h00, h10 = 2 * a ** 3 - 3 * a * a + 1, (a ** 3 - 2 * a * a + a) * lk
        w = 2 * 0.6931 / HALF_LIFE
        e = math.exp(-w * dt)
        res = {}
        for b in bones:
            x, v = self.off.get(b), self.vel.get(b)
            if x is not None:
                for c in range(6):
                    if lk > 0:  # x, v hold the offset and speed it started with
                        cur[b][c] += h00 * x[c] + h10 * v[c]
                        continue
                    j = v[c] + w * x[c]
                    x[c], v[c] = (x[c] + j * dt) * e, (v[c] - w * j * dt) * e
                    cur[b][c] += x[c]
            if dt > 0 and b in self.out:
                self.ov[b] = [(cur[b][c] - self.out[b][c]) / dt for c in range(6)]
            self.out[b] = list(cur[b])
            res[b] = cur[b]
        # the springs last, on the eased pose; each reads its parents before any spring moves them
        swing = []
        for bone, gain, hz, damp in self.springs:
            node = self.rig.by_name.get(bone)
            if node is None:
                continue
            ax = az = 0.0
            p = node.parent
            while p is not None:
                r = res.get(p.name)
                if r:
                    ax += r[0]
                    az += r[2]
                p = p.parent
            sx, sv = self.sx.setdefault(bone, [0.0, 0.0]), self.sv.setdefault(bone, [0.0, 0.0])
            if self.seeded and dt > 0:
                lu = self.up[bone]
                sx[0] -= gain * (ax - lu[0])
                sx[1] -= gain * (az - lu[1])
                om = 2 * math.pi * hz
                steps = min(16, math.ceil(dt / STEP))  # KitBlend.STEP: slices, so a stiff spring never blows up
                h = dt / steps
                for _ in range(steps):
                    for c in range(2):
                        acc = -om * om * sx[c] - 2 * damp * om * sv[c]
                        sv[c] += acc * h
                        sx[c] += sv[c] * h
            self.up[bone] = (ax, az)
            swing.append((bone, sx))
        for bone, sx in swing:
            r = res.setdefault(bone, [0.0] * 6)
            r[0] += SWING * math.tanh(sx[0] / SWING)
            r[2] += SWING * math.tanh(sx[1] / SWING)
        self.seeded = True
        return {b: {"rot": tuple(v[:3]), "pos": tuple(v[3:]), "scale": pose.get(b, {}).get("scale", (1, 1, 1))} for b, v in res.items()}


def play(rig, segs, total, fps):
    """The whole sequence at fps: [(t, final pose, move-layer pose in degrees, move id, since)]."""
    bl, out, last = Blend(rig), [], -1
    for f in range(int(total * fps)):
        t = f / fps
        ls, mid, since, key = layers_at(rig, segs, t)
        linked = last >= 0 and key == last + 1  # a string's next blow, straight from the last
        final = bl.step(rig.sample(ls), key, 1 / fps, segs[key][2] if linked else 0.0)
        last = key
        idle = rig.sample(ls[:1])
        layer = {}
        for b, e in final.items():
            i = idle.get(b, {}).get("rot", (0, 0, 0))
            layer[b] = tuple((e["rot"][c] - i[c]) * 180 / math.pi for c in range(3))
        out.append((t, final, layer, mid, since))
    return out


def measure(rig, moves_by_id, seq):
    segs, total = schedule(moves_by_id, seq)
    prev, prevv = {}, {}
    still, run, kinks, worst, peak, snap = 0.0, 0.0, 0, 0.0, 0.0, 0.0
    held, moving_frames = 0, 0
    mag = []
    for t, _, pose, mid, since in play(rig, segs, total, FPS):
        seg = next((x for x in segs if x[0] == mid and x[1] <= t < x[5]), None) if mid else None
        strike = seg is not None and seg[2] - 0.1 <= since <= seg[2] + seg[3] + 0.15
        mag.append(sum(abs((c + 180) % 360 - 180) for v in pose.values() for c in v))  # a whole turn (a spinning halo) is no turn
        fastest = 0.0
        slow = posed = 0
        for b, v in pose.items():
            if b in prev:
                d = math.sqrt(sum(((v[i] - prev[b][i] + 180) % 360 - 180) ** 2 for i in range(3)))  # a turn of 360° is no turn
                fastest = max(fastest, d * FPS)
                if sum(abs(c) for c in v) > 3:
                    posed += 1
                    slow += d * FPS < STILL_SPEED
                if b in prevv:
                    jump = abs(d - prevv[b])
                    if strike:
                        snap = max(snap, jump)
                    else:
                        if jump > KINK and mid is not None:
                            kinks += 1
                        worst = max(worst, jump)
                prevv[b] = d
            prev[b] = v
        peak = max(peak, fastest)
        in_move = mid is not None
        if in_move:
            moving_frames += 1
            held += posed > 0 and slow >= 0.7 * posed
        if in_move and fastest < STILL_SPEED:
            run += 1 / FPS
        else:
            if run >= STILL_SPELL:
                still += run
            run = 0.0
    if run >= STILL_SPELL:
        still += run
    dip = None
    if len(segs) > 1:
        # between each pair of contacts: how low the move layer falls, against the lower of the two contacts' size
        dips = []
        for a_, b_ in zip(segs, segs[1:]):
            ca, cb = int((a_[1] + a_[2]) * FPS), int((b_[1] + b_[2]) * FPS)
            lo = min(mag[ca:cb + 1])
            top = min(mag[ca], mag[cb]) or 1.0
            dips.append(max(0.0, 1 - lo / top))
        dip = max(dips)
    return {"seq": seq, "seconds": round(total, 2), "still_s": round(still, 2), "kinks": kinks, "worst_kink": round(worst, 1), "strike_snap": round(snap, 1),
            "held": round(held / max(1, moving_frames), 2),
            "dip": None if dip is None else round(dip, 2), "peak_deg_s": round(peak)}


def gif(path, rig, lit, moves_by_id, seq, yaw=28, pitch=10, size=(300, 270)):
    from PIL import Image, ImageDraw
    segs, total = schedule(moves_by_id, seq)
    played = play(rig, segs, total, GIF_FPS)
    trees = [rig.tree(p[1]) for p in played]
    b = R.bounds_of(trees, yaw, pitch)
    px = min((size[0] - 20) / (b[1] - b[0]), (size[1] - 40) / (b[3] - b[2]))
    org = (size[0], size[1], (size[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, size[1] - 26 - b[3] * px)
    frames = []
    for (t, _, _, mid, since), tr in zip(played, trees):
        im = Image.new("RGB", size, (156, 172, 190))
        body = R.render(tr, lit, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=org)
        im.paste(body, (0, 0), body)
        d = ImageDraw.Draw(im)
        label = "idle"
        if mid is not None:
            s = next(x for x in segs if x[0] == mid and x[1] <= t < x[5])
            label = f"{mid}  {stage_of(since, s[2], s[3], s[4])}"
        d.rectangle((0, size[1] - 18, size[0], size[1]), fill=(32, 36, 44))
        d.text((6, size[1] - 15), f"{label}   {t:4.2f} s", fill=(230, 232, 238))
        frames.append(im.convert("P", palette=Image.ADAPTIVE, colors=96))
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / GIF_FPS), loop=0, disposal=2)


def strings(fight):
    """Every string the fight script can play (a pool entry and one of its `then` lists), once each."""
    seen, out = set(), []
    for ph in fight.get("phases", []):
        for e in ph.get("pool", []):
            for th in e.get("then", []):
                s = tuple([e["move"]] + list(th))
                if s not in seen:
                    seen.add(s)
                    out.append(list(s))
    return out


def run(outdir, d, skin, glow, gifs=True):
    from PIL import Image
    rig, mv = d["rig"], d["moves"]
    by = {m.id: m for m in mv}
    lit = Image.alpha_composite(skin.image(), glow.image())
    os.makedirs(outdir, exist_ok=True)
    rows = []
    for seq in [[m.id] for m in mv] + strings(d.get("fight") or {}):
        row = measure(rig, by, seq)
        rows.append(row)
        if gifs:
            name = "-".join(seq)
            gif(os.path.join(outdir, f"{rig.id}_{name}.gif"), rig, lit, by, seq)
    with open(os.path.join(outdir, f"{rig.id}_motion.json"), "w") as f:
        json.dump(rows, f, indent=1)
    return rows
