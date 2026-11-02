"""The carriers' sounds (SPEC §47.2, "See it coming"): every ranged harm a creature sends travels or is drawn first, and
each carrier sounds as it comes, from where it is (mono, played at the carrier by mobs.client.CarrierView and the shot's
own loop), so you hear a wave rolling at you behind your back, a volley whistling down, a beam lock on. Synthesised from
scratch with tools/art/audiokit.py (no samples), mastered to the sfx loudness of audio_mix.py, written to
src/main/resources/assets/awaken/sounds/fights_*.ogg and registered in src/main/merge/mobs_fights/assets/awaken/
(sounds.json, the subtitles in lang/en_us.json).

    python3 tools/art/specs/audio_fights.py            render the files and write the JSON
    python3 tools/art/specs/audio_fights.py --json     only the JSON

- shot_release: a shot leaves the hand or mouth: a short pressured cough of air with a glassy bite gliding down.
- shot_hum:     the shot in flight (a loop): a whirring tone with a slow beat and a thin airy edge, so you hear where it is.
- volley_throw: pods thrown up: a deep lob with a rising swish.
- volley_fall:  a pod coming down on its ring: a falling whistle over 0.8 s, ending where it lands.
- volley_hit:   the pod lands: a dull thud, grit and a short crackle.
- wave_roll:    a wave rolling over the ground (a loop): low grinding rumble, a rush of grit on top.
- lock:         an aimed thing locks on you: a hard metallic click with a short rising ring after it. Never vanilla's.
- ground_run:   a ground line racing at you (a loop): crackling earth splitting, quick and dry.
- beam_fire:    a beam fires along its locked line: a searing tear with a falling whine.
- pull_draw:    water or wind drawn in (a loop): a rushing suction that swells inward.
- field_hiss:   a field's edge (a loop): soft hiss and slow bubbling.
- growl:        a beast gathers for a told bite: a throat's rattle swelling for half a second, cut as it lunges.
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
MERGE = os.path.join(ROOT, "src/main/merge/mobs_fights/assets/awaken")


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def shot_release(seed):
    L = 0.55
    n = K.ns(L)
    cough = _norm(K.bandpass(K.noise(n, "pink", seed), 900, 0.9)) * K.perc(n, 0.002, 0.18)
    low = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 220, 2)) * K.perc(n, 0.003, 0.25)
    f = K.glide(n, 1900, 520)
    bite = np.sin(2 * np.pi * np.cumsum(f) / K.SR) * K.perc(n, 0.001, 0.32)
    glass = K.glass(1480, n, bright=0.7, t60=0.35, seed=seed + 2)
    x = 0.55 * cough + 0.35 * low + 0.22 * bite + 0.12 * _norm(glass)
    return K.declick(x)


def shot_hum(seed):
    L = 2.0
    n = K.ns(L)
    t = K.tvec(n)
    # two close partials beating slowly (a whirr), a fifth over them, a thin band of air
    tone = np.sin(2 * np.pi * 196 * t) + 0.8 * np.sin(2 * np.pi * 199 * t) + 0.35 * np.sin(2 * np.pi * 294 * t)
    wob = 0.75 + 0.25 * np.sin(2 * np.pi * 6.0 * t)
    air = _norm(K.bandpass(K.noise(n, "white", seed), 2400, 1.4))
    x = 0.6 * _norm(tone) * wob + 0.18 * air
    return K.loop_seamless(x, 0.5)


def volley_throw(seed):
    L = 0.7
    n = K.ns(L)
    thump = K.sub_boom(n, 90, 45, 0.25, seed)
    f = K.glide(n, 300, 1400)
    swish = _norm(K.stft_filter(K.noise(n, "pink", seed + 1), lambda ts, fr: K.bp_resp(300 + 1600 * min(1.0, ts / L), 1.2)(fr)))
    x = 0.7 * _norm(thump) + 0.4 * swish * K.swell(n, 0.35, 2.0) + 0.06 * np.sin(2 * np.pi * np.cumsum(f) / K.SR) * K.swell(n, 0.4, 2.0)
    return K.declick(x)


def volley_fall(seed):
    L = 0.85
    n = K.ns(L)
    f = K.glide(n, 2100, 700)
    whistle = np.sin(2 * np.pi * np.cumsum(f) / K.SR)
    air = _norm(K.stft_filter(K.noise(n, "white", seed), lambda ts, fr: K.bp_resp(2100 - 1400 * min(1.0, ts / L), 3.0)(fr)))
    env = K.swell(n, 0.92, 2.2)
    x = (0.45 * whistle + 0.55 * air) * env
    return K.declick(x)


def volley_hit(seed):
    L = 0.8
    n = K.ns(L)
    thud = K.sub_boom(n, 70, 38, 0.4, seed)
    grit = _norm(K.lowpass(K.noise(n, "pink", seed + 1), 2600, 2)) * K.perc(n, 0.002, 0.3)
    crack = K.crackle(n, 70, seed + 2, 0.7, 0.8) * K.perc(n, 0.004, 0.45)
    x = 0.75 * _norm(thud) + 0.45 * grit + 0.3 * _norm(crack)
    return K.declick(x)


def wave_roll(seed):
    L = 2.4
    n = K.ns(L)
    rumble = _norm(K.lowpass(K.noise(n, "brown", seed), 140, 2)) * (0.8 + 0.2 * K.smooth_noise(n, 3.0, seed + 1))
    grind = _norm(K.bandpass(K.noise(n, "pink", seed + 2), 420, 0.8)) * (0.6 + 0.4 * K.smooth_noise(n, 7.0, seed + 3))
    rush = _norm(K.highpass(K.noise(n, "pink", seed + 4), 1800, 2))
    grit = _norm(K.crackle(n, 45, seed + 5, 0.5, 0.6))
    x = 0.6 * rumble + 0.35 * grind + 0.12 * rush + 0.18 * grit
    return K.loop_seamless(x, 0.6)


def lock(seed):
    L = 0.45
    n = K.ns(L)
    click = _norm(K.highpass(K.noise(n, "white", seed), 3000, 2)) * K.perc(n, 0.0005, 0.02)
    body = K.modal(1150, (1.0, 2.76, 5.4), (1.0, 0.5, 0.25), (0.12, 0.08, 0.05), n, 0.0008, seed + 1)
    f = K.glide(n, 1400, 2300)
    ring = np.sin(2 * np.pi * np.cumsum(f) / K.SR) * K.perc(n, 0.03, 0.3) * K.swell(n, 0.12, 1.0)
    x = 0.6 * click + 0.55 * _norm(body) + 0.28 * ring
    return K.declick(x)


def ground_run(seed):
    L = 1.6
    n = K.ns(L)
    crack = _norm(K.crackle(n, 120, seed, 0.65, 0.85))
    split = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 650, 1.0)) * (0.6 + 0.4 * K.smooth_noise(n, 9.0, seed + 2))
    low = _norm(K.lowpass(K.noise(n, "brown", seed + 3), 120, 2))
    x = 0.5 * crack + 0.35 * split + 0.3 * low
    return K.loop_seamless(x, 0.4)


def beam_fire(seed):
    L = 1.0
    n = K.ns(L)
    tear = _norm(K.bandpass(K.noise(n, "white", seed), 1600, 0.6)) * K.perc(n, 0.004, 0.55)
    f = K.glide(n, 1700, 380)
    whine = np.sin(2 * np.pi * np.cumsum(f) / K.SR) * K.perc(n, 0.01, 0.8)
    saw = K.fm(220, 1.5, 3.0, n, None, K.perc(n, 0.005, 0.6), 0.3, seed + 1)
    boom = K.sub_boom(n, 80, 40, 0.5, seed + 2)
    x = 0.5 * tear + 0.25 * whine + 0.25 * _norm(saw) + 0.45 * _norm(boom)
    return K.declick(x)


def pull_draw(seed):
    L = 2.2
    n = K.ns(L)
    t = K.tvec(n)
    # suction: a rushing band that keeps sliding down (a slow saw of centres, seamless at the loop)
    cen = 900 * 2 ** (-1.2 * ((t * 1.25) % 1.0))
    rush = _norm(K.stft_filter(K.noise(n, "pink", seed), lambda ts, fr: K.bp_resp(900 * 2 ** (-1.2 * ((ts * 1.25) % 1.0)), 1.1)(fr)))
    low = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 160, 2))
    x = 0.65 * rush * (0.7 + 0.3 * np.sin(2 * np.pi * 1.25 * t)) + 0.3 * low
    del cen
    return K.loop_seamless(x, 0.5)


def field_hiss(seed):
    L = 2.5
    n = K.ns(L)
    hiss = _norm(K.lowpass(K.highpass(K.noise(n, "white", seed), 2200, 2), 7000, 2)) * (0.7 + 0.3 * K.smooth_noise(n, 2.0, seed + 1))
    bub = _norm(K.crackle(n, 9, seed + 2, 0.25, 0.4))
    bub = _norm(K.lowpass(bub, 900, 2))
    x = 0.55 * hiss + 0.35 * bub
    return K.loop_seamless(x, 0.6)


def growl(seed):
    L = 0.6
    n = K.ns(L)
    t = K.tvec(n)
    # a throat's rattle: noise chopped at a ragged 30 Hz through two mouth formants, a chest under it, swelling as it gathers
    rate = 30 + 6 * K.smooth_noise(n, 6.0, seed + 3)
    fry = np.maximum(0, np.sin(2 * np.pi * np.cumsum(rate) / K.SR)) ** 3
    src = K.noise(n, "pink", seed) * fry
    f1 = _norm(K.bandpass(src, 320, 1.4))
    f2 = _norm(K.bandpass(src, 850, 1.8))
    chest = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 140, 2)) * fry
    env = np.minimum(1, t / 0.35) ** 1.5 * (1 - np.clip((t - 0.5) / 0.08, 0, 1))
    x = (0.6 * f1 + 0.3 * f2 + 0.35 * chest) * env
    return K.declick(x)


# name, render, gated RMS (dBFS), event, subtitle
SOUNDS_SPEC = [
    ("fights_shot_release", shot_release, -19.0, "mobs.fights.shot_release", "A shot flies"),
    ("fights_shot_hum", shot_hum, -22.0, "mobs.fights.shot_hum", "A shot hums past"),
    ("fights_volley_throw", volley_throw, -19.0, "mobs.fights.volley_throw", "Something is thrown up"),
    ("fights_volley_fall", volley_fall, -21.0, "mobs.fights.volley_fall", "Something falls toward you"),
    ("fights_volley_hit", volley_hit, -18.0, "mobs.fights.volley_hit", "Something lands hard"),
    ("fights_wave_roll", wave_roll, -20.0, "mobs.fights.wave_roll", "A wave rolls over the ground"),
    ("fights_lock", lock, -18.0, "mobs.fights.lock", "It locks on"),
    ("fights_ground_run", ground_run, -20.0, "mobs.fights.ground_run", "The ground splits toward you"),
    ("fights_beam_fire", beam_fire, -18.0, "mobs.fights.beam_fire", "A beam fires"),
    ("fights_pull_draw", pull_draw, -21.0, "mobs.fights.pull_draw", "Something draws you in"),
    ("fights_field_hiss", field_hiss, -23.0, "mobs.fights.field_hiss", "A cloud hisses"),
    ("fights_growl", growl, -19.0, "mobs.fights.growl", "A beast growls"),
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
            print(f"  {name:22s} {len(y) / K.SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    sounds, lang = OrderedDict(), OrderedDict()
    for name, _, _, event, subtitle in SOUNDS_SPEC:
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event,
                                    sounds=[OrderedDict(name="awaken:" + name, stream=False)])
        lang["subtitles.awaken." + event] = subtitle
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/mobs_fights")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
