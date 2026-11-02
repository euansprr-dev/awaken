"""The Ritual of the Bell, sound v2: the cinematic pass (Euan, 2026-11-02: the statues' steps are "very small sounds",
every step, hit, laser and the final moment "far from epic"; bring the whole first sequence to AAA cinematic level).

Built only from recordings already in the repo under CC0 (tools/art/sources/ritual: BigSoundBank's fall of stone,
tombstone opening, thunder, far explosion, wind, welding arc; tools/art/sources/kenney: Kenney's heavy soft, punch,
mining and plate impacts) layered over the audio kit's synthesis. Nothing is downloaded.

What changed from v1 (ritual_recordings.py), and why:
  * Loud enough to be big. v1 mastered the stone at -41 LUFS (a vanilla Ravager step is -18, an explosion -13) and
    the game then played it at about half gain. v2 masters every physical hit to -1 dBTP with its body dense
    (momentary peaks about -10 to -14 LUFS), so the game's gain sets the mix instead of fighting the files.
  * Weight you can hear on a laptop. A stone colossus's footfall is a felt low thump: a sub that drops in pitch,
    saturated so its 2nd and 3rd harmonics (80-140 Hz) carry it on MacBook speakers, under a stone clack, grit, kicked
    pebbles, a puff of dust and the hall's tail.
  * Each layer has a job (contact, body, sub, debris, air, room), and variants change the take, not just the pitch.

    python3 tools/art/specs/ritual_v2.py preview DIR [name ...]   render to DIR as .ogg and .mp3 with a loudness table
    python3 tools/art/specs/ritual_v2.py list                      the cue names

Mono for every sound placed in the world (OpenAL pans and attenuates it); stereo for the hall bed and the god's
scene-wide moments. Deterministic: every random source is seeded.
"""
import json
import math
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, ART)
import audiokit as K  # noqa: E402

SR = K.SR
SRC_DIR = os.path.join(ART, "sources")
SOURCES = {
    "stone_fall": "ritual/1022.flac",       # Fall of stone (BigSoundBank 1022, CC0): the clean rock contacts
    "tomb": "ritual/0580.flac",             # Tombstone opening (0580): stone friction and grinding
    "thunder": "ritual/3113.flac",          # Thunder 2 (3113): low mass and long tails
    "blast": "ritual/1023.flac",            # Explosion far away (1023): pressure
    "wind": "ritual/0595.flac",             # Wind (0595): air
    "arc": "ritual/0950.flac",              # Long welding (0950): a real electric arc
    "bell": "ritual/3446.flac",             # Bell 1 o'clock (3446)
}
for _i in range(5):
    SOURCES[f"soft{_i}"] = f"kenney/impact-sounds/Audio/impactSoft_heavy_00{_i}.ogg"
for _i in (1, 2, 3, 4):
    SOURCES[f"punch{_i}"] = f"kenney/impact-sounds/Audio/impactPunch_heavy_00{_i}.ogg"
for _i in (0, 1, 3):
    SOURCES[f"mine{_i}"] = f"kenney/impact-sounds/Audio/impactMining_00{_i}.ogg"
for _i in range(5):
    SOURCES[f"plate{_i}"] = f"kenney/impact-sounds/Audio/impactPlate_heavy_00{_i}.ogg"

# the strongest single rock contacts in stone_fall (seconds), and its runs of bouncing rubble
HITS = (1.720, 4.660, 6.230, 10.125, 14.995, 16.590, 8.310, 28.000)
RUBBLE = (16.70, 20.10, 22.02, 23.53, 17.05)

_cache = {}


def raw(name):
    if name not in _cache:
        x = K.mono(K.read_audio(os.path.join(SRC_DIR, SOURCES[name])))
        _cache[name] = x - np.mean(x)
    return _cache[name]


def take(name, start, dur, rate=1.0):
    """dur seconds of a recording from start, played at `rate` (0.7 = lower and slower), normalised to peak 1."""
    x = raw(name)
    a = max(0, K.ns(start))
    need = int(K.ns(dur) * rate) + 2
    seg = x[a:a + need]
    if rate != 1.0:
        idx = np.arange(0, len(seg) - 1, rate)
        seg = np.interp(idx, np.arange(len(seg)), seg)
    seg = K.pad_to(seg[:K.ns(dur)], K.ns(dur))
    return norm(seg)


def norm(x):
    return x / max(np.max(np.abs(x)), 1e-12)


def env(x, pts):
    t, g = zip(*pts)
    return x * np.interp(np.arange(len(x)) / SR, t, g)


def place(dst, x, at=0.0, gain=1.0):
    i = K.ns(at)
    if dst.ndim == 2 and x.ndim == 1:
        x = np.stack([x, x], axis=1)
    n = min(len(x), len(dst) - i)
    if n > 0:
        dst[i:i + n] += x[:n] * gain


def sat(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def sub(dur, f0, f1, t60, attack=0.003, drive=2.2):
    """A felt drop: a sine falling from f0 to f1, saturated so its harmonics carry it on small speakers."""
    n = K.ns(dur)
    t = K.tvec(n)
    f = f1 + (f0 - f1) * np.exp(-t / 0.09)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return sat(np.sin(ph) * K.perc(n, attack, t60), drive)


def noise_env(dur, lo, hi, pts, seed, color="pink"):
    n = K.noise(K.ns(dur), color, seed=seed)
    n = K.highpass(K.lowpass(n, hi, 2), lo, 2)
    return env(norm(n), pts)


_IRS = {}


def hall_ir(t60=2.6, bright=0.25, seed=7, predelay=0.03):
    key = (t60, bright, seed, predelay)
    if key not in _IRS:
        ir = K.reverb_ir(t60=t60, predelay=predelay, bright=bright, width=1.0, early=0.6, size=1.6, seed=seed, low_mult=1.35)
        _IRS[key] = ir
    return _IRS[key]


def room(x, t60=2.6, wet=0.25, bright=0.25, seed=7, stereo=False):
    """The hall: the dry sound plus a dark stone tail (a mono sum for world sounds, both sides for scene sounds)."""
    ir = hall_ir(t60, bright, seed)
    w = K.highpass(K.convolve(x, ir), 110, 2)
    if stereo:
        out = np.zeros((len(w), 2))
        place(out, K.stereo(x) if x.ndim == 1 else x, 0, 1.0)
        return out + w * wet
    out = np.zeros(len(w))
    out[:len(x)] += K.mono(x) if x.ndim == 2 else x
    return out + (w[:, 0] + w[:, 1]) * 0.5 * wet


def finish(x, peak=-1.0, push=4.0, hp=24.0, tail=0.08):
    """High-pass the rumble, push the body `push` dB into a transparent limiter, set the true peak, fade the tail."""
    y = K.highpass(x, hp, 2)
    y = norm(y) * K.dbamp(peak + push)
    y = K.limit(y, peak - 0.25, release=0.09)
    tp = K.true_peak(y)
    y = y * K.dbamp(peak - tp - 0.05)
    n = len(y)
    k = min(n, K.ns(tail))
    fade = np.cos(0.5 * np.pi * np.arange(k) / k)
    if y.ndim == 2:
        y[n - k:] *= fade[:, None]
    else:
        y[n - k:] *= fade
    return K.declick(y, 0.0005, 0.005)


def trim(x, floor_db=-54.0, keep=0.05):
    """Cuts the silent end of a tail."""
    m = np.abs(K.mono(x) if x.ndim == 2 else x)
    thr = K.dbamp(floor_db)
    idx = np.nonzero(m > thr)[0]
    end = min(len(x), (idx[-1] if len(idx) else len(x)) + K.ns(keep))
    return x[:end]


# =====================================================================================================================
# The statues
# =====================================================================================================================

def footfall(v):
    """A stone colossus's foot coming down flat: clack, thump, felt drop, a grind as the weight settles, pebbles, dust,
    the hall. Four takes (v 0..3): a different rock contact, body and rubble each time."""
    x = np.zeros(K.ns(1.4))
    # contact: basalt sole on stone floor, dropped 7-9 semitones for mass
    c = take("stone_fall", HITS[v] - 0.004, 0.2, rate=0.6 + 0.035 * v)
    c = env(K.highpass(c, 160, 2), [(0, 0), (0.0015, 1), (0.025, 0.5), (0.1, 0.1), (0.2, 0)])
    place(x, norm(c), 0.0, 0.5)
    # body: the weight landing (a heavy soft impact a fifth lower)
    b = take(f"soft{(v + 1) % 5}", 0, 0.55, rate=0.68)
    place(x, norm(K.lowpass(b, 650, 2)), 0.002, 0.95)
    p = take(f"punch{v + 1}", 0.0, 0.45, rate=0.62)
    place(x, norm(K.lowpass(p, 420, 2)), 0.0, 0.45)
    # the felt drop
    place(x, sub(0.8, 54 - 3 * v, 33, 0.36), 0.0, 0.85)
    # the weight settling: a short grind
    g = take("tomb", 13.62 + 0.21 * v, 0.32, rate=0.72)
    g = env(K.bandpass(g, 650, 0.7), [(0, 0), (0.03, 1), (0.18, 0.35), (0.32, 0)])
    place(x, norm(g), 0.035, 0.09)
    # pebbles kicked loose, and a breath of dust pushed out from under the foot
    d = take("stone_fall", RUBBLE[v], 0.85, rate=0.82)
    d = env(K.highpass(d, 700, 2), [(0, 0), (0.008, 1), (0.4, 0.35), (0.85, 0)])
    place(x, norm(d), 0.05, 0.08)
    place(x, noise_env(0.7, 900, 5200, [(0, 0), (0.008, 1), (0.12, 0.3), (0.7, 0)], 31 + v), 0.0, 0.05)
    y = room(x, t60=2.0, wet=0.2, seed=11)
    return trim(finish(y, -1.0, push=4.5))


def windup(v=0):
    """The statue's joints grind as it heaves the glaive up: stone on stone, a creak, dust trickling from the shoulder."""
    L = 1.3
    x = np.zeros(K.ns(L))
    g = take("tomb", 10.9 + 0.4 * v, L, rate=0.5)
    g = env(K.lowpass(K.highpass(g, 90, 2), 2400, 2), [(0, 0), (0.12, 0.6), (0.5, 1), (0.95, 0.8), (1.3, 0)])
    place(x, norm(g), 0, 0.8)
    for k, at in enumerate((0.05, 0.42, 0.8)):
        c = take("stone_fall", HITS[(k + 3 + v) % 8], 0.12, rate=1.0)
        place(x, norm(K.highpass(env(c, [(0, 0), (0.001, 1), (0.05, 0.2), (0.12, 0)]), 1200, 2)), at, 0.12 - 0.02 * k)
    place(x, noise_env(L, 1500, 7000, [(0, 0), (0.3, 0.5), (1.0, 0.3), (1.3, 0)], 41 + v), 0, 0.05)
    place(x, sub(L, 40, 36, 0.9, attack=0.4, drive=1.2), 0, 0.25)
    y = room(x, t60=2.0, wet=0.15, seed=11)
    return trim(finish(y, -3.0, push=2.0))


def whoosh(v=0):
    """The glaive coming down: a heavy blade of stone moving a lot of air. A low push swells into a ripping whoosh
    that peaks where the blade moves fastest (0.30 s) and is cut by the impact."""
    L = 0.62
    n = K.ns(L)
    t = K.tvec(n)
    peak = 0.30
    amp = np.where(t < peak, (t / peak) ** 2.4, np.exp(-(t - peak) / 0.07))
    # air: real wind, swept through a resonant band that rises with the blade's speed then falls (its Doppler)
    a = take("wind", 31.0 + 3.1 * v, L, rate=1.0)
    centre = lambda tc: 260 + 1500 * max(0.0, 1 - abs(tc - peak) / 0.22) ** 1.6

    def resp(tc, f):
        c = centre(tc)
        return 1.0 / np.sqrt(1 + ((f - c) / (0.35 * c)) ** 2) ** 2
    a = norm(K.stft_filter(a, resp, frame=1024, hop=128))
    # flutter: a big blade cuts the air in beats
    flutter = 1.0 - 0.35 * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(14 + 30 * amp) / SR))
    x = a * amp * flutter
    # the mass: a low push under it
    low = K.lowpass(K.noise(n, "pink", seed=51 + v), 160, 3)
    x += norm(low) * amp * 0.75
    x += sat(K.sine(K.glide(n, 55, 85), n) * amp ** 1.5, 1.5) * 0.3
    x = env(x, [(0, 0), (0.02, 1), (L - 0.05, 1), (L, 0)])
    return trim(finish(room(x, t60=1.4, wet=0.06, seed=11), -2.0, push=3.0))


def strike(v=0):
    """The glaive's blade into the floor 2.5 blocks ahead: a crack, a crunch of stone, a boom felt in the chest,
    rubble raining back down, dust, and the hall ringing."""
    x = np.zeros(K.ns(3.6))
    # the crack: rock on rock, two contacts a hair apart (blade, then the shaft's weight behind it)
    for k, (h, rate, g) in enumerate(((HITS[(4 + v) % 8], 0.78, 1.0), (HITS[(6 + v) % 8], 0.66, 0.55))):
        c = take("stone_fall", h - 0.003, 0.3, rate=rate)
        c = env(K.highpass(c, 220, 2), [(0, 0), (0.001, 1), (0.04, 0.5), (0.15, 0.12), (0.3, 0)])
        place(x, norm(c), 0.012 * k, 0.55 * g)
    # a pick biting stone: the chink and the crunch
    m = take(f"mine{(0, 1, 3)[v % 3]}", 0, 0.9, rate=0.72)
    place(x, norm(K.lowpass(m, 3500, 2)), 0.0, 0.5)
    # the boom: punch and pressure, and a felt drop
    p = take(f"punch{2 + v}", 0, 0.5, rate=0.52)
    place(x, norm(K.lowpass(p, 380, 2)), 0.0, 0.8)
    bl = env(take("blast", 0.02, 1.6, rate=0.8), [(0, 0), (0.004, 1), (0.25, 0.45), (1.6, 0)])
    place(x, norm(K.lowpass(bl, 420, 2)), 0.0, 0.6)
    place(x, sub(1.8, 50, 26, 1.0), 0.0, 0.85)
    # rubble raining back down and grit
    d = take("stone_fall", RUBBLE[(v + 1) % 5], 1.6, rate=0.9)
    d = env(K.highpass(d, 500, 2), [(0, 0), (0.06, 0.4), (0.25, 1), (0.9, 0.4), (1.6, 0)])
    place(x, norm(d), 0.08, 0.16)
    place(x, noise_env(1.2, 1200, 7000, [(0, 0), (0.005, 1), (0.2, 0.35), (1.2, 0)], 61 + v), 0.0, 0.07)
    y = room(x, t60=2.8, wet=0.3, seed=11)
    return trim(finish(y, -1.0, push=6.0))


def rise():
    """Twelve statues of basalt tearing free of the floor and standing: cracks, a slow groan of stone from all sides,
    dust pouring off them, then their feet planting one after another."""
    L = 5.0
    x = np.zeros(K.ns(L))
    # the first crack, everywhere at once
    for k in range(3):
        c = take("stone_fall", HITS[k * 2], 0.35, rate=0.7 + 0.08 * k)
        place(x, norm(K.highpass(env(c, [(0, 0), (0.001, 1), (0.08, 0.3), (0.35, 0)]), 200, 2)), 0.02 * k, 0.4)
    place(x, sub(1.6, 44, 28, 1.2), 0.0, 0.5)
    # the groan: grinding stone slowed into a deep moan, three of them offset (the hall is full of them)
    for k, (st, rate, at) in enumerate(((10.8, 0.36, 0.15), (2.9, 0.42, 0.45), (14.4, 0.33, 0.8))):
        g = take("tomb", st, 3.2, rate=rate)
        g = env(K.lowpass(K.highpass(g, 60, 2), 1600, 2), [(0, 0), (0.4, 0.8), (1.6, 1), (2.6, 0.6), (3.2, 0)])
        place(x, norm(g), at, 0.45)
    # micro-cracks and dust pouring off them
    for k in range(9):
        c = take("stone_fall", HITS[(k * 3) % 8] + 0.05, 0.25, rate=1.1 + 0.05 * (k % 3))
        place(x, norm(K.highpass(env(c, [(0, 0), (0.001, 1), (0.06, 0.2), (0.25, 0)]), 900, 2)), 0.4 + 0.27 * k, 0.1)
    place(x, noise_env(L, 1100, 6500, [(0, 0), (0.6, 0.4), (2.0, 0.6), (3.6, 0.35), (5.0, 0)], 71), 0, 0.09)
    # the feet planting as they come up (the staggered rise, 0.1 s apart down the nave)
    for k, at in enumerate((2.1, 2.35, 2.62, 2.95, 3.3)):
        f = footfall(k % 4)
        place(x, f, at, 0.35 - 0.03 * k)
    y = room(x, t60=3.0, wet=0.25, seed=11)
    return trim(finish(y, -1.0, push=3.5))


# =====================================================================================================================
# The god
# =====================================================================================================================

def eyes():
    """Its eyes open (4 s, the bloom at 2.9 s): a deep swell of the hall itself and a low choir under a rising shimmer,
    sucked into one blinding hit: a boom, a thunder-crack, and a ringing that hangs in the air."""
    L = 6.0
    BLOOM = 2.9
    x = np.zeros((K.ns(L), 2))
    n_up = K.ns(BLOOM)
    # the swell: a low choir on D, its fifth and octave, rising in level and brightness
    ch = np.zeros(n_up)
    for f0, vo, s in ((36.7, "o", 1), (55.0, "o", 2), (73.4, "a", 3), (110.0, "a", 4)):
        ch += K.choir(f0, n_up, vo, voices=5, seed=80 + s, bright=0.35, vib_cents=8, spread=7) * (0.9 if f0 < 60 else 0.6)
    ch = norm(K.lowpass(ch, 2400, 2)) * np.linspace(0, 1, n_up) ** 2.2
    place(x, np.stack([ch, np.roll(ch, K.ns(0.013))], axis=1), 0, 0.5)
    # air rushing toward the face (a reversed wind tail), and a sub pressure rising under it
    w = take("wind", 48.0, BLOOM, rate=1.0)
    w = norm(K.highpass(w, 300, 2)) * np.linspace(0, 1, n_up) ** 3
    place(x, np.stack([w, np.roll(w, K.ns(0.021))], axis=1), 0, 0.3)
    place(x, K.sine(K.glide(n_up, 28, 41), n_up) * np.linspace(0, 1, n_up) ** 2, 0, 0.5)
    # the shimmer: glassy partials high over the hall, rising a fifth
    sh = sum(K.sine(K.glide(n_up, f * 0.67, f), n_up) * (0.5 ** k) for k, f in enumerate((1174.7, 1760.0, 2349.3)))
    place(x, np.stack([sh, -sh], axis=1) * np.linspace(0, 1, n_up)[:, None] ** 3, 0, 0.06)
    # the bloom
    hit = np.zeros(K.ns(L - BLOOM))
    place(hit, sub(3.0, 58, 27, 2.2), 0, 0.9)
    th = take("thunder", 1.1, 2.9, rate=0.75)
    place(hit, norm(env(K.lowpass(th, 1400, 2), [(0, 0), (0.01, 1), (0.6, 0.6), (2.9, 0)])), 0, 0.6)
    p = take("punch3", 0, 0.5, rate=0.5)
    place(hit, norm(K.lowpass(p, 300, 2)), 0, 0.6)
    ring = sum(K.sine(f, len(hit)) * K.perc(len(hit), 0.002, 2.6 - 0.4 * k) * (0.6 ** k) for k, f in enumerate((587.3, 880.0, 1174.7, 1760.0)))
    place(hit, ring, 0, 0.12)
    place(x, np.stack([hit, hit], axis=1), BLOOM, 1.0)
    y = room(x, t60=3.4, wet=0.3, seed=13, stereo=True)
    return trim(finish(y, -1.0, push=3.0))


def gaze_charge():
    """45 ticks (2.25 s) to the release, exactly: the beam gathers in its eyes. A rising whine, an arc crackling ever
    faster, the air sucked toward it, and a sub that tightens. Ends on the release, not over it."""
    L = 2.25
    n = K.ns(L)
    t = K.tvec(n)
    u = t / L
    x = np.zeros(n)
    # the whine: a buzzing tone climbing an octave and a fifth, its edge opening up
    f = 70 * 2 ** (u ** 1.6 * 1.6)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.5 * np.sin(2 * ph + 0.4 * np.sin(3 * ph)) + 0.25 * np.sin(3.01 * ph)
    tone = K.stft_filter(sat(tone, 1.0 + 3.0 * u ** 2), lambda tc, fr: 1.0 / np.sqrt(1 + (fr / (400 + 2600 * (tc / L) ** 2)) ** 4))
    place(x, norm(tone) * u ** 1.8, 0, 0.26)
    # the arc: a real welding arc, spitting faster
    arc = take("arc", 2.0, L, rate=1.0)
    arc = K.highpass(arc, 500, 2) * (u ** 2.0)
    place(x, norm(arc), 0, 0.4)
    # the air drawn in (reversed wind)
    w = take("wind", 60.0, L, rate=1.0)[::-1]
    place(x, norm(K.bandpass(w, 900, 0.6)) * u ** 3, 0, 0.3)
    place(x, K.sine(K.glide(n, 30, 46), n) * u ** 2, 0, 0.4)
    x = env(x, [(0, 0), (0.05, 1), (L - 0.012, 1), (L, 0)])
    return finish(x, -2.0, push=2.0, tail=0.012)


def gaze_fire():
    """The release at its eyes: a deep crack of pressure and a searing beam tone that tears out across the hall."""
    L = 2.6
    x = np.zeros(K.ns(L))
    bl = take("blast", 0.02, 1.8, rate=0.85)
    place(x, norm(env(K.lowpass(bl, 900, 2), [(0, 0), (0.003, 1), (0.3, 0.4), (1.8, 0)])), 0, 0.7)
    place(x, sub(1.6, 70, 30, 1.1), 0, 0.8)
    # the beam: a harsh, bright, buzzing tone that sustains then frays
    n = K.ns(1.9)
    t = K.tvec(n)
    f = 110 * (1 + 0.04 * np.sin(2 * np.pi * 7 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    beam = sat(np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.4 * np.sin(3 * ph + 2 * np.sin(ph)), 3.0)
    beam = norm(K.highpass(beam, 150, 2)) * K.perc(n, 0.004, 1.6)
    place(x, beam, 0, 0.35)
    arc = take("arc", 0.30, 1.4, rate=0.8)
    place(x, norm(env(K.highpass(arc, 400, 2), [(0, 0), (0.004, 1), (0.3, 0.6), (1.4, 0)])), 0, 0.35)
    place(x, noise_env(0.5, 2000, 12000, [(0, 0), (0.001, 1), (0.06, 0.2), (0.5, 0)], 91), 0, 0.2)
    y = room(x, t60=2.8, wet=0.22, seed=13)
    return trim(finish(y, -1.0, push=5.0))


def gaze_travel():
    """The beam's front running down its line (12 ticks): a hissing, crackling tear that the game moves along it."""
    L = 0.7
    n = K.ns(L)
    t = K.tvec(n)
    a = take("arc", 4.0, L, rate=0.9)
    hiss = K.noise(n, "white", seed=95)

    def resp(tc, f):
        c = 3500 - 2400 * min(1.0, tc / 0.6)
        return 1.0 / np.sqrt(1 + ((f - c) / (0.6 * c)) ** 2)
    hiss = K.stft_filter(hiss, resp, frame=1024, hop=128)
    x = norm(K.highpass(a, 600, 2)) * 0.6 + norm(hiss) * 0.5
    x = x * K.perc(n, 0.004, 0.65)
    return trim(finish(x, -2.0, push=3.0))


def gaze_impact():
    """Where the beam lands: stone blown apart, a blast of pressure, rubble, and the floor left hissing."""
    x = np.zeros(K.ns(3.8))
    c = take("stone_fall", HITS[1] - 0.003, 0.3, rate=0.72)
    place(x, norm(env(K.highpass(c, 200, 2), [(0, 0), (0.001, 1), (0.05, 0.4), (0.3, 0)])), 0, 0.55)
    bl = take("blast", 0.02, 2.2, rate=0.75)
    place(x, norm(env(K.lowpass(bl, 1500, 2), [(0, 0), (0.003, 1), (0.35, 0.5), (2.2, 0)])), 0, 0.85)
    place(x, sub(2.0, 60, 25, 1.4), 0, 0.85)
    d = take("stone_fall", RUBBLE[2], 1.8, rate=0.85)
    place(x, norm(env(K.highpass(d, 500, 2), [(0, 0), (0.1, 1), (0.8, 0.4), (1.8, 0)])), 0.1, 0.2)
    s = take("arc", 8.0, 2.6, rate=1.0)
    place(x, norm(env(K.highpass(s, 1500, 2), [(0, 0), (0.15, 0.6), (0.6, 0.4), (2.6, 0)])), 0.15, 0.1)
    y = room(x, t60=3.0, wet=0.28, seed=13)
    return trim(finish(y, -1.0, push=6.0))


def rumble():
    """The hall shaking as it wakes: thunder deep in the stone, creaks, dust."""
    L = 4.0
    x = np.zeros(K.ns(L))
    th = take("thunder", 0.9, L, rate=0.6)
    place(x, norm(env(K.lowpass(th, 500, 3), [(0, 0), (0.2, 0.8), (1.0, 1), (4.0, 0)])), 0, 0.9)
    place(x, sub(L, 34, 30, 2.6, attack=0.25, drive=1.4), 0, 0.5)
    for k in range(5):
        c = take("tomb", 2.9 + 1.7 * k, 0.4, rate=0.6)
        place(x, norm(env(K.bandpass(c, 500, 0.7), [(0, 0), (0.05, 1), (0.4, 0)])), 0.3 + 0.6 * k, 0.12)
    place(x, noise_env(L, 1500, 6500, [(0, 0), (0.6, 0.6), (3.0, 0.3), (4.0, 0)], 111), 0, 0.07)
    return trim(finish(room(x, t60=3.0, wet=0.2, seed=13), -2.0, push=3.0))


def pit():
    """A segment of the rune ring dropping open: stone cracking, the slab grinding down, rubble falling into fire,
    and the heat roaring up out of it."""
    L = 4.4
    x = np.zeros(K.ns(L))
    c = take("stone_fall", HITS[3], 0.35, rate=0.7)
    place(x, norm(env(K.highpass(c, 200, 2), [(0, 0), (0.001, 1), (0.08, 0.3), (0.35, 0)])), 0, 0.5)
    g = take("tomb", 7.8, 1.5, rate=0.55)
    place(x, norm(env(K.lowpass(g, 1800, 2), [(0, 0), (0.08, 1), (1.2, 0.7), (1.5, 0)])), 0.05, 0.55)
    place(x, sub(1.8, 48, 28, 1.2), 0.0, 0.6)
    d = take("stone_fall", RUBBLE[0], 2.0, rate=0.75)
    place(x, norm(env(K.highpass(d, 300, 2), [(0, 0), (0.1, 1), (1.4, 0.4), (2.0, 0)])), 0.3, 0.25)
    # the heat: a low roar of fire welling up
    roar = K.lowpass(K.noise(K.ns(3.6), "brown", seed=121), 420, 2)
    roar = norm(roar) * (0.6 + 0.4 * K.smooth_noise(K.ns(3.6), 3.0, 122))
    place(x, env(roar, [(0, 0), (0.5, 0.6), (1.4, 1), (3.6, 0)]), 0.6, 0.45)
    y = room(x, t60=2.8, wet=0.25, seed=13)
    return trim(finish(y, -1.0, push=4.5))


def doors():
    """The doors grind shut (1.4 s) and meet with a slam that shakes the hall; the boom rolls away down the nave."""
    L = 6.0
    MEET = 1.4
    x = np.zeros(K.ns(L))
    g = take("tomb", 7.8, MEET + 0.1, rate=0.6)
    g = env(K.lowpass(g, 2600, 2), [(0, 0), (0.15, 0.5), (MEET - 0.1, 1), (MEET + 0.02, 0.2), (MEET + 0.1, 0)])
    place(x, norm(g), 0, 0.5)
    place(x, sub(2.4, 46, 25, 1.8), MEET, 0.95)
    p = take("punch1", 0, 0.5, rate=0.48)
    place(x, norm(K.lowpass(p, 360, 2)), MEET, 0.85)
    c = take("stone_fall", HITS[0] - 0.003, 0.3, rate=0.62)
    place(x, norm(env(K.highpass(c, 150, 2), [(0, 0), (0.001, 1), (0.05, 0.4), (0.3, 0)])), MEET, 0.5)
    bl = take("blast", 0.02, 2.0, rate=0.7)
    place(x, norm(env(K.lowpass(bl, 600, 2), [(0, 0), (0.004, 1), (0.4, 0.4), (2.0, 0)])), MEET, 0.5)
    d = take("stone_fall", RUBBLE[4], 1.2, rate=0.9)
    place(x, norm(env(K.highpass(d, 800, 2), [(0, 0), (0.1, 1), (1.2, 0)])), MEET + 0.1, 0.12)
    y = room(x, t60=3.6, wet=0.35, seed=17)
    return trim(finish(y, -1.0, push=4.0))


def flames_out():
    """Every flame in the hall dies at once: a great breath drawn out of the room, and a soft low whump."""
    L = 2.2
    x = np.zeros(K.ns(L))
    w = take("wind", 70.0, 1.0, rate=1.0)[::-1]
    place(x, norm(K.bandpass(w, 700, 0.5)) * np.linspace(0, 1, K.ns(1.0)) ** 2, 0, 0.5)
    place(x, sub(1.2, 60, 34, 0.6, attack=0.01, drive=1.5), 0.95, 0.6)
    place(x, noise_env(1.2, 200, 3000, [(0, 0), (0.01, 1), (0.25, 0.3), (1.2, 0)], 131, "brown"), 0.95, 0.4)
    return trim(finish(room(x, t60=3.0, wet=0.3, seed=17), -3.0, push=2.0))


def raise_():
    """The End: the colossus lifts one hand. 22 blocks of marble move: a vast groan of stone, dust sheeting off its
    arm, small falls of rubble, a pressure rising under it all."""
    L = 6.0
    x = np.zeros((K.ns(L), 2))
    for k, (st, rate, at, side) in enumerate(((2.9, 0.28, 0.0, -1), (10.8, 0.3, 0.3, 1), (14.4, 0.26, 0.6, 0))):
        g = take("tomb", st, 4.8, rate=rate)
        g = norm(env(K.lowpass(K.highpass(g, 40, 2), 1200, 2), [(0, 0), (0.6, 0.7), (2.2, 1), (4.0, 0.6), (4.8, 0)]))
        pan = 0.5 + 0.35 * side
        place(x, np.stack([g * math.cos(pan * math.pi / 2), g * math.sin(pan * math.pi / 2)], axis=1), at, 0.5)
    place(x, K.sine(K.glide(K.ns(L), 26, 38), K.ns(L)) * K.swell(K.ns(L), 0.6, 1.5), 0, 0.4)
    for k in range(7):
        d = take("stone_fall", RUBBLE[k % 5], 0.8, rate=0.9 + 0.05 * k)
        d = norm(K.highpass(d, 700, 2)) * K.perc(K.ns(0.8), 0.01, 0.6)
        pan = (k % 3) / 2
        place(x, np.stack([d * (1 - pan), d * pan], axis=1), 0.8 + 0.55 * k, 0.12)
    place(x, np.stack([noise_env(L, 1200, 6000, [(0, 0), (1.0, 0.5), (3.0, 0.8), (6.0, 0)], 141 + c) for c in (0, 1)], axis=1), 0, 0.08)
    y = room(x, t60=3.6, wet=0.3, seed=19, stereo=True)
    return trim(finish(y, -2.0, push=3.0))


def error():
    """[ERROR]: the System itself stutters: the drain's light glitching, digital shards, a dropout."""
    L = 1.2
    n = K.ns(L)
    x = np.zeros((n, 2))
    r = K.rng(151)
    t = 0.0
    while t < L - 0.08:
        d = float(r.uniform(0.02, 0.07))
        f0 = float(r.choice([587.3, 880.0, 1174.7, 1318.5, 293.7]))
        seg = K.ns(d)
        tone = np.sign(np.sin(2 * np.pi * f0 * K.tvec(seg))) * 0.5 + K.noise(seg, "white", int(r.integers(1, 1 << 30))) * 0.3
        tone = np.round(tone * 6) / 6  # crushed
        pan = float(r.uniform(0.15, 0.85))
        place(x, np.stack([tone * (1 - pan), tone * pan], axis=1) * K.perc(seg, 0.001, d * 2)[:, None], t, 0.4)
        t += d + float(r.uniform(0.0, 0.05))
    place(x, sub(0.6, 80, 40, 0.4, drive=2.5), 0.0, 0.4)
    return finish(x, -3.0, push=2.0)


def close():
    """The hand closes on everyone at once. A reversed roar sucks in, then the crush: a boom felt in the chest, stone
    crushed, the System's glass shattering. At 0.46 s the screen is black and the sound is cut with it; only the sub
    rolls on in the dark."""
    PRE = 0.0
    L = 3.2
    x = np.zeros((K.ns(L), 2))
    cut = 0.46
    hit = np.zeros(K.ns(cut + 0.05))
    place(hit, sub(cut + 0.05, 72, 32, 2.0, drive=2.8), 0, 0.9)
    p = take("punch2", 0, 0.5, rate=0.45)
    place(hit, norm(K.lowpass(p, 400, 2)), 0, 0.9)
    bl = take("blast", 0.02, cut, rate=0.65)
    place(hit, norm(K.lowpass(bl, 2500, 2)), 0, 0.7)
    for k in range(3):
        c = take("stone_fall", HITS[k + 2] - 0.003, 0.3, rate=0.55 + 0.1 * k)
        place(hit, norm(K.highpass(c, 200, 2)), 0.004 * k, 0.4)
    gl = take("mine1", 0, 0.5, rate=1.4)  # the bright crunch on top
    place(hit, norm(K.highpass(gl, 1500, 2)), 0, 0.2)
    hit = env(hit, [(0, 1), (cut - 0.03, 1), (cut, 0), (cut + 0.05, 0)])
    place(x, np.stack([hit, hit], axis=1), PRE, 1.0)
    # in the black: the felt roll of it, alone
    s = sub(L - cut, 30, 24, 2.4, attack=0.002, drive=1.2)
    place(x, np.stack([s, s], axis=1), PRE + cut, 0.5)
    return finish(x, -1.0, push=5.0, tail=0.3)


def hall():
    """The hall's air while it waits and judges (a seamless 24 s loop, stereo): deep wind in the vault, a low drone of
    the stone itself on D, far creaks and grit settling. Present, never busy, and kept out of the sub (below 50 Hz belongs
    to the feet and the hits)."""
    L = 26.0
    n = K.ns(L)
    x = np.zeros((n, 2))
    for c in (0, 1):
        w = take("wind", 5.0 + 11.0 * c, L, rate=0.7)
        x[:, c] += norm(K.lowpass(w, 500, 2)) * 0.6
    dr = K.drone(36.71, n, partials=(1, 2, 3, 4), amps=(0.3, 0.6, 0.3, 0.1), beat=0.11, seed=161)
    dr = norm(dr) * (0.75 + 0.25 * K.smooth_noise(n, 0.15, 162))
    x[:, 0] += dr * 0.35
    x[:, 1] += np.roll(dr, K.ns(0.03)) * 0.35
    r = K.rng(163)
    for k in range(9):
        at = float(r.uniform(0.5, L - 3))
        c = take("tomb", float(r.uniform(1, 20)), 0.9, rate=float(r.uniform(0.4, 0.6)))
        c = norm(K.bandpass(c, 500, 0.6)) * K.perc(K.ns(0.9), 0.08, 0.8)
        pan = float(r.uniform(0.1, 0.9))
        place(x, np.stack([c * (1 - pan), c * pan], axis=1), at, 0.06)
    y = room(x, t60=3.4, wet=0.2, seed=19, stereo=True)[:n]
    y = K.loop_seamless(y, 2.0)
    y = K.highpass(y, 50, 4)
    return y / max(np.max(np.abs(y)), 1e-9) * K.dbamp(-10)


# the cue table: name -> (renderer, what it is)
CUES = {
    "footstep1": (lambda: footfall(0), "a statue's foot, take 1"),
    "footstep2": (lambda: footfall(1), "take 2"),
    "footstep3": (lambda: footfall(2), "take 3"),
    "footstep4": (lambda: footfall(3), "take 4 (new)"),
    "windup": (lambda: windup(0), "new: its joints grind as it heaves the glaive up"),
    "swing": (lambda: whoosh(0), "the glaive coming down"),
    "swing2": (lambda: whoosh(1), "the glaive coming down, take 2 (new)"),
    "impact": (lambda: strike(0), "the blade into the floor"),
    "impact2": (lambda: strike(1), "the blade into the floor, take 2 (new)"),
    "statue_rise": (rise, "the twelve stand"),
    "eyes": (eyes, "its eyes open"),
    "gaze_charge": (gaze_charge, "the beam gathers (2.25 s, to the tick)"),
    "gaze_fire": (gaze_fire, "the beam's release at its eyes"),
    "gaze_travel": (gaze_travel, "the beam's front running down the line"),
    "gaze_impact": (gaze_impact, "where the beam lands"),
    "rumble": (rumble, "the hall shakes"),
    "pit": (pit, "the rune ring opens into a pit"),
    "doors": (doors, "the doors grind shut and slam"),
    "flames_out": (flames_out, "every flame dies"),
    "raise": (raise_, "new: the End, its hand rises"),
    "error": (error, "new: [ERROR]"),
    "close": (close, "the hand closes"),
    "hall": (hall, "the hall's air (loop)"),
}


def momentary_max(x):
    """The loudest 400 ms (EBU R128 momentary), LUFS."""
    y = K.k_weight(K.stereo(x) if x.ndim == 1 else x)
    w = K.ns(0.4)
    ms = np.sum(y ** 2, axis=1) if y.ndim == 2 else y ** 2
    if len(ms) < w:
        return -0.691 + 10 * math.log10(max(np.mean(ms), 1e-12))
    c = np.cumsum(np.concatenate([[0], ms]))
    m = (c[w:] - c[:-w]) / w
    return -0.691 + 10 * math.log10(max(np.max(m), 1e-12))


def write_mp3(path, x):
    ch = 1 if x.ndim == 1 else x.shape[1]
    data = np.clip(x, -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(ch), "-i", "pipe:0",
                    "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", "192k", path], input=data, check=True)


def preview(out, only=()):
    os.makedirs(out, exist_ok=True)
    table = {}
    for name, (fn, about) in CUES.items():
        if only and name not in only:
            continue
        y = fn()
        K.write_ogg(os.path.join(out, name + ".ogg"), y, 5)
        write_mp3(os.path.join(out, name + ".mp3"), y)
        row = {"seconds": round(len(y) / SR, 2), "channels": 1 if y.ndim == 1 else 2,
               "true_peak": round(K.true_peak(y), 1), "lufs_i": round(K.loudness(y), 1),
               "lufs_m_max": round(momentary_max(y), 1), "about": about}
        table[name] = row
        print(f"{name:12s} {row['seconds']:5.2f} s {'mo' if row['channels'] == 1 else 'st'}  TP {row['true_peak']:5.1f}  "
              f"I {row['lufs_i']:6.1f}  M-max {row['lufs_m_max']:6.1f}  {about}")
    with open(os.path.join(out, "table.json"), "w") as f:
        json.dump(table, f, indent=1)
    return table


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        preview(sys.argv[2], sys.argv[3:])
    elif len(sys.argv) > 1 and sys.argv[1] == "list":
        print("\n".join(f"{k:12s} {v[1]}" for k, v in CUES.items()))
    else:
        print(__doc__)
