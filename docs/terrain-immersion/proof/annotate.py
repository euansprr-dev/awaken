"""Numbers Euan's four reference shots (/Volumes/MEDIA/terrain-ref/) for the audit, and writes the pictures the plan uses as JPEGs."""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

REF = "/Volumes/MEDIA/terrain-ref/"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "img")

# image -> [(x, y, number)] in the shot's own pixels
MARKS = {
    "euan-lumen-road.webp": [(1620, 430, 1), (1010, 870, 2), (210, 770, 3), (1250, 600, 4), (560, 660, 5), (880, 560, 6)],
    "euan-river-mountain.webp": [(300, 640, 1), (980, 430, 2), (1640, 330, 3), (1050, 820, 4), (580, 380, 5)],
    "w7-road-stepped.png": [(640, 640, 1), (950, 690, 2), (1300, 470, 3), (350, 700, 4), (760, 350, 5)],
    "w7-mountain-town.png": [(130, 330, 1), (720, 220, 2), (420, 700, 3), (900, 850, 4), (1450, 620, 5)],
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, marks in MARKS.items():
        im = Image.open(REF + name).convert("RGB")
        w, h = im.size
        s = 1600 / w
        im = im.resize((1600, int(h * s)), Image.LANCZOS)
        d = ImageDraw.Draw(im)
        font = ImageFont.load_default(size=30)
        for x, y, n in marks:
            x, y = x * s, y * s
            r = 24
            d.ellipse((x - r - 3, y - r - 3, x + r + 3, y + r + 3), fill=(20, 20, 24))
            d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 196, 64))
            tw = d.textlength(str(n), font=font)
            d.text((x - tw / 2, y - 17), str(n), fill=(20, 20, 24), font=font)
        base = os.path.splitext(name)[0]
        im.save(os.path.join(OUT, "audit-%s.jpg" % base), quality=86)
        print("audit-%s.jpg" % base)


if __name__ == "__main__":
    main()
