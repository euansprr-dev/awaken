"""The Glass Serpent's silhouette round (docs/bosses/ROLLOUT.md): the Glass gate's Lord, a Serpent. "Your reflection
blinks first." It lunges, sprays shards, dives into the sand and erupts beneath you, and ends in a glass storm. Three
shapes in pale glass, black glass, desert sand and a hard white glint."""
from fauna_rig import Ramp
from kit.silhouettes import Mass

GLASS = Ramp("#3e7c80", "#5aa0a2", "#7cc0c0", "#a6dcda", "#d4f2ee")
BLACK = Ramp("#06100f", "#0c1a1a", "#142626", "#1e3434", "#2a4444")
SAND = Ramp("#7a5c34", "#987646", "#b8925a", "#d2ae74", "#e8cc96")
GLINT = Ramp("#bfe8ff", "#e0f6ff", "#ffffff")
MATS = {"g": (GLASS, 2.6), "d": (BLACK, 2.2), "s": (SAND, 2.4), "w": (GLINT, 2.8)}


def _chain(m, parent, start, rot0, segs, draw):
    """A chain of segments, each one bone on from the last along its own up: segs = [(w, d, length, rot)]."""
    bn = m.bone(parent, start[0], start[1], start[2], rot0)
    prev, out = 0, []
    for k, (w, d, ln, rot) in enumerate(segs):
        bn = m.bone(bn, 0, prev, 0, rot)
        draw(bn, k, w, d, ln)
        prev = ln
        out.append(bn)
    return out


def _mound(m, x, z, w, d):
    m.box(None, "s", x, 0, z, w, 3, d)
    m.box(None, "s", x, 3, z, w - 6, 2, d - 6)


# ---- A: the Shard Wyrm -----------------------------------------------------------------------------------------------
def shard_wyrm():
    m = Mass("gs_a", "A · The Shard Wyrm",
             "A great serpent of glass rearing out of the sand. Its body is armoured in overlapping glass plates like "
             "shingles that catch the sun, darker glass beneath. A fin of tall shards runs down its back. Its head is "
             "long and narrow like a blade, crowned with swept shards, the jaw full of glass teeth. Behind it, an arch "
             "of its body breaks the sand and dives back in.", MATS)
    _mound(m, 0, 4, 30, 26)

    def seg(bn, k, w, d, ln):
        m.box(bn, "d", 0, 0, 0, w, ln + 1, d)
        m.box(bn, "g", 0, 1, -d / 2 - 0.6, w - 2, ln - 2, 2)  # the shingle plates over the front
        m.box(bn, "g", 0, ln - 4, 0, w + 2, 3, d + 2)
        if k < 7:
            fn = m.bone(bn, 0, ln - 2, d / 2, (-40, 0, 0))
            m.box(fn, "g", 0, 0, 0, 2, 8 + (k % 3) * 3, 3)  # a fin of shards down its back
            m.box(fn, "w", 0, 6 + (k % 3) * 3, 0, 1, 2, 2)
    segs = [(18, 16, 10, (0, 0, 0)), (17, 15, 10, (-14, 0, 0)), (16, 14, 10, (-12, 0, 0)), (15, 13, 10, (10, 0, 0)),
            (14, 12, 10, (22, 0, 0)), (13, 11, 9, (22, 0, 0)), (12, 10, 8, (8, 0, 0)), (11, 10, 7, (-12, 0, 0))]
    body = _chain(m, None, (0, 2, 4), (0, 0, 0), segs, seg)
    # the head: long and narrow, out over the front
    hd = m.bone(body[-1], 0, 7, 0, (-16, 0, 0))
    m.box(hd, "d", 0, -2, -2, 12, 9, 12)
    m.box(hd, "g", 0, 0, -12, 10, 6, 12)  # the snout
    m.box(hd, "g", 0, 1, -20, 6, 4, 6)  # to a point
    m.box(hd, "d", 0, -5, -12, 9, 3, 14)  # the lower jaw, a little open
    for x in (-3, 0, 3):
        m.box(hd, "w", x, -2.5, -16, 1, 2, 1)  # glass teeth
    for s in (1, -1):
        m.box(hd, "w", s * 4.5, 4, -9, 2, 1, 2)  # eyes like cut glass
        for k, (rx, ln) in enumerate(((-50, 14), (-66, 11), (-80, 8))):
            cr = m.bone(hd, s * (3 + k), 6, 2 + k * 2, (rx, 0, s * -20))
            m.box(cr, "g", 0, 0, 0, 2, ln, 2)  # a crown of swept shards
            m.box(cr, "w", 0, ln - 2, 0, 1, 2, 1)
    # an arch of its body behind, breaking the sand and diving back in
    _mound(m, 0, 94, 20, 16)
    _mound(m, 0, 52, 22, 16)
    arch = [(13, 12, 10, (0, 0, 0)), (13, 12, 10, (28, 0, 0)), (12, 11, 10, (28, 0, 0)), (12, 11, 10, (28, 0, 0)),
            (11, 10, 10, (28, 0, 0)), (11, 10, 10, (28, 0, 0))]
    _chain(m, None, (0, 1, 94), (20, 0, 0), arch, seg)
    return m


# ---- B: the Hourglass ------------------------------------------------------------------------------------------------
def hourglass():
    m = Mass("gs_b", "B · The Hourglass",
             "A serpent you can see through. Its body is a chain of hollow glass frames, each one an hourglass with "
             "sand pouring through it, strung on a spine of black glass. It lies coiled on the floor like a spring and "
             "raises its head out of the middle: a horned skull of black glass. When it's hurt, the sand runs out of "
             "it in heaps, like time running out.", MATS)
    def seg(bn, k, w, d, ln):
        m.box(bn, "d", 0, 0, 0, 3, ln + 1, 3)  # the spine
        for x, z in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            m.box(bn, "g", x * (w / 2 - 1), 1, z * (d / 2 - 1), 1, ln - 1, 1)  # the frame's posts
        m.box(bn, "g", 0, 0, 0, w, 1, d)  # its top and bottom
        m.box(bn, "g", 0, ln, 0, w, 1, d)
        m.box(bn, "s", 0, 1, 0, w - 3, max(1, round(ln * 0.3)), d - 3)  # sand in the lower half
        m.box(bn, "s", 0, round(ln * 0.3) + 1, 0, 2, round(ln * 0.5), 2)  # pouring through the waist
        m.box(bn, "s", 0, ln - 3, 0, w - 6, 2, d - 6)
    # coiled on the floor in a square-ish spiral, the neck rising out of the middle
    segs = [(15, 14, 11, (0, 0, 0))] + [(15 - k // 3, 14 - k // 3, 11, (0, 0, 32)) for k in range(10)] + \
           [(11, 11, 10, (-40, 0, 14)), (11, 10, 10, (-34, 0, 8)), (10, 10, 10, (-16, 0, 0)), (10, 9, 10, (0, 0, 0)),
            (10, 9, 10, (12, 0, 0)), (9, 9, 9, (16, 0, 0))]
    body = _chain(m, None, (20, 8, 10), (90, 0, 0), segs, seg)
    hd = m.bone(body[-1], 0, 9, 0, (20, 0, 0))
    m.box(hd, "d", 0, 0, -3, 13, 11, 13)  # a skull of black glass
    m.box(hd, "d", 0, 1, -13, 9, 6, 9)
    m.box(hd, "d", 0, -3, -12, 8, 3, 10)
    for s in (1, -1):
        m.box(hd, "w", s * 3.5, 6, -9.6, 3, 2, 1)  # hollow eyes, lit
        h1 = m.bone(hd, s * 5, 9, 1, (-40, 0, s * -30))
        m.box(h1, "d", 0, 0, 0, 3, 12, 3)
        h2 = m.bone(h1, 0, 12, 0, (-40, 0, s * 20))
        m.box(h2, "d", 0, 0, 0, 2, 10, 2)
        m.box(h2, "g", 0, 8, 0, 1, 3, 1)
    for k in range(5):
        m.box(None, "s", -12 + k * 6, 1, 26 - (k % 2) * 4, 3, 2 + (k % 3), 3)  # sand run out of it, in heaps
    return m


# ---- C: the Mirror Eel -----------------------------------------------------------------------------------------------
def mirror_eel():
    m = Mass("gs_c", "C · The Mirror Eel",
             "Flat as a ribbon and as wide as a door, it cuts through the sand like a blade, in low arches. Its body is a band of "
             "mirror glass edged with fins as sharp as razors, twisting as it rises so it flashes. It has no face, "
             "only a mirror where a face should be, framed in black glass, and in the mirror you see yourself. Your "
             "reflection blinks first.", MATS)
    def seg(bn, k, w, d, ln):
        m.box(bn, "g", 0, 0, 0, w, ln + 1, d)  # the ribbon, mirror glass
        m.box(bn, "w", 0, 2, -d / 2 - 0.3, w - 6, ln - 3, 1)
        m.box(bn, "d", 0, 0, 0, 4, ln + 1, d + 1)  # a dark line down its middle
        for s in (1, -1):
            m.box(bn, "d", s * (w / 2 + 1), 0, 0, 2, ln, 2)  # razor fins on both edges
            m.box(bn, "g", s * (w / 2 + 3), 2, 0, 2, ln - 3, 1)
    # it cuts through the sand in low arches, twisting as it goes, the head out in front
    for z0, h in ((96, 1.0), (60, 1.25)):
        _mound(m, 0, z0, 30, 12)
        _mound(m, 0, z0 - 30, 30, 12)
        arch = [(18 - (z0 < 80) * 2, 3, 9, (0, 18, 0))] + [(18 - (z0 < 80) * 2, 3, 9, (round(34 * h), 12, 0)) for _ in range(4)]
        _chain(m, None, (0, 1, z0), (round(24 * h), 0, 0), arch, seg)
    _mound(m, 0, 24, 30, 12)
    segs = [(20, 3, 10, (0, 0, 0)), (19, 3, 10, (8, 16, 0)), (18, 3, 10, (12, 16, 0)), (17, 3, 9, (16, 16, 0)),
            (16, 3, 9, (18, 14, 0))]
    body = _chain(m, None, (0, 1, 24), (24, 0, 0), segs, seg)
    hd = m.bone(body[-1], 0, 8, 0, (-56, -30, 0))
    m.box(hd, "d", 0, 0, 0, 16, 16, 4)  # the frame of black glass
    m.box(hd, "w", 0, 2, -2.4, 12, 12, 1)  # the mirror where a face should be
    m.box(hd, "d", 0, 7.5, -2.9, 8, 1, 1)  # and in it, a line of a blink
    for s in (1, -1):
        sp = m.bone(hd, s * 7, 16, 0, (0, 0, s * -24))
        m.box(sp, "d", 0, 0, 0, 2, 8, 2)  # two points of black glass above
    return m


def options():
    return [shard_wyrm(), hourglass(), mirror_eel()]
