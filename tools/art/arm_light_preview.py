#!/usr/bin/env python3
"""The powers' light on an armoured arm, off line (SPEC 32.N, "The light on what you wear").

A picture of what powers.client.fx.ArmourLight draws, without the game: each set's arm piece cut into the same cells
(one a texel of every face), each cell given the same part to play (cloth, plate, trim, or what glows of itself) from the
set's own texture, and lit by the same rules. The rules here and in ArmourLight.java are kept line for line; change both.

  python3 tools/art/arm_light_preview.py <out dir> [set ...]      sheets: a row a style, the arm from four sides
"""
import math
import os
import struct
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "awaken")
SOFT, PLATE, TRIM, EMIT = 0, 1, 2, 3
FRONT, BACK, OUTER, INNER, CAP = 0, 1, 2, 3, 4
ROWS = 12.5

# FxStyle: core, mid, edge, accent
STYLES = {
    "KINETIC": (0xFFF1D6, 0xF2A541, 0xB26A12, 0x9A8A72), "VELOCITY": (0xFFFFFF, 0xFF4A3D, 0xA8262F, 0xFF8A7A),
    "FLIGHT": (0xE6F6FF, 0xA7CDE3, 0x6B98B8, 0xDCDFE8), "GRAVITY": (0xE0D0FF, 0x9A6BFF, 0x4A2A9A, 0x0D0A17),
    "ENERGY": (0xFFFFFF, 0x7FC3FF, 0x3C86CC, 0x1D5591), "HEAT": (0xFFF4D0, 0xFF9A3C, 0xC44A12, 0x55180A),
    "CHARGE": (0xF0FAFF, 0x9AD8FF, 0x3C86CC, 0xFFFFFF), "SHADOW": (0xC3B0FF, 0x8F6BFF, 0x231A3B, 0x0D0A17),
    "MIND": (0xFFE6F6, 0xD088C2, 0x73367C, 0xFFD27A), "ARCHITECT": (0xFFE6B0, 0xFFD28A, 0x937646, 0x7FC3FF),
    "TECH": (0xE6FFFA, 0x47C6B0, 0x17564E, 0x7FC3FF), "SPIRIT": (0xF0FFF5, 0x86E0A0, 0x3F8F6E, 0x9FB0FF),
    "SOUL": (0xE8ECFF, 0x9FB0FF, 0x4C50B3, 0x86E0A0), "FROST": (0xFFFFFF, 0xA7CDE3, 0x406A8C, 0xE6F6FF),
    "PRISM": (0xFFFFFF, 0xE9D8FF, 0x8F6BFF, 0x7FC3FF),
}

# A set's own way with the light (ArmourLight.SETS and FAMILIES; the two tables are kept the same, row for row).
# soft, plate, trim, emit: how much its cloth, plate, lines and glowing parts each take.
# lead: how many rows its lines run ahead of the rest (metal conducts).
# soak: how much its cloth holds a glow where the light has been (hide and cloth).
# shimmer: how much what glows of itself flickers (crystal, a signal).
# grain: +1 its bright texels take more light, -1 its dark ones do (the light sits in bark's cracks, bone's hollows).
# tips: how much the parts that stand off the arm (fins, feathers, spurs) light late and bright.
# ripple: how much a wave of shade runs down its plate and cloth (rows of scales, of feathers).
# glitter: how often an unlit texel where the light has been catches it for a moment (mail's links, rivets, star metal).
def style(soft, plate, trim, emit, lead, soak=0.0, shimmer=0.0, grain=1.0, tips=0.5, ripple=0.0, glitter=0.0):
    return dict(soft=soft, plate=plate, trim=trim, emit=emit, lead=lead, soak=soak, shimmer=shimmer, grain=grain, tips=tips,
                ripple=ripple, glitter=glitter)


SETS = {
    "default": style(0.8, 0.85, 1.0, 1.15, 1.0),
    # E
    "wildhide": style(0.9, 0.85, 1.0, 1.15, 0.5, soak=0.22, tips=0.0),                     # fur and hide: it soaks; the lacing leads a little
    "bellguard": style(0.8, 0.9, 0.9, 1.15, 2.0, soak=0.08),                               # bell bronze, rim after rim: they carry it far
    "rootwarden": style(0.9, 0.85, 1.0, 1.3, 0.5, soak=0.1, grain=-1.0, tips=0.0),         # bark: the light sits in its cracks, the resin fills
    "amberwing": style(0.9, 0.85, 1.0, 1.15, 1.0, soak=0.14, tips=0.3),                    # a jerkin: it soaks, the amber cuff leads
    # D
    "beastbound": style(0.85, 0.85, 1.0, 1.2, 1.0, soak=0.06, grain=-1.0, tips=0.7),       # bone plates: the light in their hollows
    "cleftrunner": style(0.85, 0.85, 1.0, 1.15, 1.5, soak=0.16, tips=0.3),                 # cloth under lamellar strips
    "lanternlight": style(0.85, 0.9, 1.0, 1.3, 1.5, soak=0.08, glitter=0.6),               # a robe over mail: the links glitter, the lantern glass fills
    "bladeward": style(0.8, 0.9, 1.0, 1.15, 2.0, soak=0.08, glitter=0.35),                 # a brigandine: its rivets catch the light
    "hornlord": style(0.7, 0.85, 1.05, 1.2, 2.0, grain=-0.6),                              # horn and plate: the light in the horn's grooves
    # C
    "gatewrought": style(0.85, 0.9, 1.0, 1.15, 1.5, soak=0.12),                            # a leather sleeve under a plated glove
    "vaultbreaker": style(0.7, 0.85, 1.05, 1.2, 2.5),                                      # steel: the cuff rings run well ahead
    "frostwalker": style(0.9, 0.85, 1.0, 1.2, 0.5, soak=0.2, shimmer=0.6, tips=0.0),       # hide: it soaks the light up; its stone flickers
    "sunward": style(0.85, 0.9, 1.1, 1.2, 1.5, soak=0.14, shimmer=0.5),                    # cloth under gold bands and a stone
    "shardcrown": style(0.7, 0.8, 1.0, 1.3, 1.0, shimmer=1.0, tips=0.6),                   # crystal fills and shimmers
    # B
    "veilwoven": style(0.85, 0.85, 0.65, 1.15, 2.0, soak=0.12, tips=0.3),                   # a veil of threads: they carry it, and are kept from flooding
    "deepcarapace": style(0.85, 0.85, 1.0, 1.25, 1.0, grain=-1.0, tips=0.8),               # shell: the light between its plates, out along the spurs
    "cloudrunner": style(0.9, 0.85, 0.9, 1.15, 1.0, soak=0.1, tips=1.0, ripple=0.6),       # feathers: a wave down the rows, out to the tips
    "tidebreaker": style(0.85, 0.9, 1.0, 1.2, 1.5, soak=0.06, tips=0.8, ripple=0.4, glitter=0.5),  # wet mail and fins
    "hollowheart": style(0.85, 0.85, 1.0, 1.25, 1.0, soak=0.08, grain=-1.0, tips=0.7),     # bone: the light in the hollows, the heart-stone fills
    # A
    "lordscale": style(0.7, 0.85, 1.05, 1.3, 2.0, ripple=0.6),                             # scales: a wave down their rows, the ember seams fill
    "leviathan": style(0.8, 0.85, 1.05, 1.25, 1.5, soak=0.06, tips=0.9, ripple=0.4),       # deep plate and fins: out along the fins
    "galewing": style(0.9, 0.85, 1.0, 1.15, 1.0, soak=0.1, tips=1.0, ripple=0.6),          # feathers, as Cloudrunner's, over plate
    "halo_choir": style(0.8, 0.85, 0.95, 1.3, 2.0, soak=0.08, shimmer=0.5, glitter=0.3),   # ivory and gold: the gold carries it, the halo's stone flickers
    "dreamsteel": style(0.8, 0.85, 1.0, 1.3, 1.5, shimmer=0.8, tips=0.6),                  # dream steel: plates off the sleeve, stones that flicker
    # S
    "sovereign": style(0.7, 0.85, 0.85, 1.25, 2.5, ripple=0.3),                             # the Weave: its threads carry the light well ahead
    "starfall": style(0.7, 0.85, 1.05, 1.2, 2.0, glitter=0.8),                             # star metal: specks of it catch the light
    "riftwalker": style(0.85, 0.9, 0.85, 1.2, 1.5, tips=0.8, glitter=0.4),                  # plates that float off the sleeve
    "nightcourt": style(0.9, 0.85, 0.9, 1.15, 1.0, soak=0.18, tips=0.0),                   # court leather and a half-cloak that hangs: it soaks, nothing flares
    "signals_echo": style(0.85, 0.85, 1.0, 1.0, 1.0, soak=0.12, shimmer=0.7, tips=0.3),    # the Signal runs through it already: its light flickers, never doubles
}


def i32(x):
    x &= 0xFFFFFFFF
    return x - (1 << 32) if x & 0x80000000 else x


def hash3(a, b, c):
    h = i32(i32(a * 0x9E3779B1) + i32(b * 0x85EBCA77) + i32(c * 0xC2B2AE3D))
    h ^= (h & 0xFFFFFFFF) >> 15
    h = i32(h * 0x2C1B3C6D)
    h ^= (h & 0xFFFFFFFF) >> 12
    return (h & 0xFFFF) / 65536.0


def fmod(a, b):
    r = math.fmod(a, b)
    return r + b if r < 0 else r


def read(path):
    b = open(path, "rb").read()
    _, _, _, n = struct.unpack_from("<IHHI", b, 0)
    off, out = 16, []
    for _ in range(n):
        x, y, z = struct.unpack_from("<fff", b, off)
        nx, ny, nz, _al = struct.unpack_from("<bbbB", b, off + 12)
        u, v = struct.unpack_from("<HH", b, off + 16)
        off += 20
        out.append(((x, y, z), (nx / 127, ny / 127, nz / 127), (u / 65535, v / 65535)))
    return out


def accent_of(path):
    b = open(path, "rb").read()
    return b[12], b[13], b[14]


def near(colour, accent):
    """ArmourLight.near: a texel in the set's own accent colour (by hue: both taken at full brightness), and not a dark one."""
    m, ma = max(colour), max(accent)
    if m <= 110 or ma == 0:
        return False
    return sum(abs(colour[k] / m - accent[k] / ma) for k in range(3)) < 0.25


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def cells(corners, tex, accent=(0, 0, 0)):
    """ArmourLight.build: every pair of triangles is one flat face; a cell a texel of it."""
    w, h = tex.size
    px = tex.load()
    out = []
    # the arm's own middle: a left arm's bone has the arm a texel to its right, a right arm's a texel to its left
    axis = 1.0 if sum(c[0][0] for c in corners) > 0 else -1.0
    for q in range(len(corners) // 6):
        six = corners[q * 6:q * 6 + 6]
        a, b, c = six[0], six[1], six[2]
        second = [v[0] for v in six[3:6]]
        d = next((v for v in six[3:6] if v[0] not in (a[0], b[0], c[0])), None)
        if d is None:
            continue
        ring = [a, b, c]
        lone = next((k for k in range(3) if ring[k][0] not in second), 0)
        c0, c1, c3 = ring[lone], ring[(lone + 1) % 3], ring[(lone + 2) % 3]
        e1 = [c1[0][k] - c0[0][k] for k in range(3)]
        e2 = [c3[0][k] - c0[0][k] for k in range(3)]
        n1 = max(1, round(math.sqrt(sum(x * x for x in e1)) * 16))
        n2 = max(1, round(math.sqrt(sum(x * x for x in e2)) * 16))
        n = c0[1]
        # which edge runs along the arm (the bone's y), and so which counts across it
        first_along = abs(e1[1]) >= abs(e2[1])
        ax = max(range(3), key=lambda k: abs(n[k]))
        face = (FRONT if n[2] < 0 else BACK) if ax == 2 else (OUTER if n[0] < 0 else INNER) if ax == 0 else (CAP if n[1] > 0 else FRONT)
        grid = {}
        for i in range(n1):
            for j in range(n2):
                s0, s1, t0, t1 = i / n1, (i + 1) / n1, j / n2, (j + 1) / n2
                p = [[c0[0][k] + e1[k] * s + e2[k] * t for k in range(3)] for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1))]
                sm, tm = (s0 + s1) / 2, (t0 + t1) / 2
                u = c0[2][0] + (c1[2][0] - c0[2][0]) * sm + (c3[2][0] - c0[2][0]) * tm
                v = c0[2][1] + (c1[2][1] - c0[2][1]) * sm + (c3[2][1] - c0[2][1]) * tm
                tx, ty = min(w // 2 - 1, int(u * w)), min(h - 1, int(v * h))
                alb, mat = px[tx, ty], px[tx + w // 2, ty]
                metal, gloss, emit = mat[0] / 255, mat[1] / 255, mat[2] / 255
                if alb[3] < 26:
                    continue
                base = PLATE if metal > 0.3 else SOFT
                # a line: bright glossy metal (the armour shader's own trims), or the set's accent colour wherever it is painted
                role = EMIT if emit > 0.25 else TRIM if metal * gloss * smooth(0.35, 0.7, max(alb[:3]) / 255) > 0.3 or near(alb[:3], accent) else base
                cy = sum(pt[1] for pt in p) / 4 * 16
                cx, cz = sum(pt[0] for pt in p) / 4 * 16, sum(pt[2] for pt in p) / 4 * 16
                # how far it stands off the sleeve (the arm is two texels each way from its middle, a sleeve and its cuff two more)
                off = min(4.0, max(0.0, max(abs(cx - axis), abs(cz)) - 4.0))
                grid[(i, j)] = len(out)
                out.append(dict(p=p, n=n, alb=alb, role=role, base=base, q=q, v=cy + 2.0, face=face, lum=max(alb[:3]) / 255, off=off,
                                ul=j if first_along else i, cols=n2 if first_along else n1))
        # a line is thin: the rim of a bright plate, or a strip a texel or two wide. The middle of a wide one is plate or cloth again.
        inner = [k for (i, j), k in grid.items() if out[k]["role"] == TRIM
                 and all(out[grid[nb]]["role"] == TRIM if nb in grid else False for nb in ((i - 1, j), (i + 1, j), (i, j - 1), (i, j + 1)))]
        for k in inner:
            out[k]["role"] = out[k]["base"]
    return out


def light(style, c, t, seed, charge, awake, gains):
    """ArmourLight.light: how lit one cell is. 0 dark, to 1 and a touch over; negative for ink."""
    v, q, ul, cols, role, f = c["v"], c["q"], c["ul"], max(1, c["cols"]), c["role"], c["face"]
    lead, soak, shimmer = gains["lead"], gains["soak"], gains["shimmer"]
    # what stands off the arm lights late: the light has to run out along it (to the fist it arrives later; from the hand it grows later)
    out = c["off"] * 0.8 * gains["tips"]
    if out > 0:
        v = v - out if style in FROM_HAND else v + out
    vi = math.floor(v)
    reach = charge * ROWS
    line = role >= TRIM  # the set's own lines: its trims and what glows of itself
    here = v < reach + (lead if line else 0.0)  # the lines run ahead of the rest
    region = here  # where the light has been: there a set's cloth may hold a glow
    g = (gains["soft"], gains["plate"], gains["trim"], gains["emit"])[role]
    i = 0.0
    if style == "KINETIC":
        # cracks: the set's lines pulse as if about to split, one seam wanders down every face
        if here:
            if role == EMIT:
                i = 1.05
            elif role == TRIM:
                i = 0.62 + 0.38 * math.sin(t * 0.55 + v * 0.9)
            else:
                seam = int(hash3(seed, q, vi // 2) * cols)
                on = ul == seam or (vi % 4 == 1 and abs(ul - seam) == 1 and hash3(seed, q + 7, vi) > 0.45)
                if awake and not on:
                    on = vi % 6 == 3 and hash3(seed, q + 3, ul + vi) > 0.5
                i = 0.6 + 0.3 * math.sin(t * 0.55 + v * 0.9) if on else 0.0
    elif style == "HEAT":
        # the hand's end burns; the lines glow hot behind it and embers sit in the rest
        lick = 3.5 + 2.5 * math.sin(t * 0.9 + ul * 2.1 + q) + (2.0 if awake else 0.0)
        if v >= ROWS - lick * charge:
            i = 0.75 + 0.35 * hash3(seed, ul + q * 5, int(t * 0.8) + vi)
            region = True
        elif here:
            if line:
                i = 0.55 + 0.35 * math.sin(t * 0.7 + ul + v)
            else:
                i = 0.45 + 0.4 * math.sin(t * 0.7 + ul + v) if hash3(seed, q * 4 + ul, vi) > 0.78 else 0.0
    elif style == "VELOCITY":
        # streaks run to the fist: down every line, and one lane of each face
        if here:
            lane = int(hash3(seed, q, 1) * cols)
            if line or ul == lane or (awake and ul == (lane + 2) % cols):
                run = fmod(v - t * 1.6 + hash3(seed, q, ul) * 9.0, 6.0)
                i = 0.55 + 0.14 * run if run < 3.6 else (0.3 if line else 0.0)
    elif style == "FLIGHT":
        # motes that lift and drift; the lines hold a pale light
        if here:
            drift = int(t * 0.22)
            hh = hash3(seed + drift // 3, q * 5 + ul, vi + drift)
            if hh > (0.8 if awake else 0.86):
                i = 0.6 + 0.4 * math.sin(t * 0.4 + hh * 30.0)
            elif line:
                i = 0.42
    elif style == "GRAVITY":
        # bands falling to the wrist, ink pooled there with the lines lit over it
        if here:
            band = fmod(v + t * 0.5, 4.0)
            if v >= 9.5:
                i = 0.9 if line else -(0.6 + 0.4 * math.sin(t * 0.3 + ul))
            elif band < 1.0:
                i = 0.85 if line else 0.7
            elif awake and band < 2.0 and (ul + vi) % 2 == 0:
                i = 0.45
    elif style in ("ENERGY", "PRISM"):
        # the lines carry it, a pulse running down them; a vein forks across each face
        if here:
            pulse = fmod(v - t * 0.9, 5.0)
            if line:
                i = 1.15 if pulse < 1.2 else 0.62
            else:
                vein = min(cols - 1, (vi // 2 + int(hash3(seed, q, 2) * 2)) % 2 + (1 if cols > 2 else 0))
                on = ul == vein or (vi % 4 == 1 and ul == min(cols - 1, vein + 1)) or (awake and vi % 4 == 3 and ul == max(0, vein - 1))
                if on:
                    i = 1.1 if pulse < 1.2 else 0.58
    elif style == "CHARGE":
        # lightning crawling the lines, jumping every few ticks; a jagged line down each face
        if here:
            jump = int(t * 0.5)
            if line:
                on = hash3(seed + jump, q, vi // 2) > 0.4
            else:
                at = int(hash3(seed + jump, q, vi // 2) * cols)
                on = ul == at or (awake and ul == (at + 1) % cols and vi % 3 == 0)
            if on:
                i = 1.1 if hash3(seed + int(t), q + ul, vi) > 0.25 else 0.5
    elif style == "SHADOW":
        # ink creeping back from the hand over cloth and plate; the lines stay lit in it
        edge = ROWS - charge * (7.0 + (2.0 if awake else 0.0)) + hash3(seed, q, ul) * 2.5 + 0.6 * math.sin(t * 0.25 + ul + q)
        region = False
        if v >= edge + 1.0:
            i = 0.85 if line else -1.0
        elif v >= edge:
            i = 0.8
    elif style in ("MIND", "SOUL"):
        # rings closing round the wrist, one after another, across everything they cross
        rings = 1 + int(charge * (4.99 if awake else 3.99))
        for r in range(rings):
            at = 10.5 - r * 2
            if abs(v - at) <= 0.5:
                turn = fmod(t * 0.25 - r * 0.7, 3.0)
                i = 1.1 if turn < 1.0 else 0.6
                break
        region = v >= 10.5 - rings * 2
        if i == 0.0 and line and region:
            i = 0.4
    elif style == "ARCHITECT":
        # a drawing: the set's lines inked in as the light arrives, a rule across every third row
        if here:
            if line:
                i = 1.1 if v > reach - 1.5 else 0.62
            elif vi % 3 == 0 or ul == 0:
                i = 1.0 if v > reach - 1.5 else 0.5
            elif awake and (ul + vi) % 4 == 0:
                i = 0.35
    elif style == "TECH":
        # a scan: the lines lit teal, a bright row sweeping the arm, a pixel blinking on each face
        if here:
            scan = fmod(t * 0.45, ROWS + 3.0)
            if abs(v - scan) <= 0.5:
                i = 1.15
            elif line:
                i = 0.6
            elif hash3(seed, q, ul * 31 + vi) > 0.93:
                i = 1.1 if (int(t * 0.3) + vi) % 3 == 0 else 0.45
            elif awake and vi % 4 == 3:
                i = 0.4
    elif style == "SPIRIT":
        # tongues of flame from the hand, taller on the lines
        tongue = (2.5 + 3.5 * hash3(seed, q, ul) + 1.4 * math.sin(t * 0.6 + ul * 1.7 + q * 2.3)) * charge + (1.5 * charge if awake else 0.0)
        if line:
            tongue += (1.0 + lead) * charge
        region = v >= ROWS - 4.0 * charge
        if v >= ROWS - tongue:
            i = 1.1 if v >= ROWS - 2 else 0.6 + 0.25 * math.sin(t * 0.8 + v)
    elif style == "FROST":
        # rime: it takes the lines first, then crystals grow across the rest in diagonals
        grown = charge * (9.0 + (3.0 if awake else 0.0))
        region = v >= ROWS - grown
        if v >= ROWS - grown - (1.0 + lead if line else 0.0):
            if line:
                i = 0.75 + 0.4 * hash3(seed, ul + q * 3, vi + int(t * 0.15))
            elif (ul + vi + f) % 3 == 0 or (ul - vi - f) % 4 == 0:
                i = 0.7 + 0.4 * hash3(seed, ul + q * 3, vi + int(t * 0.15))
            elif v >= ROWS - 1.5:
                i = 0.5
    else:
        if here and (line or (ul + vi) % 3 == 0):
            i = 0.6
    if i == 0.0 and not line and region:
        # nothing of the motif here: a texel may still catch the light for a moment (mail, rivets), or cloth hold a glow
        if gains["glitter"] > 0 and hash3(seed, q * 13 + ul, vi * 7 + int(t * 0.5)) > 1.0 - 0.07 * gains["glitter"]:
            return 0.95 * g
        if role == SOFT and soak > 0:
            return soak * (0.8 + 0.2 * math.sin(t * 0.3 + q))
        return 0.0
    if role == EMIT and i > 0:
        i *= 1.0 - 0.3 * shimmer * hash3(seed, q * 7 + ul, vi + int(t * 0.6))
    if not line and i > 0:
        # cloth and plate take the light by their own painting, so the piece still reads: its bright texels more (grain +1)
        # or its dark ones (grain -1: the light sits in the cracks)
        grain, lum = gains["grain"], c["lum"]
        i *= 0.5 + 0.65 * (lum * (1 + grain) + (1 - lum) * (1 - grain)) * 0.5
        # rows of scales or feathers: a wave of shade running down them
        if gains["ripple"] > 0:
            i *= 1.0 - 0.35 * gains["ripple"] * (0.5 + 0.5 * math.sin(t * 0.45 - v * 1.4 + q * 0.7))
    if i > 0 and c["off"] > 0:
        i *= 1.0 + 0.35 * gains["tips"] * min(c["off"], 3.0) / 3.0  # and what stands off the arm burns a little brighter
    return i * g if i > 0 else i


def alpha(c, i):
    """ArmourLight: how much of a cell the light covers. The lines are crisp; cloth and plate are tinted, never painted out."""
    if i < 0:
        return 0.62 * min(1.0, -i)
    return max(0.0, min(1.0, 0.18 + 0.55 * i if c["role"] >= TRIM else 0.62 * i))


# The types whose light grows back from the hand (the rest run to the fist).
FROM_HAND = ("HEAT", "SHADOW", "MIND", "SOUL", "SPIRIT", "FROST")


def rgb(x):
    return (x >> 16) & 255, (x >> 8) & 255, x & 255


def mix(a, b, t):
    return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))


def view(cs, lit, style, yaw, pitch, size=(250, 400), scale=30):
    core, mid, _edge, accent = (rgb(x) for x in STYLES[style])
    cyw, syw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def turn(x, y, z):
        x, z = x * cyw + z * syw, -x * syw + z * cyw
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    img = Image.new("RGB", size, (24, 26, 34))
    d = ImageDraw.Draw(img)
    items = []
    for k, c in enumerate(cs):
        pts = [turn(p[0] * 16 - 1.0, p[1] * 16 - 3.0, p[2] * 16) for p in c["p"]]
        nz = turn(*c["n"])[2]
        if nz > 0.05:
            continue
        items.append((sum(p[2] for p in pts) / 4, pts, k, -nz))
    items.sort(key=lambda it: -it[0])
    for _depth, pts, k, lam in items:
        c = cs[k]
        shade = 0.45 + 0.55 * lam
        col = tuple(int(x * shade) for x in c["alb"][:3])
        i = lit[k]
        if i < 0:
            col = mix(col, accent, alpha(c, i))
        elif i > 0:
            glow = mix(mid, core, max(0.0, min(1.0, (i - 0.55) * 2.2)))
            col = mix(col, glow, alpha(c, i))
        d.polygon([(size[0] / 2 + p[0] * scale * 0.5, size[1] / 2 + p[1] * scale * 0.5) for p in pts], fill=col)
    return img


def sheet(out, sid, styles, charge=1.0, t=37.0, awake=False):
    tex = Image.open(os.path.join(ROOT, "textures", "entity", "armour", sid + ".png")).convert("RGBA")
    mesh = os.path.join(ROOT, "armour", sid, "chestplate_left_arm.mesh")
    cs = cells(read(mesh), tex, accent_of(mesh))
    gains = SETS.get(sid, SETS["default"])
    views = [(0, 0), (90, 0), (180, 0), (-90, 0), (55, -50)]
    img = Image.new("RGB", (250 * len(views) * 2 + 20, 400 * ((len(styles) + 1) // 2)), (24, 26, 34))
    d = ImageDraw.Draw(img)
    for si, style in enumerate(styles):
        seed = hash(style) & 0xFFFF
        lit = [light(style, c, t, seed, charge, awake, gains) for c in cs]
        x0, y0 = (si % 2) * (250 * len(views) + 20), (si // 2) * 400
        for vi, (yaw, pitch) in enumerate(views):
            img.paste(view(cs, lit, style, yaw, pitch), (x0 + 250 * vi, y0))
        d.text((x0 + 8, y0 + 6), f"{sid}  {style}  charge {charge}", fill=(255, 255, 255))
    path = os.path.join(out, f"{sid}_{int(charge * 100)}.png")
    img.save(path)
    return path


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for sid in sys.argv[2:] or ["gatewrought", "vaultbreaker", "frostwalker", "sunward", "shardcrown"]:
        print(sheet(out, sid, ["ENERGY", "HEAT", "FROST", "KINETIC", "CHARGE", "SHADOW", "SPIRIT", "TECH", "VELOCITY", "MIND", "GRAVITY", "ARCHITECT"]))
