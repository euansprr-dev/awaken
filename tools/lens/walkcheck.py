#!/usr/bin/env python3
"""Measurement only (Steady 60, 12 Nov; SPEC §22 "The walk's reads"): the light pass with a second walk beside the
first, for proof that a faster walk gives the same answers. Writes shaders/post/lens_light_walkcheck.fsh: today's
lens_light.fsh with the walk as it stood at a given commit (default 5a1f0582b, the walk before the open map) added as
blockRayRef, both walks run for every pixel that walks, and the light buffer replaced by r = 1 where the two differ in
status, exit distance or answer (bit for bit), g = 1 where a pixel walked. Its chain is lens_probe_cinematic_walkcheck
(autopilot `lens chain cinematic_walkcheck`); `lensdebug 5` shows the light buffer, and tools/lens/walkcompare.py counts
red and green in the shots.
Run from anywhere: tools/lens/walkcheck.py [commit]"""
import json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHADERS = ROOT / "src/main/resources/assets/awaken/shaders/post"
FX = ROOT / "src/main/resources/assets/awaken/post_effect"
REL = "src/main/resources/assets/awaken/shaders/post/lens_light.fsh"
commit = sys.argv[1] if len(sys.argv) > 1 else "5a1f0582b"

def walk_of(src):
    start = src.index("float blockRay(vec3 o, vec3 l, out int status, out float tOut) {")
    end = src.index("\n}\n", start) + 3
    return src[start:end]

now = (ROOT / REL).read_text()
old = subprocess.run(["git", "-C", str(ROOT), "show", f"{commit}:{REL}"], capture_output=True, text=True, check=True).stdout
ref = walk_of(old).replace("float blockRay(", "float blockRayRef(", 1)

out = now
# the reference walk and the two flags, just before the walk of today
anchor = walk_of(now)
out = out.replace(anchor, "bool walkDiff = false, walked = false;\n\n" + ref + "\n" + anchor, 1)
call = "    float through = blockRay(o, l, status, tOut);\n"
assert call in out
out = out.replace(call, call + """    {
        int s2;
        float t2;
        float r2 = blockRayRef(o, l, s2, t2);
        walked = true;
        if (s2 != status || floatBitsToUint(t2) != floatBitsToUint(tOut) || floatBitsToUint(r2) != floatBitsToUint(through)) walkDiff = true;
    }
""", 1)
tail = "    fragColor = vec4(clamp(res, 0.0, 1.0), code);\n}"
assert out.rstrip().endswith(tail.rstrip())
out = out.rstrip()[: -len(tail.rstrip())] + "    fragColor = vec4(walkDiff ? 1.0 : 0.0, walked ? 1.0 : 0.0, 0.0, code);\n}\n"
# the early exits before the walk keep their own output; the check wants every pixel's flags
out = out.replace("        fragColor = vec4(clamp(hist.rgb + (noise - 0.5) / 255.0, 0.0, 1.0), code);\n        return;",
                  "        fragColor = vec4(0.0, 0.0, 0.0, code);\n        return;", 1)
out = out.replace("// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22)",
                  "// MEASUREMENT ONLY, written by tools/lens/walkcheck.py from lens_light.fsh: two walks side by side.\n// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22)", 1)
(SHADERS / "lens_light_walkcheck.fsh").write_text(out)

(SHADERS / "lens_light_ref.fsh").write_text(old.replace("// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22)",
    f"// MEASUREMENT ONLY, written by tools/lens/walkcheck.py: lens_light.fsh as it stood at {commit}, for before and after\n// pictures in one run.\n// AWAKEN Lens 2 (Cinematic and Ultra, SPEC §22)", 1))

for name, shader in (("walkcheck", "lens_light_walkcheck"), ("ref", "lens_light_ref")):
    base = json.loads((FX / "lens_cinematic.json").read_text())
    for p in base["passes"]:
        if p["fragment_shader"] == "awaken:post/lens_light":
            p["fragment_shader"] = "awaken:post/" + shader
    (FX / f"lens_probe_cinematic_{name}.json").write_text(json.dumps(base, indent=2) + "\n")
print("walk check and reference light pass written against", commit)
