"""Textures of the descent module: the Well, Waystones, pits and the palettes of the ten strata.

Each stratum gets its own ramp (added here) so the strata read differently at a glance; the shared identity (slate,
amber lantern, signal blue) carries the Precursor pieces: the Well, the Waystones, the conduits and the pits.
"""
import math

from pixelart import *

# Stratum ramps (dark -> light)
RAMPS["rim"] = ["#1a140e", "#2a2016", "#3c2f20", "#52412b", "#6b5638", "#86704a"]
RAMPS["rimglow"] = ["#14301a", "#1f5a2a", "#2f8a3c", "#4fbf55", "#8cf07c", "#d4ffb8"]
RAMPS["garden"] = ["#4a4e45", "#646a5c", "#7e8574", "#9ba18e", "#b9bea9", "#d7dbc6"]
RAMPS["bloom"] = ["#4a1838", "#7a2a5c", "#b04888", "#e070b4", "#ffa6d8", "#ffe0f2"]
RAMPS["choir"] = ["#0b1c26", "#12303f", "#1b4558", "#285e74", "#3b7d92", "#5aa0b2"]
RAMPS["marrow"] = ["#3d352a", "#564b3b", "#72644f", "#8f7f66", "#ad9d80", "#cbbd9e"]
RAMPS["ash"] = ["#3a3a3c", "#4c4c4f", "#606064", "#76767a", "#8e8e92", "#a8a8ac"]
RAMPS["hush"] = ["#15171c", "#1d2027", "#272b33", "#323741", "#3f4551", "#4e5563"]
RAMPS["pale"] = ["#3a4048", "#56606c", "#78838f", "#a0abb6", "#c8d1d9", "#eef3f7"]
RAMPS["foundry"] = ["#16130f", "#221d16", "#302820", "#40352a", "#544637", "#6c5a46"]
RAMPS["brine"] = ["#0c2026", "#123139", "#1a444e", "#245a66", "#327380", "#4a909c"]
RAMPS["court"] = ["#08060c", "#100c17", "#191324", "#231b33", "#2f2444", "#3e3058"]
RAMPS["pit"] = ["#020205", "#07061a", "#120f33", "#1d1a52", "#33307f", "#7a86e6"]
RAMPS["seed"] = ["#081a33", "#0f2f5a", "#1a4f8c", "#3a86cc", "#8cd0ff", "#e8f8ff"]


def glow_block(ramp_name, seed, density=0.35):
    """A lamp-like block: bright core, darker rim, a few hot specks."""
    rp = ramp(ramp_name)
    c = Canvas()
    rng = rng_for("glow", seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10.6
            v = 1.0 - d + (rng.random() - 0.5) * 0.25
            c.set(x, y, rp[max(1, min(len(rp) - 1, int(v * (len(rp) - 1)) + 1))])
    overlay_noise(c, ramp_name, seed=seed + "s", density=density * 0.2, idx=len(rp) - 1)
    return c


def moss_glow(base, glow, seed, density):
    c = stone(base, seed=seed, scale=3)
    rg = ramp(glow)
    rng = rng_for("mossglow", seed)
    for y in range(16):
        for x in range(16):
            if rng.random() < density:
                c.set(x, y, rg[3 + rng.randrange(0, 3)])
    return c


def tiles(ramp_name, seed, size=8, lo=1):
    rp = ramp(ramp_name)
    c = stone(ramp_name, seed=seed, scale=2, contrast=0.5, lo=lo + 1, hi=lo + 2)
    for y in range(16):
        for x in range(16):
            if x % size == 0 or y % size == 0:
                c.set(x, y, rp[lo - 1 if lo > 0 else 0])
            elif x % size == 1 or y % size == 1:
                c.set(x, y, rp[lo + 3] if lo + 3 < len(rp) else rp[-1])
    return c


def grate(ramp_name, seed):
    rp = ramp(ramp_name)
    c = plates(ramp_name, seed=seed, size=16, rivets=False)
    for y in range(2, 14):
        for x in range(2, 14):
            if (x % 3 == 0) or (y % 4 == 0):
                c.set(x, y, rp[4])
            else:
                c.set(x, y, rp[0])
    return frame_border(c, ramp_name, 1)


def ribbed(ramp_name, seed):
    rp = ramp(ramp_name)
    c = stone(ramp_name, seed=seed, scale=2, lo=2, hi=4)
    for y in range(16):
        for x in range(16):
            if y % 5 == 0:
                c.set(x, y, rp[1])
            elif y % 5 == 1:
                c.set(x, y, rp[5])
    return c


def ghost(seed="grave"):
    """The Grave Echo: a hooded figure of soul light, 32x32 with transparency (billboarded)."""
    rp = ramp("spirit")
    c = Canvas(32, 32)
    rng = rng_for("ghost", seed)
    for y in range(32):
        for x in range(32):
            # head + shoulders + tapering robe
            hx, hy = (x - 15.5) / 5.0, (y - 7.5) / 5.0
            head = hx * hx + hy * hy < 1
            body_w = 4 + max(0, (y - 11)) * 0.45
            body = 11 <= y <= 30 and abs(x - 15.5) < body_w * (1 - max(0, (y - 26)) * 0.12)
            if not (head or body):
                continue
            edge = 1.0 if head else max(0.0, 1 - abs(x - 15.5) / body_w)
            v = 0.35 + 0.6 * edge - (y / 32) * 0.25 + (rng.random() - 0.5) * 0.12
            idx = max(1, min(len(rp) - 1, int(v * len(rp))))
            alpha = int(255 * (0.55 + 0.4 * edge) * (1 - max(0, y - 24) / 12))
            r, g, b, _ = rp[idx]
            c.set(x, y, (r, g, b, max(40, min(255, alpha))))
    # eyes: amber lantern light
    ra = ramp("amber")
    for x in (13, 18):
        c.set(x, 8, ra[5])
        c.set(x, 9, ra[4])
    return c


def build(out):
    # ---- shared Precursor pieces --------------------------------------------------------------------------------------
    out.block("abyss_stone", stone("abyss", seed="abyss_stone", scale=3, contrast=1.2))
    out.block("well_stone", plates("precursor", seed="well_stone"))
    out.block("well_glyph", glyph(plates("precursor", seed="well_glyph"), "signal", seed="well_glyph"))
    out.block("well_light", glow_block("signal", "well_light"))
    out.block("well_lift_top", circuits(frame_border(plates("precursor", seed="lift"), "brass", 1), "amber", seed="lift", lines=4))
    out.block("well_lift_side", frame_border(circuits(plates("precursor", seed="lift_side"), "amber", seed="lift_side", lines=2), "brass", 1))
    out.block("ascent_stone_top", glyph(stone("slate", seed="ascent_top", bevel=True), "signal", seed="ascent"))
    out.block("ascent_stone_side", circuits(stone("slate", seed="ascent_side", bevel=True), "signal", seed="ascent_side", lines=2))
    out.block("waystone_side", glyph(stone("slate", seed="waystone", bevel=True), "obsidian", seed="waystone", size=7))
    out.block("waystone_top", stone("slate", seed="waystone_top", bevel=True))
    out.block("waystone_side_lit", glyph(stone("slate", seed="waystone", bevel=True), "amber", seed="waystone", size=7))
    out.block("waystone_top_lit", glyph(stone("slate", seed="waystone_top", bevel=True), "amber", seed="waystone_top", size=5))
    out.block("descent_pit", portal_frames("pit", seed="pit", frames=16), frametime=3)
    out.block("conduit_side", circuits(plates("precursor", seed="conduit"), "slate", seed="conduit", lines=3, glow=False))
    out.block("conduit_top", frame_border(plates("precursor", seed="conduit_top"), "brass", 1))
    out.block("conduit_side_lit", circuits(plates("precursor", seed="conduit"), "signal", seed="conduit", lines=3))
    out.block("conduit_top_lit", crystal(frame_border(plates("precursor", seed="conduit_top"), "brass", 1), "signal", seed="ct", count=1))
    out.block("foundry_seal", frame_border(glyph(plates("foundry", seed="seal", size=16), "crimson", seed="seal", size=7), "brass", 2))

    # ---- 1 The Rim ----------------------------------------------------------------------------------------------------
    out.block("rim_stone", stone("rim", seed="rim_stone", scale=3, contrast=1.1))
    out.block("rim_soil", overlay_noise(stone("rim", seed="rim_soil", scale=2, lo=1, hi=4), "wood", seed="roots", density=0.12, idx=4))
    out.block("rim_moss", moss_glow("moss", "rimglow", "rim_moss", 0.22))

    # ---- 2 The Hanging Gardens ----------------------------------------------------------------------------------------
    out.block("garden_stone", overlay_noise(stone("garden", seed="garden_stone", scale=4), "moss", seed="gs", density=0.08, idx=3))
    out.block("garden_bloom", glow_block("bloom", "garden_bloom", 0.5))

    # ---- 3 The Drowned Choir ------------------------------------------------------------------------------------------
    out.block("choir_stone", stone("choir", seed="choir_stone", scale=3))
    out.block("choir_bricks", bricks("choir", seed="choir_bricks", brick_w=8, brick_h=4))
    out.block("choir_crystal", crystal(stone("choir", seed="choir_crystal", lo=0, hi=2), "frost", seed="choir_crystal", count=4))

    # ---- 4 The Bone Orchard -------------------------------------------------------------------------------------------
    out.block("orchard_ash", stone("ash", seed="orchard_ash", scale=2, contrast=0.7))
    out.block("marrow_stone", ribbed("marrow", "marrow_stone"))
    out.block("marrow_lantern", glow_block("amber", "marrow_lantern"))

    # ---- 5 The Silent Halls -------------------------------------------------------------------------------------------
    out.block("hush_stone", stone("hush", seed="hush_stone", scale=4, contrast=0.6))
    out.block("hush_tiles", tiles("hush", "hush_tiles", size=8, lo=1))
    out.block("hush_lamp", glow_block("pale", "hush_lamp", 0.2))

    # ---- 6 The Precursor Foundry --------------------------------------------------------------------------------------
    out.block("foundry_plate", circuits(plates("foundry", seed="foundry_plate"), "amber", seed="foundry_plate", lines=2, glow=False))
    out.block("foundry_grate", grate("foundry", "foundry_grate"))
    out.block("foundry_lamp", frame_border(glow_block("amber", "foundry_lamp"), "brass", 1))

    # ---- 7 The Inverted Sea -------------------------------------------------------------------------------------------
    out.block("brine_stone", overlay_noise(stone("brine", seed="brine_stone", scale=3), "water", seed="brine", density=0.1, idx=5))
    out.block("tide_coral", crystal(glow_block("water", "tide_coral"), "frost", seed="tide_coral", count=3))

    # ---- 8 The Dreaming Stratum ---------------------------------------------------------------------------------------
    out.block("stratum_cloud", overlay_noise(stone("dream", seed="dream_cloud", scale=3, contrast=1.0, lo=3), "cloud", seed="dc", density=0.12, idx=4))
    out.block("dream_tile", tiles("dream", "dream_tile", size=8, lo=2))
    out.block("dream_glow", glow_block("dream", "dream_glow"))

    # ---- 9 The Hollow Court -------------------------------------------------------------------------------------------
    out.block("court_stone", stone("court", seed="court_stone", scale=4, contrast=0.8))
    out.block("court_tile", frame_border(tiles("court", "court_tile", size=16, lo=1), "brass", 1))
    out.block("court_brazier_top", glow_block("ember", "court_brazier"))
    out.block("court_brazier_side", frame_border(bricks("court", seed="brazier", brick_w=16, brick_h=4), "brass", 1))

    # ---- 10 The Seed --------------------------------------------------------------------------------------------------
    out.block("seed_shell", crystal(stone("seed", seed="seed_shell", lo=0, hi=2), "seed", seed="seed_shell", count=2))
    out.block("seed_vein", circuits(stone("abyss", seed="seed_vein"), "seed", seed="seed_vein", lines=4))
    out.block("seed_core", portal_frames("seed", seed="seed_core", frames=16), frametime=4)

    # ---- entities -----------------------------------------------------------------------------------------------------
    out.entity("grave_echo", ghost())
