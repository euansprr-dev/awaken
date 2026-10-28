"""The edge hold's headwind (SPEC §28.9, "The edge hold"): the cue that a fast player is being eased at the edge of the
land their game has, so the slowdown reads as the world catching up, not lag. Synthesised from scratch with
tools/art/audiokit.py (no samples), mono, a seamless loop played at the listener (core.client.EdgeHoldCue raises it with
the hold and lets it go when the land comes), mastered to the sfx loudness of audio_mix.py, written to
src/main/resources/assets/awaken/sounds/edge_headwind.ogg and registered in src/main/merge/core_edge/assets/awaken/
(sounds.json, the subtitle in lang/en_us.json).

    python3 tools/art/specs/audio_edge_hold.py            render the file and write the JSON
    python3 tools/art/specs/audio_edge_hold.py --json     only the JSON

- headwind: air pushing back in your face, not rushing past: a soft wide breath that leans up and down in slow swells,
            a low pressure under it, a thin hiss riding the swells, and at a swell's peak a faint glassy shimmer (the
            Signal's light, the land forming ahead). Never the Ascent's wind (a rush that rises with speed), never vanilla's.
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
MERGE = os.path.join(ROOT, "src/main/merge/core_edge/assets/awaken")


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _sweep(x, centre, q):
    """Band-pass with a moving centre (centre: per-sample Hz)."""
    n = len(x)

    def resp(tsec, f):
        c = centre[min(n - 1, max(0, int(tsec * K.SR)))]
        return K.bp_resp(c, q)(f)
    return K.stft_filter(x, resp)


def headwind(seed):
    L = 12.0
    n = K.ns(L)
    # slow swells, one every three or four seconds: 0..1
    swell = np.clip(0.5 + 0.5 * K.smooth_noise(n, 0.28, seed + 1), 0, 1)
    # the breath in the face: pink noise through a wide band that leans up with each swell (about 420 to 900 Hz)
    cen = 620 * 2 ** (0.55 * (swell - 0.5) * 2)
    breath = _norm(_sweep(K.noise(n, "pink", seed + 2), cen, 0.8))
    # the pressure under it, and a thin hiss that only comes with the swells
    low = _norm(K.lowpass(K.noise(n, "brown", seed + 3), 170, 2))
    hiss = _norm(K.lowpass(K.highpass(K.noise(n, "white", seed + 4), 2800, 2), 7500, 2))
    # a faint shimmer of glass where a swell peaks (E6 and B6, narrow bands of air, so the loop's crossfade can't beat)
    tone = _norm(K.bandpass(K.noise(n, "white", seed + 5), 1318.5, 22.0)
                 + 0.6 * K.bandpass(K.noise(n, "white", seed + 6), 1975.5, 22.0))
    peak = np.clip((swell - 0.62) / 0.38, 0, 1) ** 2
    x = (0.58 * breath + 0.26 * low + 0.12 * hiss * swell ** 1.6) * (0.6 + 0.4 * swell) + 0.05 * tone * peak
    return K.loop_seamless(K.lowpass(x, 8000, 2), 2.0)


# name, render, gated RMS (dBFS), event, subtitle
SOUNDS_SPEC = [
    ("edge_headwind", headwind, -22.0, "edge.headwind", "The wind pushes back"),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    tgt, tol, ceil, q = TARGETS["sfx"]
    if "--json" not in argv:
        os.makedirs(SOUNDS, exist_ok=True)
        for name, fn, rms, *_ in SOUNDS_SPEC:
            assert abs(rms - tgt) <= tol, name
            x = K.mono(fn(sum(map(ord, name))))
            y = K.master(x, rms, ceil - 0.7)
            path = os.path.join(SOUNDS, name + ".ogg")
            K.write_ogg(path, y, q, None)
            print(f"  {name:20s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds, lang = OrderedDict(), OrderedDict()
    for name, _, _, event, subtitle in SOUNDS_SPEC:
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event,
                                    sounds=[OrderedDict(name="awaken:" + name, stream=False)])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/core_edge")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
