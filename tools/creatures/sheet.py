# python3 -I tools/creatures/sheet.py <clip root> <out.png> name:t0:n:step[:x0,y0,x1,y1] ...
# A contact sheet of autopilot clip frames: one row per spec, n frames from t0 game ticks into the clip, step apart,
# optionally cropped to a fixed box (frame pixels). Each tile is stamped with its tick.
import sys
from PIL import Image, ImageDraw
D, out = sys.argv[1], sys.argv[2]
specs = sys.argv[3:]  # name:t0:n:step[:x0,y0,x1,y1]
rows = []
for sp in specs:
    parts = sp.split(':')
    name, t0, n, step = parts[0], float(parts[1]), int(parts[2]), float(parts[3])
    crop = tuple(int(v) for v in parts[4].split(',')) if len(parts) > 4 else None
    ts = [(int(i), float(t)) for i, t in (l.split() for l in open(f'{D}/{name}/times.txt'))]
    base = ts[0][1]
    row = []
    for k in range(n):
        want = base + t0 + k * step
        i, t = min(ts, key=lambda x: abs(x[1] - want))
        im = Image.open(f'{D}/{name}/f{i:05d}.png')
        if crop: im = im.crop(crop)
        row.append((im, t - base))
    rows.append(row)
w, h = rows[0][0][0].size
W = Image.new('RGB', (w * max(len(r) for r in rows), h * len(rows)))
d = ImageDraw.Draw(W)
for r, row in enumerate(rows):
    for k, (im, t) in enumerate(row):
        W.paste(im, (k * w, r * h)); d.text((k * w + 4, r * h + 4), f'{t:.0f}t', fill=(255, 0, 0))
W.save(out)
