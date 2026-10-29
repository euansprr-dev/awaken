"""gifsheet.py OUT N GIF...: N frames of each GIF side by side, one row per GIF."""
import sys
from PIL import Image, ImageDraw, ImageSequence
out, n, gifs = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
rows = []
for g in gifs:
    im = Image.open(g)
    fr = [f.convert("RGB") for f in ImageSequence.Iterator(im)]
    pick = [fr[round(i * (len(fr) - 1) / (n - 1))] for i in range(n)]
    rows.append((g.split("/")[-1], pick, len(fr)))
w, h = rows[0][1][0].size
sheet = Image.new("RGB", (w * n, h * len(rows)), (255, 255, 255))
d = ImageDraw.Draw(sheet)
for ri, (name, pick, total) in enumerate(rows):
    for ci, f in enumerate(pick):
        sheet.paste(f, (ci * w, ri * h))
    d.text((4, ri * h + 4), f"{name} ({total} fr)", fill=(0, 0, 0))
sheet.save(out)
print(out, sheet.size)
