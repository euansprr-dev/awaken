"""The Inscribing Table (SPEC §45.5, §45.10): a heavy desk of dark wood under a slate top, a lapis channel let into the
slate one pixel in from its edge (the renderer lights it from above), brass at the corners and a low brass bracket at the
back for the floating book. The book is deep blue leather with brass corners and lapis-inked pages. The page item is a
torn leaf with a lapis mark; the burin is the table's engraving tool (dark wood knob, brass ferrule, a steel point).

    python3 tools/art/build_all.py enchant          textures
    python3 tools/art/specs/enchant.py content      blockstate, block and item models, burin name
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
from pixelart import Canvas, hex_rgba, ramp, rng_for  # noqa: E402
import _enchant_glyphs as GL  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/enchant")

# a deep, near-black wood for the desk, and the lapis of the channel
DESK = [hex_rgba(h) for h in ("#140c08", "#1e130c", "#2a1b11", "#382417", "#4a301e", "#5e3e27")]
LAPIS = [hex_rgba(h) for h in ("#0c1640", "#16266e", "#1f3a9e", "#2f56cc", "#4f7ff0", "#9cc0ff")]
LEATHER = [hex_rgba(h) for h in ("#0a0d1c", "#121a33", "#1a2547", "#24325c", "#324475", "#46598f")]
PAPER = [hex_rgba(h) for h in ("#8d7a58", "#a8946c", "#c4b088", "#d9c7a0", "#e8dabb", "#f4ead2")]


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def grain(c, rp, seed, x0=0, y0=0, w=16, h=16, lo=1, hi=4, vertical=False):
    """Wood grain: long streaks with a knot or two."""
    rng = rng_for(seed)
    phase = [rng.random() * 6.28 for _ in range(4)]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            a, b = (x, y) if vertical else (y, x)
            v = math.sin(a * 1.7 + math.sin(b * 0.35 + phase[0]) * 1.4 + phase[1]) * 0.5 + 0.5
            v = v * 0.7 + rng.random() * 0.3
            c.set(x, y, rp[clamp(lo + int(v * (hi - lo + 1)), lo, hi)])


def top():
    """The slate top: a wood rim, the lapis channel inside it, slate with a faint grain of its own, brass at the corners."""
    sl = ramp("slate")
    br = ramp("brass")
    rng = rng_for("inscribing_top")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            n = rng.random()
            c.set(x, y, sl[2] if n < 0.55 else sl[3] if n < 0.85 else sl[1])
    # a soft lighter wash across the middle, where the piece lies
    for y in range(4, 12):
        for x in range(3, 13):
            if rng.random() < 0.35:
                c.set(x, y, sl[3])
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, DESK[3] if (i % 5) else DESK[2])
    for i in range(1, 15):
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            c.set(x, y, LAPIS[3] if (i % 4) else LAPIS[4])
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, br[4])
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, br[3])
    return c


def panel(front=False):
    """The desk's sides: a framed panel of dark wood with a brass band; the front carries a lapis mark."""
    br = ramp("brass")
    c = Canvas()
    grain(c, DESK, "inscribing_panel" + ("_f" if front else ""), lo=1, hi=3, vertical=True)
    for i in range(16):
        c.set(i, 0, br[3])
        c.set(i, 1, DESK[4])
        c.set(0, i, DESK[4])
        c.set(15, i, DESK[1])
        c.set(i, 15, DESK[0])
    # the sunk panel's frame
    for i in range(3, 13):
        c.set(i, 4, DESK[0])
        c.set(i, 13, DESK[4])
        c.set(2, i + 1, DESK[0])
        c.set(13, i + 1, DESK[4])
    for (x, y) in ((1, 2), (14, 2), (1, 14), (14, 14)):
        c.set(x, y, br[4])
    if front:
        # a ring and a spear through it: what the table does, in lapis
        for a in range(48):
            t = a / 48 * math.tau
            x, y = 7.5 + math.cos(t) * 3.4, 8.5 + math.sin(t) * 3.4
            c.set(round(x), round(y), LAPIS[3])
        for x in range(4, 12):
            c.set(x, 8 + (1 if x > 7 else 0), LAPIS[4])
        c.set(7, 8, LAPIS[5])
        c.set(8, 9, LAPIS[5])
    return c


def trim():
    """The top board's edge and the plinth: dark wood with a brass line."""
    br = ramp("brass")
    c = Canvas()
    grain(c, DESK, "inscribing_trim", lo=2, hi=4)
    for i in range(16):
        c.set(i, 0, DESK[5])
        c.set(i, 7, br[3] if i % 4 else br[4])
        c.set(i, 8, DESK[1])
        c.set(i, 15, DESK[0])
    return c


def brass():
    br = ramp("brass")
    rng = rng_for("inscribing_brass")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = 3 + (1 if (x + y) % 7 == 0 else 0) - (1 if rng.random() < 0.15 else 0)
            c.set(x, y, br[clamp(v, 1, 5)])
    return c


def book():
    """64x32 in the vanilla book's layout: covers, spine, page blocks and the turning leaves."""
    br = ramp("brass")
    c = Canvas(64, 32)
    rng = rng_for("inscribing_book")

    def leather(x0, y0, w, h, outer):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                v = 2 + (1 if rng.random() < 0.3 else 0) - (1 if rng.random() < 0.12 else 0)
                c.set(x, y, LEATHER[v if outer else v - 1])
        if outer:
            for x in range(x0, x0 + w):
                c.set(x, y0, LEATHER[4])
                c.set(x, y0 + h - 1, LEATHER[0])
            for (x, y) in ((x0, y0), (x0 + w - 1, y0), (x0, y0 + h - 1), (x0 + w - 1, y0 + h - 1)):
                c.set(x, y, br[4])
            # a lapis ring tooled into the leather
            cx, cy = x0 + w / 2 - 0.5, y0 + h / 2 - 0.5
            for a in range(24):
                t = a / 24 * math.tau
                c.set(round(cx + math.cos(t) * 1.6), round(cy + math.sin(t) * 2.2), LAPIS[3])
            c.set(round(cx), round(cy), LAPIS[5])

    leather(0, 0, 6, 10, True)
    leather(6, 0, 6, 10, False)
    leather(16, 0, 6, 10, True)
    leather(22, 0, 6, 10, False)
    for y in range(10):
        for x in range(12, 16):
            c.set(x, y, LEATHER[3] if x in (12, 15) else LEATHER[2])
        if y in (2, 7):
            for x in range(12, 16):
                c.set(x, y, br[3])

    def pages(x0, y0, w, h, ink):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c.set(x, y, PAPER[4] if rng.random() < 0.8 else PAPER[3])
        if ink:
            for y in range(y0 + 1, y0 + h - 1, 2):
                for x in range(x0 + 1, x0 + w - 1):
                    if rng.random() < 0.7:
                        c.set(x, y, LAPIS[2] if rng.random() < 0.5 else PAPER[1])

    def block(u, v, w, h, d):
        # a box's faces: top and bottom are page edges, the big faces are written pages
        pages(u + d, v, w, d, False)
        pages(u + d + w, v, w, d, False)
        for y in range(v + d, v + d + h):
            for x in list(range(u, u + d)) + list(range(u + d + w, u + d + w + d)):
                c.set(x, y, PAPER[2] if y % 2 else PAPER[3])
        pages(u + d, v + d, w, h, True)
        pages(u + d + w + d, v + d, w, h, True)

    block(0, 10, 5, 8, 1)
    block(12, 10, 5, 8, 1)
    pages(24, 10, 5, 8, True)
    pages(29, 10, 5, 8, True)
    return c


def page_item():
    """A torn leaf with a lapis mark: what a Lord drops, the scribe sells, an old book teaches."""
    c = Canvas()
    rng = rng_for("enchant_page_item")
    edge = [hex_rgba("#3a2c1a")]
    for y in range(2, 15):
        for x in range(3, 13):
            torn = (y == 2 and rng.random() < 0.35) or (x == 12 and rng.random() < 0.3)
            if torn:
                continue
            c.set(x, y, PAPER[4] if (x + y) % 5 else PAPER[3])
    for y in range(2, 15):
        c.set(3, y, PAPER[2])
    for x in range(3, 13):
        c.set(x, 14, PAPER[1])
    for a in range(32):
        t = a / 32 * math.tau
        c.set(round(7.5 + math.cos(t) * 2.6), round(8 + math.sin(t) * 2.6), LAPIS[3])
    for x in range(5, 11):
        c.set(x, 8, LAPIS[4])
    c.set(8, 8, LAPIS[5])
    c.set(7, 5, LAPIS[5])
    # outline the paper
    out = c.copy()
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3] == 0 and any(c.get(x + dx, y + dy)[3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.set(x, y, edge[0])
    return out


def burin():
    """The engraving tool, held point-down: a dark knob, a brass ferrule, a long steel point with a lapis glint."""
    br = ramp("brass")
    st = ramp("mirror")
    c = Canvas()
    # diagonal from lower left (knob) to upper right (point), like any held tool
    for i in range(3):
        for (dx, dy) in ((0, 0), (1, 0), (0, -1), (1, -1)):
            c.set(2 + i + dx, 13 - i + dy, DESK[3 + (1 if dx == 0 and dy == -1 else 0)])
    c.set(2, 14, DESK[1])
    for i in range(3, 5):
        c.set(2 + i, 13 - i, br[4])
        c.set(3 + i, 13 - i, br[3])
    for i in range(5, 12):
        c.set(2 + i, 13 - i, st[4] if i < 10 else st[5])
        if i < 9:
            c.set(3 + i, 13 - i, st[2])
    c.set(14, 1, LAPIS[5])
    c.set(13, 1, LAPIS[3])
    out = c.copy()
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3] == 0 and any(c.get(x + dx, y + dy)[3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.set(x, y, hex_rgba("#0b0806"))
    return out


# the Study's drifting letters (SPEC §45.13): our own script, white so the particle tints them (lapis, or the colour of
# what's being written), each a 6x6 rune in an 8x8 leaf with a faint halo
LETTERS = [
    ".####.|#....#|#.##.#|#.##.#|#....#|.####.",
    "..##..|.####.|..##..|..##..|..##..|.#..#.",
    "#....#|#....#|#....#|.#..#.|..##..|..##..",
    ".#..#.|#.##.#|.#..#.|.#..#.|#.##.#|.#..#.",
    "####..|...#..|...#..|..###.|...#.#|...#..",
    "..##..|..##..|.#..#.|.#..#.|#....#|######",
    "##....|.##...|..##..|...##.|..##..|.##...",
    "......|.####.|#.##.#|#.##.#|.####.|......",
    "#.##.#|.####.|..##..|..##..|..##..|..##..",
    "######|..##..|..##..|######|..##..|..##..",
    "#####.|#...#.|#.#.#.|#.###.|#.....|######",
    ".#....|#.#..#|...##.|.#....|#.#..#|...##.",
]


def letter(rows):
    c = Canvas(8, 8)
    on = set()
    for y, row in enumerate(rows.split("|")):
        for x, ch in enumerate(row):
            if ch == "#":
                on.add((x + 1, y + 1))
    for (x, y) in on:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in on:
                    c.set(x + dx, y + dy, (200, 220, 255, 70))
    for (x, y) in on:
        c.set(x, y, (255, 255, 255, 255))
    return c


# the Precursor Word Stone (SPEC §45.14): a stele of the ruins' dark plating on a low plinth, a panel let into its face
# with the script carved down it; the glowing overlay is white (the block tint gives each enchantment its colour)
WORD_SCRIPT = [
    "#..#",
    ".##.",
    "",
    "###.",
    "#.#.",
    "",
    ".#.#",
    ".###",
]
WORD_X, WORD_Y = 6, 3  # where the script starts on the 16x16 face


def word_plating(seed, seams=True):
    sl = ramp("slate")
    rng = rng_for("word_stone", seed)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            n = rng.random()
            c.set(x, y, sl[2] if n < 0.6 else sl[3] if n < 0.88 else sl[1])
    if seams:
        for y in range(16):
            c.set(7, y, sl[1])
            c.set(8, y, sl[3])
        for x in range(16):
            c.set(x, 0, sl[4] if x % 5 else sl[3])
    return c


def word_cells():
    for dy, row in enumerate(WORD_SCRIPT):
        for dx, ch in enumerate(row):
            if ch == "#":
                yield WORD_X + dx, WORD_Y + dy


def word_face():
    """The stele's face: the panel's carved border (dark groove, a lit lip below and right) and the script carved in."""
    sl = ramp("slate")
    c = word_plating("face", seams=False)
    for x in range(4, 12):
        c.set(x, 2, sl[0])
        c.set(x, 12, sl[4])
    for y in range(2, 13):
        c.set(4, y, sl[0])
        c.set(11, y, sl[4])
    for y in range(3, 12):
        for x in range(5, 11):
            c.set(x, y, sl[1] if (x + y) % 3 else sl[2])
    for (x, y) in word_cells():
        c.set(x, y, sl[0])
    return c


def word_script():
    """The glowing script, white, with the panel's groove lit faintly round it (cutout: every pixel opaque or none)."""
    c = Canvas()
    for x in range(4, 12):
        c.set(x, 2, (92, 92, 92, 255))
    for y in range(3, 12):
        c.set(4, y, (92, 92, 92, 255))
    for (x, y) in word_cells():
        c.set(x, y, (255, 255, 255, 255))
    return c


def page_icon(g):
    """One inscription's page (SPEC §45.15): a parchment leaf with a band in its family's colour and its own sign in dark
    ink (the glyph without its family's frame, which the band stands for at sixteen pixels)."""
    c = Canvas()
    edge, paper, shade = hex_rgba("#70583a"), PAPER[4], PAPER[2]
    for y in range(1, 15):
        for x in range(2, 14):
            c.set(x, y, paper)
    for x in range(2, 14):
        c.set(x, 0, edge)
        c.set(x, 15, edge)
        c.set(x, 14, shade)
    for y in range(1, 15):
        c.set(1, y, edge)
        c.set(14, y, edge)
        c.set(13, y, shade)
    r, gr, b = GL.rgb(g["colour"])
    for x in range(2, 14):
        c.set(x, 1, (r, gr, b, 255))
        c.set(x, 2, (int(r * 0.72), int(gr * 0.72), int(b * 0.72), 255))
    strokes = GL.sign(g)
    xs = [x for st in strokes for x, _ in st]
    ys = [y for st in strokes for _, y in st]
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    k = min(8 / max(w, 0.01), 8 / max(h, 0.01), 3.0)
    ox, oy = 7.5 - (min(xs) + w / 2) * k, 8.5 - (min(ys) + h / 2) * k
    ink = hex_rgba("#3a2a1c")
    for st in strokes:
        pts = [(round(ox + x * k), round(oy + y * k)) for x, y in st]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = max(abs(x1 - x0), abs(y1 - y0), 1)
            for i in range(n + 1):
                c.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), ink)
    return c


def sign_particle(g):
    """One inscription's sign, white so the particle tints it: among the letters that drift in a Study, the signs of the
    words you know (SPEC §45.17)."""
    c = Canvas()
    strokes = GL.sign(g)
    xs = [x for st in strokes for x, _ in st]
    ys = [y for st in strokes for _, y in st]
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    k = min(10 / max(w, 0.01), 10 / max(h, 0.01), 3.4)
    ox, oy = 7.5 - (min(xs) + w / 2) * k, 7.5 - (min(ys) + h / 2) * k
    white = (255, 255, 255, 255)
    for st in strokes:
        pts = [(round(ox + x * k), round(oy + y * k)) for x, y in st]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = max(abs(x1 - x0), abs(y1 - y0), 1)
            for i in range(n + 1):
                c.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), white)
    return c


def inkwell():
    """The desk's inkwell: dark stone, a pool of lapis in its top. Top at (0..4, 0..4), a side at (4..8, 0..3), the lit
    pool alone at (8..10, 0..2)."""
    sl = ramp("slate")
    c = Canvas()
    rng = rng_for("inscribing_inkwell")
    for y in range(16):
        for x in range(16):
            c.set(x, y, sl[1 + (1 if rng.random() < 0.35 else 0)])
    for y in range(4):
        for x in range(4):
            rim = x in (0, 3) or y in (0, 3)
            c.set(x, y, sl[3] if rim else LAPIS[3 + (1 if (x + y) % 2 == 0 else 0)])
    for x in range(4, 8):
        c.set(x, 0, sl[3])
        c.set(x, 2, sl[0])
    for y in range(2):
        for x in range(8, 10):
            c.set(x, y, LAPIS[4 if (x + y) % 2 == 0 else 5])
    return c


def build(out):
    out.block("inscribing_inkwell", inkwell())
    for g in GL.GLYPHS:
        out.item("enchantment_page_" + GL.path(g), page_icon(g))
    out.block("inscribing_table_top", top())
    out.block("inscribing_table_side", panel())
    out.block("inscribing_table_front", panel(front=True))
    out.block("inscribing_table_trim", trim())
    out.block("inscribing_table_brass", brass())
    out.block("inscribing_book", book())
    out.item("enchantment_page", page_item())
    out.item("burin", burin())
    for i, rows in enumerate(LETTERS):
        out.particle(f"enchant_letter_{i}", letter(rows))
    for g in GL.GLYPHS:
        out.particle(f"enchant_sign_{g['no']}", sign_particle(g))
    out.block("word_stone_side", word_plating("side"))
    out.block("word_stone_top", word_plating("top", seams=False))
    out.block("word_stone_face", word_face())
    out.block("word_stone_script", word_script())


# ---- content ---------------------------------------------------------------------------------------------------------

def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    cur.update(data)
    _write(path, cur)


def box(lo, hi, faces):
    return {"from": lo, "to": hi, "faces": faces}


def table_model():
    """The scribe's desk (SPEC §45.15), north-facing: the writer stands on the north side (z 0). Two trestle ends and a
    stretcher, an apron with a brass pull, the slate with its lapis channel, a rail along the back, a brass rest for the
    book at the writer's left (the renderer lays the book on it) and an inkwell of lapis at their right (the renderer
    stands the burin in it while nobody writes). Faces without a uv take the model's own."""
    f = lambda tex, **kw: {"texture": tex} | kw  # noqa: E731

    def span(a0, a1):
        """A run of the model's own coordinates as texels inside 0..16 (a part above the block's top slides down into it)."""
        if a1 > 16:
            a0, a1 = a0 - (a1 - 16), 16
        if a0 < 0:
            a0, a1 = 0, a1 - a0
        return max(0, a0), min(16, a1)

    def uv(lo, hi, d):
        (x0, y0, z0), (x1, y1, z1) = lo, hi
        if d in ("up", "down"):
            u, v = span(x0, x1), span(z0, z1)
            return [u[0], v[0], u[1], v[1]]
        u, v = span(*((x0, x1) if d in ("north", "south") else (z0, z1))), span(y0, y1)
        return [u[0], 16 - v[1], u[1], 16 - v[0]]

    def solid(lo, hi, tex, **over):
        faces = {d: f(tex, uv=uv(lo, hi, d)) for d in ("north", "south", "west", "east", "up", "down")}
        for d, face in over.items():
            faces[d] = face if "uv" in face else face | {"uv": uv(lo, hi, d)}
        return box(lo, hi, faces)

    els = [
        # the trestle ends, their feet and the stretcher between them
        solid([1, 0, 2], [3, 10, 14], "#side"), solid([13, 0, 2], [15, 10, 14], "#side"),
        solid([0.5, 0, 1.5], [3.5, 1.5, 14.5], "#trim", down=f("#trim", cullface="down")),
        solid([12.5, 0, 1.5], [15.5, 1.5, 14.5], "#trim", down=f("#trim", cullface="down")),
        solid([3, 4, 7], [13, 6, 9], "#trim"),
        # the apron toward the writer, with its drawer's brass pull
        solid([3, 7.5, 1.5], [13, 10, 3], "#side"), solid([7.2, 8.2, 1], [8.8, 9.2, 1.5], "#brass"),
        # the slate let into the top board, its lapis channel one pixel in from the edge
        box([0, 10, 0], [16, 12, 16], {
            "down": f("#trim", uv=[0, 0, 16, 16]), "up": f("#top", uv=[0, 0, 16, 16]),
            "north": f("#trim", uv=[0, 6, 16, 8], cullface="north"), "south": f("#trim", uv=[0, 6, 16, 8], cullface="south"),
            "west": f("#trim", uv=[0, 6, 16, 8], cullface="west"), "east": f("#trim", uv=[0, 6, 16, 8], cullface="east")}),
        # the rail along the back under a brass cap, and a cheek at each side
        solid([0, 12, 14], [16, 14.5, 16], "#side"), solid([0, 14.5, 14], [16, 15, 16], "#brass"),
        solid([0, 12, 9], [1, 13.5, 14], "#side"), solid([15, 12, 9], [16, 13.5, 14], "#side"),
        # the book's rest: a brass post off the rail and a plate, on the writer's left (east when you face south)
        solid([11.6, 15, 14.4], [12.4, 17, 15.2], "#brass"), solid([6.8, 17, 11.2], [14.8, 17.4, 16], "#brass"),
        # the inkwell on the writer's right, and its pool of lapis, lit
        box([1.4, 12, 10.8], [4.8, 14.2, 14], {
            "up": f("#inkwell", uv=[0, 0, 4, 4]), "north": f("#inkwell", uv=[4, 0, 8, 3]), "south": f("#inkwell", uv=[4, 0, 8, 3]),
            "west": f("#inkwell", uv=[4, 0, 8, 3]), "east": f("#inkwell", uv=[4, 0, 8, 3])}),
        {"from": [2.2, 14.2, 11.6], "to": [4, 14.25, 13.2], "shade": False, "light_emission": 12,
         "faces": {"up": f("#inkwell", uv=[8, 0, 10, 2])}},
    ]
    b = "awaken:block/inscribing_table_"
    return {"parent": "minecraft:block/block",
            "textures": {"particle": b + "side", "top": b + "top", "side": b + "side", "trim": b + "trim", "brass": b + "brass",
                         "inkwell": "awaken:block/inscribing_inkwell"},
            "elements": els,
            "display": {
                "gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.6, 0.6, 0.6]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}}


def word_stone_model():
    """North-facing: the carved face looks north (z 0)."""
    t = lambda tex, uv, cull=None: ({"texture": tex, "uv": uv} | ({"cullface": cull} if cull else {}))  # noqa: E731
    els = [
        box([1, 0, 1], [15, 3, 15], {
            "down": t("#top", [1, 1, 15, 15], "down"), "up": t("#top", [1, 1, 15, 15]),
            "north": t("#side", [1, 13, 15, 16]), "south": t("#side", [1, 13, 15, 16]),
            "west": t("#side", [1, 13, 15, 16]), "east": t("#side", [1, 13, 15, 16])}),
        box([3, 3, 5], [13, 15, 11], {
            "north": t("#face", [3, 1, 13, 13]), "south": t("#side", [3, 1, 13, 13]),
            "west": t("#side", [5, 1, 11, 13]), "east": t("#side", [5, 1, 11, 13])}),
        box([2.5, 15, 4.5], [13.5, 16, 11.5], {
            "up": t("#top", [2, 4, 14, 12]), "down": t("#top", [2, 4, 14, 12]),
            "north": t("#side", [2, 0, 14, 1]), "south": t("#side", [2, 0, 14, 1]),
            "west": t("#side", [4, 0, 12, 1]), "east": t("#side", [4, 0, 12, 1])}),
        # the script, glowing a hair in front of the face, in the enchantment's colour
        {"from": [3, 3, 4.95], "to": [13, 15, 4.95], "shade": False, "light_emission": 15,
         "faces": {"north": {"texture": "#script", "uv": [3, 1, 13, 13], "tintindex": 0}}},
    ]
    b = "awaken:block/word_stone_"
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"particle": b + "side", "side": b + "side", "top": b + "top", "face": b + "face", "script": b + "script"},
            "elements": els}


def content():
    _write(os.path.join(ASSETS, "models/block/inscribing_table.json"), table_model())
    variants = {}
    for i, face in enumerate(("north", "east", "south", "west")):
        v = {"model": "awaken:block/inscribing_table"}
        if i:
            v["y"] = 90 * i
        variants[f"facing={face}"] = v
    _write(os.path.join(ASSETS, "blockstates/inscribing_table.json"), {"variants": variants})
    _write(os.path.join(ASSETS, "items/inscribing_table.json"), {"model": {"type": "minecraft:model", "model": "awaken:block/inscribing_table"}})
    for name, parent in (("enchantment_page", "minecraft:item/generated"), ("burin", "minecraft:item/handheld")):
        _write(os.path.join(ASSETS, f"models/item/{name}.json"), {"parent": parent, "textures": {"layer0": f"awaken:item/{name}"}})
        _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"awaken:item/{name}"}})
    # every inscription's page has its own leaf (enchant.client.PageSelect names it by the enchantment's id path); a page
    # of anything else keeps the plain leaf
    cases = []
    for g in GL.GLYPHS:
        name = "enchantment_page_" + GL.path(g)
        _write(os.path.join(ASSETS, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"awaken:item/{name}"}})
        cases.append({"when": GL.path(g), "model": {"type": "minecraft:model", "model": f"awaken:item/{name}"}})
    _write(os.path.join(ASSETS, "items/enchantment_page.json"), {"model": {
        "type": "minecraft:select", "property": "awaken:page", "cases": cases,
        "fallback": {"type": "minecraft:model", "model": "awaken:item/enchantment_page"}}})
    _merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {"item.awaken.burin": "Burin", "block.awaken.word_stone": "Word Stone"})
    _write(os.path.join(ASSETS, "models/block/word_stone.json"), word_stone_model())
    stone = {}
    for i, face in enumerate(("north", "east", "south", "west")):
        v = {"model": "awaken:block/word_stone"}
        if i:
            v["y"] = 90 * i
        stone[f"facing={face}"] = v
    _write(os.path.join(ASSETS, "blockstates/word_stone.json"), {"variants": stone})
    _write(os.path.join(ASSETS, "particles/enchant_letter.json"),
           {"textures": [f"awaken:enchant_letter_{i}" for i in range(len(LETTERS))]
                        + [f"awaken:enchant_sign_{g['no']}" for g in GL.GLYPHS]})  # LetterParticle counts on this order
    print("enchant: content written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
