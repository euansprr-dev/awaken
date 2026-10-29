"""The Hive Regent's silhouette round (docs/bosses/ROLLOUT.md): the Hive gate's Lord, a Flier. "A hum you feel in your
teeth." She dives at you, sends out her brood, rains honey and stingers, and at the end feeds on royal jelly and
frenzies. Three shapes in black chitin, gold bands, pale wax, glass wings and a glowing amber honey."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

CHITIN = Ramp("#0e0c0a", "#171411", "#221d18", "#2e2720", "#3c332a")
GOLD = Ramp("#5a3a08", "#8a5c10", "#b8801a", "#dca22c", "#f2c450")
WAX = Ramp("#6e5a32", "#927a48", "#b49c62", "#d2bc82", "#eadaa4")
GLASS = Ramp("#5a6670", "#7a8892", "#9eacb4", "#c4d0d6", "#e6eef0")
HONEY = Ramp("#a85a06", "#d8820e", "#f4a820", "#ffc848", "#ffe490")
MATS = {"c": (CHITIN, 2.6), "y": (GOLD, 2.6), "w": (WAX, 2.4), "g": (GLASS, 3.0), "h": (HONEY, 2.8)}


def _wing(m, parent, x, up, z, rot, w, h, mat="g"):
    """A glass wing: a thin pane on a bone, a dark vein down its leading edge."""
    b = m.bone(parent, x, up, z, rot)
    m.box(b, mat, 0, 0, 0, w, h, 1)
    m.box(b, "c", -w / 2 + 0.5 if x > 0 else w / 2 - 0.5, 0, 0, 1, h, 2)
    return b


# ---- A: the Empress --------------------------------------------------------------------------------------------------
def empress():
    m = Mass("hr_a", "A · The Empress",
             "A queen who stands upright in the air like a woman in a gown. Her long banded abdomen hangs down behind her "
             "like a train, ending in the stinger. Four tall glass wings rise from her back. She has four arms: the top "
             "pair hold two amber stinger daggers, the lower pair fold across her chest. Her head is crowned with three "
             "amber spikes and two long feelers, and her big eyes glow honey-gold.", MATS)
    waist = m.bone(None, 0, 40, 2, (12, 0, 0))
    seg, prev = waist, 0
    for k, (w, h) in enumerate(((14, 6), (18, 7), (17, 7), (14, 6), (10, 5), (6, 4))):  # the abdomen, a train behind her
        seg = m.bone(seg, 0, -prev, 0, (10 if k else 0, 0, 0))
        m.box(seg, "y" if k % 2 else "c", 0, -h, 0, w, h, w - 2)
        prev = h
    m.box(seg, "h", 0, -10, 0, 2, 6, 2)  # the stinger
    tor = m.bone(None, 0, 40, 0)
    m.box(tor, "c", 0, 0, 0, 10, 5, 8)  # the waist, narrow
    m.box(tor, "c", 0, 5, 0, 16, 10, 10)  # the thorax
    m.box(tor, "y", 0, 9, -5.4, 12, 3, 1)
    m.box(tor, "w", 0, 15, 0, 18, 3, 12)  # a ruff of pale fur at the neck
    hd = m.bone(tor, 0, 18, -1)
    m.box(hd, "c", 0, 0, 0, 9, 9, 8)
    for s in (1, -1):
        m.box(hd, "h", s * 4.5, 2, -1, 2, 5, 5)  # the big eyes on the sides of the head
        m.box(hd, "c", s * 2, -2, -4, 2, 3, 2)  # mandibles
        an = m.bone(hd, s * 2, 9, -2, (-30, 0, s * 26))
        m.box(an, "c", 0, 0, 0, 1, 10, 1)
        an2 = m.bone(an, 0, 10, 0, (50, 0, s * 20))
        m.box(an2, "c", 0, 0, 0, 1, 8, 1)
        m.box(an2, "h", 0, 8, 0, 2, 2, 2)
    for x, h in ((0, 7), (3, 5), (-3, 5)):  # a crown of three amber spikes
        m.box(hd, "h", x, 9, 0, 2, h, 2)
    # four tall glass wings, the upper pair bigger, leaning out and back
    for s in (1, -1):
        _wing(m, tor, s * 4, 12, 5, (-16, 0, s * 26), 16, 36)
        _wing(m, tor, s * 4, 8, 5, (-10, 0, s * 58), 12, 24)
    # the upper arms hold the stinger daggers out before her, points up; the lower pair fold across her chest
    for s in (1, -1):
        ar = m.bone(tor, s * 9, 13, 0, (-50, 0, s * -24))
        m.box(ar, "c", 0, -10, 0, 3, 10, 3)
        fr = m.bone(ar, 0, -10, 0, (-60, 0, 0))
        m.box(fr, "c", 0, -10, 0, 3, 10, 3)
        m.box(fr, "y", 0, -12, 0, 4, 2, 4)
        dg = m.bone(fr, 0, -11, 0, (130, 0, s * 10))
        m.box(dg, "h", 0, 0, 0, 2, 16, 3)
        m.box(dg, "h", 0, 16, 0, 1, 3, 2)
        lo = m.bone(tor, s * 7, 8, -2, (-70, 0, s * 30))
        m.box(lo, "c", 0, -8, 0, 2, 8, 2)
        lf = m.bone(lo, 0, -8, 0, (0, 0, s * 90))
        m.box(lf, "c", 0, -6, 0, 2, 6, 2)
    return m


# ---- B: the Living Hive ----------------------------------------------------------------------------------------------
def living_hive():
    m = Mass("hr_b", "B · The Living Hive",
             "The hive itself flies. A tall stepped hive of wax and old comb hangs in the air, held up by rows of wings "
             "along its sides, with dark doorways where drones come and go. Comb hangs from its base, dripping glowing "
             "honey. Out of the top rises the queen from the waist up, black and gold, arms up with a stinger dagger in "
             "each hand. A cloud of drones circles it all.", MATS)
    hv = m.bone(None, 0, 18, 0)
    y = 0
    for k, (w, h) in enumerate(((22, 5), (28, 6), (30, 6), (28, 6), (24, 5), (18, 4))):  # the hive, stepped like a skep
        m.box(hv, "w" if k % 2 else "y", 0, y, 0, w, h, w)
        y += h
    for x, yy in ((0, 8), (-7, 14), (8, 20), (-4, 24)):  # doorways
        m.box(hv, "c", x, yy, -15.4 if yy < 26 else -12.4, 4, 3, 1)
    for x, ln in ((-8, 10), (-2, 14), (5, 8), (10, 11)):  # comb hanging from its base, dripping honey
        m.box(hv, "w", x, -ln, 2, 5, ln, 8)
        m.box(hv, "h", x, -ln - 4, 2, 1, 4, 1)
    # rows of wings along its sides
    for s in (1, -1):
        for k, (up, ang) in enumerate(((6, 70), (13, 50), (20, 34))):
            _wing(m, hv, s * 14, up, 4, (-20, 0, s * ang), 8, 18 - k * 2)
    # the queen from the waist up, out of the top
    q = m.bone(hv, 0, y, 0)
    m.box(q, "c", 0, 0, 0, 10, 4, 8)
    m.box(q, "y", 0, 4, 0, 12, 4, 9)
    m.box(q, "c", 0, 8, 0, 14, 7, 9)
    hd = m.bone(q, 0, 15, -1)
    m.box(hd, "c", 0, 0, 0, 8, 8, 7)
    for s in (1, -1):
        m.box(hd, "h", s * 4, 2, -1, 2, 4, 4)
        an = m.bone(hd, s * 2, 8, -2, (-20, 0, s * 30))
        m.box(an, "c", 0, 0, 0, 1, 9, 1)
        ar = m.bone(q, s * 8, 12, 0, (0, 0, s * 150))
        m.box(ar, "c", 0, -10, 0, 3, 10, 3)
        fr = m.bone(ar, 0, -10, 0, (0, 0, s * -20))
        m.box(fr, "c", 0, -9, 0, 3, 9, 3)
        dg = m.bone(fr, 0, -10, 0)
        m.box(dg, "h", 0, -14, 0, 2, 14, 3)
    for x, h in ((0, 6), (3, 4), (-3, 4)):
        m.box(hd, "h", x, 8, 0, 2, h, 2)
    # a cloud of drones
    for x, up, z in ((-30, 30, -6), (28, 40, 4), (-24, 52, 10), (34, 22, -10), (-36, 18, 4), (20, 58, -8), (0, 64, 12), (40, 48, 0)):
        d = m.bone(None, x, up, z)
        m.box(d, "c", 0, 0, 0, 3, 3, 4)
        m.box(d, "y", 0, 1, 1, 3, 1, 2)
        m.box(d, "g", 0, 3, 1, 4, 1, 3)
    return m


# ---- C: the Hornet ---------------------------------------------------------------------------------------------------
def hornet():
    m = Mass("hr_c", "C · The Hornet",
             "A long, low hunter built to dive. A narrow black and gold body as long as a horse, two great pairs of "
             "glass wings swept back, six hooked legs folded under it. Its long abdomen curls under its body so the "
             "stinger points forward between its legs, ready to strike as it dives. Its head is all eyes and long "
             "shearing mandibles.", MATS)
    bd = m.bone(None, 0, 30, 0)
    m.box(bd, "c", 0, -6, -8, 14, 12, 18)  # the thorax
    m.box(bd, "y", 0, 4, -8, 12, 3, 16)
    m.box(bd, "c", 0, -2, 1, 4, 4, 4)  # the waist
    hd = m.bone(bd, 0, 0, -17, (14, 0, 0))
    m.box(hd, "c", 0, -6, -5, 12, 12, 10)
    for s in (1, -1):
        m.box(hd, "h", s * 5, -3, -5, 2, 7, 7)
        mb = m.bone(hd, s * 3, -5, -9, (40, 0, 0))
        m.box(mb, "c", 0, -8, 0, 2, 8, 2)
        m.box(mb, "c", s * -1, -10, -1, 2, 3, 2)
        an = m.bone(hd, s * 2, 4, -8, (-60, 0, s * 20))
        m.box(an, "c", 0, 0, 0, 1, 12, 1)
    # the abdomen: back from the waist, then curling down and forward under the body, the stinger pointing ahead
    seg = m.bone(bd, 0, 0, 3, (-80, 0, 0))
    prev = 0
    for k, (w, ln) in enumerate(((11, 9), (14, 10), (13, 9), (11, 8), (8, 7), (5, 6))):
        seg = m.bone(seg, 0, prev, 0, (0 if k == 0 else -34, 0, 0))
        m.box(seg, "y" if k % 2 else "c", 0, 0, 0, w, ln, w)
        prev = ln
    m.box(seg, "h", 0, 5, 0, 2, 8, 2)
    # two pairs of wings swept back, flat
    for s in (1, -1):
        for k, (ang, w, ln) in enumerate(((34, 14, 50), (58, 10, 36))):
            wb = m.bone(bd, s * 5, 5, -6 + k * 4, (-80, s * ang, 0))
            m.box(wb, "g", 0, 0, 0, w, ln, 1)
            m.box(wb, "c", s * (w / 2 - 0.5), 0, 0, 1, ln, 2)
    # six legs folded under, hooked
    for k, z in enumerate((-12, -7, -2)):
        for s in (1, -1):
            lg = m.bone(bd, s * 5, -4, z, (-20 + k * 20, 0, s * -40))
            m.box(lg, "c", 0, -9, 0, 2, 9, 2)
            lw = m.bone(lg, 0, -9, 0, (0, 0, s * 70))
            m.box(lw, "c", 0, -8, 0, 1, 8, 1)
            m.box(lw, "y", 0, -10, 0, 2, 2, 2)
    return m


def options():
    return [empress(), living_hive(), hornet()]
