"""The Sleeper's silhouette round (docs/bosses/ROLLOUT.md): the Nightmare gate's Lord, a Brute. "You've been here
before. You haven't." It sleepwalks between dream and waking: a restless fist, heavy eyelids that dim the room, night
terrors, a step through the dream, and at the end it wakes, and everything does. Three shapes in a cream nightgown,
pallid skin, midnight blue, old wood and brass, a candle's flame, patchwork, and a lilac dream glow."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

GOWN = Ramp("#6e6458", "#8e8474", "#aea492", "#ccc4b0", "#e8e2d0")
NIGHT = Ramp("#0e1024", "#161a36", "#20264a", "#2c3460", "#3a447a")
DREAM = Ramp("#8a4c9a", "#b070c0", "#d498dc", "#ecc0f0", "#fae4fc")
SKIN = Ramp("#5a6a70", "#7a8a8e", "#9aa8aa", "#b8c4c4", "#d4dcdc")
WOOD = Ramp("#2a1a10", "#3c2616", "#52341e", "#6a4428", "#845836")
BRASS = Ramp("#5a4214", "#86661e", "#b08c30", "#d4b04c", "#ecd27a")
FLAME = Ramp("#d8781a", "#f4a030", "#ffc85a", "#ffe49a", "#fff6d8")
RED = Ramp("#4a1818", "#6c2420", "#8c342c", "#a8463a", "#c45c4a")
TEAL = Ramp("#123a3a", "#1c5050", "#286868", "#368282", "#4a9c9a")
OCHRE = Ramp("#5a4418", "#7c5e22", "#9c7a30", "#b89440", "#d2ae58")
FUR = Ramp("#3a2818", "#523822", "#6c4a2e", "#86603c", "#a07a50")
MATS = {"w": (GOWN, 2.6), "n": (NIGHT, 2.4), "d": (DREAM, 3.0), "s": (SKIN, 2.6), "o": (WOOD, 2.4), "b": (BRASS, 2.6),
        "f": (FLAME, 3.0), "r": (RED, 2.6), "t": (TEAL, 2.6), "y": (OCHRE, 2.6), "u": (FUR, 2.6)}


def _nightcap(m, parent, y, w, bend=-36, n=6, mat="n", tip="d"):
    """A long floppy nightcap from the top of a head, flopping over to one side, a pom-pom at its tip."""
    b, h = parent, y
    for k in range(n):
        b = m.bone(b, 0, h, 0, (6 if k else 0, 0, bend if k == 0 else bend / 3))
        ww = max(2, round(w * (1 - k / n)))
        m.box(b, mat, 0, 0, 0, ww, 5, ww)
        h = 5
    m.box(b, tip, 0, 5, 0, 4, 4, 4)


# ---- A: the Sleepwalker ----------------------------------------------------------------------------------------------
def sleepwalker():
    m = Mass("sl_a", "A · The Sleepwalker",
             "A giant walking in his sleep. A long cream nightgown to his ankles, bare grey feet, a long floppy nightcap "
             "hanging over his shoulder. His eyes are shut and his head hangs, and his arms are held straight out in "
             "front of him the way sleepwalkers are drawn. From one hand a candle in a brass holder swings by its ring, "
             "still lit. The hem of his gown frays into a lilac dream mist round his feet.", MATS)
    for s in (1, -1):
        m.box(None, "s", s * 7, 0, -3, 8, 4, 12)  # bare feet
        m.box(None, "s", s * 7, 4, 0, 6, 4, 6)
    rb = m.bone(None, 0, 6, 0)
    y = 0
    for k, (w, d, h) in enumerate(((32, 24, 8), (30, 22, 12), (28, 20, 14), (27, 19, 14), (28, 19, 12))):  # the gown
        m.box(rb, "w", 0, y, 0, w, h, d)
        y += h
    for k in range(9):  # the hem fraying into mist
        a = k / 9 * 2 * math.pi
        m.box(None, "d", round(15 * math.cos(a)), 1 + (k % 3) * 2, round(11 * math.sin(a)), 4, 3 + (k % 2) * 2, 4)
    tor = m.bone(rb, 0, y, 0)
    m.box(tor, "w", 0, 0, 0, 32, 10, 20)  # broad sloping shoulders
    m.box(tor, "w", 0, 10, 1, 22, 3, 15)
    m.box(tor, "n", 0, 4, -10.2, 2, 6, 1)  # buttons down the yoke
    hd = m.bone(tor, 0, 12, -4, (28, 0, -14))  # the head hanging forward and to the side, asleep
    m.box(hd, "s", 0, 0, 0, 14, 15, 13)
    for s in (1, -1):
        m.box(hd, "n", s * 3.5, 7, -6.8, 4, 1, 1)  # eyes shut
    m.box(hd, "n", 0, 2, -6.8, 4, 2, 1)  # the mouth slack
    m.box(hd, "s", 0, 6, -7.5, 2, 3, 2)
    _nightcap(m, hd, 15, 14, bend=-44)
    # arms held straight out before him
    sh = 6 + y + 8
    for s in (1, -1):
        m.rod(None, "w", (s * 15, sh, 0), (s * 13, sh - 2, -20), w=9)  # the sleeves
        m.rod(None, "s", (s * 13, sh - 2, -20), (s * 11, sh - 6, -38), w=7)
        hand = m.bone(None, s * 11, sh - 7, -41, (40, 0, 0))
        m.box(hand, "s", 0, -4, 0, 7, 6, 4)
        for k in range(3):
            m.box(hand, "s", (k - 1) * 2, -9, -1, 1, 5, 1)  # fingers hanging
    # the candle swinging by its ring from the right hand
    cx, cy, cz = -11, sh - 16, -42
    m.rod(None, "b", (cx, sh - 9, cz), (cx, cy, cz), w=1)
    m.box(None, "b", cx, cy - 2, cz, 8, 2, 8)
    m.box(None, "w", cx, cy, cz, 3, 7, 3)
    m.box(None, "f", cx, cy + 7, cz, 2, 4, 2)
    m.box(None, "f", cx, cy + 11, cz, 1, 2, 1)
    return m


# ---- B: the Four-Poster ----------------------------------------------------------------------------------------------
def four_poster():
    m = Mass("sl_b", "B · The Four-Poster",
             "He never got out of bed: the bed walks. A great four-poster of black wood stalks on its four carved posts "
             "like legs, its canopy high above, torn curtains of midnight blue hanging from it. The Sleeper lies in "
             "it under a patchwork quilt, his head on the pillow and his nightcap trailing over the headboard. One "
             "huge arm hangs over the side to the floor, dragging its fist. A dreamcatcher swings from the canopy.", MATS)
    H = 38  # the mattress
    for sx in (1, -1):
        for sz in (1, -1):
            m.rod(None, "o", (sx * 18, H, sz * 30), (sx * 23, 16, sz * 36), w=4)  # the posts, walking
            m.rod(None, "o", (sx * 23, 16, sz * 36), (sx * 22, 2, sz * 37), w=4)
            m.box(None, "o", sx * 22, 0, sz * 37, 6, 3, 6)
            m.box(None, "o", sx * 23, 15, sz * 36, 6, 3, 6)  # a carved knob at the knee
            m.box(None, "o", sx * 18, H, sz * 30, 3, 56 if sz > 0 else 50, 3)  # on up to the canopy
    bd = m.bone(None, 0, H, 0)
    m.box(bd, "o", 0, 0, 0, 38, 5, 62)  # the frame
    m.box(bd, "w", 0, 5, 0, 34, 5, 58)  # the mattress
    for k in range(4):  # the patchwork quilt over him, a mound where he lies
        for j in range(3):
            m.box(bd, ("r", "t", "y")[(k + j) % 3], -11 + j * 11, 10 + (2 if j == 1 else 0) + (1 if k in (1, 2) else 0), -18 + k * 10, 11, 3, 10)
    m.box(bd, "w", 0, 10, 24, 22, 4, 10)  # the pillow
    hd = m.bone(bd, 0, 13, 20, (-80, 0, 0))  # his head on it, face up
    m.box(hd, "s", 0, 0, 0, 16, 15, 14)
    for s in (1, -1):
        m.box(hd, "n", s * 4, 8, -7.2, 4, 1, 1)
    _nightcap(m, hd, 15, 15, bend=-20, n=5)
    m.box(bd, "o", 0, 5, 31, 38, 32, 3)  # the headboard
    m.box(bd, "o", 0, 37, 31, 24, 6, 3)
    m.box(bd, "o", 0, 5, -31, 38, 14, 3)  # the footboard
    # the arm hanging over the side to the floor, the fist dragging
    m.rod(None, "s", (17, H + 12, 10), (24, 24, 4), w=8)
    m.rod(None, "s", (24, 24, 4), (25, 8, -4), w=8)
    m.box(None, "s", 25, 0, -6, 12, 10, 12)
    # the canopy and its torn curtains
    cn = m.bone(None, 0, H + 52, 0)
    m.box(cn, "o", 0, 0, 0, 42, 4, 66)
    m.box(cn, "o", 0, 4, 0, 30, 3, 54)
    for sx in (1, -1):
        for z, ln in ((-24, 30), (-12, 22), (6, 34), (20, 26)):
            m.box(cn, "n", sx * 20, -ln, z, 1, ln, 9)
    for z, ln in ((-10, 18), (10, 26)):
        m.box(cn, "n", z, -ln, -32, 9, ln, 1)
    dc = m.bone(cn, 0, -6, -33)  # the dreamcatcher
    for k in range(8):
        a = k / 8 * 2 * math.pi
        m.box(dc, "b", round(5 * math.cos(a)), round(-8 + 5 * math.sin(a)), 0, 2, 2, 1)
    for x in (-3, 0, 3):
        m.box(dc, "d", x, -20 + abs(x), 0, 1, 6, 1)
    return m


# ---- C: the Bear -----------------------------------------------------------------------------------------------------
def bear():
    m = Mass("sl_c", "C · The Bear",
             "The thing a child hugs to sleep, grown huge in the dream. A patchwork teddy bear taller than a house "
             "door, its brown fur worn bald in places and mended with squares of red, teal and ochre cloth. One eye is "
             "a button and the other is a stitched X. A big red bow at its neck. A seam has split open across its "
             "belly and the stuffing pushes out, glowing lilac. It walks on stubby legs with its great round paws held "
             "out to hug you.", MATS)
    for s in (1, -1):
        m.box(None, "u", s * 10, 0, 0, 15, 18, 17)  # stubby legs
        m.box(None, "y", s * 10, 3, -8.8, 10, 10, 1)  # the foot pads
    bd = m.bone(None, 0, 14, 0)
    y = 0
    for w, d, h in ((34, 28, 10), (40, 32, 12), (40, 32, 12), (36, 28, 10), (30, 24, 6)):  # the round belly
        m.box(bd, "u", 0, y, 0, w, h, d)
        y += h
    for x, yy, z, w, h, mat in ((-11, 10, -16.4, 10, 12, "r"), (12, 24, -16.4, 9, 9, "t"), (-14, 32, -14.4, 8, 8, "y"), (17, 8, 4, 1, 12, "t")):
        m.box(bd, mat, x, yy, z, w, h, 1 if z < 0 else 10)  # patches
    m.box(bd, "n", 0, 16, -16.6, 22, 1, 1)  # the split seam
    m.box(bd, "w", 0, 13, -17.5, 16, 3, 2)  # the stuffing pushing out
    m.box(bd, "d", 0, 14, -18.6, 10, 2, 1)
    for x in (-6, -1, 4):
        m.box(bd, "w", x, 9 + abs(x) * 0.3, -18, 4, 4, 3)
    m.box(bd, "n", 20.4, 4, 0, 1, 40, 1)  # side seams
    m.box(bd, "n", -20.4, 4, 0, 1, 40, 1)
    hd = m.bone(bd, 0, y, -2, (10, 0, 6))
    m.box(hd, "u", 0, 0, 0, 28, 22, 24)
    m.box(hd, "y", 0, 2, -13, 13, 9, 5)  # the muzzle
    m.box(hd, "n", 0, 8, -15.8, 5, 3, 2)  # the nose
    m.box(hd, "n", 0, 4, -15.6, 1, 4, 1)
    m.box(hd, "n", 7, 13, -12.4, 5, 5, 1)  # the button eye
    m.box(hd, "b", 7, 14.5, -12.8, 2, 2, 1)
    for a in (1, -1):  # the other eye, a stitched X
        x = m.bone(hd, -7, 15, -12.4, (0, 0, a * 45))
        m.box(x, "r", 0, -3.5, 0, 1, 7, 1)
    for s in (1, -1):
        e = m.bone(hd, s * 11, 20, 0, (0, 0, s * -20))
        m.box(e, "u", 0, 0, 0, 10, 9, 5)
        m.box(e, "r", 0, 2, -2.6, 6, 5, 1)
    m.box(hd, "t", -9, 16, 6, 8, 6, 1)  # a patch on its head
    bw = m.bone(bd, 0, y - 2, -12)  # the bow
    m.box(bw, "r", 0, -1, 0, 5, 5, 4)
    for s in (1, -1):
        m.box(bw, "r", s * 7, -2, 0, 9, 8, 3)
        m.box(bw, "r", s * 5, -10, 0, 3, 8, 2)
    # the arms out to hug you
    for s in (1, -1):
        m.rod(None, "u", (s * 18, 14 + 40, -2), (s * 20, 42, -22), w=12)
        m.box(None, "u", s * 20, 34, -27, 13, 13, 12)  # the round paws
        m.box(None, "y", s * 20, 36, -33.6, 8, 8, 1)
    return m


def options():
    return [sleepwalker(), four_poster(), bear()]
