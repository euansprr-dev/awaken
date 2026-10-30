"""covcheck.py BOSS: the fight kinds Kits.REQUIRED wants, from the built moves and fight JSON."""
import json, os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../../../src/main/resources/data/awaken/kit/")
b = sys.argv[1]
mv = {m["id"]: m for m in json.load(open(R + "moves/" + b + ".json"))["moves"]}
fi = json.load(open(R + "fights/" + b + ".json"))
cov = set()
for ph in fi["phases"]:
    for e in ph["pool"]:
        for mid in [e["move"]] + [x for s in e.get("then", []) for x in s]:
            m = mv[mid]; cov |= set(m.get("kinds", []))
            if m.get("signature"): cov.add("signature")
            if m.get("time", {}).get("hold", 0) > 0: cov.add("delay")
        if any(e.get("then", [])): cov.add("combo")
print(b, "missing", [k for k in ("gap", "combo", "delay", "area", "ranged", "behind", "signature") if k not in cov])
