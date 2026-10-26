#!/usr/bin/env python3
"""Village troubles (SPEC §28.7): the trail's marks (drag scuffs, torn cloth), the carved token on a family's door, and
the items a trouble gives or asks for (a keepsake, a village's bell, the herb remedy). 16x16 textures with their block
models, blockstates and item definitions.

  python3 -I tools/art/specs/village_troubles.py            writes them under src/main/resources/assets/awaken
  python3 -I tools/art/specs/village_troubles.py preview D  also a sheet of every texture at 8x into D
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), c[3])


# ---- the trail -------------------------------------------------------------------------------------------------------

SOIL = [rgb("#2e2016"), rgb("#3d2b1d"), rgb("#4d3624"), rgb("#634630"), rgb("#7a5a3c")]
GRASS = [rgb("#4a6b2a"), rgb("#5f8434"), rgb("#78a040")]


def drag_marks(look):
    """Two heel-grooves dragged through the ground, north to south, with clods thrown up beside them and torn grass."""
    r = random.Random(1000 + look)
    im = img()
    px = im.load()
    for groove in (5 + look % 2, 10 - (look // 2)):
        x = groove
        for y in range(16):
            if r.random() < 0.10 and 1 < y < 14:
                continue  # the heel lifted a moment
            if r.random() < 0.18:
                x = max(2, min(13, x + r.choice((-1, 1))))
            px[x, y] = SOIL[r.randint(0, 1)]
            px[x + 1, y] = SOIL[r.randint(1, 2)]
            # thrown-up edge, lighter
            if r.random() < 0.55:
                ex = x - 1 if r.random() < 0.5 else x + 2
                if 0 <= ex < 16 and px[ex, y][3] == 0:
                    px[ex, y] = SOIL[r.randint(3, 4)]
    # clods and torn grass blades about the grooves
    for _ in range(9 + look * 2):
        x, y = r.randint(1, 14), r.randint(0, 15)
        if px[x, y][3] == 0:
            px[x, y] = (SOIL[r.randint(2, 4)] if r.random() < 0.6 else GRASS[r.randint(0, 2)])
    return im


CLOTH = [("#9c3a2e", "#c4573f"), ("#35527f", "#4e72a6"), ("#4b7034", "#679645"), ("#a87b2a", "#cf9e3f")]


def torn_cloth(look):
    """A strip of someone's clothes caught on the ground: a ragged scrap, folded once, its torn edge frayed."""
    r = random.Random(2000 + look)
    base, light = rgb(CLOTH[look][0]), rgb(CLOTH[look][1])
    im = img()
    px = im.load()
    # a long scrap lying corner to corner, wider in the middle
    for y in range(3, 14):
        w = 3 + (2 if 5 < y < 11 else 0) + r.randint(0, 1)
        cx = 4 + (y - 3) * 0.7
        for x in range(int(cx - w / 2), int(cx + w / 2) + 1):
            if 0 <= x < 16:
                fold = (x + y) % 5 == 0
                px[x, y] = shade(base, 0.72) if fold else (light if r.random() < 0.35 else base)
    # frayed, torn end
    for y in (12, 13, 14):
        for x in range(9, 14):
            if px[x, y][3] and r.random() < 0.5:
                px[x, y] = (0, 0, 0, 0)
    for _ in range(4):
        x, y = r.randint(9, 14), r.randint(12, 15)
        px[x, y] = shade(base, 0.85)
    # a darker crease and a few threads
    for y in range(4, 13):
        x = int(4 + (y - 3) * 0.7)
        if px[x, y][3]:
            px[x, y] = shade(base, 0.6)
    return im


# ---- the token ------------------------------------------------------------------------------------------------------

OAK = [rgb("#4a3420"), rgb("#6b4f2c"), rgb("#8a6a3e"), rgb("#a8854f"), rgb("#c2a066")]


def carved_token():
    """A round of oak with a carved knot-star in it, its rim darker, end-grain rings faint."""
    im = img()
    px = im.load()
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d > 7.6:
                continue
            if d > 6.6:
                px[x, y] = OAK[1]
            else:
                ring = int(d * 1.3) % 3
                px[x, y] = OAK[3] if ring else OAK[2]
    # the carved mark: an eight-pointed star of cuts, the cuts dark with a light lip
    cuts = [(7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)]
    for i in range(4, 12):
        cuts += [(i, i), (i, 15 - i)]
    for i in range(5, 11):
        cuts += [(7, i), (8, i), (i, 7), (i, 8)] if i in (5, 10) else []
    for (x, y) in cuts:
        if px[x, y][3]:
            px[x, y] = OAK[0]
            if y + 1 < 16 and px[x, y + 1][3] and (x, y + 1) not in cuts:
                px[x, y + 1] = OAK[4]
    # the nail hole at the top
    px[7, 1] = OAK[0]
    return im


# ---- items -------------------------------------------------------------------------------------------------------------

def keepsake():
    """A carved charm on a red cord."""
    im = img()
    px = im.load()
    cord = [rgb("#7a1e1a"), rgb("#a8322a")]
    for i, (x, y) in enumerate([(3, 1), (4, 2), (5, 3), (6, 4), (7, 5), (12, 1), (11, 2), (10, 3), (9, 4), (8, 5)]):
        px[x, y] = cord[i % 2]
    token = carved_token().resize((10, 10), Image.NEAREST)
    im.alpha_composite(token, (3, 5))
    return outline(im)


BRONZE = [rgb("#4a2f12"), rgb("#7a4f1e"), rgb("#a8712c"), rgb("#cf9a3e"), rgb("#efc86a"), rgb("#fff0b0")]


def village_bell():
    """A bronze bell: its crown and loop, a flaring body lit down one side, the lip and the clapper."""
    im = img()
    px = im.load()
    rows = {2: (7, 8), 3: (6, 9), 4: (5, 10), 5: (5, 10), 6: (5, 10), 7: (4, 11), 8: (4, 11), 9: (4, 11), 10: (3, 12), 11: (3, 12), 12: (2, 13)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            t = (x - a) / max(1, b - a)
            k = 4 if t < 0.25 else 3 if t < 0.55 else 2 if t < 0.85 else 1
            px[x, y] = BRONZE[k]
    for x in range(2, 14):
        px[x, 13] = BRONZE[1] if x not in (2, 13) else BRONZE[0]
    px[4, 4] = BRONZE[5]
    px[4, 5] = BRONZE[5]
    # the loop
    for (x, y) in [(7, 0), (8, 0), (6, 1), (9, 1)]:
        px[x, y] = BRONZE[2]
    # the clapper under the lip
    px[7, 14] = rgb("#3a3a3a")
    px[8, 14] = rgb("#3a3a3a")
    px[7, 15] = rgb("#5a5a5a")
    return outline(im)


def grey_remedy():
    """A glass bottle of pale green-grey brew with a sprig of sunleaf in its neck."""
    im = img()
    px = im.load()
    glass, glass2, brew, brew2 = rgb("#cfe3e8", 170), rgb("#e8f4f6", 200), rgb("#9fb89a"), rgb("#c2d8b4")
    for y in range(7, 15):
        for x in range(4, 12):
            if (x in (4, 11)) and y in (7, 14):
                continue
            px[x, y] = brew if y > 8 else glass
    for y in range(9, 14):
        px[5, y] = brew2
    for y in range(4, 7):
        for x in range(6, 10):
            px[x, y] = glass2
    for x in range(6, 10):
        px[x, 3] = rgb("#8a6a3e")
    # the sprig
    for (x, y) in [(9, 2), (10, 1), (11, 0), (10, 2), (8, 1)]:
        px[x, y] = rgb("#7fb04a")
    return outline(im)


def outline(im):
    """A dark outline round an item's shape (the mod's item style)."""
    out = im.copy()
    src, dst = im.load(), out.load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[nx, ny][3] > 120:
                    dst[x, y] = (24, 18, 14, 255)
                    break
    return out


# ---- writing --------------------------------------------------------------------------------------------------------------

def save(im, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return path


def write_json(rel, obj):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def flat_model(tex, height=0.25):
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"mark": tex, "particle": tex},
        "elements": [{
            "from": [0, 0.02, 0], "to": [16, height, 16],
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#mark"}, "down": {"uv": [0, 16, 16, 0], "texture": "#mark", "cullface": "down"}},
        }],
    }


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_look_states(model):
    v = {}
    for f, rot in ROT.items():
        for look in range(4):
            e = {"model": "awaken:block/%s_%d" % (model, look)}
            if rot:
                e["y"] = rot
            v["facing=%s,look=%d" % (f, look)] = e
    return {"variants": v}


def main(argv):
    written = []
    for look in range(4):
        written.append(save(drag_marks(look), "block/drag_marks_%d" % look))
        written.append(save(torn_cloth(look), "block/torn_cloth_%d" % look))
        write_json("models/block/drag_marks_%d.json" % look, flat_model("awaken:block/drag_marks_%d" % look))
        write_json("models/block/torn_cloth_%d.json" % look, flat_model("awaken:block/torn_cloth_%d" % look, 0.5))
    write_json("blockstates/drag_marks.json", facing_look_states("drag_marks"))
    write_json("blockstates/torn_cloth.json", facing_look_states("torn_cloth"))
    written.append(save(carved_token(), "block/carved_token"))
    write_json("models/block/carved_token.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"face": "awaken:block/carved_token", "particle": "awaken:block/carved_token", "side": "minecraft:block/stripped_oak_log"},
        "elements": [
            {"from": [4, 3, 14.5], "to": [12, 11, 16], "faces": {
                "north": {"uv": [4, 4, 12, 12], "texture": "#face"},
                "south": {"uv": [4, 4, 12, 12], "texture": "#face", "cullface": "south"},
                "east": {"uv": [0, 4, 1.5, 12], "texture": "#side"}, "west": {"uv": [0, 4, 1.5, 12], "texture": "#side"},
                "up": {"uv": [4, 0, 12, 1.5], "texture": "#side"}, "down": {"uv": [4, 0, 12, 1.5], "texture": "#side"}}},
            {"from": [7.5, 11, 15], "to": [8.5, 12.5, 16], "faces": {
                "north": {"uv": [0, 0, 1, 1.5], "texture": "#side"}, "east": {"uv": [0, 0, 1, 1.5], "texture": "#side"},
                "west": {"uv": [0, 0, 1, 1.5], "texture": "#side"}, "up": {"uv": [0, 0, 1, 1], "texture": "#side"}}},
        ],
    })
    write_json("blockstates/carved_token.json", {"variants": {("facing=" + f): ({"model": "awaken:block/carved_token", "y": r} if r else
                                                                               {"model": "awaken:block/carved_token"}) for f, r in ROT.items()}})
    for name, fn in (("keepsake", keepsake), ("village_bell", village_bell), ("grey_remedy", grey_remedy)):
        written.append(save(fn(), "item/" + name))
        write_json("models/item/%s.json" % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + name}})
        write_json("items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})
    write_json("models/item/carved_token.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:block/carved_token"}})
    write_json("items/carved_token.json", {"model": {"type": "minecraft:model", "model": "awaken:item/carved_token"}})
    for b in ("drag_marks", "torn_cloth"):
        write_json("items/%s.json" % b, {"model": {"type": "minecraft:model", "model": "awaken:block/%s_0" % b}})
    print("village troubles:", len(written), "textures")
    if len(argv) > 2 and argv[1] == "preview":
        sheet = Image.new("RGBA", (len(written) * 136, 136), (40, 44, 52, 255))
        for i, p in enumerate(written):
            sheet.alpha_composite(Image.open(p).convert("RGBA").resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        os.makedirs(argv[2], exist_ok=True)
        sheet.save(os.path.join(argv[2], "village_troubles.png"))


if __name__ == "__main__":
    main(sys.argv)
