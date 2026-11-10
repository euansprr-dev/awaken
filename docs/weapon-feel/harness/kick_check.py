"""Disposable: does the row's from/to (which drives the camera kick and the knockback's side) agree with the blade the
baked keys draw on screen at the hit? Read-only."""
import json, math, sys, os
ROOT = sys.argv[1]
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
import numpy as np
import anim_lab as L
import swing_studio as S
swings = L.load_swings(); T = L.table()
THRUST = ("stab", "jab", "bash")
bad = []; n = 0
for sid in T:
    for mv, m in T[sid]["moves"].items():
        clip = swings.get(sid, {}).get("moves", {}).get(mv)
        sw = m.get("swing")
        if not clip or not sw: continue
        form = sw.get("form", "")
        if form in THRUST or m.get("swish") == "thrust": continue
        rs = S.rows(sid, mv, swings)
        hit = clip.get("hit", L.hit_tick(m))
        ts = np.array([x[0] for x in rs]); mid = np.array([x[2] for x in rs])
        # the blade's middle over the half tick around the hit
        a = np.array([np.interp(hit - 0.25, ts, mid[:, 0]), np.interp(hit - 0.25, ts, mid[:, 1])])
        b = np.array([np.interp(hit + 0.25, ts, mid[:, 0]), np.interp(hit + 0.25, ts, mid[:, 1])])
        v = b - a
        if np.linalg.norm(v) < 0.02: continue
        fr, to = sw["from"], sw["to"]
        row = np.array([to[0] - fr[0], to[1] - fr[1]])
        if np.linalg.norm(row) < 1e-6: continue
        n += 1
        ang = math.degrees(math.acos(max(-1, min(1, float(v @ row / (np.linalg.norm(v) * np.linalg.norm(row)))))))
        side_row = row[0] / max(0.05, abs(row[0]) + abs(row[1]))
        side_blade = v[0] / max(1e-6, abs(v[0]) + abs(v[1]))
        kick = clip.get("kick")
        kang = None
        if kick:
            kv = np.array([kick[0], -kick[1]])
            if np.linalg.norm(kv) > 1e-6:
                kang = math.degrees(math.acos(max(-1, min(1, float(v @ kv / (np.linalg.norm(v) * np.linalg.norm(kv)))))))
        cam_ang = kang if kang is not None else ang
        flip = side_row * side_blade < -0.04  # knocked the other way from the blade
        if cam_ang > 45 or flip:
            bad.append((sid, mv, form, round(cam_ang), round(side_row, 2), round(side_blade, 2), flip, "keyed kick" if kick else "row"))
print(n, "cuts checked;", len(bad), "where the camera kick is over 45 degrees off the blade, or the knockback's side is flipped")
for b in bad: print("  %-11s %-10s %-6s kick off by %3d deg   knock side %+.2f  blade side %+.2f  %s %s" % (b[0], b[1], b[2], b[3], b[4], b[5], "KNOCK FLIPPED" if b[6] else "", b[7]))
