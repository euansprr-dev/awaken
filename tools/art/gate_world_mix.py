#!/usr/bin/env python3
"""The gate worlds' steady sound, mixed the way the game mixes it, checked for static (SPEC 38.9, "No static").

    python3 tools/art/gate_world_mix.py                  report every gate biome; exit 1 if one breaks the rule
    python3 tools/art/gate_world_mix.py --out DIR        also write each biome's 40 s mix as DIR/<biome>.ogg
    python3 tools/art/gate_world_mix.py --root TREE      measure another checkout (or an extracted tree)
    python3 tools/art/gate_world_mix.py BIOME ...        only these (e.g. gate_world/pipe_maze_steam)

Per biome: its loop at 1.0 x the sounds.json volume (vanilla's LoopSoundInstance), the floor's WorldBed at 0.55 (the
same world sound again, gates.client.WorldBed), and its additions rolled as vanilla rolls them (tick_chance each tick,
1.0 x the sounds.json volume, a random variant; 15 s of warm start so long ones already overlap). Music and the mood
sound are minutes apart and left out. Vanilla's own sounds come from the launcher's asset index (Gradle's cache).

The rule, against vanilla rain (weather.rain at 0.2, the steady hiss every player knows: -36.5 LUFS, static -45):
static is each moment's 2-8 kHz level (dB re full scale, 85 ms frames) plus how noise-like that moment is (spectral
flatness over 1.5-10 kHz against white noise's 0.5); the 90th percentile moment of the 40 s must be -36 or under, about
9 dB over that rain. A crack or a pop now and then doesn't count; a bed of hiss, or noisy one-shots sounding a
tenth of the time, does. Loudness (LUFS over the 40 s, the loudest 400 ms) is reported, not ruled.
"""
import glob
import json
import math
import os
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
ASSETS = os.environ.get("MC_ASSETS", os.path.join(os.environ.get("GRADLE_USER_HOME", "/Volumes/MEDIA/.gradle-home"),
                                                  "caches/neoformruntime/assets"))
SECONDS = 40
STATIC_MAX = -36.0
BED_GAIN = 0.55  # WorldBed.LEVEL


def vanilla():
    idx = max(glob.glob(ASSETS + "/indexes/*.json"), key=lambda p: int(os.path.basename(p)[:-5]) if os.path.basename(p)[:-5].isdigit() else -1)
    objects = json.load(open(idx))["objects"]
    v = objects["minecraft/sounds.json"]
    return objects, json.load(open(f"{ASSETS}/objects/{v['hash'][:2]}/{v['hash']}"))


class Tree:
    def __init__(self, root):
        self.root = root
        self.objects, vsj = vanilla()
        self.events = {}
        for f in glob.glob(root + "/src/main/merge/*/assets/awaken/sounds.json"):
            for k, v in json.load(open(f)).items():
                self.events["awaken:" + k] = v
        for k, v in vsj.items():
            self.events.setdefault("minecraft:" + k, v)
        self.cache = {}

    def audio(self, ns, name):
        if (ns, name) not in self.cache:
            if ns == "minecraft":
                h = self.objects.get(f"minecraft/sounds/{name}.ogg")
                path = f"{ASSETS}/objects/{h['hash'][:2]}/{h['hash']}" if h else None
            else:
                path = f"{self.root}/src/main/resources/assets/{ns}/sounds/{name}.ogg"
            x = None
            if path and os.path.exists(path):
                x = K.read_audio(path)
                x = x.mean(axis=1) if x.ndim == 2 else x
            self.cache[(ns, name)] = x
        return self.cache[(ns, name)]

    def variants(self, event):
        """(audio, volume, pitch) of each variant of an event, nested events flattened."""
        out = []
        for s in self.events.get(event, {}).get("sounds", []):
            s = {"name": s} if isinstance(s, str) else s
            ns, _, name = s["name"].rpartition(":")
            ns = ns or "minecraft"
            if s.get("type") == "event":
                inner = s["name"] if ":" in s["name"] else "minecraft:" + s["name"]
                out += [(a, v * s.get("volume", 1.0), p * s.get("pitch", 1.0)) for a, v, p in self.variants(inner)]
                continue
            a = self.audio(ns, name)
            if a is not None:
                out.append((a, s.get("volume", 1.0), s.get("pitch", 1.0)))
        return out


def repitch(a, p):
    if abs(p - 1) < 1e-3:
        return a
    return np.interp(np.arange(int(len(a) / p)) * p, np.arange(len(a)), a)


def looped(a, n, off):
    return np.tile(a, int(math.ceil((n + off) / len(a))) + 1)[off:off + n]


def measure(mix):
    lufs = K.loudness(mix)
    ms = np.sum(K.k_weight(K.stereo(mix)) ** 2, axis=1)
    w = K.ns(0.4)
    c = np.cumsum(np.concatenate([[0], ms]))
    mmax = -0.691 + 10 * math.log10(max(np.max((c[w:] - c[:-w]) / w), 1e-12))
    N, H = 4096, 2048
    win = np.hanning(N)
    P = np.abs(np.fft.rfft(np.lib.stride_tricks.sliding_window_view(mix, N)[::H] * win, axis=1)) ** 2 / (win ** 2).sum() / (N / 2)
    f = np.fft.rfftfreq(N, 1 / K.SR)
    band = P[:, (f >= 2000) & (f < 8000)].sum(axis=1)
    Pb = P[:, (f >= 1500) & (f <= 10000)] + 1e-20
    flat = np.exp(np.mean(np.log(Pb), axis=1)) / np.mean(Pb, axis=1)
    s = 10 * np.log10(np.maximum(band, 1e-20)) + 10 * np.log10(np.maximum(flat, 1e-3) / 0.5)
    return round(float(np.percentile(s, 90)), 1), round(lufs, 1), round(mmax, 1)


def beds(root):
    """biome -> the world's own sound, which WorldBed loops on its floor and moods."""
    out = {}
    for f in glob.glob(root + "/src/main/resources/data/awaken/gate_worlds/**/*.json", recursive=True):
        d = json.load(open(f))
        if d.get("sound"):
            for b in [d.get("biome")] + [m.get("biome") for m in d.get("moods", []) if isinstance(m, dict)]:
                if b:
                    out[b.split(":", 1)[-1]] = d["sound"]
    return out


def render(tree, rel, amb, bed, n):
    mix = np.zeros(n)
    rng = np.random.default_rng(zlib.crc32(rel.encode()))
    if "loop" in amb:
        for a, v, p in tree.variants(amb["loop"])[:1]:
            mix += v * looped(repitch(a, p), n, int(rng.integers(0, len(a))))
    if bed:
        for a, v, p in tree.variants(bed)[:1]:
            mix += BED_GAIN * v * looped(repitch(a, p), n, int(rng.integers(0, len(a))))
    adds = amb.get("additions")
    for x in adds if isinstance(adds, list) else [adds] if adds else []:
        vs = tree.variants(x["sound"])
        if not vs:
            continue
        for t in range(-300, int(n / K.SR * 20)):
            if rng.random() < x["tick_chance"]:
                a, v, p = vs[int(rng.integers(0, len(vs)))]
                s = repitch(a, p) * min(1.0, v)
                i0 = int(t / 20 * K.SR)
                j0, j1 = max(0, i0), min(n, i0 + len(s))
                if j1 > j0:
                    mix[j0:j1] += s[j0 - i0:j1 - i0]
    return mix


def main(argv):
    root = argv[argv.index("--root") + 1] if "--root" in argv else ROOT
    out = argv[argv.index("--out") + 1] if "--out" in argv else None
    skip = {argv[argv.index(k) + 1] for k in ("--root", "--out") if k in argv}
    only = {a for a in argv if not a.startswith("--") and a not in skip}
    tree, bed, n = Tree(root), beds(root), SECONDS * K.SR
    base = root + "/src/main/resources/data/awaken/worldgen/biome"
    bad = 0
    for f in sorted(glob.glob(base + "/**/*.json", recursive=True)):
        rel = os.path.relpath(f, base)[:-5]
        if not rel.startswith("gate") or (only and rel not in only):
            continue
        amb = json.load(open(f)).get("attributes", {}).get("minecraft:audio/ambient_sounds", {})
        mix = render(tree, rel, amb, bed.get(rel), n)
        if not np.any(mix):
            continue
        static, lufs, mmax = measure(mix)
        bad += static > STATIC_MAX
        print(f"{rel:44s} static {static:6.1f}  {lufs:6.1f} LUFS  max {mmax:6.1f}  {'STATIC' if static > STATIC_MAX else ''}")
        if out:
            os.makedirs(out, exist_ok=True)
            K.write_ogg(f"{out}/{rel.replace('/', '__')}.ogg", np.clip(mix, -1, 1), quality=5)
    print(f"{bad} biome(s) break the rule (static at the 90th percentile moment <= {STATIC_MAX})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
