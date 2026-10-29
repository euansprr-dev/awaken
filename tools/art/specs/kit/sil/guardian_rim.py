"""The Rootmother's silhouette round (docs/bosses/ROLLOUT.md): the Rim's Guardian, a Crawler. "The roots under the Rim
are hers." Moss webs, Rootfall, her brood and root lances. Three spiders grown out of the Rim, in root bark, moss,
pale stone, silk and a green-gold glow."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

BARK = Ramp("#1e140c", "#2c1d12", "#3c2818", "#4e3520", "#62442a")
MOSS = Ramp("#1a2a10", "#263a16", "#34501e", "#446628", "#587e34")
STONE = Ramp("#4a4a44", "#626058", "#7a786e", "#929084", "#aaa89a")
SILK = Ramp("#8a9a7a", "#a6b496", "#c0ccb0", "#d8e2c8")
GLOW = Ramp("#7aa82a", "#a0d040", "#c4ec6a", "#e4ffa0")
LEAF = Ramp("#203c14", "#2e521c", "#3e6a26", "#528432")
MATS = {"b": (BARK, 2.6), "m": (MOSS, 2.6), "s": (STONE, 2.6), "k": (SILK, 2.4), "g": (GLOW, 3.0), "f": (LEAF, 2.6)}


def _legs(m, parent, hips, knee_out, knee_up, foot_out, spread, w=(3, 2), knot=0):
    """Four legs a side: from each hip up to a high knee and down to the floor, splayed front to back."""
    for s in (1, -1):
        for k, (hx, hy, hz) in enumerate(hips):
            z = (k - 1.5) * spread
            knee = (s * knee_out, knee_up - abs(k - 1.5) * 3, hz + z * 0.7)
            foot = (s * foot_out, 0, hz + z * 1.3)
            m.rod(parent, "b", (s * hx, hy, hz), knee, w=w[0])
            m.rod(parent, "b", knee, foot, w=w[1])
            if knot:
                m.box(parent, "m", knee[0], knee[1] - knot / 2, knee[2], knot, knot, knot)


# ---- A: the Moss Widow -----------------------------------------------------------------------------------------------
def moss_widow():
    m = Mass("rm_a", "A · The Moss Widow",
             "A heavy spider as big as a cart. Her legs are twisted roots, arched high over her back, and her abdomen is a "
             "great boulder of turf and stone with moss hanging off it in sheets. Under it hangs her brood sac, glowing "
             "green-gold through the silk. A beard of moss hangs under her head, and two root fangs curve down from it.", MATS)
    ct = m.bone(None, 0, 14, -10)
    m.box(ct, "b", 0, -4, 0, 16, 10, 14)
    m.box(ct, "m", 0, 5, 0, 14, 2, 12)
    for x in (-4, -1.5, 1.5, 4):
        m.box(ct, "g", x, 2, -7.4, 2, 2, 1)
    for s in (1, -1):
        m.rod(ct, "b", (s * 3, 0, -7), (s * 4, -10, -12), w=2)  # the fangs
    for x, h in ((-4, 7), (0, 9), (4, 6)):
        m.box(ct, "m", x, -4 - h, -5, 3, h, 2)  # the moss beard
    ab = m.bone(None, 0, 22, 12)
    for w, h, d, y in ((24, 8, 24, -12), (30, 10, 30, -4), (26, 8, 28, 6), (16, 4, 18, 14)):
        m.box(ab, "s" if y == 6 else "m", 0, y, 0, w, h, d)
    m.box(ab, "g", 0, -18, 2, 10, 6, 10)  # the brood sac
    m.box(ab, "k", 0, -19, 2, 12, 2, 12)
    for x, z, h in ((-14, 6, 12), (14, -4, 10), (-10, 14, 14), (12, 12, 11)):
        m.box(ab, "m", x, -4 - h, z, 2, h, 4)  # moss hanging in sheets
    _legs(m, None, [(7, 16, -14), (8, 16, -10), (8, 16, -6), (7, 16, -2)], 26, 38, 40, 9)
    return m


# ---- B: the Long-Legs ------------------------------------------------------------------------------------------------
def long_legs():
    m = Mass("rm_b", "B · The Long-Legs",
             "A harvestman taller than a house: a small knotted body of root and moss held high in the air on eight long "
             "thin legs, each one bowed up to a mossy knee far above her and down to the ground far out. Under her body "
             "hangs a cluster of glowing eggs and long streamers of moss and silk that sway to the floor. When she drops, "
             "she drops her whole body down between her legs.", MATS)
    bd = m.bone(None, 0, 54, 0)
    m.box(bd, "b", 0, -5, 0, 14, 10, 16)
    m.box(bd, "m", 0, 5, 1, 12, 3, 12)
    m.box(bd, "b", 0, 8, -3, 4, 4, 4)  # the turret of eyes
    for x in (-1, 1):
        m.box(bd, "g", x, 9, -5.4, 1, 2, 1)
    for x, z, y in ((-3, -2, -10), (2, 2, -11), (0, 5, -9), (4, -3, -9), (-3, 4, -12)):
        m.box(bd, "g", x, y, z, 4, 4, 4)  # the eggs
    for x, z, ln, mat in ((-5, -4, 26, "m"), (5, 3, 32, "k"), (-2, 6, 36, "m"), (3, -6, 22, "k"), (0, 0, 40, "m")):
        m.box(bd, mat, x, -12 - ln, z, 1, ln, 1)  # the streamers
    _legs(m, None, [(6, 54, -6), (7, 54, -2), (7, 54, 2), (6, 54, 6)], 30, 86, 58, 22, w=(2, 2), knot=4)
    return m


# ---- C: the Turf Mantle ----------------------------------------------------------------------------------------------
def turf_mantle():
    m = Mass("rm_c", "C · The Turf Mantle",
             "A piece of the Rim that walks. Her back is a wide flat slab of stone and turf with ferns and three young "
             "trees growing on it, and roots trailing from its edges. Her legs come out from under it like roots and "
             "arch over the edge to the ground; her head is hidden under the front of the slab, two root fangs and a "
             "row of green eyes in the dark.", MATS)
    sl = m.bone(None, 0, 20, 2)
    m.box(sl, "s", 0, 0, 0, 44, 7, 50)
    m.box(sl, "m", 0, 7, 0, 46, 3, 52)
    for x, z, w, d in ((-20, 18, 8, 10), (18, -20, 10, 8), (0, 26, 20, 4)):
        m.box(sl, "s", x, 1, z, w, 5, d)  # a ragged edge
    for x, z, ht in ((-10, -8, 16), (12, 6, 22), (-6, 16, 12)):
        m.box(sl, "b", x, 10, z, 2, ht, 2)  # the young trees
        m.box(sl, "f", x, 10 + ht - 4, z, 10, 8, 10)
    for x, z in ((6, -16), (-16, 2), (16, 18)):
        m.box(sl, "f", x, 10, z, 5, 3, 5)  # ferns
    for x, z, ln in ((-22, -10, 10), (22, 4, 12), (-14, -25, 8), (10, 26, 9)):
        m.box(sl, "b", x, -ln, z, 1, ln, 1)  # roots off the edge
    hd = m.bone(None, 0, 12, -22)
    m.box(hd, "b", 0, -4, 0, 12, 8, 8)
    for x in (-4, -1.5, 1.5, 4):
        m.box(hd, "g", x, 1, -4.4, 2, 2, 1)
    for s in (1, -1):
        m.rod(hd, "b", (s * 3, -2, -4), (s * 3, -11, -9), w=2)
    _legs(m, None, [(10, 18, -16), (12, 18, -6), (12, 18, 6), (10, 18, 16)], 30, 26, 40, 12)
    return m


def options():
    return [moss_widow(), long_legs(), turf_mantle()]
