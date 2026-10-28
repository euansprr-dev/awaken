"""The power ladder's sounds (SPEC 34.5-34.6), rendered and mastered by audio_powers.py with the family kits.

  evolve          a power climbed a step, calm: a rising sweep into a warm major chord with a bell on top
  evolve_sting    the same in a fight: a short rising chime that stays out of the way
  evolve_awaken   level 5: a deeper swell, a choir and a gold bell over a sub that lands
  tier3_<family>  a power's level-3 layer, played with its release: the family's colour, short and bright
  tier5_<family>  the awakened layer: the family's colour with weight, a halo of air and a bell or a sub

The tier layers sit under a power's own sound (about 6 dB down), so a level-5 cast sounds like the same power, grown.
"""
from collections import OrderedDict

import numpy as np

import audiokit as K
import ritual_sounds as R
import audio_power_kits as V


def _chord(root, dur, seed, ratios=(1.0, 1.26, 1.5, 2.0), attack=0.18):
    n = K.ns(dur)
    t = K.tvec(n)
    x = np.zeros(n)
    for i, q in enumerate(ratios):
        x += V.pad(root * q, dur, seed + i, 0.004, attack) * (1.0 - 0.12 * i)
    return R.unit(x) * np.minimum(1.0, (t[-1] - t) / (dur * 0.45))


def evolve(s):
    sweep = R.unit(R.sweep(K.noise(K.ns(0.7), "pink", s), lambda t: 300 + 3200 * min(1, t / 0.6), 2.2)) * np.linspace(0, 1, K.ns(0.7)) ** 1.6
    return V.mix(2.2, [(sweep, 0, 0.45), (V.rise(220, 440, 0.6, s + 1, 0.4), 0, 0.35), (_chord(220, 1.8, s + 2), 0.55, 0.8),
                       (V.bell(880, 1.6, s + 3, 2.0, 0.55), 0.6, 0.45), (V.shimmer(1760, 1.2, s + 4, 1.4), 0.62, 0.25)], "hall", 0.28, 1.2, 0.6)


def evolve_sting(s):
    return V.mix(0.8, [(V.rise(440, 1320, 0.35, s, 0.5), 0, 0.55), (V.shimmer(1320, 0.6, s + 1, 0.8), 0.3, 0.5),
                       (V.air_burst(0.15, 4200, s + 2), 0.3, 0.2)], "field", 0.14, 1.2, 0.3)


def evolve_awaken(s):
    n = K.ns(1.2)
    swell = R.unit(R.sweep(K.noise(n, "pink", s), lambda t: 150 + 2600 * min(1, t / 1.1), 1.8)) * np.linspace(0, 1, n) ** 2.0
    choir = R.unit(K.choir(196.0, K.ns(2.6), "a", 6, s + 1, 0.4, 10, 9.0)) * np.minimum(1.0, K.tvec(K.ns(2.6)) / 0.3)
    return V.mix(3.2, [(swell, 0, 0.5), (V.thump(0.9, 70, 32, 0.6, 1.6), 1.05, 0.9), (_chord(196, 2.4, s + 2, (1.0, 1.26, 1.5, 2.0, 2.52)), 1.05, 0.7),
                       (choir, 1.0, 0.45), (V.bell(784, 2.2, s + 3, 2.6, 0.6), 1.08, 0.5), (V.shimmer(1568, 1.8, s + 4, 1.8), 1.1, 0.3)],
                 "hall", 0.32, 1.3, 0.8)


# ---- the families' tier layers --------------------------------------------------------------------------------------------
def _t3(fam, s):
    if fam == "kinetic":
        return V.mix(0.6, [(V.metal(s, 0.9, 0.5, "heavy"), 0, 0.5), (V.thump(0.3, 120, 55, 0.15, 1.6), 0, 0.6), (V.rubble(s + 1, 0.3), 0.04, 0.25)], "stone", 0.18)
    if fam == "velocity":
        return V.mix(0.5, [(V.whoosh(0.35, 900, 4200, 0.4, s), 0, 0.6), (V.shimmer(2400, 0.35, s + 1, 0.4), 0.12, 0.35)], "field", 0.1)
    if fam == "flight":
        return V.mix(0.7, [(V.whoosh(0.6, 300, 1800, 0.5, s, 1.0), 0, 0.6), (V.pad(330, 0.6, s + 1), 0.05, 0.3)], "field", 0.16)
    if fam == "energy":
        return V.mix(0.6, [(V.rise(600, 1600, 0.25, s, 0.4), 0, 0.45), (V.shimmer(1600, 0.5, s + 1, 0.7), 0.2, 0.5), (V.zap(0.2, s + 2), 0.2, 0.15)], "field", 0.14)
    if fam == "shadow":
        return V.mix(0.7, [(V.ink(0.6, s, 500), 0, 0.6), (V.fall(400, 120, 0.5, s + 1), 0.05, 0.35)], "void", 0.22)
    if fam == "mind":
        return V.mix(0.7, [(K.fm_bell(660, K.ns(0.7), 1.0, 2.0, 3.0, s) * 0.9, 0, 0.5), (V.pad(330, 0.6, s + 1), 0, 0.3)], "hall", 0.2)
    if fam == "architect":
        return V.mix(0.6, [(R.unit(K.bar(880, K.ns(0.6), 0.8, 0.6, s)), 0, 0.5), (V.click(0.05, s + 1, 2600), 0, 0.4), (V.thump(0.2, 140, 70, 0.1, 1.4), 0, 0.35)], "stone", 0.16)
    if fam == "tech":
        return V.mix(0.5, [(V.servo(0.18, 600, 1400, s), 0, 0.35), (V.click(0.04, s + 1), 0.0, 0.5), (V.click(0.04, s + 2, 4200), 0.08, 0.4),
                           (V.shimmer(2000, 0.3, s + 3, 0.3), 0.1, 0.25)], "field", 0.1)
    return V.mix(0.8, [(V.bell(990, 0.7, s, 1.0, 0.5), 0, 0.45), (V.pad(495, 0.6, s + 1), 0, 0.3), (V.air_burst(0.2, 2400, s + 2), 0, 0.15)], "hall", 0.22)


def _t5(fam, s):
    halo = R.unit(R.sweep(K.noise(K.ns(0.9), "pink", s + 9), lambda t: 900 + 2600 * np.exp(-t / 0.4), 1.4)) * K.perc(K.ns(0.9), 0.02, 0.6)
    if fam == "kinetic":
        core = [(V.thump(0.7, 75, 30, 0.45, 2.2), 0, 1.0), (V.metal(s, 0.7, 0.8, "heavy"), 0.01, 0.45), (V.bell(220, 1.0, s + 1, 1.4, 0.4), 0.02, 0.35),
                (V.rubble(s + 2, 0.6, 70), 0.06, 0.3)]
        room = "stone"
    elif fam == "velocity":
        core = [(V.whoosh(0.5, 600, 5200, 0.35, s, 1.4), 0, 0.7), (V.thump(0.35, 110, 45, 0.18, 1.6), 0.12, 0.5), (V.shimmer(2800, 0.6, s + 1, 0.8), 0.15, 0.35)]
        room = "field"
    elif fam == "flight":
        core = [(V.whoosh(0.9, 200, 2200, 0.45, s, 0.9), 0, 0.6), (_chord(262, 0.9, s + 1, (1.0, 1.5, 2.0)), 0.05, 0.35), (V.thump(0.5, 70, 35, 0.3, 1.4), 0.2, 0.5)]
        room = "field"
    elif fam == "energy":
        core = [(V.rise(300, 1200, 0.3, s, 0.5), 0, 0.4), (V.thump(0.5, 90, 40, 0.3, 1.8), 0.28, 0.7), (V.bell(1175, 1.0, s + 1, 1.4, 0.65), 0.3, 0.45),
                (V.shimmer(2350, 0.9, s + 2, 1.0), 0.32, 0.35)]
        room = "hall"
    elif fam == "shadow":
        core = [(V.ink(0.9, s, 400), 0, 0.6), (V.thump(0.6, 60, 28, 0.4, 1.8), 0.2, 0.8), (V.fall(300, 70, 0.8, s + 1), 0.2, 0.4)]
        room = "void"
    elif fam == "mind":
        core = [(K.fm_bell(440, K.ns(1.1), 1.6, 2.4, 3.5, s) * 0.9, 0, 0.5), (_chord(220, 1.0, s + 1, (1.0, 1.2, 1.5)), 0, 0.4), (V.thump(0.4, 90, 45, 0.25, 1.4), 0, 0.4)]
        room = "hall"
    elif fam == "architect":
        core = [(R.unit(K.bar(440, K.ns(0.9), 1.2, 0.5, s)), 0, 0.5), (V.thump(0.5, 80, 38, 0.3, 1.8), 0, 0.7), (V.bell(880, 1.0, s + 1, 1.3, 0.5), 0.02, 0.35)]
        room = "stone"
    elif fam == "tech":
        core = [(V.servo(0.3, 300, 1800, s), 0, 0.4), (V.thump(0.4, 100, 45, 0.25, 1.6), 0.25, 0.6), (V.zap(0.3, s + 1), 0.25, 0.3),
                (V.shimmer(1800, 0.6, s + 2, 0.6), 0.28, 0.3)]
        room = "field"
    else:
        core = [(V.bell(660, 1.4, s, 2.0, 0.55), 0, 0.5), (R.unit(K.choir(330.0, K.ns(1.2), "o", 5, s + 1, 0.35, 10, 8.0)), 0, 0.35),
                (V.thump(0.5, 80, 40, 0.3, 1.4), 0, 0.45)]
        room = "hall"
    return V.mix(1.3, core + [(halo, 0.05, 0.3)], room, 0.26, 1.6, 0.4)


FAMILIES = ("kinetic", "velocity", "flight", "energy", "shadow", "mind", "architect", "tech", "spirit")

# event: (render, variants, rms dBFS in the power window, attenuation blocks, crest dB)
EVENTS = OrderedDict([
    ("evolve", (evolve, 2, -17.0, 24, 14.0)),
    ("evolve_sting", (evolve_sting, 2, -19.0, 16, 12.0)),
    ("evolve_awaken", (evolve_awaken, 1, -15.0, 32, 14.0)),
])
for _f in FAMILIES:
    EVENTS["tier3_" + _f] = ((lambda f: lambda s: _t3(f, s))(_f), 2, -20.0, 20, 12.0)
    EVENTS["tier5_" + _f] = ((lambda f: lambda s: _t5(f, s))(_f), 2, -18.0, 28, 12.0)

SUBTITLES = {"evolve": "A power grows", "evolve_sting": "A power grows", "evolve_awaken": "A power awakens"}
for _f in FAMILIES:  # every sound has a subtitle (AudioTests): the layer's says the power is stronger, not what it did
    SUBTITLES["tier3_" + _f] = "Power swells"
    SUBTITLES["tier5_" + _f] = "Awakened power roars"
