"""Makes a contact sheet of textures (scaled x8 with names) for look tests: python3 tools/art/preview.py out.png [dir]"""
import os, sys
from PIL import Image, ImageDraw
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/assets/awaken/textures")
out = sys.argv[1] if len(sys.argv) > 1 else "preview.png"
sub = sys.argv[2] if len(sys.argv) > 2 else ""
files = []
for d, _, fs in os.walk(os.path.join(root, sub)):
    files += [os.path.join(d, f) for f in fs if f.endswith(".png")]
files.sort()
S, cols = 96, 8
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGBA", (cols * (S + 16), rows * (S + 30) + 10), (24, 26, 34, 255))
dr = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    im = Image.open(f).convert("RGBA")
    im = im.crop((0, 0, im.width, min(im.height, im.width)))
    im = im.resize((S, S), Image.NEAREST)
    x, y = (i % cols) * (S + 16) + 8, (i // cols) * (S + 30) + 8
    sheet.paste(im, (x, y), im)
    dr.text((x, y + S + 2), os.path.basename(f)[:-4][:16], fill=(200, 205, 215, 255))
sheet.save(out)
print(out, len(files))
