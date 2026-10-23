"""Story assets: Vael's 64x64 player-layout skin and the Vael spawn egg."""
from pixelart import *
from PIL import Image
import random


def _fill(img, box, ramp_name, seed, lo=1, hi=4, noise=0.25):
    rp = ramp(ramp_name)
    rng = rng_for("vael", seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = (y - y0) / max(1, y1 - y0)
            idx = hi - int(v * (hi - lo) + rng.random() * noise * (hi - lo))
            img.putpixel((x, y), rp[max(lo, min(hi, idx))])


def vael_skin():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = [hex_rgba(c) for c in ("#8a5a3c", "#a06a48", "#b97f5a", "#cf9670")]
    # head (all faces) in skin, then hair/hood over top and back
    for x in range(0, 32):
        for y in range(0, 16):
            img.putpixel((x, y), skin[2 if (x + y) % 5 else 1])
    _fill(img, (8, 0, 16, 8), "night", "hairtop", 1, 3)
    _fill(img, (24, 8, 32, 16), "night", "hairback", 1, 3)
    for x in range(8, 16):  # fringe
        img.putpixel((x, 8), hex_rgba("#1b2742")); img.putpixel((x, 9), hex_rgba("#28385a") if x % 3 else hex_rgba("#1b2742"))
    for x in (0, 16):  # sides: hair down to the ears
        _fill(img, (x, 8, x + 8, 11), "night", "side" + str(x), 1, 3)
    # face: eyes (amber), mouth
    for (x, y, c) in ((9, 12, "#f2a541"), (10, 12, "#ffffff"), (13, 12, "#ffffff"), (14, 12, "#f2a541"), (11, 14, "#6d3b2a"), (12, 14, "#6d3b2a")):
        img.putpixel((x, y), hex_rgba(c))
    # hood (hat layer, 32..64 x 0..16): open-front hood
    _fill(img, (40, 0, 48, 8), "slate", "hoodtop", 1, 4)
    _fill(img, (56, 8, 64, 16), "slate", "hoodback", 1, 4)
    _fill(img, (32, 8, 40, 16), "slate", "hoodr", 1, 4)
    _fill(img, (48, 8, 56, 16), "slate", "hoodl", 1, 4)
    for x in range(40, 48):
        img.putpixel((x, 8), ramp("slate")[3]); img.putpixel((x, 9), ramp("slate")[2])
    # body: slate coat with an amber scarf and brass buckle
    _fill(img, (16, 16, 40, 32), "slate", "body", 1, 4)
    for x in range(20, 28):
        for y in (20, 21):
            img.putpixel((x, y), ramp("amber")[4 if x % 2 else 3])
    img.putpixel((23, 27), ramp("brass")[4]); img.putpixel((24, 27), ramp("brass")[4])
    # jacket overlay: a longer coat hem
    _fill(img, (16, 36, 40, 48), "slate", "jacket", 0, 3)
    for x in range(16, 40):
        for y in range(36, 44):
            img.putpixel((x, y), (0, 0, 0, 0))
    # arms: coat sleeves with skin hands at the bottom
    for box in ((40, 16, 56, 32), (32, 48, 48, 64)):
        _fill(img, box, "slate", "arm" + str(box[0]), 1, 4)
    for x in list(range(40, 56)) + list(range(32, 48)):
        y_hand = 30 if x < 56 and x >= 40 else 62
        for y in (y_hand, y_hand + 1):
            img.putpixel((x, y), skin[2])
    # legs: dark trousers and boots
    for box in ((0, 16, 16, 32), (16, 48, 32, 64)):
        _fill(img, box, "night", "leg" + str(box[0]), 2, 4)
        for x in range(box[0], box[2]):
            for y in range(box[3] - 3, box[3]):
                img.putpixel((x, y), ramp("wood")[1])
    return img


def build(out):
    import os
    path = os.path.join(out.root, "assets/awaken/textures/entity/vael.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    vael_skin().save(path)
    out.written.append("entity/vael")
    out.item("vael_spawn_egg", orb("slate", core_ramp="amber"))
