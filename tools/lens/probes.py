#!/usr/bin/env python3
"""Writes Lens's measurement chains (docs/perf/LENS-APPLE.md): each the Cinematic or Ultra chain with one group of passes
replaced by awaken:post/lens_null, so the GPU time a window saves against the real chain is what that group costs.
'extra' adds one more full-size pass (into swap, before the composite), the cost of a full-screen pass as such.
Run from the repo root; the chains go to assets/awaken/post_effect/lens_probe_<tier>_<group>.json (dev only, loaded by
the autopilot's `lens chain <id>`). Since 12 Nov (SPEC §22 "Steady 60") they are made from the Tiles twins
(lens_<tier>_tiles.json, tools/lens/block.py), the chains Cinematic and Ultra run while Tiles binds Lens's block, and each
composite part's shader (lens_composite_probe_<part>.fsh) is the twin's composite with one #define more."""
import copy, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
SHADERS = ROOT / "src/main/resources/assets/awaken/shaders/post"
COMPOSITE = "awaken:post/lens_composite_lit_tiles"
# each part the probes leave out of the composite, by its #define in lens_composite.glsl
PARTS = {"light": "PROBE_NO_LIGHT", "clouds": "PROBE_NO_CLOUDS", "scatter": "PROBE_NO_SCATTER", "grade": "PROBE_NO_GRADE",
         "cover": "PROBE_NO_COVER", "crisp": "PROBE_NO_CRISP", "water": "PROBE_NO_WATER", "air": "PROBE_NO_AIR",
         "air_fog": "PROBE_NO_AIR_FOG", "near_clouds": "PROBE_NO_NEAR_CLOUDS", "under": "PROBE_NO_UNDER"}
TWIN_SRC = (SHADERS / "lens_composite_lit_tiles.fsh").read_text()
for part, define in PARTS.items():
    src = TWIN_SRC.replace("#define LENS_NO_DEBUG\n", f"#define LENS_NO_DEBUG\n#define {define}\n// measurement only (tools/lens/probes.py): the twin's composite without one part\n", 1)
    assert define in src
    (SHADERS / f"lens_composite_probe_{part}.fsh").write_text(src)
GROUPS = {
    # nothing nulled: the real chain under a probe's name, the base the others are read against (a probe chain never
    # takes straight out, so the real chain is not that base)
    "none": [],
    "light": ["awaken:post/lens_light"],
    "clouds": ["awaken:post/lens_clouds"],
    "prefilter": ["awaken:post/lens_prefilter_lit"],
    "bloom": ["awaken:post/lens_down", "awaken:post/lens_blur", "awaken:post/lens_downblur"],
    "shafts": ["awaken:post/lens_shafts"],
    "composite": [COMPOSITE],
}

def null_pass(p):
    q = copy.deepcopy(p)
    q["fragment_shader"] = "awaken:post/lens_null"
    return q

for tier in ("cinematic", "ultra"):
    base = json.loads((FX / f"lens_{tier}_tiles.json").read_text())
    # the twin's own composite alone (a probe chain's name is not the twin's, so Tiles would leave neither out)
    base["passes"] = [p for p in base["passes"] if p["fragment_shader"] != "awaken:post/lens_composite_lit_tiles_calm"]
    for group, shaders in GROUPS.items():
        d = copy.deepcopy(base)
        d["passes"] = [null_pass(p) if p["fragment_shader"] in shaders else p for p in d["passes"]]
        (FX / f"lens_probe_{tier}_{group}.json").write_text(json.dumps(d, indent=2) + "\n")
    # the lit composite without one of its parts (lens_composite_probe_<part>.fsh: the same shader with one #define)
    for part in PARTS:
        d = copy.deepcopy(base)
        for p in d["passes"]:
            if p["fragment_shader"] == COMPOSITE:
                p["fragment_shader"] = f"awaken:post/lens_composite_probe_{part}"
        (FX / f"lens_probe_{tier}_comp_{part}.json").write_text(json.dumps(d, indent=2) + "\n")
    # the final copy back to the main target, as a constant (its load and store stay)
    d = copy.deepcopy(base)
    last = d["passes"][-1]
    assert last["output"] == "minecraft:main"
    d["passes"][-1] = null_pass(last)
    (FX / f"lens_probe_{tier}_final.json").write_text(json.dumps(d, indent=2) + "\n")
    # one more full-size pass: a constant into swap just before the composite overwrites it
    d = copy.deepcopy(base)
    at = next(i for i, p in enumerate(d["passes"]) if p["output"] == "swap")
    d["passes"].insert(at, {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "awaken:post/lens_null", "inputs": [], "output": "swap"})
    (FX / f"lens_probe_{tier}_extra.json").write_text(json.dumps(d, indent=2) + "\n")
    print(tier, "probes written")
