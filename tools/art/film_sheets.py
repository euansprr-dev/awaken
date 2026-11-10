"""One picture per monster from its film clips (SPEC §60.9), for reading a whole reel at a glance.

    python3 -I tools/art/film_sheets.py <clips dir> <out dir> [--cols 8] [--width 300] [--crop x0,y0,x1,y1]

<clips dir> holds the recorder's folders (<id>_idle, <id>_fight, <id>_m0.., <id>_death: f*.png and times.txt), as
`gh run download ... -n tour-monsters-film-<n>-clips` leaves them. For each monster it writes <out dir>/<id>.jpg: a row
or two for each clip, frames at even steps of game time, each stamped with its seconds into the clip. --crop cuts every
frame to that part of it first (fractions of the frame, 0..1).

Standalone on purpose (only Pillow): it reads downloaded files, so it is run with -I and imports nothing of ours.
"""
import os
import sys

from PIL import Image, ImageDraw

ORDER = ("idle", "fight", "m0", "m1", "m2", "death")
#: frames shown for each kind of clip (a fight gets two rows)
COUNT = {"idle": 1, "fight": 3, "death": 1}


def frames(d):
    out = []
    path = os.path.join(d, "times.txt")
    if not os.path.exists(path):
        return out
    with open(path) as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2:
                continue
            p = os.path.join(d, f"f{int(parts[0]):05d}.png")
            if os.path.exists(p):
                out.append((p, float(parts[1])))
    return out


def picked(fr, n):
    """n frames at even steps of game time (the nearest recorded frame to each step)."""
    if len(fr) <= n:
        return fr
    t0, t1 = fr[0][1], fr[-1][1]
    out, k = [], 0
    for i in range(n):
        want = t0 + (t1 - t0) * i / (n - 1)
        while k + 1 < len(fr) and abs(fr[k + 1][1] - want) <= abs(fr[k][1] - want):
            k += 1
        out.append(fr[k])
    return out


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    src, dst = argv[0], argv[1]
    cols = int(argv[argv.index("--cols") + 1]) if "--cols" in argv else 8
    width = int(argv[argv.index("--width") + 1]) if "--width" in argv else 300
    crop = tuple(float(v) for v in argv[argv.index("--crop") + 1].split(",")) if "--crop" in argv else (0.0, 0.0, 1.0, 1.0)
    os.makedirs(dst, exist_ok=True)
    ids = {}
    for name in sorted(os.listdir(src)):
        if not os.path.isdir(os.path.join(src, name)):
            continue
        for kind in ORDER:
            if name.endswith("_" + kind):
                ids.setdefault(name[: -len(kind) - 1], {})[kind] = os.path.join(src, name)
    for mid, clips in sorted(ids.items()):
        rows = []
        for kind in ORDER:
            if kind not in clips:
                continue
            fr = frames(clips[kind])
            if len(fr) < 2:
                continue
            n = cols * COUNT.get(kind, 2)
            pick = picked(fr, n)
            for r in range(0, len(pick), cols):
                rows.append((kind if r == 0 else "", pick[r:r + cols], fr[0][1], len(fr), (fr[-1][1] - fr[0][1]) / 20.0))
        if not rows:
            continue
        first = Image.open(rows[0][1][0][0])
        box = (int(first.width * crop[0]), int(first.height * crop[1]), int(first.width * crop[2]), int(first.height * crop[3]))
        cw, ch = box[2] - box[0], box[3] - box[1]
        h = max(1, round(width * ch / cw))
        sheet = Image.new("RGB", (cols * width, len(rows) * h), (12, 13, 18))
        d = ImageDraw.Draw(sheet)
        for ri, (label, pick, t0, total, secs) in enumerate(rows):
            for ci, (p, t) in enumerate(pick):
                im = Image.open(p).convert("RGB").crop(box).resize((width, h), Image.BILINEAR)
                sheet.paste(im, (ci * width, ri * h))
                d.rectangle((ci * width, ri * h, ci * width + 34, ri * h + 11), fill=(0, 0, 0))
                d.text((ci * width + 2, ri * h), f"{(t - t0) / 20.0:.2f}", fill=(235, 235, 235))
            if label:
                tag = f"{mid} {label}  ({total} frames, {secs:.1f} s)"
                d.rectangle((40, ri * h, 40 + 7 * len(tag), ri * h + 11), fill=(0, 0, 0))
                d.text((43, ri * h), tag, fill=(255, 226, 150))
        sheet.save(os.path.join(dst, mid + ".jpg"), quality=86)
        print(mid, sheet.size, len(rows), "rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
