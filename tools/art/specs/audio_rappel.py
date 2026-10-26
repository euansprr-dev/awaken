"""The rappel's and the rope fittings' sounds (SPEC §28.5). Synthesised from scratch with tools/art/audiokit.py (no
samples), mono, mastered to the sfx loudness of audio_mix.py, written to src/main/resources/assets/awaken/sounds/
ascent_rappel_*.ogg and registered in src/main/merge/ascent_rappel/assets/awaken/ (sounds.json, subtitles in
lang/en_us.json, which also keeps the fittings' own words, written by hand).

    python3 tools/art/specs/audio_rappel.py            render every file and write the JSON
    python3 tools/art/specs/audio_rappel.py --json     only the JSON
    python3 tools/art/specs/audio_rappel.py --only ascent_rappel_kick   one file (and the JSON)

The palette is the Ascent's (audio_ascent.py: the Signal's light pulled taut, a thread of glass that sings) brought
against rock: the line running through a gloved hand, boots on stone, grit, and the metal of the fittings. Never a
vanilla sound.
- rappel_pay:        the line paying out through the hand (a seamless loop): a dry rasp of friction with a grain to it,
                     the line's thin singing over it, a low thrum of the rope under load
- rappel_pay_glass:  the Deepglass line paying out (a loop): the same run turned smooth: an airy hiss, the glass's
                     low hum and a slow shimmer of crystal over it
- rappel_catch:      a bite's catch (every half block without the Deepglass line): the glove squeezing the line, a
                     short pluck of its thread taking the weight, a small click
- rappel_kick:       the kick off the wall: boots hitting stone (a dull thud), a crunch and scrape of grit, a breath of
                     air as the body swings out
- rappel_plant:      the feet back on the wall after a bound: a softer thud, a short scrape, grit pattering down
- rappel_reel:       the gold reel winding: a pawl's quick clicks over a spinning bright metal hum
- rappel_fit:        a fitting on the Tether: two clasps snapping shut, the line drawn taut (a rising glassy sing) and
                     its hum settling
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402
import audio_ascent as A  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge/ascent_rappel/assets/awaken")

at, norm, thump, sweep, thread = A.at, A.norm, A.thump, A.sweep, A.thread
_IR = {}


def rock_space():
    """A shaft of stone: quick close reflections, a darker, short tail."""
    if "r" not in _IR:
        _IR["r"] = K.reverb_ir(t60=0.9, predelay=0.008, bright=0.3, width=0.0, early=0.75, size=0.45, seed=207)
    return _IR["r"]


def finish(x, wet=0.22, lp=8500):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, rock_space()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.001, 0.02)


def grit(dur, rate, seed, fc=2600):
    """Grit and small stones: crackle, high-passed, falling away."""
    n = K.ns(dur)
    c = K.crackle(n, rate, seed, 0.55, 0.65)
    return K.highpass(K.bandpass(c, fc, 0.7), 500, 2) * np.linspace(1, 0, n) ** 1.4


def scrape(dur, seed, fc0=1800, fc1=900):
    """A boot sole dragged on stone: band noise sliding down, roughened by a fast stick-slip."""
    n = K.ns(dur)
    x = sweep(dur, fc0, fc1, 1.6, seed, 0.12, 1.2, "white")
    t = np.arange(n) / K.SR
    rough = 0.55 + 0.45 * np.maximum(0, np.sin(2 * np.pi * 70 * t + 3 * np.sin(2 * np.pi * 11 * t))) ** 3
    return x * rough


def pay(seed):
    """Two seconds of line running out through a hand, seamless."""
    dur = 2.0
    n = K.ns(dur + 1.0)
    t = np.arange(n) / K.SR
    # the rasp: friction noise in two bands, grained by a fast irregular stick-slip
    rasp = norm(K.bandnoise(n, 2300, 1.1, seed + 1)) * 0.6 + norm(K.bandnoise(n, 900, 1.4, seed + 2)) * 0.45
    grain = 0.6 + 0.4 * np.abs(K.smooth_noise(n, 90, seed + 3))
    # the thread of light singing thinly on it, wavering
    sing = K.sine(1760 * (1 + 0.004 * K.smooth_noise(n, 3, seed + 4)), n) * 0.06 + K.sine(2640, n) * 0.025
    # the rope under load: a low thrum
    thrum = norm(K.lowpass(K.noise(n, "brown", seed + 5), 150, 2)) * 0.35
    x = rasp * grain + sing + thrum
    x *= 0.85 + 0.15 * K.smooth_noise(n, 2.2, seed + 6)
    return K.loop_seamless(K.lowpass(x, 8000, 2), 1.0)


def pay_glass(seed):
    """The Deepglass line's run, seamless: smooth air, a low glass hum, a slow shimmer of crystal."""
    dur = 3.0
    n = K.ns(dur + 1.0)
    t = np.arange(n) / K.SR
    hiss = norm(K.stft_filter(K.noise(n, "pink", seed + 1),
                              lambda tt, f: K.bp_resp(1900 + 400 * np.sin(2 * np.pi * tt / 1.5), 0.9)(f), 2048, 512)) * 0.55
    hum = (K.sine(146.8, n) + 0.5 * K.sine(220.0, n) + 0.25 * K.sine(293.7 * (1 + 0.002 * np.sin(2 * np.pi * 0.7 * t)), n)) * 0.22
    shimmer = np.zeros(n)
    rng = K.rng(seed + 2)
    for i in range(14):
        t0 = rng.uniform(0, dur + 0.6)
        f = rng.choice([1174.7, 1568.0, 1760.0, 2349.3, 2637.0])
        g = K.glass(f, K.ns(0.9), bright=0.5, t60=0.7, seed=seed + 10 + i, hit=0.15)
        shimmer += at(n, t0, g) * rng.uniform(0.04, 0.08)
    x = hiss + hum + shimmer * 1.4
    x *= 0.9 + 0.1 * K.smooth_noise(n, 1.4, seed + 3)
    return K.loop_seamless(K.lowpass(x, 9000, 2), 1.0)


def catch(seed):
    n = K.ns(0.3)
    squeeze = norm(K.bandnoise(K.ns(0.06), 1300, 2.2, seed + 1)) * K.perc(K.ns(0.06), 0.002, 0.03)
    x = at(n, 0.0, squeeze) * 0.5                                                          # the glove grips
    x += at(n, 0.008, thread(196, 0.28, 0.16, seed + 2, 0.5)) * 0.35                       # the thread takes the weight
    x += at(n, 0.0, K.highpass(K.noise(K.ns(0.008), "white", seed + 3), 2500, 2) * K.perc(K.ns(0.008), 0.0003, 0.003)) * 0.4
    x += at(n, 0.004, thump(140, 90, 0.08, 0.04, 0.3, 500, seed + 4)) * 0.3
    return finish(x, 0.15)


def kick(seed):
    n = K.ns(0.75)
    x = at(n, 0.0, thump(95, 52, 0.25, 0.08, 0.9, 420, seed + 1)) * 1.0                   # boots on stone
    x += at(n, 0.006, grit(0.4, 160, seed + 2)) * 0.45                                     # a crunch of grit
    x += at(n, 0.02, scrape(0.18, seed + 3, 2200, 1100)) * 0.3                             # the soles drag off
    x += at(n, 0.05, sweep(0.5, 260, 1100, 0.9, seed + 4, 0.35, 1.3)) * 0.35               # the body swings out
    return finish(x, 0.28)


def plant(seed):
    n = K.ns(0.6)
    x = at(n, 0.0, thump(110, 62, 0.18, 0.06, 0.8, 480, seed + 1)) * 0.75                 # the feet meet the rock
    x += at(n, 0.01, scrape(0.14, seed + 2, 1600, 900)) * 0.28
    x += at(n, 0.06, grit(0.45, 70, seed + 3, 2200)) * 0.35                                # grit pattering down
    return finish(x, 0.3)


def reel(seed):
    n = K.ns(0.36)
    t = np.arange(n) / K.SR
    # the pawl: quick clicks, a little irregular
    clicks = np.zeros(n)
    rng = K.rng(seed + 1)
    tk = 0.0
    while tk < 0.32:
        c = K.highpass(K.noise(K.ns(0.006), "white", seed + 2 + int(tk * 1000)), 3000, 2) * K.perc(K.ns(0.006), 0.0002, 0.002)
        c += K.sine(4200, K.ns(0.006)) * K.perc(K.ns(0.006), 0.0002, 0.003) * 0.6
        clicks += at(n, tk, c) * rng.uniform(0.7, 1.0)
        tk += 1 / 34 * rng.uniform(0.85, 1.15)
    # the spool spinning: a bright metal hum with gold's warm ring
    hum = (K.sine(392 * (1 + 0.01 * t), n) + 0.4 * K.sine(784, n) + 0.2 * K.sine(1176, n)) * K.adsr(n, 0.02, 0.05, 0.8, 0.12) * 0.2
    x = clicks * 0.6 + hum + norm(K.bandnoise(n, 3200, 1.2, seed + 3)) * 0.05
    return finish(x, 0.12)


def fit(seed):
    n = K.ns(1.2)
    clasp = lambda s: K.highpass(K.noise(K.ns(0.012), "white", s), 1800, 2) * K.perc(K.ns(0.012), 0.0003, 0.005)
    x = at(n, 0.0, clasp(seed + 1)) * 0.7 + at(n, 0.0, K.bar(1480, K.ns(0.4), t60=0.25, bright=0.6, seed=seed + 2)) * 0.18
    x += at(n, 0.11, clasp(seed + 3)) * 0.8 + at(n, 0.11, K.bar(1760, K.ns(0.4), t60=0.25, bright=0.6, seed=seed + 4)) * 0.2
    sing = K.sine(K.glide(K.ns(0.6), 440, 880), K.ns(0.6)) * K.adsr(K.ns(0.6), 0.15, 0.1, 0.6, 0.3)
    x += at(n, 0.16, sing) * 0.12                                                          # the line drawn taut
    x += at(n, 0.2, thread(220, 0.9, 0.7, seed + 5, 0.45)) * 0.3                           # its hum settling
    x += at(n, 0.3, K.glass(1318.5, K.ns(0.8), bright=0.45, t60=0.7, seed=seed + 6, hit=0.3)) * 0.1
    return A.finish(x, 0.25)


SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, attenuation, loop
    ("ascent_rappel_pay", pay, -22.0, "ascent.rappel_pay", "Rope runs through a hand", 16, True),
    ("ascent_rappel_pay_glass", pay_glass, -22.5, "ascent.rappel_pay_glass", "A glass line runs out", 16, True),
    ("ascent_rappel_catch", catch, -22.0, "ascent.rappel_catch", "A rope is gripped", 12, False),
    ("ascent_rappel_kick", kick, -20.0, "ascent.rappel_kick", "Boots kick off rock", 20, False),
    ("ascent_rappel_plant", plant, -21.0, "ascent.rappel_plant", "Boots land on rock", 16, False),
    ("ascent_rappel_reel", reel, -22.0, "ascent.rappel_reel", "A reel winds", 12, False),
    ("ascent_rappel_fit", fit, -21.0, "ascent.rappel_fit", "A fitting snaps onto a rope", 12, False),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    tgt, tol, ceil, q = TARGETS["sfx"]
    if "--json" not in argv:
        os.makedirs(SOUNDS, exist_ok=True)
        only = argv[argv.index("--only") + 1] if "--only" in argv else None
        for name, fn, rms, *_ in SOUNDS_SPEC:
            assert abs(rms - tgt) <= tol, name
            if only and name != only:
                continue
            x = K.mono(fn(sum(map(ord, name))))
            y = K.master(x, rms, ceil - 0.7)
            path = os.path.join(SOUNDS, name + ".ogg")
            K.write_ogg(path, y, q, None)
            print(f"  {name:26s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds, lang = OrderedDict(), OrderedDict()
    for name, _, _, event, subtitle, att, loop in SOUNDS_SPEC:
        entry = OrderedDict(name="awaken:" + name)
        entry["attenuation_distance"] = att
        if loop:
            entry["stream"] = False
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=[entry])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    # the lang file also holds the fittings' own words (written by hand): keep every line that isn't a subtitle here
    lang_path = os.path.join(MERGE, "lang/en_us.json")
    if os.path.exists(lang_path):
        with open(lang_path) as f:
            for k, v in json.load(f, object_pairs_hook=OrderedDict).items():
                if not k.startswith("subtitles.awaken.ascent.rappel_"):
                    lang[k] = v
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/ascent_rappel")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
