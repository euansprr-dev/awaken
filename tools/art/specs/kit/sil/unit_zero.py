"""Unit Zero's silhouette round (docs/bosses/ROLLOUT.md): the Precursor gate's last floor, a Duelist on a 1 x 3 block
hitbox that fights fast (a beam, a blink, palm-edge cuts, a spin, a burst). Three shape ideas in its own values: pale
ceramic, dark joints, a slate plate, one cold blue."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

CERAMIC = Ramp("#5d6474", "#7a8292", "#9aa2b1", "#bcc3cf", "#dde2ea")
JOINT = Ramp("#0b0d12", "#141720", "#1e222c", "#2a2f3b")
SLATE = Ramp("#262b36", "#343a48", "#454c5c", "#586072")
BLUE = Ramp("#1c4fd0", "#3a7cff", "#6aa8ff", "#a8d4ff", "#e4f4ff")
MATS = {"c": (CERAMIC, 2.6), "j": (JOINT, 2.0), "s": (SLATE, 2.2), "g": (BLUE, 2.8)}


# ---- A: the Sentinel -------------------------------------------------------------------------------------------------
def sentinel():
    m = Mass("uz_a", "A · The Sentinel",
             "A cathedral statue that moves. Tall, slender and upright, pale ceramic over black joints, weight on one "
             "leg. A small faceless helm with one blue line for a visor and a long crest swept back. Flat pauldrons "
             "step up into spires. A narrow tabard in front and split coat-tails behind, to the ankle. Its hands are "
             "fused into long flat blades, one lowered, one raised in salute. A great square halo stands on its point "
             "behind it, a diamond framing the head and both shoulders.", MATS)
    # legs: long and slim, the left straight under it, the right a little forward with the knee eased
    for s, (th, sh) in ((1, ((0, 0, -2), (2, 0, 0))), (-1, ((-14, 0, 4), (16, 0, 0)))):
        hip = m.bone(None, s * 3.5, 30, 0, th)
        m.box(hip, "j", 0, -15, 0, 3, 15, 3)  # thigh
        m.box(hip, "c", s * 0.5, -12, -1, 4, 10, 3)  # thigh plate
        kn = m.bone(hip, 0, -15, 0, sh)
        m.box(kn, "c", 0, -8, 0, 4, 8, 4)  # greave, tapering
        m.box(kn, "c", 0, -13, 0, 3, 5, 3)
        m.box(kn, "c", 0, -2, -2.5, 4, 4, 2)  # knee cop
        m.box(kn, "g", 0, -9, -2.2, 1, 6, 1)  # a seam down the shin
        ft = m.bone(kn, 0, -13, 0, (-th[0] - sh[0], 0, 0))
        m.box(ft, "c", 0, -2, -2, 3, 2, 8)  # a long pointed sabaton
        m.box(ft, "c", 0, -2, -7, 2, 1, 3)
    m.box(None, "j", 0, 27, 0, 7, 4, 5)  # pelvis
    m.box(None, "c", 0, 30, 0, 9, 2, 6)  # belt
    m.box(None, "s", 0, 12, -3.2, 4, 18, 1)  # the tabard, front
    for s in (1, -1):
        ct = m.bone(None, s * 2.2, 30, 3.2, (6, 0, s * 4))
        m.box(ct, "s", 0, -24, 0, 4, 24, 1)  # coat-tails, split, to the ankle
        m.box(ct, "s", 0, -27, 0, 2, 3, 1)
        t = m.bone(None, s * 4.5, 30, 0, (0, 0, s * 10))
        m.box(t, "c", 0, -7, 0, 2, 7, 6)  # tassets over the hips
    m.box(None, "j", 0, 32, 0, 4, 5, 4)  # a narrow waist
    ch = m.bone(None, 0, 37, 0)
    m.box(ch, "c", 0, 0, 0, 8, 3, 6)  # the chest widens upward in steps, a V
    m.box(ch, "c", 0, 3, 0, 12, 4, 7)
    m.box(ch, "c", 0, 7, 0, 15, 4, 7)
    m.box(ch, "g", 0, 1, -3.9, 1, 9, 1)  # the core: a slit of blue down the sternum
    m.box(ch, "g", 0, 8, -3.9, 5, 1, 1)
    for s in (1, -1):
        pa = m.bone(ch, s * 7.5, 10, 0, (0, 0, s * -22))
        m.box(pa, "c", 0, 0, 0, 9, 2, 8)  # flat pauldrons, stepping up
        m.box(pa, "c", s * 1.5, 2, 0, 6, 2, 6)
        m.box(pa, "c", s * 3, 4, 0, 2, 7, 3)  # into a spire
        m.box(pa, "c", s * 3, 11, 0, 1, 3, 1)
    # arms: long and slim, the hands fused into blades; the left lowered, the right raised in salute
    for s, (ar, fr) in ((1, ((-4, 0, 6), (-12, 0, 0))), (-1, ((-24, 0, -8), (-112, 0, 18)))):
        arm = m.bone(ch, s * 8, 9, 0, ar)
        m.box(arm, "j", 0, -11, 0, 3, 11, 3)
        el = m.bone(arm, 0, -11, 0, fr)
        m.box(el, "c", 0, -10, 0, 3, 10, 3)  # bracer
        m.box(el, "c", 0, -7, 0, 4, 4, 4)
        m.box(el, "g", 0, -9, -1.8, 1, 6, 1)
        m.box(el, "c", 0, -14, 0, 2, 4, 3)  # the hand fused into a blade, tapering in steps
        m.box(el, "c", 0, -20, -0.5, 1, 6, 2)
        m.box(el, "c", 0, -24, -0.5, 1, 4, 1)
        m.box(el, "g", 0, -23, -1.8, 1, 9, 1)  # its edge
    # the helm: small, faceless, a crest swept back
    m.box(ch, "j", 0, 11, 0, 2, 3, 2)  # neck
    hd = m.bone(ch, 0, 13.5, -0.5)
    m.box(hd, "c", 0, 0, 0, 5, 7, 6)
    m.box(hd, "c", 0, 1, -3.4, 4, 4, 1)  # faceplate
    m.box(hd, "g", 0, 4, -3.4, 5, 1, 1)  # one blue line for a visor
    cr = m.bone(hd, 0, 6, 0, (-30, 0, 0))
    m.box(cr, "c", 0, 0, 1, 1, 3, 7)  # the crest, swept back
    m.box(cr, "c", 0, 2, 4, 1, 3, 6)
    m.box(cr, "c", 0, 4, 8, 1, 2, 4)
    # the halo: a great square stood on its point behind the head and shoulders, on a stalk from the spine
    m.box(ch, "j", 0, 4, 4, 2, 10, 2)  # the stalk
    halo = m.bone(ch, 0, 14, 6, (0, 0, 45))
    for x, y, w, h in ((0, 11, 23, 1), (0, -12, 23, 1), (11, -11, 1, 22), (-11, -11, 1, 22)):
        m.box(halo, "c", x, y, 0, w, h, 1)
    for x, y, w, h in ((0, 9, 19, 1), (0, -10, 19, 1), (9, -9, 1, 18), (-9, -9, 1, 18)):
        m.box(halo, "g", x, y, 0, w, h, 1)  # an inner frame of light
    for x, y in ((11, 11), (-11, 11), (11, -12), (-11, -12)):
        m.box(halo, "s", x, y - 1, 0, 3, 3, 2)  # nodes on the diamond's points
    return m


# ---- B: the Unbound --------------------------------------------------------------------------------------------------
def unbound():
    m = Mass("uz_b", "B · The Unbound",
             "A figure drawn in pieces. A black wire frame holds nothing together: its ceramic plates float a finger's "
             "width off the body with cold light between them. It hovers a hand above the floor, toes pointed. Two "
             "great shoulder plates drift up and out like folded wings. The head is a hollow square with one point of "
             "light inside, and the halo is four loose bars orbiting it. Plates gather ahead of each hand into a blade "
             "of floating shards.", MATS)
    lift = 3  # it hovers
    for s in (1, -1):
        hip = m.bone(None, s * 3.5, 26 + lift, 0, (-6, 0, s * -2))
        m.box(hip, "j", 0, -13, 0, 2, 13, 2)  # the wire thigh
        m.box(hip, "c", 0, -11, -3, 4, 8, 2)  # its plate, floating in front
        m.box(hip, "c", s * 2.5, -9, 0, 1, 6, 3)
        kn = m.bone(hip, 0, -13, 0, (14, 0, 0))
        m.box(kn, "g", 0, -1, 0, 3, 2, 3)  # the knee joint of light
        m.box(kn, "j", 0, -11, 0, 2, 11, 2)
        m.box(kn, "c", 0, -10, -3, 4, 9, 2)  # shin plate, floating
        ft = m.bone(kn, 0, -11, 0, (40, 0, 0))
        m.box(ft, "c", 0, -7, -1, 3, 7, 2)  # the foot hangs, toes pointed
    m.box(None, "j", 0, 24 + lift, 0, 7, 2, 2)  # the pelvis bar
    m.box(None, "c", 0, 21 + lift, -3, 8, 5, 2)  # a floating hip plate
    m.box(None, "j", 0, 26 + lift, 0, 2, 12, 2)  # the spine wire
    for k in range(3):
        m.box(None, "j", 0, 30 + lift + k * 3, 0, 8 - k, 1, 4)  # ribs
    ch = m.bone(None, 0, 34 + lift, 0)
    m.box(ch, "g", 0, 0, 0, 4, 5, 3)  # the core, seen through the gaps
    for s in (1, -1):  # the chest in floating shards: two plates in a V with the core's light between them
        pc = m.bone(ch, s * 3.5, 2, -4, (0, 0, s * 10))
        m.box(pc, "c", 0, 0, 0, 6, 8, 2)
        m.box(pc, "c", s * 1, 8, 0.5, 4, 2, 1)
    for k in range(3):  # and three strips across the belly, gaps between
        m.box(None, "c", 0, 28 + lift + k * 2.5, -3, 7 - k, 2, 1)
    m.box(ch, "c", 0, 0, 4, 10, 10, 2)  # the back plate, floating behind
    m.box(ch, "j", 0, 8, 0, 12, 1, 2)  # the shoulder bar
    for s in (1, -1):
        w1 = m.bone(ch, s * 8, 13, 1, (0, 0, s * -38))
        m.box(w1, "c", 0, 0, 0, 12, 3, 9)  # great shoulder plates, drifting up and out like folded wings
        w2 = m.bone(w1, s * 3, 5, 0, (0, 0, s * -12))
        m.box(w2, "c", 0, 0, 0, 9, 2, 7)
        m.box(w2, "g", 0, -1.5, 0, 7, 1, 1)
    # arms: thin wire, plates floating off the forearm, a blade of shards ahead of each hand
    for s, (ar, fr) in ((1, ((-30, 0, 16), (-40, 0, 0))), (-1, ((-10, 0, -10), (-20, 0, 0)))):
        arm = m.bone(ch, s * 7, 8, 0, ar)
        m.box(arm, "j", 0, -9, 0, 2, 9, 2)
        el = m.bone(arm, 0, -9, 0, fr)
        m.box(el, "j", 0, -9, 0, 2, 9, 2)
        m.box(el, "c", s * 2.5, -8, 0, 2, 7, 4)  # forearm plate, floating outside
        m.box(el, "g", 0, -11, 0, 2, 2, 2)  # the hand: a node of light
        for k, (h, d) in enumerate(((4, 3), (4, 2), (3, 1))):
            m.box(el, "c", 0, -14 - k * 5 - h, 0, 1, h, d)  # shards in a line, a blade with gaps
    # head: a hollow square frame with a point of light, the halo as four loose bars
    m.box(ch, "j", 0, 9, 0, 2, 3, 2)
    hd = m.bone(ch, 0, 12, 0)
    for x, y, w, h in ((0, 7, 7, 1), (0, 0, 7, 1), (3, 1, 1, 6), (-3, 1, 1, 6)):
        m.box(hd, "c", x, y, -3, w, h, 1)  # the front square
        m.box(hd, "c", x, y, 3, w, h, 1)  # and the back
    for x in (3, -3):
        for y in (0, 7):
            m.box(hd, "c", x, y, 0, 1, 1, 5)  # edges joining them
    m.box(hd, "g", 0, 3, 0, 2, 2, 2)  # one point of light inside
    orb = m.bone(hd, 0, 3.5, -5, (0, 0, 18))  # the halo: four loose bars on a square orbit round the head, turned
    for x, y, rz in ((0, 11, 0), (11, 0, 90), (0, -11, 0), (-11, 0, 90)):
        bar = m.bone(orb, x, y, 0, (0, 0, rz))
        m.box(bar, "c", 0, -0.5, 0, 8, 1, 1)
        m.box(bar, "g", 3, -1, 0, 2, 2, 2)
    return m


# ---- C: the Hunter ---------------------------------------------------------------------------------------------------
def hunter():
    m = Mass("uz_c", "C · The Hunter",
             "Built to chase. It runs on bent-back legs like a heron, high on its toes, pitched forward and low. A "
             "narrow head pushed out flat like a blade, a blue slit along each side. Mantis blades lie folded back "
             "along both forearms; one is snapped open. Plates step down its spine into a long tail that curls up "
             "into a blade it spins with. The halo is broken into two square brackets rising behind the head.", MATS)
    # bent-back legs: the thigh forward, the shin back to a high hock, a long foot down to the toes
    for s, k in ((1, 0), (-1, 1)):
        hip = m.bone(None, s * 4, 29, 3, (-50 + k * 14, 0, s * -4))
        m.box(hip, "j", 0, -11, 0, 4, 11, 4)
        m.box(hip, "c", s * 0.5, -9, -2.5, 5, 9, 2)  # thigh plate
        kn = m.bone(hip, 0, -11, 0, (86 - k * 6, 0, 0))
        m.box(kn, "c", 0, -13, 0, 3, 13, 3)  # the shin, raked back
        m.box(kn, "c", 0, -3, -2, 4, 4, 2)  # knee cop
        m.box(kn, "g", 0, -11, -1.8, 1, 8, 1)
        hk = m.bone(kn, 0, -13, 0, (-52 - k * 6, 0, 0))
        m.box(hk, "c", 0, 0, 2, 2, 3, 4)  # a spur off the back of the hock
        m.box(hk, "j", 0, -10, 0, 2, 10, 2)  # the long foot
        to = m.bone(hk, 0, -10, 0, (-60 + k * 2, 0, 0))
        m.box(to, "c", 0, -6, 0, 4, 6, 2)  # toes, pointing ahead
    m.box(None, "j", 0, 26, 3, 8, 5, 6)  # pelvis
    tor = m.bone(None, 0, 29, 3, (50, 0, 0))
    m.box(tor, "j", 0, 0, 0, 5, 6, 4)  # a narrow waist
    m.box(tor, "c", 0, 5, 0, 9, 4, 6)
    m.box(tor, "c", 0, 9, 0, 12, 5, 7)  # the chest
    m.box(tor, "g", 0, 7, -3.9, 2, 5, 1)  # core
    for k in range(4):  # plates stepping down the spine
        m.box(tor, "c", 0, 1 + k * 3.5, 3.5, 2, 3, 3 + k)
    # the tail: plates curling back and up into a blade
    tl = m.bone(None, 0, 27, 6, (104, 0, 0))
    seg = tl
    for k, (w, ln, bend) in enumerate(((4, 6, 0), (4, 6, 3), (3, 6, 5), (3, 5, 9), (2, 5, 16))):
        seg = m.bone(seg, 0, -(ln if k else 0), 0, (bend, 0, 0))
        m.box(seg, "c", 0, -ln, 0, w, ln - 1, w)
        m.box(seg, "j", 0, -ln - 1, 0, 2, 1, 2)
    tb = m.bone(seg, 0, -5, 0, (14, 0, 0))
    m.box(tb, "c", 0, -10, 0, 1, 10, 4)  # the tail blade
    m.box(tb, "c", 0, -13, 0.5, 1, 3, 2)
    m.box(tb, "g", 0, -11, -2.2, 1, 9, 1)
    # head: narrow and flat like a blade, pushed out low on the neck
    nk = m.bone(tor, 0, 14, -2, (-30, 0, 0))
    m.box(nk, "j", 0, -1, -2, 3, 3, 5)
    hd = m.bone(nk, 0, 0, -6, (-20, 0, 0))
    m.box(hd, "c", 0, -2, -4, 5, 4, 9)
    m.box(hd, "c", 0, -1.5, -10, 4, 3, 5)
    m.box(hd, "c", 0, -1, -14, 2, 2, 4)  # narrowing to a point
    m.box(hd, "c", 0, 2, -1, 3, 1, 6)  # a ridge along the top
    for s in (1, -1):
        m.box(hd, "g", s * 2.6, -0.5, -7, 1, 1, 9)  # a slit of blue along each side
        br = m.bone(hd, s * 2.5, 2, 2, (-24, 0, s * -10))  # the halo broken into two brackets, rising
        m.box(br, "c", 0, 0, 0, 1, 10, 1)
        m.box(br, "c", s * 2, 9, 0, 5, 1, 1)
        m.box(br, "c", 0, 0, 0, 3, 1, 1)
        m.box(br, "g", 0, 4, 0, 2, 2, 2)
    # arms: long, a mantis blade folded back along each forearm; the left one snapped open
    for s, (ar, fr, open_) in ((1, ((-80, 0, 10), (-30, 0, 0), True)), (-1, ((-20, 0, -12), (-110, 0, 0), False))):
        arm = m.bone(tor, s * 7, 10, -1, ar)
        m.box(arm, "c", 0, -3, 0, 5, 4, 5)  # shoulder cap
        m.box(arm, "j", 0, -11, 0, 3, 11, 3)
        el = m.bone(arm, 0, -11, 0, fr)
        m.box(el, "c", 0, -10, 0, 3, 10, 3)
        m.box(el, "c", 0, -6, 0, 4, 4, 4)
        m.box(el, "c", 0, -13, -0.5, 2, 3, 2)  # hand
        bl = m.bone(el, s * 2, -11, 0, (0 if open_ else 172, 0, 0))
        ln = 18 if open_ else 14
        m.box(bl, "c", 0, -ln, 0, 1, ln, 3)  # the blade
        m.box(bl, "c", 0, -ln - 3, -0.5, 1, 3, 2)
        m.box(bl, "g", 0, -ln + 1, -1.6, 1, ln - 2, 1)
    return m


def options():
    return [sentinel(), unbound(), hunter()]
