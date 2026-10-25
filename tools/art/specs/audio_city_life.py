"""CARILLON's soundscape (M4 B3b, SPEC §16.0 decision 28): the districts' beds, the places that sound, the animals'
voices and the Choir's carillon. Synthesised from scratch with tools/art/audiokit.py (numpy only, seeded); nothing is
recorded or copied. Where the game already has the right voice (cats, dogs, hooves) the client references it by id.

    python3 tools/art/specs/audio_city_life.py              render everything, write the events, subtitles, manifests
    python3 tools/art/specs/audio_city_life.py beds         only the bed stems and manifests
    python3 tools/art/specs/audio_city_life.py shots        only the one-shots and loops

Three kinds of file:
  - Bed stems (sounds/layers/layer_city_<stem>.ogg, stereo, 48 s, all the same length so they loop together): the
    district beds the core audio's ambience slot plays (MusicLayers.Slot.AMBIENCE, contexts awaken:city/<district> by
    day and awaken:city/night after dark; assets/awaken/music_layers/city/*.json gives each stem's gain per crowd level).
  - Place loops (sounds/amb_city_<name>.ogg, mono, seamless): played at their place by citizens.client.CitySound:
    fountains, water lapping at the quays, the mill wheels, a tavern's muffled evening.
  - One-shots (sounds/city/<name><n>.ogg, mono, several variants each): forges, stalls, boats, songbirds, the animals,
    the carillon's three tunes (dawn, noon, dusk; mono like the rest, so the spire is where they come from).

The crowd is the hard one: a murmur of formant voices (glottal harmonics shaped by vowel formants, syllables and
phrases with falling intonation, consonant hiss between), placed at distances round the listener, darkened and
reverberated by distance. At a distance this reads as people; up close it would be gibberish, so the near layer stays a
few metres off and the beds never carry words. CC0 recordings could replace the murmur after the owner's yes.
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MANIFESTS = os.path.join(ROOT, "src/main/resources/assets/awaken/music_layers/city")
MERGE = os.path.join(ROOT, "src/main/merge/citizens/assets/awaken")
DATA = os.path.join(ROOT, "src/main/resources/assets/awaken/city/soundscape.json")

SR = K.SR
STEM_SECONDS = 48.0
VSR = 16000  # the voices are made at 16 kHz (nothing of a distant crowd lives above 6 kHz) and brought up after


def build(out):  # rendered on demand (main), not by build_all
    pass


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def fold(x, n):
    """Wraps whatever runs past n back onto the start (a reverb tail into the loop's head)."""
    y = x[:n].copy()
    rest = x[n:]
    while len(rest):
        k = min(n, len(rest))
        y[:k] += rest[:k]
        rest = rest[k:]
    return y


def up(x):
    """16 kHz voices to the game's rate."""
    n = int(round(len(x) * SR / VSR))
    return np.interp(np.arange(n) * VSR / SR, np.arange(len(x)), x)


# =====================================================================================================================
# Voices
# =====================================================================================================================

# vowel formants (F1, F2, F3) Hz, an adult's; a woman's scale by ~1.15
VOWELS = {"a": (750, 1220, 2600), "e": (480, 1850, 2550), "i": (300, 2250, 2950), "o": (500, 900, 2450),
          "u": (330, 820, 2350), "@": (520, 1350, 2450), "ai": (650, 1650, 2600), "ou": (450, 1000, 2400)}
VNAMES = list(VOWELS)


def voice(n, seed, female=False, rate=1.0, lively=1.0, laugh=0.0):
    """One person talking for n samples at VSR: phrases of syllables, pauses between, the pitch falling across a
    phrase with a lift on the stressed syllable; voiced harmonics through moving formants, consonants as hiss."""
    r = K.rng(seed)
    out = np.zeros(n)
    base = r.uniform(178, 238) if female else r.uniform(92, 138)
    fscale = 1.15 if female else 1.0
    tilt = r.uniform(0.9, 1.15)
    t = int(r.uniform(0, 1.5) * VSR)
    while t < n:
        if laugh and r.random() < laugh:
            t = _laugh(out, t, n, base, fscale, r)
            t += int(r.uniform(0.3, 1.2) * VSR)
            continue
        syll = int(r.integers(3, 11))
        stress = int(r.integers(0, syll))
        question = r.random() < 0.15
        prev = VOWELS[VNAMES[int(r.integers(0, len(VNAMES)))]]
        for k in range(syll):
            if t >= n:
                break
            # consonant: a short hiss or a stop's burst before most syllables
            if r.random() < 0.7:
                cl = int(r.uniform(0.018, 0.06) / rate * VSR)
                if t + cl < n:
                    fc = r.uniform(2200, 5200) if r.random() < 0.6 else r.uniform(900, 1800)
                    hiss = K.rng(int(r.integers(1 << 30))).standard_normal(cl)
                    env = np.sin(np.linspace(0, np.pi, cl)) ** 1.5
                    out[t:t + cl] += _band(hiss, fc, 1.5) * env * 0.12 * lively
                    t += cl
            dur = r.uniform(0.09, 0.24) / rate * (1.35 if k == stress else 1.0) * (1.4 if k == syll - 1 else 1.0)
            L = int(dur * VSR)
            if t + L >= n:
                break
            prog = k / max(1, syll - 1)
            f_start = base * (1.08 - 0.22 * prog) * (1.16 if k == stress else 1.0)
            f_end = f_start * (0.96 if not (question and k == syll - 1) else 1.25)
            f_start *= 1 + lively * 0.05 * r.standard_normal()
            v = VOWELS[VNAMES[int(r.integers(0, len(VNAMES)))]]
            out[t:t + L] += _vowel(L, f_start, f_end, prev, v, fscale, tilt, r) * (1.25 if k == stress else 1.0)
            prev = v
            t += L
        t += int(r.uniform(0.18, 0.95) * VSR / rate)
    return out


def _band(x, fc, q):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / VSR)
    X *= 1.0 / (1.0 + ((f - fc) / (fc / q)) ** 2)
    return np.fft.irfft(X, len(x))


def _vowel(L, f0a, f0b, va, vb, fscale, tilt, r):
    """A voiced syllable: harmonics of a gliding f0, each weighted by the vowel's formant envelope (moving from the
    last vowel to this one), with a little jitter and shimmer, a soft attack and release."""
    t = np.arange(L) / VSR
    u = t / max(t[-1], 1e-6)
    f0 = f0a + (f0b - f0a) * u
    f0 = f0 * (1 + 0.006 * np.sin(2 * np.pi * r.uniform(4, 7) * t) + 0.004 * r.standard_normal(L).cumsum() / np.sqrt(np.arange(1, L + 1)))
    ph = 2 * np.pi * np.cumsum(f0) / VSR
    mix = np.clip(u * 3, 0, 1)
    F = [((va[i] + (vb[i] - va[i]) * mix) * fscale) for i in range(3)]
    B = [90.0, 120.0, 180.0]
    G = [1.0, 0.7, 0.35]
    hmax = max(1, int(3600 / max(f0a, f0b)))
    H = np.arange(1, hmax + 1)[:, None]
    fh = H * f0[None, :]
    env = sum(G[i] / (1 + ((fh - F[i][None, :]) / B[i]) ** 2) for i in range(3)) * H ** (-0.6 * tilt)
    y = (np.sin(ph[None, :] * H) * env).sum(axis=0)
    a = int(0.02 * VSR)
    rl = int(0.035 * VSR)
    w = np.ones(L)
    w[:a] = np.linspace(0, 1, a) ** 1.5
    w[-rl:] *= np.linspace(1, 0, rl) ** 1.2
    return y * w * 0.05


def _laugh(out, t, n, base, fscale, r):
    """"ha-ha-ha": breathy 'a's, high and falling, quick."""
    k = int(r.integers(3, 7))
    f = base * r.uniform(1.25, 1.6)
    for i in range(k):
        L = int(r.uniform(0.08, 0.13) * VSR)
        if t + L >= n:
            return t
        y = _vowel(L, f, f * 0.94, VOWELS["a"], VOWELS["a"], fscale, 0.8, r)
        breath = _band(r.standard_normal(L), 1500, 0.8) * np.sin(np.linspace(0, np.pi, L)) * 0.03
        out[t:t + L] += (y + breath) * 1.3
        t += L + int(r.uniform(0.03, 0.07) * VSR)
        f *= 0.95
    return t


def crowd(seconds, people, near, seed, room=1.0, bright=1.0):
    """A murmur: `people` voices at distances from `near` to ~40 m round the listener, stereo, a city's reverb."""
    r = K.rng(seed)
    nv = int(seconds * VSR)
    L = np.zeros(nv)
    R_ = np.zeros(nv)
    for p in range(people):
        female = r.random() < 0.48
        v = voice(nv, seed * 1000 + p, female, rate=r.uniform(0.85, 1.2), lively=r.uniform(0.7, 1.3), laugh=0.04 if r.random() < 0.3 else 0.0)
        d = near + (40 - near) * r.random() ** 1.4
        # distance: quieter (1/d), darker (air and bodies in the way), and the talk comes and goes
        X = np.fft.rfft(v)
        f = np.fft.rfftfreq(nv, 1 / VSR)
        fc = (5200 * bright) / (1 + d / 9)
        X *= 1 / (1 + (f / fc) ** 2) / np.sqrt(1 + (220 / np.maximum(f, 1)) ** 2)
        v = np.fft.irfft(X, nv)
        come = 0.55 + 0.45 * np.clip(K.smooth_noise(nv, 0.08, seed * 77 + p) * 1.5 + 0.3, 0, 1)
        v *= come / max(1.5, d)
        pan_ = np.clip(r.uniform(-1, 1), -1, 1)
        a = (pan_ + 1) * np.pi / 4
        L += v * np.cos(a)
        R_ += v * np.sin(a)
    st = np.stack([up(L), up(R_)], axis=1)
    if room > 0:
        ir = K.reverb_ir(t60=1.4 * room, predelay=0.03, bright=0.35, width=0.9, early=0.6, size=1.6, seed=seed)
        wet = K.convolve(st, ir)
        n = len(st)
        st = st + fold(wet, n) * (0.35 * room)
    return st


# =====================================================================================================================
# Place sounds: small physical models
# =====================================================================================================================

def hits(n, rate, seed, fn, jitter=0.6):
    """Scatters events at ~rate per second (Poisson-ish, clumped): fn(r) -> mono array added at each time."""
    r = K.rng(seed)
    out = np.zeros(n)
    t = r.uniform(0, 1 / max(rate, 1e-3))
    while True:
        i = int(t * SR)
        if i >= n:
            break
        x = fn(r)
        k = min(len(x), n - i)
        out[i:i + k] += x[:k]
        t += (1 / rate) * (1 - jitter + 2 * jitter * r.random())
    return out


def step(r, stone=True):
    """A footstep on stone (or wood): a click with a body, 40 ms."""
    L = K.ns(0.05)
    x = r.standard_normal(L) * np.exp(-np.arange(L) / (0.006 * SR))
    x = K.bandpass(x, r.uniform(1400, 2600) if stone else r.uniform(500, 900), 1.2)
    thump = np.sin(2 * np.pi * r.uniform(90, 140) * np.arange(L) / SR) * np.exp(-np.arange(L) / (0.012 * SR)) * 0.6
    return (x + thump) * r.uniform(0.4, 1.0)


def wood_knock(r, f=None):
    f = f or r.uniform(140, 320)
    L = K.ns(0.25)
    return K.modal(f, (1, 2.32, 3.9, 5.6), (1, 0.45, 0.2, 0.1), (0.09, 0.05, 0.03, 0.02), L, seed=int(r.integers(1 << 30))) * r.uniform(0.4, 1)


def metal_ring(f, t60, n, seed, bright=1.0):
    return K.modal(f, (1, 2.76, 5.40, 8.93, 13.3), (1, 0.6 * bright, 0.35 * bright, 0.2 * bright, 0.1 * bright),
                   (t60, t60 * 0.6, t60 * 0.35, t60 * 0.2, t60 * 0.12), n, seed=seed, pair=0.003)


def water_flow(n, seed, fc=600, depth=0.5):
    w = K.noise(n, "pink", seed)
    w = K.lowpass(K.highpass(w, 80, 2), fc, 2)
    return _norm(w * (1 + depth * K.smooth_noise(n, 0.4, seed + 1)))


def lap(r):
    """One wave against the quay wall: a slosh up and a fall back, with a few bright plops."""
    L = K.ns(r.uniform(0.9, 1.6))
    t = np.arange(L) / SR
    body = K.bandpass(r.standard_normal(L), r.uniform(350, 700), 0.9)
    env = (1 - np.exp(-t / 0.08)) * np.exp(-t / r.uniform(0.25, 0.45))
    low = K.lowpass(r.standard_normal(L), 180, 2) * env * 1.6
    x = body * env + low
    for _ in range(int(r.integers(2, 7))):
        i = int(r.uniform(0.05, 0.6) * SR)
        f0 = r.uniform(700, 1600)
        pl = K.ns(0.04)
        chirp = np.sin(2 * np.pi * np.cumsum(np.linspace(f0, f0 * 1.6, pl)) / SR) * np.exp(-np.arange(pl) / (0.008 * SR))
        if i + pl < L:
            x[i:i + pl] += chirp * r.uniform(0.15, 0.4)
    return x * r.uniform(0.5, 1.0)


def droplets(n, rate, seed, lo=900, hi=3500):
    def one(r):
        pl = K.ns(0.03)
        f0 = r.uniform(lo, hi)
        return np.sin(2 * np.pi * np.cumsum(np.linspace(f0, f0 * r.uniform(1.2, 1.9), pl)) / SR) * np.exp(-np.arange(pl) / (0.006 * SR)) * r.uniform(0.2, 1)
    return hits(n, rate, seed, one, jitter=0.95)


# ---- the place loops (mono) -------------------------------------------------------------------------------------

def amb_city_fountain(seed):
    n = K.ns(12 + 1)
    spray = K.bandpass(K.noise(n, "white", seed), 3200, 0.7) * (0.75 + 0.25 * K.smooth_noise(n, 3, seed + 1))
    pour = water_flow(n, seed + 2, fc=900, depth=0.3)
    drops = droplets(n, 160, seed + 3, 1200, 4200)
    x = 0.55 * _norm(spray) + 0.6 * pour + 0.35 * _norm(drops)
    return K.loop_seamless(x, 1.0)


def amb_city_lap(seed):
    n = K.ns(14 + 1)
    x = hits(n, 0.75, seed, lap, jitter=0.7)
    x += 0.25 * water_flow(n, seed + 4, fc=420, depth=0.6)
    x += 0.12 * _norm(droplets(n, 6, seed + 5, 600, 1800))
    return K.loop_seamless(x, 1.0)


def amb_city_mill(seed):
    """A mill wheel: the race churning, a paddle slapping each beat, the axle's wooden knock once a turn, a creak."""
    n = K.ns(16 + 1)
    rev = 4.0  # seconds a turn, 8 paddles
    churn = water_flow(n, seed, fc=1100, depth=0.4)
    t = np.arange(n) / SR
    slap_env = (0.5 + 0.5 * np.cos(2 * np.pi * t * 8 / rev)) ** 6
    slap = K.bandpass(K.noise(n, "white", seed + 1), 800, 0.8) * slap_env
    knocks = np.zeros(n)
    r = K.rng(seed + 2)
    for k in range(int(16 / rev) + 1):
        i = int(k * rev * SR)
        x = wood_knock(r, 95) * 1.4
        knocks[i:i + len(x)] += x[: max(0, min(len(x), n - i))]
        j = int((k * rev + rev * 0.5) * SR)
        cr = _creak(r, 0.5)
        knocks[j:j + len(cr)] += cr[: max(0, min(len(cr), n - j))] * 0.5
    x = 0.7 * churn + 0.5 * _norm(slap) + 0.6 * knocks
    return K.loop_seamless(x, 1.0)


def _creak(r, dur):
    """Wood under strain: a slip-stick pulse train through a wooden body."""
    L = K.ns(dur)
    t = np.arange(L) / SR
    rate = r.uniform(25, 60) * (1 + 0.5 * np.sin(np.pi * t / dur))
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float) * (0.5 + r.random(L))
    body = K.modal(r.uniform(300, 700), (1, 1.9, 3.1), (1, 0.6, 0.3), (0.03, 0.02, 0.01), K.ns(0.06), seed=int(r.integers(1 << 30)))
    y = np.convolve(pulses, body)[:L]
    return _norm(y) * np.sin(np.pi * t / dur) ** 0.7


def amb_city_tavern(seed):
    """A tavern heard from the lane: talk and laughter through the walls, pewter on wood, a fiddle and a lute."""
    sec = 14
    talk = crowd(sec + 1, 9, 2.5, seed, room=0.6, bright=0.8)[:, 0]
    r = K.rng(seed + 1)
    n = len(talk)
    clinks = hits(n, 1.2, seed + 2, lambda q: metal_ring(q.uniform(1800, 2800), 0.25, K.ns(0.4), int(q.integers(1 << 30)), 0.6) * q.uniform(0.2, 0.6))
    thuds = hits(n, 0.5, seed + 3, lambda q: wood_knock(q, q.uniform(110, 180)))
    tune = _reel(n, seed + 4)
    x = 0.9 * _norm(talk) + 0.25 * _norm(clinks) + 0.25 * _norm(thuds) + 0.3 * tune
    # through the walls: the highs gone, a boxy room
    x = K.lowpass(x, 1300, 2)
    x = K.bandpass(x, 450, 0.35) * 0.6 + x * 0.5
    return K.loop_seamless(x, 1.0)


def _reel(n, seed):
    """A jaunty tune in D (fiddle over a strummed lute), our own: two bars round and round."""
    r = K.rng(seed)
    notes = [62, 66, 69, 66, 71, 69, 66, 64, 62, 64, 66, 69, 67, 66, 64, 62]
    beat = 0.22
    y = np.zeros(n)
    t = 0.0
    k = 0
    while t * SR < n:
        m = notes[k % len(notes)] + (12 if (k // 16) % 2 else 0) * 0
        f = 440 * 2 ** ((m - 69) / 12)
        L = K.ns(beat * 1.6)
        i = int(t * SR)
        tone = K.strings(f, L, voices=2, seed=k, bright=0.55, vib_cents=12) * K.adsr(L, 0.02, 0.08, 0.6, 0.12)
        if k % 4 == 0:
            ch = sum(K.pluck(440 * 2 ** ((q - 69) / 12), K.ns(0.9), bright=0.5, t60=0.8, seed=k + q) for q in (50, 57, 62))
            j = min(len(ch), n - i)
            y[i:i + j] += 0.5 * ch[:j]
        j = min(L, n - i)
        y[i:i + j] += tone[:j]
        t += beat * (1.0 + 0.04 * r.standard_normal())
        k += 1
    return _norm(y)


# ---- one-shots (mono) -------------------------------------------------------------------------------------------

def anvil(seed):
    """A smith's pattern: two taps on the anvil's face between blows, then the blow that rings."""
    r = K.rng(seed)
    pattern = [(0.0, 1.0), (0.32, 0.35), (0.48, 0.3), (0.72, 1.0), (1.04, 0.35), (1.2, 0.3), (1.44, 1.0)][: int(r.integers(4, 8))]
    n = K.ns(pattern[-1][0] + 1.6)
    x = np.zeros(n)
    f = r.uniform(820, 1050)
    for t, a in pattern:
        i = int(t * SR)
        ring = metal_ring(f * r.uniform(0.98, 1.02), 1.1 if a > 0.5 else 0.35, n - i, int(r.integers(1 << 30)), 0.9)
        hit = K.bandpass(r.standard_normal(K.ns(0.015)), 4000, 1.0) * 0.6
        x[i:] += ring * a
        x[i:i + len(hit)] += hit * a
    return x


def bellows(seed):
    r = K.rng(seed)
    n = K.ns(1.6)
    t = np.arange(n) / SR
    env = np.sin(np.pi * np.clip(t / 1.3, 0, 1)) ** 1.5
    whoosh = K.lowpass(K.noise(n, "pink", seed), 700, 2) * env
    roar = K.bandpass(K.noise(n, "white", seed + 1), 300, 0.7) * env ** 2 * 0.6
    cr = np.zeros(n)
    c = _creak(r, 0.4)
    cr[:len(c)] += c * 0.3
    return _norm(whoosh) + 0.5 * _norm(roar) + cr


def stall(seed):
    """A stall at work: a crate set down, wares shuffled, a few coins counted out."""
    r = K.rng(seed)
    n = K.ns(1.8)
    x = np.zeros(n)
    kind = seed % 3
    if kind == 0:
        k = wood_knock(r, r.uniform(90, 140)) * 1.3
        x[:len(k)] += k
        rat = hits(K.ns(0.5), 30, seed + 1, lambda q: wood_knock(q, q.uniform(400, 900)) * 0.25)
        x[K.ns(0.05):K.ns(0.05) + len(rat)] += rat
    elif kind == 1:
        sh = K.bandpass(K.noise(K.ns(0.8), "pink", seed), 2500, 0.6) * np.sin(np.linspace(0, np.pi, K.ns(0.8))) * 0.5
        x[:len(sh)] += sh
    t = 0.3 if kind != 2 else 0.0
    for _ in range(int(r.integers(2, 6)) if kind != 1 else 2):
        i = int((t + r.uniform(0, 0.12)) * SR)
        c = metal_ring(r.uniform(3200, 4800), 0.18, K.ns(0.3), int(r.integers(1 << 30)), 0.8) * r.uniform(0.3, 0.7)
        x[i:i + len(c)] += c[: max(0, min(len(c), n - i))]
        t += r.uniform(0.12, 0.25)
    return x


def boat_creak(seed):
    r = K.rng(seed)
    n = K.ns(2.0)
    x = np.zeros(n)
    c = _creak(r, r.uniform(0.6, 1.2))
    x[:len(c)] += c
    k = wood_knock(r, r.uniform(110, 170))
    i = int(r.uniform(0.3, 1.0) * SR)
    x[i:i + len(k)] += k * 0.7
    l2 = lap(r)
    j = int(r.uniform(0.0, 0.5) * SR)
    m = min(len(l2), n - j)
    x[j:j + m] += l2[:m] * 0.5
    return x


def songbird(seed):
    """A small bird's phrase: whistled notes and quick trills, each note a glide (our own songs, five birds)."""
    r = K.rng(seed)
    n = K.ns(2.2)
    x = np.zeros(n)
    t = 0.05
    style = seed % 5
    base = [2600, 3400, 4200, 3000, 5000][style]
    for k in range(int(r.integers(4, 10))):
        dur = r.uniform(0.04, 0.16) if style != 2 else r.uniform(0.02, 0.05)
        L = K.ns(dur)
        f0 = base * r.uniform(0.75, 1.35)
        f1 = f0 * r.uniform(0.7, 1.4)
        f = np.linspace(f0, f1, L)
        if style in (1, 4) and r.random() < 0.5:
            f = f * (1 + 0.08 * np.sin(2 * np.pi * r.uniform(30, 60) * np.arange(L) / SR))  # a trill
        tone = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.15 * np.sin(4 * np.pi * np.cumsum(f) / SR)
        env = np.sin(np.linspace(0, np.pi, L)) ** 0.8
        i = int(t * SR)
        if i + L >= n:
            break
        x[i:i + L] += tone * env
        t += dur + r.uniform(0.01, 0.09) * (3 if k % 3 == 2 else 1)
    return x


def pigeon_flutter(seed):
    """Wings clapping up off the stones: a flurry of flaps (several birds), slowing as they climb."""
    r = K.rng(seed)
    n = K.ns(1.6)
    x = np.zeros(n)
    for b in range(int(r.integers(3, 7))):
        t = r.uniform(0, 0.25)
        rate = r.uniform(9, 13)
        for k in range(int(r.integers(6, 12))):
            L = K.ns(0.035)
            clap = K.bandpass(r.standard_normal(L), r.uniform(900, 1700), 0.9) * np.exp(-np.arange(L) / (0.008 * SR))
            thump = np.sin(2 * np.pi * 120 * np.arange(L) / SR) * np.exp(-np.arange(L) / (0.01 * SR)) * 0.4
            i = int(t * SR)
            if i + L >= n:
                break
            x[i:i + L] += (clap + thump) * (1 - k / 14) * r.uniform(0.6, 1.0)
            t += 1 / rate * (1 + k * 0.05)
    return x


def pigeon_coo(seed):
    """A pigeon's coo: a soft, throaty hoo-OO-oo, rolling in the throat."""
    r = K.rng(seed)
    parts = [(0.18, 1.0), (0.5, 1.12), (0.25, 0.95)][: int(r.integers(2, 4))]
    n = K.ns(sum(p[0] for p in parts) + 0.4)
    x = np.zeros(n)
    t = 0.02
    f0 = r.uniform(330, 420)
    for dur, k in parts:
        L = K.ns(dur)
        tt = np.arange(L) / SR
        f = f0 * k * (1 - 0.06 * tt / dur) * (1 + 0.02 * np.sin(2 * np.pi * 26 * tt))
        ph = 2 * np.pi * np.cumsum(f) / SR
        tone = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)
        env = np.sin(np.pi * tt / dur) ** 1.2 * (0.8 + 0.2 * np.sin(2 * np.pi * 26 * tt))
        i = int(t * SR)
        x[i:i + L] += tone * env
        t += dur + 0.05
    return K.lowpass(x, 1800, 2)


def gull_call(seed):
    """A gull: a run of harsh yelps falling in pitch (kyow, kyow, kyow), or one long laughing call."""
    r = K.rng(seed)
    count = int(r.integers(2, 6))
    n = K.ns(0.32 * count + 0.5)
    x = np.zeros(n)
    t = 0.02
    f0 = r.uniform(1150, 1500)
    for k in range(count):
        dur = r.uniform(0.16, 0.26)
        L = K.ns(dur)
        tt = np.arange(L) / SR
        f = f0 * (1.15 - 0.45 * (tt / dur) ** 0.7) * (1 - 0.05 * k)
        ph = 2 * np.pi * np.cumsum(f) / SR
        # a harsh, nasal voice: rich harmonics through two formants
        tone = sum(np.sin(h * ph) / h ** 0.6 for h in range(1, 9))
        tone = K.bandpass(tone, 1600, 1.2) + 0.6 * K.bandpass(tone, 3000, 1.5)
        env = np.minimum(1, tt / 0.02) * np.exp(-tt / (dur * 0.6))
        i = int(t * SR)
        x[i:i + L] += tone * env
        t += dur + r.uniform(0.05, 0.12)
    return x


def cart_roll(seed):
    """Iron tyres over cobbles: a low rumble, the clack of each stone's edge, the bed's creak; fades in and out."""
    r = K.rng(seed)
    n = K.ns(2.6)
    t = np.arange(n) / SR
    rumble = K.lowpass(K.noise(n, "brown", seed), 140, 2)
    clacks = hits(n, 11, seed + 1, lambda q: K.bandpass(q.standard_normal(K.ns(0.02)), q.uniform(500, 1100), 1.0)
                  * np.exp(-np.arange(K.ns(0.02)) / (0.004 * SR)) * q.uniform(0.3, 1), jitter=0.5)
    creak = np.zeros(n)
    c = _creak(r, 0.8)
    i = int(r.uniform(0.4, 1.2) * SR)
    creak[i:i + len(c)] += c * 0.4
    env = np.sin(np.pi * t / t[-1]) ** 0.8
    return (_norm(rumble) * 0.8 + _norm(clacks) * 0.7 + creak) * env


def cart_bells(seed):
    """The collar's brass bells shaken by a toss of the head: a bright jingle that settles."""
    r = K.rng(seed)
    n = K.ns(1.6)
    x = np.zeros(n)
    t = 0.0
    for k in range(int(r.integers(8, 16))):
        i = int(t * SR)
        f = r.choice([2350, 2790, 3130, 3520]) * r.uniform(0.99, 1.01)
        ring = metal_ring(f, 0.35, n - i, int(r.integers(1 << 30)), 0.5) * (1 - k / 18) * r.uniform(0.4, 1)
        x[i:] += ring
        t += r.uniform(0.02, 0.09) * (1 + k * 0.08)
    return x


# ---- the carillon (shipped mono, from the Choir's spire) ------------------------------------------------------------

SMALL = [(1.0, 1.0, 1.0), (2.0, 0.7, 0.7), (2.4, 0.45, 0.45), (3.0, 0.35, 0.35), (4.0, 0.2, 0.25), (5.2, 0.12, 0.15)]
# our own tunes: dawn rises, noon is the city's theme, dusk settles (MIDI notes, beats)
TUNES = {
    "dawn": [(74, 1), (76, 1), (78, 1), (81, 2), (78, 1), (81, 1), (83, 1), (86, 3)],
    "noon": [(74, 1), (78, 1), (81, 1), (86, 2), (85, 1), (81, 1), (83, 1), (81, 2), (78, 1), (79, 1), (81, 1), (78, 2),
             (76, 1), (74, 1), (76, 1), (74, 3)],
    "dusk": [(86, 2), (83, 1), (81, 1), (78, 2), (76, 1), (78, 1), (74, 4)],
}


def carillon(name, seed):
    beat = {"dawn": 0.42, "noon": 0.36, "dusk": 0.55}[name]
    notes = TUNES[name]
    total = sum(b for _, b in notes) * beat + 6.0
    n = K.ns(total)
    x = np.zeros(n)
    r = K.rng(seed)
    t = 0.2
    for m, b in notes:
        f = 440 * 2 ** ((m - 69) / 12)
        i = int(t * SR)
        L = n - i
        tone = np.zeros(L)
        tt = np.arange(L) / SR
        for ratio, amp, dt in SMALL:
            fr = f * ratio
            t60 = 3.2 * dt * (600 / f) ** 0.5
            tone += amp * np.sin(2 * np.pi * fr * tt + r.uniform(0, 6)) * np.sin(2 * np.pi * (fr + 0.8 * ratio) * tt) ** 2 * 10 ** (-3 * tt / t60)
        strike = K.bandpass(r.standard_normal(K.ns(0.02)), 3000, 1.0) * 0.2
        tone[:len(strike)] += strike
        x[i:] += tone * (1.0 if b > 1 else 0.85)
        t += b * beat * (1 + 0.03 * r.standard_normal())
    # heard across the roofs from high up: a long, open tail
    ir = K.reverb_ir(t60=3.2, predelay=0.05, bright=0.45, width=1.0, early=0.4, size=2.2, seed=seed)
    wet = K.convolve(x, ir)[:n]
    return K.stereo(x) * 0.7 + wet * 0.55


# =====================================================================================================================
# Bed stems (stereo, all STEM_SECONDS long)
# =====================================================================================================================

def stem_air(seed):
    n = K.ns(STEM_SECONDS + 1.5)
    L = K.lowpass(K.noise(n, "pink", seed), 650, 2) * (0.6 + 0.4 * K.smooth_noise(n, 0.12, seed + 1))
    R_ = K.lowpass(K.noise(n, "pink", seed + 7), 650, 2) * (0.6 + 0.4 * K.smooth_noise(n, 0.12, seed + 8))
    hum = K.lowpass(K.noise(n, "brown", seed + 2), 160, 2)
    x = np.stack([L + 0.5 * hum, R_ + 0.5 * hum], axis=1)
    return K.loop_seamless(x, 1.5)


def stem_murmur(seed, people, near, bright):
    sec = STEM_SECONDS + 1.5
    x = crowd(sec, people, near, seed, room=1.0, bright=bright)
    return K.loop_seamless(x[: K.ns(sec)], 1.5)


def stem_bustle(seed):
    """The street's own noise far off: footsteps, a cart passing, crates, doors, a distant hammer."""
    n = K.ns(STEM_SECONDS + 1.5)
    steps = hits(n, 14, seed, lambda r: step(r) * 0.6)
    carts = np.zeros(n)
    r = K.rng(seed + 1)
    for k in range(4):
        c = cart_roll(int(r.integers(1 << 30)))
        c = np.interp(np.arange(len(c) * 3) / 3, np.arange(len(c)), c)  # slower, longer: far off
        i = int(r.uniform(0, STEM_SECONDS - 8) * SR)
        carts[i:i + len(c)] += c[: max(0, min(len(c), n - i))] * 0.6
    knocks = hits(n, 0.6, seed + 2, lambda q: wood_knock(q) * 0.7)
    x = _norm(steps) * 0.6 + _norm(carts) * 0.5 + _norm(knocks) * 0.35
    x = K.lowpass(x, 2600, 1)
    st = np.stack([x * (0.7 + 0.3 * K.smooth_noise(n, 0.2, seed + 3)), np.roll(x, K.ns(0.013)) * (0.7 + 0.3 * K.smooth_noise(n, 0.2, seed + 4))], axis=1)
    ir = K.reverb_ir(t60=1.6, predelay=0.03, bright=0.3, width=1.0, early=0.6, size=1.8, seed=seed)
    st = st + fold(K.convolve(st, ir), n) * 0.4
    return K.loop_seamless(st, 1.5)


def stem_water(seed):
    n = K.ns(STEM_SECONDS + 1.5)
    a = water_flow(n, seed, fc=520, depth=0.5)
    b = water_flow(n, seed + 9, fc=520, depth=0.5)
    laps = hits(n, 0.5, seed + 1, lap) * 0.5
    x = np.stack([a + laps, b + np.roll(laps, K.ns(0.02))], axis=1)
    return K.loop_seamless(x, 1.5)


def stem_works(seed):
    """The Works from a street or two away: hammers on anvils, a saw, the mill's churn."""
    n = K.ns(STEM_SECONDS + 1.5)
    hammers = hits(n, 1.6, seed, lambda r: metal_ring(r.uniform(700, 1100), 0.6, K.ns(0.8), int(r.integers(1 << 30)), 0.7) * r.uniform(0.3, 1))
    r = K.rng(seed + 1)
    saw = np.zeros(n)
    for k in range(5):
        L = K.ns(r.uniform(3, 6))
        tt = np.arange(L) / SR
        s = K.bandpass(r.standard_normal(L), 1700, 1.4) * (0.5 + 0.5 * np.abs(np.sin(np.pi * 1.4 * tt))) * np.sin(np.pi * tt / tt[-1])
        i = int(r.uniform(0, STEM_SECONDS - 6) * SR)
        saw[i:i + L] += s[: max(0, min(L, n - i))]
    churn = water_flow(n, seed + 2, fc=800, depth=0.4)
    x = _norm(hammers) * 0.8 + _norm(saw) * 0.25 + churn * 0.35
    x = K.lowpass(x, 3000, 1)
    st = np.stack([x, np.roll(x, K.ns(0.017))], axis=1)
    ir = K.reverb_ir(t60=1.8, predelay=0.04, bright=0.3, width=1.0, early=0.5, size=2.0, seed=seed)
    st = st * 0.6 + fold(K.convolve(st, ir), n) * 0.5
    return K.loop_seamless(st, 1.5)


def stem_birds(seed):
    n = K.ns(STEM_SECONDS + 1.5)
    L = hits(n, 0.6, seed, lambda r: songbird(int(r.integers(1 << 30))) * r.uniform(0.3, 1))
    R_ = hits(n, 0.6, seed + 1, lambda r: songbird(int(r.integers(1 << 30))) * r.uniform(0.3, 1))
    sparrows = hits(n, 3, seed + 2, lambda r: songbird(2 + 5 * int(r.integers(0, 1000))) * 0.25)
    x = np.stack([L + sparrows * 0.6, R_ + sparrows * 0.4], axis=1)
    ir = K.reverb_ir(t60=0.9, predelay=0.02, bright=0.6, width=1.0, early=0.3, size=1.0, seed=seed)
    x = x + fold(K.convolve(x, ir), n) * 0.25
    return K.loop_seamless(x, 1.5)


def stem_crickets(seed):
    """Night insects in the gardens and the fields under the wall: several crickets, each its own rate."""
    n = K.ns(STEM_SECONDS + 1.5)
    t = np.arange(n) / SR
    st = np.zeros((n, 2))
    r = K.rng(seed)
    for k in range(7):
        carrier = r.uniform(4200, 5200)
        chirp_rate = r.uniform(1.2, 2.4)
        pulses = 3 + int(r.integers(0, 3))
        ph = (t * chirp_rate + r.random()) % 1.0
        gate = np.zeros(n)
        for p in range(pulses):
            c = p * 0.045
            gate += np.exp(-((ph - 0.05 - c) / 0.008) ** 2)
        x = np.sin(2 * np.pi * carrier * t) * gate * (0.4 + 0.6 * np.clip(K.smooth_noise(n, 0.05, seed + k), 0, 1)) / (1 + k * 0.4)
        p_ = r.uniform(-0.9, 0.9)
        a = (p_ + 1) * np.pi / 4
        st[:, 0] += x * np.cos(a)
        st[:, 1] += x * np.sin(a)
    return K.loop_seamless(st, 1.5)


def stem_night_far(seed):
    """The city asleep: an owl now and then, a door, far water, the wind in the wall's crenels."""
    n = K.ns(STEM_SECONDS + 1.5)
    r = K.rng(seed)
    x = np.zeros(n)
    for k in range(3):
        i = int(r.uniform(2, STEM_SECONDS - 4) * SR)
        for j, (dt, f, d) in enumerate(((0, 360, 0.5), (0.9, 340, 0.25), (1.25, 340, 0.6))):
            L = K.ns(d)
            tt = np.arange(L) / SR
            hoot = np.sin(2 * np.pi * f * (1 - 0.05 * tt / d) * tt) * np.sin(np.pi * tt / d) ** 1.5
            ii = i + int(dt * SR)
            x[ii:ii + L] += hoot[: max(0, min(L, n - ii))] * 0.5
    wind = K.bandpass(K.noise(n, "pink", seed + 1), 420, 1.5) * np.clip(K.smooth_noise(n, 0.07, seed + 2), 0, 1) ** 2
    knocks = hits(n, 0.15, seed + 3, lambda q: wood_knock(q) * 0.5)
    x = x + _norm(wind) * 0.5 + knocks
    st = np.stack([x, np.roll(x, K.ns(0.02))], axis=1)
    ir = K.reverb_ir(t60=2.2, predelay=0.05, bright=0.3, width=1.0, early=0.4, size=2.2, seed=seed)
    st = st * 0.5 + fold(K.convolve(st, ir), n) * 0.5
    return K.loop_seamless(st, 1.5)


def stem_tavern_far(seed):
    n = K.ns(STEM_SECONDS + 1.5)
    talk = crowd(STEM_SECONDS + 1.5, 14, 14, seed, room=1.3, bright=0.55)
    talk = talk / (np.max(np.abs(talk)) + 1e-12)
    # the music only now and then, through a door that opens and closes
    tune = K.lowpass(_reel(n, seed + 1), 900, 2) * np.clip(K.smooth_noise(n, 0.05, seed + 2) * 2 - 0.4, 0, 1)
    x = talk[:n] + np.stack([tune, tune], axis=1) * 0.18
    return K.loop_seamless(x[:n], 1.5)


# stem name, renderer, gated RMS dBFS once mastered alone (the manifest gains set the balance per district)
STEMS = [
    ("air", lambda s: stem_air(s), -35.0),
    ("murmur_far", lambda s: stem_murmur(s, 44, 12, 0.6), -33.0),
    ("murmur_near", lambda s: stem_murmur(s, 16, 4, 1.0), -31.0),
    ("bustle", lambda s: stem_bustle(s), -35.0),
    ("water", lambda s: stem_water(s), -34.0),
    ("works", lambda s: stem_works(s), -35.0),
    ("birds", lambda s: stem_birds(s), -37.0),
    ("crickets", lambda s: stem_crickets(s), -38.0),
    ("night_far", lambda s: stem_night_far(s), -38.0),
    ("tavern_far", lambda s: stem_tavern_far(s), -36.0),
]

# Each district's bed by crowd level (calm, light, busy, packed; the server forces the level from how many citizens
# are outdoors in the district now). Night is one bed for the whole city: calm deep night, light the evening's taverns.
DISTRICTS = {
    # name: (stems in this manifest, gains per level)
    "market": (["air", "murmur_far", "murmur_near", "bustle", "birds", "water"],
               [[0.8, 0.5, 0.0, 0.3, 0.4, 0.0], [0.7, 0.85, 0.25, 0.55, 0.3, 0.0], [0.6, 1.0, 0.6, 0.8, 0.2, 0.0], [0.5, 1.0, 1.0, 1.0, 0.15, 0.0]]),
    "bellwatch": (["air", "murmur_far", "murmur_near", "bustle", "birds", "water"],
                  [[0.9, 0.3, 0.0, 0.2, 0.5, 0.1], [0.85, 0.55, 0.1, 0.4, 0.45, 0.1], [0.8, 0.75, 0.3, 0.55, 0.4, 0.1], [0.7, 0.9, 0.5, 0.7, 0.3, 0.1]]),
    "quay": (["air", "murmur_far", "murmur_near", "bustle", "water"],
             [[0.7, 0.25, 0.0, 0.25, 1.0], [0.7, 0.5, 0.1, 0.45, 1.0], [0.6, 0.7, 0.3, 0.65, 0.95], [0.55, 0.85, 0.5, 0.8, 0.9]]),
    "terrace": (["air", "murmur_far", "murmur_near", "bustle", "birds"],
                [[0.9, 0.35, 0.0, 0.15, 0.55], [0.85, 0.5, 0.1, 0.25, 0.5], [0.8, 0.6, 0.25, 0.35, 0.45], [0.75, 0.7, 0.4, 0.45, 0.4]]),
    "works": (["air", "murmur_far", "works", "bustle", "water"],
              [[0.8, 0.25, 0.25, 0.2, 0.25], [0.75, 0.45, 0.65, 0.45, 0.25], [0.7, 0.6, 1.0, 0.6, 0.25], [0.65, 0.7, 1.0, 0.75, 0.25]]),
    "garden": (["air", "murmur_far", "birds", "bustle", "water"],
               [[0.85, 0.2, 0.8, 0.1, 0.2], [0.8, 0.35, 1.0, 0.2, 0.2], [0.75, 0.5, 1.0, 0.3, 0.2], [0.7, 0.6, 0.9, 0.4, 0.2]]),
    "wallring": (["air", "murmur_far", "bustle", "birds", "night_far"],
                 [[1.0, 0.15, 0.1, 0.3, 0.3], [1.0, 0.3, 0.2, 0.3, 0.2], [0.95, 0.45, 0.3, 0.3, 0.15], [0.9, 0.55, 0.4, 0.25, 0.1]]),
    "night": (["air", "crickets", "night_far", "tavern_far", "water", "murmur_far"],
              [[0.7, 0.8, 0.8, 0.0, 0.3, 0.0], [0.65, 0.7, 0.6, 0.6, 0.3, 0.15], [0.6, 0.6, 0.5, 0.9, 0.3, 0.3], [0.6, 0.5, 0.4, 1.0, 0.3, 0.45]]),
}
TITLES = {"market": "Carillon: Choir Square and the Market", "bellwatch": "Carillon: Bellwatch", "quay": "Carillon: Lantern Quay",
          "terrace": "Carillon: the High Terrace", "works": "Carillon: the Works", "garden": "Carillon: Garden Ward",
          "wallring": "Carillon: the Wallring", "night": "Carillon at night"}


def stem_file(name):
    return "layers/layer_city_" + name


# =====================================================================================================================
# Events and data
# =====================================================================================================================

# event id: (files [(name, variants)], subtitle, attenuation, stream)
SHOTS = {
    "city.pigeon.flutter": (("pigeon_flutter", pigeon_flutter, 3), "Pigeons take off", 28, False),
    "city.pigeon.coo": (("pigeon_coo", pigeon_coo, 3), "Pigeon coos", 16, False),
    "city.gull.call": (("gull_call", gull_call, 4), "Gull calls", 56, False),
    "city.cart.roll": (("cart_roll", cart_roll, 2), "Cart rolls", 28, False),
    "city.cart.bells": (("cart_bells", cart_bells, 2), "Harness bells jingle", 22, False),
    "city.anvil": (("anvil", anvil, 4), "Hammer on anvil", 44, False),
    "city.bellows": (("bellows", bellows, 2), "Bellows breathe", 16, False),
    "city.stall": (("stall", stall, 6), "Market stall", 14, False),
    "city.boat": (("boat_creak", boat_creak, 4), "Boat creaks", 18, False),
    "city.songbird": (("songbird", songbird, 5), "Bird sings", 30, False),
}
LOOPS = {
    "city.fountain": ("amb_city_fountain", amb_city_fountain, "Fountain splashes", 18),
    "city.lap": ("amb_city_lap", amb_city_lap, "Water laps at the quay", 16),
    "city.mill": ("amb_city_mill", amb_city_mill, "Mill wheel turns", 26),
    "city.tavern": ("amb_city_tavern", amb_city_tavern, "Tavern noise", 18),
}
CARILLON = {"city.carillon.dawn": "dawn", "city.carillon.noon": "noon", "city.carillon.dusk": "dusk"}


def write_events():
    path = os.path.join(MERGE, "sounds.json")
    ev = json.load(open(path))
    lang_path = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(lang_path))
    for eid, ((name, _, n), sub, att, stream) in SHOTS.items():
        ev[eid] = {"subtitle": "subtitles.awaken." + eid,
                   "sounds": [{"name": f"awaken:city/{name}{k + 1}", "attenuation_distance": att} for k in range(n)]}
        lang["subtitles.awaken." + eid] = sub
    for eid, (name, _, sub, att) in LOOPS.items():
        ev[eid] = {"subtitle": "subtitles.awaken." + eid, "sounds": [{"name": f"awaken:{name}", "attenuation_distance": att}]}
        lang["subtitles.awaken." + eid] = sub
    for eid, tune in CARILLON.items():
        ev[eid] = {"subtitle": "subtitles.awaken.city.carillon",
                   "sounds": [{"name": f"awaken:city/carillon_{tune}", "attenuation_distance": 320, "stream": True}]}
    lang["subtitles.awaken.city.carillon"] = "The Choir's carillon plays"
    for st, _, _ in STEMS:  # the ambience slot reads its stems' files straight from the manifest
        ev.pop("layer.city." + st, None)
    with open(path, "w") as f:
        json.dump(ev, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_manifests():
    os.makedirs(MANIFESTS, exist_ok=True)
    frames = K.ns(STEM_SECONDS)
    for d, (stems, gains) in DISTRICTS.items():
        m = {"schemaVersion": 1, "source": "ambient", "title": TITLES[d], "sampleRate": SR, "frames": frames,
             "stems": [{"name": s, "file": "awaken:" + stem_file(s), "attack": 6, "duck": 8, "release": 10} for s in stems],
             "levels": [{"name": nm, "gains": g} for nm, g in zip(("calm", "tension", "combat", "lord"), gains)]}
        with open(os.path.join(MANIFESTS, d + ".json"), "w") as f:
            json.dump(m, f, indent=2)
            f.write("\n")


def render_beds():
    for k, (name, fn, rms) in enumerate(STEMS):
        x = fn(9100 + k * 37)
        x = x[: K.ns(STEM_SECONDS)]
        y = K.master(x, rms, -3.0)
        y = y[: K.ns(STEM_SECONDS)]
        if len(y) < K.ns(STEM_SECONDS):
            y = np.pad(y, ((0, K.ns(STEM_SECONDS) - len(y)), (0, 0)))
        K.write_ogg(os.path.join(SOUNDS, stem_file(name) + ".ogg"), y, 3, "AWAKEN - Carillon (" + name + ")")
        print(f"  stem {name:11s} RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}")


def render_shots():
    tgt, tol, ceil, q = TARGETS["sfx"]
    for eid, ((name, fn, n), _, _, _) in SHOTS.items():
        for k in range(n):
            x = K.mono(fn(sum(map(ord, name)) * 13 + k * 101))
            y = K.master(x, -22.0, ceil - 0.7)
            K.write_ogg(os.path.join(SOUNDS, f"city/{name}{k + 1}.ogg"), y, q, None)
        print(f"  {name:15s} x{n}")
    at, atol, aceil, aq = TARGETS["amb"]
    for eid, (name, fn, _, _) in LOOPS.items():
        y = K.master(K.mono(fn(sum(map(ord, name)))), at, aceil)
        K.write_ogg(os.path.join(SOUNDS, name + ".ogg"), y, aq, None)
        print(f"  {name:20s} {len(y) / SR:5.1f} s  RMS {K.gated_rms(y):6.1f}")
    for eid, tune in CARILLON.items():
        # mono, so the spire is a place: its 320-block falloff applies (a stereo sound plays unplaced, as loud everywhere)
        y = K.master(K.mono(carillon(tune, 4400 + len(tune))), -21.0, ceil - 0.7)
        K.write_ogg(os.path.join(SOUNDS, f"city/carillon_{tune}.ogg"), y, q, None)
        print(f"  carillon_{tune:5s} {len(y) / SR:5.1f} s")


def main(argv):
    what = argv[0] if argv else "all"
    if what in ("all", "beds"):
        render_beds()
        write_manifests()
    if what in ("all", "shots"):
        render_shots()
    write_events()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
