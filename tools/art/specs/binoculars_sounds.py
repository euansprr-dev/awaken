"""The binoculars' sounds (SPEC §39.5): our own, synthesised with the audio kit (tools/art/audiokit.py). Quiet and near
the ear: only your own client plays them, as UI sounds.

    python3 tools/art/specs/binoculars_sounds.py build     render the .ogg files and write the JSON

    raise    fabric and strap: a rustle that swells as the hands lift them, a small strap creak, a leather pat at the top
    eyecup   the eyecups meet the eyes: a soft rubber thump and a breath of glass
    step     one detent of the focus wheel: a dry click with a tiny metal ring
    lower    taken from the eyes and let down: a rustle falling away and a soft cloth landing

Writes src/main/resources/assets/awaken/sounds/ui_binoculars_<name>.ogg (mono, 44.1 kHz Vorbis), the events
binoculars.<name> in src/main/merge/binoculars/assets/awaken/sounds.json and their subtitles in the binoculars lang
file (other keys kept). Mastered to the ui window of audio_mix.py. Deterministic: every random source is seeded.
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, ART)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge/binoculars/assets/awaken")
SR = K.SR


def env(n, pts):
    xs, ys = zip(*pts)
    return np.interp(np.arange(n) / SR, xs, ys)


def put(dst, src, t, g=1.0):
    i = K.ns(t)
    m = min(len(src), len(dst) - i)
    if m > 0:
        dst[i:i + m] += src[:m] * g


def pk(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def rustle(n, seed, pts, lo=900.0, hi=5200.0):
    """Cloth: band-limited noise broken into small grains (fibres catching), shaped by pts."""
    x = K.noise(n, "pink", seed)
    x = K.highpass(K.lowpass(x, hi, 2), lo, 2)
    grains = K.smooth_noise(n, rate=55.0, seed=seed + 1)
    grains = np.clip(grains * 1.6 + 0.4, 0.0, None) ** 1.5
    return pk(x * grains * env(n, pts))


def raise_():
    n = K.ns(0.42)
    out = rustle(n, 11, [(0, 0), (0.05, 0.5), (0.22, 1.0), (0.34, 0.5), (0.42, 0)]) * 0.8
    # the strap: a small creak (a few narrow resonances, rubbed)
    creak = K.bandnoise(K.ns(0.09), 620.0, 9.0, seed=12) + 0.6 * K.bandnoise(K.ns(0.09), 1310.0, 12.0, seed=13)
    creak *= K.perc(len(creak), 0.01, 0.07)
    put(out, pk(creak), 0.12, 0.35)
    # a leather pat as they come to the face
    pat = K.lowpass(K.noise(K.ns(0.05), "white", 14), 1400.0, 2) * K.perc(K.ns(0.05), 0.0015, 0.035)
    put(out, pk(pat), 0.33, 0.55)
    return out


def eyecup():
    n = K.ns(0.24)
    out = np.zeros(n)
    thump = K.sine(110.0, K.ns(0.12)) * K.perc(K.ns(0.12), 0.003, 0.06)
    put(out, pk(thump), 0.0, 0.7)
    puff = K.lowpass(K.noise(K.ns(0.08), "pink", 21), 800.0, 2) * K.perc(K.ns(0.08), 0.004, 0.05)
    put(out, pk(puff), 0.0, 0.6)
    # a breath of glass: a soft, high, short ring well under the thump
    g = K.glass(2900.0, K.ns(0.2), bright=0.4, t60=0.25, seed=22, hit=0.4)
    put(out, pk(g), 0.012, 0.12)
    return out


def step():
    n = K.ns(0.09)
    out = np.zeros(n)
    click = K.bandpass(K.noise(K.ns(0.012), "white", 31), 3200.0, 1.4) * K.perc(K.ns(0.012), 0.0003, 0.006)
    put(out, pk(click), 0.0, 1.0)
    ring = K.modal(2250.0, [1.0, 2.71, 4.1], [1.0, 0.4, 0.2], [0.05, 0.03, 0.02], K.ns(0.08), attack=0.0005, seed=32)
    put(out, pk(ring), 0.001, 0.25)
    return out


def lower():
    n = K.ns(0.32)
    out = rustle(n, 41, [(0, 0), (0.02, 1.0), (0.12, 0.7), (0.24, 0.25), (0.32, 0)], lo=700.0, hi=4200.0) * 0.75
    land = K.lowpass(K.noise(K.ns(0.07), "pink", 42), 900.0, 2) * K.perc(K.ns(0.07), 0.002, 0.05)
    put(out, pk(land), 0.2, 0.5)
    return out


SOUNDS = OrderedDict([
    ("raise", (raise_, "Binoculars raised")),
    ("eyecup", (eyecup, "Binoculars at the eyes")),
    ("step", (step, "Binoculars focus clicks")),
    ("lower", (lower, "Binoculars lowered")),
])


def build_audio():
    rms, _tol, ceiling, quality = TARGETS["ui"]
    events = OrderedDict()
    subs = OrderedDict()
    for name, (fn, sub) in SOUNDS.items():
        x = K.declick(fn())
        y = K.master(x, rms, ceiling_db=ceiling)
        path = os.path.join(SND, "ui_binoculars_%s.ogg" % name)
        K.write_ogg(path, y, quality=quality)
        print("%-8s %.2f s  rms %.1f  peak %.1f" % (name, len(y) / SR, K.gated_rms(y), K.true_peak(y)))
        events["binoculars." + name] = {"subtitle": "subtitles.awaken.binoculars." + name, "sounds": [{"name": "awaken:ui_binoculars_" + name}]}
        subs["subtitles.awaken.binoculars." + name] = sub
    os.makedirs(MERGE, exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(events, f, indent=2)
        f.write("\n")
    lang = os.path.join(MERGE, "lang/en_us.json")
    os.makedirs(os.path.dirname(lang), exist_ok=True)
    have = OrderedDict()
    if os.path.exists(lang):
        with open(lang) as f:
            have = json.load(f, object_pairs_hook=OrderedDict)
    have.update(subs)
    with open(lang, "w") as f:
        json.dump(have, f, indent=2, ensure_ascii=False)
        f.write("\n")


def build(out):  # textures only in build_all; audio is rendered on demand
    pass


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        build_audio()
    else:
        print(__doc__)
