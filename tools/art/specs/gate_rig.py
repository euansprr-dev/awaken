"""The Gate Rig's blocks and particles (docs/SPEC.md §17 "Gate Rig"): the Mirror Hall's turning mirror (a silvered pane
in a gilt frame, standing at a diagonal), its sun lens and its light seal, the pavilion's cracked mirror, the wells'
glowing rims, and the rig's own particles (grit, sparks, glints, mirror shards, a well's motes). Pixel art in the
mod's palette, like every texture; the game tints grit, glints and motes per theme.

    python3 tools/art/build_all.py gate_rig        textures
    python3 tools/art/specs/gate_rig.py content    blockstates, models, item models, loot tables, lang, tags, particles
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import pixelart as P  # noqa: E402
from pixelart import Canvas, ramp, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge")

P.RAMPS.update({
    "silver": ["#3a4048", "#5c6570", "#8a94a0", "#b8c2cc", "#dde4ea", "#f8fbff"],
    "gilt": ["#3b2a12", "#5c4119", "#8a6a2a", "#b8913c", "#dcb95c", "#f6e3a0"],
    "lensgold": ["#5a3a0a", "#94631a", "#d29a2e", "#f4c858", "#ffe9a0", "#fffbe8"],
    "sealcold": ["#0b1424", "#13253f", "#1d3a60", "#2b557f", "#3f739e"],
    "sealhot": ["#1d5591", "#3c86cc", "#7fc3ff", "#bfe2ff", "#ecf7ff", "#ffffff"],
    "wellstone": ["#120f1c", "#1c1830", "#28223f", "#352d52", "#463c68"],
    "wellglow": ["#4a2a8a", "#7a4fd0", "#a98cff", "#d6c8ff", "#f4efff"],
})


def canvas(fill):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, fill(x, y))
    return c


def blank():
    return Canvas()


# ---- blocks ----------------------------------------------------------------------------------------------------------

def mirror_glass():
    """The silvered face: cool silver with a diagonal sheen, brighter toward the top."""
    sv = ramp("silver")

    def f(x, y):
        streak = (x + y) % 9 in (0, 1) or (x + y) % 13 == 5
        v = 2 + int((15 - y) / 15 * 2.2 + (x / 15) * 0.6)
        return sv[5] if streak else sv[min(5, v)]
    return canvas(f)


def mirror_back():
    """The pane's back: dark gilt-bronze with rivets (light stops here)."""
    gl = ramp("gilt")
    c = canvas(lambda x, y: gl[0] if (x + y) % 4 else gl[1])
    for (rx, ry) in ((3, 3), (12, 3), (3, 12), (12, 12), (7, 7), (8, 8)):
        c.set(rx, ry, gl[3])
    return c


def mirror_frame():
    gl = ramp("gilt")
    return canvas(lambda x, y: gl[4] if y % 5 == 0 else gl[3] if (x + y) % 3 else gl[2])


def lens_front():
    """The sun lens: rings of gold round a white-hot heart."""
    lg = ramp("lensgold")
    gl = ramp("gilt")

    def f(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 7.2:
            return gl[2]
        if d > 6.2:
            return gl[4]
        return lg[min(5, 5 - int(d / 1.3))]
    base = canvas(f)
    glow = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.2:
                glow.set(x, y, lg[5] if d < 2.2 else lg[4])
    return base, glow


def frame_side(name):
    gl = ramp("gilt")
    sl = ramp("slate")
    rng = rng_for(name)
    return canvas(lambda x, y: gl[3] if x in (0, 15) or y in (0, 15) else sl[2 + (1 if rng.random() < 0.3 else 0)])


def seal_front(lit):
    """The light seal: a crystal set in a gilt ring, dark and cold until the light reaches it."""
    cold, hot, gl = ramp("sealcold"), ramp("sealhot"), ramp("gilt")

    def f(x, y):
        dx, dy = abs(x - 7.5), abs(y - 7.5)
        diamond = dx + dy
        if diamond > 9.5:
            return gl[2]
        if diamond > 8.0:
            return gl[4]
        k = int(diamond / 1.7)
        return hot[max(0, 5 - k)] if lit else cold[max(0, 4 - k)]
    base = canvas(f)
    glow = blank()
    if lit:
        for y in range(16):
            for x in range(16):
                if abs(x - 7.5) + abs(y - 7.5) < 6.0:
                    glow.set(x, y, hot[5])
    return base, glow


def mirror_cracked():
    """A mirror crazed with fine cracks: they meet off-centre, and the silver between them is a little out of true."""
    sv = ramp("silver")
    c = mirror_glass()
    rng = rng_for("gate_mirror_cracked")
    cx, cy = 6 + rng.randrange(4), 5 + rng.randrange(5)
    for k in range(7):
        a = k / 7 * math.tau + rng.random() * 0.5
        x, y = float(cx), float(cy)
        for _ in range(12):
            x += math.cos(a)
            y += math.sin(a)
            a += (rng.random() - 0.5) * 0.6
            ix, iy = int(round(x)), int(round(y))
            if not (0 <= ix < 16 and 0 <= iy < 16):
                break
            c.set(ix, iy, sv[1])
            if 0 <= ix + 1 < 16 and rng.random() < 0.4:
                c.set(ix + 1, iy, sv[5])
    c.set(cx, cy, sv[0])
    return c


def well_rim():
    """The rim of a well: dark stone round a channel of violet light running across it."""
    st, wg = ramp("wellstone"), ramp("wellglow")
    rng = rng_for("gate_well_rim")

    def f(x, y):
        if y in (6, 7, 8, 9):
            return wg[3] if y in (7, 8) else wg[1]
        return st[1 + (1 if rng.random() < 0.35 else 0) + (1 if (x + y) % 7 == 0 else 0)]
    base = canvas(f)
    glow = blank()
    for x in range(16):
        glow.set(x, 7, wg[4])
        glow.set(x, 8, wg[3])
    for x in (2, 7, 12):
        glow.set(x, 6, wg[2])
        glow.set(x, 9, wg[2])
    return base, glow


def textures():
    t = {"gate_rig_mirror_glass": mirror_glass(), "gate_rig_mirror_back": mirror_back(), "gate_rig_mirror_frame": mirror_frame()}
    t["gate_rig_lens"], t["gate_rig_lens_glow"] = lens_front()
    t["gate_rig_lens_side"] = frame_side("lens")
    t["gate_rig_seal"], _ = seal_front(False)
    t["gate_rig_seal_lit"], t["gate_rig_seal_lit_glow"] = seal_front(True)
    t["gate_rig_seal_side"] = frame_side("seal")
    t["gate_mirror_cracked"] = mirror_cracked()
    t["gate_well_rim"], t["gate_well_rim_glow"] = well_rim()
    return t


# ---- particles (pale: the game tints them) ---------------------------------------------------------------------------

def grit(frame):
    rng = rng_for("grit", frame)
    c = Canvas(8, 8)
    n = 3 - frame // 2
    for _ in range(2 + n):
        x, y = rng.randrange(1, 7), rng.randrange(1, 7)
        c.set(x, y, (236, 230, 220, 255))
        if rng.random() < 0.5:
            c.set(x + 1, y, (200, 194, 186, 230))
    return c


def spark(frame):
    c = Canvas(8, 8)
    core = (255, 250, 230, 255)
    c.set(3, 3 + frame, core)
    c.set(4, 3 + frame, core)
    for k in range(1, 4 - frame):
        c.set(3, 3 + frame - k, (255, 210, 140, 200 - k * 50))
    return c


def glint(frame):
    c = Canvas(8, 8)
    r = 3 - frame
    for k in range(-r, r + 1):
        a = 255 - abs(k) * 60
        c.set(3 + k, 3, (255, 255, 255, a))
        c.set(3, 3 + k, (255, 255, 255, a))
    c.set(3, 3, (255, 255, 255, 255))
    return c


def shard(frame):
    rng = rng_for("shard", frame)
    c = Canvas(8, 8)
    pts = [(rng.randrange(1, 4), rng.randrange(1, 4)), (rng.randrange(4, 7), rng.randrange(1, 4)), (rng.randrange(2, 6), rng.randrange(4, 7))]
    for y in range(8):
        for x in range(8):
            (x1, y1), (x2, y2), (x3, y3) = pts
            d = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)
            if d == 0:
                continue
            a = ((y2 - y3) * (x - x3) + (x3 - x2) * (y - y3)) / d
            b = ((y3 - y1) * (x - x3) + (x1 - x3) * (y - y3)) / d
            if a >= 0 and b >= 0 and a + b <= 1:
                c.set(x, y, (250, 252, 255, 255) if (x + y + frame) % 3 == 0 else (200, 214, 230, 220))
    return c


def mote(frame):
    c = Canvas(8, 8)
    r = 2.6 - frame * 0.6
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d < r:
                c.set(x, y, (255, 255, 255, int(255 * max(0.2, 1 - d / r))))
    return c


PARTICLES = {"gate_rig_grit": (grit, 4), "gate_rig_spark": (spark, 3), "gate_rig_glint": (glint, 3),
             "gate_rig_shard": (shard, 4), "gate_rig_mote": (mote, 3)}


def build(out):
    for name, c in textures().items():
        out.block(name, c)
    for name, (fn, n) in PARTICLES.items():
        for i in range(n):
            out.particle(f"{name}_{i}", fn(i))


# ---- content ---------------------------------------------------------------------------------------------------------

SIDES = ("down", "up", "north", "south", "west", "east")


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


def cube(faces, emission=0):
    e = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": faces[d], "cullface": d} for d in SIDES}}
    if emission:
        e["light_emission"] = emission
    return e


def front_model(front, side, glow=None):
    """A cube with its front to the north (the blockstate turns it), and a glowing overlay on the front."""
    faces = {d: "#side" for d in SIDES}
    faces["north"] = "#front"
    tex = {"particle": side, "side": side, "front": front}
    els = [cube(faces)]
    if glow:
        tex["glow"] = glow
        els.append({"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                    "faces": {"north": {"texture": "#glow", "cullface": "north"}}})
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def mirror_model():
    """A silvered pane in a gilt frame at a diagonal: turned -45 degrees, its north face looks north-east (turn 0)."""
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": -45}

    def box(a, b, faces):
        return {"from": a, "to": b, "rotation": rot, "faces": faces}
    frame = {d: {"texture": "#frame"} for d in SIDES}
    pane = {"north": {"texture": "#glass"}, "south": {"texture": "#back"}, "up": {"texture": "#frame"}, "down": {"texture": "#frame"},
            "east": {"texture": "#frame"}, "west": {"texture": "#frame"}}
    els = [box([1.5, 0.5, 7.5], [14.5, 15.5, 8.5], pane),
           box([0.5, 0, 7], [1.5, 16, 9], frame), box([14.5, 0, 7], [15.5, 16, 9], frame),
           box([1.5, 15.5, 7], [14.5, 16, 9], frame), box([1.5, 0, 7], [14.5, 0.5, 9], frame)]
    els[0]["light_emission"] = 6
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "awaken:block/gate_rig_mirror_frame", "glass": "awaken:block/gate_rig_mirror_glass",
                         "back": "awaken:block/gate_rig_mirror_back", "frame": "awaken:block/gate_rig_mirror_frame"},
            "elements": els}


def glow_model(name):
    t = f"awaken:block/{name}"
    return {"parent": "minecraft:block/block", "textures": {"particle": t, "base": t, "glow": t + "_glow"},
            "elements": [cube({d: "#base" for d in SIDES}),
                         dict(cube({d: "#glow" for d in SIDES}, 15), shade_direction_override="up")]}


def bright_model(name):
    t = f"awaken:block/{name}"
    return {"parent": "minecraft:block/block", "textures": {"particle": t, "all": t}, "elements": [cube({d: "#all" for d in SIDES}, 15)]}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
NAMES = {"gate_rig_mirror": "Turning Mirror", "gate_rig_lens": "Sun Lens", "gate_rig_seal": "Light Seal",
         "gate_mirror_cracked": "Cracked Mirror", "gate_well_rim": "Well Rim"}


def content():
    b = lambda n: f"awaken:block/{n}"  # noqa: E731
    _write(os.path.join(ASSETS, "models/block/gate_rig_mirror.json"), mirror_model())
    _write(os.path.join(ASSETS, "blockstates/gate_rig_mirror.json"), {"variants": {
        f"turn={t},turning={tu}": dict({"model": b("gate_rig_mirror")}, **({"y": t * 90} if t else {}))
        for t in range(4) for tu in ("false", "true")}})
    _write(os.path.join(ASSETS, "models/block/gate_rig_lens.json"), front_model(b("gate_rig_lens"), b("gate_rig_lens_side"), b("gate_rig_lens_glow")))
    _write(os.path.join(ASSETS, "blockstates/gate_rig_lens.json"), {"variants": {
        f"facing={f}": dict({"model": b("gate_rig_lens")}, **({"y": r} if r else {})) for f, r in FACINGS}})
    _write(os.path.join(ASSETS, "models/block/gate_rig_seal.json"), front_model(b("gate_rig_seal"), b("gate_rig_seal_side")))
    _write(os.path.join(ASSETS, "models/block/gate_rig_seal_lit.json"),
           front_model(b("gate_rig_seal_lit"), b("gate_rig_seal_side"), b("gate_rig_seal_lit_glow")))
    _write(os.path.join(ASSETS, "blockstates/gate_rig_seal.json"), {"variants": {
        f"facing={f},lit={lit}": dict({"model": b("gate_rig_seal_lit" if lit == "true" else "gate_rig_seal")}, **({"y": r} if r else {}))
        for f, r in FACINGS for lit in ("false", "true")}})
    _write(os.path.join(ASSETS, "models/block/gate_mirror_cracked.json"), bright_model("gate_mirror_cracked"))
    _write(os.path.join(ASSETS, "models/block/gate_well_rim.json"), glow_model("gate_well_rim"))
    for name in ("gate_mirror_cracked", "gate_well_rim"):
        _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {"": {"model": b(name)}}})
    for name in NAMES:
        _write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
            "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for name in NAMES:
        _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": b(name)}})
    for name, (_, n) in PARTICLES.items():
        _write(os.path.join(ASSETS, f"particles/{name}.json"), {"textures": [f"awaken:{name}_{i}" for i in range(n)]})
    lang = {f"block.awaken.{k}": v for k, v in NAMES.items()}
    lang.update({
        "death.attack.awaken.gate_light": "%1$s was burned by the light",
        "death.attack.awaken.gate_light.player": "%1$s was burned by the light %2$s turned on them",
        "death.attack.awaken.gate_rubble": "%1$s was buried as the gate came down",
        "death.attack.awaken.gate_rubble.player": "%1$s was buried as the gate came down, fleeing %2$s",
    })
    _merge(os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json"), lang)
    _merge(os.path.join(MERGE, "gates/data/minecraft/tags/block/mineable/pickaxe.json"),
           {"values": ["awaken:gate_mirror_cracked", "awaken:gate_well_rim"]})
    print("gate_rig: content written for", len(NAMES), "blocks and", len(PARTICLES), "particles")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
