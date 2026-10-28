"""The deep's sounds (docs/SPEC.md §28.3; PLAN 7.4-7.5): mining with its own voice. Synthesised from scratch with
tools/art/audiokit.py (no samples), mono (played in the world), mastered to the sfx loudness of audio_mix.py, written to
src/main/resources/assets/awaken/sounds/delve_*.ogg and registered as delve.* in src/main/merge/delve/assets/awaken/
(sounds.json and the lang file: other keys are kept as they are).

    python3 tools/art/specs/audio_delve.py            render every file and write the JSON
    python3 tools/art/specs/audio_delve.py --json     only the JSON
    python3 tools/art/specs/audio_delve.py --only iron,diamond

Every ore sounds like itself when the pick bites (PLAN 7.4): iron rings, copper clanks dull, gold is soft, diamond cracks
like glass, Deepglass hums; coal thocks, redstone fizzes, lapis chimes, emerald ticks like crystal. The last blow is a
crack of the rock letting go. A lode's heart breaking is a deep split and the whole vein ringing; cracks crackle along
it. A cave-in creaks and trickles, then the roof comes down. The heavy strike strains, glints and lands. The Tuning
Fork takes an ore's note, strikes, and rings (a long, pure, seamlessly looping tone the game bends in pitch). A piton
tinks into rock; Runner's rope whirs as it pays out; Deepglass hums; Deep Ear hears a cave breathe or glass hum behind
the rock.
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
MERGE = os.path.join(ROOT, "src/main/merge/delve/assets/awaken")

_IR = {}


def cave_ir():
    """A cave: a dark, longish tail with strong early reflections off near rock."""
    if "c" not in _IR:
        _IR["c"] = K.reverb_ir(t60=1.4, predelay=0.018, bright=0.28, width=0.0, early=0.8, size=0.8, seed=73)
    return _IR["c"]


def at(n, t, seg):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def burst(dur, fc, q, t60, seed, attack=0.0008):
    n = K.ns(dur)
    return K.bandnoise(n, fc, q, seed) * K.perc(n, attack, t60)


def thump(f0, f1, dur, t60, body=0.5, body_fc=240, seed=0):
    n = K.ns(dur)
    s = K.sine(K.glide(n, f0, f1), n) * K.perc(n, 0.002, t60)
    return s + body * K.bandnoise(n, body_fc, 1.1, seed) * K.perc(n, 0.0015, t60 * 0.5)


def grit(dur, rate, seed, bright=0.4, fade=1.0):
    n = K.ns(dur)
    u = np.arange(n) / max(n - 1, 1)
    return K.crackle(n, rate, seed, bright, 0.5) * (1 - u) ** fade


def click(seed, fc=4200, dur=0.012):
    """The steel bite itself: a tiny broadband tick."""
    return burst(dur, fc, 0.8, dur * 0.6, seed, attack=0.0003)


def finish(x, wet=0.25, lp=9000):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, cave_ir()))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.0008, 0.03)


# ---- the strike: every ore its own voice ---------------------------------------------------------------------------

def strike_coal(seed):
    n = K.ns(0.32)
    x = at(n, 0, thump(160, 120, 0.25, 0.06, 0.9, 700, seed)) * 0.9          # a dull thock, sooty
    x += at(n, 0, burst(0.05, 1100, 1.0, 0.03, seed + 1)) * 0.6
    x += at(n, 0.005, grit(0.18, 90, seed + 2, 0.25, 1.5)) * 0.25
    return finish(x, 0.18, 6000)


def strike_copper(seed):
    n = K.ns(0.5)
    x = at(n, 0, click(seed)) * 0.5
    x += at(n, 0.001, K.modal(410, [1, 2.31, 3.93, 5.6], [1, 0.5, 0.3, 0.15], [0.22, 0.14, 0.09, 0.06], K.ns(0.45), seed=seed + 1)) * 0.75
    x += at(n, 0, burst(0.04, 900, 1.2, 0.02, seed + 2)) * 0.5                 # dull, a clank more than a ring
    return finish(x, 0.2, 6500)


def strike_iron(seed):
    n = K.ns(0.75)
    x = at(n, 0, click(seed, 5200)) * 0.6
    x += at(n, 0.0008, K.modal(1180, [1, 2.76, 5.4, 8.93], [1, 0.55, 0.3, 0.12], [0.62, 0.38, 0.2, 0.1], K.ns(0.7),
                               seed=seed + 1, detune_cents=4, pair=0.4)) * 0.55     # iron rings
    x += at(n, 0, burst(0.03, 2400, 1.2, 0.015, seed + 2)) * 0.4
    return finish(x, 0.22, 11000)


def strike_gold(seed):
    n = K.ns(0.45)
    x = at(n, 0, thump(240, 200, 0.3, 0.08, 0.4, 900, seed)) * 0.6           # soft: the metal gives
    x += at(n, 0.003, K.modal(660, [1, 2.4, 4.1], [1, 0.25, 0.08], [0.3, 0.15, 0.08], K.ns(0.4), seed=seed + 1)) * 0.35
    return finish(K.lowpass(x, 4500, 2), 0.2, 6000)


def strike_redstone(seed):
    n = K.ns(0.45)
    x = at(n, 0, click(seed, 3600)) * 0.5
    x += at(n, 0.002, grit(0.35, 420, seed + 1, 0.9, 1.2)) * 0.6              # it fizzes
    zap = K.sine(K.glide(K.ns(0.12), 2400, 900), K.ns(0.12)) * K.perc(K.ns(0.12), 0.002, 0.06)
    x += at(n, 0.004, zap) * 0.25
    x += at(n, 0, burst(0.04, 1400, 1.0, 0.02, seed + 2)) * 0.35
    return finish(x, 0.18, 10000)


def strike_lapis(seed):
    n = K.ns(0.7)
    x = at(n, 0, click(seed, 3800)) * 0.45
    x += at(n, 0.001, K.glass(1318, K.ns(0.65), bright=0.45, t60=0.5, seed=seed + 1, hit=0.4)) * 0.55   # a chime in the stone
    x += at(n, 0, burst(0.035, 1500, 1.0, 0.02, seed + 2)) * 0.45
    return finish(x, 0.25, 9000)


def strike_emerald(seed):
    n = K.ns(0.6)
    x = at(n, 0, click(seed, 6000)) * 0.4
    x += at(n, 0.001, K.glass(1760, K.ns(0.5), bright=0.7, t60=0.32, seed=seed + 1, hit=0.6)) * 0.45
    x += at(n, 0.03, K.glass(2637, K.ns(0.4), bright=0.6, t60=0.22, seed=seed + 2, hit=0.3)) * 0.25    # a second tick, higher
    x += at(n, 0, burst(0.03, 1800, 1.0, 0.018, seed + 3)) * 0.35
    return finish(x, 0.22, 12000)


def strike_diamond(seed):
    n = K.ns(0.7)
    x = at(n, 0, K.highpass(burst(0.02, 7000, 0.5, 0.008, seed, 0.0002), 3000, 2)) * 0.9   # cracks like glass
    x += at(n, 0.002, grit(0.22, 900, seed + 1, 1.0, 2.0)) * 0.5
    x += at(n, 0.004, K.glass(2217, K.ns(0.6), bright=0.85, t60=0.45, seed=seed + 2, hit=0.8)) * 0.45
    x += at(n, 0, burst(0.03, 2000, 1.0, 0.015, seed + 3)) * 0.3
    return finish(x, 0.25, 14000)


def strike_deepglass(seed):
    n = K.ns(0.9)
    x = at(n, 0, click(seed, 2800)) * 0.4
    x += at(n, 0.002, K.glass(880, K.ns(0.8), bright=0.35, t60=0.7, seed=seed + 1, hit=0.5)) * 0.4
    hum = K.drone(110, K.ns(0.85), partials=(1, 2, 3), amps=(1, 0.4, 0.15), beat=0.7, seed=seed + 2)
    x += at(n, 0, hum * K.line(K.ns(0.85), [(0, 0), (0.06, 1), (0.5, 0.6), (1, 0)])) * 0.35          # it hums
    return finish(x, 0.3, 8000)


def strike_last(seed):
    n = K.ns(0.55)
    x = at(n, 0, K.highpass(burst(0.03, 2600, 0.7, 0.012, seed), 900, 2)) * 0.9   # the rock lets go
    x += at(n, 0, thump(110, 55, 0.4, 0.14, 0.8, 260, seed + 1)) * 0.8
    x += at(n, 0.01, grit(0.4, 160, seed + 2, 0.4, 1.4)) * 0.4
    return finish(x, 0.3, 9000)


# ---- the vein gives -------------------------------------------------------------------------------------------------

def vein_gives(seed):
    n = K.ns(2.8)
    x = at(n, 0, K.sub_boom(K.ns(1.6), 56, 32, 1.1, seed)) * 0.9                 # the heart splits, deep
    x += at(n, 0, K.highpass(burst(0.05, 2200, 0.6, 0.025, seed + 1), 600, 2)) * 0.8
    x += at(n, 0.02, grit(1.2, 220, seed + 2, 0.55, 1.2)) * 0.45                 # cracks running out along the seam
    # the vein rings: a rising spread of glass partials, a fifth and an octave over D
    for i, (f, t) in enumerate(((587.3, 0.15), (880.0, 0.32), (1174.7, 0.5), (1760.0, 0.72))):
        x += at(n, t, K.glass(f, K.ns(1.8), bright=0.55, t60=1.4, seed=seed + 10 + i, hit=0.3)) * (0.3 - i * 0.04)
    return finish(x, 0.45, 12000)


def vein_crack(seed):
    n = K.ns(0.35)
    x = at(n, 0, K.highpass(grit(0.3, 700, seed, 0.8, 1.6), 1200, 2)) * 0.9
    x += at(n, 0, burst(0.012, 4200, 0.8, 0.006, seed + 1, 0.0002)) * 0.5
    return finish(x, 0.3, 12000)


# ---- cave-ins -------------------------------------------------------------------------------------------------------

def cave_creak(seed):
    n = K.ns(2.6)
    groan = K.bandnoise(n, 160, 3.0, seed) * (0.6 + 0.4 * np.sin(2 * np.pi * 2.3 * np.arange(n) / K.SR))
    x = groan * K.line(n, [(0, 0), (0.15, 0.7), (0.6, 1.0), (1, 0.2)]) * 0.7      # the roof groans
    x += at(n, 0.2, grit(2.3, 140, seed + 1, 0.45, 0.3)) * 0.45                  # dust trickling down
    x += at(n, 0.9, burst(0.03, 1600, 1.0, 0.015, seed + 2)) * 0.35
    x += at(n, 1.7, burst(0.03, 1300, 1.0, 0.015, seed + 3)) * 0.45
    return finish(x, 0.4, 7000)


def cave_fall(seed):
    n = K.ns(2.4)
    x = at(n, 0, K.sub_boom(K.ns(2.0), 52, 28, 1.6, seed)) * 1.0                 # the roof comes down
    x += at(n, 0, K.lowpass(K.noise(K.ns(2.0), "brown", seed + 1), 600, 2) * K.perc(K.ns(2.0), 0.03, 1.2)) * 0.8
    for i in range(6):
        x += at(n, 0.08 + i * 0.17, thump(90 - i * 6, 45, 0.4, 0.18, 0.9, 200, seed + 10 + i)) * (0.6 - i * 0.06)
    x += at(n, 0.05, grit(1.8, 260, seed + 3, 0.5, 1.0)) * 0.5
    return finish(x, 0.45, 6500)


# ---- the heavy strike -----------------------------------------------------------------------------------------------

def heavy_charge(seed):
    n = K.ns(1.3)
    strain = K.lowpass(K.noise(n, "pink", seed), 900, 2) * K.line(n, [(0, 0), (0.7, 0.8), (1, 1.0)])
    tone = K.sine(K.glide(n, 110, 196), n) * K.line(n, [(0, 0), (0.3, 0.4), (1, 0.7)])
    return finish(strain * 0.6 + tone * 0.4, 0.2, 5000)


def heavy_glint(seed):
    n = K.ns(0.7)
    x = at(n, 0, K.glass(2349, K.ns(0.6), bright=0.8, t60=0.4, seed=seed, hit=0.8)) * 0.6
    x += at(n, 0.02, K.glass(3520, K.ns(0.5), bright=0.6, t60=0.3, seed=seed + 1, hit=0.2)) * 0.35
    return finish(x, 0.25, 15000)


def heavy_strike(seed):
    n = K.ns(1.0)
    x = at(n, 0, thump(130, 40, 0.8, 0.28, 1.0, 300, seed)) * 1.0               # it lands
    x += at(n, 0, K.highpass(burst(0.05, 2600, 0.5, 0.03, seed + 1), 800, 2)) * 0.8
    x += at(n, 0.01, grit(0.8, 300, seed + 2, 0.5, 1.2)) * 0.6                  # the rock bursts
    x += at(n, 0.002, K.modal(780, [1, 2.7, 5.3], [1, 0.4, 0.15], [0.35, 0.2, 0.1], K.ns(0.5), seed=seed + 3)) * 0.25
    return finish(x, 0.35, 9000)


# ---- the Tuning Fork ------------------------------------------------------------------------------------------------

def fork_tune(seed):
    n = K.ns(0.9)
    x = at(n, 0, K.glass(880, K.ns(0.85), bright=0.3, t60=0.7, seed=seed, hit=0.3)) * 0.5
    x += at(n, 0.05, K.sine(np.full(K.ns(0.8), 880.0), K.ns(0.8)) * K.perc(K.ns(0.8), 0.02, 0.6)) * 0.4
    return finish(x, 0.25, 9000)


def fork_strike(seed):
    n = K.ns(1.2)
    x = at(n, 0, click(seed, 3000, 0.01)) * 0.6
    fork = K.modal(880, [1, 6.27], [1, 0.25], [1.1, 0.15], K.ns(1.1), seed=seed + 1)   # a fork's pure tone, its clang partial short
    x += at(n, 0.001, fork) * 0.6
    return finish(x, 0.25, 12000)


def fork_ring(seed):
    """A pure fork tone with a slow beat, exactly 2 s of whole cycles at both frequencies: it loops without a seam."""
    n = K.ns(2.0)
    t = np.arange(n) / K.SR
    x = np.sin(2 * np.pi * 880.0 * t) + 0.5 * np.sin(2 * np.pi * 880.5 * t + 1.1) + 0.08 * np.sin(2 * np.pi * 1760.0 * t)
    return x * 0.5


def true_strike(seed):
    """A true strike (SPEC §34.3): a soft bell-glass chime over the bite, at A5; the game bends it to the ore's note."""
    n = K.ns(0.8)
    x = at(n, 0, K.glass(880, K.ns(0.75), bright=0.22, t60=0.55, seed=seed, hit=0.15)) * 0.45
    x += at(n, 0.004, K.modal(880, [1, 2.0, 3.01], [1, 0.3, 0.12], [0.6, 0.35, 0.2], K.ns(0.7), seed=seed + 1)) * 0.4
    return finish(x, 0.3, 11000)


# ---- pitons, rope, Deepglass, Deep Ear -------------------------------------------------------------------------------

def piton_set(seed):
    n = K.ns(0.45)
    x = at(n, 0, click(seed, 5000)) * 0.6
    x += at(n, 0.0005, K.modal(2600, [1, 2.4, 4.6], [1, 0.4, 0.2], [0.18, 0.1, 0.05], K.ns(0.3), seed=seed + 1)) * 0.5
    x += at(n, 0.12, click(seed + 2, 4600)) * 0.4                                # a second tap seats it
    x += at(n, 0.1205, K.modal(2750, [1, 2.4], [1, 0.3], [0.12, 0.06], K.ns(0.2), seed=seed + 3)) * 0.35
    return finish(x, 0.25, 12000)


def rope_pay(seed):
    n = K.ns(1.1)
    whirr = K.bandnoise(n, 900, 1.6, seed) * (0.55 + 0.45 * np.sin(2 * np.pi * K.glide(n, 28, 12).cumsum() / K.SR))
    x = whirr * K.line(n, [(0, 0), (0.05, 1), (0.75, 0.7), (1, 0)]) * 0.7         # it runs out over the lip
    x += at(n, 0.9, thump(140, 90, 0.2, 0.06, 0.6, 500, seed + 1)) * 0.4          # and snaps taut
    return finish(x, 0.3, 8000)


def deepglass_hum(seed):
    n = K.ns(2.6)
    hum = K.drone(98, n, partials=(1, 2, 3, 5), amps=(1, 0.5, 0.2, 0.06), beat=0.5, seed=seed)
    x = hum * K.line(n, [(0, 0), (0.3, 1), (0.7, 0.9), (1, 0)]) * 0.6
    x += at(n, 0.4, K.glass(1567, K.ns(1.6), bright=0.2, t60=1.2, seed=seed + 1, hit=0.0)) * 0.12
    return finish(x, 0.35, 5000)


def deep_ear_cave(seed):
    n = K.ns(2.2)
    breath = K.bandnoise(n, 420, 1.4, seed) * K.line(n, [(0, 0), (0.45, 1), (1, 0)])   # a cave breathing, far off
    return finish(K.lowpass(breath, 1600, 2) * 0.8, 0.6, 3000)


def deep_ear_glass(seed):
    n = K.ns(2.0)
    hum = K.drone(87, n, partials=(1, 2, 3), amps=(1, 0.4, 0.15), beat=0.6, seed=seed)
    x = hum * K.line(n, [(0, 0), (0.4, 1), (1, 0)]) * 0.6
    x += at(n, 0.3, K.glass(1396, K.ns(1.4), bright=0.15, t60=1.0, seed=seed + 1, hit=0.0)) * 0.12
    return finish(K.lowpass(x, 2500, 2), 0.55, 3000)


ORE_STRIKES = [("coal", strike_coal, "Coal struck"), ("copper", strike_copper, "Copper clanks"), ("iron", strike_iron, "Iron rings"),
               ("gold", strike_gold, "Gold gives"), ("redstone", strike_redstone, "Redstone fizzes"), ("lapis", strike_lapis, "Lapis chimes"),
               ("emerald", strike_emerald, "Emerald ticks"), ("diamond", strike_diamond, "Diamond cracks"),
               ("deepglass", strike_deepglass, "Deepglass hums")]

SOUNDS_SPEC = [
    # file, render, rms (dBFS, sfx -20 ± 4), event, subtitle, volume, attenuation
    *[("delve_strike_" + o, fn, -21.0, "delve.strike." + o, sub, 1.0, 16) for o, fn, sub in ORE_STRIKES],
    ("delve_strike_last", strike_last, -20.0, "delve.strike_last", "Rock gives way", 1.0, 16),
    ("delve_vein_gives", vein_gives, -18.5, "delve.vein_gives", "The vein gives", 1.0, 48),
    ("delve_vein_crack", vein_crack, -21.0, "delve.vein_crack", "Cracks run through the rock", 1.0, 24),
    ("delve_cave_creak", cave_creak, -20.0, "delve.cave_creak", "The roof creaks", 1.0, 32),
    ("delve_cave_fall", cave_fall, -17.5, "delve.cave_fall", "The roof comes down", 1.0, 48),
    ("delve_heavy_charge", heavy_charge, -22.0, "delve.heavy_charge", "A pick drawn back", 1.0, 12),
    ("delve_heavy_glint", heavy_glint, -21.0, "delve.heavy_glint", "The strike is ready", 1.0, 12),
    ("delve_heavy_strike", heavy_strike, -18.0, "delve.heavy_strike", "A heavy strike bores the rock", 1.0, 32),
    ("delve_fork_tune", fork_tune, -21.0, "delve.fork_tune", "The fork takes a note", 1.0, 12),
    ("delve_fork_strike", fork_strike, -20.0, "delve.fork_strike", "Tuning fork struck", 1.0, 16),
    ("delve_true_strike", true_strike, -22.0, "delve.true_strike", "A true strike rings", 1.0, 12),
    ("delve_fork_ring", fork_ring, -22.0, "delve.fork_ring", "Tuning fork rings", 1.0, 12),
    ("delve_piton_set", piton_set, -20.0, "delve.piton_set", "Piton hammered in", 1.0, 16),
    ("delve_rope_pay", rope_pay, -21.0, "delve.rope_pay", "Rope pays out", 1.0, 16),
    ("delve_deepglass_hum", deepglass_hum, -23.0, "delve.deepglass_hum", "Deepglass hums", 1.0, 8),
    ("delve_deep_ear_cave", deep_ear_cave, -23.0, "delve.deep_ear_cave", "A cave breathes behind the rock", 1.0, 16),
    ("delve_deep_ear_glass", deep_ear_glass, -23.0, "delve.deep_ear_glass", "Glass hums behind the rock", 1.0, 16),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    tgt, tol, ceil, q = TARGETS["sfx"]
    only = None
    for a in argv:
        if a.startswith("--only"):
            only = set(argv[argv.index(a) + 1].split(",")) if a == "--only" else set(a.split("=", 1)[1].split(","))
    if "--json" not in argv:
        os.makedirs(SOUNDS, exist_ok=True)
        for name, fn, rms, *_ in SOUNDS_SPEC:
            short = name.replace("delve_strike_", "").replace("delve_", "")
            if only is not None and short not in only:
                continue
            assert abs(rms - tgt) <= tol, name
            x = K.mono(fn(sum(map(ord, name))))
            y = K.master(x, rms, ceil - 0.7)
            path = os.path.join(SOUNDS, name + ".ogg")
            K.write_ogg(path, y, q, None)
            print(f"  {name:26s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds_path, lang_path = os.path.join(MERGE, "sounds.json"), os.path.join(MERGE, "lang/en_us.json")
    sounds = json.load(open(sounds_path), object_pairs_hook=OrderedDict) if os.path.exists(sounds_path) else OrderedDict()
    lang = json.load(open(lang_path), object_pairs_hook=OrderedDict) if os.path.exists(lang_path) else OrderedDict()
    for name, _, _, event, subtitle, vol, att in SOUNDS_SPEC:
        entry = OrderedDict(name="awaken:" + name)
        if vol != 1.0:
            entry["volume"] = vol
        entry["attenuation_distance"] = att
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=[entry])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.dirname(lang_path), exist_ok=True)
    with open(sounds_path, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(OrderedDict(sorted(lang.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(SOUNDS_SPEC)} delve.* in merge/delve")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
