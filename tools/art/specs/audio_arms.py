"""Arms' sounds (SPEC §24, docs/combat/PLAN.md §4.8): the feel of steel. Hybrid by design: CC0 recordings (Kenney's
Impact Sounds and RPG Audio, tools/art/sources/kenney, CC0 1.0) give the physical transients and bodies that
synthesis renders thin; the audio kit (tools/art/audiokit.py, and the Ritual's proven helpers in ritual_sounds.py)
gives the air, the steel's ring, the low weight, the shimmer, the rooms and the tells' own language. Every event is
built in layers (a transient, a body for the material, a tail, a sweetener) and has seeded variants so a fight never
repeats one sample.

Mono (OpenAL only places mono sources). Mastered to the arms loudness of audio_mix.py, calibrated against vanilla's
own combat sounds (its strong attacks sit at -11..-14 dBFS gated RMS, its shield block near -9.5): at AWAKEN's
general -20 the blows would sound weaker than a wooden sword. Written to src/main/resources/assets/awaken/sounds/arms/
and registered in src/main/merge/arms/assets/awaken/ (sounds.json, subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_arms.py            render every file, check them, write the JSON
    python3 tools/art/specs/audio_arms.py --json     only the JSON
    python3 tools/art/specs/audio_arms.py --check    only the checks, on what is on disk
    python3 tools/art/specs/audio_arms.py hit_ swish only these (name prefixes)

The checks (run on every build, and by hand): loudness and true peak in the arms window; a blow's transient lands in
its first 6 ms (so the sound meets the hit-stop's frame); no click at either end; variants differ (a spectral
distance, so the round-robin never sounds like one sample); nothing harsher than its own family allows in 2-5 kHz; the
low end held under the body so laptop speakers don't fold. They catch a broken sound, not a dull one: a listener's
ear is the final gate (docs/combat/listening-*.html, keep / remake / cut).
Deterministic: every random source is seeded. build(out) is a no-op so build_all.py (textures) does not render audio.
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
sys.path.insert(0, ART)
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
import ritual_sounds as R  # noqa: E402  (helpers only: boom, crack, thud, sweep, glue, Bus, master)
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/arms")
MERGE = os.path.join(ROOT, "src/main/merge/arms/assets/awaken")
SRC = os.path.join(ART, "sources/kenney")
SR = K.SR

# an open field and a gate's stone room: blows sit in a place, never in a box
R.ROOMS.setdefault("field", dict(t60=0.45, predelay=0.006, bright=0.5, width=0.0, early=0.75, size=0.35, seed=411))
R.ROOMS.setdefault("stone", dict(t60=1.3, predelay=0.014, bright=0.42, width=0.0, early=0.6, size=0.7, seed=412, low_mult=1.15))

_CACHE = {}


# ---- the recordings ------------------------------------------------------------------------------------------------

def rec(name):
    """A CC0 recording, mono, its lead-in trimmed to 1 ms before the transient, peak 1."""
    if name not in _CACHE:
        path = None
        for sub in ("impact-sounds/Audio", "rpg-audio/Audio"):
            p = os.path.join(SRC, sub, name + ".ogg")
            if os.path.exists(p):
                path = p
        if path is None:
            raise FileNotFoundError(f"CC0 source {name} (tools/art/sources/kenney)")
        x = K.read_audio(path)
        x = K.mono(x) if x.ndim > 1 else x
        x = x - np.mean(x[:64]) if len(x) > 64 else x
        pk = np.max(np.abs(x)) + 1e-12
        on = int(np.argmax(np.abs(x) > pk * 0.12))
        x = x[max(0, on - K.ns(0.001)):]
        _CACHE[name] = K.declick(x / (np.max(np.abs(x)) + 1e-12), 0.0004, 0.006)
    return _CACHE[name]


def shift(x, ratio):
    """Pitch by resampling (ratio > 1 higher and shorter): how a recorded blow is made bigger or smaller."""
    n = int(len(x) / ratio)
    if n < 2:
        return x[:1]
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def pick(names, r):
    return names[int(r.integers(0, len(names)))]


def trim(x, sec):
    return R.fade_out(x[:K.ns(sec)], min(sec * 0.4, 0.08))


# ---- the steel ----------------------------------------------------------------------------------------------------

STEEL = (1.0, 2.756, 5.404, 8.933, 13.34)  # a free bar's modes: inharmonic, so it rings like a blade, not a bell


def ring(f0, t60, dur, seed, bright=0.6):
    """A blade's ring: damped inharmonic modes, the upper ones shorter, split pairs beating slowly."""
    n = K.ns(dur)
    amps = [1.0, 0.55 * bright + 0.2, 0.35 * bright, 0.22 * bright, 0.12 * bright]
    t60s = [t60, t60 * 0.62, t60 * 0.4, t60 * 0.26, t60 * 0.17]
    return R.pk(K.modal(f0, STEEL, amps, t60s, n, attack=0.0004, seed=seed, detune_cents=4.0, pair=0.35))


def scrape(dur, seed, fc=3800):
    """Steel sliding on steel: a bright grainy band, rough in time."""
    n = K.ns(dur)
    grain = K.smooth_noise(n, 140.0, seed) * 0.5 + 0.5
    return R.unit(K.bandpass(K.noise(n, "white", seed + 1), fc, 2.2)) * grain * K.perc(n, 0.002, dur * 0.7)


def spark(dur, seed):
    """Sparks off the edges: a crackle of tiny bright ticks."""
    return R.unit(K.highpass(K.crackle(K.ns(dur), 260.0, seed, 0.95, 0.85), 2500, 2)) * K.perc(K.ns(dur), 0.0005, dur * 0.5)


def sub(dur, f0, f1, t60, drive=1.6):
    """The weight under a blow, saturated a little so small speakers still feel it (R.boom)."""
    return R.boom(dur, f0, f1, t60, tau=0.04, drive=drive, attack=0.002)


# ---- the air ------------------------------------------------------------------------------------------------------

def swish(dur, passby, lo, hi, q, seed, tone=0.0, tone_f=900.0, edge=0.25):
    """A blade through the air: a band of noise whose centre rises to the pass (the blade closest, moving fastest)
    and falls after it, its level shaped the same way; a thin edge band an octave and a half up; with tone > 0 the
    whistle of a thin blade."""
    n = K.ns(dur)
    t = K.tvec(n)
    width = max(0.03, passby * 0.55)
    bell = np.exp(-((t - passby) / width) ** 2)
    rise = np.clip(t / max(1e-3, passby), 0, 1) ** 2.2
    env = np.where(t < passby, rise, np.exp(-(t - passby) / (width * 0.9))) * (0.25 + 0.75 * bell)
    cen = lambda s: lo + (hi - lo) * float(np.exp(-((s - passby) / width) ** 2))  # noqa: E731
    body = R.unit(R.sweep(K.noise(n, "pink", seed), cen, q)) * env
    edgeb = R.unit(R.sweep(K.noise(n, "white", seed + 1), lambda s: 2.8 * cen(s), q * 1.6)) * env ** 1.4
    out = body + edge * edgeb
    if tone > 0:
        f = tone_f * (0.92 + 0.16 * bell)  # a little doppler through the pass
        out += tone * R.unit(K.sine(f, n)) * env ** 2.0 * 0.35
    return R.fade_out(out, 0.03)


def cloth(dur, seed, r):
    """The arm and sleeve moving with the swing (a CC0 cloth recording, low and short)."""
    x = rec(pick(["cloth1", "cloth2", "cloth3", "cloth4"], r))
    return trim(K.lowpass(shift(x, r.uniform(0.92, 1.1)), 3500, 2), dur)


# ---- events -------------------------------------------------------------------------------------------------------
# Each takes a seed and returns mono audio. Timings: a blow's contact is at 0 (the file starts on the hit-stop frame);
# a swish's pass is where the tell says (the client starts it on the press, and the blade passes on the active tick).

def swish_light(seed):
    """A longsword's quick strike: a clean, fast pass with the faint whistle of the fuller."""
    r = K.rng(seed)
    pass_t = r.uniform(0.085, 0.11)  # the active tick is 2 ticks (0.1 s) after the press
    x = swish(0.32, pass_t, r.uniform(380, 460), r.uniform(1400, 1800), 1.4, seed, tone=0.45, tone_f=r.uniform(780, 980))
    b = R.Bus(0.45)
    b.add(x, 0.0, 1.0, "field", 0.12)
    b.add(cloth(0.18, seed + 3, r), 0.0, 0.12)
    return b.render()


def swish_heavy(seed):
    """A heavy's rising cut: slower, deeper, more air moved, a thrum under it."""
    r = K.rng(seed)
    pass_t = r.uniform(0.13, 0.16)
    x = swish(0.55, pass_t, r.uniform(180, 230), r.uniform(800, 1000), 1.1, seed, tone=0.3, tone_f=r.uniform(420, 520), edge=0.18)
    n = K.ns(0.55)
    thrum = R.unit(K.lowpass(K.noise(n, "brown", seed + 4), 160, 2)) * np.exp(-((K.tvec(n) - pass_t) / 0.08) ** 2)
    b = R.Bus(0.7)
    b.add(x, 0.0, 1.0, "field", 0.15)
    b.add(thrum, 0.0, 0.35)
    b.add(cloth(0.3, seed + 3, r), 0.0, 0.16)
    return b.render()


def swish_thrust(seed):
    """A thrust: no arc, a short sharp hiss forward and a snap of the sleeve."""
    r = K.rng(seed)
    x = swish(0.24, r.uniform(0.07, 0.09), 520, r.uniform(1500, 1850), 1.5, seed, tone=0.25, tone_f=1100, edge=0.22)
    b = R.Bus(0.35)
    b.add(x, 0.0, 1.0, "field", 0.1)
    b.add(cloth(0.12, seed + 3, r), 0.03, 0.15)
    return b.render()


def _blow(layers, dur, room="field", wet=0.18, drive=1.5):
    """Stacks a blow's layers at their offsets, glued so they land as one (R.glue), in its room."""
    hit = np.zeros(K.ns(dur))
    for sig, t, g in layers:
        R.put(hit, sig, t, g)
    b = R.Bus(dur + (0.35 if room == "stone" else 0.15))
    b.add(R.glue(hit, drive), 0.0, 1.0, room, wet, hp=110)
    return R.fade_out(b.render(), 0.12)


def hit_flesh(seed, heavy=False):
    """Steel into a body: the slice's edge, the meat of the blow, the weight under it."""
    r = K.rng(seed)
    slice_ = K.highpass(rec(pick(["knifeSlice", "knifeSlice2"], r)), 1800, 2)
    punch = rec(pick(["impactPunch_medium_000", "impactPunch_medium_001", "impactPunch_medium_003", "impactPunch_heavy_001",
                      "impactPunch_heavy_003"], r))
    soft = rec(pick(["impactSoft_medium_000", "impactSoft_medium_001", "impactSoft_medium_002", "impactSoft_heavy_000"], r))
    k = 0.82 if heavy else r.uniform(0.95, 1.08)
    layers = [
        (trim(shift(slice_, r.uniform(1.0, 1.15)), 0.12), 0.0, 0.55),
        (trim(shift(punch, k), 0.3), 0.001, 1.0),
        (trim(K.lowpass(shift(soft, k * 0.9), 900, 2), 0.25), 0.002, 0.8),
        (sub(0.22, 110 if heavy else 140, 48, 0.12 if heavy else 0.07), 0.0, 0.7 if heavy else 0.45),
    ]
    return _blow(layers, 0.42 if heavy else 0.32, drive=1.8 if heavy else 1.5)


def hit_blunt(seed):
    """A maul's head into anything: no edge, all weight. A deep punch through the body, a dull crunch of what gives
    under it, the ground's answer in the sub; the blunt family the Maul plays for every body but a spirit."""
    r = K.rng(seed)
    punch = rec(pick(["impactPunch_heavy_001", "impactPunch_heavy_002", "impactPunch_heavy_003", "impactPunch_heavy_004"], r))
    soft = rec(pick(["impactSoft_heavy_000", "impactSoft_heavy_002", "impactSoft_heavy_004"], r))
    layers = [
        (trim(shift(punch, r.uniform(0.72, 0.82)), 0.34), 0.0, 1.0),
        (trim(K.lowpass(shift(soft, r.uniform(0.7, 0.8)), 700, 2), 0.3), 0.002, 0.9),
        (R.crack(seed + 3, r.uniform(0.35, 0.5), 0.25), 0.004, 0.35),
        (sub(0.32, 95, 38, 0.14, drive=1.9), 0.0, 0.85),
    ]
    return _blow(layers, 0.5, drive=1.9)


def hit_flesh_heavy(seed):
    return hit_flesh(seed, True)


def hit_armor(seed):
    """Steel on plate: a hard clank, a short dull ring of the armour, a scrape as the edge skids off."""
    r = K.rng(seed)
    clank = rec(pick(["impactPlate_heavy_001", "impactPlate_heavy_003", "impactMetal_heavy_000", "impactMetal_heavy_004"], r))
    plate = rec(pick(["impactPlate_medium_003", "impactPlate_medium_000", "impactPlate_medium_004"], r))
    layers = [
        (trim(shift(clank, r.uniform(0.9, 1.05)), 0.35), 0.0, 1.0),
        (trim(K.lowpass(shift(plate, r.uniform(0.8, 0.9)), 2500, 2), 0.4), 0.002, 0.45),
        (ring(r.uniform(610, 760), 0.22, 0.4, seed + 2, 0.45) * K.perc(K.ns(0.4), 0.0005, 0.25), 0.001, 0.3),
        (scrape(0.12, seed + 3, r.uniform(3200, 4200)), 0.012, 0.22),
        (sub(0.16, 120, 60, 0.05), 0.0, 0.35),
    ]
    return _blow(layers, 0.45)


def hit_bone(seed):
    """Steel through bone (skeletons): a dry crack, a hollow knock, splinters."""
    r = K.rng(seed)
    knock = rec(pick(["impactWood_light_000", "impactWood_light_002", "impactWood_light_004"], r))
    layers = [
        (R.crack(seed + 1, r.uniform(0.6, 0.9), 0.3), 0.0, 0.9),
        (trim(shift(knock, r.uniform(1.25, 1.45)), 0.18), 0.001, 0.8),
        (R.debris(0.25, 6, 0.01, 0.12, seed + 2, 1.2, 2.2), 0.004, 0.25),
        (sub(0.1, 150, 80, 0.035), 0.0, 0.3),
    ]
    return _blow(layers, 0.32, drive=1.4)


def hit_wood(seed):
    """Steel into wood (the Root Saint, creakings, shields): a solid chunk and a split."""
    r = K.rng(seed)
    chunk = rec(pick(["impactWood_medium_000", "impactWood_medium_002", "impactWood_heavy_001", "impactPlank_medium_001"], r))
    layers = [
        (trim(shift(chunk, r.uniform(0.92, 1.05)), 0.32), 0.0, 1.0),
        (trim(K.highpass(rec("chop"), 900, 2), 0.2), 0.001, 0.5),
        (R.crack(seed + 2, 1.1, 0.25), 0.006, 0.35),
    ]
    return _blow(layers, 0.4)


def hit_stone(seed):
    """Steel on stone and golem-iron: a hard blunt strike, grit, the low answer of something heavy."""
    r = K.rng(seed)
    rock = rec(pick(["impactMining_000", "impactMining_001", "impactMining_003", "impactGeneric_light_002"], r))
    layers = [
        (trim(shift(rock, r.uniform(0.85, 0.95)), 0.35), 0.0, 1.0),
        (trim(shift(rec(pick(["impactMetal_medium_001", "impactMetal_medium_004"], r)), 0.85), 0.2), 0.0, 0.45),
        (R.debris(0.4, 10, 0.01, 0.25, seed + 2, 0.9, 2.0), 0.005, 0.3),
        (sub(0.3, 95, 40, 0.12), 0.0, 0.7),
    ]
    return _blow(layers, 0.5, "stone", 0.2, drive=1.7)


def hit_spirit(seed):
    """Steel through a spirit: no flesh, a cold glassy shear and a breath drawn backwards."""
    r = K.rng(seed)
    n = K.ns(0.6)
    breath = R.unit(K.bandpass(K.noise(n, "pink", seed + 3), 1400, 0.9)) * R.bp(n, [(0, 0), (0.18, 1.0), (0.22, 0.2), (0.6, 0)])
    layers = [
        (trim(shift(rec(pick(["impactGlass_light_000", "impactGlass_light_002", "impactGlass_light_004"], r)), r.uniform(0.75, 0.9)), 0.3), 0.0, 0.8),
        (K.glass(r.uniform(1300, 1700), K.ns(0.6), 0.8, 0.9, seed + 2, 0.9), 0.002, 0.45),
        (breath[::-1].copy(), 0.0, 0.2),
    ]
    return _blow(layers, 0.6, "stone", 0.3, drive=1.2)


def hit_slime(seed):
    """Steel into slime: a wet slap and a squelch that closes behind the blade."""
    r = K.rng(seed)
    n = K.ns(0.35)
    t = K.tvec(n)
    squelch = R.unit(R.sweep(K.noise(n, "pink", seed + 1), lambda s: 300 + 900 * np.exp(-s / 0.06), 3.0)) * K.perc(n, 0.002, 0.18)
    layers = [
        (trim(K.lowpass(shift(rec(pick(["impactSoft_medium_001", "impactSoft_medium_003"], r)), 1.2), 1600, 2), 0.2), 0.0, 1.0),
        (squelch, 0.004, 0.6),
        (sub(0.12, 160, 90, 0.05) * (0.5 + 0.5 * np.cos(t[:K.ns(0.12)] * 60)), 0.0, 0.25),
    ]
    return _blow(layers, 0.35, drive=1.3)


def deflect(seed):
    """A deflect: blade meets blade at the perfect moment. A hard bright strike, the steel singing clear for most of
    a second, sparks, and a rising shimmer that says you did it right."""
    r = K.rng(seed)
    strike = rec(pick(["impactMetal_light_001", "impactMetal_light_002", "impactMetal_light_004"], r))
    f0 = r.uniform(1150, 1320)
    n = K.ns(1.1)
    shimmer = K.glass(f0 * 2.0, n, 0.9, 1.2, seed + 5, 0.6) * R.bp(n, [(0, 0), (0.04, 0.6), (0.2, 1.0), (1.1, 0)])
    layers = [
        (trim(shift(strike, r.uniform(0.95, 1.05)), 0.3), 0.0, 1.0),
        (ring(f0, 0.75, 1.1, seed + 2, 0.75), 0.0, 0.65),
        (ring(f0 * 1.497, 0.5, 1.0, seed + 3, 0.5), 0.003, 0.3),  # the second blade, a fifth up
        (spark(0.25, seed + 4), 0.002, 0.35),
        (shimmer, 0.0, 0.18),
        (sub(0.12, 180, 90, 0.04, 1.2), 0.0, 0.3),
    ]
    return _blow(layers, 1.1, "field", 0.25, drive=1.35)


def block(seed):
    """A block: the guard takes the blow. Heavy, dull, the weapon shuddering in your grip; no song."""
    r = K.rng(seed)
    hit = rec(pick(["impactPlate_heavy_000", "impactPlate_heavy_002", "impactPlate_heavy_004"], r))
    layers = [
        (trim(shift(hit, r.uniform(0.78, 0.86)), 0.35), 0.0, 1.0),
        (ring(r.uniform(420, 520), 0.12, 0.3, seed + 2, 0.3) * K.perc(K.ns(0.3), 0.0005, 0.12), 0.0, 0.3),
        (trim(K.lowpass(rec(pick(["impactSoft_heavy_001", "impactSoft_heavy_003"], r)), 700, 2), 0.25), 0.0, 0.6),
        (sub(0.2, 120, 55, 0.07), 0.0, 0.55),
    ]
    return _blow(layers, 0.4, drive=1.6)


def guard_break(seed):
    """A guard broken by a heavy: a crushing clank, the blade wrenched aside (a scrape), a sick detuned ring."""
    r = K.rng(seed)
    n = K.ns(0.9)
    wob = ring(r.uniform(330, 380), 0.5, 0.9, seed + 2, 0.6) * (1 + 0.25 * np.sin(2 * np.pi * 7.0 * K.tvec(n)))
    layers = [
        (trim(shift(rec(pick(["impactMetal_heavy_001", "impactMetal_heavy_003"], r)), 0.72), 0.45), 0.0, 1.0),
        (trim(shift(rec("impactPlate_heavy_001"), 0.7), 0.4), 0.004, 0.6),
        (scrape(0.3, seed + 3, 2600), 0.03, 0.4),
        (wob, 0.0, 0.3),
        (sub(0.4, 90, 38, 0.15, 2.0), 0.0, 0.85),
    ]
    return _blow(layers, 0.9, "field", 0.22, drive=1.9)


def posture_break(seed):
    """A stance breaking (BREAK): something structural gives. A deep fracture, glass-like shards of the guard, a low
    drop that hangs for a beat."""
    r = K.rng(seed)
    n = K.ns(1.3)
    layers = [
        (R.crack(seed + 1, 1.6, 0.5), 0.0, 1.0),
        (trim(shift(rec(pick(["impactGlass_heavy_000", "impactGlass_heavy_001", "impactGlass_heavy_004"], r)), 0.8), 0.45), 0.003, 0.75),
        (sub(0.9, 75, 28, 0.45, 2.2), 0.0, 0.95),
        (R.debris(0.8, 14, 0.02, 0.5, seed + 3, 1.0, 1.8), 0.01, 0.3),
        (K.glass(r.uniform(520, 600), n, 0.5, 1.0, seed + 4, 0.8), 0.0, 0.15),
    ]
    return _blow(layers, 1.3, "stone", 0.28, drive=1.8)


def execution(seed):
    """An Execution on a broken foe: the biggest blow there is. The cut, the body's whole weight, the steel ringing
    out over it, the ground answering."""
    r = K.rng(seed)
    n = K.ns(1.8)
    tail = ring(r.uniform(560, 640), 1.1, 1.8, seed + 2, 0.55)
    layers = [
        (trim(K.highpass(rec("knifeSlice2"), 1500, 2), 0.15), 0.0, 0.7),
        (trim(shift(rec("impactPunch_heavy_002"), 0.7), 0.5), 0.001, 1.0),
        (trim(shift(rec("impactSoft_heavy_002"), 0.75), 0.5), 0.002, 0.9),
        (R.boom(1.4, 70, 26, 0.7, tau=0.06, drive=2.2, attack=0.003), 0.0, 1.0),
        (tail, 0.01, 0.3),
        (R.debris(1.2, 16, 0.03, 0.8, seed + 3, 0.8, 1.6), 0.02, 0.3),
    ]
    return _blow(layers, 1.8, "stone", 0.3, drive=2.0)


def art(seed):
    """The Art: the blade's gathered light let go. The steel's ring swelling for the wind-up's fifth of a second,
    then the great swish and the weight of it, and a wave of air rolling out across the ground with a long bright
    ring over it (the wave travels; the sound goes with it)."""
    r = K.rng(seed)
    n = K.ns(1.9)
    t = K.tvec(n)
    rel = 0.2  # the move's wind-up (4 ticks): the release lands here
    f0 = r.uniform(1750, 1950)
    swell = np.clip(t / rel, 0, 1) ** 2.2 * (t < rel + 0.02)
    shimmer = (ring(f0, 1.2, 1.9, seed + 1, 0.8) + 0.5 * K.glass(f0 * 1.5, n, 0.8, 0.9, seed + 2, 0.6)) * swell
    cut = swish(0.7, 0.12, r.uniform(170, 210), r.uniform(900, 1100), 1.0, seed + 3, tone=0.25, tone_f=r.uniform(380, 460), edge=0.2)
    wave = R.sweep(K.noise(K.ns(1.4), "pink", seed + 4), lambda u: 2400 * np.exp(-u * 2.2) + 220, 1.4)
    wave = R.unit(wave) * np.exp(-K.tvec(K.ns(1.4)) * 2.4) * np.clip(K.tvec(K.ns(1.4)) / 0.05, 0, 1)
    b = R.Bus(1.9)
    b.add(shimmer, 0.0, 0.45, "field", 0.2)
    b.add(cut, rel - 0.1, 0.9, "field", 0.15)
    b.add(R.boom(1.2, 62, 28, 0.8, tau=0.07, drive=2.0, attack=0.004), rel, 0.9)
    b.add(wave, rel + 0.02, 0.55, "field", 0.35)
    b.add(ring(f0 * 0.5, 1.4, 1.6, seed + 5, 0.5), rel, 0.22, "field", 0.3)
    return R.fade_out(b.render(), 0.3)


def perfect_dodge(seed):
    """A perfect dodge: the world's breath held. A rush past your ear pulled backwards into silence, a low dip, one
    soft high tone; then the room comes back."""
    r = K.rng(seed)
    n = K.ns(0.9)
    t = K.tvec(n)
    rush = swish(0.5, 0.3, 300, 2400, 1.0, seed + 1, edge=0.4)
    dip = R.unit(K.lowpass(K.noise(n, "brown", seed + 2), 220, 2)) * R.bp(n, [(0, 0), (0.08, 1.0), (0.5, 0.6), (0.9, 0)])
    tone = K.glass(r.uniform(1750, 1900), n, 0.4, 1.4, seed + 3, 0.5) * R.bp(n, [(0, 0), (0.1, 1.0), (0.9, 0)])
    b = R.Bus(1.1)
    b.add(rush[::-1].copy(), 0.0, 0.7, "field", 0.2)
    b.add(dip, 0.0, 0.45)
    b.add(tone, 0.08, 0.35, "field", 0.4)
    b.add(np.sin(2 * np.pi * 55 * t) * R.bp(n, [(0, 0), (0.06, 1.0), (0.6, 0)]), 0.0, 0.25)
    return R.fade_out(b.render(), 0.2)


def dodge(seed):
    """A dodge: a boot scuff, cloth, a short rush of air."""
    r = K.rng(seed)
    b = R.Bus(0.4)
    b.add(trim(shift(rec("footstep_concrete_000"), r.uniform(0.85, 0.95)), 0.12), 0.0, 0.8, "field", 0.1)
    b.add(cloth(0.25, seed + 2, r), 0.005, 0.5)
    b.add(swish(0.28, 0.08, 500, 1600, 1.0, seed + 3, edge=0.3), 0.0, 0.5)
    return R.fade_out(b.render(), 0.08)


def charge(seed):
    """A heavy being held: leather creaking under the grip, a low tension rising, the edge humming."""
    r = K.rng(seed)
    n = K.ns(1.0)
    t = K.tvec(n)
    riser = R.unit(R.sweep(K.noise(n, "pink", seed + 1), lambda s: 200 + 900 * min(1.0, s), 2.0)) * (t / t[-1]) ** 1.5
    hum = R.unit(K.sine(220 + 110 * t, n)) * (t / t[-1]) ** 2 * 0.5
    b = R.Bus(1.1)
    b.add(trim(rec(pick(["creak1", "creak2", "creak3"], r)), 0.5) if _has("creak1") else np.zeros(1), 0.0, 0.35)
    b.add(trim(rec("handleSmallLeather"), 0.4) if _has("handleSmallLeather") else np.zeros(1), 0.05, 0.3)
    b.add(riser, 0.0, 0.5, "field", 0.1)
    b.add(hum, 0.0, 0.25)
    return R.fade_out(b.render(), 0.1)


def glint(seed):
    """The glint: release now. One bright clean ping of steel, short so it never covers the release."""
    r = K.rng(seed)
    f0 = r.uniform(2450, 2650)
    b = R.Bus(0.6)
    b.add(ring(f0, 0.35, 0.5, seed + 1, 0.9), 0.0, 0.8, "field", 0.3)
    b.add(K.glass(f0 * 1.5, K.ns(0.5), 0.9, 0.5, seed + 2, 0.7), 0.0, 0.35)
    return R.fade_out(b.render(), 0.1)


# the tells: each class has its own voice, so a fight can be read by ear (played by the creature, not the player)

def tell_light(seed):
    """LIGHT: a short rising draw and a bright tick: deflect it."""
    b = R.Bus(0.5)
    b.add(swish(0.38, 0.34, 350, 1500, 1.2, seed, edge=0.2)[:K.ns(0.38)], 0.0, 0.6, "field", 0.1)
    b.add(ring(1900, 0.08, 0.15, seed + 1, 0.8), 0.34, 0.5)
    return R.fade_out(b.render(), 0.06)


def tell_heavy(seed):
    """HEAVY: a deep drawn breath and an amber hum swelling: deflect or dodge, don't block."""
    n = K.ns(0.85)
    t = K.tvec(n)
    breath = R.unit(R.sweep(K.noise(n, "pink", seed), lambda s: 250 + 500 * min(1.0, s / 0.8), 1.4)) * (t / t[-1]) ** 1.3
    hum = (R.unit(K.sine(110, n)) + 0.6 * R.unit(K.sine(165, n))) * (t / t[-1]) ** 2.2 * 0.6
    b = R.Bus(1.0)
    b.add(breath, 0.0, 0.7, "field", 0.15)
    b.add(hum, 0.0, 0.5)
    return R.fade_out(b.render(), 0.05)


def tell_perilous(seed):
    """PERILOUS: a sharp metallic shriek, two tones grinding against each other: dodge. Nothing else sounds like it."""
    r = K.rng(seed)
    f0 = r.uniform(1300, 1650)
    n = K.ns(0.6)
    t = K.tvec(n)
    env = R.bp(n, [(0, 0), (0.03, 1.0), (0.45, 0.8), (0.6, 0)])
    a = K.fm(f0, r.uniform(1.35, 1.48), r.uniform(1.8, 2.6), n, index_env=env, amp_env=env, seed=seed)
    bb = K.fm(f0 * 1.0595, 1.0, r.uniform(1.1, 1.7), n, index_env=env, amp_env=env, seed=seed + 1)  # a semitone up: the grind
    b = R.Bus(0.8)
    b.add(R.unit(a + 0.8 * bb) * env, 0.0, 0.8, "field", 0.2)
    b.add(scrape(0.5, seed + 2, 4200), 0.0, 0.25)
    return R.fade_out(b.render(), 0.05)


def tell_grab(seed):
    """GRAB: the creak of reaching hands and leather drawn tight: dodge."""
    r = K.rng(seed)
    b = R.Bus(0.7)
    if _has("creak1"):
        b.add(trim(shift(rec(pick(["creak1", "creak2", "creak3"], r)), 0.8), 0.5), 0.0, 0.8, "field", 0.15)
    if _has("handleSmallLeather2"):
        b.add(trim(rec("handleSmallLeather2"), 0.4), 0.1, 0.5)
    b.add(swish(0.4, 0.35, 250, 700, 1.0, seed + 1, edge=0.1), 0.0, 0.4)
    return R.fade_out(b.render(), 0.05)


def hurt(seed):
    """You, hit: a blunt blow to the body, the breath knocked out (no voice), a low thump you feel."""
    r = K.rng(seed)
    layers = [
        (trim(K.lowpass(shift(rec(pick(["impactPunch_medium_000", "impactPunch_medium_004", "impactPunch_heavy_004"], r)), 0.85), 2400, 2), 0.3), 0.0, 1.0),
        (trim(K.lowpass(rec(pick(["impactSoft_heavy_000", "impactSoft_heavy_004"], r)), 500, 2), 0.3), 0.0, 0.8),
        (cloth(0.2, seed + 2, r), 0.005, 0.3),
        (sub(0.25, 100, 45, 0.09), 0.0, 0.6),
    ]
    return _blow(layers, 0.4, drive=1.6)


def heartbeat(seed):
    """Low health: one slow beat, felt more than heard. The lub (a soft deep thump with a body band small speakers
    carry), the dub a third of a second later, higher and shorter; nothing bright, nothing that sounds like a blow."""
    r = K.rng(seed)
    lag = 0.30 + 0.03 * r.random()
    b = R.Bus(0.75)
    for t, f0, f1, g, band in ((0.0, 62, 38, 1.0, 1.0), (lag, 78, 48, 0.7, 0.75)):
        b.add(sub(0.16, f0, f1, 0.07, drive=1.3), t, g)
        n = K.ns(0.09)
        body = R.unit(K.lowpass(K.noise(n, "pink", seed + int(t * 100)), 210, 2)) * K.perc(n, 0.004, 0.05)
        b.add(body, t + 0.002, 0.35 * band)
    return R.fade_out(b.render(), 0.08)


def _has(name):
    try:
        rec(name)
        return True
    except FileNotFoundError:
        return False


# ---- the kit ------------------------------------------------------------------------------------------------------
# event: (render, variants, rms dBFS in the arms window, attenuation blocks, subtitle, a blow? (transient check))
EVENTS = OrderedDict([
    # event: (render, variants, rms dBFS in the arms window, attenuation blocks, subtitle, a blow? (onset check),
    #         crest dB (peak over body: vanilla's hits sit near 12), event volume (soft events play soft, files stay level))
    ("swish_light", (swish_light, 4, -15.0, 16, "Blade swings", False, 12.0, 0.75)),
    ("swish_heavy", (swish_heavy, 3, -14.0, 18, "Heavy blade swings", False, 12.0, 0.85)),
    ("swish_thrust", (swish_thrust, 3, -15.0, 16, "Blade thrusts", False, 12.0, 0.75)),
    ("hit_flesh", (hit_flesh, 5, -13.0, 18, "Blade strikes", True, 12.0, 1.0)),
    ("hit_flesh_heavy", (hit_flesh_heavy, 3, -12.0, 22, "Heavy blow lands", True, 12.0, 1.0)),
    ("hit_armor", (hit_armor, 4, -13.0, 20, "Blade strikes armour", True, 12.5, 1.0)),
    ("hit_bone", (hit_bone, 4, -13.5, 18, "Blade cracks bone", True, 12.0, 1.0)),
    ("hit_wood", (hit_wood, 3, -13.5, 18, "Blade bites wood", True, 12.0, 1.0)),
    ("hit_stone", (hit_stone, 3, -13.0, 20, "Blade strikes stone", True, 12.0, 1.0)),
    ("hit_spirit", (hit_spirit, 3, -14.5, 18, "Blade shears a spirit", True, 13.0, 1.0)),
    ("hit_slime", (hit_slime, 3, -14.0, 16, "Blade splits slime", True, 12.0, 1.0)),
    ("deflect", (deflect, 4, -13.0, 24, "Blow deflected", True, 13.5, 1.0)),
    ("block", (block, 4, -12.5, 20, "Blow blocked", True, 12.0, 1.0)),
    ("guard_break", (guard_break, 2, -12.0, 24, "Guard broken", True, 12.5, 1.0)),
    ("break", (posture_break, 2, -12.0, 28, "Stance breaks", True, 13.0, 1.0)),
    ("execution", (execution, 2, -11.5, 32, "Execution", True, 13.0, 1.0)),
    ("art", (art, 2, -12.0, 32, "Blade's Art unleashed", False, 13.0, 1.0)),
    ("perfect_dodge", (perfect_dodge, 2, -15.0, 12, "Perfect dodge", False, 13.0, 0.7)),
    ("dodge", (dodge, 3, -15.0, 12, "Dodges", False, 12.0, 0.6)),
    ("charge", (charge, 2, -15.5, 12, "Blade drawn back", False, 12.0, 0.45)),
    ("glint", (glint, 2, -15.0, 16, "Blade glints", False, 14.0, 0.7)),
    ("tell_light", (tell_light, 3, -15.0, 24, "Creature winds up", False, 12.0, 0.8)),
    ("tell_heavy", (tell_heavy, 2, -14.5, 28, "Creature gathers a heavy blow", False, 12.0, 0.9)),
    ("tell_perilous", (tell_perilous, 2, -14.0, 32, "Creature readies a perilous blow", False, 12.0, 1.0)),
    ("tell_grab", (tell_grab, 2, -15.0, 24, "Creature reaches to grab", False, 12.0, 0.85)),
    ("hurt", (hurt, 4, -14.0, 12, "You're hit", True, 12.0, 0.9)),
    ("heartbeat", (heartbeat, 2, -16.0, 8, "Heartbeat pounds", False, 12.0, 0.55)),
    ("hit_blunt", (hit_blunt, 4, -12.0, 22, "Heavy blow crashes home", True, 12.0, 1.0)),
])



def seed_of(event, k):
    return 9100 + list(EVENTS).index(event) * 61 + k * 7


def tame(x, crest_db):
    """Brings a sound's crest (peak over gated RMS) down to crest_db with a soft saturation that keeps the shape of
    the blow: game hits are compressed (vanilla's sit near 12 dB). Without it a sharp transient over a room tail
    leaves the mastering limiter pumping the whole file down."""
    y = x / (np.max(np.abs(x)) + 1e-12)
    for drive in (1.0, 1.4, 1.9, 2.5, 3.2, 4.0, 5.0, 6.5):
        z = np.tanh(drive * y) / np.tanh(drive)
        if -K.gated_rms(z) <= crest_db:
            return z
    return z


def master(x, rms, ceiling):
    """Levels a short blow to its RMS without pumping it. A transient limiter with a fast release (25 ms) takes only
    the spike: the body and the tail keep their level, so a hit stays as loud as its target and keeps its punch.
    (audiokit's mastering limiter releases over 120 ms, longer than half these files, and drags the tail down with
    the spike.) Returns (audio, dB the limiter took at the peak)."""
    y = K.highpass(x, 30.0, 2)
    took = 0.0
    for _ in range(4):
        y = y * K.dbamp(rms - K.gated_rms(y))
        tp = K.true_peak(y)
        if tp <= ceiling:
            break
        took = max(took, tp - ceiling)
        y = K.limit(y, ceiling - 0.4, release=0.025, look=0.0015)
    if K.true_peak(y) > ceiling:
        y = y * K.dbamp(ceiling - K.true_peak(y) - 0.05)
    y = K.declick(y, 0.0008, 0.012)
    # 1.5 ms of silence first: Vorbis rings ahead of a blow that starts on its first sample (a click on every play)
    y = np.concatenate([np.zeros(K.ns(0.0015)), y])
    tail = min(len(y), K.ns(0.006))
    y[-tail:] *= np.linspace(1.0, 0.0, tail) ** 2  # the last samples always reach silence: no click when it ends
    return y, took


def render(event, k):
    fn, _, rms, _, _, _, crest, _ = EVENTS[event]
    tgt, tol, ceil, _ = TARGETS["arms"]
    assert abs(rms - tgt) <= tol, f"{event}: {rms} outside the arms window"
    x = tame(K.mono(fn(seed_of(event, k))), crest)
    return master(x, rms, ceil - 0.7)


# ---- checks -------------------------------------------------------------------------------------------------------

def spectrum(x, bands=48):
    """Log-spaced band energies (dB), the variants' fingerprint."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    s = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    edges = np.geomspace(60, 16000, bands + 1)
    return np.array([10 * np.log10(s[(f >= a) & (f < b)].sum() + 1e-12) for a, b in zip(edges[:-1], edges[1:])])


def check(event, files):
    """Returns the problems with an event's rendered variants (empty: it passes)."""
    _, variants, rms, _, _, blow, _, _ = EVENTS[event]
    tgt, tol, ceil, _ = TARGETS["arms"]
    bad = []
    fps = []
    for path in files:
        x = K.read_audio(path)
        name = os.path.basename(path)
        if x.ndim > 1:
            bad.append(f"{name}: stereo (positional sounds must be mono)")
            x = K.mono(x)
        g = K.gated_rms(x)
        if abs(g - tgt) > tol:
            bad.append(f"{name}: RMS {g:.1f} outside {tgt:.0f}±{tol:.0f}")
        tp = K.true_peak(x)
        if tp > ceil + 0.05:
            bad.append(f"{name}: true peak {tp:.1f} > {ceil:.0f}")
        pk = np.max(np.abs(x)) + 1e-12
        if blow:
            onset = np.argmax(np.abs(x) > pk * 0.5) / SR
            if onset > 0.006:
                bad.append(f"{name}: the blow lands {onset * 1000:.1f} ms in (more than 6 ms after the hit-stop's frame)")
        if np.abs(x[0]) > pk * 0.02 or np.max(np.abs(x[-K.ns(0.005):])) > pk * 0.02:
            bad.append(f"{name}: a click at an end")
        sp = spectrum(x)
        harsh = sp[(np.geomspace(60, 16000, 49)[:-1] >= 2000) & (np.geomspace(60, 16000, 49)[:-1] < 5000)].max() - sp.max()
        if harsh > -1.0 and event not in ("tell_perilous", "deflect", "glint", "hit_armor"):
            bad.append(f"{name}: 2-5 kHz is the loudest band ({harsh:.1f} dB): harsh")
        fps.append(sp)
    if len(files) != variants:
        bad.append(f"{event}: {len(files)} files, {variants} variants expected")
    for i in range(len(fps)):
        for j in range(i + 1, len(fps)):
            d = float(np.sqrt(np.mean((fps[i] - fps[j]) ** 2)))
            if d < 0.8:
                bad.append(f"{event}: variants {i} and {j} are near copies (spectral distance {d:.2f} dB)")
    return bad


def files_of(event):
    _, variants, *_ = EVENTS[event]
    return [os.path.join(SND, f"arms_{event}_{k}.ogg") for k in range(variants)]


# ---- the JSON -----------------------------------------------------------------------------------------------------

def write_json():
    sounds, lang = OrderedDict(), OrderedDict()
    for event, (_, variants, _, att, subtitle, _, _, volume) in EVENTS.items():
        entries = []
        for k in range(variants):
            e = OrderedDict(name=f"awaken:arms/arms_{event}_{k}")
            if volume != 1.0:
                e["volume"] = volume
            e["attenuation_distance"] = att
            entries.append(e)
        sounds["arms." + event] = OrderedDict(subtitle="subtitles.awaken.arms." + event, sounds=entries)
        lang["subtitles.awaken.arms." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    # merge: the file is shared with the weapons' names (arms_weapons.py); only the kit's own subtitles are replaced
    lpath = os.path.join(MERGE, "lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur = OrderedDict((k, v) for k, v in cur.items() if not k.startswith("subtitles.awaken.arms."))
    cur.update(lang)
    with open(lpath, "w") as f:
        json.dump(cur, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/arms")


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    os.makedirs(SND, exist_ok=True)
    if "--json" not in argv and "--check" not in argv:
        for event, (_, variants, *_) in EVENTS.items():
            if only and not any(event.startswith(p) for p in only):
                continue
            for k in range(variants):
                y, took = render(event, k)
                path = os.path.join(SND, f"arms_{event}_{k}.ogg")
                K.write_ogg(path, y, TARGETS["arms"][3], None)
                # the encoder can overshoot a noisy file's peak by a dB: measure what was written, trim, write again
                ceil = TARGETS["arms"][2]
                for _ in range(3):
                    tp = K.true_peak(K.read_audio(path))
                    if tp <= ceil - 0.05:
                        break
                    y = y * K.dbamp(ceil - 0.25 - tp)
                    K.write_ogg(path, y, TARGETS["arms"][3], None)
                print(f"  arms_{event}_{k:<2d} {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}"
                      f"  limiter {took:4.1f} dB  {os.path.getsize(path) // 1024} KB")
    problems = []
    for event in EVENTS:
        if only and not any(event.startswith(p) for p in only):
            continue
        fs = [f for f in files_of(event) if os.path.exists(f)]
        problems += check(event, fs)
    for p in problems:
        print("  CHECK " + p)
    write_json()
    print(f"{len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
