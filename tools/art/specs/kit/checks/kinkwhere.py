"""kinkwhere.py BOSS SEQ: the largest frame-to-frame speed changes in a move or string (time, bone, move, since its start, speeds)."""
import sys, math
sys.path.insert(0, ".")
import kit_bodies as K
from kit import motion as M
want = sys.argv[1]; seq = sys.argv[2].split("-")
for d in K.designs([want]):
    rig = d["rig"]; by = {m.id: m for m in d["moves"]}
    segs, total = M.schedule(by, seq)
    print("segs", [(s[0], round(s[1],2), round(s[2],2), round(s[3],2), round(s[4],2), round(s[5],2)) for s in segs])
    prev, prevv, rows = {}, {}, []
    for t, _, pose, mid, since in M.play(rig, segs, total, M.FPS):
        for b, v in pose.items():
            if b in prev:
                dd = math.sqrt(sum((v[i] - prev[b][i]) ** 2 for i in range(3)))
                if b in prevv and mid is not None:
                    rows.append((abs(dd - prevv[b]), t, b, mid, round(since, 3), round(prevv[b],1), round(dd,1)))
                prevv[b] = dd
            prev[b] = v
    rows.sort(reverse=True)
    for r in rows[:25]: print([round(r[0],1)] + list(r[1:]))
    m = by[seq[0]]
    print("W A R", m.windup, m.active, m.recover, "link", getattr(m, "link", None))
