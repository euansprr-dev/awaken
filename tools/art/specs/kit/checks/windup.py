"""windup.py BOSS: each blow's wind-up peak distance from rest (the kit's readability check, at least 12), by bone group."""
import sys, importlib, math
sys.path.insert(0, ".")
from kit import core
d = importlib.import_module("kit.bosses." + sys.argv[1]).design()
r = d["rig"]
for mv in d["moves"]:
    if mv.cls == "NONE" or not mv.hit:
        continue
    peak = core.sample(r, core.clip_name(mv), mv.seconds("windup"))
    groups = {}
    for b, e in peak.items():
        g = b.split("_")[0] if b.startswith("a") and b[1:2].isdigit() else "core"
        groups.setdefault(g, []).append(max(abs(v) for v in e["rot"]) / core.DEG)
    print(f"{mv.id:10s} {core.pose_distance(r, peak):5.1f}  n={len(peak)}  " + "  ".join(f"{g} {sum(v)/len(v):.0f}" for g, v in sorted(groups.items())))
