# python3 -I tools/creatures/track.py <clip root> <out.png> <crop w> <crop h> <scale> name:t0:n:step [name:t0:n:step ...]
# A contact sheet that follows the creature: each frame is cropped round the creature (the median of the pixels that
# are neither sky nor the grey studio floor), one row per spec: n frames from t0 game ticks into the clip, step apart.
# MODE=dark keeps only dark pixels (a black or purple body), MODE=white only near-white ones (the hushling).
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

D, out, cw, ch, sc = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5])
MODE = os.environ.get("MODE", "")
rows = []
for spec in sys.argv[6:]:
    name, t0, n, step = spec.split(":")[:4]
    t0, n, step = float(t0), int(n), float(step)
    ts = [(int(i), float(t)) for i, t in (ln.split() for ln in open(f"{D}/{name}/times.txt"))]
    base = ts[0][1]
    row = []
    for k in range(n):
        i, t = min(ts, key=lambda x: abs(x[1] - (base + t0 + k * step)))
        im = Image.open(f"{D}/{name}/f{i:05d}.png").convert("RGB")
        a = np.asarray(im).astype(int)
        r, b = a[..., 0], a[..., 2]
        sat = np.max(a, 2) - np.min(a, 2)
        if MODE == "white":
            m = np.min(a, 2) > 215
            m[:int(a.shape[0] * 0.15)] = False
        else:
            m = ~((b > r + 25) & (b > 150)) & (sat >= 14)
            m[:int(a.shape[0] * 0.1)] = False
            if MODE == "dark":
                m &= np.max(a, 2) < 120
        ys, xs = np.nonzero(m)
        cx, cy = (int(np.median(xs)), int(np.median(ys))) if len(xs) >= 20 else (a.shape[1] // 2, a.shape[0] // 2)
        x0 = max(0, min(a.shape[1] - cw, cx - cw // 2))
        y0 = max(0, min(a.shape[0] - ch, cy - ch // 2))
        row.append((im.crop((x0, y0, x0 + cw, y0 + ch)).resize((int(cw * sc), int(ch * sc)), Image.NEAREST), t - base))
    rows.append(row)
w, h = rows[0][0][0].size
sheet = Image.new("RGB", (w * max(len(r) for r in rows), h * len(rows)))
d = ImageDraw.Draw(sheet)
for ri, row in enumerate(rows):
    for k, (im, t) in enumerate(row):
        sheet.paste(im, (k * w, ri * h))
        d.text((k * w + 4, ri * h + 4), f"{t:.0f}t", fill=(255, 0, 0))
sheet.save(out)
print(out, sheet.size)
