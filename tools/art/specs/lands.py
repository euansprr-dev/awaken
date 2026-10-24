"""Awakened Lands (docs/modules/lands.md): bark, leaves and ground for the Awakened biomes, and the falling-leaf sprites.

  python3 tools/art/build_all.py lands          writes the textures
  python3 tools/art/specs/lands.py preview <dir> tiles each block 3x3 (as it sits in the world) into a contact sheet
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from pixelart import Canvas, hex_rgba, quantize, rng_for, value_noise  # noqa: E402

RAMPS = {
    # Elderwood: pale silver-cream bark with dark fissures, warm cream heartwood, gold-green leaves
    "elder_bark": ["#2f2a24", "#4c463d", "#716a5e", "#968e7e", "#b9b19d", "#d8d0bb", "#ece6d2"],
    "elder_wood": ["#5a4630", "#7d6444", "#a3875e", "#c4a97c", "#dcc59a", "#efe0bd"],
    "elder_leaf": ["#1f3012", "#30481a", "#456224", "#5f7c2c", "#829435", "#aaa83f", "#d6c35a"],
    # Lumen Forest: ash-white bark, blue-grey heartwood, deep teal leaves with glowing specks
    "lumen_bark": ["#3b4550", "#56616d", "#77828e", "#9aa4ae", "#bcc4cb", "#dbe1e5", "#f0f3f5"],
    "lumen_wood": ["#36404c", "#4e5a68", "#6a7786", "#8995a3", "#a9b3bf", "#c9d1da"],
    "lumen_leaf": ["#04191c", "#08292e", "#0d3c42", "#135159", "#1b6a72", "#27878d", "#3aa6a8"],
    "lumen_glow": ["#3fd6e0", "#7ff0f2", "#c8fffb", "#ffffff"],
    "lumen_moss": ["#061a1b", "#0b2a2b", "#123c3c", "#1a5050", "#246664", "#307d78"],
}


def rp(name):
    return [hex_rgba(c) for c in RAMPS[name]]


def shade(c, f):
    return (max(0, min(255, round(c[0] * f))), max(0, min(255, round(c[1] * f))), max(0, min(255, round(c[2] * f))), c[3])


# ---------------------------------------------------------------------------------------------------------------------
# Bark


def bark(ramp, seed, fissures=4, plates=True):
    """Furrowed bark: dark fissures that wander, meet and part again (ridges interlace like old ash or oak), ridges lit
    from the left with a darker lip on their right, a little moss-dark in the deepest furrows."""
    c = Canvas()
    r = rp(ramp)
    rng = rng_for("bark", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    depth = [[0.0] * 16 for _ in range(16)]
    starts = sorted(rng.sample(range(16), fissures))
    for fx in starts:
        x = float(fx)
        drift = rng.choice((-1, 1)) * rng.uniform(0.15, 0.4)
        for y in range(16):
            # drift slowly one way, then the other: fissures lean, meet a neighbour, part again
            if rng.random() < 0.18:
                drift = -drift
            x += drift + rng.uniform(-0.25, 0.25)
            xi = int(round(x)) % 16
            depth[y][xi] = 1.0
            if n[y][xi] > 0.5:
                depth[y][(xi + 1) % 16] = max(depth[y][(xi + 1) % 16], 0.6)
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            if depth[y][x] >= 1.0:
                idx = 0 if v < 0.55 else 1
            elif depth[y][x] > 0:
                idx = 2
            else:
                idx = 3 + quantize(v, len(r) - 4)
                if depth[y][(x - 1) % 16] >= 1.0:
                    idx = min(len(r) - 1, idx + 1)  # lit left edge of a ridge
                if depth[y][(x + 1) % 16] >= 1.0:
                    idx = max(2, idx - 1)  # shadowed right lip
            c.set(x, y, r[idx])
    if plates:
        for _ in range(4):
            x, y = rng.randrange(16), rng.randrange(16)
            for dx in range(rng.choice((2, 3))):
                if depth[y][(x + dx) % 16] == 0:
                    c.set((x + dx) % 16, y, r[2])
    return c


def stripped(wood_ramp, seed):
    """Stripped log: smooth heartwood with long vertical grain and a few darker streaks."""
    c = Canvas()
    w = rp(wood_ramp)
    rng = rng_for("stripped", seed)
    cols = [rng.random() for _ in range(16)]
    n = value_noise(16, 16, rng, scale=2, octaves=2)
    for y in range(16):
        for x in range(16):
            v = 0.55 * cols[x] + 0.45 * n[y][x]
            c.set(x, y, w[2 + quantize(v, len(w) - 2)])
    for _ in range(3):
        x, y0, ln = rng.randrange(16), rng.randrange(16), rng.randrange(4, 9)
        for y in range(y0, y0 + ln):
            c.set(x, y % 16, w[1])
    return c


def log_top(bark_ramp, wood_ramp, seed):
    """End grain: rings in the heartwood, a bark rim."""
    c = Canvas()
    b, w = rp(bark_ramp), rp(wood_ramp)
    rng = rng_for("top", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, b[2] if rng.random() < 0.7 else b[1])
                continue
            d = math.hypot(x - 7.5, y - 7.5) + n[y][x] * 1.2
            ring = int(d) % 3 == 0
            idx = len(w) - 2 - int(d / 2.4)
            idx = max(1, min(len(w) - 1, idx - (1 if ring else 0)))
            c.set(x, y, w[idx])
    return c


def planks(wood_ramp, seed):
    """Four boards with grain and seams."""
    c = Canvas()
    w = rp(wood_ramp)
    rng = rng_for("planks", seed)
    for board in range(4):
        y0 = board * 4
        offset = rng.randrange(16)
        grain = value_noise(16, 4, rng_for("grain", seed, board), scale=2, octaves=2)
        for y in range(y0, y0 + 4):
            for x in range(16):
                v = grain[y - y0][(x + offset) % 16]
                idx = 2 + quantize(v, len(w) - 3)
                if y == y0 + 3:
                    idx = 1  # seam under each board
                elif y == y0:
                    idx = min(len(w) - 1, idx + 1)  # lit top edge
                c.set(x, y, w[idx])
        # one butt joint per board
        jx = (offset + 7) % 16
        for y in range(y0, y0 + 3):
            c.set(jx, y, w[1])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Leaves


def leaves(ramp, seed, holes=0.22, glow=None):
    """A canopy tile: small leaf clusters lit from the top-left, gaps to the sky. Returns (leaves, glow layer or None)."""
    c = Canvas()
    g = Canvas() if glow else None
    r = rp(ramp)
    rng = rng_for("leaves", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    # leaves are 2x2 to 3x2 blobs; place many, light the top-left pixel of each
    filled = [[False] * 16 for _ in range(16)]
    for _ in range(70):
        x, y = rng.randrange(16), rng.randrange(16)
        wdt, hgt = rng.choice(((1, 2), (2, 1), (2, 2), (2, 2), (3, 2)))
        base = 1 + quantize(n[y][x], len(r) - 2)
        for dy in range(hgt):
            for dx in range(wdt):
                px, py = (x + dx) % 16, (y + dy) % 16
                idx = base + (1 if dy == 0 and dx == 0 else 0) - (1 if dy == hgt - 1 and dx == wdt - 1 else 0)
                c.set(px, py, r[max(0, min(len(r) - 1, idx))])
                filled[py][px] = True
    # dark understory where nothing landed, then cut holes in the darkest spots
    for y in range(16):
        for x in range(16):
            if not filled[y][x]:
                c.set(x, y, r[0] if n[y][x] < 0.5 else r[1])
    cells = sorted(((n[y][x] + rng.random() * 0.35, x, y) for y in range(16) for x in range(16) if not filled[y][x]))
    for _, x, y in cells[: int(256 * holes)]:
        c.set(x, y, (0, 0, 0, 0))
    if glow:
        gr = [hex_rgba(h) for h in RAMPS[glow]]
        lit = [(x, y) for y in range(16) for x in range(16) if c.get(x, y)[3] > 0]
        rng.shuffle(lit)
        for x, y in lit[:9]:
            k = rng.randrange(len(gr))
            g.set(x, y, gr[k])
            c.set(x, y, shade(gr[0], 0.75))  # the speck shows (dimmer) even where the glow layer isn't drawn
    return c, g


# ---------------------------------------------------------------------------------------------------------------------
# Ground


def moss_carpet(ramp, seed, glow=None):
    c = Canvas()
    g = Canvas() if glow else None
    r = rp(ramp)
    rng = rng_for("moss", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=3)
    for y in range(16):
        for x in range(16):
            idx = quantize(n[y][x] * 0.8 + rng.random() * 0.2, len(r))
            c.set(x, y, r[idx])
    if glow:
        gr = [hex_rgba(h) for h in RAMPS[glow]]
        for _ in range(11):
            x, y = rng.randrange(16), rng.randrange(16)
            g.set(x, y, gr[rng.randrange(2)])
            c.set(x, y, shade(gr[0], 0.6))
    return c, g


def lumen_cap(seed):
    """Three small glowing mushrooms (cross model): pale curved stems, teal domes with a glowing gill rim underneath."""
    c, g = Canvas(), Canvas()
    stem = rp("lumen_bark")
    gl = [hex_rgba(h) for h in RAMPS["lumen_glow"]]
    cap = rp("lumen_leaf")
    for cx, top, h, rad, lean in ((4, 8, 7, 2, -1), (10, 4, 11, 3, 1), (13, 11, 4, 1, 0)):
        for i, y in enumerate(range(top + 1, min(16, top + 1 + h))):
            x = cx + (lean if i > h // 2 else 0)
            c.set(x, y, stem[5] if i % 3 else stem[4])
        # the dome: widest at its rim (row top), rounded above
        for dy in range(-rad, 1):
            half = rad if dy == 0 else max(0, rad + dy + (1 if rad > 1 else 0))
            for dx in range(-half, half + 1):
                x, y = cx + dx, top + dy
                lit = dx < 0 and dy < 0
                c.set(x, y, cap[6] if lit else cap[4] if dy < 0 else cap[3])
        # the gills glow in a line under the rim
        for dx in range(-rad + (1 if rad > 1 else 0), rad + (0 if rad > 1 else 1)):
            g.set(cx + dx, top + 1 if dx != 0 else top, gl[1] if dx % 2 else gl[2])
            c.set(cx + dx, top + 1 if dx != 0 else top, shade(gl[0], 0.7))
        g.set(cx - rad // 2, top - rad + 1, gl[3])  # one bright spot on the dome
    return c, g


def stripped_top(wood_ramp, seed):
    """End grain of a stripped log: the same rings, a heartwood rim instead of bark."""
    c = log_top("lumen_bark" if wood_ramp.startswith("lumen") else "elder_bark", wood_ramp, seed)
    w = rp(wood_ramp)
    rng = rng_for("stripped_top", seed)
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, w[2] if rng.random() < 0.7 else w[1])
    return c


def sapling(leaf_ramp, bark_ramp, seed, glow=None):
    """A sapling for the cross model: a pale stem that forks once, leaf tufts lit from the top-left."""
    c = Canvas()
    lf, bk = rp(leaf_ramp), rp(bark_ramp)
    rng = rng_for("sapling", seed)
    x = 7.5
    for y in range(15, 5, -1):
        if y < 11 and rng.random() < 0.3:
            x += rng.choice((-0.6, 0.6))
        xi = int(round(x))
        c.set(xi, y, bk[5] if y % 3 else bk[4])
        if y > 12:
            c.set(xi + 1, y, bk[3])
    top = int(round(x))
    for sign in (-1, 1):
        for i in range(3):
            c.set(top + sign * (i + 1), 8 - i, bk[4])
    # tufts: rounded clusters at the fork tips and the crown
    for cx, cy, r in ((top - 4, 5, 2.6), (top + 4, 4, 2.8), (top, 2, 2.4)):
        for yy in range(16):
            for xx in range(16):
                d = math.hypot(xx - cx, (yy - cy) * 1.15)
                if d <= r + rng.uniform(-0.4, 0.3):
                    lit = (xx - cx) + (yy - cy) < -1
                    idx = len(lf) - 1 if lit else (len(lf) - 3 if d < r * 0.6 else len(lf) - 4)
                    c.set(xx, yy, lf[max(1, min(len(lf) - 1, idx))])
    if glow:
        gr = [hex_rgba(h) for h in RAMPS[glow]]
        lit = [(xx, yy) for yy in range(8) for xx in range(16) if c.get(xx, yy)[3] > 0]
        rng.shuffle(lit)
        for xx, yy in lit[:4]:
            c.set(xx, yy, gr[rng.randrange(1, 3)])
    return c


def lumen_vines(seed, tip):
    """Hanging lumen vines for the cross model: beaded strands of deep teal; a tip ends each strand in a glowing bulb.
    Returns (vines, glow layer or None)."""
    c = Canvas()
    g = Canvas() if tip else None
    lf = rp("lumen_leaf")
    gl = [hex_rgba(h) for h in RAMPS["lumen_glow"]]
    rng = rng_for("vines", seed, tip)
    for sx in (3, 7, 10, 13):
        x = sx + rng.choice((-1, 0, 0, 1))
        end = 16 if not tip else rng.randrange(8, 13)
        for y in range(0, end):
            if rng.random() < 0.15:
                x = max(1, min(13, x + rng.choice((-1, 1))))
            bead = (y + sx) % 3 == 0
            c.set(x, y, lf[5] if bead else lf[3] if y % 2 else lf[4])
            if bead and rng.random() < 0.6:
                c.set(x + rng.choice((-1, 1)), y, lf[2])
        if tip:
            # the bulb: a 2x3 drop that glows from inside
            for dy in range(3):
                for dx in (0, 1):
                    px, py = x + dx, end + dy
                    if py < 16:
                        core = dy == 1
                        c.set(px, py, shade(gl[0], 0.8 if core else 0.55))
                        g.set(px, py, gl[2] if core else gl[1])
    return c, g


def glow_tile():
    """A solid glow tile: the emissive pixels of Lumen models sample it, so every mip level keeps the same colour."""
    c = Canvas()
    gl = [hex_rgba(h) for h in RAMPS["lumen_glow"]]
    rng = rng_for("glow", "tile")
    for y in range(16):
        for x in range(16):
            c.set(x, y, gl[1] if rng.random() < 0.7 else gl[2])
    return c


def speck_pixels(name):
    """The glowing pixels of a speck layer (lumen_leaves_glow, lumen_moss_glow) as (x, y) texture coordinates; the
    content generator turns each into a tiny emissive model face over the matching dim pixel of the base texture."""
    if name == "lumen_leaves":
        _, g = leaves("lumen_leaf", "lumen", holes=0.24, glow="lumen_glow")
    else:
        _, g = moss_carpet("lumen_moss", "lumen", glow="lumen_glow")
    return [(x, y) for y in range(16) for x in range(16) if g.get(x, y)[3] > 0]


# ---------------------------------------------------------------------------------------------------------------------
# Falling leaf sprites (white, tinted per tree at spawn; four tumble frames each)


def leaf_sprite(seed, shape):
    c = Canvas(8, 8)
    base = [(255, 255, 255, 255), (222, 222, 222, 255), (190, 190, 190, 255)]
    pts = {
        "oval": [(2, 1), (3, 1), (1, 2), (2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (4, 5)],
        "long": [(3, 0), (3, 1), (2, 2), (3, 2), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (3, 5), (3, 6)],
        "round": [(2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (4, 3), (5, 3), (2, 4), (3, 4), (4, 4), (3, 5)],
        "edge": [(1, 3), (2, 3), (3, 3), (4, 3), (5, 3), (6, 3)],
    }[shape]
    for i, (x, y) in enumerate(pts):
        c.set(x, y, base[0] if i < 2 else base[1] if (x + y) % 3 else base[2])
    return c


def build(out):
    out.block("elder_log", bark("elder_bark", "elder"))
    out.block("elder_log_top", log_top("elder_bark", "elder_wood", "elder"))
    out.block("stripped_elder_log", stripped("elder_wood", "elder"))
    out.block("elder_planks", planks("elder_wood", "elder"))
    leaf, _ = leaves("elder_leaf", "elder", holes=0.2)
    out.block("elder_leaves", leaf)
    out.block("lumen_log", bark("lumen_bark", "lumen", fissures=3))
    out.block("lumen_log_top", log_top("lumen_bark", "lumen_wood", "lumen"))
    out.block("lumen_planks", planks("lumen_wood", "lumen"))
    leaf, glow = leaves("lumen_leaf", "lumen", holes=0.24, glow="lumen_glow")
    out.block("lumen_leaves", leaf)
    out.block("lumen_leaves_glow", glow)
    moss, glow = moss_carpet("lumen_moss", "lumen", glow="lumen_glow")
    out.block("lumen_moss", moss)
    out.block("lumen_moss_glow", glow)
    cap, glow = lumen_cap("lumen")
    out.block("lumen_cap", cap)
    out.block("lumen_cap_glow", glow)
    out.block("stripped_elder_log_top", stripped_top("elder_wood", "elder"))
    out.block("stripped_lumen_log", stripped("lumen_wood", "lumen"))
    out.block("stripped_lumen_log_top", stripped_top("lumen_wood", "lumen"))
    out.block("elder_sapling", sapling("elder_leaf", "elder_bark", "elder"))
    out.block("lumen_sapling", sapling("lumen_leaf", "lumen_bark", "lumen", glow="lumen_glow"))
    vine, _ = lumen_vines("lumen", False)
    out.block("lumen_vines", vine)
    vine, glow = lumen_vines("lumen", True)
    out.block("lumen_vines_tip", vine)
    out.block("lumen_vines_tip_glow", glow)
    out.block("lumen_glow", glow_tile())
    for i, shape in enumerate(("oval", "long", "round", "edge")):
        out.particle(f"falling_leaf_{i}", leaf_sprite("leaf", shape))


def preview(dest):
    """Each block tiled 3x3 at 8x, glow layers composited, on a mid-grey card; one sheet."""
    from PIL import Image, ImageDraw

    class Mem:
        def __init__(self):
            self.tex = {}

        def block(self, name, c, frametime=None):
            self.tex[name] = c.image()

        def particle(self, name, c):
            self.tex["p_" + name] = c.image()

    m = Mem()
    build(m)
    names = [k for k in m.tex if not k.endswith("_glow") and not k.startswith("p_")]
    tile = 16 * 3 * 4
    cols = 4
    rows = math.ceil(len(names) / cols)
    sheet = Image.new("RGBA", (cols * (tile + 16) + 16, rows * (tile + 34) + 16), (60, 64, 72, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        img = m.tex[name]
        if name + "_glow" in m.tex:
            img = Image.alpha_composite(img, m.tex[name + "_glow"])
        t = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
        for ty in range(3):
            for tx in range(3):
                t.paste(img, (tx * 16, ty * 16))
        t = t.resize((tile, tile), Image.NEAREST)
        x, y = 16 + (i % cols) * (tile + 16), 16 + (i // cols) * (tile + 34)
        sheet.alpha_composite(t, (x, y))
        d.text((x, y + tile + 4), name, fill=(255, 230, 120, 255))
    os.makedirs(dest, exist_ok=True)
    sheet.save(os.path.join(dest, "lands_blocks.png"))
    print(os.path.join(dest, "lands_blocks.png"))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
