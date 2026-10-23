"""Mobs module textures: for every creature a skin layer (theme colours painted over the re-tinted vanilla body) and a
glow layer (eyes, runes, cracks; drawn emissive), full textures for our own models (Serpent, Listener, Bell, Lantern,
Shade, and the player-shaped Unit Zero / Signal Echo / Faceless), spawn eggs and boss part icons.

The creature list is read from the Java bestiary (tools/content/mobs_bestiary.py). Vanilla textures are never copied:
only their sizes and where the face sits are known here. Custom model UV boxes mirror mobs/client/model/AwModels.java.
Run: python3 tools/art/build_all.py mobs
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "../../content")))
import mobs_bestiary as B  # noqa: E402
from pixelart import *  # noqa: E402,F401
from pixelart import _outline  # noqa: E402

# ---------------------------------------------------------------------------------------------------------------------
# Vanilla bodies: png size, declared layer size (UV units), face rectangles and eye pixels in UV units.
# eyes: (u, v, w, h); face: (u, v, w, h) kept clear of skin patches so faces stay readable.
VANILLA = {
    "ZOMBIE": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "HUSK": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "DROWNED": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "SKELETON": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 11, 2, 2), (13, 11, 2, 2)]),
    "STRAY": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 11, 2, 2), (13, 11, 2, 2)]),
    "WITHER_SKELETON": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 11, 2, 2), (13, 11, 2, 2)]),
    "BOGGED": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 11, 2, 2), (13, 11, 2, 2)]),
    "PIGLIN": ((64, 64), (64, 64), (8, 8, 10, 8), [(9, 11, 2, 1), (15, 11, 2, 1)]),
    "PIGLIN_BRUTE": ((64, 64), (64, 64), (8, 8, 10, 8), [(9, 11, 2, 1), (15, 11, 2, 1)]),
    "MIMIC": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "ENDERMAN": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "VINDICATOR": ((64, 64), (64, 64), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "EVOKER": ((64, 64), (64, 64), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "PILLAGER": ((64, 64), (64, 64), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "ILLUSIONER": ((64, 64), (64, 64), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "WITCH": ((64, 128), (64, 128), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "SPIDER": ((64, 32), (64, 32), (40, 12, 8, 8), [(41, 14, 2, 1), (45, 14, 2, 1), (42, 16, 1, 1), (45, 16, 1, 1), (40, 13, 1, 1), (47, 13, 1, 1)]),
    "CAVE_SPIDER": ((64, 32), (64, 32), (40, 12, 8, 8), [(41, 14, 2, 1), (45, 14, 2, 1), (42, 16, 1, 1), (45, 16, 1, 1)]),
    "WOLF": ((64, 32), (64, 32), (4, 4, 6, 6), [(5, 6, 1, 1), (8, 6, 1, 1)]),
    "SNOW_WOLF": ((64, 32), (64, 32), (4, 4, 6, 6), [(5, 6, 1, 1), (8, 6, 1, 1)]),
    "HOGLIN": ((128, 64), (128, 64), (80, 20, 14, 6), [(81, 22, 2, 1), (91, 22, 2, 1)]),
    "ZOGLIN": ((128, 64), (128, 64), (80, 20, 14, 6), [(81, 22, 2, 1), (91, 22, 2, 1)]),
    "POLAR_BEAR": ((128, 64), (128, 64), (7, 7, 7, 7), [(8, 9, 1, 1), (12, 9, 1, 1)]),
    "RAVAGER": ((128, 128), (128, 128), (16, 16, 16, 20), [(19, 22, 3, 2), (26, 22, 3, 2)]),
    "IRON_GOLEM": ((128, 128), (128, 128), (8, 8, 8, 10), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "WARDEN": ((128, 128), (128, 128), (10, 42, 16, 16), [(13, 48, 2, 1), (21, 48, 2, 1)]),
    "CREAKING": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "CREEPER": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 10, 2, 2), (13, 10, 2, 2)]),
    "SILVERFISH": ((64, 32), (64, 32), (0, 0, 0, 0), []),
    "ENDERMITE": ((64, 32), (64, 32), (0, 0, 0, 0), []),
    "SLIME": ((64, 32), (64, 32), (8, 8, 8, 8), [(32, 0, 8, 4), (32, 4, 8, 4)]),
    "MAGMA": ((64, 64), (64, 64), (0, 0, 0, 0), []),
    "BREEZE": ((32, 32), (32, 32), (0, 0, 0, 0), []),
    "VEX": ((32, 32), (32, 32), (5, 5, 5, 5), [(6, 7, 1, 1), (8, 7, 1, 1)]),
    "BLAZE": ((64, 32), (64, 32), (8, 8, 8, 8), [(9, 12, 2, 1), (13, 12, 2, 1)]),
    "PHANTOM": ((64, 64), (64, 64), (0, 0, 0, 0), []),
    "BEE": ((64, 64), (64, 64), (10, 10, 7, 7), [(11, 12, 1, 2), (15, 12, 1, 2)]),
    "GUARDIAN": ((64, 64), (64, 64), (0, 0, 0, 0), [(8, 0, 2, 2)]),
    "ELDER_GUARDIAN": ((64, 64), (64, 64), (0, 0, 0, 0), [(8, 0, 2, 2)]),
    "GHAST": ((128, 64), (64, 32), (16, 16, 16, 16), [(19, 21, 3, 2), (26, 21, 3, 2)]),
    "WITHER": ((64, 64), (64, 64), (8, 8, 8, 8), [(9, 11, 2, 1), (13, 11, 2, 1)]),
}

# Our models: name -> (size, [(name, u, v, w, h, d, kind)]). Keep in sync with AwModels.java.
CUSTOM = {
    "SERPENT": ((64, 64), [("head", 0, 0, 8, 6, 10, "head"), ("fin", 36, 0, 1, 4, 8, "fin"), ("jaw", 0, 16, 7, 2, 8, "jaw"),
                            ("segA", 0, 26, 6, 6, 8, "body"), ("segB", 28, 26, 5, 5, 8, "body"), ("segC", 0, 42, 4, 4, 8, "body"),
                            ("tail", 26, 42, 2, 2, 8, "body"), ("tailfin", 46, 42, 1, 6, 6, "fin")]),
    "LISTENER": ((64, 64), [("head", 0, 0, 8, 8, 8, "blind"), ("earL", 32, 0, 1, 12, 10, "ear"), ("earR", 32, 22, 1, 12, 10, "ear"),
                             ("body", 0, 16, 8, 14, 4, "ribs"), ("armR", 0, 36, 3, 20, 3, "limb"), ("armL", 12, 36, 3, 20, 3, "limb"),
                             ("legR", 24, 44, 3, 16, 3, "limb"), ("legL", 36, 44, 3, 16, 3, "limb")]),
    "BELL": ((128, 128), [("legR", 0, 64, 6, 14, 6, "armour"), ("legL", 24, 64, 6, 14, 6, "armour"), ("body", 0, 32, 16, 16, 10, "robe"),
                           ("armR", 48, 64, 5, 20, 5, "armour"), ("armL", 68, 64, 5, 20, 5, "armour"), ("bell", 0, 0, 14, 13, 14, "bell"),
                           ("rim", 56, 0, 18, 3, 18, "rim"), ("spike", 100, 32, 3, 4, 3, "bell"), ("clapper", 0, 90, 4, 6, 4, "clapper")]),
    "LANTERN": ((64, 64), [("robe", 0, 20, 10, 18, 6, "robe"), ("armR", 32, 20, 3, 12, 3, "sleeve"), ("armL", 44, 20, 3, 12, 3, "sleeve"),
                            ("head", 0, 0, 8, 10, 8, "cage"), ("handle", 48, 0, 2, 3, 2, "metal"), ("core", 32, 0, 4, 4, 4, "core")]),
    "SHADE": ((64, 64), [("robe", 0, 40, 8, 12, 4, "robe"), ("torso", 0, 20, 10, 14, 6, "robe"), ("head", 0, 0, 8, 10, 8, "hood"),
                          ("armR", 32, 20, 3, 18, 3, "sleeve"), ("armL", 44, 20, 3, 18, 3, "sleeve"),
                          ("haloA", 32, 0, 12, 1, 1, "halo"), ("haloB", 32, 4, 1, 1, 12, "halo")]),
    "HUMAN": ((64, 64), [("head", 0, 0, 8, 8, 8, "face"), ("body", 16, 16, 8, 12, 4, "torso"), ("armR", 40, 16, 4, 12, 4, "limb"),
                          ("armL", 32, 48, 4, 12, 4, "limb"), ("legR", 0, 16, 4, 12, 4, "limb"), ("legL", 16, 48, 4, 12, 4, "limb")]),
}

# Theme look: (skin ramp, accent ramp, pattern)
STYLE = {
    "BELL": ("stone", "spirit", "runes"), "FUNGAL": ("fungal", "spore", "spores"), "EMBER": ("ember", "amber", "cracks"),
    "DROWNED": ("water", "signal", "drips"), "CLOCKWORK": ("brass", "amber", "rivets"), "GLASS": ("sand", "mirror", "shards"),
    "HIVE": ("amber", "spore", "combs"), "TEMPLE": ("moss", "leaf", "vines"), "LANTERN": ("wood", "amber", "lanterns"),
    "FROST": ("frost", "cloud", "rime"), "MIRROR": ("mirror", "signal", "shards"), "PRECURSOR": ("precursor", "signal", "circuits"),
    "NIGHTMARE": ("dream", "crimson", "eyes"), "SKY": ("cloud", "signal", "wisps"), "ABYSS": ("abyss", "spirit", "void"),
    "RIM": ("moss", "leaf", "spores"), "GARDENS": ("leaf", "spore", "vines"), "CHOIR": ("water", "frost", "drips"),
    "ORCHARD": ("bone", "crimson", "marrow"), "SILENT": ("stone", "cloud", "void"), "FOUNDRY": ("precursor", "amber", "rivets"),
    "INVERTED": ("water", "signal", "drips"), "DREAMING": ("dream", "spirit", "eyes"), "COURT": ("obsidian", "brass", "void"),
    "SEED": ("cloud", "signal", "circuits"), "VEIL": ("spirit", "signal", "runes"), "DREAM": ("dream", "cloud", "wisps"),
    "FEAR": ("night", "crimson", "eyes"),
}

COVER = {"NORMAL": 0.40, "ELITE": 0.48, "WARDEN": 0.54, "LORD": 0.56, "GUARDIAN": 0.56, "SOVEREIGN": 0.6, "FINAL": 0.62, "NIGHTMARE": 0.55}
MARKS = {"NORMAL": 1.0, "ELITE": 1.4, "WARDEN": 1.7, "LORD": 2.0, "GUARDIAN": 2.0, "SOVEREIGN": 2.4, "FINAL": 2.6, "NIGHTMARE": 2.0}


def hexrgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def glow_ramp(hexcol):
    """Five steps from a dim version of the glow colour to near white."""
    c = hexrgb(hexcol)
    dark = mixc(c, (0, 0, 0), 0.45)
    return [(*dark, 255), (*mixc(dark, c, 0.5), 255), (*c, 255), (*mixc(c, (255, 255, 255), 0.35), 255), (*mixc(c, (255, 255, 255), 0.7), 255)]


def tinted_ramp(name, tint_hex, amount=0.35):
    t = hexrgb(tint_hex)
    return [(*mixc(px[:3], t, amount), 255) for px in ramp(name)]


# ---------------------------------------------------------------------------------------------------------------------
# Patterns. Each paints marks over a canvas; `emissive` picks the bright glow palette, otherwise the accent ramp.


def _scatter(c, rng, n, painter):
    for _ in range(int(n)):
        painter(rng.randrange(c.w), rng.randrange(c.h))


def pattern(c, kind, rng, pal, density, mask=None):
    """pal: list of colours dark->bright. density scales the number of marks per 1024 px."""
    area = c.w * c.h / 1024.0
    n = max(1, density * area)

    def put(x, y, col):
        if 0 <= x < c.w and 0 <= y < c.h and (mask is None or not mask(x, y)):
            c.set(x, y, col)

    if kind == "runes":
        def rune(x, y):
            shape = rng.choice([[(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (0, 2), (1, 1), (2, 0), (2, 2)],
                                [(0, 1), (1, 0), (2, 1), (1, 2)], [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)]])
            for dx, dy in shape:
                put(x + dx, y + dy, pal[-1] if (dx + dy) % 2 == 0 else pal[-2])
        _scatter(c, rng, n * 6, rune)
    elif kind in ("spores", "marrow"):
        def dot(x, y):
            put(x, y, pal[-1])
            if rng.random() < 0.5:
                put(x + 1, y, pal[-2])
                put(x, y + 1, pal[-3])
        _scatter(c, rng, n * 18, dot)
    elif kind == "cracks":
        def crack(x, y):
            for _ in range(rng.randrange(4, 12)):
                put(x, y, pal[-1] if rng.random() < 0.6 else pal[-2])
                x += rng.choice((-1, 0, 1))
                y += rng.choice((0, 1, 1))
        _scatter(c, rng, n * 7, crack)
    elif kind == "drips":
        def drip(x, y):
            ln = rng.randrange(2, 6)
            for k in range(ln):
                put(x, y + k, pal[-1] if k == ln - 1 else pal[-2])
        _scatter(c, rng, n * 10, drip)
    elif kind in ("rivets", "circuits"):
        if kind == "rivets":
            step = 4
            for y in range(1, c.h, step):
                for x in range((y // step) % 2 * 2 + 1, c.w, step):
                    if rng.random() < 0.35 * density:
                        put(x, y, pal[-1])
        def trace(x, y):
            dx, dy = rng.choice([(1, 0), (0, 1)])
            for k in range(rng.randrange(3, 9)):
                put(x, y, pal[-2])
                if rng.random() < 0.25:
                    dx, dy = dy, dx
                x += dx
                y += dy
            put(x, y, pal[-1])
        _scatter(c, rng, n * (3 if kind == "rivets" else 8), trace)
    elif kind == "shards":
        def shard(x, y):
            for k in range(rng.randrange(2, 6)):
                put(x + k, y - k, pal[-1] if k == 0 else pal[-2])
        _scatter(c, rng, n * 9, shard)
    elif kind == "combs":
        def comb(x, y):
            for dx, dy in ((0, 0), (1, 0), (-1, 1), (2, 1), (0, 2), (1, 2)):
                put(x + dx, y + dy, pal[-2])
            put(x, y + 1, pal[-1])
            put(x + 1, y + 1, pal[-1])
        _scatter(c, rng, n * 6, comb)
    elif kind == "vines":
        def vine(x, y):
            for k in range(rng.randrange(5, 14)):
                put(x + int(round(math.sin(k * 0.9) * 1.2)), y + k, pal[-2])
                if k % 4 == 2:
                    put(x + 2, y + k, pal[-1])
        _scatter(c, rng, n * 5, vine)
    elif kind == "lanterns":
        def lamp(x, y):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                put(x + dx, y + dy, pal[-1] if dy == 0 else pal[-2])
            put(x, y - 1, pal[-3])
        _scatter(c, rng, n * 6, lamp)
    elif kind == "rime":
        def frost(x, y):
            put(x, y, pal[-1])
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if rng.random() < 0.6:
                    put(x + dx, y + dy, pal[-2])
        _scatter(c, rng, n * 14, frost)
    elif kind == "eyes":
        def eye(x, y):
            put(x, y, pal[-1])
            put(x + 1, y, pal[-1])
            put(x, y - 1, pal[-3])
            put(x + 1, y - 1, pal[-3])
        _scatter(c, rng, n * 12, eye)
    elif kind == "wisps":
        def wisp(x, y):
            for k in range(rng.randrange(3, 8)):
                put(x + k, y + int(round(math.sin(k * 0.7))), pal[-2] if k % 2 else pal[-1])
        _scatter(c, rng, n * 6, wisp)
    elif kind == "void":
        def star(x, y):
            put(x, y, pal[-1])
            if rng.random() < 0.3:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(x + dx, y + dy, pal[-3])
        _scatter(c, rng, n * 12, star)
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Skin and glow layers over vanilla bodies


def face_mask(face, sx, sy):
    u, v, w, h = face
    if w == 0:
        return lambda x, y: False
    return lambda x, y: u * sx <= x < (u + w) * sx and v * sy <= y < (v + h) * sy


def skin_layer(m, png, declared, face, rng):
    W, H = png
    sx, sy = W / declared[0], H / declared[1]
    skin_name, accent_name, kind = STYLE[m["theme"]]
    tint = m["tint"] or B_THEMES[m["theme"]]["tint"]
    rp = tinted_ramp(skin_name, tint, 0.25)
    ra = ramp(accent_name)
    c = Canvas(W, H)
    noise = value_noise(W, H, rng, scale=max(4, W // 10), octaves=3)
    flat = sorted(v for row in noise for v in row)
    cover = COVER[m["tier"]]
    thr = flat[int(len(flat) * (1 - cover))]
    mask = face_mask(face, sx, sy)
    for y in range(H):
        for x in range(W):
            v = noise[y][x]
            if v < thr or mask(x, y):
                continue
            edge = any(0 <= x + dx < W and 0 <= y + dy < H and noise[y + dy][x + dx] < thr for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            t = (v - thr) / max(1e-6, 1 - thr)
            idx = 1 if edge else 2 + quantize(min(0.999, t * 1.2), len(rp) - 2)
            c.set(x, y, rp[min(len(rp) - 1, idx)])
    pal = [ra[1], ra[2], ra[3], ra[4]]
    pattern(c, kind, rng, pal, 0.45 * MARKS[m["tier"]], mask)
    return c


def glow_layer(m, png, declared, face, eyes, rng):
    W, H = png
    sx, sy = W / declared[0], H / declared[1]
    _, _, kind = STYLE[m["theme"]]
    g = glow_ramp(m["glow"] or B_THEMES[m["theme"]]["glow"])
    c = Canvas(W, H)
    pattern(c, kind, rng, g, 0.16 * MARKS[m["tier"]], face_mask(face, sx, sy))
    for (u, v, w, h) in eyes:
        for y in range(int(v * sy), int((v + h) * sy)):
            for x in range(int(u * sx), int((u + w) * sx)):
                c.set(x, y, g[4] if (x + y) % 3 else g[3])
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Full textures for our own models (box painter)


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint_rect(c, rect, col_fn):
    u, v, w, h = rect
    for j in range(h):
        for i in range(w):
            c.set(u + i, v + j, col_fn(i, j, w, h))


def box(c, rng, u, v, w, h, d, rp, kind, accent, glowc):
    """Shades a cube's six faces from the ramp, then adds the part's detail."""
    n = len(rp)
    for name, rect in faces(u, v, w, h, d).items():
        base = {"top": n - 2, "bottom": 1, "front": n - 3, "back": 2, "left": 2, "right": n - 3}[name]

        def col(i, j, fw, fh, base=base):
            k = base
            if j == 0 and name not in ("top", "bottom"):
                k = min(n - 1, k + 1)
            if j == fh - 1 and name not in ("top", "bottom"):
                k = max(0, k - 1)
            if rng.random() < 0.18:
                k = max(0, min(n - 1, k + rng.choice((-1, 1))))
            return rp[k]
        paint_rect(c, rect, col)
    f = faces(u, v, w, h, d)
    fu, fv, fw, fh = f["front"]
    if kind in ("body", "jaw"):
        bu, bv, bw, bh = f["bottom"]
        paint_rect(c, f["bottom"], lambda i, j, a, b: accent[3] if (i + j) % 2 else accent[2])
        for name in ("left", "right", "top"):
            ru, rv, rw, rh = f[name]
            for j in range(rh):
                for i in range(rw):
                    if (i + 2 * j) % 4 == 0:
                        c.set(ru + i, rv + j, rp[max(0, n - 4)])
    elif kind == "head":
        paint_rect(c, f["front"], lambda i, j, a, b: rp[n - 2] if j < 2 else rp[n - 3])
    elif kind == "fin":
        for name in ("left", "right"):
            paint_rect(c, f[name], lambda i, j, a, b: accent[4] if j < 2 else accent[3])
    elif kind == "ear":
        for name in ("left", "right"):
            ru, rv, rw, rh = f[name]
            for j in range(rh):
                for i in range(rw):
                    c.set(ru + i, rv + j, rp[n - 2] if i % 3 == 0 else rp[n - 3] if j % 2 else rp[n - 4])
    elif kind == "ribs":
        for j in range(1, fh, 3):
            for i in range(1, fw - 1):
                c.set(fu + i, fv + j, rp[n - 1])
    elif kind == "blind":
        paint_rect(c, f["front"], lambda i, j, a, b: rp[n - 2] if abs(i - a / 2 + 0.5) > 2 else rp[n - 1])
    elif kind in ("bell", "rim", "spike"):
        for name, rect in f.items():
            ru, rv, rw, rh = rect
            for j in range(rh):
                for i in range(rw):
                    if (i * 7 + j * 3) % 11 == 0:
                        c.set(ru + i, rv + j, accent[4])
        if kind == "rim":
            paint_rect(c, f["bottom"], lambda i, j, a, b: (10, 8, 14, 255))
    elif kind == "clapper":
        paint_rect(c, f["front"], lambda i, j, a, b: accent[3])
    elif kind in ("robe", "sleeve"):
        for name in ("front", "back", "left", "right"):
            ru, rv, rw, rh = f[name]
            for i in range(rw):
                c.set(ru + i, rv + rh - 1, accent[3])
                if rh > 4:
                    c.set(ru + i, rv + rh - 2, accent[2])
        if kind == "robe":
            for j in range(fh):
                c.set(fu + fw // 2, fv + j, accent[2])
    elif kind == "cage":
        for name in ("front", "back", "left", "right"):
            ru, rv, rw, rh = f[name]
            for j in range(rh):
                for i in range(rw):
                    bar = i == 0 or i == rw - 1 or j == 0 or j == rh - 1 or i == rw // 2
                    c.set(ru + i, rv + j, accent[2] if bar else (40, 34, 30, 255))
    elif kind == "core":
        paint_rect(c, (u, v, 2 * (w + d), h + d), lambda i, j, a, b: glowc[3] if (i + j) % 2 else glowc[4])
    elif kind in ("hood", "face"):
        paint_rect(c, f["front"], lambda i, j, a, b: (8, 8, 12, 255) if 1 <= j < b - 1 and 1 <= i < a - 1 else rp[1])
    elif kind == "halo":
        paint_rect(c, (u, v, 2 * (w + d), h + d), lambda i, j, a, b: glowc[4] if i % 2 else glowc[3])
    elif kind == "armour":
        for name in ("front", "left", "right", "back"):
            ru, rv, rw, rh = f[name]
            for i in range(rw):
                c.set(ru + i, rv + rh // 3, accent[3])
    elif kind == "torso":
        for j in range(fh):
            c.set(fu + fw // 2, fv + j, accent[3])
        for i in range(fw):
            c.set(fu + i, fv + 4, accent[2])


def custom_texture(m, rng):
    size, boxes = CUSTOM[m["model"]]
    skin_name, accent_name, _ = STYLE[m["theme"]]
    tint = m["tint"] or B_THEMES[m["theme"]]["tint"]
    rp = tinted_ramp(skin_name, tint, 0.2)
    if m["model"] == "BELL":
        rp = tinted_ramp("brass", tint, 0.15)
    ra = ramp(accent_name)
    g = glow_ramp(m["glow"] or B_THEMES[m["theme"]]["glow"])
    c = Canvas(*size)
    for (name, u, v, w, h, d, kind) in boxes:
        part_ramp = rp
        if m["model"] == "BELL" and kind in ("armour", "robe"):
            part_ramp = tinted_ramp("slate", tint, 0.2)
        if m["model"] in ("LANTERN", "SHADE") and kind in ("robe", "sleeve", "hood"):
            part_ramp = tinted_ramp("night" if m["model"] == "SHADE" else "wood", tint, 0.25)
        box(c, rng, u, v, w, h, d, part_ramp, kind, ra, g)
    # Theme marks (moss, rivets, rime...) over the painted body, never on empty texture space.
    _, _, pat = STYLE[m["theme"]]
    pattern(c, pat, rng, [ra[1], ra[2], ra[3], ra[4]], 0.5 * MARKS[m["tier"]], lambda x, y: c.get(x, y)[3] == 0)
    if m["model"] == "HUMAN" and m["theme"] in ("PRECURSOR", "SEED"):
        circuits(c, "signal", seed=m["id"], lines=10, glow=False)
    return c


def custom_glow(m, rng):
    size, boxes = CUSTOM[m["model"]]
    g = glow_ramp(m["glow"] or B_THEMES[m["theme"]]["glow"])
    _, _, kind = STYLE[m["theme"]]
    c = Canvas(*size)
    for (name, u, v, w, h, d, part) in boxes:
        f = faces(u, v, w, h, d)
        fu, fv, fw, fh = f["front"]
        if part in ("head", "hood", "face"):
            ey = fv + max(2, fh // 2 - 1)
            for ex in (fu + max(1, fw // 2 - 3), fu + min(fw - 2, fw // 2 + 2)):
                c.set(ex, ey, g[4])
                c.set(ex + 1 if part == "head" else ex, ey, g[4])
        if part in ("core", "halo"):
            paint_rect(c, (u, v, 2 * (w + d), h + d), lambda i, j, a, b: g[4] if (i + j) % 2 else g[3])
        if part == "ear":
            for side in ("left", "right"):
                ru, rv, rw, rh = f[side]
                for j in range(1, rh - 1, 3):
                    for i in range(1, rw - 1):
                        if (i + j) % 4 == 0:
                            c.set(ru + i, rv + j, g[3])
        if part in ("bell", "rim"):
            ru, rv, rw, rh = f["front"]
            for i in range(0, rw, 2):
                c.set(ru + i, rv + rh // 2, g[4])
        if part == "body" or part == "ribs" or part == "torso" or part == "robe":
            sub = Canvas(fw, fh)
            pattern(sub, kind, rng, g, 3.0 * MARKS[m["tier"]])
            for j in range(fh):
                for i in range(fw):
                    if sub.get(i, j)[3] > 0:
                        c.set(fu + i, fv + j, sub.get(i, j))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Items


def egg(m, rng):
    skin_name, accent_name, _ = STYLE[m["theme"]]
    tint = m["tint"] or B_THEMES[m["theme"]]["tint"]
    rp = tinted_ramp(skin_name, tint, 0.3)
    g = glow_ramp(m["glow"] or B_THEMES[m["theme"]]["glow"])
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.8) / 6.6
            if dy < 0:
                dx *= 1.0 + (-dy) * 0.18
            d = math.hypot(dx, dy)
            if d <= 1.0:
                light = ((x - 7.5) * -0.6 + (y - 8.8) * -0.8) / 7.0
                c.set(x, y, rp[1 + quantize(max(0.0, min(0.999, 0.55 + light * 0.5 - d * 0.2)), len(rp) - 1)])
    spots = 4 + {"NORMAL": 0, "ELITE": 1, "WARDEN": 2}.get(m["tier"], 3)
    for _ in range(spots):
        x, y = rng.randrange(4, 12), rng.randrange(4, 14)
        if c.get(x, y)[3]:
            c.set(x, y, g[3])
            if c.get(x + 1, y)[3]:
                c.set(x + 1, y, g[2])
    if m["tier"] in B.BOSS_TIERS:
        gold = ramp("brass")
        for x in (5, 7, 9):
            c.set(x, 1, gold[5])
        for x in range(5, 11):
            c.set(x, 2, gold[4])
    elif m["tier"] == "WARDEN":
        cr = ramp("crimson")
        c.set(7, 2, cr[5])
        c.set(8, 2, cr[4])
    elif m["tier"] == "ELITE":
        c.set(7, 2, ramp("amber")[5])
    c.set(5, 5, (255, 255, 255, 200))
    return _outline(c)


def part_icon(p, rng):
    pid, rp = p["id"], p["ramp"]
    if any(k in pid for k in ("fang", "stinger", "horn", "clapper", "ear")):
        return polygon([(3, 14), (6, 13), (13, 3), (11, 2), (4, 10)], rp)
    if any(k in pid for k in ("scale", "fin", "feather", "eyelid", "silk", "shadow")):
        return polygon([(8, 1), (13, 6), (12, 12), (8, 15), (4, 12), (3, 6)], rp)
    if any(k in pid for k in ("core", "heart", "pearl", "sac", "jelly", "resin", "lullaby")):
        return orb(rp, core_ramp="amber" if rp != "amber" else "signal")
    if any(k in pid for k in ("crown", "halo", "beads", "gear", "mainspring")):
        c = Canvas()
        rr = ramp(rp)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 3.6 <= d <= 6.4:
                    c.set(x, y, rr[2 + quantize(0.6 - (x + y) / 40, len(rr) - 2)])
                    if "gear" in pid or "mainspring" in pid:
                        ang = math.atan2(y - 7.5, x - 7.5)
                        if d > 5.6 and int((ang + math.pi) / (math.pi / 4)) % 2:
                            c.set(x, y, (0, 0, 0, 0))
        if "crown" in pid:
            for x in (3, 7, 12):
                c.set(x, 1, rr[-1])
                c.set(x, 2, rr[-2])
        return _outline(c)
    if any(k in pid for k in ("codex", "ledger")):
        return book(cover=rp if rp in RAMPS else "dream", accent="amber")
    if "quill" in pid:
        return blade(ramp_name=rp, hilt="wood", gem="signal")
    if "lantern" in pid:
        return device(body="brass", screen="amber", seed=pid)
    if "key" in pid:
        return blade(ramp_name="brass", hilt=rp, gem="dream")
    if "plating" in pid:
        c = plates(rp, seed=pid)
        return c
    return shard(rp)


# ---------------------------------------------------------------------------------------------------------------------

B_THEMES = {}


def build(out):
    B_THEMES.update(B.themes())
    for m in B.mobs():
        rng = rng_for("mob", m["id"])
        if m["model"] in CUSTOM:
            out.entity("mobs/" + m["id"], custom_texture(m, rng))
            out.entity("mobs/" + m["id"] + "_glow", custom_glow(m, rng_for("mobglow", m["id"])))
        else:
            png, declared, face, eyes = VANILLA[m["model"]]
            out.entity("mobs/" + m["id"], skin_layer(m, png, declared, face, rng))
            out.entity("mobs/" + m["id"] + "_glow", glow_layer(m, png, declared, face, eyes, rng_for("mobglow", m["id"])))
        out.item(m["id"] + "_spawn_egg", egg(m, rng_for("egg", m["id"])))
        for p in m["parts"]:
            out.item(p["id"], part_icon(p, rng_for("part", p["id"])))
