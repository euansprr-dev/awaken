"""Renders the mod's own audio (music cues and tracks, the System's UI family, ambience beds) from
tools/art/specs/audio_*.py, masters each file to its category's loudness, encodes Ogg Vorbis into
src/main/resources/assets/awaken/sounds/, and writes the sound events:

  src/main/merge/audio/assets/awaken/sounds.json      every new event (music.*, ui.*, amb.*)
  src/main/merge/audio/assets/awaken/lang/en_us.json  their subtitles, and the track names the Now Playing toast shows
  src/main/merge/core/assets/awaken/sounds.json       only the five System events it replaces (signal.*, level_up, rank_up)
  data/awaken/worldgen/biome/gate_*.json, stratum_*.json   only their minecraft:audio/background_music attribute

    python3 tools/art/audio_build.py                 everything
    python3 tools/art/audio_build.py ui_ amb_rift    only files whose name starts with one of these (JSON is always written)
    python3 tools/art/audio_build.py --json          only the JSON (and the layered music manifests)
    python3 tools/art/audio_build.py layer_hive      one gate theme's layered stems (mastered as a group)

Then: python3 tools/art/audio_mix.py (loudness report) and python3 tools/art/audio_sheet.py (spectrograms).
"""
import importlib.util
import json
import os
import sys
import time
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge")
BIOMES = os.path.join(ROOT, "src/main/resources/data/awaken/worldgen/biome")
SPECS = ["audio_ui", "audio_amb", "audio_music", "audio_lands", "audio_layers"]


class Snd:
    """One file: assets/awaken/sounds/<name>.ogg, rendered by render(seed) -> mono or stereo float array."""

    def __init__(self, name, render, title=None, rms=None, quality=None, seed=0, mono=False):
        self.name, self.render, self.title, self.seed, self.mono = name, render, title, seed, mono
        cat = name.split("_", 1)[0]
        self.cat = cat if cat in TARGETS else "sfx"
        tgt, tol, ceil, q = TARGETS[self.cat]
        self.rms = tgt if rms is None else rms
        assert abs(self.rms - tgt) <= tol, f"{name}: rms {self.rms} outside its category's window"
        self.ceiling = ceil
        self.quality = q if quality is None else quality


class Evt:
    """One sound event. files: [(file name, weight, volume)], refs: [(event id, weight, volume)] (other events,
    vanilla or ours). attenuation: blocks for positional sounds. folder: which merge folder owns the entry ("audio",
    "core" for the System events it re-voices, or another module's folder, whose files are updated, not replaced)."""

    def __init__(self, id, subtitle, files=(), refs=(), stream=False, attenuation=None, folder="audio", pitch=1.0):
        self.id, self.subtitle, self.files, self.refs = id, subtitle, list(files), list(refs)
        self.stream, self.attenuation, self.folder, self.pitch = stream, attenuation, folder, pitch

    def key(self):
        return "subtitles.awaken." + self.id

    def json(self):
        out = []
        for f in self.files:
            name, weight, vol = (f + (1, 1.0))[:3] if isinstance(f, tuple) else (f, 1, 1.0)
            e = OrderedDict(name="awaken:" + name)
            if vol != 1.0:
                e["volume"] = vol
            if self.pitch != 1.0:
                e["pitch"] = self.pitch
            if weight != 1:
                e["weight"] = weight
            if self.stream:
                e["stream"] = True
            if self.attenuation:
                e["attenuation_distance"] = self.attenuation
            out.append(e)
        for r in self.refs:
            ev, weight, vol = (r + (1, 1.0))[:3] if isinstance(r, tuple) else (r, 1, 1.0)
            e = OrderedDict(name=ev, type="event")
            if vol != 1.0:
                e["volume"] = vol
            if weight != 1:
                e["weight"] = weight
            out.append(e)
        return OrderedDict(subtitle=self.key(), sounds=out)


def load_specs():
    mods = []
    for name in SPECS:
        spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, "specs", name + ".py"))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        mods.append(mod)
    return mods


def render(snd):
    t0 = time.time()
    x = snd.render(snd.seed)
    if snd.mono:
        x = K.mono(x)  # played in the world: OpenAL only places mono sources
    y = K.master(x, snd.rms, snd.ceiling - 0.7)  # room for the encoder's overshoot
    path = os.path.join(SOUNDS, snd.name + ".ogg")
    K.write_ogg(path, y, snd.quality, snd.title)
    kb = os.path.getsize(path) / 1024
    print(f"  {snd.name:34s} {len(y) / K.SR:6.1f} s  {'st' if y.ndim == 2 else 'mo'}  RMS {K.gated_rms(y):6.1f}  "
          f"TP {K.true_peak(y):5.1f}  {kb:7.0f} KB  ({time.time() - t0:.1f} s)")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_events(events, lang_extra):
    audio = OrderedDict()
    lang = OrderedDict()
    others = OrderedDict()  # another module's folder: {folder: (sounds, lang)}, merged into what is already there
    core_path = os.path.join(MERGE, "core/assets/awaken/sounds.json")
    core = json.load(open(core_path), object_pairs_hook=OrderedDict)
    for e in events:
        if e.folder == "core":
            assert e.id in core, f"{e.id} is not an existing core event"
            core[e.id] = e.json()  # keeps its existing subtitle key (core's lang has it)
        elif e.folder == "audio":
            audio[e.id] = e.json()
            lang[e.key()] = e.subtitle
        else:
            snd, lng = others.setdefault(e.folder, (OrderedDict(), OrderedDict()))
            snd[e.id] = e.json()
            lng[e.key()] = e.subtitle
    lang.update(lang_extra)
    write_json(os.path.join(MERGE, "audio/assets/awaken/sounds.json"), audio)
    write_json(os.path.join(MERGE, "audio/assets/awaken/lang/en_us.json"), lang)
    write_json(core_path, core)
    for folder, (snd, lng) in others.items():
        for rel, add in (("assets/awaken/sounds.json", snd), ("assets/awaken/lang/en_us.json", lng)):
            path = os.path.join(MERGE, folder, rel)
            data = json.load(open(path), object_pairs_hook=OrderedDict) if os.path.exists(path) else OrderedDict()
            data.update(add)
            write_json(path, data)
    print(f"events: {len(audio)} in merge/audio, {sum(e.folder == 'core' for e in events)} replaced in merge/core"
          + "".join(f", {len(v[0])} in merge/{k}" for k, v in others.items()))


def patch_biomes(music):
    """Sets only the background_music attribute of the biomes named in music {biome file stem: Music json}."""
    for stem, mus in music.items():
        p = os.path.join(BIOMES, stem + ".json")
        b = json.load(open(p), object_pairs_hook=OrderedDict)
        b["attributes"]["minecraft:audio/background_music"] = mus
        write_json(p, b)
    print(f"biomes: {len(music)} music attributes set")


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    mods = load_specs()
    sounds, events, lang, biomes = [], [], OrderedDict(), OrderedDict()
    for m in mods:
        sounds += m.sounds(Snd)
        events += m.events(Evt)
        lang.update(getattr(m, "LANG", {}))
        biomes.update(getattr(m, "BIOME_MUSIC", {}))
    # groups: files mastered together (the layered music's stems), each with a manifest the game reads
    groups = [g for m in mods for g in getattr(m, "groups", lambda: [])()]
    names = {s.name for s in sounds} | {f for g in groups for f in g.files}
    for e in events:
        for f in e.files:
            n = f[0] if isinstance(f, tuple) else f
            assert n in names, f"{e.id} points at {n}, which no spec renders"
    if "--json" not in argv:
        todo = [s for s in sounds if not only or any(s.name.startswith(o) for o in only)]
        gtodo = [g for g in groups if not only or any(os.path.basename(f).startswith(o) for f in g.files for o in only)]
        print(f"rendering {len(todo)} of {len(sounds)} files, {len(gtodo)} of {len(groups)} groups")
        for s in todo:
            render(s)
        for g in gtodo:
            g.build(K.write_ogg)  # writes its own manifest with what it measured
    else:
        for g in groups:
            g.write_manifest()  # keeps the last measured numbers
    write_events(events, lang)
    patch_biomes(biomes)
    ours = ("music_", "ui_", "amb_")
    stale = [f for f in os.listdir(SOUNDS) if f.endswith(".ogg") and f.startswith(ours) and f[:-4] not in names] \
        if os.path.isdir(SOUNDS) else []
    layers = os.path.join(SOUNDS, "layers")
    stale += ["layers/" + f for f in (os.listdir(layers) if os.path.isdir(layers) else [])
              if f.endswith(".ogg") and "layers/" + f[:-4] not in names]
    if stale:
        print("files no spec renders any more (delete them):", ", ".join(sorted(stale)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
