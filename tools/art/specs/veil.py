"""Veil textures: spirit matter (glows blue), Veil Gate stone and glyphs, the Stillpoint shrine, doors and clouds, the dead."""
import math

from pixelart import *
from pixelart import _outline


def tinted(c: Canvas, alpha: int) -> Canvas:
    """Same pixels, new alpha (for translucent spirit matter)."""
    for y in range(c.h):
        for x in range(c.w):
            r, g, b, a = c.get(x, y)
            if a:
                c.set(x, y, (r, g, b, min(a, alpha)))
    return c


def veil_stone(seed="veil_stone") -> Canvas:
    c = stone("spirit", seed=seed, scale=4, contrast=0.8, lo=0, hi=3)
    rg = ramp("signal")
    rng = rng_for("veil_vein", seed)
    # thin faint veins of light
    for _ in range(2):
        x, y = rng.randrange(0, 16), rng.randrange(0, 16)
        for i in range(rng.randrange(6, 12)):
            c.blend(x, y, rg[3], 0.55)
            x = (x + rng.choice((-1, 0, 1))) % 16
            y = (y + 1) % 16
    return c


def ghost_pane(seed, alpha_lo=110, alpha_hi=185, edge_alpha=225, interior=True) -> Canvas:
    rp = ramp("signal")
    rng = rng_for("ghost", seed)
    n = value_noise(16, 16, rng, scale=4, octaves=2)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                c.set(x, y, (*rp[4][:3], edge_alpha))
            elif interior:
                v = n[y][x]
                swirl = 0.5 + 0.5 * math.sin((x + y) * 0.7 + v * 5)
                idx = 2 + quantize(v * 0.6 + swirl * 0.4, 3)
                a = int(alpha_lo + (alpha_hi - alpha_lo) * swirl)
                c.set(x, y, (*rp[idx][:3], a))
            elif x in (1, 14) or y in (1, 14):
                c.set(x, y, (*rp[3][:3], 70))
    return c


def lever_base() -> Canvas:
    rp = ramp("signal")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = 0.5 + 0.5 * math.sin(x * 0.9) * math.cos(y * 0.7)
            c.set(x, y, (*rp[1 + quantize(v, 3)][:3], 215))
    return c


def lever_handle() -> Canvas:
    rp = ramp("signal")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, (*rp[5 if (x + y) % 3 else 4][:3], 235))
    return c


def cache_side() -> Canvas:
    c = stone("spirit", seed="cache", scale=2, contrast=0.6, lo=1, hi=3)
    frame_border(c, "signal")
    glyph(c, "signal", seed="cache_side", size=5)
    return tinted(c, 230)


def cache_top() -> Canvas:
    c = stone("spirit", seed="cache_top", scale=2, contrast=0.6, lo=1, hi=3)
    frame_border(c, "signal")
    rg = ramp("signal")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.5:
                c.blend(x, y, rg[5] if d < 2 else rg[4], 0.9 - d * 0.12)
    return tinted(c, 235)


def glyph_block(lit: bool) -> Canvas:
    c = veil_stone("veil_glyph")
    rg = ramp("signal" if lit else "spirit")
    rng = rng_for("glyph_ring")
    # a ring with a rune: bright when lit, dull when not
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d <= 6.4:
                c.set(x, y, rg[4 if lit else 2])
    glyph(c, "signal" if lit else "spirit", seed="veil_glyph_rune", size=5)
    if not lit:
        for y in range(16):
            for x in range(16):
                r, g, b, a = c.get(x, y)
                c.set(x, y, (int(r * 0.75), int(g * 0.75), int(b * 0.8), a))
    return c


def shrine_side() -> Canvas:
    c = bricks("bone", seed="shrine", brick_w=8, brick_h=4, mortar=1)
    rg = ramp("amber")
    for x in range(16):
        c.blend(x, 7, rg[3], 0.35)
    return c


def shrine_top() -> Canvas:
    c = stone("cloud", seed="shrine_top", scale=2, contrast=0.5, lo=1, hi=4)
    frame_border(c, "bone")
    glyph(c, "amber", seed="shrine_rune", size=5)
    return c


def flame() -> Canvas:
    rg = ramp("amber")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 1.2, (y - 9) / 1.8)
            if d < 6:
                c.set(x, y, rg[5 - quantize(d / 6, 4)])
    return c


def door_frames(ramp_name, dark=False, alpha=215):
    frames = portal_frames(ramp_name, seed="door_" + ramp_name, frames=16)
    for f in frames:
        for y in range(16):
            for x in range(16):
                r, g, b, a = f.get(x, y)
                if dark:
                    r, g, b = int(r * 0.45), int(g * 0.4), int(b * 0.55)
                f.set(x, y, (r, g, b, alpha))
    return frames


def cloud() -> Canvas:
    c = stone("cloud", seed="stillpoint_cloud", scale=2, contrast=0.45, lo=2, hi=5)
    peach = hex_rgba("#ffe2cc")
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 3) % 11 == 0:
                c.blend(x, y, peach, 0.35)
    return c


def lever_item() -> Canvas:
    rp = ramp("signal")
    c = Canvas()
    for x in range(4, 12):
        for y in range(11, 14):
            c.set(x, y, rp[2])
    for i in range(8):
        c.set(6 + i // 2, 10 - i, rp[4])
        c.set(7 + i // 2, 10 - i, rp[5])
    c.set(10, 3, rp[5]); c.set(11, 2, rp[5])
    return outline_item(c)


def outline_item(c):
    return _outline(c)


def mark_item() -> Canvas:
    rg = ramp("amber")
    c = Canvas()
    for i in range(-5, 6):
        c.set(7 + i, 7, rg[3 if abs(i) > 2 else 5])
        c.set(7, 7 + i, rg[3 if abs(i) > 2 else 5])
    for i in (-2, 2):
        c.set(7 + i, 7 + i, rg[4]); c.set(7 + i, 7 - i, rg[4])
    return c


def spirit_skin() -> Canvas:
    """64x64 player-layout skin: a hooded, robed figure, pale so the faction tint shows, fading out below the knees."""
    c = Canvas(64, 64)
    robe = ramp("mirror")
    shade = ramp("spirit")
    rng = rng_for("spirit_skin")
    n = value_noise(64, 64, rng, scale=8, octaves=2)

    def robe_px(x, y, fold=0.0, alpha=255):
        v = 0.55 + (n[y][x] - 0.5) * 0.5 + fold
        idx = 2 + quantize(max(0.0, min(0.999, v)), 4)
        r, g, b, _ = robe[idx]
        return (r, g, b, alpha)

    def rect(x0, y0, x1, y1, fn):
        for y in range(y0, y1):
            for x in range(x0, x1):
                p = fn(x, y)
                if p:
                    c.set(x, y, p)

    # head: a shadowed face with two soft eyes
    rect(0, 0, 32, 16, lambda x, y: (*shade[1][:3], 255) if 8 <= x < 16 and 8 <= y < 16 else robe_px(x, y, -0.15))
    for ex in (10, 13):
        c.set(ex, 12, hex_rgba("#d6ecff"))
        c.blend(ex, 13, hex_rgba("#7fc3ff"), 0.6)
    # hood (hat layer): everything but the face opening
    def hood(x, y):
        if 40 <= x < 48 and 8 <= y < 16:  # front of the hood: only the rim
            if x in (40, 47) or y == 8:
                return robe_px(x, y, 0.1)
            return None
        return robe_px(x, y, 0.05)
    rect(32, 0, 64, 16, hood)
    # body, arms, legs: robe with vertical folds
    def body(x, y):
        fold = 0.12 * math.sin(x * 1.3)
        return robe_px(x, y, fold)
    rect(16, 16, 40, 32, body)
    rect(40, 16, 56, 32, body)
    rect(32, 48, 48, 64, body)
    # a sash at the waist
    for x in range(20, 28):
        c.set(x, 30, hex_rgba("#c3cbff"))
    # legs fade out like smoke
    def leg(x, y, top):
        t = (y - top) / 16
        alpha = int(255 * max(0.18, 1 - t * 0.9))
        return robe_px(x, y, 0.1 * math.sin(x * 1.1), alpha)
    rect(0, 16, 16, 32, lambda x, y: leg(x, y, 16))
    rect(16, 48, 32, 64, lambda x, y: leg(x, y, 48))
    # jacket / sleeves overlay: a light outer robe edge
    rect(16, 32, 40, 48, lambda x, y: robe_px(x, y, 0.2, 150) if y >= 46 else None)
    rect(40, 32, 56, 48, lambda x, y: robe_px(x, y, 0.2, 160) if y >= 44 else None)
    rect(48, 48, 64, 64, lambda x, y: robe_px(x, y, 0.2, 160) if y >= 60 else None)
    return c


def shock_icon() -> Canvas:
    rp = ramp("spirit")
    c = Canvas(18, 18)
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - 8.5, y - 8.5)
            if d < 7:
                c.set(x, y, rp[1 + quantize(1 - d / 7, 4)])
    crack = [(8, 2), (9, 4), (7, 6), (10, 8), (8, 10), (9, 12), (7, 14)]
    for x, y in crack:
        c.set(x, y, hex_rgba("#ffffff"))
        c.set(x + 1, y, hex_rgba("#ff8a7a"))
    return c


def build(out):
    out.block("veil_stone", veil_stone())
    out.block("veil_glyph", glyph_block(False))
    out.block("veil_glyph_lit", glyph_block(True))
    out.block("ghost_door", ghost_pane("door"))
    out.block("ghost_door_open", ghost_pane("door_open", interior=False, edge_alpha=120))
    out.block("ghost_lever_base", lever_base())
    out.block("ghost_lever_handle", lever_handle())
    out.block("veil_cache", cache_side())
    out.block("veil_cache_top", cache_top())
    out.block("veil_mark", particle_dot("amber", 16))
    out.block("stillpoint_shrine_side", shrine_side())
    out.block("stillpoint_shrine_top", shrine_top())
    out.block("stillpoint_flame", flame())
    out.block("stillpoint_door_out", door_frames("signal"), frametime=3)
    out.block("stillpoint_door_in", door_frames("dream", dark=True, alpha=200), frametime=4)
    out.block("stillpoint_cloud", cloud())
    out.item("ghost_lever", lever_item())
    out.item("veil_mark", mark_item())
    out.entity("veil_spirit", spirit_skin())
    out._save("mob_effect/spirit_shock", shock_icon())
