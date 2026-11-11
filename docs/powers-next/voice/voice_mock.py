#!/usr/bin/env python3
"""Powers, the next level: sound mock-ups built the way the game would play them (docs/powers-next/PLAN.md, "Sound").

A power's sound is a SCORE: for each beat of the cast (gather, peak, release, impact, tail) a few layers, and each layer is
one of Minecraft's own recordings named the way sounds.json names it, with a volume, a pitch and a start in ticks. That is all
the game can do with a named sound (no filters, no reversing, pitch 0.5 to 2, volume at most 1, starts on the 50 ms tick), so
this mixer does nothing else: what you hear here is what the game would play.

    voice_mock.py probe PATTERN...        length, level, brightness and the ding check for matching vanilla recordings
    voice_mock.py render                  writes the listening page to run-autopilot/powers-next/listen/ (gitignored)

Nothing of Mojang's is written into the repo: the recordings are read from this machine's own game assets and the page stays
local (Euan's rule, 9 Nov). The scores themselves (names and numbers) live in scores.py.
"""
import hashlib
import json
import os
import re
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
import ding_check as D  # noqa: E402

SR = 44100
TICK = 0.05
ASSETS = os.environ.get("MC_ASSETS", "/Volumes/MEDIA/.gradle-home/caches/neoformruntime/assets")
OURS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
OUT = os.path.join(ROOT, "run-autopilot/powers-next/listen")
_INDEX = None
_CACHE = {}


def index():
    global _INDEX
    if _INDEX is None:
        idx = sorted(f for f in os.listdir(os.path.join(ASSETS, "indexes")) if f.endswith(".json"))[-1]
        objs = json.load(open(os.path.join(ASSETS, "indexes", idx)))["objects"]
        _INDEX = {k[len("minecraft/sounds/"):-4]: v["hash"] for k, v in objs.items() if k.startswith("minecraft/sounds/") and k.endswith(".ogg")}
    return _INDEX


def path(name):
    """minecraft:mob/warden/sonic_boom1 -> the file in the local asset store; awaken:powers/x -> the mod's own file."""
    ns, _, rel = name.partition(":")
    if ns == "awaken":
        return os.path.join(OURS, rel + ".ogg")
    h = index()[rel]
    return os.path.join(ASSETS, "objects", h[:2], h)


def load(name):
    if name not in _CACHE:
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path(name), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"], capture_output=True, check=True).stdout
        _CACHE[name] = np.frombuffer(raw, dtype="<f4").astype(float)
    return _CACHE[name]


def variants(name):
    """A name ending in # stands for every numbered take of it, as a sounds.json event lists them."""
    if not name.endswith("#"):
        return [name]
    ns, _, rel = name[:-1].partition(":")
    if ns == "awaken":
        takes = sorted(f[:-4] for f in os.listdir(os.path.dirname(os.path.join(OURS, rel))) if re.fullmatch(re.escape(os.path.basename(rel)) + r"\d+\.ogg", f))
        return [f"awaken:{os.path.dirname(rel)}/{t}" for t in takes]
    takes = sorted(k for k in index() if re.fullmatch(re.escape(rel) + r"_?\d+", k))
    return ["minecraft:" + t for t in takes] or [name[:-1]]


def pitched(x, pitch):
    """The game's pitch is playback speed."""
    pitch = min(2.0, max(0.5, pitch))
    n = int(len(x) / pitch)
    return np.interp(np.arange(n) * pitch, np.arange(len(x)), x)


def mix(score, seed=0, length=None):
    """score: [(name, tick, volume, pitch[, jitter[, max_seconds]])]. Returns mono samples."""
    rng = np.random.default_rng(seed)
    parts = []
    for layer in score:
        name, tick, vol, pitch = layer[:4]
        jitter = layer[4] if len(layer) > 4 else 0.04
        cut = layer[5] if len(layer) > 5 else None
        takes = variants(name)
        x = load(takes[int(rng.integers(len(takes)))])
        x = pitched(x, pitch * (1 + rng.uniform(-jitter, jitter)))
        if cut:
            n = int(cut * SR)
            x = x[:n].copy()
            f = min(len(x), int(0.12 * SR))
            x[-f:] *= np.linspace(1, 0, f)
        parts.append((int(round(tick * TICK * SR)), x * min(1.0, vol)))
    n = max(at + len(x) for at, x in parts)
    if length:
        n = max(n, int(length * SR))
    out = np.zeros(n)
    for at, x in parts:
        out[at:at + len(x)] += x
    return out


def describe(x):
    if len(x) < 64:
        return dict(secs=0, peak=-99, rms=-99, centroid=0, ding=False)
    w = int(0.05 * SR)
    e = np.sqrt(np.convolve(x * x, np.ones(w) / w, "same"))
    S = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    f = np.fft.rfftfreq(len(x), 1 / SR)
    d = D.check(x)
    return dict(secs=len(x) / SR, peak=20 * np.log10(np.max(np.abs(x)) + 1e-9), rms=20 * np.log10(np.max(e) + 1e-9),
                centroid=float(np.sum(f * S) / (np.sum(S) + 1e-9)), ding=bool(d["ding"]), hz=float(d["hz"]))


def encode(x, out_path):
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    y = np.clip(x, -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-", "-codec:a", "libmp3lame", "-q:a", "3", out_path], input=y, check=True)


def probe(patterns):
    for k in sorted(index()):
        if any(re.search(p, k) for p in patterns):
            d = describe(load("minecraft:" + k))
            print(f"{k:58s} {d['secs']:5.2f}s peak {d['peak']:6.1f} loud {d['rms']:6.1f} bright {d['centroid']:6.0f} Hz {'DING %.0f Hz' % d['hz'] if d['ding'] else ''}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "probe":
        probe(sys.argv[2:])
    elif cmd == "render":
        import page
        page.render()
    else:
        raise SystemExit(__doc__)
