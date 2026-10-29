"""The Choirmaster's silhouette round (docs/bosses/ROLLOUT.md): the Drowned Choir's Guardian, a Caster. "The singing was
coming from her all along." Hymns of salt, the Refrain, the rising tide, her choir of drowned and the undertow. Three
singers of the deep, in sea blue, salt white, coral, pearl and a cyan glow."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

SEA = Ramp("#0c1a26", "#122636", "#1a3448", "#24445c", "#305672")
SALT = Ramp("#8a969c", "#a6b2b6", "#c2ccce", "#dce4e4", "#f2f6f4")
CORAL = Ramp("#6a2a26", "#8a3a32", "#ac4e40", "#cc6650", "#e88466")
PEARL = Ramp("#a49c94", "#c0b8ae", "#dad2c8", "#f0eae2")
GLOW = Ramp("#2a9ab0", "#44c4d8", "#6ae0f0", "#a4f4ff")
SKIN = Ramp("#3a4c58", "#4c606c", "#607682", "#768c98")
MATS = {"w": (SEA, 2.6), "s": (SALT, 2.6), "c": (CORAL, 2.6), "p": (PEARL, 2.6), "g": (GLOW, 3.0), "k": (SKIN, 2.6)}


def _singer(m, base, arms_up=True, hair="w"):
    """A woman's upper body from the waist (base: height of the waist), singing, her hands raised to lead."""
    m.box(None, "k", 0, base, 0, 8, 12, 5)
    m.box(None, "k", 0, base + 12, 0, 3, 3, 3)
    hd = m.bone(None, 0, base + 15, 0)
    m.box(hd, "k", 0, 0, 0, 7, 8, 7)
    m.box(hd, "g", 0, 1, -3.6, 3, 2, 1)  # the singing mouth, lit
    m.box(hd, hair, 0, 5, 1.5, 9, 6, 6)
    m.box(hd, hair, 0, -14, 3, 8, 16, 2)  # the hair down her back, kelp
    for s in (1, -1):
        if arms_up:
            m.rod(None, "k", (s * 5, base + 11, 0), (s * 11, base + 18, -3), w=2)
            m.rod(None, "k", (s * 11, base + 18, -3), (s * 13, base + 27, -5), w=2)
        else:
            m.rod(None, "k", (s * 5, base + 11, 0), (s * 9, base + 2, -3), w=2)


# ---- A: the Conch Siren ----------------------------------------------------------------------------------------------
def conch_siren():
    m = Mass("ch_a", "A · The Conch Siren",
             "She lives in a great spiral conch as long as a boat, salt-white with a coral lip, and drags it along the "
             "floor behind her the way a hermit crab does. Out of its mouth rises the Choirmaster herself to the waist, her "
             "arms raised to lead, her hair of kelp down her back. The singing comes out of the shell as well as out of "
             "her, and when she sings the shell's spire glows from inside.", MATS)
    sh = m.bone(None, 0, 0, 8)
    for k in range(9):  # the spiral, stepping in to its spire behind her
        t = k / 8
        w = round(26 - 16 * t)
        z = k * 4.2
        y = 2 + 4 * math.sin(t * 3.0) + 6 * t
        m.box(sh, "s" if k % 2 else "p", 0, y, z, w, round(18 - 9 * t), 5)
        if k % 2 == 0:
            m.box(sh, "g", 0, y + round(18 - 9 * t) - 1, z, 3, 2, 3)
    m.box(sh, "c", 0, 0, -3, 28, 22, 3)  # the coral lip round its mouth
    m.box(sh, "w", 0, 3, -4.6, 18, 16, 1)
    _singer(m, 14)
    return m


# ---- B: the Lionfish Diva --------------------------------------------------------------------------------------------
def lionfish_diva():
    m = Mass("ch_b", "B · The Lionfish Diva",
             "A woman of the deep with a lionfish's colours, coral red striped with salt white. A great fan of long "
             "venomous fin spines stands up behind her head and shoulders like a choir collar, with webbing between, and "
             "her long trailing fins hang from her arms like sleeves and down to the floor like a gown. She floats a "
             "little above the floor, singing, her fins moving in the water that isn't there.", MATS)
    gn = m.bone(None, 0, 4, 0)
    for k, (w, d) in enumerate(((22, 16), (18, 12), (14, 10), (11, 8))):
        m.box(gn, "c" if k % 2 == 0 else "s", 0, k * 6, 2, w, 6, d)  # the gown of trailing fins
    for x, z in ((-11, 6), (11, 6), (0, 10), (-7, -4), (7, -4)):
        m.box(gn, "c", x, -4, z, 3, 8, 2)  # its tatters
    _singer(m, 26, arms_up=False, hair="c")
    for s in (1, -1):  # the sleeve fins
        m.box(None, "c", s * 10, 16, -2, 2, 14, 8)
        m.box(None, "s", s * 10.5, 18, -2, 1, 10, 6)
    for k in range(9):  # the fan of spines behind her head and shoulders
        a = math.radians(-80 + k * 20)
        tip = (round(30 * math.sin(a), 1), round(42 + 26 * math.cos(a), 1), 5)
        m.rod(None, "c" if k % 2 else "s", (0, 40, 4), tip, w=1)
        m.box(None, "c", tip[0] * 0.6, tip[1] * 0.6 + 40 * 0.4 - 2, 5, 5, 5, 1)  # the webbing
    return m


# ---- C: the Cantor -----------------------------------------------------------------------------------------------------
def cantor():
    m = Mass("ch_c", "C · The Cantor",
             "A great pale octopus of the deep. It stands up on its eight arms the way an octopus walks on the sea floor, "
             "its mantle high above them like a domed head, and two of its arms are raised to lead the choir. Pearls are "
             "set along the arms in rows, and its siphon is a long horn it sings through. Its eye is a single glowing "
             "slit under the dome.", MATS)
    for k in range(6):  # the six arms it stands on, curling out along the floor
        a = math.radians(30 + k * 60)
        mid = (round(10 * math.cos(a), 1), 10, round(10 * math.sin(a), 1))
        tip = (round(24 * math.cos(a), 1), 1, round(24 * math.sin(a), 1))
        m.rod(None, "k", (round(4 * math.cos(a), 1), 22, round(4 * math.sin(a), 1)), mid, w=4)
        m.rod(None, "k", mid, tip, w=3)
        m.box(None, "p", mid[0], mid[1] - 2, mid[2], 2, 2, 2)
    mt = m.bone(None, 0, 22, 2)
    for w, h, d, y in ((18, 8, 16, 0), (22, 12, 20, 8), (20, 10, 18, 20), (14, 6, 12, 30)):
        m.box(mt, "k", 0, y, 0, w, h, d)  # the mantle
    m.box(mt, "g", 0, 6, -8.6, 8, 2, 1)  # the eye
    m.rod(mt, "c", (6, 4, -6), (14, 10, -14), w=3)  # the siphon horn
    m.box(mt, "c", 15, 9, -15, 5, 5, 5)
    for s in (1, -1):  # the two arms it leads with
        m.rod(mt, "k", (s * 8, 4, -4), (s * 18, 18, -8), w=3)
        m.rod(mt, "k", (s * 18, 18, -8), (s * 20, 34, -6), w=2)
    return m


def options():
    return [conch_siren(), lionfish_diva(), cantor()]
