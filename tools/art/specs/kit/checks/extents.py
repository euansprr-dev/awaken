"""extents.py BOSS [clip t]: the body's true-size extents at rest (blocks) and the hitbox it implies at its scale."""
import sys, importlib; sys.path.insert(0, ".")
from kit import core
d = importlib.import_module("kit.bosses." + sys.argv[1]).design(); r = d["rig"]
clip, t = (sys.argv[2], float(sys.argv[3])) if len(sys.argv) > 3 else ("idle", 0.0)
s = r.sample([(clip, t, 1.0)])
lo = [1e9] * 3; hi = [-1e9] * 3
for node in r.bones:
    if not node.cubes:
        continue
    M, T = core._chain(r, s, node)
    for c in node.cubes:
        for x in (c.x, c.x + c.w):
            for y in (c.y, c.y + c.h):
                for z in (c.z, c.z + c.d):
                    p = [sum(M[i][j] * v for j, v in enumerate((x, y, z))) + T[i] for i in range(3)]
                    lo = [min(a, b) for a, b in zip(lo, p)]; hi = [max(a, b) for a, b in zip(hi, p)]
w, h, dd = (hi[0] - lo[0]) / 16, (core.GROUND - lo[1]) / 16, (hi[2] - lo[2]) / 16
sc = d["scale"]
print(f"{sys.argv[1]}: true w {w:.2f} h {h:.2f} d {dd:.2f} blocks; at scale {sc}: size({min(w, dd) * 0.8 / sc:.2f}F, {h / sc:.2f}F)")
