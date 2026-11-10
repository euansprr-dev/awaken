"""Disposable: the blade's grip and tip on the screen for a few baked moves, for the approval page's player."""
import json, sys, os
ROOT = sys.argv[1]
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
import numpy as np
import anim_lab as L
import swing_studio as S
swings = L.load_swings(); T = L.table()
picks = [("longsword", "strike_1"), ("longsword", "strike_2"), ("longsword", "strike_3"), ("longsword", "heavy"),
         ("maul", "strike_1"), ("maul", "strike_2"), ("katana", "strike_1"), ("axe", "strike_4")]
out = {}
for sid, mv in picks:
    m = T[sid]["moves"][mv]; clip = swings[sid]["moves"][mv]
    held = L.Held("gatewrought_" + sid)
    ks = []
    for k in clip["keys"]:
        d, R = L.new_pose(sid, mv, k[0], swings)
        tip, grip = S.lead_points(held, clip.get("far"), d, R)
        a, b = S.ndc(grip), S.ndc(tip)
        ks.append([round(float(k[0]), 2), round(float(a[0]), 3), round(float(a[1]), 3), round(float(b[0]), 3), round(float(b[1]), 3),
                   round(float(k[8]), 2), round(float(k[9]), 2), round(float(k[10]), 2)])
    sw = m["swing"]
    out[sid + "." + mv] = dict(keys=ks, windup=m["windup"], active=m["active"], recovery=m["recovery"], hit=clip.get("hit", L.hit_tick(m)),
                               hitstop=m["hitstop"], row=[sw["to"][0] - sw["from"][0], sw["to"][1] - sw["from"][1]], form=sw.get("form"),
                               weight=S.weight_class(sid), bite=[clip.get("bite", [1, 0])[0], clip.get("bite", [1, 0])[1]] if clip.get("bite") else S.bite_of(sid, mv))
json.dump(out, open(sys.argv[2], "w"), separators=(",", ":"))
for k, v in out.items(): print(k, len(v["keys"]), "keys hit", v["hit"], "hitstop", v["hitstop"], "bite", v["bite"], "row", [round(x, 2) for x in v["row"]], v["keys"][0][1:5])
print(os.path.getsize(sys.argv[2]), "bytes")
