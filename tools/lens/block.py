#!/usr/bin/env python3
"""Declares Lens's uniform block (shaders/include/lens_block.glsl: LensF[64], LensP[32], LensUse) on every pass of the
Lens chains whose shader reads F() or P(), with zeros: where Tiles' hook doesn't bind the live block, LensUse.x stays 0
and the shaders read their textures as before. Run from the repo root after changing a chain (then tools/lens/probes.py).
Then writes each lit chain's Tiles twin (SPEC §22 "Steady 60"): lens_cinematic_tiles.json and lens_ultra_tiles.json, the
same chain with the composite read from the block alone (lens_composite_lit_tiles.fsh), and before it the calm
composite (lens_composite_lit_tiles_calm.fsh), both into swap: Tiles leaves one of the two out every frame (LensClient
names them by their place in the chain, CALM_AT and FULL_AT, checked here). LensClient runs a twin only while Tiles
binds the live block, so never edit a twin: edit its chain and run this."""
import copy, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
READERS = {f"awaken:post/{n}" for n in ("lens_sunmap", "lens_light", "lens_clouds", "lens_prefilter", "lens_prefilter_lit",
                                         "lens_shafts", "lens_composite", "lens_composite_lit")}
ZERO = [0.0, 0.0, 0.0, 0.0]
# the frame's texels: LensFrame.WIDTH, and LensF's size in lens_block.glsl (checked below)
F_TEXELS = 64
assert f"vec4 LensF[{F_TEXELS}];" in (ROOT / "src/main/resources/assets/awaken/shaders/include/lens_block.glsl").read_text()
assert f"static final int WIDTH = {F_TEXELS};" in (ROOT / "src/main/java/dev/awaken/lens/client/LensFrame.java").read_text()
BLOCK = ([{"name": f"f{i}", "type": "vec4", "value": ZERO} for i in range(F_TEXELS)]
         + [{"name": f"p{i}", "type": "vec4", "value": ZERO} for i in range(32)]
         + [{"name": "use", "type": "vec4", "value": ZERO}])

for tier in ("swift", "lite", "cinematic", "ultra"):
    f = FX / f"lens_{tier}.json"
    d = json.loads(f.read_text())
    n = 0
    for p in d["passes"]:
        # (and any pass that declares it already: Lite's are written by hand, as are the sky's and the lay's)
        if p["fragment_shader"] in READERS or "LensBlock" in p.get("uniforms", {}):
            p.setdefault("uniforms", {})["LensBlock"] = BLOCK
            n += 1
        # the frame's texture as an input says its width
        for i in p.get("inputs", []):
            if i.get("location") == "awaken:lens_frame":
                i["width"] = F_TEXELS
    f.write_text(json.dumps(d, indent=2) + "\n")
    print(f"{tier}: LensBlock on {n} passes")

# the Tiles twins: the lit chains with the composite that reads the block alone, the calm one just before it
JAVA = (ROOT / "src/main/java/dev/awaken/lens/client/LensClient.java").read_text()
CALM_AT, FULL_AT = (int(v) for v in re.search(r"CALM_AT = (\d+), FULL_AT = (\d+)", JAVA).groups())
for tier in ("cinematic", "ultra"):
    d = json.loads((FX / f"lens_{tier}.json").read_text())
    at = [i for i, p in enumerate(d["passes"]) if p["fragment_shader"] == "awaken:post/lens_composite_lit"]
    assert len(at) == 1, f"{tier}: one composite"
    full = d["passes"][at[0]]
    assert full["output"] == "swap"
    full["fragment_shader"] = "awaken:post/lens_composite_lit_tiles"
    calm = copy.deepcopy(full)
    calm["fragment_shader"] = "awaken:post/lens_composite_lit_tiles_calm"
    d["passes"].insert(at[0], calm)
    assert (at[0], at[0] + 1) == (CALM_AT, FULL_AT), f"{tier}: the composites sit at {at[0]} and {at[0] + 1}: set LensClient's CALM_AT, FULL_AT"
    (FX / f"lens_{tier}_tiles.json").write_text(json.dumps(d, indent=2) + "\n")
    print(f"{tier}: twin lens_{tier}_tiles.json (calm composite {CALM_AT}, the twin's own {FULL_AT})")

# Every other Lens chain in the folder (the measuring chains that tools/lens/probes.py, twincheck.py and regs.py write):
# the same block and the frame's width on each, so a change of the block's size reaches them all at once.
swept = 0
for f in sorted(FX.glob("lens_*.json")):
    d = json.loads(f.read_text())
    was = json.dumps(d)
    for p in d["passes"]:
        if "LensBlock" in p.get("uniforms", {}):
            p["uniforms"]["LensBlock"] = BLOCK
        for i in p.get("inputs", []):
            if i.get("location") == "awaken:lens_frame":
                i["width"] = F_TEXELS
    if json.dumps(d) != was:
        f.write_text(json.dumps(d, indent=2) + "\n")
        swept += 1
print(f"the block's size on {swept} other chains")

# A texture input ("location") without its width and height keeps the whole chain from loading: the game logs the
# parse error and draws without Lens (run 43203754646: a Glow input added by hand, and every lit chain was off).
missing = [f"{p.name}: {i['sampler_name']}" for p in sorted(FX.glob("lens_*.json")) for ps in json.loads(p.read_text())["passes"]
           for i in ps.get("inputs", []) if "location" in i and not ("width" in i and "height" in i)]
if missing:
    raise SystemExit("texture inputs without width and height (the chain will not load): " + ", ".join(missing[:8])
                     + (f" and {len(missing) - 8} more: run tools/lens/probes.py after fixing the chain" if len(missing) > 8 else ""))
