"""Anomalies (world/anomaly): the rift tear and its core, the procession's light, the spirit cache, the fallen star and its
meteor, the shimmer grove's curtain and spirit; and the landmarks' (world/landmark) light shaft and glow."""
import math

from pixelart import *


def rift_tear(seed="rift_tear", w=32, h=64):
    """A jagged vertical crack: a white-hot seam, signal-blue body, spirit-violet edge and a soft glow that fades out.
    Drawn on a transparent 32x64 canvas; the renderer stretches it about 1.6 x 3.6 blocks."""
    sig, spi = ramp("signal"), ramp("spirit")
    rng = rng_for("rift", seed)
    # the seam wanders left and right in short straight runs, like a crack in glass
    xs, x, y = [], w / 2, 0
    while y < h:
        run = rng.randrange(3, 8)
        dx = rng.uniform(-2.2, 2.2)
        for _ in range(run):
            if y >= h:
                break
            xs.append(x)
            x = max(9.0, min(w - 9.0, x + dx / run))
            y += 1
    # two short side cracks
    branches = []
    for _ in range(2):
        by = rng.randrange(h // 4, 3 * h // 4)
        branches.append((by, rng.choice((-1, 1)), rng.randrange(4, 8)))
    c = Canvas(w, h)
    for y in range(h):
        t = y / (h - 1)
        width = 0.6 + 4.2 * math.sin(math.pi * t) ** 0.7  # thin at both tips, widest in the middle
        for x in range(w):
            d = abs(x + 0.5 - xs[y])
            for by, side, ln in branches:
                k = y - by
                if 0 <= k < ln:
                    d = min(d, abs(x + 0.5 - (xs[by] + side * k * 0.9)) + k * 0.25)
            if d < width * 0.3:
                c.set(x, y, (255, 255, 255, 255))
            elif d < width * 0.6:
                c.set(x, y, sig[5])
            elif d < width:
                r, g, b, _ = sig[4]
                c.set(x, y, (r, g, b, 235))
            elif d < width + 1.5:
                r, g, b, _ = spi[4]
                c.set(x, y, (r, g, b, 190))
            elif d < width + 6:
                r, g, b, _ = spi[3]
                a = int(150 * (1 - (d - width - 1.5) / 4.5) ** 1.6 * (0.4 + 0.6 * math.sin(math.pi * t)))
                if a > 4:
                    c.set(x, y, (r, g, b, a))
    return c


def rift_core(seed="rift_core"):
    """The core at the tear's foot: a bright eye of light in a violet shell, with dark fracture lines."""
    sig, spi = ramp("signal"), ramp("spirit")
    rng = rng_for("core", seed)
    c = Canvas(16, 16)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 2.0:
                c.set(x, y, (255, 255, 255, 255))
            elif d < 3.4:
                c.set(x, y, sig[5])
            elif d < 5.0:
                c.set(x, y, sig[4] if (x + y) % 5 else spi[5])
            elif d < 6.4:
                c.set(x, y, spi[4])
            elif d < 7.8:
                r, g, b, _ = spi[3]
                c.set(x, y, (r, g, b, int(220 * (7.8 - d) / 1.4)))
    for _ in range(4):
        a = rng.uniform(0, math.tau)
        for k in range(4, 8):
            c.set(int(round(cx + math.cos(a) * k)), int(round(cy + math.sin(a) * k)), spi[1])
    return c


def cache_side():
    """Slate in a brass frame with a small four-pointed amber star (the lights' mark) low on the face."""
    c = stone("slate", seed="spirit_cache", scale=3, contrast=0.8, lo=1, hi=4)
    frame_border(c, "brass")
    am = ramp("amber")
    cx, cy = 7, 11
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx), abs(y - cy)
            if dx + dy <= 1:
                c.set(x, y, am[5])
            elif (dx == 0 and dy <= 3) or (dy == 0 and dx <= 3):
                c.set(x, y, am[4])
            elif dx + dy <= 3:
                c.blend(x, y, am[2], 0.4)
    return c


def cache_top():
    c = stone("slate", seed="spirit_cache_top", scale=3, contrast=0.7, lo=1, hi=4)
    frame_border(c, "brass")
    am = ramp("amber")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                c.set(x, y, am[5])
            elif d < 2.8:
                c.set(x, y, am[4])
            elif d < 3.8:
                c.blend(x, y, am[3], 0.6)
    return c


def meteor(sealed=True, seed="fallen_star"):
    """The fallen star's meteor. Sealed: a scorched dark crust split by white-hot cracks. Cooled: pale signal-blue
    crystal showing through a grey crust."""
    rng = rng_for("meteor", seed)
    c = stone("obsidian" if sealed else "slate", seed=seed, scale=3, contrast=0.9, lo=0, hi=3 if sealed else 4)
    hot = ramp("ember") if sealed else ramp("signal")
    # cracks: a few random walks across the face
    for _ in range(4 if sealed else 3):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randrange(6, 12)):
            c.set(x, y, hot[5] if sealed else hot[4])
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if rng.random() < 0.35:
                    c.blend(nx, ny, hot[3], 0.6)
            x = max(0, min(15, x + rng.choice((-1, 0, 1))))
            y = max(0, min(15, y + rng.choice((-1, 0, 1))))
    if not sealed:
        # crystal facets showing through
        crystal(c, "signal", seed=seed + "_crystal", count=3)
    else:
        # a white-hot heart
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 8.5)
                if d < 1.6:
                    c.set(x, y, (255, 250, 230, 255))
                elif d < 2.6:
                    c.blend(x, y, hot[5], 0.8)
    return c


def star_glow():
    """A falling star seen from below: a white point, a blue-white halo that fades out (drawn camera-facing)."""
    sig = ramp("signal")
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 16
            if d < 0.12:
                c.set(x, y, (255, 255, 255, 255))
            elif d < 1:
                r, g, b, _ = sig[5] if d < 0.4 else sig[4]
                a = int(255 * (1 - d) ** 2.2)
                # four soft rays
                ray = max(0.0, 1 - min(abs(x - 15.5), abs(y - 15.5)) / 2.0) * (1 - d)
                a = min(255, a + int(160 * ray))
                if a > 3:
                    c.set(x, y, (r, g, b, a))
    return c


def shimmer_curtain(seed="shimmer"):
    """A grove's curtain: white streaks (tinted in game) that are bright at the foot and fade upward, with a wavering
    top edge. 32x32; v=1 is the bottom."""
    rng = rng_for("shimmer", seed)
    phases = [rng.uniform(0, math.tau) for _ in range(4)]
    c = Canvas(32, 32)
    for x in range(32):
        streak = 0.55 + 0.45 * math.sin(x * 0.7 + phases[0]) * math.sin(x * 0.23 + phases[1])
        top = 0.25 + 0.2 * math.sin(x * 0.4 + phases[2])
        for y in range(32):
            t = 1 - y / 31  # 1 at the top row, 0 at the bottom
            if t > 1 - top:
                continue
            k = (1 - t / (1 - top)) ** 1.6
            a = int(255 * k * (0.5 + 0.5 * streak))
            if a > 2:
                c.set(x, y, (255, 255, 255, a))
    return c


def spirit():
    """The whispering spirit: a soft figure of light, wider at the head, trailing off below."""
    spi = ramp("spirit")
    c = Canvas(16, 24)
    for y in range(24):
        for x in range(16):
            # head
            dh = math.hypot(x - 7.5, y - 6) / 5.5
            # body: a tapering wisp
            w = max(0.8, 7.0 * (1 - (y - 8) / 17)) if y >= 8 else 0
            db = abs(x - 7.5 - math.sin(y * 0.5) * 0.8) / w if w > 0 else 9
            d = min(dh, db)
            if d < 1:
                a = int(255 * (1 - d) ** 1.3)
                col = (255, 255, 255) if d < 0.35 else spi[5][:3] if d < 0.7 else spi[4][:3]
                c.set(x, y, (*col, a))
    return c


def shaft():
    """A landmark's light shaft: bright at the foot (v=1), a long soft rise into the sky, a Gaussian cross-section with
    faint vertical rays. White; the client tints it and layers a wide halo under a narrow core."""
    c = Canvas(16, 128)
    for y in range(128):
        up = 1 - y / 127  # 1 at the top
        fall = (1 - up) ** 0.85 * (1 - up ** 8)
        for x in range(16):
            side = math.exp(-((x - 7.5) / 4.0) ** 2)
            rays = 0.88 + 0.12 * math.sin(x * 1.9 + 0.7)
            a = int(255 * fall * side * rays)
            if a > 1:
                c.set(x, y, (255, 255, 255, a))
    return c


def glow():
    """A soft round glow (the Well's pillar tops)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 8
            if d < 1:
                a = int(255 * (1 - d) ** 2)
                if a > 1:
                    c.set(x, y, (255, 255, 255, a))
    return c


def build(out):
    out.entity("rift_tear", rift_tear())
    out.entity("rift_core", rift_core())
    out.entity("anomaly_mote", particle_dot("amber", 16))
    out.block("anomaly_spirit_cache", cache_side())
    out.block("anomaly_spirit_cache_top", cache_top())
    out.block("anomaly_fallen_star", meteor(True))
    out.block("anomaly_fallen_star_cool", meteor(False))
    out.entity("anomaly_star", star_glow())
    out.entity("anomaly_shimmer", shimmer_curtain())
    out.entity("anomaly_spirit", spirit())
    # landmarks (world/landmark): client-drawn markers
    out._save("misc/landmark_shaft", shaft())
    out._save("misc/landmark_glow", glow())
