#!/usr/bin/env python3
"""Terrain studio: turns `/awaken terrain survey` grids (terrain/<name>.json) into maps and numbers.

  survey.py stats <grid.json>...            numbers per grid (relief, land, biomes per km, biome shares)
  survey.py map <grid.json> <out.png>       a hill-shaded biome map, spawn at the centre
  survey.py sheet <out.png> <grid.json>...  maps side by side with their key numbers (for comparing presets)

Numbers (docs/SPEC.md §15):
  land        share of samples above sea level
  relief      max - min ground height in each 512-block window: median and 90th percentile
  high        share of land above y 120 / 160 / 200 / 250
  per_km      distinct landscapes per kilometre of mostly-land ray (biomes grouped by look; rivers, beaches and
              seas left out), averaged over eight rays from the centre
  changes_km  landscape changes per kilometre on the same rays
  awakened    share of land in awaken: biomes
"""
import hashlib
import json
import math
import statistics
import sys

# Map colours for vanilla biomes (roughly their look from above); anything else gets a stable hashed colour.
COLOURS = {
    "plains": (141, 179, 96), "sunflower_plains": (160, 190, 90), "meadow": (131, 187, 109), "cherry_grove": (236, 170, 205),
    "forest": (56, 120, 50), "flower_forest": (90, 150, 60), "birch_forest": (98, 150, 80), "old_growth_birch_forest": (88, 140, 70),
    "dark_forest": (40, 82, 30), "pale_garden": (150, 150, 140), "dappled_forest": (100, 140, 60),
    "taiga": (50, 102, 90), "old_growth_pine_taiga": (70, 90, 60), "old_growth_spruce_taiga": (60, 85, 65),
    "snowy_taiga": (200, 215, 220), "snowy_plains": (240, 245, 250), "ice_spikes": (190, 220, 245), "grove": (215, 225, 230),
    "snowy_slopes": (230, 235, 240), "frozen_peaks": (200, 225, 245), "jagged_peaks": (220, 225, 235), "stony_peaks": (150, 150, 150),
    "windswept_hills": (110, 120, 110), "windswept_gravelly_hills": (125, 125, 120), "windswept_forest": (70, 100, 70),
    "windswept_savanna": (170, 160, 90), "savanna": (189, 178, 95), "savanna_plateau": (167, 157, 100),
    "desert": (230, 215, 160), "badlands": (190, 90, 40), "eroded_badlands": (205, 105, 50), "wooded_badlands": (160, 110, 60),
    "jungle": (40, 140, 30), "sparse_jungle": (90, 160, 60), "bamboo_jungle": (70, 170, 50),
    "swamp": (70, 90, 60), "mangrove_swamp": (60, 100, 70), "mushroom_fields": (160, 120, 160),
    "river": (60, 110, 200), "frozen_river": (160, 180, 230), "beach": (240, 230, 170), "snowy_beach": (240, 240, 230), "stony_shore": (130, 130, 130),
    "ocean": (40, 70, 160), "deep_ocean": (25, 45, 120), "warm_ocean": (50, 110, 180), "lukewarm_ocean": (45, 90, 170),
    "deep_lukewarm_ocean": (30, 60, 140), "cold_ocean": (40, 60, 140), "deep_cold_ocean": (25, 40, 110),
    "frozen_ocean": (130, 150, 200), "deep_frozen_ocean": (90, 110, 170),
}


# Landscapes: biomes that look alike from the ground count as one. Water and shores are not landscapes here.
GROUPS = {
    "plains": "open", "sunflower_plains": "open", "meadow": "open",
    "forest": "woods", "flower_forest": "woods", "birch_forest": "woods", "old_growth_birch_forest": "woods", "dappled_forest": "woods",
    "dark_forest": "dark", "pale_garden": "dark",
    "taiga": "taiga", "old_growth_pine_taiga": "taiga", "old_growth_spruce_taiga": "taiga",
    "snowy_taiga": "snow", "snowy_plains": "snow", "ice_spikes": "snow", "grove": "snow", "snowy_slopes": "snow",
    "frozen_peaks": "peaks", "jagged_peaks": "peaks", "stony_peaks": "peaks",
    "windswept_hills": "hills", "windswept_gravelly_hills": "hills", "windswept_forest": "hills",
    "savanna": "savanna", "savanna_plateau": "savanna", "windswept_savanna": "savanna",
    "desert": "desert", "badlands": "badlands", "eroded_badlands": "badlands", "wooded_badlands": "badlands",
    "jungle": "jungle", "sparse_jungle": "jungle", "bamboo_jungle": "jungle",
    "swamp": "swamp", "mangrove_swamp": "swamp", "mushroom_fields": "mushroom", "cherry_grove": "cherry",
}
WATER = {"river", "frozen_river", "beach", "snowy_beach", "stony_shore", "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean",
         "deep_lukewarm_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean"}


def landscape(biome):
    ns, _, path = biome.partition(":")
    if ns == "minecraft":
        return None if path in WATER else GROUPS.get(path, path)
    return biome  # every Awakened biome is its own landscape


def colour(biome):
    ns, _, path = biome.partition(":")
    if ns == "minecraft" and path in COLOURS:
        return COLOURS[path]
    h = hashlib.sha1(biome.encode()).digest()
    return (80 + h[0] % 160, 80 + h[1] % 160, 80 + h[2] % 160)


def load(path):
    with open(path) as f:
        g = json.load(f)
    assert g.get("schemaVersion") == 1, path
    return g


def stats(g):
    n, step, sea = g["size"], g["step"], g["seaLevel"]
    floor, biome, pal = g["floor"], g["biome"], g["palette"]
    land = [i for i, f in enumerate(floor) if f >= sea]
    out = {"name": g["name"], "size_blocks": (n - 1) * step, "step": step, "millis": g["millis"],
           "land": len(land) / len(floor)}
    # relief per 512-block window (non-overlapping)
    w = max(1, 512 // step)
    reliefs = []
    for r0 in range(0, n - w + 1, w):
        for c0 in range(0, n - w + 1, w):
            vals = [floor[(r0 + r) * n + c0 + c] for r in range(w) for c in range(w)]
            reliefs.append(max(vals) - min(vals))
    reliefs.sort()
    out["relief_p50"] = statistics.median(reliefs) if reliefs else 0
    out["relief_p90"] = reliefs[int(0.9 * (len(reliefs) - 1))] if reliefs else 0
    lh = [floor[i] for i in land]
    for y in (120, 160, 200, 250):
        out[f"high_{y}"] = sum(1 for v in lh if v >= y) / max(1, len(lh))
    out["peak"] = max(floor)
    # eight rays from the centre
    c = n // 2
    per_km, changes_km = [], []
    for k in range(8):
        a = k * math.pi / 4
        ray = []
        for s in range(c + 1):
            col, row = c + round(math.cos(a) * s), c + round(math.sin(a) * s)
            if 0 <= col < n and 0 <= row < n:
                ray.append(landscape(pal[biome[row * n + col]]) if floor[row * n + col] >= sea else None)
        length_km = max(1e-9, (len(ray) - 1) * step / 1000)
        # distinct biomes in each whole kilometre of the ray, averaged
        per = max(1, int(1000 // step))
        chunks = [ray[i:i + per] for i in range(0, len(ray), per) if len(ray[i:i + per]) == per]
        # only kilometres that are mostly land: open sea is not "the same landscape"
        land_chunks = [ch for ch in chunks if sum(1 for v in ch if v is not None) >= len(ch) / 2]
        if land_chunks:
            per_km.append(statistics.mean(len(set(ch) - {None}) for ch in land_chunks))
        land_ray = [v for v in ray if v is not None]
        changes_km.append(sum(1 for i in range(1, len(land_ray)) if land_ray[i] != land_ray[i - 1]) / length_km)
    out["per_km"] = statistics.mean(per_km) if per_km else 0
    out["changes_km"] = statistics.mean(changes_km)
    counts = {}
    for i in land:
        counts[pal[biome[i]]] = counts.get(pal[biome[i]], 0) + 1
    out["biomes"] = len(counts)
    out["awakened"] = sum(v for k, v in counts.items() if k.startswith("awaken:")) / max(1, len(land))
    out["shares"] = sorted(((k, v / max(1, len(land))) for k, v in counts.items()), key=lambda kv: -kv[1])
    return out


def render(g, scale=None):
    from PIL import Image
    n, sea = g["size"], g["seaLevel"]
    floor, biome, pal = g["floor"], g["biome"], g["palette"]
    img = Image.new("RGB", (n, n))
    px = img.load()
    for row in range(n):
        for col in range(n):
            i = row * n + col
            f = floor[i]
            if f < sea:
                depth = min(1.0, (sea - f) / 60)
                base = (int(70 - 40 * depth), int(120 - 60 * depth), int(210 - 70 * depth))
                px[col, row] = base
                continue
            r, gg, b = colour(pal[biome[i]])
            # hill shade from the north-west, plus a little height brightening
            fx = floor[row * n + min(n - 1, col + 1)] - floor[row * n + max(0, col - 1)]
            fz = floor[min(n - 1, row + 1) * n + col] - floor[max(0, row - 1) * n + col]
            shade = 1.0 - 0.035 * (fx + fz) / max(1, g["step"] / 8)
            shade = max(0.45, min(1.45, shade)) * (0.85 + 0.3 * min(1.0, (f - sea) / 160))
            px[col, row] = (min(255, int(r * shade)), min(255, int(gg * shade)), min(255, int(b * shade)))
    if scale:
        img = img.resize((scale, scale), Image.NEAREST)
    return img


def draw_spawn(img):
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    c = img.width // 2
    d.ellipse((c - 5, c - 5, c + 5, c + 5), outline=(255, 40, 40), width=2)


def fmt(s):
    return (f"land {s['land']:.0%} · relief p50 {s['relief_p50']} p90 {s['relief_p90']} · peak {s['peak']} · "
            f">160 {s['high_160']:.0%} >200 {s['high_200']:.0%} · landscapes/km {s['per_km']:.2f} · changes/km {s['changes_km']:.1f} · "
            f"biomes {s['biomes']} · awakened {s['awakened']:.0%}")


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd = argv[1]
    if cmd == "stats":
        for p in argv[2:]:
            s = stats(load(p))
            print(f"{s['name']}: {fmt(s)}")
            print("   top: " + ", ".join(f"{k.split(':')[-1]} {v:.0%}" for k, v in s["shares"][:10]))
        return 0
    if cmd == "map":
        img = render(load(argv[2]), 768)
        draw_spawn(img)
        img.save(argv[3])
        return 0
    if cmd == "sheet":
        from PIL import Image, ImageDraw
        out, grids = argv[2], [load(p) for p in argv[3:]]
        tile, pad, text = 640, 8, 64
        sheet = Image.new("RGB", (len(grids) * (tile + pad) + pad, tile + text + 2 * pad), (16, 18, 24))
        d = ImageDraw.Draw(sheet)
        for k, g in enumerate(grids):
            img = render(g, tile)
            draw_spawn(img)
            x = pad + k * (tile + pad)
            sheet.paste(img, (x, pad))
            s = stats(g)
            d.text((x, tile + pad + 4), f"{g['name']}  ({s['size_blocks']} blocks across)", fill=(255, 230, 120))
            line = fmt(s)
            mid = line.find(" · landscapes/km")
            d.text((x, tile + pad + 20), line[:mid], fill=(220, 220, 220))
            d.text((x, tile + pad + 36), line[mid + 3:], fill=(220, 220, 220))
        sheet.save(out)
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
