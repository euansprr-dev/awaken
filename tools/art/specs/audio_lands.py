"""The living land's wind beds (docs/modules/lands.md), rendered by tools/art/audio_build.py like every ambience bed:
mono seamless loops, mastered to the ambience loudness. The client (terrain.client.LeafRustle) plays them around the
listener and drives their volume from the wind (dev.awaken.terrain.Wind) and the canopy overhead; so the files
themselves stay even, and the gusts you hear are the game's.

  amb_lands_rustle     leaves in the wind: thousands of tiny leaf ticks over a soft shush, a little air underneath
  amb_lands_wind_high  deep wind high up: a slow-moving low howl, a rumble, a faint whistle in the gusts
  amb_glass_steppe     Glass Steppe's biome bed (minecraft:audio/ambient_sounds loop): warm wind, dry grass, glass ticking
  amb_shattered_highlands  Shattered Highlands' bed: high cold wind, the faults' low hum, rock crackling
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


def glass_steppe(seed):
    """Glass Steppe: a warm open wind over dry grass (a soft low swell and a dry hiss), and now and then the glass
    ticking and ringing faintly as it warms: tiny struck-glass notes, high and far apart, a thin shimmer over them."""
    L = 14.0
    n = K.ns(L)
    r = K.rng(seed)
    wind = _norm(K.lowpass(K.noise(n, "pink", seed + 1), 520, 2)) * (0.55 + 0.45 * K.smooth_noise(n, 0.12, seed + 2))
    hiss = _norm(K.bandpass(K.noise(n, "pink", seed + 3), 4200, 0.8)) * (0.5 + 0.5 * (0.5 + 0.5 * K.smooth_noise(n, 0.35, seed + 4)))
    ring = np.zeros(n)
    notes = [K.hz(K.midi(m)) for m in ("E6", "G#6", "B6", "C#7", "E7", "G#7")]
    for k in range(26):
        t0 = r.uniform(0, L - 1.6)
        f = notes[int(r.integers(len(notes)))] * 2 ** (r.normal(0, 8) / 1200)
        g = K.glass(f, K.ns(1.5), bright=0.35, t60=r.uniform(0.5, 1.4), seed=seed + 20 + k, hit=r.uniform(0.3, 1.0))
        s = K.ns(t0)
        ring[s:s + len(g)] += g[: n - s] * r.uniform(0.1, 1.0) ** 2
    ticks = np.zeros(n)
    for h in np.nonzero(r.random(n) < 7.0 / K.SR)[0]:
        Lt = min(K.ns(0.006), n - h)
        ticks[h:h + Lt] += r.standard_normal(Lt) * np.exp(-np.arange(Lt) / (Lt * 0.25)) * r.uniform(0.2, 1.0)
    ticks = _norm(K.highpass(ticks, 3000, 2))
    t = K.tvec(n)
    shimmer = (np.sin(2 * np.pi * 5274 * t) + 0.6 * np.sin(2 * np.pi * 6645 * t + 1.3)) * (0.5 + 0.5 * K.smooth_noise(n, 0.2, seed + 5)) ** 2
    x = 0.46 * wind + 0.26 * hiss + 0.2 * _norm(ring) + 0.06 * ticks + 0.012 * shimmer
    return K.loop_seamless(K.lowpass(x, 9500, 2), 1.5)


def shattered_highlands(seed):
    """Shattered Highlands: a cold high wind moaning over rock, under it the faults' low hum (two close tones beating
    slowly, a soft overtone), and the odd crackle and tick of rock splitting along the light."""
    L = 14.0
    n = K.ns(L)
    r = K.rng(seed)
    cen = 300 * 2 ** (0.7 * K.smooth_noise(n, 0.15, seed + 1))
    howl = _norm(_sweep(K.noise(n, "pink", seed + 2), cen, 1.8)) * (0.55 + 0.45 * (0.5 + 0.5 * K.smooth_noise(n, 0.2, seed + 3)))
    air = _norm(K.highpass(K.noise(n, "pink", seed + 4), 1800, 2)) * (0.4 + 0.6 * (0.5 + 0.5 * K.smooth_noise(n, 0.3, seed + 5)))
    t = K.tvec(n)
    # 55 Hz and a twin a fraction off: a hum that breathes; exact cycles in the loop so it joins
    f1, f2 = 55.0, 55.0 + 2.0 / L
    hum = np.sin(2 * np.pi * f1 * t) + 0.8 * np.sin(2 * np.pi * f2 * t) + 0.3 * np.sin(2 * np.pi * 165.0 * t + 0.7)
    hum *= 0.7 + 0.3 * K.smooth_noise(n, 0.1, seed + 6)
    crack = K.crackle(n, rate=5.0, seed=seed + 7, bright=0.35, burstiness=0.8)
    knock = np.zeros(n)
    for k in range(5):
        t0 = r.uniform(0, L - 1.0)
        g = K.bandnoise(K.ns(0.08), r.uniform(500, 1400), 2.0, seed + 30 + k) * K.perc(K.ns(0.08), 0.001, 0.06)
        s = K.ns(t0)
        knock[s:s + len(g)] += g[: n - s] * r.uniform(0.3, 1.0)
    x = 0.48 * howl + 0.16 * air + 0.2 * _norm(hum) + 0.09 * _norm(crack) + 0.07 * _norm(knock)
    return K.loop_seamless(K.lowpass(x, 8000, 2), 2.0)


def sounds(Snd):
    return [
        Snd("amb_lands_rustle", rustle, rms=-28.0, seed=41, mono=True),
        Snd("amb_lands_wind_high", wind_high, rms=-28.0, seed=42, mono=True),
        Snd("amb_glass_steppe", glass_steppe, rms=-29.0, seed=43, mono=True),
        Snd("amb_shattered_highlands", shattered_highlands, rms=-28.0, seed=44, mono=True),
    ]


def events(Evt):
    return [
        Evt("amb.lands_rustle", "Leaves rustle in the wind", files=["amb_lands_rustle"], folder="terrain"),
        Evt("amb.lands_wind_high", "Wind blows high", files=["amb_lands_wind_high"], folder="terrain"),
        Evt("amb.glass_steppe", "Glass ticks in the heat", files=["amb_glass_steppe"], folder="terrain"),
        Evt("amb.shattered_highlands", "The faults hum", files=["amb_shattered_highlands"], folder="terrain"),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
