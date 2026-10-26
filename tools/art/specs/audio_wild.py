"""The voices of the overworld's ambient wildlife (Wild Life): the songbirds on the branches, the woodpecker on its
trunk, owls, crows, ducks, herons, a buzzard circling, squirrels, frogs, crickets in the grass. Synthesised from scratch
with tools/art/audiokit.py (numpy only, seeded); nothing is recorded or copied.

    python3 tools/art/specs/audio_wild.py              render every one-shot, write the events and subtitles
    python3 tools/art/specs/audio_wild.py json         only the events and subtitles
    python3 tools/art/specs/audio_wild.py only crow,wild.owl   only the files of these names or events
    python3 tools/art/specs/audio_wild.py sheet OUT    a spectrogram contact sheet of the rendered files (a row an event)

One-shots only (sounds/wild/<name><n>.ogg, mono, several variants each): each is played at the creature that is seen
to make it, on top of AmbientSounds' stereo bed of real birds and crickets, so they are modest and natural, never
cartoony. They are rendered DRY (Sound Physics adds the room; a whisper of early reflection is all they carry) and
mastered to the events' loudness (docs/sound/PLAN.md: -26 +-3 dBFS gated RMS, <= -6 dBTP; the small close sounds,
chips, taps, plops and rustles, about -30). Event ids never start with `ambient.` (Sound Physics skips those).

How the voices are made:
  - Birdsong is whistles on moving pitch curves: every note a glide (birds almost never hold a pitch), the syrinx's
    harmonics faint, a little pitch jitter and amplitude flutter, sometimes two voices at once (the robin's liquid
    notes). Songs are built from motifs (warbles, trills, slurs, thin high notes), each variant its own phrase.
  - Calls (crow, duck, heron, the owl's ke-wick) are a voiced source (harmonics of a jittery f0, subharmonics for
    roughness, breath noise pulsing with each cycle) through vocal-tract formants that move as the beak opens.
  - The woodpecker is a struck modal body: a sub-millisecond click into a dead branch's few inharmonic modes.
  - Wings and leaves are filtered noise grains; the frog's plop a falling cavity resonance; the cricket pulsed tone.
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
MERGE = os.path.join(ROOT, "src/main/merge/wildlife/assets/awaken")

SR = K.SR
CEIL = -6.0  # true-peak ceiling of every event (dBFS)


def build(out):  # rendered on demand (main), not by build_all
    pass


# =====================================================================================================================
# Helpers
# =====================================================================================================================

def T(n):
    return np.arange(n) / SR


def _sd(r):
    return int(r.integers(1 << 30))


def _unit(x):
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def rise(k):
    return 0.5 - 0.5 * np.cos(np.pi * np.arange(k) / k) if k > 0 else np.zeros(0)


def env(n, a=0.005, rel=0.012):
    """Raised-cosine attack and release over a note of n samples (nothing starts or stops on a corner)."""
    e = np.ones(n)
    ka = min(K.ns(a), n // 2)
    kr = min(K.ns(rel), n - ka)
    if ka:
        e[:ka] = rise(ka)
    if kr:
        e[n - kr:] *= rise(kr)[::-1]
    return e


def smooth(x, ms):
    """Hann moving average `ms` wide, the ends held: rounds the corners of a piecewise curve."""
    k = int(ms * SR / 1000) | 1
    k = min(k, (len(x) // 2) * 2 - 1)
    if k < 3:
        return x
    w = np.hanning(k + 2)[1:-1]
    w /= w.sum()
    p = k // 2
    return np.convolve(np.concatenate([np.full(p, x[0]), x, np.full(p, x[-1])]), w, mode="valid")


def curve(n, pts, ms=4.0, log=True):
    """A curve through (fraction, value) points: straight in log (pitch) or linear, corners rounded over `ms`."""
    u = np.arange(n) / max(n - 1, 1)
    xs = [p[0] for p in pts]
    ys = np.array([p[1] for p in pts], dtype=float)
    y = np.interp(u, xs, np.log(ys) if log else ys)
    if ms:
        y = smooth(y, ms)
    return np.exp(y) if log else y


def lp1(x, fc):
    """Causal one-pole lowpass (no pre-ring ahead of a transient, unlike the kit's zero-phase filters)."""
    a = np.exp(-2 * np.pi * fc / SR)
    L = max(8, int(np.ceil(np.log(1e-4) / np.log(a))))
    return np.convolve(x, (1 - a) * a ** np.arange(L))[:len(x)]


def burst(r, dur, fc, q, tau):
    """A short noise burst: noise filtered first, then shaped (so the filter never rings ahead of the attack)."""
    L = K.ns(dur)
    nz = _unit(K.bandpass(r.standard_normal(L), fc, q))
    return nz * (1 - np.exp(-T(L) / 0.0008)) * np.exp(-T(L) / tau)


class Buf:
    """A mono timeline that grows as sounds are placed on it."""

    def __init__(self):
        self.x = np.zeros(K.ns(1.0))

    def add(self, sig, t, gain=1.0):
        """Adds sig at t seconds; returns the time it ends."""
        i = max(0, K.ns(t))
        need = i + len(sig)
        if need > len(self.x):
            self.x = np.concatenate([self.x, np.zeros(need - len(self.x) + K.ns(0.5))])
        self.x[i:need] += sig * gain
        return t + len(sig) / SR

    def out(self, floor_db=-72.0, tail=0.015):
        x = self.x
        pk = np.max(np.abs(x)) + 1e-12
        idx = np.nonzero(np.abs(x) > pk * K.dbamp(floor_db))[0]
        end = idx[-1] + 1 if len(idx) else len(x)
        return x[:min(len(x), end + K.ns(tail))].copy()


def whisper(x, seed, amt=0.09):
    """The faintest early reflection (ground, a trunk nearby), darker than the voice: not a room, just not a vacuum."""
    r = K.rng(seed)
    dark = K.lowpass(x, 4200, 1)
    y = np.concatenate([x, np.zeros(K.ns(0.03))])
    for d, g in ((r.uniform(0.0045, 0.0075), amt), (r.uniform(0.011, 0.016), amt * 0.55), (r.uniform(0.019, 0.026), amt * 0.3)):
        i = K.ns(d)
        y[i:i + len(x)] += dark * g
    return y


# ---- whistles (birdsong) --------------------------------------------------------------------------------------------

HARM = (1.0, 0.05, 0.012)  # a songbird's syrinx: nearly pure, the beak filters what overtones there are


def tone(f, amps=HARM, seed=0, jitter=0.0015):
    """A whistle on the per-sample pitch curve f; each harmonic fades out before the top of the band."""
    n = len(f)
    r = K.rng(seed)
    if jitter:
        f = f * (1 + jitter * K.smooth_noise(n, 90, seed + 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.zeros(n)
    for h, a in enumerate(amps, 1):
        if a:
            y += a * np.clip((18500 - h * f) / 2500, 0, 1) * np.sin(h * ph + (r.uniform(0, 2 * np.pi) if h > 1 else 0.0))
    return y


def note(dur, pts, amp=1.0, a=0.005, rel=0.012, amps=HARM, seed=0, vib=None, track=0.0, epts=None, ms=4.0,
         jitter=0.0015, am=0.03):
    """One bird note: a glide through pts [(fraction, Hz)], a soft attack and release, optional vibrato (rate Hz,
    depth), loudness leaning with pitch (`track`), an amplitude shape (`epts`) and a little flutter (`am`)."""
    n = K.ns(dur)
    f = curve(n, pts, ms)
    if vib:
        f = f * (1 + vib[1] * np.sin(2 * np.pi * vib[0] * T(n) + K.rng(seed + 3).uniform(0, 6.28)))
    y = tone(f, amps, seed, jitter)
    e = env(n, a, rel)
    if epts:
        e = e * curve(n, epts, 3.0, log=False)
    if track:
        e = e * (f / np.mean(f)) ** track
    if am:
        e = e * (1 + am * K.smooth_noise(n, 60, seed + 7))
    return y * e * amp


# ---- voiced calls (crow, duck, heron, owl) ------------------------------------------------------------------------

def resonance(f, forms, floor=0.035, zeros=()):
    """A vocal tract's gain at f: Lorentzian peaks (centre Hz, bandwidth Hz, level) over a floor, times a notch for
    each zero (centre, bandwidth, depth), the anti-resonance that makes a voice nasal."""
    g = np.full(np.shape(f), floor, dtype=float)
    for fc, bw, lv in forms:
        g = g + lv / (1.0 + ((f - fc) / (0.5 * bw)) ** 2)
    for fc, bw, d in zeros:
        g = g * (1.0 - d / (1.0 + ((f - fc) / (0.5 * bw)) ** 2))
    return g


def voiced(f0, forms, tilt=0.8, jitter=0.012, shimmer=0.12, sub=0.0, breath=0.2, fmax=4500.0, seed=0, shift=None,
           zeros=()):
    """A rough animal voice on the pitch curve f0: harmonics through formants (scaled by `shift` as the beak opens),
    jitter and shimmer, subharmonics coming and going (period doubling: the rasp), and breath noise that pulses with
    each cycle. Harmonics fade out above fmax. Unit RMS."""
    n = len(f0)
    r = K.rng(seed)
    f = f0 * (1 + jitter * (0.75 * K.smooth_noise(n, 160, seed + 1) + 0.25 * K.smooth_noise(n, 25, seed + 2)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    sc = 1.0 if shift is None else shift
    top = 1.25 * fmax
    y = np.zeros(n)
    for h in range(1, int(top / max(np.min(f), 40.0)) + 2):
        fh = h * f
        g = resonance(fh / sc, forms, zeros=zeros) * h ** -tilt * np.clip((top - fh) / (0.25 * fmax), 0, 1)
        y += g * np.sin(h * ph + (r.uniform(0, 2 * np.pi) if h > 1 else 0.0))
    if sub:
        depth = sub * np.clip(0.45 + K.smooth_noise(n, 7, seed + 4), 0, 1)
        for m in range(1, 2 * int(top / max(np.min(f), 40.0)) + 2, 2):
            fh = 0.5 * m * f
            g = resonance(fh / sc, forms, zeros=zeros) * (0.5 * m) ** -tilt * np.clip((top - fh) / (0.25 * fmax), 0, 1)
            y += 0.6 * depth * g * np.sin(0.5 * m * ph + r.uniform(0, 2 * np.pi))
    y = _unit(y * (1 + shimmer * K.smooth_noise(n, 120, seed + 5)))
    if breath:
        gate = (0.5 + 0.5 * np.cos(ph)) ** 4
        lp = K.lp_resp(fmax, 2)
        nz = K.fft_filter(K.noise(n, "white", seed + 6), lambda fr: resonance(fr, forms, zeros=zeros) * lp(fr))
        y = y + breath * _unit(_unit(nz) * (0.35 + 0.65 * gate))
    return y


# ---- wood, leaves, water ------------------------------------------------------------------------------------------

def knock(r, base, modes, hard=1.0, dull=1.0, click=0.5, click_fc=5500):
    """A beak (or a twig) striking wood: a click under a millisecond long, then the wood's few inharmonic modes
    [(ratio, level, t60 s)] ringing out; each strike excites them a little differently. `dull` shortens the ring."""
    L = K.ns(max(m[2] for m in modes) * dull * 1.1 + 0.012)
    tt = T(L)
    y = np.zeros(L)
    for ratio, a, t60 in modes:
        f = base * ratio * (1 + 0.004 * r.standard_normal())
        y += a * r.uniform(0.7, 1.3) * np.exp(-6.91 * tt / (t60 * dull)) * np.sin(2 * np.pi * f * tt)
    y *= 1 - np.exp(-tt / 0.0003)  # contact lasts a fraction of a millisecond: the very top is softened
    y /= np.max(np.abs(y)) + 1e-12
    k = K.ns(0.003)
    c = lp1(r.standard_normal(k) * np.exp(-T(k) / 0.0005), click_fc)
    y[:k] += click * c / (np.max(np.abs(c)) + 1e-12)
    return y * hard


def leaves(r, clumps, count=(5, 14), spread=0.012, lo=1100, hi=6500, swish=0.0):
    """Dry leaves crackling: around each clump time a scatter of tiny resonant ticks (a leaf crumpling is many small
    cracks within a few milliseconds). `swish` adds the soft brush of leaves sliding over each other."""
    n = K.ns(max(clumps) + 6 * spread + 0.03)
    out = np.zeros(n)
    for t0 in clumps:
        for _ in range(int(r.integers(*count))):
            i = K.ns(t0 + abs(r.normal(0, spread)))
            fc = np.exp(r.uniform(np.log(lo), np.log(hi)))
            tau = r.uniform(1.2, 3.5) / (np.pi * fc)
            L = max(16, int(tau * 7 * SR))
            h = np.exp(-T(L) / tau) * np.sin(2 * np.pi * fc * T(L))
            tick = np.convolve(r.standard_normal(int(r.integers(2, 24))), h)
            tick *= min(1.0, 0.15 + 0.35 * r.pareto(2.2)) / (np.max(np.abs(tick)) + 1e-12)
            j = min(n, i + len(tick))
            if i < n:
                out[i:j] += tick[:j - i]
    if swish:
        sw = _unit(K.bandpass(K.noise(n, "pink", _sd(r)), 850, 0.6))
        e = np.zeros(n)
        for t0 in clumps:
            L = K.ns(r.uniform(0.05, 0.09))
            i = K.ns(t0)
            j = min(n, i + L)
            e[i:j] += np.hanning(L)[:j - i]
        out += swish * 0.25 * sw * e
    # crisp, not hissy: the edge comes off 2-5 kHz, the very top rolls away, no rumble
    hp = K.hp_resp(350, 1)
    return K.fft_filter(out, lambda f: hp(f) * (1 - 0.55 / (1 + ((f - 3300) / 1800) ** 2)) / np.sqrt(1 + (f / 7000) ** 2))


def drops(r, b, t0, count, span, lo=1300, hi=2600, level=0.15):
    """Drops falling back onto still water: tiny rising plinks."""
    for _ in range(count):
        L = K.ns(0.03)
        f0 = r.uniform(lo, hi)
        f = f0 * (1 + 0.35 * (1 - np.exp(-T(L) / 0.006)))
        y = tone(f, (1, 0.05), _sd(r), 0) * (1 - np.exp(-T(L) / 0.0006)) * np.exp(-T(L) / r.uniform(0.003, 0.006))
        b.add(y * level * r.uniform(0.5, 1.0), t0 + r.uniform(0, span))


# =====================================================================================================================
# Songbirds
# =====================================================================================================================

# ---- robin: phrases of motifs, each phrase different --------------------------------------------------------------

def _r_flute(b, t, r):
    """The robin's low clear note, the wistful one: a soft whistle that leans up and falls away."""
    f = r.uniform(2350, 3100)
    t = b.add(note(r.uniform(0.12, 0.2), [(0, f * 0.94), (0.2, f), (0.72, f * 1.07), (1, f * 0.86)], r.uniform(0.6, 0.8),
                   a=0.014, rel=0.03, seed=_sd(r), track=0.4), t)
    if r.random() < 0.6:
        g = f * r.uniform(1.45, 1.7)
        t = b.add(note(r.uniform(0.04, 0.06), [(0, g), (1, g * 0.9)], 0.5, a=0.004, rel=0.012, seed=_sd(r)), t + r.uniform(0.02, 0.04))
    return t


def _r_warble(b, t, r):
    """Liquid warbling: one unbroken voice swinging between low and high faster than the ear can follow."""
    k = int(r.integers(3, 7))
    seg = r.uniform(0.028, 0.05, k)
    edges = np.concatenate([[0.0], np.cumsum(seg)]) / seg.sum()
    lo, hi = r.uniform(2500, 3400), r.uniform(4200, 5600)
    first_high = r.random() < 0.5
    pts = [(edges[i], (hi if (i % 2 == 0) == first_high else lo) * r.uniform(0.9, 1.1)) for i in range(k + 1)]
    epts = [(0.0, 0.6)]
    for i in range(k):
        epts.append(((edges[i] + edges[i + 1]) / 2, r.uniform(0.7, 1.0)))
        if i < k - 1:
            epts.append((edges[i + 1], 0.3))
    epts.append((1.0, 0.6))
    return b.add(note(float(seg.sum()), pts, r.uniform(0.75, 0.95), a=0.004, rel=0.01, seed=_sd(r), epts=epts, ms=3.0), t)


def _r_thin(b, t, r):
    """High, thin, silvery notes at the top of the robin's range, soft; sometimes a tiny high trill."""
    if r.random() < 0.4:
        k = int(r.integers(4, 8))
        per = 1 / r.uniform(26, 36)
        f = r.uniform(6400, 7600)
        for i in range(k):
            b.add(note(per * 0.62, [(0, f * 1.04), (1, f * 0.9)], 0.42 * r.uniform(0.85, 1), a=0.002, rel=0.006, seed=_sd(r)), t + i * per)
        return t + k * per
    for _ in range(int(r.integers(1, 4))):
        f = r.uniform(5900, 7800)
        pts = [[(0, f), (1, f * 0.93)], [(0, f * 0.95), (0.6, f * 1.03), (1, f * 0.98)], [(0, f * 1.06), (0.3, f), (1, f)]][int(r.integers(0, 3))]
        t = b.add(note(r.uniform(0.03, 0.075), pts, r.uniform(0.4, 0.6), a=0.004, rel=0.012, seed=_sd(r)), t)
        t += r.uniform(0.025, 0.07)
    return t


def _r_trill(b, t, r):
    """A quick trill: one short down-sweeping element repeated, drifting a little in pitch and pace."""
    k = int(r.integers(3, 7))
    rate, accel = r.uniform(15, 26), r.uniform(-0.15, 0.2)
    hi, lo = r.uniform(4800, 6400), r.uniform(3100, 4000)
    drift = r.uniform(-0.015, 0.015)
    for i in range(k):
        per = 1 / (rate * (1 + accel * i / k)) * r.uniform(0.95, 1.05)
        g = (1 + drift * i) * r.uniform(0.975, 1.025)
        b.add(note(per * r.uniform(0.6, 0.72), [(0, hi * g), (0.15, hi * g * r.uniform(0.96, 1.01)), (1, lo * g * r.uniform(0.96, 1.04))],
                   (0.65 + 0.35 * np.sin(np.pi * (i + 0.5) / k)) * r.uniform(0.85, 1.0), a=0.003, rel=0.008, seed=_sd(r)), t)
        t += per
    return t


def _r_slur(b, t, r):
    """A long falling (or rising) slur with a liquid quiver in it."""
    d = r.uniform(0.12, 0.22)
    if r.random() < 0.65:
        f0, f1 = r.uniform(4600, 5400), r.uniform(2600, 3200)
        pts = [(0, f0), (0.3, f0 * 0.93), (1, f1)]
    else:
        f0, f1 = r.uniform(2700, 3100), r.uniform(4000, 4500)
        pts = [(0, f0), (0.75, f1), (1, f1 * 0.9)]
    return b.add(note(d, pts, r.uniform(0.75, 0.95), a=0.008, rel=0.025, seed=_sd(r), vib=(r.uniform(28, 40), 0.012)), t)


def _r_chevron(b, t, r):
    """Two or three quick up-and-down notes."""
    lo, hi = r.uniform(2800, 3400), r.uniform(5000, 6000)
    for _ in range(int(r.integers(2, 4))):
        t = b.add(note(r.uniform(0.05, 0.08), [(0, lo), (0.45, hi), (1, lo * 1.05)], r.uniform(0.65, 0.85), a=0.004, rel=0.012, seed=_sd(r)), t)
        t += r.uniform(0.02, 0.04)
        lo, hi = lo * r.uniform(0.96, 1.05), hi * r.uniform(0.95, 1.04)
    return t


def _r_twovoice(b, t, r):
    """Both sides of the syrinx at once: one voice falling while the other rises, beating as they cross."""
    d = r.uniform(0.14, 0.22)
    a0, a1 = r.uniform(4300, 4900), r.uniform(3000, 3400)
    b0, b1 = r.uniform(2700, 3000), r.uniform(3700, 4100)
    va = note(d, [(0, a0), (1, a1)], 0.8, a=0.01, rel=0.02, seed=_sd(r))
    vb = note(d, [(0, b0), (1, b1)], 0.5, a=0.02, rel=0.02, seed=_sd(r))
    return b.add(va + vb, t)


ROBIN = {"flute": _r_flute, "warble": _r_warble, "thin": _r_thin, "trill": _r_trill, "slur": _r_slur,
         "chevron": _r_chevron, "twovoice": _r_twovoice}
ROBIN_PLANS = [["flute", "warble", "thin", "trill", "slur"],
               ["slur", "thin", "warble", "chevron", "trill", "thin"],
               ["twovoice", "trill", "warble", "thin", "flute"],
               ["thin", "warble", "slur", "chevron", "thin", "trill"],
               ["chevron", "flute", "twovoice", "warble", "thin", "slur"]]


def robin(v, seed):
    """A European robin's phrase (1.5-3 s): high thin notes, liquid warbles and trills, pauses inside the phrase."""
    r = K.rng(seed)
    b = Buf()
    t = 0.008
    limit = r.uniform(2.0, 2.7)
    plan = list(ROBIN_PLANS[v])
    while len(plan) < 9:
        plan.append(["thin", "trill", "warble"][int(r.integers(0, 3))])
    for k, m in enumerate(plan):
        if t > limit or (k >= len(ROBIN_PLANS[v]) and t > 1.9):
            break
        t = ROBIN[m](b, t, r) + r.uniform(0.05, 0.15)
    return whisper(b.out(), seed)


# ---- tits ---------------------------------------------------------------------------------------------------------

def tit(v, seed):
    """A great tit's tea-cher tea-cher (clear whistles, the pitch alternating), or a blue tit's tsee-tsee and trill."""
    r = K.rng(seed)
    b = Buf()
    t = 0.008
    if v == 3:  # blue tit: tsee tsee tsu-hu-hu-hu-hu
        for _ in range(2):
            f = r.uniform(7000, 7500)
            t = b.add(note(0.065, [(0, f), (1, f * 0.9)], 0.55, a=0.005, rel=0.015, seed=_sd(r)), t) + r.uniform(0.07, 0.1)
        f = r.uniform(5400, 5800)
        t = b.add(note(0.06, [(0, f * 1.03), (1, f * 0.9)], 0.7, a=0.004, rel=0.015, seed=_sd(r)), t) + 0.06
        k = int(r.integers(8, 12))
        rate, f = r.uniform(13, 16), r.uniform(4300, 4700)
        for i in range(k):
            b.add(note(0.045, [(0, f * 1.06), (0.3, f), (1, f * 0.9)], 0.9 * (1 - 0.3 * max(0, i - k + 4) / 4),
                       a=0.003, rel=0.012, seed=_sd(r)), t)
            t += 1 / rate * r.uniform(0.97, 1.03)
        return whisper(b.out(), seed)
    hi = [5800, 5300, 5050][v] * r.uniform(0.98, 1.02)
    lo = [4100, 3800, 4250][v] * r.uniform(0.98, 1.02)
    pattern = [["T", "C"], ["T", "C", "C"], ["C", "T"]][v]
    reps = [4, 3, 4][v]
    period = [0.30, 0.40, 0.34][v]
    for i in range(reps):
        tt = t
        amp = [0.75, 0.95, 1.0, 0.92, 0.85][i]
        for s in pattern:
            g = r.uniform(0.99, 1.01)
            if s == "T":
                y = note(r.uniform(0.07, 0.09), [(0, hi * 0.9 * g), (0.12, hi * 1.01 * g), (1, hi * 0.97 * g)], amp,
                         a=0.004, rel=0.016, amps=(1, 0.035, 0.008), seed=_sd(r))
            else:
                y = note(r.uniform(0.085, 0.11), [(0, lo * 1.12 * g), (0.22, lo * g), (1, lo * 0.955 * g)], amp * 0.92,
                         a=0.004, rel=0.018, amps=(1, 0.035, 0.008), seed=_sd(r))
            tt = b.add(y, tt) + r.uniform(0.022, 0.035)
        t += period * r.uniform(0.97, 1.03)
    return whisper(b.out(), seed)


# ---- chaffinch ----------------------------------------------------------------------------------------------------

def finch(v, seed):
    """A chaffinch: a run of chips descending and speeding up, a faster lower run, and the flourish at the end."""
    r = K.rng(seed)
    b = Buf()
    t = 0.008
    n1 = [8, 10, 8, 9][v]
    f = r.uniform(6200, 7000)
    gap0, gap1 = r.uniform(0.135, 0.15), r.uniform(0.095, 0.105)
    for i in range(n1):
        u = i / max(1, n1 - 1)
        b.add(note(r.uniform(0.035, 0.045), [(0, f * 1.05), (0.3, f), (1, f * 0.62)], 0.6 + 0.3 * u, a=0.003, rel=0.01, seed=_sd(r)), t)
        t += gap0 + (gap1 - gap0) * u
        f *= r.uniform(0.965, 0.98)
    t += 0.04
    n2 = [6, 6, 5, 6][v]
    f = r.uniform(4400, 5000)
    for i in range(n2):  # the second run: lower, quicker, each note a little hooked chevron
        u = i / max(1, n2 - 1)
        b.add(note(0.034, [(0, f * 0.9), (0.2, f * 1.06), (1, f * 0.62)], 0.9 + 0.1 * u, a=0.002, rel=0.009, seed=_sd(r)), t)
        t += 0.088 - 0.022 * u
        f *= 0.985
    if v in (0, 2):  # a third, quicker run
        f = r.uniform(3600, 4000)
        for i in range(3):
            b.add(note(0.025, [(0, f * 1.1), (1, f * 0.7)], 0.85, a=0.002, rel=0.008, seed=_sd(r)), t)
            t += 0.055
    t += 0.03
    # the flourish: chew-ee-oo, a dip, a leap and a long fall
    flo = [[(0, 3600), (0.2, 2400), (0.38, 2200), (0.5, 5400), (0.62, 4600), (1.0, 2000)],
           [(0, 3300), (0.25, 2200), (0.45, 4800), (0.6, 4400), (1.0, 1900)],
           [(0, 3900), (0.18, 2600), (0.32, 2500), (0.46, 5700), (0.58, 5000), (0.8, 3000), (1.0, 2300)],
           [(0, 3500), (0.3, 2300), (0.5, 5200), (1.0, 2100)]][v]
    d = r.uniform(0.3, 0.38)
    t = b.add(note(d, [(p, q * r.uniform(0.97, 1.03)) for p, q in flo], 1.0, a=0.006, rel=0.04, seed=_sd(r), ms=6.0,
                   epts=[(0, 0.6), (0.35, 0.5), (0.5, 1.0), (1, 0.45)]), t)
    if v in (0, 3):  # and a last "kik"
        b.add(note(0.022, [(0, 6000), (1, 5000)], 0.55, a=0.002, rel=0.008, seed=_sd(r)), t + 0.035)
    return whisper(b.out(), seed)


# ---- wren ---------------------------------------------------------------------------------------------------------

def _w_notes(b, t, r):
    for _ in range(int(r.integers(3, 6))):
        f = r.uniform(5000, 7500)
        shape = [[(0, f), (1, f * 0.88)], [(0, f * 0.9), (0.5, f * 1.05), (1, f * 0.93)], [(0, f * 0.92), (1, f * 1.04)]][int(r.integers(0, 3))]
        t = b.add(note(r.uniform(0.03, 0.06), shape, r.uniform(0.8, 1.0), a=0.003, rel=0.01, seed=_sd(r)), t) + r.uniform(0.02, 0.04)
    return t


def _w_trill(b, t, r):
    rate = r.uniform(18, 35)
    k = int(r.uniform(0.25, 0.45) * rate)
    f = r.uniform(4200, 6800)
    kind = int(r.integers(0, 3))
    for i in range(k):
        g = f * (1 + 0.01 * i * r.uniform(-1, 1))
        pts = [[(0, g * 1.2), (1, g * 0.82)], [(0, g * 0.85), (1, g * 1.15)], [(0, g * 0.85), (0.5, g * 1.15), (1, g * 0.9)]][kind]
        b.add(note(0.62 / rate, pts, r.uniform(0.85, 1.0), a=0.002, rel=0.006, seed=_sd(r)), t)
        t += 1 / rate
    return t


def _w_rattle(b, t, r):
    """The wren's rattle: tiny burry elements so fast they fuse."""
    rate = r.uniform(34, 44)
    k = int(r.uniform(0.25, 0.4) * rate)
    f = r.uniform(3900, 4700)
    for i in range(k):
        g = f * r.uniform(0.97, 1.03)
        b.add(note(0.55 / rate, [(0, g * 1.25), (0.6, g * 0.9), (1, g * 0.82)], r.uniform(0.75, 1.0), a=0.0015, rel=0.004,
                   amps=(1, 0.12, 0.03), seed=_sd(r), ms=1.5), t)
        t += 1 / rate * r.uniform(0.96, 1.04)
    return t


def _w_run(b, t, r):
    k = int(r.integers(6, 11))
    rate = r.uniform(22, 28)
    f0, f1 = r.uniform(6800, 7600), r.uniform(4000, 4500)
    for i in range(k):
        f = f0 * (f1 / f0) ** (i / (k - 1))
        b.add(note(0.6 / rate, [(0, f * 1.1), (1, f * 0.9)], 0.9, a=0.002, rel=0.006, seed=_sd(r)), t)
        t += 1 / rate
    return t


def _w_buzz(b, t, r):
    d = r.uniform(0.12, 0.18)
    f = r.uniform(4600, 5400)
    return b.add(note(d, [(0, f), (1, f * 0.95)], 0.85, a=0.006, rel=0.015, seed=_sd(r), vib=(r.uniform(60, 80), 0.09)), t)


WREN = {"notes": _w_notes, "trill": _w_trill, "rattle": _w_rattle, "run": _w_run, "buzz": _w_buzz}
WREN_PLANS = [["notes", "trill", "rattle", "notes", "trill", "run", "notes", "trill"],
              ["trill", "notes", "rattle", "trill", "buzz", "notes", "run", "trill"],
              ["notes", "rattle", "trill", "run", "rattle", "notes", "trill", "notes"],
              ["run", "trill", "notes", "trill", "rattle", "buzz", "notes", "trill", "run"]]


def wren(v, seed):
    """A wren: loud for its size, trills and rattles and bright notes almost without a breath (2-4 s)."""
    r = K.rng(seed)
    b = Buf()
    t = 0.008
    end = [2.6, 2.9, 3.2, 3.4][v]
    for m in WREN_PLANS[v]:
        if t > end:
            break
        t = WREN[m](b, t, r) + r.uniform(0.02, 0.06)
    return whisper(b.out(), seed)


# ---- contact calls ------------------------------------------------------------------------------------------------

def chip(v, seed):
    """One soft contact call: a robin's tic, a tsip, a chaffinch's pink, a tsee-tsip, a chuk, a tsi-tsi-tsi."""
    r = K.rng(seed)
    b = Buf()
    t = 0.005
    if v == 0:
        b.add(note(0.016, [(0, 8200), (1, 3600)], a=0.0015, rel=0.006, amps=(1, 0.1), seed=_sd(r), ms=1.0), t)
        b.add(note(0.014, [(0, 7600), (1, 3800)], 0.55, a=0.0015, rel=0.006, amps=(1, 0.1), seed=_sd(r), ms=1.0), t + 0.065)
    elif v == 1:
        b.add(note(0.06, [(0, 7600), (0.3, 7900), (1, 6300)], a=0.006, rel=0.018, seed=_sd(r)), t)
    elif v == 2:
        b.add(note(0.055, [(0, 3800), (0.25, 5200), (0.5, 4800), (1, 4300)], amps=(1, 0.18, 0.05), a=0.003, rel=0.02, seed=_sd(r)), t)
    elif v == 3:
        t = b.add(note(0.05, [(0, 7200), (1, 6600)], 0.8, a=0.005, rel=0.014, seed=_sd(r)), t) + 0.06
        b.add(note(0.04, [(0, 6700), (0.4, 6900), (1, 6000)], a=0.004, rel=0.012, seed=_sd(r)), t)
    elif v == 4:
        y = note(0.045, [(0, 3300), (0.2, 3000), (1, 2100)], amps=(1, 0.45, 0.2, 0.08), a=0.002, rel=0.02, seed=_sd(r))
        y[:K.ns(0.01)] += 0.25 * K.highpass(burst(r, 0.01, 2400, 2.0, 0.002), 1200, 2)
        b.add(y, t)
    else:
        for i in range(3):
            f = r.uniform(6800, 7200)
            b.add(note(0.025, [(0, f * 1.02), (1, f * 0.95)], 0.8 + 0.1 * i, a=0.003, rel=0.009, seed=_sd(r)), t + i * 0.06)
    return b.out()


# ---- wings --------------------------------------------------------------------------------------------------------

def _beat_small(r, fc, per):
    """One small wingbeat: a puff of air off the feathers, the downstroke quick, the recovery slower."""
    L = K.ns(per * 1.4)
    tt = T(L)
    nz = _unit(K.lowpass(K.bandpass(r.standard_normal(L), fc, 0.7), 2500, 2))
    low = _unit(K.lowpass(r.standard_normal(L), 380, 2))
    return (nz + 0.5 * low) * (1 - np.exp(-tt / (per * 0.12))) * np.exp(-tt / (per * 0.32))


def flush(v, seed):
    """A small bird bursting off a branch: a fast whirr of tiny wingbeats (one bird, or two or three together),
    the leaves it leaves shaking, sometimes an alarm tick."""
    r = K.rng(seed)
    b = Buf()
    b.add(leaves(r, [0.0, 0.015], count=(4, 9), spread=0.008) * 0.35, 0.004)
    for i in range(int([1, 2, 3, 1][v])):
        t = 0.008 + (0.0 if i == 0 else r.uniform(0.03, 0.14) * i)
        rate = r.uniform(17, 24)
        k = max(5, int(r.uniform(0.28, 0.48) * rate))
        fc = r.uniform(850, 1300)
        loud = 1.0 if i == 0 else r.uniform(0.55, 0.85)
        for j in range(k):
            u = j / k
            amp = loud * min(1.0, 0.65 + 0.2 * j) * np.exp(-1.8 * u) * r.uniform(0.82, 1.0)
            b.add(_beat_small(r, fc * (1 - 0.3 * u), 1 / rate), t, amp)
            t += (1 / rate) * (1 + 0.04 * r.standard_normal())
    if v in (1, 3):
        b.add(note(0.018, [(0, 6800), (1, 3600)], 0.3, a=0.0015, rel=0.006, amps=(1, 0.1), seed=_sd(r), ms=1.0), r.uniform(0.0, 0.04))
    return b.out()


def _whump(r, dark):
    """One heavy downstroke: a push of air (low, breathy), a soft thump in the chest, the feathers on the way up."""
    L = K.ns(0.42)
    tt = T(L)
    e = (1 - np.exp(-tt / 0.028)) * np.exp(-tt / 0.1)
    air = _unit(K.lowpass(K.noise(L, "pink", _sd(r)), 700 * dark, 2))
    low = _unit(K.bandpass(K.noise(L, "white", _sd(r)), 210 * dark, 1.1))
    thump = np.sin(2 * np.pi * r.uniform(70, 95) * tt) * (1 - np.exp(-tt / 0.006)) * np.exp(-tt / 0.045)
    sw_e = np.where((tt > 0.15) & (tt < 0.32), np.sin(np.pi * np.clip((tt - 0.15) / 0.17, 0, 1)) ** 2, 0.0)
    swish = _unit(K.bandpass(K.noise(L, "white", _sd(r)), 1500 * dark, 0.9))
    return (air * 0.9 + low * 0.6) * e + thump * 0.5 + swish * sw_e * 0.13


def wings_big(v, seed):
    """A large bird taking off: a few slow, heavy, airy wingbeats (about three a second), getting farther."""
    r = K.rng(seed)
    b = Buf()
    t = 0.01
    if v == 0:  # off the shallows: water running off it
        drops(r, b, 0.05, 6, 0.45, level=0.12)
    elif v == 2:  # off a branch: the push, the bough knocking, its leaves
        b.add(knock(r, r.uniform(150, 190), [(1.0, 1.0, 0.12), (2.3, 0.5, 0.07), (3.7, 0.25, 0.04)], hard=0.35, click=0.2, click_fc=2500), 0.004)
        b.add(leaves(r, [0.02, 0.09, 0.2], count=(6, 14), spread=0.02, swish=0.6) * 0.3, 0.004)
        t = 0.04
    rate = r.uniform(2.6, 3.4)
    beats = [4, 5, 3][v]
    for k in range(beats):
        amp = [0.85, 1.0, 0.9, 0.78, 0.66][k] * r.uniform(0.9, 1.05)
        b.add(_whump(r, 1 - 0.1 * k), t, amp)
        t += 1 / rate * (1.08 if k == 0 else 1.0) * (1 + 0.04 * r.standard_normal())
    return b.out()


# =====================================================================================================================
# Woodpecker
# =====================================================================================================================

# dead wood: base Hz, modes (ratio, level, t60 s); the hollow ones carry a low cavity mode that rings longest
TREES = [
    (430, [(0.62, 0.5, 0.2), (1.0, 1.0, 0.16), (1.38, 0.8, 0.12), (1.71, 0.6, 0.1), (2.06, 0.45, 0.08), (2.62, 0.3, 0.06), (3.4, 0.15, 0.04)]),
    (640, [(1.0, 1.0, 0.1), (1.29, 0.7, 0.08), (1.55, 0.6, 0.07), (2.1, 0.35, 0.05), (2.9, 0.2, 0.03)]),
    (780, [(1.0, 1.0, 0.09), (1.17, 0.6, 0.07), (1.43, 0.7, 0.06), (1.9, 0.4, 0.05), (2.6, 0.25, 0.035), (3.5, 0.1, 0.025)]),
    (520, [(0.7, 0.45, 0.17), (1.0, 1.0, 0.14), (1.24, 0.9, 0.11), (1.66, 0.5, 0.08), (2.3, 0.3, 0.05), (3.1, 0.15, 0.03)]),
]


def woodpecker_drum(v, seed):
    """A great spotted woodpecker drumming: 12-20 strikes at 15-20 a second into resonant dead wood, speeding up a
    little and dying away."""
    r = K.rng(seed)
    base, modes = TREES[v]
    base *= r.uniform(0.97, 1.03)
    b = Buf()
    count = [15, 18, 12, 20][v]
    i0, i1 = 1 / r.uniform(14.5, 16.5), 1 / r.uniform(18.5, 21.0)
    t = 0.005
    for k in range(count):
        u = k / (count - 1)
        amp = min(1.0, 0.75 + 0.15 * k) * (1 - 0.55 * u ** 1.6) * r.uniform(0.9, 1.06)
        b.add(knock(r, base, modes, click=0.55), t, amp)
        t += (i0 + (i1 - i0) * u ** 0.8) * (1 + 0.02 * r.standard_normal())
    return b.out()


def woodpecker_tap(v, seed):
    """A woodpecker feeding: slow, irregular pecks into live bark (duller, shorter), a crumb of bark now and then."""
    r = K.rng(seed)
    base = r.uniform(560, 760)
    modes = [(1.0, 1.0, 0.07), (1.35, 0.7, 0.05), (1.8, 0.4, 0.035), (2.6, 0.2, 0.02)]
    b = Buf()
    t = 0.005
    count = [4, 6, 3][v]
    for k in range(count):
        b.add(knock(r, base * r.uniform(0.98, 1.02), modes, hard=r.uniform(0.6, 1.0), dull=r.uniform(0.4, 0.6), click=0.35, click_fc=4000), t)
        if r.random() < 0.45:
            b.add(leaves(r, [0.0], count=(2, 6), spread=0.015, lo=900, hi=4000) * 0.15, t + r.uniform(0.02, 0.05))
        t += 0.085 if (v == 1 and k == 2) else r.uniform(0.18, 0.45) * (1.3 if v == 2 else 1.0)
    return b.out()


# =====================================================================================================================
# Calls: crow, buzzard, owl, duck, heron
# =====================================================================================================================

CROW = [(900, 300, 0.8), (1420, 340, 1.0), (2050, 420, 0.4), (2800, 500, 0.1)]
CROW_NASAL = [(1150, 170, 0.5)]


def caw(r, dur, f0, fall=0.22, rough=1.0):
    """One kraa: a rough, nasal low-mid voice, the beak opening at the start, the pitch falling at the end."""
    n = K.ns(dur)
    f = curve(n, [(0, f0 * 0.8), (0.1, f0), (0.4, f0 * 1.07), (0.75, f0 * (1 - fall * 0.5)), (1, f0 * (1 - fall))], ms=10)
    shift = curve(n, [(0, 0.78), (0.15, 1.0), (0.75, 1.0), (1, 0.86)], log=False)
    y = voiced(f, CROW, tilt=0.5, jitter=0.035 * rough, shimmer=0.25 * rough, sub=0.6 * rough, breath=0.55, fmax=4000,
               seed=_sd(r), shift=shift, zeros=CROW_NASAL)
    y = y * env(n, 0.015, 0.09) * curve(n, [(0, 0.7), (0.12, 1.0), (0.7, 0.85), (1, 0.6)], log=False)
    y[:K.ns(0.012)] += 0.3 * burst(r, 0.012, 1500, 1.2, 0.004)
    return y


def crow(v, seed):
    """A carrion crow: two to four harsh kraa calls."""
    r = K.rng(seed)
    f0 = [470, 430, 500, 455, 410][v] * r.uniform(0.97, 1.03)
    calls = [3, 4, 2, 3, 4][v]
    dur = [0.38, 0.34, 0.44, 0.28, 0.36][v]
    gap = [0.78, 0.62, 0.9, 0.5, 0.7][v]
    rough = [1.0, 0.9, 1.0, 0.8, 1.35][v]
    b = Buf()
    t = 0.006
    for k in range(calls):
        b.add(caw(r, dur * r.uniform(0.9, 1.08), f0 * (1 - 0.025 * k) * r.uniform(0.98, 1.02), r.uniform(0.18, 0.28), rough),
              t, [1.0, 0.95, 0.88, 0.8][k] * r.uniform(0.9, 1.0))
        t += gap * r.uniform(0.9, 1.12)
    y = K.lowpass(K.highpass(b.out(), 220, 2), 3400, 2)
    return whisper(y, seed)


BUZZARD = [
    (1.25, [(0, 2150), (0.07, 2600), (0.2, 2550), (0.6, 2050), (1, 1500)]),
    (1.4, [(0, 2000), (0.1, 2500), (0.32, 2450), (0.55, 2250), (0.62, 2050), (1, 1450)]),
    (1.05, [(0, 2300), (0.06, 2700), (0.35, 2400), (1, 1550)]),
]


def buzzard(v, seed):
    """A common buzzard's mew from high up: peee-yoo, a long falling whistle with a hoarse edge, thin with distance."""
    r = K.rng(seed)
    dur, pts = BUZZARD[v]
    n = K.ns(dur)
    f = curve(n, [(p, q * r.uniform(0.97, 1.03)) for p, q in pts], ms=25)
    f = f * (1 + 0.005 * np.sin(2 * np.pi * r.uniform(5, 7) * T(n)) + 0.006 * K.smooth_noise(n, 50, seed))
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(ph) + 0.16 * np.sin(2 * ph + 1.1) + 0.05 * np.sin(3 * ph + 2.3)
    hoarse = _unit(K.lowpass(K.noise(n, "white", seed + 1), 120, 2))
    y = y + 0.22 * hoarse * np.sin(ph + 0.7)
    e = env(n, 0.06, 0.22) * curve(n, [(0, 0.5), (0.1, 1.0), (0.55, 0.9), (1, 0.45)], log=False)
    y = y * e * (1 + 0.12 * K.smooth_noise(n, 45, seed + 2))
    y = K.lowpass(K.highpass(y, 900, 2), 6000, 1)
    return whisper(np.concatenate([np.zeros(K.ns(0.005)), y]), seed, 0.06)


OWL_FORMS = [(1500, 600, 1.0), (2500, 700, 0.35)]


def hoot(dur, f0, seed, trem=None, amp=1.0):
    """A tawny owl's hoot: soft and breathy, an 'h' of air before the tone; `trem` (rate from, to, depth from, to)
    quavers it, deep at first (hu-hu-hu) easing into the long held note."""
    r = K.rng(seed)
    n = K.ns(dur)
    f = curve(n, [(0, f0 * 0.9), (0.12, f0), (0.6, f0 * 1.015), (1, f0 * 0.95)], ms=12)
    if trem:
        rate = np.linspace(trem[0], trem[1], n)
        depth = np.linspace(trem[2], trem[3], n)
        q = 2 * np.pi * np.cumsum(rate) / SR
        am = 1 - depth * (0.5 - 0.5 * np.cos(q))
        f = f * (1 + 0.015 * depth * np.sin(q))
    else:
        am = 1 + 0.04 * K.smooth_noise(n, 8, seed)
    y = tone(f, (1, 0.1, 0.025), seed, jitter=0.003)
    breath = _unit(K.bandpass(K.noise(n, "white", seed + 1), f0, 4.0)) + 0.5 * _unit(K.bandpass(K.noise(n, "white", seed + 2), 2 * f0, 4.0))
    y = (y + 0.08 * breath) * env(n, 0.05, min(0.15, dur * 0.3)) * am
    h = K.ns(0.05)  # the breath that starts it
    y[:h] += 0.12 * _unit(K.lowpass(r.standard_normal(h), 1200, 2)) * np.hanning(h)
    return y * amp


def owl(v, seed):
    """A tawny owl: the male's hoooo ... hu, hu-hu-hoooooo, or the female's ke-wick (2-4 s)."""
    r = K.rng(seed)
    b = Buf()
    if v == 3:  # ke-wick, twice
        f0 = r.uniform(1150, 1300)
        t = 0.006
        for k in range(2):
            g = f0 * (1 - 0.04 * k)
            n1 = K.ns(0.055)
            ke = voiced(curve(n1, [(0, g * 1.05), (1, g * 0.95)]), OWL_FORMS, tilt=0.9, jitter=0.02, shimmer=0.15, sub=0.2,
                        breath=0.3, fmax=4000, seed=_sd(r)) * env(n1, 0.006, 0.02)
            n2 = K.ns(0.2)
            wick = voiced(curve(n2, [(0, g * 0.85), (0.4, g * 1.35), (0.65, g * 1.3), (1, g)], ms=8), OWL_FORMS, tilt=0.9,
                          jitter=0.025, shimmer=0.18, sub=0.3, breath=0.3, fmax=4000, seed=_sd(r))
            wick *= env(n2, 0.01, 0.06) * curve(n2, [(0, 0.7), (0.4, 1.0), (1, 0.5)], log=False)
            b.add(ke * 0.7, t)
            b.add(wick, t + 0.095)
            t += r.uniform(1.55, 1.85)
        y = K.lowpass(K.highpass(b.out(), 500, 2), 3800, 2)
        return whisper(y, seed)
    f0 = [440, 390, 480][v] * r.uniform(0.98, 1.02)
    if v == 0:
        t = b.add(hoot(0.68, f0, _sd(r)), 0.006)
        t = b.add(hoot(0.09, f0 * 0.97, _sd(r), amp=0.55), t + 0.62)
        b.add(hoot(1.25, f0 * 1.02, _sd(r), trem=(8.5, 13, 0.85, 0.3)), t + 0.2)
    elif v == 1:
        t = b.add(hoot(0.55, f0, _sd(r)), 0.006)
        t = b.add(hoot(0.08, f0 * 0.96, _sd(r), amp=0.5), t + 0.5)
        b.add(hoot(1.0, f0 * 1.03, _sd(r), trem=(9.5, 12, 0.9, 0.35)), t + 0.16)
    else:
        t = b.add(hoot(0.1, f0 * 0.97, _sd(r), amp=0.55), 0.006)
        b.add(hoot(1.5, f0, _sd(r), trem=(8.0, 12.5, 0.85, 0.25)), t + 0.22)
    return whisper(K.lowpass(K.highpass(b.out(), 150, 2), 2500, 2), seed)


DUCK = [(950, 220, 1.0), (1750, 280, 0.8), (2600, 380, 0.3), (350, 120, 0.25)]
DUCK_NASAL = [(620, 200, 0.85)]


def quack(r, dur, f0, amp=1.0, soft=0.0):
    """One quack: a buzzy, reedy source through a nasal tract that opens (kwaa) and closes again. `soft` makes it the
    low conversational kwek."""
    n = K.ns(dur)
    f = curve(n, [(0, f0 * 0.95), (0.15, f0 * 1.08), (0.5, f0), (1, f0 * 0.8)], ms=6)
    shift = curve(n, [(0, 0.62), (0.18, 1.0 - 0.2 * soft), (0.6, 0.95 - 0.2 * soft), (1, 0.75)], log=False)
    y = voiced(f, DUCK, tilt=0.45 + 0.4 * soft, jitter=0.02, shimmer=0.15, sub=0.2, breath=0.22 + 0.2 * soft, fmax=4200,
               seed=_sd(r), shift=shift, zeros=DUCK_NASAL)
    y = y * env(n, 0.01, 0.05) * curve(n, [(0, 0.6), (0.15, 1.0), (0.6, 0.75), (1, 0.4)], log=False)
    y[:K.ns(0.008)] += 0.2 * burst(r, 0.008, 1800, 1.0, 0.002)
    return y * amp


def duck(v, seed):
    """A mallard: a run of quacks falling away (QUACK quack quack quack), or a soft conversational chuckle."""
    r = K.rng(seed)
    b = Buf()
    t = 0.006
    if v == 4:
        for k in range(int(r.integers(8, 12))):
            b.add(quack(r, r.uniform(0.06, 0.09), r.uniform(220, 260), r.uniform(0.35, 0.65), soft=1.0), t)
            t += r.uniform(0.1, 0.14) if r.random() < 0.65 else r.uniform(0.2, 0.3)
        return whisper(K.lowpass(K.highpass(b.out(), 180, 2), 3600, 2), seed)
    count = [5, 3, 2, 4][v]
    f0 = [330, 350, 310, 300][v] * r.uniform(0.97, 1.03)
    space = [0.27, 0.24, 0.32, 0.2][v]
    for k in range(count):
        u = k / max(1, count - 1)
        d = [0.22, 0.2, 0.26, 0.18][v] * (1 - 0.3 * u) * r.uniform(0.95, 1.05)
        b.add(quack(r, d, f0 * (1 - 0.03 * k), (1 - 0.5 * u) * r.uniform(0.92, 1.0)), t)
        t += space * (1 - 0.1 * u) * r.uniform(0.95, 1.05)
    return whisper(K.lowpass(K.highpass(b.out(), 180, 2), 3600, 2), seed)


HERON = [(560, 220, 1.0), (1100, 260, 0.8), (1750, 350, 0.45), (2500, 500, 0.15)]


def heron(v, seed):
    """A grey heron's fraank: one harsh, low, rough croak, a rolled r at its start."""
    r = K.rng(seed)
    dur = [0.55, 0.66][v] * r.uniform(0.95, 1.05)
    f0 = [190, 172][v] * r.uniform(0.97, 1.03)
    n = K.ns(dur)
    f = curve(n, [(0, f0 * 0.85), (0.1, f0 * 1.05), (0.6, f0), (1, f0 * 0.75)], ms=10)
    if v == 0:
        shift = curve(n, [(0, 0.85), (0.12, 1.0), (0.8, 0.97), (1, 0.85)], log=False)
        zeros = ()
    else:  # kraaank: closing to a nasal end
        shift = curve(n, [(0, 0.85), (0.12, 1.02), (0.7, 0.98), (1, 0.72)], log=False)
        zeros = [(800, 250, 0.7)]
    y = voiced(f, HERON, tilt=0.6, jitter=0.05, shimmer=0.3, sub=0.7, breath=0.5, fmax=3600, seed=_sd(r), shift=shift, zeros=zeros)
    flutter = 1 + 0.25 * K.smooth_noise(n, 35, seed + 3)
    roll = 1 - 0.35 * np.clip(1 - T(n) / 0.06, 0, 1) * (0.5 + 0.5 * np.sin(2 * np.pi * 28 * T(n)))
    y = y * env(n, 0.015, 0.12) * flutter * roll * curve(n, [(0, 0.8), (0.15, 1.0), (0.75, 0.85), (1, 0.6)], log=False)
    fr = K.ns(0.04)
    lead = burst(r, 0.04, 1100, 0.8, 0.02) * (0.6 + 0.4 * np.sin(2 * np.pi * 28 * T(fr)))
    b = Buf()
    b.add(lead * 0.3, 0.006)
    b.add(y, 0.006 + 0.025)
    return whisper(K.lowpass(K.highpass(b.out(), 140, 2), 3300, 2), seed)


# =====================================================================================================================
# Small animals: squirrel, frog, cricket, rustle
# =====================================================================================================================

def chuk(r, f0, amp=1.0):
    """A squirrel's chuk: a dry click of the tongue and a short falling note under it."""
    L = K.ns(0.045)
    tt = T(L)
    f = curve(L, [(0, f0 * 1.25), (0.25, f0), (1, f0 * 0.72)], ms=2)
    y = tone(f, (1, 0.3, 0.08), _sd(r), jitter=0.01) * (1 - np.exp(-tt / 0.0015)) * np.exp(-tt / 0.011)
    y = 0.7 * y / (np.max(np.abs(y)) + 1e-12) + 0.3 * burst(r, 0.045, f0, 2.0, 0.006) / 3.0
    k = K.ns(0.004)
    c = lp1(lp1(r.standard_normal(k) * np.exp(-T(k) / 0.0008), 2400), 2400)
    y[:k] += 0.3 * c / (np.max(np.abs(c)) + 1e-12)
    return y * amp


def squeak(r, amp):
    f0 = r.uniform(3000, 3800)
    return note(r.uniform(0.05, 0.09), [(0, f0 * 0.9), (0.35, f0 * 1.2), (1, f0 * 0.95)], amp, a=0.006, rel=0.02,
                amps=(1, 0.15, 0.04), seed=_sd(r))


def squirrel(v, seed):
    """A squirrel scolding: rapid dry chuk-chuk-chuk in bursts timed to the flicks of its tail, squeaky chirps between."""
    r = K.rng(seed)
    b = Buf()
    t = 0.006
    f0 = r.uniform(1150, 1450)
    plans = [[(4, 13, False), (3, 13, True), (5, 14, False)],
             [(3, 11, True), (3, 11, False), (3, 12, False), (4, 11, True)],
             [(6, 16, False), (6, 24, False), (2, 9, True)]]
    for count, rate, sq in plans[v]:
        for i in range(count):
            b.add(chuk(r, f0 * (0.95 if i == 0 else 1 + 0.03 * i) * r.uniform(0.98, 1.02), (1.0 if i == 0 else r.uniform(0.7, 0.9))), t)
            t += 1 / rate * r.uniform(0.94, 1.06)
        if sq:
            t = b.add(squeak(r, 0.3), t + 0.02)
        t += r.uniform(0.08, 0.16)
    return b.out()


def frog_plop(v, seed):
    """A frog into still water: the slap of its body, a plip (the cavity it leaves closing, its pitch falling), a
    bubble, a tiny splash and the drops falling back."""
    r = K.rng(seed)
    b = Buf()
    t0 = 0.005
    L = K.ns(0.03)
    slap = _unit(lp1(r.standard_normal(L), 500)) * np.exp(-T(L) / 0.006)
    b.add(slap * 0.35, t0)
    f_hi, f_lo = [(1050, 520), (950, 470), (1150, 560)][v]
    L = K.ns(0.16)
    f = f_lo + (f_hi - f_lo) * np.exp(-T(L) / r.uniform(0.018, 0.026))
    plip = tone(f, (1, 0.08), _sd(r), 0) * (1 - np.exp(-T(L) / 0.0015)) * np.exp(-T(L) / r.uniform(0.03, 0.04))
    b.add(plip, t0 + 0.004)
    if v != 1:
        L = K.ns(0.03)
        g = r.uniform(1250, 1450)
        bub = tone(g * (1 + 0.3 * (1 - np.exp(-T(L) / 0.008))), (1,), _sd(r), 0) * (1 - np.exp(-T(L) / 0.001)) * np.exp(-T(L) / 0.008)
        b.add(bub * 0.25, t0 + r.uniform(0.05, 0.08))
    L = K.ns(0.07)
    spray = _unit(K.lowpass(K.highpass(r.standard_normal(L), 400, 2), 2800, 2)) * (1 - np.exp(-T(L) / 0.002)) * np.exp(-T(L) / 0.015)
    b.add(spray * [0.3, 0.4, 0.25][v], t0 + 0.002)
    drops(r, b, t0 + 0.07, [3, 2, 4][v], 0.15, level=0.18)
    return b.out()


CRICKETS = [  # carrier Hz, chirps a second, pulses a chirp, pulse period s, pulse length s, seconds
    (4500, 2.5, 4, 0.034, 0.017, 2.6),
    (4800, 2.9, 3, 0.030, 0.015, 2.2),
    (4250, 2.1, 4, 0.038, 0.019, 3.4),
    (4650, 2.7, 3, 0.032, 0.016, 3.0),
]


def cricket(v, seed):
    """A field cricket singing: chirps of 3-4 pure pulses, the pitch slipping down a little through each pulse as the
    file runs over the scraper; whole chirps only, so it starts and stops cleanly."""
    r = K.rng(seed)
    carrier, rate, pulses, per, plen, dur = CRICKETS[v]
    b = Buf()
    t = 0.006
    for _ in range(int(np.ceil(dur * rate))):
        for p in range(pulses):
            L = K.ns(plen * r.uniform(0.95, 1.05))
            fc = carrier * (1 + 0.004 * r.standard_normal())
            f = curve(L, [(0, fc * 1.012), (1, fc * 0.985)], ms=0)
            u = np.arange(L) / (L - 1)
            amp = [0.75, 1.0, 0.95, 0.85][p] * r.uniform(0.92, 1.05)
            b.add(tone(f, (1, 0.04), _sd(r), 0) * np.sin(np.pi * u) ** 0.8 * amp, t + p * per + 0.0015 * r.standard_normal())
        t += 1 / rate * (1 + 0.03 * r.standard_normal())
    return b.out()


def snap(r):
    """A dry twig breaking under a foot: a crack and a short woody ring."""
    return knock(r, r.uniform(900, 1300), [(1.0, 1.0, 0.03), (1.6, 0.6, 0.02), (2.7, 0.3, 0.015)], click=0.9, click_fc=5000)


def rustle(v, seed):
    """A small animal moving through leaves and twigs: a squirrel's scurry, a rabbit darting off, or something nosing
    through the litter and snapping a twig."""
    r = K.rng(seed)
    b = Buf()
    if v == 0:
        steps = np.cumsum(r.uniform(0.06, 0.1, int(r.integers(5, 7)))) - 0.05
        b.add(leaves(r, list(steps), count=(5, 12), spread=0.012, swish=0.5), 0.004)
    elif v == 1:
        steps = [0.0, 0.13 + r.uniform(-0.01, 0.01), 0.27 + r.uniform(-0.02, 0.02)]
        b.add(leaves(r, steps, count=(12, 24), spread=0.02, swish=0.9), 0.004)
        for s in steps:
            L = K.ns(0.04)
            thump = np.sin(2 * np.pi * r.uniform(110, 160) * T(L)) * (1 - np.exp(-T(L) / 0.003)) * np.exp(-T(L) / 0.012)
            b.add(thump * 0.35, 0.004 + s)
    else:
        steps = list(np.sort(r.uniform(0.0, 0.5, int(r.integers(7, 10)))))
        b.add(leaves(r, steps, count=(3, 8), spread=0.01, swish=0.4), 0.004)
        b.add(snap(r) * 0.5, 0.004 + r.uniform(0.22, 0.34))
    return b.out()


# =====================================================================================================================
# Events
# =====================================================================================================================

# event id: (file name, renderer, variants, attenuation distance, gated RMS dBFS, subtitle)
SHOTS = {
    "wild.song.robin": ("robin", robin, 5, 32, -26.0, "Robin sings"),
    "wild.song.tit": ("tit", tit, 4, 28, -26.0, "Tit sings"),
    "wild.song.finch": ("finch", finch, 4, 32, -26.0, "Chaffinch sings"),
    "wild.song.wren": ("wren", wren, 4, 36, -24.0, "Wren sings"),
    "wild.chip": ("chip", chip, 6, 16, -30.0, "Bird chirps"),
    "wild.flush": ("flush", flush, 4, 20, -27.0, "Bird flutters away"),
    "wild.wings.big": ("wings_big", wings_big, 3, 28, -27.0, "Large wings flap"),
    "wild.woodpecker.drum": ("woodpecker_drum", woodpecker_drum, 4, 56, -24.0, "Woodpecker drums"),
    "wild.woodpecker.tap": ("woodpecker_tap", woodpecker_tap, 3, 20, -30.0, "Woodpecker taps"),
    "wild.crow.caw": ("crow", crow, 5, 56, -25.0, "Crow caws"),
    "wild.raptor.call": ("buzzard", buzzard, 3, 110, -28.0, "Buzzard calls"),
    "wild.owl.hoot": ("owl", owl, 4, 72, -27.0, "Owl hoots"),
    "wild.duck.quack": ("duck", duck, 5, 36, -26.0, "Duck quacks"),
    "wild.heron.croak": ("heron", heron, 2, 48, -26.0, "Heron croaks"),
    "wild.squirrel.chatter": ("squirrel", squirrel, 3, 20, -28.0, "Squirrel chatters"),
    "wild.frog.plop": ("frog_plop", frog_plop, 3, 16, -30.0, "Frog plops into water"),
    "wild.cricket": ("cricket", cricket, 4, 20, -28.0, "Cricket chirps"),
    "wild.rustle": ("rustle", rustle, 3, 14, -30.0, "Leaves rustle"),
}


def path(name, k):
    return os.path.join(SOUNDS, f"wild/{name}{k + 1}.ogg")


def master(x, rms, ceil=CEIL - 0.6):
    """Sets the gated RMS; a peak over the ceiling first costs up to 2 dB of loudness (inside the tolerance), and only
    what is left is limited, so transients stay whole. Faded 3-5 ms at the ends."""
    y = K.highpass(x, 40, 2)
    y = y * K.dbamp(rms - K.gated_rms(y))
    tp = K.true_peak(y)
    if tp > ceil:
        y = y * K.dbamp(-min(tp - ceil, 2.0))
        if K.true_peak(y) > ceil:
            y = K.limit(y, ceil - 0.2, release=0.05)
    if K.true_peak(y) > ceil:
        y = y * K.dbamp(ceil - K.true_peak(y) - 0.05)
    return K.declick(y, 0.003, 0.005)


def write_events():
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    spath = os.path.join(MERGE, "sounds.json")
    ev = json.load(open(spath)) if os.path.exists(spath) else {}
    lpath = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(lpath)) if os.path.exists(lpath) else {}
    for eid, (name, _, n, att, _, sub) in SHOTS.items():
        ev[eid] = {"subtitle": "subtitles.awaken." + eid,
                   "sounds": [{"name": f"awaken:wild/{name}{k + 1}", "attenuation_distance": att} for k in range(n)]}
        lang["subtitles.awaken." + eid] = sub
    with open(spath, "w") as f:
        json.dump(ev, f, indent=2)
        f.write("\n")
    with open(lpath, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


def render_shots(only=None):
    q = TARGETS["sfx"][3]
    rows = []
    for eid, (name, fn, n, _, rms, _) in SHOTS.items():
        if only and not any(name.startswith(o) or eid.startswith(o) for o in only):
            continue
        for k in range(n):
            y = master(K.mono(fn(k, sum(map(ord, name)) * 13 + k * 101)), rms)
            p = path(name, k)
            K.write_ogg(p, y, q, None)
            d = K.read_audio(p)
            tp = K.true_peak(d)
            if tp > CEIL:  # the encoder can lift a peak a little: take it back under
                y = y * K.dbamp(CEIL - tp - 0.15)
                K.write_ogg(p, y, q, None)
                d = K.read_audio(p)
            rows.append((f"wild/{name}{k + 1}", len(d) / SR, K.gated_rms(d), K.true_peak(d), os.path.getsize(p), rms))
    total = 0
    print(f"  {'file':26s} {'len':>5s} {'RMS':>6s} {'TP':>6s} {'kB':>5s}  target")
    for f, dur, g, tp, size, rms in rows:
        total += size
        flag = "" if abs(g - rms) <= 1.0 and tp <= CEIL + 0.01 else "  <-"
        print(f"  {f:26s} {dur:5.2f} {g:6.1f} {tp:6.1f} {size / 1024:5.0f}  {rms:5.0f}{flag}")
    print(f"  {len(rows)} files, {total / 1024:.0f} kB")
    return rows


# ---- contact sheet ------------------------------------------------------------------------------------------------

def _spec(x, w, h, fmin=120.0, fmax=16000.0, floor=-75.0):
    """Log-frequency spectrogram w x h, each tile against its own loudest bin."""
    from audio_sheet import cmap
    from PIL import Image
    m = K.mono(x)
    frame = 512
    m = np.concatenate([np.zeros(frame // 2), m, np.zeros(frame // 2)])
    hop = max(8, (len(m) - frame) // max(1, 2 * w))
    win = np.hanning(frame)
    S = np.array([np.abs(np.fft.rfft(m[s:s + frame] * win)) for s in range(0, len(m) - frame + 1, hop)]).T
    S = 20 * np.log10(S + 1e-9)
    S -= S.max()
    f = np.fft.rfftfreq(frame, 1 / SR)
    rows = np.geomspace(fmax, fmin, h)
    img = np.array([S[np.argmin(np.abs(f - fr))] if not ((f >= fr / 1.03) & (f <= fr * 1.03)).any()
                    else S[(f >= fr / 1.03) & (f <= fr * 1.03)].max(axis=0) for fr in rows])
    rgb = cmap((img - floor) / -floor)
    return Image.fromarray(rgb, "RGB").resize((w, h), Image.BILINEAR)


def sheet(out):
    from PIL import Image, ImageDraw
    pxs, sh, lab, gap = 100, 104, 200, 6
    rows = []
    for eid, (name, _, n, att, rms, _) in SHOTS.items():
        tiles = []
        for k in range(n):
            x = K.read_audio(path(name, k))
            dur = len(x) / SR
            w = int(np.clip(dur * pxs, 64, 380))
            t = Image.new("RGB", (w, sh + 14), (24, 26, 34))
            ImageDraw.Draw(t).text((2, 1), f"{k + 1}  {dur:.2f}s  {K.gated_rms(x):.1f} / {K.true_peak(x):.1f}", fill=(210, 215, 225))
            t.paste(_spec(x, w, sh), (0, 14))
            tiles.append(t)
        rows.append((eid, att, rms, tiles))
    width = lab + max(sum(t.size[0] + gap for t in tl) for _, _, _, tl in rows) + gap
    height = sum(sh + 14 + gap for _ in rows) + 30
    im = Image.new("RGB", (width, height), (10, 10, 14))
    dr = ImageDraw.Draw(im)
    dr.text((8, 8), "AWAKEN wild voices: log spectrograms 120 Hz-16 kHz (guides 500 Hz, 1k, 2k, 5k, 10k), "
                    "each tile against its own peak; labels: variant, length, gated RMS / true peak dBFS", fill=(230, 230, 240))
    y = 30
    guides = [500, 1000, 2000, 5000, 10000]
    for eid, att, rms, tiles in rows:
        dr.text((8, y + 4), eid, fill=(240, 240, 250))
        dr.text((8, y + 20), f"{len(tiles)} variants, {att} blocks", fill=(170, 175, 190))
        dr.text((8, y + 36), f"target {rms:.0f} dBFS", fill=(170, 175, 190))
        for fr in guides:
            yy = y + 14 + int(sh * np.log(16000 / fr) / np.log(16000 / 120))
            dr.line([(lab - 30, yy), (lab - 4, yy)], fill=(120, 120, 140))
            dr.text((lab - 64, yy - 6), f"{fr // 1000}k" if fr >= 1000 else f"{fr}", fill=(120, 120, 140))
        x = lab
        for t in tiles:
            im.paste(t, (x, y))
            x += t.size[0] + gap
        y += sh + 14 + gap
    im.save(out)
    print("  sheet ->", out)


def main(argv):
    what = argv[0] if argv else "all"
    if what == "sheet":
        sheet(argv[1] if len(argv) > 1 else "wild_audio_sheet.png")
        return 0
    if what == "all":
        render_shots()
    elif what == "only":
        render_shots(argv[1].split(","))
    write_events()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
