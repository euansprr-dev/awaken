"""The Forge Song's sounds (SPEC §44.4): the hammer on each metal, the fire and bellows, the pull, the quench, the
count-in, the set's melody bell, a cut of hide, a piece set on the Fitting Stand, a set waking, a missed beat.

Built like arms' (audio_arms.py, whose helpers it borrows): a CC0 recording for the physical transient (Kenney's Impact
Sounds and RPG Audio, tools/art/sources/kenney, CC0 1.0), the audio kit for each metal's own ring and the air around
it. Each metal rings in its own way: copper a short warm bar, iron the anvil's bright inharmonic ring, diamond a hard
glassy ping, Deepglass a singing glass tone with a slow beat, netherite a dark heavy ring under a deep thud, starmetal
a shimmering bell with a high halo. Seeded variants so a song never repeats one sample.

Mono, mastered to the arms loudness (audio_mix.py). Written to src/main/resources/assets/awaken/sounds/forge/ and
registered in src/main/merge/armour/assets/awaken/ (sounds.json; subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_forge.py          render everything and write the JSON
    python3 tools/art/specs/audio_forge.py --json   only the JSON
    python3 tools/art/specs/audio_forge.py strike_  only these (name prefixes)
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
import ritual_sounds as R  # noqa: E402
import audio_arms as A  # noqa: E402  (rec, shift, ring, spark, sub)
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/forge")
MERGE = os.path.join(ROOT, "src/main/merge/armour/assets/awaken")
SR = K.SR

_ROOM = None


def room(x, wet=0.18):
    """A smithy's stone room: short, a little warm."""
    global _ROOM
    if _ROOM is None:
        _ROOM = K.reverb_ir(t60=0.75, predelay=0.008, bright=0.45, width=0.0, early=0.65, size=0.45, seed=4401)
    wetx = K.convolve(x, _ROOM)
    wetx = K.mono(wetx) if wetx.ndim > 1 else wetx
    out = np.zeros(max(len(x), len(wetx)))
    out[:len(x)] += x
    out[:len(wetx)] += wetx * wet
    return out


def mix(*parts):
    n = max(len(p) for p, _ in parts)
    out = np.zeros(n)
    for p, g in parts:
        out[:len(p)] += p * g
    return out


def unit(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def sizzle(dur, seed, level=1.0):
    """Hot metal spitting under the hammer: a short bright hiss with crackle."""
    n = K.ns(dur)
    hiss = unit(K.bandpass(K.noise(n, "white", seed), 5200, 1.2)) * K.perc(n, 0.002, dur * 0.6)
    return (hiss * 0.6 + A.spark(dur, seed + 1) * 0.5) * level


# ---- the hammer on each metal --------------------------------------------------------------------------------------

METALS = ["copper", "iron", "diamond", "deepglass", "netherite", "starmetal"]


def strike(metal, k):
    r = K.rng(900 + 37 * k + METALS.index(metal) * 11)
    var = 1.0 + (k - 1) * 0.035
    if metal == "copper":
        t = A.shift(A.rec(A.pick(["impactPlate_medium_000", "impactPlate_medium_003", "impactPlate_medium_004"], r)), 1.1 * var)
        body = A.ring(520 * var, 0.35, 0.6, 11 + k, bright=0.35)
        x = mix((t, 0.9), (body, 0.35), (A.sub(0.25, 110, 70, 0.12), 0.25))
    elif metal == "iron":
        t = A.shift(A.rec(A.pick(["impactMetal_heavy_000", "impactMetal_heavy_001", "impactMetal_heavy_003"], r)), 1.0 * var)
        body = A.ring(880 * var, 1.1, 1.6, 21 + k, bright=0.7)
        x = mix((t, 0.85), (body, 0.45), (A.sub(0.3, 95, 60, 0.14), 0.3), (sizzle(0.35, 31 + k), 0.18))
    elif metal == "diamond":
        t = A.shift(A.rec(A.pick(["impactGlass_heavy_000", "impactGlass_heavy_001", "impactGlass_heavy_004"], r)), 1.15 * var)
        body = R.pk(K.glass(1480 * var, K.ns(1.4), bright=0.8, t60=1.1, seed=41 + k))
        x = mix((t, 0.7), (body, 0.45), (A.ring(740 * var, 0.6, 1.0, 51 + k, 0.5), 0.25), (A.sub(0.25, 100, 70, 0.1), 0.25))
    elif metal == "deepglass":
        t = A.shift(A.rec(A.pick(["impactGlass_light_000", "impactGlass_light_002", "impactGlass_light_004"], r)), 0.9 * var)
        n = K.ns(2.2)
        sing = R.pk(K.glass(660 * var, n, bright=0.5, t60=2.0, seed=61 + k)) + R.pk(K.glass(663.5 * var, n, bright=0.4, t60=2.0, seed=62 + k)) * 0.8
        x = mix((t, 0.6), (unit(sing), 0.55), (A.sub(0.3, 90, 60, 0.14), 0.25))
    elif metal == "netherite":
        t = A.shift(A.rec(A.pick(["impactMetal_heavy_000", "impactMetal_heavy_004", "impactPlate_heavy_002"], r)), 0.78 * var)
        body = A.ring(410 * var, 1.4, 1.9, 71 + k, bright=0.45)
        x = mix((t, 0.9), (body, 0.45), (A.sub(0.45, 70, 42, 0.22, drive=1.9), 0.5), (sizzle(0.45, 81 + k), 0.15))
    else:  # starmetal
        t = A.shift(A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_002", "impactMetal_medium_004"], r)), 0.95 * var)
        halo = R.pk(K.fm_bell(1320 * var, K.ns(2.4), t60=2.0, index=1.8, ratio=2.76, seed=91 + k))
        body = A.ring(990 * var, 1.2, 1.8, 92 + k, bright=0.8)
        x = mix((t, 0.75), (body, 0.4), (halo, 0.3), (A.sub(0.35, 90, 55, 0.16), 0.35), (sizzle(0.3, 93 + k), 0.12))
    return room(x)


# ---- the rest ------------------------------------------------------------------------------------------------------

def bellows(k):
    """Two pumps of the bellows and the fire answering: air in, a roar swelling, air out."""
    dur = 2.4
    n = K.ns(dur)
    out = np.zeros(n)
    for i, at in enumerate((0.0, 1.15)):
        m = K.ns(1.0)
        air = unit(K.lowpass(K.noise(m, "pink", 300 + 10 * k + i), 900, 2)) * K.swell(m, 0.45, 2.2)
        s = K.ns(at)
        out[s:s + m] += air[: n - s] * 0.7
    roar = unit(K.lowpass(K.noise(n, "pink", 330 + k), 420, 2)) * (0.55 + 0.45 * K.swell(n, 0.55, 1.5))
    pops = unit(K.highpass(K.crackle(n, 22.0, 340 + k, 0.5, 0.6), 600, 1)) * 0.35
    return room((out + roar * 0.8 + pops) * K.line(n, [(0, 0), (0.05, 1), (0.85, 1), (1, 0)]), 0.12)


def pull(k):
    """Tongs on the bar and it out of the coals: a scrape, a clink, a breath of heat."""
    t = A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_004"], K.rng(500 + k)))
    sc = A.scrape(0.35, 510 + k, fc=2600)
    whoosh = unit(K.lowpass(K.noise(K.ns(0.6), "pink", 520 + k), 1200, 2)) * K.swell(K.ns(0.6), 0.3, 2.0)
    return room(mix((sc, 0.5), (t, 0.6), (whoosh, 0.35)))


def quench(k):
    """Into the trough: a fierce hiss that boils and dies, a few bubbles."""
    dur = 2.2
    n = K.ns(dur)
    hiss = unit(K.bandpass(K.noise(n, "white", 600 + k), 4200, 0.9)) * K.perc(n, 0.004, 1.4)
    boil = unit(K.bandpass(K.crackle(n, 90.0, 610 + k, 0.4, 0.5), 900, 1.5)) * K.perc(n, 0.05, 1.8) * 0.5
    plunge = A.rec("impactSoft_medium_001") if k % 2 == 0 else A.rec("impactSoft_medium_003")
    return room(mix((hiss, 0.8), (boil, 0.45), (plunge, 0.5)), 0.1)


def count(k):
    """The count-in: a light tap of the hammer on the anvil's horn, the song's pulse."""
    t = A.shift(A.rec("impactMetal_light_002"), 1.35 + 0.05 * k)
    return room(mix((t, 0.55), (A.ring(1760, 0.18, 0.3, 700 + k, 0.4), 0.25)), 0.08)


def note(k):
    """The melody's bell (C5; the game pitches it): a struck bell, clear, a gentle halo."""
    n = K.ns(2.6)
    b = R.pk(K.bell(K.hz(72), n, t60=2.2, bright=0.55, seed=800 + k))
    return room(b, 0.2)


def cut(k):
    """A knife through hide along the line."""
    s = A.rec("knifeSlice" if k % 2 == 0 else "knifeSlice2")
    rub = A.rec(A.pick(["handleSmallLeather", "handleSmallLeather2"], K.rng(810 + k)))
    return room(mix((s, 0.8), (rub, 0.35)), 0.08)


def fit(k):
    """A piece set onto the stand: plate meeting plate, straps settling."""
    t = A.rec(A.pick(["impactPlate_medium_000", "impactPlate_heavy_001"], K.rng(900 + k)))
    cloth = A.rec(A.pick(["cloth1", "cloth2", "cloth3"], K.rng(910 + k)))
    return room(mix((t, 0.7), (cloth, 0.45), (A.sub(0.3, 85, 55, 0.12), 0.3)))


def wake(k):
    """A set stands whole and wakes: a low swell, a rising shimmer, a bell chord blooming, the room ringing."""
    dur = 4.2
    n = K.ns(dur)
    swell = unit(K.lowpass(K.noise(n, "pink", 1000), 260, 2)) * K.line(n, [(0, 0), (0.35, 0.9), (0.6, 0.3), (1, 0)])
    rise = unit(K.bandpass(K.noise(n, "white", 1001), 3000, 1.0)) * K.line(n, [(0, 0), (0.3, 0.6), (0.36, 0), (1, 0)])
    chord = np.zeros(n)
    at = K.ns(1.35)
    for m, g in ((60, 1.0), (67, 0.8), (72, 0.7), (76, 0.5), (84, 0.25)):
        b = R.pk(K.bell(K.hz(m), n - at, t60=3.0, bright=0.5, seed=1010 + m))
        chord[at:] += b * g
    boom = np.zeros(n)
    bm = A.sub(1.6, 60, 34, 0.6, drive=1.8)
    boom[at:at + len(bm)] += bm[: n - at]
    return room(mix((swell, 0.35), (rise, 0.3), (unit(chord), 0.6), (boom, 0.55)), 0.3)


def miss(k):
    """A beat let pass: the hammer glancing dull off cooling metal."""
    t = A.shift(A.rec(A.pick(["impactSoft_heavy_000", "impactSoft_heavy_002"], K.rng(1100 + k))), 0.85)
    return room(mix((t, 0.7), (A.ring(300, 0.15, 0.3, 1110 + k, 0.2), 0.2)), 0.08)


# name: (render(k), variants, attenuation, subtitle)
EVENTS = OrderedDict([
    ("strike_hide", (lambda k: strike("copper", k), 3, 16, "Hammer on hide")),
    ("strike_copper", (lambda k: strike("copper", k), 3, 16, "Hammer on copper")),
    ("strike_iron", (lambda k: strike("iron", k), 3, 20, "Hammer on iron")),
    ("strike_diamond", (lambda k: strike("diamond", k), 3, 20, "Hammer on diamond")),
    ("strike_deepglass", (lambda k: strike("deepglass", k), 3, 20, "Hammer on Deepglass")),
    ("strike_netherite", (lambda k: strike("netherite", k), 3, 24, "Hammer on netherite")),
    ("strike_starmetal", (lambda k: strike("starmetal", k), 3, 24, "Hammer on starmetal")),
    ("bellows", (bellows, 2, 16, "Bellows and fire")),
    ("pull", (pull, 2, 12, "Metal pulled from the fire")),
    ("quench", (quench, 2, 16, "Metal quenched")),
    ("count", (count, 2, 10, "The anvil taps the beat")),
    ("note", (note, 1, 16, "The Forge's bell")),
    ("cut", (cut, 2, 10, "Hide cut")),
    ("fit", (fit, 2, 12, "Armour fitted")),
    ("wake", (wake, 1, 32, "A set wakes")),
    ("miss", (miss, 2, 10, "A missed beat")),
])


def write_json():
    sounds, lang = OrderedDict(), OrderedDict()
    for event, (_, variants, att, subtitle) in EVENTS.items():
        sounds["forge." + event] = OrderedDict(subtitle="subtitles.awaken.forge." + event, sounds=[
            OrderedDict(name=f"awaken:forge/forge_{event}_{k}", attenuation_distance=att) for k in range(variants)])
        lang["subtitles.awaken.forge." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    lpath = os.path.join(MERGE, "lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur = OrderedDict((k, v) for k, v in cur.items() if not k.startswith("subtitles.awaken.forge."))
    cur.update(lang)
    with open(lpath, "w") as f:
        json.dump(cur, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/armour")


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    os.makedirs(SND, exist_ok=True)
    rms, _, ceil, q = TARGETS["arms"]
    if "--json" not in argv:
        for event, (render, variants, *_) in EVENTS.items():
            if only and not any(event.startswith(p) for p in only):
                continue
            for k in range(variants):
                y = render(k)
                y, took = R.master(y, rms - (4.0 if event in ("bellows", "count", "cut", "miss") else 0.0), ceil, 6.0)
                path = os.path.join(SND, f"forge_{event}_{k}.ogg")
                K.write_ogg(path, y, q, None)
                print(f"  forge_{event}_{k}  {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    write_json()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
