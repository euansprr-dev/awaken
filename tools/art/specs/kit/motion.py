"""The Creature Lab in motion (docs/bosses/REWORK.md R0): each move and each string played as the game plays it, as an
animated preview, and measured, so motion can be judged before anyone launches the game.

    python3 tools/art/specs/kit_bodies.py motion DIR [ids...]

It plays a sequence the way KitRenderer does:
- the idle always runs underneath, and the move's clips add on top;
- each stage clip is stretched to its ticks;
- a move fades in over 0.15 s, and the move before it fades out from its last frame over the same 0.15 s;
- a move's recovery lets go over 0.15 s after its last tick.
There are no holds and no enrage.

It writes one GIF per move and per string (`then` in the fight script), and motion.json with the numbers. The meter
samples the whole pose 60 times a second and reports:
- **still**: seconds inside the move when no bone of the move's own layer turns faster than 15°/s, counted in spells
  of 0.15 s or more (a whole-body freeze);
- **kinks**: frames where a bone's speed changes by more than 3° a frame from one frame to the next (an abrupt start
  or stop, as where one clip hands over to the next), and the largest;
- **held**: the share of the move's time when at least 70% of the bones it poses are still (under 15°/s): a pose
  held while one or two parts drift;
- **dip** (strings only): how far the move layer falls back toward rest between two blows, 100% meaning all the way;
- **peak**: the fastest bone, °/s.
"""
import json
import math
import os

import fauna_rig as R

FADE = 0.15
FPS = 60
GIF_FPS = 20
STILL_SPEED = 15.0  # deg/s
STILL_SPELL = 0.15  # s
KINK = 3.0  # deg/frame change of speed at 60 fps


def _smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def schedule(moves_by_id, seq, lead=0.5, tail=0.6):
    """The sequence as timed segments: [(id, start s, windup s, active s, recover s)], and the total length."""
    out, t = [], lead
    for mid in seq:
        m = moves_by_id[mid]
        w, a, r = m.windup / 20, m.active / 20, m.recover / 20
        out.append((mid, t, w, a, r))
        t += w + a + r + 1 / 20  # the next move begins the tick after this one stops
    return out, t + tail


def layers_at(rig, segs, t):
    """The layers the runtime would play at time t: the idle, the move playing (or letting go) and the one before it."""
    ls = [("idle", t, 1.0)]
    cur, prev = None, None
    for i, s in enumerate(segs):
        if t >= s[1]:
            cur, prev = s, segs[i - 1] if i > 0 else None
    if cur is None:
        return ls, None, 0.0
    mid, st, w, a, r = cur
    since = t - st
    stage, w_ = _stage(rig, mid, since, w, a, r)
    weight = _smooth(since / FADE) if prev is not None or since < FADE else 1.0
    if since > w + a + r:
        weight *= max(0.0, 1 - (since - w - a - r) / FADE)
    if weight > 0:
        ls.append((stage[0], stage[1], weight))
    if prev is not None:
        pmid, pst, pw, pa, pr = prev
        end = pst + pw + pa + pr
        sincend = t - end
        if 0 <= sincend < FADE:
            ps, _ = _stage(rig, pmid, pw + pa + pr, pw, pa, pr)
            ls.append((ps[0], ps[1], 1 - _smooth(sincend / FADE)))
    return ls, mid, since


def _stage(rig, mid, since, w, a, r):
    clip = lambda n: rig.clips[mid + "_" + n]
    if since < w:
        c = clip("windup")
        return (c.name, min(c.length, since / max(0.05, w) * c.length)), "windup"
    if since < w + a:
        c = clip("active")
        return (c.name, (since - w) / max(0.05, a) * c.length), "active"
    c = clip("recover")
    return (c.name, min(c.length, (since - w - a) / max(0.05, r) * c.length)), "recover"


def _move_layer(rig, ls):
    """The pose of the move layers alone (without the idle), as {bone: (rx, ry, rz) degrees}."""
    p = rig.sample([x for x in ls if x[0] != "idle"])
    return {b: tuple(v * 180 / math.pi for v in e["rot"]) for b, e in p.items()}


def measure(rig, moves_by_id, seq):
    segs, total = schedule(moves_by_id, seq)
    n = int(total * FPS)
    prev, prevv = {}, {}
    still, run, kinks, worst, peak = 0.0, 0.0, 0, 0.0, 0.0
    held, moving_frames = 0, 0
    mag = []
    for f in range(n):
        t = f / FPS
        ls, mid, _ = layers_at(rig, segs, t)
        pose = _move_layer(rig, ls)
        mag.append(sum(abs(c) for v in pose.values() for c in v))
        fastest = 0.0
        slow = posed = 0
        for b, v in pose.items():
            if b in prev:
                d = math.sqrt(sum((v[i] - prev[b][i]) ** 2 for i in range(3)))
                fastest = max(fastest, d * FPS)
                if sum(abs(c) for c in v) > 3:
                    posed += 1
                    slow += d * FPS < STILL_SPEED
                if b in prevv:
                    jump = abs(d - prevv[b])
                    if jump > KINK and mid is not None:
                        kinks += 1
                    worst = max(worst, jump)
                prevv[b] = d
            prev[b] = v
        peak = max(peak, fastest)
        in_move = mid is not None and any(s[1] <= t <= s[1] + s[2] + s[3] + s[4] for s in segs)
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
    return {"seq": seq, "seconds": round(total, 2), "still_s": round(still, 2), "kinks": kinks, "worst_kink": round(worst, 1),
            "held": round(held / max(1, moving_frames), 2),
            "dip": None if dip is None else round(dip, 2), "peak_deg_s": round(peak)}


def gif(path, rig, lit, moves_by_id, seq, yaw=28, pitch=10, size=(300, 270)):
    from PIL import Image, ImageDraw
    segs, total = schedule(moves_by_id, seq)
    times = [f / GIF_FPS for f in range(int(total * GIF_FPS))]
    trees = [rig.tree(rig.sample(layers_at(rig, segs, t)[0])) for t in times]
    b = R.bounds_of(trees, yaw, pitch)
    px = min((size[0] - 20) / (b[1] - b[0]), (size[1] - 40) / (b[3] - b[2]))
    org = (size[0], size[1], (size[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, size[1] - 26 - b[3] * px)
    frames = []
    for t, tr in zip(times, trees):
        im = Image.new("RGB", size, (156, 172, 190))
        body = R.render(tr, lit, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=org)
        im.paste(body, (0, 0), body)
        d = ImageDraw.Draw(im)
        _, mid, since = layers_at(rig, segs, t)
        label = "idle"
        if mid is not None:
            s = next(x for x in segs if x[0] == mid and x[1] <= t)
            _, label = _stage(rig, mid, since, s[2], s[3], s[4])
            label = f"{mid}  {label}" if since <= s[2] + s[3] + s[4] else f"{mid}  letting go"
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
