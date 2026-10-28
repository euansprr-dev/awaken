"""The lock-on's sounds (SPEC §40.4): in the System's own glass voice (audio_ui.glass_note in its small bright room, notes
from D major pentatonic, D E F# A B), so a lock under a tell, a toast or the band never clashes. Short and close: only
your own client plays them, as UI sounds, and they must never cover a creature's tell.

    python3 tools/art/specs/aim_sounds.py build     render the .ogg files and write the JSON

    lock     the ring closes on the creature: a breath of air drawn in, then D6 and A6 struck together, a low warm tick
             under them (the ring meeting the body)
    switch   the lock jumps to the next creature: one tiny glass tick (F#6), a whisper of air across
    release  let go: A5 falling to D5, soft, the air let out
    break    the locked creature falls: the ring splinters, a bright B6 glint over a short fizz, D6 under it

Writes src/main/resources/assets/awaken/sounds/ui_aim_<name>.ogg (stereo, 44.1 kHz Vorbis), the events aim.<name> in
src/main/merge/aim/assets/awaken/sounds.json and their subtitles in the aim lang file (other keys kept). Mastered to the ui
window of audio_mix.py. Deterministic: every random source is seeded.
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
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402
import audio_ui as U  # noqa: E402

SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge/aim/assets/awaken")


def air(length, at, dur, f0, f1, level, seed, pan=0.0, q=2.4):
    """Air through a band gliding f0 -> f1: light moving (as the band's own projection sounds)."""
    n = K.ns(length)
    s = K.ns(at)
    k = min(K.ns(dur), n - s)
    tail = K.ns(0.05)
    seg = K.stft_filter(K.noise(k + tail, "white", seed),
                        lambda t, f: K.bp_resp(f0 * (f1 / f0) ** min(1.0, max(0.0, t / dur)), q)(f), frame=1024, hop=256)
    seg = K.highpass(seg, 900, 2)
    seg = seg / (np.std(seg) + 1e-12)
    env = np.concatenate([K._rise(k) ** 1.6, np.exp(-np.arange(tail) / K.ns(0.015))])
    x = np.zeros(n)
    m = min(len(seg), n - s)
    x[s:s + m] = (seg * env)[:m]
    return K.pan(x * level, pan)


def lock():
    L = 0.7
    y = U.notes([(0.085, "D6", 0.46, 0.55, 0.7), (0.088, "A6", 0.34, 0.45, 0.8), (0.150, "D7", 0.08, 0.25, 0.85)], L, wet=0.16, spread=0.2)
    pull = air(L, 0.0, 0.09, 7200, 2600, 0.05, seed=21)  # drawn in as the ring closes
    return y + pull + U.warm("D4", L, 0.085, 0.12, t60=0.12, attack=0.002)


def switch():
    L = 0.3
    y = U.notes([(0.012, "F#6", 0.34, 0.14, 0.75)], L, wet=0.10, spread=0.0)
    return y + air(L, 0.0, 0.05, 3000, 6400, 0.018, seed=23, pan=0.2)


def release():
    L = 0.55
    y = U.notes([(0.0, "A5", 0.24, 0.22, 0.5), (0.055, "D5", 0.22, 0.30, 0.45)], L, wet=0.14, spread=0.15, lp=6000)
    return y + air(L, 0.0, 0.12, 2400, 6000, 0.015, seed=25)


def break_():
    L = 0.8
    y = U.notes([(0.0, "B6", 0.30, 0.30, 0.85), (0.008, "D6", 0.30, 0.55, 0.6), (0.020, "F#6", 0.16, 0.35, 0.7)], L, wet=0.2, spread=0.35)
    n = K.ns(L)
    fizz = np.zeros(n)
    k = K.ns(0.16)
    fizz[:k] = K.crackle(k, rate=320, seed=27, bright=0.85) * K.perc(k, 0.001, 0.12) * 0.05
    # the splinters spread: the fizz once to the left, again a moment later to the right
    return y + K.pan(fizz, -0.2) + K.pan(np.roll(fizz, K.ns(0.02)), 0.25) * 0.6


SOUNDS = OrderedDict([
    ("lock", (lock, "Lock taken")),
    ("switch", (switch, "Lock moves")),
    ("release", (release, "Lock released")),
    ("break", (break_, "Locked creature falls")),
])


def build_audio():
    rms, _tol, ceiling, quality = TARGETS["ui"]
    events = OrderedDict()
    subs = OrderedDict()
    for name, (fn, sub) in SOUNDS.items():
        x = K.declick(fn())
        y = K.master(x, rms, ceiling_db=ceiling)
        path = os.path.join(SND, "ui_aim_%s.ogg" % name)
        K.write_ogg(path, y, quality=quality)
        print("%-8s %.2f s  rms %.1f  peak %.1f" % (name, len(y) / K.SR, K.gated_rms(y), K.true_peak(y)))
        events["aim." + name] = {"subtitle": "subtitles.awaken.aim." + name, "sounds": [{"name": "awaken:ui_aim_" + name}]}
        subs["subtitles.awaken.aim." + name] = sub
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


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        build_audio()
    else:
        print(__doc__)
