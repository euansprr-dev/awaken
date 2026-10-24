#!/usr/bin/env python3
"""Lens looks per biome for Awakened Lands (docs/modules/lands.md): writes assets/awaken/lens/looks_lands.json.

  python3 -I tools/terrain/looks.py write | check

Each named look only says how it differs from Lens's overworld default (looks.json "default"); Lens fills in the
rest. Vanilla biomes get gentle looks (the land they already are, a little more so); Awakened biomes get strong ones.
Rules from SPEC §14: exposure ≤ 1.05, shadows never lifted (no shadow lift fields exist), and the haze goes toward
the game's own fog colour unless hazeTintMix says otherwise. Biomes not listed keep the default look.
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
OUT = os.path.join(ROOT, "src/main/resources/assets/awaken/lens/looks_lands.json")

LOOKS = {
    # --- vanilla groups: gentle ---
    "sunbaked": ("Sunbaked: warm dust in the air, bleached highlights, a pale gold haze", {
        "warmth": 0.14, "saturation": 1.06, "highlightTint": [1.0, 0.9, 0.7], "haze": 0.4, "hazeStart": 0.35,
        "hazeTint": [0.95, 0.85, 0.65], "hazeTintMix": 0.45, "hazeDesaturate": 0.18, "sunGlow": 1.0}),
    "red_rock": ("Red rock: rust shadows, hot light, dust", {
        "warmth": 0.18, "contrast": 1.12, "shadowTint": [0.5, 0.36, 0.32], "highlightTint": [1.0, 0.82, 0.6],
        "haze": 0.36, "hazeTint": [0.95, 0.75, 0.6], "hazeTintMix": 0.4, "sunsetWarmth": 0.8}),
    "golden_grass": ("Golden grass: long warm light over the savanna", {
        "warmth": 0.12, "saturation": 1.1, "highlightTint": [1.0, 0.88, 0.62], "haze": 0.34,
        "hazeTint": [0.95, 0.88, 0.7], "hazeTintMix": 0.35, "sunsetWarmth": 0.8}),
    "humid": ("Humid: wet green air, soft highlights, haze close in", {
        "saturation": 1.16, "warmth": 0.04, "shadowTint": [0.36, 0.5, 0.4], "bloom": 0.85, "haze": 0.46,
        "hazeStart": 0.28, "hazeTint": [0.7, 0.85, 0.72], "hazeTintMix": 0.4, "shafts": 0.7}),
    "murk": ("Murk: olive water light, low mist, flat shadows", {
        "saturation": 0.98, "warmth": 0.02, "shadowTint": [0.38, 0.44, 0.36], "highlightTint": [0.92, 0.95, 0.8],
        "haze": 0.5, "hazeStart": 0.25, "hazeTint": [0.62, 0.7, 0.6], "hazeTintMix": 0.5, "hazeDesaturate": 0.2,
        "vignette": 0.18}),
    "deep_wood": ("Deep wood: dim, cool undergrowth, light breaking through", {
        "contrast": 1.12, "saturation": 1.06, "shadowTint": [0.34, 0.42, 0.42], "vignette": 0.2, "shafts": 0.8,
        "haze": 0.36, "hazeStart": 0.3, "hazeTint": [0.6, 0.7, 0.66], "hazeTintMix": 0.35}),
    "pine_mist": ("Pine mist: cool air, blue-green distance", {
        "warmth": -0.04, "shadowTint": [0.36, 0.46, 0.52], "haze": 0.42, "hazeStart": 0.32,
        "hazeTint": [0.66, 0.78, 0.82], "hazeTintMix": 0.4, "hazeDesaturate": 0.16, "shafts": 0.65}),
    "crisp_cold": ("Crisp cold: clean blue shadows, bright snow, clear distance", {
        "warmth": -0.08, "contrast": 1.1, "saturation": 1.04, "shadowTint": [0.4, 0.5, 0.7],
        "highlightTint": [0.96, 0.97, 1.0], "bloomThreshold": 0.7, "haze": 0.26, "hazeTint": [0.75, 0.85, 1.0],
        "hazeTintMix": 0.35, "sunGlow": 0.9}),
    "high_air": ("High air: thin and clear, deep sky, sharp light", {
        "contrast": 1.12, "saturation": 1.1, "shadowTint": [0.4, 0.46, 0.64], "haze": 0.22, "hazeStart": 0.6,
        "hazeTint": [0.7, 0.8, 1.0], "hazeTintMix": 0.3}),
    "bright_meadow": ("Bright meadow: warm open light, colour in the flowers", {
        "saturation": 1.18, "warmth": 0.08, "bloom": 0.8, "haze": 0.3}),
    "blossom": ("Blossom: soft pink bloom, warm light", {
        "saturation": 1.14, "warmth": 0.08, "highlightTint": [1.0, 0.88, 0.9], "bloom": 0.9, "bloomThreshold": 0.58,
        "haze": 0.34, "hazeTint": [0.98, 0.85, 0.9], "hazeTintMix": 0.35}),
    "fungal": ("Fungal: violet shadows, a strange soft light", {
        "saturation": 1.08, "shadowTint": [0.46, 0.38, 0.52], "highlightTint": [1.0, 0.9, 0.95],
        "haze": 0.38, "hazeTint": [0.8, 0.7, 0.85], "hazeTintMix": 0.45}),
    "pale": ("Pale: drained colour, grey mist, an old stillness", {
        "saturation": 0.85, "contrast": 1.06, "shadowTint": [0.44, 0.46, 0.48], "haze": 0.45, "hazeStart": 0.28,
        "hazeTint": [0.72, 0.74, 0.74], "hazeTintMix": 0.55, "hazeDesaturate": 0.3, "vignette": 0.18}),
    # --- Awakened biomes: strong ---
    "elderwood": ("Gold Elderwood: sun through a high canopy, gold highlights, green-gold shadows, a warm haze, strong shafts", {
        "exposure": 1.02, "contrast": 1.1, "saturation": 1.15, "warmth": 0.14, "shadowTint": [0.45, 0.5, 0.4],
        "highlightTint": [1.0, 0.88, 0.6], "splitTone": 0.14, "vignette": 0.14, "bloom": 0.8, "bloomThreshold": 0.6,
        "skyBloom": 0.3, "shafts": 1.0, "haze": 0.38, "hazeStart": 0.35, "hazeTint": [0.95, 0.85, 0.6],
        "hazeTintMix": 0.45, "hazeDesaturate": 0.08, "sunsetWarmth": 0.75, "sunGlow": 0.9}),
    "lumen": ("Lumen: cool teal shadows, the glow blooms at night, a low blue mist", {
        "exposure": 1.0, "contrast": 1.12, "saturation": 1.1, "warmth": -0.08, "shadowTint": [0.3, 0.5, 0.6],
        "highlightTint": [0.75, 0.95, 1.0], "splitTone": 0.16, "vignette": 0.2, "bloom": 1.35, "bloomThreshold": 0.38,
        "skyBloom": 0.2, "shafts": 0.4, "haze": 0.42, "hazeStart": 0.3, "hazeTint": [0.45, 0.75, 0.85],
        "hazeTintMix": 0.55, "hazeDesaturate": 0.15, "sunsetWarmth": 0.35, "sunGlow": 0.6}),
}

BIOMES = {
    "sunbaked": ["desert", "beach"],
    "red_rock": ["badlands", "eroded_badlands", "wooded_badlands"],
    "golden_grass": ["savanna", "savanna_plateau", "windswept_savanna"],
    "humid": ["jungle", "sparse_jungle", "bamboo_jungle"],
    "murk": ["swamp", "mangrove_swamp"],
    "deep_wood": ["dark_forest"],
    "pine_mist": ["taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "windswept_forest"],
    "crisp_cold": ["snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes", "snowy_beach", "frozen_river"],
    "high_air": ["jagged_peaks", "frozen_peaks", "stony_peaks", "windswept_hills", "windswept_gravelly_hills"],
    "bright_meadow": ["meadow", "flower_forest", "sunflower_plains"],
    "blossom": ["cherry_grove"],
    "fungal": ["mushroom_fields"],
    "pale": ["pale_garden"],
    "elderwood": ["awaken:elderwood"],
    "lumen": ["awaken:lumen_forest"],
}


def render():
    looks = {}
    seen = set()
    for look, biomes in BIOMES.items():
        name, fields = LOOKS[look]
        for b in biomes:
            bid = b if ":" in b else "minecraft:" + b
            assert bid not in seen, f"{bid} has two looks"
            seen.add(bid)
            assert fields.get("exposure", 1.0) <= 1.05, f"{look}: exposure over 1.05"
            looks["biome:" + bid] = {"_name": name, **fields}
    doc = {
        "schemaVersion": 1,
        "_about": "GENERATED by tools/terrain/looks.py (edit there). Lens looks per biome for Awakened Lands "
                  "(docs/modules/lands.md): keys biome:<id> win over the dimension's look where the camera's biome "
                  "matches; missing fields inherit from default. Merged with looks.json by Lens.",
        "looks": dict(sorted(looks.items())),
    }
    return json.dumps(doc, indent=2) + "\n"


def main(argv):
    text = render()
    if len(argv) > 1 and argv[1] == "write":
        with open(OUT, "w") as f:
            f.write(text)
        print(f"looks: {text.count('biome:')} biome looks written")
        return 0
    if len(argv) > 1 and argv[1] == "check":
        current = open(OUT).read() if os.path.exists(OUT) else ""
        if current != text:
            print("looks: looks_lands.json is stale; run tools/terrain/looks.py write")
            return 1
        print("looks: up to date")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
