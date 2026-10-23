"""Writes the Veil's block states, block/item models and client item definitions.

Run from the repo root: python3 tools/content/veil_assets.py
The ghost lever copies the vanilla lever's block state and models (from the local reference assets) with spirit textures.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.abspath(os.path.join(HERE, "../../src/main/resources/assets/awaken"))
A = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft"


def w(rel, obj):
    p = os.path.join(R, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def cube(name, tex, translucent=False, emissive=None):
    t = {"sprite": "awaken:block/" + tex, "force_translucent": True} if translucent else "awaken:block/" + tex
    if emissive is None:
        w("models/block/%s.json" % name, {"parent": "minecraft:block/cube_all", "textures": {"all": t}})
        return
    faces = {d: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d} for d in ("down", "up", "north", "south", "west", "east")}
    w("models/block/%s.json" % name, {
        "parent": "minecraft:block/block",
        "textures": {"particle": "awaken:block/" + tex, "all": t},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": emissive, "faces": faces}],
    })


def box(frm, to, emissive=None):
    faces = {}
    for d in ("down", "up", "north", "south", "west", "east"):
        tex = "#top" if d in ("up", "down") else "#side"
        if d in ("up", "down"):
            uv = [frm[0], frm[2], to[0], to[2]]
        elif d in ("north", "south"):
            uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        else:
            uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        faces[d] = {"uv": [max(0, min(16, v)) for v in uv], "texture": tex}
    el = {"from": frm, "to": to, "faces": faces}
    if emissive:
        el["light_emission"] = emissive
    return el


def main():
    # ghost lever: the vanilla lever's shape and states, spirit textures, glowing handle
    bs = json.load(open(A + "/blockstates/lever.json"))
    for v in bs["variants"].values():
        v["model"] = v["model"].replace("minecraft:block/lever_on", "awaken:block/ghost_lever_on").replace(
            "minecraft:block/lever", "awaken:block/ghost_lever")
    w("blockstates/ghost_lever.json", bs)
    for name in ("lever", "lever_on"):
        m = json.load(open(A + "/models/block/%s.json" % name))
        m["textures"] = {
            "particle": "awaken:block/ghost_lever_base",
            "base": {"sprite": "awaken:block/ghost_lever_base", "force_translucent": True},
            "lever": {"sprite": "awaken:block/ghost_lever_handle", "force_translucent": True},
        }
        for el in m["elements"]:
            el["light_emission"] = 15 if any(f["texture"] == "#lever" for f in el["faces"].values()) else 9
        w("models/block/ghost_%s.json" % name, m)
    w("items/ghost_lever.json", {"model": {"type": "minecraft:model", "model": "awaken:item/ghost_lever"}})
    w("models/item/ghost_lever.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/ghost_lever"}})

    cube("ghost_door", "ghost_door", translucent=True, emissive=12)
    cube("ghost_door_open", "ghost_door_open", translucent=True, emissive=8)
    w("blockstates/ghost_door.json", {"variants": {
        "open=false": {"model": "awaken:block/ghost_door"},
        "open=true": {"model": "awaken:block/ghost_door_open"}}})

    cube("veil_stone", "veil_stone")
    w("blockstates/veil_stone.json", {"variants": {"": {"model": "awaken:block/veil_stone"}}})
    cube("veil_glyph", "veil_glyph")
    cube("veil_glyph_lit", "veil_glyph_lit", emissive=15)
    w("blockstates/veil_glyph.json", {"variants": {
        "lit=false": {"model": "awaken:block/veil_glyph"},
        "lit=true": {"model": "awaken:block/veil_glyph_lit"}}})

    cube("stillpoint_door_out", "stillpoint_door_out", translucent=True, emissive=15)
    w("blockstates/stillpoint_door_out.json", {"variants": {"": {"model": "awaken:block/stillpoint_door_out"}}})
    cube("stillpoint_door_in", "stillpoint_door_in", translucent=True)
    w("blockstates/stillpoint_door_in.json", {"variants": {"": {"model": "awaken:block/stillpoint_door_in"}}})
    cube("stillpoint_cloud", "stillpoint_cloud")
    w("blockstates/stillpoint_cloud.json", {"variants": {"": {"model": "awaken:block/stillpoint_cloud"}}})

    # the secret marker is invisible in the world; it only needs a particle sprite
    w("models/block/veil_mark.json", {"textures": {"particle": "awaken:block/veil_mark"}})
    w("blockstates/veil_mark.json", {"variants": {"": {"model": "awaken:block/veil_mark"}}})
    w("items/veil_mark.json", {"model": {"type": "minecraft:model", "model": "awaken:item/veil_mark"}})
    w("models/item/veil_mark.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/veil_mark"}})

    w("models/block/veil_cache.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "awaken:block/veil_cache",
            "side": {"sprite": "awaken:block/veil_cache", "force_translucent": True},
            "top": {"sprite": "awaken:block/veil_cache_top", "force_translucent": True},
        },
        "elements": [box([3, 0, 3], [13, 12, 13], emissive=13)],
    })
    w("blockstates/veil_cache.json", {"variants": {"": {"model": "awaken:block/veil_cache"}}})

    flame = box([6.5, 14, 6.5], [9.5, 16.5, 9.5], emissive=15)
    for f in flame["faces"].values():
        f["texture"] = "#flame"
    w("models/block/stillpoint_shrine.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "awaken:block/stillpoint_shrine_side",
            "side": "awaken:block/stillpoint_shrine_side",
            "top": "awaken:block/stillpoint_shrine_top",
            "flame": "awaken:block/stillpoint_flame",
        },
        "elements": [box([2, 0, 2], [14, 3, 14]), box([5, 3, 5], [11, 11, 11]), box([3, 11, 3], [13, 14, 13]), flame],
    })
    w("blockstates/stillpoint_shrine.json", {"variants": {"": {"model": "awaken:block/stillpoint_shrine"}}})

    for b in ("ghost_door", "veil_cache", "veil_stone", "veil_glyph", "stillpoint_shrine", "stillpoint_door_out",
              "stillpoint_door_in", "stillpoint_cloud"):
        w("items/%s.json" % b, {"model": {"type": "minecraft:model", "model": "awaken:block/" + b}})
    print("veil assets written")


if __name__ == "__main__":
    sys.exit(main())
