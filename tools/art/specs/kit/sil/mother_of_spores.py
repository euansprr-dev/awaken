"""Mother of Spores' silhouette round (docs/bosses/ROLLOUT.md): the Fungal gate's mother, a Flier on a 3 x 3 block
hitbox that lashes, swoops, lobs spores, drops on you and rains spores. Three shape ideas in her own values: a dark plum
cap, pale gills, mauve flesh, a bone-pale face, one sea-green glow."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

CAP = Ramp("#1c0f18", "#2a1522", "#3a1c2e", "#4c243a", "#602e46")
GILL = Ramp("#665848", "#86765e", "#a69478", "#c4b294", "#ddd0b2")
FLESH = Ramp("#3a2232", "#4e2e42", "#663c54", "#7e4c66", "#985e7a")
FACE = Ramp("#7c7268", "#9a9086", "#b8aea2", "#d4cabc", "#ebe3d6")
GLOW = Ramp("#0e6a5a", "#18a088", "#3cd2b0", "#86f0d4", "#d4fff0")
MATS = {"k": (CAP, 2.6), "g": (GILL, 2.4), "f": (FLESH, 2.4), "p": (FACE, 2.4), "s": (GLOW, 2.8)}


def _rod(m, parent, mat, a, b, w=1):
    """A bar from point a to point b (x, up, z in the parent's space): a bone at a turned to face b."""
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    ln = math.sqrt(dx * dx + dy * dy + dz * dz)
    L = max(1, round(ln))
    # the bar hangs down its bone (model +y); X then Z turn that onto the direction, in model space (x, -up, z)
    Dx, Dy, Dz = dx / ln, -dy / ln, dz / ln
    rx = math.degrees(math.asin(max(-1.0, min(1.0, Dz))))
    rz = math.degrees(math.atan2(-Dx, Dy))
    bn = m.bone(parent, a[0], a[1], a[2], (rx, 0, rz))
    m.box(bn, mat, 0, -L, 0, w, L, w)
    return bn


# ---- A: the Veiled Lady ----------------------------------------------------------------------------------------------
def veiled_lady():
    m = Mass("ms_a", "A · The Veiled Lady",
             "A bride of the deep woods. A tall thimble of a cap, its tip bent over, pitted with glowing spores. "
             "Under its brim hangs a gaunt pale woman, head bowed, her face seen through the net. From under the cap "
             "falls her veil: a lattice widening like a bell to just above the floor, a cage of pale threads she "
             "drifts inside. Her long thin arms reach out through it, and root tendrils trail from under the hem.", MATS)
    rim = 46  # the cap's brim, above the ground
    cap = m.bone(None, 0, rim, 0)
    tiers = ((20, 4), (19, 5), (17, 5), (15, 5), (12, 4))
    for k, (w, h) in enumerate(tiers):
        m.box(cap, "k", 0, sum(hh for _, hh in tiers[:k]), 0, w, h, w)
    tip = m.bone(cap, 0, 23, 0, (-22, 0, 10))  # the tip, bent over
    m.box(tip, "k", 0, 0, 0, 9, 4, 9)
    m.box(tip, "k", 0, 4, -1, 6, 3, 6)
    m.box(tip, "s", 0, 7, -1.5, 3, 2, 3)
    for x, y, z in ((5, 1, -10.5), (-4, 6, -10), (2, 11, -9), (-7, 1, -10.5), (6, 15, -8), (0, 5, -10), (10.5, 3, 3),
                    (-10, 8, -2), (9.5, 12, -4), (-8, 3, 10.5), (4, 9, 10), (-3, 15, 8)):
        m.box(cap, "s", x, y, z, 2, 2, 1)  # spore pits on the cap's faces
    m.box(cap, "g", 0, -2, 0, 18, 2, 18)  # the gills, under the brim
    # the woman under the cap: head bowed, close under the brim
    m.box(cap, "f", 0, -4, 0, 4, 3, 4)  # her neck, from the cap's heart
    hd = m.bone(cap, 0, -3, -1, (24, 0, 0))
    m.box(hd, "p", 0, -8, 0, 7, 8, 7)
    m.box(hd, "p", 0, -10, -1, 5, 2, 5)  # a narrow chin
    m.box(hd, "s", 0, -4, -3.6, 5, 1, 1)  # eyes
    tor = m.bone(cap, 0, -11, 0, (8, 0, 0))
    m.box(tor, "f", 0, -9, 0, 9, 9, 5)  # a gaunt chest
    m.box(tor, "f", 0, -16, 0, 5, 7, 4)  # a narrow waist
    for k in range(3):
        m.box(tor, "p", 0, -8 + k * 2.5, -2.6, 7 - k, 1, 1)  # ribs showing
    sk = m.bone(tor, 0, -16, 0, (4, 0, 0))
    rags = ((7, 6), (5, 5), (3, 4))
    for k, (w, ln) in enumerate(rags):
        m.box(sk, "g", 0, -sum(l for _, l in rags[:k + 1]), 0, w, ln, max(2, w - 3))  # unravelling into gills
    # her arms, reaching out through the net, one high and one low
    for s, (ar, fr) in ((1, ((-58, 0, 34), (-36, 0, 0))), (-1, ((-28, 0, -22), (-46, 0, 0)))):
        arm = m.bone(tor, s * 5, -1, 0, ar)
        m.box(arm, "p", 0, -14, 0, 2, 14, 2)
        el = m.bone(arm, 0, -14, 0, fr)
        m.box(el, "p", 0, -13, 0, 2, 13, 2)
        m.box(el, "p", 0, -15, 0, 3, 2, 2)  # a hand
        for k in range(4):  # long fingers, spread
            f = m.bone(el, (k - 1.5) * 0.8, -15, 0, (-12 + k * 8, 0, (k - 1.5) * 12))
            m.box(f, "p", 0, -7, 0, 1, 7, 1)
    # the veil: a lattice bell from under the cap (half 8) to the hem (half 26), four faces of threads and rings
    top, hem, h0, h1 = rim - 1, 5, 8, 26
    for face in range(4):
        def at(u, t):  # u along the face (-1..1), t down it (0..1): (x, up, z)
            half = h0 + (h1 - h0) * t
            pts = ((u * half, -half), (half, u * half), (-u * half, half), (-half, -u * half))[face]
            return (pts[0], top - (top - hem) * t, pts[1])
        for i in range(7):
            u = -1 + i / 3
            ln = 1.0 - (0.1 if i % 2 else 0.0) - (0.06 * (i % 3))
            _rod(m, None, "g", at(u, 0), at(u, ln))  # threads, the hem uneven
        for t in (0.22, 0.46, 0.7, 0.9):
            for i in range(6):
                u0, u1 = -1 + i / 3, -1 + (i + 1) / 3
                _rod(m, None, "g", at(u0, t), at(u1, t))  # rings
        for i, t in ((2, 0.46), (4, 0.7), (1, 0.9), (5, 0.22)):
            x, y, z = at(-1 + i / 3, t)
            m.box(None, "s", x, y - 1, z, 2, 2, 2)  # glowing knots where threads cross
    # root tendrils trailing under the hem
    for k, (x, z, ln) in enumerate(((6, -4, 16), (-8, 2, 20), (2, 8, 14), (-3, -9, 18), (10, 6, 12))):
        t = m.bone(None, x, 18, z, ((k % 3 - 1) * 14, 0, (k % 2) * 16 - 8))
        m.box(t, "f", 0, -ln, 0, 2, ln, 2)
    return m


# ---- B: the Shelf Mother ---------------------------------------------------------------------------------------------
def shelf_mother():
    m = Mass("ms_b", "B · The Shelf Mother",
             "A dead tree that floats. A crooked trunk hangs in the air, grown over with shelf fungus in broad flat "
             "tiers, the widest a crown at the top, each one edged with glowing spores. Low on the trunk a long pale "
             "face looks out between two shelves over a glowing hollow. Two root-arms as long as the trunk spread wide "
             "as if to hold you, the fingers hanging like roots, and long tendrils trail beneath her.", MATS)
    # the trunk, crooked, in four leaning segments
    seg = m.bone(None, 0, 22, 0, (8, 0, -6))
    trunk = [seg]
    for k, (w, ln, rot) in enumerate(((10, 14, (0, 0, 0)), (9, 13, (-12, 0, 14)), (8, 12, (10, 0, -10)), (6, 10, (-8, 0, 8)))):
        if k:
            seg = m.bone(seg, 0, trunk_len, 0, rot)
            trunk.append(seg)
        m.box(seg, "k" if k % 2 else "f", 0, 0, 0, w, ln, w)
        trunk_len = ln
    # shelves: flat stepped brackets jutting out, alternating sides, the widest a crown at the top
    for bn, (x, y, z, w, d, side) in ((trunk[0], (7, 6, -2, 16, 14, 1)), (trunk[1], (-8, 3, 1, 20, 16, -1)),
                                      (trunk[1], (8, 10, 2, 14, 12, 1)), (trunk[2], (-6, 7, -1, 18, 14, -1)),
                                      (trunk[3], (0, 9, 0, 40, 32, 0))):
        m.box(bn, "g", x, y - 1, z, w - 2, 1, d - 2)  # the pale underside
        m.box(bn, "k", x, y, z, w, 3, d)
        m.box(bn, "k", x + side * 1, y + 3, z, w - 6, 2, d - 6)  # a second step on top
        m.box(bn, "s", x + side * (w / 2 - 1), y + 1, z, 2, 1, d - 4)  # the glowing outer edge
        if side == 0:
            m.box(bn, "k", 0, y + 5, 0, 24, 3, 20)  # the crown steps up twice more
            m.box(bn, "k", 0, y + 8, 0, 12, 3, 10)
            for s in (1, -1):
                m.box(bn, "s", s * 19.5, y + 1, 0, 1, 1, 28)
                m.box(bn, "s", 0, y + 1, s * 15.5, 36, 1, 1)
    # the face: a long pale mask in the trunk, hooded by the shelves above it
    fc = m.bone(trunk[1], 0, 1, -4.5, (-4, 0, 0))
    m.box(fc, "p", 0, -3, -1, 11, 16, 2)  # a long pale face, wider than the trunk
    m.box(fc, "p", 0, -6, -1.5, 6, 3, 2)  # its chin
    m.box(fc, "s", 0, -12, -1, 5, 6, 2)  # under it, the trunk split open on a glowing hollow
    for s in (1, -1):
        m.box(fc, "s", s * 2.5, 7, -2.4, 2, 3, 1)  # hollow eyes, lit from inside
        m.box(fc, "k", s * 2.5, 10, -2.4, 4, 1, 1)  # brows
    m.box(fc, "k", 0, 0, -2.4, 4, 2, 1)  # mouth
    # two root-arms spread wide like an embrace, then down, the fingers spread like roots
    for s in (1, -1):
        arm = m.bone(trunk[1], s * 4, 6, -2, (-20, 0, s * 84))
        m.box(arm, "f", 0, -18, 0, 3, 18, 3)
        el = m.bone(arm, 0, -18, 0, (-16, 0, s * -40))
        m.box(el, "f", 0, -16, 0, 2, 16, 2)
        for k in range(4):
            f = m.bone(el, 0, -14, 0, (-30 + k * 16, 0, (k - 1.5) * 18))
            m.box(f, "f", 0, -7, 0, 1, 7, 1)
    # a few long tendrils trailing back under the trunk
    for k, (x, z, ln) in enumerate(((2, -1, 22), (-3, 2, 26), (1, 3, 16))):
        t = m.bone(trunk[0], x, 0, z, (24 + k * 6, 0, (k - 1) * 12))
        m.box(t, "f", 0, -ln, 0, 1, ln, 1)
    return m


# ---- C: the Crowned Host --------------------------------------------------------------------------------------------
def crowned_host():
    m = Mass("ms_c", "C · The Crowned Host",
             "The fungus wears her. A pale woman hangs in the air as if held up by strings, back arched, head fallen "
             "to one side, arms limp and open, her legs fused into one long root tail trailing behind. Out of her back and skull a "
             "crown of fungus stalks fans up and out wider than she is, each tipped with a glowing club, some forking. "
             "A ruff of gills rings her neck and shelf fungus grows on one shoulder. When she rises, the crown flares.",
             MATS)
    pel = m.bone(None, 0, 26, 2, (-6, 0, 0))
    m.box(pel, "f", 0, -2, 0, 8, 5, 5)
    seg = m.bone(pel, 0, -2, 0, (8, 0, 4))  # the legs fused into a root tail, trailing back
    for k, (w, ln) in enumerate(((6, 8), (5, 7), (4, 6), (3, 6), (2, 5))):
        if k:
            seg = m.bone(seg, 0, -prev, 0, (11, 0, (k % 2) * 8 - 4))
        m.box(seg, "f" if k < 3 else "k", 0, -ln, 0, w, ln, w)
        prev = ln
    m.box(seg, "s", 0, -7, 0, 2, 2, 2)
    tor = m.bone(pel, 0, 3, 0, (-10, 0, 4))  # the back arched
    m.box(tor, "f", 0, 0, 0, 6, 6, 4)  # waist
    m.box(tor, "f", 0, 6, 0, 10, 8, 6)  # chest
    for k in range(3):
        m.box(tor, "p", 0, 7 + k * 2.2, -3.2, 8 - k * 2, 1, 1)  # ribs showing
    m.box(tor, "k", 2, 3, 3.4, 6, 9, 1)  # dark growth over the back
    for k, (w, h) in enumerate(((14, 2), (11, 2), (8, 2))):  # a ruff of gills round the neck, stepped
        m.box(tor, "g", 0, 13 + k * 1.5, 0, w, h, w - 4)
    sh = m.bone(tor, 6, 12, 0, (0, 0, -24))  # shelf fungus on one shoulder
    m.box(sh, "k", 3, 0, 0, 9, 2, 9)
    m.box(sh, "k", 2, 2, 0, 6, 2, 6)
    m.box(sh, "g", 3, -1, 0, 8, 1, 8)
    hd = m.bone(tor, 0, 15, 0, (34, 0, -20))  # the head fallen forward to one side
    m.box(hd, "p", 0, 0, 0, 7, 8, 7)
    m.box(hd, "p", 0, -1, -2, 5, 2, 4)  # jaw, slack
    m.box(hd, "s", 0, 4, -3.6, 5, 1, 1)
    for s in (1, -1):  # arms limp, hanging, the fingers trailing
        arm = m.bone(tor, s * 5.5, 12, 0, (-8, 0, s * 36))
        m.box(arm, "p", 0, -13, 0, 2, 13, 2)
        el = m.bone(arm, 0, -13, 0, (-12, 0, s * -14))
        m.box(el, "p", 0, -12, 0, 2, 12, 2)
        for k in range(3):
            f = m.bone(el, (k - 1) * 0.7, -12, 0, (8, 0, (k - 1) * 10))
            m.box(f, "p", 0, -6, 0, 1, 6, 1)
    # the crown: stalks fanning out of the back and skull, each with a glowing club, some forking
    stalks = ((hd, 0, 7, 0, 0, 36), (hd, 2, 6, 1, 18, 32), (hd, -2, 6, 1, -18, 32),
              (tor, 4, 12, 3, 38, 38), (tor, -4, 12, 3, -38, 38), (tor, 5, 9, 3, 60, 32), (tor, -5, 9, 3, -60, 32),
              (tor, 3, 6, 3, 80, 26), (tor, -3, 6, 3, -80, 26), (tor, 0, 10, 3, 10, 42), (tor, 1, 4, 3, 100, 18),
              (tor, -1, 4, 3, -100, 16))
    for k, (bn, x, y, z, rz, ln) in enumerate(stalks):
        st = m.bone(bn, x, y, z, ((-34 if bn is hd else 14) - (k % 3) * 6, 0, -rz))  # rising, fanned out to the sides
        lo = ln * 0.55
        m.box(st, "g", 0, 0, 0, 2, round(lo), 2)
        up = m.bone(st, 0, round(lo), 0, (-6, 0, ((k % 2) * 2 - 1) * 8))
        m.box(up, "g", 0, 0, 0, 1, round(ln - lo), 1)
        m.box(up, "s", 0, round(ln - lo), 0, 3, 5, 3)  # the club
        if k in (3, 4, 9, 6):
            fk = m.bone(st, 0, round(lo) - 2, 0, (0, 0, -22 if rz >= 0 else 22))
            m.box(fk, "g", 0, 0, 0, 1, 10, 1)
            m.box(fk, "s", 0, 10, 0, 2, 3, 2)
    return m


def options():
    return [veiled_lady(), shelf_mother(), crowned_host()]
