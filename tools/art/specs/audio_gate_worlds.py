"""The Bell Crypt's sound (docs/SPEC.md §38.9): its five worlds' beds and the live layer's events. Rebuild:

    python3 tools/art/specs/audio_gate_worlds.py            render every file and write the JSON
    python3 tools/art/specs/audio_gate_worlds.py --json     only the JSON

Recorded first. Every bell is one of the three CC0 church and hand bells in tools/art/sources/city (their README has
the provenance), only pitched (by resampling, so the strike and its decay stay the recording's), trimmed, faded and set
in a stone room; there is no oscillator under any bell. Bones, creaks and the catch are Kenney's CC0 impacts and creaks
(tools/art/sources/kenney). Water and moving air come from the caves' own loops already in the mod
(amb_cave_far_water, amb_cave_breath; tools/art/specs/audio_caves.py), and drips are the caves' drips by reference.
The Storm mood's far thunder is vanilla's own thunder by reference (nothing of Mojang's is shipped).

Written to src/main/resources/assets/awaken/sounds/gate_world/ and registered as gate.world.* in
src/main/merge/gates/assets/awaken/ (sounds.json and the lang file: other keys are kept as they are). Loudness by
tools/art/audio_mix.py: beds amb (-28 +- 3 dBFS gated RMS, -6 dBTP), events sfx (-20 +- 4, -1 dBTP).
"""
import hashlib
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
from audio_city import HASHES  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
OUT = os.path.join(SOUNDS, "gate_world")
MERGE = os.path.join(ROOT, "src/main/merge/gates/assets/awaken")
CITY = os.path.join(ROOT, "tools/art/sources/city")
KENNEY = os.path.join(ROOT, "tools/art/sources/kenney")

_CACHE = {}


def bell(src):
    """A recorded bell (3446 the church bell, 0292 the small bell), checked against its hash, mono, DC and rumble gone."""
    if src not in _CACHE:
        path = os.path.join(CITY, src + ".wav")
        assert hashlib.sha256(open(path, "rb").read()).hexdigest() == HASHES[src], path
        x = K.mono(K.read_audio(path))
        x = K.highpass(x - np.mean(x), 45, 2)
        _CACHE[src] = x / (np.max(np.abs(x)) + 1e-12)
    return _CACHE[src]


def clip(pack, name):
    key = pack + "/" + name
    if key not in _CACHE:
        x = K.mono(K.read_audio(os.path.join(KENNEY, pack, "Audio", name + ".ogg")))
        _CACHE[key] = x / (np.max(np.abs(x)) + 1e-12)
    return _CACHE[key]


def ours(name):
    if name not in _CACHE:
        x = K.mono(K.read_audio(os.path.join(SOUNDS, name + ".ogg")))
        _CACHE[name] = x / (np.max(np.abs(x)) + 1e-12)
    return _CACHE[name]


def rate(x, r):
    """Resampled by r: above 1 higher and shorter, below 1 lower and longer (a recording's own pitch shift)."""
    if r == 1:
        return x.copy()
    return np.interp(np.arange(0, len(x) - 1, r), np.arange(len(x)), x)


def trim(x, start=0.0, seconds=None, fade=0.4):
    y = x[K.ns(start):]
    if seconds is not None:
        y = y[:K.ns(seconds)]
    y = y.copy()
    f = min(K.ns(fade), len(y) // 3)
    y[:K.ns(0.002)] *= np.linspace(0, 1, K.ns(0.002))
    if f > 0:
        y[-f:] *= np.linspace(1, 0, f) ** 2
    return y


def at(n, t, seg, gain=1.0):
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m] * gain
    return out


_IR = {}


def room(kind):
    """The stone round the sound: a crypt (close, a long dark tail), the shaft (tall), the field (open, very long)."""
    if kind not in _IR:
        t60, pre, bright, size = {"crypt": (2.4, 0.02, 0.35, 0.8), "shaft": (3.6, 0.04, 0.3, 1.2), "field": (5.0, 0.06, 0.25, 1.4),
                                  "small": (0.9, 0.008, 0.45, 0.5)}[kind]
        _IR[kind] = K.reverb_ir(t60=t60, predelay=pre, bright=bright, width=0.0, early=0.5, size=size, seed=len(kind) * 7 + 3)
    return _IR[kind]


def wet(x, kind, mix):
    w = K.mono(K.convolve(x, room(kind)))
    w = w[:len(x) + K.ns(1.5)]
    y = np.zeros(len(w))
    y[:len(x)] += x
    y += w * mix
    f = K.ns(0.6)
    y[-f:] *= np.linspace(1, 0, f) ** 2
    return y


# ---- events --------------------------------------------------------------------------------------------------------

def strike(v):
    """A hung bell struck by hand: the church bell's strike, a touch higher for the smaller ones (pitch does the rest)."""
    x = trim(rate(bell("3446"), (1.0, 0.92)[v]), 0, 5.5, 1.2)
    return wet(x, "crypt", 0.3)


def handbell(v):
    """A handbell rung where you stand: the small bell, a little lower the second time."""
    x = trim(rate(bell("0292"), (1.0, 0.95)[v]), 0, 3.4, 0.9)
    return wet(x, "crypt", 0.22)


def toll(v):
    """A far toll somewhere above: the church bell well down, its highs lost on the way, a long room round it."""
    x = trim(rate(bell("3446"), (0.62, 0.66)[v]), 0, None, 2.0)
    x = K.lowpass(x, 2600, 2)
    return wet(x, "shaft", 0.5)


def great_bell(v):
    """The Great Bell itself: the church bell an octave down, close and enormous."""
    x = trim(rate(bell("3446"), 0.5), 0, None, 2.5)
    return wet(x, "crypt", 0.4)


def ringer(v):
    """The Ringer's coming: two tolls a hair apart, beating against each other."""
    a = trim(rate(bell("3446"), 0.56), 0, None, 2.0)
    b = trim(rate(bell("3446"), 0.585), 0, None, 2.0)
    n = max(len(a), len(b)) + K.ns(0.08)
    x = at(n, 0, a) + at(n, 0.08, b, 0.9)
    return wet(K.lowpass(x, 2200, 2), "shaft", 0.55)


def hum(v):
    """A bell humming softly as you pass under it: the church bell's tail, the strike left out, low."""
    x = trim(rate(bell("3446"), 0.7), 2.6, 7.5, 2.0)
    i = K.ns(1.2)
    x[:i] *= np.linspace(0, 1, i) ** 2
    return wet(K.lowpass(x, 1400, 2), "crypt", 0.35)


def swing(v):
    """The bell column groans as it starts to swing: old wood and rope creaking slowly, a deep knock in the stone."""
    n = K.ns(4.2)
    x = np.zeros(n)
    for i, (name, r, t, g) in enumerate((("creak1", 0.48, 0.0, 1.0), ("creak2", 0.42, 0.9, 0.9), ("creak3", 0.5, 1.9, 0.8),
                                          ("creak1", 0.38, 2.6, 0.7))):
        x += at(n, t, trim(rate(clip("rpg-audio", name), r), 0, None, 0.3), g)
    x += at(n, 0.15, trim(rate(clip("impact-sounds", "impactMining_001"), 0.4), 0, None, 0.4), 0.55)
    return wet(K.lowpass(x, 3200, 2), "shaft", 0.45)


def flood(v):
    """The water rising: the caves' far water swelling up close, a deep slosh under it."""
    w = ours("amb_cave_far_water")
    n = K.ns(6.0)
    x = w[:n].copy() if len(w) >= n else np.resize(w, n)
    u = np.arange(n) / n
    x *= (0.15 + 0.85 * np.sin(np.pi * np.minimum(1, u * 1.4)) ** 1.5) * np.where(u > 0.8, (1 - u) / 0.2, 1)
    x = K.lowpass(x, 3000, 2) * 0.8
    for t, g in ((0.2, 0.7), (1.4, 0.55), (2.9, 0.45)):
        x += at(n, t, trim(rate(clip("impact-sounds", "impactSoft_heavy_00" + str(int(t * 10) % 5)), 0.55), 0, None, 0.4), g)
    return wet(x, "crypt", 0.35)


def bone_fall(v):
    """Bones coming down: a rattle of dry clacks thinning out, grit with them."""
    rnd = K.rng(41 + v)
    n = K.ns(2.4)
    x = np.zeros(n)
    woods = ["impactWood_light_000", "impactWood_light_002", "impactWood_light_004", "impactPlank_medium_001", "impactWood_medium_000"]
    t = 0.0
    for i in range(30):
        t += rnd.exponential(0.02 + i * 0.004)
        if t > 1.9:
            break
        s = clip("impact-sounds", woods[rnd.integers(len(woods))])
        x += at(n, t, trim(rate(s, rnd.uniform(1.15, 1.7)), 0, 0.35, 0.08), rnd.uniform(0.3, 0.9) * (1 - t / 2.4))
    x += at(n, 0.0, trim(rate(clip("impact-sounds", "impactMining_003"), 0.8), 0, None, 0.3), 0.5)
    return wet(x, "crypt", 0.3)


def catch(v):
    """The shaft gives you back: a soft heavy landing, a high bell's breath over it."""
    n = K.ns(2.6)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactSoft_heavy_002"), 0.85), 0, None, 0.3))
    x += at(n, 0.04, trim(rate(bell("0292"), 1.5), 0, 2.2, 0.8), 0.2)
    return wet(x, "crypt", 0.25)


def found(v):
    """A secret found: three handbells in a chord, one after another."""
    n = K.ns(4.0)
    x = np.zeros(n)
    for i, r in enumerate((1.0, 1.26, 1.5)):
        x += at(n, i * 0.13, trim(rate(bell("0292"), r), 0, 3.5, 1.0), 0.75 - i * 0.12)
    return wet(x, "crypt", 0.3)


def arrive(v):
    """Arriving in one of the Bell Crypt's worlds: a far toll, and a handbell answering it."""
    n = K.ns(7.0)
    x = at(n, 0, K.lowpass(trim(rate(bell("3446"), 0.72), 0, 6.5, 2.5), 3000, 2))
    x += at(n, 0.7, trim(rate(bell("0292"), 1.12), 0, 3.0, 1.0), 0.35)
    return wet(x, "field", 0.4)


# ---- beds ----------------------------------------------------------------------------------------------------------

BED = 50.0  # rendered; the loop crossfades its last 2 s into its start


def tile(x, n, seed, xfade=1.5):
    """A loop of x made n long: copies from random offsets, crossfaded, so it never repeats on the beat."""
    rnd = K.rng(seed)
    out = np.zeros(n)
    k = K.ns(xfade)
    pos = 0
    while pos < n:
        off = rnd.integers(0, max(1, len(x) // 3))
        seg = x[off:].copy()
        seg[:k] *= np.sin(0.5 * np.pi * np.arange(k) / k)
        seg[-k:] *= np.cos(0.5 * np.pi * np.arange(k) / k)
        m = min(len(seg), n - pos)
        out[pos:pos + m] += seg[:m]
        pos += len(seg) - k
    return out


def drone(r, seed):
    """A low drone from the church bell's own hum (its tail, the strike left out), pitched and drawn out."""
    t = trim(rate(bell("3446"), r), 2.4 / r, None, 0.5)
    return K.lowpass(tile(t / (np.max(np.abs(t)) + 1e-12), K.ns(BED), seed, 2.5), 900, 2)


def scatter(n, seed, picks, count, gain, spread=1.0):
    """count one-shots at random times and sides: (L, R)."""
    rnd = K.rng(seed)
    left, right = np.zeros(n), np.zeros(n)
    for i in range(count):
        seg = picks(rnd)
        t = rnd.uniform(0.5, BED - 4)
        p = rnd.uniform(-spread, spread)
        g = gain * rnd.uniform(0.6, 1.0)
        left += at(n, t, seg, g * np.cos((p + 1) * np.pi / 4))
        right += at(n, t, seg, g * np.sin((p + 1) * np.pi / 4))
    return left, right


def drips(rnd):
    name = ["amb_cave_drip_1", "amb_cave_drip_2", "amb_cave_drip_3", "amb_cave_drip_pool_1", "amb_cave_drip_pool_2"][rnd.integers(5)]
    return ours(name)


def far_bell(rnd):
    x = trim(rate(bell("3446"), rnd.uniform(0.75, 1.15)), 0, None, 2.0)
    return K.lowpass(wet(x, "field", 0.7), 1300, 2)


def wide(mono_l, mono_r, *layers):
    x = np.stack([mono_l, mono_r], axis=1)
    for l, r in layers:
        x[:, 0] += l[:len(x)]
        x[:, 1] += r[:len(x)]
    return x


def water(seed, lp=1600):
    """Slow water: the caves' far water, two takes from different places for the two sides, swelling as it laps."""
    w = ours("amb_cave_far_water")
    n = K.ns(BED)
    a, b = tile(w, n, seed), tile(w, n, seed + 1)
    lap = 0.65 + 0.35 * K.smooth_noise(n, 0.25, seed + 2)
    return K.lowpass(a * lap, lp, 2), K.lowpass(b * lap[::-1], lp, 2)


def air(seed, lp=2000):
    w = ours("amb_cave_breath")
    n = K.ns(BED)
    return K.lowpass(tile(w, n, seed), lp, 2), K.lowpass(tile(w, n, seed + 1), lp, 2)


def bed_belfry(v):
    """The Sunken Belfry: slow water round the causeway, the bells' low hum over it, drips off the vault."""
    n = K.ns(BED)
    wl, wr = water(11)
    d = drone(0.5, 12) * 0.5
    return wide(wl + d, wr + d, scatter(n, 13, drips, 9, 0.35))


def bed_ossuary(v):
    """The Ossuary Ways: still dry air in the passages, a drone so low it's felt, bones settling, a drip."""
    n = K.ns(BED)
    al, ar = air(21, 1400)
    d = drone(0.42, 22) * 0.45
    bones = scatter(n, 23, lambda r: trim(rate(clip("impact-sounds", "impactWood_light_00" + str(2 * r.integers(3))), r.uniform(1.3, 1.8)),
                                          0, 0.3, 0.08), 4, 0.12)
    return wide(al * 0.6 + d, ar * 0.6 + d, bones, scatter(n, 24, drips, 4, 0.25))


def bed_shaft(v):
    """The Bell Shaft: air moving up the shaft, the column's deep hum, ropes and timbers creaking far above."""
    n = K.ns(BED)
    al, ar = air(31, 2400)
    d = drone(0.45, 32) * 0.55
    creaks = scatter(n, 33, lambda r: K.lowpass(trim(rate(clip("rpg-audio", "creak" + str(1 + r.integers(3))), r.uniform(0.45, 0.6)), 0, None, 0.3),
                                                1800, 2), 3, 0.18)
    return wide(al * 0.8 + d, ar * 0.8 + d, creaks, scatter(n, 34, drips, 5, 0.22))


def bed_field(v):
    """The Field of Bells: open still water, a wide hum a fifth apart, bells ringing very far off now and then."""
    n = K.ns(BED)
    wl, wr = water(41, 1200)
    lo, hi = drone(0.5, 42) * 0.4, drone(0.75, 43) * 0.25
    return wide(wl * 0.8 + lo + hi * 0.4, wr * 0.8 + lo * 0.8 + hi, scatter(n, 44, far_bell, 3, 0.18))


def bed_rest(v):
    """Bellmaker's Rest: a small warm room, the hum of a bell at rest, the old wood of the bench."""
    n = K.ns(BED)
    al, ar = air(51, 900)
    d = drone(0.6, 52) * 0.35
    creak = scatter(n, 53, lambda r: K.lowpass(trim(rate(clip("rpg-audio", "creak" + str(1 + r.integers(3))), r.uniform(0.8, 1.0)), 0, None, 0.2),
                                               2500, 2), 2, 0.1)
    return wide(al * 0.3 + d, ar * 0.3 + d, creak)


# file, render, variants, rms, event, subtitle, attenuation (events) or 0 (a bed, streamed). Each bell's level is
# set so its strike stays under the ceiling without the limiter touching it (a handbell's crest is 22 dB).
SPEC = [
    ("strike", strike, 2, -21.0, "gate.world.strike", "Bell rings", 24),
    ("handbell", handbell, 2, -24.0, "gate.world.handbell", "Handbell rings", 16),
    ("toll", toll, 2, -21.0, "gate.world.toll", "A far bell tolls", 64),
    ("great_bell", great_bell, 1, -22.0, "gate.world.great_bell", "The Great Bell tolls", 64),
    ("ringer", ringer, 1, -21.0, "gate.world.ringer", "The Ringer's bell", 64),
    ("hum", hum, 1, -23.5, "gate.world.hum", "A bell hums", 32),
    ("swing", swing, 1, -20.0, "gate.world.swing", "The bells groan", 48),
    ("flood", flood, 1, -20.0, "gate.world.flood", "Water rises", 32),
    ("bone_fall", bone_fall, 2, -22.0, "gate.world.bone_fall", "Bones fall", 24),
    ("catch", catch, 1, -21.0, "gate.world.catch", "Caught on the ledge", 16),
    ("found", found, 1, -23.0, "gate.world.found", "A secret found", 16),
    ("arrive", arrive, 1, -22.0, "gate.world.arrive", "", 16),
    ("amb_belfry", bed_belfry, 1, -28.0, "gate.world.belfry", "", 0),
    ("amb_ossuary", bed_ossuary, 1, -29.0, "gate.world.ossuary", "", 0),
    ("amb_shaft", bed_shaft, 1, -28.0, "gate.world.shaft", "", 0),
    ("amb_field", bed_field, 1, -28.5, "gate.world.field", "", 0),
    ("amb_rest", bed_rest, 1, -29.0, "gate.world.rest", "", 0),
]

# events made of sounds already in the pack (ours, or vanilla's own by reference)
REFS = OrderedDict([
    ("gate.world.drip", ("Water drips", [OrderedDict(name="awaken:amb_cave_drip_" + str(i), attenuation_distance=16) for i in (1, 2, 3, 4)]
                         + [OrderedDict(name="awaken:amb_cave_drip_pool_" + str(i), attenuation_distance=16) for i in (1, 2)])),
    ("gate.world.storm", ("Thunder far above", [OrderedDict(name="minecraft:ambient/weather/thunder" + str(i), volume=0.35, pitch=0.7)
                                               for i in (1, 2, 3)])),
])


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    total = 0
    if "--json" not in argv:
        for name, fn, variants, rms, event, _, att in SPEC:
            cat = "amb" if name.startswith("amb_") else "sfx"
            tgt, tol, ceil, q = TARGETS[cat]
            assert abs(rms - tgt) <= tol, name
            for v in range(variants):
                x = fn(v)
                if cat == "sfx":
                    x = K.mono(x)
                y = K.master(x, rms, ceil - 0.7)
                if cat == "amb":
                    y = K.loop_seamless(y, 2.0)
                file = name + ("" if variants == 1 else "_" + str(v + 1))
                path = os.path.join(OUT, file + ".ogg")
                K.write_ogg(path, y, q, None)
                total += os.path.getsize(path)
                print(f"  {file:16s} {len(y) / K.SR:5.1f} s  {1 if y.ndim == 1 else y.shape[1]} ch  RMS {K.gated_rms(y):6.1f}"
                      f"  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
        print(f"  {total / 1e6:.2f} MB in sounds/gate_world")
    sounds_path, lang_path = os.path.join(MERGE, "sounds.json"), os.path.join(MERGE, "lang/en_us.json")
    sounds = json.load(open(sounds_path), object_pairs_hook=OrderedDict)
    lang = json.load(open(lang_path), object_pairs_hook=OrderedDict)
    for name, _, variants, _, event, subtitle, att in SPEC:
        entries = []
        for v in range(variants):
            e = OrderedDict(name="awaken:gate_world/" + name + ("" if variants == 1 else "_" + str(v + 1)))
            if att:
                e["attenuation_distance"] = att
            else:
                e["stream"] = True
            entries.append(e)
        sounds[event] = OrderedDict(sounds=entries)
        if subtitle:
            sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=entries)
            lang["subtitles.awaken." + event] = subtitle
    for event, (subtitle, entries) in REFS.items():
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=entries)
        lang["subtitles.awaken." + event] = subtitle
    with open(sounds_path, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(SPEC) + len(REFS)} gate.world.* in merge/gates")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
