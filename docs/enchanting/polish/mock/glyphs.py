"""The polish plan's glyph sheets, drawn from the game's own table (tools/art/specs/_enchant_glyphs.py).

    python3 docs/enchanting/polish/mock/glyphs.py            writes glyphs.json, glyph_sheet.png, pages_sheet.png
"""
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "../../../../tools/art/specs")))
from _enchant_glyphs import CUP, GABLE, GLYPHS, KNOT, SHIELD, SPEAR, rgb  # noqa: E402,F401


def draw_glyph(d, gl, ox, oy, unit, width, colour):
    for s in gl["strokes"]:
        pts = [(ox + x * unit, oy + y * unit) for x, y in s]
        d.line(pts, fill=colour, width=width, joint="curve")
        r = width / 2 - 0.5
        for x, y in pts:
            d.ellipse([x - r, y - r, x + r, y + r], fill=colour)


def page_icon(gl):
    """A 16 x 16 page item: a parchment sheet with a band in the family's colour and the inscription's own sign (the
    glyph without its family frame, which the band stands for at this size) in dark ink."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    paper, shade, edge = (236, 224, 198, 255), (212, 194, 158, 255), (112, 88, 58, 255)
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = paper
    for x in range(2, 14):
        px[x, 0] = edge
        px[x, 15] = edge
    for y in range(1, 15):
        px[1, y] = edge
        px[14, y] = edge
    for x in range(2, 14):
        px[x, 14] = shade
    for y in range(1, 15):
        px[13, y] = shade
    r, gg, b = rgb(gl["colour"])
    band, band2 = (r, gg, b, 255), (int(r * 0.72), int(gg * 0.72), int(b * 0.72), 255)
    for x in range(2, 14):
        px[x, 1] = band
        px[x, 2] = band2
    frames = len({"ward": SHIELD, "bane": SPEAR, "spoils": CUP, "carrying": GABLE, "keeping": KNOT}[gl["family"]])
    sign = gl["strokes"][frames:]
    xs = [x for s in sign for x, _ in s]
    ys = [y for s in sign for _, y in s]
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    k = min(8 / max(w, 0.01), 8 / max(h, 0.01), 3.0)
    ox, oy = 7.5 - (min(xs) + w / 2) * k, 8.5 - (min(ys) + h / 2) * k
    ink = (58, 42, 28, 255)
    layer = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for s in sign:
        d.line([(round(ox + x * k), round(oy + y * k)) for x, y in s], fill=ink, width=1)
    im.alpha_composite(layer)
    return im


if __name__ == "__main__":
    json.dump(GLYPHS, open(os.path.join(HERE, "glyphs.json"), "w"), separators=(",", ":"))
    cols, cell = 9, 120
    rows = (len(GLYPHS) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * (cell + 18)), (22, 24, 32))
    d = ImageDraw.Draw(sheet)
    for i, gl in enumerate(GLYPHS):
        cx, cy = (i % cols) * cell, (i // cols) * (cell + 18)
        draw_glyph(d, gl, cx + 24, cy + 20, 12, 5, rgb(gl["colour"]))
        d.text((cx + 6, cy + cell), ("* " if gl["new"] else "") + gl["name"], fill=(200, 205, 215))
    sheet.save(os.path.join(HERE, "glyph_sheet.png"))
    sc = 6
    pages = Image.new("RGBA", (cols * 16 * sc + (cols + 1) * 12, rows * (16 * sc + 12) + 12), (22, 24, 32, 255))
    for i, gl in enumerate(GLYPHS):
        ic = page_icon(gl).resize((16 * sc, 16 * sc), Image.NEAREST)
        pages.alpha_composite(ic, (12 + (i % cols) * (16 * sc + 12), 12 + (i // cols) * (16 * sc + 12)))
    pages.save(os.path.join(HERE, "pages_sheet.png"))
    print(len(GLYPHS), "glyphs")
