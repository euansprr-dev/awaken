"""Powers' authored cast sounds (docs/power-anims/PLAN.md section 5.6): layered, not vanilla remixes. The first set, for
the stamp and the beam through the core; every other power still plays its vanilla remix (merge/powers sounds.json) until
its family's kit is made. Built on audio_arms' helpers (CC0 recordings from tools/art/sources/kenney for the physical
transients, the audio kit for the air, the weight and the energy) and mastered the same way (a transient limiter, no pumping).

  stomp_lift   the knee comes up: cloth and leather, a breath of air rising (plays on the press)
  stomp_plant  the plant: a heavy punch and a dull body, the stone cracking, the weight in the sub, rubble falling back,
               the ground's rumble as the tail (plays on the release tick, with the wave)
  beam_gather  hands at the sternum: a rising tone and a band of air closing on a soft tick
  beam_loop    the hum the beam holds (played every 10 ticks over a 0.62 s file, so one fades into the next)
  beam_end     the light bleeds out: a falling tone, falling air, a scatter of sparks

    python3 tools/art/specs/audio_powers.py            render every file, check, write the JSON
    python3 tools/art/specs/audio_powers.py --json     only the JSON
    python3 tools/art/specs/audio_powers.py stomp      only these (name prefixes)

Files: assets/awaken/sounds/powers/power_<event>_<k>.ogg; events power.<event> in src/main/merge/powers/assets/awaken/
sounds.json (only these entries are replaced). Deterministic: every random source is seeded.
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
sys.path.insert(0, ART)
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
import ritual_sounds as R  # noqa: E402
import audio_arms as AA  # noqa: E402  (helpers: rec, pick, shift, trim, cloth, sub, _blow, tame, master)
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/powers")
MERGE = os.path.join(ROOT, "src/main/merge/powers/assets/awaken")
SR = K.SR


def stomp_lift(seed):
    r = K.rng(seed)
    n = K.ns(0.42)
    leather = AA.trim(AA.rec("handleSmallLeather"), 0.25) if AA._has("handleSmallLeather") else np.zeros(1)
    air = R.unit(R.sweep(K.noise(n, "pink", seed + 1), lambda s: 400 + 1400 * min(1.0, s / 0.3), 1.5)) * K.perc(n, 0.12, 0.25)
    b = R.Bus(0.46)
    b.add(AA.cloth(0.3, seed, r), 0.0, 0.8)
    b.add(leather, 0.04, 0.5)
    b.add(air, 0.0, 0.45, "field", 0.1)
    return R.fade_out(b.render(), 0.08)


def stomp_plant(seed):
    r = K.rng(seed)
    punch = AA.rec(AA.pick(["impactPunch_heavy_001", "impactPunch_heavy_002", "impactPunch_heavy_003", "impactPunch_heavy_004"], r))
    soft = AA.rec(AA.pick(["impactSoft_heavy_000", "impactSoft_heavy_002", "impactSoft_heavy_004"], r))
    rubble = R.unit(K.highpass(K.crackle(K.ns(0.5), 90.0, seed + 7, 0.9, 0.8), 900, 1)) * K.perc(K.ns(0.5), 0.01, 0.35)
    layers = [
        (AA.trim(AA.shift(punch, r.uniform(0.62, 0.72)), 0.45), 0.0, 1.0),
        (AA.trim(K.lowpass(AA.shift(soft, r.uniform(0.6, 0.7)), 600, 2), 0.4), 0.002, 0.9),
        (R.crack(seed + 3, r.uniform(0.45, 0.6), 0.3), 0.003, 0.5),
        (AA.sub(0.5, 85, 34, 0.26, drive=2.0), 0.0, 1.0),
        (rubble, 0.07, 0.3),
    ]
    hit = AA._blow(layers, 0.9, room="stone", wet=0.22, drive=2.0)
    n = len(hit)
    rumble = R.unit(K.lowpass(K.noise(n, "pink", seed + 9), 140, 2)) * K.perc(n, 0.02, 0.5)
    R.put(hit, rumble, 0.02, 0.35)
    return R.fade_out(hit, 0.15)


def beam_gather(seed):
    n = K.ns(0.42)
    t = K.tvec(n)
    f = 150 * np.exp(np.log(900 / 150) * np.minimum(1.0, t / 0.36))
    tone = R.unit(K.sine(f, n) + 0.5 * K.sine(f * 2.01, n)) * (t / t[-1]) ** 1.3
    riser = R.unit(R.sweep(K.noise(n, "pink", seed + 1), lambda s: 300 + 2600 * min(1.0, s / 0.36), 2.0)) * (t / t[-1]) ** 1.5
    tick = R.unit(K.bandpass(K.noise(K.ns(0.03), "white", seed + 2), 2600, 3)) * K.perc(K.ns(0.03), 0.0005, 0.02)
    b = R.Bus(0.5)
    b.add(tone, 0.0, 0.5, "field", 0.12)
    b.add(riser, 0.0, 0.5)
    b.add(tick, 0.36, 0.6)
    return R.fade_out(b.render(), 0.06)


def beam_loop(seed):
    r = K.rng(seed)
    n = K.ns(0.62)
    t = K.tvec(n)
    f0 = 98.0 * r.uniform(0.98, 1.02)
    hum = np.zeros(n)
    for i, (m, a) in enumerate(((1, 1.0), (2, 0.55), (3, 0.4), (5, 0.22), (8, 0.12))):
        hum += a * K.sine(f0 * m * (1 + 0.004 * np.sin(2 * np.pi * (0.9 + 0.3 * i) * t + i + seed)), n)
    fizz = R.unit(K.bandpass(K.noise(n, "white", seed + 1), 1800, 1.2)) * (0.5 + 0.5 * K.smooth_noise(n, 30.0, seed + 2)) * 0.5
    snap = R.unit(K.highpass(K.crackle(n, 40.0, seed + 3, 0.9, 0.8), 2500, 2)) * 0.4
    env = np.minimum(1.0, t / 0.07) * np.minimum(1.0, (t[-1] - t) / 0.12)
    return (R.unit(hum) * 0.8 + fizz * 0.35 + snap * 0.25) * env


def beam_end(seed):
    n = K.ns(0.75)
    t = K.tvec(n)
    f = 90 + 610 * np.exp(-t / 0.18)
    tone = R.unit(K.sine(f, n) + 0.4 * K.sine(f * 2.0, n)) * K.perc(n, 0.004, 0.4)
    air = R.unit(R.sweep(K.noise(n, "pink", seed + 1), lambda s: 200 + 2800 * np.exp(-s / 0.2), 1.6)) * K.perc(n, 0.004, 0.35)
    sparks = R.unit(K.highpass(K.crackle(n, 70.0, seed + 2, 0.95, 0.85), 2200, 2)) * K.perc(n, 0.01, 0.4)
    b = R.Bus(0.8)
    b.add(tone, 0.0, 0.6, "field", 0.15)
    b.add(air, 0.0, 0.5)
    b.add(sparks, 0.02, 0.35)
    b.add(AA.sub(0.3, 70, 38, 0.14, drive=1.4), 0.0, 0.4)
    return R.fade_out(b.render(), 0.1)


# event: (render, variants, rms dBFS in the power window, attenuation blocks, crest dB)
EVENTS = OrderedDict([
    ("stomp_lift", (stomp_lift, 3, -19.0, 16, 12.0)),
    ("stomp_plant", (stomp_plant, 4, -13.0, 28, 12.0)),
    ("beam_gather", (beam_gather, 2, -18.0, 16, 12.0)),
    ("beam_loop", (beam_loop, 3, -19.0, 18, 14.0)),
    ("beam_end", (beam_end, 2, -17.0, 18, 12.0)),
])


def seed_of(event, k):
    return 9700 + list(EVENTS).index(event) * 61 + k * 7


def render(event, k):
    fn, _, rms, _, crest = EVENTS[event]
    tgt, tol, ceil, _ = TARGETS["power"]
    assert abs(rms - tgt) <= tol, f"{event}: {rms} outside the power window"
    x = AA.tame(K.mono(fn(seed_of(event, k))), crest)
    return AA.master(x, rms, ceil - 0.7)


def files_of(event):
    return [os.path.join(SND, f"power_{event}_{k}.ogg") for k in range(EVENTS[event][1])]


def check(event, files):
    out = []
    tgt, tol, ceil, _ = TARGETS["power"]
    for f in files:
        x = K.read_audio(f)
        x = K.mono(x) if x.ndim > 1 else x
        rms, tp = K.gated_rms(x), K.true_peak(x)
        if abs(rms - EVENTS[event][2]) > 2.5:
            out.append(f"{os.path.basename(f)}: RMS {rms:.1f} (aimed {EVENTS[event][2]})")
        if tp > ceil + 0.05:
            out.append(f"{os.path.basename(f)}: true peak {tp:.1f} over {ceil}")
        if abs(x[0]) > 0.02 or abs(x[-1]) > 0.02:
            out.append(f"{os.path.basename(f)}: a click at an end")
    return out


def write_json():
    path = os.path.join(MERGE, "sounds.json")
    cur = json.load(open(path)) if os.path.exists(path) else {}
    for event, (_, variants, _, att, _) in EVENTS.items():
        entries = []
        for k in range(variants):
            entries.append(OrderedDict(name=f"awaken:powers/power_{event}_{k}", attenuation_distance=att))
        cur["power." + event] = OrderedDict(sounds=entries)
    os.makedirs(MERGE, exist_ok=True)
    with open(path, "w") as f:
        json.dump(cur, f, indent=2)
        f.write("\n")
    print(f"events: {len(EVENTS)} in merge/powers")


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    os.makedirs(SND, exist_ok=True)
    if "--json" not in argv:
        for event, (_, variants, *_) in EVENTS.items():
            if only and not any(event.startswith(p) for p in only):
                continue
            for k in range(variants):
                y, took = render(event, k)
                path = os.path.join(SND, f"power_{event}_{k}.ogg")
                K.write_ogg(path, y, TARGETS["power"][3], None)
                ceil = TARGETS["power"][2]
                for _ in range(3):
                    tp = K.true_peak(K.read_audio(path))
                    if tp <= ceil - 0.05:
                        break
                    y = y * K.dbamp(ceil - 0.25 - tp)
                    K.write_ogg(path, y, TARGETS["power"][3], None)
                print(f"  power_{event}_{k:<2d} {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  limiter {took:4.1f} dB  {os.path.getsize(path) // 1024} KB")
    problems = []
    for event in EVENTS:
        if only and not any(event.startswith(p) for p in only):
            continue
        problems += check(event, [f for f in files_of(event) if os.path.exists(f)])
    for p in problems:
        print("  CHECK " + p)
    write_json()
    print(f"{len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
