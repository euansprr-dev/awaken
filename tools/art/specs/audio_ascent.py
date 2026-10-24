"""The Ascent's sounds: the rope, the air and the wings. Synthesised from scratch with tools/art/audiokit.py (no
samples), mono (played in the world, or at the listener for the wind), mastered to the sfx loudness of audio_mix.py,
written to src/main/resources/assets/awaken/sounds/ascent_*.ogg and registered in src/main/merge/ascent/assets/awaken/
(sounds.json, subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_ascent.py            render every file and write the JSON
    python3 tools/art/specs/audio_ascent.py --json     only the JSON

The palette is the Signal's light pulled taut (a thread of glass that sings when it takes weight), moving air, and
feathers. Never the body's cloth-and-grit alone, never a vanilla sound.
- rope_fire:    a thrown line of light: a rising whip of air and a bright plucked thread
- rope_bite:    the line biting into wood or stone: a short thump, a crackle of light, a glass ting
- rope_strain:  the line taking your weight: a taut low hum with a creak of friction through it
- rope_release: let go: the line's twang falling away, a flick of air
- launch:       thrown off the rope: a body-sized whoosh rising
- wind:         the rush of air past your ears (a seamless loop; the game raises its pitch and volume with speed)
- wings_open:   the wings opening: a deep push of air, feathers shaking out, a breath of light
- wings_fold:   folding them: a soft rustle closing, a last puff
- flap:         one wing beat: a low whomp of air and a feather rustle
- boom:         the sonic boom's crack: a double crack (the shock's two edges) and a long low rumble
- land:         coming down out of the air: a heavy settle and a skid of grit
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
MERGE = os.path.join(ROOT, "src/main/merge/ascent/assets/awaken")

_IR = {}


def air_space():
    """Open air: almost no room, a faint far reflection so it isn't dry as a booth."""
    if "a" not in _IR:
        _IR["a"] = K.reverb_ir(t60=0.5, predelay=0.012, bright=0.5, width=0.0, early=0.4, size=0.6, seed=91)
    return _IR["a"]


def at(n, t, seg):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def norm(x):
    return x / (np.std(x) + 1e-12)


def thump(f0, f1, dur, t60, body=0.5, body_fc=260, seed=0):
    n = K.ns(dur)
    s = K.sine(K.glide(n, f0, f1), n) * K.perc(n, 0.003, t60)
    b = K.bandnoise(n, body_fc, 1.1, seed) * K.perc(n, 0.002, t60 * 0.45)
    return s + body * b


def sweep(dur, f_from, f_to, q, seed, rise=0.3, shape=1.5, color="pink"):
    """Air moving: band noise whose centre slides, swelling and fading."""
    n = K.ns(dur)
    x = K.noise(n, color, seed)
    y = K.stft_filter(x, lambda t, f: K.bp_resp(f_from * (f_to / f_from) ** min(1.0, max(0.0, t / dur)), q)(f), 1024, 256)
    u = np.arange(n) / max(n - 1, 1)
    env = np.where(u < rise, (u / rise) ** shape, ((1 - u) / (1 - rise)) ** (shape * 0.8))
    return norm(y) * env


def thread(f0, dur, t60, seed, bright=0.7):
    """The rope of light as a struck string of glass: a pluck with glassy upper partials."""
    n = K.ns(dur)
    p = K.pluck(f0, n, bright=bright, t60=t60, pos=0.13, seed=seed, metal=0.35)
    g = K.glass(f0 * 3.01, n, bright=0.5, t60=t60 * 0.5, seed=seed + 1, hit=0.6)
    return norm(p) + 0.35 * norm(g)


def rustle(dur, rate, seed, fc=3800, q=0.9, fade=1.2):
    """Feathers shaking: band noise chopped at a flutter rate, fading."""
    n = K.ns(dur)
    x = K.bandnoise(n, fc, q, seed)
    t = np.arange(n) / K.SR
    flutter = 0.55 + 0.45 * np.sin(2 * np.pi * rate * t + 1.3 * np.sin(2 * np.pi * rate * 0.37 * t))
    u = np.arange(n) / max(n - 1, 1)
    return norm(x) * flutter * (1 - u) ** fade


def finish(x, wet=0.2, lp=9000):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, air_space()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.001, 0.02)


# ---- the sounds ------------------------------------------------------------------------------------------------------

def rope_fire(seed):
    n = K.ns(0.5)
    x = at(n, 0.0, sweep(0.22, 900, 5200, 1.4, seed + 1, 0.55, 1.2, "white")) * 0.6   # the line whips out
    x += at(n, 0.02, thread(880, 0.45, 0.35, seed + 2, 0.85)) * 0.3                    # the thread rings
    x += at(n, 0.0, K.highpass(K.bandnoise(K.ns(0.03), 6000, 0.8, seed + 3) * K.perc(K.ns(0.03), 0.0005, 0.012), 3000, 2)) * 0.5
    return finish(x, 0.18)


def rope_bite(seed):
    n = K.ns(0.6)
    x = at(n, 0.0, thump(110, 60, 0.22, 0.09, 0.7, 380, seed + 1)) * 0.9               # into the wood
    c = K.crackle(K.ns(0.12), 260, seed + 2, 0.75, 0.6)
    x += at(n, 0.003, K.highpass(c, 1500, 2) * K.perc(K.ns(0.12), 0.001, 0.06)) * 0.45  # light crackling at the bite
    x += at(n, 0.01, K.glass(1760, K.ns(0.5), bright=0.65, t60=0.4, seed=seed + 3)) * 0.18  # a ting
    return finish(x, 0.22)


def rope_strain(seed):
    n = K.ns(0.7)
    hum = thread(110, 0.7, 0.9, seed + 1, 0.35)
    # stick-slip friction: band noise pulsed at a creaking rate that slows as it settles
    fr = K.bandnoise(n, 520, 3.0, seed + 2)
    t = np.arange(n) / K.SR
    rate = 34 - 14 * t / t[-1]
    pulses = np.maximum(0, np.sin(2 * np.pi * np.cumsum(rate) / K.SR)) ** 6
    creak = norm(fr) * pulses * K.perc(n, 0.01, 0.5)
    x = hum * 0.5 * K.perc(n, 0.004, 0.6) + creak * 0.45
    x += at(n, 0.0, thump(70, 50, 0.2, 0.1, 0.3, 200, seed + 3)) * 0.6
    return finish(x, 0.15, 7000)


def rope_release(seed):
    n = K.ns(0.55)
    tw = thread(330, 0.55, 0.3, seed + 1, 0.6)
    bend = K.sine(K.glide(n, 330, 180), n) * K.perc(n, 0.002, 0.18)
    x = tw * 0.4 + bend * 0.4
    x += at(n, 0.0, sweep(0.18, 3000, 1200, 1.0, seed + 2, 0.15, 1.2, "white")) * 0.25
    return finish(x)


def launch(seed):
    n = K.ns(0.7)
    x = at(n, 0.0, sweep(0.6, 300, 2400, 0.9, seed + 1, 0.35, 1.3)) * 0.8
    x += at(n, 0.0, thump(75, 45, 0.25, 0.12, 0.5, 220, seed + 2)) * 0.6
    return finish(x, 0.25)


def wind(seed):
    """Four seconds of air rushing past, seamless: a low roar, a moving band of gusts, a thin whistle on top."""
    dur = 4.0
    n = K.ns(dur + 1.0)
    t = np.arange(n) / K.SR
    roar = norm(K.lowpass(K.noise(n, "brown", seed + 1), 260, 2))
    gust_fc = lambda tt, f: K.bp_resp(700 + 500 * np.sin(2 * np.pi * tt / 2.5) + 250 * np.sin(2 * np.pi * tt / 1.1 + 1), 1.2)(f)
    gusts = norm(K.stft_filter(K.noise(n, "pink", seed + 2), gust_fc, 2048, 512))
    whistle = norm(K.bandnoise(n, 2600, 9.0, seed + 3)) * (0.6 + 0.4 * np.sin(2 * np.pi * t / 1.7))
    level = 0.8 + 0.2 * K.smooth_noise(n, 1.5, seed + 4)
    x = (roar * 0.7 + gusts * 0.45 + whistle * 0.08) * level
    return K.loop_seamless(x, 1.0)


def wings_open(seed):
    n = K.ns(1.0)
    push = norm(K.lowpass(K.noise(K.ns(0.6), "brown", seed + 1), 340, 2)) * K.adsr(K.ns(0.6), 0.04, 0.12, 0.4, 0.4)
    x = at(n, 0.0, push) * 0.9                                                           # the whoomph
    x += at(n, 0.05, rustle(0.5, 24, seed + 2, 4200, 0.8, 1.4)) * 0.3                    # feathers shaking out
    x += at(n, 0.0, sweep(0.45, 500, 1400, 0.8, seed + 3, 0.2, 1.2)) * 0.3
    x += at(n, 0.08, K.glass(1320, K.ns(0.9), bright=0.4, t60=0.9, seed=seed + 4)) * 0.08  # a breath of light
    return finish(x, 0.3, 8000)


def wings_fold(seed):
    n = K.ns(0.6)
    x = at(n, 0.0, rustle(0.4, 30, seed + 1, 3600, 0.9, 1.0)) * 0.35
    x += at(n, 0.18, thump(90, 60, 0.18, 0.08, 0.4, 260, seed + 2)) * 0.35
    x += at(n, 0.0, sweep(0.3, 1400, 600, 0.8, seed + 3, 0.6, 1.2)) * 0.25
    return finish(x)


def flap(seed):
    n = K.ns(0.55)
    whomp = norm(K.lowpass(K.noise(K.ns(0.4), "brown", seed + 1), 420, 2)) * K.adsr(K.ns(0.4), 0.02, 0.08, 0.35, 0.25)
    x = at(n, 0.0, whomp) * 0.9
    x += at(n, 0.02, rustle(0.3, 36, seed + 2, 3900, 1.0, 1.6)) * 0.22
    return finish(x, 0.25, 8000)


def boom(seed):
    n = K.ns(2.6)
    crack = lambda s: K.highpass(K.noise(K.ns(0.03), "white", s), 700, 2) * K.perc(K.ns(0.03), 0.0002, 0.012)
    x = at(n, 0.0, crack(seed + 1)) * 1.4
    x += at(n, 0.0, thump(60, 32, 0.5, 0.3, 0.9, 140, seed + 2)) * 1.0
    x += at(n, 0.085, crack(seed + 3)) * 1.1                                             # the shock's back edge
    x += at(n, 0.085, thump(55, 30, 0.4, 0.25, 0.6, 120, seed + 4)) * 0.6
    rumble = norm(K.lowpass(K.noise(K.ns(2.4), "brown", seed + 5), 110, 2)) * K.perc(K.ns(2.4), 0.04, 1.6)
    x += at(n, 0.05, rumble) * 0.55
    x += at(n, 0.0, K.sub_boom(K.ns(2.0), 46, 28, 1.4, seed + 6)) * 0.6
    return finish(x, 0.35, 6000)


def land(seed):
    n = K.ns(0.6)
    x = at(n, 0.0, thump(80, 42, 0.35, 0.18, 0.8, 220, seed + 1)) * 1.0
    gr = K.crackle(K.ns(0.35), 120, seed + 2, 0.4, 0.5) * np.linspace(1, 0, K.ns(0.35)) ** 1.3
    x += at(n, 0.02, gr) * 0.3
    x += at(n, 0.0, sweep(0.3, 1200, 500, 0.9, seed + 3, 0.1, 1.2)) * 0.18
    return finish(x, 0.15, 7000)


SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, volume, attenuation
    ("ascent_rope_fire", rope_fire, -21.0, "ascent.rope_fire", "A line of light is thrown", 1.0, 24),
    ("ascent_rope_bite", rope_bite, -21.0, "ascent.rope_bite", "A line of light bites", 1.0, 24),
    ("ascent_rope_strain", rope_strain, -22.0, "ascent.rope_strain", "A line takes weight", 1.0, 20),
    ("ascent_rope_release", rope_release, -22.0, "ascent.rope_release", "A line lets go", 1.0, 20),
    ("ascent_launch", launch, -20.5, "ascent.launch", "Someone launches off a line", 1.0, 24),
    ("ascent_wind", wind, -21.0, "ascent.wind", "Wind rushes past", 1.0, 16),
    ("ascent_wings_open", wings_open, -20.0, "ascent.wings_open", "Wings of light open", 1.0, 32),
    ("ascent_wings_fold", wings_fold, -22.0, "ascent.wings_fold", "Wings fold", 1.0, 24),
    ("ascent_flap", flap, -21.0, "ascent.flap", "A wingbeat", 1.0, 28),
    ("ascent_boom", boom, -17.0, "ascent.boom", "A sonic boom", 1.0, 96),
    ("ascent_land", land, -20.0, "ascent.land", "Someone lands hard", 1.0, 20),
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
    for name, _, _, event, subtitle, vol, att in SOUNDS_SPEC:
        entry = OrderedDict(name="awaken:" + name)
        if vol != 1.0:
            entry["volume"] = vol
        entry["attenuation_distance"] = att
        if name == "ascent_wind":
            entry["stream"] = False
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=[entry])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/ascent")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
