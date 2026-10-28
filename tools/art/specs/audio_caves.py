"""The senses underground (SPEC §28.5; PLAN 8.4): the Hollows' sound and the deep's hum. Synthesised from scratch with
tools/art/audiokit.py (no samples), written to src/main/resources/assets/awaken/sounds/ and registered as delve.cave.*
and delve.deep.* in src/main/merge/delve_sense/assets/awaken/ (sounds.json and the subtitles).

    python3 tools/art/specs/audio_caves.py            render every file and write the JSON
    python3 tools/art/specs/audio_caves.py --json     only the JSON
    python3 tools/art/specs/audio_caves.py --only amb_cave_drip,delve_deep_hum   some files (and the JSON)

The cave layer is delve's own client driver (delve.client.CaveSound), placed on real blocks it finds round you, so the
one-shots are mono and fairly dry (Sound Physics gives them the cave's own room; a short dark tail is baked in for
players without it), mastered under the ambience rule (amb_*: -28 dBFS gated RMS, mono). AmbientSounds plays the
diffuse cave beds in the pack; these are what it lacks: sounds with a place.

  amb_cave_drip_*          a drop off a dripstone tip or a wet roof landing on wet rock: a tick and the bubble's rising
                           plink, sometimes a smaller drop thrown back up
  amb_cave_drip_pool_*     a drop into a pool: rounder and lower, a soft plop under it
  amb_cave_drip_acid_*     sulfur caves: a drop off a sulfur spike into an acid pool, sizzling as it lands
  delve/settle_*, delve/cave_breath, delve/band_room, delve/cave_answers
                           recorded (CC0), cut by audio_cave_recorded.py; only their events are written here
  amb_cave_far_water       a loop: an underground stream heard from a distance, its highs gone, the cave round it
  amb_cave_sulfur_vent_*   sulfur caves: gas seeping from the rock, a sputtering hiss with a low push under it
  amb_cave_sulfur_bubble_* sulfur caves: thick acid bubbling up in a pool, one to three slow blops
  delve_deep_hum           a loop (stereo, streamed): the deep's hum below y 0, a low D in pure partials that beat
                           slowly, a breath of pressure under it; every partial whole over the loop, so it never seams
  delve_deep_seep          a loop (stereo, streamed): the seep's second voice over the hum, stranger: a wordless voice
                           a tritone off the hum, bending slowly, glass swelling backwards out of the dark, a whisper

The air beds the Conductor mixes for players without AmbientSounds (assets/awaken/soundscape/family/hollows.json; read
straight from the files, no events), co-prime lengths so the mix never repeats audibly, the deeper the lower:

  amb_world_hollows_a      shallow cave air: a soft hollow air in the mid band (fades with depth)
  amb_world_hollows_b      the cave's body: pink air through the low modes of a rock room
  amb_world_hollows_c      the deep undertone: a slow low pressure (rises with depth)
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
MERGE = os.path.join(ROOT, "src/main/merge/delve_sense/assets/awaken")

_IR = {}


def ir(kind):
    """Mono rooms: "cave" a mid cave (dark, strong early reflections off near rock), "hall" a big dark cavern."""
    if kind not in _IR:
        if kind == "cave":
            _IR[kind] = K.reverb_ir(t60=1.7, predelay=0.014, bright=0.26, width=0.0, early=0.75, size=0.9, seed=81)
        else:
            _IR[kind] = K.reverb_ir(t60=3.2, predelay=0.03, bright=0.18, width=0.0, early=0.55, size=1.6, seed=82, low_mult=1.4)
    return _IR[kind]


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def at(n, t, seg):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def burst(dur, fc, q, t60, seed, attack=0.0006):
    n = K.ns(dur)
    return K.bandnoise(n, fc, q, seed) * K.perc(n, attack, t60)


def finish(x, room="cave", wet=0.35, lp=9000):
    n = len(x)
    y = x + wet * K.mono(K.convolve(x, ir(room)))[:n]
    return K.declick(K.lowpass(y, lp, 2), 0.0006, 0.05)


def circ(x, resp):
    """A zero-phase filter applied around the loop (the FFT of exactly its length): a loop stays seamless."""
    n = len(x)
    H = resp(np.fft.rfftfreq(n, 1 / K.SR))
    if x.ndim == 1:
        return np.fft.irfft(np.fft.rfft(x) * H, n)
    return np.stack([np.fft.irfft(np.fft.rfft(x[:, c]) * H, n) for c in range(x.shape[1])], axis=1)


def master_loop(x, rms_db, ceiling_db):
    """Mastering for a loop: the rumble filter around the loop, a plain gain to the RMS (no limiter to pump, no fades
    at the ends to tick at the seam), and only a gain down if a peak passes the ceiling."""
    y = circ(x, K.hp_resp(25.0, 2))
    y = y * K.dbamp(rms_db - K.gated_rms(y))
    tp = K.true_peak(y)
    return y * K.dbamp(ceiling_db - tp - 0.05) if tp > ceiling_db else y


def plink(f0, rise, t60, dur=0.16):
    """A drop's bubble: a sine whose pitch rises as the bubble it trapped nears the surface, gone in a few hundredths."""
    n = K.ns(dur)
    t = K.tvec(n)
    f = f0 * (1 + rise * (1 - np.exp(-t / 0.011)))
    return K.sine(f, n) * K.perc(n, 0.0004, t60)


def _swells(n, seed, rate, floor, power=1.6):
    e = 0.5 + 0.5 * K.smooth_noise(n, rate, seed)
    return floor + (1 - floor) * np.clip(e, 0, 1) ** power


# ---- drips -------------------------------------------------------------------------------------------------------------

def drip(seed):
    r = K.rng(seed)
    n = K.ns(2.0)
    f0 = r.uniform(1050, 2350)
    x = at(n, 0, K.highpass(burst(0.004, 6200, 0.7, 0.002, seed), 2600, 2)) * r.uniform(0.22, 0.4)   # the drop strikes
    x += at(n, 0.0008, plink(f0, r.uniform(0.35, 0.85), r.uniform(0.035, 0.065))) * 0.9
    if r.random() < 0.55:                                                                       # a smaller drop thrown back up
        x += at(n, r.uniform(0.07, 0.17), plink(f0 * r.uniform(1.25, 1.6), 0.6, 0.028)) * r.uniform(0.18, 0.32)
    return finish(x, "cave", 0.42, 9500)


def drip_pool(seed):
    r = K.rng(seed)
    n = K.ns(2.4)
    f0 = r.uniform(620, 1250)
    x = at(n, 0, K.lowpass(burst(0.03, 230, 1.4, 0.02, seed, 0.001), 900, 2)) * 0.45               # a soft plop
    x += at(n, 0.001, plink(f0, r.uniform(0.55, 1.1), r.uniform(0.08, 0.13), 0.3)) * 0.9          # a rounder bubble
    x += at(n, 0.0, K.highpass(burst(0.012, 3800, 0.9, 0.008, seed + 1), 1800, 2)) * 0.18          # a little splash
    if r.random() < 0.5:
        x += at(n, r.uniform(0.11, 0.2), plink(f0 * r.uniform(1.4, 1.8), 0.7, 0.04)) * 0.22
    return finish(x, "hall", 0.4, 8000)


def drip_acid(seed):
    r = K.rng(seed)
    n = K.ns(2.2)
    f0 = r.uniform(800, 1500)
    x = at(n, 0, plink(f0, r.uniform(0.3, 0.6), 0.05)) * 0.7
    m = K.ns(r.uniform(0.5, 0.9))
    u = np.arange(m) / m
    fizz = K.highpass(K.crackle(m, r.uniform(700, 1100), seed + 1, 0.9, 0.3), 2400, 2) * (1 - u) ** 1.6
    hiss = K.bandnoise(m, 5200, 0.9, seed + 2) * (1 - u) ** 2.2 * 0.25
    x += at(n, 0.012, fizz * 0.55 + hiss)                                                     # it sizzles where it lands
    return finish(x, "cave", 0.33, 11000)


# ---- loops ---------------------------------------------------------------------------------------------------------

def far_water(seed):
    L, seam = 19.0, 2.0  # 17 s
    n = K.ns(L)
    r = K.rng(seed)
    babble = np.zeros(n)
    count = int(L * 55)
    for k in range(count):                                                                     # many small bubbles in the run
        i = int(r.integers(0, n - K.ns(0.08)))
        f = r.uniform(320, 1400)
        a = r.pareto(2.2) * 0.25 + 0.05
        m = K.ns(r.uniform(0.02, 0.06))
        seg = plink(f, r.uniform(0.2, 0.7), m / K.SR * 0.8, m / K.SR)
        babble[i:i + len(seg)] += seg * min(a, 1.2)
    wash = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 700, 0.6)) * (0.8 + 0.2 * K.smooth_noise(n, 0.2, seed + 2))
    x = 0.55 * _norm(babble) + 0.45 * wash
    x = K.lowpass(x, 2100, 2)                                                                  # far: its highs gone
    x = x + 0.6 * K.mono(K.convolve(x, ir("hall")))[:n]
    return K.loop_seamless(K.highpass(x, 60, 2), seam)


# ---- sulfur caves --------------------------------------------------------------------------------------------------

def sulfur_vent(seed):
    r = K.rng(seed)
    dur = r.uniform(2.4, 3.6)
    n = K.ns(dur + 1.2)
    m = K.ns(dur)
    env = K.adsr(m, r.uniform(0.2, 0.4), 0.3, 0.75, r.uniform(0.7, 1.1))
    sputter = 0.62 + 0.38 * np.clip(0.5 + 0.5 * K.smooth_noise(m, 7.0, seed + 1), 0, 1)
    hiss = _norm(K.bandpass(K.noise(m, "white", seed + 2), r.uniform(2800, 3600), 0.7)) * 0.75
    hiss += _norm(K.bandpass(K.noise(m, "white", seed + 3), 6200, 1.0)) * 0.2
    push = _norm(K.lowpass(K.noise(m, "brown", seed + 4), 200, 2)) * 0.35                       # the gas's low push
    x = at(n, 0, (hiss * sputter + push) * env)
    for k in range(int(r.integers(2, 5))):                                                     # little pops in the vent
        x += at(n, r.uniform(0.2, dur - 0.3), burst(0.006, r.uniform(1500, 2600), 1.2, 0.004, seed + 10 + k)) * 0.3
    return finish(x, "cave", 0.3, 10000)


def sulfur_bubble(seed):
    r = K.rng(seed)
    n = K.ns(2.8)
    x = np.zeros(n)
    t = 0.0
    for k in range(int(r.integers(1, 4))):                                                     # one to three slow blops
        f0 = r.uniform(170, 420)
        x += at(n, t, plink(f0, r.uniform(0.3, 0.6), r.uniform(0.12, 0.24), 0.45)) * r.uniform(0.7, 1.0)
        x += at(n, t, K.lowpass(burst(0.05, 320, 1.0, 0.04, seed + k, 0.003), 700, 2)) * 0.35    # thick: a glop under it
        t += r.uniform(0.18, 0.5)
    return finish(K.lowpass(x, 5000, 2), "cave", 0.35, 6000)


# ---- the deep ------------------------------------------------------------------------------------------------------

def _whole(f, L):
    """The nearest frequency with a whole number of cycles in L seconds (so a loop of pure tones never seams)."""
    return max(1, round(f * L)) / L


def deep_hum(seed):
    """D1 and its partials, each with a twin a few cycles a loop away so they beat slowly, different on each side;
    a swell and a slow pressure throb whose periods divide the loop; a breath of low noise under it."""
    L = 29.0
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    f1 = K.hz(K.midi("D1"))
    chans = []
    for c in range(2):
        x = np.zeros(n)
        # the second partial leads; the fourth to eighth carry it on small speakers, where D1 itself can't be heard
        for p, a in ((1, 0.55), (2, 1.0), (3, 0.6), (4, 0.62), (6, 0.34), (8, 0.16), (12, 0.05)):
            f = _whole(f1 * p, L)
            twin = f + (2 + c + p % 3) / L                                                     # beats 2-5 times a loop
            ph = r.uniform(0, 6.28)
            x += a * (np.sin(2 * np.pi * f * t + ph) + 0.55 * np.sin(2 * np.pi * twin * t + ph + 1.7 + c))
        swell = 0.85 + 0.15 * np.sin(2 * np.pi * 2 * t / L + c * 0.6)                           # two swells a loop
        throb = 0.92 + 0.08 * np.sin(2 * np.pi * 7 * t / L)                                    # pressure, every ~4 s
        chans.append(_norm(x) * swell * throb)
    tone = np.stack(chans, axis=1)
    air_n = K.ns(L + 3.0)
    air = []
    for c in range(2):
        a = _norm(K.lowpass(K.noise(air_n, "brown", seed + 10 + c), 120, 2)) * _swells(air_n, seed + 20 + c, 0.05, 0.5)
        air.append(K.loop_seamless(a, 3.0)[:n])
    x = 0.82 * tone + 0.18 * np.stack(air, axis=1)
    return circ(circ(x, K.lp_resp(1400, 2)), K.hp_resp(28, 2))


def _voice(f0, n, vowel, bend, seed):
    """A wordless voice: a formant-shaped wavetable on a slowly bending pitch with a little vibrato."""
    amps = K.saw_amps(f0 * 1.06, bright=0.25, tilt=1.3, cap=60, formants=K.VOWELS[vowel], f0=f0)
    vib = K.vibrato(n, 4.2, 9.0, 0.5, seed, drift=6.0)
    return K.wt_voice(f0, n, amps, bend * vib)


def deep_seep(seed):
    """The second voice: a tritone over the hum's D (G#2 and D#3, a minor second against its A), two voices morphing
    between "u" and "o", bending a quarter tone up and back over the loop; glass notes swelling backwards out of the
    dark (Deepglass answering), and a whisper of moving formants. Stereo: each side its own voices."""
    L, seam = 25.0, 2.0  # 23 s
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    chans = []
    for c in range(2):
        bend = 2 ** ((48.0 * np.sin(2 * np.pi * t / (L - seam) + c * 1.3) * np.clip(t / 2.0, 0, 1)) / 1200)
        morph = 0.5 + 0.5 * np.sin(2 * np.pi * t / 11.5 + c)
        v = np.zeros(n)
        for f0, g in ((K.hz(K.midi("G#2")), 1.0), (K.hz(K.midi("D#3")), 0.55)):
            f0 *= 2 ** (r.uniform(-6, 6) / 1200)
            v += g * (_voice(f0, n, "u", bend, seed + 10 * c + 1) * (1 - morph) + _voice(f0, n, "o", bend, seed + 10 * c + 2) * morph)
        v = _norm(v) * (0.7 + 0.3 * _swells(n, seed + 30 + c, 0.09, 0.2, 1.2))
        glass = np.zeros(n)
        for k in range(4):                                                                     # glass swelling backwards
            f = K.hz(K.midi(("G#5", "D6", "A5", "D#6")[(k + c) % 4]))
            g = K.glass(f, K.ns(3.0), bright=0.25, t60=2.4, seed=seed + 50 + 7 * k + c, hit=0.0)
            g = g * np.exp(-np.arange(len(g)) / K.ns(1.2))
            rev = g[::-1]
            s = K.ns(r.uniform(1.0, L - seam - 4.0))
            glass[s:s + len(rev)] += rev[: n - s] * r.uniform(0.5, 1.0)
        cen = 1400 * 2 ** (0.9 * K.smooth_noise(n, 0.35, seed + 60 + c))

        def resp(tsec, f, cen=cen):
            return K.bp_resp(cen[min(n - 1, int(tsec * K.SR))], 3.0)(f)
        whisper = _norm(K.stft_filter(K.noise(n, "pink", seed + 70 + c), resp)) * _swells(n, seed + 80 + c, 0.25, 0.0, 2.0)
        chans.append(0.66 * v + 0.2 * _norm(glass) + 0.14 * whisper)
    x = np.stack(chans, axis=1)
    return K.loop_seamless(K.highpass(K.lowpass(x, 4200, 2), 60, 2), seam)


# ---- the Hollows' air beds (no AmbientSounds) ---------------------------------------------------------------------

def hollows_shallow(seed):
    L = 40.0  # 37 s after the 3 s seam
    n = K.ns(L)
    air = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 650, 0.6))
    high = _norm(K.bandpass(K.noise(n, "pink", seed + 2), 2200, 0.9))
    g = _swells(n, seed + 3, 0.07, 0.45, 1.3)
    return K.loop_seamless(K.lowpass((0.82 * air + 0.18 * high * g) * g, 4500, 2), 3.0)


def hollows_body(seed):
    L = 45.0  # 41 s after the 4 s seam
    n = K.ns(L)
    src = K.noise(n, "pink", seed + 1)
    modes = sum(a * _norm(K.bandpass(src, f, q)) for f, q, a in ((110, 4.0, 0.9), (190, 4.5, 0.7), (310, 5.0, 0.45), (470, 5.0, 0.25)))
    broad = _norm(K.bandpass(K.noise(n, "pink", seed + 2), 250, 0.6))
    g = _swells(n, seed + 3, 0.05, 0.55, 1.2)
    return K.loop_seamless(K.lowpass((0.6 * _norm(modes) + 0.4 * broad) * g, 1800, 2), 4.0)


def hollows_deep(seed):
    L = 57.0  # 53 s after the 4 s seam
    n = K.ns(L)
    deep = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 85, 2)) * _swells(n, seed + 2, 0.035, 0.5)
    press = _norm(K.bandpass(K.noise(n, "pink", seed + 3), 55, 2.5)) * _swells(n, seed + 4, 0.02, 0.3)
    return K.loop_seamless(K.highpass(0.7 * deep + 0.3 * press, 25, 2), 4.0)


# ---- the list ------------------------------------------------------------------------------------------------------

def _variants(stem, fn, count, rms):
    return [(f"{stem}_{k + 1}", (lambda f, k: (lambda s: f(s + 101 * k)))(fn, k), rms) for k in range(count)]


# file, render, rms (dBFS gated), channels (loops are the files without a variant number in LOOPS)
LOOPS = {"amb_cave_far_water", "delve_deep_hum", "delve_deep_seep", "amb_world_hollows_a",
         "amb_world_hollows_b", "amb_world_hollows_c"}
FILES = [
    *[(f, fn, rms, 1) for f, fn, rms in _variants("amb_cave_drip", drip, 6, -28.0)],
    *[(f, fn, rms, 1) for f, fn, rms in _variants("amb_cave_drip_pool", drip_pool, 4, -28.0)],
    *[(f, fn, rms, 1) for f, fn, rms in _variants("amb_cave_drip_acid", drip_acid, 3, -28.0)],
    ("amb_cave_far_water", far_water, -28.5, 1),
    *[(f, fn, rms, 1) for f, fn, rms in _variants("amb_cave_sulfur_vent", sulfur_vent, 3, -28.0)],
    *[(f, fn, rms, 1) for f, fn, rms in _variants("amb_cave_sulfur_bubble", sulfur_bubble, 4, -28.0)],
    ("delve_deep_hum", deep_hum, -23.0, 2),
    ("delve_deep_seep", deep_seep, -23.0, 2),
    ("amb_world_hollows_a", hollows_shallow, -29.0, 1),
    ("amb_world_hollows_b", hollows_body, -29.0, 1),
    ("amb_world_hollows_c", hollows_deep, -30.0, 1),
]

# event, subtitle, file stem, variants (0: one file, no suffix), attenuation distance, stream
EVENTS = [
    ("delve.cave.drip", "Water drips", "amb_cave_drip", 6, 16, False),
    ("delve.cave.drip_pool", "A drop falls into a pool", "amb_cave_drip_pool", 4, 16, False),
    ("delve.cave.drip_acid", "Acid drips and sizzles", "amb_cave_drip_acid", 3, 16, False),
    ("delve.cave.settle", "Stone settles", "delve/settle", 4, 32, False),
    ("delve.cave.far_water", "Water runs somewhere in the dark", "amb_cave_far_water", 0, 32, False),
    ("delve.cave.breath", "Air breathes in from the way out", "delve/cave_breath", 0, 24, False),
    ("delve.cave.sulfur_vent", "Gas hisses from the rock", "amb_cave_sulfur_vent", 3, 16, False),
    ("delve.cave.sulfur_bubble", "An acid pool bubbles", "amb_cave_sulfur_bubble", 4, 16, False),
    ("delve.deep.hum", "The deep hums", "delve_deep_hum", 0, 16, True),
    ("delve.deep.seep", "Something in the deep hums back", "delve_deep_seep", 0, 16, True),
    ("delve.band.room", "The cave's air hums around you", "delve/band_room", 0, 16, True),
    ("delve.cave.answers", "The cave answers", "delve/cave_answers", 0, 48, False),
]


def main(argv):
    only = None
    if "--only" in argv:
        only = set(argv[argv.index("--only") + 1].split(","))
    if "--json" not in argv:
        os.makedirs(SOUNDS, exist_ok=True)
        for name, fn, rms, ch in FILES:
            stem = name.rsplit("_", 1)[0] if name[-1].isdigit() else name
            if only is not None and name not in only and stem not in only:
                continue
            cat = "amb" if name.startswith("amb_") else "sfx"
            tgt, tol, ceil, q = TARGETS[cat]
            assert abs(rms - tgt) <= tol, name
            x = fn(sum(map(ord, name)))
            x = K.mono(x) if ch == 1 else K.stereo(x)
            y = master_loop(x, rms, ceil - 0.7) if name in LOOPS else K.master(x, rms, ceil - 0.7)
            path = os.path.join(SOUNDS, name + ".ogg")
            K.write_ogg(path, y, q, None)
            print(f"  {name:28s} {len(y) / K.SR:6.2f} s  {ch} ch  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds, lang = OrderedDict(), OrderedDict()
    for event, subtitle, stem, count, att, stream in EVENTS:
        files = [stem] if count == 0 else [f"{stem}_{k + 1}" for k in range(count)]
        entries = []
        for f in files:
            e = OrderedDict(name="awaken:" + f)
            e["attenuation_distance"] = att
            if stream:
                e["stream"] = True
            entries.append(e)
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=entries)
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} delve.cave.* / delve.deep.* in merge/delve_sense")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
