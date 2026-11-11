#!/usr/bin/env python3
"""Measurement only (Steady 60, 12 Nov; SPEC §22 "Steady 60"): what the composite's size costs. The lit composite runs
slower for code it never reaches (tour steady60-perf12-b: leaving out the near clouds' walk saved 2.2 ms where nearly
every pixel leaves it at once, the water 1.5 ms with no water in view), which is what a shader held back by how many
registers it needs looks like: the GPU keeps fewer pixels in flight. These are the variants that say what holds it back.
  u          F() and P() from the uniform block alone (LENS_UNIFORMS): no texture-read fallback beside each value, which
             made every frame and look value a per-pixel register. Valid only while Tiles binds the live block, as it
             does for every awaken:lens_ chain (so these chains draw exactly what the real one does).
  nodbg      the development views left out (LENS_NO_DEBUG)
  u_nodbg    both
  lean       everything off in a calm frame left out too: rain's wet, frost, heat, the motion smear, focus, the tear,
             storms, the old water (LENS_CALM, since 12 Nov the twins' calm composite, lens_composite_lit_tiles_calm).
  u_lean     both
  u_all      every pass of the chain that reads F() or P() from the uniform block alone, the composite as u
  u_all_twin the same with the twin's composite (u_nodbg); u_nodbg_notlite the twin without Lite's paths
  calm       the twins' calm composite; calm_noday the same without the night sky's code (PROBE_NO_NIGHT)
Writes shaders/post/lens_composite_probe_<v>.fsh, shaders/post/<pass>_u.fsh and post_effect/lens_probe_cinematic_<v>.json
(autopilot `lens chain cinematic_comp_<v>` / `cinematic_u_all`). Run from anywhere."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHADERS = ROOT / "src/main/resources/assets/awaken/shaders/post"
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
HEAD = "// MEASUREMENT ONLY, written by tools/lens/regs.py: {what}\n"

def with_defines(src, defines, what):
    lines = src.split("\n")
    # after #version and its extension line
    at = next(i for i, l in enumerate(lines) if l.startswith("#extension")) + 1
    return "\n".join(lines[:at] + [HEAD.format(what=what).rstrip()] + [f"#define {d}" for d in defines] + lines[at:])

COMP = (SHADERS / "lens_composite_lit.fsh").read_text()
VARIANTS = {
    "u": ["LENS_UNIFORMS"],
    "nodbg": ["LENS_NO_DEBUG"],
    "u_nodbg": ["LENS_UNIFORMS", "LENS_NO_DEBUG"],
    "lean": ["LENS_CALM"],
    "u_lean": ["LENS_UNIFORMS", "LENS_CALM"],
    # the twin (u_nodbg) with Lite's paths left out too: the lit chains never run as Lite (LensParams: framed && !lit)
    "u_nodbg_notlite": ["LENS_UNIFORMS", "LENS_NO_DEBUG", "LENS_NOT_LITE"],
    # the calm composite (lens_composite_lit_tiles_calm) in a probe chain, and without the night sky's code (PROBE_NO_NIGHT:
    # what a day-only composite would save; by day `dark` is 0 and that code draws nothing)
    "calm": ["LENS_UNIFORMS", "LENS_NO_DEBUG", "LENS_NOT_LITE", "LENS_CALM"],
    "calm_noday": ["LENS_UNIFORMS", "LENS_NO_DEBUG", "LENS_NOT_LITE", "LENS_CALM", "PROBE_NO_NIGHT"],
}
for v, defs in VARIANTS.items():
    (SHADERS / f"lens_composite_probe_{v}.fsh").write_text(with_defines(COMP, defs, f"the lit composite with {', '.join(defs)}"))

PASSES = ["lens_airsky", "lens_light", "lens_clouds", "lens_prefilter_lit", "lens_shafts"]
for p in PASSES:
    (SHADERS / f"{p}_u.fsh").write_text(with_defines((SHADERS / f"{p}.fsh").read_text(), ["LENS_UNIFORMS"], f"{p}.fsh with LENS_UNIFORMS"))

base = json.loads((FX / "lens_cinematic.json").read_text())
def chain(name, swap):
    d = json.loads(json.dumps(base))
    for p in d["passes"]:
        if p["fragment_shader"] in swap:
            p["fragment_shader"] = swap[p["fragment_shader"]]
    (FX / f"lens_probe_cinematic_{name}.json").write_text(json.dumps(d, indent=2) + "\n")

for v in VARIANTS:
    chain(f"comp_{v}", {"awaken:post/lens_composite_lit": f"awaken:post/lens_composite_probe_{v}"})
every = {f"awaken:post/{p}": f"awaken:post/{p}_u" for p in PASSES}
chain("u_all", {**every, "awaken:post/lens_composite_lit": "awaken:post/lens_composite_probe_u"})
# every pass from the block with the twin's own composite (from the block, no development views)
chain("u_all_twin", {**every, "awaken:post/lens_composite_lit": "awaken:post/lens_composite_lit_tiles"})
print("register variants written:", ", ".join(list(VARIANTS) + ["u_all", "u_all_twin"]))
