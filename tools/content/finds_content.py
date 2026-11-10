"""Generates the finds' prop kit (SPEC §58.8): the small blocks a find is dressed and worked with. For each prop it writes
the block models (one per state), the blockstate, the client item, the loot table and the lang line, and it writes
`src/main/java/dev/awaken/finds/kit/Props.java` so the game registers exactly what this file lists. Textures are vanilla's,
referenced by name; nothing of Mojang's is copied. Also writes the finds' tier loot tables and the `finds/heavy` item tag.
Run: python3 tools/content/finds_content.py (rewrites the files; a second run changes nothing).

A prop is one block with a horizontal facing and a `state` of 0..3. What a state means is the prop's own business (a lamp
cold or lit, a cairn one to four stones high, a dial's quarter turn). Sizes are in sixteenths of a block; a model may run
from -16 to 32 on any axis, so a tall figure is still one block.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = f"{RES}/assets/awaken"
DATA = f"{RES}/data/awaken"
MERGE = os.path.join(ROOT, "src/main/merge/finds")
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/finds/kit/Props.java")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")


def mc(n):
    return "minecraft:" + n


def aw(n):
    return "awaken:" + n


# texture keys used by the models below: key -> vanilla block texture
TEX = {
    "stone": "andesite", "cobble": "cobblestone", "mossy": "mossy_cobblestone", "smooth": "smooth_stone", "dark": "deepslate",
    "tuff": "tuff", "pale": "calcite", "wood": "spruce_log", "plank": "spruce_planks", "darkwood": "dark_oak_planks",
    "strip": "stripped_spruce_log", "iron": "anvil", "rust": "exposed_copper", "bronze": "weathered_copper", "verdigris": "oxidized_copper",
    "brass": "cut_copper", "rope": "stripped_oak_log", "straw": "hay_block_side", "paper": "birch_planks", "glow": "ochre_froglight_side",
    "flame": "shroomlight", "bone": "bone_block_side", "red": "red_wool", "blue": "light_blue_wool", "white": "white_wool",
    "gold": "gold_block", "gourd": "pumpkin_side", "ash": "gray_concrete_powder", "cloth": "brown_wool", "coal": "coal_block",
    "glass": "tinted_glass", "water": "light_blue_concrete", "leaf": "moss_block", "clay": "terracotta", "shell": "smooth_quartz",
}


def b(x0, y0, z0, x1, y1, z1, tex, rot=None):
    """A box. `rot` is (axis, angle, origin) with the angle a multiple of 22.5."""
    faces = {f: {"texture": "#" + tex} for f in ("down", "up", "north", "south", "west", "east")}
    el = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}
    if rot:
        el["rotation"] = {"axis": rot[0], "angle": rot[1], "origin": list(rot[2])}
    return el


def stack(n):
    """One to four flat stones, each a little smaller and turned against the last."""
    sizes = [(2, 14, 3, 13), (3, 13, 2, 12), (4, 12, 4, 13), (5, 11, 5, 11)]
    turns = [0, 22.5, -22.5, 22.5]
    out, y = [], 0
    for i in range(n):
        x0, x1, z0, z1 = sizes[i]
        h = 4 if i < 2 else 3.5
        out.append(b(x0, y, z0, x1, y + h, z1, "stone" if i % 2 == 0 else "cobble", ("y", turns[i], (8, y, 8)) if turns[i] else None))
        y += h
    return out


def figure(pose):
    """A stone person, two blocks tall, from a dozen boxes. Poses: stand, reach, sit, down (fallen), child."""
    s = "smooth"
    if pose == "down":
        return [b(3, 0, -10, 13, 5, 2, s), b(4, 0, 2, 12, 5, 14, s), b(5, 0, 14, 11, 6, 20, "stone"), b(1, 0, 3, 4, 3, 13, s), b(12, 0, 3, 15, 3, 13, s)]
    if pose == "sit":
        return [b(4, 0, 4, 12, 4, 14, s), b(4, 4, 8, 12, 14, 13, s), b(5, 14, 7.5, 11, 20, 13.5, "stone"), b(4.5, 1, -2, 7.5, 4, 5, s), b(8.5, 1, -2, 11.5, 4, 5, s),
                b(2, 6, 3, 4, 9, 11, s), b(12, 6, 3, 14, 9, 11, s)]
    if pose == "child":
        return [b(5.5, 0, 6.5, 7.5, 7, 9.5, s), b(8.5, 0, 6.5, 10.5, 7, 9.5, s), b(5, 7, 6, 11, 14, 10, s), b(5.5, 14, 5.5, 10.5, 19, 10.5, "stone"),
                b(3.5, 8, 7, 5, 13, 9, s), b(11, 12, 7, 12.5, 21, 9, s)]
    legs = [b(4.5, 0, 6, 7.5, 12, 10, s), b(8.5, 0, 6, 11.5, 12, 10, s)]
    body = [b(4, 12, 5.5, 12, 24, 10.5, s), b(5, 24, 5, 11, 30, 11, "stone")]
    if pose == "reach":
        arms = [b(2, 17, 6.5, 4, 20, 14, s), b(12, 17, 6.5, 14, 20, 14, s)]
    else:
        arms = [b(2, 13, 6.5, 4, 23, 9.5, s), b(12, 13, 6.5, 14, 23, 9.5, s)]
    return legs + body + arms


def lamp(lit, cracked=False):
    core = "flame" if lit else ("coal" if not cracked else "ash")
    return [b(6, 0, 6, 10, 1, 10, "iron"), b(6.5, 1, 6.5, 9.5, 7, 9.5, core), b(6, 1, 6, 6.6, 7, 6.6, "iron"), b(9.4, 1, 6, 10, 7, 6.6, "iron"),
            b(6, 1, 9.4, 6.6, 7, 10, "iron"), b(9.4, 1, 9.4, 10, 7, 10, "iron"), b(5.5, 7, 5.5, 10.5, 8.5, 10.5, "iron"), b(7, 8.5, 7, 9, 10, 9, "iron")]


def post(lean):
    rot = None if lean == 0 else ("x", 22.5 if lean == 1 else -22.5, (8, 0, 8))
    if lean == 3:
        return [b(6, 0, 6, 10, 6, 10, "wood"), b(2, 0, 11, 14, 3, 14, "wood", ("y", 22.5, (8, 0, 12)))]
    return [b(6, 0, 6, 10, 16, 10, "wood", rot)]


def dial(turn, tex="bronze", mark="gold"):
    """A flat disc with one raised mark that goes round with the state."""
    pos = [(7, 2, 9, 5), (11, 7, 14, 9), (7, 11, 9, 14), (2, 7, 5, 9)][turn]
    return [b(6, 0, 6, 10, 9, 10, "stone"), b(2, 9, 2, 14, 11, 14, tex), b(pos[0], 11, pos[1], pos[2], 12, pos[3], mark), b(7, 11, 7, 9, 13, 9, tex)]


# name: (display, [models per state], shape (x0,y0,z0,x1,y1,z1), solid, lit from state (or None), light, heavy)
PROPS = {}


def prop(name, display, states, shape, solid=True, lit=None, light=0, heavy=False):
    PROPS[name] = (display, states, shape, solid, lit, light, heavy)


prop("flat_stones", "Flat Stones", [stack(1), stack(2), stack(3), stack(4)], (2, 0, 2, 14, 15, 14))
prop("cairn", "Cairn", [
    [b(1, 0, 1, 15, 6, 15, "cobble"), b(2, 6, 3, 14, 11, 13, "stone", ("y", 22.5, (8, 6, 8))), b(3, 11, 3, 13, 16, 13, "cobble")],
    [b(1, 0, 1, 15, 6, 15, "mossy"), b(2, 6, 3, 14, 11, 13, "stone", ("y", 22.5, (8, 6, 8))), b(3, 11, 3, 13, 16, 13, "mossy")],
    [b(1, 0, 2, 15, 5, 14, "cobble"), b(0, 0, 9, 7, 4, 16, "stone", ("y", 22.5, (4, 0, 12))), b(9, 0, 0, 16, 3, 6, "stone")],
    [b(1, 0, 1, 15, 6, 15, "cobble"), b(2, 6, 3, 14, 11, 13, "stone", ("y", -22.5, (8, 6, 8))), b(3, 11, 3, 13, 16, 13, "tuff")]],
     (1, 0, 1, 15, 16, 15))
prop("standing_stone", "Standing Stone", [
    [b(4, 0, 5, 12, 20, 11, "stone"), b(5, 20, 6, 11, 24, 10, "stone")],
    [b(4, 0, 5, 12, 20, 11, "mossy"), b(5, 20, 6, 11, 24, 10, "stone")],
    [b(4, 0, 5, 12, 20, 11, "stone"), b(5, 20, 6, 11, 24, 10, "stone"), b(6, 8, 4.6, 10, 16, 5, "dark"), b(7, 10, 4.4, 9, 14, 4.6, "gold")],
    [b(4, 0, 5, 12, 12, 11, "stone"), b(4, 12, 5, 6, 18, 11, "stone"), b(10, 12, 5, 12, 18, 11, "stone"), b(4, 18, 5, 12, 22, 11, "stone")]],
     (4, 0, 5, 12, 16, 11))
prop("milestone", "Milestone", [
    [b(4, 0, 5, 12, 11, 11, "smooth"), b(5, 11, 6, 11, 13, 10, "smooth"), b(7, 5, 4.6, 9, 9, 5, "dark")],
    [b(4, 0, 5, 12, 11, 11, "smooth", ("z", 22.5, (8, 0, 8))), b(7, 5, 4.6, 9, 9, 5, "dark", ("z", 22.5, (8, 0, 8)))],
    [b(4, 0, 5, 12, 11, 11, "mossy"), b(5, 11, 6, 11, 13, 10, "smooth"), b(6, 5, 4.6, 10, 6, 5, "dark"), b(6, 8, 4.6, 10, 9, 5, "dark")],
    [b(4, 0, 5, 12, 11, 11, "smooth"), b(5, 11, 6, 11, 13, 10, "smooth"), b(7.5, 13, 7.5, 8.5, 14, 8.5, "brass")]],
     (4, 0, 5, 12, 13, 11))
prop("footing", "Footing Stone", [
    [b(1, 0, 1, 15, 6, 15, "smooth"), b(3, 6, 3, 13, 9, 13, "stone")],
    [b(1, 0, 1, 15, 6, 15, "smooth"), b(3, 6, 3, 13, 9, 13, "stone"), b(4, 1, -5, 12, 5, 2, "plank"), b(5, 5, -4, 11, 5.5, 1, "darkwood")],
    [b(1, 0, 1, 15, 6, 15, "mossy"), b(3, 6, 3, 13, 9, 13, "stone")],
    [b(1, 0, 1, 15, 6, 15, "smooth"), b(3, 6, 3, 13, 9, 13, "stone")]],
     (1, 0, 1, 15, 9, 15))
prop("stone_bowl", "Stone Bowl", [
    [b(3, 0, 3, 13, 2, 13, "stone"), b(3, 2, 3, 13, 5, 5, "stone"), b(3, 2, 11, 13, 5, 13, "stone"), b(3, 2, 5, 5, 5, 11, "stone"), b(11, 2, 5, 13, 5, 11, "stone")],
    [b(3, 0, 3, 13, 2, 13, "stone"), b(3, 2, 3, 13, 5, 5, "stone"), b(3, 2, 11, 13, 5, 13, "stone"), b(3, 2, 5, 5, 5, 11, "stone"), b(11, 2, 5, 13, 5, 11, "stone"),
     b(6, 2, 6, 10, 3.5, 10, "clay")],
    [b(3, 0, 3, 13, 2, 13, "stone"), b(3, 2, 3, 13, 5, 5, "stone"), b(3, 2, 11, 13, 5, 13, "stone"), b(3, 2, 5, 5, 5, 11, "stone"), b(11, 2, 5, 13, 5, 11, "stone"),
     b(6.5, 2, 6.5, 9.5, 5, 9.5, "gold")],
    [b(3, 0, 3, 13, 2, 13, "mossy"), b(3, 2, 3, 13, 5, 5, "mossy"), b(3, 2, 11, 13, 5, 13, "mossy"), b(3, 2, 5, 5, 5, 11, "mossy"), b(11, 2, 5, 13, 5, 11, "mossy")]],
     (3, 0, 3, 13, 5, 13))
prop("stone_basin", "Stone Basin", [
    [b(0, 0, 0, 16, 3, 16, "smooth"), b(0, 3, 0, 16, 9, 2, "smooth"), b(0, 3, 14, 16, 9, 16, "smooth"), b(0, 3, 2, 2, 9, 14, "smooth"), b(14, 3, 2, 16, 9, 14, "smooth")],
    [b(0, 0, 0, 16, 3, 16, "smooth"), b(0, 3, 0, 16, 9, 2, "smooth"), b(0, 3, 14, 16, 9, 16, "smooth"), b(0, 3, 2, 2, 9, 14, "smooth"), b(14, 3, 2, 16, 9, 14, "smooth"),
     b(2, 3, 2, 14, 5, 14, "water")],
    [b(0, 0, 0, 16, 3, 16, "smooth"), b(0, 3, 0, 16, 9, 2, "smooth"), b(0, 3, 14, 16, 9, 16, "smooth"), b(0, 3, 2, 2, 9, 14, "smooth"), b(14, 3, 2, 16, 9, 14, "smooth"),
     b(2, 3, 2, 14, 8, 14, "water")],
    [b(0, 0, 0, 16, 3, 16, "mossy"), b(0, 3, 0, 16, 9, 2, "mossy"), b(0, 3, 14, 16, 9, 16, "mossy"), b(0, 3, 2, 2, 9, 14, "mossy"), b(14, 3, 2, 16, 9, 14, "mossy")]],
     (0, 0, 0, 16, 9, 16))
prop("cap_stone", "Capstone", [
    [b(0, 0, 0, 16, 4, 16, "smooth"), b(2, 4, 2, 14, 5, 14, "stone")],
    [b(7, 0, 0, 23, 4, 16, "smooth", ("y", 22.5, (8, 0, 8)))],
    [b(0, 0, 0, 16, 4, 16, "mossy"), b(2, 4, 2, 14, 5, 14, "stone")],
    [b(0, 0, 0, 7, 4, 16, "smooth"), b(9, 0, 0, 16, 3, 16, "smooth", ("z", -22.5, (9, 0, 8)))]],
     (0, 0, 0, 16, 4, 16))
prop("post", "Old Post", [post(0), post(1), post(2), post(3)], (6, 0, 6, 10, 16, 10))
prop("iron_lamp", "Iron Lamp", [lamp(False), lamp(True), lamp(False, True), lamp(True)], (5, 0, 5, 11, 10, 11), lit=1, light=13)
prop("paper_lantern", "Paper Lantern", [
    [b(7.5, 13, 7.5, 8.5, 16, 8.5, "rope"), b(5, 3, 5, 11, 13, 11, "paper"), b(6, 2, 6, 10, 3, 10, "darkwood"), b(6, 13, 6, 10, 14, 10, "darkwood"), b(9, 6, 4.8, 11, 9, 5, "cloth")],
    [b(7.5, 13, 7.5, 8.5, 16, 8.5, "rope"), b(5, 3, 5, 11, 13, 11, "glow"), b(6, 2, 6, 10, 3, 10, "darkwood"), b(6, 13, 6, 10, 14, 10, "darkwood"), b(9, 6, 4.8, 11, 9, 5, "cloth")]] * 2,
     (5, 2, 5, 11, 16, 11), solid=False, lit=1, light=12)
prop("fire_basket", "Fire Basket", [
    [b(2, 0, 2, 14, 2, 14, "iron"), b(2, 2, 2, 3, 10, 14, "iron"), b(13, 2, 2, 14, 10, 14, "iron"), b(3, 2, 2, 13, 10, 3, "iron"), b(3, 2, 13, 13, 10, 14, "iron"),
     b(4, 2, 4, 12, 6, 7, "wood"), b(4, 2, 9, 12, 6, 12, "wood"), b(5, 6, 4, 8, 9, 12, "wood")],
    [b(2, 0, 2, 14, 2, 14, "iron"), b(2, 2, 2, 3, 10, 14, "iron"), b(13, 2, 2, 14, 10, 14, "iron"), b(3, 2, 2, 13, 10, 3, "iron"), b(3, 2, 13, 13, 10, 14, "iron"),
     b(4, 2, 4, 12, 6, 12, "coal"), b(5, 6, 5, 11, 13, 11, "flame"), b(6.5, 13, 6.5, 9.5, 17, 9.5, "flame")]] * 2,
     (2, 0, 2, 14, 10, 14), lit=1, light=15)
prop("rope_peg", "Rope Peg", [
    [b(7, 0, 7, 9, 7, 9, "wood"), b(6.5, 4, 6.5, 9.5, 5.5, 9.5, "rope"), b(7.5, 5, 7.5, 8.5, 16, 8.5, "rope", ("x", -22.5, (8, 5, 8)))],
    [b(7, 0, 7, 9, 7, 9, "wood"), b(3, 0, 10, 12, 1.5, 12, "rope", ("y", 22.5, (8, 0, 11)))]] * 2,
     (6, 0, 6, 10, 7, 10), solid=False)
prop("rope_length", "Hanging Rope", [
    [b(7.5, 0, 7.5, 8.5, 16, 8.5, "rope")],
    [b(7.5, 6, 7.5, 8.5, 16, 8.5, "rope"), b(7, 4, 7, 9, 6, 9, "rope")],
    [b(7.5, 0, 7.5, 8.5, 16, 8.5, "rope"), b(6, 6, 6, 10, 9, 10, "bronze")],
    [b(7.5, 0, 7.5, 8.5, 16, 8.5, "verdigris")]],
     (6.5, 0, 6.5, 9.5, 16, 9.5), solid=False)
prop("rope_coil", "Rope Coil", [
    [b(3, 0, 3, 13, 2, 13, "rope"), b(4, 2, 4, 12, 4, 12, "rope"), b(6, 4, 6, 10, 5, 10, "rope")]] * 4, (3, 0, 3, 13, 5, 13), solid=False)
prop("bundle", "Wrapped Bundle", [
    [b(4, 2, 4, 12, 12, 12, "cloth"), b(3.5, 6, 3.5, 12.5, 7.5, 12.5, "rope"), b(7.5, 12, 7.5, 8.5, 16, 8.5, "rope")],
    [b(2, 0, 3, 14, 2, 13, "cloth"), b(5, 2, 6, 9, 4, 10, "straw"), b(10, 0, 2, 13, 3, 5, "clay")],
    [b(4, 0, 4, 12, 10, 12, "cloth"), b(3.5, 4, 3.5, 12.5, 5.5, 12.5, "rope")],
    [b(4, 0, 4, 12, 10, 12, "cloth"), b(5, 10, 5, 11, 11, 11, "gold")]],
     (4, 0, 4, 12, 12, 12), solid=False)
prop("basket", "Woven Basket", [
    [b(3, 0, 3, 13, 1, 13, "straw"), b(3, 1, 3, 13, 8, 4, "straw"), b(3, 1, 12, 13, 8, 13, "straw"), b(3, 1, 4, 4, 8, 12, "straw"), b(12, 1, 4, 13, 8, 12, "straw")],
    [b(3, 0, 3, 13, 1, 13, "straw"), b(3, 1, 3, 13, 8, 4, "straw"), b(3, 1, 12, 13, 8, 13, "straw"), b(3, 1, 4, 4, 8, 12, "straw"), b(12, 1, 4, 13, 8, 12, "straw"),
     b(4, 1, 4, 12, 7, 12, "gourd")]] * 2,
     (3, 0, 3, 13, 8, 13))
prop("cart_wheel", "Cart Wheel", [
    [b(7, 0, 1, 9, 14, 3, "wood"), b(7, 0, 13, 9, 14, 15, "wood"), b(7, 0, 3, 9, 2, 13, "wood"), b(7, 12, 3, 9, 14, 13, "wood"), b(7.3, 6, 3, 8.7, 8, 13, "darkwood"), b(7.3, 2, 7, 8.7, 12, 9, "darkwood")],
    [b(1, 0, 1, 15, 2, 3, "wood"), b(1, 0, 13, 15, 2, 15, "wood"), b(1, 0, 3, 3, 2, 13, "wood"), b(6, 0, 3, 8, 1.4, 13, "darkwood")]] * 2,
     (5, 0, 1, 11, 14, 15), solid=False)
prop("signpost", "Signpost", [
    [b(7, 0, 7, 9, 22, 9, "wood")],
    [b(7, 0, 7, 9, 22, 9, "wood"), b(9, 17, 7.5, 19, 20, 8.5, "plank")],
    [b(7, 0, 7, 9, 22, 9, "wood"), b(9, 17, 7.5, 19, 20, 8.5, "plank"), b(-3, 13, 7.5, 7, 16, 8.5, "plank")],
    [b(7, 0, 7, 9, 22, 9, "wood"), b(9, 17, 7.5, 19, 20, 8.5, "plank"), b(-3, 13, 7.5, 7, 16, 8.5, "plank"), b(7.5, 9, 9, 8.5, 12, 19, "plank")]],
     (6, 0, 6, 10, 16, 10))
prop("sign_arm", "Signpost Arm", [
    [b(3, 0, 6, 14, 1.2, 9, "plank"), b(13, 0, 6.8, 15, 1.2, 8.2, "plank")],
    [b(3, 0, 6, 14, 1.2, 9, "plank", ("y", 22.5, (8, 0, 8))), b(6, 1.2, 7, 8, 1.6, 8, "dark")],
    [b(3, 0, 6, 14, 1.2, 9, "plank", ("y", -22.5, (8, 0, 8))), b(9, 1.2, 7, 11, 1.6, 8, "red")],
    [b(3, 0, 6, 14, 1.2, 9, "plank"), b(6, 1.2, 7, 10, 1.6, 8, "gold")]],
     (3, 0, 5, 14, 2, 10), solid=False)
prop("scarecrow", "Scarecrow", [
    [b(2, 0, 6, 26, 2, 9, "wood"), b(8, 0, 1, 16, 4, 14, "straw"), b(18, 0, 5, 23, 5, 10, "straw")],
    [b(7, 0, 7, 9, 26, 9, "wood"), b(-2, 17, 7.3, 18, 19, 8.7, "wood"), b(4, 10, 5.5, 12, 20, 10.5, "cloth"), b(5, 20, 5, 11, 26, 11, "straw")],
    [b(7, 0, 7, 9, 26, 9, "wood"), b(-2, 17, 7.3, 18, 19, 8.7, "wood"), b(4, 10, 5.5, 12, 20, 10.5, "cloth"), b(5, 20, 5, 11, 26, 11, "straw"),
     b(3, 26, 3, 13, 27, 13, "darkwood"), b(5, 27, 5, 11, 30, 11, "darkwood")],
    [b(7, 0, 7, 9, 26, 9, "wood"), b(-2, 17, 7.3, 18, 19, 8.7, "wood")]],
     (6, 0, 6, 10, 16, 10))
prop("winch", "Well Winch", [
    [b(0, 0, 7, 2, 14, 9, "wood"), b(14, 0, 7, 16, 14, 9, "wood"), b(2, 11, 7, 14, 13, 9, "strip"), b(16, 10, 7.5, 18, 14, 8.5, "iron")],
    [b(0, 0, 7, 2, 14, 9, "wood"), b(14, 0, 7, 16, 14, 9, "wood"), b(2, 11, 7, 14, 13, 9, "strip"), b(16, 10, 7.5, 18, 14, 8.5, "iron"),
     b(5, 10.5, 6.5, 11, 13.5, 9.5, "rope"), b(7.5, 2, 7.5, 8.5, 11, 8.5, "rope"), b(6, -4, 6, 10, 2, 10, "darkwood")]] * 2,
     (0, 0, 6, 16, 14, 10))
prop("wind_chime", "Wind Chime", [
    [b(4, 14, 7.5, 12, 15, 8.5, "wood"), b(4.5, 5, 7.6, 5.5, 14, 8.4, "bone"), b(7.5, 3, 7.6, 8.5, 14, 8.4, "shell"), b(10.5, 6, 7.6, 11.5, 14, 8.4, "bone"), b(7.8, 15, 7.8, 8.2, 16, 8.2, "rope")],
    [b(4, 14, 7.5, 12, 15, 8.5, "wood"), b(4.5, 5, 7.6, 5.5, 14, 8.4, "bone"), b(10.5, 6, 7.6, 11.5, 14, 8.4, "bone"), b(7.8, 15, 7.8, 8.2, 16, 8.2, "rope"), b(7.8, 9, 7.8, 8.2, 14, 8.2, "rope")]] * 2,
     (4, 3, 7, 12, 16, 9), solid=False)
prop("gong", "Rock Gong", [
    [b(0, 0, 7, 2, 24, 9, "wood"), b(14, 0, 7, 16, 24, 9, "wood"), b(0, 22, 7, 16, 24, 9, "wood"), b(3, 5, 7.3, 13, 19, 8.7, "dark"), b(4.5, 19, 7.7, 5, 22, 8.3, "rope"),
     b(11, 19, 7.7, 11.5, 22, 8.3, "rope"), b(6.5, 10.5, 7, 9.5, 13.5, 7.3, "stone")]] * 4,
     (0, 0, 6, 16, 16, 10))
prop("sundial", "Sundial", [
    [b(5, 0, 5, 11, 8, 11, "stone"), b(1, 8, 1, 15, 10, 15, "smooth"), b(6, 10, 7.5, 13, 10.6, 8.5, "bronze")],
    [b(5, 0, 5, 11, 8, 11, "stone"), b(1, 8, 1, 15, 10, 15, "smooth"), b(7.5, 10, 4, 8.5, 16, 12, "bronze", ("x", -22.5, (8, 10, 8)))],
    [b(5, 0, 5, 11, 8, 11, "stone"), b(1, 8, 1, 15, 10, 15, "smooth"), b(7.5, 10, 4, 8.5, 16, 12, "bronze", ("x", -22.5, (8, 10, 8))), b(2, 10, 7, 4, 10.4, 9, "gold")],
    [b(5, 0, 5, 11, 8, 11, "mossy"), b(1, 8, 1, 15, 10, 15, "smooth"), b(6, 10, 7.5, 13, 10.6, 8.5, "bronze")]],
     (1, 0, 1, 15, 10, 15))
prop("weather_vane", "Weather Vane", [
    [b(7.5, 0, 7.5, 8.5, 12, 8.5, "iron"), b(7.6, 12, 0, 8.4, 14, 16, "rust"), b(7.6, 11, 12, 8.4, 16, 16, "rust")],
    [b(7.5, 0, 7.5, 8.5, 12, 8.5, "iron"), b(7.6, 12, 0, 8.4, 14, 16, "iron"), b(7.6, 11, 12, 8.4, 16, 16, "iron"), b(7.2, 12.2, -1, 8.8, 13.8, 1, "gold")]] * 2,
     (6, 0, 6, 10, 14, 10), solid=False)
prop("ring_post", "Ring Post", [dial(0), dial(1), dial(2), dial(3)], (2, 0, 2, 14, 12, 14))
prop("mirror_disc", "Bronze Mirror", [
    [b(7, 0, 7, 9, 10, 9, "wood"), b(2, 10, 7.4, 14, 22, 8.6, c)] for c in ("verdigris", "bronze", "brass", "gold")],
     (6, 0, 6, 10, 16, 10))
prop("brass_ring", "Brass Ring", [dial(0, "brass", "glass"), dial(1, "brass", "glass"), dial(2, "brass", "glass"), dial(3, "brass", "glass")], (2, 0, 2, 14, 12, 14))
prop("picture_stone", "Picture Stone", [
    [b(0, 0, 6, 16, 16, 10, "smooth"), b(*box, "dark")] for box in ((2, 2, 5.6, 8, 8, 6), (8, 2, 5.6, 14, 8, 6), (8, 8, 5.6, 14, 14, 6), (2, 8, 5.6, 8, 14, 6))],
     (0, 0, 6, 16, 16, 10))
prop("orrery", "Orrery", [
    [b(6, 0, 6, 10, 10, 10, "stone"), b(4, 10, 4, 12, 12, 12, "brass"), b(7, 12, 7, 9, 18, 9, "brass")] + arms for arms in (
        [], [b(9, 15, 7.5, 22, 16, 8.5, "brass"), b(21, 14, 6.5, 24, 17, 9.5, "gold")],
        [b(9, 15, 7.5, 22, 16, 8.5, "brass"), b(21, 14, 6.5, 24, 17, 9.5, "gold"), b(-6, 16.5, 7.5, 7, 17.5, 8.5, "brass"), b(-8, 15.5, 6.5, -5, 18.5, 9.5, "verdigris")],
        [b(9, 15, 7.5, 22, 16, 8.5, "brass"), b(21, 14, 6.5, 24, 17, 9.5, "gold"), b(-6, 16.5, 7.5, 7, 17.5, 8.5, "brass"), b(-8, 15.5, 6.5, -5, 18.5, 9.5, "verdigris"),
         b(7.5, 13.5, 9, 8.5, 14.5, 20, "brass"), b(6.5, 12.5, 19, 9.5, 15.5, 22, "glass")])],
     (4, 0, 4, 12, 16, 12))
prop("orrery_arm", "Orrery Arm", [[b(2, 0, 7, 13, 1, 9, "brass"), b(12, 0, 6, 15, 3, 10, t)] for t in ("gold", "verdigris", "glass", "gold")], (2, 0, 6, 15, 3, 10), solid=False)
prop("stilled", "Stilled Figure", [figure("stand"), figure("reach"), figure("sit"), figure("down")], (3, 0, 4, 13, 16, 12))
prop("stilled_child", "Stilled Child", [figure("child")] * 4, (4, 0, 5, 12, 16, 11))
prop("stone_sheep", "Stone Sheep", [
    [b(4, 5, 1, 12, 12, 13, "pale"), b(5, 9, -3, 11, 14, 2, "smooth"), b(4.5, 0, 2, 6.5, 5, 4, "smooth"), b(9.5, 0, 2, 11.5, 5, 4, "smooth"), b(4.5, 0, 10, 6.5, 5, 12, "smooth"), b(9.5, 0, 10, 11.5, 5, 12, "smooth")],
    [b(1, 0, 1, 8, 8, 13, "pale"), b(2, 1, -3, 7, 6, 2, "smooth"), b(8, 1, 2, 13, 3, 4, "smooth"), b(8, 5, 2, 13, 7, 4, "smooth"), b(8, 1, 10, 13, 3, 12, "smooth"), b(8, 5, 10, 13, 7, 12, "smooth")]] * 2,
     (3, 0, 1, 13, 12, 13), heavy=True)
prop("stone_cards", "Stone Cards", [[b(3 + i * 2.6, 0, 6 - (i % 2), 5.4 + i * 2.6, 0.5, 10 - (i % 2), "pale", ("y", 22.5 * ((i % 3) - 1), (8, 0, 8)) if i % 3 != 1 else None) for i in range(n)]
                                    for n in (1, 2, 3, 4)], (2, 0, 4, 14, 1, 12), solid=False)
prop("old_rod", "Old Fishing Rod", [[b(1, 0, 7.5, 15, 1, 8.5, "strip", ("y", 22.5, (8, 0, 8))), b(12, 0, 5, 14, 1.4, 7, "iron")]] * 4, (1, 0, 5, 15, 2, 11), solid=False)
prop("kite", "Kite", [
    [b(4, 3, 7.6, 12, 13, 8.4, "red", ("z", 22.5, (8, 8, 8))), b(7.6, 0, 7.6, 8.4, 4, 8.4, "rope")],
    [b(2, 0, 3, 14, 0.6, 13, "red", ("y", 22.5, (8, 0, 8))), b(7.6, 0, 12, 8.4, 0.5, 16, "white")]] * 2,
     (3, 0, 3, 13, 13, 13), solid=False)
prop("blade_blank", "Half-made Blade", [
    [b(7, 0, 1, 9, 1, 12, "iron"), b(6, 0, 12, 10, 1.2, 13, "rust"), b(7.3, 0, 13, 8.7, 1, 16, "wood")],
    [b(7, 0, 1, 9, 1, 12, "white"), b(6, 0, 12, 10, 1.2, 13, "gold"), b(7.3, 0, 13, 8.7, 1, 16, "darkwood")]] * 2,
     (6, 0, 1, 10, 2, 16), solid=False)
prop("nest", "Nest", [
    [b(2, 0, 2, 14, 2, 14, "straw"), b(2, 2, 2, 14, 5, 4, "wood"), b(2, 2, 12, 14, 5, 14, "wood"), b(2, 2, 4, 4, 5, 12, "wood"), b(12, 2, 4, 14, 5, 12, "wood")] + extra for extra in (
        [], [b(5, 2, 5, 8, 6, 8, "shell"), b(8.5, 2, 7, 11, 5.5, 10, "shell")], [b(5, 2, 5, 8, 4, 8, "gold"), b(8, 2, 8, 11, 3.5, 11, "blue"), b(6, 2, 9, 8, 3, 11, "brass")],
        [b(5, 2, 5, 11, 6, 11, "stone")])],
     (2, 0, 2, 14, 5, 14))
prop("great_rib", "Great Rib", [
    [b(6, 0, 6, 10, 16, 10, "bone"), b(6, 16, 5, 10, 28, 9, "bone", ("x", 22.5, (8, 16, 8)))],
    [b(6, 0, 6, 10, 16, 10, "bone")],
    [b(5, 0, 5, 11, 6, 11, "bone"), b(2, 0, 9, 14, 3, 12, "bone", ("y", 22.5, (8, 0, 10)))],
    [b(6, 0, 6, 10, 16, 10, "bone"), b(6.5, 16, 6.5, 9.5, 24, 9.5, "bone")]],
     (5, 0, 5, 11, 16, 11))
prop("gourd", "Painted Gourd", [
    [b(7.7, 11, 7.7, 8.3, 16, 8.3, "rope"), b(5, 3, 5, 11, 11, 11, t), b(6, 2, 6, 10, 3, 10, t), b(6.5, 11, 6.5, 9.5, 12, 9.5, "wood")] for t in ("gourd", "red", "blue", "white")],
     (5, 2, 5, 11, 16, 11), solid=False)
prop("flag", "Marker Flag", [
    [b(7.4, 0, 7.4, 8.6, 24, 8.6, "strip"), b(8.6, 15, 7.7, 18, 23, 8.3, t)] for t in ("red", "white", "blue", "gold")],
     (6.5, 0, 6.5, 9.5, 16, 9.5), solid=False)
prop("ring_target", "Ring on a Pole", [
    [b(7.4, 0, 7.4, 8.6, 14, 8.6, "strip"), b(3, 14, 7.5, 13, 15.5, 8.5, "iron"), b(3, 22.5, 7.5, 13, 24, 8.5, "iron"), b(3, 15.5, 7.5, 4.5, 22.5, 8.5, "iron"), b(11.5, 15.5, 7.5, 13, 22.5, 8.5, "iron")],
    [b(7.4, 0, 7.4, 8.6, 14, 8.6, "strip"), b(3, 14, 7.5, 13, 15.5, 8.5, "gold"), b(3, 22.5, 7.5, 13, 24, 8.5, "gold"), b(3, 15.5, 7.5, 4.5, 22.5, 8.5, "gold"), b(11.5, 15.5, 7.5, 13, 22.5, 8.5, "gold")]] * 2,
     (6.5, 0, 6.5, 9.5, 16, 9.5), solid=False)
prop("start_stone", "Start Stone", [
    [b(2, 0, 2, 14, 3, 14, "smooth"), b(5, 3, 5, 11, 4, 11, c)] for c in ("dark", "gold", "flame", "verdigris")],
     (2, 0, 2, 14, 4, 14), lit=2, light=7)
prop("map_tile", "Floor Tile", [
    [b(0, 0, 0, 16, 1, 16, "ash")], [b(0, 0, 0, 16, 1, 16, "clay"), b(3, 1, 3, 13, 1.3, 13, "pale")],
    [b(3, 0, 3, 13, 1, 13, "clay", ("y", 22.5, (8, 0, 8)))], [b(0, 0, 0, 16, 1, 16, "clay"), b(6, 1, 6, 10, 1.6, 10, "gold")]],
     (0, 0, 0, 16, 1, 16), solid=False)
prop("stone_horn", "Stone Horn", [
    [b(3, 2, -8, 13, 12, 8, "stone"), b(1, 0, 8, 15, 14, 24, "stone"), b(5, 4, -16, 11, 10, -8, "stone")],
    [b(3, 2, -8, 13, 12, 8, "stone"), b(1, 0, 8, 15, 14, 24, "stone"), b(5, 4, -16, 11, 10, -8, "stone"), b(4, 3, 18, 12, 9, 26, "straw")]] * 2,
     (3, 0, 0, 13, 12, 16))
prop("bell_tongue", "Bell Tongue", [[b(6, 0, 5, 10, 3, 9, "bronze"), b(7.3, 0, 9, 8.7, 2, 15, "bronze")]] * 4, (5, 0, 5, 11, 3, 15), solid=False)
prop("lens", "Lighthouse Lens", [
    [b(3, 0, 3, 13, 2, 13, "brass"), b(4, 2, 4, 12, 12, 12, "glass"), b(3, 12, 3, 13, 14, 13, "brass")],
    [b(3, 0, 3, 13, 2, 13, "brass"), b(4, 2, 4, 12, 12, 12, "glow"), b(3, 12, 3, 13, 14, 13, "brass")]] * 2,
     (3, 0, 3, 13, 14, 13), lit=1, light=15, heavy=True)
prop("summit_stone", "Summit Stone", [
    [b(3, 0, 4, 13, 6, 12, t), b(5, 6, 5, 11, 9, 11, t)] for t in ("bronze", "pale", "dark", "red")], (3, 0, 4, 13, 9, 12), heavy=True)
prop("loose_stone", "Loose Stone", [
    [b(4, 0, 5, 12, 4, 11, t, ("y", r, (8, 0, 8)) if r else None)] for t, r in (("stone", 0), ("cobble", 22.5), ("stone", -22.5), ("mossy", 0))], (4, 0, 5, 12, 4, 11), solid=False)
prop("wedge", "Wedge Stone", [[b(5, 0, 6, 11, 3, 10, "dark", ("z", 22.5, (8, 0, 8)))]] * 4, (5, 0, 6, 11, 4, 10), solid=False)
prop("plug", "Pond Plug", [
    [b(4, 0, 4, 12, 6, 12, "darkwood"), b(6, 6, 6, 10, 9, 10, "iron"), b(7.5, 9, 7.5, 8.5, 16, 8.5, "rope")],
    [b(2, 0, 4, 10, 6, 12, "darkwood", ("z", 22.5, (6, 0, 8))), b(10, 0, 7, 16, 1.2, 9, "rope")]] * 2,
     (4, 0, 4, 12, 9, 12))
prop("hoard", "Small Hoard", [
    [b(4, 0, 4, 12, 2, 12, "straw"), b(5, 2, 5, 8, 4, 8, "gold"), b(8, 2, 8, 11, 3.5, 11, "blue"), b(6, 2, 9, 8, 3, 11, "brass")],
    [b(4, 0, 4, 12, 2, 12, "straw")]] * 2,
     (4, 0, 4, 12, 4, 12), solid=False)


def variants(name):
    out = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for s in range(4):
            v = {"model": aw(f"block/{name}_{s}")}
            if y:
                v["y"] = y
            out[f"facing={facing},state={s}"] = v
    return out


def used(elements):
    keys = set()
    for el in elements:
        for f in el["faces"].values():
            keys.add(f["texture"][1:])
    return keys


TIERS = {
    # table: (rolls of the common pool, [(item, weight, min, max)], [(rare item, weight, min, max)], chance of a rare)
    "e_small": (2, [("minecraft:bread", 6, 1, 2), ("minecraft:candle", 4, 1, 3), ("minecraft:iron_nugget", 6, 2, 6), ("minecraft:arrow", 5, 3, 8), ("minecraft:string", 4, 1, 3),
                    ("minecraft:leather", 3, 1, 2), ("minecraft:emerald", 3, 1, 1), ("minecraft:honey_bottle", 2, 1, 1)], [("minecraft:name_tag", 2, 1, 1), ("minecraft:spyglass", 1, 1, 1)], 0.08),
    "e_big": (3, [("minecraft:iron_ingot", 6, 1, 3), ("minecraft:emerald", 5, 1, 3), ("minecraft:golden_carrot", 4, 1, 3), ("minecraft:arrow", 4, 6, 14), ("minecraft:lantern", 3, 1, 2),
                  ("minecraft:copper_ingot", 5, 2, 6)], [("minecraft:saddle", 2, 1, 1), ("minecraft:name_tag", 2, 1, 1), ("minecraft:music_disc_13", 1, 1, 1)], 0.2),
    "d_small": (2, [("minecraft:iron_ingot", 6, 1, 2), ("minecraft:gold_nugget", 5, 3, 8), ("minecraft:emerald", 4, 1, 2), ("minecraft:golden_carrot", 3, 1, 2), ("minecraft:spectral_arrow", 3, 2, 6),
                    ("minecraft:amethyst_shard", 4, 1, 3)], [("minecraft:experience_bottle", 3, 1, 2), ("minecraft:ender_pearl", 1, 1, 1)], 0.1),
    "d_big": (3, [("minecraft:gold_ingot", 6, 1, 3), ("minecraft:iron_ingot", 6, 2, 5), ("minecraft:emerald", 5, 2, 4), ("minecraft:amethyst_shard", 4, 2, 5), ("minecraft:experience_bottle", 3, 1, 3)],
              [("minecraft:diamond", 2, 1, 1), ("minecraft:golden_apple", 2, 1, 1), ("minecraft:music_disc_cat", 1, 1, 1)], 0.25),
    "c_small": (2, [("minecraft:gold_ingot", 6, 1, 2), ("minecraft:emerald", 5, 1, 3), ("minecraft:experience_bottle", 4, 1, 2), ("minecraft:lapis_lazuli", 4, 2, 6), ("minecraft:quartz", 3, 2, 5)],
                [("minecraft:diamond", 2, 1, 1), ("minecraft:ender_pearl", 2, 1, 2)], 0.12),
    "c_big": (3, [("minecraft:gold_ingot", 6, 2, 5), ("minecraft:emerald", 5, 3, 6), ("minecraft:experience_bottle", 4, 2, 4), ("minecraft:lapis_lazuli", 4, 4, 9), ("minecraft:diamond", 2, 1, 1)],
              [("minecraft:diamond", 3, 1, 2), ("minecraft:golden_apple", 2, 1, 2), ("minecraft:totem_of_undying", 1, 1, 1)], 0.3),
    "b_small": (2, [("minecraft:gold_ingot", 5, 2, 4), ("minecraft:emerald", 5, 2, 4), ("minecraft:experience_bottle", 5, 2, 3), ("minecraft:diamond", 2, 1, 1), ("minecraft:ender_pearl", 3, 1, 2)],
                [("minecraft:diamond", 3, 1, 2), ("minecraft:golden_apple", 2, 1, 1)], 0.15),
    "b_big": (4, [("minecraft:gold_ingot", 5, 3, 6), ("minecraft:emerald", 5, 4, 8), ("minecraft:experience_bottle", 5, 3, 5), ("minecraft:diamond", 3, 1, 2), ("minecraft:ender_pearl", 3, 1, 3)],
              [("minecraft:diamond", 3, 2, 3), ("minecraft:enchanted_golden_apple", 1, 1, 1), ("minecraft:netherite_scrap", 1, 1, 1)], 0.35),
    "a_small": (2, [("minecraft:diamond", 4, 1, 1), ("minecraft:emerald", 5, 3, 6), ("minecraft:experience_bottle", 5, 2, 4), ("minecraft:gold_ingot", 4, 3, 6), ("minecraft:ender_pearl", 3, 2, 3)],
                [("minecraft:diamond", 3, 2, 3), ("minecraft:netherite_scrap", 1, 1, 1)], 0.2),
    "a_big": (4, [("minecraft:diamond", 5, 1, 3), ("minecraft:emerald", 5, 5, 10), ("minecraft:experience_bottle", 5, 4, 7), ("minecraft:gold_ingot", 4, 4, 8), ("minecraft:ender_pearl", 3, 2, 4)],
              [("minecraft:netherite_scrap", 2, 1, 2), ("minecraft:enchanted_golden_apple", 1, 1, 1), ("minecraft:diamond", 3, 3, 5)], 0.45),
}


def entry(item, weight, lo, hi):
    e = {"type": mc("item"), "name": item, "weight": weight}
    if hi > 1:
        e["functions"] = [{"function": mc("set_count"), "count": {"type": mc("uniform"), "min": lo, "max": hi}}]
    return e


def loot_tiers():
    for name, (rolls, common, rare, chance) in TIERS.items():
        write(f"{DATA}/loot_table/finds/{name}.json", {"type": mc("chest"), "random_sequence": aw("finds/" + name), "pools": [
            {"rolls": rolls, "entries": [entry(*c) for c in common]},
            {"rolls": 1, "conditions": [{"condition": mc("random_chance"), "chance": chance}], "entries": [entry(*r) for r in rare]}]})


def java():
    lines = ["package dev.awaken.finds.kit;", "", "import dev.awaken.terrain.block.BlockSet;", "import net.minecraft.world.level.block.SoundType;",
             "import net.minecraft.world.level.block.state.BlockBehaviour;", "import net.minecraft.world.level.material.MapColor;",
             "import net.minecraft.world.level.material.PushReaction;", "import net.neoforged.bus.api.IEventBus;", "import net.neoforged.neoforge.registries.DeferredBlock;", "",
             "import java.util.ArrayList;", "import java.util.List;", "",
             "/**", " * GENERATED by tools/content/finds_content.py (edit there). The finds' prop kit (SPEC §58.8): every small block a find is",
             " * dressed and worked with, each one a {@link PropBlock} with a facing and a state of 0..3.", " */",
             "public final class Props {", "    private Props() {}", "", "    private static final BlockSet SET = new BlockSet();",
             "    private static final List<String> IDS = new ArrayList<>();", ""]
    for name, (display, states, shape, solid, lit, light, heavy) in PROPS.items():
        args = ", ".join(str(float(v)) + "F" for v in shape)
        lines.append(f"    /** {display}. */")
        lines.append(f"    public static final DeferredBlock<PropBlock> {name.upper()} = prop(\"{name}\", {args}, {'true' if solid else 'false'}, {lit if lit is not None else 9}, {light});")
    lines += ["",
              "    private static DeferredBlock<PropBlock> prop(String id, float x0, float y0, float z0, float x1, float y1, float z1, boolean solid, int litFrom, int light) {",
              "        IDS.add(id);",
              "        return SET.withItem(SET.blocks.registerBlock(id, p -> new PropBlock(p, x0, y0, z0, x1, y1, z1, solid), p -> {",
              "            BlockBehaviour.Properties q = p.mapColor(MapColor.STONE).strength(0.6F).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.POPPED)",
              "                    .lightLevel(s -> s.getValue(PropBlock.STATE) >= litFrom && (litFrom != 1 || (s.getValue(PropBlock.STATE) & 1) == 1) ? light : 0);",
              "            return solid ? q : q.noCollision();",
              "        }));",
              "    }", "",
              "    public static void init(IEventBus modBus) {", "        SET.init(modBus);", "    }", "",
              "    /** Whether a prop used as a cache stands open in its state 1 (a drawer, a lid, a bundle). */",
              "    public static boolean opens(net.minecraft.world.level.block.Block b) {",
              "        return b == FOOTING.get() || b == CAP_STONE.get() || b == BUNDLE.get() || b == PLUG.get();",
              "    }", "",
              "    /** Every prop's id, for the ledger. */",
              "    public static String[] ids() {", "        return IDS.toArray(String[]::new);", "    }", "}", ""]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("\n".join(lines))


def main():
    lang, heavy = {}, []
    for name, (display, states, shape, solid, lit, light, is_heavy) in PROPS.items():
        states = (states * 4)[:4]
        for s, elements in enumerate(states):
            keys = sorted(used(elements))
            tex = {k: mc("block/" + TEX[k]) for k in keys}
            tex["particle"] = tex[keys[0]]
            write(f"{ASSETS}/models/block/{name}_{s}.json", {"textures": tex, "elements": elements,
                                                              "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
                                                                          "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                                                                          "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
                                                                          "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                                                                          "fixed": {"scale": [0.5, 0.5, 0.5]}}})
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": variants(name)})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw(f"block/{name}_0")}})
        write(f"{DATA}/loot_table/blocks/{name}.json", {"type": mc("block"), "random_sequence": aw("blocks/" + name), "pools": [
            {"rolls": 1, "entries": [{"type": mc("item"), "name": aw(name)}], "conditions": [{"condition": mc("survives_explosion")}]}]})
        lang[f"block.awaken.{name}"] = display
        if is_heavy:
            heavy.append(aw(name))
    write(f"{DATA}/tags/item/finds/heavy.json", {"values": heavy})
    write(f"{MERGE}/assets/awaken/lang/en_us.json", lang)
    loot_tiers()
    java()
    print("finds_content: wrote", len(PROPS), "props,", len(TIERS), "loot tiers")


if __name__ == "__main__":
    main()
