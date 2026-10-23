"""AWAKEN audio kit: numpy-only synthesis, effects, mastering and Vorbis encoding for the mod's music, UI and ambience.

No samples: every voice is additive, modal, FM or wavetable synthesis with real envelopes, played into an algorithmic
(synthesised impulse response) convolution reverb. Everything is seeded, so a rebuild writes the same audio.
Specs live in tools/art/specs/audio_*.py; tools/art/audio_build.py renders them, tools/art/audio_mix.py measures them.

Conventions: mono signals are 1-D float64 arrays, stereo is (n, 2). Times in seconds, pitches as MIDI numbers or names
("D4", "Bb3", "F#5"). Oscillators that could alias are band-limited (harmonics stop below 0.45 SR, FM renders 2x
oversampled when its sidebands would fold).
"""
import os
import subprocess

import numpy as np

SR = 44100
NYQ = SR / 2
TOP = 0.45 * SR  # highest partial we let through (19.8 kHz)


# ---- basics ------------------------------------------------------------------------------------------------------

def ns(sec):
    return max(0, int(round(sec * SR)))


def tvec(n):
    return np.arange(n) / SR


def hz(m):
    return 440.0 * 2.0 ** ((np.asarray(m, dtype=float) - 69.0) / 12.0)


_STEP = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    """'D4' -> 62, 'Bb3' -> 58, 'F#5' -> 78. Numbers pass through."""
    if not isinstance(name, str):
        return name
    s = name.strip()
    m = _STEP[s[0].upper()]
    i = 1
    while i < len(s) and s[i] in "#b":
        m += 1 if s[i] == "#" else -1
        i += 1
    return m + 12 * (int(s[i:]) + 1)


def dbamp(db):
    return 10.0 ** (db / 20.0)


def ampdb(a):
    return 20.0 * np.log10(np.maximum(np.abs(a), 1e-12))


def rng(seed):
    return np.random.default_rng(int(round(abs(seed) * 7919)) if not isinstance(seed, (int, np.integer)) else abs(int(seed)))


def stereo(x):
    return x if x.ndim == 2 else np.stack([x, x], axis=1)


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=1)


def pad_to(x, n):
    if len(x) >= n:
        return x[:n]
    shape = (n - len(x),) + x.shape[1:]
    return np.concatenate([x, np.zeros(shape)])


SCALES = {
    "ionian": [0, 2, 4, 5, 7, 9, 11], "dorian": [0, 2, 3, 5, 7, 9, 10], "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11], "mixolydian": [0, 2, 4, 5, 7, 9, 10], "aeolian": [0, 2, 3, 5, 7, 8, 10],
    "locrian": [0, 1, 3, 5, 6, 8, 10], "hijaz": [0, 1, 4, 5, 7, 8, 10], "harmonic": [0, 2, 3, 5, 7, 8, 11],
    "penta_major": [0, 2, 4, 7, 9], "penta_minor": [0, 3, 5, 7, 10], "hirajoshi": [0, 2, 3, 7, 8],
    "in": [0, 1, 5, 7, 8], "whole": [0, 2, 4, 6, 8, 10],
}


def degree(root, scale, d):
    """Scale degree d (0-based, may be negative or past an octave) above MIDI root."""
    sc = SCALES[scale] if isinstance(scale, str) else scale
    o, i = divmod(d, len(sc))
    return midi(root) + 12 * o + sc[i]


# ---- envelopes ---------------------------------------------------------------------------------------------------

def _rise(k):
    """Raised-cosine ramp 0..1 over k samples (no corner at either end)."""
    if k <= 0:
        return np.zeros(0)
    return 0.5 - 0.5 * np.cos(np.pi * np.arange(k) / k)


def declick(x, a=0.002, r=0.004):
    """Fades the first a and last r seconds so nothing starts or stops on a non-zero sample."""
    x = x.copy()
    ka, kr = min(ns(a), len(x) // 2), min(ns(r), len(x) // 2)
    if ka:
        g = _rise(ka)
        x[:ka] *= g if x.ndim == 1 else g[:, None]
    if kr:
        g = _rise(kr)[::-1]
        x[-kr:] *= g if x.ndim == 1 else g[:, None]
    return x


def perc(n, attack=0.004, t60=1.0, hold=0.0, t60_late=None, late_mix=0.0):
    """Percussive envelope: smooth attack, optional hold, exponential decay reaching -60 dB after t60 seconds.
    With t60_late, a second slower decay (a piano's after-sound) is blended in at late_mix."""
    t = tvec(n)
    ka = min(ns(attack), n)
    e = np.ones(n)
    e[:ka] = _rise(ka)
    after = np.maximum(t - attack - hold, 0.0)
    dec = 10.0 ** (-3.0 * after / max(t60, 1e-4))
    if t60_late:
        dec = (1 - late_mix) * dec + late_mix * 10.0 ** (-3.0 * after / t60_late)
    return e * dec


def adsr(n, a=0.01, d=0.1, s=0.7, r=0.3, curve=1.0):
    """Gate-length envelope: attack a, decay d to sustain s, release r at the end of n (all cosine-smoothed)."""
    e = np.full(n, float(s))
    ka, kd, kr = ns(a), ns(d), ns(r)
    ka = min(ka, n)
    e[:ka] = _rise(ka) ** curve
    kd = min(kd, n - ka)
    if kd > 0:
        e[ka:ka + kd] = 1.0 - (1.0 - s) * _rise(kd)
    kr = min(kr, n)
    if kr > 0:
        e[n - kr:] *= _rise(kr)[::-1]
    return e


def swell(n, peak=0.5, shape=2.0):
    """Rises to 1 at fraction `peak` of n, then falls to 0 (sine-shaped, `shape` sharpens it)."""
    t = np.arange(n) / max(n - 1, 1)
    up = np.sin(0.5 * np.pi * np.clip(t / max(peak, 1e-6), 0, 1)) ** shape
    down = np.sin(0.5 * np.pi * np.clip((1 - t) / max(1 - peak, 1e-6), 0, 1)) ** shape
    return np.where(t <= peak, up, down)


def line(n, points):
    """Piecewise-linear curve through (fraction, value) points."""
    xs, ys = zip(*points)
    return np.interp(np.arange(n) / max(n - 1, 1), xs, ys)


def glide(n, f0, f1, curve="exp"):
    """Frequency sweep from f0 to f1 over n samples."""
    u = np.arange(n) / max(n - 1, 1)
    if curve == "exp":
        return f0 * (f1 / f0) ** u
    return f0 + (f1 - f0) * u


# ---- oscillators -------------------------------------------------------------------------------------------------

def phase(freq, n, phase0=0.0):
    """Phase in cycles for a constant or per-sample frequency."""
    if np.isscalar(freq):
        return phase0 + freq * np.arange(n) / SR
    f = np.asarray(freq, dtype=float)[:n]
    return phase0 + np.concatenate([[0.0], np.cumsum(f[:-1])]) / SR


def sine(freq, n, phase0=0.0):
    return np.sin(2 * np.pi * phase(freq, n, phase0))


def vibrato(n, rate=5.0, cents=12.0, delay=0.3, seed=0, drift=4.0):
    """Frequency multiplier: a vibrato that fades in after `delay`, plus slow random drift (cents)."""
    r = rng(seed)
    t = tvec(n)
    fade = np.clip((t - delay) / 0.6, 0, 1)
    wob = np.sin(2 * np.pi * rate * t + r.uniform(0, 6.28)) * (1 + 0.15 * np.sin(2 * np.pi * 0.37 * t))
    dr = smooth_noise(n, 1.5, seed + 1) * drift
    return 2.0 ** ((cents * fade * wob + dr) / 1200.0)


def smooth_noise(n, rate=2.0, seed=0):
    """Band-limited random curve in about -1..1 that changes `rate` times a second."""
    r = rng(seed)
    k = max(4, int(n / SR * rate) + 4)
    pts = r.uniform(-1, 1, k)
    xs = np.linspace(0, n, k)
    # cosine interpolation between random points
    idx = np.searchsorted(xs, np.arange(n), side="right") - 1
    idx = np.clip(idx, 0, k - 2)
    u = (np.arange(n) - xs[idx]) / (xs[idx + 1] - xs[idx])
    w = 0.5 - 0.5 * np.cos(np.pi * u)
    return pts[idx] * (1 - w) + pts[idx + 1] * w


_TABLE = 8192


def harmonic_table(amps, phases=None):
    """One cycle with harmonic k (1-based) at amps[k-1]."""
    k = np.arange(1, len(amps) + 1)
    x = np.arange(_TABLE) / _TABLE
    ph = np.zeros(len(amps)) if phases is None else phases
    tab = np.zeros(_TABLE)
    for i in range(len(amps)):
        if amps[i] != 0:
            tab += amps[i] * np.sin(2 * np.pi * k[i] * x + ph[i])
    return tab


def table_osc(tab, freq, n, phase0=0.0):
    """Reads a single-cycle table with linear interpolation at a constant or varying frequency."""
    p = phase(freq, n, phase0) % 1.0
    pos = p * _TABLE
    i0 = pos.astype(np.int64) % _TABLE
    fr = pos - np.floor(pos)
    return tab[i0] * (1 - fr) + tab[(i0 + 1) % _TABLE] * fr


def max_harmonics(fmax):
    return max(1, int(TOP / max(fmax, 1.0)))


def saw_amps(f0max, bright=1.0, tilt=1.0, cap=200, formants=None, f0=None):
    """Harmonic amplitudes of a softened sawtooth: 1/k^tilt with a gentle lowpass at bright*f-related corner, cut
    below Nyquist for the highest frequency the note reaches. `formants`: list of (Hz, width Hz, gain dB) body peaks."""
    K = min(cap, max_harmonics(f0max))
    k = np.arange(1, K + 1, dtype=float)
    base = f0 if f0 else f0max
    fk = k * base
    corner = 400.0 + 6000.0 * bright
    a = (1.0 / k ** tilt) / np.sqrt(1.0 + (fk / corner) ** 4)
    if formants:
        g = np.zeros(K)
        for (fc, bw, gdb) in formants:
            g += (dbamp(gdb) - 1.0) * np.exp(-0.5 * ((fk - fc) / bw) ** 2)
        a *= (1.0 + g)
    return a


def wt_voice(f0, n, amps, vib=None, phase0=0.0):
    """A wavetable voice at f0 (Hz) with an optional frequency-multiplier curve `vib`."""
    tab = harmonic_table(amps)
    freq = f0 if vib is None else f0 * vib
    return table_osc(tab, freq, n, phase0)


# ---- modal / additive instruments --------------------------------------------------------------------------------

def modal(f0, ratios, amps, t60s, n, attack=0.002, seed=0, detune_cents=0.0, pair=0.0):
    """Sum of damped sinusoids (bells, glass, bars). `pair` adds a twin of each mode `detune_cents` away at that
    level, so modes beat slowly like a real bell's split partials."""
    r = rng(seed)
    t = tvec(n)
    out = np.zeros(n)
    ka = max(1, ns(attack))
    on = np.ones(n)
    on[:ka] = _rise(ka)
    for ratio, a, t60 in zip(ratios, amps, t60s):
        f = f0 * ratio
        if f >= TOP or a == 0:
            continue
        env = 10.0 ** (-3.0 * t / t60)
        ph = r.uniform(0, 2 * np.pi)
        out += a * env * np.sin(2 * np.pi * f * t + ph)
        if pair:
            f2 = f * 2 ** (detune_cents / 1200.0)
            if f2 < TOP:
                out += a * pair * env * np.sin(2 * np.pi * f2 * t + ph + 1.3)
    return out * on


def glass(f0, n, bright=0.6, t60=2.5, seed=0, hit=1.0):
    """The System's glass: a struck glass bowl. Few, slightly inharmonic, split modes; the strike adds a short
    bright tick whose level is `hit`."""
    ratios = [1.0, 2.0 * 1.0018, 2.76, 4.07, 5.43, 6.95]
    amps = np.array([1.0, 0.32, 0.22 * bright, 0.12 * bright, 0.06 * bright, 0.03 * bright])
    t60s = [t60, t60 * 0.72, t60 * 0.45, t60 * 0.3, t60 * 0.2, t60 * 0.13]
    x = modal(f0, ratios, amps, t60s, n, attack=0.0015, seed=seed, detune_cents=1.6, pair=0.55)
    if hit > 0:
        tick = bandnoise(min(n, ns(0.03)), 3000 + 5000 * bright, 2.5, seed + 7) * perc(min(n, ns(0.03)), 0.0005, 0.02)
        x[:len(tick)] += tick * 0.25 * hit
    return x


def bell(f0, n, t60=6.0, bright=0.6, seed=0):
    """A tuned church-style bell (hum, prime, tierce, quint, nominal...) with split partials."""
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166, 5.433]
    amps = np.array([0.55, 1.0, 0.7, 0.35, 0.8, 0.3 * bright, 0.25 * bright, 0.25 * bright, 0.12 * bright, 0.07 * bright])
    t60s = [t60 * 1.3, t60, t60 * 0.8, t60 * 0.6, t60 * 0.55, t60 * 0.35, t60 * 0.3, t60 * 0.28, t60 * 0.18, t60 * 0.12]
    return modal(f0, ratios, amps, t60s, n, attack=0.002, seed=seed, detune_cents=2.5, pair=0.6)


def bar(f0, n, t60=1.2, bright=0.5, seed=0, wood=False):
    """A struck bar (marimba/vibes-like): free-free bar modes, tuned first overtone for marimba (4x)."""
    if wood:
        ratios, amps = [1.0, 3.93, 9.2], [1.0, 0.25 * bright, 0.05 * bright]
        t60s = [t60, t60 * 0.25, t60 * 0.08]
    else:
        ratios, amps = [1.0, 2.756, 5.404, 8.933], [1.0, 0.3 * bright, 0.12 * bright, 0.05 * bright]
        t60s = [t60, t60 * 0.4, t60 * 0.2, t60 * 0.1]
    return modal(f0, ratios, amps, t60s, n, attack=0.0015, seed=seed)


def piano(f0, n, vel=0.6, dur=None, seed=0, felt=0.6):
    """A felt piano: three slightly detuned strings with stretched (inharmonic) partials, two-stage decay, a soft
    hammer. `dur` is when the key lifts (dampers fall); `felt` darkens and softens the attack."""
    r = rng(seed)
    t = tvec(n)
    m = 69 + 12 * np.log2(f0 / 440.0)
    B = 0.00012 * 2 ** ((m - 40) / 14.0)            # inharmonicity grows up the keyboard
    T0 = float(np.clip(14.0 * 2 ** (-(m - 36) / 18.0), 1.2, 16.0))
    corner = (900 + 4200 * vel) * (1.25 - 0.8 * felt)   # hammer brightness
    out = np.zeros(n)
    K = 40
    det = [-0.9, 0.0, 0.8]
    for k in range(1, K + 1):
        fk = k * f0 * np.sqrt(1 + B * k * k)
        if fk >= TOP:
            break
        a = (1.0 / k ** 1.05) / np.sqrt(1 + (fk / corner) ** 3)
        if k > 1:
            a *= 0.55 + 0.45 * abs(np.sin(np.pi * k * 0.12))   # strike point at 1/8 of the string
        if a < 1e-4:
            continue
        t60 = T0 / (1 + 0.09 * (k - 1) + 0.0009 * fk / 10)
        env = 0.72 * 10 ** (-3 * t / (t60 * 0.28)) + 0.28 * 10 ** (-3 * t / t60)
        for d in det:
            f = fk * 2 ** ((d + r.normal(0, 0.15)) / 1200)
            out += a / 3 * env * np.sin(2 * np.pi * f * t + r.uniform(0, 6.28))
    # attack: felt softens the first milliseconds
    ka = ns(0.003 + 0.008 * felt)
    out[:ka] *= _rise(ka)
    # hammer thump (low, short)
    kh = min(n, ns(0.04))
    thump = lowpass(r.standard_normal(kh), 300 + 1200 * vel) * perc(kh, 0.001, 0.03)
    out[:kh] += thump * 0.03 * vel
    if dur is not None and dur * SR < n:
        k0 = ns(dur)
        damp = np.ones(n)
        damp[k0:] = 10 ** (-3 * (t[k0:] - t[k0]) / 0.35)
        out *= damp
    return out * (0.4 + 0.6 * vel)


def pluck(f0, n, bright=0.5, t60=2.5, pos=0.18, seed=0, metal=0.0):
    """A plucked string as additive modes: harmonics shaped by the pluck position, higher ones dying faster;
    `metal` adds stiffness (stretched partials) for a koto/guzheng edge."""
    r = rng(seed)
    t = tvec(n)
    out = np.zeros(n)
    B = 0.00004 + 0.0004 * metal
    for k in range(1, 48):
        fk = k * f0 * np.sqrt(1 + B * k * k)
        if fk >= TOP:
            break
        a = abs(np.sin(np.pi * k * pos)) / k ** (1.6 - 0.8 * bright)
        a /= np.sqrt(1 + (fk / (1500 + 7000 * bright)) ** 2)
        if a < 1e-4:
            continue
        tk = t60 / (1 + 0.35 * (k - 1) * (1.2 - bright))
        out += a * 10 ** (-3 * t / tk) * np.sin(2 * np.pi * fk * t + r.uniform(0, 6.28))
    ka = ns(0.0015)
    out[:ka] *= _rise(ka)
    kp = min(n, ns(0.012))
    out[:kp] += bandnoise(kp, 2500 + 3000 * bright, 1.5, seed + 3) * perc(kp, 0.0003, 0.01) * 0.08
    return out


def fm(fc, ratio, index, n, index_env=None, amp_env=None, feedback=0.0, seed=0):
    """Two-operator phase modulation, rendered 2x oversampled when its sidebands would reach Nyquist."""
    fmod = fc * ratio
    peak_index = index * (1.0 if index_env is None else float(np.max(index_env)))
    over = 2 if fc + (peak_index + 2) * fmod > TOP else 1
    m = n * over
    t = np.arange(m) / (SR * over)
    ie = 1.0 if index_env is None else np.interp(np.arange(m) / over, np.arange(n), index_env)
    mod = np.sin(2 * np.pi * fmod * t)
    if feedback:
        mod = np.sin(2 * np.pi * fmod * t + feedback * mod)
    y = np.sin(2 * np.pi * fc * t + index * ie * mod)
    if over > 1:
        y = resample_down(y, over)[:n]
    if amp_env is not None:
        y = y * amp_env
    return y


def fm_bell(f0, n, t60=4.0, index=2.5, ratio=3.5, seed=0):
    """A glassy FM bell: bright strike settling to a pure tone."""
    env = perc(n, 0.002, t60)
    ie = perc(n, 0.001, t60 * 0.35)
    return fm(f0, ratio, index, n, index_env=ie, amp_env=env, seed=seed)


def flute(f0, n, breath=0.25, seed=0, vib_cents=10):
    """A soft wooden flute: near-sine body with a breath band around it and a little chiff."""
    v = vibrato(n, 4.8, vib_cents, 0.35, seed)
    body = sine(f0 * v, n) + 0.12 * sine(2 * f0 * v, n) + 0.04 * sine(3 * f0 * v, n)
    air = bandnoise(n, f0 * 2.0, 1.2, seed + 1) * 0.6 + bandnoise(n, f0, 3.0, seed + 2)
    ch = min(n, ns(0.06))
    chiff = np.zeros(n)
    chiff[:ch] = bandnoise(ch, 2 * f0 + 1500, 1.0, seed + 3) * perc(ch, 0.002, 0.05)
    return body + breath * air * (0.6 + 0.4 * smooth_noise(n, 3, seed + 4)) + 0.25 * chiff


VOWELS = {  # formant (Hz, bandwidth Hz, gain dB)
    "a": [(800, 90, 12), (1150, 110, 8), (2900, 160, 2), (3900, 200, -2)],
    "o": [(450, 70, 12), (800, 90, 8), (2830, 150, -4), (3800, 200, -8)],
    "u": [(325, 60, 12), (700, 80, 4), (2530, 140, -8), (3500, 200, -12)],
    "e": [(400, 70, 11), (1700, 110, 6), (2600, 150, 2), (3200, 200, 0)],
    "m": [(250, 60, 10), (1200, 300, -6), (2500, 400, -14)],
}


def choir(f0, n, vowel="a", voices=6, seed=0, bright=0.4, vib_cents=14, spread=9.0, breath=0.05):
    """A distant choir: an ensemble of formant-shaped voices with independent vibrato and drift. Mono."""
    r = rng(seed)
    out = np.zeros(n)
    form = VOWELS[vowel]
    for v in range(voices):
        det = 2 ** (r.uniform(-spread, spread) / 1200)
        f = f0 * det
        amps = saw_amps(f * 1.03, bright=bright, tilt=1.25, cap=90, formants=form, f0=f)
        vib = vibrato(n, r.uniform(4.4, 5.6), vib_cents, r.uniform(0.2, 0.6), seed + 10 * v + 1, drift=5.0)
        out += wt_voice(f, n, amps, vib, phase0=r.uniform(0, 1))
    out /= voices ** 0.5
    if breath:
        out += breath * bandnoise(n, form[1][0], 1.0, seed + 99) * (0.5 + 0.5 * smooth_noise(n, 2, seed + 98))
    return out


STRING_BODY = [(280, 80, 5), (460, 120, 3), (1050, 300, 2), (2600, 600, -2)]


def strings(f0, n, voices=5, seed=0, bright=0.45, vib_cents=9, spread=7.0):
    """A soft string section pad (bowed: saw-like with a body resonance), ensemble detuned. Mono."""
    r = rng(seed)
    out = np.zeros(n)
    for v in range(voices):
        f = f0 * 2 ** (r.uniform(-spread, spread) / 1200)
        amps = saw_amps(f * 1.02, bright=bright, tilt=1.1, cap=120, formants=STRING_BODY, f0=f)
        vib = vibrato(n, r.uniform(4.6, 5.8), vib_cents, r.uniform(0.3, 0.8), seed + 7 * v + 3, drift=3.0)
        out += wt_voice(f, n, amps, vib, phase0=r.uniform(0, 1))
    return out / voices ** 0.5


def pad(f0, n, voices=7, seed=0, bright=0.3, spread=12.0, tilt=1.0):
    """A warm analogue-style pad: detuned softened saws, slow drift. Mono."""
    r = rng(seed)
    out = np.zeros(n)
    for v in range(voices):
        f = f0 * 2 ** (r.uniform(-spread, spread) / 1200)
        amps = saw_amps(f * 1.02, bright=bright, tilt=tilt, cap=150, f0=f)
        drift = 2 ** (smooth_noise(n, 0.3, seed + v * 5 + 2) * 3.0 / 1200)
        out += wt_voice(f, n, amps, drift, phase0=r.uniform(0, 1))
    return out / voices ** 0.5


def drone(f0, n, partials=(1, 2, 3, 4, 6), amps=(1, 0.5, 0.25, 0.15, 0.06), beat=0.15, seed=0):
    """A low drone of pure partials, each with a slowly beating twin."""
    r = rng(seed)
    t = tvec(n)
    out = np.zeros(n)
    for p, a in zip(partials, amps):
        f = f0 * p
        if f >= TOP:
            continue
        ph = r.uniform(0, 6.28)
        lfo = 1 + 0.25 * smooth_noise(n, 0.2, seed + p)
        out += a * lfo * (np.sin(2 * np.pi * f * t + ph) + 0.6 * np.sin(2 * np.pi * (f + beat * p) * t + ph + 2))
    return out


def sub_boom(n, f0=48.0, f1=30.0, t60=1.8, seed=0):
    """A deep impact: a sine dropping in pitch with a soft body."""
    f = glide(n, f0, f1)
    return sine(f, n) * perc(n, 0.006, t60)


# ---- noise and filters -------------------------------------------------------------------------------------------

def noise(n, color="white", seed=0):
    r = rng(seed)
    w = r.standard_normal(n)
    if color == "white":
        return w
    X = np.fft.rfft(w)
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1] if n > 1 else 1
    X *= 1 / np.sqrt(f) if color == "pink" else 1 / f
    y = np.fft.irfft(X, n)
    return y / (np.std(y) + 1e-12)


def fft_filter(x, resp):
    """Zero-phase filter: multiplies the spectrum by resp(freqs). x mono or stereo."""
    n = len(x)
    nfft = 1 << int(np.ceil(np.log2(max(n, 2))))
    f = np.fft.rfftfreq(nfft, 1 / SR)
    H = resp(f)
    if x.ndim == 1:
        return np.fft.irfft(np.fft.rfft(x, nfft) * H, nfft)[:n]
    return np.stack([np.fft.irfft(np.fft.rfft(x[:, c], nfft) * H, nfft)[:n] for c in range(x.shape[1])], axis=1)


def lp_resp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def hp_resp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def bp_resp(fc, q=1.0):
    def r(f):
        ff = np.maximum(f, 1e-3)
        return 1.0 / np.sqrt(1.0 + (q * (ff / fc - fc / ff)) ** 2)
    return r


def lowpass(x, fc, order=2):
    return fft_filter(x, lp_resp(fc, order))


def highpass(x, fc, order=2):
    return fft_filter(x, hp_resp(fc, order))


def bandpass(x, fc, q=1.0):
    return fft_filter(x, bp_resp(fc, q))


def shelf(x, fc, gain_db, high=True):
    g = dbamp(gain_db)

    def r(f):
        s = 1.0 / (1.0 + (fc / np.maximum(f, 1e-3)) ** 2) if high else 1.0 / (1.0 + (f / fc) ** 2)
        return 1.0 + (g - 1.0) * s
    return fft_filter(x, r)


def bandnoise(n, fc, q=1.0, seed=0):
    """Noise around fc (Q q), normalised to unit RMS."""
    if n <= 0:
        return np.zeros(0)
    y = bandpass(noise(n, "white", seed), fc, q)
    return y / (np.std(y) + 1e-12)


def stft_filter(x, resp_at, frame=2048, hop=512):
    """Time-varying filter: resp_at(t_seconds, freqs) -> gains, applied per frame with overlap-add. Mono only."""
    n = len(x)
    win = np.sqrt(np.hanning(frame + 1)[:-1])
    pad = np.concatenate([np.zeros(frame), x, np.zeros(frame)])
    out = np.zeros(len(pad))
    norm = np.zeros(len(pad))
    f = np.fft.rfftfreq(frame, 1 / SR)
    for start in range(0, len(pad) - frame, hop):
        seg = pad[start:start + frame] * win
        tc = (start + frame / 2 - frame) / SR
        Y = np.fft.rfft(seg) * resp_at(tc, f)
        out[start:start + frame] += np.fft.irfft(Y, frame) * win
        norm[start:start + frame] += win * win
    out /= np.maximum(norm, 1e-9)
    return out[frame:frame + n]


def resample_down(y, factor):
    """Brick-wall decimation by an integer factor (FFT)."""
    m = len(y)
    Y = np.fft.rfft(y)
    keep = m // factor // 2 + 1
    Y = Y[:keep].copy()
    Y[-1] *= 0.5
    return np.fft.irfft(Y, m // factor) / factor


def crackle(n, rate=30.0, seed=0, bright=0.6, burstiness=0.7):
    """Electric crackle: sparse clicks and short bursts, density varying in clumps. Mono."""
    r = rng(seed)
    out = np.zeros(n)
    dens = np.clip(0.5 + 0.8 * smooth_noise(n, 1.2, seed + 1), 0, None) ** (1 + 2 * burstiness)
    lam = rate / SR * dens
    hits = np.nonzero(r.random(n) < lam)[0]
    for h in hits:
        L = int(r.integers(8, 300 if r.random() < 0.85 else 1400))
        L = min(L, n - h)
        if L <= 2:
            continue
        seg = r.standard_normal(L) * np.exp(-np.arange(L) / (L * r.uniform(0.15, 0.4)))
        out[h:h + L] += seg * r.uniform(0.2, 1.0)
    out = highpass(out, 400 + 1200 * bright, 1)
    out = lowpass(out, 5000 + 9000 * bright, 2)
    return out / (np.max(np.abs(out)) + 1e-12)


# ---- space -------------------------------------------------------------------------------------------------------

def pan(x, p):
    """Constant-power pan of a mono signal: p -1 (left) .. +1 (right)."""
    a = (np.clip(p, -1, 1) + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def delay_line(x, delay, mix=1.0):
    """Reads x at a constant or per-sample delay (seconds), linear interpolation. Mono."""
    n = len(x)
    d = np.full(n, delay * SR) if np.isscalar(delay) else np.asarray(delay) * SR
    pos = np.arange(n) - d
    y = np.interp(pos, np.arange(n), x, left=0.0, right=0.0)
    return y if mix == 1.0 else (1 - mix) * x + mix * y


def chorus(x, depth_ms=6.0, rate=0.35, voices=2, seed=0, mix=0.5):
    """Stereo chorus of a mono signal (modulated delays left and right)."""
    r = rng(seed)
    n = len(x)
    t = tvec(n)
    L = np.zeros(n)
    R = np.zeros(n)
    for v in range(voices):
        ph = r.uniform(0, 6.28)
        d = (12 + depth_ms * (0.5 + 0.5 * np.sin(2 * np.pi * rate * (1 + 0.2 * v) * t + ph))) / 1000
        y = delay_line(x, d)
        if v % 2 == 0:
            L += y
        else:
            R += y
    if voices == 1:
        R = L
    return np.stack([(1 - mix) * x + mix * L / max(1, (voices + 1) // 2),
                     (1 - mix) * x + mix * R / max(1, voices // 2)], axis=1)


def echo(x, time=0.4, feedback=0.4, taps=5, damp=3000.0, pingpong=True):
    """Feedback echo as a sum of taps (each darker); mono in, stereo out."""
    n = len(x)
    out = np.zeros((n + ns(time * taps), 2))
    y = x.copy()
    for k in range(1, taps + 1):
        y = lowpass(y, damp, 1)
        g = feedback ** k
        off = ns(time * k)
        side = (k % 2) if pingpong else 2
        seg = y * g
        if side == 2:
            out[off:off + n, 0] += seg
            out[off:off + n, 1] += seg
        else:
            out[off:off + n, side] += seg
    return out


def reverb_ir(t60=2.8, predelay=0.02, bright=0.5, width=1.0, early=0.5, size=1.0, seed=0, low_mult=1.25):
    """A synthesised stereo room: sparse early reflections, then a dense noise tail decaying faster in the highs
    (air and walls). Returns (n, 2), normalised to unit energy per channel."""
    r = rng(seed)
    L = t60 * 1.05 + predelay + 0.1
    n = ns(L)
    t = tvec(n)
    bands = [(0, 180, low_mult), (180, 700, 1.1), (700, 2500, 1.0), (2500, 6000, 0.55 + 0.35 * bright),
             (6000, 12000, 0.3 + 0.3 * bright), (12000, NYQ, 0.15 + 0.2 * bright)]
    out = np.zeros((n, 2))
    common = r.standard_normal(n)
    for c in range(2):
        w = r.standard_normal(n)
        w = np.sqrt(width) * w + np.sqrt(max(0.0, 1 - width)) * common
        X = np.fft.rfft(w)
        f = np.fft.rfftfreq(n, 1 / SR)
        tail = np.zeros(n)
        for lo, hi, mult in bands:
            m = ((f >= lo) & (f < hi)).astype(float)
            band = np.fft.irfft(X * m, n)
            tail += band * 10 ** (-3 * t / (t60 * mult))
        # density builds over the first ~60 ms * size
        build = np.clip(t / (0.06 * size), 0, 1) ** 1.5
        tail *= build
        out[:, c] = tail
    out = np.roll(out, ns(predelay), axis=0)
    out[:ns(predelay)] = 0
    if early > 0:
        for k in range(14):
            d = predelay * 0.4 + r.uniform(0.004, 0.065) * size
            g = early * r.uniform(0.25, 0.7) * (1 - k / 16)
            i = ns(d)
            if i < n:
                ch = k % 2
                out[i, ch] += g * 3.0
                out[min(n - 1, i + int(r.integers(5, 40))), 1 - ch] += g * 1.8
    # soften the very top of the early taps
    out = lowpass(out, 9000 + 8000 * bright, 1)
    e = np.sqrt(np.sum(out ** 2, axis=0))
    return out / np.maximum(e, 1e-12)


def convolve(x, ir):
    """FFT convolution; mono x with stereo ir -> stereo; stereo x uses its mid for the send."""
    src = mono(x)
    n = len(src) + len(ir) - 1
    nfft = 1 << int(np.ceil(np.log2(n)))
    S = np.fft.rfft(src, nfft)
    return np.stack([np.fft.irfft(S * np.fft.rfft(ir[:, c], nfft), nfft)[:n] for c in range(2)], axis=1)


def reverse_swell(x, ir):
    """A reversed reverb tail that sucks into the sound (the 'gate forming' riser)."""
    wet = convolve(x[::-1], ir)
    return wet[::-1]


class Mix:
    """A stereo bus with named reverb sends. add() places a mono or stereo signal at a time (seconds)."""

    def __init__(self, seconds):
        self.n = ns(seconds)
        self.dry = np.zeros((self.n, 2))
        self.sends = {}

    def add(self, sig, at=0.0, gain=1.0, pan_pos=0.0, send=None, wet=0.0):
        i = ns(at)
        if i >= self.n or len(sig) == 0:
            return
        s = sig[: self.n - i] * gain
        st = pan(s, pan_pos) if s.ndim == 1 else s
        self.dry[i:i + len(st)] += st
        if send and wet > 0:
            buf = self.sends.setdefault(send, np.zeros(self.n))
            buf[i:i + len(st)] += mono(st) * wet

    def render(self, irs, tail=0.0):
        """Mixes the dry bus with each send convolved by irs[name]; `tail` seconds are kept after the end."""
        total = self.n + ns(tail)
        out = pad_to(self.dry, total)
        for name, buf in self.sends.items():
            wet = convolve(buf, irs[name])
            out += pad_to(wet, total)
        return out


# ---- mastering and measurement -----------------------------------------------------------------------------------

def _biquad_mag(b, a, f):
    z = np.exp(-1j * 2 * np.pi * f / SR)
    return np.abs((b[0] + b[1] * z + b[2] * z * z) / (a[0] + a[1] * z + a[2] * z * z))


def k_weight(x):
    """ITU-R BS.1770 K-weighting: a +4 dB high shelf at 1.5 kHz and a 38 Hz high-pass (the usual biquad designs),
    applied as a magnitude response."""
    w0 = 2 * np.pi * 1500.0 / SR
    A, q = 10 ** (4.0 / 40), 1 / np.sqrt(2)
    al = np.sin(w0) / (2 * q)
    c = np.cos(w0)
    sb = [A * ((A + 1) + (A - 1) * c + 2 * np.sqrt(A) * al), -2 * A * ((A - 1) + (A + 1) * c),
          A * ((A + 1) + (A - 1) * c - 2 * np.sqrt(A) * al)]
    sa = [(A + 1) - (A - 1) * c + 2 * np.sqrt(A) * al, 2 * ((A - 1) - (A + 1) * c),
          (A + 1) - (A - 1) * c - 2 * np.sqrt(A) * al]
    w1 = 2 * np.pi * 38.0 / SR
    al1 = np.sin(w1) / (2 * 0.5)
    c1 = np.cos(w1)
    hb = [(1 + c1) / 2, -(1 + c1), (1 + c1) / 2]
    ha = [1 + al1, -2 * c1, 1 - al1]
    return fft_filter(x, lambda f: _biquad_mag(sb, sa, f) * _biquad_mag(hb, ha, f))


def loudness(x):
    """Integrated loudness (LUFS, BS.1770 gating: 400 ms blocks, 75% overlap, -70 absolute, -10 relative)."""
    st = stereo(x)
    kw = k_weight(st)
    blk, hop = ns(0.4), ns(0.1)
    if len(kw) < blk:
        kw = pad_to(kw, blk)
    ms = []
    for s in range(0, len(kw) - blk + 1, hop):
        seg = kw[s:s + blk]
        ms.append(np.sum(np.mean(seg ** 2, axis=0)))
    ms = np.array(ms)
    lk = -0.691 + 10 * np.log10(np.maximum(ms, 1e-20))
    g = ms[lk > -70]
    if len(g) == 0:
        return -99.0
    rel = -0.691 + 10 * np.log10(np.mean(g)) - 10
    g2 = ms[(lk > -70) & (lk > rel)]
    return float(-0.691 + 10 * np.log10(np.mean(g2)))


def gated_rms(x, block=0.1, abs_gate=-60.0, rel_gate=-20.0):
    """RMS (dBFS) over the blocks that carry the sound: blocks under abs_gate, or rel_gate below the mean, are
    left out, so silence and tails don't drag the number down."""
    st = stereo(x)
    b = max(1, ns(block))
    nb = max(1, len(st) // b)
    seg = pad_to(st, nb * b).reshape(nb, b, 2)
    p = np.mean(seg ** 2, axis=(1, 2))
    d = 10 * np.log10(np.maximum(p, 1e-20))
    keep = p[d > abs_gate]
    if len(keep) == 0:
        return -99.0
    thr = 10 * np.log10(np.mean(keep)) + rel_gate
    keep = p[(d > abs_gate) & (d > thr)]
    return float(10 * np.log10(np.mean(keep)))


def true_peak(x, over=4):
    """Peak (dBFS) of the signal upsampled x4 (catches inter-sample overs)."""
    st = stereo(x)
    pk = 0.0
    for c in range(st.shape[1]):
        y = st[:, c]
        n = len(y)
        Y = np.fft.rfft(y)
        up = np.fft.irfft(Y, n * over) * over
        pk = max(pk, float(np.max(np.abs(up))))
    return float(ampdb(pk))


def limit(x, ceiling_db=-1.0, release=0.12, look=0.004):
    """A transparent look-ahead peak limiter: gain never lets |x| pass the ceiling; releases smoothly."""
    st = stereo(x).copy()
    ceil = dbamp(ceiling_db)
    blk = 64
    n = len(st)
    nb = (n + blk - 1) // blk
    pk = np.max(np.abs(pad_to(st, nb * blk)).reshape(nb, blk, 2), axis=(1, 2))
    need = np.minimum(1.0, ceil / np.maximum(pk, 1e-12))
    lb = max(1, int(np.ceil(look * SR / blk)))
    # look ahead: a block's gain is the minimum of the next few blocks' needs
    ahead = need.copy()
    for k in range(1, lb + 1):
        ahead[:-k] = np.minimum(ahead[:-k], need[k:])
    g = np.empty(nb)
    cur = 1.0
    rel = np.exp(-blk / (release * SR))
    for i in range(nb):
        cur = ahead[i] if ahead[i] < cur else ahead[i] + (cur - ahead[i]) * rel
        g[i] = cur
    gs = np.interp(np.arange(n), np.arange(nb) * blk + blk / 2, g)
    # a block's gain must hold over the whole block: never exceed the block's own need
    gs = np.minimum(gs, np.repeat(ahead, blk)[:n])
    out = st * gs[:, None]
    return out if x.ndim == 2 else out[:, 0]


def master(x, rms_db, ceiling_db=-1.0, hp=25.0):
    """Removes DC and rumble, sets the gated RMS, and limits peaks under the ceiling (true peak)."""
    y = highpass(stereo(x) if x.ndim == 2 else x, hp, 2)
    for _ in range(4):
        cur = gated_rms(y)
        y = y * dbamp(rms_db - cur)
        if true_peak(y) > ceiling_db:
            y = limit(y, ceiling_db - 0.3)
        if abs(gated_rms(y) - rms_db) < 0.15:
            break
    if true_peak(y) > ceiling_db:
        y = y * dbamp(ceiling_db - true_peak(y) - 0.05)
    return declick(y, 0.001, 0.01)


def loop_seamless(x, xfade=1.0):
    """Makes x loop without a seam: its last `xfade` seconds are crossfaded (equal power) into its start."""
    k = ns(xfade)
    body = x[: len(x) - k].copy()
    tail = x[len(x) - k:]
    w = np.sin(0.5 * np.pi * np.arange(k) / k)
    if x.ndim == 1:
        body[:k] = body[:k] * w + tail * np.cos(0.5 * np.pi * np.arange(k) / k)
    else:
        body[:k] = body[:k] * w[:, None] + tail * np.cos(0.5 * np.pi * np.arange(k) / k)[:, None]
    return body


# ---- files -------------------------------------------------------------------------------------------------------

def write_ogg(path, x, quality=4, title=None):
    """Encodes float audio to Ogg Vorbis with ffmpeg (bit-exact muxing so rebuilds don't change the bytes)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    ch = 1 if x.ndim == 1 else x.shape[1]
    data = np.clip(x, -1, 1).astype("<f4").tobytes()
    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(ch), "-i", "pipe:0",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
           "-c:a", "libvorbis", "-q:a", str(quality)]
    if title:
        cmd += ["-metadata", f"title={title}", "-metadata", "artist=AWAKEN"]
    cmd += [path]
    subprocess.run(cmd, input=data, check=True)


def read_audio(path):
    """Decodes any file ffmpeg reads to float64 (n, channels) at SR."""
    probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                            "-of", "csv=p=0", path], capture_output=True, text=True)
    ch = int(probe.stdout.strip() or 2)
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ar", str(SR), "-"],
                       capture_output=True, check=True)
    y = np.frombuffer(r.stdout, dtype="<f4").astype(np.float64)
    return y.reshape(-1, ch) if ch > 1 else y
