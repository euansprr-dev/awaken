#!/usr/bin/env python3
"""The Stilled's textures (SPEC 43.3): five stone figures (man, woman, old man, old woman, young), their belongings,
the left chest (an old travelling trunk) and the letter, the tagged key and the keepsake.

The figures are weathered stone with the faintest colour of the clothes they had on, lichen on the tops and on one side,
and fine cracks; what they carried keeps its own faded colour, so they read as people turned to stone with real things.
The layout matches StilledModel (128 x 128): each box's faces as Minecraft lays them out.

    python3 tools/art/stilled.py            # writes into src/main/resources/assets/awaken/textures/
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "awaken", "textures")


def faces(u, v, w, h, d):
    """A box's faces as (x0, y0, x1, y1): top, bottom, right, front, left, back."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


class Painter:
    def __init__(self, img, seed):
        self.img = img
        self.px = img.load()
        self.r = random.Random(seed)

    def put(self, x, y, c, a=255):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def stone(self, rect, tint, t=0.16, light=1.0):
        """Weathered stone over a rectangle, tinted with what the cloth or skin was."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                g = self.r.randint(118, 146)
                base = (g, g, int(g * 0.97))
                c = mix(base, tint, t)
                if self.r.random() < 0.07:
                    c = shade(c, 0.86)
                elif self.r.random() < 0.05:
                    c = shade(c, 1.1)
                self.put(x, y, shade(c, light))

    def lichen(self, rect, amount):
        x0, y0, x1, y1 = rect
        for _ in range(int((x1 - x0) * (y1 - y0) * amount)):
            x, y = self.r.randrange(x0, x1), self.r.randrange(y0, y1)
            c = self.r.choice([(154, 166, 74), (176, 178, 112), (122, 140, 66), (201, 199, 160)])
            self.put(x, y, c)
            if self.r.random() < 0.5:
                self.put(x + self.r.choice([-1, 1]), y, shade(c, 0.9))

    def crack(self, rect):
        x0, y0, x1, y1 = rect
        if x1 - x0 < 3 or y1 - y0 < 3:
            return
        x, y = self.r.randrange(x0, x1), y0 + self.r.randrange(0, max(1, (y1 - y0) // 2))
        for _ in range(self.r.randint(2, 5)):
            if not (x0 <= x < x1 and y0 <= y < y1):
                break
            self.put(x, y, (84, 84, 82))
            y += 1
            x += self.r.choice([-1, 0, 0, 1])

    def clear(self, rect):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[x, y] = (0, 0, 0, 0)

    def fill(self, rect, colour, jitter=10, a=255):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                j = self.r.randint(-jitter, jitter)
                self.put(x, y, tuple(max(0, min(255, c + j)) for c in colour), a)


# what each look wore, as faint tints over the stone, and their hair
LOOKS = {
    "man": dict(skin=(186, 160, 140), shirt=(170, 160, 130), coat=(96, 104, 128), legs=(92, 86, 74), boots=(70, 60, 52),
                hair="short", hair_c=(90, 74, 60), coat_kind="coat"),
    "woman": dict(skin=(190, 162, 146), shirt=(182, 170, 150), coat=(140, 92, 88), legs=(110, 96, 118), boots=(76, 64, 58),
                  hair="long", hair_c=(112, 82, 56), coat_kind="shawl"),
    "old_man": dict(skin=(180, 158, 142), shirt=(160, 156, 140), coat=(110, 102, 80), legs=(84, 80, 72), boots=(64, 58, 50),
                    hair="cap", hair_c=(150, 148, 140), coat_kind="coat", old=True),
    "old_woman": dict(skin=(184, 160, 146), shirt=(170, 168, 158), coat=(84, 96, 88), legs=(80, 86, 100), boots=(64, 58, 54),
                      hair="bun", hair_c=(178, 176, 170), coat_kind="shawl", old=True),
    "young": dict(skin=(194, 168, 150), shirt=(188, 178, 160), coat=(120, 132, 98), legs=(130, 104, 96), boots=(78, 66, 58),
                  hair="long", hair_c=(130, 96, 60), coat_kind="pinafore", young=True),
}


def figure(name, look, seed):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    p = Painter(img, seed)
    old, young = look.get("old", False), look.get("young", False)

    # head: stone skin, a face with its eyes closed
    head = faces(0, 0, 8, 8, 8)
    for k, rect in head.items():
        p.stone(rect, look["skin"], 0.2, 1.05 if k == "top" else 1.0)
    fx, fy = head["front"][0], head["front"][1]
    dark, deep = (92, 90, 86), (74, 72, 70)
    for x in (1, 2, 5, 6):
        p.put(fx + x, fy + 4, dark)  # closed lids
    p.put(fx + 1, fy + 3, shade((132, 130, 124), 0.9))
    p.put(fx + 6, fy + 3, shade((132, 130, 124), 0.9))
    p.put(fx + 3, fy + 5, (150, 148, 142))  # the nose's ridge
    p.put(fx + 4, fy + 5, (150, 148, 142))
    p.put(fx + 3, fy + 6, (104, 102, 98))
    p.put(fx + 4, fy + 6, (104, 102, 98))
    for x in (3, 4) if young else (2, 3, 4, 5):
        p.put(fx + x, fy + 7, deep)  # the mouth, set
    if old:
        p.put(fx + 0, fy + 5, dark)
        p.put(fx + 7, fy + 5, dark)
        p.put(fx + 2, fy + 2, (108, 106, 100))
        p.put(fx + 5, fy + 2, (108, 106, 100))
    p.lichen(head["top"], 0.18)
    p.lichen(head["left"], 0.08)
    p.crack(head["right"])

    # hair (the overlay layer: clear, then only where hair is)
    hair = faces(32, 0, 8, 8, 8)
    for rect in hair.values():
        p.clear(rect)
    hc = look["hair_c"]
    kind = look["hair"]
    p.stone(hair["top"], hc, 0.3)
    if kind in ("short", "cap"):
        for k in ("back", "left", "right"):
            x0, y0, x1, y1 = hair[k]
            p.stone((x0, y0, x1, y0 + (5 if k == "back" else 2)), hc, 0.3, 0.92)
        if kind == "cap":  # a flat cap with its brim over the eyes
            x0, y0, x1, y1 = hair["front"]
            p.stone((x0, y0, x1, y0 + 2), (96, 92, 82), 0.35, 0.85)
            p.stone(hair["top"], (96, 92, 82), 0.35, 0.9)
        else:
            x0, y0, x1, y1 = hair["front"]
            p.stone((x0, y0, x1, y0 + 1), hc, 0.3, 0.9)
    else:  # long hair (or a bun): the back and sides down, a parting at the front
        for k in ("back", "left", "right"):
            x0, y0, x1, y1 = hair[k]
            depth = 8 if kind == "long" else 4
            if k != "back":
                depth = 6 if kind == "long" else 3
            p.stone((x0, y0, x1, y0 + depth), hc, 0.3, 0.92)
        x0, y0, x1, y1 = hair["front"]
        p.stone((x0, y0, x1, y0 + 1), hc, 0.3, 0.9)
        p.stone((x0, y0 + 1, x0 + 1, y0 + 4), hc, 0.3, 0.9)
        p.stone((x1 - 1, y0 + 1, x1, y0 + 4), hc, 0.3, 0.9)
        if kind == "bun":
            x0, y0, x1, y1 = hair["back"]
            p.stone((x0 + 2, y0 + 1, x0 + 6, y0 + 4), shade(hc, 0.85), 0.4, 0.85)
    p.lichen(hair["top"], 0.12)

    # torso: the shirt
    torso = faces(0, 16, 8, 12, 4)
    for rect in torso.values():
        p.stone(rect, look["shirt"])
    p.lichen(torso["top"], 0.25)
    p.crack(torso["front"])

    # the coat, shawl or pinafore over it
    coat = faces(24, 16, 8, 12, 4)
    for rect in coat.values():
        p.clear(rect)
    ck = look["coat_kind"]
    if ck == "coat":
        for k, rect in coat.items():
            if k == "bottom":
                continue
            p.stone(rect, look["coat"], 0.2, 0.95)
        x0, y0, x1, y1 = coat["front"]
        for y in range(y0, y1):  # open down the middle, the shirt showing
            p.px[x0 + 3, y] = (0, 0, 0, 0)
            p.px[x0 + 4, y] = (0, 0, 0, 0)
        for y in (y0 + 3, y0 + 6, y0 + 9):
            p.put(x0 + 2, y, (92, 90, 86))  # buttons
        x0, y0, x1, y1 = coat["top"]
        p.lichen(coat["top"], 0.3)
    elif ck == "shawl":
        for k in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = coat[k]
            p.stone((x0, y0, x1, y0 + 5), look["coat"], 0.22, 0.95)
            if k == "front":
                for i in range(3):  # its knotted ends
                    p.stone((x0 + 3, y0 + 5 + i, x0 + 5, y0 + 6 + i), look["coat"], 0.22, 0.9)
        p.stone(coat["top"], look["coat"], 0.22, 0.95)
        x0, y0, x1, y1 = coat["front"]
        p.stone((x0 + 1, y0 + 8, x1 - 1, y1), (196, 190, 176), 0.18, 1.05)  # an apron
        p.lichen(coat["top"], 0.3)
    else:  # a pinafore: front and back panels on straps
        for k in ("front", "back"):
            x0, y0, x1, y1 = coat[k]
            p.stone((x0 + 1, y0 + 3, x1 - 1, y1), look["coat"], 0.22, 1.0)
            p.stone((x0 + 1, y0, x0 + 2, y0 + 3), look["coat"], 0.22, 1.0)
            p.stone((x1 - 2, y0, x1 - 1, y0 + 3), look["coat"], 0.22, 1.0)

    # arms: sleeves, the hands at the forearms' ends
    for v, hands in ((16, False), (26, True)):
        arm = faces(48, v, 4, 6, 4)
        for k, rect in arm.items():
            p.stone(rect, look["coat"] if look["coat_kind"] == "coat" else look["shirt"], 0.18)
        if hands:
            for k in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = arm[k]
                p.stone((x0, y1 - 2, x1, y1), look["skin"], 0.2, 1.05)
            p.stone(arm["bottom"], look["skin"], 0.2, 1.05)
        p.lichen(arm["left"], 0.08)

    # legs: trousers or a long skirt, boots at the shins' ends
    for v, boots in ((32, False), (42, True)):
        leg = faces(0, v, 4, 6, 4)
        for rect in leg.values():
            p.stone(rect, look["legs"], 0.18, 0.95)
        if boots:
            for k in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = leg[k]
                p.stone((x0, y1 - 2, x1, y1), look["boots"], 0.28, 0.85)
            p.stone(leg["bottom"], look["boots"], 0.28, 0.8)
        p.crack(leg["front"])

    props(p)
    img.save(os.path.join(ROOT, "entity", "stilled", name + ".png"))


def props(p):
    """What they carried: faded but still its own colour (it didn't turn)."""
    # the pack: old canvas with leather straps
    pack = faces(64, 0, 8, 10, 4)
    for rect in pack.values():
        p.fill(rect, (128, 116, 88), 8)
    for k in ("front", "back"):
        x0, y0, x1, y1 = pack[k]
        for y in range(y0, y1):
            p.put(x0 + 1, y, (86, 64, 46))
            p.put(x1 - 2, y, (86, 64, 46))
        p.fill((x0 + 2, y0 + 3, x1 - 2, y0 + 5), (110, 98, 74), 6)  # the flap
    p.lichen(pack["top"], 0.1)
    # the lantern: dark iron, smoked glass
    lan = faces(88, 0, 4, 6, 4)
    for k, rect in lan.items():
        p.fill(rect, (54, 52, 50), 6)
        if k in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = rect
            p.fill((x0 + 1, y0 + 1, x1 - 1, y1 - 1), (120, 108, 70), 10)
    # the milestone: cut stone with a worn number
    ms = faces(64, 28, 8, 7, 6)
    for k, rect in ms.items():
        p.stone(rect, (170, 160, 130), 0.12, 1.08)
    x0, y0, x1, y1 = ms["front"]
    for x, y in ((2, 2), (2, 3), (2, 4), (4, 2), (5, 2), (5, 3), (4, 4), (5, 4), (4, 3)):
        p.put(x0 + x, y0 + y, (96, 94, 88))
    p.lichen(ms["top"], 0.3)
    p.lichen(ms["left"], 0.15)
    # the grave marker: a plain stone with a cross cut in it
    gr = faces(96, 16, 6, 10, 2)
    for rect in gr.values():
        p.stone(rect, (150, 150, 140), 0.1, 1.04)
    x0, y0, x1, y1 = gr["front"]
    for y in range(y0 + 2, y0 + 7):
        p.put(x0 + 2, y, (92, 92, 88))
        p.put(x0 + 3, y, (92, 92, 88))
    for x in range(x0 + 1, x0 + 5):
        p.put(x, y0 + 3, (92, 92, 88))
    p.lichen(gr["top"], 0.3)
    # the bundle: a knotted blanket
    bu = faces(64, 44, 10, 6, 8)
    for rect in bu.values():
        p.fill(rect, (126, 104, 98), 10)
    for k in ("front", "back", "left", "right", "top"):
        x0, y0, x1, y1 = bu[k]
        for x in range(x0, x1, 3):
            for y in range(y0, y1):
                p.put(x, y, (110, 88, 84))
    x0, y0, x1, y1 = bu["top"]
    p.fill((x0 + 4, y0 + 3, x0 + 6, y0 + 5), (96, 74, 70), 4)  # the knot
    # the lead: worn leather
    ld = faces(100, 44, 1, 1, 12)
    for rect in ld.values():
        p.fill(rect, (98, 70, 48), 8)
    # the cart's tail: grey old planks with an iron band
    ca = faces(0, 64, 24, 10, 8)
    for k, rect in ca.items():
        p.fill(rect, (112, 100, 84), 8)
        x0, y0, x1, y1 = rect
        if k in ("front", "back", "left", "right"):
            for y in range(y0 + 3, y1, 3):
                for x in range(x0, x1):
                    p.put(x, y, (84, 74, 62))
            for x in range(x0, x1):
                p.put(x, y1 - 1, (66, 64, 62))
        else:
            for x in range(x0 + 3, x1, 4):
                for y in range(y0, y1):
                    p.put(x, y, (90, 80, 66))
    p.lichen(ca["top"], 0.06)


def chest():
    """The left chest: an old travelling trunk, dark wood, iron bands, a heavy lock."""
    r = random.Random(43)
    out = os.path.join(ROOT, "block")
    wood, band, dark = (98, 72, 50), (70, 68, 66), (56, 40, 30)

    def base(seed):
        img = Image.new("RGBA", (16, 16))
        p = Painter(img, seed)
        p.fill((0, 0, 16, 16), wood, 9)
        for y in range(0, 16, 4):
            for x in range(16):
                p.put(x, y, dark)
        for x in (2, 13):
            for y in range(16):
                p.put(x, y, band)
                p.put(x + 1, y, shade(band, 1.15))
        for x, y in ((2, 1), (13, 1), (2, 14), (13, 14)):
            p.put(x, y, (120, 116, 108))  # rivets
        return img, p

    side, _ = base(1)
    side.save(os.path.join(out, "left_chest_side.png"))
    top, tp = base(2)
    for x in range(16):  # a strap over the lid
        tp.put(x, 7, (82, 60, 44))
        tp.put(x, 8, (82, 60, 44))
    top.save(os.path.join(out, "left_chest_top.png"))
    front, fp = base(3)
    for y in range(16):
        fp.put(7, y, band)
        fp.put(8, y, band)
    fp.fill((5, 3, 11, 9), (64, 62, 60), 4)  # the lock plate
    fp.fill((6, 4, 10, 8), (88, 84, 76), 4)
    fp.put(7, 5, (24, 22, 20))  # the keyhole
    fp.put(8, 5, (24, 22, 20))
    fp.put(8, 6, (24, 22, 20))
    for x, y in ((r.randrange(16), r.randrange(10, 16)) for _ in range(6)):
        fp.put(x, y, (126, 92, 60))  # scuffs
    front.save(os.path.join(out, "left_chest_front.png"))


def items():
    out = os.path.join(ROOT, "item")
    # the letter: folded paper gone yellow, a brown wax seal, a string
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 7)
    p.fill((2, 4, 14, 12), (226, 212, 172), 6)
    for x in range(2, 14):
        p.put(x, 4, (186, 170, 132))
        p.put(x, 11, (172, 156, 120))
    for y in range(4, 12):
        p.put(2, y, (186, 170, 132))
        p.put(13, y, (172, 156, 120))
    for i in range(6):  # the fold
        p.put(2 + i, 4 + i, (198, 184, 146))
        p.put(13 - i, 4 + i, (198, 184, 146))
    p.fill((7, 8, 9, 10), (132, 52, 40), 4)
    p.put(8, 7, (110, 40, 32))
    for y in range(3, 13):
        p.put(5, y, (150, 130, 100))
    img.save(os.path.join(out, "stilled_letter.png"))
    # the key: old iron, a paper tag on a string
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 8)
    iron, iron_d = (112, 108, 100), (70, 68, 64)
    for x, y in ((3, 3), (4, 2), (5, 2), (6, 3), (6, 4), (5, 5), (4, 5), (3, 4)):
        p.put(x, y, iron)  # the bow
    for i in range(7):
        p.put(6 + i, 5 + i, iron if i % 2 == 0 else iron_d)  # the shaft
        p.put(7 + i, 5 + i, iron_d)
    p.put(11, 12, iron)
    p.put(12, 13, iron)
    p.put(10, 13, iron_d)
    for i in range(3):
        p.put(3 - i, 6 + i * 2, (170, 150, 110))  # the string
    p.fill((0, 11, 5, 15), (222, 208, 170), 6)  # the tag
    p.put(1, 12, (120, 110, 90))
    p.put(2, 12, (120, 110, 90))
    p.put(1, 13, (120, 110, 90))
    img.save(os.path.join(out, "stilled_key.png"))
    # the keepsake: something small wrapped in a handkerchief, tied, a name scratched on a tag
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 9)
    for y in range(5, 14):
        w = 5 - abs(y - 10) // 2
        p.fill((8 - w, y, 8 + w, y + 1), (200, 186, 196), 8)
    p.fill((6, 3, 10, 6), (190, 176, 186), 6)  # the gathered top
    p.put(5, 2, (186, 170, 180))
    p.put(10, 2, (186, 170, 180))
    for x in range(5, 11):
        p.put(x, 6, (128, 88, 70))  # the ribbon
    for y in range(7, 10):
        p.put(11 + (y - 7), y, (128, 88, 70))
    p.fill((12, 9, 15, 13), (222, 206, 168), 4)
    p.put(13, 10, (110, 96, 80))
    p.put(13, 11, (110, 96, 80))
    for x, y in ((7, 9), (9, 11), (6, 12)):
        p.put(x, y, (176, 160, 172))  # folds
    img.save(os.path.join(out, "stilled_keepsake.png"))


def main():
    os.makedirs(os.path.join(ROOT, "entity", "stilled"), exist_ok=True)
    for i, (name, look) in enumerate(LOOKS.items()):
        figure(name, look, 1000 + i)
    chest()
    items()
    print("stilled textures written")


if __name__ == "__main__":
    main()
