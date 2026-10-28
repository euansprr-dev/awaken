"""Original neutral rain for vanilla's existing weather batch (SPEC §22).

Same 64×256 atlas footprint and UV density as the game. Sparse, one-pixel streaks with
uneven lengths and tapered opacity, no blue pigment or opaque white bars. Generation
uses no Minecraft asset as input. Snow and the Rolling Storm's distant curtain are separate.

    python3 tools/art/build_all.py lens_rain
    python3 tools/art/specs/lens_rain.py preview /path/to/preview.png
"""
import os
import random
import sys
from PIL import Image, ImageDraw

W, H = 64, 256


def rain():
    rng = random.Random(261028)
    out = Image.new("RGBA", (W, H), (208, 208, 208, 0))
    for i in range(28):
        # Spread heads over the tile; wrap the tails so vertical scrolling has no hard seam.
        x = rng.randrange(W)
        y = (i * H // 28 + rng.randrange(6)) % H
        length = rng.randrange(8, 23)
        opacity = rng.randrange(90, 145)
        grey = rng.randrange(194, 219)
        for dy in range(length):
            t = (dy + 0.5) / length
            # A short soft head and a long thinning tail: motion blur without a solid dash.
            envelope = min(1.0, t / 0.18) * (1.0 - t) ** 0.65
            alpha = round(opacity * envelope)
            yy = (y + dy) % H
            if alpha > out.getpixel((x, yy))[3]:
                out.putpixel((x, yy), (grey, grey, grey, alpha))
    return out


def write(root):
    path = os.path.join(root, "assets/minecraft/textures/environment/rain.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rain().save(path)
    return ["minecraft/environment/rain"]


def build(out):
    out.written.extend(write(out.root))


def preview(path):
    # Offline contact sheet only. These backgrounds are made here, not screenshots or game assets.
    samples = [("Overcast", (94, 105, 99), 0.8, (1, 1, 1)),
               ("Forest", (31, 48, 36), 0.9, (0.70, 0.74, 0.76)),
               ("Night", (12, 17, 26), 0.9, (0.30, 0.34, 0.40)),
               ("Lamplight", (53, 42, 30), 0.7, (0.95, 0.80, 0.60))]
    sheet = Image.new("RGB", (W * 4 * 3, (H + 22) * 3), (21, 24, 27))
    draw = ImageDraw.Draw(sheet)
    for i, (title, bg, intensity, light) in enumerate(samples):
        base = Image.new("RGBA", (W, H), (*bg, 255))
        drops = rain()
        drops.putdata([(round(r * light[0]), round(g * light[1]), round(b * light[2]), round(a * intensity))
                       for r, g, b, a in drops.getdata()])
        base = Image.alpha_composite(base, drops).convert("RGB")
        sheet.paste(base.resize((W * 3, H * 3), Image.Resampling.NEAREST), (i * W * 3, 44))
        draw.text((i * W * 3 + 10, 15), title, fill=(220, 224, 225))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "preview":
        preview(sys.argv[2])
