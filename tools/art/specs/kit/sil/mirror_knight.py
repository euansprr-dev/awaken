"""The Mirror Knight's silhouette round (docs/bosses/ROLLOUT.md): the Mirror palace's Lord, a Duelist. "Footsteps that
match yours." It wears your face, parries perfectly, lunges, splits into reflections, steps through the glass, and at
the end the palace shatters. Three shapes in mirror silver, dark steel, gilt, black glass, royal blue cloth and a cold
white light."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

MIRROR = Ramp("#5c6670", "#8a96a2", "#b6c2cc", "#dce4ea", "#f6fafc")
STEEL = Ramp("#22262c", "#323840", "#454c56", "#5a626e", "#727b88")
GILT = Ramp("#5a4210", "#86641c", "#b08a2c", "#d4ae48", "#f0d27a")
BLACK = Ramp("#0a0c10", "#12151a", "#1a1e25", "#242a33", "#303742")
CLOTH = Ramp("#141c3a", "#1e2a54", "#2a3a70", "#3a4e8c", "#5066a8")
LIGHT = Ramp("#9ad8f0", "#bfe8f8", "#dcf4ff", "#f0faff", "#ffffff")
MATS = {"m": (MIRROR, 3.0), "s": (STEEL, 2.6), "g": (GILT, 2.6), "k": (BLACK, 2.4), "c": (CLOTH, 2.6), "l": (LIGHT, 3.0)}


def _legs(m, mat="m", hip=32, spread=5, front=(0, 0)):
    """Two armoured legs from the hip to the floor; front: how far forward each foot stands (left, right)."""
    for s, fz in ((1, front[0]), (-1, front[1])):
        m.rod(None, mat, (s * spread, hip, 0), (s * spread, hip - 16, fz * 0.5), w=7)
        m.box(None, mat, s * spread, hip - 19, fz * 0.5 - 3.6, 5, 4, 1)  # the knee cop
        m.rod(None, mat, (s * spread, hip - 16, fz * 0.5), (s * spread, 2, fz), w=6)
        m.box(None, "s", s * spread, 0, fz - 2, 7, 3, 11)  # the sabaton


# ---- A: the Silvered Knight ------------------------------------------------------------------------------------------
def silvered():
    m = Mass("mk_a", "A · The Silvered Knight",
             "A tall knight in full plate polished to a mirror, so the whole room moves across it. His great helm has "
             "no slit: its face is one flat mirror, and in it you see yourself. A crown of mirror shards rises from "
             "the helm. A cape of cracked mirror panels hangs from his shoulders to his heels, and he carries a tall "
             "kite shield that is a mirror too, for the parry, and a long straight sword held point down before him.",
             MATS)
    _legs(m, front=(-2, 4))
    tor = m.bone(None, 0, 32, 0)
    m.box(tor, "m", 0, 0, 0, 20, 6, 13)  # the faulds
    for s in (1, -1):
        m.box(tor, "m", s * 6, -9, -5.5, 8, 10, 2)  # tassets over the thighs
    m.box(tor, "s", 0, 6, 0, 16, 4, 11)
    m.box(tor, "m", 0, 10, 0, 22, 16, 14)  # the breastplate
    m.box(tor, "l", 0, 13, -7.4, 12, 10, 1)  # its polished ridge
    m.box(tor, "g", 0, 25, 0, 23, 1, 15)
    m.box(tor, "s", 0, 26, 0, 12, 3, 10)  # the gorget
    for s in (1, -1):
        p = m.bone(tor, s * 12, 25, 0, (0, 0, s * -12))
        m.box(p, "m", 0, -6, 0, 10, 8, 14)
        m.box(p, "m", s, -10, 0, 9, 4, 12)
        m.box(p, "g", 0, 2, 0, 9, 2, 12)
    hd = m.bone(tor, 0, 29, -1)
    m.box(hd, "m", 0, 0, 0, 12, 13, 12)  # the great helm
    m.box(hd, "l", 0, 2, -6.2, 10, 9, 1)  # its face, one flat mirror
    m.box(hd, "g", 0, 11, -6.4, 12, 1, 1)
    for k, (x, h) in enumerate(((-5, 5), (-3, 8), (-1, 11), (1, 9), (3, 7), (5, 4))):  # the crown of shards
        m.box(hd, "l" if k % 2 else "m", x, 13, -4 + (k % 3), 2, h, 2)
    for z, h in ((0, 6), (3, 5), (5, 3)):
        m.box(hd, "m", 0, 13, z, 2, h, 2)
    # the cape of cracked mirror panels, black glass between them
    cp = m.bone(tor, 0, 26, 7.5, (10, 0, 0))
    for k, x in enumerate((-10, -5, 0, 5, 10)):
        ln = (46, 50, 52, 50, 46)[k]
        m.box(cp, "l" if k % 2 else "m", x, -ln, 0, 4, ln, 1)
        m.box(cp, "k", x + 2.5, -ln + 3, 0.4, 1, ln - 3, 1)
    # the shield on the left arm, held before him
    m.rod(None, "m", (13, 55, 0), (15, 42, -6), w=6)
    m.rod(None, "m", (15, 42, -6), (13, 36, -13), w=5)
    sh = m.bone(None, 15, 32, -16, (0, -14, 0))
    for y, w, h in ((14, 16, 16), (6, 13, 8), (0, 9, 6), (-4, 5, 4), (-7, 2, 3)):
        m.box(sh, "l", 0, y, 0, w, h, 2)
    m.box(sh, "g", 0, 30, 0, 17, 2, 3)
    m.box(sh, "g", 0, -6, -1.2, 1, 36, 1)
    m.box(sh, "g", 0, 20, -1.2, 14, 1, 1)
    # the sword arm low before him, the sword point down to the floor
    m.rod(None, "m", (-13, 55, 0), (-15, 42, -5), w=6)
    m.rod(None, "m", (-15, 42, -5), (-10, 34, -14), w=5)
    m.box(None, "s", -9, 31, -15, 6, 5, 6)  # the gauntlet
    m.box(None, "g", -9, 29, -16, 10, 2, 3)  # the cross
    m.rod(None, "l", (-9, 29, -16), (-8, 1, -21), w=2)  # the blade
    m.rod(None, "s", (-9, 36, -15), (-9, 40, -14), w=2)
    m.box(None, "g", -9, 40, -14, 3, 3, 3)
    return m


# ---- B: the Reflection -----------------------------------------------------------------------------------------------
def reflection():
    m = Mass("mk_b", "B · The Reflection",
             "Your reflection, stepping out of the mirror. A tall gilded mirror frame stands behind it like a doorway, "
             "and its back foot is still in the glass. Its body is a knight's shape in dark silvered glass with a cold "
             "light along its edges, cracked where it came through, shards falling off its shoulders. It has no face, "
             "only a smooth oval of mirror. It holds its sword in its left hand, the way a reflection would.", MATS)
    fr = m.bone(None, 0, 0, 12)  # the frame, a tall arch of gilt
    for s in (1, -1):
        m.box(fr, "g", s * 18, 0, 0, 4, 76, 5)
        m.box(fr, "g", s * 18, 0, 0, 7, 4, 7)  # its feet
        m.box(fr, "g", s * 14, 76, 0, 8, 4, 5)
        m.box(fr, "g", s * 8, 80, 0, 8, 3, 5)
    m.box(fr, "g", 0, 82, 0, 10, 4, 5)
    m.box(fr, "g", 0, 86, 0, 4, 6, 4)  # its crest
    m.box(fr, "k", 0, 4, 0.6, 32, 72, 1)  # the glass, dark
    for x, y, h in ((-9, 20, 26), (6, 34, 30), (-2, 50, 22), (11, 12, 18)):  # cracks running out from where it came through
        m.box(fr, "l", x, y, 0, 1, h, 1)
    # the knight, stepping forward out of it, the back foot still in the glass
    m.rod(None, "k", (5, 32, 2), (5, 17, -4), w=7)
    m.rod(None, "k", (5, 17, -4), (5, 2, -8), w=6)
    m.box(None, "m", 5, 0, -10, 7, 3, 10)
    m.rod(None, "k", (-5, 32, 2), (-5, 17, 8), w=7)
    m.rod(None, "k", (-5, 17, 8), (-5, 3, 13), w=6)
    tor = m.bone(None, 0, 32, 2, (6, 0, 0))
    m.box(tor, "k", 0, 0, 0, 18, 6, 11)
    m.box(tor, "k", 0, 6, 0, 15, 4, 10)
    m.box(tor, "k", 0, 10, 0, 21, 15, 13)
    m.box(tor, "l", 0, 10, -6.6, 1, 15, 1)  # light along its edges
    for s in (1, -1):
        m.box(tor, "l", s * 10.6, 10, 0, 1, 15, 1)
        p = m.bone(tor, s * 11, 24, 0, (0, 0, s * -10))
        m.box(p, "k", 0, -5, 0, 9, 7, 12)
        m.box(p, "m", 0, 1, 0, 8, 1, 11)
        for k in range(3):  # shards falling off its shoulder
            m.box(p, "m", s * (3 + k * 3), 4 + k * 4, -2 + k, 2, 3, 2)
    hd = m.bone(tor, 0, 27, -1)
    m.box(hd, "k", 0, 0, 0, 10, 12, 10)
    m.box(hd, "l", 0, 2, -5.2, 8, 9, 1)  # the oval of mirror where a face would be
    m.box(hd, "k", 0, 12, 1, 6, 3, 8)
    # the sword in its LEFT hand, raised across it; the right hand open toward you
    m.rod(None, "k", (12, 56, 2), (16, 46, -4), w=6)
    m.rod(None, "k", (16, 46, -4), (12, 52, -14), w=5)
    m.box(None, "m", 12, 50, -16, 5, 5, 5)
    m.box(None, "g", 12, 55, -16, 3, 2, 9)  # the cross
    m.rod(None, "l", (12, 57, -16), (2, 92, -18), w=2)  # the blade, raised past its head
    m.rod(None, "k", (-12, 56, 2), (-16, 46, -6), w=6)
    m.rod(None, "k", (-16, 46, -6), (-16, 40, -18), w=5)
    for k in range(4):
        m.box(None, "m", -18 + k * 1.4, 37, -21, 1, 5, 1)  # its fingers, open
    return m


# ---- C: the Masked Duelist -------------------------------------------------------------------------------------------
def duelist():
    m = Mass("mk_c", "C · The Masked Duelist",
             "A tall, slender fencer of the palace. A long coat of royal blue opens below the waist into a skirt of "
             "mirror panels that swing as he moves. A wide-brimmed black hat with a long white plume, a high collar, "
             "and under the brim an oval mask of mirror with nothing behind it. He stands side-on in guard, a long "
             "thin rapier reaching out at you and a parrying dagger raised behind, ready to turn your blow aside.", MATS)
    # side-on: the front (right) foot forward, the back foot turned out
    m.rod(None, "k", (-3, 34, -2), (-4, 18, -12), w=5)
    m.rod(None, "k", (-4, 18, -12), (-4, 2, -16), w=5)
    m.box(None, "k", -4, 0, -18, 5, 3, 9)
    m.box(None, "k", -4, 12, -14, 7, 6, 7)  # a boot cuff
    m.rod(None, "k", (3, 34, 2), (5, 18, 10), w=5)
    m.rod(None, "k", (5, 18, 10), (6, 2, 14), w=5)
    m.box(None, "k", 7, 0, 14, 6, 3, 8)
    m.box(None, "k", 6, 12, 12, 7, 6, 7)
    tor = m.bone(None, 0, 34, 0)
    for k, (x, z, h) in enumerate(((-9, -3, 18), (-5, -6, 20), (0, -7, 21), (5, -6, 20), (9, -3, 18), (8, 4, 17), (0, 6, 19), (-8, 4, 17))):
        m.box(tor, "l" if k % 2 else "m", x, -h + 4, z, 5, h, 2)  # the skirt of mirror panels
    m.box(tor, "c", 0, 0, 0, 16, 6, 11)
    m.box(tor, "g", 0, 5, 0, 15, 2, 10)  # the belt
    m.box(tor, "c", 0, 7, 0, 15, 14, 10)  # the coat
    m.box(tor, "g", 0, 9, -5.4, 1, 12, 1)
    m.box(tor, "c", 0, 21, 0, 19, 4, 11)
    m.box(tor, "c", 0, 25, 1, 11, 6, 9)  # the high collar
    cp = m.bone(tor, 7, 24, 2, (8, 0, 14))  # a short cape over the left shoulder
    m.box(cp, "c", 0, -22, 0, 10, 24, 12)
    m.box(cp, "g", 0, -23, 0, 11, 2, 13)
    hd = m.bone(tor, 0, 28, -1)
    m.box(hd, "k", 0, 0, 0, 8, 10, 8)
    m.box(hd, "l", 0, 0, -4.4, 7, 9, 1)  # the oval mask of mirror
    m.box(hd, "k", 0, 9, 0, 26, 2, 24)  # the wide brim
    m.box(hd, "k", 0, 11, 0, 10, 6, 10)  # the crown of the hat
    m.box(hd, "g", 0, 11, 0, 11, 2, 11)
    pl = m.bone(hd, 4, 15, 2, (20, 0, -30))  # the plume, sweeping back
    for k in range(5):
        m.box(pl, "l", k * 2, k * 2.4, k * 3, 3, 3, 4)
    # the rapier arm out at you, the blade reaching on
    m.rod(None, "c", (-9, 57, -2), (-10, 50, -14), w=5)
    m.rod(None, "c", (-10, 50, -14), (-9, 48, -26), w=4)
    m.box(None, "g", -9, 45, -29, 6, 6, 4)  # the swept hilt
    m.rod(None, "l", (-9, 48, -31), (-8, 44, -76), w=1)  # the long thin blade
    # the dagger arm raised behind
    m.rod(None, "c", (9, 57, 2), (14, 62, 10), w=5)
    m.rod(None, "c", (14, 62, 10), (13, 74, 6), w=4)
    m.box(None, "g", 13, 74, 6, 4, 2, 4)
    m.rod(None, "l", (13, 76, 6), (12, 88, 2), w=2)
    return m


def options():
    return [silvered(), reflection(), duelist()]
