"""The Gearwright Colossus's silhouette round (docs/bosses/ROLLOUT.md): the Clockwork gate's Lord, a Brute. "The foundry
built one thing. It is still building it." It slams with pistons, throws a halo of sparks, strides, and ends on an
overload arc of lightning. Three shapes in brass, dark iron, green copper and the white-blue of an electric arc."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

BRASS = Ramp("#4a3412", "#6e4e1c", "#94702c", "#b89244", "#d8b464")
IRON = Ramp("#16181c", "#22252b", "#30343c", "#41464f", "#555b66")
COPPER = Ramp("#1f3a34", "#2c5248", "#3c6c5e", "#4f8774", "#68a28c")
ARC = Ramp("#2a6cff", "#56a0ff", "#8cc8ff", "#c4e6ff", "#f2fbff")
MATS = {"b": (BRASS, 2.6), "i": (IRON, 2.4), "c": (COPPER, 2.4), "g": (ARC, 2.8)}


def _cog(m, bone, mat, cx, y0, cz, size, depth, axis="x"):
    """A cog in steps (no round shapes): a square, a cross over it, and teeth on the corners."""
    s, k = size, max(2, size // 4)
    if axis == "x":
        m.box(bone, mat, cx, y0 + k, cz, depth, s - 2 * k, s)
        m.box(bone, mat, cx, y0, cz, depth, s, s - 2 * k)
        m.box(bone, "i", cx, y0 + s / 2 - 1, cz, depth + 1, 3, 3)
    else:
        m.box(bone, mat, cx, y0 + k, cz, s, s - 2 * k, depth)
        m.box(bone, mat, cx, y0, cz, s - 2 * k, s, depth)
        m.box(bone, "i", cx, y0 + s / 2 - 1, cz, 3, 3, depth + 1)


# ---- A: the Piston Titan ---------------------------------------------------------------------------------------------
def piston_titan():
    m = Mass("gc_a", "A · The Piston Titan",
             "A walking engine, top-heavy and square. Its chest is a boiler bound in brass bands with a furnace grate "
             "glowing in the middle and two stacks venting at its back. Each arm is a giant piston: a housing at the "
             "shoulder and a ram as thick as a man that drives out into a flat hammer. Its head is small, sunk low in a "
             "cage between the shoulders. It stands on hydraulic legs with feet like anvils.", MATS)
    for s in (1, -1):
        hip = m.bone(None, s * 14, 40, 2, (4, 0, s * -3))
        m.box(hip, "i", 0, -18, 0, 13, 18, 13)  # thigh housing
        m.box(hip, "b", 0, -8, 0, 15, 4, 15)  # a brass band
        kn = m.bone(hip, 0, -18, 0, (-6, 0, 0))
        _cog(m, kn, "b", s * 7, -5, 0, 10, 2)  # the knee's cog
        m.box(kn, "i", 0, -10, 0, 11, 10, 11)  # the jack: a sleeve and the rod sliding out of it
        m.box(kn, "c", 0, -18, 0, 8, 9, 8)
        m.box(kn, "g", 0, -16, -4.4, 2, 5, 1)
        m.box(None, "i", s * 14.5, 0, -3, 18, 6, 24)  # feet like anvils
        m.box(None, "i", s * 14.5, 6, -1, 14, 3, 18)
    m.box(None, "i", 0, 38, 2, 30, 9, 20)  # the gearbox at the hips
    for s in (1, -1):
        _cog(m, None, "b", s * 15.5, 36, 2, 12, 2)
    # a narrow waist of pistons: the chest rides on four rams
    for x, z in ((-7, -5), (7, -5), (-7, 7), (7, 7)):
        m.box(None, "c", x, 46, z + 2, 4, 10, 4)
        m.box(None, "b", x, 46, z + 2, 6, 3, 6)
    ch = m.bone(None, 0, 54, 2, (10, 0, 0))
    m.box(ch, "i", 0, 0, 0, 22, 5, 18)
    for k, (w, h, d) in enumerate(((28, 8, 22), (36, 9, 26), (44, 10, 28))):  # the boiler widens upward in steps
        m.box(ch, "c", 0, 5 + sum(hh for _, hh, _ in ((28, 8, 22), (36, 9, 26), (44, 10, 28))[:k]), 0, w, h, d)
        m.box(ch, "b", 0, 5 + sum(hh for _, hh, _ in ((28, 8, 22), (36, 9, 26), (44, 10, 28))[:k + 1]) - 2, 0, w + 2, 2, d + 2)
    m.box(ch, "i", 0, 9, -11.5, 14, 14, 2)  # the furnace grate
    for k in range(3):
        m.box(ch, "g", 0, 11 + k * 4, -12.6, 10, 2, 1)
    for s in (1, -1):  # two stacks venting at the back, leaning out
        st = m.bone(ch, s * 12, 30, 12, (-18, 0, s * -14))
        m.box(st, "i", 0, 0, 0, 7, 28, 7)
        m.box(st, "b", 0, 10, 0, 9, 2, 9)
        m.box(st, "b", 0, 24, 0, 9, 3, 9)
        m.box(st, "g", 0, 28, 0, 5, 2, 5)
    # the head, small, pushed forward low between the shoulders in a cage
    hd = m.bone(ch, 0, 30, -12, (-6, 0, 0))
    m.box(hd, "i", 0, 0, 0, 12, 11, 12)
    m.box(hd, "g", 0, 5, -6.5, 8, 2, 1)  # a slit visor
    for x in (-6, -2, 2, 6):
        m.box(hd, "b", x, -1, -7, 1, 13, 1)  # the cage's bars
    m.box(hd, "b", 0, 11, -1, 14, 2, 14)
    m.box(hd, "b", 0, 13, 2, 4, 4, 10)  # a crest of brass
    # arms: a piston housing at the shoulder, a ram driving out into a hammer
    for s, (ar, fr) in ((1, ((-6, 0, 14), (-20, 0, 0))), (-1, ((-38, 0, -10), (-44, 0, 0)))):
        sh = m.bone(ch, s * 25, 28, 0, (0, 0, s * -10))
        m.box(sh, "b", 0, -6, 0, 18, 16, 20)  # shoulder housing
        m.box(sh, "i", s * 2, 10, 0, 14, 4, 16)
        _cog(m, sh, "b", s * 9.5, -4, 0, 14, 2)
        arm = m.bone(sh, s * 3, -6, 0, ar)
        m.box(arm, "i", 0, -20, 0, 14, 20, 14)  # piston housing
        m.box(arm, "b", 0, -19, 0, 16, 3, 16)
        el = m.bone(arm, 0, -20, 0, fr)
        m.box(el, "c", 0, -12, 0, 10, 12, 10)  # the ram
        for x, z in ((-7, 0), (7, 0), (0, -7), (0, 7)):
            m.box(el, "i", x, -16, z, 2, 16, 2)  # exposed rods round it
        m.box(el, "i", 0, -30, 0, 24, 14, 24)  # the hammer face
        m.box(el, "b", 0, -31, 0, 26, 3, 26)
        m.box(el, "b", 0, -20, 0, 18, 3, 18)
        m.box(el, "g", 0, -26, -12.6, 10, 2, 1)
    return m


# ---- B: the Unfinished -----------------------------------------------------------------------------------------------
def unfinished():
    m = Mass("gc_b", "B · The Unfinished",
             "The foundry is still building it. Its right side is done: plated in brass, with a great hammer of a fist. "
             "Its left side is bare frame and scaffolding, the plates not fitted yet, and the left arm is a crane that "
             "ends in a hook on a chain. A crane jib rises off its shoulder and still swings over its own head. Its head "
             "is a welder's mask with one slit of arc light, and sparks fall where it's being welded.", MATS)
    for s in (1, -1):
        built = s < 0
        hip = m.bone(None, s * 12, 38, 2, (6, 0, s * -3))
        if built:
            m.box(hip, "b", 0, -18, 0, 13, 18, 13)
            m.box(hip, "i", 0, -4, 0, 15, 4, 15)
            m.box(hip, "i", 0, -13, -7, 9, 2, 1)
        else:
            for x, z in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
                m.box(hip, "i", x, -18, z, 2, 18, 2)  # a frame of four girders
            for y in (-16, -9, -2):
                m.box(hip, "i", 0, y, 0, 12, 2, 12)
            m.box(hip, "c", 0, -14, 0, 4, 12, 4)  # the piston inside
        kn = m.bone(hip, 0, -18, 0, (-8, 0, 0))
        m.box(kn, "b" if built else "i", 0, -18, 0, 12 if built else 8, 18, 12 if built else 8)
        if not built:
            m.box(kn, "c", 0, -16, -5, 10, 3, 2)
        m.box(None, "i", s * 12.5, 0, -3, 16, 6, 22)  # feet
    m.box(None, "i", 0, 36, 2, 30, 10, 20)
    ch = m.bone(None, 0, 46, 2, (10, 0, 0))
    m.box(ch, "i", 0, 0, 0, 26, 8, 20)
    for k, (w, h, d) in enumerate(((16, 9, 24), (19, 10, 27), (21, 9, 29))):  # the right half of the chest, plated in steps
        y = 8 + sum(hh for _, hh, _ in ((16, 9, 24), (19, 10, 27), (21, 9, 29))[:k])
        m.box(ch, "b", -1 - w / 2, y, 0, w, h, d)
        m.box(ch, "i", -1 - w / 2, y + h - 1, 0, w + 1, 1, d + 1)  # a seam between plates
    m.box(ch, "i", -9, 14, -14.2, 12, 2, 1)
    m.box(ch, "i", -9, 22, -15.2, 14, 2, 1)
    for x, z in ((2, -12), (12, -12), (2, 12), (12, 12), (17, 0)):  # the left half, girders and cross-bracing
        m.box(ch, "i", x, 8, z, 2, 30, 2)
    for y in (12, 22, 32):
        m.box(ch, "i", 9, y, 0, 18, 2, 26)
    m.box(ch, "c", 6, 12, 0, 6, 18, 8)  # its machinery, bare
    m.box(ch, "g", 6, 18, -4.5, 3, 3, 1)  # the core, unshielded
    m.box(ch, "g", 9, 4, -12, 2, 2, 2)  # sparks falling from a weld
    m.box(ch, "g", 11, -2, -13, 1, 1, 1)
    m.box(ch, "g", 8, -7, -12, 1, 1, 1)
    # the head: a welder's mask
    hd = m.bone(ch, -2, 38, -8)
    m.box(hd, "i", 0, 0, 0, 13, 12, 12)
    m.box(hd, "b", 0, 1, -6.5, 15, 10, 2)  # the mask
    m.box(hd, "g", 0, 6, -7.6, 10, 1, 1)  # one slit of arc light
    # the right arm, finished: a great hammer of a fist
    sh = m.bone(ch, -24, 30, 0, (0, 0, 14))
    m.box(sh, "b", 0, -6, 0, 18, 14, 22)
    m.box(sh, "b", -1, 8, 0, 16, 4, 20)  # the pauldron steps up
    m.box(sh, "b", -2, 12, 0, 10, 3, 14)
    arm = m.bone(sh, -2, -6, 0, (-20, 0, -8))
    m.box(arm, "i", 0, -6, 0, 9, 6, 9)  # the joint shows
    m.box(arm, "b", 0, -20, 0, 13, 14, 13)
    el = m.bone(arm, 0, -22, 0, (-28, 0, 0))
    _cog(m, el, "b", -7, -4, 0, 9, 2)
    m.box(el, "b", 0, -18, 0, 15, 16, 15)
    m.box(el, "i", 0, -12, 0, 17, 3, 17)
    m.box(el, "i", 0, -32, 0, 20, 14, 20)  # the fist
    for k in range(4):
        m.box(el, "b", -7.5 + k * 5, -33, -10, 4, 6, 3)  # knuckles
    m.box(el, "b", 0, -21, 0, 22, 3, 22)
    # the left arm: a crane, a hook on a chain
    sl = m.bone(ch, 20, 28, 0, (0, 0, -10))
    m.box(sl, "i", 0, -4, 0, 12, 10, 12)
    _cog(m, sl, "b", 6.5, -4, 0, 10, 2)
    la = m.bone(sl, 2, -4, 0, (-60, 0, -10))
    for x, z in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        m.box(la, "i", x, -26, z, 1, 26, 1)  # a lattice boom
    for y in range(-24, 0, 6):
        m.box(la, "i", 0, y, 0, 7, 1, 7)
    hk = m.bone(la, 0, -26, 0, (60, 0, 0))
    for k in range(5):
        m.box(hk, "i", 0, -3 - k * 3, 0, 1 if k % 2 else 2, 3, 2 if k % 2 else 1)  # the chain
    m.box(hk, "c", 0, -20, 0, 2, 5, 3)  # the hook
    m.box(hk, "c", 0, -21, -3, 2, 2, 5)
    m.box(hk, "c", 0, -19, -5, 2, 3, 2)
    # a crane jib off the shoulder, swung over its own head
    jb = m.bone(ch, 12, 34, 8, (-24, 0, 30))
    for x, z in ((-2, -2), (2, -2), (0, 2)):
        m.box(jb, "i", x, 0, z, 1, 48, 1)
    for y in range(4, 48, 7):
        m.box(jb, "i", 0, y, 0, 5, 1, 5)
    m.box(jb, "b", 0, 46, 0, 6, 4, 6)
    cb = m.bone(jb, 0, 48, 0, (24, 0, -30))
    m.box(cb, "i", 0, -22, 0, 1, 22, 1)  # its cable
    m.box(cb, "b", 0, -27, 0, 5, 5, 5)  # and a plate it's still lowering into place
    return m


# ---- C: the Clock Tower ----------------------------------------------------------------------------------------------
def clock_tower():
    m = Mass("gc_c", "C · The Clock Tower",
             "A clock tower that walked out of the foundry. Tall and narrow, on long jointed iron legs. Its body is the "
             "tower, with a square clock face on its chest and a stepped spire for a head, lit windows in its belfry. "
             "Its arms are the clock's hands: one long and one short, flat blades that sweep. Under it hangs the "
             "pendulum, a long rod with a heavy weight that swings between its legs and slams the floor.", MATS)
    for s in (1, -1):
        hip = m.bone(None, s * 9, 52, 2, (-18, 0, s * -6))
        m.box(hip, "i", 0, -24, 0, 6, 24, 6)  # long thigh
        m.box(hip, "b", 0, -6, 0, 8, 4, 8)
        kn = m.bone(hip, 0, -24, 0, (32, 0, 0))
        _cog(m, kn, "b", s * 4, -4, 0, 8, 2)
        m.box(kn, "i", 0, -26, 0, 5, 26, 5)  # long shin
        m.box(kn, "c", 0, -20, -3, 3, 12, 1)
        ft = m.bone(kn, 0, -26, 0, (-14, 0, 0))
        m.box(ft, "i", 0, -3, -4, 9, 3, 16)  # a long splayed foot
        m.box(ft, "i", 0, -2, -12, 5, 2, 4)
    tw = m.bone(None, 0, 50, 2)
    m.box(tw, "i", 0, 0, 0, 24, 6, 20)  # the tower's foot
    m.box(tw, "c", 0, 6, 0, 20, 36, 18)  # the tower
    for x, z in ((-10, -9), (10, -9), (-10, 9), (10, 9)):
        m.box(tw, "i", x, 6, z, 4, 14, 4)  # buttresses on the corners, stepping in
        m.box(tw, "i", x, 20, z, 3, 8, 3)
    for y in (6, 20, 40):
        m.box(tw, "b", 0, y, 0, 22, 2, 20)  # cornices
    # the clock face: a square in steps, with numerals as studs and two hands
    m.box(tw, "b", 0, 21, -9.5, 18, 18, 1)
    m.box(tw, "i", 0, 22, -10.2, 16, 16, 1)
    for x, y in ((0, 36), (0, 22), (7, 29), (-7, 29), (5, 34), (-5, 34), (5, 24), (-5, 24)):
        m.box(tw, "b", x, y - 0.5, -10.8, 1, 1, 1)
    m.box(tw, "g", 0, 29, -10.9, 1, 7, 1)
    m.box(tw, "g", 2.5, 29, -10.9, 5, 1, 1)
    # the belfry and the spire
    m.box(tw, "c", 0, 42, 0, 16, 12, 14)
    for s in (1, -1):
        m.box(tw, "g", s * 4, 45, -7.2, 3, 6, 1)  # lit windows
        m.box(tw, "g", s * 8.2, 45, 0, 1, 6, 3)
    for k, (w, h) in enumerate(((20, 3), (15, 4), (11, 4), (7, 5), (3, 6))):
        m.box(tw, "i", 0, 54 + sum(hh for _, hh in ((20, 3), (15, 4), (11, 4), (7, 5), (3, 6))[:k]), 0, w, h, w - 2)
    m.box(tw, "b", 0, 76, 0, 1, 6, 1)  # a weathervane rod
    m.box(tw, "b", 1.5, 79, 0, 4, 1, 1)
    # the arms are the clock's hands: flat blades from a cog at each shoulder
    for s, (rz, ln, rx) in ((1, (-64, 46, -14)), (-1, (40, 32, 10))):
        sh = m.bone(tw, s * 11, 34, 0)
        _cog(m, sh, "b", s * 1.5, -5, 0, 10, 3)
        hand = m.bone(sh, s * 2, 0, 0, (rx, 0, rz))
        m.box(hand, "i", 0, -ln, 0, 3, ln, 2)  # the hand, a flat blade
        m.box(hand, "i", 0, -ln + 6, 0, 5, 6, 2)
        m.box(hand, "b", 0, -ln - 4, 0, 1, 4, 2)  # its point
        m.box(hand, "b", 0, -10, 0, 6, 4, 3)  # the counterweight near the hub
        m.box(hand, "g", 0, -ln + 2, -1.4, 1, ln - 14, 1)
    # the pendulum, hanging between the legs
    pd = m.bone(tw, 0, 0, 2, (12, 0, 0))
    m.box(pd, "b", 0, -36, 0, 2, 36, 2)
    m.box(pd, "i", 0, -44, 0, 14, 8, 6)  # the weight in steps
    m.box(pd, "i", 0, -47, 0, 10, 3, 6)
    m.box(pd, "b", 0, -42, 0, 16, 2, 7)
    m.box(pd, "g", 0, -41, -3.6, 6, 2, 1)
    return m


def options():
    return [piston_titan(), unfinished(), clock_tower()]
