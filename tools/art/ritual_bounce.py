"""A stereo bounce of the whole Ritual of the Bell as one player hears it, from the doors closing to the tear: the
director's real timeline (story.Ritual's ticks), the hall's real places (PiecesRitual), the game's sound rules
(volume clamped to 1, linear fall-off to each event's attenuation distance, mono events panned by direction) and
RitualAudio's mix (six voices with the bed, two heavy at once, per-sound gaps, the reading duck).

    python3 tools/art/ritual_bounce.py today OUT.wav                 today's files and mix
    python3 tools/art/ritual_bounce.py v2 OUT.wav CUE_DIR            ritual_v2.py's cues and the proposed mix
    python3 tools/art/ritual_bounce.py v3 OUT.wav CUE_DIR [nopulse]  v2 plus Euan's first listen: the plates' timers,
                                                                      hits that land, and the hall's pulse under it all

The listener stands in the nave 15 blocks in from the doors, facing the throne, and kneels at BOW (so no rule strike).
It is a model of the game, not a recording of it: no Sound Physics, no occlusion, no Doppler.
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402

SR = K.SR
TODAY = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/ritual")
EVENTS = json.load(open(os.path.join(ROOT, "src/main/merge/story/assets/awaken/sounds.json")))
TICK = 1 / 20

# the hall, in blocks: u across (+ right as you face the throne), w from the doors, h up from the floor
LISTEN = (0.0, 15.0, 1.6)
DOORS = (0.0, 0.0, 1.5)
ALTAR = (0.0, 22.0, 0.5)
BELL = (0.0, 22.0, 20.5)
EYES = (0.0, 36.5, 22.5)
STATUES = [(s * 11.5, row + 0.5, 0.0) for row in (4, 9, 14, 19, 24, 29) for s in (-1, 1)]
PLATES = [(math.cos(math.radians(-90 + 120 * k)) * 5.0, 22.0 + math.sin(math.radians(-90 + 120 * k)) * 5.0, 0.2) for k in range(3)]
BEATS = (("DOORS", 190), ("BOW", 190), ("RING", 360), ("JUDGED", 80), ("WAKING", 170), ("JUDGEMENT", 800), ("END", 231))


class Mix:
    """RitualAudio, as a renderer: voices with lifetimes, the budget, the gaps, the reading duck."""

    def __init__(self, seconds, cues, rules):
        self.out = np.zeros((K.ns(seconds), 2))
        self.cues = cues
        self.rules = rules
        self.voices = []  # (end tick, group)
        self.next_sound = {}
        self.next_group = [0, 0, 0, 0]
        self.reading_until = -1
        self.log = []
        self.queue = []

    def play(self, tick, *args, **kw):
        """Queued: the beats schedule their cues out of order; flush() plays them in time, as the game would."""
        self.queue.append((tick, len(self.queue), args, kw, self.reading_until))

    def cut(self, tick, name, fade=0.08):
        """Queued: stop every voice of `name` at this tick (SoundManager.stop on the instance, with a short fade)."""
        self.queue.append((tick, len(self.queue), (name, fade), {"_cut": True}, self.reading_until))

    def flush(self):
        for tick, _, args, kw, reading in sorted(self.queue, key=lambda q: (q[0], q[1])):
            self.reading_until = reading
            if kw.get("_cut"):
                for v in [v for v in self.voices if v.get("name") == args[0] and v["end"] > tick]:
                    self.stop(v, tick, args[1])
                continue
            self.now(tick, *args, **kw)
        self.queue = []

    def clip(self, name):
        if name not in self.cues:
            path = os.path.join(self.rules["dir"].get(name, TODAY), name + ".ogg")
            self.cues[name] = K.read_audio(path)
        return self.cues[name]

    def now(self, tick, name, gain, group, gap, at=None, pitch=1.0, range_=1e9, move_to=None, scene=False):
        r = self.rules
        if at is not None and dist(at, LISTEN) >= range_:
            return
        self.voices = [v for v in self.voices if v["end"] > tick]
        count = len(self.voices) + 1  # the bed
        heavy = [v for v in self.voices if v["group"] == 1]
        if tick < self.next_sound.get(name, -1):
            self.log.append((tick, name, "gap"))
            return
        if count >= r["voices"] and group <= 1:
            # guidance and the beam replace a small detail (cut, as SoundManager.stop does)
            for v in self.voices:
                if v["group"] >= 2:
                    self.stop(v, tick, 0.0)
                    count -= 1
                    break
        if group == 1 and len(heavy) >= r["heavy"] and r.get("steal"):
            # the proposed rule: a new heavy hit is never dropped; the oldest heavy's tail fades out under it
            self.stop(min(heavy, key=lambda v: v["start"]), tick, 0.25)
            heavy = heavy[1:]
            count -= 1
        if count >= r["voices"] or tick < self.next_group[group] or (group == 1 and len(heavy) >= r["heavy"]):
            self.log.append((tick, name, "budget"))
            return
        self.next_group[group] = tick + {0: 0, 1: 0, 2: r["foot_gap"], 3: 2}[group]
        self.next_sound[name] = tick + (gap if r["gaps"] else 0)
        x = self.clip(name)
        if pitch != 1.0:
            idx = np.arange(0, len(x) - 1, pitch)
            x = np.stack([np.interp(idx, np.arange(len(x)), x[:, c]) for c in range(x.shape[1])], 1) if x.ndim == 2 \
                else np.interp(idx, np.arange(len(x)), x)
        ev = EVENTS.get("story.ritual." + name, {"sounds": [{}]})["sounds"][0]
        vol = min(1.0, gain * ev.get("volume", 1.0) * r["json_volume"].get(name, 1.0))
        att = r["attenuation"].get(name, ev.get("attenuation_distance", 16)) * max(1.0, gain)
        i0 = K.ns(tick * TICK)
        n = min(len(x), len(self.out) - i0)
        if n <= 0:
            return
        if x.ndim == 2 or at is None:
            seg = (K.stereo(x) if x.ndim == 1 else x)[:n] * vol
            self.log.append((tick, name, f"ui {vol:.2f}"))
        else:
            # a mono event in the world: fall-off and pan per 50 ms block (moving sources move)
            duck_reading = tick < self.reading_until
            seg = np.zeros((n, 2))
            blk = K.ns(0.05)
            for b in range(0, n, blk):
                frac = min(1.0, (b / SR) / 0.6) if move_to is not None else 0
                p = lerp(at, move_to, frac) if move_to is not None else at
                d = dist(p, LISTEN)
                g = vol * max(0.0, 1 - d / att)
                if duck_reading and tick + b / SR / TICK < self.reading_until:
                    g *= r["read_duck"]
                pan = 0.5 + 0.5 * math.sin(math.atan2(p[0] - LISTEN[0], p[1] - LISTEN[1]))
                part = x[b:b + blk] * g
                seg[b:b + len(part), 0] = part * math.cos(pan * math.pi / 2)
                seg[b:b + len(part), 1] = part * math.sin(pan * math.pi / 2)
            self.log.append((tick, name, f"world d {dist(at, LISTEN):.0f} vol {vol:.2f} att {att:.0f}"))
        self.out[i0:i0 + n] += seg
        self.voices.append({"end": tick + int(n / SR / TICK) + 1, "group": group, "start": tick, "i0": i0, "seg": seg, "name": name})

    def stop(self, v, tick, fade):
        """Take back what a voice would still have played after this tick (with a fade, or cut)."""
        k = K.ns(tick * TICK) - v["i0"]
        rest = v["seg"][k:]
        keep = np.zeros(len(rest))
        f = min(len(rest), K.ns(fade))
        keep[:f] = np.linspace(1, 0, f) if f else keep[:f]
        self.out[v["i0"] + k:v["i0"] + k + len(rest)] -= rest * (1 - keep)[:, None]
        self.voices.remove(v)


def dist(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def bed(mix, start_tick, end_tick, name, level, reading, r):
    """The looping bed, eased toward its level (ducked while words are read) like RitualAudio.Bed."""
    x = mix.clip(name)
    x = K.stereo(x) if x.ndim == 1 else x
    ev = EVENTS.get("story.ritual." + name, {"sounds": [{}]})["sounds"][0]
    jv = ev.get("volume", 1.0) * r["json_volume"].get(name, 1.0)
    i0, i1 = K.ns(start_tick * TICK), K.ns(end_tick * TICK)
    n = i1 - i0
    loop = np.tile(x, (n // len(x) + 2, 1))[:n]
    vol = np.zeros(n)
    v = 0.001
    blk = K.ns(TICK)
    for b in range(0, n, blk):
        t = start_tick + b / blk
        want = level * (r["bed_read"] / 0.5 if any(a <= t < z for a, z in reading) else 1)
        if r.get("bed_duck"):
            for (ht, hd) in r["heavy_hits"]:
                if ht <= t < ht + hd:
                    want *= r["bed_duck"]
        v += (want - v) * 0.04
        vol[b:b + blk] = v
    tail = np.linspace(1, 0, min(n, K.ns(0.8)))
    vol[-len(tail):] *= tail
    mix.out[i0:i1] += loop * (vol * jv)[:, None]


def run(mode, cue_dir=None, pulse=True):
    v3 = mode == "v3"
    v2 = mode in ("v2", "v3")
    r = {"voices": 6, "heavy": 2, "foot_gap": 4, "gaps": True, "read_duck": 0.45, "bed_read": 0.16,
         "json_volume": {}, "attenuation": {}, "dir": {}, "heavy_hits": []}
    if v2:
        names = [f[:-4] for f in os.listdir(cue_dir) if f.endswith(".ogg")]
        r["dir"] = {n: cue_dir for n in names}
        # the proposed mix: every toll heard, steps and hits carry, a bed that ducks under the big hits
        r.update({"voices": 10, "heavy": 3, "steal": True, "foot_gap": 2, "read_duck": 0.7, "bed_read": 0.3, "bed_duck": 0.55})
        r["attenuation"] = {"footstep1": 48, "footstep2": 48, "footstep3": 48, "footstep4": 48, "impact": 56, "impact2": 56,
                            "swing": 32, "swing2": 32, "windup": 32, "gaze_charge": 96, "gaze_fire": 96, "gaze_travel": 48,
                            "gaze_impact": 64, "pit": 64, "statue_rise": 96, "rumble": 96, "doors": 80, "flames_out": 80}
        r["json_volume"] = {"candle": 1.0}
    if v3:
        # hits land: louder and further than the steps; the plates' timers carry to the whole hall
        r["attenuation"].update({"impact": 64, "impact2": 64, "plate_tick": 96, "plate_tock": 96, "plate_rise": 96})
        r["v3"] = True
    total = sum(n for _, n in BEATS) + 200 + 60
    mix = Mix(total * TICK + 12, {}, r)
    G = (lambda old, new: new if v2 else old)
    t0 = {}
    t = 0
    for b, n in BEATS:
        t0[b] = t
        t += n
    reading = []
    # beat 1: the doors
    T = t0["DOORS"]
    mix.play(T + 0, "doors", G(0.6, 1.0), 1, 80, DOORS, range_=64 if not v2 else 96)
    mix.play(T + 34, "flames_out", G(0.6, 0.9), 3 if not v2 else 1, 20, ALTAR, range_=64 if not v2 else 96)
    for k in range(40):
        order = k / 39
        side = -1 if k % 2 else 1
        pos = (side * 10.0, 2 + order * 33, 1.0)
        mix.play(T + 80 + int(order * 100), "candle", G(0.32, 0.4), 3, 6, pos, pitch=0.8 + order * 0.6, range_=24 if not v2 else 32)
    # beat 2: BOW
    T = t0["BOW"]
    mix.play(T, "bell", G(0.6, 0.85), 0, G(100, 10), BELL, pitch=1.0, range_=96)
    mix.play(T + 12, "words", 0.35 if not v2 else 0.45, 0, 20)
    reading.append((T + 12, T + 12 + max(60, 150)))
    mix.reading_until = T + 12 + 150
    # RING
    T = t0["RING"]
    mix.play(T, "bell", G(0.6, 0.85), 0, G(100, 10), BELL, pitch=1.12, range_=96)
    mix.play(T, "words", 0.35 if not v2 else 0.45, 0, 20)
    ring_read = 340 if not v2 else 90
    reading.append((T, T + ring_read))
    mix.reading_until = T + ring_read
    for k, at in enumerate((40, 140, 240)):
        mix.play(T + at, "bell", G(0.38, 0.6), 0, G(100, 10), PLATES[k], pitch=1.26 + k * 0.25, range_=96)
        if v3:
            plate_timer(mix, T + at, k)
    # BE JUDGED
    T = t0["JUDGED"]
    mix.play(T, "bell", G(0.6, 0.85), 0, G(100, 10), BELL, pitch=0.89, range_=96)
    mix.play(T, "words", 0.35 * 0.84 if not v2 else 0.4, 0, 20, pitch=0.84)
    reading.append((T, T + 90))
    mix.reading_until = T + 90
    mix.play(T + 18, "bell", G(0.6, 0.85), 0, G(100, 10), BELL, pitch=0.75, range_=96)
    # beat 3: the waking
    T = t0["WAKING"]
    if v2:
        mix.play(T, "eyes", 0.9, 1, 80)  # the god fills the hall: a scene sound, both ears
        r["heavy_hits"].append((T + 58, 40))
    else:
        mix.play(T, "eyes", 0.6, 1, 80, EYES, range_=96)
    mix.play(T + 12, "rumble", G(0.5, 0.6), 3 if not v2 else 1, G(60, 20), ALTAR, range_=96)
    mix.play(T + 58, "rumble", G(0.5, 0.75), 3 if not v2 else 1, G(60, 20), ALTAR, range_=96)
    mix.play(T + 92, "statue_rise", G(0.55, 0.95), 1, 60, ALTAR, range_=64 if not v2 else 96)
    if v2:
        r["heavy_hits"].append((T + 92, 50))
    # beat 4: the Judgement
    T = t0["JUDGEMENT"]
    judgement(mix, T, v2, r, v3)
    # beat 5: the end
    T = t0["END"]
    if v2:
        mix.play(T, "raise", 0.8, 0, 80)
    mix.play(T + 40, "drain", G(0.5, 0.6), 0, 80)
    if v2:
        mix.play(T + 195, "error", 0.6, 0, 40)
    end = T + 222
    mix.flush()
    # the hand closes: everything else is cut
    cut = K.ns(end * TICK)
    mix.out[cut:] = 0
    mix.voices = []
    mix.play(end, "close", G(0.65, 1.0), 0, 20)
    # the hall's bed under it all
    bed(mix, 0, end, "hall", 0.5, reading, r)
    if v3 and pulse:
        hall_pulse(mix, t0, end, r)
    # the void: the black, a far bell, the heartbeat
    V = end + 8
    bed(mix, V, V + 200, "void", 0.25, [], r)
    mix.play(V + 22, "bell", G(0.4, 0.45), 0, 140, pitch=0.8)
    for k in range(5):
        mix.play(V + 50 + 34 * k, "heartbeat", 0.38, 0, 30)
    mix.play(V + 200, "tear", 0.4, 0, 20)
    mix.flush()
    y = mix.out[:K.ns((V + 260) * TICK)]
    # OpenAL Soft's output limiter
    y = K.limit(y, -0.3, release=0.15)
    return y, mix.log, t0


def judgement(mix, T, v2, r, v3=False):
    """The statues advance (two at once for one player), wind up, swing; the gaze five times; pits."""
    statues = [list(s) for s in STATUES]
    busy = {}
    swings = []
    for k, g in enumerate((30, 200, 370, 540, 690)):
        mix.play(T + g, "gaze_charge", G2(v2, 0.6, 0.8), 0, 30, EYES, range_=G2(v2, 48, 96))
        mix.play(T + g + 45, "gaze_fire", G2(v2, 0.55, 0.9), 1, 30, EYES, range_=G2(v2, 64, 96))
        mix.play(T + g + 45, "gaze_impact", G2(v2, 0.55, 1.0), 1, 30, LISTEN, range_=G2(v2, 48, 64))
        mix.play(T + g + 45, "gaze_travel", G2(v2, 0.45, 0.7), 0, 30, EYES, move_to=LISTEN, range_=1e9)
        if v2:
            r["heavy_hits"].append((T + g + 45, 30))
    for pk in range(220, 800 - 25, 120):
        mix.play(T + pk + 30, "pit", G2(v2, 0.55, 0.9), 1, 60, ALTAR, range_=G2(v2, 48, 64))
    plant_every = 0.8 / (0.3 * 0.55) if not v2 else 1.25 / 0.075  # ticks between footfalls of one statue
    step_n = 0
    for tick in range(40, 800 - 25):
        if (tick - 40) % 45 == 0 and len(swings) < 2:
            best = min((i for i in range(12) if i not in busy), key=lambda i: dist(statues[i], LISTEN))
            busy[best] = True
            swings.append({"i": best, "phase": 0, "since": tick, "acc": 0.0})
        for w in list(swings):
            s = statues[w["i"]]
            if w["phase"] == 0:
                d = (LISTEN[0] - s[0], LISTEN[1] - s[1])
                ln = math.hypot(*d)
                if ln > 0.05:
                    s[0] += d[0] / ln * min(ln, 0.075)
                    s[1] += d[1] / ln * min(ln, 0.075)
                w["acc"] += 1
                if w["acc"] >= plant_every:
                    w["acc"] -= plant_every
                    step_n += 1
                    name = f"footstep{(w['i'] + step_n) % (4 if v2 else 3) + 1}"
                    mix.play(T + tick, name, 0.8 if v3 else G2(v2, 0.48, 0.9), 2, 0, tuple(s), range_=G2(v2, 36, 48))
                if dist(s, LISTEN) <= 3.0 or tick - w["since"] >= 80:
                    w["phase"], w["since"] = 1, tick
                    if v2:
                        mix.play(T + tick, "windup", 0.7, 3, 0, tuple(s), range_=32)
            elif w["phase"] == 1:
                whoosh_at = 17 if not v2 else 19
                if tick - w["since"] == whoosh_at:
                    mix.play(T + tick, "swing" if not v2 or w["i"] % 2 == 0 else "swing2", G2(v2, 0.5, 0.85), 3, 3, tuple(s), range_=G2(v2, 24, 32))
                if tick - w["since"] >= 25:
                    w["phase"], w["since"] = 2, tick
                    fwd = (LISTEN[0] - s[0], LISTEN[1] - s[1])
                    ln = max(1e-6, math.hypot(*fwd))
                    hit = (s[0] + fwd[0] / ln * 2.5, s[1] + fwd[1] / ln * 2.5, 0.0)
                    mix.play(T + tick, "impact" if not v2 or w["i"] % 2 == 0 else "impact2", G2(v2, 0.55, 1.0), 1, 4, hit, range_=64 if v3 else G2(v2, 32, 56))
                    if v2:
                        r["heavy_hits"].append((T + tick, 24))
                    # it caught you (the first two times; a player moves after that)
                    if v3 and dist(s, LISTEN) <= 3.8 and r.setdefault("struck", 0) < 2:
                        r["struck"] += 1
                        mix.play(T + tick + 1, "struck", 0.9, 0, 10)
            elif tick - w["since"] >= 22:
                swings.remove(w)
                busy.pop(w["i"], None)


# a plate's timer: ticks from the plate, faster and louder to its deadline (80 ticks), stopped when you stand on it
PLATE_TICKS = (6, 14, 22, 29, 36, 42, 48, 53, 58, 62, 66, 70, 73, 76, 79)
PLATE_ANSWER = (50, 57, 63)  # when the bounce's player reaches each plate (ticks after it lights)


def plate_timer(mix, lit, k):
    on = PLATE_ANSWER[k]
    mix.play(lit, "plate_rise", 0.32, 0, 0, PLATES[k], range_=1e9)
    for j, dt in enumerate(PLATE_TICKS):
        if dt >= on:
            break
        u = dt / 80
        mix.play(lit + dt, "plate_tick" if j % 2 == 0 else "plate_tock", 0.55 + 0.45 * u, 0, 0, PLATES[k],
                 pitch=1.0 + 0.12 * u, range_=1e9)
    mix.cut(lit + on, "plate_rise", 0.06)
    mix.play(lit + on, "plate_lock", 0.8, 0, 0, pitch=1.0 + k * 0.19)


def hall_pulse(mix, t0, end, r):
    """The hall's pulse and the tone under it (music: its own channels, outside the effects budget). A soft drum
    while it waits, quickening through the rings, racing to the god's eyes, driving the Judgement; gone when the hand
    rises, the tone rising and bending up to the close, then cut with everything else."""
    hits = []  # (tick, name, gain)

    def every(a, b, step, name, gain, accent=None):
        t = a
        k = 0
        while t < b:
            g = gain if accent is None or k % accent else min(1.0, gain * 1.45)
            hits.append((t, name if accent is None or k % accent else "pulse_hard", g))
            t += step
            k += 1
    D, B, R, J, W, JU, E = (t0[b] for b in ("DOORS", "BOW", "RING", "JUDGED", "WAKING", "JUDGEMENT", "END"))
    every(D + 60, B, 30, "pulse_soft", 0.32)
    every(B, R, 30, "pulse_soft", 0.38)
    every(R + 10, J, 20, "pulse_soft", 0.42)
    every(J + 30, W, 25, "pulse_soft", 0.48)
    # racing to the eyes: the gaps shrink from 16 ticks to 3 and land on the bloom
    t = W + 58
    gaps = [3, 3, 4, 4, 5, 5, 6, 7, 8, 10, 12, 14]
    seq = []
    for g in gaps:
        t -= g
        seq.append(t)
    for i, tt in enumerate(sorted(seq)):
        hits.append((tt, "pulse_soft", 0.4 + 0.3 * i / len(seq)))
    hits.append((W + 58, "pulse_hard", 0.95))
    hits.append((W + 92, "pulse_hard", 0.95))
    every(W + 110, JU, 16, "pulse_soft", 0.5, accent=4)
    every(JU, E, 16, "pulse_soft", 0.5, accent=4)
    for tick, name, gain in hits:
        x = mix.clip(name)
        x = K.stereo(x) if x.ndim == 1 else x
        i0 = K.ns(tick * TICK)
        n = min(len(x), K.ns(end * TICK) - i0)
        if n > 0:
            duck = 0.55 if any(ht <= tick < ht + hd for ht, hd in r["heavy_hits"]) else 1.0
            mix.out[i0:i0 + n] += x[:n] * gain * duck
    # the tone: in with the flames dying, up beat by beat, bent up a minor third through the End
    x = mix.clip("pulse_drone")
    i0, i1 = K.ns((D + 34) * TICK), K.ns(end * TICK)
    n = i1 - i0
    lv = [(D + 34, 0.0), (D + 110, 0.22), (B, 0.26), (R, 0.32), (J, 0.42), (W, 0.5), (W + 58, 0.62), (JU, 0.5),
          (E, 0.55), (end - 10, 0.95), (end, 0.95)]
    ticks = np.arange(n) / SR / TICK + D + 34
    level = np.interp(ticks, [a for a, _ in lv], [b for _, b in lv])
    for ht, hd in r["heavy_hits"]:
        m = (ticks >= ht) & (ticks < ht + hd)
        level[m] *= 0.6
    rate = np.where(ticks < E, 1.0, 2 ** ((3 * np.clip((ticks - E) / (end - E), 0, 1) ** 1.5) / 12))
    pos = np.cumsum(rate)
    loop = np.tile(x, (int(pos[-1] // len(x)) + 2, 1))
    idx = np.arange(len(loop))
    y = np.stack([np.interp(pos, idx, loop[:, c]) for c in (0, 1)], axis=1)
    mix.out[i0:i1] += y * level[:, None]


def G2(v2, old, new):
    return new if v2 else old


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    y, log, t0 = run(mode, sys.argv[3] if len(sys.argv) > 3 else None, pulse="nopulse" not in sys.argv[4:])
    data = np.clip(y, -1, 1).astype("<f4").tobytes()
    import subprocess
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "pipe:0", out], input=data, check=True)
    dropped = [l for l in log if l[2] in ("gap", "budget")]
    print(f"{out}: {len(y) / SR:.1f} s, LUFS {K.loudness(y):.1f}, TP {K.true_peak(y):.1f}; beats at "
          + ", ".join(f"{b} {v * TICK:.1f}s" for b, v in t0.items()))
    print(f"played {sum(1 for l in log if l[2] not in ('gap', 'budget'))}, dropped {len(dropped)}:",
          sorted({(l[1], l[2]) for l in dropped}))
    with open(out + ".log.txt", "w") as f:
        for l in log:
            f.write(f"{l[0] * TICK:7.2f}s {l[1]:12s} {l[2]}\n")
