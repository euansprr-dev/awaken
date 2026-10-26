"""The Registry ceremony's things (SPEC §16.4): the Holo band (a 3D item model), the Hunter's Licence, the wrist slot's
empty icon and the Registry paper the signing scene lays on the desk.

    python3 tools/art/specs/registry.py            write them into src/main/resources/assets/awaken/
    python3 tools/art/specs/registry.py preview D  the same, plus a contact sheet (x12) in D

    holo_band        a graphite wristband stood on its edge: eight segments round, bronze edges, a thread of System
                     blue round its middle and a glass face on top with the diamond (the face glows: light_emission)
    hunters_licence  the licence folded once: cream card, the Association's blue band, a blue wax seal, a line of ink
    slot/wrist       the empty wrist slot: a pale band outline, as vanilla draws its empty armour slots
    registry_paper   the form on the desk (64x48, drawn flat on the counter): cream laid paper, a blue header rule,
                     ruled lines, the signature line, the seal ring where Bram's stamp lands

Nothing is random: the same pixels every time. No copied assets.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "../../../src/main/resources/assets/awaken"))


def rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


GRAPHITE = ["#101319", "#1a1e27", "#262b36", "#353b48", "#4a5262"]
BRONZE = ["#3e2a14", "#6a4a24", "#94703a", "#bd9654", "#e0bd78"]
BLUE = ["#081428", "#0e2648", "#18447a", "#2f72b8", "#5fa8e8", "#a8d4ff", "#e2f2ff"]
PAPER = ["#b9a982", "#d8c9a2", "#e9ddbc", "#f3ead0", "#fbf5e2"]
INK = "#1c1a2a"


# ---- the band: one 16x16 texture, regions per face kind ------------------------------------------------------------

def band_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    # A (0..8, 0..4): the outer face of a segment: bronze edges, graphite, a blue thread in the middle
    for x in range(8):
        for y in range(4):
            c = GRAPHITE[3] if y in (1, 2) else BRONZE[3]
            if y == 0:
                c = BRONZE[4] if x % 4 else BRONZE[3]
            if y == 3:
                c = BRONZE[2]
            if y == 2 and x in (3, 4):
                c = BLUE[4]
            if y == 1:
                c = GRAPHITE[4] if x % 3 == 0 else GRAPHITE[3]
            px[x, y] = rgba(c)
    # B (8..16, 0..4): the inside (against the skin): dark, a soft pad
    for x in range(8, 16):
        for y in range(4):
            px[x, y] = rgba(GRAPHITE[1] if y in (1, 2) else GRAPHITE[2])
    # C (0..8, 4..8): the band's edges (its rims): bronze
    for x in range(8):
        for y in range(4, 8):
            px[x, y] = rgba(BRONZE[2] if (x + y) % 3 else BRONZE[3])
    # E (8..16, 4..8): the face's sides: dark metal with a bright lip
    for x in range(8, 16):
        for y in range(4, 8):
            px[x, y] = rgba(GRAPHITE[4] if y == 4 else GRAPHITE[2])
    # D (0..8, 8..16): the glass face: deep blue, a bright rim, the System's diamond
    for x in range(8):
        for y in range(8, 16):
            u, v = x - 3.5, y - 11.5
            rim = x in (0, 7) or y in (8, 15)
            c = BLUE[3] if rim else BLUE[1] if (x + y) % 2 else BLUE[0]
            if abs(u) + abs(v) <= 2.6 and abs(u) + abs(v) >= 1.4:
                c = BLUE[6]
            elif abs(u) + abs(v) < 1.4:
                c = BLUE[4]
            if y == 9 and 1 <= x <= 6 and not rim:
                c = BLUE[2]
            px[x, y] = rgba(c)
    # F (8..16, 8..16): the light thread's glow (unused by faces today; kept for a lit variant)
    for x in range(8, 16):
        for y in range(8, 16):
            px[x, y] = rgba(BLUE[5], 255)
    return im


def band_model():
    """Eight segments round the x axis (the band stood on its edge), a glass face on top."""
    r_in, r_out, half_w = 3.25, 4.5, 1.6  # radius in, out; half the band's width along x
    chord = 2 * r_out * math.tan(math.radians(22.5)) + 0.02
    els = []
    for k in range(8):
        seg = {
            "from": [8 - half_w, 8 + r_in, 8 - chord / 2],
            "to": [8 + half_w, 8 + r_out, 8 + chord / 2],
            "rotation": {"origin": [8, 8, 8], "axis": "x", "angle": k * 45.0},
            "faces": {
                "up": {"uv": [0, 0, 8, 4], "texture": "#band"},
                "down": {"uv": [8, 0, 16, 4], "texture": "#band"},
                "east": {"uv": [0, 4, 8, 8], "texture": "#band"},
                "west": {"uv": [0, 4, 8, 8], "texture": "#band"},
                "north": {"uv": [0, 4, 2, 8], "texture": "#band"},
                "south": {"uv": [0, 4, 2, 8], "texture": "#band"},
            },
        }
        # angles must be in the loader's range: express 180..315 as -180..-45
        a = k * 45.0
        if a > 180:
            a -= 360
        seg["rotation"]["angle"] = a
        els.append(seg)
    # the glass face on top: a low plate, its top lit from within
    els.append({
        "from": [8 - half_w - 0.3, 8 + r_out - 0.1, 8 - 2.3],
        "to": [8 + half_w + 0.3, 8 + r_out + 0.7, 8 + 2.3],
        "light_emission": 12,
        "faces": {
            "up": {"uv": [0, 8, 8, 16], "texture": "#band"},
            "north": {"uv": [8, 4, 16, 5], "texture": "#band"},
            "south": {"uv": [8, 4, 16, 5], "texture": "#band"},
            "east": {"uv": [8, 4, 16, 5], "texture": "#band"},
            "west": {"uv": [8, 4, 16, 5], "texture": "#band"},
            "down": {"uv": [8, 0, 16, 4], "texture": "#band"},
        },
    })
    return {
        "parent": "minecraft:block/block",
        "textures": {"band": "awaken:item/holo_band", "particle": "awaken:item/holo_band"},
        "elements": els,
        "display": {
            "gui": {"rotation": [28, 135, 0], "translation": [0, -0.6, 0], "scale": [1.05, 1.05, 1.05]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [70, 0, 0], "translation": [0, 1.5, 1.5], "scale": [0.42, 0.42, 0.42]},
            "thirdperson_lefthand": {"rotation": [70, 0, 0], "translation": [0, 1.5, 1.5], "scale": [0.42, 0.42, 0.42]},
            "firstperson_righthand": {"rotation": [15, 100, -10], "translation": [1, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [15, 100, 10], "translation": [1, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
        },
    }


# ---- the licence: a 16x16 icon ----------------------------------------------------------------------------------------

def licence_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # the card, folded once (the back leaf peeks out above, a shade darker), one-pixel ink outline
    d.rectangle([2, 2, 13, 4], fill=rgba(PAPER[1]))
    d.rectangle([1, 4, 14, 13], fill=rgba(PAPER[3]))
    d.line([1, 4, 14, 4], fill=rgba(PAPER[4]))
    d.rectangle([1, 5, 14, 6], fill=rgba(BLUE[2]))
    d.line([1, 5, 14, 5], fill=rgba(BLUE[3]))
    for y in (8, 10):
        d.line([3, y, 9, y], fill=rgba(PAPER[0]))
    # a line of ink (the signature)
    for x, y in [(3, 12), (4, 11), (5, 12), (6, 12), (7, 11), (8, 12), (9, 11)]:
        im.putpixel((x, y), rgba(INK))
    # the seal
    d.ellipse([10, 8, 13, 11], fill=rgba(BLUE[3]))
    im.putpixel((11, 9), rgba(BLUE[5]))
    im.putpixel((12, 10), rgba(BLUE[1]))
    # the outline
    out = rgba("#3a2f20")
    for x in range(16):
        for y in range(16):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and im.getpixel((nx, ny))[3] > 0 and im.getpixel((nx, ny)) != out:
                        im.putpixel((x, y), out)
                        break
    return im


# ---- the empty wrist slot ---------------------------------------------------------------------------------------------

def wrist_slot():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = (255, 255, 255, 70)
    hi = (255, 255, 255, 110)
    # an ellipse of band seen from the side, its face on top
    for t in range(64):
        a = t / 64 * math.tau
        x, y = 8 + 5.6 * math.cos(a), 9 + 3.2 * math.sin(a)
        im.putpixel((int(round(x - 0.01)), int(round(y))), c)
    for x in range(6, 11):
        im.putpixel((x, 4), hi)
        im.putpixel((x, 5), c)
    im.putpixel((8, 5), hi)
    return im


# ---- the Registry paper ---------------------------------------------------------------------------------------------

def registry_paper():
    w, h = 64, 48
    im = Image.new("RGBA", (w, h), rgba(PAPER[3]))
    px = im.load()
    # laid lines: a faint grain across the sheet
    for y in range(h):
        for x in range(w):
            k = ((x * 7 + y * 13) % 11 == 0)
            if y % 3 == 0:
                px[x, y] = rgba(PAPER[2])
            if k:
                px[x, y] = rgba(PAPER[4])
    d = ImageDraw.Draw(im)
    # the edge, a little darker, a deckle on the right
    d.rectangle([0, 0, w - 1, h - 1], outline=rgba(PAPER[1]))
    # the Association's header band and its rule
    d.rectangle([3, 3, w - 4, 8], fill=rgba(BLUE[2]))
    d.line([3, 9, w - 4, 9], fill=rgba(BLUE[3]))
    d.line([3, 10, w - 4, 10], fill=rgba(BRONZE[3]))
    # ruled lines for the printed form
    for y in (15, 19, 23, 27):
        d.line([5, y, w - 6, y], fill=rgba(PAPER[0]))
    # the signature line (the ink goes above it), with a small printed cross
    d.line([5, 38, 44, 38], fill=rgba("#6b5e44"))
    d.line([5, 35, 7, 37], fill=rgba("#6b5e44"))
    d.line([7, 35, 5, 37], fill=rgba("#6b5e44"))
    # the seal ring where the stamp lands
    d.ellipse([47, 29, 59, 41], outline=rgba(PAPER[0]))
    return im


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def build():
    out = {}

    def save(rel, im):
        p = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        out[rel] = im

    save("textures/item/holo_band.png", band_texture())
    save("textures/item/hunters_licence.png", licence_icon())
    save("textures/gui/sprites/container/slot/wrist.png", wrist_slot())
    save("textures/opening/registry_paper.png", registry_paper())
    write_json(os.path.join(ROOT, "models/item/holo_band.json"), band_model())
    write_json(os.path.join(ROOT, "items/holo_band.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/holo_band"}})
    write_json(os.path.join(ROOT, "models/item/hunters_licence.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/hunters_licence"}})
    write_json(os.path.join(ROOT, "items/hunters_licence.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/hunters_licence"}})
    return out


if __name__ == "__main__":
    ims = build()
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        S = 12
        sheet = Image.new("RGBA", (sum(im.width * S + 16 for im in ims.values()) + 16, max(im.height for im in ims.values()) * S + 32), (96, 98, 104, 255))
        x = 16
        for rel, im in ims.items():
            big = im.resize((im.width * S, im.height * S), Image.NEAREST)
            sheet.paste(big, (x, 16), big)
            x += big.width + 16
        os.makedirs(sys.argv[2], exist_ok=True)
        sheet.save(os.path.join(sys.argv[2], "registry_art.png"))
        print(os.path.join(sys.argv[2], "registry_art.png"))
    print("wrote", ", ".join(ims))
