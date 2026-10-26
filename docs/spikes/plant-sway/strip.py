"""Motion strips for swaying plants (SPEC 15): one fixed camera, frames a second apart.

    python3 -I docs/spikes/plant-sway/strip.py <out_dir> <name> <crop x0,y0,x1,y1 | -> <frame1.png> [<frame2.png> ...]

Writes <name>_strip.jpg (the frames at a quarter size over what changed since the previous frame, white = moved,
amplified 6x, ending with the run's total change in red over the first frame), <name>_crop.jpg (the same crop of every
frame at full size, for judging bases, joints and gaps) and <name>.gif (the frames at half size, a second each).
Prints the share of pixels that changed between frames, so a swaying run and a still control compare as numbers.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

THRESH = 12


def main(argv):
    out, name, crop, paths = Path(argv[0]), argv[1], argv[2], argv[3:]
    out.mkdir(parents=True, exist_ok=True)
    imgs = [Image.open(p).convert("RGB") for p in paths]
    arrs = [np.asarray(i, dtype=np.int16) for i in imgs]
    w, h = imgs[0].size
    tw, th = w // 4, h // 4

    diffs = [np.abs(b - a).max(axis=2) for a, b in zip(arrs, arrs[1:])]
    dmax = np.max(diffs, axis=0)
    moved = dmax > THRESH
    shares = [float((d > THRESH).mean() * 100) for d in diffs]
    print(f"{name}: changed per step " + ", ".join(f"{s:.1f}%" for s in shares) + f"; over the run {moved.mean() * 100:.1f}%")

    pad = 30
    sheet = Image.new("RGB", (tw * len(imgs), th * 2 + pad * 2), (18, 18, 18))
    draw = ImageDraw.Draw(sheet)
    draw.text((8, 8), f"{name}: {len(imgs)} frames 1 s apart; below, what changed since the previous frame "
                      f"(per step {', '.join(f'{s:.1f}%' for s in shares)}); last, the whole run in red", fill=(230, 230, 230))
    for i, img in enumerate(imgs):
        sheet.paste(img.resize((tw, th), Image.LANCZOS), (i * tw, pad))
        draw.text((i * tw + 6, pad + 4), str(i + 1), fill=(255, 255, 0))
    for i, d in enumerate(diffs):
        cell = Image.fromarray(np.repeat(np.minimum(d * 6, 255).astype(np.uint8)[:, :, None], 3, axis=2))
        sheet.paste(cell.resize((tw, th), Image.LANCZOS), ((i + 1) * tw, pad * 2 + th))
    over = arrs[0].copy()
    over[moved] = (over[moved] * 0.3 + np.array([255, 0, 0]) * 0.7).astype(np.int16)
    sheet.paste(Image.fromarray(np.clip(over, 0, 255).astype(np.uint8)).resize((tw, th), Image.LANCZOS), (0, pad * 2 + th))
    sheet.save(out / f"{name}_strip.jpg", quality=90)

    if crop != "-":
        x0, y0, x1, y1 = (int(v) for v in crop.split(","))
        cw, ch = x1 - x0, y1 - y0
        cols = 4
        rows = (len(imgs) + cols - 1) // cols
        cs = Image.new("RGB", (cw * cols, ch * rows), (18, 18, 18))
        cd = ImageDraw.Draw(cs)
        for i, img in enumerate(imgs):
            cs.paste(img.crop((x0, y0, x1, y1)), ((i % cols) * cw, (i // cols) * ch))
            cd.text(((i % cols) * cw + 6, (i // cols) * ch + 4), str(i + 1), fill=(255, 255, 0))
        cs.save(out / f"{name}_crop.jpg", quality=92)

    frames = [i.resize((w // 2, h // 2), Image.LANCZOS) for i in imgs]
    frames[0].save(out / f"{name}.gif", save_all=True, append_images=frames[1:], duration=1000, loop=0, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1:])
