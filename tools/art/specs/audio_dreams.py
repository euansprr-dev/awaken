"""A trial dream's own sounds (SPEC §9.3, §9.6; docs/dreams/PLAN.md §8), rendered by tools/art/audio_build.py. Soft,
pearl and gold like the dream's look: nothing here is vanilla. All mono: the server plays them at the dreamer (and the
clock's tick is the client's own, in the UI channel).

  dream_catch   the dream catching your fall: an airy swoop up and two slow heartbeats under it
  dream_tick    the clock's last ten seconds: one quiet tick of glass and wood (the client slows them as time runs out)
  dream_ring    a ring passed: a small gold chime (pitched up ring by ring in the game)
  dream_won     the end reached: a warm rising shimmer that opens out
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _place(out, x, at):
    s = K.ns(at)
    e = min(len(out), s + len(x))
    out[s:e] += x[: e - s]


def _beat(seed):
    """One heartbeat: a low, round lub and a softer dub."""
    x = np.zeros(K.ns(0.5))
    _place(x, K.sub_boom(K.ns(0.22), f0=72.0, f1=48.0, t60=0.14, seed=seed), 0.0)
    _place(x, K.sub_boom(K.ns(0.2), f0=66.0, f1=44.0, t60=0.12, seed=seed + 1) * 0.6, 0.17)
    return K.lowpass(x, 240, 2)


def catch(seed):
    L = 1.9
    n = K.ns(L)
    t = np.arange(n) / K.SR
    # the swoop: breathy noise whose band sweeps down then lifts, as if caught mid-fall and borne up
    air = K.noise(n, "pink", seed)
    sweep = K.line(n, [(0, 1800.0), (0.25, 520.0), (0.55, 1500.0), (1.0, 900.0)])
    swoop = np.zeros(n)
    hop = K.ns(0.02)
    for s in range(0, n, hop):
        e = min(n, s + hop * 3)
        swoop[s:e] += K.bandpass(air[s:e], float(sweep[s]), 1.4) * np.hanning(e - s)
    swoop *= K.line(n, [(0, 0), (0.08, 1.0), (0.45, 0.7), (0.8, 0.15), (1.0, 0)])
    # a pearl shimmer riding the lift
    shimmer = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((1318.5, 0.5), (1975.5, 0.3), (2637.0, 0.15)))
    shimmer *= K.line(n, [(0, 0), (0.25, 0), (0.45, 0.35), (1.0, 0)])
    x = _norm(swoop) * 0.8 + shimmer * 0.12
    _place(x, _beat(seed + 5) * 1.6, 0.42)
    _place(x, _beat(seed + 7) * 1.2, 1.12)
    wet = K.convolve(x, K.reverb_ir(t60=1.6, predelay=0.03, bright=0.35, width=0.6, seed=seed + 9))[:n]
    return K.declick(_norm(x) + wet[:, 0] * 0.35)


def tick(seed):
    L = 0.45
    n = K.ns(L)
    # a glass tap over a dry wooden knock, very short, a little room
    glass = K.glass(2350.0, n, bright=0.35, t60=0.18, seed=seed) * 0.55
    knock = _norm(K.lowpass(K.noise(K.ns(0.03), "white", seed + 1), 1400, 2)) * K.perc(K.ns(0.03), 0.001, 0.02)
    x = np.zeros(n)
    _place(x, knock * 0.6, 0.0)
    x += glass
    wet = K.convolve(x, K.reverb_ir(t60=0.5, predelay=0.01, bright=0.4, width=0.4, seed=seed + 2))[:n]
    return K.declick(_norm(x) + wet[:, 0] * 0.25)


def ring(seed):
    L = 1.3
    n = K.ns(L)
    f = K.hz(K.midi("E6"))
    x = _norm(K.bell(f, n, t60=0.9, bright=0.25, seed=seed)) * 0.7
    x += _norm(K.glass(f * 1.5, n, bright=0.3, t60=0.6, seed=seed + 1)) * 0.3
    wet = K.convolve(x, K.reverb_ir(t60=1.4, predelay=0.02, bright=0.45, width=0.8, seed=seed + 3))[:n]
    return K.declick(_norm(x) + wet[:, 0] * 0.4)


def won(seed):
    L = 3.2
    n = K.ns(L)
    t = np.arange(n) / K.SR
    # a warm chord rising a fourth, gold on top, opening into the room
    x = np.zeros(n)
    for k, m in enumerate(("A3", "E4", "A4", "C#5", "E5")):
        f0 = K.hz(K.midi(m))
        bend = K.line(n, [(0, 0.75), (0.35, 1.0), (1.0, 1.0)])
        ph = 2 * np.pi * np.cumsum(f0 * bend) / K.SR
        env = K.line(n, [(0, 0), (0.08 + k * 0.04, 0), (0.3 + k * 0.03, 1), (0.7, 0.6), (1.0, 0)])
        x += (np.sin(ph) + 0.25 * np.sin(2 * ph + 0.3)) * env * (1.0 - k * 0.12)
    sparkle = _norm(K.highpass(K.noise(n, "white", seed + 4), 6000, 2)) * K.line(n, [(0, 0), (0.3, 0.05), (0.6, 0.02), (1.0, 0)])
    x = _norm(K.lowpass(x, 3200, 2)) + sparkle
    wet = K.convolve(x, K.reverb_ir(t60=2.6, predelay=0.04, bright=0.4, width=1.0, seed=seed + 5))[:n]
    return K.declick(_norm(x) + wet[:, 0] * 0.5)


def sounds(Snd):
    return [
        Snd("dream_catch", catch, rms=-22.0, seed=900, mono=True),
        Snd("dream_tick", tick, rms=-24.0, seed=901, mono=True),
        Snd("dream_ring", ring, rms=-22.0, seed=902, mono=True),
        Snd("dream_won", won, rms=-21.0, seed=903, mono=True),
    ]


def events(Evt):
    return [
        Evt("dream.catch", "The dream catches you", files=["dream_catch"], folder="lucidium"),
        Evt("dream.tick", "The dream's clock ticks", files=["dream_tick"], folder="lucidium"),
        Evt("dream.ring", "A ring of light passes", files=["dream_ring"], folder="lucidium"),
        Evt("dream.won", "The dream opens", files=["dream_won"], folder="lucidium"),
    ]
