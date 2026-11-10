"""The Quiet Barrow's own blocks (SPEC 50.8, gate 6): what a moor of burial mounds needs that the Knell family doesn't have. Heather and
bent grass, peat turf, the cap of a standing stone, kerb stones, the shields along the stone ship's side, burial urns, the pale green
light in the hold and the ship's ragged sail.

    python3 tools/art/build_all.py realm_barrow        textures
    python3 tools/art/specs/realm_barrow.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_barrow.py sheet PATH every texture at 6x, named

Drawn by code, nothing copied. Same table as the Barrow part of ThemeBlocks.DEFS. The stone is Knell's, lifted the way Knell lifts it, so
a standing stone's cap matches the stone under it. Mid-toned on purpose: the dusk comes from the light (tools/art/tone_check.py barrow_).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import realm_knell as KN  # noqa: E402

RAMPS = dict(KN.RAMPS)
RAMPS.update({
    # look round 1: a dusty mauve over dark olive, dry straw that isn't white, peat that is brown
    "heath": ["#4a3f4c", "#5c4e5e", "#6f5f72", "#837186", "#97859a", "#ab9aae"],
    "heathdry": ["#6f6850", "#847c5f", "#99906f", "#ada480", "#bfb692", "#cfc7a5"],
    "stem": ["#2f3425", "#3b412d", "#484f37", "#565d41", "#656c4d", "#747b59"],
    "straw": ["#5e5638", "#726944", "#877d52", "#9b9161", "#aea472", "#c0b685"],
    "bent": ["#6d6442", "#83794f", "#998e5e", "#ada26e", "#c0b580", "#d1c794"],
    "peat": ["#3b2e23", "#4a3a2c", "#5a4736", "#6a5541", "#7b634d", "#8c725a"],
    "clay": ["#6a4a3a", "#825a46", "#9a6c54", "#b08064", "#c49678", "#d6ae90"],
    "cloth": ["#5a3230", "#70403c", "#874f48", "#9d6056", "#b27466", "#c58a7a"],
    "grave": ["#4c8a6a", "#62a882", "#7cc49a", "#9cdcb4", "#c0f0d0", "#e4ffee"],
    "board": ["#5a4a3a", "#6e5b47", "#836d55", "#978064", "#ab9475", "#bea888"],
})



def _dim(name, f):
    """A ramp taken down in brightness, hue kept: how the plants were brought to the tones look round 1 asks for."""
    RAMPS[name] = ["#%02x%02x%02x" % tuple(min(255, int(int(h[i:i + 2], 16) * f)) for i in (1, 3, 5)) for h in RAMPS[name]]


_dim("heath", 0.75)
_dim("heathdry", 0.74)
_dim("straw", 0.85)
_dim("bent", 0.79)

BLOCKS = {
    "barrow_heather": ("Barrow Heather", None),
    "barrow_heather_pale": ("Dry Barrow Heather", None),
    "barrow_bent": ("Moor Bent", None),
    "barrow_turf": ("Barrow Turf", "shovel"),
    "barrow_menhir_cap": ("Standing Stone Cap", "pickaxe"),
    "barrow_kerb": ("Kerb Stone", "pickaxe"),
    "barrow_shield": ("Stone Ship Shield", "axe"),
    "barrow_shield_south": ("Stone Ship Shield", "axe"),
    "barrow_urn": ("Burial Urn", "pickaxe"),
    "barrow_grave_glow": ("Grave Light", "pickaxe"),
    "barrow_sail": ("Ragged Sailcloth", None),
}
PLANTS = ("barrow_heather", "barrow_heather_pale", "barrow_bent")

B = K.B


def _P():
    return K.px(RAMPS)


def tex_heather(P, flower, seed, stem="stem"):
    """Knee-high sprigs: wiry stems that lean, flowers crowded up the top two thirds of each."""
    st, fl = P.ramp(stem), P.ramp(flower)
    rng = P.rng_for("heather", seed)
    c = P.Canvas(16, 16)
    for i in range(7):
        x = 1 + i * 2 + rng.randrange(0, 2)
        top = rng.randrange(2, 7)
        lean = rng.choice((-1, 0, 0, 1))
        for y in range(15, top - 1, -1):
            xx = max(0, min(15, x + (lean * (15 - y)) // 6))
            c.set(xx, y, st[1 + (y + i) % 3])
            if y < 12 and rng.random() < 0.8:
                side = xx + rng.choice((-1, 1))
                if 0 <= side <= 15:
                    c.set(side, y, fl[2 + rng.randrange(0, 4)])
            if y < 10 and rng.random() < 0.5:
                c.set(xx, y, fl[3 + rng.randrange(0, 3)])
        c.set(max(0, min(15, x + (lean * (15 - top)) // 6)), top, fl[5])
    return c


def tex_bent(P):
    """Moor grass gone pale: thin blades, some bowed over at the tip."""
    rp = P.ramp("bent")
    rng = P.rng_for("bent")
    c = P.Canvas(16, 16)
    for i in range(9):
        x = rng.randrange(1, 15)
        top = rng.randrange(1, 8)
        bow = rng.choice((-1, 1))
        for y in range(15, top - 1, -1):
            xx = x + (bow if y < top + 3 else 0)
            if 0 <= xx <= 15:
                c.set(xx, y, rp[min(5, 1 + (15 - y) // 4 + rng.randrange(0, 2))])
        tip = x + 2 * bow
        if 0 <= tip <= 15:
            c.set(tip, top + 1, rp[5])
    return c


def tex_turf(P, top):
    """Peat: brown and fibrous at the cut side; on top, the mat of heather roots with dead sprigs and a little green."""
    c = K.noisy(P, "peat", "turf-top" if top else "turf", scale=3, octaves=3, lo=1, span=3, jitter=0.2)
    rng = P.rng_for("turf", top)
    st, he = P.ramp("stem"), P.ramp("heath")
    if top:
        for _ in range(34):
            x, y = rng.randrange(16), rng.randrange(16)
            c.blend(x, y, st[2 + rng.randrange(0, 3)], 0.7)
        for _ in range(9):
            x, y = rng.randrange(16), rng.randrange(16)
            c.blend(x, y, he[2 + rng.randrange(0, 2)], 0.6)
    else:
        for x in range(16):
            c.blend(x, 0, st[3], 0.75)
            if rng.random() < 0.6:
                c.blend(x, 1, st[2], 0.55)
        for _ in range(8):
            x, y = rng.randrange(16), rng.randrange(3, 16)
            c.blend(x, y, P.ramp("peat")[0], 0.6)
            c.blend(x, min(15, y + 1), P.ramp("peat")[0], 0.35)
    return c


def tex_shield(P):
    """A round shield face on: boards, an iron rim, a gilt boss with its four rivets. Outside the round is clear."""
    bd, ir, gi = P.ramp("board"), P.ramp("iron"), P.ramp("agilt")
    rng = P.rng_for("shield")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.4:
                c.set(x, y, bd[1])  # the plate's edges show this where the model's boxes stand proud of the round
            elif d > 6.2:
                c.set(x, y, ir[3 + (x + y) % 2])
            elif d < 2.3:
                c.set(x, y, gi[3 + (1 if x + y < 15 else 0) + (1 if d < 1.2 else 0)])
            else:
                c.set(x, y, bd[1 + (x // 3 + rng.randrange(0, 2)) % 4] if x % 3 else bd[0])
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.set(x, y, gi[4])
    return c


def tex_clay(P):
    """Fired clay with two scratched bands and the ash glaze run down from the shoulder."""
    c = K.noisy(P, "clay", "urn", scale=3, octaves=2, lo=1, span=3, jitter=0.15)
    rp = P.ramp("clay")
    for x in range(16):
        c.set(x, 5, rp[0])
        c.set(x, 6, rp[4])
        c.set(x, 11, rp[0])
    rng = P.rng_for("urn-run")
    for _ in range(4):
        x = rng.randrange(16)
        for y in range(rng.randrange(3, 9)):
            c.blend(x, y, rp[5], 0.5)
    return c


def tex_glow(P):
    c = K.noisy(P, "grave", "glow", scale=4, octaves=2, lo=1, span=4, jitter=0.1)
    rng = P.rng_for("glow-motes")
    for _ in range(10):
        c.set(rng.randrange(16), rng.randrange(16), P.ramp("grave")[5])
    return c


def tex_sail(P):
    """Old sailcloth: a coarse weave in faded red, darker where the seams run, a few worn threads."""
    rp = P.ramp("cloth")
    rng = P.rng_for("sail")
    n = P.value_noise(16, 16, rng, scale=4, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 1 + int(n[y][x] * 3.2) + (1 if (x + y) % 2 == 0 else 0)
            if x % 8 == 0:
                v -= 2
            c.set(x, y, rp[max(0, min(5, v))])
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        for k in range(rng.randrange(2, 5)):
            c.blend(x, min(15, y + k), rp[5], 0.5)
    return c


def textures():
    P = _P()
    return {
        "barrow_heather": tex_heather(P, "heath", "h"), "barrow_heather_pale": tex_heather(P, "heathdry", "p", stem="straw"), "barrow_bent": tex_bent(P),
        "barrow_turf": tex_turf(P, False), "barrow_turf_top": tex_turf(P, True),
        "barrow_menhir_cap": KN.stained(P, KN.cut_block(P, "astone", "cap", lo=2, span=3, chips=6), "amoss", "cap", runs=3),
        "barrow_kerb": KN.cut_block(P, "apale", "kerb", lo=1, span=3, chips=5),
        "barrow_shield": tex_shield(P), "barrow_urn": tex_clay(P), "barrow_grave_glow": tex_glow(P), "barrow_sail": tex_sail(P),
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c)


def models():
    m = {}
    for n in PLANTS:
        m[n] = K.cross_model(n)
    m["barrow_turf"] = K.cube_model("barrow_turf", top="barrow_turf_top", bottom="barrow_turf")
    # a standing stone never ends in a cube: three steps in, 16, 12 and 6 wide
    m["barrow_menhir_cap"] = K.model({"s": B + "barrow_menhir_cap"}, [
        K.box((0, 0, 0), (16, 5, 16), "#s"), K.box((2, 5, 2), (14, 9, 14), "#s"), K.box((5, 9, 5), (11, 12, 11), "#s")])
    # low and rounded: a 12 wide body 10 high, its shoulders taken off by two smaller boxes
    m["barrow_kerb"] = K.model({"s": B + "barrow_kerb"}, [
        K.box((2, 0, 3), (14, 8, 13), "#s"), K.box((3, 8, 4), (13, 10, 12), "#s"), K.box((3, 0, 2), (13, 7, 14), "#s")])
    # an eight-sided plate 14 across and 3 thick against the south side of its cell, the boss standing out to the north
    plate = [K.box((1, 5, 13), (15, 11, 16), "#s"), K.box((3, 3, 13), (13, 13, 16), "#s"), K.box((5, 1, 13), (11, 15, 16), "#s"),
             K.box((6, 6, 11), (10, 10, 13), "#s")]
    m["barrow_shield"] = K.model({"s": B + "barrow_shield"}, plate)
    m["barrow_urn"] = K.model({"s": B + "barrow_urn"}, [
        K.box((5, 0, 5), (11, 2, 11), "#s"), K.box((2, 2, 2), (14, 10, 14), "#s"), K.box((4, 10, 4), (12, 13, 12), "#s"),
        K.box((3, 13, 3), (13, 14, 13), "#s")])
    m["barrow_grave_glow"] = K.model({"all": B + "barrow_grave_glow"}, [K.cube_el("#all", emission=15)])
    # cloth two pixels thick in the middle of its cell, hanging along x
    m["barrow_sail"] = K.model({"s": B + "barrow_sail"}, [K.box((0, 0, 7), (16, 16, 9), "#s")], ao=False)
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n != "barrow_shield_south"}
    s["barrow_shield_south"] = {"variants": {"": {"model": B + "barrow_shield", "y": 180}}}
    return s


def content():
    """As K.content, but the words and tags go in this gate's own merge folder, so three builders never write the same file."""
    ms, st = models(), states()
    for name, data in ms.items():
        K.write(os.path.join(K.ASSETS, f"models/block/{name}.json"), data)
    for name, data in st.items():
        K.write(os.path.join(K.ASSETS, f"blockstates/{name}.json"), data)
    for name in BLOCKS:
        if name in PLANTS:
            K.write(os.path.join(K.ASSETS, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": B + name}})
            ref = "awaken:item/" + name
        else:
            ref = B + (name if name in ms else "barrow_shield")
        K.write(os.path.join(K.ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": ref}})
        K.loot(name)
    own = os.path.join(K.MERGE, "gates_barrow")
    K.merge(os.path.join(own, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v[0] for k, v in BLOCKS.items()})
    for tool, tag in K.TOOLS.items():
        vals = [f"awaken:{k}" for k, v in BLOCKS.items() if v[1] == tool]
        if vals:
            K.merge(os.path.join(own, f"data/minecraft/tags/block/{tag}.json"), {"values": vals})
    print(f"realm_barrow: content written for {len(BLOCKS)} blocks")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_barrow.png")
    else:
        print(__doc__)
