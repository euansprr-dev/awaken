"""The Wayfarers' camp (SPEC §27.6): the bedroll laid by the fire and the pot hung over it.

    python3 tools/art/build_all.py camp         write src/main/resources/assets/awaken/textures/block/<id>.png
    python3 tools/art/specs/camp.py preview DIR  the two sheets at x10, on mid grey

    wayfarer_bedroll  32x32. An oatmeal wool roll woven in a fine twill, a band of the Wayfarers' teal and a thin rust
                      stripe across it, its edge whipped in darker wool; the head end rolled up tight for a pillow with
                      a spiral seam, held by a leather strap with a brass buckle. Regions (px):
                        mat top, foot   (0,0)-(14,16)    mat top, head   (16,0)-(30,16)
                        mat side        (0,16)-(16,18)   mat end         (0,18)-(14,20)
                        roll top        (0,20)-(14,27)   roll front      (16,20)-(30,25)   roll end  (16,26)-(23,31)
    camp_pot          16x16. A soot-black iron pot with a dull rim and a warm stew in it, rope-bound spruce poles, a
                      blackened chain. Regions (px):
                        pot side (0,0)-(8,7)   pot top (8,0)-(16,8)   pole (0,8)-(2,16)   chain (3,8)-(4,16)

Painted from the shared palette plus small local ramps; no randomness beyond seeded weave noise, so the same pixels
every build. Light from the top left.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import *  # noqa: E402,F401,F403

LOCAL = {
    "wool":    ["#5e5242", "#7d6f5a", "#9c8d74", "#b8aa8e", "#d0c3a6", "#e4d9bf"],
    "teal":    ["#0f2e2e", "#174a48", "#21665f", "#2f857a", "#47a596"],
    "rust":    ["#3a170c", "#5e2814", "#82401f", "#a65a2c"],
    "leather": ["#1e120a", "#33200f", "#4d3019", "#684523", "#835c30"],
    "brass":   ["#4a3410", "#7a5a1c", "#b08a30", "#dcb84e", "#f4dc86"],
    "iron":    ["#0c0d10", "#17191d", "#23262c", "#323640", "#4a505c", "#6c7484"],
    "stew":    ["#3a1e0e", "#5a3216", "#7a4a20", "#9c6630", "#c08848"],
    "pole":    ["#2a1c10", "#40301e", "#5a442a", "#76603c", "#927a52"],
    "rope":    ["#5a4628", "#7e6a40", "#a48e5e", "#c4b080"],
}


def col(name, i):
    rp = LOCAL.get(name) or RAMPS[name]
    return hex_rgba(rp[max(0, min(len(rp) - 1, i))])


def noise(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def weave(c, x0, y0, w, h, along_y=True, seed=1, stripes=True):
    """Twill wool: diagonal ribs, a teal band and a rust stripe across (across the length), a darker whipped edge."""
    for j in range(h):
        for i in range(w):
            u, v = (i, j) if along_y else (j, i)  # v runs along the roll's length
            rib = ((u + v) % 3 == 0)
            n = noise(x0 + i, y0 + j, seed)
            k = 3 + (1 if n > 0.72 else 0) - (1 if rib else 0) - (1 if n < 0.12 else 0)
            ramp_name = "wool"
            if stripes:
                band = v % 16
                if band in (5, 6, 7):
                    ramp_name, k = "teal", 3 - (1 if rib else 0) + (1 if band == 5 else 0)
                elif band == 9:
                    ramp_name, k = "rust", 2 + (1 if n > 0.6 else 0)
            edge = u == 0 or u == (w if along_y else h) - 1
            if edge:
                k = min(k, 1 if ramp_name == "wool" else 1)
                ramp_name = "wool" if ramp_name == "wool" else ramp_name
            if u == 1 and not edge:
                k += 1  # light catching the turned edge, top left
            c.set(x0 + i, y0 + j, col(ramp_name, k))


def bedroll() -> Canvas:
    c = Canvas(32, 32)
    # mat tops: the foot, and the head (whose far half the pillow covers)
    weave(c, 0, 0, 14, 16, True, seed=3)
    weave(c, 16, 0, 14, 16, True, seed=5)
    # the mat's edge seen from the side and the end: wool in shadow, the whipped stitch
    for i in range(16):
        for j in range(2):
            c.set(i, 16 + j, col("wool", 2 - j if i % 2 else 1))
    for i in range(14):
        for j in range(2):
            c.set(i, 18 + j, col("wool", 2 - j if i % 2 == 0 else 1))
    # the roll from above: tight turns of wool across, a strap round its middle with a brass buckle
    for i in range(14):
        for j in range(7):
            turn = j % 2
            n = noise(i, 20 + j, 9)
            k = 4 - turn - (1 if j in (0, 6) else 0) + (1 if n > 0.8 else 0)
            band = (i + 2) % 16
            ramp_name = "teal" if band in (5, 6, 7) else "wool"
            c.set(i, 20 + j, col(ramp_name, k - (1 if ramp_name == "teal" else 0)))
    for j in range(7):
        c.set(9, 20 + j, col("leather", 3 if j < 3 else 2))
        c.set(10, 20 + j, col("leather", 2 if j < 3 else 1))
    c.set(9, 23, col("brass", 3))
    c.set(10, 23, col("brass", 2))
    c.set(9, 22, col("brass", 4))
    # the roll's long face: its turns stacked, rounded top lit, bottom in shadow, the strap down it
    for i in range(14):
        for j in range(5):
            k = [4, 3, 3, 2, 1][j]
            band = (i + 2) % 16
            ramp_name = "teal" if band in (5, 6, 7) else "wool"
            if noise(16 + i, 20 + j, 11) > 0.85:
                k -= 1
            c.set(16 + i, 20 + j, col(ramp_name, k - (1 if ramp_name == "teal" else 0)))
    for j in range(5):
        c.set(25, 20 + j, col("leather", 3 - (j // 2)))
        c.set(26, 20 + j, col("leather", 2 - (j // 3)))
    c.set(25, 22, col("brass", 3))
    # the roll's end: the spiral of wool wound on itself
    cx, cy = 3.0, 2.0
    for i in range(7):
        for j in range(5):
            dx, dy = i - cx, (j - cy) * 1.3
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            s = (r - a / math.tau * 1.6) % 1.6
            k = 4 if s < 0.6 else 2
            if r > 3.4:
                k = 1
            c.set(16 + i, 26 + j, col("wool", k))
    c.set(19, 28, col("wool", 1))
    return c


def pot() -> Canvas:
    c = Canvas(16, 16)
    # the pot's side: soot-black iron, a dull rim on top, a lit flank to the left, rivets
    for i in range(8):
        for j in range(7):
            k = 2 + (1 if i in (1, 2) else 0) - (1 if i >= 6 else 0) - (1 if j >= 5 else 0)
            if j == 0:
                k = 4 if i < 6 else 3
            if noise(i, j, 21) > 0.82:
                k = max(0, k - 1)
            c.set(i, j, col("iron", k))
    c.set(2, 3, col("iron", 5))
    c.set(5, 3, col("iron", 4))
    # the pot from above: its rim ring and a warm stew with a bubble
    for i in range(8):
        for j in range(8):
            dx, dy = i - 3.5, j - 3.5
            r = math.hypot(dx, dy)
            if r > 3.9:
                k, rn = 1, "iron"
            elif r > 2.9:
                k, rn = (4 if dx + dy < 0 else 2), "iron"
            else:
                k, rn = (3 if noise(8 + i, j, 23) > 0.5 else 2), "stew"
                if (i, j) in ((3, 3), (4, 2)):
                    k = 4
            c.set(8 + i, j, col(rn, k))
    # a pole: spruce, its grain along it, a lit edge, rope bindings
    for j in range(8):
        c.set(0, 8 + j, col("pole", 3 if j % 3 else 4))
        c.set(1, 8 + j, col("pole", 2 if j % 4 else 1))
    for j in (9, 10):
        c.set(0, j, col("rope", 3))
        c.set(1, j, col("rope", 2))
    # the chain: dark links, a glint on every other
    for j in range(8):
        c.set(3, 8 + j, col("iron", 4 if j % 2 == 0 else 2))
    return c


TEXTURES = {"wayfarer_bedroll": bedroll, "camp_pot": pot}


def build(out):
    for name, fn in TEXTURES.items():
        out.block(name, fn())


def preview(directory):
    os.makedirs(directory, exist_ok=True)
    for name, fn in TEXTURES.items():
        img = fn().image()
        big = img.resize((img.width * 10, img.height * 10), Image.NEAREST)
        sheet = Image.new("RGBA", big.size, (96, 96, 96, 255))
        sheet.alpha_composite(big)
        sheet.save(os.path.join(directory, name + "_x10.png"))


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        preview(sys.argv[2])
