#!/usr/bin/env python3
"""The hundred bells (SPEC §43.7, §43.16): the band's handbell, the frame and beams your bells hang on, and the bells
themselves, each shaped by its rank and cast in its family's metal (stage 6, the style sheet):

  size by rank   six sizes, a house bell at E to an S bell; the lip half-width L (model units) in SIZES, every part of the
                 profile a fraction of L, so all of them read as one family. The Great Bells (the Provings' and the Seed)
                 are their own, taller shape, drawn about two blocks tall on their beam.
  metal          the land's bronze gone green (yours, the people's, the Wilds', the gates', the big days'); the Deep's dark
                 iron with an ember glowing in the lip (the Deep, the Descent); the Above's pale silver; the dreams' pearl;
                 the Veil's cold glass (translucent); the Court's black and gold; the Great Bells' old bronze with a band
                 of old words round the waist. A bell lifted from a gate is dulled by the Deep (its _dull model, flag 0
                 of minecraft:custom_model_data) until the day it hangs.
  one hand       the frame, the beams, the hooks and every bell's loop and clapper share our timber and black ironwork.

Bell numbers, ranks and sources are read from BellCatalog.java, so this never drifts from the game. bells.BellLook keeps
the same SIZES and the Great Bell's height for the renderers.
  python3 -I tools/art/specs/bells.py            writes them under src/main/resources/assets/awaken
  python3 -I tools/art/specs/bells.py preview D  also the style sheet (textures at 8x, every family at every size) into D
"""
import json
import math
import os
import random
import re
import sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# old bronze: brown under the green, never brass-bright (the Great Bells and the Court have the gold)
BRONZE = [rgb("#33200f"), rgb("#5a3818"), rgb("#835628"), rgb("#a87636"), rgb("#c8964e"), rgb("#e2bd7a")]
WOOD = [rgb("#2a1a10"), rgb("#4a3020"), rgb("#6b4a30"), rgb("#8a6440")]
OUT = rgb("#1a1008")


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def outline(im):
    px = im.load()
    src = im.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] != 0:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src[x + dx, y + dy][3] != 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT
    return im


def bell_body(px, top, rows, lit=0.25):
    """A flared bell from row {@code top}: rows is a list of (left, right) spans, lit down its left side."""
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            t = (x - a) / max(1, b - a)
            k = 4 if t < lit else 3 if t < 0.55 else 2 if t < 0.85 else 1
            px[x, y] = BRONZE[k]
    a, b = rows[-1]
    for x in range(a, b + 1):
        px[x, top + len(rows)] = BRONZE[1] if a < x < b else BRONZE[0]


def handbell():
    """A bronze handbell on a turned wooden handle, held mouth-down, its clapper showing under the lip."""
    im = img()
    px = im.load()
    # the handle: a turned grip and a collar
    for y in range(0, 6):
        for x in (7, 8):
            px[x, y] = WOOD[3] if x == 7 else WOOD[2]
    px[7, 0] = WOOD[2]
    for x in range(6, 10):
        px[x, 5] = WOOD[1]
    bell_body(px, 6, [(6, 9), (5, 10), (5, 10), (4, 11), (4, 11), (3, 12), (3, 12)])
    px[5, 7] = BRONZE[5]
    px[5, 8] = BRONZE[5]
    px[7, 14] = rgb("#3a3a3a")
    px[8, 14] = rgb("#3a3a3a")
    px[8, 15] = rgb("#5a5a5a")
    return outline(im)


def bell_frame():
    """The frame: two dark timber posts and a beam, a bronze bell hanging under it."""
    im = img()
    px = im.load()
    for y in range(1, 16):
        for x in (1, 2, 13, 14):
            px[x, y] = WOOD[3] if x in (1, 13) else WOOD[1]
    for x in range(0, 16):
        px[x, 1] = WOOD[3]
        px[x, 2] = WOOD[2]
    px[7, 3] = WOOD[0]
    px[8, 3] = WOOD[0]
    bell_body(px, 4, [(6, 9), (5, 10), (5, 10), (4, 11), (4, 11), (4, 11), (3, 12)])
    px[5, 5] = BRONZE[5]
    px[7, 12] = rgb("#3a3a3a")
    px[8, 12] = rgb("#3a3a3a")
    return outline(im)


# ---- models -------------------------------------------------------------------------------------------------------------

def box(frm, to, tex, uv=None):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        face = {"texture": tex}
        if uv:
            face["uv"] = uv
        faces[f] = face
    return {"from": frm, "to": to, "faces": faces}


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---- the metals (stage 6) ----------------------------------------------------------------------------------------------

def mix(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3)) + (a[3] if len(a) > 3 else 255,)


PALETTES = {
    # dark to light; the land's bronze is BRONZE, gone green over it
    "bronze": BRONZE,
    "iron": [rgb("#141518"), rgb("#1f2126"), rgb("#2c2f36"), rgb("#3c4049"), rgb("#565b66"), rgb("#7b828f")],
    "silver": [rgb("#454d59"), rgb("#69727f"), rgb("#8f99a7"), rgb("#b7c0cc"), rgb("#dae1e9"), rgb("#f8fbff")],
    "pearl": [rgb("#7a6c86"), rgb("#9d8fa9"), rgb("#bfb2ca"), rgb("#dacfe1"), rgb("#efe7f1"), rgb("#fffaff")],
    "glass": [rgb("#1d4a5c", 140), rgb("#2f6c85", 150), rgb("#4f97b3", 165), rgb("#86c6dc", 180), rgb("#c4ecf7", 205), rgb("#f4fdff", 230)],
    "court": [rgb("#08080a"), rgb("#111115"), rgb("#1a1a20"), rgb("#26262e"), rgb("#373742"), rgb("#525264")],
    "great": [rgb("#2c190a"), rgb("#523118"), rgb("#7c5025"), rgb("#a67238"), rgb("#d1a158"), rgb("#f0d38c")],
}
GOLD = [rgb("#4f3508"), rgb("#86600f"), rgb("#c4941f"), rgb("#e8c04a"), rgb("#fbe38a"), rgb("#fff6cf")]
VERDIGRIS = [rgb("#24483c"), rgb("#356c58"), rgb("#4f9077"), rgb("#79b89a"), rgb("#a8dcc2")]
EMBER = [rgb("#3a0d05"), rgb("#7a1f08"), rgb("#c2410c"), rgb("#f97316"), rgb("#fdba74"), rgb("#fff1c9")]
ASH = rgb("#4b5056")

# texture rows each part of a bell samples (side faces): every size maps the same bands
ROWS = {"head": (0, 1), "crown": (1, 2), "shoulder": (2, 4), "upper": (4, 8), "lower": (8, 12), "bow": (12, 14), "lip": (14, 16)}


def noise(x, y, seed):
    return random.Random((x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)).random() - 0.5


def body_shade(x, y, seed):
    """0..5 for a bell's side at column x, row y: light down the left third, a bead at the shoulder and the sound bow
    catching it, the row under each bead in shadow, the lip's last row turned under."""
    t = x / 15.0
    k = 1.2 + 3.4 * max(0.0, min(1.0, 0.5 + 0.5 * math.cos((t - 0.28) * math.pi * 1.15)))
    if y in (3, 12):
        k += 0.9
    if y in (4, 13):
        k -= 0.8
    if y == 0:
        k += 0.4
    if y == 15:
        k -= 0.9
    k += noise(x, y, seed) * 0.55
    return max(0, min(5, int(round(k))))


def metal(name, seed=7):
    """The body texture of a family's bells."""
    im = img()
    px = im.load()
    pal = PALETTES[name]
    for y in range(16):
        for x in range(16):
            px[x, y] = pal[body_shade(x, y, seed)]
    r = random.Random(seed)
    if name == "bronze":
        # verdigris: streaks run down from under the shoulder bead, thickest on the shaded side, and green settles in
        # the shadow under each bead
        for x in range(16):
            if r.random() < (0.35 if x < 6 else 0.7):
                run = 2 + int(r.random() * (6 if x < 6 else 9))
                for y in range(4, min(15, 4 + run)):
                    if r.random() < 0.85:
                        px[x, y] = VERDIGRIS[max(0, min(4, body_shade(x, y, seed) - 1 + (1 if r.random() < 0.3 else 0)))]
        for x in range(16):
            for y in (4, 13, 1):
                if r.random() < 0.65:
                    px[x, y] = VERDIGRIS[max(0, min(4, body_shade(x, y, seed)))]
    elif name == "iron":
        for _ in range(7):
            x, y = r.randrange(16), r.randrange(2, 14)
            px[x, y] = rgb("#5a3424")
    elif name == "silver":
        for y in range(16):
            for x in range(16):
                if body_shade(x, y, seed) >= 4:
                    px[x, y] = mix(px[x, y], rgb("#e8f3ff"), 0.4)
    elif name == "pearl":
        sheen = [rgb("#f6c8dd"), rgb("#c3efe9"), rgb("#dccbf6")]
        for y in range(16):
            for x in range(16):
                px[x, y] = mix(px[x, y], sheen[((x + y) // 3) % 3], 0.4)
    elif name == "glass":
        # frost on the rims: the crown, both beads and the lip
        for x in range(16):
            for y in (0, 3, 12, 15):
                if r.random() < 0.75:
                    px[x, y] = rgb("#f2fbff", 230)
    elif name == "court":
        # black, with gold on the crown, both beads and the lip
        for x in range(16):
            for y in (0, 1, 3, 12, 14, 15):
                px[x, y] = GOLD[max(1, min(5, body_shade(x, y, seed) + (1 if y in (3, 12) else 0)))]
    elif name == "great":
        # a band of old words round the waist, framed by two bright beads
        for x in range(16):
            for y in (4, 8):
                px[x, y] = GOLD[max(1, min(5, body_shade(x, y, seed)))]
            for y in (5, 6, 7):
                px[x, y] = PALETTES["great"][max(0, body_shade(x, y, seed) - 2)]
        glyphs = random.Random(seed + 3)
        x = 0
        while x < 16:
            w = 2 if glyphs.random() < 0.5 else 3
            for gx in range(x, min(16, x + w)):
                for gy in (5, 6, 7):
                    if glyphs.random() < 0.55:
                        px[gx, gy] = PALETTES["great"][min(5, body_shade(gx, gy, seed) + 1)]
            x += w + 1
    return im


def ember():
    """The Deep's lip: dark iron cracked with ember above, the very edge glowing."""
    im = metal("iron")
    px = im.load()
    r = random.Random(41)
    for x in range(16):
        px[x, 15] = EMBER[4 if r.random() < 0.6 else 5] if x < 9 else EMBER[3 if r.random() < 0.6 else 4]
        if r.random() < 0.5:
            px[x, 14] = EMBER[2 + (1 if r.random() < 0.4 else 0)]
    return im


def dull(im, seed=11):
    """A bell the Deep held: the shine gone grey under ash, flecked with grime. Comes clean the day it hangs."""
    out = im.copy()
    px = out.load()
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] == 0:
                continue
            d = mix(c, ASH, 0.5 + noise(x // 2, y // 2, seed) * 0.2)
            d = tuple(int(v * 0.86) for v in d[:3]) + (c[3],)
            if r.random() < 0.04:
                d = rgb("#2b2f33", c[3])
            px[x, y] = d
    return out


def mouth():
    """Inside a bell, looked up into: near black, a little light caught on the rim."""
    im = img()
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = rgb("#2a1c10") if d > 6.5 else rgb("#140d07") if d > 4.5 else rgb("#070504")
    return im


def timber(post=False):
    """Our timber: dark oiled oak, the grain running along the beam (or up the post)."""
    im = img()
    px = im.load()
    r = random.Random(23 if post else 21)
    rings = [r.random() for _ in range(16)]
    for a in range(16):
        for b in range(16):
            k = 1.6 + (0.9 if rings[a] > 0.72 else -0.6 if rings[a] < 0.2 else 0) + noise(a, b, 5) * 0.8
            if (a + b * 3) % 11 == 0 and r.random() < 0.3:
                k -= 0.8
            c = WOOD[max(0, min(3, int(round(k))))]
            if post:
                px[a, b] = c
            else:
                px[b, a] = c
    return im


def ironwork():
    """Black forged iron, hammer-pitted, a rivet in each corner: the hooks, straps, loops and clappers."""
    im = img()
    px = im.load()
    pal = [rgb("#141518"), rgb("#1f2125"), rgb("#2c2f35"), rgb("#40444c"), rgb("#5d626c")]
    for y in range(16):
        for x in range(16):
            k = 2 + noise(x, y, 9) * 1.4 + (0.6 if x + y < 10 else 0)
            px[x, y] = pal[max(0, min(4, int(round(k))))]
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = pal[4]
        px[x + 1, y + 1] = pal[0]
    return im


# ---- the bells' shapes ----------------------------------------------------------------------------------------------

# the lip's half-width by rank (model units): a house bell at E up to an S bell; every part of the profile follows it
SIZES = {"E": 4.0, "D": 4.5, "C": 5.0, "B": 5.5, "A": 6.0, "S": 6.5}
# (part, half-width and height as fractions of L)
# a rounded head, a waist that barely widens, then the flare at the sound bow and the lip: a bell, not a pyramid
PROFILE = [("head", 0.30, 0.10), ("crown", 0.47, 0.10), ("shoulder", 0.60, 0.14), ("upper", 0.64, 0.50),
           ("lower", 0.70, 0.45), ("bow", 0.85, 0.26), ("lip", 1.00, 0.16)]
# a Great Bell: taller in the waist, its lip half-width GREAT_L; drawn about two blocks tall on its Great Beam
GREAT_L = 7.5
GREAT_PROFILE = [("head", 0.30, 0.09), ("crown", 0.46, 0.10), ("shoulder", 0.60, 0.14), ("upper", 0.64, 0.62),
                 ("lower", 0.70, 0.55), ("bow", 0.86, 0.28), ("lip", 1.00, 0.18)]
TOP = 15.0  # a bell hangs from (8, 15, 8): its loop sits on the hook above that
CHAMFER = 0.72  # each part's second box: how far in its corners are cut

FAMILY = {"OWN": "bronze", "PEOPLE": "bronze", "WILDS": "bronze", "GATE_WORLD": "bronze", "BIG_DAY": "bronze",
          "DEEP": "iron", "DESCENT": "iron", "ABOVE": "silver", "LUCIDIUM": "pearl", "VEIL": "glass", "COURT": "court",
          "PROVING": "great", "SEED": "great"}


def catalog():
    """(no, id, rank, source) for all hundred, read from BellCatalog.java."""
    src = open(os.path.join(ROOT, "src/main/java/dev/awaken/bells/BellCatalog.java")).read()
    rows = re.findall(r'"(\d\d)\|(\w+)\|[^|]+\|([EDCBAS])\|(\w+)\|\w+"', src)
    assert len(rows) == 100, len(rows)
    return [(int(n), i, rk, so) for n, i, rk, so in rows]


def height(profile, L):
    return sum(h for _, _, h in profile) * L


def face(tex, uv):
    return {"texture": tex, "uv": uv}


def bell_elements(profile, L, family, dulled):
    """A bell hanging from TOP: each part a box with its band of the texture on its sides, the ledges lit from above, the
    mouth dark underneath, a clapper just showing under the lip, and its iron loop."""
    els, y = [], TOP
    glow = family == "iron" and not dulled
    # each part is two crossed boxes, so its corners are cut and it reads round (the glass, seen through, keeps one box)
    cut = 1.0 if family == "glass" else CHAMFER
    for part, fw, fh in profile:
        hw, h = fw * L, fh * L
        y0 = y - h
        r0, r1 = ROWS[part]
        for k, (ax, az, inset) in enumerate(((hw, hw * cut, 0.0), (hw * cut, hw, 0.02))):
            if k == 1 and cut == 1.0:
                break
            side = face("#lip" if part == "lip" and glow else "#body", [0, r0, 16, r1])
            faces = {d: dict(side) for d in ("north", "south", "east", "west")}
            faces["up"] = face("#body", [4, 2, 12, 4])
            if part == "lip":
                faces["down"] = face("#mouth", [0, 0, 16, 16])
            e = {"from": [8 - ax, y0 + inset, 8 - az], "to": [8 + ax, y - inset, 8 + az], "faces": faces}
            if part == "lip" and glow:
                e["light_emission"] = 11
            els.append(e)
        y = y0
    bottom = y
    # the clapper: an iron shank and its ball, just below the lip
    c = 0.11 * L
    els.append(box([8 - c, bottom - 0.22 * L, 8 - c], [8 + c, bottom + 0.35 * L, 8 + c], "#iron", [6, 6, 10, 10]))
    b = 0.2 * L
    els.append(box([8 - b, bottom - 0.32 * L, 8 - b], [8 + b, bottom - 0.12 * L, 8 + b], "#iron", [4, 4, 12, 12]))
    # the loop it hangs by
    els.append(box([7.3, TOP, 7.7], [8.7, 16, 8.3], "#iron", [5, 0, 11, 4]))
    els.append(box([7.7, TOP - 0.2, 7.3], [8.3, 15.6, 8.7], "#iron", [0, 5, 4, 11]))
    for e in els:
        e["from"] = [round(v, 3) for v in e["from"]]
        e["to"] = [round(v, 3) for v in e["to"]]
    return els


def bell_display(L, great):
    """How a bell is held, shown in a slot and dropped. Held small and mouth down; in a slot, smaller bells drawn a little
    larger so the icon reads, but still smaller than an S bell's."""
    H = height(GREAT_PROFILE if great else PROFILE, L)
    mid = TOP - H / 2 - 8  # how far above the model's centre the bell's own middle is
    gui = round(0.72 * (8.0 / L) ** 0.7 * (0.9 if great else 0.95), 3)
    # held: a bigger bell still looks bigger, but an S bell or a Great Bell doesn't fill the corner of the screen
    hand = round(0.36 * (4.0 / L) ** 0.6 * (0.95 if great else 1.0), 3)
    ground = 0.36 if great else 0.42
    return {
        "firstperson_righthand": {"rotation": [0, -25, 8], "translation": [0.5, round(3.5 - mid * hand, 2), 0], "scale": [hand] * 3},
        "firstperson_lefthand": {"rotation": [0, -25, -8], "translation": [0.5, round(3.5 - mid * hand, 2), 0], "scale": [hand] * 3},
        "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, round(1.5 - mid * 0.35, 2), 1], "scale": [0.35] * 3},
        "thirdperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, round(1.5 - mid * 0.35, 2), 1], "scale": [0.35] * 3},
        "gui": {"rotation": [20, 30, 0], "translation": [0, round(-mid * gui, 2), 0], "scale": [gui] * 3},
        "ground": {"translation": [0, 2, 0], "scale": [ground] * 3},
        "fixed": {"translation": [0, round(-mid * 0.7, 2), 0], "scale": [0.7] * 3}}


def bell_model(family, rank, dulled):
    great = family == "great"
    L = GREAT_L if great else SIZES[rank]
    body = "awaken:block/bell_metal_%s%s" % (family, "_dull" if dulled else "")
    tex = {"particle": body, "body": body, "mouth": "awaken:block/bell_mouth", "iron": "awaken:block/bell_ironwork"}
    if family == "glass":
        tex["body"] = {"sprite": body, "force_translucent": True}
    if family == "iron" and not dulled:
        tex["lip"] = "awaken:block/bell_metal_ember"
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex,
            "elements": bell_elements(GREAT_PROFILE if great else PROFILE, L, family, dulled), "display": bell_display(L, great)}


def bell_models():
    """Every bell model in use, by family and rank, and the item definition picking each bell's by its number (the dulled
    one while the stack carries custom_model_data flag 0)."""
    groups = {}
    for no, _, rank, source in catalog():
        family = FAMILY[source]
        key = "great" if family == "great" else "%s_%s" % (family, rank.lower())
        groups.setdefault(key, (family, rank, []))[2].append(no)
    written = []
    for key, (family, rank, _) in sorted(groups.items()):
        write_json("models/item/bell/%s.json" % key, bell_model(family, rank, False))
        write_json("models/item/bell/%s_dull.json" % key, bell_model(family, rank, True))
        written.append(key)

    def pick(suffix):
        cases = [{"when": sorted(nos), "model": {"type": "minecraft:model", "model": "awaken:item/bell/%s%s" % (key, suffix)}}
                 for key, (_, _, nos) in sorted(groups.items())]
        return {"type": "minecraft:select", "property": "minecraft:component", "component": "awaken:bell", "cases": cases,
                "fallback": {"type": "minecraft:model", "model": "awaken:item/bell/bronze_c%s" % suffix}}

    write_json("items/bell.json", {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
                                             "on_true": pick("_dull"), "on_false": pick("")}})
    return written


# ---- the frame, the beams, the hooks: our timber and ironwork ----------------------------------------------------------

WOOD_TEX = {"particle": "awaken:block/bell_timber", "wood": "awaken:block/bell_timber", "post": "awaken:block/bell_timber_post",
            "iron": "awaken:block/bell_ironwork"}


def hooks(xs, under, zs=None):
    """Small iron hooks hanging from a beam's underside at {@code under}: a shank and a curl a bell's loop sits on. Each
    hook at its x, and at its z if {@code zs} gives one (else the middle)."""
    out = []
    for i, x in enumerate(xs):
        z = zs[i] if zs else 8
        out.append(box([x - 0.4, under - 1.2, z - 0.4], [x + 0.4, under, z + 0.4], "#iron", [7, 0, 9, 4]))
        out.append(box([x - 0.9, under - 1.6, z - 0.4], [x + 0.9, under - 1.2, z + 0.4], "#iron", [4, 7, 12, 9]))
    return out


def frame_model():
    """Two posts and a beam with three hooks (SPEC 43.16): the bells themselves are drawn by the block's renderer. The posts
    stand just outside the block so the three bells between them can be big enough to read; iron straps where the beam
    meets each post."""
    els = [box([-2, 0, 6], [0, 16, 10], "#post", [6, 0, 10, 16]), box([16, 0, 6], [18, 16, 10], "#post", [6, 0, 10, 16]),
           box([-3, 14, 5.5], [19, 16, 10.5], "#wood", [0, 6, 16, 8]),
           box([-3, 0, 5], [1, 1, 11], "#wood", [0, 4, 8, 6]), box([15, 0, 5], [19, 1, 11], "#wood", [8, 4, 16, 6]),
           box([-2.3, 13.6, 5.7], [0.3, 16.3, 10.3], "#iron", [0, 0, 6, 6]), box([15.7, 13.6, 5.7], [18.3, 16.3, 10.3], "#iron", [0, 0, 6, 6])]
    els += hooks([2.7, 8, 13.3], 14)
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def beam_model():
    """A Bell Beam: a broad timber across the top of its block, iron straps at each end, four hooks staggered front and back
    so four bells big enough to read hang from one block (Hanging.hookModel)."""
    els = [box([0, 13, 4], [16, 16, 12], "#wood", [0, 4, 16, 12]), box([0.5, 12.8, 3.8], [1.5, 16.2, 12.2], "#iron", [0, 0, 4, 16]),
           box([14.5, 12.8, 3.8], [15.5, 16.2, 12.2], "#iron", [12, 0, 16, 16])]
    els += hooks([2.5, 6.17, 9.83, 13.5], 13, [5.5, 10.5, 5.5, 10.5])
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def great_beam_model():
    """A Great Beam: a heavy timber, three iron bands and one big hook a Great Bell hangs from."""
    els = [box([0, 12, 5], [16, 16, 11], "#wood", [0, 4, 16, 10])]
    for x in (1.0, 7.0, 13.0):
        els.append(box([x, 11.8, 4.8], [x + 2, 16.2, 11.2], "#iron", [x, 0, x + 2, 16]))
    els += [box([7.4, 10.4, 7.4], [8.6, 12, 8.6], "#iron", [6, 0, 10, 6]), box([6.6, 9.8, 7.4], [9.4, 10.4, 8.6], "#iron", [3, 8, 13, 10])]
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def facing_states(model):
    return {"variants": {
        "facing=north": {"model": model}, "facing=east": {"model": model, "y": 90},
        "facing=south": {"model": model, "y": 180}, "facing=west": {"model": model, "y": 270}}}


def sleeping_model(tex):
    """A great bell, half sunk, mouth down: its lip on the ground and its crown at shoulder height."""
    s = 1.6
    els = []
    y = 0.0
    for half, h in reversed([(2.0, 1.0), (3.0, 1.5), (3.5, 3.0), (4.0, 2.0), (5.0, 1.0)]):
        hh = min(8.0, half * s)
        els.append(box([8 - hh, y, 8 - hh], [8 + hh, min(16.0, y + h * s), 8 + hh], "#bell"))
        y = min(16.0, y + h * s)
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": tex, "bell": tex}, "elements": els}


def build(out):
    """tools/art/build_all.py: this spec writes its own files (run it directly)."""


METALS = ("bronze", "iron", "silver", "pearl", "glass", "court", "great")


def textures():
    """Every block texture this spec owns: name -> image."""
    out = {}
    for name in METALS:
        im = metal(name)
        out["bell_metal_" + name] = im
        out["bell_metal_%s_dull" % name] = dull(im)
    out["bell_metal_ember"] = ember()
    out["bell_mouth"] = mouth()
    out["bell_timber"] = timber()
    out["bell_timber_post"] = timber(post=True)
    out["bell_ironwork"] = ironwork()
    return out


def sheet(out_dir, keys):
    """The style sheet: the textures at 8x, then every family at every size it's cast in (and dulled), and the frame and
    beams, drawn from their model files by tools/art/block_preview.py."""
    sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
    import block_preview as bp
    tex = textures()
    names = list(tex)
    top = Image.new("RGBA", (len(names) * 136, 150), (36, 40, 48, 255))
    d = ImageDraw.Draw(top)
    for i, n in enumerate(names):
        top.alpha_composite(tex[n].resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        d.text((i * 136 + 4, 134), n.replace("bell_", "").replace("metal_", ""), fill=(210, 214, 222, 255))
    tw, th = 170, 230
    fams = {}
    for k in keys:
        fams.setdefault(k.split("_")[0], []).append(k)
    order = ["E", "D", "C", "B", "A", "S"]
    rows = []
    for fam in METALS:
        ks = sorted(fams.get(fam, []), key=lambda k: order.index(k.split("_")[1].upper()) if "_" in k else 0)
        rows.append([("awaken:item/bell/" + k, k) for k in ks] + [("awaken:item/bell/" + ks[-1] + "_dull", ks[-1] + " dulled")])
    rows.append([("awaken:block/bell_frame", "frame"), ("awaken:block/bell_beam", "bell beam"), ("awaken:block/great_beam", "great beam")])
    cols = max(len(r) for r in rows)
    body = Image.new("RGB", (max(cols * tw, top.width), len(rows) * th), bp.BG)
    d2 = ImageDraw.Draw(body)
    for j, row in enumerate(rows):
        for i, (ident, label) in enumerate(row):
            im = bp.render(bp.quads(ident), 35, 22, False, S=9, W=tw, H=th - 14)
            body.paste(im, (i * tw, j * th))
            d2.text((i * tw + 6, j * th + th - 13), label, fill=(210, 214, 222))
    full = Image.new("RGB", (body.width, top.height + body.height), bp.BG)
    full.paste(top.convert("RGB"), (0, 0))
    full.paste(body, (0, top.height))
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "bells_style_sheet.png")
    full.save(path)
    print(path)


def main(argv):
    written = []
    for name, fn in (("handbell", handbell), ("bell_frame", bell_frame)):
        path = os.path.join(ASSETS, "textures/item/%s.png" % name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        written.append(path)
    for name, im in textures().items():
        path = os.path.join(ASSETS, "textures/block/%s.png" % name)
        im.save(path)
        written.append(path)
    write_json("models/item/handbell.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "awaken:item/handbell"}})
    write_json("items/handbell.json", {"model": {"type": "minecraft:model", "model": "awaken:item/handbell"}})
    write_json("models/item/bell_frame.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/bell_frame"}})
    write_json("items/bell_frame.json", {"model": {"type": "minecraft:model", "model": "awaken:item/bell_frame"}})
    write_json("models/block/bell_frame.json", frame_model())
    write_json("blockstates/bell_frame.json", facing_states("awaken:block/bell_frame"))
    # the beams, the bells as things, and a found bell (drawn by its renderer; the model is its particle)
    write_json("models/block/bell_beam.json", beam_model())
    write_json("blockstates/bell_beam.json", facing_states("awaken:block/bell_beam"))
    write_json("items/bell_beam.json", {"model": {"type": "minecraft:model", "model": "awaken:block/bell_beam"}})
    write_json("models/block/great_beam.json", great_beam_model())
    write_json("blockstates/great_beam.json", facing_states("awaken:block/great_beam"))
    write_json("items/great_beam.json", {"model": {"type": "minecraft:model", "model": "awaken:block/great_beam"}})
    keys = bell_models()
    old = os.path.join(ASSETS, "models/item/bell.json")
    if os.path.exists(old):
        os.remove(old)
    write_json("models/block/found_bell.json", {"textures": {"particle": "awaken:block/bell_metal_bronze"}})
    write_json("blockstates/found_bell.json", {"variants": {"": {"model": "awaken:block/found_bell"}}})
    write_json("models/block/sleeping_bell.json", sleeping_model("minecraft:block/oxidized_copper"))
    write_json("models/block/sleeping_bell_awake.json", sleeping_model("awaken:block/gate_bell_bronze"))
    write_json("blockstates/sleeping_bell.json", {"variants": {
        "awake=false": {"model": "awaken:block/sleeping_bell"}, "awake=true": {"model": "awaken:block/sleeping_bell_awake"}}})
    print("bells:", len(written), "textures,", len(keys) * 2, "bell models")
    if len(argv) > 2 and argv[1] == "preview":
        sheet(argv[2], keys)


if __name__ == "__main__":
    main(sys.argv)
