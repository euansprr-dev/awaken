"""The Sky Leviathan's silhouette round (docs/bosses/ROLLOUT.md): the Sky Archipelago's Lord, a Serpent fought in the
air (SPEC §17.18). "The ground is much too far below." It dives through the sky, tilts gravity, then opens the Storm Eye.
Three shapes in sky blue, storm slate, cloud white, pearl, old gold, weathered stone, and lightning. Not a whale: the
white whale and the cloud whales are the Gate Worlds' own."""
import math

from fauna_rig import Ramp
from kit.silhouettes import Mass

CLOUD = Ramp("#8a94a8", "#aab4c4", "#c8d0dc", "#e2e8f0", "#f6f8fc")
SKY = Ramp("#14365a", "#1e4c7c", "#2c66a0", "#4686c0", "#6caada")
STORM = Ramp("#12161e", "#1c222e", "#283042", "#363f56", "#48526c")
PEARL = Ramp("#8c84a8", "#aaa2c4", "#c8c2dc", "#e2def0", "#f8f6ff")
GOLD = Ramp("#5a4214", "#86661e", "#b08c30", "#d4b04c", "#ecd27a")
STONE = Ramp("#3a3836", "#54514c", "#6e6a62", "#8a857a", "#a8a294")
BOLT = Ramp("#2a70d8", "#4a96ff", "#80bcff", "#b8dcff", "#eef8ff")
MATS = {"c": (CLOUD, 2.6), "s": (SKY, 2.6), "t": (STORM, 2.4), "p": (PEARL, 3.0), "g": (GOLD, 2.6),
        "r": (STONE, 2.4), "l": (BOLT, 3.0)}


def _chain(m, pts, widths, mat, belly=None):
    """A body along a curve: a bar between each pair of points, a cube at each joint so the bends close, and a paler
    belly bar slung under it."""
    for k in range(len(pts) - 1):
        a, b, w = pts[k], pts[k + 1], widths[k]
        m.rod(None, mat, a, b, w=w)
        m.box(None, mat, a[0], a[1] - w / 2, a[2], w, w, w)
        if belly:
            o = 0.34 * w
            m.rod(None, belly, (a[0], a[1] - o, a[2]), (b[0], b[1] - o, b[2]), w=max(1, round(0.78 * w)))


def _claw(m, x, up, z, s, mat="g", toe=5):
    for k in (-1, 0, 1):
        m.rod(None, mat, (x, up, z), (x + s * k * 2, up - 3, z - toe + abs(k)), w=1)


# ---- A: the Cloud Dragon ---------------------------------------------------------------------------------------------
def cloud_dragon():
    m = Mass("sk_a", "A · The Cloud Dragon",
             "A long sky dragon with no wings, the kind that swims through the air. Its body winds like a ribbon a dozen "
             "blocks long, sky blue on the back and pale underneath, with a mane of white cloud running down its spine. "
             "Four short legs with gold claws; one front claw holds a glowing pearl. Branching gold antlers, long "
             "whiskers that trail in the wind, and a tail that ends in a tuft of cloud.", MATS)
    N = 26
    pts, ws = [], []
    for k in range(N + 1):
        t = k / N
        pts.append((round(22 * math.sin(2 * math.pi * 0.95 * t + 0.3), 1),
                    round(46 + 16 * math.sin(2 * math.pi * 1.15 * t + 1.3), 1),
                    round(-80 + 196 * t, 1)))
        ws.append(11 if t < 0.1 else max(2, round(15 * (1 - t) ** 0.75 + 1)))
    _chain(m, pts, ws, "s", belly="p")
    for k in range(1, N - 3):  # the cloud mane down the spine
        x, up, z = pts[k]
        w = ws[k]
        m.box(None, "c", x, up + w / 2 - 1, z, max(4, round(w * 0.8)), 4 + 3 * (k % 2), 7)
        if k % 3 == 0:
            m.box(None, "c", x + (3 if k % 2 else -3), up + w / 2 + 3, z + 2, 5, 4, 5)
    for k in range(0, 4):  # the cloud tuft at the tail's end
        x, up, z = pts[-1]
        m.box(None, "c", x + (-4, 4, 0, 1)[k], up - 3 + (0, 2, 5, -4)[k], z + (0, 3, 6, 8)[k], 8 - k, 7 - k, 8 - k)
    # four short legs, the front right claw holding the pearl
    for leg, kk in (("front", 4), ("hind", 15)):
        x, up, z = pts[kk]
        w = ws[kk]
        for s in (1, -1):
            sh = (x + s * w / 2, up - 2, z)
            el = (x + s * (w / 2 + 7), up - 10, z - 3)
            ft = (x + s * (w / 2 + 6), up - 18, z - 8)
            m.rod(None, "s", sh, el, w=5)
            m.rod(None, "s", el, ft, w=4)
            m.box(None, "c", el[0] + s * 2, el[1] - 2, el[2] + 3, 4, 4, 5)  # a puff of cloud at the elbow
            if leg == "front" and s == -1:
                m.box(None, "p", ft[0], ft[1] - 9, ft[2] - 4, 9, 9, 9)  # the pearl
                m.box(None, "l", ft[0], ft[1] - 7, ft[2] - 8.8, 3, 3, 1)
                _claw(m, ft[0], ft[1], ft[2], s, toe=6)
            else:
                _claw(m, ft[0], ft[1], ft[2], s)
    # the head, out in front, looking ahead
    x0, up0, z0 = pts[0]
    hd = m.bone(None, x0, up0 - 6, z0, (0, 0, 0))
    m.box(hd, "s", 0, 0, 2, 15, 13, 14)  # the skull
    m.box(hd, "s", 0, 3, -12, 11, 7, 14)  # the long snout
    m.box(hd, "p", 0, 0, -12, 10, 3, 14)
    m.box(hd, "s", 0, 4, -20, 13, 5, 4)  # the nose
    m.box(hd, "t", 0, 6, -22.2, 9, 2, 1)
    jw = m.bone(hd, 0, 1, -4, (-16, 0, 0))  # the jaw, a little open
    m.box(jw, "p", 0, -4, -8, 10, 4, 16)
    for s in (1, -1):
        m.box(hd, "c", s * 3, 3, -18.5, 1, 3, 1)  # fangs
        m.box(hd, "g", s * 7.6, 8, -3, 1, 3, 4)  # the eyes
        m.box(hd, "s", s * 6, 11, -2, 5, 3, 8)  # brows
        # the whiskers, trailing back in the wind
        m.rod(hd, "g", (s * 6, 6, -18), (s * 16, 2, -14), w=1)
        m.rod(hd, "g", (s * 16, 2, -14), (s * 26, -8, 2), w=1)
        m.rod(hd, "g", (s * 26, -8, 2), (s * 30, -10, 20), w=1)
        # the branching antlers, swept back
        a1 = (s * 4, 13, 4)
        a2 = (s * 9, 24, 14)
        a3 = (s * 12, 32, 28)
        m.rod(hd, "g", a1, a2, w=3)
        m.rod(hd, "g", a2, a3, w=2)
        m.rod(hd, "g", a2, (s * 15, 30, 10), w=2)
        m.rod(hd, "g", ((a2[0] + a3[0]) / 2, (a2[1] + a3[1]) / 2, (a2[2] + a3[2]) / 2), (s * 16, 38, 22), w=2)
        m.box(hd, "c", s * 8, 6, 10, 6, 8, 6)  # the mane starts behind the cheeks
    for k in range(3):  # a beard of cloud under the jaw
        m.box(hd, "c", 0, -8 - 2 * k, -10 + 5 * k, 8 - k, 5, 6)
    return m


# ---- B: the Ray ------------------------------------------------------------------------------------------------------
def ray():
    m = Mass("sk_b", "B · The Ray",
             "A great flat ray that flies on the wind the way rays swim, its wings ten blocks from tip to tip. Storm "
             "slate on top, veined with lightning; pale as cloud underneath, with glowing gill slits. Two horns curl "
             "forward either side of its wide mouth. Behind it a whip of a tail three times its length, ending in a "
             "fork of lightning. Cloud trails off its wing tips.", MATS)
    bk = m.bone(None, 0, 40, 0, (8, 0, 24))  # banking into a dive, so its back shows
    for i in range(-13, 14):  # the wing, strip by strip from tip to tip
        x = i * 6
        r = abs(x) / 80
        front = -30 + 36 * r ** 1.1
        back = 30 - 18 * r
        c = max(4, round(back - front))
        h = max(2, round(15 * (1 - r) ** 1.6) + 1)
        yc = 10 * r ** 2.2 - 3 * r
        ht, hb = max(1, round(h * 0.62)), max(1, h - round(h * 0.62))
        m.box(bk, "t", x, yc, (front + back) / 2, 6, ht, c)
        m.box(bk, "c", x, yc - hb, (front + back) / 2 + 1, 6, hb, max(2, c - 3))
        if abs(i) < 9 and i % 2 == 0:  # lightning veins across the top
            m.box(bk, "l", x, yc + ht, (front + back) / 2 - c / 6, 6, 1, 1)
    m.box(bk, "t", 0, 10, -6, 10, 4, 34)  # the raised back ridge
    for k in range(6):
        m.box(bk, "l", 0, 14, -16 + k * 6, 2, 1, 3)
    for s in (1, -1):
        for x, z, ln in ((14, -10, 18), (30, -4, 16), (46, 4, 12), (62, 10, 8)):  # veins running back along each wing
            r = x / 80
            m.box(bk, "l", s * x, 10 * r ** 2.2 - 3 * r + max(1, round(round(15 * (1 - r) ** 1.6 + 1) * 0.62)), z, 1, 1, ln)
        for k in range(5):  # the gill slits underneath, glowing
            m.box(bk, "l", s * (8 + k * 2), -6.4 + k * 0.2, -14 + k * 3, 1, 1, 6)
        # the horns either side of the mouth, curling forward
        m.rod(bk, "t", (s * 10, 4, -28), (s * 14, 2, -46), w=5)
        m.rod(bk, "t", (s * 14, 2, -46), (s * 10, -3, -56), w=3)
        m.rod(bk, "t", (s * 10, -3, -56), (s * 6, -7, -52), w=2)
        m.box(bk, "g", s * 15, 4, -20, 2, 3, 3)  # the eyes, on the sides of the head
        for k in range(3):  # cloud trailing off the wing tips
            m.box(bk, "c", s * (78 - k * 3), 8 - k * 2, 14 + k * 8, 6 - k, 4, 8)
    m.box(bk, "t", 0, -4, -30, 18, 5, 4)  # the wide mouth
    m.box(bk, "c", 0, -2, -32.2, 14, 2, 1)
    # the whip of a tail, waving behind it
    pts = [(round(7 * math.sin(k / 3), 1), round(2 + 6 * math.sin(k / 4 + 1), 1), 28 + k * 9) for k in range(13)]
    for k in range(12):
        m.rod(bk, "t", pts[k], pts[k + 1], w=max(1, 5 - k // 3))
    x, up, z = pts[-1]  # the forked lightning at its end
    for s in (1, -1):
        p = (x, up, z)
        for k, (dx, du, dz) in enumerate(((s * 4, 3, 6), (-s * 1, 2, 5), (s * 5, 4, 6), (s * 1, 1, 5))):
            q = (p[0] + dx, p[1] + du * s, p[2] + dz)
            m.rod(bk, "l", p, q, w=2 if k < 2 else 1)
            p = q
    return m


# ---- C: the Sky Ruin -------------------------------------------------------------------------------------------------
def sky_ruin():
    m = Mass("sk_c", "C · The Sky Ruin",
             "An old serpent of the sky that has carried a temple on its back for so long the temple has become part "
             "of it. Two great humps of stone-grey scale, sky blue underneath: on the first stand broken columns and an "
             "arch, on the second a little bell tower with its gold bell still in it. Chains hang from the ruins and "
             "cloud spills off them as it flies. Long fins trail from its sides. A pearl is set in its forehead.", MATS)
    N = 24
    pts, ws = [], []
    for k in range(N + 1):
        t = k / N
        up = 28 + 30 * math.exp(-((t - 0.3) / 0.11) ** 2) + 24 * math.exp(-((t - 0.64) / 0.1) ** 2) + 18 * math.exp(-(t / 0.09) ** 2)
        pts.append((round(9 * math.sin(2 * math.pi * t + 0.6), 1), round(up, 1), round(-70 + 180 * t, 1)))
        ws.append(14 if t < 0.1 else max(3, round(21 * (1 - t) ** 0.6)))
    _chain(m, pts, ws, "r", belly="s")
    # long fins trailing from its sides
    for kk in (5, 12, 17):
        x, up, z = pts[kk]
        w = ws[kk]
        for s in (1, -1):
            fn = m.bone(None, x + s * w / 2, up - 2, z, (-60, 0, s * 70))
            m.box(fn, "s", 0, 0, 4, 1, 18 - kk // 2, 8)
    # the tail ends in a fan
    x, up, z = pts[-1]
    for s in (1, 0, -1):
        m.rod(None, "s", (x, up, z), (x + s * 8, up + 2 - abs(s) * 3, z + 16), w=2)
    # the ruins: columns and an arch on the first hump
    k1 = 7
    x, up, z = pts[k1]
    top = up + ws[k1] / 2
    m.box(None, "r", x, top - 2, z, 24, 4, 30)  # the platform of old paving, sunk into its back
    for zz in (-10, 10):
        for s in (1, -1):
            h = 22 if not (zz > 0 and s < 0) else 11  # one column broken off
            m.box(None, "r", x + s * 9, top + 2, z + zz, 5, h, 5)
            m.box(None, "r", x + s * 9, top + 2, z + zz, 7, 2, 7)
    m.box(None, "r", x, top + 24, z - 10, 25, 3, 6)  # the arch across the front pair
    m.box(None, "r", x, top + 27, z - 10, 15, 3, 6)
    m.box(None, "r", x, top + 30, z - 10, 5, 3, 6)
    # the bell tower on the second hump
    k2 = 15
    x, up, z = pts[k2]
    top = up + ws[k2] / 2
    m.box(None, "r", x, top - 2, z, 20, 4, 22)
    m.box(None, "r", x, top + 2, z, 13, 20, 13)  # the tower
    for sx in (1, -1):
        for sz in (1, -1):
            m.box(None, "r", x + sx * 5, top + 22, z + sz * 5, 3, 10, 3)  # the open belfry
    m.box(None, "g", x, top + 23, z, 5, 6, 5)  # the bell
    m.box(None, "g", x, top + 22, z, 7, 2, 7)
    m.box(None, "r", x, top + 32, z, 15, 3, 15)  # the roof, stepped to a point
    m.box(None, "r", x, top + 35, z, 10, 3, 10)
    m.box(None, "r", x, top + 38, z, 5, 3, 5)
    m.box(None, "g", x, top + 41, z, 1, 4, 1)
    # chains hanging from both ruins, and cloud spilling off them
    for kk, zs in ((k1, (-12, 4, 13)), (k2, (-8, 8))):
        x, up, z = pts[kk]
        w = ws[kk]
        for j, zz in enumerate(zs):
            s = 1 if j % 2 == 0 else -1
            a = (x + s * (w / 2 + 2), up + w / 2 - 2, z + zz)
            b = (a[0] + s * 2, a[1] - 26 - 4 * j, a[2] + 3)
            m.rod(None, "t", a, b, w=1)
            m.box(None, "r", b[0], b[1] - 4, b[2], 4, 4, 4)  # a broken stone on its end
        for s in (1, -1):
            m.box(None, "c", x + s * (w / 2 + 3), up + w / 2 - 4, z - 6, 6, 6, 10)
            m.box(None, "c", x + s * (w / 2 + 6), up + w / 2 - 9, z + 2, 6, 5, 8)
            m.box(None, "c", x + s * (w / 2 + 4), up - w / 2 - 2, z + 10, 7, 5, 9)
    # the head: old and blunt, a horned brow, the pearl in its forehead
    x0, up0, z0 = pts[0]
    hd = m.bone(None, x0, up0 - 8, z0, (-6, 0, 0))
    m.box(hd, "r", 0, 0, 0, 18, 15, 18)
    m.box(hd, "r", 0, 2, -14, 15, 9, 12)  # the muzzle
    m.box(hd, "s", 0, 0, -12, 13, 3, 14)
    jw = m.bone(hd, 0, 1, -2, (-10, 0, 0))
    m.box(jw, "s", 0, -4, -10, 14, 4, 18)
    m.box(hd, "r", 0, 15, -6, 16, 4, 8)  # the heavy brow
    m.box(hd, "p", 0, 13, -10.2, 5, 5, 2)  # the pearl
    m.box(hd, "l", 0, 14.5, -11.4, 2, 2, 1)
    for s in (1, -1):
        m.box(hd, "g", s * 6, 9, -9.2, 3, 2, 1)  # the eyes under the brow
        m.rod(hd, "r", (s * 7, 17, -2), (s * 14, 26, 12), w=4)  # horns swept back
        m.rod(hd, "r", (s * 14, 26, 12), (s * 15, 27, 24), w=2)
        fr = m.bone(hd, s * 9, 6, 6, (-20, s * 30, s * 50))  # the frill fins behind the jaw
        m.box(fr, "s", 0, 0, 0, 1, 14, 10)
    return m


def options():
    return [cloud_dragon(), ray(), sky_ruin()]
