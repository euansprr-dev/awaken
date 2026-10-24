"""The living land's wind beds (docs/modules/lands.md), rendered by tools/art/audio_build.py like every ambience bed:
mono seamless loops, mastered to the ambience loudness. The client (terrain.client.LeafRustle) plays them around the
listener and drives their volume from the wind (dev.awaken.terrain.Wind) and the canopy overhead; so the files
themselves stay even, and the gusts you hear are the game's.

  amb_lands_rustle     leaves in the wind: thousands of tiny leaf ticks over a soft shush, a little air underneath
  amb_lands_wind_high  deep wind high up: a slow-moving low howl, a rumble, a faint whistle in the gusts
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _ticks(n, rate, seed, lo, hi, dur=(0.004, 0.035)):
    """Leaf ticks: very short filtered noise bursts at a clumpy density (a canopy is never even)."""
    r = K.rng(seed)
    out = np.zeros(n)
    dens = np.clip(0.55 + 0.7 * K.smooth_noise(n, 0.9, seed + 1), 0.05, None) ** 1.5
    hits = np.nonzero(r.random(n) < rate / K.SR * dens)[0]
    for h in hits:
        L = min(int(r.uniform(*dur) * K.SR), n - h)
        if L <= 4:
            continue
        seg = r.standard_normal(L) * np.exp(-np.arange(L) / (L * r.uniform(0.2, 0.45)))
        out[h:h + L] += seg * r.uniform(0.15, 1.0) ** 2
    return _norm(K.lowpass(K.highpass(out, lo, 2), hi, 2))


def rustle(seed):
    L = 12.0
    n = K.ns(L)
    ticks = _ticks(n, 900, seed, 1400, 7500)
    # the shush: leaves brushing, a band of noise that swells and thins with the canopy's motion
    shush = _norm(K.bandpass(K.noise(n, "pink", seed + 2), 3200, 0.7))
    shush *= 0.55 + 0.45 * (0.5 + 0.5 * K.smooth_noise(n, 0.45, seed + 3))
    # moving air under it
    air = _norm(K.lowpass(K.noise(n, "pink", seed + 4), 420, 2)) * (0.6 + 0.4 * K.smooth_noise(n, 0.2, seed + 5))
    x = 0.42 * ticks + 0.38 * shush + 0.2 * air
    return K.loop_seamless(K.lowpass(x, 9000, 2), 1.5)


def wind_high(seed):
    L = 14.0
    n = K.ns(L)
    # a low howl: pink noise through a band that wanders between ~170 and ~430 Hz
    cen = 270 * 2 ** (0.65 * K.smooth_noise(n, 0.18, seed + 1))
    howl = _norm(_sweep(K.noise(n, "pink", seed + 2), cen, 1.6))
    howl *= 0.6 + 0.4 * (0.5 + 0.5 * K.smooth_noise(n, 0.25, seed + 3))
    rumble = _norm(K.lowpass(K.noise(n, "brown", seed + 4), 110, 2))
    # a faint whistle where the gust peaks
    wcen = 760 * 2 ** (0.3 * K.smooth_noise(n, 0.3, seed + 5))
    whistle = _norm(_sweep(K.noise(n, "white", seed + 6), wcen, 9.0)) * np.clip(K.smooth_noise(n, 0.15, seed + 7), 0, 1) ** 2
    x = 0.6 * howl + 0.32 * rumble + 0.06 * whistle
    return K.loop_seamless(K.lowpass(x, 2400, 2), 2.0)


def _sweep(x, centre, q):
    """Band-pass with a moving centre (centre: per-sample Hz)."""
    n = len(x)

    def resp(tsec, f):
        c = centre[min(n - 1, max(0, int(tsec * K.SR)))]
        return K.bp_resp(c, q)(f)
    return K.stft_filter(x, resp)


def sounds(Snd):
    return [
        Snd("amb_lands_rustle", rustle, rms=-28.0, seed=41, mono=True),
        Snd("amb_lands_wind_high", wind_high, rms=-28.0, seed=42, mono=True),
    ]


def events(Evt):
    return [
        Evt("amb.lands_rustle", "Leaves rustle in the wind", files=["amb_lands_rustle"], folder="terrain"),
        Evt("amb.lands_wind_high", "Wind blows high", files=["amb_lands_wind_high"], folder="terrain"),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
