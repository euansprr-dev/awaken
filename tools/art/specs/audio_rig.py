"""The Gate Rig's sounds (docs/SPEC.md §17 "Gate Rig"): what a living room sounds like when it moves. Synthesised
from scratch with tools/art/audiokit.py (no samples), mono (they are played in the world), mastered to the sfx loudness
of audio_mix.py, written to src/main/resources/assets/awaken/sounds/gate_rig_*.ogg and registered as gate.rig.* in
src/main/merge/gates/assets/awaken/ (sounds.json and the lang file: other keys are kept as they are).

    python3 tools/art/specs/audio_rig.py            render every file and write the JSON
    python3 tools/art/specs/audio_rig.py --json     only the JSON

Three families, one per archetype, and the cave-in:
- Collapse Run, stone: crack (a tile splitting under you, grit in it), fall (it breaks away and drops into the dark),
  knit (the causeway pulling itself back together, a warm tone under it), run (the floor shuddering awake), pit
  (thrown back out with a gust and a thud).
- Mirror Hall, glass and light: turn (stone grinding round on its pivot, a ratchet's click, the glass ringing), hum (the
  lens's steady chord), burn (a sizzle where the light bites), ignite (the seal catching the light: a rising chord in
  the System's D major pentatonic), shatter (cracked glass falling away in a hundred small bells).
- Gravity Wells, air: flip_up and flip_down (the whoomph of down turning over, rising or falling), surge (the room
  drawing breath before it turns).
- Cave-in: cave (the ceiling creaking, dust trickling), rubble (a block of it hitting the floor).
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
MERGE = os.path.join(ROOT, "src/main/merge/gates/assets/awaken")

_IR = {}


def hall():
    """A stone hall: a longer tail than the body's sounds, a gate is a big room."""
    if "h" not in _IR:
        _IR["h"] = K.reverb_ir(t60=1.1, predelay=0.012, bright=0.4, width=0.0, early=0.6, size=0.7, seed=91)
    return _IR["h"]


def at(n, t, seg):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def burst(dur, fc, q, t60, seed, attack=0.001):
    n = K.ns(dur)
    return K.bandnoise(n, fc, q, seed) * K.perc(n, attack, t60)


def thump(f0, f1, dur, t60, body=0.5, body_fc=240, seed=0):
    n = K.ns(dur)
    s = K.sine(K.glide(n, f0, f1), n) * K.perc(n, 0.003, t60)
    return s + body * K.bandnoise(n, body_fc, 1.1, seed) * K.perc(n, 0.002, t60 * 0.5)


def sweep(dur, f_from, f_to, q, seed, rise=0.3, shape=1.5, color="pink"):
    """Air or grit moving: band noise whose centre slides, swelling and fading."""
    n = K.ns(dur)
    x = K.noise(n, color, seed)
    y = K.stft_filter(x, lambda t, f: K.bp_resp(f_from * (f_to / f_from) ** min(1.0, max(0.0, t / dur)), q)(f), 1024, 256)
    u = np.arange(n) / max(n - 1, 1)
    env = np.where(u < rise, (u / rise) ** shape, ((1 - u) / (1 - rise)) ** (shape * 0.8))
    return y / (np.std(y) + 1e-12) * env


def grit(dur, rate, seed, bright=0.4, fade=1.0):
    n = K.ns(dur)
    u = np.arange(n) / max(n - 1, 1)
    return K.crackle(n, rate, seed, bright, 0.5) * (1 - u) ** fade


def grind(dur, fc, seed, wobble=7.0):
    """Stone dragged on stone: low band noise with a slow lurch in it."""
    n = K.ns(dur)
    x = K.bandnoise(n, fc, 2.2, seed)
    lurch = 0.6 + 0.4 * np.sin(2 * np.pi * wobble * np.arange(n) / K.SR + seed)
    return x * lurch * K.line(n, [(0, 0), (0.1, 1), (0.8, 0.8), (1, 0)])


def glass_ping(f0, dur, seed, bright=0.7):
    return K.glass(f0, K.ns(dur), bright=bright, t60=dur * 0.8, seed=seed)


def finish(x, wet=0.22, lp=8000):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, hall()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.001, 0.03)


# ---- the Collapse Run --------------------------------------------------------------------------------------------

def crack(seed):
    n = K.ns(0.5)
    x = at(n, 0.0, burst(0.04, 2600, 1.2, 0.02, seed + 1)) * 0.9            # the split
    x += at(n, 0.005, grit(0.32, 160, seed + 2, 0.5, 1.6)) * 0.55          # grit running along it
    x += at(n, 0.01, grind(0.28, 320, seed + 3, 11.0)) * 0.3               # the slab shifting
    x += at(n, 0.0, thump(110, 70, 0.12, 0.06, 0.3, 300, seed + 4)) * 0.35
    return finish(x, 0.18)


def fall(seed):
    n = K.ns(1.0)
    x = at(n, 0.0, burst(0.05, 1500, 0.9, 0.04, seed + 1)) * 0.9           # it breaks away
    x += at(n, 0.0, grit(0.45, 120, seed + 2, 0.45, 1.2)) * 0.5
    x += at(n, 0.04, sweep(0.6, 1800, 260, 1.4, seed + 3, 0.15, 1.2)) * 0.28  # falling, darker as it goes
    x += at(n, 0.62, thump(70, 40, 0.3, 0.16, 0.6, 180, seed + 4)) * 0.4    # a long way down, it lands
    x += at(n, 0.63, grit(0.25, 90, seed + 5, 0.3, 1.5)) * 0.25
    return finish(x, 0.3, 7000)


def knit(seed):
    n = K.ns(0.6)
    g = grit(0.35, 140, seed + 1, 0.4, 0.6)[::-1]                          # the crumble, run backward
    x = at(n, 0.0, g) * 0.45
    tone = K.sine(K.glide(K.ns(0.5), 180, 270), K.ns(0.5)) * K.line(K.ns(0.5), [(0, 0), (0.6, 1), (1, 0)])
    x += at(n, 0.02, tone) * 0.22                                          # a warm tone pulling it shut
    x += at(n, 0.34, thump(160, 120, 0.1, 0.05, 0.4, 420, seed + 2)) * 0.6  # set
    return finish(x, 0.2)


def run(seed):
    n = K.ns(1.5)
    x = at(n, 0.0, K.sub_boom(K.ns(1.4), 52, 34, 1.0, seed + 1)) * 0.8     # the floor wakes
    x += at(n, 0.05, grind(1.3, 180, seed + 2, 5.0)) * 0.55
    x += at(n, 0.1, grit(1.2, 60, seed + 3, 0.35, 0.8)) * 0.3
    return finish(x, 0.3, 6000)


def pit(seed):
    n = K.ns(0.75)
    x = at(n, 0.0, sweep(0.45, 300, 1600, 1.0, seed + 1, 0.6, 1.4)) * 0.45  # a gust from below
    x += at(n, 0.38, thump(90, 50, 0.3, 0.14, 0.7, 220, seed + 2)) * 0.9    # landing on the ledge
    x += at(n, 0.39, grit(0.25, 100, seed + 3, 0.35, 1.4)) * 0.3
    return finish(x)


# ---- the Mirror Hall ---------------------------------------------------------------------------------------------

def turn(seed):
    n = K.ns(0.55)
    x = at(n, 0.0, grind(0.32, 600, seed + 1, 18.0)) * 0.5                 # stone on its pivot
    for i, t in enumerate((0.05, 0.13, 0.21)):
        x += at(n, t, burst(0.015, 3800, 2.0, 0.006, seed + 2 + i)) * 0.35  # the ratchet
    x += at(n, 0.3, burst(0.02, 2200, 1.4, 0.01, seed + 6)) * 0.8           # home: click
    x += at(n, 0.3, glass_ping(K.hz(K.midi("A5")), 0.25, seed + 7)) * 0.25  # and the glass rings
    return finish(x, 0.25)


def hum(seed):
    n = K.ns(1.8)
    tones = [K.hz(K.midi(m)) for m in ("D4", "A4", "D5")]
    x = np.zeros(n)
    for i, f in enumerate(tones):
        beat = 1 + 0.25 * np.sin(2 * np.pi * (0.7 + 0.3 * i) * np.arange(n) / K.SR)
        x += K.sine(f, n) * beat * (0.5, 0.3, 0.15)[i]
    x *= K.line(n, [(0, 0), (0.2, 1), (0.75, 1), (1, 0)])
    x += K.lowpass(K.noise(n, "pink", seed), 900, 2) * 0.03
    return finish(x, 0.35)


def burn(seed):
    n = K.ns(0.55)
    hiss = K.highpass(K.noise(n, "white", seed + 1), 3500, 2) * K.perc(n, 0.004, 0.3)
    x = hiss * 0.35 + at(n, 0.0, grit(0.5, 400, seed + 2, 0.9, 1.0)) * 0.45  # the sizzle and its spits
    x += at(n, 0.0, burst(0.05, 900, 1.0, 0.03, seed + 3)) * 0.3
    return finish(x, 0.12, 11000)


def ignite(seed):
    n = K.ns(1.7)
    x = np.zeros(n)
    for i, (m, t) in enumerate((("D5", 0.0), ("F#5", 0.08), ("A5", 0.16), ("B5", 0.24), ("D6", 0.32))):
        x += at(n, t, glass_ping(K.hz(K.midi(m)), 1.3, seed + i, 0.6)) * (0.5 - i * 0.05)
    swell = K.pad(K.hz(K.midi("D4")), n, voices=5, seed=seed + 9, bright=0.35)
    x += swell * K.line(n, [(0, 0), (0.3, 0.8), (0.7, 0.5), (1, 0)]) * 0.3
    return finish(x, 0.35, 12000)


def shatter(seed):
    n = K.ns(1.2)
    x = at(n, 0.0, burst(0.08, 4200, 0.8, 0.05, seed + 1)) * 0.9            # the crack runs out
    rng = K.rng(seed + 2)
    for i in range(18):
        t = 0.02 + rng.random() * 0.6
        f = K.hz(K.midi(int(rng.integers(79, 98))))
        x += at(n, t, glass_ping(f, 0.4, seed + 10 + i, 0.8)) * (0.18 * (1 - t))  # a hundred small bells
    x += at(n, 0.01, grit(0.7, 260, seed + 3, 0.95, 1.4)) * 0.35
    return finish(x, 0.3, 14000)


# ---- the Gravity Wells -------------------------------------------------------------------------------------------

def flip(seed, up):
    n = K.ns(0.75)
    lo, hi = (240, 2400) if up else (2400, 240)
    x = at(n, 0.0, sweep(0.6, lo, hi, 1.1, seed + 1, 0.35, 1.3)) * 0.6      # the whoomph
    x += at(n, 0.0, K.sine(K.glide(K.ns(0.5), 60 if up else 110, 110 if up else 50), K.ns(0.5)) * K.perc(K.ns(0.5), 0.02, 0.3)) * 0.6
    x += at(n, 0.02, glass_ping(K.hz(K.midi("D6" if up else "A5")), 0.4, seed + 2, 0.5)) * 0.12
    return finish(x, 0.2)


def flip_up(seed):
    return flip(seed, True)


def flip_down(seed):
    return flip(seed, False)


def surge(seed):
    n = K.ns(1.7)
    p = K.pad(K.hz(K.midi("A3")), n, voices=6, seed=seed + 1, bright=0.3)
    trem = 1 + 0.35 * np.sin(2 * np.pi * K.glide(n, 3, 11) * np.arange(n) / K.SR)
    x = p * trem * K.line(n, [(0, 0), (0.8, 1), (0.95, 0.8), (1, 0)]) * 0.5
    x += at(n, 0.0, sweep(1.6, 200, 1400, 0.9, seed + 2, 0.85, 1.6)) * 0.25
    return finish(x, 0.3)


# ---- the cave-in -------------------------------------------------------------------------------------------------

def cave(seed):
    n = K.ns(1.1)
    x = at(n, 0.0, grind(0.8, 140, seed + 1, 3.0)) * 0.6                   # the ceiling creaks
    x += at(n, 0.1, grit(0.9, 220, seed + 2, 0.7, 0.4)) * 0.3              # dust trickling down
    x += at(n, 0.0, burst(0.03, 1800, 1.0, 0.015, seed + 3)) * 0.4
    return finish(x, 0.3, 9000)


def rubble(seed):
    n = K.ns(0.85)
    x = at(n, 0.0, thump(80, 38, 0.45, 0.22, 0.9, 170, seed + 1)) * 1.0     # it lands
    x += at(n, 0.0, K.highpass(burst(0.03, 3200, 0.7, 0.01, seed + 2), 1600, 2)) * 0.45
    x += at(n, 0.01, grit(0.5, 130, seed + 3, 0.4, 1.3)) * 0.45            # bits of it skittering
    return finish(x, 0.25, 7000)


SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, volume, attenuation
    ("gate_rig_crack", crack, -21.0, "gate.rig.crack", "Floor cracks", 1.0, 20),
    ("gate_rig_fall", fall, -20.0, "gate.rig.fall", "Floor falls away", 1.0, 24),
    ("gate_rig_knit", knit, -22.0, "gate.rig.knit", "Floor knits back", 1.0, 20),
    ("gate_rig_run", run, -19.0, "gate.rig.run", "The floor shudders", 1.0, 32),
    ("gate_rig_pit", pit, -20.0, "gate.rig.pit", "The pit throws you back", 1.0, 16),
    ("gate_rig_turn", turn, -21.0, "gate.rig.turn", "Mirror turns", 1.0, 16),
    ("gate_rig_hum", hum, -23.0, "gate.rig.hum", "Lens hums", 1.0, 14),
    ("gate_rig_burn", burn, -21.0, "gate.rig.burn", "Light burns", 1.0, 16),
    ("gate_rig_ignite", ignite, -20.0, "gate.rig.ignite", "Seal catches the light", 1.0, 32),
    ("gate_rig_shatter", shatter, -19.5, "gate.rig.shatter", "Mirror shatters", 1.0, 28),
    ("gate_rig_flip_up", flip_up, -21.0, "gate.rig.flip_up", "Gravity turns over", 1.0, 16),
    ("gate_rig_flip_down", flip_down, -21.0, "gate.rig.flip_down", "Gravity rights itself", 1.0, 16),
    ("gate_rig_surge", surge, -21.0, "gate.rig.surge", "The room draws breath", 1.0, 32),
    ("gate_rig_cave", cave, -20.5, "gate.rig.cave", "Ceiling creaks", 1.0, 24),
    ("gate_rig_rubble", rubble, -19.0, "gate.rig.rubble", "Rubble lands", 1.0, 24),
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
    sounds_path, lang_path = os.path.join(MERGE, "sounds.json"), os.path.join(MERGE, "lang/en_us.json")
    sounds = json.load(open(sounds_path), object_pairs_hook=OrderedDict)
    lang = json.load(open(lang_path), object_pairs_hook=OrderedDict)
    for name, _, _, event, subtitle, vol, att in SOUNDS_SPEC:
        entry = OrderedDict(name="awaken:" + name)
        if vol != 1.0:
            entry["volume"] = vol
        entry["attenuation_distance"] = att
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=[entry])
        lang["subtitles.awaken." + event] = subtitle
    with open(sounds_path, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(SOUNDS_SPEC)} gate.rig.* in merge/gates")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
