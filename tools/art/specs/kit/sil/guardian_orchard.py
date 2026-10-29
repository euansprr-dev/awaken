"""The Ossuary King's silhouette round (docs/bosses/ROLLOUT.md): the Bone Orchard's Guardian, a Brute. "The orchard grew
from his ribs." Bone Crush, Rib Spikes, the dead of his court rising, the Royal March; he gets up once. Three dead
kings, in old bone, black bark, tarnished gold, rotten velvet and a red glow."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

BONE = Ramp("#5e5444", "#7a6e5a", "#968a72", "#b2a68c", "#cac0a6")
BARK = Ramp("#120c0a", "#1c1410", "#281c16", "#36261c", "#463224")
GOLD = Ramp("#4a3412", "#6a4c1c", "#8c6a2a", "#ae8a3c", "#ccac56")
VELVET = Ramp("#200a10", "#300f18", "#421622", "#561e2e", "#6c283a")
GLOW = Ramp("#a01818", "#d83030", "#ff5a4a", "#ff9a80")
LEAF = Ramp("#2a1a10", "#3a2414", "#4e3018", "#643e1e")
MATS = {"o": (BONE, 2.6), "b": (BARK, 2.6), "g": (GOLD, 2.6), "v": (VELVET, 2.6), "r": (GLOW, 3.0), "l": (LEAF, 2.6)}


def _ribs(m, bone, y0, n, w, d, gap=3, mat="o"):
    for k in range(n):
        ww = w - abs(k - n / 2) * 1.2
        m.box(bone, mat, 0, y0 + k * gap, 0.5, round(ww), 1, d)
    m.box(bone, mat, 0, y0, d / 2 - 1, 2, n * gap, 2)  # the spine behind


def _crown(m, bone, y, w):
    m.box(bone, "g", 0, y, 0, w, 2, w)
    for x, z in ((-w / 2 + 1, -w / 2 + 1), (w / 2 - 1, -w / 2 + 1), (0, -w / 2 + 0.5), (-w / 2 + 1, w / 2 - 1), (w / 2 - 1, w / 2 - 1)):
        m.box(bone, "g", x, y + 2, z, 1, 3, 1)


# ---- A: the Orchard King ---------------------------------------------------------------------------------------------
def orchard_king():
    m = Mass("ok_a", "A · The Orchard King",
             "A giant king's skeleton with an apple tree growing out of his open ribs: its black trunk climbs up through "
             "his chest and spreads its bare branches over his shoulders like a second crown, hung with glowing red "
             "apples. Its roots run down out of his pelvis and round his leg bones into the ground. He wears a gold crown "
             "on his skull and a rotten velvet cloak, and drags a great sword of bone.", MATS)
    for s in (1, -1):
        m.rod(None, "o", (s * 5, 36, 0), (s * 6, 18, -1), w=3)  # the thigh bones
        m.rod(None, "o", (s * 6, 18, -1), (s * 6, 2, 0), w=3)
        m.box(None, "o", s * 6, 0, -2, 4, 2, 7)
        m.rod(None, "b", (s * 3, 36, 1), (s * 9, 1, 3), w=1)  # its roots, down the legs
    m.box(None, "o", 0, 34, 0, 14, 5, 7)  # the pelvis
    m.box(None, "o", 0, 39, 1, 2, 8, 2)
    tb = m.bone(None, 0, 46, 0)
    _ribs(m, tb, 0, 7, 18, 10)
    m.box(tb, "o", 0, 21, 0.5, 24, 3, 9)  # the shoulders
    m.box(tb, "v", 0, -8, 6, 26, 30, 2)  # the cloak behind
    m.box(tb, "o", 0, 24, 0, 3, 3, 3)
    hd = m.bone(tb, 0, 27, -1)
    m.box(hd, "o", 0, 0, 0, 10, 10, 10)
    m.box(hd, "o", 0, -3, -3, 8, 3, 5)  # the jaw
    for x in (-2.5, 2.5):
        m.box(hd, "r", x, 4, -5.4, 2, 2, 1)
    _crown(m, hd, 10, 11)
    m.rod(tb, "b", (0, -6, 1), (1, 34, 0), w=3)  # the trunk, up through him
    for a, b in (((1, 26, 0), (-16, 40, 2)), ((1, 28, 0), (17, 42, -1)), ((0, 32, 0), (-6, 48, 4)), ((1, 32, 0), (8, 50, -3)),
                 ((-10, 36, 1), (-20, 44, -2)), ((10, 37, 0), (20, 34, 3))):
        m.rod(tb, "b", a, b, w=2)
    for x, y, z in ((-16, 38, 2), (17, 40, -1), (-6, 46, 4), (8, 48, -3), (-20, 42, -2), (20, 32, 3), (-12, 34, 0), (12, 44, 1)):
        m.box(tb, "r", x, y - 3, z, 3, 3, 3)  # the apples
    for s in (1, -1):
        m.rod(tb, "o", (s * 12, 20, 0), (s * 15, 2, -2), w=3)  # the arms
        m.rod(tb, "o", (s * 15, 2, -2), (s * 16, -16, -4), w=2)
    m.rod(None, "o", (-16, 30, -6), (-20, 0, -20), w=4)  # the bone sword, dragged
    m.box(None, "g", -16, 29, -6, 8, 2, 2)
    return m


# ---- B: the Ossuary Colossus -----------------------------------------------------------------------------------------
def ossuary_colossus():
    m = Mass("ok_b", "B · The Ossuary Colossus",
             "A hulk built out of a whole ossuary: his body is a wall of skulls stacked in rows the way they are in a "
             "charnel house, his arms are bundles of long bones bound with black roots, and his fists are knots of "
             "skulls. On top of it all sits one small crowned skull, his own, with red light in its eyes. He walks bent "
             "forward under his own weight.", MATS)
    for s in (1, -1):
        m.box(None, "o", s * 7, 0, 0, 8, 22, 8)
        m.box(None, "b", s * 7, 8, -4.5, 9, 2, 1)
    bd = m.bone(None, 0, 22, 0, (12, 0, 0))
    for row in range(5):  # the wall of skulls
        w = 26 + (row % 2) * 2
        y = row * 6
        m.box(bd, "o", 0, y, 0, w, 6, 16)
        for k in range(5):
            m.box(bd, "b", -w / 2 + 3 + k * (w - 6) / 4, y + 2, -8.4, 2, 2, 1)
    m.box(bd, "o", 0, 30, 0, 34, 6, 18)  # the shoulders
    hd = m.bone(bd, 0, 36, -4)
    m.box(hd, "o", 0, 0, 0, 8, 8, 8)
    for x in (-2, 2):
        m.box(hd, "r", x, 3, -4.4, 2, 2, 1)
    _crown(m, hd, 8, 9)
    for s in (1, -1):
        m.rod(bd, "o", (s * 17, 32, 0), (s * 22, 12, -2), w=6)
        m.rod(bd, "o", (s * 22, 12, -2), (s * 22, -8, -6), w=5)
        m.box(bd, "o", s * 22, -18, -6, 10, 10, 10)  # the fists of skulls
    return m


# ---- C: the Knuckle King ---------------------------------------------------------------------------------------------
def knuckle_king():
    m = Mass("ok_c", "C · The Knuckle King",
             "A king's skeleton grown huge in the chest and arms and short in the legs, who goes on his knuckles like an "
             "ape. His ribcage is a great barrel, open at the front, with a red light burning inside it where his heart "
             "was; his crowned skull hangs low and forward in front of it. A young apple tree grows out of his back "
             "between his shoulders, and his cloak hangs in rags from its branches.", MATS)
    for s in (1, -1):
        m.rod(None, "o", (s * 6, 20, 8), (s * 7, 10, 6), w=3)
        m.rod(None, "o", (s * 7, 10, 6), (s * 7, 1, 8), w=3)
        m.box(None, "o", s * 7, 0, 6, 4, 2, 6)
    tb = m.bone(None, 0, 20, 4, (-34, 0, 0))
    _ribs(m, tb, 0, 8, 26, 16)
    m.box(tb, "r", 0, 8, 0, 6, 6, 6)  # the heart's light
    m.box(tb, "o", 0, 25, 0, 32, 4, 12)
    hd = m.bone(None, 0, 30, -18)
    m.box(hd, "o", 0, 0, 0, 10, 10, 10)
    m.box(hd, "o", 0, -3, -3, 8, 3, 5)
    for x in (-2.5, 2.5):
        m.box(hd, "r", x, 4, -5.4, 2, 2, 1)
    _crown(m, hd, 10, 11)
    for s in (1, -1):
        m.rod(None, "o", (s * 16, 42, -6), (s * 22, 22, -14), w=4)  # the long arms, down to his knuckles
        m.rod(None, "o", (s * 22, 22, -14), (s * 20, 3, -18), w=3)
        m.box(None, "o", s * 20, 0, -18, 6, 4, 6)
    m.rod(None, "b", (0, 40, 8), (0, 60, 14), w=2)  # the tree out of his back
    for b in ((-10, 66, 18), (9, 68, 12), (0, 72, 20)):
        m.rod(None, "b", (0, 58, 14), b, w=1)
        m.box(None, "r", b[0], b[1] - 3, b[2], 3, 3, 3)
    m.box(None, "v", 0, 30, 18, 24, 26, 2)  # the rags of the cloak
    return m


def options():
    return [orchard_king(), ossuary_colossus(), knuckle_king()]
