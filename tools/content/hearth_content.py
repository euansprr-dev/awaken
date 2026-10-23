"""Writes the hearth module's block/item JSON: blockstates, models, client item definitions, loot tables, recipes and
particle definitions. Run from the repo root: python3 tools/content/hearth_content.py (idempotent)."""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../src/main/resources"))
A = os.path.join(ROOT, "assets/awaken")
D = os.path.join(ROOT, "data/awaken")
ROLES = ["guard", "haul", "farm", "mine", "train"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def blockstate(name, variants):
    write(f"{A}/blockstates/{name}.json", {"variants": variants})


def model(name, obj):
    write(f"{A}/models/block/{name}.json", obj)


def item_def(name, model_ref):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model_ref}})


def loot(name):
    write(f"{D}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}], "rolls": 1}],
        "random_sequence": f"awaken:blocks/{name}"})


def facing(name, model_name=None):
    m = f"awaken:block/{model_name or name}"
    blockstate(name, {f"facing={d}": ({"model": m, "y": y} if y else {"model": m})
                      for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))})


def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces, emissive=0):
    e = {"from": frm, "to": to, "faces": faces}
    if emissive:
        e["light_emission"] = emissive
    return e


def all_faces(tex, **override):
    out = {d: face(tex) for d in ("down", "up", "north", "south", "west", "east")}
    out.update(override)
    return out


def simple_block(name):
    blockstate(name, {"": {"model": f"awaken:block/{name}"}})


def main():
    # Hearth Anchor: obsidian frame, amber core.
    model("hearth_anchor", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": "awaken:block/hearth_anchor_side", "top": "awaken:block/hearth_anchor_top", "bottom": "awaken:block/hearth_anchor_bottom"}})
    simple_block("hearth_anchor")

    model("meditation_mat", {"parent": "minecraft:block/carpet", "textures": {"wool": "awaken:block/meditation_mat"}})
    simple_block("meditation_mat")

    model("forge", {"parent": "minecraft:block/orientable", "textures": {
        "front": "awaken:block/forge_front", "side": "awaken:block/forge_side", "top": "awaken:block/forge_top"}})
    facing("forge")

    # Trophy plaque against the south side of the cell (model faces north).
    model("trophy_mount", {"parent": "minecraft:block/block", "textures": {"plaque": "awaken:block/trophy_mount", "particle": "awaken:block/trophy_mount"},
                           "elements": [box([2, 2, 14], [14, 14, 16], all_faces("#plaque", north=face("#plaque", [2, 2, 14, 14])))]})
    facing("trophy_mount")

    # Thin plates: role pads and course gates share one template.
    model("thin_plate", {"parent": "minecraft:block/thin_block", "textures": {"particle": "#top"}, "elements": [box([0, 0, 0], [16, 1, 16], {
        "down": face("#side", [0, 0, 16, 16], "down"), "up": face("#top", [0, 0, 16, 16]),
        "north": face("#side", [0, 15, 16, 16], "north"), "south": face("#side", [0, 15, 16, 16], "south"),
        "west": face("#side", [0, 15, 16, 16], "west"), "east": face("#side", [0, 15, 16, 16], "east")})]})
    for r in ROLES:
        n = f"role_pad_{r}"
        model(n, {"parent": "awaken:block/thin_plate", "textures": {"top": f"awaken:block/{n}", "side": "awaken:block/role_pad_side"}})
        simple_block(n)
    for n in ("start_gate", "finish_gate"):
        model(n, {"parent": "awaken:block/thin_plate", "textures": {"top": f"awaken:block/{n}", "side": "awaken:block/role_pad_side"}})
        simple_block(n)

    # Spire Board: a board on a short post (front faces north).
    model("spire_board", {"parent": "minecraft:block/block", "textures": {"board": "awaken:block/spire_board", "post": "awaken:block/spire_board_post",
                                                                         "particle": "awaken:block/spire_board_post"},
                          "elements": [box([1, 4, 6], [15, 16, 10], all_faces("#post", north=face("#board", [1, 0, 15, 12]),
                                                                              south=face("#board", [1, 0, 15, 12]))),
                                       box([6, 0, 7], [10, 4, 9], all_faces("#post"))]})
    facing("spire_board")

    model("reassessment_monolith", {"parent": "minecraft:block/block", "textures": {"side": "awaken:block/monolith_side", "top": "awaken:block/monolith_top",
                                                                                   "particle": "awaken:block/monolith_side"},
                                    "elements": [box([2, 0, 2], [14, 16, 14], all_faces("#side", up=face("#top", [2, 2, 14, 14]),
                                                                                        down=face("#top", [2, 2, 14, 14], "down")), emissive=4)]})
    simple_block("reassessment_monolith")

    # Checkpoint ring: four glowing bars in the middle plane (axis=z faces along z).
    band = "#ring"
    bars = [box([0, 0, 7], [16, 2, 9], all_faces(band)), box([0, 14, 7], [16, 16, 9], all_faces(band)),
            box([0, 2, 7], [2, 14, 9], all_faces(band)), box([14, 2, 7], [16, 14, 9], all_faces(band))]
    for b in bars:
        b["light_emission"] = 12
        for k, f in b["faces"].items():
            f["uv"] = [0, 6, 16, 10] if k in ("north", "south", "up", "down") else [6, 6, 10, 10]
    model("checkpoint_ring", {"parent": "minecraft:block/block", "textures": {"ring": "awaken:block/checkpoint_ring", "particle": "awaken:block/checkpoint_ring"},
                              "elements": bars})
    blockstate("checkpoint_ring", {"axis=z": {"model": "awaken:block/checkpoint_ring"}, "axis=x": {"model": "awaken:block/checkpoint_ring", "y": 90}})

    model("target_dummy", {"parent": "minecraft:block/block", "textures": {"face": "awaken:block/target_dummy", "side": "awaken:block/target_dummy_side",
                                                                          "particle": "awaken:block/target_dummy_side"},
                           "elements": [box([7, 0, 7], [9, 6, 9], all_faces("#side")),
                                        box([3, 6, 6], [13, 16, 10], all_faces("#side", north=face("#face", [3, 0, 13, 10]),
                                                                               south=face("#face", [3, 0, 13, 10])))]})
    facing("target_dummy")

    blocks = ["hearth_anchor", "meditation_mat", "forge", "trophy_mount", "spire_board", "reassessment_monolith", "start_gate",
              "finish_gate", "checkpoint_ring", "target_dummy"] + [f"role_pad_{r}" for r in ROLES]
    for b in blocks:
        item_def(b, f"awaken:block/{b}")
        loot(b)

    write(f"{A}/models/item/trophy.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/trophy"}})
    item_def("trophy", "awaken:item/trophy")

    write(f"{A}/particles/echo_wisp.json", {"textures": [f"awaken:echo_wisp_{i}" for i in range(4)]})
    write(f"{A}/particles/hearth_ember.json", {"textures": [f"awaken:hearth_ember_{i}" for i in range(3)]})

    # Recipes.
    def shaped(name, pattern, key, count=1, category="misc"):
        r = {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": {"id": f"awaken:{name}"}}
        if count > 1:
            r["result"]["count"] = count
        write(f"{D}/recipe/{name}.json", r)

    def shapeless(name, ingredients, count=1, category="misc"):
        r = {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": {"id": f"awaken:{name}"}}
        if count > 1:
            r["result"]["count"] = count
        write(f"{D}/recipe/{name}.json", r)

    shaped("hearth_anchor", ["OSO", "SHS", "OSO"], {"O": "minecraft:obsidian", "S": "minecraft:stone_bricks", "H": "awaken:lord_heart"})
    shaped("meditation_mat", ["WAW"], {"W": "#minecraft:wool", "A": "minecraft:amethyst_shard"}, category="building")
    shaped("forge", ["BBB", "IFI", "BBB"], {"B": "minecraft:bricks", "I": "minecraft:iron_ingot", "F": "minecraft:blast_furnace"})
    shaped("trophy_mount", ["PPP", "PGP"], {"P": "#minecraft:planks", "G": "minecraft:gold_ingot"}, count=2, category="building")
    keys = {"guard": "minecraft:iron_sword", "haul": "minecraft:chest", "farm": "minecraft:wheat", "mine": "minecraft:iron_pickaxe", "train": "minecraft:target"}
    for r in ROLES:
        shapeless(f"role_pad_{r}", ["minecraft:smooth_stone_slab", "minecraft:smooth_stone_slab", "awaken:essence_shard", keys[r]])
    shaped("spire_board", ["PPP", "PLP", " S "], {"P": "#minecraft:planks", "L": "minecraft:lantern", "S": "minecraft:stick"}, category="building")
    shaped("reassessment_monolith", ["DAD", "DED", "DDD"], {"D": "minecraft:polished_deepslate", "A": "minecraft:amethyst_shard", "E": "awaken:essence_shard"})
    shapeless("start_gate", ["minecraft:smooth_stone_slab", "minecraft:lime_dye", "minecraft:redstone"])
    shapeless("finish_gate", ["minecraft:smooth_stone_slab", "minecraft:black_dye", "minecraft:white_dye"])
    shaped("checkpoint_ring", ["SSS", "S S", "SGS"], {"S": "minecraft:stick", "G": "minecraft:glowstone_dust"}, count=2)
    shaped("target_dummy", ["T", "H", "F"], {"T": "minecraft:target", "H": "minecraft:hay_block", "F": "#minecraft:wooden_fences"})
    print("hearth content written")


if __name__ == "__main__":
    main()
