"""The Drowned Archivist's silhouette round (docs/bosses/ROLLOUT.md): the Drowned gate's Lord, a Caster. "Every book
here was written by someone who drowned." A page that washes you back, volleys of ink, loose pages that fly at you, an
ink cloud, and at the end the Flood and its undertow. Three shapes in sodden paper, black ink, old leather, tarnished
brass, drowned blue-grey skin and a glowing sea-green water."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

PAPER = Ramp("#5e5a4c", "#807a66", "#a29a80", "#c2b998", "#dcd4b4")
INK = Ramp("#06080c", "#0c1016", "#141a22", "#1e2630", "#2a3440")
LEATHER = Ramp("#24160e", "#352216", "#4a3020", "#60402c", "#78543a")
BRASS = Ramp("#3a2c12", "#5a4418", "#7a5e22", "#9a7a32", "#b89648")
SKIN = Ramp("#2e3a40", "#3e4c54", "#52626a", "#687a82", "#80949a")
WATER = Ramp("#0e5a5e", "#16807e", "#24a49a", "#4cc8b8", "#8eead8")
MATS = {"p": (PAPER, 2.6), "k": (INK, 2.4), "l": (LEATHER, 2.4), "b": (BRASS, 2.6), "s": (SKIN, 2.6), "w": (WATER, 2.8)}


def _book(m, parent, x, up, z, rot=(0, 0, 0), w=6, h=8, open_=False):
    """A book on a bone: shut (a block with its pages showing at the edge) or open (two boards and two pages)."""
    b = m.bone(parent, x, up, z, rot)
    if open_:
        for s in (1, -1):
            m.box(b, "l", s * w / 2, 0, 0, w, h, 1)
            m.box(b, "p", s * (w / 2 - 0.5), 0, -1, w - 1, h - 1, 1)
    else:
        m.box(b, "l", 0, 0, 0, w, h, 2)
        m.box(b, "p", 0.5, 0.5, 0, w - 1, h - 1, 3)
    return b


# ---- A: the Lectern --------------------------------------------------------------------------------------------------
def lectern():
    m = Mass("da_a", "A · The Lectern",
             "A tall drowned scholar grown into his own reading stand. From the waist down he is a heavy lectern of "
             "black, waterlogged wood, and the robe runs into it. A great book lies open on the desk in front of him and "
             "the sea pours off its pages onto the floor. He stoops over it in a sodden hood, a bloated blue face with "
             "no eyes, one long hand on the page and the other holding a quill as long as a spear. An anchor hangs "
             "from a chain round his neck.", MATS)
    # the lectern: a heavy foot, a post the robe runs into, a wide sloped desk jutting out in front at his chest
    base = m.bone(None, 0, 0, 4)
    m.box(base, "l", 0, 0, 0, 28, 4, 22)
    m.box(base, "k", 0, 4, 0, 22, 4, 18)
    for k, (w, d, h) in enumerate(((16, 14, 10), (18, 15, 10), (20, 16, 10))):  # the robe flowing down into it
        m.box(base, "k" if k % 2 == 0 else "l", 0, 8 + 10 * k, 0, w, h, d)
    desk = m.bone(None, 0, 34, -14, (-28, 0, 0))
    m.box(desk, "l", 0, -1, 0, 34, 3, 18)
    m.box(desk, "l", 0, -16, 6, 6, 14, 6)  # its leg
    for sd in (1, -1):  # the book open on it, its pages fanning up
        pg = m.bone(desk, sd * 1, 2, 0, (0, 0, sd * -24))
        m.box(pg, "l", sd * 8, 0, 0, 16, 1, 14)
        m.box(pg, "p", sd * 8, 1, 0, 15, 2, 13)
        for k in range(3):
            lf = m.bone(pg, sd * (3 + k * 4), 3, 0, (0, 0, sd * -(20 + k * 14)))
            m.box(lf, "p", 0, 0, 0, 1, 9 - k, 12)
    for k, (x, ln) in enumerate(((-10, 26), (-3, 30), (4, 28), (11, 24))):  # the sea pours off its front edge
        m.box(None, "w", x, 0, -24 - (k % 2), 5, ln, 2)
    m.box(None, "w", 0, 0, -26, 34, 1, 12)  # and spreads on the floor
    # him: tall, stooped over the book, a pointed sodden hood
    tor = m.bone(base, 0, 38, 0, (30, 0, 0))
    m.box(tor, "k", 0, 0, 0, 20, 14, 14)
    m.box(tor, "k", 0, 14, 0, 24, 8, 15)
    hd = m.bone(tor, 0, 22, -4, (16, 0, 0))
    m.box(hd, "k", 0, 0, 0, 14, 12, 14)
    m.box(hd, "k", 0, 12, 1, 10, 6, 10)  # the hood, peaked
    m.box(hd, "k", 0, 18, 2, 4, 6, 4)
    m.box(hd, "s", 0, 1, -5, 9, 10, 4)  # the bloated face, sunk in it
    m.box(hd, "k", 0, 5, -7.4, 7, 2, 1)  # no eyes, only dark
    ch = m.bone(tor, 0, 16, -7, (-30, 0, 0))  # the anchor on its chain
    m.box(ch, "b", 0, -14, 0, 1, 14, 1)
    m.box(ch, "b", 0, -22, 0, 2, 8, 2)
    m.box(ch, "b", 0, -23, 0, 10, 2, 2)
    m.box(ch, "b", -4, -21, 0, 2, 3, 2)
    m.box(ch, "b", 4, -21, 0, 2, 3, 2)
    # long arms out over the book: the left hand flat on the page, the right holding the quill up like a spear
    for sd, (ar, fr) in ((1, ((-60, 0, 10), (-10, 0, 0))), (-1, ((-30, 0, -24), (-80, 0, 0)))):
        arm = m.bone(tor, sd * 12, 18, 0, ar)
        m.box(arm, "k", 0, -16, 0, 6, 16, 6)
        el = m.bone(arm, 0, -16, 0, fr)
        m.box(el, "k", 0, -14, 0, 6, 14, 6)
        m.box(el, "s", 0, -21, 0, 4, 7, 4)  # long drowned fingers
        if sd < 0:
            q = m.bone(el, 0, -18, 0, (100, 0, 0))
            m.box(q, "b", 0, -14, 0, 1, 56, 1)
            m.box(q, "p", 0, 28, 0, 2, 16, 6)
            m.box(q, "k", 0, -16, 0, 1, 3, 1)
    return m


# ---- B: the Diver ----------------------------------------------------------------------------------------------------
def diver():
    m = Mass("da_b", "B · The Diver",
             "An archivist in an old diving suit, huge and slow. Heavy lead boots, a barrel chest of patched canvas, and "
             "a square brass helmet whose window is full of water, with his drowned face pressed against it. On his "
             "back he carries a tall case of shelves stacked with books, chained shut. A great book is chained to his "
             "wrist, and his air hose trails up behind him and ends in nothing.", MATS)
    for s in (1, -1):
        m.box(None, "b", s * 7, 0, -1, 11, 6, 15)  # lead boots
        lg = m.bone(None, s * 7, 6, 0)
        m.box(lg, "l", 0, 0, 0, 9, 18, 10)
        m.box(lg, "b", 0, 10, -5.4, 7, 4, 1)  # a brass knee plate
    tor = m.bone(None, 0, 24, 0)
    for k, (w, d, h) in enumerate(((22, 16, 6), (26, 18, 8), (28, 20, 8), (26, 18, 4))):  # the barrel chest, stepped
        m.box(tor, "l" if k % 2 == 0 else "k", 0, sum(x[2] for x in ((22, 16, 6), (26, 18, 8), (28, 20, 8), (26, 18, 4))[:k]), 0, w, h, d)
    m.box(tor, "b", 0, 24, 0, 20, 3, 16)  # the brass collar
    hd = m.bone(tor, 0, 27, -1)
    m.box(hd, "b", 0, 0, 0, 16, 16, 16)  # the square helmet
    m.box(hd, "w", 0, 4, -8.4, 10, 9, 1)  # the window, full of water
    m.box(hd, "s", 0, 5, -7.6, 7, 6, 1)  # his face pressed against it
    for s in (1, -1):
        m.box(hd, "w", s * 8.4, 5, 0, 1, 6, 6)  # small side windows
    m.box(hd, "b", 0, 16, 0, 6, 3, 6)
    # the air hose from the back of the helmet, up and away, cut
    h = m.bone(hd, 0, 12, 8, (-40, 0, 0))
    seg = h
    for k in range(5):
        seg = m.bone(seg, 0, 0 if k == 0 else 8, 0, (0 if k == 0 else -12, 0, 6))
        m.box(seg, "k", 0, 0, 0, 3, 8, 3)
    # the case of shelves on his back, taller than him, chained shut
    cs = m.bone(tor, 0, 2, 13)
    m.box(cs, "l", 0, 0, 0, 20, 46, 10)
    m.box(cs, "l", 0, 46, 0, 14, 4, 10)  # a peaked top
    m.box(cs, "l", 0, 50, 0, 6, 3, 10)
    for k in range(4):
        m.box(cs, "p", 0, 4 + k * 10, -0.5, 16, 7, 11)  # the books in it, edges showing
    for k in (14, 34):
        m.box(cs, "b", 0, k, 0, 22, 1, 12)  # the chains
    for s, (ar, fr) in ((1, ((-30, 0, 16), (-50, 0, 0))), (-1, ((-10, 0, -12), (-20, 0, 0)))):
        sh = m.bone(tor, s * 15, 22, 0)
        m.box(sh, "b", 0, -4, 0, 10, 8, 12)
        arm = m.bone(sh, 0, -2, 0, ar)
        m.box(arm, "l", 0, -14, 0, 8, 14, 8)
        el = m.bone(arm, 0, -14, 0, fr)
        m.box(el, "l", 0, -12, 0, 8, 12, 8)
        m.box(el, "b", 0, -16, 0, 9, 4, 9)  # a brass cuff and a heavy glove
        if s > 0:
            ch = m.bone(el, 0, -16, 0, (60, 0, 0))
            m.box(ch, "b", 0, -10, 0, 1, 10, 1)
            _book(m, ch, 0, -24, 0, (0, 0, 0), w=16, h=14)
    return m


# ---- C: the Tide of Pages --------------------------------------------------------------------------------------------
def tide():
    m = Mass("da_c", "C · The Tide of Pages",
             "A column of sea water rises out of the floor and holds a drowned scholar up inside it from the waist. His "
             "arms are spread, his head is thrown back, his long hair floats upward, and his mouth is open as if he "
             "is reading aloud. Rings of books circle the column at three heights, flapping like birds, and wet pages "
             "peel off his hands and stream up into the dark.", MATS)
    col = m.bone(None, 0, 0, 0)
    m.box(col, "w", 0, 0, 0, 30, 2, 30)  # a pool on the floor
    y = 2
    for k, (w, h) in enumerate(((20, 8), (14, 8), (12, 8), (13, 8), (16, 6))):  # the column, waisted, swirling
        b = m.bone(col, 0, y, 0, (0, k * 18, 0))
        m.box(b, "w", 0, 0, 0, w, h, w)
        y += h
    tor = m.bone(col, 0, y, 0, (-14, 0, 0))
    m.box(tor, "k", 0, 0, 0, 14, 8, 9)  # the wet robe at the waist
    m.box(tor, "s", 0, 8, 0, 14, 12, 9)  # the drowned chest
    m.box(tor, "k", 0, 18, 0, 16, 4, 10)
    hd = m.bone(tor, 0, 22, 0, (-30, 0, 0))
    m.box(hd, "s", 0, 0, 0, 3, 3, 3)
    m.box(hd, "s", 0, 3, 0, 9, 10, 9)
    m.box(hd, "k", 0, 4, -4.6, 5, 2, 1)  # the open mouth
    for x, z, ln in ((-3, 3, 18), (3, 3, 16), (0, 4, 20), (-4, 0, 12), (4, 0, 14)):  # hair floating up
        hb = m.bone(hd, x, 12, z, (-20, 0, x * 3))
        m.box(hb, "k", 0, 0, 0, 1, ln, 1)
    for s in (1, -1):
        arm = m.bone(tor, s * 8, 18, 0, (0, 0, s * 100))  # arms spread
        m.box(arm, "s", 0, -14, 0, 4, 14, 4)
        el = m.bone(arm, 0, -14, 0, (0, 0, s * -20))
        m.box(el, "s", 0, -14, 0, 4, 14, 4)
        for k in range(3):  # pages peeling off his hands and streaming up
            pg = m.bone(el, s * 2, -14 + k * 3, 2, (-30 - k * 10, 0, s * (-60 + k * 20)))
            m.box(pg, "p", 0, 0, 0, 5, 7, 1)
    # rings of books circling the column
    import math
    for ring, (up, rad, n) in enumerate(((10, 22, 6), (24, 26, 7), (40, 22, 6))):
        for k in range(n):
            a = math.radians(k * 360 / n + ring * 20)
            _book(m, None, round(math.cos(a) * rad), up + (k % 2) * 3, round(math.sin(a) * rad), (20, -math.degrees(a), 0),
                  w=4, h=6, open_=True)
    return m


def options():
    return [lectern(), diver(), tide()]
