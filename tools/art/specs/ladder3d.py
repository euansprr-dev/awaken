"""The 3D ladder (Carillon M4 B2, Euan's request after the "Smart Ladders" mod): every minecraft:ladder in the game is
drawn as two chunky side rails with round-ish rungs standing off the wall, instead of vanilla's flat plane.

Visual only: the model overrides minecraft:block/ladder (all four facings rotate it, as vanilla's blockstate does);
collision, the outline and the climb box are LadderBlock's own code (z 13..16 for facing=north), so movement and
RunnerRules see vanilla's ladder. Every element stays inside that box.

Writes assets/awaken/textures/block/ladder_3d.png (the atlas below) and assets/minecraft/models/block/ladder.json.

Atlas (16x16, vanilla's ladder palette):
  x 0-1   rail front (2 wide), vertical grain, lit left edge
  x 2-4   rail side (3 deep)
  x 5-12  rows 0-1 rung front (light over dark: round), rows 2-3 rung top, rows 4-5 rung underside
  x 13-15 rows 0-2 rail end grain
"""
import json
import os

from pixelart import Canvas, hex_rgba, rng_for

# vanilla ladder.png, dark -> light
WOOD = [hex_rgba(c) for c in ("#3a2f1e", "#513d24", "#67502c", "#7e6237", "#967441", "#b58d50")]


def texture():
    c = Canvas()
    rng = rng_for("ladder_3d")
    # rail front: a lit edge, the body, a shadowed edge; grain flecks running down it (tiles vertically)
    for y in range(16):
        c.set(0, y, WOOD[5] if rng.random() < 0.75 else WOOD[4])
        c.set(1, y, WOOD[3] if rng.random() < 0.7 else WOOD[2])
    for y in (3, 4, 11):
        c.set(0, y, WOOD[4])
    c.set(1, 7, WOOD[1])  # a knot
    c.set(1, 8, WOOD[2])
    # rail side: mid tones, grain lines
    for y in range(16):
        for x in (2, 3, 4):
            v = 3 if x == 2 else (2 if x == 4 else 3)
            if rng.random() < 0.18:
                v -= 1
            c.set(x, y, WOOD[v])
    # rungs: front light over dark (a rounded rod in the light from above), top light, underside dark
    for x in range(5, 13):
        c.set(x, 0, WOOD[5] if rng.random() < 0.8 else WOOD[4])
        c.set(x, 1, WOOD[2] if rng.random() < 0.8 else WOOD[3])
        c.set(x, 2, WOOD[4] if rng.random() < 0.7 else WOOD[5])
        c.set(x, 3, WOOD[4])
        c.set(x, 4, WOOD[1])
        c.set(x, 5, WOOD[0] if rng.random() < 0.5 else WOOD[1])
    # rail ends: end grain, a ring
    for x in range(13, 16):
        for y in range(3):
            c.set(x, y, WOOD[4] if (x, y) == (14, 1) else WOOD[3])
    return c


def model():
    """Facing north (the wall to the south, z 16): rails x 2-4 and 12-14 (where vanilla's are), against the wall; four
    rungs (where vanilla's are) set half a pixel off it, the wall showing behind and between them."""
    tex = "awaken:block/ladder_3d"
    rail_faces = {
        "north": {"uv": [0, 0, 2, 16], "texture": "#t"},
        "east": {"uv": [2, 0, 5, 16], "texture": "#t"},
        "west": {"uv": [2, 0, 5, 16], "texture": "#t"},
        "up": {"uv": [13, 0, 15, 3], "texture": "#t"},
        "down": {"uv": [13, 0, 15, 3], "texture": "#t"},
        "south": {"uv": [0, 0, 2, 16], "texture": "#t", "cullface": "south"},
    }
    els = [
        {"from": [2, 0, 13], "to": [4, 16, 16], "faces": rail_faces},
        {"from": [12, 0, 13], "to": [14, 16, 16], "faces": rail_faces},
    ]
    for y in (1, 5, 9, 13):
        els.append({"from": [4, y, 13.5], "to": [12, y + 2, 15.5], "faces": {
            "north": {"uv": [5, 0, 13, 2], "texture": "#t"},
            "up": {"uv": [5, 2, 13, 4], "texture": "#t"},
            "down": {"uv": [5, 4, 13, 6], "texture": "#t"},
            "south": {"uv": [5, 4, 13, 6], "texture": "#t"},
        }})
    return {
        "ambientocclusion": False,
        "textures": {"particle": "block/ladder", "t": tex},
        "elements": els,
    }


def build(out):
    out.block("ladder_3d", texture())
    path = os.path.join(out.root, "assets/minecraft/models/block/ladder.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(model(), f, indent=2)
        f.write("\n")
