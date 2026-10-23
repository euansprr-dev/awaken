"""Gates module textures: surface gate frame and rank cores, in-gate portals, shrine, seal, cache, rune bars, runes,
entrance sigil, updraft and the lost lantern. Run: python3 tools/art/build_all.py gates"""
import math

import pixelart as px
from pixelart import *

# Rank colours from SPEC section 7 as short ramps (dark -> light) for the animated cores.
px.RAMPS.update({
    "rank_e": ["#5d6375", "#8c93a6", "#b9bfcf", "#dfe3ee", "#f4f6fb", "#ffffff"],
    "rank_d": ["#123a26", "#1f6040", "#2f8d5c", "#45b277", "#5fd08f", "#a8f0c4"],
    "rank_c": ["#0f2a4d", "#1d4d86", "#3576c0", "#5096e6", "#6fb3ff", "#c4e2ff"],
    "rank_b": ["#2a1650", "#45287f", "#6a44b3", "#8f68dc", "#b48cff", "#e2d2ff"],
    "rank_a": ["#4a2a06", "#7a460b", "#b26a12", "#e08f1f", "#f2a541", "#ffd28a"],
    "rank_s": ["#3a0a10", "#6e1420", "#a8242f", "#d83a48", "#ff5a6a", "#ffb0b6"],
    "rank_sov": ["#050407", "#141018", "#2a2314", "#5c4a1a", "#d4af37", "#fff0b0"],
    "broken": ["#160406", "#2e080c", "#4d0f14", "#2a2a2a", "#6a6a6a", "#a4a4a4"],
})


def translucent(frames, alpha_lo=150, alpha_hi=235):
    """Brighter pixels are more opaque: the core reads as light, not glass."""
    for c in frames:
        for y in range(c.h):
            for x in range(c.w):
                r, g, b, a = c.get(x, y)
                lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
                c.set(x, y, (r, g, b, int(alpha_lo + (alpha_hi - alpha_lo) * lum)))
    return frames


def frame_block(seed, cracked=False):
    c = stone("obsidian", seed=seed, bevel=True, contrast=0.8)
    glyph(c, "signal", seed=seed, size=5)
    if cracked:
        rng = rng_for("crack", seed)
        rc = ramp("crimson")
        x, y = rng.randrange(3, 13), 0
        while y < 16:
            c.set(x, y, rc[1])
            if rng.random() < 0.5:
                c.set(x + 1, y, rc[0])
            x = max(1, min(14, x + rng.choice((-1, 0, 1))))
            y += 1
    return c


def shrine_side():
    c = bricks("precursor", seed="shrine", brick_w=8, brick_h=4)
    sg = ramp("signal")
    for y in range(3, 13):
        c.set(7, y, sg[3])
        c.set(8, y, sg[4])
    for x in range(5, 11):
        c.set(x, 2, sg[2])
        c.set(x, 13, sg[2])
    return c


def shrine_top():
    c = stone("precursor", seed="shrine_top", scale=2, contrast=0.5, lo=2, hi=3)
    sg = ramp("signal")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if abs(d - 5.2) < 0.8:
                c.set(x, y, sg[4])
            elif d < 2.2:
                c.set(x, y, sg[5] if d < 1.2 else sg[3])
    return frame_border(c, "slate")


def seal_side():
    c = stone("abyss", seed="seal", bevel=True)
    sp = ramp("spirit")
    for y in range(2, 14, 3):
        for x in range(3, 13):
            if (x + y) % 4 == 0:
                c.set(x, y, sp[3])
    return c


def seal_top():
    c = stone("abyss", seed="seal_top", contrast=0.6)
    sp = ramp("spirit")
    for i in range(16):
        c.set(i, i, sp[4])
        c.set(15 - i, i, sp[4])
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 7.5, y - 7.5) < 2.5:
                c.set(x, y, sp[5])
    return frame_border(c, "slate")


def cache_side():
    c = plates("wood", seed="cache", size=8, rivets=False)
    br = ramp("brass")
    for x in range(16):
        c.set(x, 0, br[3])
        c.set(x, 15, br[1])
    for y in range(16):
        c.set(0, y, br[3])
        c.set(15, y, br[1])
    for y in range(6, 10):
        c.set(7, y, br[4])
        c.set(8, y, br[4])
    c.set(7, 10, br[5])
    return c


def cache_top(open_=False):
    c = plates("wood", seed="cache_top", size=8, rivets=False)
    br = ramp("brass")
    am = ramp("amber")
    if open_:
        for y in range(3, 13):
            for x in range(3, 13):
                c.set(x, y, ramp("night")[1])
        for (x, y) in [(5, 6), (9, 8), (7, 10), (10, 5)]:
            c.set(x, y, am[4])
    else:
        glyph(c, "amber", seed="cache_lock", size=5)
    return frame_border(c, "brass")


def bars():
    c = Canvas()
    sg = ramp("signal")
    for x in (1, 5, 10, 14):
        for y in range(16):
            c.set(x, y, sg[3] if y % 4 else sg[5])
            c.blend(x + 1, y, sg[2], 0.55)
    for y in (2, 13):
        for x in range(16):
            c.set(x, y, sg[2])
    return c


def rune(on):
    c = stone("slate", seed="rune", bevel=True, contrast=0.7)
    if on:
        glyph(c, "signal", seed="rune_mark", size=7)
        rg = ramp("signal")
        for i in range(16):
            c.set(i, 0, rg[4])
            c.set(i, 15, rg[4])
    else:
        glyph(c, "slate", seed="rune_mark", size=7)
    return c


def entrance_top():
    c = stone("slate", seed="entrance", contrast=0.6)
    sg = ramp("signal")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            if 4.2 < d < 5.4 and int((a + math.pi) / (math.pi / 4)) % 2 == 0:
                c.set(x, y, sg[4])
            if d < 1.6:
                c.set(x, y, sg[5])
    return c


def updraft(top):
    c = stone("cloud", seed="updraft" + str(top), scale=2, contrast=0.6, lo=2)
    sg = ramp("signal")
    if top:
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if abs(d - 4) < 0.7 or abs(d - 6.5) < 0.6:
                    c.set(x, y, sg[4])
    else:
        for x in range(2, 14, 3):
            for y in range(4, 12):
                c.set(x, y, sg[3])
    return c


def lantern():
    c = Canvas()
    sp = ramp("spirit")
    ir = ramp("slate")
    for x in range(6):
        for y in range(2, 9):
            edge = x in (0, 5) or y in (2, 8)
            c.set(x, y, ir[3] if edge else sp[4 + (y % 2)])
    for x in range(6):
        for y in range(9, 15):
            c.set(x, y, ir[2] if (x + y) % 3 else ir[4])
    for x in range(1, 5):
        c.set(x, 0, ir[4])
        c.set(x, 1, ir[3])
    return c


def build(out):
    out.block("gate_frame", frame_block("gate_frame"))
    out.block("gate_frame_cracked", frame_block("gate_frame", cracked=True))
    for r, rp in [("e", "rank_e"), ("d", "rank_d"), ("c", "rank_c"), ("b", "rank_b"), ("a", "rank_a"), ("s", "rank_s"), ("sovereign", "rank_sov")]:
        out.block("gate_core_" + r, translucent(portal_frames(rp, seed="core_" + r, frames=16)), frametime=2)
    out.block("gate_core_broken", translucent(portal_frames("broken", seed="core_broken", frames=16), 170, 240), frametime=3)
    out.block("gate_exit", translucent(portal_frames("signal", seed="exit", frames=16)), frametime=2)
    out.block("gate_rift", translucent(portal_frames("abyss", seed="rift", frames=16), 200, 250), frametime=2)
    out.block("gate_inner", translucent(portal_frames("dream", seed="inner", frames=16)), frametime=2)
    out.block("gate_return", translucent(portal_frames("amber", seed="return", frames=16)), frametime=2)
    out.block("rest_shrine_side", shrine_side())
    out.block("rest_shrine_top", shrine_top())
    out.block("gate_seal_side", seal_side())
    out.block("gate_seal_top", seal_top())
    out.block("gate_cache_side", cache_side())
    out.block("gate_cache_top", cache_top())
    out.block("gate_cache_top_open", cache_top(True))
    out.block("gate_bars", bars())
    out.block("gate_rune_off", rune(False))
    out.block("gate_rune_on", rune(True))
    out.block("gate_entrance_top", entrance_top())
    out.block("gate_updraft_top", updraft(True))
    out.block("gate_updraft_side", updraft(False))
    out.block("lost_lantern", lantern())
