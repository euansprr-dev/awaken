"""CARILLON's bells: the Choir's great bronze bell, a district tower's bell, the Watch's alarm bell and the crier's handbell.
Synthesised from scratch with tools/art/audiokit.py (numpy only, seeded), mono (they are played in the world), mastered to
the sfx loudness of audio_mix.py, written to src/main/resources/assets/awaken/sounds/city/<name>.ogg.

    python3 tools/art/specs/audio_city.py            render the four files and report their levels
    python3 tools/art/specs/audio_city.py sheet OUT  also a spectrogram sheet (tools/art/audio_sheet.py) of the four

The events city.bell.hour / .district / .alarm and city.handbell are registered in src/main/merge/citizens/assets/awaken/
sounds.json (written by the builder, with their subtitles); nothing is written here but the .ogg files.

A bronze bell is not a harmonic instrument: its partials sit at hum 0.5, prime 1, tierce 1.19 (the minor third that makes
a bell sound like a bell), quint 1.5, nominal 2, then 2.5, 2.67, 3, 4... of the prime, the low ones ringing for seconds, the
high ones gone in a fraction of a second. Each partial is a pair of close frequencies, so it beats slowly (the shimmer).
- bell_hour:     the great bell, one stroke: hum D2, prime D3, ~8 s, a dull heavy clapper blow under a bronze ring
- bell_district: a tower bell, one stroke: prime A3, ~4 s, brighter and lighter than the great bell
- bell_alarm:    a small fast bell, one hard stroke: prime ~F6, ~1 s, tight (it is played many times a second)
- handbell:      the crier's brass handbell, a quick double ring: ~1 s
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/city")


def build(out):  # textures only in build_all; audio is rendered on demand (main)
    pass


# ---- the bell ---------------------------------------------------------------------------------------------------------

# (ratio to the prime, amplitude, t60 as a fraction of the prime's, beat in Hz as a fraction of the prime's beat)
GREAT = [(0.5, 0.62, 1.35, 0.6), (1.0, 1.0, 1.0, 1.0), (1.19, 0.78, 0.78, 1.5), (1.5, 0.46, 0.62, 2.0),
         (2.0, 0.92, 0.55, 2.6), (2.5, 0.34, 0.38, 3.2), (2.67, 0.30, 0.33, 3.8), (3.0, 0.20, 0.26, 4.4),
         (4.0, 0.15, 0.17, 5.1), (5.4, 0.08, 0.11, 6.0), (6.9, 0.05, 0.075, 7.0), (8.6, 0.03, 0.05, 8.0)]

SMALL = [(1.0, 1.0, 1.0, 1.0), (2.0, 0.85, 0.82, 1.7), (2.42, 0.45, 0.5, 2.3), (3.0, 0.38, 0.42, 3.0),
         (3.75, 0.30, 0.3, 3.6), (4.65, 0.22, 0.22, 4.2), (5.95, 0.14, 0.16, 5.0), (7.2, 0.09, 0.1, 5.8)]

BRASS = [(1.0, 1.0, 1.0, 1.0), (2.01, 0.7, 0.75, 1.9), (2.76, 0.5, 0.55, 2.5), (3.98, 0.34, 0.4, 3.3), (5.2, 0.24, 0.28, 4.1),
         (6.7, 0.15, 0.17, 5.0), (8.3, 0.09, 0.1, 6.0)]


def partials(n, prime, parts, t60, beat, seed, bright=1.0):
    """A modal bell: every partial a damped pair of sines that beat against each other."""
    r = K.rng(seed)
    t = K.tvec(n)
    x = np.zeros(n)
    for ratio, amp, dt, db in parts:
        f = prime * ratio
        if f >= K.TOP:
            continue
        a = amp * (bright ** max(0.0, np.log2(max(ratio, 1.0))))
        env = 10.0 ** (-3.0 * t / (t60 * dt))
        ph, ph2 = r.uniform(0, 2 * np.pi, 2)
        bt = beat * db * r.uniform(0.85, 1.15)
        x += a * env * np.sin(2 * np.pi * f * t + ph)
        x += a * 0.8 * env * np.sin(2 * np.pi * (f + bt) * t + ph2)
    on = K.ns(0.0015)
    x[:on] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(on) / on)
    return x


def at(n, t, seg):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def clapper(n, fc, t60, seed, thump=0.0, thump_f=(150, 80)):
    """The strike: a band of noise (the clapper's clang), under it a falling low sine (the weight of the blow)."""
    m = K.ns(min(0.25, t60 * 6 + 0.05))
    x = K.bandnoise(m, fc, 0.8, seed) * K.perc(m, 0.0004, t60)
    if thump:
        x = x + thump * K.sine(K.glide(m, thump_f[0], thump_f[1]), m) * K.perc(m, 0.002, 0.14)
    return at(n, 0.0, x)


def tower(x, t60, wet, seed):
    """Open air round a tower: a long soft tail and an early slap."""
    ir = K.reverb_ir(t60=t60, predelay=0.014, bright=0.35, width=0.5, early=0.6, size=1.6, seed=seed)
    w = K.mono(K.convolve(x, ir))[:len(x)]
    return x + wet * w


def fade(x, last):
    n = len(x)
    k = K.ns(last)
    g = np.ones(n)
    g[n - k:] = 0.5 + 0.5 * np.cos(np.pi * np.arange(k) / k)
    return x * g


# ---- the sounds -------------------------------------------------------------------------------------------------------

def bell_hour(seed):
    n = K.ns(8.6)
    prime = K.hz(K.midi("D3"))              # hum D2
    x = partials(n, prime, GREAT, 7.6, 1.1, seed, bright=0.95)
    x += clapper(n, 700, 0.05, seed + 1, thump=0.9, thump_f=(190, 85)) * 0.42
    x += clapper(n, 2600, 0.03, seed + 2) * 0.1
    x = tower(x, 2.4, 0.2, seed + 3)
    return K.lowpass(fade(x, 1.8), 9000, 2)


def bell_district(seed):
    n = K.ns(4.4)
    prime = K.hz(K.midi("A3"))
    x = partials(n, prime, GREAT, 3.7, 1.9, seed, bright=1.05)
    x += clapper(n, 1500, 0.04, seed + 1, thump=0.5, thump_f=(240, 130)) * 0.38
    x += clapper(n, 3600, 0.025, seed + 2) * 0.12
    x = tower(x, 1.6, 0.16, seed + 3)
    return K.lowpass(fade(x, 1.2), 10500, 2)


def bell_alarm(seed):
    n = K.ns(1.05)
    prime = K.hz(K.midi("F6"))
    x = partials(n, prime, SMALL, 0.62, 1.4, seed, bright=1.15)
    x += clapper(n, 4200, 0.012, seed + 1) * 0.3
    x += clapper(n, 1800, 0.02, seed + 2) * 0.12
    return K.lowpass(fade(x, 0.25), 11500, 2)


def handbell(seed):
    n = K.ns(1.15)
    p1, p2 = K.hz(K.midi("C6")), K.hz(K.midi("C6")) * 1.0113
    x = partials(n, p1, BRASS, 0.78, 1.8, seed, bright=1.0)
    x += at(n, 0.19, partials(n - K.ns(0.19), p2, BRASS, 0.7, 1.6, seed + 5, bright=1.0)) * 0.82
    # the clapper's chatter between and after the strokes, and the two clangs themselves
    x += clapper(n, 3300, 0.012, seed + 1) * 0.22
    x += at(n, 0.19, clapper(n, 3000, 0.012, seed + 2)) * 0.18
    for k, tt in enumerate((0.07, 0.1, 0.27)):
        x += at(n, tt, clapper(n, 3800 + 300 * k, 0.006, seed + 6 + k)) * 0.05
    x = tower(x, 0.5, 0.1, seed + 3)
    return K.lowpass(fade(x, 0.3), 11000, 2)


SOUNDS_SPEC = [
    # file, render, rms (dBFS; sfx = -20 +- 4)
    ("bell_hour", bell_hour, -19.0),
    ("bell_district", bell_district, -20.0),
    ("bell_alarm", bell_alarm, -20.5),
    ("handbell", handbell, -22.5),
]


def main(argv):
    tgt, tol, ceil, q = TARGETS["sfx"]
    for name, fn, rms in SOUNDS_SPEC:
        assert abs(rms - tgt) <= tol, name
        x = K.mono(fn(sum(map(ord, name))))
        y = K.master(x, rms, ceil - 0.7)
        path = os.path.join(SOUNDS, name + ".ogg")
        K.write_ogg(path, y, q, None)
        print(f"  {name:14s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  "
              f"{os.path.getsize(path) // 1024} KB")
    if len(argv) >= 2 and argv[0] == "sheet":
        import subprocess
        subprocess.run([sys.executable, os.path.join(ROOT, "tools/art/audio_sheet.py"), argv[1], os.path.join(SOUNDS, "*.ogg"), "--cols", "2"],
                       check=False)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
