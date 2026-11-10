"""The Brood family (SPEC 50.9): what a nest of the Hive is made of. Grey nest paper laid down in layers, dark resin underfoot,
comb with something alive in it, sacs hung from every roof, a skin over each doorway.

    python3 tools/art/build_all.py realm_brood        textures
    python3 tools/art/specs/realm_brood.py content    blockstates, models, item definitions, loot, lang, tags (lang and tags into src/main/merge/gates_brood)
    python3 tools/art/tone_check.py brood_            the family must stay lightable
    python3 tools/art/specs/realm_brood.py sheet PATH every texture at 6x, named

Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402

# Mid-toned on purpose (SPEC 50.8, "Textures that can be lit"): the nest is dark because little light reaches it, never
# because its paper is near black. The glow ramps are the only bright ones.
RAMPS = {
    "paper": ["#5a4e40", "#6e6050", "#827260", "#968672", "#aa9a86", "#beae9a"],
    "paperd": ["#463a30", "#56483c", "#665648", "#766454", "#867260", "#96826e"],
    "paperp": ["#7a6c58", "#8e8068", "#a2947a", "#b6a88c", "#cabc9e", "#ded0b2"],
    "resin": ["#4e2414", "#66301a", "#7e3e20", "#964e28", "#ae6032", "#c6743e"],
    "earth": ["#40342a", "#504234", "#605040", "#705e4c", "#806c58", "#907a64"],
    "larva": ["#5c1408", "#86200c", "#b03410", "#d8521c", "#f47a34", "#ffb070"],
    "jelly": ["#5a6a1c", "#7a8a28", "#9aaa3a", "#bac850", "#d6e070", "#eef4a0"],
    "skin": ["#6a1c14", "#8a2a1c", "#aa3c26", "#c85434", "#e07048", "#f09468"],
}

BLOCKS = {
    "brood_paper": ("Nest Paper", "hoe"),
    "brood_paper_dark": ("Damp Nest Paper", "hoe"),
    "brood_paper_pale": ("Dry Nest Paper", "hoe"),
    "brood_earth": ("Rooted Earth", "shovel"),
    "brood_resin": ("Nest Resin", "pickaxe"),
    "brood_resin_slab": ("Nest Resin Slab", "pickaxe"),
    "brood_cell": ("Brood Comb", "hoe"),
    "brood_cell_lit": ("Stirring Brood Comb", "hoe"),
    "brood_sac_drip": ("Brood Sac", None),
    "brood_membrane": ("Nest Skin", "hoe"),
    "brood_jelly": ("Royal Jelly", "hoe"),
    "brood_spitter": ("Spitter Cell", "hoe"),
    "brood_spitter_lit": ("Roused Spitter Cell", "hoe"),
}

B = K.B


def _P():
    return K.px(RAMPS)


def clear(P):
    return P.Canvas(16, 16)


def mottle(P, ramp, seed, lo=1.0, span=3.0, flecks=8):
    rp = P.ramp(ramp)
    rng = P.rng_for("mottle", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=4, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[max(0, min(5, int(lo + n[y][x] * span)))])
    for _ in range(flecks):
        c.set(rng.randrange(16), rng.randrange(16), rp[min(5, int(lo + span) + 1)])
    return c


def tex_paper(P, ramp, seed):
    """Nest paper: thin layers laid one over another, each a slightly different pulp, their edges wandering. Tiles both ways."""
    rp = P.ramp(ramp)
    rng = P.rng_for("paper", ramp, seed)
    tone = [1 + rng.randrange(4) for _ in range(8)]
    phase = [rng.random() * 6.28 for _ in range(8)]
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            # two whole waves across the block, so the edge of a layer meets itself on the next block
            band = int(((y + 1.3 * math.sin(x * math.pi / 4 + phase[(y // 2) % 8])) % 16) // 2) % 8
            edge = int(((y + 1 + 1.3 * math.sin(x * math.pi / 4 + phase[((y + 1) // 2) % 8])) % 16) // 2) % 8 != band
            v = tone[band] - (1 if edge else 0) + (1 if rng.random() < 0.06 else 0)
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_earth(P):
    """Dark soil with pale root hairs through it."""
    c = mottle(P, "earth", "earth", lo=0.8, span=3.2, flecks=4)
    rng = P.rng_for("roots")
    rp = P.ramp("paperp")
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(3 + rng.randrange(4)):
            c.set(x % 16, y % 16, rp[1 + rng.randrange(2)])
            x += rng.choice((-1, 0, 1))
            y += 1
    return c


def _hex_dist(x, y, cx, cy):
    dx, dy = abs(x - cx), abs(y - cy)
    return max(dy, 0.866 * dx + 0.5 * dy)


CENTRES = [(4, 3), (12, 3), (0, 11), (8, 11), (16, 11), (4, 19), (12, 19), (4, -5), (12, -5)]


def tex_comb(P, lit):
    """Brood comb: paper ridges round capped cells. Lit, three of the cells have something awake behind the cap; the glow layer is those alone."""
    # a lit cell lights its own block, so its ridges are drawn darker: beside sleeping comb in the half dark it must read as the same comb with embers in it, not as a pale box
    rim, cap, gl = P.ramp("paperd" if lit else "paperp"), P.ramp("paperd"), P.ramp("larva")
    rng = P.rng_for("comb", lit)
    awake = {(4, 3), (8, 11), (12, 19), (12, -5)} if lit else set()
    c, g = P.Canvas(16, 16), clear(P)
    for y in range(16):
        for x in range(16):
            cc = min(CENTRES, key=lambda q: _hex_dist(x + 0.5, y + 0.5, q[0], q[1]))
            d = _hex_dist(x + 0.5, y + 0.5, cc[0], cc[1])
            if d > 3.3:
                c.set(x, y, rim[(0 if lit else 3) + (1 if rng.random() < 0.3 else 0)])
            elif d > 2.6:
                c.set(x, y, P.ramp("earth")[0] if lit else rim[1])
            elif cc in awake:
                v = gl[max(1, min(5, int(5.4 - d * 1.3 + rng.random() * 0.8)))]
                c.set(x, y, v)
                g.set(x, y, v)
            else:
                c.set(x, y, cap[max(0, min(5, int(2.6 - d * 0.5 + rng.random() * 1.2)))])
    return c, g


def tex_sac(P):
    """A sac on its thread, seen side on: a dark skin, and inside it the light of whatever is growing."""
    skin, gl, thread = P.ramp("skin"), P.ramp("larva"), P.ramp("paperp")
    c, g = clear(P), clear(P)
    for y in range(0, 4):
        c.set(7, y, thread[2])
        c.set(8, y, thread[1])
    for y in range(3, 16):
        t = (y - 3) / 12.0
        w = 1.2 + 4.2 * math.sin(min(1.0, t * 1.15) * math.pi) ** 0.8
        for x in range(16):
            d = abs(x + 0.5 - 8) / w
            if d > 1:
                continue
            if d > 0.72:
                c.set(x, y, skin[1 if x < 8 else 0])
            else:
                v = gl[max(1, min(5, int(4.8 - d * 2.6 - abs(t - 0.6) * 2.2)))]
                c.set(x, y, v)
                g.set(x, y, v)
    return c, g


def tex_membrane(P):
    """A skin grown across a doorway: red, veined, and you can very nearly see through it."""
    rp = P.ramp("skin")
    rng = P.rng_for("membrane")
    n = P.value_noise(16, 16, rng, scale=5, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r, g_, b, _ = rp[max(0, min(5, int(1 + n[y][x] * 3)))]
            c.set(x, y, (r, g_, b, 205))
    for sx in (3, 9, 13):
        x = sx
        for y in range(16):
            r, g_, b, _ = rp[0]
            c.set(x % 16, y, (r, g_, b, 240))
            if rng.random() < 0.35:
                x += rng.choice((-1, 1))
    return c


def tex_jelly(P):
    c = mottle(P, "jelly", "jelly", lo=1.6, span=3.0, flecks=10)
    return c


def tex_spitter(P, lit):
    """A cell with a mouth: a ring of pale paper round a dark throat. Roused, the throat is full of what it is about to throw."""
    rim, cap, gl = P.ramp("paperp"), P.ramp("paperd"), P.ramp("jelly")
    rng = P.rng_for("spitter", lit)
    c, g = P.Canvas(16, 16), clear(P)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d > 6.4:
                c.set(x, y, rim[2 + (1 if rng.random() < 0.3 else 0)])
            elif d > 4.6:
                c.set(x, y, rim[4 if (x + y) % 3 else 3])
            elif lit:
                v = gl[max(1, min(5, int(5.6 - d * 0.8 + rng.random() * 0.6)))]
                c.set(x, y, v)
                g.set(x, y, v)
            else:
                c.set(x, y, cap[max(0, min(5, int(d * 0.45 + rng.random() * 0.8)))])
    return c, g


def textures():
    P = _P()
    comb, _ = tex_comb(P, False)
    comb_lit, comb_g = tex_comb(P, True)
    sac, sac_g = tex_sac(P)
    spit, _ = tex_spitter(P, False)
    spit_lit, spit_g = tex_spitter(P, True)
    jelly = tex_jelly(P)
    return {
        "brood_paper": tex_paper(P, "paper", "a"),
        "brood_paper_dark": tex_paper(P, "paperd", "b"),
        "brood_paper_pale": tex_paper(P, "paperp", "c"),
        "brood_earth": tex_earth(P),
        "brood_resin": mottle(P, "resin", "resin", lo=1.0, span=3.0, flecks=10),
        "brood_cell": comb,
        "brood_cell_lit": comb_lit, "brood_cell_lit_glow": comb_g,
        "brood_sac_drip": sac, "brood_sac_drip_glow": sac_g,
        "brood_membrane": tex_membrane(P),
        "brood_jelly": jelly, "brood_jelly_glow": jelly,
        "brood_spitter": spit,
        "brood_spitter_lit": spit_lit, "brood_spitter_lit_glow": spit_g,
    }


def build(out):
    for name, c in textures().items():
        out.block(name, c)


RESIN = B + "brood_resin"


def models():
    simple = ("brood_paper", "brood_paper_dark", "brood_paper_pale", "brood_earth", "brood_resin", "brood_cell", "brood_spitter")
    m = {n: K.cube_model(n) for n in simple}
    for n in ("brood_cell_lit", "brood_jelly", "brood_spitter_lit"):
        m[n] = K.cube_model(n, glow=True)
    m["brood_membrane"] = K.cube_model("brood_membrane", translucent=True)
    m["brood_sac_drip"] = K.cross_model("brood_sac_drip", glow=True)
    tex = {"bottom": RESIN, "top": RESIN, "side": RESIN}
    m["brood_resin_slab"] = {"parent": "minecraft:block/slab", "textures": tex}
    m["brood_resin_slab_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n != "brood_resin_slab"}
    s["brood_resin_slab"] = {"variants": {"type=bottom": {"model": B + "brood_resin_slab"}, "type=top": {"model": B + "brood_resin_slab_top"}, "type=double": {"model": RESIN}}}
    return s


def content():
    """As K.content, but the shared words and tags go to this gate's own merge folder, so no two gates ever write one file."""
    for name, data in models().items():
        K.write(os.path.join(K.ASSETS, f"models/block/{name}.json"), data)
    for name, data in states().items():
        K.write(os.path.join(K.ASSETS, f"blockstates/{name}.json"), data)
    for name in BLOCKS:
        K.write(os.path.join(K.ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": B + name}})
        K.loot(name)
    D.slab_loot("brood_resin_slab")
    own = os.path.join(K.MERGE, "gates_brood")
    K.write(os.path.join(own, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v[0] for k, v in BLOCKS.items()})
    for tool, tag in K.TOOLS.items():
        vals = [f"awaken:{k}" for k, v in BLOCKS.items() if v[1] == tool]
        if vals:
            K.write(os.path.join(own, f"data/minecraft/tags/block/{tag}.json"), {"values": vals})
    for what in ("block", "item"):
        K.write(os.path.join(own, f"data/minecraft/tags/{what}/slabs.json"), {"values": ["awaken:brood_resin_slab"]})
    # a bell rope is a rope: you can go up and down one (the Hour Crypt lets one down from the bridge to the bell, SPEC 50.9)
    K.write(os.path.join(K.MERGE, "gates_run", "data/minecraft/tags/block/climbable.json"), {"values": ["awaken:crypt_bell_rope"]})
    print(f"realm_brood: content written for {len(BLOCKS)} blocks")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_brood.png")
    else:
        print(__doc__)
