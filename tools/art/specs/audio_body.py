"""The Awakened Body's sounds: what a trained body sounds like against the world. Synthesised from scratch with
tools/art/audiokit.py (no samples), mono (they're played in the world), mastered to the sfx loudness of
audio_mix.py, written to src/main/resources/assets/awaken/sounds/body_*.ogg and registered in
src/main/merge/body/assets/awaken/ (sounds.json, subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_body.py            render every file and write the JSON
    python3 tools/art/specs/audio_body.py --json     only the JSON

The palette is cloth, skin, boots and grit, never the System's glass: the body is you, not the Signal.
- roll:    a heavy shoulder meeting the ground, cloth dragged over it with grit under it, the feet coming round
- sprint:  a hard push-off: a boot scuff, a cloth snap, air pulled past you
- slide:   a hip dropping onto the ground and a long friction slide, its pitch falling as it slows, gravel under it
- mantle:  two palms slapping onto stone, cloth scraping over the lip, a boot setting down on top
- impact:  the weight behind a moving hit: a low punch and a short crack, under vanilla's own hit sound
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
MERGE = os.path.join(ROOT, "src/main/merge/body/assets/awaken")

_IR = {}


def room():
    """A small outdoor-ish space: a short early reflection so the sounds sit in the world, not in a box."""
    if "r" not in _IR:
        _IR["r"] = K.reverb_ir(t60=0.35, predelay=0.004, bright=0.45, width=0.0, early=0.8, size=0.3, seed=71)
    return _IR["r"]


def at(n, t, seg):
    """Places seg into a buffer of n samples at t seconds."""
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def thump(f0, f1, dur, t60, body=0.5, body_fc=260, seed=0):
    """A body landing: a falling sine with a short low-mid noise body."""
    n = K.ns(dur)
    s = K.sine(K.glide(n, f0, f1), n) * K.perc(n, 0.003, t60)
    b = K.bandnoise(n, body_fc, 1.1, seed) * K.perc(n, 0.002, t60 * 0.45)
    return s + body * b


def burst(dur, fc, q, t60, seed, attack=0.001):
    n = K.ns(dur)
    return K.bandnoise(n, fc, q, seed) * K.perc(n, attack, t60)


def swish(dur, f_from, f_to, q, seed, rise=0.3, shape=1.5):
    """Cloth or air moving: band noise whose centre slides, swelling and fading."""
    n = K.ns(dur)
    x = K.noise(n, "pink", seed)
    y = K.stft_filter(x, lambda t, f: K.bp_resp(f_from * (f_to / f_from) ** min(1.0, max(0.0, t / dur)), q)(f), 1024, 256)
    u = np.arange(n) / max(n - 1, 1)
    env = np.where(u < rise, (u / rise) ** shape, ((1 - u) / (1 - rise)) ** (shape * 0.8))
    return y / (np.std(y) + 1e-12) * env


def grit(dur, rate, seed, bright=0.35, fade=1.0):
    """Gravel and dust under a moving body: sparse low crackle."""
    n = K.ns(dur)
    c = K.crackle(n, rate, seed, bright, 0.4)
    u = np.arange(n) / max(n - 1, 1)
    return c * (1 - u) ** fade


def finish(x, wet=0.18, lp=7000):
    """Into the small space, a gentle top roll-off, no clicks."""
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, room()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.001, 0.02)


# ---- the sounds ------------------------------------------------------------------------------------------------------

def roll(seed):
    n = K.ns(0.62)
    x = at(n, 0.0, thump(95, 52, 0.3, 0.16, 0.8, 240, seed + 1)) * 1.0
    x += at(n, 0.015, swish(0.4, 1900, 750, 0.9, seed + 2, 0.2)) * 0.2
    x += at(n, 0.03, grit(0.4, 70, seed + 3, 0.3, 1.4)) * 0.22
    x += at(n, 0.31, thump(130, 85, 0.18, 0.09, 0.6, 320, seed + 4)) * 0.45  # the feet coming round
    x += at(n, 0.33, burst(0.06, 2400, 1.4, 0.035, seed + 5)) * 0.12        # a boot scuff
    return finish(x)


def sprint(seed):
    n = K.ns(0.42)
    x = at(n, 0.0, thump(80, 60, 0.12, 0.06, 0.5, 300, seed + 1)) * 0.9       # heel down
    x += at(n, 0.004, burst(0.05, 2600, 1.6, 0.03, seed + 2)) * 0.7            # boot scuff
    x += at(n, 0.012, K.highpass(burst(0.03, 5200, 0.8, 0.012, seed + 3), 3000, 2)) * 0.45  # cloth snap
    x += at(n, 0.02, swish(0.24, 700, 1500, 0.7, seed + 4, 0.12, 1.4)) * 0.1   # air pulled past
    return finish(x, 0.12)


def slide(seed):
    n = K.ns(0.95)
    x = at(n, 0.0, thump(85, 55, 0.2, 0.12, 0.7, 220, seed + 1)) * 0.75       # the hip drops
    fr = swish(0.85, 2600, 700, 1.8, seed + 2, 0.04, 0.9)                     # friction, slowing
    x += at(n, 0.01, fr) * 0.55
    x += at(n, 0.01, grit(0.8, 140, seed + 3, 0.45, 0.8)) * 0.3               # gravel
    rum = K.lowpass(K.noise(K.ns(0.85), "brown", seed + 4), 180, 2)
    rum = rum / (np.std(rum) + 1e-12) * K.line(len(rum), [(0, 0), (0.05, 1), (0.7, 0.5), (1, 0)])
    x += at(n, 0.01, rum) * 0.3                                                # the body's weight on the ground
    return finish(x, 0.15, 6500)


def mantle(seed):
    n = K.ns(0.58)
    slap = lambda s: K.bandpass(K.noise(K.ns(0.05), "white", s), 1300, 0.7) * K.perc(K.ns(0.05), 0.0005, 0.03)
    x = at(n, 0.0, slap(seed + 1)) * 1.3
    x += at(n, 0.0, thump(170, 120, 0.08, 0.04, 0.4, 350, seed + 2)) * 0.45
    x += at(n, 0.045, slap(seed + 3)) * 1.1                                     # the second hand
    x += at(n, 0.1, swish(0.26, 1500, 800, 1.0, seed + 4, 0.35)) * 0.12        # cloth over the lip
    x += at(n, 0.12, grit(0.22, 50, seed + 5, 0.3)) * 0.12
    x += at(n, 0.4, thump(115, 75, 0.14, 0.08, 0.6, 300, seed + 6)) * 0.7      # a boot sets down on top
    x += at(n, 0.405, burst(0.05, 2300, 1.3, 0.03, seed + 7)) * 0.18
    return finish(x)


def impact(seed):
    n = K.ns(0.45)
    x = at(n, 0.0, thump(72, 38, 0.4, 0.24, 0.9, 170, seed + 1)) * 1.0
    x += at(n, 0.0, K.highpass(burst(0.02, 3800, 0.7, 0.006, seed + 2), 1800, 2)) * 0.4  # the crack of contact
    x += at(n, 0.004, burst(0.12, 520, 1.0, 0.05, seed + 3)) * 0.35
    return finish(x, 0.1, 5500)


# ---- the Runner's Kit (SPEC §16.3) -------------------------------------------------------------------------------------

def wallstep(seed):
    """A boot striking a wall sideways mid-run: a dull knock, a scuff dragged along the face, a breath of grit."""
    n = K.ns(0.3)
    x = at(n, 0.0, thump(150, 95, 0.1, 0.05, 0.6, 420, seed + 1)) * 0.8
    x += at(n, 0.006, burst(0.07, 2100, 1.2, 0.04, seed + 2)) * 0.55
    x += at(n, 0.02, grit(0.16, 90, seed + 3, 0.35, 1.5)) * 0.18
    return finish(x, 0.1)


def walljump(seed):
    """Both feet driving off the wall: a heavy shove, cloth snapping, air pulled past you."""
    n = K.ns(0.55)
    x = at(n, 0.0, thump(110, 55, 0.22, 0.11, 0.8, 300, seed + 1)) * 1.0
    x += at(n, 0.01, burst(0.05, 2400, 1.4, 0.03, seed + 2)) * 0.6
    x += at(n, 0.03, K.highpass(burst(0.03, 5200, 0.8, 0.012, seed + 3), 3000, 2)) * 0.4
    x += at(n, 0.05, swish(0.35, 600, 1700, 0.8, seed + 4, 0.2, 1.3)) * 0.3
    return finish(x, 0.12)


def grab(seed):
    """Catching a ledge: two palms slapping stone a beat apart, the body's weight landing in the arms, cloth settling."""
    n = K.ns(0.6)
    slap = lambda sd: K.bandpass(K.noise(K.ns(0.05), "white", sd), 1400, 0.8) * K.perc(K.ns(0.05), 0.0005, 0.025)
    x = at(n, 0.0, slap(seed + 1)) * 1.3
    x += at(n, 0.03, slap(seed + 2)) * 1.1
    x += at(n, 0.05, thump(95, 60, 0.25, 0.12, 0.6, 260, seed + 3)) * 0.75
    x += at(n, 0.08, swish(0.3, 1200, 600, 0.9, seed + 4, 0.25)) * 0.14
    return finish(x)


def shimmy(seed):
    """A hand sliding along a ledge: a short dry rasp and a light grip."""
    n = K.ns(0.3)
    x = at(n, 0.0, swish(0.18, 2600, 1500, 2.0, seed + 1, 0.3, 1.0)) * 0.4
    x += at(n, 0.12, K.bandpass(K.noise(K.ns(0.04), "white", seed + 2), 1500, 0.9) * K.perc(K.ns(0.04), 0.0005, 0.02)) * 0.6
    return finish(x, 0.08)


def climb(seed):
    """Hauling up over a ledge: a strained pull, a knee on stone, a boot setting down."""
    n = K.ns(0.7)
    x = at(n, 0.0, swish(0.35, 900, 1500, 0.9, seed + 1, 0.4)) * 0.3
    x += at(n, 0.2, thump(130, 90, 0.15, 0.07, 0.5, 340, seed + 2)) * 0.6
    x += at(n, 0.22, grit(0.2, 60, seed + 3, 0.3)) * 0.14
    x += at(n, 0.48, thump(115, 75, 0.14, 0.08, 0.6, 300, seed + 4)) * 0.7
    x += at(n, 0.485, burst(0.05, 2300, 1.3, 0.03, seed + 5)) * 0.18
    return finish(x)


def drop(seed):
    """Letting go of a ledge: the hands peeling off, a rush of air."""
    n = K.ns(0.4)
    x = at(n, 0.0, burst(0.04, 1800, 1.1, 0.02, seed + 1)) * 0.5
    x += at(n, 0.02, swish(0.3, 1600, 500, 0.8, seed + 2, 0.2, 1.4)) * 0.35
    return finish(x)


SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, volume, attenuation
    ("body_roll", roll, -20.0, "body.roll", "Body rolls", 1.0, 16),
    ("body_sprint", sprint, -21.5, "body.sprint", "Body pushes off", 1.0, 16),
    ("body_slide", slide, -20.5, "body.slide", "Body slides", 1.0, 16),
    ("body_mantle", mantle, -21.0, "body.mantle", "Body climbs a ledge", 1.0, 16),
    ("body_impact", impact, -19.0, "body.impact", "Heavy hit", 1.0, 20),
    ("body_wallstep", wallstep, -23.0, "body.wallstep", "Footsteps along a wall", 1.0, 16),
    ("body_walljump", walljump, -20.0, "body.walljump", "Body kicks off a wall", 1.0, 16),
    ("body_grab", grab, -20.5, "body.grab", "Body catches a ledge", 1.0, 16),
    ("body_shimmy", shimmy, -24.0, "body.shimmy", "Hands slide along a ledge", 1.0, 12),
    ("body_climb", climb, -21.0, "body.climb", "Body climbs over a ledge", 1.0, 16),
    ("body_drop", drop, -23.0, "body.drop", "Body lets go of a ledge", 1.0, 12),
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
            print(f"  {name:14s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
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
    print(f"events: {len(sounds)} in merge/body")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
