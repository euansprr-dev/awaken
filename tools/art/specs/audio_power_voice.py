"""Powers in Full Voice (docs/powers-aaa/PLAN.md, SPEC 50.7): a whole sound pack per power, from recordings and air. Euan, 9 Nov:
"the dings, like the high-pitched sounds... the launch moment... sounds super synthetic". So nothing here is a pitched, struck or
sine layer above the sub. Every attack is a recording or noise; brightness comes from air and grit, never from a thing that rings.
The build fails any of these with a held tone in its loud part (ding_check.py). Each pack has a charge or wind, a release, an impact,
a tail and a level 5 layer, two versions of each; audio_powers.py renders and masters them. CC0: Kenney and tools/art/sources/ritual."""
import os

import numpy as np

import audiokit as K
import ritual_sounds as R
import audio_arms as AA
import audio_power_kits as PK
import ding_check as D

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
_FIELD = {}


def field(tag, dur, ratio=1.0, hp=None, lp=None, start=None):
    """A stretch of one of the CC0 field recordings. With no start it opens on the loudest attack."""
    if tag not in _FIELD:
        _FIELD[tag] = D.decode(os.path.join(ROOT, "tools/art/sources/ritual", tag + ".flac"))
    x = _FIELD[tag]
    if start is None:
        w = K.ns(0.01)
        e = np.convolve(np.abs(x), np.ones(w) / w, "same")
        at = int(np.argmax(e))
        on = at - int(np.argmax(e[max(0, at - K.ns(0.25)):at + 1][::-1] < e[at] * 0.1)) if at else 0
        i = max(0, on - K.ns(0.002))
    else:
        i = K.ns(start) % max(1, len(x) - K.ns(dur * ratio * 1.05 + 0.1))
    y = AA.shift(x[i:i + K.ns(dur * ratio * 1.05 + 0.1)], ratio)[:K.ns(dur)]
    if hp:
        y = K.highpass(y, hp, 2)
    if lp:
        y = K.lowpass(y, lp, 2)
    return R.unit(K.declick(y, 0.0004, min(0.05, dur * 0.2)))


def low(name, ratio, fc, dur):
    """A recording taken down in pitch, with the ring above fc rolled off so only its body is left."""
    return R.unit(K.lowpass(AA.trim(AA.shift(AA.rec(name), ratio), dur), fc, 2))


def swell(n, u, seed, f0, f1, q, power):
    """Air being pulled in: a band of noise that climbs. It has no pitch to ring."""
    H = len(u) / K.SR
    return R.unit(R.sweep(K.noise(n, "pink", seed), lambda T: f0 + (f1 - f0) * (T / H) ** 2, q)) * u ** power


def pressure(n, u, seed, fc0, fc1, power):
    H = len(u) / K.SR
    return R.unit(R.sweep_lp(K.noise(n, "pink", seed), lambda T: fc0 + (fc1 - fc0) * (max(T, 0.0) / H) ** 1.5, 2)) * u ** power


def snap(dur, seed, hp, t60):
    n = K.ns(dur)
    return R.unit(K.highpass(K.noise(n, "white", seed), hp, 2)) * K.perc(n, 0.0005, t60)


def varied(s, a=0.94, b=1.06):
    return float(K.rng(s).uniform(a, b))


def looped(x, fade=0.3):
    """The last `fade` seconds laid over the start, equal power: the sound runs on into itself with no join."""
    f = K.ns(fade)
    a = np.sqrt(np.linspace(0, 1, f))
    return np.concatenate([x[:f] * a + x[-f:] * a[::-1], x[f:-f]])


# ---- Haymaker: knuckles and a straining shoulder climbing to a thump, torn air, meat and stone, a long sub drop -----------------------

def hay_charge(s):
    H = 1.05
    n = K.ns(H)
    u = K.tvec(n) / H
    sub = R.unit(K.sine(K.glide(n, 38, 66), n)) * u ** 2.0  # below hearing as a note: you feel it climb
    layers = [(sub, 0, 0.5), (pressure(n, u, s, 70, 420, 1.8), 0, 0.5), (PK.cloth_rustle(s + 2, 0.5), 0.02, 0.4),
              (low("handleSmallLeather2", 0.42, 900, 0.5), 0.1, 0.34), (swell(n, u, s + 1, 240, 1500, 1.8, 2.8), 0, 0.2)]
    for i, at in enumerate((0.16, 0.33, 0.48)):  # the fist closing: leather, not a tick
        layers.append((low("handleSmallLeather", 0.7 + 0.05 * i, 3000, 0.16), at, 0.5))
    return PK.mix(H, layers, "small", 0.06, 1.5, 0.05)


def hay_charge_loop(s):
    D_ = 2.6
    n = K.ns(D_ + 0.3)
    t = K.tvec(n)
    shake = 0.7 + 0.3 * np.sin(2 * np.pi * 7.3 * t + 1.3 * np.sin(2 * np.pi * 0.9 * t))  # the arm trembling under the load
    air = R.unit(K.lowpass(K.noise(n, "pink", s), 360, 2)) * shake
    sub = R.unit(K.sine(60 + 3 * np.sin(2 * np.pi * 0.77 * t), n))
    b = np.zeros(n)
    R.put(b, air, 0, 0.6)
    R.put(b, sub, 0, 0.4)
    r = K.rng(s + 1)
    for at in (0.3, 1.0, 1.55, 2.2):
        R.put(b, low("handleSmallLeather2", r.uniform(0.38, 0.46), 800, 0.45), at + r.uniform(-0.08, 0.08), 0.22)
    return looped(b)


def hay_peak(s):
    return PK.mix(0.4, [(PK.thump(0.24, 88, 52, 0.09, 1.6), 0, 1.0), (low("handleSmallLeather", 0.8 * varied(s), 2600, 0.14), 0, 0.7),
                        (PK.cloth_rustle(s + 1, 0.2), 0, 0.3)], "small", 0.08, 1.5, 0.1)


def hay_release(s):
    return PK.mix(0.4, [(PK.whoosh(0.26, 380, 3000, 0.55, s, q=1.0), 0, 1.0), (PK.air_burst(0.16, 1500, s + 1, q=0.7), 0.04, 0.45)], "field", 0.08, 1.3, 0.1)


def hay_impact(s):
    return PK.mix(0.75, [(PK.punch(s, 0.52), 0, 1.3), (PK.body(s + 1, 0.48, 620, 0.5), 0.002, 0.9), (field("1022", 0.55, 0.62 * varied(s + 2), 80, 3200), 0, 0.85),
                         (snap(0.03, s + 3, 2400, 0.018), 0, 0.4), (R.crack(s + 4, 0.5, 0.3), 0.004, 0.5), (PK.thump(0.3, 110, 50, 0.12, 2.2), 0, 1.0)],
                  "stone", 0.14, 2.0, 0.15)


def hay_tail(s):
    return PK.mix(1.7, [(PK.thump(1.3, 92 * varied(s), 26, 0.62, 2.5), 0, 1.3), (PK.rumble(1.4, 110, 0.7, s + 1), 0.04, 0.5), (PK.rubble(s + 2, 0.6, 50.0), 0.08, 0.22)],
                  "field", 0.14, 1.8, 0.3)


def hay_wall(s):
    return PK.mix(1.0, [(field("1022", 0.8, 0.72 * varied(s), 90, 3600), 0, 1.2), (R.crack(s + 1, 0.8, 0.4), 0.002, 0.8), (PK.punch(s + 2, 0.46), 0, 0.8),
                        (PK.thump(0.5, 96, 34, 0.24, 2.3), 0, 1.0), (PK.rubble(s + 3, 0.7, 70.0), 0.06, 0.4)], "stone", 0.2, 2.0, 0.25)


def hay_l5(s):
    return PK.mix(2.3, [(PK.thump(1.6, 84 * varied(s), 22, 0.8, 2.8), 0, 1.3), (field("1023", 2.0, 0.6, 30, 1500), 0.02, 0.8), (field("1022", 0.9, 0.5, 40, 1800), 0.01, 0.7),
                        (PK.rubble(s + 1, 1.2, 60.0), 0.15, 0.3)], "field", 0.18, 1.9, 0.4)


# ---- Shockwave: the authored stomp stays; a low rumble rolling away, a knock for each foe it meets --------------------------------------

def wave_roll(s):
    H = 1.4
    n = K.ns(H)
    u = K.tvec(n) / H
    env = np.minimum(1, u * 7) * (1 - u) ** 0.5
    thunder = field("3113", H, 0.62 * varied(s), 30, 440, start=1.2 + (s % 5) * 0.5) * env
    grit = R.unit(K.bandpass(K.crackle(n, 38.0, s + 1, 0.7, 0.65), 520, 0.8)) * env
    return PK.mix(H, [(thunder, 0, 1.2), (grit, 0, 0.42), (PK.rumble(H, 90, 0.8, s + 2) * env, 0, 0.6)], "field", 0.1, 1.5, 0.2)


def wave_knock(s):
    name = ("impactSoft_heavy_000", "impactSoft_heavy_002", "impactSoft_heavy_004")[s % 3]
    return PK.mix(0.34, [(low(name, 0.58 * varied(s), 1500, 0.26), 0, 1.2), (PK.thump(0.2, 104, 58, 0.08, 1.8), 0, 0.8), (PK.rubble(s + 1, 0.16, 90.0), 0.01, 0.3)],
                  "field", 0.08, 1.7, 0.08)


def wave_tail(s):
    return PK.mix(1.3, [(field("1022", 0.9, 0.55 * varied(s), 60, 2400), 0, 0.9), (PK.rubble(s + 1, 1.0, 55.0), 0.03, 0.5), (PK.thump(0.5, 76, 34, 0.26, 2.0), 0, 0.8),
                        (PK.rumble(1.1, 100, 0.6, s + 2), 0, 0.45)], "field", 0.14, 1.6, 0.25)


def wave_wall(s):
    return PK.mix(0.85, [(field("1022", 0.6, 0.66 * varied(s), 70, 3000), 0, 1.1), (PK.thump(0.4, 92, 40, 0.18, 2.2), 0, 1.0), (PK.rubble(s + 1, 0.5, 80.0), 0.03, 0.45),
                         (R.crack(s + 2, 0.5, 0.25), 0.002, 0.5)], "stone", 0.18, 1.9, 0.2)


def wave_l5(s):
    n = K.ns(1.6)
    tear = field("0580", 1.6, 0.5 * varied(s), 70, 620, start=0.6 + (s % 4) * 0.4) * K.perc(n, 0.02, 1.1)
    return PK.mix(2.2, [(tear, 0, 0.9), (field("1022", 1.0, 0.48, 40, 1900), 0.05, 0.9), (PK.thump(1.2, 72, 24, 0.62, 2.6), 0, 1.2), (PK.rubble(s + 1, 1.4, 70.0), 0.12, 0.4),
                        (PK.rumble(1.9, 100, 1.0, s + 2), 0, 0.5)], "field", 0.2, 1.8, 0.4)


# ---- Gravity Well: a throb you feel more than hear, noise sucking inward in pulses, debris rattling round, a swallowed thud --------------

def _inhale(dur, seed, fc=800, power=2.8):
    n = K.ns(dur)
    return R.unit(K.lowpass(K.noise(n, "pink", seed), fc, 2)) * (K.tvec(n) / dur) ** power


def well_open(s):
    # the well is there the moment you press, so the intake is only a breath before the thud
    return PK.mix(1.0, [(_inhale(0.14, s, 800, 2.0), 0, 0.6), (PK.thump(0.6, 66 * varied(s + 1), 24, 0.36, 2.4), 0.1, 1.2), (field("1022", 0.5, 0.42, 40, 1300), 0.1, 0.55)],
                  "hall", 0.18, 1.8, 0.3)


def well_loop(s):
    span = 4.0
    n = K.ns(span + 0.4)
    t = K.tvec(n)
    body = R.unit(K.lowpass(K.sine(34 + 3 * np.sin(t * 1.3), n) + 0.5 * K.sine(62.4, n), 170, 2))
    throb = 0.5 + 0.5 * np.sin(2 * np.pi * 2.0 * t - 1.2 + (s % 2) * 0.8) ** 2  # whole cycles in the loop, so the join holds the beat
    inward = R.unit(R.sweep(K.noise(n, "pink", s), lambda T: 2400 * 0.07 ** (((T * 2.0) % 1.0) ** 1.2) + 80, 1.1)) * (0.45 + 0.55 * throb)
    pull = R.unit(K.lowpass(field("0595", span + 0.4, 0.7, start=2.0 + (s % 3))[::-1], 1400, 2))
    debris = R.unit(K.bandpass(K.crackle(n, 44.0, s + 1, 0.7, 0.65), 900, 0.8)) * (0.3 + 0.5 * (0.5 + 0.5 * np.sin(t * 6.3)))
    b = np.zeros(n)
    R.put(b, body * throb, 0, 0.75)
    R.put(b, inward, 0, 0.42)
    R.put(b, pull, 0, 0.32)
    R.put(b, debris, 0, 0.22)
    return looped(b, 0.4)


def well_eat(s):
    x = AA.trim(AA.shift(AA.rec(("impactSoft_medium_001", "impactSoft_medium_003")[s % 2]), 0.5 * varied(s)), 0.22)[::-1]
    return PK.mix(0.36, [(R.unit(K.lowpass(x, 1100, 2)), 0, 1.0), (PK.thump(0.14, 72, 30, 0.06, 1.8), 0.2, 0.9)], "small", 0.06, 1.5, 0.06)


def _whump(dur, seed):
    n = K.ns(dur)
    return R.unit(R.sweep_lp(K.noise(n, "pink", seed), lambda T: 90 + 900 * (T / dur) ** 3, 2)) * (K.tvec(n) / dur) ** 2.2


def well_tail(s):
    w = 0.5
    return PK.mix(1.5, [(_whump(w, s), 0, 0.85), (field("1022", 0.7, 0.45 * varied(s + 1), 40, 1800), w, 1.1), (PK.thump(0.75, 58, 20, 0.5, 2.8), w, 1.3)], "hall", 0.18, 1.7, 0.3)


def well_l5(s):
    w = 0.3
    return PK.mix(2.3, [(_whump(w, s), 0, 0.8), (field("1023", 1.8, 0.62 * varied(s + 1), 30, 1900), w, 1.1), (PK.thump(1.0, 70, 22, 0.55, 2.8), w, 1.3), (field("1022", 0.8, 0.5, 50, 2200), w, 0.8),
                        (PK.rubble(s + 2, 1.0, 70.0), w + 0.1, 0.35)], "hall", 0.22, 1.9, 0.4)


# ---- Railgun: a real electric arc building, heavy relays closing faster, a split second of nothing, a crack and a boom down the line -----

_PLATES = ("impactPlate_medium_000", "impactPlate_medium_003", "impactPlate_medium_004")


def _hum(n, s, hz=88):
    return R.unit(K.lowpass(np.sign(np.sin(2 * np.pi * hz * K.tvec(n))), 320, 2)) * (0.85 + 0.15 * K.rng(s).normal(0, 1, n).clip(-1, 1))


def rail_charge(s):
    H = 1.25
    n = K.ns(H)
    u = K.tvec(n) / H
    arc = K.bandpass(field("0950", H, 0.85, start=3.0 + (s % 3) * 2.0), 1100, 0.5)
    layers = [(R.unit(arc) * u ** 1.6, 0, 0.42), (swell(n, u, s + 1, 130, 2400, 2.2, 2.0), 0, 0.2), (pressure(n, u, s + 2, 60, 300, 1.6), 0, 0.42),
              (_hum(n, s + 3) * (0.25 + 0.75 * u), 0, 0.3)]
    for i, at in enumerate((0.1, 0.32, 0.52, 0.7, 0.86, 0.99, 1.09, 1.17)):  # the coils: heavy relays closing, dull and getting quicker
        layers.append((low(_PLATES[i % 3], (0.52 + 0.03 * i) * varied(s + 10 + i, 0.97, 1.03), 780, 0.12), at, 0.5))
    return PK.mix(H, layers, "small", 0.06, 1.5, 0.05)


def rail_charge_loop(s):
    span = 2.0
    n = K.ns(span + 0.3)
    arc = R.unit(K.bandpass(field("0950", span + 0.3, 0.85, start=8.0 + (s % 3) * 2.0), 1100, 0.5))
    b = np.zeros(n)
    R.put(b, arc, 0, 0.36)
    R.put(b, _hum(n, s + 1), 0, 0.36)
    for at in (0.1, 0.6, 1.1, 1.6):  # relays ticking over: held, not building
        R.put(b, low(_PLATES[int(at * 10) % 3], 0.6, 780, 0.12), at, 0.28)
    return looped(b)


def rail_ready(s):
    return PK.mix(0.34, [(low("impactPlate_heavy_001", 0.5 * varied(s), 900, 0.2), 0, 1.1), (PK.thump(0.16, 90, 56, 0.06, 1.7), 0, 0.7)], "small", 0.08, 1.5, 0.08)


def rail_release(s):
    gap = 0.05  # the charge stops dead a hair before: the gap is the warning
    return PK.mix(0.55, [(snap(0.05, s, 1100, 0.03), gap, 1.25), (PK.air_burst(0.32, 1500, s + 1, q=0.7), gap, 0.8), (low("impactPlate_heavy_001", 0.46 * varied(s + 2), 1600, 0.3), gap, 0.75),
                         (PK.thump(0.45, 130, 34, 0.24, 2.4), gap, 1.25)], "field", 0.1, 1.9, 0.12)


def rail_impact(s):
    # a plate rings above about 700 Hz even taken down an octave: only its thud is used, the bite is stone and noise
    return PK.mix(0.7, [(low("impactPlate_heavy_002", 0.5 * varied(s), 620, 0.4), 0, 0.9), (field("1022", 0.5, 0.62, 90, 950), 0, 1.0), (PK.thump(0.3, 106, 46, 0.14, 2.2), 0, 0.9),
                        (snap(0.03, s + 1, 1800, 0.02), 0, 0.55), (R.crack(s + 2, 0.5, 0.25), 0.002, 0.55), (PK.rubble(s + 3, 0.4, 90.0), 0.02, 0.3)], "stone", 0.14, 1.8, 0.15)


def rail_tail(s):
    v = K.ns(0.5)
    wake = R.unit(K.bandpass(K.noise(v, "pink", s), 700, 0.7)) * K.perc(v, 0.004, 0.35)
    tail = K.echo(wake, time=0.16, feedback=0.42, taps=4, damp=1500.0, pingpong=False)
    tail = K.mono(tail) if getattr(tail, "ndim", 1) > 1 else tail
    return PK.mix(1.8, [(tail, 0, 0.6), (PK.rumble(1.5, 95, 0.9, s + 1), 0, 0.6), (field("1023", 1.5, 0.75 * varied(s + 2), 40, 1400), 0, 0.8)], "field", 0.2, 1.6, 0.3)


def rail_l5(s):
    return PK.mix(1.6, [(snap(0.06, s, 900, 0.04), 0, 1.0), (field("1023", 1.3, 0.85 * varied(s + 1), 40, 2400), 0, 1.1), (PK.thump(0.7, 110, 28, 0.36, 2.6), 0, 1.1), (PK.rubble(s + 2, 0.8, 60.0), 0.05, 0.3)],
                  "stone", 0.2, 1.9, 0.3)


def rail_fill(s):
    n = K.ns(1.1)
    u = K.tvec(n) / 1.1
    # 0580 has a steady whine near 1,580 Hz at normal speed: only what is below it is kept, the grit is rubble
    grind = field("0580", 1.1, 0.6 * varied(s), 90, 700, start=0.5 + (s % 4) * 0.3) * np.sin(np.pi * u) ** 0.6
    return PK.mix(1.3, [(grind, 0, 1.0), (PK.rubble(s + 1, 1.0, 40.0), 0, 0.5), (PK.thump(0.3, 74, 40, 0.14, 1.8), 0.95, 0.6)], "stone", 0.14, 1.5, 0.15)


# event: (render, variants, rms dBFS, attenuation blocks, crest dB)
EVENTS = {
    "hay_charge": (hay_charge, 2, -20.0, 18, 14.0), "hay_charge_loop": (hay_charge_loop, 2, -21.0, 16, 12.0), "hay_peak": (hay_peak, 2, -17.0, 22, 14.0),
    "hay_release": (hay_release, 2, -17.0, 22, 14.0), "hay_impact": (hay_impact, 2, -12.0, 32, 13.0), "hay_tail": (hay_tail, 2, -17.0, 40, 12.0),
    "hay_wall": (hay_wall, 2, -13.0, 32, 13.0), "hay_l5": (hay_l5, 2, -16.0, 44, 12.0),
    "wave_roll": (wave_roll, 2, -17.0, 40, 12.0), "wave_knock": (wave_knock, 2, -16.0, 22, 13.0), "wave_tail": (wave_tail, 2, -17.0, 32, 12.0),
    "wave_wall": (wave_wall, 2, -16.5, 28, 13.0), "wave_l5": (wave_l5, 2, -16.0, 44, 12.0),
    "well_open": (well_open, 2, -16.0, 32, 13.0), "well_loop": (well_loop, 2, -18.0, 32, 11.0),
    "well_eat": (well_eat, 2, -18.0, 18, 13.0), "well_tail": (well_tail, 2, -15.0, 36, 13.0), "well_l5": (well_l5, 2, -14.0, 48, 13.0),
    "rail_charge": (rail_charge, 2, -20.0, 20, 14.0), "rail_charge_loop": (rail_charge_loop, 2, -21.0, 18, 12.0), "rail_ready": (rail_ready, 2, -18.0, 20, 13.0),
    "rail_release": (rail_release, 2, -13.0, 48, 13.0), "rail_impact": (rail_impact, 2, -14.0, 32, 13.0), "rail_tail": (rail_tail, 2, -19.0, 48, 12.0),
    "rail_l5": (rail_l5, 2, -14.0, 48, 13.0), "rail_fill": (rail_fill, 2, -20.0, 24, 12.0),
}

SUBTITLES = {
    "hay_charge": "A fist tightens", "hay_charge_loop": "A shoulder strains", "hay_peak": "Knuckles lock", "hay_release": "A punch tears the air", "hay_impact": "A punch lands",
    "hay_tail": "The blow rolls away", "hay_wall": "Something hits a wall", "hay_l5": "The ground shudders",
    "wave_roll": "Rumble rolls away", "wave_knock": "The wave strikes something", "wave_tail": "The wave collapses", "wave_wall": "The wave breaks on a wall", "wave_l5": "The earth splits",
    "well_open": "A gravity well opens", "well_loop": "A gravity well throbs", "well_eat": "A shot is swallowed", "well_tail": "The well closes", "well_l5": "A well collapses",
    "rail_charge": "Relays charge", "rail_charge_loop": "A coil holds its charge", "rail_ready": "A relay closes", "rail_release": "A slug cracks past", "rail_impact": "A slug strikes", "rail_tail": "A boom runs away",
    "rail_l5": "The tunnel blasts", "rail_fill": "Stone grinds shut",
}


def build(out):  # build_all.py makes textures; audio is rendered on demand (audio_powers.py)
    pass
