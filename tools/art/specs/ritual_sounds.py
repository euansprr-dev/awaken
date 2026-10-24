"""The Ritual of the Bell's sounds (docs/proposals/the-ritual.md): the hall, the rules, the Seated One waking and
judging, the end, the void and the awakening. Eighteen events, all synthesised here with the audio kit
(tools/art/audiokit.py): modal bronze, struck glass (the System), a distant choir (something bigger than you), stone
friction and fracture, synthesised rooms. D Phrygian like the Bell Crypt; the System's glass keeps D major pentatonic;
the tear carries the Signal's sigh (D A Bb A) rising to D.

    python3 tools/art/specs/ritual_sounds.py build [name ...]   render the .ogg files (all, or only these), write the JSON
    python3 tools/art/specs/ritual_sounds.py sheet DIR          spectrogram sheets of the rendered files (judge by eye)

Writes src/main/resources/assets/awaken/sounds/ritual/<name>.ogg (44.1 kHz Vorbis), the events story.ritual.<name> in
src/main/merge/story/assets/awaken/sounds.json and their subtitles in the story lang file (other keys kept as they are).
Sounds placed in the hall are mono (OpenAL only places mono sources); the ones played on the listener (words, drain,
tear, void) are stereo. Every file is mastered inside the sfx window of audio_mix.py (-20 +- 4 dBFS gated RMS, true
peak under -1 dBFS) by this spec's own master() (hits keep their shape; see its comment): the bell, the eyes, the doors
and the close are the loudest moments; the candle, the heartbeat and the void bed are made soft by their event volume,
so audio_mix.py --fix never re-levels them. Build prints RMS, LUFS and how much the limiter took.

Sync points for the director: the doors meet (the BOOM) at DOOR_SHUT = 1.9 s; the eyes bloom at EYES_BLOOM = 2.8 s;
the darkness opens at TEAR_OPEN = 0.85 s; the hand's impact is 0.06 s into "close" and it cuts to black at 0.46 s.
"drain" ends at its peak: stop it when "close" plays, or the cut to silence is lost. The heartbeat is 1.3 s (made to
repeat every ~1.6 s); the void bed loops seamlessly at 12 s.
Deterministic: every random source is seeded. build(out) is a no-op so build_all.py (textures) does not render audio.
"""
import json
import os
import sys
import time
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, ART)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/ritual")
MERGE = os.path.join(ROOT, "src/main/merge/story/assets/awaken")
SR = K.SR
M = K.midi


def build(out):  # textures only in build_all; audio is rendered on demand
    pass


# ---- small helpers -------------------------------------------------------------------------------------------------

def hzm(m):
    return float(K.hz(M(m)))


def bp(n, pts):
    """Breakpoint curve over n samples: pts [(seconds, value)], linear in between."""
    xs, ys = zip(*pts)
    return np.interp(np.arange(n) / SR, xs, ys)


def unit(x):
    """Unit RMS (so layers are mixed by their gains, not by how loud each generator happens to come out)."""
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def pk(x):
    """Peak 1 (transients are mixed by their peaks, so the limiter never has to flatten them)."""
    return x / (np.max(np.abs(x)) + 1e-12)


def glue(x, drive=1.6):
    """A stack of transients (boom, thud, crack...) soft-clipped together so they land as one blow: peak 1, the
    body raised against the spike, so the limiter never has to flatten the hit."""
    return np.tanh(drive * pk(x)) / np.tanh(drive)


def far_wall(x, taps=((0.29, -12.0, 3000.0), (0.46, -17.0, 2000.0))):
    """The far end of the hall answering: late, darker returns (delay s, dB, low-pass Hz). The synthetic room's
    early reflections stop at ~140 ms; a hall this size also throws one back after a third of a second."""
    out = x.copy()
    for d, db, lp in taps:
        put(out, K.lowpass(x, lp, 2), d, K.dbamp(db))
    return out


def slab(freqs, t60, n, seed):
    """A stone slab's body: a few low modes, struck. Peak 1."""
    m = K.modal(1.0, freqs, [1.0, 0.75, 0.55, 0.4, 0.3, 0.2][: len(freqs)],
                [t60 * k for k in (1.0, 0.8, 0.6, 0.45, 0.35, 0.3)][: len(freqs)], n, attack=0.0008, seed=seed)
    return pk(m * K.perc(n, 0.0008, t60 * 1.2))


def reversed_swell(dry, ir, length, close=0.025):
    """A reversed reverb of `dry` that swells into its end; the last `close` seconds shut smoothly so the
    reversed strike never stops dead. Peak 1."""
    rv = fconv(dry, ir)[: K.ns(length)][::-1].copy()
    k = K.ns(close)
    rv[-k:] *= K._rise(k)[::-1]
    return pk(rv)


def put(dst, src, t, g=1.0):
    i = K.ns(t)
    if i >= len(dst) or len(src) == 0:
        return
    m = min(len(src), len(dst) - i)
    dst[i:i + m] += src[:m] * g


def fconv(a, b):
    n = len(a) + len(b) - 1
    nfft = 1 << int(np.ceil(np.log2(max(n, 2))))
    return np.fft.irfft(np.fft.rfft(a, nfft) * np.fft.rfft(b, nfft), nfft)[:n]


def fade_out(x, sec):
    """Raised-cosine fade over the last `sec` seconds (tails end before the file does)."""
    x = x.copy()
    k = min(len(x), K.ns(sec))
    g = K._rise(k)[::-1] ** 1.5
    x[-k:] *= g if x.ndim == 1 else g[:, None]
    return x


def sweep(x, centre, q):
    """Band-pass with a moving centre: centre(t seconds) -> Hz. Mono."""
    return K.stft_filter(x, lambda t, f: K.bp_resp(max(30.0, centre(t)), q)(f), frame=2048, hop=256)


def sweep_lp(x, cutoff, order=2):
    """Low-pass with a moving cutoff: cutoff(t seconds) -> Hz. Mono."""
    return K.stft_filter(x, lambda t, f: K.lp_resp(max(40.0, cutoff(t)), order)(f), frame=2048, hop=256)


# ---- rooms: the hall is black stone, enormous ----------------------------------------------------------------------

ROOMS = {
    "nave": dict(t60=6.8, predelay=0.07, bright=0.35, width=1.0, early=0.35, size=2.2, seed=301, low_mult=1.3),
    "hall": dict(t60=2.8, predelay=0.03, bright=0.45, width=1.0, early=0.5, size=1.2, seed=302, low_mult=1.2),
    "small": dict(t60=1.0, predelay=0.012, bright=0.55, width=0.9, early=0.6, size=0.5, seed=303),
    "void": dict(t60=5.5, predelay=0.09, bright=0.15, width=1.0, early=0.12, size=2.6, seed=304, low_mult=1.0),
}
_IRS = {}


def room(name, stereo=False):
    if name not in _IRS:
        _IRS[name] = K.reverb_ir(**ROOMS[name])
    ir = _IRS[name]
    if stereo:
        return ir
    m = ir[:, 0]
    return m / np.sqrt(np.sum(m ** 2))


class Bus:
    """A mono bus with reverb sends: what a positional sound in the hall is mixed on."""

    def __init__(self, seconds):
        self.n = K.ns(seconds)
        self.dry = np.zeros(self.n)
        self.sends = {}

    def add(self, sig, at=0.0, gain=1.0, send=None, wet=0.0, hp=None):
        """hp: high-pass what goes to the room (a boom's sub would hang in the nave for nine seconds)."""
        put(self.dry, sig, at, gain)
        if send and wet:
            put(self.sends.setdefault(send, np.zeros(self.n)), K.highpass(sig, hp, 2) if hp else sig, at, gain * wet)

    def render(self):
        out = self.dry.copy()
        for name, buf in sorted(self.sends.items()):
            out += fconv(buf, room(name))[: self.n]
        return out


def stereo_mix(mix, rooms):
    return mix.render({r: room(r, stereo=True) for r in rooms})


# ---- voices ----------------------------------------------------------------------------------------------------------

def glass_note(m, dur, vel=0.6, bright=0.55, t60=1.2, seed=0, core=0.35, glint=0.08):
    """The System's glass (the same voice as the UI family): glass + a sine core + a faint FM glint."""
    f = hzm(m) if isinstance(m, str) else float(m)
    n = K.ns(dur)
    g = K.glass(f, n, bright, t60, seed, hit=0.6 * vel)
    c = K.sine(f, n, 0.25) * K.perc(n, 0.004, t60 * 1.25)
    s = K.fm(2 * f, 1.5, 0.9, n, index_env=K.perc(n, 0.001, 0.12), amp_env=K.perc(n, 0.001, t60 * 0.35))
    return (g + core * c + glint * bright * s) * vel


def boom(dur, f0, f1, t60, tau=0.12, drive=1.4, attack=0.003):
    """A deep impact: a sine falling from f0 to f1, a little saturated so small speakers still feel it."""
    n = K.ns(dur)
    t = K.tvec(n)
    f = f1 + (f0 - f1) * np.exp(-t / tau)
    x = K.sine(f, n)
    if drive:  # saturate the wave itself, not the envelope, so the decay stays exponential
        x = np.tanh(drive * x) / np.tanh(drive)
    return x * K.perc(n, attack, t60)


def thud(dur, fc, t60, seed, attack=0.001):
    """The body of a blow: low-passed noise, struck. Peak 1."""
    n = K.ns(dur)
    return pk(K.lowpass(K.noise(n, "white", seed), fc, 2) * K.perc(n, attack, t60))


def crack(seed, size=1.0, dur=0.35):
    """Stone fracturing: a run of sharp ticks inside ~30 ms, the shard's short ring, a low knock."""
    r = K.rng(seed)
    n = K.ns(dur)
    x = np.zeros(n)
    for k in range(int(r.integers(3, 7))):
        t0 = 0.0 if k == 0 else r.uniform(0.002, 0.03)
        L = K.ns(r.uniform(0.002, 0.007))
        b = K.highpass(K.noise(L, "white", seed * 13 + k), 700 / size, 1) * K.perc(L, 0.0002, r.uniform(0.003, 0.008))
        put(x, b, t0, r.uniform(0.5, 1.0) if k else 1.0)
    freqs = r.uniform(800, 3400, 5) / size ** 0.5
    ring = K.modal(1.0, freqs, r.uniform(0.3, 1.0, 5), r.uniform(0.025, 0.08, 5) * size ** 0.5, n, attack=0.0003, seed=seed)
    knock = thud(dur, 320, 0.05 * size, seed + 5)
    return pk(pk(x) + 0.5 * pk(ring) * K.perc(n, 0.0003, 0.12) + 0.6 * min(size, 1.5) * knock)


def pebble(seed, bright=1.0):
    r = K.rng(seed)
    n = K.ns(0.06)
    f = r.uniform(1700, 6000) * bright
    m = K.modal(f, [1.0, r.uniform(1.6, 2.4), r.uniform(2.9, 3.8)], [1.0, 0.5, 0.25],
                [r.uniform(0.008, 0.03), 0.012, 0.008], n, attack=0.0003, seed=seed)
    click = K.highpass(K.noise(n, "white", seed + 1), 1500, 1) * K.perc(n, 0.0002, 0.004)
    return 0.6 * m + 0.5 * click


def debris(L, count, t0, t1, seed, bright=1.0, shape=1.8, lp=None):
    """Small stones falling and bouncing, more of them early."""
    r = K.rng(seed)
    x = np.zeros(K.ns(L))
    for k in range(count):
        u = r.random() ** shape
        t = t0 + (t1 - t0) * u
        a = r.uniform(0.2, 1.0) * (1 - 0.7 * u)
        p = pebble(seed * 101 + k, bright * r.uniform(0.6, 1.2))
        put(x, p, t, a)
        if r.random() < 0.35:
            put(x, p, t + r.uniform(0.03, 0.09), a * 0.4)
    return K.lowpass(x, lp, 2) if lp else x


def dust(L, seed, rate=400.0, fc=5000.0):
    """Dust and grit sifting down: a sparse sand of tiny grains over a faint hiss."""
    n = K.ns(L)
    r = K.rng(seed)
    imp = (r.random(n) < rate / SR) * r.random(n) ** 2
    k = np.arange(K.ns(0.003))
    grains = fconv(imp, np.exp(-k / K.ns(0.0006)))[:n] * K.noise(n, "white", seed + 1)
    return unit(K.bandpass(grains + 0.04 * K.noise(n, "white", seed + 2), fc, 0.7))


STONE = ((150, 1.0, 1.0), (360, 1.1, 0.85), (850, 1.2, 0.5), (2000, 1.4, 0.22))


def grind(L, speed, seed, res=STONE, rate=(9.0, 34.0), grit=0.3, drag=0.2):
    """Stone dragging on stone: stick-slip bursts (their rate follows the speed) through the stone's broad
    resonances, a continuous drag under them, and finer sand grinding between the faces.
    speed: breakpoints [(seconds, 0..1)]; res: (Hz, width in octaves, gain)."""
    n = K.ns(L)
    r = K.rng(seed)
    sp = np.clip(bp(n, speed), 0, 1)
    rr = (rate[0] + (rate[1] - rate[0]) * sp) * (1 + 0.45 * K.smooth_noise(n, 25, seed + 1))
    ph = np.cumsum(np.maximum(rr, 0.5)) / SR
    idx = np.nonzero(np.diff(np.floor(ph)) > 0)[0]
    imp = np.zeros(n)
    imp[idx] = r.uniform(0.25, 1.0, len(idx)) ** 1.5 * (sp[idx] > 0.02)
    k = np.arange(K.ns(0.06))
    am = fconv(imp, np.exp(-k / K.ns(0.004)) + 0.3 * np.exp(-k / K.ns(0.02)))[:n]
    src = K.noise(n, "white", seed + 2) * am + drag * K.noise(n, "pink", seed + 3) * sp

    def resp(f):
        ff = np.maximum(f, 1.0)
        g = np.full_like(ff, 0.03)
        for fc, w, gain in res:
            g += gain * np.exp(-0.5 * (np.log2(ff / fc) / (w / 2)) ** 2)
        return g / np.sqrt(1 + (ff / 4500) ** 4)
    body = unit(K.fft_filter(src, resp))
    # sand between the faces: faster, finer grains, brighter
    rr2 = 70 + 160 * sp
    ph2 = np.cumsum(rr2 * (1 + 0.6 * K.smooth_noise(n, 60, seed + 4))) / SR
    idx2 = np.nonzero(np.diff(np.floor(ph2)) > 0)[0]
    imp2 = np.zeros(n)
    imp2[idx2] = r.random(len(idx2)) ** 2 * (sp[idx2] > 0.05)
    k2 = np.arange(K.ns(0.01))
    sand = fconv(imp2, np.exp(-k2 / K.ns(0.0012)))[:n] * K.noise(n, "white", seed + 5)
    sand = unit(K.lowpass(K.highpass(sand, 1800, 2), 7000, 2))
    y = body + grit * sand
    y = 3.5 * np.tanh(y / 3.5)  # the slips are spiky (17 dB crest): round them to ~11 dB, stone still crackles
    return unit(y) * sp ** 0.6


def choir_glide(fcurve, vowel="a", voices=6, seed=0, bright=0.4, vib_cents=12, spread=10.0):
    """The kit's distant choir on a moving pitch (fcurve: Hz per sample). Mono."""
    n = len(fcurve)
    r = K.rng(seed)
    form = K.VOWELS[vowel]
    fmax = float(np.max(fcurve))
    fmid = float(np.exp(np.mean(np.log(fcurve))))
    out = np.zeros(n)
    for v in range(voices):
        det = 2 ** (r.uniform(-spread, spread) / 1200)
        amps = K.saw_amps(fmax * det * 1.03, bright=bright, tilt=1.25, cap=90, formants=form, f0=fmid * det)
        vib = K.vibrato(n, r.uniform(4.4, 5.6), vib_cents, r.uniform(0.2, 0.6), seed + 10 * v + 1, drift=5.0)
        out += K.table_osc(K.harmonic_table(amps), fcurve * det * vib, n, r.uniform(0, 1))
    return out / voices ** 0.5


def organ(f0, n, seed, stops=((0.5, 0.45), (1.0, 1.0), (2.0, 0.4), (3.0, 0.1), (4.0, 0.12))):
    """A low organ-ish drone: flue pipes (16', 8', 4', 2 2/3', 2'), each a little out with the next, and a chiff."""
    r = K.rng(seed)
    t = K.tvec(n)
    out = np.zeros(n)
    for mult, a in stops:
        f = f0 * mult * 2 ** (r.uniform(-2.5, 2.5) / 1200)
        for h, ha in ((1, 1.0), (2, 0.28), (3, 0.09)):
            if f * h < K.TOP:
                out += a * ha * np.sin(2 * np.pi * f * h * t + r.uniform(0, 6.28))
    kc = min(n, K.ns(0.15))
    out[:kc] += K.bandnoise(kc, min(f0 * 6, 6000), 1.5, seed + 9) * K.perc(kc, 0.004, 0.1) * 0.12
    return out


def beat(seed, f0=60.0, f1=38.0, t60=0.26):
    """One heart sound: a soft low thump falling in pitch, a little flesh around it."""
    n = K.ns(0.5)
    t = K.tvec(n)
    f = f1 + (f0 - f1) * np.exp(-t / 0.04)
    body = np.tanh(1.8 * K.sine(f, n)) / np.tanh(1.8)  # odd harmonics (110-180 Hz): still felt on small speakers
    body *= K.perc(n, 0.007, t60)
    flesh = unit(K.lowpass(K.noise(n, "white", seed), 140, 2)) * K.perc(n, 0.004, 0.08) * 0.22
    return body + flesh


def lubdub(seed, gap=0.3):
    x = np.zeros(K.ns(gap + 0.5))
    put(x, beat(seed, 60, 38, 0.26), 0.0)
    put(x, beat(seed + 1, 70, 44, 0.2), gap, 0.7)
    return x


# ---- 1. the great bell ---------------------------------------------------------------------------------------------

# a bronze bell's partials over its prime (D3): hum, prime, tierce (a minor third), quint, nominal, then the upper
# partials, each split in two (the bell is never quite round), so every partial beats at its own slow rate
BELL = [  # ratio, amp, t60 s, beat Hz, attack s
    (0.5, 0.85, 11.0, 0.21, 0.035), (1.0, 0.70, 7.5, 0.37, 0.012), (1.193, 0.62, 6.2, 0.52, 0.006),
    (1.497, 0.30, 3.6, 0.66, 0.004), (2.0, 1.00, 5.6, 0.93, 0.003), (2.52, 0.40, 2.6, 1.31, 0.002),
    (2.667, 0.52, 2.3, 0.78, 0.002), (3.006, 0.50, 2.8, 1.62, 0.002), (3.36, 0.2, 1.5, 1.1, 0.002),
    (4.0, 0.36, 1.9, 2.1, 0.002), (4.24, 0.16, 1.1, 1.7, 0.002), (4.53, 0.16, 0.95, 2.4, 0.002),
    (5.03, 0.17, 0.85, 1.9, 0.002), (5.34, 0.12, 0.65, 2.7, 0.002), (5.98, 0.1, 0.55, 3.1, 0.002),
    (6.72, 0.09, 0.45, 2.2, 0.002), (7.55, 0.07, 0.36, 3.6, 0.002), (8.31, 0.06, 0.3, 2.9, 0.002),
    (9.22, 0.045, 0.24, 4.1, 0.002), (10.4, 0.04, 0.2, 3.3, 0.002), (11.7, 0.03, 0.16, 4.6, 0.002),
    (13.1, 0.024, 0.12, 3.8, 0.002), (14.9, 0.018, 0.1, 5.2, 0.002), (16.8, 0.013, 0.08, 4.4, 0.002),
]


def great_bell(f1, L, seed, strike=1.0):
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    x = np.zeros(n)
    for ratio, a, t60, beat_hz, att in BELL:
        f = f1 * ratio
        env = 10.0 ** (-3.0 * t / t60)
        ka = K.ns(att)
        env[:ka] *= K._rise(ka)
        p = r.uniform(0.55, 0.85)
        ph = r.uniform(0, 2 * np.pi)
        x += a * env * (np.sin(2 * np.pi * f * t + ph) + p * np.sin(2 * np.pi * (f + beat_hz) * t + ph + r.uniform(1, 3)))
    x = pk(x)
    # the clapper: iron on bronze. A hard clang of high, short modes, a bright click and a dull thunk
    ks = K.ns(0.4)
    ks = K.ns(0.7)
    clang = K.modal(f1, r.uniform(17, 45, 22), r.uniform(0.4, 1.0, 22), r.uniform(0.06, 0.32, 22), ks,
                    attack=0.0004, seed=seed + 1)
    put(x, pk(clang), 0.0, 0.3 * strike)
    kc = K.ns(0.05)
    put(x, pk(K.bandnoise(kc, 1800, 0.8, seed + 2) * K.perc(kc, 0.0004, 0.025)), 0.0, 0.18 * strike)
    put(x, thud(0.2, 350, 0.07, seed + 3), 0.0, 0.25 * strike)
    put(x, boom(0.6, 80, 50, 0.25, drive=0), 0.0, 0.2 * strike)  # the bell's mass, struck
    # the tower answers: a felt D1 under the hum
    sub = K.sine(f1 / 4, n) * K.perc(n, 0.08, 5.0)
    return x + 0.05 * sub


def bell(seed):
    L = 9.6
    b = Bus(L)
    b.add(far_wall(great_bell(hzm("D3"), L, seed)), 0.0, 1.0, "nave", 0.5)
    return fade_out(b.render(), 2.4)


# ---- 2. the doors ----------------------------------------------------------------------------------------------------

DOOR_SHUT = 1.9  # the doors meet (the BOOM)
DOOR_STONE = ((120, 1.0, 1.0), (300, 1.1, 0.8), (700, 1.2, 0.45), (1700, 1.4, 0.16))


def doors(seed):
    L = 4.0
    T = DOOR_SHUT
    b = Bus(L)
    # two doors, the right one a beat behind; each speeds up as it swings, then the last heavy drag
    a = grind(T, [(0, 0), (0.15, 0.5), (0.9, 0.8), (1.6, 1.0), (T - 0.05, 0.85), (T, 0.0)], seed + 1,
              res=DOOR_STONE, rate=(8, 24), grit=0.15)
    c = grind(T, [(0, 0), (0.3, 0.4), (1.0, 0.75), (1.7, 0.95), (T - 0.04, 0.8), (T, 0.0)], seed + 2,
              res=tuple((f * 1.2, w, g) for f, w, g in DOOR_STONE), rate=(10, 22), grit=0.15)
    b.add(K.lowpass(a, 3000, 2), 0.0, 0.1, "nave", 0.3)
    b.add(K.lowpass(c, 3000, 2), 0.0, 0.08, "nave", 0.3)
    # the iron pivots groan under the weight
    n = K.ns(T)
    piv = grind(T, [(0, 0), (0.3, 0.6), (1.2, 0.8), (T, 0.2)], seed + 3,
                res=((310, 0.25, 1.0), (780, 0.25, 0.6), (1530, 0.3, 0.3)), rate=(7, 14), grit=0.0, drag=0.0)
    b.add(piv, 0.0, 0.03, "nave", 0.4)
    # the mass moving: a low weight under it all
    rum = unit(K.lowpass(K.noise(n, "brown", seed + 4), 90, 3)) * bp(n, [(0, 0), (0.3, 0.6), (T, 1.0)])
    rum += 0.5 * K.sine(41.0, n) * bp(n, [(0, 0), (0.5, 0.4), (T, 1.0)])
    b.add(rum, 0.0, 0.07, "nave", 0.2, hp=80)
    b.add(debris(T, 10, 0.2, T, seed + 5, 0.9, shape=1.0), 0.0, 0.1, "nave", 0.3)
    # BOOM: stone on stone, the whole hall answers
    hit = boom(2.0, 62, 30, 1.1, tau=0.1, drive=1.8, attack=0.005)
    put(hit, thud(1.0, 700, 0.28, seed + 6), 0.0, 0.6)
    put(hit, slab([96, 212, 365, 590, 870], 0.6, K.ns(1.2), seed + 7), 0.0, 0.45)
    put(hit, crack(seed + 8, 1.6), 0.004, 0.35)
    b.add(far_wall(glue(hit, 1.8)), T, 1.0, "nave", 0.5, hp=70)
    b.add(dust(L - T, seed + 9, 260, 4500) * bp(K.ns(L - T), [(0, 0), (0.1, 1), (1.2, 0.5), (L - T, 0)]),
          T + 0.05, 0.04, "nave", 0.4)
    b.add(debris(L - T, 16, 0.06, 1.6, seed + 10, 0.8), T, 0.25, "nave", 0.4)
    return fade_out(b.render(), 1.0)


# ---- 3. every flame dies -------------------------------------------------------------------------------------------

def flames_out(seed):
    L = 1.6
    T = 0.42  # the moment they all go
    b = Bus(L)
    n0 = K.ns(T)
    # the flames, for an instant: a soft roar and crackle, pulled inward with the air
    fire = unit(K.lowpass(K.noise(n0, "pink", seed + 1), 700, 2)) * (0.8 + 0.2 * K.smooth_noise(n0, 14, seed + 2))
    fire += 0.5 * K.crackle(n0, rate=60, seed=seed + 3, bright=0.5)
    b.add(fire * bp(n0, [(0, 0.5), (T * 0.7, 0.8), (T, 0.0)]), 0.0, 0.1, "hall", 0.2)
    # the inward suck: a reversed whoosh, rising and brightening, stopping dead on the moment
    puffsrc = K.bandnoise(K.ns(0.09), 1400, 0.7, seed + 4) * K.perc(K.ns(0.09), 0.002, 0.06)
    rev = reversed_swell(puffsrc, room("hall"), 1.2, close=0.01)[-n0:]
    rush = sweep(K.noise(n0, "pink", seed + 5), lambda t: 350 * (3800 / 350) ** min(1.0, t / T), 1.3)
    suck = rev * 0.5 + pk(unit(rush) * bp(n0, [(0, 0), (T * 0.5, 0.2), (T, 1.0)]) ** 2) * 0.4
    suck *= bp(n0, [(0, 0), (T * 0.6, 0.35), (T - 0.012, 1.0), (T, 0.0)]) ** 1.5
    b.add(suck, 0.0, 0.8, "hall", 0.1)
    # pfff: a hundred small flames snuffed together
    r = K.rng(seed + 6)
    pf = np.zeros(K.ns(0.4))
    for k in range(40):
        d = r.uniform(0.04, 0.12)
        kn = K.ns(d)
        p = K.bandnoise(kn, r.uniform(700, 2600), 1.2, seed + 50 + k) * K.perc(kn, r.uniform(0.003, 0.01), d * 0.6)
        put(pf, p, r.uniform(0, 0.07) ** 1.3 * 3, r.uniform(0.3, 1.0))
    b.add(pk(K.lowpass(pf, 3200, 2)), T, 0.4, "hall", 0.08)
    y = b.render()
    # the air dies with them: everything shuts within a fifth of a second...
    g = np.ones(len(y))
    c0, c1 = K.ns(T + 0.1), K.ns(T + 0.32)
    g[c0:c1] = 1 - (1 - 0.02) * K._rise(c1 - c0)
    g[c1:] = 0.02
    y *= g
    # ...and the silence lands: a deep, sudden thump
    put(y, boom(0.9, 52, 30, 0.3, tau=0.06, drive=1.4, attack=0.004), T, 0.9)
    put(y, thud(0.3, 160, 0.08, seed + 7), T, 0.3)
    return fade_out(y, 0.5)


# ---- 4. one candle -------------------------------------------------------------------------------------------------

def candle(seed):
    L = 0.6
    b = Bus(L)
    n = K.ns(0.25)
    # fwip: a small breath that brightens as the wick takes
    f = sweep(K.noise(n, "white", seed + 1), lambda t: 700 + 1000 * min(1.0, t / 0.05), 1.6)
    f = pk(K.lowpass(f, 4000, 2) * K.perc(n, 0.006, 0.09))
    b.add(f, 0.0, 0.5, "small", 0.12)
    b.add(thud(0.15, 220, 0.05, seed + 2, attack=0.004), 0.0, 0.3)
    b.add(K.crackle(K.ns(0.12), rate=60, seed=seed + 3, bright=0.4) * K.perc(K.ns(0.12), 0.002, 0.08), 0.02, 0.03)
    # the faint warm glass shimmer: the System has noticed
    g = glass_note("D6", 0.55, 0.5, 0.3, 0.45, seed + 4, core=0.6, glint=0.0)
    b.add(K.lowpass(g, 5000, 1), 0.025, 0.14, "small", 0.3)
    return fade_out(b.render(), 0.22)


# ---- 5. the words (UI, stereo) -------------------------------------------------------------------------------------

def words(seed):
    L = 3.4
    mix = K.Mix(L)
    # the incision: stone scraped by light, crossing the wall
    n = K.ns(0.4)
    scr = grind(0.4, [(0, 0), (0.02, 1.0), (0.25, 0.7), (0.4, 0.0)], seed + 1,
                res=((1800, 1.0, 1.0), (3600, 1.0, 0.7)), rate=(60, 95), grit=0.6, drag=0.4)
    scr = pk(sweep(scr, lambda t: 1800 * (4800 / 1800) ** min(1.0, t / 0.3), 0.9) * K.perc(n, 0.002, 0.35))
    for i, (p, d) in enumerate(((-0.35, 0.0), (0.35, 0.01))):
        mix.add(scr * 0.4, d, 1.0, p, "nave", 0.35)
    t = K.tvec(n)
    light = (np.sin(2 * np.pi * hzm("A7") * t) + 0.7 * np.sin(2 * np.pi * hzm("D8") * 1.002 * t))
    light *= (0.6 + 0.4 * np.sin(2 * np.pi * 31 * t)) * K.perc(n, 0.004, 0.25)
    mix.add(light * 0.05, 0.0, 1.0, 0.0, "nave", 0.5)
    mix.add(thud(0.3, 250, 0.08, seed + 2), 0.0, 0.4, 0.0, "nave", 0.3)
    # the bloom: the System's glass, then a far choir and an organ under it (D Phrygian, a faint b9 on top)
    for i, m in enumerate(("D6", "A6")):
        mix.add(glass_note(m, L - 0.1, 0.4, 0.55, 2.4, seed + 10 + i), 0.08 + 0.02 * i, 0.35, 0.3 - 0.6 * i, "nave", 0.5)
    nc = K.ns(L - 0.12)
    for i, (m, g, p, v) in enumerate((("D4", 0.5, -0.4, "a"), ("F4", 0.38, 0.4, "a"), ("A4", 0.42, -0.15, "a"),
                                      ("D5", 0.2, 0.2, "o"), ("Eb5", 0.07, 0.5, "o"))):
        c = K.choir(hzm(m), nc, v, 6, seed + 20 + i, bright=0.35)
        mix.add(c * K.adsr(nc, 0.45 + 0.1 * i, 0.4, 0.75, 1.9) * g * 0.13, 0.12, 1.0, p, "nave", 0.7)
    og = organ(hzm("D3"), nc, seed + 30) * K.adsr(nc, 0.35, 0.3, 0.8, 1.7)
    mix.add(K.lowpass(og, 1600, 2) * 0.04, 0.1, 1.0, 0.0, "nave", 0.5)
    return fade_out(stereo_mix(mix, ("nave",)), 1.0)


# ---- 6. the eyes open ----------------------------------------------------------------------------------------------

EYES_BLOOM = 2.8


def eyes(seed):
    L = 7.5
    T = EYES_BLOOM
    b = Bus(L)
    nb = K.ns(T)
    u = np.arange(nb) / nb
    # the build: something under the floor wakes. Sub and rumble, trembling faster as it rises
    tr = np.cumsum(3 + 7 * u ** 2) / SR
    sub = (K.sine(hzm("D1"), nb) + 0.6 * K.sine(hzm("D2"), nb)) * (0.75 + 0.25 * np.sin(2 * np.pi * tr))
    b.add(sub * u ** 1.8, 0.0, 0.25, "nave", 0.15, hp=60)
    rum = unit(K.lowpass(K.noise(nb, "brown", seed + 1), 110, 3)) * u ** 2.2
    b.add(rum, 0.0, 0.18, "nave", 0.25)
    # monks under it, then the choir cluster rising and tightening into the chord
    hum = K.choir(hzm("D2"), nb, "o", 6, seed + 2, bright=0.25) + 0.7 * K.choir(hzm("A2"), nb, "o", 6, seed + 3, bright=0.25)
    b.add(K.lowpass(hum, 900, 2) * K.swell(nb, 0.85, 1.2) * 0.04, 0.0, 1.0, "nave", 0.6)
    for i, (m0, m1) in enumerate(((57, 62), (58, 65), (60, 69), (62, 74), (63, 77))):
        fc = K.hz(m0 + (m1 - m0) * u ** 2.6)
        c = choir_glide(fc, "a" if i % 2 else "o", 6, seed + 10 + i, bright=0.3 + 0.1 * (i % 3))
        b.add(c * (0.03 + 0.97 * u ** 2.4) * 0.045, 0.0, 1.0, "nave", 0.6)
    air = sweep(K.noise(nb, "white", seed + 4), lambda t: 2000 * (9000 / 2000) ** (t / T), 0.8)
    b.add(unit(air) * u ** 3, 0.0, 0.05, "nave", 0.4)
    # a reversed bloom sucks into the moment
    src = Bus(1.4)
    for i, m in enumerate(("D5", "A5", "D6", "F6")):
        src.add(glass_note(m, 1.4, 0.6, 0.6, 1.2, seed + 40 + i))
    src.add(boom(1.4, 70, 40, 0.6, drive=0), 0.0, 0.6)
    b.add(reversed_swell(src.dry, room("nave"), 2.4), T - 2.4, 0.3)
    # BLOOM: the deep impact...
    hit = boom(3.0, 58, 26, 1.8, tau=0.16, drive=1.8, attack=0.006)
    put(hit, thud(1.5, 900, 0.45, seed + 5), 0.0, 0.4)
    drum = K.modal(55.0, [1.0, 1.59, 2.14, 2.65], [1.0, 0.6, 0.4, 0.2], [1.0, 0.6, 0.4, 0.3], K.ns(1.5), attack=0.002, seed=seed + 6)
    put(hit, pk(drum * K.perc(K.ns(1.5), 0.002, 1.0)), 0.0, 0.35)
    b.add(glue(hit, 1.6), T, 1.0, "nave", 0.5, hp=70)
    # ...the blazing shimmer chord...
    for i, m in enumerate(("D6", "A6", "D7", "F7", "A7", "D8")):
        g = glass_note(m, L - T, 0.6, 0.85, 3.6 - 0.3 * i, seed + 60 + i, core=0.5, glint=0.15)
        b.add(g, T + 0.004 * i, 0.32 - 0.03 * i, "nave", 0.55)
    nr = K.ns(L - T)
    t = K.tvec(nr)
    r = K.rng(seed + 7)
    sh = np.zeros(nr)
    for k_ in range(24):  # many slightly detuned high voices flickering: light too bright to look at
        m = ("D6", "A6", "D7", "A7", "F7")[k_ % 5]
        f = hzm(m) * 2 ** (r.normal(0, 7) / 1200)
        flick = 0.55 + 0.45 * K.smooth_noise(nr, r.uniform(6, 14), seed + 100 + k_)
        sh += np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) * flick
    sh *= K.perc(nr, 0.02, 3.2)
    b.add(unit(sh) * 0.1, T, 1.0, "nave", 0.6)
    for i, m in enumerate(("D5", "A5")):
        b.add(K.fm_bell(hzm(m), nr, 4.0, 2.8, 3.5, seed + 70 + i), T, 0.12, "nave", 0.6)
    # ...and the choir, full voice, held and let go
    for i, (m, g, v) in enumerate((("D3", 0.7, "a"), ("A3", 0.6, "a"), ("D4", 0.6, "a"), ("F4", 0.45, "a"),
                                   ("A4", 0.5, "a"), ("D5", 0.35, "o"))):
        c = K.choir(hzm(m), nr, v, 6, seed + 80 + i, bright=0.42)
        b.add(c * K.adsr(nr, 0.06, 0.6, 0.6, 3.2) * g * 0.18, T, 1.0, "nave", 0.6)
    og = organ(hzm("D2"), nr, seed + 90) * K.adsr(nr, 0.05, 0.8, 0.6, 3.5)
    b.add(K.lowpass(og, 1200, 2) * 0.1, T, 1.0, "nave", 0.5)
    return fade_out(b.render(), 1.6)


# ---- 7. the gaze charges -------------------------------------------------------------------------------------------

def gaze_charge(seed):
    L = 2.3
    n = K.ns(L)
    u = np.arange(n) / n
    b = Bus(L)
    # the whine: rising, its vibrato faster and narrower as it tightens, a fifth above it, a partner beating closer
    vr = np.cumsum(5 + 40 * u ** 2) / SR
    vib = 2 ** ((40 * (1 - u) ** 1.5 + 6) * np.sin(2 * np.pi * vr) / 1200)
    f = 260 * (2600 / 260) ** (u ** 1.6) * vib
    w = K.sine(f, n) + 0.3 * K.sine(2 * f, n) + 0.12 * K.sine(3 * f, n)
    w += 0.4 * K.sine(1.5 * f, n) + 0.5 * K.sine(f * 2 ** (30 * (1 - u) / 1200), n)
    pulse = 0.7 + 0.3 * np.sin(2 * np.pi * np.cumsum(6 + 34 * u ** 2) / SR)
    b.add(w * pulse, 0.0, 0.12, "hall", 0.35)
    # growing crackle
    cr = K.crackle(n, rate=140, seed=seed + 1, bright=0.7, burstiness=0.5) * u ** 2
    b.add(cr, 0.0, 0.35, "hall", 0.3)
    # a low throb, quickening
    th = (K.sine(48.0, n) + 0.4 * K.sine(96.0, n)) * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(2.5 + 9 * u ** 1.5) / SR)) ** 2
    b.add(th, 0.0, 0.28, "hall", 0.15, hp=80)
    air = sweep(K.noise(n, "white", seed + 2), lambda tt: 1500 * (8000 / 1500) ** (tt / L), 1.2)
    b.add(unit(air) * u, 0.0, 0.05, "hall", 0.3)
    # everything swells from almost nothing; it ends abruptly, the room cut with it
    y = b.render() * (0.04 + 0.96 * u ** 1.8)
    k = K.ns(0.006)
    y[-k:] *= K._rise(k)[::-1]
    return y


# ---- 8. the gaze fires ---------------------------------------------------------------------------------------------

def gaze_fire(seed):
    L = 1.5
    b = Bus(L)
    n = K.ns(L)
    t = K.tvec(n)
    # the searing blast
    blast = K.lowpass(K.highpass(K.noise(n, "white", seed + 1), 300, 1), 9000, 2)
    hit = pk(blast * K.perc(n, 0.006, 0.5, hold=0.08)) * 0.6  # it sears for a moment, then dies
    # the beam: a hard buzzing tone falling from the charge's last note, through a burning band
    f = 2600 * np.exp(-t / 0.03) + 180 + 40 * np.exp(-t / 0.3)
    amps = K.saw_amps(2800, bright=0.8, tilt=0.9, cap=110)
    tone = K.table_osc(K.harmonic_table(amps), f * (1 + 0.01 * K.smooth_noise(n, 40, seed + 2)), n)
    tone = pk(K.bandpass(tone, 1600, 0.6) * K.perc(n, 0.006, 0.6, hold=0.18))
    hit += tone * 0.4
    # the high sizzle
    sz = unit(K.highpass(K.crackle(n, rate=420, seed=seed + 3, bright=0.95, burstiness=0.3), 3000, 2))
    sz = 2.5 * np.tanh(sz / 2.5)  # crackle is all spikes: round them so the sizzle reads as a texture
    b.add(sz * K.perc(n, 0.008, 0.5, hold=0.22), 0.0, 0.12, "nave", 0.3)
    fizz = K.highpass(K.noise(n, "white", seed + 4), 5000, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 70 * t)) * K.perc(n, 0.008, 0.5, hold=0.18)
    b.add(unit(fizz), 0.0, 0.05, "nave", 0.3)
    # the concussion
    put(hit, boom(1.2, 78, 34, 0.45, tau=0.07, drive=1.8, attack=0.006), 0.0, 0.85)
    put(hit, thud(0.4, 320, 0.1, seed + 5), 0.0, 0.5)
    b.add(glue(hit, 2.4), 0.0, 1.0, "nave", 0.35, hp=90)
    return fade_out(b.render(), 0.5)


# ---- 9. twelve statues stand ---------------------------------------------------------------------------------------

def statue_rise(seed):
    L = 2.8
    b = Bus(L)
    low = ((110, 1.0, 1.0), (260, 1.1, 0.8), (620, 1.2, 0.45), (1500, 1.4, 0.16))
    for i in range(4):
        st = 0.05 + 0.18 * i
        d = 1.55 - 0.1 * i
        g = grind(d, [(0, 0), (0.12, 0.7), (d * 0.6, 1.0), (d - 0.06, 0.6), (d, 0)], seed + 10 + i,
                  res=tuple((fc * (1 + 0.12 * i), w, a) for fc, w, a in low), rate=(8, 20 + 3 * i), grit=0.2)
        b.add(K.lowpass(g, 3500, 2), st, 0.07 - 0.008 * i, "nave", 0.35)
    r = K.rng(seed + 1)
    for k in range(9):  # cracking free of their kneeling poses
        b.add(crack(seed + 20 + k, r.uniform(0.7, 1.4)), r.uniform(0.05, 1.5), r.uniform(0.35, 0.75), "nave", 0.35)
    n = K.ns(L)
    rum = unit(K.lowpass(K.noise(n, "brown", seed + 2), 110, 3)) * bp(n, [(0, 0), (0.4, 0.8), (1.6, 1.0), (L, 0.2)])
    b.add(rum, 0.0, 0.08, "nave", 0.25, hp=80)
    for k, tt in enumerate((1.62, 1.8, 1.97, 2.12)):  # feet planted, one after another
        hit = boom(0.8, 72, 38, 0.3, tau=0.05, drive=1.8, attack=0.004)
        put(hit, thud(0.4, 600, 0.12, seed + 30 + k), 0.0, 0.5)
        b.add(glue(hit, 1.6), tt, 0.85 - 0.08 * k, "nave", 0.4, hp=80)
    b.add(dust(L, seed + 3, 380, 5000) * bp(n, [(0, 0), (0.4, 0.6), (1.8, 1.0), (L, 0.3)]), 0.0, 0.035, "nave", 0.4)
    b.add(debris(L, 26, 0.2, 2.6, seed + 4, 0.9, shape=1.2), 0.0, 0.22, "nave", 0.35)
    return fade_out(b.render(), 0.6)


# ---- 10. a stone weapon swings -------------------------------------------------------------------------------------

def swing(seed):
    L = 0.9
    P = 0.42  # the blade passes closest
    b = Bus(L)
    n = K.ns(L)
    env = bp(n, [(0, 0), (P * 0.5, 0.25), (P, 1.0), (P + 0.18, 0.3), (L - 0.05, 0.0), (L, 0)]) ** 2.0
    cen = lambda t: 170 + 480 * np.exp(-((t - P) / 0.16) ** 2)  # noqa: E731
    w = unit(sweep(K.noise(n, "pink", seed + 1), cen, 1.6)) * env
    edge = unit(sweep(K.noise(n, "white", seed + 2), lambda t: 3.2 * cen(t), 3.0)) * env ** 1.5
    t = K.tvec(n)
    push = K.sine(55 + 30 * np.exp(-((t - P) / 0.15) ** 2), n) * env
    air = unit(K.lowpass(K.noise(n, "white", seed + 3), 130, 2)) * env
    b.add(w, 0.0, 0.8, "hall", 0.2)
    b.add(edge, 0.0, 0.12, "hall", 0.2)
    b.add(push, 0.0, 0.22)
    b.add(air, 0.0, 0.3)
    return fade_out(b.render(), 0.1)


# ---- 11. it lands --------------------------------------------------------------------------------------------------

def impact(seed):
    L = 1.2
    b = Bus(L)
    hit = boom(1.0, 86, 42, 0.5, tau=0.06, drive=2.2, attack=0.003)
    put(hit, slab([140, 262, 410, 630, 905], 0.4, K.ns(0.8), seed + 6), 0.0, 0.8)
    put(hit, thud(0.6, 1200, 0.3, seed + 1, attack=0.001), 0.0, 0.8)
    put(hit, crack(seed + 2, 1.3), 0.002, 0.55)
    put(hit, crack(seed + 3, 0.8), 0.035, 0.3)
    b.add(glue(hit, 2.6), 0.0, 1.0, "nave", 0.3, hp=90)
    b.add(debris(L, 18, 0.03, 0.9, seed + 4, 1.0, shape=2.0), 0.0, 0.45, "nave", 0.3)
    n = K.ns(0.8)
    puff = unit(K.lowpass(K.noise(n, "pink", seed + 5), 2500, 2)) * K.perc(n, 0.01, 0.45)
    b.add(puff, 0.01, 0.06, "nave", 0.3)
    return fade_out(b.render(), 0.35)


# ---- 12. the floor gives way ---------------------------------------------------------------------------------------

def pit(seed):
    L = 1.6
    b = Bus(L)
    n = K.ns(L)
    hit = boom(1.2, 70, 36, 0.6, tau=0.08, drive=2.0, attack=0.004) * 0.85
    put(hit, slab([118, 231, 377, 560, 790], 0.45, K.ns(1.0), seed + 6), 0.0, 0.75)
    put(hit, thud(0.6, 1000, 0.3, seed + 7), 0.0, 0.7)
    put(hit, crack(seed + 1, 1.8), 0.0, 0.6)
    b.add(glue(hit, 2.4), 0.0, 1.0, "nave", 0.35, hp=80)
    rum = unit(K.lowpass(K.noise(n, "brown", seed + 2), 120, 3)) * bp(n, [(0, 0.3), (0.12, 1.0), (0.8, 0.8), (L, 0)])
    b.add(rum, 0.0, 0.25, "nave", 0.3, hp=70)
    # the crumble falls away into the dark: denser at first, darker as it goes down
    cr = debris(L, 60, 0.0, 1.3, seed + 3, 0.8, shape=1.4)
    r = K.rng(seed + 4)
    for k in range(10):  # chunks tumbling
        tt = r.uniform(0.02, 1.1) ** 1.2
        put(cr, thud(0.25, r.uniform(250, 600), 0.06, seed + 40 + k), tt, r.uniform(0.4, 0.9) * (1 - 0.5 * tt))
    cr = sweep_lp(cr, lambda t: 7000 * (500 / 7000) ** min(1.0, t / 1.3))
    b.add(pk(cr), 0.0, 0.85, "nave", 0.35)
    b.add(dust(L, seed + 5, 500, 3500) * bp(n, [(0, 0), (0.1, 1.0), (L, 0)]), 0.0, 0.04, "nave", 0.3)
    return fade_out(b.render(), 0.4)


# ---- 13. the hall shakes -------------------------------------------------------------------------------------------

def rumble(seed):
    L = 3.6
    b = Bus(L)
    n = K.ns(L)
    env = bp(n, [(0, 0), (0.45, 1.0), (2.3, 0.85), (L, 0)])
    shake = 0.72 + 0.28 * np.abs(K.smooth_noise(n, 7, seed + 1))
    low = unit(K.lowpass(K.noise(n, "brown", seed + 2), 85, 3))
    low += 0.6 * (K.sine(33.0, n) + 0.6 * K.sine(49.0, n)) * (0.7 + 0.3 * K.smooth_noise(n, 3, seed + 3))
    b.add(low * shake * env, 0.0, 0.7, "nave", 0.2)
    walls = unit(K.bandpass(K.noise(n, "pink", seed + 4), 260, 1.2)) * (0.6 + 0.4 * K.smooth_noise(n, 5, seed + 5))
    b.add(walls * env, 0.0, 0.14, "nave", 0.3)
    b.add(grind(L, [(0, 0), (0.5, 0.5), (2.0, 0.6), (L, 0)], seed + 6, rate=(5, 12), grit=0.2), 0.0, 0.08, "nave", 0.35)
    b.add(dust(L, seed + 7, 500, 5200) * env, 0.0, 0.05, "nave", 0.4)
    b.add(debris(L, 22, 0.3, 3.2, seed + 8, 0.9, shape=1.0), 0.0, 0.25, "nave", 0.4)
    return fade_out(b.render(), 0.5)


# ---- 14. the light drains into its palm (UI, stereo) ----------------------------------------------------------------

def drain(seed):
    L = 8.5
    n = K.ns(L)
    u = np.arange(n) / n
    rise = 2 ** (u ** 1.6)  # everything climbs an octave, faster at the end
    mix = K.Mix(L)
    # the chord, climbing, with more voices joining as it gathers, and ever brighter
    voices = [("D3", 0.0, -0.5), ("A3", 0.0, 0.5), ("D4", 0.0, -0.2), ("F4", 0.0, 0.25), ("A4", 0.0, 0.0),
              ("D5", 2.5, -0.6), ("F5", 4.0, 0.6), ("A5", 5.0, -0.35), ("D6", 6.2, 0.35)]
    bright = lambda t: 900 * (12000 / 900) ** (t / L)  # noqa: E731
    for i, (m, start, p) in enumerate(voices):
        s = K.ns(start)
        fc = hzm(m) * rise[s:]
        c = choir_glide(fc, "a" if i % 3 else "o", 5, seed + 10 + i, bright=0.4)
        pd = K.table_osc(K.harmonic_table(K.saw_amps(float(fc.max()) * 1.02, 0.4, 1.0, 120)), fc, len(fc))
        v = (c + 0.25 * pd) * (0.03 + 0.97 * u[s:] ** 2.2)
        k = min(len(v), K.ns(1.0))
        v[:k] *= K._rise(k)
        v = K.stft_filter(v, lambda tt, f, s0=start: K.lp_resp(bright(tt + s0), 2)(f), frame=2048, hop=512)
        mix.add(v * 0.07, start, 1.0, p, "nave", 0.4)
    # light gathering: glass grains rising in pitch and density
    r = K.rng(seed + 1)
    pent = [0, 3, 5, 7, 10]
    t = 0.2
    k = 0
    while t < L - 0.05:
        uu = t / L
        deg = int(r.integers(0, 10))
        oct_ = 5 + int(r.integers(0, 2)) + int(round(1.5 * uu))
        m = 12 * (oct_ + 1) + 2 + pent[deg % 5] + 12 * (deg // 5)
        f = float(K.hz(m)) * 2 ** (uu ** 1.6) * 2 ** (r.normal(0, 4) / 1200)
        d = r.uniform(0.25, 0.8)
        g = K.glass(min(f, 9000), K.ns(d), 0.5, d * 0.8, seed + 200 + k, hit=0.3) * (0.08 + 0.92 * uu ** 1.8) * r.uniform(0.3, 1)
        mix.add(g * 0.07, t, 1.0, r.uniform(-0.8, 0.8), "nave", 0.6)
        t += 1.0 / (1.5 + 45 * uu ** 2.5)
        k += 1
    # air, sucked up the scale
    for sd, p in ((2, -0.4), (3, 0.4)):
        a = sweep(K.noise(n, "white", seed + sd), lambda tt: 300 * (9000 / 300) ** (tt / L), 0.9)
        mix.add(unit(a) * u ** 2.5 * 0.05, 0.0, 1.0, p, "nave", 0.3)
    # a reversed swell into the peak
    src = Bus(2.0)
    for i, m in enumerate(("D5", "A5", "D6", "A6", "D7")):
        src.add(glass_note(m, 2.0, 0.6, 0.7, 1.5, seed + 40 + i))
    mix.add(reversed_swell(src.dry, room("nave"), 3.0, close=0.01) * 0.3, L - 3.0, 1.0, 0.0)
    # the drone and the ground, rising under it
    d = K.sine(hzm("D1") * rise, n) + 0.5 * K.sine(hzm("D2") * rise, n)
    mix.add(d * (0.1 + 0.9 * u ** 2) * 0.18, 0.0, 1.0, 0.0, "nave", 0.15)
    rum = unit(K.lowpass(K.noise(n, "brown", seed + 4), 100, 3)) * u ** 2.5
    mix.add(rum * 0.12, 0.0, 1.0, 0.0, "nave", 0.2)
    # the heartbeat underneath, quickening
    t = 0.3
    k = 0
    while t < L - 0.2:
        uu = t / L
        iv = 1.25 * (0.33 / 1.25) ** uu
        hb = lubdub(seed + 300 + k, gap=float(np.clip(0.3 * iv, 0.11, 0.3)))
        mix.add(hb * (0.3 + 0.7 * uu) * 0.3, t, 1.0, 0.0, "nave", 0.1)
        t += iv
        k += 1
    y = stereo_mix(mix, ("nave",))
    # it ends at its peak: "close" cuts it
    kk = K.ns(0.008)
    y[-kk:] *= K._rise(kk)[::-1][:, None]
    return y


# ---- 15. the hand closes -------------------------------------------------------------------------------------------

def close(seed):
    L = 2.5
    T = 0.06  # the impact (a tiny breath before it)
    CUT = 0.46  # then nothing
    b = Bus(L)
    pre = K.bandpass(K.noise(K.ns(T), "white", seed + 1), 1800, 0.8) * K._rise(K.ns(T)) ** 3
    b.add(pk(pre) * 0.3, 0.0)
    hit = boom(1.2, 72, 34, 0.9, tau=0.12, drive=2.6, attack=0.003) * 0.55
    put(hit, slab([72, 113, 168, 244, 352], 0.45, K.ns(0.6), seed + 6), 0.0, 1.0)
    # the crush: everything at once, saturated
    n = K.ns(0.5)
    cr = K.bandpass(K.noise(n, "white", seed + 2), 700, 0.5) + 0.5 * K.bandpass(K.noise(n, "white", seed + 7), 1900, 0.8)
    cr = np.tanh(4 * unit(cr)) * K.perc(n, 0.001, 0.4)
    put(hit, K.lowpass(cr, 5000, 2), 0.0, 1.0)
    put(hit, thud(0.5, 600, 0.25, seed + 3), 0.0, 0.9)
    put(hit, crack(seed + 4, 2.0), 0.0, 0.6)
    put(hit, K.crackle(n, rate=900, seed=seed + 5, bright=0.5, burstiness=0.2) * K.perc(n, 0.001, 0.12), 0.0, 0.25)
    b.add(glue(hit, 2.6), T, 1.0)
    y = b.render()
    # the world snaps shut: a hard cut, and only the faintest of tails
    gate = np.ones(len(y))
    c0, c1 = K.ns(CUT), K.ns(CUT + 0.025)
    gate[c0:c1] = 1 - (1 - 0.018) * K._rise(c1 - c0)
    gate[c1:] = 0.018
    y = y * gate
    tl = K.ns(L - CUT)
    ring = K.sine(hzm("D8"), tl) * K.adsr(tl, 0.15, 0.2, 0.7, 1.6) * K.perc(tl, 0.001, 2.4)
    put(y, ring, CUT, 0.004)  # the ears ringing in the black
    return fade_out(y, 0.8)


# ---- 16. a heartbeat in the dark (UI) ------------------------------------------------------------------------------

def heartbeat(seed):
    L = 1.3
    b = Bus(L)
    x = np.zeros(K.ns(1.0))
    put(x, beat(seed, 60, 38, 0.26), 0.0)
    put(x, beat(seed + 1, 70, 44, 0.2), 0.3, 0.7)
    ka = K.ns(0.012)
    x[:ka] *= K._rise(ka)  # soft onset: felt, not a click
    b.add(x, 0.04, 1.0)
    return fade_out(K.lowpass(b.render(), 700, 2), 0.3)


# ---- 17. the darkness tears open (UI, stereo) ----------------------------------------------------------------------

TEAR_OPEN = 0.85


def tear(seed):
    L = 3.0
    T = TEAR_OPEN
    mix = K.Mix(L)
    nt = K.ns(T + 0.15)
    u = np.clip(np.arange(nt) / K.ns(T), 0, 1)
    env = u ** 1.8 * bp(nt, [(0, 1), (T, 1), (T + 0.15, 0)])
    r = K.rng(seed)
    for c, p in enumerate((-1.0, 1.0)):
        # the rip: fast, uneven micro-tears, climbing; L and R part company as it opens (from the centre outward)
        imp = (r.random(nt) < (300 + 1500 * u) / SR) * r.random(nt) ** 2
        k = np.arange(K.ns(0.004))
        rip = fconv(imp, np.exp(-k / K.ns(0.0008)))[:nt] * K.noise(nt, "white", seed + 10 + c)
        rip = sweep(rip, lambda t: 800 * (5000 / 800) ** min(1.0, t / T), 0.8)
        rush = sweep(K.noise(nt, "pink", seed + 20 + c), lambda t: 250 * (6000 / 250) ** min(1.0, t / T), 1.0)
        layer = np.tanh(0.6 * unit(rip) + 0.5 * unit(rush)) * env
        mix.add(layer * 0.24, 0.0, 1.0, p * 0.15, "nave", 0.25)
        mix.add(layer * u * 0.16, 0.0, 1.0, p * 0.9)
    # the opening: a reversed glass swell lands on it, then warmth
    src = Bus(1.2)
    for i, m in enumerate(("D5", "A5", "D6")):
        src.add(glass_note(m, 1.2, 0.6, 0.6, 1.0, seed + 30 + i))
    mix.add(K.stereo(reversed_swell(src.dry, room("nave"), 1.0) * 0.2), T - 1.0)
    nw = K.ns(L - T)
    for i, (m, g, p) in enumerate((("D3", 0.6, -0.3), ("A3", 0.5, 0.3), ("D4", 0.45, 0.0))):
        pd = K.pad(hzm(m), nw, 6, seed + 40 + i, bright=0.32)
        mix.add(pd * K.adsr(nw, 0.25, 0.4, 0.75, 1.3) * g * 0.13, T, 1.0, p, "nave", 0.5)
    for i, (m, p) in enumerate((("D4", -0.4), ("A4", 0.4))):
        c = K.choir(hzm(m), nw, "a", 5, seed + 50 + i, bright=0.38)
        mix.add(c * K.adsr(nw, 0.3, 0.5, 0.7, 1.2) * 0.05, T, 1.0, p, "nave", 0.6)
    w = K.sine(hzm("D2"), nw) * K.perc(nw, 0.05, 1.8)
    mix.add(w * 0.22, T, 1.0, 0.0, "nave", 0.2)
    # the Signal's sigh, D A Bb A, rising to D; then a few glints of the System's own D major
    sigh = [(0.03, "D5", 0.36), (0.18, "A5", 0.4), (0.33, "Bb5", 0.44), (0.48, "A5", 0.42), (0.67, "D6", 0.55)]
    for i, (dt, m, v) in enumerate(sigh):
        mix.add(glass_note(m, L - T - dt, v, 0.55, 1.6, seed + 60 + i), T + dt, 0.42, 0.3 * np.sin(1.7 * i), "nave", 0.45)
    for i, (dt, m) in enumerate(((0.85, "F#6"), (0.93, "A6"), (1.01, "E7"), (1.1, "D7"))):
        mix.add(glass_note(m, L - T - dt, 0.16, 0.7, 0.9, seed + 70 + i), T + dt, 0.3, 0.6 * np.sin(2.3 * i + 1), "nave", 0.55)
    return fade_out(stereo_mix(mix, ("nave",)), 0.9)


# ---- 18. the void (UI bed, stereo, seamless loop) ------------------------------------------------------------------

def void(seed):
    L = 12.0
    P = 6.0  # pre-roll, so the room is already full when the loop begins
    XF = 2.0
    total = P + L + XF
    n = K.ns(total)
    mix = K.Mix(total)
    # cold air, barely moving
    for c, p in enumerate((-1.0, 1.0)):
        a = unit(K.lowpass(K.noise(n, "pink", seed + 1 + c), 450, 2)) * (0.7 + 0.3 * K.smooth_noise(n, 0.15, seed + 3 + c))
        hi = unit(K.bandpass(K.noise(n, "white", seed + 5 + c), 2800, 0.7)) * (0.5 + 0.5 * K.smooth_noise(n, 0.2, seed + 7 + c)) ** 2
        mix.add(a * 0.045 + hi * 0.003, 0.0, 1.0, p)
    # a faint distant choir, an open fifth, very far away
    for i, (m, v, g, p) in enumerate((("D2", "u", 1.0, -0.3), ("A2", "o", 0.7, 0.3), ("D3", "u", 0.35, 0.0))):
        c = K.choir(hzm(m), n, v, 6, seed + 10 + i, bright=0.22, vib_cents=8)
        c = K.lowpass(c, 900, 2) * (0.6 + 0.4 * K.smooth_noise(n, 0.12, seed + 20 + i))
        mix.add(c * g * 0.02, 0.0, 1.0, p, "void", 1.0)
    d = K.sine(hzm("D1"), n) * (0.7 + 0.3 * K.smooth_noise(n, 0.1, seed + 30))
    mix.add(d * 0.02, 0.0, 1.0, 0.0)
    # the odd far glint: a mote of blue light
    for i, (tt, m, v, p) in enumerate(((1.8, "A6", 0.22, -0.55), (5.6, "D7", 0.16, 0.6), (8.4, "F6", 0.1, -0.1))):
        g = glass_note(m, 3.0, v, 0.45, 2.2, seed + 40 + i, core=0.5)
        mix.add(K.lowpass(g, 6000, 1) * 0.12, P + tt, 1.0, p, "void", 1.4)
    y = stereo_mix(mix, ("void",))[K.ns(P):]
    return K.loop_seamless(y, XF)


# ---- the table -----------------------------------------------------------------------------------------------------

# name: (render, gated RMS dBFS, mono, event volume, attenuation blocks or None, subtitle[, give dB])
# give: how far under its target RMS a sound may land so its peaks stay unlimited (default GIVE)
SOUNDS = OrderedDict([
    ("bell", (bell, -16.5, True, 1.0, 96, "Great bell tolls", 1.0)),
    ("doors", (doors, -17.0, True, 1.0, 64, "Stone doors grind shut")),
    ("flames_out", (flames_out, -19.0, True, 1.0, 64, "Every flame dies")),
    ("candle", (candle, -23.0, True, 0.5, 24, "Candle lights", 1.0)),
    ("words", (words, -19.0, False, 1.0, None, "A rule is carved in light")),
    ("eyes", (eyes, -17.0, True, 1.0, 96, "The Seated One opens its eyes", 1.5)),
    ("gaze_charge", (gaze_charge, -21.5, True, 1.0, 48, "The Seated One's gaze gathers", 2.0)),
    ("gaze_fire", (gaze_fire, -18.0, True, 1.0, 64, "The gaze sears")),
    ("statue_rise", (statue_rise, -18.0, True, 1.0, 64, "Statues rise")),
    ("swing", (swing, -22.0, True, 1.0, 24, "Stone weapon swings", 2.0)),
    ("impact", (impact, -18.5, True, 1.0, 32, "Stone weapon strikes")),
    ("pit", (pit, -18.5, True, 1.0, 48, "The floor collapses")),
    ("rumble", (rumble, -19.0, True, 1.0, 96, "The hall shakes")),
    ("drain", (drain, -22.5, False, 1.0, None, "Light drains into its palm", 1.5)),
    ("close", (close, -16.0, True, 1.0, None, "The hand closes")),
    ("heartbeat", (heartbeat, -21.0, True, 0.8, None, "Heartbeat")),
    ("tear", (tear, -19.5, False, 1.0, None, "The darkness tears open")),
    ("void", (void, -23.5, False, 0.5, None, "Cold, dark stillness", 0.5)),
])
STREAM_OVER = 6.0  # seconds: longer files are streamed
GIVE = 3.0
QUALITY = 5


def event_json(name, dur):
    _, _, _, vol, att = SOUNDS[name][:5]
    e = OrderedDict(name="awaken:ritual/" + name)
    if vol != 1.0:
        e["volume"] = vol
    if dur > STREAM_OVER:
        e["stream"] = True
    if att:
        e["attenuation_distance"] = att
    return OrderedDict(subtitle="subtitles.awaken.story.ritual." + name, sounds=[e])


def master(x, rms, ceiling, give):
    """audiokit.master's job, but a hit keeps its shape. audiokit iterates gain-to-RMS and limiting, which on a
    blow with a long room tail works as a 10 dB compressor (the tail counts in the gated RMS, so every pass lifts
    it and the limiter pushes the blow back down). Here the level is the target RMS or as loud as the peak
    ceiling allows, whichever is lower, but never more than `give` dB under the target; one limiter pass takes the
    rest. Returns (audio, dB the limiter took)."""
    y = K.highpass(x, 25.0, 2)
    g_rms = rms - K.gated_rms(y)
    g = max(min(g_rms, ceiling - K.true_peak(y)), g_rms - give)
    y = y * K.dbamp(g)
    over = max(0.0, K.true_peak(y) - ceiling)
    if over > 0:
        y = K.limit(y, ceiling - 0.3)
        if K.true_peak(y) > ceiling:
            y = y * K.dbamp(ceiling - K.true_peak(y) - 0.05)
    return K.declick(y, 0.001, 0.01), over


def render(name):
    fn, rms, mono = SOUNDS[name][:3]
    give = SOUNDS[name][6] if len(SOUNDS[name]) > 6 else GIVE
    seed = 7000 + list(SOUNDS).index(name) * 37
    x = fn(seed)
    if mono:
        x = K.mono(x)
    tgt, tol, ceil, _ = TARGETS["sfx"]
    assert abs(rms - tgt) <= tol and abs(rms - give - tgt) <= tol, f"{name}: {rms} (give {give}) outside the sfx window"
    return master(x, rms, ceil - 0.7, give)  # 0.7 dB of room for the encoder


def write_json(durs):
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    events = OrderedDict(("story.ritual." + n, event_json(n, durs[n])) for n in SOUNDS)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(events, f, indent=2)
        f.write("\n")
    path = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(path), object_pairs_hook=OrderedDict) if os.path.exists(path) else OrderedDict()
    for n, row in SOUNDS.items():
        lang["subtitles.awaken.story.ritual." + n] = row[5]
    with open(path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


def build_sounds(only=()):
    todo = [n for n in SOUNDS if not only or n in only]
    unknown = [o for o in only if o not in SOUNDS]
    if unknown:
        raise SystemExit("no such sound: " + ", ".join(unknown))
    durs = {}
    for n in SOUNDS:
        p = os.path.join(SND, n + ".ogg")
        if n not in todo and os.path.exists(p):
            durs[n] = len(K.read_audio(p)) / SR
    for n in todo:
        t0 = time.time()
        y, lim = render(n)
        path = os.path.join(SND, n + ".ogg")
        K.write_ogg(path, y, QUALITY)
        durs[n] = len(y) / SR
        print(f"  ritual/{n:12s} {durs[n]:5.2f} s  {'mo' if y.ndim == 1 else 'st'}  RMS {K.gated_rms(y):6.1f}  "
              f"LUFS {K.loudness(y):6.1f}  TP {K.true_peak(y):5.1f}  limited {lim:4.1f} dB  {os.path.getsize(path) / 1024:5.0f} KB  "
              f"({time.time() - t0:.1f} s)")
    missing = [n for n in SOUNDS if n not in durs]
    if missing:
        raise SystemExit("not rendered yet: " + ", ".join(missing))
    write_json(durs)
    print(f"ritual sounds: {len(todo)} rendered, {len(SOUNDS)} events in merge/story")


def sheet(outdir):
    """Spectrogram sheets of the rendered files (audio_sheet's tiles), six to a sheet."""
    import audio_sheet as S
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    names = list(SOUNDS)
    for s in range(0, len(names), 6):
        tiles = [S.tile(os.path.join(SND, n + ".ogg"), width=600, spec_h=150) for n in names[s:s + 6]]
        tw, th = tiles[0].size
        im = Image.new("RGB", (2 * (tw + 8) + 8, 3 * (th + 8) + 8), (10, 10, 14))
        for k, t in enumerate(tiles):
            im.paste(t, (8 + (k % 2) * (tw + 8), 8 + (k // 2) * (th + 8)))
        p = os.path.join(outdir, f"ritual_sounds_{s // 6 + 1}.png")
        im.save(p)
        print(p)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        build_sounds(sys.argv[2:])
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        sheet(sys.argv[2] if len(sys.argv) > 2 else "/tmp/ritual_sounds")
    else:
        print(__doc__)
