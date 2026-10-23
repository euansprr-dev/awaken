"""Structures: the overworld's built things in one visual language (docs/modules/structures.md).

    python3 tools/art/build_all.py structures          textures (gate vortex atlas, shards, fissures, ruin and well blocks)
    python3 tools/art/specs/structures.py content      blockstates, models, item definitions, loot, lang, sounds
    python3 tools/art/specs/structures.py preview DIR  isometric renders of every structure the GameTests built
                                                       (run/structures/*.json), two angles, day and night, and the gate
                                                       vortex's key frames (run/structures/vortex_frames.json)

The previews draw the real block textures (vanilla from /Volumes/MEDIA/Awaken/.ref/assets, ours from the resources) on
the block lists the Java builders produced, light them with a flood fill like the game's block light, and composite the
vortex frames exported from the same Java code the renderer runs (dev.awaken.gates.GateVortex).
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, ramp, rng_for, hex_rgba  # noqa: E402
import pixelart as P  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge")
VANILLA = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft"

RANK_IDS = ["e", "d", "c", "b", "a", "s", "sovereign"]
# rank colours as the vortex uses them (dev.awaken.gates.GateVortex palette: crack/glow)
RANK_GLOW = ["#b8d6ff", "#5fd08f", "#6fb3ff", "#b48cff", "#f2a541", "#ff5a6a", "#ffd24a"]
RANK_HOT = ["#ffffff", "#d8ffe8", "#e0f0ff", "#f0e4ff", "#fff0c8", "#ffe0e4", "#fff6d0"]


# ---------------------------------------------------------------------------------------------------------------------
# numpy helpers


def _hash2(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def periodic_noise(w, h, fx, fy, seed, octaves=4):
    """Value noise on a w x h grid, periodic in x with fx cells (and in y with fy cells), fbm summed, in 0..1."""
    xs = np.arange(w)[None, :].repeat(h, 0).astype(np.float64)
    ys = np.arange(h)[:, None].repeat(w, 1).astype(np.float64)
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cx, cy = fx * 2 ** o, fy * 2 ** o
        gx, gy = xs * cx / w, ys * cy / h
        x0, y0 = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
        tx, ty = gx - x0, gy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        def g(ix, iy):
            return _hash2(np.mod(ix, cx), np.mod(iy, cy), seed + o * 101)
        a = g(x0, y0) * (1 - tx) + g(x0 + 1, y0) * tx
        b = g(x0, y0 + 1) * (1 - tx) + g(x0 + 1, y0 + 1) * tx
        total += (a * (1 - ty) + b * ty) * amp
        norm += amp
        amp *= 0.55
    return total / norm


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def to_img(rgb, a):
    arr = np.zeros(a.shape + (4,), np.uint8)
    arr[..., :3] = np.clip(rgb * 255, 0, 255).astype(np.uint8)
    arr[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


# ---------------------------------------------------------------------------------------------------------------------
# The gate vortex atlas (layout: dev.awaken.gates.GateVortex, "atlas layout")

ATLAS = 512
ARMS = (4, 124)
FIL = (136, 256)
RIM = (268, 332)
CRACK = (344, 360)
HALO = (0, 372, 128)
HEART = (136, 372, 128)
MOTE = (276, 372, 64)
SPARK = (276, 440, 64)


def vortex_atlas():
    img = np.zeros((ATLAS, ATLAS, 4))
    W = ATLAS

    # arms: three arms of different width and strength in one turn of u (the renderer wraps it once round the
    # circle), their edges torn by noise and streaks running along them (low frequency along v)
    h = ARMS[1] - ARMS[0]
    u = (np.arange(W)[None, :] + 0.5) / W
    v = (np.arange(h)[:, None] + 0.5) / h
    n1 = periodic_noise(W, h, 9, 3, 11)
    n2 = periodic_noise(W, h, 96, 2, 12, octaves=3)
    acc = np.zeros((h, W))
    for centre, wid, amp in ((0.06, 0.07, 1.0), (0.37, 0.05, 0.78), (0.7, 0.062, 0.92), (0.54, 0.022, 0.45), (0.88, 0.018, 0.4)):
        du = np.abs(((u - centre + 0.5 + 0.05 * (n1 - 0.5)) % 1.0) - 0.5)
        acc += amp * np.exp(-(du / (wid * (0.7 + 0.8 * v))) ** 2)
    streak = 0.5 + 0.5 * smooth(0.3, 0.75, n2)
    a = np.clip(acc * streak * (0.7 + 0.55 * n1), 0, 1)
    rgb = np.ones((h, W, 3)) * (0.8 + 0.2 * a[..., None])
    img[ARMS[0]:ARMS[1], :, :3] = rgb
    img[ARMS[0]:ARMS[1], :, 3] = a

    # filaments: thin bright threads, four per half turn, barely wavering
    h = FIL[1] - FIL[0]
    v = (np.arange(h)[:, None] + 0.5) / h
    n3 = periodic_noise(W, h, 8, 4, 21)
    acc = np.zeros((h, W))
    for k, (off, wid, amp) in enumerate([(0.0, 0.009, 1.0), (0.27, 0.006, 0.8), (0.55, 0.012, 0.7), (0.79, 0.005, 0.6)]):
        d = np.abs(((u + off + 0.012 * np.sin(v * 7 + k) + 0.02 * (n3 - 0.5)) % 1.0) - 0.5)
        d = np.minimum(d, 1 - d)
        acc += amp * np.exp(-(d / wid) ** 2)
    a = np.clip(acc * (0.55 + 0.6 * n3) * smooth(0.0, 0.25, v), 0, 1)
    img[FIL[0]:FIL[1], :, :3] = 1.0
    img[FIL[0]:FIL[1], :, 3] = a

    # rim: a hot line wavering round r = 1 (v ~ 0.43 of the band), a soft glow, sparks
    h = RIM[1] - RIM[0]
    v = (np.arange(h)[:, None] + 0.5) / h
    n4 = periodic_noise(W, h, 32, 1, 31, octaves=3)
    n5 = periodic_noise(W, h, 96, 4, 32, octaves=2)
    wob = 0.07 * (n4 - 0.5) * 2
    line = np.exp(-((v - 0.43 - wob) / 0.05) ** 2)
    glow = 0.45 * np.exp(-((v - 0.43) / 0.22) ** 2)
    sparks = smooth(0.72, 0.9, n5) * np.exp(-((v - 0.43) / 0.3) ** 2)
    a = np.clip(line + glow + 0.8 * sparks, 0, 1)
    rgb = np.ones((h, W, 3)) * (0.85 + 0.15 * line[..., None])
    img[RIM[0]:RIM[1], :, :3] = rgb
    img[RIM[0]:RIM[1], :, 3] = a

    # ground crack: a jagged hot line along u with a glow, periodic
    h = CRACK[1] - CRACK[0]
    v = (np.arange(h)[:, None] + 0.5) / h
    n6 = periodic_noise(W, h, 40, 1, 41, octaves=3)
    line = np.exp(-((v - 0.5 - 0.35 * (n6 - 0.5)) / 0.09) ** 2)
    glow = 0.35 * np.exp(-((v - 0.5) / 0.35) ** 2)
    img[CRACK[0]:CRACK[1], :, :3] = 1.0
    img[CRACK[0]:CRACK[1], :, 3] = np.clip(line + glow, 0, 1)

    def sprite(rect, fn):
        x0, y0, s = rect
        yy, xx = np.mgrid[0:s, 0:s]
        r = np.hypot((xx + 0.5) / s * 2 - 1, (yy + 0.5) / s * 2 - 1)
        ang = np.arctan2((yy + 0.5) / s * 2 - 1, (xx + 0.5) / s * 2 - 1)
        rgb, a = fn(r, ang)
        img[y0:y0 + s, x0:x0 + s, :3] = rgb
        img[y0:y0 + s, x0:x0 + s, 3] = a

    sprite(HALO, lambda r, ang: (np.ones(r.shape + (3,)), np.clip(1 - r, 0, 1) ** 2.2))
    hn = periodic_noise(HEART[2], HEART[2], 4, 4, 51, octaves=3)
    sprite(HEART, lambda r, ang: (np.ones(r.shape + (3,)), np.clip((1 - smooth(0.55, 1.0, r)) * (0.9 + 0.1 * hn), 0, 1)))
    sprite(MOTE, lambda r, ang: (np.ones(r.shape + (3,)), np.clip(np.exp(-(r / 0.22) ** 2) + 0.35 * np.clip(1 - r, 0, 1) ** 2, 0, 1)))
    sprite(SPARK, lambda r, ang: (np.ones(r.shape + (3,)),
                                  np.clip(np.exp(-(r / 0.12) ** 2) + 0.9 * np.exp(-(r * np.abs(np.sin(2 * ang)) / 0.03) ** 2) * np.clip(1 - r, 0, 1), 0, 1)))
    return to_img(img[..., :3], img[..., 3])


def shard_texture():
    """32x32, four 16x16 tiles of torn-up gate rock: obsidian black with frame-stone glyph veins."""
    c = Canvas(32, 32)
    obs = ramp("obsidian")
    sig = ramp("signal")
    for tile in range(4):
        ox, oy = (tile & 1) * 16, (tile >> 1) * 16
        base = P.stone("obsidian", seed=f"gate_shard_{tile}", scale=3, contrast=1.2)
        for y in range(16):
            for x in range(16):
                c.set(ox + x, oy + y, base.get(x, y))
        rng = rng_for("gate_shard_vein", tile)
        x, y = rng.randrange(3, 13), 0
        while y < 16:
            c.set(ox + x, oy + y, sig[3] if rng.random() < 0.7 else sig[4])
            if rng.random() < 0.4:
                x = max(1, min(14, x + rng.choice((-1, 1))))
            y += 1
        # chipped bright edges
        for i in range(16):
            if rng.random() < 0.3:
                c.set(ox + i, oy, obs[-1])
            if rng.random() < 0.3:
                c.set(ox, oy + i, obs[-1])
    return c


def fissure_top(glow_hex, hot_hex, seed, diag=False):
    """Scorched stone split by a crack that runs edge to edge (west to east, or corner to corner when {@code diag}), so
    neighbouring fissure blocks join into one long crack; it glows (the model draws it full bright)."""
    base = P.stone("obsidian", seed="gate_fissure_base", scale=3, contrast=1.1, lo=1, hi=3)
    glow, hot = hex_rgba(glow_hex), hex_rgba(hot_hex)
    rng = rng_for("gate_fissure_crack", seed, diag)
    pts = []
    if diag:
        off = 0
        for i in range(16):
            pts.append((i, max(0, min(15, i + off))))
            if 2 < i < 13 and rng.random() < 0.35:
                off = max(-2, min(2, off + rng.choice((-1, 1))))
            elif i >= 13:
                off = int(off * 0.5)
    else:
        y = 7
        for x in range(16):
            pts.append((x, y))
            if 2 < x < 13 and rng.random() < 0.4:
                y = max(5, min(10, y + rng.choice((-1, 1))))
            elif x >= 13:
                y = 7 if abs(y - 7) <= 1 else y + (1 if y < 7 else -1)
    # a short branch off the middle
    bx, by = pts[rng.randrange(5, 11)]
    d = rng.choice((-1, 1))
    branch = []
    x, yy = bx, by
    for _ in range(rng.randrange(2, 5)):
        yy += d
        x += rng.choice((0, 1))
        if 0 <= x < 16 and 0 <= yy < 16:
            branch.append((x, yy))
    lit = set(pts) | set(branch)
    for (x, yy) in pts + branch:
        base.set(x, yy, hot)
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            q = (x + dx, yy + dy)
            if q not in lit and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                base.blend(*q, glow, 0.7)
    return base


def fissure_side():
    base = P.stone("obsidian", seed="gate_fissure_side", scale=3, contrast=1.0, lo=1, hi=3)
    sl = ramp("slate")
    for y in range(16):
        base.set(5 + (y // 5) % 2, y, sl[0])
    return base


def build(out):
    root = out.root
    os.makedirs(os.path.join(root, "assets/awaken/textures/entity"), exist_ok=True)
    atlas_path = os.path.join(root, "assets/awaken/textures/entity/gate_vortex.png")
    vortex_atlas().save(atlas_path)
    with open(atlas_path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True}}, f)
    out.written.append("entity/gate_vortex")
    out.entity("gate_shard", shard_texture())
    for i, rid in enumerate(RANK_IDS):
        out.block("gate_fissure_" + rid, fissure_top(RANK_GLOW[i], RANK_HOT[i], rid))
        out.block("gate_fissure_" + rid + "_diag", fissure_top(RANK_GLOW[i], RANK_HOT[i], rid, diag=True))
    out.block("gate_fissure_broken", fissure_top("#ff3a1a", "#ffd0a0", "broken"))
    out.block("gate_fissure_broken_diag", fissure_top("#ff3a1a", "#ffd0a0", "broken", diag=True))
    out.block("gate_fissure_side", fissure_side())


# ---------------------------------------------------------------------------------------------------------------------
# JSON content


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def merge_json(path, data):
    """Adds keys to a shared merge file (keeps everything else in it)."""
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    cur.update(data)
    write_json(path, cur)


def fissure_model(tex):
    faces = {}
    for d in ("down", "up", "north", "south", "west", "east"):
        faces[d] = {"texture": "#top" if d == "up" else "#side", "cullface": d}
    return {"parent": "minecraft:block/block", "textures": {"particle": "awaken:block/gate_fissure_side", "top": tex,
                                                           "side": "awaken:block/gate_fissure_side"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "faces": faces}]}


def content():
    variants = {}
    for i, rid in enumerate(RANK_IDS + ["broken"]):
        for suffix in ("", "_diag"):
            write_json(os.path.join(ASSETS, f"models/block/gate_fissure_{rid}{suffix}.json"),
                       fissure_model(f"awaken:block/gate_fissure_{rid}{suffix}"))
        # dir 0 east-west, 1 north-west to south-east, 2 north-south, 3 north-east to south-west
        variants[f"dir=0,rank={i}"] = {"model": f"awaken:block/gate_fissure_{rid}"}
        variants[f"dir=1,rank={i}"] = {"model": f"awaken:block/gate_fissure_{rid}_diag"}
        variants[f"dir=2,rank={i}"] = {"model": f"awaken:block/gate_fissure_{rid}", "y": 90}
        variants[f"dir=3,rank={i}"] = {"model": f"awaken:block/gate_fissure_{rid}_diag", "y": 90}
    write_json(os.path.join(ASSETS, "blockstates/gate_fissure.json"), {"variants": variants})
    merge_json(os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json"), {
        "block.awaken.gate_fissure": "Gate Fissure",
        "subtitles.awaken.gate.vortex": "Gate vortex turns",
    })
    merge_json(os.path.join(MERGE, "gates/assets/awaken/sounds.json"), {
        "gate.hum": {"subtitle": "subtitles.awaken.gate.hum", "sounds": [
            {"name": "minecraft:block/beacon/ambient", "pitch": 0.7, "attenuation_distance": 48}]},
        "gate.vortex": {"subtitle": "subtitles.awaken.gate.vortex", "sounds": [
            {"name": "minecraft:portal/portal", "pitch": 0.6, "volume": 0.6, "attenuation_distance": 48},
            {"name": "minecraft:block/conduit/ambient", "pitch": 0.5, "volume": 0.8, "attenuation_distance": 48}]},
    })
    print("structures: content written")


# >>> preview
# ---------------------------------------------------------------------------------------------------------------------
# Preview: an isometric voxel renderer for the block lists the GameTests dumped (run/structures/*.json)

RUN = os.path.join(ROOT, "run/structures")
GRASS_TINT = (0x79, 0xC0, 0x5A)
FOLIAGE_TINT = (0x59, 0xAE, 0x30)
TINTED = {"grass_block": GRASS_TINT, "short_grass": GRASS_TINT, "tall_grass": GRASS_TINT, "fern": GRASS_TINT, "large_fern": GRASS_TINT,
          "oak_leaves": FOLIAGE_TINT, "jungle_leaves": FOLIAGE_TINT, "dark_oak_leaves": FOLIAGE_TINT, "acacia_leaves": FOLIAGE_TINT,
          "mangrove_leaves": FOLIAGE_TINT, "vine": FOLIAGE_TINT, "lily_pad": (0x20, 0x80, 0x30), "spruce_leaves": (0x61, 0x99, 0x61),
          "birch_leaves": (0x80, 0xA7, 0x55), "water": (0x3F, 0x76, 0xE4), "bush": GRASS_TINT, "leaf_litter": (0xA0, 0x70, 0x3A),
          "sugar_cane": GRASS_TINT, "big_dripleaf": FOLIAGE_TINT, "moss_carpet": (255, 255, 255)}
# block light the game gives these blocks (ours by name in our_light)
EMIT = {"glowstone": 15, "sea_lantern": 15, "lantern": 15, "soul_lantern": 10, "torch": 14, "wall_torch": 14, "soul_torch": 10,
        "soul_wall_torch": 10, "shroomlight": 15, "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15,
        "glow_lichen": 7, "magma_block": 3, "end_rod": 14, "jack_o_lantern": 15, "crying_obsidian": 10, "beacon": 15, "lava": 15,
        "fire": 15, "soul_fire": 10, "campfire": 15, "soul_campfire": 10, "redstone_lamp": 15, "amethyst_cluster": 5,
        "candle": 3, "firefly_bush": 2, "copper_bulb": 15, "waxed_copper_bulb": 15, "conduit": 15}
FULL_BRIGHT = {"glowstone", "sea_lantern", "shroomlight", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "magma_block",
               "end_rod", "lantern", "soul_lantern", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "jack_o_lantern",
               "crying_obsidian", "glow_lichen", "lava", "fire", "soul_fire", "campfire", "soul_campfire", "redstone_lamp", "copper_bulb",
               "waxed_copper_bulb", "beacon", "conduit"}


def our_light(path):
    if path.startswith("gate_fissure"):
        return 7
    if any(k in path for k in ("glow_panel", "well_light", "warning_light", "ruin_lamp", "lair_glow", "deep_light")):
        return 15
    if "glyph" in path:
        return 10
    if path.startswith("gate_core"):
        return 15
    return 0


_MODEL_CACHE, _TEX_CACHE, _LOOK_CACHE = {}, {}, {}


def _split(ident, default_ns="minecraft"):
    if ":" in ident:
        ns, path = ident.split(":", 1)
    else:
        ns, path = default_ns, ident
    return ns, path


def _asset(ns, kind, path):
    base = VANILLA if ns == "minecraft" else os.path.join(RES, "assets", ns)
    return os.path.join(base, kind, path)


def _load_model(ident):
    if ident in _MODEL_CACHE:
        return _MODEL_CACHE[ident]
    ns, path = _split(ident)
    p = _asset(ns, "models", path + ".json")
    chain = []
    while p and os.path.exists(p):
        with open(p) as f:
            m = json.load(f)
        chain.append(m)
        par = m.get("parent")
        if not par or par.startswith("builtin"):
            break
        pns, ppath = _split(par)
        p = _asset(pns, "models", ppath + ".json")
    textures, elements = {}, None
    for m in reversed(chain):
        textures.update(m.get("textures", {}))
        if "elements" in m:
            elements = m["elements"]
    _MODEL_CACHE[ident] = (textures, elements)
    return textures, elements


def _resolve_tex(textures, ref):
    for _ in range(8):
        if isinstance(ref, dict):
            ref = ref.get("sprite")
        if not ref or not ref.startswith("#"):
            break
        ref = textures.get(ref[1:])
    if isinstance(ref, dict):
        ref = ref.get("sprite")
    return ref


def _texture(ident):
    if ident in _TEX_CACHE:
        return _TEX_CACHE[ident]
    im = None
    if ident:
        ns, path = _split(ident)
        p = _asset(ns, "textures", path + ".png")
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            if im.height > im.width:
                im = im.crop((0, 0, im.width, im.width))
            if im.width != 16:
                im = im.resize((16, 16), Image.BOX)
    if im is None:
        im = Image.new("RGBA", (16, 16), (200, 0, 200, 255))
    _TEX_CACHE[ident] = im
    return im


def _match(key, props):
    if key == "":
        return True
    for kv in key.split(","):
        k, v = kv.split("=")
        if props.get(k) != v:
            return False
    return True


def _when(cond, props):
    if "OR" in cond:
        return any(_when(c, props) for c in cond["OR"])
    if "AND" in cond:
        return all(_when(c, props) for c in cond["AND"])
    for k, v in cond.items():
        if props.get(k) not in str(v).lower().split("|"):
            return False
    return True


DIRS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
DIR_OF = {v: k for k, v in DIRS.items()}


def _rot_dir(d, rx, ry):
    x, y, z = d
    for _ in range((rx // 90) % 4):
        y, z = -z, y
    for _ in range((ry // 90) % 4):
        x, z = -z, x
    return (x, y, z)


def _tint(im, rgb):
    a = np.asarray(im).astype(np.float32)
    a[..., :3] *= np.array(rgb, np.float32) / 255.0
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def block_look(name, props_str):
    """(shape, faces): shape 'cube' | 'cross' | 'liquid' | ('box', y0, y1, x0, x1, z0, z1); faces: world direction -> 16x16 RGBA."""
    key = (name, props_str)
    if key in _LOOK_CACHE:
        return _LOOK_CACHE[key]
    ns, path = _split(name)
    props = dict(kv.split("=", 1) for kv in props_str.split(",") if "=" in kv)
    if path in ("water", "lava"):
        tex = _texture(f"minecraft:block/{path}_still")
        if path == "water":
            tex = _tint(tex, TINTED["water"])
        look = ("liquid", {d: tex for d in DIRS})
        _LOOK_CACHE[key] = look
        return look
    bs = _asset(ns, "blockstates", path + ".json")
    model, rx, ry = None, 0, 0
    if os.path.exists(bs):
        with open(bs) as f:
            st = json.load(f)
        if "variants" in st:
            for k, v in st["variants"].items():
                if _match(k, props):
                    v = v[0] if isinstance(v, list) else v
                    model, rx, ry = v["model"], v.get("x", 0), v.get("y", 0)
                    break
        elif "multipart" in st:
            for part in st["multipart"]:
                if "when" not in part or _when(part["when"], props):
                    a = part["apply"]
                    a = a[0] if isinstance(a, list) else a
                    model, rx, ry = a["model"], a.get("x", 0), a.get("y", 0)
                    break
    textures, elements = _load_model(model) if model else ({}, None)
    faces, shape = {}, "cube"
    if elements:
        lo = [min(e["from"][i] for e in elements) for i in range(3)]
        hi = [max(e["to"][i] for e in elements) for i in range(3)]
        thin = any(e["to"][i] - e["from"][i] < 0.01 for e in elements for i in range(3))
        if thin or any("rotation" in e and abs(e["rotation"].get("angle", 0)) == 45 for e in elements):
            shape = "cross"
        elif hi[1] - lo[1] < 15.9 or hi[0] - lo[0] < 15.9 or hi[2] - lo[2] < 15.9:
            box = [lo[0] / 16, hi[0] / 16, lo[1] / 16, hi[1] / 16, lo[2] / 16, hi[2] / 16]
            # turn the box with the variant (y only; x rotations of slabs are rare)
            for _ in range((ry // 90) % 4):
                x0, x1, _y0, _y1, z0, z1 = box
                box = [1 - z1, 1 - z0, _y0, _y1, x0, x1]
            if (rx // 90) % 2 == 1:
                box = [box[0], box[1], box[4], box[5], 1 - box[3], 1 - box[2]]
            shape = ("box", box[2], box[3], box[0], box[1], box[4], box[5])
        for e in elements:
            for fd, fv in e.get("faces", {}).items():
                wd = DIR_OF[_rot_dir(DIRS[fd], rx, ry)]
                if wd in faces:
                    continue
                tex = _texture(_resolve_tex(textures, fv.get("texture")))
                if fv.get("tintindex") is not None or path in TINTED:
                    tex = _tint(tex, TINTED.get(path, GRASS_TINT))
                faces[wd] = tex
    if not faces:
        tex = _texture(_resolve_tex(textures, "#particle") if textures else f"{ns}:block/{path}")
        faces = {d: tex for d in DIRS}
    first = next(iter(faces.values()))
    for d in DIRS:
        faces.setdefault(d, first)
    if ry % 360 and "up" in faces:
        faces["up"] = faces["up"].rotate(-ry)
    if shape == "cross":
        faces = {d: faces.get("north", first) for d in DIRS}
    look = (shape, faces)
    _LOOK_CACHE[key] = look
    return look


def _emission(name):
    ns, path = _split(name)
    if ns == "minecraft":
        if path.endswith("candle"):
            return 3
        return EMIT.get(path, 0)
    return our_light(path)


def _full_bright(name):
    ns, path = _split(name)
    if ns == "minecraft":
        return path in FULL_BRIGHT
    return our_light(path) >= 7


def _opaque_look(look):
    shape, faces = look
    return shape == "cube" and np.asarray(faces["up"])[..., 3].min() > 200


class Scene:
    """A dumped structure: cells (x, y, z) -> (name, props), its block light, and the vortex if it is a gate."""

    def __init__(self, data):
        self.name = data["name"]
        self.kind = data.get("kind", "")
        self.vortex = data.get("vortex")
        self.cells, self.emitters = {}, {}
        for b in data["blocks"]:
            x, y, z, name = b[0], b[1], b[2], b[3]
            props = b[4] if len(b) > 4 else ""
            if "gate_core" in name:
                self.emitters[(x, y, z)] = 15  # invisible: the renderer draws the vortex, the blocks still light the pad
                continue
            self.cells[(x, y, z)] = (name, props)
        self._light = None

    def light(self):
        if self._light is not None:
            return self._light
        lv = dict(self.emitters)
        for p, (name, props) in self.cells.items():
            e = _emission(name)
            if e > lv.get(p, 0):
                lv[p] = e
        frontier = list(lv)
        while frontier:
            nxt = []
            for p in frontier:
                l = lv[p] - 1
                if l <= 0:
                    continue
                for d in DIRS.values():
                    q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                    if lv.get(q, 0) >= l:
                        continue
                    c = self.cells.get(q)
                    if c is not None and _opaque_look(block_look(*c)):
                        continue
                    lv[q] = l
                    nxt.append(q)
            frontier = nxt
        self._light = lv
        return lv


def _rot(p, k):
    x, y, z = p
    for _ in range(k % 4):
        x, z = -z, x
    return (x, y, z)


def _unrot_dir(d, k):
    x, y, z = d
    for _ in range((4 - k % 4) % 4):
        x, z = -z, x
    return (x, y, z)


def _iso(ox, oy, S):
    return lambda a, b, c: (ox + (a - c) * S, oy + (a + c) * S * 0.5 - b * S)


def _face_poly(iso, x, y, z, face, h0=0.0, h1=1.0, x0=0.0, x1=1.0, z0=0.0, z1=1.0):
    if face == "top":
        return [iso(x + x0, y + h1, z + z0), iso(x + x1, y + h1, z + z0), iso(x + x1, y + h1, z + z1), iso(x + x0, y + h1, z + z1)]
    if face == "left":  # the +z face
        return [iso(x + x0, y + h1, z + z1), iso(x + x1, y + h1, z + z1), iso(x + x1, y + h0, z + z1), iso(x + x0, y + h0, z + z1)]
    return [iso(x + x1, y + h1, z + z1), iso(x + x1, y + h1, z + z0), iso(x + x1, y + h0, z + z0), iso(x + x1, y + h0, z + z1)]


def _affine_from(src3, dst3):
    """Inverse affine (dst -> src) coefficients for PIL from three point pairs."""
    A = np.array([[dst3[i][0], dst3[i][1], 1.0] for i in range(3)])
    bx = np.array([src3[i][0] for i in range(3)])
    by = np.array([src3[i][1] for i in range(3)])
    try:
        a = np.linalg.solve(A, bx)
        d = np.linalg.solve(A, by)
    except np.linalg.LinAlgError:
        return None
    return (a[0], a[1], a[2], d[0], d[1], d[2])


_WARP = {}


def _warp(tex, poly):
    """The 16x16 texture on a parallelogram: returns (sprite, x, y)."""
    minx, miny = int(math.floor(min(q[0] for q in poly))), int(math.floor(min(q[1] for q in poly)))
    maxx, maxy = int(math.ceil(max(q[0] for q in poly))), int(math.ceil(max(q[1] for q in poly)))
    rel = [(q[0] - minx, q[1] - miny) for q in poly]
    key = (id(tex), tuple((round(a, 2), round(b, 2)) for a, b in rel))
    spr = _WARP.get(key)
    if spr is None:
        coeffs = _affine_from([(0, 0), (16, 0), (16, 16)], rel[:3])
        if coeffs is None:
            return None, 0, 0
        spr = tex.transform((max(1, maxx - minx), max(1, maxy - miny)), Image.AFFINE, coeffs, resample=Image.NEAREST)
        # clip to the parallelogram (the affine fills the whole box)
        mask = Image.new("L", spr.size, 0)
        ImageDraw.Draw(mask).polygon([(a, b) for a, b in rel], fill=255)
        a = np.minimum(np.asarray(spr)[..., 3], np.asarray(mask))
        arr = np.asarray(spr).copy()
        arr[..., 3] = a
        spr = Image.fromarray(arr, "RGBA")
        _WARP[key] = spr
    return spr, minx, miny


def _shade(im, b, night):
    a = np.asarray(im).astype(np.float32)
    a[..., :3] *= b
    if night:
        a[..., 2] = np.minimum(255, a[..., 2] * 1.0 + 14 * (1 - b))
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def render_iso(scene, k=0, night=False, S=10, vortex=None):
    """One isometric view (camera at +x +z after turning the scene k quarter turns), day or night."""
    cells = {_rot(p, k): v for p, v in scene.cells.items()}
    light = {_rot(p, k): v for p, v in scene.light().items()} if night else {}
    pts = list(cells) or [(0, 0, 0)]
    xs, ys, zs = [p[0] for p in pts], [p[1] for p in pts], [p[2] for p in pts]
    top_extra = 0
    if vortex is not None:
        top_extra = int(vortex["cy"] + vortex["ry"] * 1.8) + 2
    lo_s, hi_s = min(x - z for x, _, z in pts) - 2, max(x - z for x, _, z in pts) + 2
    lo_d, hi_d = min(x + z for x, _, z in pts) - 2, max(x + z for x, _, z in pts) + 2
    W = int((hi_s - lo_s) * S) + 40
    H = int((hi_d - lo_d) * S * 0.5 + (max(max(ys), top_extra) - min(ys) + 3) * S) + 40
    ox = int(-lo_s * S) + 20
    oy = int(-lo_d * S * 0.5 + (max(max(ys), top_extra) + 2) * S) + 20
    iso = _iso(ox, oy, S)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def bright(face_kind, p):
        mul = {"top": 1.0, "left": 0.8, "right": 0.62}[face_kind]
        if not night:
            return mul
        d = {"top": (0, 1, 0), "left": (0, 0, 1), "right": (1, 0, 0)}[face_kind]
        q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
        l = max(light.get(q, 0), 0)
        return (0.06 + 0.94 * (l / 15.0) ** 1.4) * (0.55 + 0.45 * mul)

    def draw(p):
        name, props = cells[p]
        shape, faces = block_look(name, props)
        x, y, z = p
        fb = _full_bright(name)
        if shape == "cross":
            tex = faces["north"]
            w = int(S * 1.55)
            spr = tex.resize((w, w), Image.NEAREST)
            b = 1.0 if (not night or fb) else bright("top", p)
            spr = _shade(spr, b, night and not fb)
            cx, cy = iso(x + 0.5, y, z + 0.5)
            img.alpha_composite(spr, (int(cx - w / 2), int(cy - w + S * 0.25)))
            if night and fb:
                glow.alpha_composite(spr, (int(cx - w / 2), int(cy - w + S * 0.25)))
            return
        h0, h1, x0, x1, z0, z1 = 0.0, 1.0, 0.0, 1.0, 0.0, 1.0
        if isinstance(shape, tuple):
            _, h0, h1, bx0, bx1, bz0, bz1 = shape
            # the box turns with the view
            corners = [_rot((bx0 - 0.5, 0, bz0 - 0.5), k), _rot((bx1 - 0.5, 0, bz1 - 0.5), k)]
            x0, x1 = sorted((corners[0][0] + 0.5, corners[1][0] + 0.5))
            z0, z1 = sorted((corners[0][2] + 0.5, corners[1][2] + 0.5))
        if shape == "liquid":
            h1 = 0.88
        for face_kind, rd in (("top", (0, 1, 0)), ("left", (0, 0, 1)), ("right", (1, 0, 0))):
            nb = (x + rd[0], y + rd[1], z + rd[2])
            if nb in cells:
                nl = block_look(*cells[nb])
                if shape != "liquid" and _opaque_look(nl) and not isinstance(shape, tuple):
                    continue
                if shape == "liquid" and nl[0] == "liquid":
                    continue
            tex = faces[DIR_OF[_unrot_dir(rd, k)]]
            if face_kind != "top" and (h1 - h0) < 0.999:
                t0, t1 = int(round((1 - h1) * 16)), int(round((1 - h0) * 16))
                tex = tex.crop((0, t0, 16, max(t0 + 1, t1))).resize((16, 16), Image.NEAREST)
            spr, mx, my = _warp(tex, _face_poly(iso, x, y, z, face_kind, h0, h1, x0, x1, z0, z1))
            if spr is None:
                continue
            b = 1.0 if (fb and night) else bright(face_kind, p)
            spr = _shade(spr, max(b, 0.92) if (fb and not night) else b, night and not fb)
            if shape == "liquid":
                arr = np.asarray(spr).copy()
                arr[..., 3] = (arr[..., 3] * 0.78).astype(np.uint8)
                spr = Image.fromarray(arr, "RGBA")
            img.alpha_composite(spr, (mx, my))
            if night and fb:
                glow.alpha_composite(spr, (mx, my))

    order = sorted(cells, key=lambda p: (p[0] + p[1] + p[2], p[1], p[0]))
    if vortex is None:
        for p in order:
            draw(p)
    else:
        # the opening's plane splits the scene: what is behind it, the vortex, what is in front
        n = _rot((0, 0, 1) if vortex["axis"] == 0 else (1, 0, 0), k)
        s = 1 if (n[0] + n[2]) > 0 else -1
        c = _rot((0.5, 0, 0.5), k)
        def front(p):
            return ((p[0] + 0.5 - (c[0])) * n[0] + (p[2] + 0.5 - c[2]) * n[2]) * s > 0.01
        for p in order:
            if not front(p):
                draw(p)
        layer = _vortex_on_iso(vortex, k, iso, (W, H), s)
        img.alpha_composite(layer)
        if night:
            glow.alpha_composite(layer)
        for p in order:
            if front(p):
                draw(p)
    return _finish(img, glow, night)


def _finish(img, glow, night):
    W, H = img.size
    t = np.linspace(0, 1, H)[:, None, None]
    top, bot = (np.array([6, 9, 22]), np.array([20, 26, 48])) if night else (np.array([118, 164, 250]), np.array([200, 222, 255]))
    bg = np.zeros((H, W, 4), np.float32)
    bg[..., :3] = top[None, None, :] * (1 - t) + bot[None, None, :] * t
    bg[..., 3] = 255
    if night:
        rng = np.random.default_rng(7)
        for _ in range(W * H // 2000):
            bg[rng.integers(0, H), rng.integers(0, W), :3] = 170
    base = Image.fromarray(bg.astype(np.uint8), "RGBA")
    base.alpha_composite(img)
    if night:
        b = np.asarray(base).astype(np.float32)
        for radius, k in ((10, 0.8), (3, 0.45)):
            ga = np.asarray(glow.filter(ImageFilter.GaussianBlur(radius))).astype(np.float32)
            b[..., :3] += ga[..., :3] * (ga[..., 3:4] / 255.0) * k
        base = Image.fromarray(b.clip(0, 255).astype(np.uint8), "RGBA")
    return base


# ---- the vortex: rasterise the exported quads in the plane of the opening, then lay that plane into a view -----------

_ATLAS = None


def _atlas():
    global _ATLAS
    if _ATLAS is None:
        _ATLAS = np.asarray(Image.open(os.path.join(ASSETS, "textures/entity/gate_vortex.png")).convert("RGBA")).astype(np.float32) / 255.0
    return _ATLAS


def rasterise_vortex(frame, ppb=24, pad=0.4):
    """The frame seen straight on (camera on +z): RGBA float image and its plane bounds (x0, x1, y0, y1)."""
    q = np.array(frame["quads"], np.float64).reshape(-1, 4, 6)
    rx, ry, cy = frame["rx"], frame["ry"], frame["cy"]
    x0, x1 = -max(rx * 1.75, rx + 3.5) - pad, max(rx * 1.75, rx + 3.5) + pad
    y0, y1 = -cy - pad, ry * 1.75 + pad
    W, H = int((x1 - x0) * ppb), int((y1 - y0) * ppb)
    col = np.zeros((H, W, 3))
    alpha = np.zeros((H, W))
    atlas = _atlas()
    # the game sorts translucent quads far to near; the camera is on +z, so by mean z then draw order
    order = np.argsort(q[:, :, 2].mean(axis=1), kind="stable")
    for qi in order:
        v = q[qi]
        X = (v[:, 0] - x0) * ppb
        Y = (y1 - v[:, 1]) * ppb
        Uv, Vv = v[:, 3], v[:, 4]
        cols = v[:, 5].astype(np.int64) & 0xFFFFFFFF
        A = ((cols >> 24) & 255) / 255.0
        R = ((cols >> 16) & 255) / 255.0
        G = ((cols >> 8) & 255) / 255.0
        B = (cols & 255) / 255.0
        for tri in ((0, 1, 2), (0, 2, 3)):
            i, j, kk = tri
            xa, ya = X[[i, j, kk]], Y[[i, j, kk]]
            bx0, bx1 = int(max(0, math.floor(xa.min()))), int(min(W - 1, math.ceil(xa.max())))
            by0, by1 = int(max(0, math.floor(ya.min()))), int(min(H - 1, math.ceil(ya.max())))
            if bx1 < bx0 or by1 < by0:
                continue
            det = (ya[1] - ya[2]) * (xa[0] - xa[2]) + (xa[2] - xa[1]) * (ya[0] - ya[2])
            if abs(det) < 1e-9:
                continue
            gy, gx = np.mgrid[by0:by1 + 1, bx0:bx1 + 1]
            px, py = gx + 0.5, gy + 0.5
            l0 = ((ya[1] - ya[2]) * (px - xa[2]) + (xa[2] - xa[1]) * (py - ya[2])) / det
            l1 = ((ya[2] - ya[0]) * (px - xa[2]) + (xa[0] - xa[2]) * (py - ya[2])) / det
            l2 = 1 - l0 - l1
            m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not m.any():
                continue
            w = (l0[m], l1[m], l2[m])
            u = w[0] * Uv[i] + w[1] * Uv[j] + w[2] * Uv[kk]
            vv = w[0] * Vv[i] + w[1] * Vv[j] + w[2] * Vv[kk]
            tx = np.mod(np.floor(u * 512).astype(np.int64), 512)
            ty = np.clip(np.floor(vv * 512).astype(np.int64), 0, 511)
            tex = atlas[ty, tx]
            ca = (w[0] * A[i] + w[1] * A[j] + w[2] * A[kk]) * tex[:, 3]
            cr = (w[0] * R[i] + w[1] * R[j] + w[2] * R[kk]) * tex[:, 0]
            cg = (w[0] * G[i] + w[1] * G[j] + w[2] * G[kk]) * tex[:, 1]
            cb = (w[0] * B[i] + w[1] * B[j] + w[2] * B[kk]) * tex[:, 2]
            yy, xx = gy[m], gx[m]
            for ch, val in ((0, cr), (1, cg), (2, cb)):
                col[yy, xx, ch] = val * ca + col[yy, xx, ch] * (1 - ca)
            alpha[yy, xx] = ca + alpha[yy, xx] * (1 - ca)
    # shards: lit boxes, painted far to near
    s = np.array(frame.get("shards", []), np.float64).reshape(-1, 4, 8)
    if len(s):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        light = np.array([0.3, 0.8, 0.5])
        light /= np.linalg.norm(light)
        for qi in np.argsort(s[:, :, 2].mean(axis=1)):
            f = s[qi]
            nrm = f[0, 5:8]
            if nrm[2] < -0.05:
                continue
            b = 0.35 + 0.65 * max(0.0, float(nrm @ light))
            c = (int(40 * b + 8), int(36 * b + 8), int(58 * b + 12), 255)
            dr.polygon([((p[0] - x0) * ppb, (y1 - p[1]) * ppb) for p in f], fill=c, outline=(90, 110, 160, 255))
        sa = np.asarray(img).astype(np.float32) / 255.0
        a = sa[..., 3]
        col = sa[..., :3] * a[..., None] + col * (1 - a[..., None])
        alpha = a + alpha * (1 - a)
    rgba = np.zeros((H, W, 4))
    safe = np.maximum(alpha, 1e-6)[..., None]
    rgba[..., :3] = np.where(alpha[..., None] > 1e-6, col / safe * 1.0, 0) * 1.0
    # premultiplied accumulation -> straight alpha
    rgba[..., :3] = np.clip(col / safe, 0, 1)
    rgba[..., 3] = np.clip(alpha, 0, 1)
    return rgba, (x0, x1, y0, y1)


def vortex_image(frame, ppb=24, background=None):
    rgba, b = rasterise_vortex(frame, ppb)
    im = to_img(rgba[..., :3], rgba[..., 3])
    if background is not None:
        bg = Image.new("RGBA", im.size, background)
        bg.alpha_composite(im)
        im = bg
    return im, b


_FRAMES = None


def _frames():
    global _FRAMES
    if _FRAMES is None:
        p = os.path.join(RUN, "vortex_frames.json")
        _FRAMES = json.load(open(p))["frames"] if os.path.exists(p) else []
    return _FRAMES


def frame_for(rank, mode, t=0.6):
    for f in _frames():
        if f["rank"] == rank and f["mode"] == mode and abs(f["t"] - t) < 1e-3:
            return f
    for f in _frames():
        if f["rank"] == rank and f["mode"] == "open":
            return f
    return None


def _vortex_on_iso(v, k, iso, size, side):
    """The vortex layer of a gate scene laid into an isometric view (a plane through the opening's centre)."""
    mode = ["open", "warning", "broken", "calm", "spent", "forming"][v["mode"]]
    frame = frame_for(v["rank"], mode)
    if frame is None:
        return Image.new("RGBA", size, (0, 0, 0, 0))
    ppb = 16
    rgba, (x0, x1, y0, y1) = rasterise_vortex(frame, ppb)
    im = to_img(rgba[..., :3], rgba[..., 3])
    # the plane: local x along the face (world x for axis 0, world z for axis 1), y up; centre at (0.5, cy, 0.5)
    face = (1, 0, 0) if v["axis"] == 0 else (0, 0, 1)
    cxw, cyw, czw = 0.5, v["centre"][1], 0.5

    def world(lx, ly):
        p = (cxw + lx * face[0], cyw + ly, czw + lx * face[2])
        return iso(*_rot(p, k))
    src = [(0, 0), (im.width, 0), (0, im.height)]
    dst = [world(x0, y1), world(x1, y1), world(x0, y0)]
    coeffs = _affine_from(src, dst)
    return im.transform(size, Image.AFFINE, coeffs, resample=Image.BILINEAR)


# ---- driver ------------------------------------------------------------------------------------------------------------

def _label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 8 + 6 * len(text), 16), fill=(0, 0, 0, 160))
    d.text((4, 3), text, fill=(235, 238, 245, 255))
    return im


def preview(outdir, only=()):
    os.makedirs(outdir, exist_ok=True)
    files = sorted(f for f in os.listdir(RUN) if f.endswith(".json") and f != "vortex_frames.json")
    if only:
        files = [f for f in files if any(o in f for o in only)]
    for f in files:
        data = json.load(open(os.path.join(RUN, f)))
        sc = Scene(data)
        vx = None
        if sc.vortex is not None:
            vx = dict(sc.vortex)
            vx["cy"], vx["ry"] = vx["centre"][1], vx["ry"]
        S = 9 if len(sc.cells) > 6000 else 11
        views = []
        for k, night in ((0, False), (1, False), (0, True), (3, True)):
            im = render_iso(sc, k, night, S, vx)
            views.append(_label(im, f"{sc.name}  view {k}  {'night' if night else 'day'}"))
        w = max(v.width for v in views)
        h = max(v.height for v in views)
        sheet = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 255))
        for i, v in enumerate(views):
            sheet.paste(v, ((i % 2) * w, (i // 2) * h))
        sheet.save(os.path.join(outdir, f[:-5] + ".png"))
        print("drew", f[:-5])
    if not only or "vortex" in only:
        vortex_sheet(outdir)


def vortex_sheet(outdir):
    frames = _frames()
    if not frames:
        return
    tiles = []
    for f in frames:
        if f["t"] != 0.6 and not (f["rank"] in (2, 5) and f["mode"] == "open"):
            continue
        im, _ = vortex_image(f, 18, (8, 10, 20, 255))
        tiles.append(_label(im, f"{RANK_IDS[f['rank']].upper()} {f['mode']} t={f['t']}"))
    cols = 4
    w = max(t.width for t in tiles)
    h = max(t.height for t in tiles)
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (w * cols, h * rows), (0, 0, 0, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * w + (w - t.width) // 2, (i // cols) * h + (h - t.height)))
    sheet.save(os.path.join(outdir, "vortex_frames.png"))
    # day view of the open ranks side by side (how they read against a bright sky)
    day = [vortex_image(frame_for(r, "open"), 12, (150, 190, 250, 255))[0] for r in range(7) if frame_for(r, "open")]
    if day:
        W = sum(d.width for d in day)
        H = max(d.height for d in day)
        strip = Image.new("RGBA", (W, H), (150, 190, 250, 255))
        x = 0
        for d in day:
            strip.paste(d, (x, H - d.height))
            x += d.width
        strip.save(os.path.join(outdir, "vortex_ranks_day.png"))
    print("drew the vortex frames")
# <<< preview


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/structures_preview", sys.argv[3:])
    else:
        print(__doc__)
