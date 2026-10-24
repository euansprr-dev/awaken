"""The Precursor ward's sounds (SPEC §17 "The Wilds"). Synthesised from scratch with tools/art/audiokit.py (no
samples), mono (they're played in the world), mastered to the sfx loudness of audio_mix.py, written to
src/main/resources/assets/awaken/sounds/wilds_*.ogg and registered in src/main/merge/wilds/assets/awaken/ (sounds.json,
subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_wilds.py            render every file and write the JSON
    python3 tools/art/specs/audio_wilds.py --json     only the JSON

The palette is the Signal's glass against the world's grit: the ward is old light refusing foreign matter.
- mark:    the ward takes note of a placed block: one small glass tick and a breath of rising shimmer
- crumble: the block comes apart: a short give, a pour of grains thinning out, three glass tones falling under it
- hold:    a site's wall refuses the pick: a dull, deep strike in a heavy body, a low hum swelling and gone
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge/wilds/assets/awaken")

_IR = {}


def hall():
    """Old stone around the site: a little longer and brighter than the body's outdoor space."""
    if "r" not in _IR:
        _IR["r"] = K.reverb_ir(t60=0.8, predelay=0.008, bright=0.55, width=0.0, early=0.6, size=0.5, seed=83)
    return _IR["r"]


def at(n, t, seg):
    """Places seg into a buffer of n samples at t seconds."""
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def finish(x, wet=0.22, lp=9000):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, hall()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.001, 0.03)


def shimmer(dur, f0, f1, seed):
    """Two thin partials sliding upward, swelling then gone: light lifting off."""
    n = K.ns(dur)
    env = K.line(n, [(0, 0), (0.25, 1), (1, 0)])
    a = K.sine(K.glide(n, f0, f1), n)
    b = K.sine(K.glide(n, f0 * 1.5, f1 * 1.5), n) * 0.5
    return (a + b) * env


# ---- the sounds ------------------------------------------------------------------------------------------------------

def mark(seed):
    n = K.ns(0.55)
    x = at(n, 0.0, K.glass(2350, K.ns(0.45), 0.55, 0.35, seed + 1, 0.8)) * 0.8
    x += at(n, 0.03, shimmer(0.42, 1400, 2600, seed + 2)) * 0.12
    x += at(n, 0.0, K.highpass(K.bandnoise(K.ns(0.03), 6000, 1.2, seed + 3) * K.perc(K.ns(0.03), 0.0005, 0.012), 3000, 2)) * 0.2
    return finish(x, 0.25)


def crumble(seed):
    n = K.ns(1.15)
    # the give: a short low thump with a little crack in it
    give = K.sine(K.glide(K.ns(0.18), 120, 62), K.ns(0.18)) * K.perc(K.ns(0.18), 0.002, 0.09)
    x = at(n, 0.0, give) * 0.7
    x += at(n, 0.0, K.highpass(K.bandnoise(K.ns(0.04), 3200, 0.9, seed + 1) * K.perc(K.ns(0.04), 0.0005, 0.02), 1500, 2)) * 0.35
    # the pour: dense grains thinning out, their colour dropping as the heap settles
    pour = K.crackle(K.ns(0.95), 420, seed + 2, 0.55, 0.55)
    pour = K.stft_filter(pour, lambda t, f: K.bp_resp(4200 * (0.45 ** min(1.0, t / 0.95)), 0.8)(f), 1024, 256)
    pour = pour / (np.std(pour) + 1e-12) * K.line(len(pour), [(0, 0), (0.04, 1), (0.5, 0.55), (1, 0)])
    x += at(n, 0.02, pour) * 0.32
    # the unmaking: three glass tones falling, quiet under the grains
    for i, f in enumerate((1975.5, 1568.0, 1318.5)):
        x += at(n, 0.05 + i * 0.07, K.glass(f, K.ns(0.7), 0.45, 0.5, seed + 3 + i, 0.6)) * (0.22 - i * 0.04)
    return finish(x)


def hold(seed):
    n = K.ns(0.8)
    # a strike into something far heavier than the pick: inharmonic, low, damped fast
    body = K.modal(92, (1.0, 2.32, 3.81, 5.4), (1.0, 0.5, 0.28, 0.14), (0.28, 0.16, 0.1, 0.07), K.ns(0.6), 0.001, seed + 1)
    x = at(n, 0.0, body) * 0.9
    x += at(n, 0.0, K.bandpass(K.noise(K.ns(0.05), "white", seed + 2), 900, 0.8) * K.perc(K.ns(0.05), 0.0005, 0.025)) * 0.45
    # the ward's hum answering: two close low tones beating, swelling and gone
    hn = K.ns(0.7)
    hum = (K.sine(K.glide(hn, 58, 55), hn) + 0.7 * K.sine(K.glide(hn, 61, 57), hn)) * K.line(hn, [(0, 0), (0.3, 1), (1, 0)])
    x += at(n, 0.02, hum) * 0.4
    x += at(n, 0.05, shimmer(0.5, 700, 520, seed + 3)) * 0.05
    return finish(x, 0.2, 6000)


SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, volume, attenuation
    ("wilds_ward_mark", mark, -23.0, "wilds.ward_mark", "Ward notices a block", 1.0, 12),
    ("wilds_ward_crumble", crumble, -20.5, "wilds.ward_crumble", "Placed block crumbles", 1.0, 16),
    ("wilds_ward_hold", hold, -19.5, "wilds.ward_hold", "Ward holds", 1.0, 16),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    tgt, tol, ceil, q = TARGETS["sfx"]
    if "--json" not in argv:
        for name, fn, rms, *_ in SOUNDS_SPEC:
            assert abs(rms - tgt) <= tol, name
            x = K.mono(fn(sum(map(ord, name))))
            y = K.master(x, rms, ceil - 0.7)
            path = os.path.join(SOUNDS, name + ".ogg")
            K.write_ogg(path, y, q, None)
            print(f"  {name:20s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds, lang = OrderedDict(), OrderedDict()
    for name, _, _, event, subtitle, vol, att in SOUNDS_SPEC:
        entry = OrderedDict(name="awaken:" + name)
        if vol != 1.0:
            entry["volume"] = vol
        entry["attenuation_distance"] = att
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=[entry])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/wilds")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
