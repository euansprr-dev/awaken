"""Draws the polish plan's draft shape for the Inscribing Table beside today's (docs/enchanting/POLISH.md), with
tools/art/block_preview.py's own renderer and the textures the table already has. A draft of the silhouette only: the
real model, its textures and its book come from the art pipeline when the plan is built.

    python3 docs/enchanting/polish/mock/desk_preview.py OUT.png
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "../../../../tools/art")))
import block_preview as BP  # noqa: E402

TEX = {"top": "awaken:block/inscribing_table_top", "side": "awaken:block/inscribing_table_side",
       "front": "awaken:block/inscribing_table_front", "trim": "awaken:block/inscribing_table_trim",
       "brass": "awaken:block/inscribing_table_brass", "wood": "minecraft:block/dark_oak_planks",
       "ink": "minecraft:block/polished_blackstone", "lapis": "minecraft:block/lapis_block",
       "paper": "minecraft:block/birch_planks", "leather": "minecraft:block/blue_terracotta",
       "steel": "minecraft:block/iron_block"}


def box(frm, to, tex, lit=False, **faces):
    f = {d: {"texture": "#" + faces.get(d, tex)} for d in ("north", "south", "east", "west", "up", "down")}
    e = {"from": list(frm), "to": list(to), "faces": f}
    if lit:
        e["light_emission"] = 12
    return e


def book(x0, y, z0, w=4.0, d=4.5):
    """An open book lying on a rest: leather boards under two paper leaves."""
    return [box((x0 - 0.3, y, z0 - 0.3), (x0 + 2 * w + 0.3, y + 0.4, z0 + d + 0.3), "leather"),
            box((x0, y + 0.4, z0), (x0 + w - 0.15, y + 1.0, z0 + d), "paper"),
            box((x0 + w + 0.15, y + 0.4, z0), (x0 + 2 * w, y + 1.0, z0 + d), "paper")]


TODAY = [box((1, 0, 1), (15, 2, 15), "trim"), box((2, 2, 2), (14, 11, 14), "side", north="front"),
         box((0, 11, 0), (16, 13, 16), "trim", up="top"), box((6, 13, 12), (10, 13.5, 15), "brass"),
         box((7.5, 13.5, 13), (8.5, 14.5, 14), "brass")] + book(4, 17.5, 10.5)

DESK = [
    # two trestle ends and a stretcher: a desk you stand at, not a plinth
    box((1, 0, 2), (3, 10, 14), "side"), box((13, 0, 2), (15, 10, 14), "side"),
    box((0.5, 0, 1.5), (3.5, 1.5, 14.5), "trim"), box((12.5, 0, 1.5), (15.5, 1.5, 14.5), "trim"),
    box((3, 4, 7), (13, 6, 9), "wood"),
    # the apron with its drawer and brass pull, toward whoever stands at it
    box((3, 7.5, 1.5), (13, 10, 3), "side", north="front"), box((7.2, 8.2, 1), (8.8, 9.2, 1.5), "brass"),
    # the slate, its lapis channel, a rail at the back and two cheeks
    box((0, 10, 0), (16, 12, 16), "trim", up="top"),
    box((0, 12, 14), (16, 14.5, 16), "side"), box((0, 12, 9), (1, 13.5, 14), "side"), box((15, 12, 9), (16, 13.5, 14), "side"),
    box((0, 14.5, 14), (16, 15, 16), "brass"),
    # the book open on a brass rest at the back, on the writer's left (high x: you stand at the north side)
    box((11.6, 15, 14.4), (12.4, 17, 15.2), "brass"), box((6.8, 17, 11.2), (14.8, 17.4, 16), "brass"),
] + book(7.1, 17.4, 11.4, 3.7, 4.2) + [
    # the inkwell of lapis at the back on the writer's right, the burin standing in it
    box((1.4, 12, 10.8), (4.8, 14.2, 14), "ink"), box((2, 14.2, 11.4), (4.2, 14.3, 13.4), "lapis", lit=True),
    box((2.8, 14.3, 12.1), (3.4, 19.5, 12.7), "steel"), box((2.6, 18.2, 11.9), (3.6, 20.2, 12.9), "wood"),
]


def quads(elements):
    out = []
    for e in elements:
        lit = e.get("light_emission", 0) > 0
        for d, f in e["faces"].items():
            pts = BP.face_quad(e["from"], e["to"], d)
            out.append((pts, [0, 0, 16, 16], BP.texture(BP.resolve(TEX, f["texture"])), 1.0 if lit else BP.SHADE[d], lit, BP.NORMAL[d]))
    return out


def backdrop(out, pitch=55.0, S=24.5, W=854, H=480, burin=True, rise=0.665):
    """The draft desk from where you stand at it, looking down: the mock-up's stage (orthographic)."""
    els = DESK if burin else DESK[:-2]
    im = BP.render(quads(els), 180, pitch, False, S=S, W=W, H=H * 2)
    # the renderer centres the model low in its frame: cut the stage out round the slate
    top = int(H * 2 * 0.62 - H * rise)
    im.crop((0, top, W, top + H)).save(out)
    print(out)


if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[2] == "stage":
    backdrop(sys.argv[1], burin=False)
elif __name__ == "__main__":
    views = [(215, 28, "from the front"), (150, 28, "from the other side"), (180, 62, "from above")]
    tw, th = 300, 330
    sheet = Image.new("RGB", (len(views) * tw * 2 + 20, th + 22), BP.BG)
    d = ImageDraw.Draw(sheet)
    for j, (els, name) in enumerate(((TODAY, "today"), (DESK, "draft"))):
        qs = quads(els)
        for i, (yaw, pitch, label) in enumerate(views):
            im = BP.render(qs, yaw, pitch, False, S=11, W=tw, H=th)
            x = j * (len(views) * tw + 20) + i * tw
            sheet.paste(im, (x, 0))
            d.text((x + 6, th + 4), name + ", " + label, fill=(200, 205, 215))
    sheet.save(sys.argv[1])
    print(sys.argv[1], sheet.size)
