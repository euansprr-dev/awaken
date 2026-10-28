"""Proposed wild flora for the Wild Lands plan (a proof, not the mod's art: the mod's sprites will come from a
tools/art/specs spec through pixelart.py). 16x16 sprites in the game's own manner: grey blades the biome tints, plus
untinted parts (seed heads, flowers, berries) laid over after tinting.

Each sprite is (tinted RGBA, untinted RGBA). `compose(sprite, tint)` gives the final RGBA.
Run: python3 -I flora.py <out_dir>  -> one PNG per plant (x8, plains tint) for the page.
"""
import os
import sys

import numpy as np
from PIL import Image

PLAINS = (0x91 / 255, 0xBD / 255, 0x59 / 255)


def blank():
    return np.zeros((16, 16, 4), np.float32), np.zeros((16, 16, 4), np.float32)


def px(a, x, y, rgb, alpha=1.0):
    if 0 <= x < 16 and 0 <= y < 16:
        a[y, x, :3] = np.asarray(rgb, np.float32) / 255.0
        a[y, x, 3] = alpha


def grey(v):
    return (v, v, v)


def blade(t, rs, x0, y0, length, lean, shades=(108, 128, 146, 166, 186), y_floor=15, gaps=0.0):
    """One blade from (x0, y0) going up `length` pixels, drifting `lean` pixels sideways over its length."""
    pts = []
    for i in range(length):
        if gaps and i > 1 and rs.random() < gaps:
            continue
        f = i / max(1, length - 1)
        x = int(round(x0 + lean * f * f))
        y = y0 - i
        if y < 0:
            break
        s = shades[min(len(shades) - 1, int(f * len(shades)))]
        px(t, x, y, grey(s + rs.integers(-6, 7)))
        pts.append((x, y))
    return pts


def head(u, rs, x, y, size, colours, spread=1):
    """A seed head or plume: a short run of pixels above (x, y), alternating left and right."""
    for i in range(size):
        dx = (i % 2) * (1 if rs.random() < 0.5 else -1) * spread if i else 0
        px(u, x + dx, y - i, colours[rs.integers(0, len(colours))])


STRAW = [(214, 190, 120), (196, 168, 96), (232, 212, 150), (180, 150, 86)]
GOLD = [(222, 182, 88), (206, 160, 64), (240, 206, 120), (190, 140, 58)]
TAWNY = [(186, 132, 72), (160, 108, 58), (206, 156, 92)]


# ---- grasses -----------------------------------------------------------------------------------------------------------
def meadow_grass(seed=1):
    """Knee-high meadow grass: many fine blades, a few with straw tips. Taller and finer than the game's short grass."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for _ in range(13):
        x0 = int(rs.integers(1, 15))
        L = int(rs.integers(7, 14))
        pts = blade(t, rs, x0, 15, L, int(rs.integers(-3, 4)))
        if pts and rs.random() < 0.3:
            x, y = pts[-1]
            head(u, rs, x, y - 1, 2, STRAW)
    return t, u


def long_grass(part, seeding=0.0, seed=2):
    """Waist-high long grass, a two-block plant. part 0 bottom, 1 top. `seeding` 0..1: how many stems carry heads."""
    rs = np.random.default_rng(seed * 7 + part)
    t, u = blank()
    if part == 0:
        for _ in range(11):
            x0 = int(rs.integers(0, 16))
            blade(t, rs, x0, 15, int(rs.integers(12, 17)), int(rs.integers(-4, 5)), shades=(96, 112, 124, 136, 146), gaps=0.18)
    else:
        for _ in range(14):
            x0 = int(rs.integers(1, 15))
            L = int(rs.integers(5, 15))
            lean = int(rs.integers(-5, 6))
            pts = blade(t, rs, x0, 15, L, lean, shades=(140, 150, 160, 172, 188))
            if pts and rs.random() < 0.15 + 0.7 * seeding:
                x, y = pts[-1]
                head(u, rs, x, y - 1, int(rs.integers(2, 5)), GOLD if seeding > 0.5 else STRAW)
    return t, u


def wild_rye(part, seed=3):
    """Chest-high wild rye of the deep wild and the steppe, a three-block plant: stems, then long nodding heads."""
    rs = np.random.default_rng(seed * 11 + part)
    t, u = blank()
    if part < 2:
        for _ in range(10 if part == 0 else 8):
            x0 = int(rs.integers(0, 16))
            blade(t, rs, x0, 15, 16, int(rs.integers(-3, 4)), gaps=0.2, shades=(98, 110, 122, 134, 146) if part == 0 else (128, 138, 148, 156, 166))
    else:
        for _ in range(10):
            x0 = int(rs.integers(1, 15))
            L = int(rs.integers(6, 13))
            lean = int(rs.integers(-4, 5))
            pts = blade(t, rs, x0, 15, L, lean, shades=(150, 160, 170, 180, 190))
            if pts and rs.random() < 0.75:
                x, y = pts[-1]
                # a nodding head: down and to the side of the stem's tip
                d = 1 if lean >= 0 else -1
                for i in range(int(rs.integers(3, 6))):
                    px(u, x + d * (1 + i // 2), y + i // 2, GOLD[rs.integers(0, 4)])
                    if i % 2:
                        px(u, x + d * (2 + i // 2), y + i // 2, STRAW[rs.integers(0, 4)])
    return t, u


def trampled(seed=4, axis=0):
    """Grass pressed flat by a herd: blades lying along one way (drawn as a flat top texture, like a carpet)."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for _ in range(26):
        x0, y0 = int(rs.integers(0, 16)), int(rs.integers(0, 16))
        L = int(rs.integers(4, 9))
        for i in range(L):
            x, y = (x0 + i, y0 + (i // 4) * (1 if rs.random() < .5 else 0)) if axis == 0 else (x0, y0 + i)
            px(t, x % 16, y % 16, grey(int(118 + i * 6 + rs.integers(-8, 8))))
    for _ in range(6):
        px(u, int(rs.integers(0, 16)), int(rs.integers(0, 16)), STRAW[rs.integers(0, 4)])
    return t, u


def clover(seed=5):
    """A flat patch of clover: trefoil leaves and a few white or pink heads (flat, like the game's pink petals)."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for _ in range(14):
        cx, cy = int(rs.integers(1, 15)), int(rs.integers(1, 15))
        for dx, dy in ((0, -1), (-1, 0), (1, 0)):
            px(t, cx + dx, cy + dy, grey(int(rs.integers(130, 170))))
        px(t, cx, cy, grey(100))
    for _ in range(4):
        cx, cy = int(rs.integers(2, 14)), int(rs.integers(2, 14))
        c = [(240, 236, 228), (226, 214, 214)] if rs.random() < .6 else [(226, 150, 180), (200, 120, 160)]
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px(u, cx + dx, cy + dy, c[(dx + dy) % 2])
    return t, u


# ---- flowers and herbs ----------------------------------------------------------------------------------------------
def cow_parsley(part, seed=6):
    """Cow parsley, the white lace of pasture verges: two blocks, thin stems, flat white umbels at the top."""
    rs = np.random.default_rng(seed * 5 + part)
    t, u = blank()
    stems = [3, 7, 11, 13]
    for x0 in stems:
        if part == 0:
            blade(t, rs, x0, 15, 16, 0, shades=(110, 120, 130, 140, 150))
            px(t, x0 - 1, 9, grey(150)); px(t, x0 + 1, 6, grey(150))      # leaf nodes
        else:
            top = int(rs.integers(3, 8))
            blade(t, rs, x0, 15, 16 - top, int(rs.integers(-1, 2)), shades=(130, 140, 150, 160, 170))
            for dx in range(-2, 3):
                for dy in (0, -1):
                    if abs(dx) + abs(dy) < 3 and rs.random() < .9:
                        px(u, x0 + dx, top - 1 + dy, [(244, 242, 232), (226, 224, 212), (250, 250, 244)][rs.integers(0, 3)])
    return t, u


def foxglove(part, seed=7):
    """Foxglove: a tall spike of purple bells on the edge of the wild, two blocks."""
    rs = np.random.default_rng(seed * 3 + part)
    t, u = blank()
    for x0 in (5, 10):
        if part == 0:
            blade(t, rs, x0, 15, 16, 0, shades=(110, 120, 130, 138, 146))
            for y in (12, 13, 14):
                px(t, x0 - 2 + (y % 2), y, grey(150)); px(t, x0 + 2 - (y % 2), y, grey(140))
            for y in range(1, 8, 2):
                px(u, x0 + (1 if y % 4 == 1 else -1), y, (178, 92, 168)); px(u, x0 + (1 if y % 4 == 1 else -1), y + 1, (140, 64, 132))
        else:
            blade(t, rs, x0, 15, 13, 0, shades=(130, 140, 150, 160, 170))
            for y in range(4, 16, 2):
                d = 1 if (y // 2) % 2 else -1
                px(u, x0 + d, y, (196, 110, 186)); px(u, x0 + d, y + 1, (150, 70, 140))
                if y > 9:
                    px(u, x0 + 2 * d, y + 1, (236, 200, 228))
    return t, u


def thistle(seed=8):
    """Thistle: grey-green spiny leaves and a purple brush head, in rough pasture."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    blade(t, rs, 8, 15, 11, 0, shades=(120, 130, 140, 150, 160))
    for y in (13, 10, 8):
        for d in (-1, 1):
            for i in range(1, 5 - (13 - y) // 3):
                px(t, 8 + d * i, y - (i % 2), grey(int(140 + i * 8)))
    for dx in range(-1, 2):
        px(u, 8 + dx, 4, (120, 128, 96)); px(u, 8 + dx, 3, (186, 96, 190))
    px(u, 8, 2, (210, 120, 214)); px(u, 7, 2, (170, 80, 170)); px(u, 9, 2, (170, 80, 170))
    return t, u


def heather(seed=9):
    """Heather: a low cushion of tiny needles with pink-purple flower spikes, on heaths and highland."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for _ in range(12):
        blade(t, rs, int(rs.integers(2, 14)), 15, int(rs.integers(5, 10)), int(rs.integers(-3, 4)), shades=(70, 84, 98, 110, 120))
    for _ in range(90):
        x, y = int(rs.integers(0, 16)), int(rs.integers(5, 16))
        if (x - 8) ** 2 / 64 + (y - 15) ** 2 / 100 < 1:
            px(t, x, y, grey(int(rs.integers(80, 130))))
    for _ in range(34):
        x, y = int(rs.integers(1, 15)), int(rs.integers(5, 12))
        if (x - 8) ** 2 / 49 + (y - 14) ** 2 / 90 < 1:
            px(u, x, y, [(190, 110, 170), (166, 92, 150), (214, 140, 196)][rs.integers(0, 3)])
    return t, u


def gorse(seed=10):
    """Gorse: a dark spiny scrub studded with yellow flowers (heath and hill edges)."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for _ in range(9):
        blade(t, rs, int(rs.integers(2, 14)), 15, int(rs.integers(8, 15)), int(rs.integers(-3, 4)), shades=(64, 74, 84, 94, 104))
    for _ in range(50):
        x, y = int(rs.integers(1, 15)), int(rs.integers(2, 14))
        if (x - 8) ** 2 / 49 + (y - 9) ** 2 / 42 < 1:
            px(t, x, y, grey(int(rs.integers(70, 110))))
    for _ in range(16):
        x, y = int(rs.integers(2, 14)), int(rs.integers(2, 12))
        if (x - 8) ** 2 / 36 + (y - 8) ** 2 / 36 < 1:
            px(u, x, y, [(240, 204, 60), (222, 180, 40), (250, 226, 110)][rs.integers(0, 3)])
    return t, u


# ---- shrubs, ferns, water's edge ------------------------------------------------------------------------------------
def bramble(seed=11, fruit=True):
    """Bramble: arching thorny canes with leaves and blackberries; thickets of it slow you and snag."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    tips = []
    for x0, d in ((3, 1), (12, -1), (7, 1), (9, -1)):
        L = 22
        for i in range(L):
            f = i / (L - 1)
            x = int(round(x0 + d * 7 * f))
            y = int(round(15 - 12 * np.sin(f * 2.6)))
            px(u, x, y, (110, 58, 56) if i % 4 else (150, 86, 76))   # cane, reddish, untinted
            if i % 4 == 2:
                for dx, dy in ((0, -1), (1, -1), (0, -2), (1, -2), (-1, -1)):
                    if rs.random() < .85:
                        px(t, x + dx * d, y + dy, grey(int(rs.integers(96, 156))))
        tips.append((x, y))
    if fruit:
        for _ in range(5):
            x, y = int(rs.integers(2, 13)), int(rs.integers(4, 11))
            px(u, x, y, (52, 26, 58)); px(u, x + 1, y, (80, 40, 88)); px(u, x, y + 1, (30, 16, 36)); px(u, x + 1, y + 1, (52, 26, 58))
    return t, u


def bracken(part, seed=12, autumn=False):
    """Bracken: a big two-block fern of the deep wild and the forest edge; turns tawny in places."""
    rs = np.random.default_rng(seed * 13 + part)
    t, u = blank()
    layer = u if autumn else t
    col = (lambda: TAWNY[rs.integers(0, 3)]) if autumn else (lambda: grey(int(rs.integers(110, 170))))
    if part == 0:
        for x0 in (4, 8, 12):
            blade(t if not autumn else u, rs, x0, 15, 16, 0, shades=(100, 110, 120, 130, 140)) if not autumn else \
                [px(u, x0, y, (120, 90, 60)) for y in range(16)]
        for y in range(2, 9, 2):
            for i in range(1, 6):
                px(layer, 8 - i, y + i // 2, col()); px(layer, 8 + i, y + i // 2, col())
    else:
        for i in range(12):
            px(layer, 8, 15 - i, col() if autumn else grey(120))
        for y in range(3, 15, 2):
            w = 6 - abs(y - 9) // 2
            for i in range(1, w + 1):
                px(layer, 8 - i, y + i // 3, col()); px(layer, 8 + i, y + i // 3, col())
    return t, u


def reeds(part, seed=13):
    """Reeds and bulrush at the water's edge, two blocks: stiff blades and brown cattail heads."""
    rs = np.random.default_rng(seed * 17 + part)
    t, u = blank()
    for _ in range(9):
        x0 = int(rs.integers(1, 15))
        blade(t, rs, x0, 15, 16 if part == 0 else int(rs.integers(6, 15)), int(rs.integers(-1, 2)) if part else 0,
              shades=(110, 124, 136, 148, 160))
    if part == 1:
        for x0 in (4, 10):
            for y in range(4, 9):
                px(u, x0, y, (110, 70, 40) if y % 2 else (90, 56, 32)); px(u, x0 + 1, y, (124, 82, 48))
            px(u, x0, 3, (150, 140, 100)); px(u, x0, 2, (150, 140, 100))
    return t, u


def hawthorn_leaves(seed=14, blossom=True):
    """Hawthorn for hedgerows: small dense leaves (tinted, like the game's leaves) with white blossom and red haws."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for y in range(16):
        for x in range(16):
            r = rs.random()
            if r < 0.80:
                px(t, x, y, grey(int(rs.integers(70, 150))))
    if blossom:
        for _ in range(14):
            x, y = int(rs.integers(0, 15)), int(rs.integers(0, 15))
            px(u, x, y, (246, 244, 236)); px(u, x + 1, y, (226, 220, 210))
        for _ in range(4):
            px(u, int(rs.integers(0, 16)), int(rs.integers(0, 16)), (176, 40, 40))
    return t, u


def bracket_fungus(seed=15):
    """Shelf fungus on a dead log's side: a few tan shelves (drawn on a side face)."""
    rs = np.random.default_rng(seed)
    t, u = blank()
    for (x0, y0, w) in ((2, 4, 6), (8, 8, 7), (3, 12, 5)):
        for i in range(w):
            px(u, x0 + i, y0, (214, 178, 120) if i % 2 else (196, 156, 100))
            px(u, x0 + i, y0 + 1, (120, 84, 50))
    return t, u


def antler_shed(seed=16):
    """A shed antler lying in the grass (a sign, and a find for the Hunting craft)."""
    t, u = blank()
    pts = [(2, 12), (3, 11), (4, 11), (5, 10), (6, 10), (7, 9), (8, 9), (9, 8), (10, 8), (11, 7), (12, 6), (13, 5)]
    for x, y in pts:
        px(u, x, y, (226, 214, 186)); px(u, x, y + 1, (178, 162, 130))
    for (x, y, L) in ((5, 10, 3), (9, 8, 3), (12, 6, 2)):
        for i in range(1, L + 1):
            px(u, x - i // 2, y - i, (236, 226, 200))
    return t, u


def compose(sprite, tint):
    t, u = sprite
    out = t.copy()
    out[..., :3] *= np.asarray(tint, np.float32)
    m = u[..., 3:4]
    out[..., :3] = out[..., :3] * (1 - m) + u[..., :3] * m
    out[..., 3] = np.maximum(t[..., 3], u[..., 3])
    return out


def stack(*parts):
    """Top part first: a double or triple plant as one tall image."""
    return np.concatenate(parts, axis=0)


CATALOGUE = [
    ("meadow_grass", lambda tn: compose(meadow_grass(), tn)),
    ("long_grass", lambda tn: stack(compose(long_grass(1, 0.2), tn), compose(long_grass(0), tn))),
    ("long_grass_seeding", lambda tn: stack(compose(long_grass(1, 0.9, seed=5), tn), compose(long_grass(0, seed=5), tn))),
    ("wild_rye", lambda tn: stack(compose(wild_rye(2), tn), compose(wild_rye(1), tn), compose(wild_rye(0), tn))),
    ("trampled_grass", lambda tn: compose(trampled(), tn)),
    ("clover", lambda tn: compose(clover(), tn)),
    ("cow_parsley", lambda tn: stack(compose(cow_parsley(1), tn), compose(cow_parsley(0), tn))),
    ("foxglove", lambda tn: stack(compose(foxglove(1), tn), compose(foxglove(0), tn))),
    ("thistle", lambda tn: compose(thistle(), tn)),
    ("heather", lambda tn: compose(heather(), tn)),
    ("gorse", lambda tn: compose(gorse(), tn)),
    ("bramble", lambda tn: compose(bramble(), tn)),
    ("bracken", lambda tn: stack(compose(bracken(1), tn), compose(bracken(0), tn))),
    ("bracken_autumn", lambda tn: stack(compose(bracken(1, autumn=True), tn), compose(bracken(0, autumn=True), tn))),
    ("reeds", lambda tn: stack(compose(reeds(1), tn), compose(reeds(0), tn))),
    ("hawthorn_leaves", lambda tn: compose(hawthorn_leaves(), (0.42, 0.62, 0.28))),
    ("bracket_fungus", lambda tn: compose(bracket_fungus(), tn)),
    ("antler_shed", lambda tn: compose(antler_shed(), tn)),
]


def main(out):
    os.makedirs(out, exist_ok=True)
    for name, make in CATALOGUE:
        a = make(PLAINS)
        im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA")
        im = im.resize((im.width * 8, im.height * 8), Image.NEAREST)
        im.save(os.path.join(out, "flora_%s.png" % name), optimize=True)
        print(name, im.size)


if __name__ == "__main__":
    main(sys.argv[1])
