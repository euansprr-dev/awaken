"""floorsweep.py [BOSS ...]: for every kit boss, guard/ground names that are not bones, and clips dipping > 1.5 px."""
import sys, importlib, os
sys.path.insert(0, ".")
from kit import core
import kit_bodies as K

seen = []
_orig = core.ground_pass


def spy(rig, ground, clips=None, skip=("death",)):
    for guard, adjust in ground:
        for g in guard:
            if g not in rig.by_name:
                seen.append(("ground guard", g))
        for a in adjust:
            if a[0] not in rig.by_name:
                seen.append(("ground adjust", a[0]))
    return _orig(rig, ground, clips, skip)


core.ground_pass = spy
ids = sys.argv[1:] or K.BOSSES
for bid in ids:
    if not os.path.exists(f"kit/bosses/{bid}.py"):
        print(f"{bid}: no module")
        continue
    seen.clear()
    mod = importlib.import_module("kit.bosses." + bid)
    d = mod.design()
    r = d["rig"]
    bad = [f"{w} {g}" for w, g in seen]
    bad += [f"guard {g}" for g in getattr(r, "guard", ()) if g not in r.by_name]
    for mv in d.get("moves", []):
        for guard, adjust in mv.ground or ():
            bad += [f"move {mv.id} guard {g}" for g in guard if g not in r.by_name]
    under = set(getattr(r, "under", ()))
    bones = [b.name for b in r.bones if b.cubes]
    dips = []
    for name in sorted(r.clips):
        if name in under:
            continue
        c = r.clips[name]
        n = max(2, int(c.length / 0.05))
        worst = (-99, 0, "")
        for k in range(n + 1):
            t = c.length * k / n
            s = r.sample([(name, t, 1.0)])
            for b in bones:
                v = core.lowest(r, s, [b]) - core.GROUND
                if v > worst[0]:
                    worst = (v, t, b)
        if worst[0] > 1.5:
            dips.append(f"{name} {worst[0]:+.1f}@{worst[1]:.2f}({worst[2]})")
    print(f"{bid}: guard={len(getattr(r, 'guard', ()))} bad={sorted(set(bad))} dips={dips}", flush=True)
