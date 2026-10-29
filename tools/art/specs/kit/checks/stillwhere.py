"""stillwhere.py BOSS SEQ: the spells inside a move where no bone moves faster than the meter's still speed."""
import sys, importlib; sys.path.insert(0, ".")
from kit import motion
d = importlib.import_module("kit.bosses." + sys.argv[1]).design(); r = d["rig"]
mv = {m.id: m for m in d["moves"]}
segs, total = motion.schedule(mv, sys.argv[2].split("-"))
print(segs)
fr = motion.play(r, segs, total, motion.FPS)
prev, start = None, None
for t, _, pose, mid, since in fr:
    if prev is not None:
        sp = max(sum(abs((a - b + 180) % 360 - 180) for a, b in zip(pose[k], prev[k])) for k in pose) * motion.FPS
        slow = mid is not None and sp < motion.STILL_SPEED
        if slow and start is None:
            start = t
        if not slow and start is not None:
            if t - start >= 0.1:
                print(f"  still {start:.2f}-{t:.2f} ({t - start:.2f} s) in {mid}")
            start = None
    prev = pose
