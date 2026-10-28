"""The break stages (SPEC §34.3): vanilla's ten destroy_stage textures redrawn as rock splitting. One crack tree grows from
the strike's point: a hairline that wanders, branches and runs toward the edges, the trunk widening and chips spalling
off near the point in the last stages. The game draws these over the block with its crumbling blend (colour times the
texture, doubled): mid grey changes nothing, dark darkens (the crack), light lightens (the lit lip on its upper-left side,
so a crack reads as cut into the face). Transparent everywhere else.

    python3 tools/art/specs/delve_cracks.py            write src/main/resources/assets/minecraft/textures/block/destroy_stage_0..9.png
    python3 tools/art/specs/delve_cracks.py preview F  a strip of the ten, large, over stone
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
OUT = os.path.join(ROOT, "src/main/resources/assets/minecraft/textures/block")

DARK, DEEP, LIP = 58, 34, 166   # the crack, its widest middle, the lit lip (128 is "no change")
STAGES = 10


def tree(seed=7):
    """Segments (x0, y0, x1, y1, born, trunk): born is the stage fraction (0-1) a segment shows from."""
    rnd = random.Random(seed)
    segs = []

    def grow(x, y, ang, length, born, step, trunk, depth):
        t = born
        for i in range(int(length)):
            ang += rnd.uniform(-0.38, 0.38)
            nx, ny = x + math.cos(ang) * 1.0, y + math.sin(ang) * 1.0
            segs.append((x, y, nx, ny, min(1.0, t), trunk))
            x, y = nx, ny
            t += step
            if not (0 <= x < 16 and 0 <= y < 16):
                return
            if depth < 2 and rnd.random() < (0.16 if trunk else 0.09):
                grow(x, y, ang + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.2), length * rnd.uniform(0.35, 0.6), t + 0.06, step * 1.15, False, depth + 1)

    cx, cy = 7.6, 8.3
    base = rnd.uniform(0, math.tau)
    # three arms out of the point, the first longest (the split), all growing over the stages
    grow(cx, cy, base, 14, 0.0, 0.062, True, 0)
    grow(cx, cy, base + math.pi + rnd.uniform(-0.4, 0.4), 12, 0.1, 0.066, True, 0)
    grow(cx, cy, base + math.pi / 2 + rnd.uniform(-0.5, 0.5), 9, 0.34, 0.07, False, 0)
    return segs, (cx, cy)


def stage(k, segs, point):
    f = k / (STAGES - 1)
    core = {}
    for x0, y0, x1, y1, born, trunk in segs:
        if born > f + 1e-6:
            continue
        steps = 4
        for s in range(steps + 1):
            t = s / steps
            px, py = int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t)
            if 0 <= px < 16 and 0 <= py < 16:
                core[(px, py)] = min(core.get((px, py), 255), DARK)
                # the trunk widens in the late stages, deepest near the point
                if trunk and k >= 6 and math.hypot(px - point[0], py - point[1]) < 1.8 + (k - 6) * 0.9:
                    core[(px, py)] = DEEP
                    for nx, ny in ((px + 1, py),):
                        if 0 <= nx < 16 and 0 <= ny < 16:
                            core[(nx, ny)] = min(core.get((nx, ny), 255), DARK)
    if k >= 8:
        # chips spalled off round the point: small pits
        rnd = random.Random(80 + k)
        for _ in range(2 + (k - 8) * 2):
            a, r = rnd.uniform(0, math.tau), rnd.uniform(1.2, 3.0)
            px, py = int(point[0] + math.cos(a) * r), int(point[1] + math.sin(a) * r)
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                if 0 <= px + dx < 16 and 0 <= py + dy < 16:
                    core[(px + dx, py + dy)] = DEEP if (dx, dy) == (0, 0) else DARK
    img = Image.new("RGBA", (16, 16), (255, 255, 255, 0))
    for (x, y) in core:
        # the lit lip: the rock's edge just above and left of the crack catches the light
        for lx, ly in ((x - 1, y), (x, y - 1)):
            if 0 <= lx < 16 and 0 <= ly < 16 and (lx, ly) not in core:
                img.putpixel((lx, ly), (LIP, LIP, LIP, 255))
    for (x, y), v in core.items():
        img.putpixel((x, y), (v, v, v, 255))
    return img


def main(argv):
    segs, point = tree()
    imgs = [stage(k, segs, point) for k in range(STAGES)]
    if len(argv) >= 2 and argv[0] == "preview":
        stone_path = os.path.join("/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block/stone.png")
        stone = Image.open(stone_path).convert("RGB")
        strip = Image.new("RGB", (16 * STAGES + STAGES - 1, 16), (20, 20, 20))
        for k, im in enumerate(imgs):
            cell = stone.copy()
            px, cp = cell.load(), im.load()
            for y in range(16):
                for x in range(16):
                    r, g, b, a = cp[x, y]
                    if a > 25:
                        px[x, y] = tuple(min(255, int(c * r / 128)) for c in px[x, y])
            strip.paste(cell, (k * 17, 0))
        strip.resize((strip.width * 12, strip.height * 12), Image.NEAREST).save(argv[1])
        print("preview:", argv[1])
        return 0
    os.makedirs(OUT, exist_ok=True)
    for k, im in enumerate(imgs):
        im.save(os.path.join(OUT, f"destroy_stage_{k}.png"))
    print(f"wrote {STAGES} break stages to {os.path.relpath(OUT, ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
