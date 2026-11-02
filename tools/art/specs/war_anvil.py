"""The War Anvil (docs/weapon-forge/PLAN.md §4.1, SPEC §46.1): a horned soot-black anvil on a block of carved dark stone.
In the stone's top before the anvil, a narrow slot lit dull orange from inside, where a finished weapon stands hilt up;
a water trough cut into the stone's left side (the smith's left, where the tongs carry the blade); an iron peg on its
right side, under the horn, where the hammer and tongs hang. Bold and plain, our voxel style like the weapons: the
hammer and tongs are sculpted like them (the weapons' painter and materials) and drawn through the weapon shader.

The model faces north (the smith stands to the north); the smith's right is west (x 0), their left east (x 16).

    python3 tools/art/build_all.py war_anvil          textures
    python3 tools/art/specs/war_anvil.py content      blockstate, model, item model, loot table, name, pickaxe tag
    python3 tools/art/specs/war_anvil.py tools        the hammer's, tongs' and stone hammer's meshes and textures
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
from pixelart import Canvas, ramp, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/arms")


# ---- textures --------------------------------------------------------------------------------------------------------

def stone_side():
    """Carved dark stone: courses of dressed blocks, a carved band of hammer marks round its middle."""
    st, sl = ramp("slate"), ramp("stone")
    rng = rng_for("war_anvil_stone_side")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            course = y // 4
            joint = y % 4 == 3 or (x + (course % 2) * 4) % 8 == 7
            k = 2 + (1 if rng.random() < 0.22 else 0) - (1 if rng.random() < 0.12 else 0)
            c.set(x, y, st[0] if joint else st[max(1, min(3, k))])
    for x in range(16):  # the carved band: a row of chisel marks, lit on their upper lip
        c.set(x, 6, sl[1] if x % 3 == 1 else st[1])
        c.set(x, 5, st[3] if x % 3 == 1 else c.get(x, 5))
    return c


def stone_top():
    """The stone's top: dressed flat, darker where it's been worked round, a soot ring under the anvil."""
    st = ramp("slate")
    rng = rng_for("war_anvil_stone_top")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            k = 2 + (1 if rng.random() < 0.2 else 0)
            if 4 <= y <= 14 and 2 <= x <= 13:
                k -= 1  # soot under the anvil
            c.set(x, y, st[3] if edge else st[max(0, k)])
    for x in range(4, 12):  # the slot's lip (its glow is the emissive element)
        c.set(x, 2, st[0])
        c.set(x, 4, st[0])
    return c


def slot_glow():
    """Inside the slot: embers, dull orange to a yellow core."""
    em = ramp("ember")
    rng = rng_for("war_anvil_slot")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 3 + (1 if (x + y) % 5 == 0 else 0) + (1 if rng.random() < 0.15 else 0)
            c.set(x, y, em[min(5, k)])
    return c


def iron():
    """Soot-black iron, hammered: dark with a cool sheen in the dents and a few bright scuffs."""
    ob, mi = ramp("stone"), ramp("mirror")
    rng = rng_for("war_anvil_iron")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dent = (x * 3 + y * 5) % 11 == 0
            k = (1 if rng.random() < 0.3 else 0) + (1 if dent else 0)
            c.set(x, y, ob[min(2, k)])
            if rng.random() < 0.03:
                c.set(x, y, mi[1])
    return c


def iron_face():
    """The anvil's face: worked bright down its middle by every blow, dark at its edges."""
    ob, mi = ramp("stone"), ramp("mirror")
    rng = rng_for("war_anvil_face")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = abs(y - 7.5) / 7.5
            if d < 0.55:
                k = 1 + (1 if rng.random() < 0.35 else 0) + (1 if d < 0.25 else 0)
                c.set(x, y, mi[min(3, k)])
            else:
                c.set(x, y, ob[1 + (1 if rng.random() < 0.25 else 0)])
    return c


def water():
    """The trough's water: dark, a glint of the room across it."""
    wa = ramp("water")
    rng = rng_for("war_anvil_water")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 1 + (1 if (x + 2 * y) % 9 == 0 else 0) + (1 if rng.random() < 0.08 else 0)
            c.set(x, y, wa[k])
    return c


# ---- the rebuilds (SPEC §46.6): Ironbound at C, the Deepglass horn at B, the Lord's anvil at A ---------------------------

DEEPGLASS = [(13, 7, 24, 255), (36, 19, 63, 255), (62, 36, 112, 255), (106, 70, 176, 255), (180, 140, 255, 255), (234, 220, 255, 255)]
NETHERITE = [(22, 17, 19, 255), (36, 29, 31, 255), (52, 42, 45, 255), (70, 58, 61, 255), (92, 78, 81, 255)]


def ironbound():
    """Re-laid iron: bright worked steel, cool, hammer-dimpled, a rivet row along its top and foot."""
    mi = ramp("mirror")
    rng = rng_for("war_anvil_ironbound")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dent = (x * 5 + y * 3) % 13 == 0
            k = 2 + (1 if rng.random() < 0.3 else 0) - (1 if dent else 0) - (1 if rng.random() < 0.08 else 0)
            c.set(x, y, mi[max(0, min(3, k))])
    for x in range(1, 16, 4):  # rivets: a bright head on a dark ring
        for y in (1, 14):
            c.set(x, y, mi[5])
            c.set(x + 1 if x < 15 else x, y, mi[1])
    return c


def ironbound_face():
    """The Ironbound face: a new plate, bright from edge to edge, the line of blows down its middle brightest."""
    mi = ramp("mirror")
    rng = rng_for("war_anvil_ironbound_face")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = abs(y - 7.5) / 7.5
            k = 3 + (1 if d < 0.3 else 0) + (1 if rng.random() < 0.25 else 0) - (1 if d > 0.85 else 0)
            c.set(x, y, mi[max(1, min(5, k))])
    return c


def band():
    """An iron strap round the stone, two texels deep (any two rows read as one): a lit upper row with a rivet head every
    four, a dark lower row with the rivet's shadow."""
    mi = ramp("mirror")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            top = y % 2 == 0
            c.set(x, y, (mi[5] if top else mi[0]) if x % 4 == 1 else mi[3] if top else mi[1])
    return c


def deepglass():
    """The horn re-forged in Deepglass: violet glass, dark in its depth, bright veins running its length."""
    rng = rng_for("war_anvil_deepglass")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            vein = (x + (y * 3) // 4) % 7 == 0 or (x * 2 + y) % 11 == 0
            k = 2 + (1 if rng.random() < 0.25 else 0) + (2 if vein else 0) - (1 if rng.random() < 0.15 else 0)
            c.set(x, y, DEEPGLASS[max(1, min(5, k))])
    return c


def netherite():
    """The Lord's anvil: netherite, near black and warm, its hammered dimples catching a little light."""
    rng = rng_for("war_anvil_netherite")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dent = (x * 3 + y * 7) % 12 == 0
            k = 2 + (1 if rng.random() < 0.3 else 0) + (1 if dent else 0) - (1 if rng.random() < 0.15 else 0)
            c.set(x, y, NETHERITE[max(0, min(4, k))])
    return c


def netherite_face():
    """The Lord's face: netherite worked to a dull shine, a gold line round its edge."""
    gd = ramp("brass")
    rng = rng_for("war_anvil_netherite_face")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = abs(y - 7.5) / 7.5
            k = 2 + (1 if d < 0.35 else 0) + (1 if rng.random() < 0.25 else 0)
            c.set(x, y, NETHERITE[min(4, k)])
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, gd[4] if (x + y) % 3 else gd[5])
    return c


def netherite_band():
    """The Lord's straps: netherite, gold rivets (two texels deep, as the iron's)."""
    gd = ramp("brass")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            top = y % 2 == 0
            c.set(x, y, (gd[5] if top else gd[1]) if x % 4 == 1 else NETHERITE[3] if top else NETHERITE[1])
    return c


def runes():
    """A band of cut runes lit gold from within, three texels deep (rows 0-2, then again down the texture): a glyph of
    strokes in each three-wide cell, a dark gap between."""
    rng = rng_for("war_anvil_runes")
    gd = ramp("brass")
    lit = (255, 244, 214, 255)
    c = Canvas()
    glyphs = [[rng.random() < 0.55 or (dx == 1 and r == 1) for r in range(3) for dx in range(3)] for _ in range(4)]
    for y in range(16):
        for x in range(16):
            cell, dx, r = x // 4, x % 4, y % 3
            on = dx < 3 and glyphs[cell][r * 3 + dx]
            c.set(x, y, (lit if (x + y) % 3 == 0 else gd[5]) if on else NETHERITE[0])
    return c


def heart():
    """The Lord's Heart set in the stone: a crimson stone in a gold setting, its middle bright."""
    cr, gd = ramp("crimson"), ramp("brass")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 6.5:
                c.set(x, y, gd[3] if d < 7.5 else gd[1])
            else:
                k = 5 - int(d / 1.4)
                c.set(x, y, cr[max(1, min(5, k))])
    return c


MAKERS = {"war_anvil_stone": stone_side, "war_anvil_stone_top": stone_top, "war_anvil_slot": slot_glow, "war_anvil_iron": iron,
          "war_anvil_face": iron_face, "war_anvil_water": water, "war_anvil_ironbound": ironbound,
          "war_anvil_ironbound_face": ironbound_face, "war_anvil_band": band, "war_anvil_deepglass": deepglass,
          "war_anvil_netherite": netherite, "war_anvil_netherite_face": netherite_face,
          "war_anvil_netherite_band": netherite_band, "war_anvil_runes": runes, "war_anvil_heart": heart}


def build(out):
    for name, f in MAKERS.items():
        out.block(name, f())


# ---- content ---------------------------------------------------------------------------------------------------------

SIDES = ("down", "up", "north", "south", "west", "east")
B = "awaken:block/war_anvil_"


def _uv(lo, hi, d, v=None):
    """The face's own texels (as Minecraft would map them), slid back inside the sprite where the element reaches past
    the block (the horn, the straps); {@code v} starts a side face at that row instead (a strap's or the runes' rows)."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    u = {"down": (x0, 16 - z1, x1, 16 - z0), "up": (x0, z0, x1, z1), "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
         "south": (x0, 16 - y1, x1, 16 - y0), "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0)}[d]
    w, h = min(16, u[2] - u[0]), min(16, u[3] - u[1])
    a, b = min(max(0, u[0]), 16 - w), min(max(0, u[1]), 16 - h)
    if v is not None and d not in ("up", "down"):
        b = v
    return [round(a, 3), round(b, 3), round(a + w, 3), round(b + h, 3)]


def el(lo, hi, tex, up=None, faces=SIDES, v=None, **extra):
    e = {"from": lo, "to": hi, "faces": {d: {"uv": _uv(lo, hi, d, v), "texture": (up if d == "up" and up else tex)} for d in faces}}
    e.update(extra)
    return e


#: the four builds' models (§46.6): as built, Ironbound, the Deepglass horn, the Lord's anvil
BUILDS = ("war_anvil", "war_anvil_ironbound", "war_anvil_deepglass", "war_anvil_lord")
SIDES4 = ("north", "south", "west", "east")


def model(build=0):
    horn = {"light_emission": 10} if build >= 2 else {}
    hn = "#horn"
    els = [
        # the stone, the trough cut into its left (east) side
        el([0, 0, 0], [13, 8, 16], "#stone", up="#stone_top"),
        el([13, 0, 0], [16, 3, 16], "#stone"),
        el([13, 3, 0], [16, 8, 2], "#stone", up="#stone_top"),
        el([13, 3, 14], [16, 8, 16], "#stone", up="#stone_top"),
        el([15, 3, 2], [16, 8, 14], "#stone", up="#stone_top"),
        el([13, 3, 2], [15, 6.5, 14], "#water", faces=("up",)),
        # the slot's embers, glowing (just above the stone's top so it reads)
        el([4, 8, 2.5], [12, 8.02, 3.5], "#slot", faces=("up",), light_emission=12, shade_direction_override="up"),
        # the anvil: foot, waist, the face with its heel, and the horn out to the smith's right (west)
        el([3, 8, 6], [13, 10, 14], "#iron"),
        el([5, 10, 7.5], [11, 12.5, 12.5], "#iron"),
        el([2, 12.5, 6], [14, 16, 13], "#iron", up="#face"),
        el([14, 13.5, 7], [15.5, 16, 12], "#iron", up="#face"),
        el([0, 13, 7.5], [2, 15.5, 11.5], hn, **horn),
        el([-2, 13.6, 8.25], [0, 15, 10.75], hn, **horn),
        el([-3.5, 14, 8.9], [-2, 14.8, 10.1], hn, **horn),
        # the peg on the right side, under the horn, where the hammer and tongs hang
        el([-1.5, 5.5, 7.5], [0, 6.5, 8.5], "#iron"),
    ]
    if build >= 1:
        # straps round the stone and its waist, iron brackets down its corners
        els += [
            el([-0.2, 0.5, -0.2], [16.2, 2.5, 16.2], "#band", faces=SIDES4, v=0),
            el([-0.2, 6, -0.2], [16.2, 8, 16.2], "#band", faces=SIDES4, v=0),
            el([4.7, 10.5, 7.2], [11.3, 12.5, 12.8], "#band", faces=SIDES4, v=0),
        ]
        for x0, z0 in ((-0.4, -0.4), (14.6, -0.4), (-0.4, 14.6), (14.6, 14.6)):
            els.append(el([x0, 0, z0], [x0 + 1.8, 8.3, z0 + 1.8], "#band", up="#iron", faces=("north", "south", "west", "east", "up")))
    if build >= 3:
        # the rune band lit from within, the Lord's Heart set in it before the smith
        els += [
            el([-0.3, 3, -0.3], [16.3, 6, 16.3], "#runes", faces=SIDES4, v=0, light_emission=9),
            el([6, 2.2, -0.8], [10, 6.2, 0], "#heart", light_emission=12),
        ]
    body = ("iron", "ironbound", "ironbound", "netherite")[build]
    face = ("face", "ironbound_face", "ironbound_face", "netherite_face")[build]
    tex = {"particle": B + body, "stone": B + "stone", "stone_top": B + "stone_top", "water": B + "water",
           "slot": B + "slot", "iron": B + body, "face": B + face, "horn": B + ("deepglass" if build >= 2 else body)}
    if build >= 1:
        tex["band"] = B + ("netherite_band" if build >= 3 else "band")
    if build >= 3:
        tex.update(runes=B + "runes", heart=B + "heart")
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    _write(path, cur)


def content():
    for b, name in enumerate(BUILDS):
        _write(os.path.join(ASSETS, f"models/block/{name}.json"), model(b))
    variants = {}
    for b, name in enumerate(BUILDS):
        for i, face in enumerate(("north", "east", "south", "west")):
            v = {"model": "awaken:block/" + name}
            if i:
                v["y"] = 90 * i
            variants[f"build={b},facing={face}"] = v
    _write(os.path.join(ASSETS, "blockstates/war_anvil.json"), {"variants": variants})
    # carried, it looks as it was built (its build rides on the item: the loot table copies it)
    _write(os.path.join(ASSETS, "items/war_anvil.json"), {"model": {
        "type": "minecraft:select", "block_state_property": "build",
        "cases": [{"when": str(b), "model": {"type": "minecraft:model", "model": "awaken:block/" + name}} for b, name in enumerate(BUILDS) if b],
        "fallback": {"type": "minecraft:model", "model": "awaken:block/war_anvil"}}})
    _write(os.path.join(RES, "data/awaken/loot_table/blocks/war_anvil.json"), {
        "type": "minecraft:block", "random_sequence": "awaken:blocks/war_anvil",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "awaken:war_anvil", "modifier": [
            {"type": "minecraft:copy_state", "block": "awaken:war_anvil", "properties": ["build"]}]}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": ["awaken:war_anvil"]})
    print("war_anvil: content written")


# ---- the hammer and tongs (the weapons' voxels, painter and materials) -------------------------------------------------

def tools():
    import numpy as np
    import arms_weapons as AW
    import mesh as MS
    import voxel as VX
    from PIL import Image

    M = AW.Mat
    AW.MATS.update({
        "anvil_iron": M("flint", 3, 0.75, 0.7, details=(AW.streak(period=5, run=3),)),
        "anvil_iron_face": M("mirror", 4, 0.85, 0.85),
        "anvil_ash": M("hide", 5, 0.0, 0.35, details=(AW.rows(3, -1),)),
        "anvil_wrap": M("pelt", 3, 0.0, 0.2, details=(AW.rows(2, -2),)),
        "anvil_rivet": M("bronze", 5, 0.4, 0.8),
    })
    # design space: texels, y up the handle from the hand, x across, z through
    hammer = [
        VX.box(-1, 1, -6, 10, -1, 1, "anvil_ash", "haft"),
        VX.box(-1.5, 1.5, -5, -1, -1.5, 1.5, "anvil_wrap", "grip"),
        VX.box(-3.5, 3.5, 9, 12, -1.5, 1.5, "anvil_iron", "head"),
        VX.box(3, 5, 8.5, 12.5, -2, 2, "anvil_iron_face", "face"),
        VX.box(-5, -3, 9.5, 11.5, -1, 1, "anvil_iron", "pein"),
        VX.box(-0.5, 0.5, 12, 13, -1, 1, "anvil_rivet", "wedge"),
    ]
    tongs = [
        VX.box(-2, -1, -15, -2, -0.5, 0.5, "anvil_iron", "handle"),
        VX.box(1, 2, -15, -2, -0.5, 0.5, "anvil_iron", "handle"),
        VX.box(-1.5, 1.5, -3, 0, -1, 1, "anvil_iron", "boss"),
        VX.box(-0.5, 0.5, -2, -1, -1.5, 1.5, "anvil_rivet", "rivet"),
        VX.box(-1.5, -0.5, 0, 4, -0.5, 0.5, "anvil_iron", "jaw"),
        VX.box(0.5, 1.5, 0, 4, -0.5, 0.5, "anvil_iron", "jaw"),
        VX.box(-1.5, 1.5, 4, 5, -0.5, 0.5, "anvil_iron_face", "lips"),
    ]
    # E's stone hammer for the knap (SPEC §46.7): a knapped flint head lashed with sinew across an antler haft, its
    # striking end where the iron hammer's face is, so the same swing lands it on the flake
    stone = [
        VX.box(-1, 1, -6, 10, -1, 1, "antler", "haft"),
        VX.box(-1.5, 1.5, -5, -1, -1.5, 1.5, "sinew", "grip"),
        VX.box(-2.5, 4.5, 8.5, 12.5, -1.5, 1.5, "flint", "head"),
        VX.box(3, 5, 9, 12, -1, 1, "flint_edge", "face"),
        VX.box(-1.5, 1.5, 7.5, 12.5, -2, 2, "sinew", "lashing"),
    ]
    same = lambda P, direction=False: np.asarray(P, dtype=float)  # noqa: E731  (kept in design texels; the game places them)
    out_dir = os.path.join(ASSETS, "arms/anvil")
    tex_dir = os.path.join(ASSETS, "textures/item/arms")
    os.makedirs(out_dir, exist_ok=True)
    for name, boxes in (("hammer", hammer), ("tongs", tongs), ("stone_hammer", stone)):
        built = VX.build(boxes, AW.painter(), same, width=32)
        built.streams = []
        MS.write(os.path.join(out_dir, name + ".mesh"), built, (150, 160, 175))
        Image.fromarray(built.texture(), "RGBA").save(os.path.join(tex_dir, "anvil_" + name + ".png"))
    print("war_anvil: hammer, tongs and stone hammer written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "tools":
        tools()
    else:
        print(__doc__)
