"""The Far Landing's skiff: its own blocks' textures, models and blockstates, and the flight's air sprites (SPEC §67.4-67.5).

Precursor navy plating and gold, a warm planked deck, cream canvas with a navy tip, brass fittings, blue Precursor lamps
on the skiff and amber ones on its mooring. Everything is a box on the texel grid.

    python3 tools/art/skiff_art.py            writes into src/main/resources/assets/awaken
    python3 tools/art/skiff_art.py OUT.png    the same, and a sheet of the textures to look at
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")
REF = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# the Precursor palette (from precursor_plating / precursor_trim / precursor_glow_panel) and the skiff's own woods
NAVY_D, NAVY, NAVY_M, NAVY_L, NAVY_H = map(rgb, ("1b2733", "263646", "34495d", "47627a", "62829c"))
GLOW_D, GLOW, GLOW_L, GLOW_W = map(rgb, ("234160", "3c86cc", "7fc3ff", "d6ecff"))
GOLD_H, GOLD, GOLD_M, GOLD_D, GOLD_S = map(rgb, ("f2dc8c", "d9b24c", "b8892f", "80601f", "4a3414"))
TEAK = [rgb(c) for c in ("c0935e", "b5884f", "a97c47", "b98d58")]
TEAK_SEAM, TEAK_DARK, TEAK_NAIL = map(rgb, ("6e4b2a", "936a3b", "4a321d"))
WOOD_D, WOOD, WOOD_L, WOOD_S = map(rgb, ("4a321d", "5e4126", "70502f", "38250f"))
CANVAS = [rgb(c) for c in ("ece2c8", "e6dabd", "f2ead4", "dfd2b2")]
CANVAS_EDGE, CANVAS_STITCH = map(rgb, ("cfc19d", "b3a37c"))
AMBER_D, AMBER, AMBER_L, AMBER_W = map(rgb, ("a85e18", "f0a030", "ffd27a", "fff3cc"))
CLEAR = (0, 0, 0, 0)


def new(w=16, h=16, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def strake(im, y0, y1, rnd, rivets):
    """One lapped navy plate: a lit top edge, a body that darkens down, a shadow under its lap, a few rivets."""
    for y in range(y0, y1 + 1):
        for x in range(16):
            if y == y0:
                c = NAVY_L
            elif y == y1:
                c = NAVY_D
            else:
                c = NAVY_M if (y - y0) <= (y1 - y0) // 2 and rnd.random() < 0.55 else NAVY
                if rnd.random() < 0.06:
                    c = NAVY_D
            im.putpixel((x, y), c)
    for x in rivets:
        im.putpixel((x, y0 + 2), NAVY_H)
        im.putpixel(((x + 1) % 16, y0 + 2), NAVY_D)


def hull():
    rnd = random.Random(67)
    im = new()
    strake(im, 0, 4, rnd, (3, 11))
    strake(im, 5, 9, rnd, (7, 15))
    strake(im, 10, 15, rnd, (3, 11))
    return im


def strake_side():
    """The deck block's side: the gold sheer line over two plates."""
    rnd = random.Random(68)
    im = new()
    for x in range(16):
        im.putpixel((x, 0), GOLD_H)
        im.putpixel((x, 1), GOLD)
        im.putpixel((x, 2), GOLD_M if x % 4 else GOLD_D)
        im.putpixel((x, 3), GOLD_S)
    strake(im, 4, 9, rnd, (5, 13))
    strake(im, 10, 15, rnd, (1, 9))
    return im


def deck_top():
    """Planks along the keel (down the texture): four boards, staggered butt joints, a nail each side of a joint."""
    rnd = random.Random(69)
    im = new()
    joints = {0: 5, 1: 12, 2: 2, 3: 9}
    for p in range(4):
        base = TEAK[p]
        for y in range(16):
            for i in range(4):
                x = p * 4 + i
                if i == 0:
                    c = TEAK_SEAM
                else:
                    c = base
                    if rnd.random() < 0.16:
                        c = TEAK_DARK
                    elif rnd.random() < 0.12:
                        c = TEAK[(p + 2) % 4]
                if y == joints[p] and i:
                    c = TEAK_SEAM
                im.putpixel((x, y), c)
        for dy in (-1, 1):
            im.putpixel((p * 4 + 2, (joints[p] + dy) % 16), TEAK_NAIL)
    return im


def brass():
    rnd = random.Random(70)
    im = new()
    for y in range(16):
        for x in range(16):
            c = GOLD if (x + y) % 7 in (0, 1) else GOLD_M
            if rnd.random() < 0.08:
                c = GOLD
            if x == 0 or y == 0:
                c = GOLD_H
            if x == 15 or y == 15:
                c = GOLD_D
            im.putpixel((x, y), c)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel((x, y), GOLD_H)
        im.putpixel((x + 1, y + 1), GOLD_S)
    return im


def spar():
    """A pole's faces (two four-wide strips down the texture, a brass band near each end) and its end grain."""
    rnd = random.Random(71)
    im = new()
    for strip in range(2):
        for y in range(16):
            for i in range(4):
                c = (WOOD_L, WOOD, WOOD, WOOD_D)[i]
                if rnd.random() < 0.14:
                    c = WOOD_D if i < 3 else WOOD_S
                if y in (1, 2, 13, 14):
                    c = (GOLD_H, GOLD, GOLD_M, GOLD_D)[i]
                im.putpixel((strip * 4 + i, y), c)
    for y in range(4):
        for x in range(4):
            edge = x in (0, 3) or y in (0, 3)
            im.putpixel((8 + x, y), WOOD_D if edge else WOOD_L)
    return im


def sail(band):
    """Canvas: a soft weave, a stitched hem round each panel; the tip panels are dyed navy with the gold lozenge."""
    rnd = random.Random(72 + band)
    im = new()
    for y in range(16):
        for x in range(16):
            if band:
                c = NAVY_M if (x + y) % 2 == 0 and rnd.random() < 0.5 else NAVY
            else:
                c = CANVAS[0]
                r = rnd.random()
                if r < 0.22:
                    c = CANVAS[1]
                elif r < 0.34:
                    c = CANVAS[2]
                elif r < 0.40:
                    c = CANVAS[3]
            if x in (0, 15) or y in (0, 15):
                c = NAVY_D if band else CANVAS_EDGE
                if not band and (x + y) % 3 == 0:
                    c = CANVAS_STITCH
            im.putpixel((x, y), c)
    if band:
        for y in range(16):
            for x in range(16):
                d = abs(x - 7.5) + abs(y - 7.5)
                if d <= 4:
                    im.putpixel((x, y), GOLD_H if d <= 1 else GOLD if d <= 3 else GOLD_D)
    return im


def gunwale():
    """Auto-mapped: rows 0-3 the cap's top (flat gold, so corners don't shimmer), 4-5 its edge, 6-15 two dark boards."""
    rnd = random.Random(74)
    im = new()
    for y in range(16):
        for x in range(16):
            if y < 4:
                c = GOLD
            elif y == 4:
                c = GOLD_H
            elif y == 5:
                c = GOLD_D
            elif y in (6, 11):
                c = WOOD_L
            elif y in (10, 15):
                c = WOOD_S
            else:
                c = WOOD_D if rnd.random() < 0.18 else WOOD
            im.putpixel((x, y), c)
    for x in (3, 11):
        im.putpixel((x, 8), GOLD_M)
        im.putpixel((x, 13), GOLD_M)
    return im


def helm_body():
    """Regions (explicit UVs in the model): the console's top 10x6 at 0,0; its edge 10x2 at 0,6; the pedestal 4x8 at
    10,0; the base 8x8 at 0,8; the crystal 2x2 at 14,0; a brass patch at 8,8."""
    im = brass()
    # console top: a navy inlay with a glowing line, like a chart table
    for y in range(6):
        for x in range(10):
            c = GOLD_M
            if x in (0, 9) or y in (0, 5):
                c = GOLD_H if (x == 0 or y == 0) else GOLD_D
            elif 1 < x < 8 and 1 < y < 4:
                c = NAVY if (x + y) % 3 else NAVY_M
            im.putpixel((x, y), c)
    im.putpixel((3, 2), GLOW_L)
    im.putpixel((4, 3), GLOW)
    im.putpixel((6, 2), GLOW_L)
    for x in range(10):
        im.putpixel((x, 6), GOLD)
        im.putpixel((x, 7), GOLD_D)
    for y in range(8):
        for x in range(4):
            c = (NAVY_L, NAVY, NAVY, NAVY_D)[x]
            if y in (1, 6):
                c = (GOLD_H, GOLD, GOLD_M, GOLD_D)[x]
            im.putpixel((10 + x, y), c)
    for y in range(2):
        for x in range(2):
            im.putpixel((14 + x, y), GLOW_W if (x, y) == (0, 0) else GLOW_L)
    return im


def helm_wheel():
    """A ship's wheel, ten across, auto-mapped onto a plane at x 3..13, y 5..15: a brass rim, eight spokes, a hub."""
    im = new()
    cx, cy = 7.5, 5.5
    for y in range(1, 11):
        for x in range(3, 13):
            d = math.hypot(x - cx, y - cy)
            ang = math.degrees(math.atan2(y - cy, x - cx)) % 45
            c = None
            if 3.4 <= d <= 4.6:
                c = GOLD if d < 4.1 else GOLD_M
            elif d < 1.3:
                c = GOLD_H
            elif d < 3.4 and (ang < 9 or ang > 36):
                c = WOOD_L
            elif 4.6 < d <= 5.4 and (ang < 7 or ang > 38):
                c = WOOD
            if c:
                im.putpixel((x, y), c)
    return im


def lamp(glass):
    """Regions: the cage's face 6x8 at 0,0; its cap 6x6 at 6,0; the small top 4x4 at 12,0; the hook 2x2 at 12,4."""
    d, g, l, w = (GLOW_D, GLOW, GLOW_L, GLOW_W) if glass == "blue" else (AMBER_D, AMBER, AMBER_L, AMBER_W)
    im = brass()
    for y in range(8):
        for x in range(6):
            if x in (0, 5) or y in (0, 7):
                c = GOLD_D if x == 5 or y == 7 else GOLD
            elif x in (2, 3) and 2 <= y <= 5:
                c = w if y in (3, 4) else l
            else:
                c = l if 2 <= y <= 5 else g
            im.putpixel((x, y), c)
    im.putpixel((1, 6), d)
    im.putpixel((4, 6), d)
    return im


def soft(size, seed, lumps):
    """A soft white puff in four steps of alpha, lumpy so it isn't a disc."""
    rnd = random.Random(seed)
    cs = [(rnd.uniform(0.3, 0.7) * size, rnd.uniform(0.35, 0.65) * size, rnd.uniform(0.22, 0.36) * size) for _ in range(lumps)]
    im = new(size, size)
    for y in range(size):
        for x in range(size):
            a = 0.0
            for cx, cy, r in cs:
                a = max(a, 1 - math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.5) / r)
            a = max(0.0, a)
            step = 0 if a <= 0.02 else 70 if a < 0.3 else 130 if a < 0.6 else 190
            if step:
                im.putpixel((x, y), (255, 255, 255, step))
    return im


def streak():
    im = new()
    for x in range(16):
        a = int(230 * math.sin(math.pi * (x + 0.5) / 16) ** 1.5)
        im.putpixel((x, 7), (255, 255, 255, a))
        im.putpixel((x, 8), (255, 255, 255, a // 3))
    return im


def mote():
    im = new(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            a = 255 if d < 1.2 else 150 if d < 2.2 else 60 if d < 3.3 else 0
            if a:
                im.putpixel((x, y), (255, 255, 255, a))
    return im


# ---- models and blockstates -----------------------------------------------------------------------------------------

def tex(name):
    return "awaken:block/" + name


def face(texture, uv=None, **more):
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    f.update(more)
    return f


def box(frm, to, faces, **more):
    e = {"from": frm, "to": to, "faces": faces}
    e.update(more)
    return e


def all_faces(texture, sides=None, up=None, down=None):
    f = {d: face(texture, sides) for d in ("north", "south", "east", "west")}
    f["up"] = face(texture, up)
    f["down"] = face(texture, down or up)
    return f


def models():
    m = {}
    m["skiff_hull"] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex("skiff_hull")}}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        m["skiff_hull_stairs" + suffix] = {"parent": "minecraft:block/" + parent,
                                           "textures": {"bottom": tex("skiff_hull"), "top": tex("skiff_hull"), "side": tex("skiff_hull")}}
    m["skiff_deck"] = {"parent": "minecraft:block/cube", "textures": {
        "particle": tex("skiff_deck_top"), "up": tex("skiff_deck_top"), "down": tex("skiff_hull"),
        "north": tex("skiff_strake"), "south": tex("skiff_strake"), "east": tex("skiff_strake"), "west": tex("skiff_strake")}}
    m["skiff_brass"] = {"parent": "minecraft:block/cube_all", "textures": {"all": tex("skiff_brass")}}
    m["skiff_spar"] = {"parent": "minecraft:block/block", "textures": {"particle": tex("skiff_spar"), "t": tex("skiff_spar")}, "elements": [
        box([6, 0, 6], [10, 16, 10], {
            "north": face("#t", [0, 0, 4, 16]), "south": face("#t", [4, 0, 8, 16]), "east": face("#t", [4, 0, 8, 16]), "west": face("#t", [0, 0, 4, 16]),
            "up": face("#t", [8, 0, 12, 4]), "down": face("#t", [8, 0, 12, 4])})]}
    for name, t in (("skiff_sail", "skiff_sail"), ("skiff_sail_band", "skiff_sail_band")):
        m[name] = {"parent": "minecraft:block/block", "textures": {"particle": tex(t), "t": tex(t)}, "elements": [
            box([0, 7, 0], [16, 9, 16], all_faces("#t", [0, 0, 16, 2], [0, 0, 16, 16]))]}
    m["skiff_gunwale"] = {"parent": "minecraft:block/block", "textures": {"particle": tex("skiff_gunwale"), "t": tex("skiff_gunwale")}, "elements": [
        box([0, 0, 0], [16, 10, 3], {d: face("#t") for d in ("north", "south", "east", "west", "down")}),
        box([0, 10, 0], [16, 12, 4], {d: face("#t") for d in ("north", "south", "east", "west", "up", "down")})]}
    b, w = "#b", "#w"
    m["skiff_helm"] = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                       "textures": {"particle": tex("skiff_brass"), "b": tex("skiff_helm"), "w": tex("skiff_helm_wheel")}, "elements": [
        box([4, 0, 4], [12, 1, 12], all_faces(b, [0, 15, 8, 16], [0, 8, 8, 16])),
        box([6, 1, 6], [10, 9, 10], {d: face(b, [10, 0, 14, 8]) for d in ("north", "south", "east", "west")}),
        box([3, 9, 4], [13, 11, 10], {"north": face(b, [0, 6, 10, 8]), "south": face(b, [0, 6, 10, 8]), "east": face(b, [0, 6, 6, 8]),
                                      "west": face(b, [0, 6, 6, 8]), "up": face(b, [0, 0, 10, 6]), "down": face(b, [8, 8, 16, 14])}),
        box([7, 11, 6], [9, 13, 8], {d: face(b, [14, 0, 16, 2]) for d in ("north", "south", "east", "west", "up")}, shade=False, light_emission=15),
        box([7, 9, 10], [9, 11, 11], {d: face(b, [8, 8, 10, 10]) for d in ("east", "west", "up", "down")}),
        box([3, 5, 11], [13, 15, 12], {"north": face(w), "south": face(w)})]}
    for glass in ("blue", "amber"):
        t = "#t"
        side, cap, small, hook = [0, 0, 6, 8], [6, 0, 12, 6], [12, 0, 16, 4], [12, 4, 14, 6]
        body = {d: face(t, side) for d in ("north", "south", "east", "west")}
        m["skiff_lamp_" + glass] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                                    "textures": {"particle": tex("skiff_lamp_" + glass), "t": tex("skiff_lamp_" + glass)}, "elements": [
            box([4, 0, 4], [12, 1, 12], all_faces(t, [6, 5, 12, 6], cap)),
            box([5, 1, 5], [11, 9, 11], dict(body, up=face(t, cap), down=face(t, cap)), shade=False, light_emission=15),
            box([6, 9, 6], [10, 10, 10], all_faces(t, [12, 3, 16, 4], small)),
            box([7, 10, 7], [9, 12, 9], all_faces(t, hook, hook))]}
        m["skiff_lamp_" + glass + "_hanging"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                                                 "textures": {"particle": tex("skiff_lamp_" + glass), "t": tex("skiff_lamp_" + glass)}, "elements": [
            box([5, 4, 5], [11, 12, 11], dict(body, up=face(t, cap), down=face(t, cap)), shade=False, light_emission=15),
            box([6, 12, 6], [10, 13, 10], all_faces(t, [12, 3, 16, 4], small)),
            box([7, 13, 7], [9, 16, 9], all_faces(t, [12, 4, 14, 7], hook)),
            box([6, 3, 6], [10, 4, 10], all_faces(t, [12, 3, 16, 4], small))]}
    return m


def blockstates():
    s = {}
    one = lambda model, **rot: dict({"model": "awaken:block/" + model}, **rot)
    s["skiff_hull"] = {"variants": {"": one("skiff_hull")}}
    s["skiff_brass"] = {"variants": {"": one("skiff_brass")}}
    s["skiff_deck"] = {"variants": {"axis=y": one("skiff_deck"), "axis=z": one("skiff_deck"), "axis=x": one("skiff_deck", y=90)}}
    s["skiff_spar"] = {"variants": {"axis=y": one("skiff_spar"), "axis=z": one("skiff_spar", x=90), "axis=x": one("skiff_spar", x=90, y=90)}}
    v = {}
    for band in ("false", "true"):
        model = "skiff_sail_band" if band == "true" else "skiff_sail"
        v["band=%s,plane=flat" % band] = one(model)
        v["band=%s,plane=athwart" % band] = one(model, x=90)
        v["band=%s,plane=fore_aft" % band] = one(model, x=90, y=90)
    s["skiff_sail"] = {"variants": v}
    s["skiff_gunwale"] = {"multipart": [
        {"when": {"north": "true"}, "apply": one("skiff_gunwale")},
        {"when": {"east": "true"}, "apply": one("skiff_gunwale", y=90)},
        {"when": {"south": "true"}, "apply": one("skiff_gunwale", y=180)},
        {"when": {"west": "true"}, "apply": one("skiff_gunwale", y=270)}]}
    s["skiff_helm"] = {"variants": {"facing=north": one("skiff_helm"), "facing=east": one("skiff_helm", y=90),
                                    "facing=south": one("skiff_helm", y=180), "facing=west": one("skiff_helm", y=270)}}
    v = {}
    for glass in ("blue", "amber"):
        v["glass=%s,hanging=false" % glass] = one("skiff_lamp_" + glass)
        v["glass=%s,hanging=true" % glass] = one("skiff_lamp_" + glass + "_hanging")
    s["skiff_lamp"] = {"variants": v}
    # the stairs: the game's own table, with our models
    oak = json.load(open(os.path.join(REF, "blockstates/oak_stairs.json")))
    text = json.dumps(oak).replace("minecraft:block/oak_stairs", "awaken:block/skiff_hull_stairs")
    s["skiff_hull_stairs"] = json.loads(text)
    return s


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    blocks = {"skiff_hull": hull(), "skiff_strake": strake_side(), "skiff_deck_top": deck_top(), "skiff_brass": brass(), "skiff_spar": spar(),
              "skiff_sail": sail(0), "skiff_sail_band": sail(1), "skiff_gunwale": gunwale(), "skiff_helm": helm_body(),
              "skiff_helm_wheel": helm_wheel(), "skiff_lamp_blue": lamp("blue"), "skiff_lamp_amber": lamp("amber")}
    parts = {"frontier_wisp_0": soft(16, 1, 4), "frontier_wisp_1": soft(16, 2, 5), "frontier_wisp_2": soft(16, 3, 3),
             "frontier_streak": streak(), "frontier_mote": mote()}
    for name, im in blocks.items():
        p = os.path.join(ASSETS, "textures/block", name + ".png")
        im.save(p)
    for name, im in parts.items():
        im.save(os.path.join(ASSETS, "textures/particle", name + ".png"))
    for name, m in models().items():
        write_json(os.path.join(ASSETS, "models/block", name + ".json"), m)
    for name, s in blockstates().items():
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), s)
    write_json(os.path.join(ASSETS, "particles/frontier_wisp.json"), {"textures": ["awaken:frontier_wisp_0", "awaken:frontier_wisp_1", "awaken:frontier_wisp_2"]})
    write_json(os.path.join(ASSETS, "particles/frontier_streak.json"), {"textures": ["awaken:frontier_streak"]})
    write_json(os.path.join(ASSETS, "particles/frontier_mote.json"), {"textures": ["awaken:frontier_mote"]})
    print("%d block textures, %d sprites, %d models, %d blockstates" % (len(blocks), len(parts), len(models()), len(blockstates())))
    if len(sys.argv) > 1:
        tiles = list(blocks.items()) + list(parts.items())
        sc = 10
        sheet = Image.new("RGBA", (len(tiles) * (16 * sc + 8), 16 * sc + 8), (58, 62, 74, 255))
        for i, (name, im) in enumerate(tiles):
            big = im.resize((16 * sc, 16 * sc), Image.NEAREST)
            sheet.paste(big, (i * (16 * sc + 8) + 4, 4), big)
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
