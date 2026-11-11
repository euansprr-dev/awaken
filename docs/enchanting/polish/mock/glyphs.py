"""The polish plan's draft script: one glyph for every inscription (docs/enchanting/POLISH.md). A glyph is a family's
frame (wards a shield, banes a spear, spoils a cup, carrying a gable, keeping a knot) with the inscription's own sign
inside, drawn as straight strokes between points of a 7 by 7 grid (x right, y down, 0-6), in the order a hand would cut
them. The same strokes are what the page item shows, what you trace, and what flashes when the inscription works.

    python3 docs/enchanting/polish/mock/glyphs.py            writes glyphs.json, glyph_sheet.png, pages_sheet.png
"""
import json
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))

SHIELD = [[(0, 0), (6, 0), (6, 3), (3, 6), (0, 3), (0, 0)]]
SPEAR = [[(0, 3), (6, 3)], [(4, 1), (6, 3), (4, 5)]]
CUP = [[(0, 0), (0, 4), (2, 6), (4, 6), (6, 4), (6, 0)]]
GABLE = [[(0, 3), (3, 0), (6, 3)]]
KNOT = [[(1, 0), (5, 0), (1, 6), (5, 6), (1, 0)]]
DIAMOND = [(3, 1), (4.5, 2.5), (3, 4), (1.5, 2.5), (3, 1)]
SQUARE = [(2, 1.5), (4, 1.5), (4, 3.5), (2, 3.5), (2, 1.5)]

HEAT, FROST, CHARGE, GRAVITY, SPIRIT = "FF8A4C", "8FD8FF", "FFD84A", "B993FF", "4FE0C2"
WARD, BANE, SPOILS, CARRY, KEEP, BLOOD = "B9C3D2", "FF5F63", "F2C45A", "4FB5EA", "F3EAD6", "9FE07A"


def g(no, name, family, colour, frame, *sign, new=False, ours=False):
    return {"no": no, "name": name, "family": family, "colour": colour, "new": new, "ours": ours or new,
            "strokes": [[list(p) for p in s] for s in list(frame) + list(sign)]}


def dn(pts, dy):
    return [(x, y + dy) for x, y in pts]


GLYPHS = [
    g(1, "Heat Ward", "ward", HEAT, SHIELD, [(2, 3.5), (3, 1.5), (4, 3.5)], ours=True),
    g(2, "Frost Ward", "ward", FROST, SHIELD, [(3, 1), (3, 4)], [(1.5, 2.5), (4.5, 2.5)], ours=True),
    g(3, "Charge Ward", "ward", CHARGE, SHIELD, [(4, 1), (2, 2.5), (4, 2.5), (2, 4)], ours=True),
    g(4, "Gravity Ward", "ward", GRAVITY, SHIELD, [(3, 1), (3, 4)], [(2, 3), (3, 4), (4, 3)], ours=True),
    g(5, "Spirit Ward", "ward", SPIRIT, SHIELD, DIAMOND, ours=True),
    g(6, "Blast Protection", "ward", WARD, SHIELD, [(2, 1.5), (4, 3.5)], [(4, 1.5), (2, 3.5)]),
    g(7, "Projectile Protection", "ward", WARD, SHIELD, [(1.5, 1.5), (4.5, 1.5)], [(3, 1.5), (3, 4)]),
    g(8, "Feather Falling", "ward", WARD, SHIELD, [(2, 1.2), (3, 2.2), (4, 1.2)], [(2, 2.8), (3, 3.8), (4, 2.8)]),
    g(9, "Clear Blood", "ward", BLOOD, SHIELD, SQUARE, ours=True),
    g(10, "Bane of the Dead", "bane", BANE, SPEAR, [(1.5, 0.5), (1.5, 5.5)], ours=True),
    g(11, "Bane of Crawlers", "bane", BANE, SPEAR, [(0.5, 1), (1.5, 3), (0.5, 5)], [(2, 1.5), (3, 3), (2, 4.5)], ours=True),
    g(12, "Bane of Beasts", "bane", BANE, SPEAR, [(0.5, 0.5), (0.5, 2)], [(1.5, 0.5), (1.5, 2)], [(2.5, 0.5), (2.5, 2)], ours=True),
    g(13, "Bane of the Deep", "bane", BANE, SPEAR, [(1, 3), (1, 5.5), (2.5, 5.5)], ours=True),
    g(14, "Looting", "spoils", SPOILS, CUP, DIAMOND),
    g(15, "Fortune", "spoils", SPOILS, CUP, [(3, 4), (3, 1)], [(2, 2), (3, 1), (4, 2)]),
    g(16, "Silk Touch", "spoils", SPOILS, CUP, SQUARE),
    g(17, "Efficiency", "spoils", SPOILS, CUP, [(1.5, 1.5), (2.5, 2.5), (1.5, 3.5)], [(3.5, 1.5), (4.5, 2.5), (3.5, 3.5)]),
    g(18, "Harvest", "spoils", SPOILS, CUP, [(2, 1), (3, 2.5), (4, 1)], [(3, 2.5), (3, 4)], ours=True),
    g(19, "Insight", "spoils", SPOILS, CUP, [(3, 1), (3, 4)], [(1.5, 2.5), (4.5, 2.5)], ours=True),
    g(20, "Respiration", "carrying", CARRY, GABLE, dn(DIAMOND, 2)),
    g(21, "Aqua Affinity", "carrying", CARRY, GABLE, [(1.5, 3.5), (4.5, 3.5)], [(3, 3.5), (3, 6)]),
    g(22, "Depth Strider", "carrying", CARRY, GABLE, [(1.5, 4), (2.25, 3.2), (3, 4), (3.75, 3.2), (4.5, 4)],
      [(1.5, 5.6), (2.25, 4.8), (3, 5.6), (3.75, 4.8), (4.5, 5.6)]),
    g(23, "Swift Sneak", "carrying", CARRY, GABLE, [(1.5, 3.5), (2.5, 4.75), (1.5, 6)], [(3.5, 3.5), (4.5, 4.75), (3.5, 6)]),
    g(24, "Unbreaking", "keeping", KEEP, KNOT, [(0, 3), (6, 3)]),
    g(25, "Mending", "keeping", KEEP, KNOT, [(2, 1.5), (4, 1.5)], [(2, 4.5), (4, 4.5)]),
    g(26, "Infinity", "keeping", KEEP, KNOT, [(3, 2), (4, 3), (3, 4), (2, 3), (3, 2)]),
    g(27, "Loyalty", "carrying", CARRY, GABLE, [(1.5, 3.5), (4.5, 3.5), (4.5, 5.5), (1.5, 5.5)]),
    g(28, "Riptide", "carrying", CARRY, GABLE, [(3, 4.6), (3.8, 4.6), (3.8, 3.6), (2.2, 3.6), (2.2, 5.6), (4.8, 5.6)]),
    g(29, "Luck of the Sea", "spoils", SPOILS, CUP, [(1.5, 3), (2.25, 2), (3, 3), (3.75, 2), (4.5, 3)]),
    g(30, "Lure", "spoils", SPOILS, CUP, [(3.5, 1), (3.5, 3.5), (2.5, 4), (2, 3)]),
    g(31, "Lantern", "carrying", CARRY, GABLE, [(3, 3), (3, 6)], [(1.5, 4.5), (4.5, 4.5)], [(2, 3.5), (4, 5.5)], [(4, 3.5), (2, 5.5)], new=True),
    g(32, "Wayfarer", "carrying", CARRY, GABLE, [(2.2, 3), (2.2, 6)], [(3.8, 3), (3.8, 6)], new=True),
    g(33, "Gather", "spoils", SPOILS, CUP, [(1.2, 1.5), (2.4, 2.5), (1.2, 3.5)], [(4.8, 1.5), (3.6, 2.5), (4.8, 3.5)], new=True),
    g(34, "Regrowth", "keeping", KEEP, KNOT, [(2.4, 3), (0.4, 1.6)], [(3.6, 3), (5.6, 1.6)], new=True),
    g(35, "Remembrance", "keeping", KEEP, KNOT, [(0, 1.5), (0, 4.5)], [(6, 1.5), (6, 4.5)], new=True),
    g(36, "Long Arm", "carrying", CARRY, GABLE, [(0.5, 4.5), (5.5, 4.5)], [(5.5, 3.5), (5.5, 5.5)], new=True),
]


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


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
