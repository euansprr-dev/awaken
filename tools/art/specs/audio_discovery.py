"""How AWAKEN tells you things (SPEC §26): the director's two cues and the rift tear (tools/art/audio_build.py renders
them).

  ui_find          the soft cue of a filed find: two glass notes a fifth apart, barely there (no toast came with it)
  ui_moment        a discovery moment (a first of its kind, a wonder, the first rift): a breath that opens into a bell
  sfx_rift_tear    a rift tearing open: air pulled in, a ripping crack with a sub boom under it, glass splinters and a
                   crackling tail; mono, heard to 128 blocks (rifts open 32-96 blocks away)
  amb_rift_rumble  the far layer under an open rift's crackle: a low uneven rumble with distant snaps, heard to 72
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _env(n, length, points):
    """A piecewise-linear envelope through (seconds, value) points."""
    return K.line(n, [(t / length, v) for t, v in points])


def _place(out, x, at):
    s = K.ns(at)
    e = min(len(out), s + len(x))
    out[s:e] += x[: e - s]


# ---- the director's cues -------------------------------------------------------------------------------------------

def find(seed):
    L = 1.6
    n = K.ns(L)
    x = np.zeros(n)
    for at, m, v in ((0.0, "E6", 0.55), (0.11, "B6", 0.42)):
        g = K.glass(K.hz(K.midi(m)), K.ns(L - at), bright=0.4, t60=0.9, seed=seed + int(at * 100), hit=0.6) * v
        _place(x, g, at)
    air = K.highpass(K.bandpass(K.noise(n, "pink", seed + 3), 6000, 0.8), 2500, 2)
    x += _norm(air) * K.perc(n, 0.03, 0.35) * 0.05
    wet = K.convolve(x, K.reverb_ir(t60=1.4, predelay=0.012, bright=0.55, width=0.8, seed=seed + 4))[:n]
    return K.declick(K.stereo(x) + wet * 0.5)


def moment(seed):
    L = 3.6
    n = K.ns(L)
    t = K.tvec(n)
    # a breath that rises into the chord (0..0.55 s), then three bell notes, low to high, held in a soft pad
    breath = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 1800, 0.7)) * _env(n, L, [(0, 0), (0.45, 1), (0.7, 0.25), (L, 0)]) * 0.18
    x = breath
    for at, m, v in ((0.5, "D5", 0.6), (0.62, "A5", 0.5), (0.78, "F#6", 0.45)):
        b = K.bell(K.hz(K.midi(m)), K.ns(L - at), t60=2.6, bright=0.45, seed=seed + int(at * 100)) * v
        _place(x, b, at)
    pad = K.pad(K.hz(K.midi("D4")), n, voices=5, seed=seed + 7, bright=0.25) * _env(n, L, [(0, 0), (0.5, 0), (1.0, 0.22), (2.6, 0.14), (L, 0)])
    x = x + pad
    sparkle = np.zeros(n)
    r = K.rng(seed + 9)
    for k in range(9):
        f = K.hz(K.midi(("A6", "D7", "E7", "F#7", "A7")[int(r.integers(5))]))
        g = K.glass(f, K.ns(0.6), bright=0.5, t60=0.4, seed=seed + 20 + k, hit=0.4) * r.uniform(0.05, 0.14)
        _place(sparkle, g, 0.7 + k * 0.09 + r.uniform(0, 0.05))
    x += sparkle
    wet = K.convolve(x, K.reverb_ir(t60=2.4, predelay=0.02, bright=0.5, width=1.0, seed=seed + 5))[:n]
    return K.declick(K.stereo(x) * 0.8 + wet * 0.45)


# ---- the rift ------------------------------------------------------------------------------------------------------

def rift_tear(seed):
    L = 5.0
    n = K.ns(L)
    x = np.zeros(n)
    # air pulled toward the crack: a band of noise sweeping up into the moment it rips (0 .. 0.42 s)
    pre = K.ns(0.42)
    u = np.arange(pre) / pre
    inhale = K.noise(pre, "pink", seed + 1)
    cen = 300 * (2400 / 300) ** (u ** 1.6)
    inhale = _norm(K.stft_filter(inhale, lambda tt, f: K.bp_resp(cen[min(pre - 1, int(tt * K.SR))], 2.0)(f))) * u ** 2.2 * 0.5
    x[:pre] += inhale
    # the rip: broadband noise through a band that falls fast, rough and torn (uneven amplitude), 0.42 .. 1.6 s
    rn = K.ns(1.2)
    v = np.arange(rn) / rn
    rip = K.noise(rn, "white", seed + 2)
    rc = 3800 * (180 / 3800) ** (v ** 0.6)
    rip = _norm(K.stft_filter(rip, lambda tt, f: K.bp_resp(rc[min(rn - 1, int(tt * K.SR))], 1.3)(f)))
    rough = 0.55 + 0.45 * np.abs(K.smooth_noise(rn, 38, seed + 3))
    rip *= rough * K.perc(rn, 0.004, 0.9)
    _place(x, rip * 0.9, 0.42)
    crack = K.crackle(K.ns(3.2), rate=55, seed=seed + 4, bright=0.75, burstiness=0.9) * K.perc(K.ns(3.2), 0.002, 1.6)
    _place(x, crack * 0.55, 0.42)
    # the weight under it: a sub boom and a low groan that slides down
    boom = K.sub_boom(K.ns(3.0), f0=62.0, f1=27.0, t60=2.2, seed=seed + 5)
    _place(x, _norm(boom) * 0.95, 0.40)
    groan = K.sine(K.glide(K.ns(2.4), 110.0, 52.0), K.ns(2.4)) * K.perc(K.ns(2.4), 0.05, 1.8)
    _place(x, groan * 0.28, 0.45)
    # glass splinters as reality cracks (inharmonic, high)
    r = K.rng(seed + 6)
    for k in range(7):
        f = r.uniform(1900, 5200)
        g = K.glass(f, K.ns(0.8), bright=0.7, t60=0.5, seed=seed + 30 + k, hit=1.0) * r.uniform(0.08, 0.2)
        _place(x, g, 0.44 + r.uniform(0, 0.3))
    # the crackling tail and a dark room so it sounds far and big
    tail = K.crackle(K.ns(3.6), rate=14, seed=seed + 7, bright=0.5, burstiness=0.8) * K.line(K.ns(3.6), [(0, 0.5), (1, 0)])
    _place(x, tail * 0.3, 1.3)
    x = K.lowpass(x, 11000, 2)
    wet = K.mono(K.convolve(x, K.reverb_ir(t60=2.8, predelay=0.03, bright=0.35, width=0.5, size=1.4, seed=seed + 8)))
    return K.declick(x + wet[: len(x)] * 0.35)


def rift_rumble(seed):
    L = 10.0
    n = K.ns(L)
    rumble = K.lowpass(K.noise(n, "brown", seed + 1), 90, 2)
    rumble = _norm(rumble) * (0.7 + 0.3 * K.smooth_noise(n, 0.6, seed + 2))
    d = K.drone(41.0, n, partials=(1, 2, 3, 5), amps=(0.6, 0.35, 0.18, 0.06), beat=0.5, seed=seed + 3)
    d = _norm(d) * (0.6 + 0.4 * K.smooth_noise(n, 0.35, seed + 4))
    snaps = K.crackle(n, rate=3.5, seed=seed + 5, bright=0.45, burstiness=0.95)
    snaps = K.lowpass(snaps, 3500, 2)
    x = 0.55 * rumble + 0.35 * d + 0.25 * _norm(snaps)
    return K.loop_seamless(x, 1.2)


def sounds(Snd):
    return [
        Snd("ui_find", find, rms=-26.5, seed=31),
        Snd("ui_moment", moment, rms=-24.0, seed=32),
        Snd("sfx_rift_tear", rift_tear, rms=-19.0, seed=33, mono=True),
        Snd("amb_rift_rumble", rift_rumble, rms=-27.0, seed=34, mono=True),
    ]


def events(Evt):
    return [
        Evt("ui.find", "Something found", files=["ui_find"]),
        Evt("ui.moment", "A discovery", files=["ui_moment"]),
        # re-voices the anomaly's own event (vanilla respawn anchor sounds before): heard as far as a rift can open
        Evt("anomaly.rift_open", "Something tears open", files=["sfx_rift_tear"], attenuation=128, folder="anomaly"),
        Evt("amb.rift_rumble", "A rift rumbles", files=["amb_rift_rumble"], attenuation=72),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
