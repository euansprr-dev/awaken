# python3 -I tools/creatures/snap.py <clip dir> x0 y0 x1 y1
# How fast a region of the frame changes, per game tick, frame to frame; prints the median, p95 and the top spikes.
# A head that snaps round shows as one spike (the Briar Sow's old snap: 23 against a median under 1); a steady turn
# is a low ridge over several frames.
import sys
import numpy as np
from PIL import Image
d = sys.argv[1]; x0, y0, x1, y1 = map(int, sys.argv[2:6])
ts = [(int(i), float(t)) for i, t in (l.split() for l in open(f'{d}/times.txt'))]
prev = None; out = []
for i, t in ts:
    a = np.asarray(Image.open(f'{d}/f{i:05d}.png').convert('L').crop((x0, y0, x1, y1))).astype(float)
    if prev is not None and t > prev[1]:
        out.append((np.abs(a - prev[0]).mean() / (t - prev[1]), t - ts[0][1], i))
    prev = (a, t)
v = np.array([o[0] for o in out])
print(f'median {np.median(v):.2f}  p95 {np.percentile(v, 95):.2f}  max {v.max():.2f}')
for o in sorted(out, reverse=True)[:6]: print(f'  {o[0]:.2f} at {o[1]:.0f}t (f{o[2]:05d})')
