"""Dreams remade (docs/dreams/PLAN.md §8): the style sheet for the trials' courses.

Not the old jumble's quartz and moss. The Lucidium keeps its lilac hour; the courses are pearl: a pale road that lays
itself, walls with a thread of gold at running height (run along it), ledges whose lip glows where the hands go, and
the Waking Door, a tall frame of pale light that breathes. Each block has an emissive layer (``_glow``) so it reads in
the dream's dusk without lighting the whole sky. The bed's mote is the one sign awake that a dream is waiting.

    python3 tools/art/build_all.py dreams          textures
    python3 tools/art/specs/dreams.py content      models, blockstates, lang (once, or after a change)
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, ramp, rng_for, value_noise, quantize  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/lucidium")

# pearl: a lilac-washed white; gold: the dream's own soft light (not the bell's brass)
PEARL = [(0x6d, 0x62, 0x86, 255), (0x8c, 0x81, 0xa6, 255), (0xaa, 0xa0, 0xc2, 255), (0xc6, 0xbe, 0xd9, 255),
         (0xdf, 0xd9, 0xec, 255), (0xf4, 0xf0, 0xfa, 255)]
GOLD = [(0x8a, 0x5e, 0x2a, 255), (0xc0, 0x8a, 0x3c, 255), (0xe8, 0xb8, 0x62, 255), (0xf7, 0xd8, 0x92, 255),
        (0xff, 0xef, 0xc8, 255), (0xff, 0xfb, 0xee, 255)]


def pearl_base(seed, scale=3, lo=1, hi=5):
    rng = rng_for("dream-pearl", seed)
    n = value_noise(16, 16, rng, scale=scale, octaves=2)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, PEARL[lo + quantize(n[y][x], hi - lo)])
    return c


def road():
    """Rounded pearl tiles, two to a block, each with a soft rim: reads as a path you can follow, not a floor."""
    c = pearl_base("road", scale=4, lo=2, hi=5)
    for y in range(16):
        for x in range(16):
            tx, ty = x % 8, y % 16
            rim = tx in (0, 7) or ty in (0, 15)
            if rim:
                c.blend(x, y, PEARL[1], 0.55)
            elif tx in (1, 6) or ty in (1, 14):
                c.blend(x, y, PEARL[5], 0.25)
    return c


def road_glow():
    """Flecks of pearl dust in the tiles (emissive): the road shines a little under your feet."""
    c = Canvas()
    rng = rng_for("road-glow")
    for _ in range(7):
        x, y = rng.randrange(1, 15), rng.randrange(2, 14)
        if x % 8 in (0, 7):
            continue
        c.set(x, y, GOLD[4] if rng.random() < 0.5 else PEARL[5])
    return c


def wall():
    """Pearl in long horizontal strata: a face you run along, never climb."""
    rng = rng_for("dream-wall")
    c = Canvas()
    bands = [rng.random() for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = bands[y] * 0.6 + 0.4 * (0.5 + 0.5 * math.sin(x * 0.4 + y * 1.7))
            c.set(x, y, PEARL[1 + quantize(v, 4)])
        if y % 4 == 3:
            for x in range(16):
                c.blend(x, y, PEARL[0], 0.35)
    return c


def wall_glow():
    """The running thread: one line of soft gold at shoulder height, unbroken across blocks."""
    c = Canvas()
    for x in range(16):
        c.set(x, 6, GOLD[3])
        c.set(x, 7, (*GOLD[2][:3], 150))
    return c


def ledge_side():
    c = wall()
    for x in range(16):
        c.set(x, 0, PEARL[5])
        c.set(x, 1, PEARL[4])
        c.blend(x, 2, PEARL[0], 0.4)
    return c


def ledge_glow():
    """The lip glows where the hands go."""
    c = Canvas()
    for x in range(16):
        c.set(x, 0, GOLD[5])
        c.set(x, 1, GOLD[3])
        if x % 3 == 1:
            c.set(x, 2, (*GOLD[2][:3], 120))
    return c


def ledge_top():
    c = pearl_base("ledge-top", scale=3, lo=3, hi=5)
    for i in range(16):
        c.blend(i, 0, PEARL[5], 0.5)
        c.blend(i, 15, PEARL[5], 0.5)
    return c


def door_frames(n=12):
    """The Waking Door's light: pale gold-white, breathing slowly. Wholly emissive."""
    frames = []
    for f in range(n):
        t = 0.5 + 0.5 * math.sin(f / n * math.tau)
        c = Canvas()
        for y in range(16):
            for x in range(16):
                core = 1 - abs(x - 7.5) / 8
                v = 0.55 + 0.35 * core + 0.1 * t + 0.05 * math.sin(y * 0.8 + f)
                c.set(x, y, GOLD[min(5, 2 + quantize(min(0.999, v), 4))])
        frames.append(c)
    return frames


def veil_frames(n=16):
    """The opening of the Waking Door: a sheet of light you can walk into."""
    frames = []
    for f in range(n):
        c = Canvas()
        for y in range(16):
            for x in range(16):
                wave = 0.5 + 0.5 * math.sin((y + f * 1.0) * 0.55 + math.sin(x * 0.7) * 0.8)
                a = int(60 + 110 * wave)
                col = GOLD[4] if wave > 0.6 else PEARL[5]
                c.set(x, y, (*col[:3], a))
        frames.append(c)
    return frames


def mote():
    """The bed's pearl mote (and the course's dust): a soft point with a gold heart."""
    c = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) / 3.6
            if d < 1:
                a = int(255 * (1 - d) ** 1.4)
                col = GOLD[5] if d < 0.35 else PEARL[5]
                c.set(x, y, (*col[:3], a))
    return c


def build(out):
    out.block("dream_road", road())
    out.block("dream_road_glow", road_glow())
    out.block("dream_wall", wall())
    out.block("dream_wall_glow", wall_glow())
    out.block("dream_ledge", ledge_side())
    out.block("dream_ledge_top", ledge_top())
    out.block("dream_ledge_glow", ledge_glow())
    out.block("waking_door", door_frames(), frametime=5)
    out.block("waking_veil", veil_frames(), frametime=3)
    out.particle("pearl_mote", mote())


# ---- content -----------------------------------------------------------------------------------------------------

SIDES = ("north", "south", "east", "west", "up", "down")


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    old = {}
    if os.path.exists(path):
        with open(path) as f:
            old = json.load(f)
    if isinstance(old.get("values"), list) and "values" in data:
        old["values"] = sorted(set(old["values"]) | set(data["values"]))
    else:
        old.update(data)
    _write(path, old)


def glow_cube(base, glow, top=None, glow_faces=SIDES):
    b = f"awaken:block/{base}"
    t = f"awaken:block/{top}" if top else b
    return {"parent": "minecraft:block/block",
            "textures": {"particle": b, "side": b, "top": t, "glow": f"awaken:block/{glow}"},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": {d: {"texture": "#top" if d in ("up", "down") else "#side", "cullface": d} for d in SIDES}},
                {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                 "faces": {d: {"texture": "#glow", "cullface": d} for d in glow_faces}}]}


def content():
    models = {
        "dream_road": glow_cube("dream_road", "dream_road_glow", glow_faces=("up",)),
        "dream_wall": glow_cube("dream_wall", "dream_wall_glow", glow_faces=("north", "south", "east", "west")),
        "dream_ledge": glow_cube("dream_ledge", "dream_ledge_glow", top="dream_ledge_top",
                                 glow_faces=("north", "south", "east", "west")),
        "waking_door": {"parent": "minecraft:block/block",
                        "textures": {"particle": "awaken:block/waking_door", "all": "awaken:block/waking_door"},
                        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15,
                                      "shade_direction_override": "up",
                                      "faces": {d: {"texture": "#all", "cullface": d} for d in SIDES}}]},
        "waking_veil": {"parent": "minecraft:block/block",
                        "textures": {"particle": "awaken:block/waking_veil",
                                     "all": {"sprite": "awaken:block/waking_veil", "force_translucent": True}},
                        "elements": [{"from": [0, 0, 7], "to": [16, 16, 9], "light_emission": 15,
                                      "shade_direction_override": "up",
                                      "faces": {d: {"texture": "#all"} for d in ("north", "south")}}]},
    }
    for name, model in models.items():
        _write(os.path.join(ASSETS, f"models/block/{name}.json"), model)
        if name == "waking_veil":
            _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {
                "axis=x": {"model": f"awaken:block/{name}", "y": 90},
                "axis=z": {"model": f"awaken:block/{name}"}}})
        else:
            _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {"": {"model": f"awaken:block/{name}"}}})
    _write(os.path.join(ASSETS, "particles/pearl_mote.json"), {"textures": ["awaken:pearl_mote"]})
    _merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {
        "block.awaken.dream_road": "Dream Road",
        "block.awaken.dream_wall": "Dream Wall",
        "block.awaken.dream_ledge": "Dream Ledge",
        "block.awaken.waking_door": "Waking Door",
        "block.awaken.waking_veil": "Waking Light",
    })
    print("dreams: content written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
