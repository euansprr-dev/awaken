"""The Lantern Broker's silhouette round (docs/bosses/ROLLOUT.md): the Lantern Night Market's Lord, a Construct, who
trades with you and duels only if you ask. "A duel, then. Lanterns down." Lantern bolts, a step that puts him
somewhere else, moths let out of their cages, a ring of paper lanterns, and Lights Out. Three shapes in dark lacquer,
red silk, brass, paper, a moth's dusty grey and a warm glowing lantern light."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

LACQUER = Ramp("#0e0a0c", "#181216", "#241a20", "#32242c", "#42303a")
SILK = Ramp("#3a0c0e", "#5a1418", "#7c1e22", "#a02a2c", "#c43c38")
BRASS = Ramp("#3a2c12", "#5a4418", "#7a5e22", "#9a7a32", "#c4a24c")
PAPER = Ramp("#6e6250", "#908268", "#b2a282", "#d0c29e", "#e8dcba")
MOTH = Ramp("#2a2622", "#3c3630", "#504840", "#665c52", "#7e7266")
LIGHT = Ramp("#b8500c", "#e8781a", "#ffa030", "#ffc860", "#fff0b8")
MATS = {"q": (LACQUER, 2.4), "r": (SILK, 2.6), "b": (BRASS, 2.6), "p": (PAPER, 2.6), "m": (MOTH, 2.4), "l": (LIGHT, 2.8)}


def _lantern(m, parent, x, up, z, size=4, rot=(0, 0, 0), paper=False):
    """A lantern hanging from a bone: a brass cap, a lit core in a frame (or a paper shade), a short hook above it."""
    b = m.bone(parent, x, up, z, rot)
    m.box(b, "b", 0, -2, 0, 1, 2, 1)
    m.box(b, "b", 0, -3, 0, size, 1, size)
    if paper:
        m.box(b, "p", 0, -3 - size - 1, 0, size + 1, size + 1, size + 1)
        m.box(b, "l", 0, -3 - size, 0, size - 1, size - 1, size + 2)
    else:
        m.box(b, "l", 0, -3 - size, 0, size - 1, size, size - 1)
        for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            m.box(b, "b", sx * (size / 2 - 0.5), -3 - size, sz * (size / 2 - 0.5), 1, size, 1)
    m.box(b, "b", 0, -4 - size, 0, size, 1, size)
    return b


# ---- A: the Pedlar ---------------------------------------------------------------------------------------------------
def pedlar():
    m = Mass("lb_a", "A · The Pedlar",
             "A tall, thin travelling merchant in a long coat to the floor. On his back is a pack frame taller than he "
             "is, hung with dozens of lit lanterns, bundles, scrolls and little cages of moths: his whole shop. Under "
             "a wide hat hung with tiny lanterns his face is in shadow except for two gold coins where his eyes should "
             "be. He leans on a staff with a lantern on a hook, and a chain blade is coiled at his hip.", MATS)
    coat = m.bone(None, 0, 0, 0)
    for k, (w, d, h) in enumerate(((22, 16, 10), (18, 13, 12), (16, 12, 14))):  # the long coat, narrowing up
        m.box(coat, "q" if k != 1 else "r", 0, sum(x[2] for x in ((22, 16, 10), (18, 13, 12), (16, 12, 14))[:k]), 0, w, h, d)
    tor = m.bone(None, 0, 36, 0, (8, 0, 0))
    m.box(tor, "q", 0, 0, 0, 16, 14, 11)
    m.box(tor, "r", 0, 3, -5.4, 4, 10, 1)  # a red silk sash
    m.box(tor, "q", 0, 14, 0, 18, 3, 12)
    hd = m.bone(tor, 0, 17, -1)
    m.box(hd, "q", 0, 0, 0, 8, 9, 8)
    for s in (1, -1):
        m.box(hd, "b", s * 2, 4, -4.4, 2, 2, 1)  # two gold coins for eyes
    m.box(hd, "q", 0, 8, 0, 30, 2, 30)  # the wide hat
    m.box(hd, "q", 0, 10, 0, 10, 5, 10)
    for sx, sz in ((14, -14), (-14, -14), (14, 14), (-14, 14), (0, -15)):
        _lantern(m, hd, sx, 8, sz, size=2)
    # the pack frame on his back, taller than him, hung with his whole shop
    fr = m.bone(tor, 0, -6, 9)
    for s in (1, -1):
        m.box(fr, "b", s * 12, 0, 0, 2, 64, 2)
    for y in (10, 30, 50, 62):
        m.box(fr, "b", 0, y, 0, 26, 2, 2)
    for k, (x, y, sz) in enumerate(((-10, 10, 4), (-4, 10, 3), (6, 10, 4), (11, 30, 3), (-8, 30, 4), (2, 30, 3), (-12, 50, 3),
                                    (-3, 50, 4), (8, 50, 3), (14, 62, 4), (-14, 62, 4), (0, 62, 3))):
        _lantern(m, fr, x, y, 2, size=sz, paper=k % 3 == 1)
    for x, y, w, h in ((-2, 14, 8, 8), (8, 34, 6, 10), (-6, 40, 5, 5), (4, 54, 6, 4)):  # bundles and scrolls
        m.box(fr, "p" if w < 7 else "r", x, y, 3, w, h, 5)
    for x, y in ((-12, 36), (10, 18)):  # cages of moths
        m.box(fr, "b", x, y, -1, 5, 6, 5)
        m.box(fr, "m", x, y + 1, -1, 3, 4, 3)
    # long arms; the right leans on a staff with a lantern on its hook
    for s, (ar, fe) in ((1, ((-10, 0, 10), (-30, 0, 0))), (-1, ((-30, 0, -14), (-50, 0, 0)))):
        arm = m.bone(tor, s * 9, 13, 0, ar)
        m.box(arm, "q", 0, -14, 0, 4, 14, 4)
        el = m.bone(arm, 0, -14, 0, fe)
        m.box(el, "q", 0, -12, 0, 4, 12, 4)
        m.box(el, "b", 0, -16, 0, 3, 4, 3)
    st = m.bone(None, -16, 0, -8)
    m.box(st, "q", 0, 0, 0, 2, 76, 2)
    m.box(st, "b", -2, 74, 0, 5, 2, 2)
    _lantern(m, st, -4, 74, 0, size=5)
    m.box(tor, "b", 8, -8, -4, 6, 6, 3)  # the chain blade coiled at his hip
    return m


# ---- B: the Lantern Head ---------------------------------------------------------------------------------------------
def lantern_head():
    m = Mass("lb_b", "B · The Lantern Head",
             "An elegant gentleman of the market in a long tailcoat, a waistcoat and spats. His head is a large ornate "
             "lantern with a top hat on it, and the flame inside makes his face. A cape of grey moth wings hangs from "
             "his shoulders. He bows with one hand held out, offering you a small lantern, and in the other he holds a "
             "cane that unfolds into a chain blade.", MATS)
    for s in (1, -1):
        lg = m.bone(None, s * 4, 22, 0)
        m.box(lg, "q", 0, -20, 0, 4, 20, 5)
        m.box(lg, "p", 0, -20, -1, 5, 4, 7)  # spats
        m.box(lg, "q", 0, -22, -1, 5, 2, 8)
    tor = m.bone(None, 0, 22, 0, (10, 0, 0))
    m.box(tor, "r", 0, 0, 0, 12, 12, 8)  # the waistcoat
    m.box(tor, "q", 0, 0, 1, 16, 18, 9)  # the tailcoat
    for s in (1, -1):
        tl = m.bone(tor, s * 4, 2, 4, (-12, 0, s * -6))
        m.box(tl, "q", 0, -20, 0, 6, 20, 2)  # its split tails
    m.box(tor, "q", 0, 18, 0, 20, 3, 10)
    hd = m.bone(tor, 0, 21, -1)  # the lantern head
    m.box(hd, "b", 0, 0, 0, 6, 2, 6)
    m.box(hd, "b", 0, 2, 0, 14, 2, 14)
    m.box(hd, "l", 0, 4, 0, 11, 12, 11)  # the flame's light, a face in it
    for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        m.box(hd, "b", sx * 6, 4, sz * 6, 2, 12, 2)
    m.box(hd, "q", 0, 6, -5.6, 2, 2, 1)
    m.box(hd, "q", -3, 10, -5.6, 2, 2, 1)
    m.box(hd, "q", 3, 10, -5.6, 2, 2, 1)
    m.box(hd, "b", 0, 16, 0, 14, 2, 14)
    m.box(hd, "q", 0, 18, 0, 18, 1, 18)  # a top hat on the lantern
    m.box(hd, "q", 0, 19, 0, 11, 12, 11)
    m.box(hd, "r", 0, 20, 0, 12, 2, 12)
    for s in (1, -1):  # the cape of moth wings
        w = m.bone(tor, s * 6, 18, 5, (-14, 0, s * 18))
        m.box(w, "m", s * 6, -32, 0, 14, 32, 1)
        m.box(w, "p", s * 8, -14, -0.4, 4, 4, 1)  # the eye on a moth's wing
    # bowing a little: one hand held out with a small lantern, the other on the cane
    for s, (ar, fe) in ((1, ((-70, 0, 6), (-10, 0, 0))), (-1, ((-10, 0, -12), (-20, 0, 0)))):
        arm = m.bone(tor, s * 9, 16, 0, ar)
        m.box(arm, "q", 0, -12, 0, 4, 12, 4)
        el = m.bone(arm, 0, -12, 0, fe)
        m.box(el, "q", 0, -10, 0, 4, 10, 4)
        m.box(el, "p", 0, -13, 0, 3, 3, 3)  # white gloves
        if s > 0:
            _lantern(m, el, 0, -14, 0, size=3)
    cn = m.bone(None, -12, 0, -4, (0, 0, -6))
    m.box(cn, "q", 0, 0, 0, 2, 26, 2)
    m.box(cn, "b", 0, 26, 0, 3, 3, 3)
    return m


# ---- C: the Walking Stall --------------------------------------------------------------------------------------------
def walking_stall():
    m = Mass("lb_c", "C · The Walking Stall",
             "The market stall walks. A curtained booth with a peaked red silk awning stands high on four long jointed "
             "legs like a heron's, strings of paper lanterns swinging from its corners. Through the curtains reach the "
             "Broker's long thin arms, four of them, holding lanterns, a weighing scale and a ledger. Between the "
             "curtains there is only a smiling paper mask.", MATS)
    bx = m.bone(None, 0, 40, 0)
    m.box(bx, "q", 0, 0, 0, 30, 4, 24)  # the booth's floor
    m.box(bx, "r", 0, 4, 0, 28, 18, 22)  # its curtains
    m.box(bx, "q", 0, 4, -11.4, 6, 18, 1)  # the gap between them
    m.box(bx, "p", 0, 12, -11.8, 5, 6, 1)  # a smiling paper mask in the gap
    m.box(bx, "q", 0, 22, 0, 32, 2, 26)
    for k, (w, d) in enumerate(((38, 32), (30, 24), (20, 16), (10, 8))):  # the peaked awning, stepped
        m.box(bx, "r" if k % 2 == 0 else "b", 0, 24 + k * 3, 0, w, 3, d)
    m.box(bx, "b", 0, 36, 0, 2, 6, 2)
    # strings of paper lanterns from its corners
    for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        for k in range(3):
            _lantern(m, bx, sx * (19 - k * 2), 24 - k * 6, sz * (16 - k), size=3, paper=True)
    # four long jointed legs, knees up like a heron's
    for sx, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        hip, knee, foot = (sx * 12, 40, sz * 9), (sx * 30, 52, sz * 24), (sx * 24, 0, sz * 20)
        m.rod(None, "q", hip, knee, 3)
        m.box(None, "b", knee[0], knee[1] - 2, knee[2], 4, 4, 4)
        m.rod(None, "q", knee, (foot[0], foot[1] + 2, foot[2]), 2)
        m.box(None, "b", foot[0], 0, foot[2], 5, 2, 7)
    # four long thin arms through the curtains
    for k, (x, up, rot, held) in enumerate(((8, 16, (-60, 0, 20), "lantern"), (-8, 16, (-50, 0, -30), "scale"),
                                            (10, 8, (-80, 0, 50), "ledger"), (-10, 8, (-80, 0, -60), "lantern"))):
        arm = m.bone(bx, x, up, -11, rot)
        m.box(arm, "q", 0, -16, 0, 2, 16, 2)
        el = m.bone(arm, 0, -16, 0, (-30, 0, 0))
        m.box(el, "q", 0, -14, 0, 2, 14, 2)
        m.box(el, "p", 0, -16, 0, 2, 2, 2)
        if held == "lantern":
            _lantern(m, el, 0, -16, 0, size=4)
        elif held == "scale":
            m.box(el, "b", 0, -18, 0, 14, 1, 1)
            for s in (1, -1):
                m.box(el, "b", s * 7, -24, 0, 1, 6, 1)
                m.box(el, "b", s * 7, -25, 0, 5, 1, 5)
        else:
            m.box(el, "r", 0, -24, 0, 8, 10, 3)
    return m


def options():
    return [pedlar(), lantern_head(), walking_stall()]
