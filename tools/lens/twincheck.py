#!/usr/bin/env python3
"""Measurement only (Steady 60, 12 Nov; SPEC §22 "Steady 60"): proof that a composite variant draws the same picture,
on one frame. The chain as written, with its composite run twice from the same inputs: the composite as written into
one target, the variant into another, then lens_twin_diff.fsh marks magenta every pixel where the two differ at all (and
its neighbours, so a mark survives the upscale) over the picture at half brightness. Count the marks with
tools/lens/walkcompare.py (shots named t_*.png).
  lens_probe_<tier>_twincheck          the Tiles twin's composite (lens_composite_lit_tiles)
  lens_probe_<tier>_twincheck_control  the twin without its crisp light near the eye (PROBE_NO_CRISP): a variant that does
                                       change the picture, so the check is seen to find a difference
  lens_probe_<tier>_twincheck_same     the composite as written against itself
  lens_probe_<tier>_twincheck_u, _nodbg  the twin's two halves alone: from the block alone, without the development views
  lens_probe_<tier>_twincheck_nudge    the composite as written with every value one step of its last bit up: what
                                       rounding alone turns in the written picture
  lens_probe_<tier>_twincheck_notlite  the twin against the twin without Lite's paths (two programs, A and B both new)
  lens_probe_<tier>_twincheck_calm     the twin's own composite against its calm one (lens_composite_lit_tiles_calm),
                                       for calm frames (the only ones it draws)
Run from anywhere: tools/lens/twincheck.py"""
import copy, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
WRITTEN = "awaken:post/lens_composite_lit"
VARIANTS = {"twincheck": "awaken:post/lens_composite_lit_tiles", "twincheck_control": "awaken:post/lens_composite_probe_crisp",
            # the composite against itself (a check of the check: two runs of one program must agree everywhere), and the
            # twin's two halves alone (tools/lens/regs.py's variants)
            "twincheck_same": WRITTEN, "twincheck_u": "awaken:post/lens_composite_probe_u",
            "twincheck_nodbg": "awaken:post/lens_composite_probe_nodbg",
            # the written composite with every value nudged one step of its last bit (lens_composite_probe_nudge): the
            # pixels whose answer turns on rounding alone, to set the twin's marks against
            "twincheck_nudge": "awaken:post/lens_composite_probe_nudge",
            # against the twin rather than the composite as written: the twin without Lite's paths (regs.py)
            "twincheck_notlite": ("awaken:post/lens_composite_lit_tiles", "awaken:post/lens_composite_probe_u_nodbg_notlite"),
            "twincheck_calm": ("awaken:post/lens_composite_lit_tiles", "awaken:post/lens_composite_lit_tiles_calm")}

for tier in ("cinematic", "ultra"):
    base = json.loads((FX / f"lens_{tier}.json").read_text())
    for name, shader in VARIANTS.items():
        d = copy.deepcopy(base)
        d["targets"]["awaken:lens_twin_a"] = {}
        d["targets"]["awaken:lens_twin_b"] = {}
        at = next(i for i, p in enumerate(d["passes"]) if p["fragment_shader"] == WRITTEN)
        comp = d["passes"][at]
        assert comp["output"] == "swap"
        a, b = copy.deepcopy(comp), copy.deepcopy(comp)
        if isinstance(shader, tuple):
            a["fragment_shader"], shader = shader
        a["output"] = "awaken:lens_twin_a"
        b["fragment_shader"], b["output"] = shader, "awaken:lens_twin_b"
        diff = {"vertex_shader": comp["vertex_shader"], "fragment_shader": "awaken:post/lens_twin_diff",
                "inputs": [{"sampler_name": "A", "target": "awaken:lens_twin_a"}, {"sampler_name": "B", "target": "awaken:lens_twin_b"}],
                "output": "swap"}
        d["passes"][at:at + 1] = [a, b, diff]
        (FX / f"lens_probe_{tier}_{name}.json").write_text(json.dumps(d, indent=2) + "\n")
print("twin checks written")
