"""A contact sheet of a tour's shots, for choosing the page's pictures: python3 sheet.py <out.jpg> <png> [<png> ...]"""
import sys
from PIL import Image, ImageDraw

out, files = sys.argv[1], sys.argv[2:]
cols = 4 if len(files) > 6 else 3
tw, th = 480, 270
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, rows * (th + 16)), (20, 20, 24))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    im = Image.open(f).convert("RGB").resize((tw, th), Image.LANCZOS)
    x, y = (i % cols) * tw, (i // cols) * (th + 16)
    sheet.paste(im, (x, y + 16))
    d.text((x + 4, y + 2), f.split("/")[-1][:60], fill=(230, 230, 230))
sheet.save(out, quality=78)
print(out, sheet.size)
