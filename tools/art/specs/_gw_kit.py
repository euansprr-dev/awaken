"""Shared kit for the gate world themes after the Bell Crypt (SPEC §38.10 on): texture helpers drawn by code, and the
content writer (models by shape, blockstates, items, loot, lang, tags) for a theme's block table, which is the same table
as dev.awaken.gates.block.ThemeBlocks.DEFS. Imported by tools/art/specs/gate_worlds_<theme>.py; build_all skips it.

Shapes: cube, column, glass, plant, hang, carpet, fall, touch, prop, set. A block may have a glow texture (only its
bright texels, the rest clear) drawn over it at full light, so only those parts glow and Lens blooms them.
"""
from __future__ import annotations

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge")
SIDES = ("down", "up", "north", "south", "west", "east")
CLEAR = (0, 0, 0, 0)


def px(ramps):
    import pixelart as P
    P.RAMPS.update(ramps)
    return P


def rgba(h, a=255):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---- textures ---------------------------------------------------------------------------------------------------------

def noisy(P, ramp, seed, scale=3, octaves=3, lo=0, span=None, jitter=0.12):
    """A ramp shaded by tileable value noise, a little grain on top."""
    rp = P.ramp(ramp)
    span = len(rp) - lo if span is None else span
    rng = P.rng_for("gw", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=scale, octaves=octaves)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = n[y][x] + (rng.random() - 0.5) * jitter
            c.set(x, y, rp[max(0, min(len(rp) - 1, lo + int(v * span)))])
    return c


def walk(P, seed, n, length, start=None, turn=0.35, wrap=True):
    """Random walks across the tile (cracks, veins, threads): lists of (x, y) points."""
    rng = P.rng_for("walk", seed)
    out = []
    for i in range(n):
        x, y = (rng.randrange(16), rng.randrange(16)) if start is None else start
        a = rng.random() * math.tau
        pts = []
        for _ in range(length):
            pts.append((int(x) % 16 if wrap else int(x), int(y) % 16 if wrap else int(y)))
            a += (rng.random() - 0.5) * turn * 2
            x += math.cos(a)
            y += math.sin(a)
        out.append(pts)
    return out


def lines(c, paths, col, shade=None):
    for pts in paths:
        for x, y in pts:
            c.set(x, y, col)
            if shade is not None:
                c.set(x + 1, y, shade) if c.get(x + 1, y) != col else None


def glow_of(paths, cols, halo=None):
    """A glow layer: the paths bright (cols cycles along each), an optional dim halo round them."""
    import pixelart as P
    g = P.Canvas(16, 16)
    if halo is not None:
        for pts in paths:
            for x, y in pts:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if g.get((x + dx) % 16, (y + dy) % 16)[3] == 0:
                        g.set((x + dx) % 16, (y + dy) % 16, halo)
    for pts in paths:
        for i, (x, y) in enumerate(pts):
            g.set(x, y, cols[(i // 2) % len(cols)])
    return g


def dots(P, seed, n, cols, size=1):
    """A glow layer of small dots (spores, cells, glints)."""
    rng = P.rng_for("dots", seed)
    g = P.Canvas(16, 16)
    for _ in range(n):
        x, y = rng.randrange(16), rng.randrange(16)
        col = cols[rng.randrange(len(cols))]
        for dx in range(size):
            for dy in range(size):
                g.set((x + dx) % 16, (y + dy) % 16, col)
    return g


def under(base, glow, dim=0.55):
    """The base with the glow drawn into it, dimmer (what you see where the glow layer's bright parts sit)."""
    c = base.copy()
    for y in range(16):
        for x in range(16):
            g = glow.get(x, y)
            if g[3]:
                c.blend(x, y, g, dim)
    return c


def boards(P, ramp, seed, h=4, nails=True):
    """Planks: boards {h} high, staggered ends, grain along them, a nail at each end."""
    rp = P.ramp(ramp)
    rng = P.rng_for("boards", ramp, seed)
    c = P.Canvas(16, 16)
    for row in range(16 // h):
        shade = rng.randrange(1, len(rp) - 2)
        cut = rng.randrange(4, 12)
        for y in range(row * h, row * h + h):
            for x in range(16):
                v = shade + (1 if (x * 7 + y * 3 + row) % 9 == 0 else 0) - (1 if (x + row * 5) % 13 == 0 else 0)
                if y == row * h + h - 1:
                    v = 0
                if x == cut:
                    v = 0
                c.set(x, y, rp[max(0, min(len(rp) - 1, v))])
        if nails:
            c.set((cut + 2) % 16, row * h + 1, rp[-1])
            c.set((cut - 2) % 16, row * h + 1, rp[-1])
    return c


def bark(P, ramp, seed, vertical=True):
    """Wood or root side: grain running along the axis, darker furrows, a knot."""
    rp = P.ramp(ramp)
    rng = P.rng_for("bark", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=2, octaves=2)
    c = P.Canvas(16, 16)
    furrows = [rng.randrange(16) for _ in range(4)]
    for y in range(16):
        for x in range(16):
            u = x if vertical else y
            w = y if vertical else x
            v = 1 + int(n[w][u] * (len(rp) - 2))
            if any(abs(((u + int(math.sin(w * 0.6 + f) * 1.2)) - f)) < 1 for f in furrows):
                v = 0
            c.set(x, y, rp[max(0, min(len(rp) - 1, v))])
    kx, ky = rng.randrange(3, 13), rng.randrange(3, 13)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        c.set(kx + dx, ky + dy, rp[0])
    return c


def rings(P, ramp, bark_ramp, seed):
    """A cut end: rings round a heart, bark at the edge."""
    rp, bp = P.ramp(ramp), P.ramp(bark_ramp)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.2:
                c.set(x, y, bp[1])
            else:
                c.set(x, y, rp[(int(d * 1.3) % 2) + (len(rp) - 3 if d < 2 else 1)])
    return c


def ashlar(P, ramp, seed, course=8, mortar=0):
    """Cut stone in courses {course} high, joints staggered, each block shaded a little differently."""
    rp = P.ramp(ramp)
    rng = P.rng_for("ashlar", ramp, seed)
    base = noisy(P, ramp, seed, scale=3, lo=1, span=len(rp) - 2)
    c = base.copy()
    for row in range(16 // course):
        off = rng.randrange(16)
        for y in range(row * course, row * course + course):
            for x in range(16):
                if y == row * course + course - 1 or (x + off) % 16 == 0 or (row % 2 == 0 and (x + off) % 8 == 0 and course < 8):
                    c.set(x, y, rp[mortar])
                elif y == row * course:
                    c.blend(x, y, rp[-1], 0.35)
    return c


def tiles(P, ramp, seed, size=8):
    """Square floor tiles {size} across, grout lines, a crack or two."""
    rp = P.ramp(ramp)
    c = noisy(P, ramp, seed, scale=2, lo=1, span=len(rp) - 2)
    for y in range(16):
        for x in range(16):
            if x % size == 0 or y % size == 0:
                c.set(x, y, rp[0])
            elif x % size == 1 or y % size == 1:
                c.blend(x, y, rp[-1], 0.3)
    lines(c, walk(P, seed + "crack", 2, 5, turn=0.5), rp[0])
    return c


def hexes(P, rim, cell, seed, fill=None, size=4.0):
    """A comb: six-sided cells {size} across, rims of {rim}; fill(i, j, x, y, edge) gives a cell's colour (None: cell)."""
    rr, cc = P.ramp(rim), P.ramp(cell)
    rng = P.rng_for("hex", seed)
    c = P.Canvas(16, 16)
    cells = {}
    for y in range(16):
        for x in range(16):
            row = int(y // (size * 0.87))
            fu = x / size + (0.5 if row % 2 else 0)
            col = int(fu)
            du, dv = abs(fu - col - 0.5), abs(y / (size * 0.87) - row - 0.5)
            edge = du > 0.38 or dv > 0.36 or du * 0.5 + dv > 0.5
            key = (col % int(16 / size), row % int(16 / (size * 0.87) + 0.5))
            if key not in cells:
                cells[key] = rng.random()
            if edge:
                c.set(x, y, rr[1 + (x + y) % 2])
            else:
                col_ = fill(key, cells[key], x, y, du + dv) if fill else None
                c.set(x, y, col_ if col_ else cc[min(len(cc) - 1, 1 + int((1 - (du + dv)) * (len(cc) - 2)))])
    return c


def sprite(rows, pal):
    """A 16x16 sprite from 16 strings; '.' is clear."""
    import pixelart as P
    c = P.Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal:
                c.set(x, y, rgba(pal[ch]) if isinstance(pal[ch], str) else pal[ch])
    return c


def only(c, keys_cols):
    """The parts of a sprite whose colours are in keys_cols (its glow layer)."""
    import pixelart as P
    g = P.Canvas(16, 16)
    wanted = set(keys_cols)
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[:3] in wanted:
                g.set(x, y, c.get(x, y))
    return g


def streaks(P, ramp, seed, frames=8, alpha=170, emissive=True):
    """A falling curtain (glowing water, molten stone): bright streaks that move down a frame at a time."""
    rp = P.ramp(ramp)
    rng = P.rng_for("streaks", seed)
    cols = [(rng.randrange(16), rng.random()) for _ in range(9)]
    out = []
    for f in range(frames):
        c = P.Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                v = 0.3
                for cx, ph in cols:
                    if abs(x - cx) <= 1:
                        k = ((y - f * 2) / 16.0 + ph) % 1.0
                        v = max(v, 1 - abs(x - cx) * 0.5 - k * 0.6)
                col = rp[max(0, min(len(rp) - 1, int(v * (len(rp) - 1))))]
                c.set(x, y, (*col[:3], int(alpha * (0.55 + v * 0.45))))
        out.append(c)
    return out


# ---- models -----------------------------------------------------------------------------------------------------------

B = "awaken:block/"


def faces(tex, cull=True, uv=None):
    out = {}
    for d in SIDES:
        f = {"texture": tex}
        if cull:
            f["cullface"] = d
        if uv:
            f["uv"] = uv
        out[d] = f
    return out


def cube_el(tex, emission=0, cull=True):
    e = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces(tex, cull)}
    if emission:
        e["light_emission"] = emission
        e["shade_direction_override"] = "up"
    return e


def box(a, b, tex, emission=0, sides=SIDES, uv=None):
    e = {"from": list(a), "to": list(b), "faces": {}}
    for d in sides:
        f = {"texture": tex}
        if uv is not None:
            f["uv"] = uv
        e["faces"][d] = f
    if emission:
        e["light_emission"] = emission
    return e


def plane(a, b, tex, emission=0):
    e = {"from": list(a), "to": list(b), "faces": {}}
    flat = [i for i in range(3) if a[i] == b[i]][0]
    for d in {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}[flat]:
        e["faces"][d] = {"texture": tex}
    if emission:
        e["light_emission"] = emission
    return e


def cross(tex, emission=0, inset=0.8, y0=0, y1=16, uv=None):
    """Vanilla's crossed planes (or a smaller pair: inset from the sides, y0 to y1, showing uv of the texture)."""
    lo, hi = inset, 16 - inset
    em = {"light_emission": emission} if emission else {}
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
    uv = uv or [0, 0, 16, 16]
    return [{"from": [lo, y0, 8], "to": [hi, y1, 8], "rotation": rot, "shade_direction_override": "up",
             "faces": {"north": {"uv": uv, "texture": tex}, "south": {"uv": uv, "texture": tex}}, **em},
            {"from": [8, y0, lo], "to": [8, y1, hi], "rotation": rot, "shade_direction_override": "up",
             "faces": {"west": {"uv": uv, "texture": tex}, "east": {"uv": uv, "texture": tex}}, **em}]


def model(textures, elements, ao=True, particle=None):
    t = dict(textures)
    t.setdefault("particle", particle or next(v if isinstance(v, str) else v["sprite"] for v in textures.values()))
    m = {"parent": "minecraft:block/block", "textures": t, "elements": elements}
    if not ao:
        m["ambientocclusion"] = False
    return m


def cube_model(name, glow=False, top=None, bottom=None, translucent=False):
    """A full block: one texture (or top and bottom), with its glow layer over it when it has one."""
    if top is None and not glow and not translucent:
        return {"parent": "minecraft:block/cube_all", "textures": {"all": B + name}}
    if not glow and top is not None:
        return {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": B + top, "bottom": B + (bottom or name), "side": B + name}}
    tex = {"side": B + name, "particle": B + name}
    if translucent:
        tex["side"] = {"sprite": B + name, "force_translucent": True}
    els = []
    f = faces("#side")
    if top is not None:
        tex["top"], tex["bottom"] = B + top, B + (bottom or name)
        f["up"]["texture"], f["down"]["texture"] = "#top", "#bottom"
    els.append({"from": [0, 0, 0], "to": [16, 16, 16], "faces": f})
    if glow:
        tex["glow"] = B + name + "_glow"
        g = faces("#glow")
        if top is not None:
            tex["glow_top"] = B + top + "_glow"
            g["up"]["texture"] = "#glow_top"
            del g["down"]
        els.append({"from": [0, 0, 0], "to": [16, 16, 16], "faces": g, "light_emission": 15, "shade_direction_override": "up"})
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def column_model(name, end, glow=False):
    if not glow:
        return {"parent": "minecraft:block/cube_column", "textures": {"side": B + name, "end": B + end}}
    f = faces("#side")
    f["up"]["texture"] = f["down"]["texture"] = "#end"
    return {"parent": "minecraft:block/block", "textures": {"particle": B + name, "side": B + name, "end": B + end, "glow": B + name + "_glow"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": f},
                         {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#glow"), "light_emission": 15}]}


def cross_model(name, glow=False):
    tex = {"particle": B + name, "plant": B + name}
    els = cross("#plant")
    if glow:
        tex["glow"] = B + name + "_glow"
        els += cross("#glow", 15)
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}


def carpet_model(name, glow=False):
    tex = {"particle": B + name, "top": B + name}
    els = [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {"up": {"texture": "#top"}, "down": {"texture": "#top", "cullface": "down"},
                                                             "north": {"texture": "#top", "uv": [0, 15, 16, 16]},
                                                             "south": {"texture": "#top", "uv": [0, 15, 16, 16]},
                                                             "west": {"texture": "#top", "uv": [0, 15, 16, 16]},
                                                             "east": {"texture": "#top", "uv": [0, 15, 16, 16]}}}]
    if glow:
        tex["glow"] = B + name + "_glow"
        els.append({"from": [0, 1.02, 0], "to": [16, 1.02, 16], "faces": {"up": {"texture": "#glow"}}, "light_emission": 15})
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}


def fall_model(name, emission=15):
    t = {"sprite": B + name, "force_translucent": True}
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"particle": B + name, "fall": t},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#fall", cull=False), **({"light_emission": emission} if emission else {})}]}


# ---- content ----------------------------------------------------------------------------------------------------------

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    write(path, cur)


def loot(name):
    write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
        "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


TOOLS = {"pickaxe": "mineable/pickaxe", "axe": "mineable/axe", "shovel": "mineable/shovel", "hoe": "mineable/hoe"}


def content(theme, blocks, models, states, lang, climbable=(), flat_items=()):
    """Writes a theme's content. blocks: name -> (English, tool or None); models: name -> json; states: name -> json."""
    for name, data in models.items():
        write(os.path.join(ASSETS, f"models/block/{name}.json"), data)
    for name, data in states.items():
        write(os.path.join(ASSETS, f"blockstates/{name}.json"), data)
    for name in blocks:
        if name in flat_items:
            write(os.path.join(ASSETS, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": B + name}})
            ref = "awaken:item/" + name
        else:
            ref = B + (name if name in models else next(k for k in models if k.startswith(name)))
        write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": ref}})
        loot(name)
    path = os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json")
    merge(path, {f"block.awaken.{k}": v[0] for k, v in blocks.items()})
    merge(path, lang)
    for tool, tag in TOOLS.items():
        vals = [f"awaken:{k}" for k, v in blocks.items() if v[1] == tool]
        if vals:
            merge(os.path.join(MERGE, f"gates/data/minecraft/tags/block/{tag}.json"), {"values": vals})
    if climbable:
        merge(os.path.join(MERGE, "gates/data/minecraft/tags/block/climbable.json"), {"values": [f"awaken:{k}" for k in climbable]})
    print(f"{theme}: content written for {len(blocks)} blocks")


def simple_state(name, model_name=None):
    return {"variants": {"": {"model": B + (model_name or name)}}}


def axis_state(name):
    m = B + name
    return {"variants": {"axis=y": {"model": m}, "axis=z": {"model": m, "x": 90}, "axis=x": {"model": m, "x": 90, "y": 90}}}


def lit_state(name):
    return {"variants": {"lit=false": {"model": B + name}, "lit=true": {"model": B + name + "_lit"}}}


def sheet(textures, path):
    from PIL import Image, ImageDraw
    items = [(n, (c[0] if isinstance(c, list) else c).image()) for n, c in textures.items()]
    S, cols = 96, 8
    rows = (len(items) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (S + 16), rows * (S + 30) + 10), (40, 42, 50, 255))
    d = ImageDraw.Draw(img)
    for i, (name, im) in enumerate(items):
        x, y = (i % cols) * (S + 16) + 8, (i // cols) * (S + 30) + 8
        big = im.resize((S, S), Image.NEAREST)
        img.paste(big, (x, y), big)
        d.text((x, y + S + 2), name[:18], fill=(220, 220, 230, 255))
    img.save(path)
