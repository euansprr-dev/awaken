"""The Root Saint's silhouette round (docs/bosses/ROLLOUT.md): the Temple gate's Lord, a Caster. "Incense and wet roots."
It sends root lances in lines, strikes with its palm, grasps you with roots, rings itself in roots and thorns. Three shapes
in pale temple stone, dark root, moss, a deep red cloth and the amber glow of incense."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

STONE = Ramp("#5c5850", "#7a756a", "#989284", "#b6af9e", "#d2cbb8")
ROOT = Ramp("#1c120c", "#2a1c12", "#3a281a", "#4c3624", "#604630")
MOSS = Ramp("#1e2c12", "#2c4018", "#3c5620", "#4e6e2a", "#648a36")
CLOTH = Ramp("#2a0c0e", "#3e1214", "#561a1c", "#702426", "#8c3030")
EMBER = Ramp("#a8500e", "#d8761a", "#f29c34", "#ffc464", "#ffe8a8")
MATS = {"s": (STONE, 2.6), "r": (ROOT, 2.2), "m": (MOSS, 2.4), "c": (CLOTH, 2.4), "g": (EMBER, 2.8)}


def _roots(m, bone, spread, n, ln, y=0, mat="r"):
    """Roots fanning out and down from a point, each in two bent pieces."""
    for k in range(n):
        ry = k * 360 / n + 13
        b0 = m.bone(bone, 0, y, 0, (0, ry, 0))
        r1 = m.bone(b0, 0, 0, 0, (spread, 0, 0))
        m.box(r1, mat, 0, -ln, 0, 3, ln, 3)
        r2 = m.bone(r1, 0, -ln, 0, (88 - spread, 0, 0))
        m.box(r2, mat, 0, -ln * 0.8, 0, 2, round(ln * 0.8), 2)


def _censer(m, bone, x, y, z, chain):
    """A censer on a chain, glowing."""
    c = m.bone(bone, x, y, z)
    m.box(c, "r", 0, -chain, 0, 1, chain, 1)
    m.box(c, "s", 0, -chain - 5, 0, 4, 5, 4)
    m.box(c, "g", 0, -chain - 3, -2.2, 2, 2, 1)
    m.box(c, "s", 0, -chain - 1, 0, 2, 1, 2)


# ---- A: the Rooted Saint ---------------------------------------------------------------------------------------------
def rooted_saint():
    m = Mass("rs_a", "A · The Rooted Saint",
             "A temple statue of a saint that came alive and grew roots. Tall, robed in stone gone green with moss, "
             "hands pressed together in prayer. Below the robe there are no feet, only roots spreading over the floor, "
             "and it moves by pulling itself along them. Behind its head stands a stone sunburst, cracked and wound "
             "with roots. Censers swing from its belt on chains, trailing incense.", MATS)
    base = m.bone(None, 0, 10, 0)
    _roots(m, base, 58, 7, 12)
    m.box(None, "r", 0, 4, 0, 12, 8, 12)  # the root crown it stands on
    rb = m.bone(None, 0, 10, 0)
    for k, (w, h, d) in enumerate(((22, 8, 18), (20, 10, 16), (18, 10, 14), (16, 8, 12))):  # the robe, narrowing upward
        m.box(rb, "s", 0, sum(hh for _, hh, _ in ((22, 8, 18), (20, 10, 16), (18, 10, 14), (16, 8, 12))[:k]), 0, w, h, d)
    for x in (-8, -3, 3, 8):
        m.box(rb, "s", x, 0, -9.5, 2, 26, 1)  # folds in the robe
    m.box(rb, "m", 4, 2, -9.6, 6, 10, 1)  # moss over it
    m.box(rb, "m", -7, 14, -8.6, 4, 8, 1)
    m.box(rb, "c", 0, 30, -7.5, 10, 6, 1)  # a red stole over the front
    m.box(rb, "c", -3, 6, -9.6, 4, 24, 1)
    m.box(rb, "c", 3, 10, -9.6, 4, 20, 1)
    for x in (-7, 7):
        _censer(m, rb, x, 30, -5, 9 if x < 0 else 13)
    ch = m.bone(rb, 0, 36, 0)
    m.box(ch, "s", 0, 0, 0, 18, 12, 12)  # chest
    m.box(ch, "s", 0, 10, 0, 22, 4, 13)  # shoulders
    m.box(ch, "c", 0, 4, -6.5, 12, 8, 1)
    # hands pressed together in prayer
    for s in (1, -1):
        arm = m.bone(ch, s * 10, 11, 0, (-16, 0, s * 12))
        m.box(arm, "s", 0, -12, 0, 6, 12, 6)
        el = m.bone(arm, 0, -12, 0, (-112, 0, s * -52))
        m.box(el, "s", 0, -10, 0, 5, 10, 5)
        m.box(el, "s", s * -0.5, -16, 0, 4, 6, 3)  # the hands, palm to palm
    # the head, hooded, bowed; the sunburst behind
    hd = m.bone(ch, 0, 14, -1, (16, 0, 0))
    m.box(hd, "s", 0, 0, 0, 9, 10, 9)
    m.box(hd, "c", 0, 0, 0.5, 11, 11, 10)  # the hood
    m.box(hd, "c", 0, 9, 1, 9, 3, 8)
    m.box(hd, "g", 0, 4, -5.2, 5, 1, 1)  # eyes lit like incense
    sb = m.bone(ch, 0, 20, 8)
    m.box(sb, "s", 0, -1, 0, 14, 14, 2)  # the sunburst's disc, a square
    for k in range(8):  # its rays in a ring
        ray = m.bone(sb, 0, 6, 0, (0, 0, k * 45))
        m.box(ray, "s", 0, 6, 0, 3, 10 if k % 2 else 14, 2)
    for k, rz in enumerate((30, -60, 150)):  # roots winding through it
        rr = m.bone(sb, 0, 6, -1.5, (0, 0, rz))
        m.box(rr, "r", 0, 0, 0, 2, 16, 2)
    m.box(sb, "m", 5, 10, -1.5, 5, 3, 1)
    return m


# ---- B: the Reliquary Tree -------------------------------------------------------------------------------------------
def reliquary_tree():
    m = Mass("rs_b", "B · The Reliquary Tree",
             "A shrine that grew into a tree and got up. A massive trunk walks on a tangle of roots like legs. In a "
             "hollow arch in its chest sits the saint's bones, lit by incense burning around them. Its branches spread "
             "above it like the vault of a temple, hung with censers and moss. Two of the branches have grown into "
             "long arms with open palms. Moss hangs from it in strands.", MATS)
    # roots as legs: six thick roots bending down to the floor
    for k, (x, z, ry) in enumerate(((-12, -8, -30), (12, -8, 30), (-16, 4, -80), (16, 4, 80), (-8, 12, -150), (8, 12, 150))):
        r0 = m.bone(None, x, 22, z, (0, ry, 0))
        r1 = m.bone(r0, 0, 0, 0, (-50, 0, 0))
        m.box(r1, "r", 0, -14, 0, 6, 14, 6)
        r2 = m.bone(r1, 0, -14, 0, (62, 0, 0))
        m.box(r2, "r", 0, -14, 0, 5, 14, 5)
        m.box(r2, "r", 0, -16, -1, 3, 3, 3)
    tr = m.bone(None, 0, 20, 2)
    m.box(tr, "r", 0, 0, 0, 34, 10, 28)
    m.box(tr, "r", 0, 10, 0, 30, 30, 24)  # the trunk, tapering
    m.box(tr, "r", 0, 40, 0, 24, 10, 20)
    for x, z in ((-16, -12), (16, -12), (-16, 12), (16, 12)):
        m.box(tr, "r", x, 0, z, 6, 18, 6)  # buttress roots at its foot
        m.box(tr, "r", x * 0.9, 18, z * 0.9, 4, 8, 4)
    # the hollow arch in the chest, the saint's bones inside lit by incense
    m.box(tr, "s", 0, 14, -12.5, 18, 22, 2)  # its stone frame
    m.box(tr, "r", 0, 16, -13.2, 14, 18, 1)
    m.box(tr, "s", 0, 34, -13.6, 12, 3, 1)  # the arch's head, stepped
    m.box(tr, "s", 0, 37, -13.6, 6, 2, 1)
    m.box(tr, "s", 0, 17, -13.8, 8, 3, 2)  # a seated skeleton: knees
    m.box(tr, "s", 0, 20, -13.4, 6, 8, 2)  # ribs
    m.box(tr, "s", 0, 28, -13.6, 5, 5, 2)  # the skull
    m.box(tr, "g", 0, 29, -14.8, 3, 1, 1)
    for x in (-6, 6):
        m.box(tr, "g", x, 16, -14.6, 2, 3, 1)  # candles of incense
        m.box(tr, "g", x * 0.6, 18, -14.6, 1, 2, 1)
    m.box(tr, "m", -10, 30, -13.2, 6, 8, 1)  # moss down the front
    m.box(tr, "m", 9, 6, -13.2, 7, 6, 1)
    # the vault of branches above, hung with censers and moss
    for k, (rz, rx, ln) in enumerate(((-44, -10, 24), (44, -10, 24), (-30, 26, 22), (30, 26, 22), (0, -34, 22), (-66, 24, 20), (66, 24, 20), (0, 10, 26))):
        br = m.bone(tr, 0, 48, 0, (rx, 0, rz))
        m.box(br, "r", 0, 0, 0, 5, ln, 5)
        tw = m.bone(br, 0, ln, 0, (rx * 0.6, 0, rz * 1.1))  # arching over and out, like a vault's ribs
        m.box(tw, "r", 0, 0, 0, 3, 14, 3)
        for j, hl in enumerate((10, 16, 8)):
            m.box(tw, "m", -1 + j, 12 - j * 4 - hl, 0, 2, hl, 2)  # moss hanging in strands
        if k in (0, 1, 5, 6):
            _censer(m, br, 0, ln - 2, 0, 10 + k)
    # two branches grown into long arms with open palms
    for s, (ar, fr) in ((1, ((-10, 0, 34), (-24, 0, -14))), (-1, ((-28, 0, -26), (-30, 0, 10)))):
        arm = m.bone(tr, s * 15, 36, 0, ar)
        m.box(arm, "r", 0, -20, 0, 7, 20, 7)
        el = m.bone(arm, 0, -20, 0, fr)
        m.box(el, "r", 0, -18, 0, 6, 18, 6)
        m.box(el, "s", 0, -26, 0, 10, 8, 3)  # an open palm of stone
        for k in range(4):
            m.box(el, "s", -3.75 + k * 2.5, -32, 0, 2, 6, 2)
    return m


# ---- C: the Kneeling Giant -------------------------------------------------------------------------------------------
def kneeling_giant():
    m = Mass("rs_c", "C · The Kneeling Giant",
             "A giant saint who knelt to pray and never got up, so the temple was built on his back. He kneels on one "
             "knee, head bowed. A stepped shrine roof sits on his shoulders, hung with censers, and roots "
             "pour off him into the floor. His open palms are as wide as a door, and he fights from the kneel: "
             "one raised over his head to slam down, the other pressed flat to the floor, sending roots through the stone.", MATS)
    # one knee down, one foot planted forward
    for s, (th, sh) in ((1, ((-80, 0, -6), (90, 0, 0))), (-1, ((-10, 0, 8), (100, 0, 0)))):
        hip = m.bone(None, s * 12, 28, 6, th)
        m.box(hip, "s", 0, -22, 0, 14, 22, 14)
        kn = m.bone(hip, 0, -22, 0, sh)
        m.box(kn, "s", 0, -22, 0, 12, 22, 12)
        m.box(kn, "c", 0, -6, -6.5, 10, 6, 1)
    m.box(None, "s", -12, 0, 26, 12, 4, 10)  # the kneeling leg's foot behind
    m.box(None, "s", 13, 0, -16, 14, 6, 20)  # the planted foot ahead
    m.box(None, "r", 0, 0, 6, 34, 3, 30)  # roots matted over the floor round him
    for k, (x, z, rz, rx) in enumerate(((-18, -6, 60, 0), (18, 10, -60, 10), (-10, 22, 30, 50), (6, -18, -20, -50), (22, -4, -80, 0))):
        rr = m.bone(None, x, 6, z, (rx, 0, rz))
        m.box(rr, "r", 0, -12, 0, 3, 12, 3)
    tor = m.bone(None, 0, 28, 6, (24, 0, 0))
    m.box(tor, "s", 0, 0, 0, 24, 10, 18)  # a narrower waist
    m.box(tor, "s", 0, 10, 0, 34, 12, 24)
    m.box(tor, "s", 0, 22, 0, 38, 10, 26)  # the chest, broad at the shoulders
    m.box(tor, "c", 0, 0, -11.5, 14, 26, 1)  # a red stole hanging down
    m.box(tor, "m", -12, 14, -14.6, 8, 10, 1)
    m.box(tor, "m", 14, 22, -14.6, 5, 6, 1)
    # the shrine on his back: a stepped roof on pillars, censers hanging from its eaves
    for x, z in ((-14, -4), (14, -4), (-14, 14), (14, 14)):
        m.box(tor, "s", x, 32, z, 4, 12, 4)
    for k, (w, d, h) in enumerate(((46, 34, 4), (38, 28, 4), (28, 20, 4), (16, 12, 4), (6, 6, 5))):
        m.box(tor, "r" if k % 2 else "s", 0, 44 + k * 4, 5, w, h, d)
    for x, z, ln in ((-22, -12, 10), (22, -12, 14), (-22, 22, 12), (22, 22, 8)):
        _censer(m, tor, x, 44, z, ln)
    m.box(tor, "g", 0, 36, 5, 6, 6, 6)  # an incense fire burning in the shrine
    # head bowed between the shoulders
    hd = m.bone(tor, 0, 31, -10, (26, 0, 0))
    m.box(hd, "s", 0, 0, 0, 12, 13, 12)
    m.box(hd, "c", 0, 0, 1, 14, 14, 12)
    m.box(hd, "g", 0, 5, -6.6, 7, 1, 1)
    # great open palms: one raised, one pressed to the floor
    for s, (ar, fr) in ((1, ((-168, 0, 16), (-12, 0, 0))), (-1, ((-30, 0, -26), (-36, 0, 0)))):
        sh = m.bone(tor, s * 21, 28, 0)
        m.box(sh, "s", 0, -6, 0, 16, 14, 18)
        arm = m.bone(sh, 0, -4, 0, ar)
        m.box(arm, "s", 0, -20, 0, 12, 20, 12)
        el = m.bone(arm, 0, -20, 0, fr)
        m.box(el, "s", 0, -18, 0, 11, 18, 11)
        pm = m.bone(el, 0, -18, 0, (20, 0, 0))
        m.box(pm, "s", 0, -12, 0, 22, 12, 6)  # a palm as wide as a door
        for k in range(4):
            m.box(pm, "s", -8.25 + k * 5.5, -20, 0, 4, 8, 5)
        m.box(pm, "s", s * -13, -8, 0, 4, 8, 5)  # thumb
        m.box(pm, "r", 0, -6, 3.5, 14, 3, 1)  # roots grown through the hand
    return m


def options():
    return [rooted_saint(), reliquary_tree(), kneeling_giant()]
