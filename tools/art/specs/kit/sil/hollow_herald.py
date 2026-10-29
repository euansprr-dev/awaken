"""The Hollow Herald's silhouette round (docs/bosses/ROLLOUT.md): the Abyssal Shrine's Lord, a Caster. "Nothing. No
sound at all." Void bolts, an unlit shrine, then the Event Horizon. Three shapes built round an absence, in void black,
abyssal violet glass, ash, tarnished gold, a cold violet-white light, and blue-black cloth."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

VOID = Ramp("#040306", "#08060c", "#0e0a14", "#15101c", "#1d1626")
ABYSS = Ramp("#1a0e30", "#2a1648", "#3c2064", "#522e84", "#6c42a6")
ASH = Ramp("#4e4a50", "#6a6670", "#88848c", "#a6a2a8", "#c4c0c4")
GOLDT = Ramp("#3e3018", "#5c4824", "#7a6232", "#987c44", "#b49858")
GLOWV = Ramp("#8a70e0", "#a892f0", "#c6b6fa", "#e2d8ff", "#f8f4ff")
CLOTHB = Ramp("#0a0c14", "#12141e", "#1a1e2a", "#242a3a", "#30384c")
MATS = {"v": (VOID, 1.6), "a": (ABYSS, 2.6), "p": (ASH, 2.6), "g": (GOLDT, 2.6), "l": (GLOWV, 3.0), "c": (CLOTHB, 2.4)}


def _ring(m, bone, r, n, mat, size, depth=2):
    """A ring of small cubes in the bone's x-up plane, centred on its pivot."""
    for k in range(n):
        a = k / n * 2 * math.pi
        m.box(bone, mat, round(r * math.cos(a), 1), round(r * math.sin(a) - size / 2, 1), 0, size, size, depth)


def _ball(m, bone, R, mat, step=3):
    """A stepped sphere centred on the bone's pivot."""
    y = -R
    while y < R:
        mid = y + step / 2
        w = max(2, round(2 * math.sqrt(max(0.0, R * R - mid * mid))))
        m.box(bone, mat, 0, y, 0, w, step, w)
        y += step


# ---- A: the Empty Robe -----------------------------------------------------------------------------------------------
def empty_robe():
    m = Mass("hh_a", "A · The Empty Robe",
             "A herald's robe with no one in it, floating above the floor. Inside the hood there is only black, ringed "
             "with a thin cold light, and a halo of black glass stands behind it. It raises a long herald's trumpet to "
             "where its mouth should be, and from the trumpet hangs a banner with nothing on it at all. The empty "
             "sleeves end in darkness, and the hem frays into the air.", MATS)
    for k in range(10):  # the hem fraying into the air
        a = k / 10 * 2 * math.pi
        m.box(None, "c", round(12 * math.cos(a), 1), 1 + (k % 3) * 2, round(9 * math.sin(a), 1), 4, 7 - (k % 3) * 2, 3)
    rb = m.bone(None, 0, 8, 0)
    y = 0
    for w, d, h in ((26, 20, 10), (24, 18, 12), (21, 16, 12), (19, 14, 10), (21, 14, 8)):
        m.box(rb, "c", 0, y, 0, w, h, d)
        y += h
    m.box(rb, "g", 0, 0, -10.4, 3, y - 2, 1)  # the gold band down the front
    m.box(rb, "a", 0, 0, -10.8, 1, y - 4, 1)
    sh = y + 8
    m.box(None, "c", 0, sh - 2, 0, 28, 5, 16)  # the shoulders of the mantle
    m.box(None, "g", 0, sh - 3, 0, 29, 2, 17)
    hd = m.bone(None, 0, sh + 3, -1, (6, 0, 0))
    m.box(hd, "c", 0, 0, 0, 17, 18, 16)  # the hood
    m.box(hd, "c", 0, 14, 5, 11, 7, 9)  # its peak, falling back
    m.box(hd, "v", 0, 3, -8.2, 11, 12, 1)  # nothing inside
    for x, yy, w, h in ((0, 15, 13, 1), (0, 2, 13, 1), (6, 3, 1, 12), (-6, 3, 1, 12)):
        m.box(hd, "l", x, yy, -8.6, w, h, 1)  # the thin cold light round it
    hl = m.bone(hd, 0, 10, 12, (-14, 0, 0))  # the halo of black glass behind the hood
    _ring(m, hl, 15, 30, "a", 4, 2)
    for k in range(4):
        a = k / 4 * 2 * math.pi + math.pi / 4
        m.box(hl, "a", round(21 * math.cos(a)), round(21 * math.sin(a) - 2), 0, 3, 5, 2)
    # the right sleeve raises the trumpet to the hood; the left hangs empty
    m.rod(None, "c", (-13, sh, 0), (-16, sh - 6, -10), w=7)
    m.rod(None, "c", (-16, sh - 6, -10), (-9, sh + 3, -20), w=6)
    m.box(None, "v", -9, sh + 1, -22, 4, 4, 2)
    m.rod(None, "c", (13, sh, 0), (16, sh - 16, -2), w=7)
    m.rod(None, "c", (16, sh - 16, -2), (17, sh - 30, -6), w=8)
    m.box(None, "v", 17, sh - 33, -7, 5, 3, 5)
    t0, t1 = (-2, sh + 9, -12), (-7, sh + 20, -50)
    m.rod(None, "g", t0, t1, w=2)  # the herald's trumpet
    for k, w in enumerate((4, 6, 8)):  # its bell, flaring
        f0 = tuple(t1[i] + (t1[i] - t0[i]) * 0.05 * k for i in range(3))
        f1 = tuple(t1[i] + (t1[i] - t0[i]) * 0.05 * (k + 1) for i in range(3))
        m.rod(None, "g", f0, f1, w=w)
    bx, bz = -5, -32  # the banner of nothing, hanging from the trumpet
    by = sh + 15
    m.box(None, "g", bx, by, bz, 2, 1, 16)
    m.box(None, "v", bx, by - 22, bz, 1, 22, 14)
    for k in range(3):
        m.box(None, "v", bx, by - 26 - (k % 2) * 2, bz - 5 + k * 5, 1, 5, 3)
        m.box(None, "g", bx, by - 29 - (k % 2) * 2, bz - 5 + k * 5, 1, 3, 1)
    return m


# ---- B: the Black Sun ------------------------------------------------------------------------------------------------
def black_sun():
    m = Mass("hh_b", "B · The Black Sun",
             "A gaunt, ash-grey figure hanging in the air, its toes pointing down. It holds a sphere of perfect black "
             "over its head, and round the sphere a tilted ring of cold light turns, the way light circles a black hole. "
             "Its tattered robes stream upward into the sphere, as if they are being pulled in, and its head is thrown "
             "back to look at it.", MATS)
    for s in (1, -1):  # the thin legs, hanging
        m.rod(None, "p", (s * 4, 34, 0), (s * 5, 18, 3), w=4)
        m.rod(None, "p", (s * 5, 18, 3), (s * 4, 6, 1), w=3)
        m.rod(None, "p", (s * 4, 6, 1), (s * 4, 1, -2), w=3)
    m.box(None, "c", 0, 26, 0, 15, 12, 10)  # the robe at its hips
    for k in range(5):
        m.box(None, "c", -6 + k * 3, 18 + (k % 2) * 3, -4 + (k % 3), 3, 9 - (k % 2) * 3, 3)
    tor = m.bone(None, 0, 38, 0)
    m.box(tor, "p", 0, 0, 0, 10, 14, 7)  # the gaunt chest
    for k in range(3):
        m.box(tor, "a", 0, 4 + k * 3, -3.8, 8, 1, 1)  # the ribs showing
    m.box(tor, "c", 0, 9, 0, 16, 6, 9)  # the ragged mantle at the shoulders
    hd = m.bone(tor, 0, 16, 0, (-34, 0, 0))  # its head thrown back
    m.box(hd, "p", 0, 0, 0, 3, 3, 3)
    m.box(hd, "p", 0, 2, 0, 8, 10, 8)
    for s in (1, -1):
        m.box(hd, "v", s * 2, 7, -4.2, 2, 2, 1)
    m.box(hd, "v", 0, 3, -4.2, 3, 2, 1)
    # the arms raised, holding the black sphere overhead
    C = (0, 84, -2)
    for s in (1, -1):
        m.rod(None, "p", (s * 7, 52, 0), (s * 12, 63, -2), w=3)
        m.rod(None, "p", (s * 12, 63, -2), (s * 8, 72, -2), w=3)
        m.box(None, "p", s * 7, 71, -2, 4, 4, 4)
    sun = m.bone(None, *C)
    _ball(m, sun, 12, "v")
    ring = m.bone(sun, 0, 0, 0, (72, 0, 18))  # the accretion ring, tilted
    _ring(m, ring, 22, 46, "l", 4, 3)
    _ring(m, ring, 17, 36, "a", 4, 3)
    # the robes streaming up into the sphere
    for a, b in (((7, 52, 2), (6, 66, 8)), ((-7, 52, 2), (-9, 64, 9)), ((5, 36, 4), (11, 58, 10)), ((-6, 34, 4), (-13, 58, 8)),
                 ((0, 30, 5), (1, 56, 12)), ((12, 64, -2), (15, 74, 2)), ((-12, 64, -2), (-15, 76, 0))):
        mid = b
        m.rod(None, "c", a, mid, w=4)
        m.rod(None, "c", mid, (C[0] + (mid[0] - C[0]) * 0.55, C[1] - 6, C[2] + (mid[2] - C[2]) * 0.5), w=2)
    return m


# ---- C: the Ring-Head ------------------------------------------------------------------------------------------------
def ring_head():
    m = Mass("hh_c", "C · The Ring-Head",
             "Very tall and very thin, in a long blue-black coat buttoned in tarnished gold. Where its head should be "
             "there is a ring of violet-black glass, lined with a thin light, and nothing at all inside it: you can see "
             "straight through. Its arms hang almost to the floor, the fingers too long. From one hand a blank scroll "
             "unrolls down to the floor and along it, with nothing written on it.", MATS)
    for s in (1, -1):
        m.rod(None, "c", (s * 4, 44, 0), (s * 4, 4, 1), w=4)  # long thin legs
        m.box(None, "v", s * 4, 0, -2, 5, 4, 9)  # pointed shoes
    cb = m.bone(None, 0, 20, 0)
    m.box(cb, "c", 0, 0, 1, 18, 12, 11)  # the long coat's skirt, split at the front
    m.box(cb, "v", 0, 0, -4.8, 2, 12, 1)
    m.box(cb, "c", 0, 12, 0, 16, 14, 9)
    m.box(cb, "c", 0, 26, 0, 15, 18, 8)
    m.box(cb, "c", 0, 44, 0, 18, 5, 9)  # the narrow, high shoulders
    for k in range(6):
        m.box(cb, "g", 0, 16 + k * 5, -4.6, 2, 2, 1)  # the buttons
    m.box(cb, "c", 0, 49, 1, 12, 8, 8)  # the tall collar, open on nothing
    m.box(cb, "v", 0, 50, -2.4, 6, 7, 1)
    m.box(None, "v", 0, 77, 0, 3, 6, 3)  # the neck
    hd = m.bone(None, 0, 95, 0)
    _ring(m, hd, 11, 18, "a", 5, 4)  # the ring of glass
    _ring(m, hd, 8, 16, "l", 2, 1)  # its thin lining of light
    for s in (1, -1):  # the long arms, almost to the floor
        m.rod(None, "c", (s * 9, 70, 0), (s * 12, 46, -1), w=4)
        m.rod(None, "c", (s * 12, 46, -1), (s * 12, 22, -4), w=3)
        m.box(None, "p", s * 12, 17, -4, 3, 5, 3)  # the hands
        for k in (-1, 0, 1):
            m.rod(None, "p", (s * 12 + k, 17, -4), (s * 12 + k * 2, 7, -5 - abs(k)), w=1)
    # the blank scroll from the right hand, down to the floor and along it
    m.rod(None, "g", (-18, 18, -8), (-6, 18, -8), w=2)
    m.box(None, "p", -12, 1, -8, 8, 17, 1)
    m.box(None, "p", -12, 0, -30, 8, 1, 44)
    m.rod(None, "p", (-16, 2, -53), (-8, 2, -53), w=4)
    return m


def options():
    return [empty_robe(), black_sun(), ring_head()]
