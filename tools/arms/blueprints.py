"""The Blueprints' art and names (SPEC §25 "Blueprints"): each style's forged silhouette (its Wildhide icon) drawn
in white line on blueprint paper, 32x32 like the weapons' icons; the item JSON, the model and the lang names.
Run after the weapons are generated: python3 tools/arms/blueprints.py
"""
import json
import pathlib

from PIL import Image, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
LANG = ROOT / "src/main/merge/arms/assets/awaken/lang/en_us.json"
TEX = RES / "assets/awaken/textures/item/arms"
STYLES = {"axe": "Axe", "maul": "Maul", "spear": "Spear", "greatblade": "Greatblade", "rapier": "Rapier", "daggers": "Daggers",
          "katana": "Katana", "twinblades": "Twinblades", "glaive": "Glaive", "scythe": "Scythe", "gauntlets": "Gauntlets",
          "chainblade": "Chainblade", "shieldblade": "Shieldblade", "warstaff": "Warstaff", "precursor": "Precursor Arm"}
PAPER, PAPER_EDGE, GRID, LINE, FOLD = (38, 84, 150, 255), (20, 48, 98, 255), (58, 108, 178, 255), (236, 246, 255, 255), (28, 66, 124, 255)


def paper():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(3, 30):
        for x in range(2, 30):
            edge = x in (2, 29) or y in (3, 29)
            grid = (x - 2) % 6 == 0 or (y - 3) % 6 == 0
            px[x, y] = PAPER_EDGE if edge else GRID if grid else PAPER
    for y in range(3, 30):  # a fold down the middle, and a curled corner
        px[16, y] = FOLD if px[16, y] != PAPER_EDGE else PAPER_EDGE
    for i in range(4):
        for j in range(4 - i):
            px[29 - j, 3 + i] = (0, 0, 0, 0) if j < 3 - i else PAPER_EDGE
    return im


def drawing(style):
    icon = Image.open(TEX / f"wildhide_{style}_icon.png").convert("RGBA")
    a = icon.getchannel("A")
    inside = a.point(lambda v: 255 if v > 40 else 0)
    outline = Image.eval(inside.filter(ImageFilter.MaxFilter(3)), lambda v: v) 
    # the line: the silhouette's edge only (the shape grown by one, less the shape), a drafting drawing
    ring = Image.composite(outline, Image.new("L", a.size, 0), Image.eval(inside, lambda v: 255 - v))
    out = paper()
    px, rp, ip = out.load(), ring.load(), inside.load()
    for y in range(4, 29):
        for x in range(3, 29):
            if rp[x, y]:
                px[x, y] = LINE
            elif ip[x, y] and (x + y) % 3 == 0:
                px[x, y] = (150, 196, 240, 255)  # hatching inside
    return out


def main():
    lang = json.loads(LANG.read_text()) if LANG.exists() else {}
    for style, name in STYLES.items():
        iid = f"{style}_blueprint"
        drawing(style).save(TEX / f"{iid}.png")
        (RES / f"assets/awaken/models/item/{iid}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"awaken:item/arms/{iid}"}}, indent=1) + "\n")
        (RES / f"assets/awaken/items/{iid}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"awaken:item/{iid}"}}, indent=1) + "\n")
        lang[f"item.awaken.{iid}"] = f"{name} Blueprint"
    LANG.write_text(json.dumps(dict(sorted(lang.items())), indent=2, ensure_ascii=False) + "\n")
    print(f"{len(STYLES)} blueprints")


if __name__ == "__main__":
    main()
