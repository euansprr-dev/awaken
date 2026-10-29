"""dipwhere.py BOSS a-b: the move layer's size through a string, and the bones that make it at the contacts and the low."""
import sys, importlib; sys.path.insert(0, ".")
from kit import core, motion
mod = importlib.import_module("kit.bosses." + sys.argv[1])
d = mod.design(); r = d["rig"]
mv = {m.id: m for m in d["moves"]}
seq = sys.argv[2].split("-")
segs, total = motion.schedule(mv, seq)
fr = motion.play(r, segs, total, motion.FPS)
mags = []
for t, _, pose, mid, since in fr:
    per = {b: sum(abs((c + 180) % 360 - 180) for c in v) for b, v in pose.items()}
    mags.append((t, sum(per.values()), per))
for a_, b_ in zip(segs, segs[1:]):
    ca, cb = int((a_[1] + a_[2]) * motion.FPS), int((b_[1] + b_[2]) * motion.FPS)
    lo = min(range(ca, cb + 1), key=lambda i: mags[i][1])
    print(f"contact {mags[ca][0]:.2f} {mags[ca][1]:.0f}  low {mags[lo][0]:.2f} {mags[lo][1]:.0f}  contact {mags[cb][0]:.2f} {mags[cb][1]:.0f}")
    for name, i in (("A", ca), ("low", lo), ("B", cb)):
        top = sorted(mags[i][2].items(), key=lambda kv: -kv[1])[:10]
        print(f"  {name:4s}", " ".join(f"{b}:{v:.0f}" for b, v in top))
    print("  curve", " ".join(f"{mags[i][1]:.0f}" for i in range(ca, cb + 1, 3)))
