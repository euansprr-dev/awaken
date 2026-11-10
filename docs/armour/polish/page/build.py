#!/usr/bin/env python3
"""Builds the armour polish plan's approval page: crops the before and after tour shots into one picture per set and
view (today on the left, the draft on the right), and writes index.html from template.html.

    python3 docs/armour/polish/page/build.py <before dir prefix> <after dir prefix>

The prefixes name tour folders under run-autopilot/screenshots-ci/ (before-armour-audit, after-armour-audit); the
newest folder of each tour is used. Pictures land in docs/armour/polish/page/img/."""
import glob
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../../.."))
CI = os.path.join(ROOT, "run-autopilot/screenshots-ci")
IMG = os.path.join(HERE, "img")

RANKS = {"E": ["wildhide", "bellguard", "rootwarden", "amberwing"],
         "D": ["beastbound", "cleftrunner", "lanternlight", "bladeward", "hornlord"],
         "C": ["gatewrought", "vaultbreaker", "frostwalker", "sunward", "shardcrown"],
         "B": ["veilwoven", "deepcarapace", "cloudrunner", "tidebreaker", "hollowheart"],
         "A": ["lordscale", "leviathan", "galewing", "halo_choir", "dreamsteel"],
         "S": ["sovereign", "starfall", "riftwalker", "nightcourt", "signals_echo"]}
# name, archetype, how it stands today, what the finish gives it (PLAN.md 2.3)
SETS = {
    "wildhide": ("Wildhide", "Strider", "The wolf hood reads at once. Your bare arm shows whatever skin you wear, and the hand is bare.", "A wrapped glove and a rolled cuff. Later: a sleeve under the bare arm."),
    "bellguard": ("Bellguard", "Bulwark", "The bell reads. Bronze and green are its own.", "A glove under its bell-mouth cuff. It needed little."),
    "rootwarden": ("Rootwarden", "Channeler", "Antlers and moss. Strong.", "A bark glove. Later: leaves that stir."),
    "amberwing": ("Amberwing", "Duelist", "The back, with its crossed amber wings, is one of the best. From the front the helm is a dark box with a band.", "Wings that hold more light and an amber cuff. Later: one break in the helm's brow."),
    "beastbound": ("Beastbound", "Bulwark", "The skull and tusks you chose. It has no light at all, which is right this early.", "A tusk-bone cuff and a riveted plate on the hand."),
    "cleftrunner": ("Cleftrunner", "Strider", "The thinnest build of all 29. It reads by blue and red alone.", "A glove with fingers. Later: a scarf tail that streams and depth in the hood."),
    "lanternlight": ("Lanternlight", "Channeler", "The shoulder lanterns read.", "A brass cuff and lamp-light on the hand. Later: lantern cages that swing."),
    "bladeward": ("Bladeward", "Duelist", "The T helm, the plume and the one great pauldron read.", "A steel gauntlet. Later: a plume that moves."),
    "hornlord": ("Hornlord", "Lord-forged", "Horns and ember. Strong.", "A gold cuff, and its ember line run on down the hand."),
    "gatewrought": ("Gatewrought", "Duelist", "The ring of five stones is good. The back is a blank slab and the whole suit is grey on grey.", "Gate-light on the hand, a silver cuff. Later: the gate's arch on the back."),
    "vaultbreaker": ("Vaultbreaker", "Bulwark", "Ram horns and the shield pauldron read. All grey.", "Diamonds that hold light at night, riveted plate in place of rims."),
    "frostwalker": ("Frostwalker", "Strider", "The icicle crown reads and the palette is its own.", "Plates cut like ice, and a lit ice plate on the hand."),
    "sunward": ("Sunward", "Channeler", "The sun disc is one of the best shapes in the game. It was dark at night.", "The disc lit after sunset, a gold cuff. Later: rays that turn slowly."),
    "shardcrown": ("Shardcrown", "Lord-forged", "The crystal crown reads. The body is dark on dark.", "Crystals lit from inside, cut faces on the steel, a crystal cuff."),
    "veilwoven": ("Veilwoven", "Channeler", "Hood and veils, as you approved them.", "A glove. Later: hems that drift."),
    "deepcarapace": ("Deepcarapace", "Bulwark", "The lowest contrast of all: a dark shape with a teal brim.", "Shell segments with pale lips, and its seam run on down the hand."),
    "cloudrunner": ("Cloudrunner", "Strider", "White and blue with fins. Fine, with plain plates.", "Feathered plates and a brighter storm stone."),
    "tidebreaker": ("Tidebreaker", "Duelist", "One teal mass.", "Amethyst spines that hold light, a violet cuff, scales on the limbs."),
    "hollowheart": ("Hollowheart", "Lord-forged", "Ribs and the caged heart. Strong.", "Rib segments on the limbs and a bone cuff. Later: a heart that beats."),
    "lordscale": ("Lordscale", "Lord-forged", "The bar. The one you approved first.", "Its molten line run on down the back of the hand. Nothing else."),
    "leviathan": ("Leviathan", "Bulwark", "Dark blue with pale frames. At a distance it is mistaken for Galewing.", "Scales in place of frames, fins that glow like deep-sea things, a bone cuff."),
    "galewing": ("Galewing", "Strider", "Frames on every plate. It sat in a crowd of blue-grey suits. Its wings are rigid.", "Paler steel, feathered plates, wing edges in storm light. Later: wings that open when you fall or glide."),
    "halo_choir": ("Halo Choir", "Channeler", "Halo and wings, strong by day. The halo was dark at night.", "The halo lit, fluted porcelain. Later: a halo that tilts and breathes."),
    "dreamsteel": ("Dreamsteel", "Duelist", "The thinnest A set. The helm is a plain pale box, and its drifting shards don't drift.", "Shards that catch light, plates cut like mirror. Later: shards that really drift."),
    "sovereign": ("Sovereign Weave", "Lord-forged", "The dark faceplate and lilac crown you chose.", "The crown lit at night, and its line run on down the hand."),
    "starfall": ("Starfall", "Bulwark", "The star in the breast reads. The limbs are frames.", "Cut faces on the plates and a silver cuff. Later: seams that blaze on a hard landing."),
    "riftwalker": ("Riftwalker", "Strider", "The rift standing behind you is the idea, and it works. Your own shirt shows through a gap at each shoulder.", "A cuff at the wrist. Later: the shoulder gap closed, and a rift that flickers and tears wider when you dodge."),
    "nightcourt": ("Nightcourt", "Duelist", "Hat, mask and coat: nobody else looks like this. A thin build.", "A leather glove under its silver cuff. Later: tails and a plume that move."),
    "signals_echo": ("Signal's Echo", "Channeler", "White robes and the System's marker. The thinnest S set, and its glitch is still.", "The marker lit at night. Later: a marker that turns and robes that really glitch."),
}

W, H = 3416, 1858


def newest(prefix, tour):
    dirs = sorted(d for d in glob.glob(os.path.join(CI, f"{prefix}-{tour}-*")) if glob.glob(os.path.join(d, "*.png")))
    if not dirs:
        print(f"no shots yet for {prefix}-{tour}: that half stays empty")
        return "/nonexistent"
    return dirs[-1]


def font(size):
    for p in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def pair(before, after, box, size, out, labels=("TODAY", "AFTER (DRAFT)"), quality=84):
    """Today on the left, the draft on the right, the same crop of the same view."""
    w, h = size
    sheet = Image.new("RGB", (w * 2 + 6, h), (12, 11, 9))
    d = ImageDraw.Draw(sheet)
    f = font(max(13, h // 30))
    for k, path in enumerate((before, after)):
        if path and os.path.exists(path):
            im = Image.open(path).convert("RGB")
            if im.size != (W, H):  # a runner that fell back to another display: scale the crop box with it
                sx, sy = im.size[0] / W, im.size[1] / H
                b = (int(box[0] * sx), int(box[1] * sy), int(box[2] * sx), int(box[3] * sy))
            else:
                b = box
            sheet.paste(im.crop(b).resize((w, h), Image.LANCZOS), (k * (w + 6), 0))
        tw = d.textlength(labels[k], font=f)
        x0 = k * (w + 6) + 10
        d.rectangle((x0 - 6, 8, x0 + tw + 6, 8 + f.size + 10), fill=(12, 11, 9))
        d.text((x0, 12), labels[k], font=f, fill=(255, 122, 46) if k else (236, 228, 212))
    sheet.save(out, quality=quality, optimize=True)


# the crops (pixels of a 3416 x 1858 shot)
BOX = {
    "front": (1148, 400, 2268, 1800),      # third person from the front, fov 50: the whole figure and its tallest crest
    "back": (1148, 400, 2268, 1800),
    "fp": (1780, 880, 3416, 1858),         # the arm, lower right
    "status": (340, 150, 1300, 1660),      # the K menu's figure, with room for the tallest crest
    "stand": (540, 290, 3060, 1858),       # an armour stand and a Fitting Stand
}
SIZE = {"front": (400, 500), "back": (400, 500), "fp": (560, 335), "status": (360, 566), "stand": (700, 436)}


def main(argv):
    bp, ap = argv if len(argv) == 2 else ("before-armour-audit", "after-armour-audit")
    os.makedirs(IMG, exist_ok=True)
    made = 0
    for half, sets in (("worn-1", RANKS["E"] + RANKS["D"] + RANKS["C"]), ("worn-2", RANKS["B"] + RANKS["A"] + RANKS["S"])):
        b, a = newest(bp, half), newest(ap, half)
        for s in sets:
            for view in ("front", "back", "fp", "status"):
                pair(os.path.join(b, f"w_{s}_{view}.png"), os.path.join(a, f"w_{s}_{view}.png"), BOX[view], SIZE[view],
                     os.path.join(IMG, f"{s}_{view}.jpg"))
                made += 1
    b, a = newest(bp, "stands"), newest(ap, "stands")
    for s in SETS:
        pair(os.path.join(b, f"s_{s}.png"), os.path.join(a, f"s_{s}.png"), BOX["stand"], SIZE["stand"], os.path.join(IMG, f"{s}_stand.jpg"))
        made += 1
    b, a = newest(bp, "far"), newest(ap, "far")
    far = {"12": ((958, 672, 2458, 1309), (640, 272)), "24": ((1258, 690, 2158, 1072), (640, 272)), "48": ((1448, 830, 1968, 1051), (640, 272)),
           "12_night": ((958, 672, 2458, 1309), (640, 272)), "24_night": ((1258, 690, 2158, 1072), (640, 272)),
           "back_night": ((208, 583, 3208, 1858), (640, 272))}
    for r in RANKS:
        for name, (box, size) in far.items():
            pair(os.path.join(b, f"f_{r}_{name}.png"), os.path.join(a, f"f_{r}_{name}.png"), box, size, os.path.join(IMG, f"far_{r}_{name}.jpg"))
            made += 1
    for s in ("lordscale", "galewing"):
        for shot in ("rest", "cast_a", "cast_b", "cast_c", "cast_d"):
            pair(os.path.join(b, f"c_{s}_{shot}.png"), os.path.join(a, f"c_{s}_{shot}.png"), (1108, 560, 3416, 1858), (640, 360),
                 os.path.join(IMG, f"cast_{s}_{shot}.jpg"))
            made += 1
    data = {"ranks": RANKS, "sets": {k: {"name": v[0], "arch": v[1], "today": v[2], "gives": v[3]} for k, v in SETS.items()}}
    html = open(os.path.join(HERE, "template.html")).read().replace("/*DATA*/", json.dumps(data))
    with open(os.path.join(HERE, "index.html"), "w") as f:
        f.write(html)
    size = sum(os.path.getsize(p) for p in glob.glob(os.path.join(IMG, "*")))
    print(f"{made} pictures, {size / 1e6:.1f} MB; index.html written")


if __name__ == "__main__":
    main(sys.argv[1:])
