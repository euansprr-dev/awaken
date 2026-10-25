"""Loudness report and normaliser for every .ogg the mod ships (src/main/resources/assets/awaken/sounds/).

    python3 tools/art/audio_mix.py            report; exit 1 if anything is outside its category's target
    python3 tools/art/audio_mix.py --fix      re-encode the files outside their target with the right gain
    python3 tools/art/audio_mix.py --ref      measure a few vanilla sounds from the launcher's assets (calibration)

The layered music's stems (sounds/layers/layer_*.ogg) are parts, not pieces: the music rule applies to what the
player hears, the mix of a context's stems at each intensity level (gains from its manifest,
assets/awaken/music_layers/**.json). The report measures every level's mix; a stem alone only has to stay under the
music's true-peak ceiling. --fix never touches stems (re-render the group: audio_build.py layer_<theme>).

Measures gated RMS (100 ms blocks; silence and tails below -60 dBFS, or 20 dB under the mean, are left out), integrated
loudness (BS.1770 LUFS) and true peak (x4 oversampled). The category comes from the file name prefix. Targets were set
from vanilla's own files (the --ref numbers): its music sits around -16 to -20 dBFS RMS, UI clicks and the level-up
around -23 to -26, block and cave ambience around -28 to -32. Ours sit a little under vanilla in every category:
the mod should never be the loudest thing in the mix.

The report is also written to build/audio-loudness.txt.
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import audiokit as K  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")

# category: (gated RMS target dBFS, tolerance dB, true-peak ceiling dBFS, Vorbis quality for --fix)
TARGETS = {
    "music": (-19.0, 2.0, -1.0, 3),
    "ui": (-24.0, 3.0, -3.0, 5),
    "amb": (-28.0, 3.0, -6.0, 4),
    "sfx": (-20.0, 4.0, -1.0, 4),
    # Arms' blows and steel (tools/art/specs/audio_arms.py): calibrated against vanilla's own combat sounds (its strong
    # attacks -11..-14 dBFS gated RMS, its shield block near -9.5), so a found blade never sounds weaker than wood
    "arms": (-14.0, 3.0, -1.0, 4),
    "layer": (-24.0, 12.0, -1.0, 1),  # a stem alone: any sensible level, the music's ceiling; the mixes are checked below
}
MANIFESTS = os.path.join(ROOT, "src/main/resources/assets/awaken/music_layers")


def category(name):
    for p in ("music", "ui", "amb", "layer", "arms"):
        if name.startswith(p + "_"):
            return p
    return "sfx"


def measure(path):
    x = K.read_audio(path)
    return {
        "file": os.path.relpath(path, SOUNDS),
        "cat": category(os.path.basename(path)),
        "dur": len(x) / K.SR,
        "ch": 1 if x.ndim == 1 else x.shape[1],
        "rms": K.gated_rms(x),
        "lufs": K.loudness(x),
        "tp": K.true_peak(x),
    }, x


def verdict(m):
    tgt, tol, ceil, _ = TARGETS[m["cat"]]
    bad = []
    if abs(m["rms"] - tgt) > tol:
        bad.append(f"RMS {m['rms']:.1f} (target {tgt:.0f}±{tol:.0f})")
    if m["tp"] > ceil + 0.05:
        bad.append(f"peak {m['tp']:.1f} > {ceil:.0f}")
    return bad


def report(fix=False):
    files = sorted(glob.glob(os.path.join(SOUNDS, "**/*.ogg"), recursive=True))
    lines = [f"{'file':44s} {'cat':5s} {'len':>6s} {'ch':>2s} {'RMS':>6s} {'LUFS':>6s} {'TP':>6s}  verdict"]
    worst = 0
    per = {}
    for f in files:
        m, x = measure(f)
        bad = verdict(m)
        if bad and fix and m["cat"] != "layer":
            tgt, tol, ceil, q = TARGETS[m["cat"]]
            y = K.master(x, tgt, ceil)
            K.write_ogg(f, y, q)
            m, x = measure(f)
            bad = verdict(m)
        per.setdefault(m["cat"], []).append(m)
        lines.append(f"{m['file']:44s} {m['cat']:5s} {m['dur']:6.1f} {m['ch']:2d} {m['rms']:6.1f} {m['lufs']:6.1f} "
                     f"{m['tp']:6.1f}  {'ok' if not bad else 'OUT: ' + '; '.join(bad)}")
        worst += bool(bad)
    lines.append("")
    for cat, ms in sorted(per.items()):
        tgt, tol, ceil, _ = TARGETS[cat]
        r = [m["rms"] for m in ms]
        lines.append(f"{cat:5s} {len(ms):3d} files  RMS {min(r):.1f}..{max(r):.1f} (target {tgt:.0f}±{tol:.0f})  "
                     f"max TP {max(m['tp'] for m in ms):.1f} (ceiling {ceil:.0f})  "
                     f"{sum(m['dur'] for m in ms):.0f} s")
    worst += layered_mixes(lines)
    lines.append(f"{len(files)} files, {worst} outside target" if files else "no .ogg files")
    text = "\n".join(lines)
    print(text)
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    with open(os.path.join(ROOT, "build/audio-loudness.txt"), "w") as fh:
        fh.write(text + "\n")
    return 1 if worst else 0


def layered_mixes(lines):
    """Every layered context's mix at every level (the music rule), from the manifests. Returns the failures."""
    tgt, tol, ceil, _ = TARGETS["music"]
    bad = 0
    paths = sorted(glob.glob(os.path.join(MANIFESTS, "**/*.json"), recursive=True))
    if not paths:
        return 0
    lines.append("")
    lines.append(f"layered music: each level's mix (music rule {tgt:.0f}+-{tol:.0f} dBFS RMS, true peak <= {ceil:.0f})")
    for p in paths:
        man = json.load(open(p))
        stems = []
        for s in man["stems"]:
            ns, path = s["file"].split(":", 1)
            stems.append(K.stereo(K.read_audio(os.path.join(ROOT, "src/main/resources/assets", ns, "sounds", path + ".ogg"))))
        n = min(len(x) for x in stems)
        if any(len(x) != man["frames"] for x in stems):
            lines.append(f"  {man['id']}: stems are not all {man['frames']} frames: {[len(x) for x in stems]}")
            bad += 1
        row = []
        for lv in man["levels"]:
            mix = sum(x[:n] * g for x, g in zip(stems, lv["gains"]) if g)
            r, tp = K.gated_rms(mix), K.true_peak(mix)
            ok = abs(r - tgt) <= tol and tp <= ceil + 0.05
            bad += not ok
            row.append(f"{lv['name']} {r:5.1f}/{tp:4.1f}{'' if ok else ' OUT'}")
        lines.append(f"  {man['id']:34s} " + "  ".join(row))
    return bad


def reference():
    base = os.path.expanduser("~/Library/Application Support/minecraft/assets")
    idx_files = sorted(glob.glob(os.path.join(base, "indexes/*.json")))
    if not idx_files:
        print("no launcher assets found")
        return 2
    idx = json.load(open(idx_files[-1]))["objects"]
    for k in ["music/game/swamp/aerie", "music/game/os_piano", "music/game/komorebi", "music/game/ancestry",
              "random/click", "random/levelup", "ui/toast/in", "block/amethyst/resonate1", "block/beacon/ambient",
              "ambient/cave/cave1", "ambient/nether/basalt_deltas/basaltground1", "note/bell"]:
        o = idx.get(f"minecraft/sounds/{k}.ogg")
        if not o:
            continue
        x = K.read_audio(os.path.join(base, "objects", o["hash"][:2], o["hash"]))
        print(f"{k:44s} RMS {K.gated_rms(x):6.1f}  LUFS {K.loudness(x):6.1f}  TP {K.true_peak(x):6.1f}")
    return 0


if __name__ == "__main__":
    if "--ref" in sys.argv:
        sys.exit(reference())
    sys.exit(report(fix="--fix" in sys.argv))
