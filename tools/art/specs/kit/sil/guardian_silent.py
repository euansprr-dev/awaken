"""The Great Listener's silhouette round (docs/bosses/ROLLOUT.md): the Silent Halls' Guardian, a Brute. "Every Listener in
the halls is one of its ears." It hears your heartbeat; Found You, the Echo Scream, Absolute Silence, and its ears.
Three blind hunters, in pale stone, grey hide, felt black, old brass and a cold white glow."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

PALE = Ramp("#6a6c70", "#84868a", "#9ea0a4", "#b8babe", "#d2d4d6")
HIDE = Ramp("#2a2a30", "#383840", "#484852", "#5a5a66", "#6e6e7a")
FELT = Ramp("#08080a", "#0e0e12", "#16161c", "#202028")
FLESH = Ramp("#5a4448", "#745a5e", "#8e7276", "#a88c8e")
GLOW = Ramp("#9aa8c8", "#bcc8e4", "#dce4f6", "#f6f8ff")
MATS = {"p": (PALE, 2.6), "h": (HIDE, 2.6), "f": (FELT, 1.6), "k": (FLESH, 2.6), "g": (GLOW, 3.0)}


# ---- A: the Ear ------------------------------------------------------------------------------------------------------
def the_ear():
    m = Mass("gl_a", "A · The Ear",
             "A gaunt pale giant, stooped, with arms that hang to its knees. It has no face at all: its whole head is one "
             "enormous ear, a shell of folded cartilage as tall as a man, turned toward whatever it is hearing. Its mouth "
             "is a long slit down the middle of its chest, and when it screams, the chest opens.", MATS)
    for s in (1, -1):
        m.rod(None, "p", (s * 5, 34, 0), (s * 6, 17, -3), w=4)
        m.rod(None, "p", (s * 6, 17, -3), (s * 6, 2, 0), w=3)
        m.box(None, "p", s * 6, 0, -2, 5, 2, 8)
    tb = m.bone(None, 0, 34, 0, (14, 0, 0))
    m.box(tb, "h", 0, 0, 0, 14, 8, 9)
    m.box(tb, "p", 0, 8, 0, 18, 16, 10)
    m.box(tb, "f", 0, 9, -5.4, 2, 13, 1)  # the mouth down its chest
    m.box(tb, "k", 0, 10, -5.6, 1, 11, 1)
    m.box(tb, "p", 0, 24, 0, 24, 4, 10)
    hd = m.bone(tb, 0, 28, -2, (-10, 25, 0))
    m.box(hd, "p", 0, 0, 1, 6, 4, 6)
    for y, w, d, x in ((2, 10, 4, 0), (8, 16, 4, 1), (16, 20, 4, 2), (24, 20, 4, 2), (32, 16, 4, 1), (38, 10, 4, 0)):
        m.box(hd, "p", x, y, 0, w, 7, d)  # the ear's shell
    for y, w, x in ((10, 10, 2), (18, 12, 3), (26, 12, 3), (32, 7, 2)):
        m.box(hd, "k", x, y, -2.4, w, 6, 1)  # its folds, darker inside
    m.box(hd, "f", 1, 14, -2.8, 4, 8, 1)
    for s in (1, -1):
        m.rod(tb, "p", (s * 12, 24, 0), (s * 15, 2, -4), w=3)
        m.rod(tb, "p", (s * 15, 2, -4), (s * 16, -20, -6), w=3)
        for k in (-1, 0, 1):
            m.rod(tb, "p", (s * 16 + k, -20, -6), (s * 16 + k * 2, -30, -8), w=1)
    return m


# ---- B: the Hush Bat -------------------------------------------------------------------------------------------------
def hush_bat():
    m = Mass("gl_b", "B · The Hush Bat",
             "A blind bat the size of a barn, that walks the halls on its folded wings like a vampire bat, the long finger "
             "bones of the wings for front legs. Its ears are huge, twice the height of its head, ribbed and twitching; its "
             "face is a wrinkled nose-leaf over a wide mouth full of needle teeth, and its eyes are stitched shut. It "
             "finds you by screaming.", MATS)
    for s in (1, -1):
        m.rod(None, "h", (s * 6, 22, 10), (s * 9, 10, 14), w=4)  # the hind legs, crouched
        m.rod(None, "h", (s * 9, 10, 14), (s * 8, 1, 10), w=3)
        m.box(None, "h", s * 8, 0, 9, 5, 2, 6)
        m.rod(None, "h", (s * 9, 36, -6), (s * 26, 46, 0), w=4)  # the folded wing: up to the wrist
        m.rod(None, "h", (s * 26, 46, 0), (s * 24, 2, -14), w=3)  # and down the long fingers to the floor
        m.box(None, "f", s * 18, 10, -6, 2, 32, 14)  # the folded membrane
        m.rod(None, "p", (s * 26, 46, 0), (s * 28, 52, 2), w=2)  # the thumb claw
    bd = m.bone(None, 0, 20, 4, (-40, 0, 0))
    m.box(bd, "h", 0, 0, 0, 18, 22, 14)
    m.box(bd, "f", 0, 6, 4, 16, 14, 8)
    hd = m.bone(None, 0, 40, -12)
    m.box(hd, "h", 0, -4, 0, 14, 12, 12)
    m.box(hd, "k", 0, -2, -7, 8, 6, 3)  # the nose-leaf
    m.box(hd, "k", 0, 2, -7.2, 3, 6, 1)
    m.box(hd, "f", 0, -6, -6.4, 10, 3, 2)  # the mouth
    for x in (-4, -2, 0, 2, 4):
        m.box(hd, "p", x, -6, -7.6, 1, 2, 1)
    for x in (-4, 4):
        m.box(hd, "k", x, 3, -6.2, 3, 1, 1)  # the stitched eyes
    for s in (1, -1):
        m.box(hd, "h", s * 8, 6, 1, 8, 22, 4)  # the great ears
        m.box(hd, "k", s * 8, 8, -1.2, 5, 18, 1)
        m.box(hd, "h", s * 9, 26, 1, 5, 6, 3)
    return m


# ---- C: the Many-Eared -----------------------------------------------------------------------------------------------
def many_eared():
    m = Mass("gl_c", "C · The Many-Eared",
             "A great pale mass that crawls on six thin legs like a tick, its back grown all over with ears, dozens of them, "
             "of every size, the way fungus grows on a log, all turning together toward a sound. In front, on a long "
             "neck, it carries an eyeless head that is mostly mouth, with felt-black lips.", MATS)
    bd = m.bone(None, 0, 16, 4)
    m.box(bd, "p", 0, 0, 0, 30, 14, 30)
    m.box(bd, "p", 0, 14, 0, 24, 6, 24)
    for k in range(22):  # the ears on its back
        a = k * 2.4
        rr = 4 + (k % 5) * 2.4
        x, z = round(rr * math.cos(a), 1), round(rr * math.sin(a), 1)
        h = 5 + (k * 7) % 6
        m.box(bd, "k" if k % 3 == 0 else "p", x, 18, z, 2, h, 4 if k % 2 else 3)
    for s in (1, -1):
        for zz in (-10, 2, 14):
            m.rod(None, "h", (s * 14, 18, zz), (s * 26, 28, zz * 1.3), w=2)
            m.rod(None, "h", (s * 26, 28, zz * 1.3), (s * 34, 0, zz * 1.6), w=2)
    m.rod(None, "p", (0, 24, -10), (0, 36, -26), w=6)  # the long neck
    hd = m.bone(None, 0, 34, -30)
    m.box(hd, "p", 0, -6, 0, 12, 12, 10)
    m.box(hd, "f", 0, -6, -5.4, 10, 9, 1)  # mostly mouth
    m.box(hd, "k", 0, -5, -5.6, 8, 7, 1)
    return m


def options():
    return [the_ear(), hush_bat(), many_eared()]
