"""The wild's plants' two sounds (SPEC §41.2, the Wild Lands stage 1a), synthesised from scratch with tools/art/audiokit.py
(numpy only, seeded); nothing is recorded or copied.

    python3 tools/art/specs/audio_wild_plants.py         render both and write the events and subtitles
    python3 tools/art/specs/audio_wild_plants.py json    only the events and subtitles

  wild_plants.swish  four one-shots (sounds/wild_plants/swish<n>.ogg, mono): long grass, rye or bracken brushing past legs
                     as you push through, a soft breathy sweep of many blades, no crackle. About -32 dBFS gated RMS: under
                     a footstep, so walking through a meadow is a hush, never a noise.
  wild_plants.hiss   one stereo loop (sounds/wild_plants/hiss.ogg, streamed): wind combing a meadow, slow swells of a high
                     airy hiss with blade flutter in it. The client loops it and sets its volume from the wind's strength and
                     the long grass around you; mastered to -30 dBFS and played well under that.
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/wild_plants")
MERGE = os.path.join(ROOT, "src/main/merge/ground/assets/awaken")
SR = K.SR
CEIL = -6.0


def build(out):  # rendered on demand (main), not by build_all
    pass


def swish(k, seed):
    """Blades sliding over cloth and skin: pink noise through a band that sweeps up then down as the legs pass, with a grain
    of tiny blade-flicks riding it. Each variant its own length and sweep."""
    r = np.random.default_rng(seed)
    dur = 0.32 + 0.08 * k + r.uniform(0, 0.06)
    n = K.ns(dur)
    t = np.arange(n) / n
    base = K.noise(n, "pink", seed)
    # the sweep: a band moving 1.4 -> 3.2 -> 2 kHz, done as a few fixed bands crossfaded (a moving filter without a phase mess)
    bands = [K.bandpass(base, fc, 0.9) for fc in (1400, 2200, 3200)]
    w = np.clip(np.stack([1 - 2 * t, 1 - abs(2 * t - 0.5) * 2, 2 * t - 0.4]), 0, 1)
    body = sum(b / (np.std(b) + 1e-12) * wi for b, wi in zip(bands, w))
    env = np.sin(np.pi * np.clip(t, 0, 1)) ** (1.2 + 0.3 * k) * (1 + 0.25 * np.sin(2 * np.pi * (3 + k) * t))
    grain = np.zeros(n)
    for _ in range(int(r.integers(30, 60))):                  # flicks: blades springing back off the leg
        i = int(r.uniform(0.1, 0.95) * n)
        L = int(r.integers(30, 90))
        fc = r.uniform(2500, 6000)
        tt = np.arange(L) / SR
        flick = np.exp(-tt * r.uniform(600, 1200)) * np.sin(2 * np.pi * fc * tt) * r.uniform(0.2, 0.6)
        j = min(n, i + L)
        grain[i:j] += flick[:j - i]
    y = body * env + 2.2 * grain * env
    return K.fft_filter(y, lambda f: K.hp_resp(500, 1)(f) / np.sqrt(1 + (f / 7500) ** 4))


def hiss(seed, dur=12.0, fade=1.5):
    """A meadow under wind, seamless: swells of a high airy hiss, two channels a little apart, with flutter in the gusts."""
    r = np.random.default_rng(seed)
    n, nf = K.ns(dur), K.ns(fade)
    m = n + nf
    t = np.arange(m) / SR
    chans = []
    for c in range(2):
        air = K.fft_filter(K.noise(m, "pink", seed + c * 7), lambda f: K.bp_resp(3600, 0.55)(f) / np.sqrt(1 + (f / 9000) ** 4))
        air /= np.std(air) + 1e-12
        low = K.bandpass(K.noise(m, "pink", seed + 31 + c), 900, 0.8)
        low /= np.std(low) + 1e-12
        # gusts: a few slow swells (periods that fit the loop) and a quicker shiver of blades inside each
        swell = 0.55 + 0.25 * np.sin(2 * np.pi * t * 2 / dur + c * 0.6) + 0.2 * np.sin(2 * np.pi * t * 5 / dur + 1.3 + c)
        shiver = 1 + 0.18 * np.sin(2 * np.pi * t * (7.3 + c * 0.4)) * swell
        chans.append((air * swell * shiver + 0.35 * low * (0.6 + 0.4 * swell)))
    x = np.stack(chans, axis=1)
    # loop: the tail fades into the head
    head = x[:nf].copy()
    ramp = np.linspace(0, 1, nf)[:, None]
    out = x[:n].copy()
    out[:nf] = head * ramp + x[n:n + nf] * (1 - ramp)
    return out


def master(x, rms, declick=True):
    y = K.highpass(x, 60, 2)
    y = y * K.dbamp(rms - K.gated_rms(y))
    if K.true_peak(y) > CEIL - 0.6:
        y = K.limit(y, CEIL - 0.8, release=0.05)
    return K.declick(y, 0.003, 0.005) if declick else y


def render():
    os.makedirs(SOUNDS, exist_ok=True)
    q = TARGETS["sfx"][3]
    for k in range(4):
        p = os.path.join(SOUNDS, f"swish{k + 1}.ogg")
        K.write_ogg(p, master(swish(k, 4100 + 37 * k), -32.0), q, None)
        d = K.read_audio(p)
        print(f"  wild_plants/swish{k + 1}  {len(d) / SR:.2f}s  {K.gated_rms(d):.1f} dBFS  peak {K.true_peak(d):.1f}")
    p = os.path.join(SOUNDS, "hiss.ogg")
    K.write_ogg(p, master(hiss(5200), -30.0, declick=False), TARGETS["amb"][3], None)
    d = K.read_audio(p)
    print(f"  wild_plants/hiss  {len(d) / SR:.2f}s  {K.gated_rms(d):.1f} dBFS  peak {K.true_peak(d):.1f}")


def write_events():
    spath = os.path.join(MERGE, "sounds.json")
    ev = json.load(open(spath)) if os.path.exists(spath) else {}
    lpath = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(lpath)) if os.path.exists(lpath) else {}
    ev["wild_plants.swish"] = {"subtitle": "subtitles.awaken.wild_plants.swish",
                               "sounds": [{"name": f"awaken:wild_plants/swish{k + 1}", "attenuation_distance": 10} for k in range(4)]}
    ev["wild_plants.hiss"] = {"subtitle": "subtitles.awaken.wild_plants.hiss",
                              "sounds": [{"name": "awaken:wild_plants/hiss", "stream": True}]}
    lang["subtitles.awaken.wild_plants.swish"] = "Grass brushes past"
    lang["subtitles.awaken.wild_plants.hiss"] = "Wind in the grass"
    with open(spath, "w") as f:
        json.dump(ev, f, indent=2)
        f.write("\n")
    with open(lpath, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    if not (len(sys.argv) > 1 and sys.argv[1] == "json"):
        render()
    write_events()
