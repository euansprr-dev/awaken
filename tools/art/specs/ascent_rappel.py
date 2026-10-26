"""The rappel and the rope's fittings (SPEC §28.5; TotK's Zonaite: metal for the movement system): the fitted lines'
looks on the rope, the gold reel at a winding hand, the three fittings' icons and item models, their recipes, and the
rappel's third-person clips (Player Animation Library).

    python3 tools/art/build_all.py ascent_rappel        the textures (lines, reel, icons)
    python3 tools/art/specs/ascent_rappel.py content    the textures, item models and definitions, recipes
                                                        (src/main/merge/ascent_rappel), the clips (+ clipping report)
    python3 tools/art/specs/ascent_rappel.py preview DIR   the textures large on dark and daylight, the icons, and a
                                                        frame strip per clip

Layout the renderer expects (dev.awaken.body.ascent.client.AscentRender), as rope.png's (ascent_fx.py): u along the
rope (repeats; a fitted line's repeat is 1.2 blocks, so its lay keeps its pitch at any length), v across it, drawn
translucent and full bright:
- rope_iron.png       steel wire rope: six strands laid round, each lit along its crown, dark in the grooves between;
                      grey-steel with a cool sheen, a dark rim for daylight (the strands only move as the rope runs)
- rope_deepglass.png  black glass drawn into a line: the same lay in violet-black, a thread of violet light down its
                      heart, and a few bright glints in the glass (they drift slowly along it)
- reel.png            the gold reel face on: a gold rim lit from the top left, five spokes, a hub, and steel line wound
                      between them (spun in the view plane at the hand while it winds); premultiplied edges, blurred
Icons (16x16 pixel art in the mod's palette): iron_line (a coil of steel line, a forged hook at its end), gold_reel
(a gold spool, steel wound on it, a crank), deepglass_line (a coil of black glass line, violet glinting in it).

Clips (the game turns the WHOLE figure: AscentAnims leans it back off the wall along the rope, facing the rock, and draws
it half a block out from the collision box, so a body against the wall stands off it on its legs). These pose the
limbs on an upright body:
- ascent_rappel        holding: the rope hand up before the face, the brake hand low at the hip, legs out to the rock,
                       knees soft, a slow breath
- ascent_rappel_walk   paying out: walking down the wall, the legs stepping in turn, the brake hand feeding
- ascent_rappel_kick   the kick (14 ticks): crouched to the rock, the push (legs long), the bound (knees soft), reaching
                       back to plant
Every clip has a _bent twin (Bendable Cuboids). Edit the poses here, never the JSON.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
import pixelart as PX  # noqa: E402
from pixelart import Canvas  # noqa: E402
import power_casts as P  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/ascent_rappel")
TEX = "assets/awaken/textures"
CLIPS_REL = "assets/awaken/player_animations/ascent_rappel.json"

STEEL = ["#1c1e22", "#2e3238", "#474c55", "#68707a", "#9aa2ab", "#d2d8de", "#f2f6fa"]
GOLD = ["#3a2604", "#6e4a0a", "#b07c14", "#e2ac2a", "#ffe27a", "#fff8d8"]
GLASS = ["#07050c", "#120a20", "#24133f", "#3e2470", "#6a45b0", "#b48cff", "#eadcff"]
FITTINGS = ["iron_line", "gold_reel", "deepglass_line"]


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def hx(h, a=255):
    return PX.hex_rgba(h, a)


def _img(col, a):
    out = np.zeros(a.shape + (4,), dtype=np.uint8)
    out[..., :3] = np.clip(col * 255, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def _lay(w, h, strands=6, twist=0.55):
    """The wire rope's lay: per texel, how lit its strand is (0 groove .. 1 crown), the across coordinate, the body."""
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    across = (V - 0.5) * 2
    # strands laid diagonally round the core: three twists a repeat, seen on the near half of the rope
    phase = (U * 3 + across * twist) * strands / 2
    crown = 0.5 + 0.5 * np.cos(phase * 2 * math.pi)
    body = np.clip(1 - np.abs(across) ** 2.4, 0, 1)
    return crown, across, body


def rope_iron():
    w, h = 128, 16
    crown, across, body = _lay(w, h)
    # lit from above: the strands' crowns bright on the upper half, a long specular line just over the middle
    light = 0.45 + 0.55 * np.clip(1 - (across + 0.15) ** 2 * 1.6, 0, 1)
    spec = np.exp(-((across + 0.32) / 0.12) ** 2) * crown ** 3
    val = (0.25 + 0.75 * crown ** 1.4) * light
    lo, mid, hi, top = rgb(STEEL[1]), rgb(STEEL[3]), rgb(STEEL[5]), rgb(STEEL[6])
    col = lo[None, None] * (1 - val[..., None]) + mid[None, None] * val[..., None]
    col = col + (hi - mid)[None, None] * np.clip(val - 0.55, 0, 1)[..., None] * 1.6
    col = col * (1 - spec[..., None]) + top[None, None] * spec[..., None]
    # a cool sheen (the steel holds the Signal's light faintly), and a dark rim so it reads against the sky
    col = col + np.array([0.0, 0.02, 0.05])[None, None] * crown[..., None]
    rim = np.exp(-((np.abs(across) - 0.86) / 0.1) ** 2)
    col = col * (1 - rim[..., None] * 0.6) + rgb(STEEL[0])[None, None] * rim[..., None] * 0.6
    a = np.clip(body * 0.98 + rim * 0.5, 0, 1) * (np.abs(across) < 0.98)
    return _img(np.clip(col, 0, 1), a)


def rope_deepglass():
    w, h = 128, 16
    crown, across, body = _lay(w, h, strands=6, twist=0.5)
    u = (np.arange(w) + 0.5) / w
    U = np.tile(u[None, :], (h, 1))
    val = 0.2 + 0.8 * crown ** 1.6
    dark, mid, violet, white = rgb(GLASS[1]), rgb(GLASS[3]), rgb(GLASS[5]), rgb(GLASS[6])
    col = dark[None, None] * (1 - val[..., None]) + mid[None, None] * val[..., None]
    # the glass's edge catches the light like a blade along each strand's crown
    edge = np.exp(-((across + 0.3) / 0.1) ** 2) * crown ** 4
    col = col + (violet - mid)[None, None] * edge[..., None]
    # a thread of violet light down its heart, breathing along its length
    heart = np.exp(-(across / 0.16) ** 2) * (0.55 + 0.45 * np.cos(U * 2 * math.pi * 2))
    col = col + violet[None, None] * heart[..., None] * 0.55
    # glints: two short bright sparks a repeat, white at their hearts
    glint = np.zeros_like(U)
    for c0, w0 in ((0.18, 0.018), (0.63, 0.013)):
        d = np.minimum(np.abs(U - c0), 1 - np.abs(U - c0))
        glint += np.exp(-(d / w0) ** 2) * np.exp(-((across + 0.2) / 0.3) ** 2)
    col = col * (1 - glint[..., None]) + white[None, None] * glint[..., None]
    rim = np.exp(-((np.abs(across) - 0.86) / 0.1) ** 2)
    col = col * (1 - rim[..., None] * 0.5) + rgb(GLASS[0])[None, None] * rim[..., None] * 0.5
    a = np.clip(body * 0.97 + rim * 0.5 + glint * 0.3, 0, 1) * (np.abs(across) < 0.98)
    return _img(np.clip(col, 0, 1), a)


def reel():
    n = 64
    y, x = np.mgrid[0:n, 0:n]
    X = (x + 0.5) / n * 2 - 1
    Y = (y + 0.5) / n * 2 - 1
    r = np.sqrt(X * X + Y * Y)
    ang = np.arctan2(Y, X)
    # light from the top left, a soft gradient across the face
    lit = np.clip(0.5 - 0.42 * (X + Y), 0, 1)
    gold_lo, gold_mid, gold_hi, gold_top = rgb(GOLD[2]), rgb(GOLD[3]), rgb(GOLD[4]), rgb(GOLD[5])
    gold = gold_lo[None, None] * (1 - lit[..., None]) + gold_hi[None, None] * lit[..., None]
    col = np.zeros((n, n, 3))
    a = np.zeros((n, n))
    # the barrel under the line: deep gold
    barrel = (r >= 0.24) & (r <= 0.38)
    col[barrel] = (gold * 0.62)[barrel]
    a[barrel] = 1
    # the wound line: a band of steel between barrel and rim, its turns as fine rings, lit the same way
    band = (r > 0.36) & (r < 0.8)
    turns = 0.5 + 0.5 * np.cos(r * 64)
    steel = rgb(STEEL[2])[None, None] * (1 - lit[..., None]) + rgb(STEEL[5])[None, None] * lit[..., None]
    steel = steel * (0.75 + 0.25 * turns[..., None])
    col[band] = steel[band]
    a[band] = 1
    # five spokes of an even width across the line, the rim, the hub
    arc = np.abs(((ang / (2 * math.pi) * 5 + 0.25) % 1) - 0.5) * (2 * math.pi / 5) * r
    spoke = (arc < 0.055) & (r > 0.22) & (r < 0.84)
    rimring = (r >= 0.8) & (r < 0.98)
    hub = r < 0.24
    for m, shade in ((spoke, 0.92), (rimring, 1.0), (hub, 1.0)):
        col[m] = (gold * shade)[m]
        a[m] = 1
    # a bright catch along the rim's lit edge and on the hub, the hub's pin dark
    catch = (rimring & (X + Y < -0.95)) | (hub & (X + Y < -0.12) & (r > 0.12))
    col[catch] = gold_top
    col[r < 0.08] = gold_lo * 0.55
    # soft edge
    a = a * np.clip((0.99 - r) / 0.03, 0, 1)
    return _img(np.clip(col, 0, 1), a)


TEXTURES = {"ascent/rope_iron": rope_iron, "ascent/rope_deepglass": rope_deepglass, "ascent/reel": reel}


# ---- icons (16x16 pixel art) -----------------------------------------------------------------------------------------

def _coil(c, ramp, tracer=None, tracer_every=0):
    for loop, (cx, cy, r) in enumerate(((7.5, 7.0, 5.4), (7.5, 8.2, 4.2), (7.5, 9.4, 3.0))):
        for k in range(72):
            a = k / 72 * math.tau
            x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * 0.6
            lit = math.sin(a) < 0 and math.cos(a) < 0.4
            col = ramp[4 if lit else 2 if math.sin(a) < 0 else 3]
            if tracer and tracer_every and (k + loop * 11) % tracer_every == 0:
                col = tracer
            c.set(int(round(x)), int(round(y)), col)


def iron_line_icon():
    st = [hx(h) for h in STEEL]
    c = Canvas()
    _coil(c, st)
    # the line's end off the coil, down to a forged hook
    for (x, y) in ((12, 11), (13, 12), (13, 13)):
        c.set(x, y, st[4])
    for (x, y, i) in ((14, 13, 5), (14, 14, 4), (13, 15, 3), (12, 14, 4)):
        c.set(x, y, st[i])
    PX._outline(c)
    return c


def gold_reel_icon():
    g = [hx(h) for h in GOLD]
    st = [hx(h) for h in STEEL]
    c = Canvas()
    # the spool from three quarters: two gold flanges, steel wound between them, a crank off the near flange
    for y in range(4, 13):
        for x in range(4, 10):
            c.set(x, y, st[3] if (y % 2 == 0) else st[2])
    for (fx, shade) in ((3, 3), (10, 4)):
        for y in range(3, 14):
            dy = abs(y - 8.5)
            if dy <= 5.2:
                c.set(fx, y, g[shade if y < 8 else shade - 1])
                if dy <= 4.0:
                    c.set(fx + (1 if fx == 10 else -1), y, g[shade - 1])
    for x in range(4, 10):
        c.set(x, 4, st[5])
    # the crank: an arm from the near flange's hub and a gold knob
    for (x, y) in ((12, 8), (13, 7), (13, 6)):
        c.set(x, y, g[2])
    c.set(14, 5, g[5])
    c.set(13, 5, g[4])
    PX._outline(c)
    return c


def deepglass_line_icon():
    gl = [hx(h) for h in GLASS]
    c = Canvas()
    _coil(c, [gl[1], gl[2], gl[3], gl[4], gl[5]], tracer=gl[6], tracer_every=13)
    for (x, y, i) in ((12, 11, 4), (13, 12, 5), (14, 13, 6)):
        c.set(x, y, gl[i])
    PX._outline(c)
    return c


ICONS = {"iron_line": iron_line_icon, "gold_reel": gold_reel_icon, "deepglass_line": deepglass_line_icon}


def build(out):
    for rel, fn in TEXTURES.items():
        path = os.path.join(out.root, TEX, rel + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        with open(path + ".mcmeta", "w") as m:
            m.write('{"texture": {"blur": true}}\n')
        out.written.append(rel)
    for name, fn in ICONS.items():
        out.item(name, fn())


# ---- content: item models, recipes ------------------------------------------------------------------------------------

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


RECIPES = {
    # four iron round a coil of Runner's rope, nuggets at the corners: the steel line
    "iron_line": {"pattern": ["NIN", "IRI", "NIN"], "key": {"N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot", "R": "awaken:runner_rope"}},
    # a gold spool on an iron axle
    "gold_reel": {"pattern": [" G ", "GIG", " G "], "key": {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot"}},
    # the iron line's steel with Deepglass drawn into it (so it keeps the iron line's reach)
    "deepglass_line": {"pattern": ["SIS", "IRI", "SIS"], "key": {"S": "awaken:deepglass_shard", "I": "minecraft:iron_ingot", "R": "awaken:runner_rope"}},
}


def content():
    for name in FITTINGS:
        write_json(os.path.join(ASSETS, "models/item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + name}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})
        r = RECIPES[name]
        write_json(os.path.join(MERGE, "data/awaken/recipe", name + ".json"),
                   {"type": "minecraft:crafting_shaped", "category": "equipment", "key": r["key"], "pattern": r["pattern"],
                    "result": {"id": "awaken:" + name}})


# ---- the clips ---------------------------------------------------------------------------------------------------------

pose, nudge = P.pose, P.nudge
CLIPS = {}


def clip(name, keys, length, loop=False, end=None, note=""):
    if name in CLIPS:
        raise ValueError(f"duplicate clip {name}")
    CLIPS[name] = P.Clip(name, keys, length, loop, end, note)
    CLIPS[name + "_bent"] = P.Clip(name + "_bent", keys, length, loop, end, note + " (bent elbows and knees)", bent=True)


def loop(name, base, jitters, period, note):
    n = len(jitters) + 1
    keys = [(0, base, "linear")] + [(period * (i + 1) / n, j, "easeinoutsine") for i, j in enumerate(jitters)] + [(period, base, "easeinoutsine")]
    clip(name, keys, period, loop=True, note=note)


# Holding on the wall: the rope hand up before the face guiding the rope, the brake hand low by the hip, legs out to the
# rock (the game leans the body back and stands it off the wall), knees soft, the feet a little apart.
RP = pose(ground=False, ra=(-150, 4, -12), re=22, la=(26, 8, -16), le=58,
          rl=(-72, 0, 8), rk=26, ll=(-66, 0, -8), lk=34)
loop("ascent_rappel", RP, [nudge(RP, la=(4, 0, 0), rk=4, lk=-3), nudge(RP, la=(-3, 0, 0), rk=-2, lk=4)], 40,
     "the rope hand up, the brake hand at the hip, legs out to the rock, a slow breath")

# Walking down: the legs step in turn down the wall (the lifted one bends and reaches lower), the brake hand feeding.
WA = nudge(RP, rl=(-14, 0, 0), rk=24, ll=(6, 0, 0), lk=-18, la=(10, 0, 0))
WB = nudge(RP, rl=(6, 0, 0), rk=-16, ll=(-14, 0, 0), lk=24, la=(-6, 0, 0))
clip("ascent_rappel_walk", [(0, WA, "linear"), (6, WB, "easeinoutsine"), (12, WA, "easeinoutsine")], 12, loop=True,
     note="walking down the wall, the legs stepping in turn, the brake hand feeding")

# The kick: crouched to the rock, the push (legs long, the arms taking the rope), the bound (knees soft, feet together),
# reaching back to plant.
CROUCH = pose(ground=False, ra=(-138, 4, -12), re=46, la=(30, 8, -16), le=70, rl=(-96, 0, 8), rk=82, ll=(-92, 0, -8), lk=84)
PUSH = pose(ground=False, ra=(-158, 4, -12), re=8, la=(18, 8, -18), le=44, rl=(-56, 0, 6), rk=4, ll=(-54, 0, -6), lk=6)
AIR = pose(ground=False, ra=(-154, 4, -12), re=16, la=(24, 8, -16), le=54, rl=(-70, 0, 4), rk=34, ll=(-66, 0, -4), lk=40)
REACH = pose(ground=False, ra=(-150, 4, -12), re=20, la=(26, 8, -16), le=58, rl=(-80, 0, 7), rk=20, ll=(-76, 0, -7), lk=24)
clip("ascent_rappel_kick", [(0, nudge(RP, rk=10, lk=10), "linear"), (2, CROUCH, "easeoutquad"), (4, PUSH, "easeoutcubic"),
                            (8, AIR, "easeinoutsine"), (12, REACH, "easeinoutsine"), (14, RP, "easeoutquad")], 14,
     note="crouched to the rock, the push, the bound, reaching back to plant")


def clips_document():
    return {"format_version": "1.8.0",
            "__comment": "GENERATED by tools/art/specs/ascent_rappel.py: edit the poses there, not this file.",
            "animations": {name: c.json() for name, c in CLIPS.items()}}


def write_clips(root=None):
    path = os.path.join(root or RES, CLIPS_REL)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(clips_document(), fh, separators=(",", ":"))
        fh.write("\n")
    return path


def check(limit=1.25):
    bad = []
    for name, c in CLIPS.items():
        worst, at, where = 0.0, 0.0, ""
        t = 0.0
        while t <= c.length + 1e-6:
            d, w = P.penetration(c.sample(t, 0.0), 0.0)
            if d > worst:
                worst, at, where = d, t, w
            t += 0.5
        if worst > limit:
            bad.append(f"{name}: {where} by {worst:.2f} px at tick {at:g}")
    return bad


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    tiles = []
    for rel, fn in TEXTURES.items():
        im = fn()
        scale = max(1, 384 // max(im.size))
        big = im.resize((im.width * scale, im.height * scale), Image.BILINEAR)
        for bg in ((8, 10, 16), (150, 190, 230)):
            base = Image.new("RGBA", big.size, bg + (255,))
            tiles.append(Image.alpha_composite(base, big))
    for name, fn in ICONS.items():
        im = fn().image().resize((160, 160), Image.NEAREST)
        base = Image.new("RGBA", im.size, (60, 62, 70, 255))
        tiles.append(Image.alpha_composite(base, im))
    W = max(t.width for t in tiles)
    H = sum(t.height + 6 for t in tiles)
    sheet = Image.new("RGBA", (W, H), (30, 30, 30, 255))
    y = 0
    for t in tiles:
        sheet.paste(t, (0, y))
        y += t.height + 6
    path = os.path.join(out_dir, "ascent_rappel_textures.png")
    sheet.save(path)
    print(path)
    for n in CLIPS:
        if not n.endswith("_bent"):
            print(P.strip(CLIPS[n], out_dir))


def main(argv):
    cmd = argv[0] if argv else "content"
    if cmd == "content":
        out = PX.Out(RES)
        build(out)
        content()
        path = write_clips()
        print(f"{len(out.written)} textures, {len(FITTINGS)} item models and recipes, {len(CLIPS)} clips -> {os.path.relpath(path, ROOT)}")
        for b in check():
            print("CLIPPING", b)
        return 0
    if cmd == "preview":
        preview(argv[1])
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
