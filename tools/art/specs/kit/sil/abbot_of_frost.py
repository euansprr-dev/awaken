"""The Abbot of Frost's silhouette round (docs/bosses/ROLLOUT.md): the Frost Monastery's Lord, a Caster. "Chanting
above a sea of clouds." Frost hymns, ice spikes from the sky, a choir of wisps, a blizzard, and at the end the Glacial
Vow. Three shapes in white wool, grey-blue frost, clear ice, old silver, dark slate and a pale glowing blue."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

WOOL = Ramp("#7a8088", "#9aa2aa", "#b8c0c8", "#d4dae0", "#eef2f4")
FROST = Ramp("#3c4c5c", "#526678", "#6a8294", "#86a0b2", "#a8c0d0")
ICE = Ramp("#4a7a96", "#6a9cb8", "#8cbed6", "#b4dcee", "#dcf4ff")
SILVER = Ramp("#3a3e44", "#585e66", "#7a8088", "#9ca2aa", "#c0c6cc")
SLATE = Ramp("#14181c", "#1e2428", "#283036", "#343e46", "#424e58")
GLOW = Ramp("#5ab4e8", "#86d0f4", "#b2e6fc", "#dcf6ff", "#f6fdff")
MATS = {"w": (WOOL, 2.6), "f": (FROST, 2.6), "i": (ICE, 2.8), "s": (SILVER, 2.6), "d": (SLATE, 2.4), "g": (GLOW, 3.0)}


def _icicles(m, bone, y, w, d, n, ln=5, mat="i"):
    """A row of icicles hanging from an edge at height y, along the front and the sides."""
    for k in range(n):
        x = -w / 2 + 1 + k * (w - 2) / max(1, n - 1)
        m.box(bone, mat, round(x), y - ln + (k % 3), -d / 2 + 0.5, 1, ln - (k % 3), 1)


# ---- A: the Frozen Saint ---------------------------------------------------------------------------------------------
def frozen_saint():
    m = Mass("af_a", "A · The Frozen Saint",
             "A very tall, thin old abbot whose robes have frozen stiff, layer on layer, the hems hung with icicles. He "
             "wears a tall mitre of clear ice. A huge string of prayer beads made of ice is wound round his neck and "
             "hangs past his knees. His hands are clasped at his chest round the chain of a silver censer that smokes "
             "with cold. His face is a frozen mask with a long beard of icicles.", MATS)
    rb = m.bone(None, 0, 0, 0)
    y = 0
    for k, (w, d, h) in enumerate(((24, 18, 10), (20, 15, 12), (17, 13, 12), (15, 11, 10))):  # the robe, frozen in layers
        m.box(rb, "w" if k % 2 == 0 else "f", 0, y, 0, w, h, d)
        _icicles(m, rb, y + 1, w, d, max(3, w // 4), ln=4)
        y += h
    tor = m.bone(None, 0, y, 0)
    m.box(tor, "w", 0, 0, 0, 16, 12, 11)
    m.box(tor, "f", 0, 12, 0, 20, 4, 13)  # a stiff frozen cape over the shoulders
    _icicles(m, tor, 12, 20, 13, 6, ln=5)
    hd = m.bone(tor, 0, 16, -1)
    m.box(hd, "s", 0, 0, 0, 8, 9, 8)  # the frozen mask
    m.box(hd, "g", 0, 5, -4.4, 5, 1, 1)
    for k, (x, ln) in enumerate(((-2, 12), (0, 16), (2, 12), (-3, 8), (3, 8))):  # a beard of icicles
        m.box(hd, "i", x, -ln + 2, -4, 1, ln, 1)
    for k, (w, h) in enumerate(((10, 6), (8, 6), (6, 6), (4, 5), (2, 4))):  # the tall mitre of ice
        m.box(hd, "i", 0, 9 + sum(x[1] for x in ((10, 6), (8, 6), (6, 6), (4, 5), (2, 4))[:k]), 0, w, h, w - 2)
    # the prayer beads: a loop of ice from his neck to past his knees
    for k in range(14):
        t = k / 13
        x = (1 - abs(2 * t - 1)) * 0 + (6 if k < 7 else -6) * (1 - abs(2 * t - 1)) * 1.2
        up = y + 14 - 42 * (1 - abs(2 * t - 1))
        m.box(None, "i", round(x), round(up), -7, 3, 3, 3)
    # hands clasped at the chest round a censer's chain
    for s in (1, -1):
        arm = m.bone(tor, s * 9, 11, 0, (-60, 0, s * -30))
        m.box(arm, "w", 0, -12, 0, 5, 12, 5)
        el = m.bone(arm, 0, -12, 0, (-60, 0, 0))
        m.box(el, "w", 0, -10, 0, 5, 10, 5)
        m.box(el, "f", 0, -12, 0, 3, 3, 3)
    cs = m.bone(tor, 0, 6, -10)
    m.box(cs, "s", 0, -20, 0, 1, 20, 1)
    m.box(cs, "s", 0, -26, 0, 6, 6, 6)
    m.box(cs, "g", 0, -25, 0, 4, 4, 7)
    m.box(cs, "g", 0, -34, 0, 3, 6, 3)  # its cold smoke falling
    return m


# ---- B: the Pilgrim --------------------------------------------------------------------------------------------------
def pilgrim():
    m = Mass("af_b", "B · The Pilgrim",
             "A huge hunched monk carrying a block of glacier ice on his back, bigger than he is, roped and chained to "
             "him. Frozen deep inside it something glows: the monastery's heart. Snow lies thick on his hood and "
             "shoulders, his breath smokes, and he walks with a long silver crook topped with a shard of ice. His robe "
             "drags in the snow.", MATS)
    rb = m.bone(None, 0, 0, 0)
    m.box(rb, "w", 0, 0, 0, 30, 8, 22)  # the robe, dragging
    m.box(rb, "w", 0, 8, 0, 26, 12, 19)
    tor = m.bone(None, 0, 20, 0, (40, 0, 0))  # hunched far forward under the weight
    m.box(tor, "w", 0, 0, 0, 28, 14, 20)
    m.box(tor, "f", 0, 14, 0, 30, 8, 21)
    m.box(tor, "w", 0, 22, 1, 26, 4, 19)  # snow on his shoulders
    hd = m.bone(tor, 0, 18, -12, (-30, 0, 0))
    m.box(hd, "f", 0, 0, 0, 14, 14, 14)  # the deep hood
    m.box(hd, "w", 0, 14, 1, 14, 3, 13)  # snow on it
    m.box(hd, "d", 0, 2, -7.4, 8, 8, 1)  # the dark inside the hood
    m.box(hd, "g", 0, 6, -7.8, 6, 1, 1)
    m.box(hd, "g", 0, 2, -12, 6, 4, 6)  # his breath smoking
    # the glacier on his back, the heart frozen inside it
    gl = m.bone(tor, 0, 4, 12, (-46, 0, 0))  # stood upright on his bent back, towering over his head
    m.box(gl, "i", 0, 0, 0, 30, 30, 22)
    m.box(gl, "g", 0, 10, 0, 10, 10, 10)  # the heart
    for k, (x, y, z, w, h, d, rx, rz) in enumerate(((-9, 26, -2, 14, 22, 14, 6, 12), (8, 24, 2, 16, 28, 14, -8, -10),
                                                    (0, 40, 0, 10, 18, 10, 4, 4), (14, 4, -4, 10, 16, 12, 0, -28),
                                                    (-15, 6, 3, 10, 14, 10, 10, 30))):  # jagged shards of the glacier
        sh = m.bone(gl, x, y, z, (rx, 0, rz))
        m.box(sh, "i", 0, 0, 0, w, h, d)
    for y in (8, 22):
        m.box(gl, "d", 0, y, 0, 32, 2, 24)  # the ropes and chains
    for s, (ar, fe) in ((1, ((-50, 0, 24), (-20, 0, 0))), (-1, ((-70, 0, -20), (-30, 0, 0)))):
        arm = m.bone(tor, s * 15, 16, 0, ar)
        m.box(arm, "w", 0, -18, 0, 9, 18, 9)
        el = m.bone(arm, 0, -18, 0, fe)
        m.box(el, "w", 0, -16, 0, 9, 16, 9)
        m.box(el, "f", 0, -21, 0, 7, 5, 7)
    ck = m.bone(None, -26, 0, -24)  # the silver crook
    m.box(ck, "s", 0, 0, 0, 2, 76, 2)
    m.box(ck, "s", 4, 74, 0, 8, 2, 2)
    m.box(ck, "s", 8, 66, 0, 2, 8, 2)
    m.box(ck, "i", 0, 76, 0, 3, 10, 3)
    return m


# ---- C: the Snow Owl -------------------------------------------------------------------------------------------------
def snow_owl():
    m = Mass("af_c", "C · The Snow Owl",
             "An abbot wrapped in a great cloak of frost-white feathers that falls from very wide shoulders to the "
             "floor in rows, like an owl's folded wings. His hood is shaped like an owl's head, with two tufts like "
             "ears and two big pale glowing eyes. Only his long thin fingers come out of the cloak, holding a string of "
             "ice beads, and his feet are talons. When he casts, the cloak opens like wings into a wall of frost.", MATS)
    for s in (1, -1):  # talons
        m.box(None, "d", s * 5, 0, -2, 6, 3, 8)
        for k in (-2, 0, 2):
            m.box(None, "d", s * 5 + k, 0, -7, 1, 2, 3)
    cl = m.bone(None, 0, 3, 0)
    y = 0
    for k, (w, d, h) in enumerate(((22, 16, 8), (26, 18, 8), (30, 20, 8), (34, 22, 8), (38, 22, 8), (40, 22, 6))):  # rows of feathers
        m.box(cl, "w" if k % 2 == 0 else "f", 0, y, 0, w, h, d)
        for x in range(-w // 2 + 2, w // 2 - 1, 4):  # feather tips along the row's edge
            m.box(cl, "w" if k % 2 else "f", x, y - 2, -d / 2 + 0.5, 3, 3, 1)
        y += h
    hd = m.bone(cl, 0, y, -2)  # the owl hood
    m.box(hd, "w", 0, 0, 0, 22, 18, 18)
    for s in (1, -1):
        m.box(hd, "g", s * 5, 7, -9.4, 6, 6, 1)  # the big pale eyes
        m.box(hd, "d", s * 5, 9, -9.8, 2, 2, 1)
        m.box(hd, "f", s * 8, 18, 0, 5, 6, 6)  # the ear tufts
        m.box(hd, "f", s * 9, 24, 1, 3, 4, 4)
    m.box(hd, "d", 0, 3, -9.6, 3, 4, 1)  # the beak
    m.box(hd, "f", 0, 13, -9.4, 14, 2, 1)  # a heavy brow
    # long thin fingers out of the cloak, holding a string of ice beads
    for s in (1, -1):
        fg = m.bone(cl, s * 4, 28, -11, (-20, 0, s * -20))
        for k in range(3):
            m.box(fg, "d", (k - 1) * 1.5, -8, 0, 1, 8, 1)
    for k in range(9):
        m.box(None, "i", round(-6 + k * 1.5), round(24 - abs(k - 4) * 2.5), -13, 2, 2, 2)
    return m


def options():
    return [frozen_saint(), pilgrim(), snow_owl()]
