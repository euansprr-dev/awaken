#!/usr/bin/env python3
"""Writes Lens's measurement chains (docs/perf/LENS-APPLE.md): each the Cinematic or Ultra chain with one group of passes
replaced by awaken:post/lens_null, so the GPU time a window saves against the real chain is what that group costs.
'extra' adds one more full-size pass (into swap, before the composite), the cost of a full-screen pass as such.
Run from the repo root; the chains go to assets/awaken/post_effect/lens_probe_<tier>_<group>.json (dev only, loaded by
the autopilot's `lens chain <id>`)."""
import copy, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
GROUPS = {
    "sunmap": ["awaken:post/lens_sunmap"],
    "light": ["awaken:post/lens_light"],
    "clouds": ["awaken:post/lens_clouds"],
    "prefilter": ["awaken:post/lens_prefilter_lit"],
    "bloom": ["awaken:post/lens_down", "awaken:post/lens_blur"],
    "shafts": ["awaken:post/lens_shafts"],
    "composite": ["awaken:post/lens_composite_lit"],
}

def null_pass(p):
    q = copy.deepcopy(p)
    q["fragment_shader"] = "awaken:post/lens_null"
    return q

for tier in ("cinematic", "ultra"):
    base = json.loads((FX / f"lens_{tier}.json").read_text())
    for group, shaders in GROUPS.items():
        d = copy.deepcopy(base)
        d["passes"] = [null_pass(p) if p["fragment_shader"] in shaders else p for p in d["passes"]]
        (FX / f"lens_probe_{tier}_{group}.json").write_text(json.dumps(d, indent=2) + "\n")
    # the lit composite without one of its parts (lens_composite_probe_<part>.fsh: the same shader with one #define)
    for part in ("light", "clouds", "scatter", "grade"):
        d = copy.deepcopy(base)
        for p in d["passes"]:
            if p["fragment_shader"] == "awaken:post/lens_composite_lit":
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
