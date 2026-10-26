#!/usr/bin/env python3
"""Declares Lens's uniform block (shaders/include/lens_block.glsl: LensF[48], LensP[32], LensUse) on every pass of the
Lens chains whose shader reads F() or P(), with zeros: where Tiles' hook doesn't bind the live block, LensUse.x stays 0
and the shaders read their textures as before. Run from the repo root after changing a chain (then tools/lens/probes.py)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
READERS = {f"awaken:post/{n}" for n in ("lens_sunmap", "lens_light", "lens_clouds", "lens_prefilter", "lens_prefilter_lit",
                                         "lens_shafts", "lens_composite", "lens_composite_lit")}
ZERO = [0.0, 0.0, 0.0, 0.0]
BLOCK = ([{"name": f"f{i}", "type": "vec4", "value": ZERO} for i in range(48)]
         + [{"name": f"p{i}", "type": "vec4", "value": ZERO} for i in range(32)]
         + [{"name": "use", "type": "vec4", "value": ZERO}])

for tier in ("swift", "cinematic", "ultra"):
    f = FX / f"lens_{tier}.json"
    d = json.loads(f.read_text())
    n = 0
    for p in d["passes"]:
        if p["fragment_shader"] in READERS:
            p.setdefault("uniforms", {})["LensBlock"] = BLOCK
            n += 1
    f.write_text(json.dumps(d, indent=2) + "\n")
    print(f"{tier}: LensBlock on {n} passes")
