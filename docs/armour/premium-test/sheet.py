"""Look A's comparison sheets from a CI tour's shots (tools/autopilot/gear-premium.txt).

    python3 docs/armour/premium-test/sheet.py SHOTS_DIR OUT_DIR [crop]

For each camera it lays the three levels side by side, Today | Level 1 | Level 2, and writes sheet_<camera>.png, plus
overview_day.png and overview_night.png with a row per camera. `crop` is the share of the frame's width kept around its
centre for the close shots (default 0.5 for everything but the lineups, which keep the whole frame).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

LEVELS = (("today", "Today (as it ships)"), ("level1", "Level 1 (planned look, glow pass)"), ("level2", "Level 2 (plus our shader)"))
DAY = ("lineup", "front", "threequarter", "back", "side", "hood")
NIGHT = ("night_lineup", "night_hood")
CELL_W = 640


def font(size):
    for p in ("/System/Library/Fonts/Helvetica.ttc", "/System/Library/Fonts/SFNS.ttf", "/Library/Fonts/Arial.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def load(shots, level, cam, crop):
    path = os.path.join(shots, "prem_%s_%s.png" % (level, cam))
    if not os.path.exists(path):
        return None
    im = Image.open(path).convert("RGB")
    if "lineup" not in cam and crop < 1:
        w, h = im.size
        cw = int(w * crop)
        im = im.crop(((w - cw) // 2, 0, (w + cw) // 2, h))
    return im.resize((CELL_W, round(im.height * CELL_W / im.width)), Image.LANCZOS)


def row(shots, cam, crop, labels=True):
    ims = [load(shots, lv, cam, crop) for lv, _ in LEVELS]
    ims = [i for i in ims if i is not None]
    if not ims:
        return None
    h = max(i.height for i in ims)
    pad, lab = 6, 34 if labels else 0
    sheet = Image.new("RGB", (len(ims) * CELL_W + (len(ims) + 1) * pad, h + lab + pad), (18, 18, 22))
    d = ImageDraw.Draw(sheet)
    f = font(20)
    for n, im in enumerate(ims):
        x = pad + n * (CELL_W + pad)
        sheet.paste(im, (x, lab))
        if labels:
            d.text((x + 6, 6), LEVELS[n][1], fill=(230, 230, 240), font=f)
    return sheet


def stack(rows):
    rows = [r for r in rows if r is not None]
    if not rows:
        return None
    out = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)), (18, 18, 22))
    y = 0
    for r in rows:
        out.paste(r, (0, y))
        y += r.height
    return out


def main(shots, out, crop=0.5):
    os.makedirs(out, exist_ok=True)
    rows = {}
    for cam in DAY + NIGHT:
        rows[cam] = row(shots, cam, crop)
        if rows[cam] is not None:
            rows[cam].save(os.path.join(out, "sheet_%s.png" % cam))
    for name, cams in (("day", DAY), ("night", NIGHT)):
        s = stack([rows[c] for c in cams])
        if s is not None:
            s.save(os.path.join(out, "overview_%s.png" % name))
    print("wrote", sorted(os.listdir(out)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 0.5)
