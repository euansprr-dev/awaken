"""The Thornback's silhouette round (docs/bosses/ROLLOUT.md): the Hanging Gardens' Guardian, a Beast. "Something heavy
moves in the hanging gardens." It charges along the ledges, blows petals hard enough to knock you off, sprays thorns
and calls the lurkers out of the vines. Three beasts of the gardens, in bark, hedge green, thorn, stone and petals."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

BARK = Ramp("#22160e", "#322216", "#44301e", "#584028", "#6c5234")
HEDGE = Ramp("#14280e", "#1e3814", "#2a4c1c", "#386224", "#4a7a30")
THORN = Ramp("#2a2014", "#3e301c", "#564428", "#706036", "#8c7c4a")
STONE = Ramp("#5a5650", "#726e66", "#8a867c", "#a29e92", "#bab6a8")
PETAL = Ramp("#a03c5a", "#c85a78", "#e47c96", "#f4a2b6", "#ffcad6")
CREAM = Ramp("#a89a7a", "#c4b690", "#dcd0aa", "#f0e8c8")
MATS = {"b": (BARK, 2.6), "h": (HEDGE, 2.6), "t": (THORN, 2.6), "s": (STONE, 2.6), "p": (PETAL, 2.8), "c": (CREAM, 2.6)}


def _quad_legs(m, xs, zs, top, w, d, mat="b", foot=None):
    for x in xs:
        for z in zs:
            m.box(None, mat, x, 0, z, w, top, d)
            if foot:
                m.box(None, foot, x, 0, z - 1, w + 1, 2, d + 2)


# ---- A: the Hedge Boar -----------------------------------------------------------------------------------------------
def hedge_boar():
    m = Mass("tb_a", "A · The Hedge Boar",
             "A boar as big as a wagon, its back a hedge of thorny bramble and climbing roses that bristles up into a crest "
             "along its spine. Its tusks are two great curved thorns, its head low and heavy for the charge. When it shakes "
             "itself, the roses on its back burst into a gale of petals.", MATS)
    _quad_legs(m, (-8, 8), (-12, 12), 16, 6, 7, foot="t")
    bd = m.bone(None, 0, 14, 0)
    m.box(bd, "b", 0, 0, 0, 22, 18, 38)
    m.box(bd, "h", 0, 14, 2, 24, 8, 36)
    for k in range(7):  # the crest of bramble and thorn along its spine
        z = -15 + k * 5
        m.box(bd, "h", 0, 22, z, 10, 6 + (k % 3) * 2, 5)
        m.rod(bd, "t", (0, 26 + (k % 3) * 2, z), (0, 34 + (k % 2) * 3, z + 3), w=2)
    for x, z in ((-8, -10), (7, -2), (-6, 8), (9, 12), (0, 16), (-9, 0)):
        m.box(bd, "p", x, 20, z, 4, 3, 4)  # roses
    hd = m.bone(None, 0, 16, -22)
    m.box(hd, "b", 0, -6, -4, 16, 14, 14)
    m.box(hd, "b", 0, -8, -13, 10, 8, 6)  # the snout
    m.box(hd, "h", 0, 6, -2, 14, 4, 12)
    for s in (1, -1):
        m.rod(hd, "c", (s * 5, -6, -14), (s * 9, 4, -20), w=2)  # the tusk thorns
        m.rod(hd, "c", (s * 9, 4, -20), (s * 8, 10, -17), w=2)
    return m


# ---- B: the Thorn Pangolin -------------------------------------------------------------------------------------------
def thorn_pangolin():
    m = Mass("tb_b", "B · The Thorn Pangolin",
             "A great pangolin whose scales are broad thorned leaves, overlapping down its arched back to a long heavy "
             "tail. It walks on its knuckles, its small head low on a long snout; to charge it rolls itself up into a ball "
             "of thorns and comes down the ledges at you. Between the scales grow pink blossoms, and they blow off it in "
             "a storm when it rears and shakes its tail.", MATS)
    _quad_legs(m, (-8, 8), (-10,), 10, 5, 6, foot="t")
    _quad_legs(m, (-8, 8), (10,), 12, 6, 7, foot="t")
    bd = m.bone(None, 0, 10, 0)
    for k, (h, y, w) in enumerate(((10, 2, 16), (14, 4, 20), (18, 4, 22), (18, 2, 22), (14, 0, 20), (10, -2, 16))):
        z = -15 + k * 6
        m.box(bd, "b", 0, y, z, w - 4, h - 2, 7)
        m.box(bd, "h", 0, y + h - 6, z + 1, w, 6, 8)  # the leaf scales, overlapping
        m.rod(bd, "t", (0, y + h, z), (0, y + h + 5, z + 3), w=1)
        for s in (1, -1):
            m.rod(bd, "t", (s * w / 2, y + h - 4, z), (s * (w / 2 + 4), y + h - 2, z + 3), w=1)
        if k % 2:
            m.box(bd, "p", 3 * (1 if k % 4 == 1 else -1), y + h, z, 3, 2, 3)
    for k in range(6):  # the tail, scaled to its tip
        z = 22 + k * 5
        y = -2 - k * 1.2
        m.box(bd, "h", 0, y, z, 10 - k, 7 - k // 2, 6)
        m.rod(bd, "t", (0, y + 7 - k // 2, z), (0, y + 10 - k // 2, z + 2), w=1)
    hd = m.bone(None, 0, 10, -22)
    m.box(hd, "b", 0, -3, 0, 8, 7, 8)
    m.box(hd, "b", 0, -4, -8, 4, 4, 9)  # the long snout
    m.box(hd, "h", 0, 3, 1, 9, 3, 7)
    return m


# ---- C: the Topiary Lion ---------------------------------------------------------------------------------------------
def topiary_lion():
    m = Mass("tb_c", "C · The Topiary Lion",
             "A lion clipped from hedge for the gardens long ago, that grew wild and got up. Its body and legs are still "
             "clipped square, its tail ends in a clipped ball, but its mane has grown out into a great tangle of thorny "
             "climbing roses round its face. Its eyes are two white flowers, and its claws are thorns. When it roars, its "
             "mane blows apart into petals.", MATS)
    _quad_legs(m, (-8, 8), (-12, 12), 18, 7, 7, mat="h", foot="t")
    bd = m.bone(None, 0, 16, 0)
    m.box(bd, "h", 0, 0, 2, 22, 16, 34)
    m.box(bd, "h", 0, 2, -12, 24, 18, 12)  # the chest, clipped deeper
    m.rod(bd, "h", (0, 12, 18), (0, 26, 30), w=3)  # the tail
    m.box(bd, "h", 0, 24, 31, 8, 8, 8)
    hd = m.bone(None, 0, 28, -20)
    for w, h, d, y in ((34, 34, 12, -14), (28, 28, 14, -11)):
        m.box(hd, "h", 0, y, 2, w, h, d)  # the mane, grown out
    for x, y in ((-14, 8), (12, 12), (-10, -12), (14, -8), (0, 16), (-16, -2), (16, 2), (2, -16)):
        m.box(hd, "p", x, y, -5, 5, 5, 3)  # the roses in it
    m.box(hd, "h", 0, -8, -6, 16, 16, 10)  # the face, clipped
    m.box(hd, "h", 0, -9, -13, 8, 7, 4)  # its muzzle
    for x in (-4, 4):
        m.box(hd, "c", x, 1, -11.4, 3, 3, 1)  # the white flower eyes
    return m


def options():
    return [hedge_boar(), thorn_pangolin(), topiary_lion()]
