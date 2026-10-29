"""The Bell Warden's silhouette round (docs/bosses/ROLLOUT.md): the Bell gate's Lord, a Construct. "The bell above you is
not a bell." It tolls in waves you dodge by stepping in, strikes with its clapper, rushes, and ends on the Grand Peal.
Three shapes in bronze, green verdigris, dark crypt stone, old rope and a pale gold ghost light."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

BRONZE = Ramp("#3a2410", "#5a3a1a", "#7c5426", "#a07034", "#c4924a")
VERD = Ramp("#1c3a34", "#285248", "#386a5c", "#4c8472", "#64a08a")
CRYPT = Ramp("#18161a", "#242126", "#322e34", "#423d44", "#544e56")
ROPE = Ramp("#3a2c1c", "#544028", "#6e5636", "#8a6e48")
GHOST = Ramp("#a88a3a", "#d0b45a", "#ecd884", "#fff0b4", "#fffae6")
MATS = {"z": (BRONZE, 2.6), "v": (VERD, 2.4), "d": (CRYPT, 2.2), "r": (ROPE, 2.2), "g": (GHOST, 2.8)}

BELL = ((46, 3), (42, 3), (36, 3), (32, 5), (30, 9), (30, 6), (32, 3), (28, 3), (20, 2), (12, 3))  # a waisted bell, lip to crown


def _bell(m, bone, y0, scale=1.0, verd=True):
    """A bell in steps, mouth down, from y0 up. Returns its height."""
    y = y0
    for k, (w, h) in enumerate(BELL):
        w2, h2 = max(2, round(w * scale)), max(1, round(h * scale))
        m.box(bone, "z", 0, y, 0, w2, h2, w2)
        if verd and k in (3, 5):
            m.box(bone, "v", w2 / 4, y + 1, -w2 / 2 - 0.4, max(2, w2 // 3), max(1, h2 - 2), 1)  # verdigris streaks
        y += h2
    return y - y0


# ---- A: the Bell That Walks ------------------------------------------------------------------------------------------
def bell_walks():
    m = Mass("bw_a", "A · The Bell That Walks",
             "It hangs in the belfry like any bell until you come in. Then four long bronze legs unfold from under its "
             "rim, jointed like a crab's, and it lowers itself onto them and walks. The clapper hangs out of its mouth "
             "like a heavy tongue and swings with every step. Inside the mouth, in the dark, there's a face. To toll, "
             "it rears up and slams its rim down on the floor.", MATS)
    bd = m.bone(None, 0, 30, 0)
    h = _bell(m, bd, 0)
    m.box(bd, "d", 0, h, 0, 8, 6, 8)  # the crown's yoke, crypt stone
    m.box(bd, "r", 0, h + 6, 0, 2, 10, 2)  # a cut rope still hanging from it
    m.box(bd, "d", 0, 0.5, 0, 40, 1, 40)  # the dark of its mouth
    m.box(bd, "g", 0, 2, -12, 8, 2, 1)  # a face in the dark: eyes
    m.box(bd, "g", 0, 5, -12, 3, 1, 1)
    # the clapper, a heavy tongue
    cl = m.bone(bd, 0, 10, 0, (14, 0, 0))
    m.box(cl, "d", 0, -16, 0, 3, 16, 3)
    m.box(cl, "z", 0, -24, 0, 8, 8, 8)
    m.box(cl, "z", 0, -26, 0, 6, 2, 6)
    # four long legs unfolding from under the rim, jointed like a crab's: up and out to a high knee, then down
    for x, z in ((1, -1), (-1, -1), (1, 1), (-1, 1)):
        hip, knee, foot = (x * 15, 33, z * 15), (x * 30, 58, z * 30), (x * 40, 0, z * 40)
        m.rod(None, "z", hip, knee, 5)
        m.box(None, "d", knee[0], knee[1] - 3, knee[2], 7, 7, 7)
        m.rod(None, "z", knee, (foot[0], foot[1] + 4, foot[2]), 4)
        m.box(None, "z", foot[0], 0, foot[2], 2, 4, 2)  # a point on the floor
    return m


# ---- B: the Bellringer -----------------------------------------------------------------------------------------------
def bellringer():
    m = Mass("bw_b", "B · The Bellringer",
             "A giant bell-ringer of bronze plates, hunched from a lifetime under the ropes. Its hood is a bell with a "
             "pale gold light where a face should be. It carries the great bell's clapper as a hammer over one "
             "shoulder. Frayed bell ropes hang from its wrists and drag behind it. Tolling bells hang from its belt.",
             MATS)
    for s in (1, -1):
        hip = m.bone(None, s * 9, 36, 2, (-4, 0, s * -4))
        m.box(hip, "d", 0, -18, 0, 11, 18, 11)
        kn = m.bone(hip, 0, -18, 0, (14, 0, 0))
        m.box(kn, "z", 0, -16, 0, 10, 16, 10)
        m.box(kn, "v", 0, -10, -5.4, 6, 6, 1)
        m.box(None, "d", s * 9.5, 0, -2, 12, 4, 16)
    # a long monk's robe of plates
    for k, (w, d, y) in enumerate(((30, 22, 14), (28, 20, 22), (26, 18, 30))):
        m.box(None, "z" if k % 2 else "d", 0, y, 2, w, 8, d)
    tor = m.bone(None, 0, 38, 2, (30, 0, 0))
    m.box(tor, "z", 0, 0, 0, 24, 12, 18)
    m.box(tor, "z", 0, 12, 0, 34, 16, 22)  # a broad hunched back
    m.box(tor, "v", -8, 14, 11.4, 10, 10, 1)
    m.box(tor, "r", 0, 6, -9.5, 26, 3, 1)  # a rope girdle
    for x, ln in ((-9, 6), (9, 9)):  # small bells on the belt
        b = m.bone(tor, x, 4, -10)
        m.box(b, "r", 0, -ln, 0, 1, ln, 1)
        _bell(m, b, -ln - 6, 0.14, verd=False)
    # the hood: a bell, a light for a face
    hd = m.bone(tor, 0, 22, -8, (-24, 0, 0))
    _bell(m, hd, 0, 0.56)
    m.box(hd, "d", 0, 0.5, -2, 20, 10, 14)  # the hood's dark mouth, the face sunk in it
    m.box(hd, "g", 0, 3, -9.5, 9, 4, 1)
    # the clapper over its right shoulder like a hammer; ropes from its wrists
    for s, (ar, fr) in ((1, ((-20, 0, 16), (-20, 0, 0))), (-1, ((-150, 0, -24), (-60, 0, 0)))):
        sh = m.bone(tor, s * 19, 24, 0)
        m.box(sh, "z", 0, -6, 0, 14, 12, 16)
        m.box(sh, "z", s * 1, 6, 0, 12, 3, 14)
        arm = m.bone(sh, 0, -4, 0, ar)
        m.box(arm, "d", 0, -16, 0, 9, 16, 9)
        el = m.bone(arm, 0, -16, 0, fr)
        m.box(el, "z", 0, -14, 0, 10, 14, 10)
        m.box(el, "d", 0, -20, 0, 9, 6, 9)
        rp = m.bone(el, 0, -16, 0, (40, 0, 0))
        for k in range(4):
            m.box(rp, "r", 0, -6 * (k + 1), k * 1.5, 2, 6, 2)  # frayed rope dragging behind
    cl = m.bone(None, -26, 70, 10, (-40, 0, 20))  # the clapper, resting on its shoulder
    m.box(cl, "d", 0, -40, 0, 4, 46, 4)
    m.box(cl, "z", 0, -54, 0, 14, 14, 14)
    m.box(cl, "z", 0, -57, 0, 10, 3, 10)
    m.box(cl, "z", 0, 6, 0, 8, 4, 8)
    return m


# ---- C: the Hollow Bell ----------------------------------------------------------------------------------------------
def hollow_bell():
    m = Mass("bw_c", "C · The Hollow Bell",
             "A knight cast in one piece with a bell. From the waist down it is the bell itself, rim scraping the "
             "floor, and it glides on it without legs. Out of the crown rises a narrow armoured chest with long arms, "
             "and a ghost-lit helm on top. It fights with the bell's clapper, a long bronze mace, and a ring of small "
             "bells floats round its shoulders, ringing.", MATS)
    bd = m.bone(None, 0, 1, 0)
    h = _bell(m, bd, 0, 1.0)
    m.box(bd, "g", 0, 0.5, -21.5, 12, 3, 1)  # light leaking from under the rim
    m.box(bd, "g", 18, 0.5, -10, 5, 2, 1)
    ch = m.bone(bd, 0, h, 0)
    m.box(ch, "z", 0, 0, 0, 14, 10, 12)  # a narrow armoured chest
    m.box(ch, "z", 0, 10, 0, 22, 10, 14)
    m.box(ch, "v", 0, 4, -6.4, 6, 12, 1)
    m.box(ch, "g", 0, 12, -7.4, 3, 4, 1)
    for s in (1, -1):
        pa = m.bone(ch, s * 11, 18, 0, (0, 0, s * -18))
        m.box(pa, "z", 0, 0, 0, 12, 4, 14)
        m.box(pa, "z", s * 2, 4, 0, 8, 3, 10)
    hd = m.bone(ch, 0, 20, 0)
    m.box(hd, "d", 0, 0, 0, 4, 3, 4)
    m.box(hd, "z", 0, 3, 0, 10, 12, 10)  # a tall helm
    m.box(hd, "g", 0, 9, -5.4, 8, 2, 1)  # its ghost-lit slit
    m.box(hd, "z", 0, 15, 0, 6, 4, 6)
    m.box(hd, "z", 0, 19, 0, 2, 5, 2)
    # long arms; the right holds the clapper as a mace
    for s, (ar, fr) in ((1, ((-10, 0, 14), (-30, 0, 0))), (-1, ((-60, 0, -16), (-40, 0, 0)))):
        arm = m.bone(ch, s * 13, 16, 0, ar)
        m.box(arm, "d", 0, -14, 0, 6, 14, 6)
        el = m.bone(arm, 0, -14, 0, fr)
        m.box(el, "z", 0, -14, 0, 7, 14, 7)
        m.box(el, "d", 0, -18, 0, 6, 4, 6)
        if s < 0:
            mc = m.bone(el, 0, -17, 0, (70, 0, 0))
            m.box(mc, "d", 0, -6, 0, 3, 34, 3)  # the clapper's haft
            m.box(mc, "z", 0, 26, 0, 12, 12, 12)  # its head
            m.box(mc, "z", 0, 23, 0, 9, 3, 9)
    # a ring of small bells floating round its shoulders
    for k in range(6):
        import math
        a = math.radians(k * 60 + 15)
        b = m.bone(ch, round(math.cos(a) * 22), 24 + (k % 2) * 4, round(math.sin(a) * 22))
        _bell(m, b, 0, 0.16, verd=False)
        m.box(b, "g", 0, -1, 0, 2, 1, 2)
    return m


def options():
    return [bell_walks(), bellringer(), hollow_bell()]
