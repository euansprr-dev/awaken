"""The Awakened layer's overworld creatures (world module, fauna): every texture, the content JSON, and a 3D preview.

    python3 tools/art/build_all.py fauna          textures (skin + glow layers, own-model textures, eggs, parts, GUI pulse)
    python3 tools/art/specs/fauna.py content      biome modifiers, loot tables, item models, lang (src/main/...; the
                                                  modifiers' biomes: tools/terrain/biomes.py)
    python3 tools/art/specs/fauna.py preview DIR  renders every creature on its real model (day, night, line-up) to DIR

The creature list is read from mobs/def/FaunaBestiary.java. Box layouts mirror the Java models box for box:
our own bodies in mobs/client/model/FaunaModels.java and AwModels.java (the serpent), vanilla bodies (allay, parrot,
fox, wolf, hoglin) from the 26.3 sources. On vanilla bodies the skin layer repaints every face, so the vanilla texture
underneath never shows; vanilla textures are only read (for the preview), never copied.
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, ART)
sys.path.insert(0, os.path.abspath(os.path.join(ROOT, "tools/content")))
from pixelart import *  # noqa: E402,F401
from pixelart import _outline  # noqa: E402
import mobs_bestiary as MB  # noqa: E402

JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/FaunaBestiary.java")
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/fauna/assets/awaken")
VANILLA_TEX = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/entity"

ROLE_RE = re.compile(r'\.fauna\(FaunaRole\.(\w+)\)')
TALENT_RE = re.compile(r'\.talent\(FaunaTalent\.(\w+)\)')
TIER_DROP = re.compile(r'')


def fauna():
    """Every overworld creature in declaration order, parsed like the mobs bestiary."""
    src = open(JAVA).read()
    out = []
    starts = [m.start() for m in MB.M_RE.finditer(src)]
    for st in starts:
        end = src.find(".build());", st)
        block = src[st:end]
        mid, name, theme, tier, model, sig = MB.M_RE.match(block).groups()
        tint = MB.TINT_RE.search(block)
        glow = MB.GLOW_RE.search(block)
        sc = MB.SCALE_RE.search(block)
        out.append({
            "id": mid, "name": MB.unescape(name), "theme": theme, "tier": tier, "model": model, "sig": MB.unescape(sig),
            "role": ROLE_RE.search(block).group(1), "talent": TALENT_RE.search(block).group(1),
            "parts": [{"id": p, "name": MB.unescape(n), "ramp": r} for p, n, r in MB.P_RE.findall(block)],
            "tint": ("#" + tint.group(1).lower()) if tint else None,
            "glow": ("#" + glow.group(1).lower()) if glow else "#ffffff",
            "scale": float(sc.group(1)) if sc else 1.0,
        })
    return out


def rgba(h, a=255):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(c[i] * f)))) for i in range(3)) + (c[3] if len(c) > 3 else 255,)


# ---------------------------------------------------------------------------------------------------------------------
# Geometry: the models as box trees (texOffs u, v; box x, y, z, w, h, d; mirror; grow), pivots and rotations in
# radians (x, y, z), exactly as the Java PartPoses. Preview poses add the idle rotations setupAnim gives.


def box(u, v, x, y, z, w, h, d, mirror=False, grow=0.0):
    return {"u": u, "v": v, "x": x, "y": y, "z": z, "w": w, "h": h, "d": d, "mirror": mirror, "grow": grow}


def part(pivot=(0, 0, 0), rot=(0, 0, 0), boxes=(), children=()):
    return {"pivot": pivot, "rot": rot, "boxes": list(boxes), "children": list(children)}


def _moth():
    head = part((0, -0.5, -1.5), (0, 0, 0), [box(16, 0, -1, -1, -2, 2, 2, 2)], [
        part((0.3, -0.8, -1.5), (-0.5, -0.3, 0.35), [box(24, 0, 0, -3, 0, 3, 3, 0)]),
        part((-0.3, -0.8, -1.5), (-0.5, 0.3, -0.35), [box(24, 0, -3, -3, 0, 3, 3, 0, mirror=True)])])
    body = part((0, 18, 0), (0.12, 0, 0), [box(0, 0, -1.5, -1.5, -1.5, 3, 3, 3), box(0, 6, -1.5, -1.0, 1.5, 3, 3, 5)], [
        head,
        part((1.5, -1.0, -0.5), (0, -0.12, -0.55), [box(0, 14, 0, 0, -2, 10, 0, 7)]),
        part((-1.5, -1.0, -0.5), (0, 0.12, 0.55), [box(0, 14, -10, 0, -2, 10, 0, 7, mirror=True)]),
        part((1.5, -0.6, 1.5), (0, -0.4, -0.4), [box(0, 22, 0, 0, 0, 7, 0, 6)]),
        part((-1.5, -0.6, 1.5), (0, 0.4, 0.4), [box(0, 22, -7, 0, 0, 7, 0, 6, mirror=True)])])
    return (64, 32), part(children=[body])


def _antler(side):
    m = side < 0
    return part((side * 1.5, -3, -1), (-0.3, 0, -side * 0.55), [box(44, 24, -0.5, -9, -0.5, 1, 9, 1, mirror=m)], [
        part((0, -3, 0), (-1.0, 0, side * 0.25), [box(48, 24, -0.5, -5, -0.5, 1, 5, 1, mirror=m)]),
        part((0, -6, 0), (-0.55, 0, -side * 0.55), [box(48, 24, -0.5, -5, -0.5, 1, 5, 1, mirror=m)]),
        part((0, -8, 0), (0.55, 0, side * 0.4), [box(48, 24, -0.5, -5, -0.5, 1, 5, 1, mirror=m)]),
        part((0, -9, 0), (0.1, 0, side * 0.7), [box(52, 24, -0.5, -4, -0.5, 1, 4, 1, mirror=m)])])


def _stag():
    head = part((0, -9, 0), (-0.45, 0, 0), [box(0, 24, -2.5, -3, -5, 5, 5, 6), box(22, 24, -1.5, -1, -8, 3, 3, 3)], [
        part((2.5, -2, 0), (0, 0, -0.35), [box(34, 24, 0, -0.5, -0.5, 3, 2, 1)]),
        part((-2.5, -2, 0), (0, 0, 0.35), [box(34, 24, -3, -0.5, -0.5, 3, 2, 1, mirror=True)]),
        _antler(1), _antler(-1)])
    return (64, 64), part(children=[
        part((0, 9, 0), (0, 0, 0), [box(0, 0, -4, -4, -8, 8, 8, 16)]),
        part((0, 7, -6.5), (0.45, 0, 0), [box(48, 0, -2, -9, -2, 4, 10, 4)], [head]),
        part((2.5, 12, -6), (0.25, 0, 0), [box(0, 40, -1.5, 0, -1.5, 3, 12, 3)]),
        part((-2.5, 12, -6), (-0.25, 0, 0), [box(0, 40, -1.5, 0, -1.5, 3, 12, 3, mirror=True)]),
        part((2.5, 12, 6), (-0.25, 0, 0), [box(0, 40, -1.5, 0, -1.5, 3, 12, 3)]),
        part((-2.5, 12, 6), (0.25, 0, 0), [box(0, 40, -1.5, 0, -1.5, 3, 12, 3, mirror=True)]),
        part((0, 6, 7.5), (0.6, 0, 0), [box(12, 40, -1.5, 0, 0, 3, 4, 2)])])


def _tortoise():
    shell = part((0, 20, 0), (0, 0, 0), [box(0, 0, -7, -5, -8, 14, 5, 16), box(0, 21, -5, -8, -6, 10, 3, 12),
                                          box(44, 27, -4, -9, -3, 4, 1, 4), box(44, 27, 1, -9, 0, 4, 1, 4)], [
        part((1, -8, -1), (0, 0.785, 0), [box(44, 21, -3, -6, 0, 6, 6, 0)]),
        part((1, -8, -1), (0, -0.785, 0), [box(44, 21, -3, -6, 0, 6, 6, 0)])])
    return (64, 64), part(children=[
        shell,
        part((0, 17, -8), (0.1, 0, 0), [box(0, 36, -2, -2, -5, 4, 4, 5)]),
        part((5, 19, -5.5), (0.3, 0, 0), [box(18, 36, -2, 0, -2, 4, 5, 4)]),
        part((-5, 19, -5.5), (-0.3, 0, 0), [box(18, 36, -2, 0, -2, 4, 5, 4, mirror=True)]),
        part((5, 19, 5.5), (-0.3, 0, 0), [box(18, 36, -2, 0, -2, 4, 5, 4)]),
        part((-5, 19, 5.5), (0.3, 0, 0), [box(18, 36, -2, 0, -2, 4, 5, 4, mirror=True)]),
        part((0, 18, 7.5), (0, 0, 0), [box(34, 36, -1, 0, 0, 2, 2, 3)])])


def _ray():
    tail = part((0, 0, 5), (-0.08, 0.15, 0), [box(38, 4, -0.5, -0.5, 0, 1, 1, 8)], [
        part((0, 0, 8), (0, 0.25, 0), [box(0, 23, -0.5, -0.5, 0, 1, 1, 8), box(18, 23, 0, -2, 1, 0, 3, 4)])])
    body = part((0, 21, 0), (0, 0, 0), [box(0, 0, -4, -1, -6, 8, 2, 11), box(38, 0, -3.5, -0.5, -9, 2, 1, 3),
                                         box(38, 0, 1.5, -0.5, -9, 2, 1, 3, mirror=True)], [
        part((4, 0, -1), (0, 0, -0.3), [box(0, 13, 0, -0.5, -4, 8, 1, 9)], [part((8, 0, 0), (0, 0, -0.35), [box(34, 13, 0, -0.5, -2.5, 6, 1, 5)])]),
        part((-4, 0, -1), (0, 0, 0.3), [box(0, 13, -8, -0.5, -4, 8, 1, 9, mirror=True)], [part((-8, 0, 0), (0, 0, 0.35), [box(34, 13, -6, -0.5, -2.5, 6, 1, 5, mirror=True)])]),
        tail])
    return (64, 32), part(children=[body])


def _scarab():
    legs = []
    for i in range(3):
        z = -2 + i * 2
        legs.append(part((2.5, -0.5, z), (0, (i - 1) * -0.5, 0.8), [box(16, 20, 0, 0, -0.5, 3, 1, 1)]))
        legs.append(part((-2.5, -0.5, z), (0, (i - 1) * 0.5, -0.8), [box(16, 20, -3, 0, -0.5, 3, 1, 1, mirror=True)]))
    head = part((0, -1, -3.5), (0, 0, 0), [box(0, 20, -2, -2, -2, 4, 3, 2)], [part((0, -1, -1), (-0.5, 0, 0), [box(12, 20, -0.5, -3, -1, 1, 3, 1)])])
    body = part((0, 22.5, 0), (0, 0, 0), [box(0, 0, -3, -2, -3.5, 6, 2, 7), box(0, 10, -3.5, -4.5, -3, 7, 3, 7)], [head] + legs)
    return (32, 32), part(children=[body])


def _allay():
    body = part((0, -4, 0), (0, 0, 0), [box(0, 10, -1.5, 0, -1, 3, 4, 2), box(0, 16, -1.5, 0, -1, 3, 5, 2, grow=-0.2)], [
        part((-1.75, 0.5, 0), (0, 0, 0.44), [box(23, 0, -0.75, -0.5, -1, 1, 4, 2, grow=-0.01)]),
        part((1.75, 0.5, 0), (0, 0, -0.44), [box(23, 6, -0.25, -0.5, -1, 1, 4, 2, grow=-0.01)]),
        part((-0.5, 0, 0.6), (0.44, -0.79, 0), [box(16, 14, 0, 1, 0, 0, 5, 8)]),
        part((0.5, 0, 0.6), (0.44, 0.79, 0), [box(16, 14, 0, 1, 0, 0, 5, 8)])])
    root = part((0, 23.5, 0), (0, 0, 0), [], [part((0, -3.99, 0), (0, 0, 0), [box(0, 0, -2.5, -5, -2.5, 5, 5, 5)]), body])
    return (32, 32), part(children=[root])


def _parrot():
    head = part((0, 15.69, -2.76), (0, 0, 0), [box(2, 2, -1, -1.5, -1, 2, 3, 2)], [
        part((0, -2, -1), (0, 0, 0), [box(10, 0, -1, -0.5, -2, 2, 1, 4)]),
        part((0, -0.5, -1.5), (0, 0, 0), [box(11, 7, -0.5, -1, -0.5, 1, 2, 1)]),
        part((0, -1.75, -2.45), (0, 0, 0), [box(16, 7, -0.5, 0, -0.5, 1, 2, 1)]),
        part((0, -2.15, 0.15), (-0.2214, 0, 0), [box(2, 18, 0, -4, -2, 0, 5, 4)])])
    return (32, 32), part(children=[
        part((0, 16.5, -3), (0.4937, 0, 0), [box(2, 8, -1.5, 0, -1.5, 3, 6, 3)]),
        part((0, 21.07, 1.16), (1.015, 0, 0), [box(22, 1, -1.5, -1, -1, 3, 4, 1)]),
        part((1.5, 16.94, -2.76), (-0.6981, -math.pi, 0), [box(19, 8, -0.5, 0, -1.5, 1, 5, 3)]),
        part((-1.5, 16.94, -2.76), (-0.6981, -math.pi, 0), [box(19, 8, -0.5, 0, -1.5, 1, 5, 3)]),
        head,
        part((1, 22, -1.05), (-0.03, 0, 0), [box(14, 18, -0.5, 0, -0.5, 1, 2, 1)]),
        part((-1, 22, -1.05), (-0.03, 0, 0), [box(14, 18, -0.5, 0, -0.5, 1, 2, 1)])])


def _fox():
    leg_l = [box(4, 24, 2, 0.5, -1, 2, 6, 2, grow=0.001)]
    leg_r = [box(13, 24, 2, 0.5, -1, 2, 6, 2, grow=0.001)]
    head = part((-1, 16.5, -3), (0, 0, 0), [box(1, 5, -3, -2, -5, 8, 6, 6)], [
        part((0, 0, 0), (0, 0, 0), [box(8, 1, -3, -4, -4, 2, 2, 1)]),
        part((0, 0, 0), (0, 0, 0), [box(15, 1, 3, -4, -4, 2, 2, 1)]),
        part((0, 0, 0), (0, 0, 0), [box(6, 18, -1, 2.01, -8, 4, 2, 3)])])
    body = part((0, 16, -6), (math.pi / 2, 0, 0), [box(24, 15, -3, 3.999, -3.5, 6, 11, 6)], [
        part((-4, 15, -1), (-0.0524, 0, 0), [box(30, 0, 2, 0, -1, 4, 9, 5)])])
    return (48, 32), part(children=[head, body,
                                     part((-5, 17.5, 7), (0, 0, 0), leg_r), part((-1, 17.5, 7), (0, 0, 0), leg_l),
                                     part((-5, 17.5, 0), (0, 0, 0), leg_r), part((-1, 17.5, 0), (0, 0, 0), leg_l)])


def _wolf():
    head = part((-1, 13.5, -7), (0, 0, 0), [], [part((0, 0, 0), (0, 0, 0), [
        box(0, 0, -2, -3, -2, 6, 6, 4), box(16, 14, -2, -5, 0, 2, 2, 1), box(16, 14, 2, -5, 0, 2, 2, 1), box(0, 10, -0.5, -0.001, -5, 3, 3, 4)])])
    leg = [box(0, 18, 0, 0, -1, 2, 8, 2)]
    legm = [box(0, 18, 0, 0, -1, 2, 8, 2, mirror=True)]
    return (64, 32), part(children=[
        head,
        part((0, 14, 2), (math.pi / 2, 0, 0), [box(18, 14, -3, -2, -3, 6, 9, 6)]),
        part((-1, 14, -3), (math.pi / 2, 0, 0), [box(21, 0, -3, -3, -3, 8, 6, 7)]),
        part((-2.5, 16, 7), (0, 0, 0), legm), part((0.5, 16, 7), (0, 0, 0), leg),
        part((-2.5, 16, -4), (0, 0, 0), legm), part((0.5, 16, -4), (0, 0, 0), leg),
        part((-1, 12, 8), (math.pi / 5, 0, 0), [], [part((0, 0, 0), (0, 0, 0), [box(9, 18, 0, 0, -1, 2, 8, 2)])])])


def _hoglin():
    head = part((0, 2, -12), (0.87266, 0, 0), [box(61, 1, -7, -3, -19, 14, 6, 19)], [
        part((-6, -2, -3), (0, 0, -2 * math.pi / 9), [box(1, 1, -6, -1, -2, 6, 1, 4)]),
        part((6, -2, -3), (0, 0, 2 * math.pi / 9), [box(1, 6, 0, -1, -2, 6, 1, 4)]),
        part((-7, 2, -12), (0, 0, 0), [box(10, 13, -1, -11, -1, 2, 11, 2)]),
        part((7, 2, -12), (0, 0, 0), [box(1, 13, -1, -11, -1, 2, 11, 2)])])
    body = part((0, 7, 0), (0, 0, 0), [box(1, 1, -8, -7, -13, 16, 14, 26)], [part((0, -14, -7), (0, 0, 0), [box(90, 33, 0, 0, -9, 0, 10, 19, grow=0.001)])])
    return (128, 64), part(children=[body, head,
                                      part((-4, 10, -8.5), (0, 0, 0), [box(66, 42, -3, 0, -3, 6, 14, 6)]),
                                      part((4, 10, -8.5), (0, 0, 0), [box(41, 42, -3, 0, -3, 6, 14, 6)]),
                                      part((-5, 13, 10), (0, 0, 0), [box(21, 45, -2.5, 0, -2.5, 5, 11, 5)]),
                                      part((5, 13, 10), (0, 0, 0), [box(0, 45, -2.5, 0, -2.5, 5, 11, 5)])])


def _serpent():
    segs = [(0, 26, 3, 6), (0, 26, 3, 6), (28, 26, 2.5, 5), (28, 26, 2.5, 5), (0, 42, 2, 4)]
    tail = part((0, 0, 8), (0, 0.2, 0), [box(26, 42, -1, -1, 0, 2, 2, 8), box(46, 42, -0.5, -3, 3, 1, 6, 6)])
    node = tail
    for i in reversed(range(5)):
        u, v, half, s = segs[i]
        node = part((0, 16, 0) if i == 0 else (0, 0, 8), (0, (0.25 if i % 2 else -0.2), 0), [box(u, v, -half, -half, 0, s, s, 8)], [node])
    head = part((0, 16, 0), (0.1, 0, 0), [box(0, 0, -4, -3, -10, 8, 6, 10), box(36, 0, -0.5, -7, -8, 1, 4, 8)],
                [part((0, 2.5, -1), (0.25, 0, 0), [box(0, 16, -3.5, 0, -8, 7, 2, 8)])])
    return (64, 64), part(children=[head, node])


MODELS = {"MOTH": _moth, "STAG": _stag, "TORTOISE": _tortoise, "RAY": _ray, "SCARAB": _scarab,
          "ALLAY": _allay, "PARROT": _parrot, "FOX": _fox, "SNOW_WOLF": _wolf, "HOGLIN": _hoglin, "SERPENT": _serpent}
OWN = {"MOTH", "STAG", "TORTOISE", "RAY", "SCARAB", "SERPENT"}
VANILLA = {"ALLAY": "allay/allay", "PARROT": "parrot/parrot_grey", "FOX": "fox/fox_snow", "SNOW_WOLF": "wolf/wolf_snowy_angry",
           "HOGLIN": "hoglin/hoglin"}


def faces(b):
    """Texture rects (x, y, w, h) of a box's faces by Minecraft's cube UV layout. 'top' is the face at minY (it draws
    on top: models render y-down), its rows run back (maxZ) to front (minZ). Side faces: 'west' runs back->front left
    to right, 'east' front->back, 'north' is the front (-z), 'south' the back."""
    u, v, w, h, d = b["u"], b["v"], int(round(b["w"])), int(round(b["h"])), int(round(b["d"]))
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def boxes_of(tree):
    out = []

    def walk(p):
        out.extend(p["boxes"])
        for ch in p["children"]:
            walk(ch)
    walk(tree)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Preview renderer: textured boxes, one texel at a time (painter's algorithm), orthographic, a key light. Good enough to
# judge silhouettes, colours and how the skin sits on the body before anyone opens the game.


def _rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    rym = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rzm = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return _mm(rzm, _mm(rym, rxm))


def _mm(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def _mv(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


def _quads(tree):
    """Every face of every box as (corners[4] in model space, uv rect, outward normal, plane?)."""
    out = []

    def walk(p, R, T):
        R2 = _mm(R, _rot(*p["rot"]))
        T2 = tuple(T[i] + _mv(R, p["pivot"])[i] for i in range(3))

        def tf(pt):
            q = _mv(R2, pt)
            return (q[0] + T2[0], q[1] + T2[1], q[2] + T2[2])
        for b in p["boxes"]:
            g = b["grow"]
            x0, y0, z0 = b["x"] - g, b["y"] - g, b["z"] - g
            x1, y1, z1 = b["x"] + b["w"] + g, b["y"] + b["h"] + g, b["z"] + b["d"] + g
            if b["mirror"]:
                x0, x1 = x1, x0
            t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
            l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
            u, v, w, h, d = b["u"], b["v"], b["w"], b["h"], b["d"]
            U0, U1, U2, U22, U3, U4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
            V0, V1, V2 = v, v + d, v + d + h
            plane = w == 0 or h == 0 or d == 0
            centre = tf(((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))
            for verts, rect in (((l1, l0, t0, t1), (U1, V0, U2, V1)), ((t2, t3, l3, l2), (U2, V1, U22, V0)),
                                ((t0, l0, l3, t3), (U0, V1, U1, V2)), ((t1, t0, t3, t2), (U1, V1, U2, V2)),
                                ((l1, t1, t2, l2), (U2, V1, U3, V2)), ((l0, l1, l2, l3), (U3, V1, U4, V2))):
                if rect[0] == rect[2] or rect[1] == rect[3]:
                    continue
                vs = [tf(q) for q in verts]
                fc = tuple(sum(q[i] for q in vs) / 4 for i in range(3))
                n = tuple(fc[i] - centre[i] for i in range(3))
                out.append((vs, rect, n, plane))
        for ch in p["children"]:
            walk(ch, R2, T2)
    walk(tree, ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return out


def render(tree, tex, yaw=35.0, pitch=22.0, px=8.0, size=None, light=1.0, night=None):
    """tex: a PIL RGBA image (composited layers). night: optional glow image drawn unlit over a darkened body."""
    from PIL import Image, ImageDraw
    ya, pa = math.radians(yaw), math.radians(pitch)
    # Look at the creature's front (-z) from front-left and a little above; y stays down on screen.
    cam = _mm(_rot(pa, 0, 0), _rot(0, ya, 0))
    lightdir = (-0.45, -0.75, -0.5)
    ln = math.sqrt(sum(c * c for c in lightdir))
    lightdir = tuple(c / ln for c in lightdir)
    tw, th = tex.size
    texpx = tex.load()
    glowpx = night.load() if night is not None else None
    polys = []
    for vs, (ua, va, ub, vb), n, plane in _quads(tree):
        cv = [_mv(cam, q) for q in vs]
        nv = _mv(cam, n)
        if not plane and nv[2] > 1e-6:
            continue  # faces away from the camera
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.62 + 0.38 * max(0.0, -sum(n[i] / nl * lightdir[i] for i in range(3)))
        nu, nvv = abs(ub - ua), abs(vb - va)
        iu, iv = int(round(nu)), int(round(nvv))
        for j in range(iv):
            for i in range(iu):
                tu = min(ua, ub) + i
                tv = min(va, vb) + j
                if not (0 <= tu < tw and 0 <= tv < th):
                    continue
                col = texpx[int(tu), int(tv)]
                gcol = glowpx[int(tu), int(tv)] if glowpx is not None else (0, 0, 0, 0)
                if col[3] < 16 and gcol[3] < 16:
                    continue
                corners = []
                for (a, b) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    s = (tu + a - ua) / (ub - ua)
                    t = (tv + b - va) / (vb - va)
                    # vertex[1]=(u0,v0) vertex[0]=(u1,v0) vertex[2]=(u0,v1) vertex[3]=(u1,v1)
                    p = tuple((1 - s) * (1 - t) * cv[1][k] + s * (1 - t) * cv[0][k] + (1 - s) * t * cv[2][k] + s * t * cv[3][k] for k in range(3))
                    corners.append(p)
                depth = sum(c[2] for c in corners) / 4
                if night is not None:
                    base = shade(col, 0.22 * lit) if col[3] >= 16 else None
                    polys.append((depth, corners, base, gcol if gcol[3] >= 16 else None))
                else:
                    polys.append((depth, corners, shade(col, lit * light) if col[3] >= 16 else None, None))
    if not polys:
        return Image.new("RGBA", (8, 8))
    xs = [c[0] for p in polys for c in p[1]]
    ys = [c[1] for p in polys for c in p[1]]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    if size is not None:
        px = min((size[0] - 16) / max(1e-6, maxx - minx), (size[1] - 16) / max(1e-6, maxy - miny))
    W = int((maxx - minx) * px) + 16 if size is None else size[0]
    H = int((maxy - miny) * px) + 16 if size is None else size[1]
    offx = 8 - minx * px if size is None else (W - (maxx - minx) * px) / 2 - minx * px
    offy = 8 - miny * px if size is None else (H - (maxy - miny) * px) - 8 - miny * px
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    glow_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gdr = ImageDraw.Draw(glow_layer)
    for depth, corners, col, gcol in sorted(polys, key=lambda p: -p[0]):
        pts = [(c[0] * px + offx, c[1] * px + offy) for c in corners]
        if col is not None:
            dr.polygon(pts, fill=col[:3] + (255,))
        if gcol is not None:
            # the game draws the glow layer translucent: its alpha is how strongly it shines over the dark body
            a = gcol[3] / 255
            under = col[:3] if col is not None else (0, 0, 0)
            dr.polygon(pts, fill=tuple(int(under[k] * (1 - a) + gcol[k] * a) for k in range(3)) + (255,))
            gdr.polygon(pts, fill=gcol[:3] + (min(255, gcol[3]),))
    if night is not None:
        from PIL import ImageFilter
        halo = glow_layer.filter(ImageFilter.GaussianBlur(max(2, px * 0.6)))
        img = Image.alpha_composite(halo, img)
    return img


def composite(m, skin, glow, tint=None):
    """The three layers as the game draws them: (tinted vanilla or own) base, the skin cutout over it, glow on top."""
    from PIL import Image
    if m["model"] in OWN:
        base = skin.image()
    else:
        vb = Image.open(os.path.join(VANILLA_TEX, VANILLA[m["model"]] + ".png")).convert("RGBA")
        vb = vb.resize((skin.w, skin.h), Image.NEAREST)
        t = rgba(tint or m["tint"] or "#ffffff")
        vpx = vb.load()
        for y in range(vb.height):
            for x in range(vb.width):
                r, g, b, a = vpx[x, y]
                vpx[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
        sk = skin.image()
        base = Image.alpha_composite(vb, Image.composite(sk, Image.new("RGBA", sk.size), sk.split()[3].point(lambda a: 255 if a >= 128 else 0)))
    g = glow.image()
    lit = Image.alpha_composite(base, g.copy())
    return lit, base, g


# ---------------------------------------------------------------------------------------------------------------------
# Painting helpers (rects are (x, y, w, h) in texture pixels)

PAL = {
    "cream": ["#4e4030", "#7a6648", "#a8946c", "#cdbd96", "#e6d9b8", "#f7efda"],
    "fur": ["#3e404c", "#5a5e6e", "#7c8296", "#9ea4b8", "#bec3d2", "#d8dce6", "#eef0f5"],
    "bark": ["#140d07", "#24170c", "#382413", "#4e331b", "#674526", "#845a33"],
    "moss": ["#18260f", "#223617", "#2f4a20", "#3f622a", "#557d37", "#73a04a"],
    "leaf": ["#14301b", "#1d4426", "#2a5c33", "#3a7a42", "#52a058", "#7cc474"],
    "sand": ["#5e482a", "#7e6339", "#a3834f", "#c4a468", "#dcc084", "#f0dcaa"],
    "gold": ["#4a3408", "#7a5810", "#a87c1c", "#d4a636", "#f0cc60", "#fff0b0"],
    "bronze": ["#1e1408", "#2e2010", "#433018", "#5c4322", "#78592e"],
    "frost": ["#1c2c40", "#2e4864", "#4a6c8e", "#7298b8", "#a4c6de", "#d4eaf6", "#f2fbff"],
    "violet": ["#16131f", "#201c2c", "#2c263b", "#3b334e", "#4e4566", "#665b82"],
    "lilac": ["#5e5474", "#7a6e92", "#988caf", "#b6abc9"],
    "petal": ["#7a3a7e", "#a8589f", "#d088c2", "#eaa8d6", "#f6c8e6", "#ffe8f4"],
    "skin": ["#2e3324", "#434a33", "#5b6444", "#747e57", "#8f996c", "#adb688"],
    "ivory": ["#6e6450", "#918670", "#b6ac93", "#d6ceb6", "#ebe4d0", "#f8f4e8"],
    "brown": ["#2a1c10", "#3e2a18", "#573c22", "#71502e", "#8c663c", "#a8804e"],
    "teal": ["#0c3a3a", "#14605c", "#1f8a82", "#38b8aa", "#5fe8d8", "#b4fff4"],
}


def pal(name):
    return [rgba(h) for h in PAL[name]]


def fill(c, r, fn):
    x, y, w, h = r
    for j in range(h):
        for i in range(w):
            col = fn(i, j, w, h)
            if col is not None:
                c.set(x + i, y + j, col)


def noise(c, r, p, rng, scale=3, lo=0.15, hi=0.85, grad=None, jitter=0.14):
    """Value noise quantised onto palette p; grad(i, j, w, h) shifts the value (light from above, darker belly...)."""
    x, y, w, h = r
    if w <= 0 or h <= 0:
        return
    nz = value_noise(max(2, w), max(2, h), rng, scale=scale, octaves=2)
    for j in range(h):
        for i in range(w):
            val = lo + (hi - lo) * nz[j][i] + (grad(i, j, w, h) if grad else 0.0) + (rng.random() - 0.5) * jitter
            c.set(x + i, y + j, p[quantize(max(0.0, min(0.999, val)), len(p))])


def vgrad(top, bottom):
    """A vertical gradient offset: +top at the first row, +bottom at the last."""
    return lambda i, j, w, h: top + (bottom - top) * (j / max(1, h - 1))


def hgrad(left, right):
    return lambda i, j, w, h: left + (right - left) * (i / max(1, w - 1))


def streaks(c, r, col, rng, n, length=(2, 4), vertical=True):
    """Fur: short strokes."""
    x, y, w, h = r
    for _ in range(int(n)):
        sx, sy = x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h))
        for k in range(rng.randint(*length)):
            px_, py_ = (sx, sy + k) if vertical else (sx + k, sy)
            if x <= px_ < x + w and y <= py_ < y + h:
                c.set(px_, py_, col)


def dots(c, r, cols, rng, n):
    x, y, w, h = r
    for _ in range(int(n)):
        c.set(x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h)), rng.choice(cols))


def border(c, r, col, sides="lrtb"):
    x, y, w, h = r
    for i in range(w):
        if "t" in sides:
            c.set(x + i, y, col)
        if "b" in sides:
            c.set(x + i, y + h - 1, col)
    for j in range(h):
        if "l" in sides:
            c.set(x, y + j, col)
        if "r" in sides:
            c.set(x + w - 1, y + j, col)


def each_side(b):
    f = faces(b)
    return [f["west"], f["north"], f["east"], f["south"]]


def paint_box(c, b, rng, p, top=0.18, bottom=-0.22, scale=3, sides=None):
    """A whole box in one material: top faces lighter, bottom darker, sides shaded top to bottom."""
    f = faces(b)
    noise(c, f["top"], p, rng, scale, grad=lambda i, j, w, h: top)
    noise(c, f["bottom"], p, rng, scale, grad=lambda i, j, w, h: bottom)
    for name in ("west", "north", "east", "south"):
        noise(c, f[name], p, rng, scale, grad=sides or vgrad(top * 0.6, bottom * 0.6))


# ---------------------------------------------------------------------------------------------------------------------
# The creatures. Each painter returns (skin, glow) canvases at its model's texture size.


def paint_lumen_moth(m, rng):
    size, tree = _moth()
    skin, glow = Canvas(*size), Canvas(*size)
    cream, amber, brown = pal("cream"), ramp("amber"), pal("brown")
    bs = boxes_of(tree)
    thorax, abdomen, head, ant = bs[0], bs[1], bs[2], bs[3]
    fore, hind = bs[5], bs[7]
    # fuzzy white thorax with a warm collar
    paint_box(skin, thorax, rng, cream[2:], top=0.3, bottom=-0.1, scale=2)
    for r in each_side(thorax):
        fill(skin, r, lambda i, j, w, h: cream[3] if j == h - 1 else None)
    # banded abdomen: cream and warm brown rings
    fa = faces(abdomen)
    for name in ("west", "east"):
        fill(skin, fa[name], lambda i, j, w, h: (brown[3] if i % 2 == 0 else cream[4]) if j < h - 1 else brown[2])
    fill(skin, fa["top"], lambda i, j, w, h: brown[3] if j % 2 == 0 else cream[4])
    fill(skin, fa["bottom"], lambda i, j, w, h: cream[2] if j % 2 else brown[2])
    fill(skin, fa["north"], lambda i, j, w, h: cream[4])
    fill(skin, fa["south"], lambda i, j, w, h: brown[2] if (i + j) % 2 else brown[3])
    # head: dark with big black eyes
    paint_box(skin, head, rng, brown[1:4], scale=2)
    hx, hy, hw, hh = faces(head)["north"]
    for i in range(hw):
        skin.set(hx + i, hy, (16, 12, 10, 255))
        glow.set(hx + i, hy, (255, 214, 140, 140))
    # feathery golden antennae (planes, both sides)
    fx = faces(ant)
    for name in ("north", "south"):
        fill(skin, fx[name], lambda i, j, w, h: rgba("#e8c070") if (i == 0 or j % 2 == 0) else None)

    def wing(r, spot, band, big):
        # moonlit cream, a dusky band, an amber eyespot with a signal-blue pupil, a darker scalloped edge
        x, y, w, h = r
        base = [rgba("#b4a27c"), rgba("#d0c29a"), rgba("#e6dab8"), rgba("#f6efd8")]
        nz = value_noise(max(2, w), max(2, h), rng, scale=3, octaves=2)
        for j in range(h):
            for i in range(w):
                t = i / max(1, w - 1)
                v = 0.25 + 0.6 * t + (nz[j][i] - 0.5) * 0.3
                col = base[quantize(max(0, min(0.999, v)), len(base))]
                if i < 2:
                    col = brown[3] if (i + j) % 2 else brown[4]  # furry wing root
                if band and int(w * 0.34) <= i <= int(w * 0.34) + 1:
                    col = rgba("#7a6448") if (i + j) % 3 else rgba("#5e4a34")
                if (j == 0 or j == h - 1) and i > 1:
                    col = shade(col, 0.86)
                if i == w - 1:
                    col = rgba("#6a5a40") if j % 2 else rgba("#8c7c5c")
                skin.set(x + i, y + j, col)
        for k in range(1, h, 2):
            for i in range(1, w - 1):
                jj = int(round(k + (i / w) * (k - h / 2) * 0.4))
                if 0 <= jj < h and (i + k) % 3 != 0:
                    skin.set(x + i, y + jj, shade(skin.get(x + i, y + jj), 0.9))
        sx, sy = x + int(w * spot[0]), y + int(h * spot[1])
        rad = 2.1 if big else 1.4
        for j in range(h):
            for i in range(w):
                d = math.hypot((x + i) - sx, (y + j) - sy)
                if d <= rad + 0.6:
                    if d <= 0.6:
                        skin.set(x + i, y + j, rgba("#1d5591"))
                        glow.set(x + i, y + j, rgba("#9fe8ff"))
                    elif d <= rad - 0.3:
                        skin.set(x + i, y + j, rgba("#2a1c10"))
                    else:
                        skin.set(x + i, y + j, amber[4])
                        glow.set(x + i, y + j, rgba("#ffd27a", 235))
        for j in range(0, h, 2):
            glow.set(x + w - 1, y + j, rgba("#ffe9b0", 110))
    ff, fh = faces(fore), faces(hind)
    wing(ff["top"], (0.62, 0.5), True, True)
    wing(fh["top"], (0.55, 0.45), False, False)
    for r, spot in ((ff["bottom"], (0.38, 0.5)), (fh["bottom"], (0.45, 0.45))):
        noise(skin, r, [rgba("#d6caa6"), rgba("#e8ddbd"), rgba("#f6efd8")], rng, 3)
        border(skin, r, rgba("#a89878"), "r")
        # the eyespot shows through underneath, softer (the bottom face is mirrored left to right)
        x, y, w, h = r
        sx, sy = x + int(w * spot[0]), y + int(h * spot[1])
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            skin.set(sx + dx, sy + dy, amber[3])
            glow.set(sx + dx, sy + dy, rgba("#ffd27a", 170))
    # both sides of every wing give off a soft lamplight, brightest at the tips: at night a moth reads as a light
    for r in (ff["top"], fh["top"], ff["bottom"], fh["bottom"]):
        x, y, w, h = r
        for j in range(h):
            for i in range(w):
                c = skin.get(x + i, y + j)
                if c[3] and not glow.get(x + i, y + j)[3]:
                    t = i / max(1, w - 1)
                    glow.set(x + i, y + j, (c[0], c[1], max(0, c[2] - 40), int(190 + 65 * t)))
    # the lumen: the tail end of the abdomen glows like a firefly's, the thorax warmly
    fa = faces(abdomen)
    for name in ("west", "east", "top", "bottom"):
        x, y, w, h = fa[name]
        if name in ("top", "bottom"):
            for i in range(w):
                for j in range(2):
                    skin.set(x + i, y + j, rgba("#ffe9b0"))
                    glow.set(x + i, y + j, rgba("#ffd27a", 230))
        else:
            back = x if name == "west" else x + w - 1
            for j in range(h):
                for dx in ((0, 1) if name == "west" else (0, -1)):
                    skin.set(back + dx, y + j, rgba("#ffe9b0"))
                    glow.set(back + dx, y + j, rgba("#ffd27a", 230))
    fill(skin, fa["south"], lambda i, j, w, h: rgba("#fff2c8"))
    fill(glow, fa["south"], lambda i, j, w, h: rgba("#ffe9b0", 255))
    tx, ty, tw, th = faces(thorax)["top"]
    for i in range(tw):
        glow.set(tx + i, ty + 1, rgba("#ffe9b0", 140))
    return skin, glow


def paint_bloom_sprite(m, rng):
    size, tree = _allay()
    skin, glow = Canvas(*size), Canvas(*size)
    petal, leaf = pal("petal"), pal("leaf")
    bs = boxes_of(tree)
    head, body, skirt, arm_r, arm_l, wing = bs[0], bs[1], bs[2], bs[3], bs[4], bs[5]
    fh = faces(head)

    def petals(r):
        # overlapping petals: each column a petal tip at the top, darker towards its base, a crease between petals
        x, y, w, h = r
        for j in range(h):
            for i in range(w):
                tip = (i + 1) % 2 == 0
                v = 4 - j + (1 if tip else 0)
                col = petal[max(1, min(5, v))]
                if i % 2 == 1 and j > 1:
                    col = shade(col, 0.88)
                skin.set(x + i, y + j, col)
    # head: a rosebud, the petals folding over the top; the face sits in the open bloom
    x, y, w, h = fh["top"]
    for j in range(h):
        for i in range(w):
            d = max(abs(i - (w - 1) / 2), abs(j - (h - 1) / 2))
            skin.set(x + i, y + j, petal[5] if d < 0.6 else petal[4] if d < 1.6 else petal[3])
    skin.set(x + w // 2, y + h // 2, rgba("#f6e27a"))
    glow.set(x + w // 2, y + h // 2, rgba("#fff2b0", 200))
    for r in (fh["west"], fh["east"], fh["south"]):
        petals(r)
    noise(skin, fh["bottom"], leaf[3:], rng, 2)
    x, y, w, h = fh["north"]
    petals(fh["north"])
    fill(skin, (x + 1, y + 1, w - 2, h - 2), lambda i, j, ww, hh: rgba("#fff3f8"))
    for ex in (x + 1, x + 3):
        skin.set(ex, y + 2, rgba("#3a2040"))
        glow.set(ex, y + 2, rgba("#ffe2f2", 200))
    skin.set(x + 2, y + 3, rgba("#e89ad0"))
    # leafy dress and petal hem
    paint_box(skin, body, rng, leaf[2:], scale=2)
    paint_box(skin, skirt, rng, leaf[1:5], scale=2)
    for r in each_side(skirt):
        fill(skin, r, lambda i, j, w, h: petal[3] if j == h - 1 else (leaf[4] if (i + j) % 3 == 0 else None))
    for a in (arm_r, arm_l):
        paint_box(skin, a, rng, leaf[3:], scale=2)
    # petal wings: veined, almost white at the edge
    fw = faces(wing)
    for name in ("west", "east"):
        x, y, w, h = fw[name]
        for j in range(h):
            for i in range(w):
                t = i / max(1, w - 1)
                col = mix(rgba("#f6c6e6"), rgba("#fff6fb"), t)
                if j == h // 2 or (i % 3 == 0 and j in (1, h - 2)):
                    col = rgba("#e89ad0")
                    glow.set(x + i, y + j, rgba("#ffd0ec", 120))
                skin.set(x + i, y + j, col)
    return skin, glow


def paint_glass_scarab(m, rng):
    size, tree = _scarab()
    skin, glow = Canvas(*size), Canvas(*size)
    bronze, gold = pal("bronze"), pal("gold")
    bs = boxes_of(tree)
    body, shell, head, horn, leg = bs[0], bs[1], bs[2], bs[3], bs[4]
    paint_box(skin, body, rng, bronze, scale=2)
    fs = faces(shell)
    x, y, w, h = fs["top"]
    for j in range(h):
        for i in range(w):
            # glassy gold dome: bright at one shoulder, a dark seam down the middle, diagonal glints
            d = math.hypot((i - w * 0.35) / w, (j - h * 0.35) / h)
            v = 0.95 - d * 1.1
            col = gold[quantize(max(0, min(0.999, v)), len(gold))]
            if i == w // 2:
                col = gold[1]
            if (i + j) % 5 == 0 and v > 0.4:
                col = gold[5]
                glow.set(x + i, y + j, rgba("#fff4d0", 160))
            skin.set(x + i, y + j, col)
    for name in ("west", "north", "east", "south"):
        noise(skin, fs[name], gold[1:4], rng, 2, grad=vgrad(0.2, -0.2))
    noise(skin, fs["bottom"], bronze, rng, 2)
    paint_box(skin, head, rng, bronze[1:], scale=2)
    hx, hy, hw, hh = faces(head)["north"]
    for ex in (hx, hx + hw - 1):
        skin.set(ex, hy + 1, rgba("#f2a541"))
        glow.set(ex, hy + 1, rgba("#ffd28a"))
    paint_box(skin, horn, rng, gold[2:], scale=2)
    paint_box(skin, leg, rng, bronze[:3], scale=2)
    return skin, glow


def paint_frostfinch(m, rng):
    size, tree = _parrot()
    skin, glow = Canvas(*size), Canvas(*size)
    white = [rgba("#b8c8da"), rgba("#d4e2ee"), rgba("#eaf2fa"), rgba("#f8fbff")]
    ice = pal("frost")
    bs = boxes_of(tree)
    body, tail, wing, head, crest, beak1, beak2, plume, leg = bs[0], bs[1], bs[2], bs[4], bs[5], bs[6], bs[7], bs[8], bs[9]
    paint_box(skin, body, rng, white, scale=2)
    fill(skin, faces(body)["north"], lambda i, j, w, h: rgba("#fdfdff") if j < h - 2 else None)
    paint_box(skin, tail, rng, ice[2:5], scale=2)
    for r in each_side(tail):
        fill(skin, r, lambda i, j, w, h: ice[1] if j >= h - 1 else None)
    for r in faces(wing).values():
        fill(skin, r, lambda i, j, w, h: white[2] if j < h * 0.45 else (ice[3] if j < h - 1 else ice[1]))
    paint_box(skin, head, rng, white[1:], scale=2)
    hx, hy, hw, hh = faces(head)["west"]
    skin.set(hx + hw - 1, hy + 1, rgba("#10141c"))
    hx, hy, hw, hh = faces(head)["east"]
    skin.set(hx, hy + 1, rgba("#10141c"))
    # the crown is white like the head; only the plume above it is ice-blue, its tips lit
    paint_box(skin, crest, rng, white[1:], scale=2)
    for b in (beak1, beak2):
        paint_box(skin, b, rng, [rgba("#c47a1c"), rgba("#f2a541")], scale=2)
    fp = faces(plume)
    for name in ("west", "east"):
        x, y, w, h = fp[name]
        for j in range(h):
            for i in range(w):
                if j < 2 and i % 2 == 1:
                    continue  # a ragged, feathery top
                skin.set(x + i, y + j, rgba("#9fe8ff") if j < 2 else rgba("#6bd0f0") if j < 4 else white[2])
        fill(glow, fp[name], lambda i, j, w, h: rgba("#c8f4ff", 180) if j < 2 and i % 2 == 0 else None)
    paint_box(skin, leg, rng, [rgba("#8a7a80"), rgba("#a8969c")], scale=2)
    return skin, glow


def paint_mossback(m, rng):
    size, tree = _tortoise()
    skin, glow = Canvas(*size), Canvas(*size)
    moss, brown, skinp, leaf, ivory = pal("moss"), pal("brown"), pal("skin"), pal("leaf"), pal("ivory")
    bs = boxes_of(tree)
    base, dome, tuft, plant, head, leg, tail = bs[0], bs[1], bs[2], bs[4], bs[6], bs[7], bs[11]
    fb = faces(base)
    # lower shell: brown-olive plates with seams, moss dripping over the top edge
    for name in ("west", "north", "east", "south"):
        x, y, w, h = fb[name]
        noise(skin, fb[name], brown[1:5], rng, 3, grad=vgrad(0.15, -0.25))
        for i in range(0, w, 4):
            for j in range(h):
                skin.set(x + i, y + j, brown[0])
        for i in range(w):
            drip = 1 + (rng.random() < 0.45) + (rng.random() < 0.2)
            for j in range(drip):
                skin.set(x + i, y + j, moss[3 + (j == 0)])
            skin.set(x + i, y + h - 1, brown[0])
    noise(skin, fb["top"], moss[2:], rng, 3)
    x, y, w, h = fb["bottom"]
    noise(skin, fb["bottom"], ivory[1:4], rng, 3)
    for j in range(0, h, 4):
        for i in range(w):
            skin.set(x + i, y + j, ivory[0])
    # the dome: moss over plates whose seams glow faintly, a few flowers
    fd = faces(dome)
    x, y, w, h = fd["top"]
    noise(skin, fd["top"], moss[2:], rng, 3, grad=lambda i, j, w, h: 0.1)
    for j in range(h):
        for i in range(w):
            if j % 4 == 0 or (i + (j // 4) * 2) % 5 == 0:
                skin.set(x + i, y + j, moss[1])
                if rng.random() < 0.35:
                    glow.set(x + i, y + j, rgba("#9cff7a", 70))
    dots(skin, fd["top"], [rgba("#f6c6e6"), rgba("#e2df6a"), rgba("#ffffff")], rng, 6)
    for name in ("west", "north", "east", "south"):
        noise(skin, fd[name], moss[1:5], rng, 2, grad=vgrad(0.15, -0.15))
    noise(skin, fd["bottom"], brown[1:3], rng, 2)
    paint_box(skin, tuft, rng, leaf[3:], scale=2)
    # the garden on its back: a small fern
    fp = faces(plant)
    for name in ("north", "south"):
        x, y, w, h = fp[name]
        for j in range(h):
            for i in range(w):
                if i in (2, 3) and j >= 2:
                    skin.set(x + i, y + j, rgba("#5c4119"))
                elif abs(i - 2.5) <= (h - j) * 0.6 and (i + j) % 2 == 0 and j < h - 1:
                    skin.set(x + i, y + j, leaf[4] if j < 3 else leaf[3])
    # scaly olive skin; a calm eye
    paint_box(skin, head, rng, skinp, scale=2)
    fh = faces(head)
    for name in ("west", "east"):
        x, y, w, h = fh[name]
        ex = x + (w - 2 if name == "west" else 1)
        skin.set(ex, y + 1, rgba("#141210"))
        glow.set(ex, y + 1, rgba("#9cff7a", 90))
    x, y, w, h = fh["north"]
    for i in range(w):
        skin.set(x + i, y + h - 1, skinp[0])
    paint_box(skin, leg, rng, skinp, scale=2)
    for r in each_side(leg):
        fill(skin, r, lambda i, j, w, h: rgba("#2a2a22") if j == h - 1 and i % 2 == 0 else None)
    paint_box(skin, tail, rng, skinp, scale=2)
    return skin, glow


def _stag_paint(m, rng, coat, mane, dark, belly, antler, tip_glow, eye_glow, moss=None):
    size, tree = _stag()
    skin, glow = Canvas(*size), Canvas(*size)
    bs = boxes_of(tree)
    body, neck, head, snout, ear, beam, tine, crown = bs[0], bs[1], bs[2], bs[3], bs[4], bs[6], bs[7], bs[10]
    leg = [b for b in bs if b["u"] == 0 and b["v"] == 40][0]
    tail = [b for b in bs if b["u"] == 12 and b["v"] == 40][0]
    fb = faces(body)
    # back darker with a spine line, flanks fading to a pale belly, dapples high on the flank
    x, y, w, h = fb["top"]
    noise(skin, fb["top"], coat, rng, 3, grad=lambda i, j, w, h: -0.12)
    for j in range(h):
        skin.set(x + w // 2, y + j, dark)
        skin.set(x + w // 2 - 1, y + j, shade(dark, 1.15))
    noise(skin, fb["bottom"], belly, rng, 3)
    for name in ("west", "east"):
        x, y, w, h = fb[name]
        noise(skin, fb[name], coat, rng, 3, grad=vgrad(-0.15, 0.3))
        for j in range(h - 2, h):
            for i in range(w):
                skin.set(x + i, y + j, belly[1 + (i + j) % 2])
        dots(skin, (x + 2, y + 1, w - 4, 3), [mix(coat[-1], (255, 255, 255, 255), 0.5)], rng, 7)
    noise(skin, fb["north"], belly, rng, 2)
    noise(skin, fb["south"], belly, rng, 2)
    # shaggy neck
    for r in faces(neck).values():
        noise(skin, r, mane, rng, 2, grad=vgrad(0.1, -0.1))
        streaks(skin, r, mane[-1], rng, r[2] * r[3] / 5, (2, 4))
    # head, muzzle, eyes
    paint_box(skin, head, rng, coat[1:], scale=2)
    fh = faces(head)
    for name in ("west", "east"):
        x, y, w, h = fh[name]
        ex = x + w - 2 if name == "west" else x + 1
        skin.set(ex, y + 1, (12, 12, 16, 255))
        glow.set(ex, y + 1, eye_glow)
    paint_box(skin, snout, rng, [shade(dark, 0.8), dark, shade(dark, 1.2)], scale=2)
    x, y, w, h = faces(snout)["north"]
    skin.set(x + 1, y + 1, (10, 10, 12, 255))
    paint_box(skin, ear, rng, coat[2:], scale=2)
    fill(skin, faces(ear)["north"], lambda i, j, w, h: rgba("#d8a0a8") if 0 < i < w - 1 else None)
    # antlers: pale bone darkening to the base, tips cold and glowing (side faces run tip -> base downwards)
    for b in (beam, tine, crown):
        for name, r in faces(b).items():
            x, y, w, h = r
            if name in ("top", "bottom"):
                fill(skin, r, lambda i, j, w, h: antler[0])
                if name == "top":
                    fill(glow, r, lambda i, j, w, h: tip_glow)
                continue
            for j in range(h):
                for i in range(w):
                    skin.set(x + i, y + j, antler[min(len(antler) - 1, int(j / max(1, h - 1) * (len(antler) - 1)))])
            for i in range(w):
                skin.set(x + i, y, tip_glow[:3] + (255,))
                glow.set(x + i, y, tip_glow)
    # legs: coat above, dark below, hooves
    for name, r in faces(leg).items():
        x, y, w, h = r
        noise(skin, r, coat[:-1], rng, 2, grad=vgrad(0.1, -0.35))
        if name in ("west", "north", "east", "south"):
            for j in range(h - 2, h):
                for i in range(w):
                    skin.set(x + i, y + j, (26, 24, 26, 255))
    fill(skin, faces(leg)["bottom"], lambda i, j, w, h: (20, 18, 20, 255))
    paint_box(skin, tail, rng, belly, scale=2)
    if moss is not None:
        # moss and lichen on the back and shoulders, hanging from the neck
        for r in (fb["top"], faces(neck)["south"], faces(neck)["top"]):
            x, y, w, h = r
            n = value_noise(max(2, w), max(2, h), rng, scale=3, octaves=2)
            for j in range(h):
                for i in range(w):
                    if n[j][i] > 0.52:
                        skin.set(x + i, y + j, moss[2 + int((n[j][i] - 0.52) * 8) % 3])
        for name in ("west", "east"):
            x, y, w, h = fb[name]
            for i in range(w):
                if rng.random() < 0.5:
                    for j in range(rng.randint(1, 3)):
                        skin.set(x + i, y + j, moss[3])
        for r in each_side(neck):
            x, y, w, h = r
            for i in range(w):
                if rng.random() < 0.6:
                    for j in range(h - rng.randint(1, 4), h):
                        skin.set(x + i, y + j, moss[2 + (j % 2)])
    return skin, glow


def paint_frost_stag(m, rng):
    fur = pal("fur")
    return _stag_paint(m, rng, fur[1:], fur[3:], rgba("#5a5e6e"), fur[4:], pal("ivory")[2:][::-1], rgba("#9fe8ff", 230), rgba("#9fe8ff", 200))


def paint_old_antler(m, rng):
    skin, glow = _stag_paint(m, rng, pal("bark"), pal("bark")[1:], rgba("#140d07"), pal("brown")[1:4], pal("bark")[2:][::-1],
                             rgba("#7fc3ff", 255), rgba("#7fc3ff", 255), moss=pal("moss"))
    # runes in the antlers and on the brow, lit from within
    size, tree = _stag()
    bs = boxes_of(tree)
    for b in (bs[6], bs[7]):
        for r in each_side(b):
            x, y, w, h = r
            for j in range(1, h, 2):
                glow.set(x + (j // 2) % max(1, w), y + j, rgba("#7fc3ff", 200))
    x, y, w, h = faces(bs[2])["north"]
    for i in range(1, w - 1):
        glow.set(x + i, y + 1, rgba("#7fc3ff", 150 if i % 2 else 220))
    return skin, glow


def paint_dune_ray(m, rng):
    size, tree = _ray()
    skin, glow = Canvas(*size), Canvas(*size)
    sand, teal = pal("sand"), pal("teal")
    belly = [rgba("#e6d0a0"), rgba("#f2e2bc"), rgba("#faf0d8")]
    bs = boxes_of(tree)
    body, lobe, wing, tip = bs[0], bs[1], bs[3], bs[4]
    tail1, tail2, fin = bs[7], bs[8], bs[9]

    def top_pattern(r, edge):
        x, y, w, h = r
        noise(skin, r, sand[1:], rng, 3, grad=lambda i, j, w, h: 0.05)
        for _ in range(max(2, w * h // 14)):
            sx, sy = x + rng.randrange(w), y + rng.randrange(h)
            skin.set(sx, sy, sand[1])
            if rng.random() < 0.3:
                skin.set(sx, sy, teal[3])
                glow.set(sx, sy, rgba("#5fe8d8", 180))
        if edge:
            for j in range(h):
                skin.set(x + w - 1, y + j, teal[2])
                glow.set(x + w - 1, y + j, rgba("#5fe8d8", 150 if j % 2 else 210))
    fb = faces(body)
    top_pattern(fb["top"], False)
    x, y, w, h = fb["top"]
    for j in range(h):
        glow.set(x + w // 2, y + j, rgba("#5fe8d8", 120))
        skin.set(x + w // 2, y + j, teal[2])
    # eyes on top, at the head end (front rows of the top face)
    for ex in (x + 1, x + w - 2):
        skin.set(ex, y + h - 2, (16, 14, 10, 255))
        glow.set(ex, y + h - 2, rgba("#b4fff4", 200))
    noise(skin, fb["bottom"], belly, rng, 3)
    for name in ("west", "north", "east", "south"):
        noise(skin, fb[name], sand[1:4], rng, 2)
    paint_box(skin, lobe, rng, sand[2:], scale=2)
    fw, ft = faces(wing), faces(tip)
    top_pattern(fw["top"], False)
    top_pattern(ft["top"], True)
    for r in (fw["bottom"], ft["bottom"]):
        noise(skin, r, belly, rng, 3)
    for b in (wing, tip):
        for name in ("west", "north", "east", "south"):
            noise(skin, faces(b)[name], sand[1:3], rng, 2)
    for b in (tail1, tail2):
        paint_box(skin, b, rng, pal("brown")[1:4], scale=2)
        x, y, w, h = faces(b)["top"]
        for j in range(0, h, 3):
            glow.set(x, y + j, rgba("#5fe8d8", 200))
            skin.set(x, y + j, teal[3])
    ff = faces(fin)
    for name in ("west", "east"):
        fill(skin, ff[name], lambda i, j, w, h: teal[2] if (i + j) % 2 else teal[3])
        fill(glow, ff[name], lambda i, j, w, h: rgba("#5fe8d8", 90))
    return skin, glow


def paint_gloomstalker(m, rng):
    size, tree = _fox()
    skin, glow = Canvas(*size), Canvas(*size)
    vio = pal("violet")
    bs = boxes_of(tree)
    head, ear_r, ear_l, nose, body, tail = bs[0], bs[1], bs[2], bs[3], bs[4], bs[5]
    leg_l = [b for b in bs if b["u"] == 4 and b["v"] == 24][0]
    leg_r = [b for b in bs if b["u"] == 13 and b["v"] == 24][0]
    lilac = pal("lilac")
    thorn = rgba("#cbb8dc")
    # head: charcoal-violet, a paler muzzle and cheeks, eyes that burn
    paint_box(skin, head, rng, vio[2:], scale=2)
    fh = faces(head)
    x, y, w, h = fh["north"]
    noise(skin, fh["north"], vio[3:], rng, 2)
    fill(skin, fh["north"], lambda i, j, w, h: lilac[1] if j >= h - 2 and 1 < i < w - 2 else None)
    for ex, inner in ((x + 1, x + 2), (x + w - 2, x + w - 3)):
        for px_, col in ((ex, "#f0e0ff"), (inner, "#c08cff")):
            skin.set(px_, y + 2, rgba(col))
            glow.set(px_, y + 2, rgba(col))
    for name in ("west", "east"):
        fill(skin, fh[name], lambda i, j, w, h: lilac[0] if j >= h - 2 else None)
    for e in (ear_r, ear_l):
        paint_box(skin, e, rng, vio[3:], scale=2)
        fill(skin, faces(e)["north"], lambda i, j, w, h: rgba("#6a4a8a"))
    paint_box(skin, nose, rng, lilac[:3], scale=2)
    x, y, w, h = faces(nose)["north"]
    skin.set(x + 1, y, (10, 6, 14, 255))
    skin.set(x + 2, y, (10, 6, 14, 255))
    # body (rotated a quarter turn: the south face is the back, north the belly): bramble thorns down the spine
    fb = faces(body)
    for name in ("west", "east"):
        noise(skin, fb[name], vio, rng, 3, grad=hgrad(0.0, 0.0))
        streaks(skin, fb[name], vio[-1], rng, fb[name][2] * fb[name][3] / 5, (2, 4))
        x, y, w, h = fb[name]
        # paler belly edge along the underside column
        edge = x if name == "west" else x + w - 1
        for j in range(h):
            skin.set(edge, y + j, lilac[0])
    for name in ("top", "bottom"):
        noise(skin, fb[name], vio[2:], rng, 2)
    x, y, w, h = fb["south"]
    noise(skin, fb["south"], vio[:4], rng, 3)
    for j in range(0, h, 2):
        skin.set(x + w // 2, y + j, thorn)
        skin.set(x + w // 2 - 1, y + j + 1, rgba("#8e78a4"))
        if j % 4 == 0:
            glow.set(x + w // 2, y + j, rgba("#c08cff", 90))
    noise(skin, fb["north"], lilac[:3], rng, 3)
    # tail: a dark brush, only its last tuft pale and faintly lit
    ft = faces(tail)
    for name, r in ft.items():
        x, y, w, h = r
        noise(skin, r, vio[1:], rng, 2)
        streaks(skin, r, vio[-1], rng, w * h / 5)
        if name in ("west", "north", "east", "south"):
            for i in range(w):
                skin.set(x + i, y + h - 1, lilac[2])
                skin.set(x + i, y + h - 2, lilac[1] if i % 2 else vio[-1])
                glow.set(x + i, y + h - 1, rgba("#c08cff", 120))
    fill(skin, ft["bottom"], lambda i, j, w, h: lilac[2])
    fill(glow, ft["bottom"], lambda i, j, w, h: rgba("#d8b8ff", 140))
    for lg in (leg_l, leg_r):
        paint_box(skin, lg, rng, vio[:4], scale=2)
        for r in each_side(lg):
            fill(skin, r, lambda i, j, w, h: (12, 10, 16, 255) if j == h - 1 else None)
    return skin, glow


def paint_wisp_hound(m, rng):
    size, tree = _wolf()
    skin, glow = Canvas(*size), Canvas(*size)
    fr = pal("frost")
    spirit = [rgba("#8fb8d8"), rgba("#b4d4ea"), rgba("#d4eaf6"), rgba("#eef8ff")]
    bs = boxes_of(tree)
    head, ear, snout, body, mane, leg, tail = bs[0], bs[1], bs[3], bs[4], bs[5], bs[6], bs[10]
    paint_box(skin, head, rng, spirit, scale=2)
    x, y, w, h = faces(head)["north"]
    for ex in (x + 1, x + w - 2):
        skin.set(ex, y + 2, rgba("#9fe8ff"))
        glow.set(ex, y + 2, rgba("#c8f8ff"))
    fill(skin, (x, y, w, 1), lambda i, j, ww, hh: fr[3])
    paint_box(skin, ear, rng, [fr[3], fr[4]], scale=2)
    paint_box(skin, snout, rng, spirit[1:], scale=2)
    x, y, w, h = faces(snout)["north"]
    skin.set(x + 1, y, fr[0])
    for b in (body, mane):
        f = faces(b)
        for name in ("west", "east", "top", "bottom", "north"):
            noise(skin, f[name], spirit, rng, 3)
            streaks(skin, f[name], spirit[-1], rng, f[name][2] * f[name][3] / 6)
            # the brightest wisps shine faintly: a hound you can see coming in the dark
            fx, fy, fw, fh = f[name]
            for j in range(fh):
                for i in range(fw):
                    if skin.get(fx + i, fy + j) == spirit[-1]:
                        glow.set(fx + i, fy + j, rgba("#c8f8ff", 60))
        x, y, w, h = f["south"]
        noise(skin, f["south"], fr[1:4], rng, 3)
        # frost runes down the back
        for j in range(1, h, 3):
            for i in range(1, w - 1):
                if (i + j) % 3 == 0:
                    glow.set(x + i, y + j, rgba("#9fe8ff", 170))
                    skin.set(x + i, y + j, fr[4])
    paint_box(skin, leg, rng, spirit[:3], scale=2)
    for r in each_side(leg):
        x, y, w, h = r
        glow.set(x + w // 2, y + h - 2, rgba("#9fe8ff", 140))
    paint_box(skin, tail, rng, spirit, scale=2)
    for r in each_side(tail):
        x, y, w, h = r
        for j in range(h - 3, h):
            for i in range(w):
                glow.set(x + i, y + j, rgba("#c8f8ff", 160))
    return skin, glow


def paint_briar_sow(m, rng):
    size, tree = _hoglin()
    skin, glow = Canvas(*size), Canvas(*size)
    moss, brown, leaf, ivory = pal("moss"), pal("brown"), pal("leaf"), pal("ivory")
    hide = [rgba("#2e2214"), rgba("#3e2e1a"), rgba("#523c22"), rgba("#664c2c"), rgba("#7a5e38"), rgba("#8e7046")]
    bs = boxes_of(tree)
    body, mane, head, ear_r, ear_l, horn_r, horn_l = bs[0], bs[1], bs[2], bs[3], bs[4], bs[5], bs[6]
    legs = bs[7:11]
    fb = faces(body)

    def bramble(r, density=1.0, flowers=True, moss_top=0.3):
        # a boar's brown hide; moss grows down from the top of each face, brambles wind through it
        x, y, w, h = r
        noise(skin, r, hide, rng, 4, grad=vgrad(0.1, -0.15), jitter=0.08)
        n = value_noise(max(2, w), max(2, h), rng, scale=5, octaves=2)
        for j in range(h):
            for i in range(w):
                v = n[j][i] + moss_top * (1.0 - j / max(1, h - 1)) - 0.15
                if v > 0.5:
                    skin.set(x + i, y + j, moss[3 + min(2, int((v - 0.5) * 8))])
        for _ in range(int(w * h / 40 * density)):
            sx, sy = x + rng.randrange(w), y + rng.randrange(h)
            for k in range(rng.randint(4, 9)):
                skin.set(sx, sy, rgba("#1e2a12"))
                if rng.random() < 0.25:
                    skin.set(sx + 1, sy, ivory[3])
                sx += rng.choice((-1, 0, 1))
                sy += rng.choice((-1, 1))
                if not (x <= sx < x + w and y <= sy < y + h):
                    break
        if flowers:
            for _ in range(int(w * h / 90)):
                fx_, fy_ = x + rng.randrange(w), y + rng.randrange(h)
                col = rng.choice([rgba("#f0a8d8"), rgba("#f6e27a"), rgba("#fff6fb")])
                skin.set(fx_, fy_, col)
                if rng.random() < 0.4:
                    glow.set(fx_, fy_, col[:3] + (110,))
    bramble(fb["top"], 1.0, moss_top=0.45)
    for name in ("west", "east", "south", "north"):
        bramble(fb[name], 0.6, flowers=name != "north", moss_top=0.35)
    noise(skin, fb["bottom"], hide[:3], rng, 4)
    # the hedge on her back: leaves and thorns with gaps
    fmn = faces(mane)
    for name in ("west", "east"):
        x, y, w, h = fmn[name]
        n = value_noise(max(2, w), max(2, h), rng, scale=4, octaves=2)
        for j in range(h):
            for i in range(w):
                if n[j][i] + (j / h) * 0.5 > 0.55:
                    skin.set(x + i, y + j, ivory[4] if rng.random() < 0.08 else leaf[2 + int(n[j][i] * 4) % 4])
    # head: dark snout, small green eyes, mossy brow
    fh = faces(head)
    for name in ("top", "bottom", "west", "east", "south"):
        bramble(fh[name], 0.3, flowers=False, moss_top=0.1)
    x, y, w, h = fh["north"]
    noise(skin, fh["north"], brown[:3], rng, 2)
    skin.set(x + 4, y + 2, (8, 6, 4, 255))
    skin.set(x + w - 5, y + 2, (8, 6, 4, 255))
    for name in ("west", "east"):
        x, y, w, h = fh[name]
        ex = x + w - 4 if name == "west" else x + 3
        skin.set(ex, y + 1, rgba("#9cff7a"))
        glow.set(ex, y + 1, rgba("#c8ffb0"))
    for e in (ear_r, ear_l):
        paint_box(skin, e, rng, hide[1:4], scale=2)
    # tusks: ivory with glowing green tips
    for t in (horn_r, horn_l):
        for name, r in faces(t).items():
            x, y, w, h = r
            fill(skin, r, lambda i, j, w, h: ivory[3 + (i % 2)] if j > 1 else rgba("#d8ffc8"))
            if name in ("west", "north", "east", "south"):
                for i in range(w):
                    glow.set(x + i, y, rgba("#9cff7a", 230))
                    glow.set(x + i, y + 1, rgba("#9cff7a", 140))
            if name == "top":
                fill(glow, r, lambda i, j, w, h: rgba("#9cff7a", 230))
    for lg in legs:
        f = faces(lg)
        for name in ("west", "north", "east", "south"):
            x, y, w, h = f[name]
            noise(skin, f[name], hide[:4], rng, 3, grad=vgrad(0.15, -0.3))
            for i in range(w):
                for j in range(min(3, h)):
                    if rng.random() < 0.6:
                        skin.set(x + i, y + j, moss[3])
                skin.set(x + i, y + h - 1, (14, 10, 6, 255))
                skin.set(x + i, y + h - 2, (24, 18, 10, 255))
        noise(skin, f["top"], moss[2:], rng, 2)
        fill(skin, f["bottom"], lambda i, j, w, h: (14, 10, 6, 255))
    return skin, glow


def paint_dune_wyrm(m, rng):
    size, tree = _serpent()
    skin, glow = Canvas(*size), Canvas(*size)
    sand, amber = pal("sand"), ramp("amber")
    belly = [rgba("#e6cc98"), rgba("#f2dcaa"), rgba("#fbeccc")]
    bs = boxes_of(tree)
    for b in bs:
        f = faces(b)
        if b["w"] == 1:
            # the fins along its head and tail
            for r in f.values():
                fill(skin, r, lambda i, j, w, h: amber[3] if (i + j) % 2 else amber[4])
                fill(glow, r, lambda i, j, w, h: rgba("#ffb347", 110) if j == 0 else None)
            continue
        for name, r in f.items():
            x, y, w, h = r
            if name == "bottom":
                noise(skin, r, belly, rng, 2)
                for j in range(0, h, 2):
                    for i in range(w):
                        skin.set(x + i, y + j, rgba("#d4b880"))
                continue
            noise(skin, r, sand[1:], rng, 3, grad=(lambda i, j, w, h: 0.12) if name == "top" else vgrad(0.1, -0.2))
            for j in range(h):
                for i in range(w):
                    if (i + (j % 2)) % 2 == 0 and rng.random() < 0.35:
                        skin.set(x + i, y + j, sand[1])
            if name == "top":
                # glowing sunfire cracks down the spine, dark bands across
                cx = x + w // 2
                for j in range(h):
                    off = int(round(math.sin(j * 0.9 + b["u"]) * 1.0))
                    skin.set(cx + off, y + j, amber[2])
                    glow.set(cx + off, y + j, rgba("#ffb347", 200 if j % 3 else 255))
                for j in range(0, h, 4):
                    for i in range(w):
                        skin.set(x + i, y + j, sand[0])
            if name in ("west", "east"):
                for i in range(0, w, 4):
                    for j in range(h):
                        skin.set(x + i, y + j, sand[0])
    # head: darker, burnished, a lit brow ridge, amber eyes and a dark mouth line
    fh = faces(bs[0])
    burnish = [rgba("#4a2c0c"), rgba("#6a4214"), rgba("#8a5a1c"), rgba("#a87428"), rgba("#c89040")]
    for name in ("top", "west", "east", "south", "north"):
        noise(skin, fh[name], burnish, rng, 2, grad=(lambda i, j, w, h: 0.1) if name == "top" else vgrad(0.15, -0.2))
    x, y, w, h = fh["top"]
    for i in range(1, w - 1):
        skin.set(x + i, y + h - 2, amber[4])
        glow.set(x + i, y + h - 2, rgba("#ffb347", 220))
    for j in range(h):
        skin.set(x + w // 2, y + j, amber[3])
        glow.set(x + w // 2, y + j, rgba("#ffb347", 160))
    for name in ("west", "east"):
        x, y, w, h = fh[name]
        ex = x + w - 3 if name == "west" else x + 2
        for dx in (0, 1):
            skin.set(ex + dx, y + 1, amber[5])
            glow.set(ex + dx, y + 1, rgba("#ffe0a0"))
    x, y, w, h = fh["north"]
    for i in range(w):
        skin.set(x + i, y + h - 1, (40, 24, 10, 255))
    fill(skin, faces(bs[2])["top"], lambda i, j, w, h: rgba("#5a2010") if 0 < i < w - 1 else rgba("#f6ecd0"))
    return skin, glow


PAINTERS = {
    "lumen_moth": paint_lumen_moth, "bloom_sprite": paint_bloom_sprite, "mossback": paint_mossback,
    "gloomstalker": paint_gloomstalker, "briar_sow": paint_briar_sow, "glass_scarab": paint_glass_scarab,
    "dune_ray": paint_dune_ray, "dune_wyrm": paint_dune_wyrm, "frostfinch": paint_frostfinch,
    "frost_stag": paint_frost_stag, "wisp_hound": paint_wisp_hound, "old_antler": paint_old_antler,
}

# Egg colours: (shell, spots)
EGG = {
    "lumen_moth": ("#e6d9b8", "#ffd27a"), "bloom_sprite": ("#f6c8e6", "#52a058"), "mossback": ("#3f622a", "#8c663c"),
    "gloomstalker": ("#251c36", "#c08cff"), "briar_sow": ("#4a4228", "#9cff7a"), "glass_scarab": ("#d4a636", "#fff4d0"),
    "dune_ray": ("#c4a468", "#5fe8d8"), "dune_wyrm": ("#a3834f", "#ffb347"), "frostfinch": ("#eef6ff", "#6bd0f0"),
    "frost_stag": ("#9ea4b8", "#eef0f5"), "wisp_hound": ("#b4d4ea", "#2e4864"), "old_antler": ("#382413", "#7fc3ff"),
}


def egg(mid, beast, rng):
    shell, spot = rgba(EGG[mid][0]), rgba(EGG[mid][1])
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.8) / 6.6
            if dy < 0:
                dx *= 1.0 + (-dy) * 0.18
            d = math.hypot(dx, dy)
            if d <= 1.0:
                light = ((x - 7.5) * -0.6 + (y - 8.8) * -0.8) / 7.0
                c.set(x, y, shade(shell, 0.78 + max(-0.2, min(0.35, light * 0.5 - d * 0.15))))
    for _ in range(6 if beast else 4):
        x, y = rng.randrange(4, 12), rng.randrange(4, 14)
        if c.get(x, y)[3]:
            c.set(x, y, spot)
            if c.get(x + 1, y)[3]:
                c.set(x + 1, y, shade(spot, 0.85))
    if beast:
        gold = ramp("brass")
        for x in (5, 7, 9):
            c.set(x, 1, gold[5])
        for x in range(5, 11):
            c.set(x, 2, gold[4])
    c.set(5, 5, (255, 255, 255, 200))
    return _outline(c)


def part_icon(p):
    pid = p["id"]
    if "tusk" in pid:
        return polygon([(3, 14), (5, 14), (13, 4), (12, 1), (9, 3), (4, 11)], "bone")
    if "scale" in pid:
        return polygon([(8, 1), (13, 5), (12, 11), (8, 15), (4, 11), (3, 5)], "sand")
    if "tine" in pid:
        c = polygon([(4, 15), (6, 15), (7, 9), (12, 2), (10, 1), (6, 6), (5, 2), (3, 3), (4, 8)], "bone")
        for (x, y) in ((11, 2), (4, 3), (10, 1)):
            c.set(x, y, rgba("#7fc3ff"))
        return c
    return shard(p["ramp"])


def gui_textures(out):
    """The awakening pulse: a soft signal-blue vignette and one ring (both drawn stretched to the screen)."""
    n = 64
    v, ring = Canvas(n, n), Canvas(n, n)
    for y in range(n):
        for x in range(n):
            d = math.hypot((x + 0.5 - n / 2) / (n / 2), (y + 0.5 - n / 2) / (n / 2))
            t = max(0.0, min(1.0, (d - 0.45) / 0.75))
            a = int(255 * (t * t * (3 - 2 * t)))
            col = mix(rgba("#c3e6ff"), rgba("#3c86cc"), t)
            v.set(x, y, col[:3] + (a,))
            rr = abs(d - 0.86)
            ra = int(255 * max(0.0, 1.0 - rr / 0.06) ** 1.5)
            if ra > 0:
                ring.set(x, y, rgba("#e6f4ff")[:3] + (ra,))
    out.gui("fauna_pulse", v)
    out.gui("fauna_ring", ring)


def build(out):
    for m in fauna():
        rng = rng_for("fauna", m["id"])
        if m["model"] in MODELS:  # hero bodies (the named beasts) are painted by heroes.py
            skin, glow = PAINTERS[m["id"]](m, rng)
            out.entity("mobs/" + m["id"], skin)
            out.entity("mobs/" + m["id"] + "_glow", glow)
        out.item(m["id"] + "_spawn_egg", egg(m["id"], m["role"] == "BEAST", rng_for("fauna_egg", m["id"])))
        for p in m["parts"]:
            out.item(p["id"], part_icon(p))
    gui_textures(out)


# ---------------------------------------------------------------------------------------------------------------------
# Content: biome modifiers, loot tables, item models, lang. Weights are relative inside the fauna category of a biome.

# Spawners per modifier: data/awaken/neoforge/biome_modifier/<name>.json, spawning in the biomes of the tag
# #awaken:fauna/<group> (the name without "fauna_"). Which biomes those are is the biome table's: tools/terrain/biomes.py.
SPAWNS = {
    "fauna_greenwild": [("lumen_moth", 30, 2, 4), ("bloom_sprite", 20, 1, 2), ("mossback", 14, 1, 2), ("gloomstalker", 22, 1, 2), ("briar_sow", 1, 1, 1)],
    "fauna_greenwild_woods": [("frost_stag", 8, 1, 2), ("frostfinch", 8, 2, 4)],
    "fauna_sunscorch": [("glass_scarab", 30, 2, 3), ("dune_ray", 22, 1, 2), ("gloomstalker", 20, 1, 2), ("dune_wyrm", 1, 1, 1)],
    "fauna_frostwild": [("frostfinch", 26, 3, 5), ("frost_stag", 22, 1, 3), ("wisp_hound", 22, 2, 3), ("old_antler", 1, 1, 1)],
    "fauna_frostwild_taiga": [("lumen_moth", 14, 2, 3)],
    "fauna_caves": [("gloomstalker", 10, 1, 2), ("wisp_hound", 10, 2, 2)],
}

# Materials: (item, min, max, chance, looting). A chance below 1 is a killed-by-player roll.
DROPS = {
    "lumen_moth": [("minecraft:glowstone_dust", 1, 1, 0.5, True)],
    "bloom_sprite": [("minecraft:bone_meal", 1, 3, 1.0, True), ("minecraft:pink_petals", 1, 1, 0.3, False)],
    "mossback": [("minecraft:moss_block", 1, 2, 1.0, True), ("minecraft:turtle_scute", 1, 1, 0.3, False)],
    "gloomstalker": [("minecraft:leather", 0, 2, 1.0, True), ("minecraft:string", 0, 2, 1.0, True)],
    "briar_sow": [("minecraft:porkchop", 2, 4, 1.0, True), ("minecraft:leather", 2, 4, 1.0, True), ("minecraft:sweet_berries", 2, 5, 1.0, False)],
    "glass_scarab": [("minecraft:quartz", 1, 2, 1.0, True)],
    "dune_ray": [("minecraft:phantom_membrane", 1, 1, 0.6, True), ("minecraft:leather", 0, 1, 1.0, True)],
    "dune_wyrm": [("minecraft:quartz", 3, 6, 1.0, True), ("minecraft:gold_nugget", 3, 8, 1.0, True)],
    "frostfinch": [("minecraft:feather", 1, 2, 1.0, True)],
    "frost_stag": [("minecraft:leather", 1, 2, 1.0, True), ("minecraft:mutton", 1, 2, 1.0, True)],
    "wisp_hound": [("minecraft:bone", 1, 2, 1.0, True), ("minecraft:snowball", 0, 2, 1.0, False)],
    "old_antler": [("minecraft:leather", 3, 5, 1.0, True), ("minecraft:bone", 2, 4, 1.0, True)],
}
# Essence: count = floor(base + per_level * contentLevel + random) (the mobs module's awaken:mob_level_count).
ESSENCE = {"AMBIENT": None, "NEUTRAL": (0.3, 0.03, 8, 0.6), "HUNTER": (0.5, 0.04, 12, 1.0), "BEAST": (3.0, 0.15, 32, 1.0)}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def looting(minv=0.0, maxv=1.0):
    return {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": minv, "max": maxv},
            "enchantment": "minecraft:looting"}


def loot_table(m):
    """26.3 loot format: entries carry a 'modifier' list, pools a single 'condition' (all_of for several)."""
    killed = {"type": "minecraft:killed_by_player"}
    pools = []
    for item, lo, hi, chance, loot in DROPS[m["id"]]:
        mods = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        if loot:
            mods.append(looting())
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": mods}]}
        if chance < 1.0:
            pool["condition"] = {"type": "minecraft:all_of", "terms": [killed, {
                "type": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting", "unenchanted_chance": chance,
                "enchanted_chance": {"type": "minecraft:linear", "base": min(1.0, chance + 0.05), "per_level_above_first": 0.05}}]}
        pools.append(pool)
    ess = ESSENCE[m["role"]]
    if ess is not None:
        base, per, mx, chance = ess
        pool = {"rolls": 1, "condition": killed, "entries": [{"type": "minecraft:item", "name": "awaken:essence_shard",
                                                              "modifier": [{"type": "awaken:mob_level_count", "base": base, "per_level": per, "max": mx}]}]}
        if chance < 1.0:
            pool["condition"] = {"type": "minecraft:all_of", "terms": [killed, {"type": "minecraft:random_chance", "chance": chance}]}
        pools.append(pool)
    for p in m["parts"]:
        pools.append({"rolls": 1, "condition": killed, "entries": [{"type": "minecraft:item", "name": "awaken:" + p["id"],
                                                                    "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]}]})
    return {"type": "minecraft:entity", "pools": pools, "random_sequence": "awaken:entities/" + m["id"]}


def item_model(name):
    write_json(os.path.join(RES, "assets/awaken/items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})
    write_json(os.path.join(RES, "assets/awaken/models/item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + name}})


LANG = {
    "gamerule.awaken.fauna_spawning": "Spawn the Awakened layer's creatures",
    "gamerule.awaken.fauna_spawning.description": "Overworld creatures of AWAKEN (moths, sprites, stags, night hunters, named beasts) spawn naturally.",
}


def spawn_modifier(name, spawners):
    """One add_spawns modifier; its biomes are the biome table's tag #awaken:fauna/<group> (tools/terrain/biomes.py)."""
    write_json(os.path.join(RES, "data/awaken/neoforge/biome_modifier", name + ".json"), {
        "type": "neoforge:add_spawns", "biomes": "#awaken:fauna/" + name[len("fauna_"):],
        "spawners": [{"type": "awaken:" + i, "weight": w, "count": {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}}
                     for i, w, lo, hi in spawners]})


def content():
    ms = fauna()
    for name, spawners in SPAWNS.items():
        spawn_modifier(name, spawners)
    lang = dict(LANG)
    for m in ms:
        write_json(os.path.join(RES, "data/awaken/loot_table/entities", m["id"] + ".json"), loot_table(m))
        egg_id = m["id"] + "_spawn_egg"
        item_model(egg_id)
        lang["entity.awaken." + m["id"]] = m["name"]
        lang["item.awaken." + egg_id] = m["name"] + " Spawn Egg"
        for p in m["parts"]:
            item_model(p["id"])
            lang["item.awaken." + p["id"]] = p["name"]
    path = os.path.join(MERGE, "lang/en_us.json")
    merged = json.load(open(path)) if os.path.exists(path) else {}
    merged.update(lang)  # fauna2.py adds P2's names to the same file
    write_json(path, dict(sorted(merged.items())))
    print(f"fauna: {len(ms)} creatures, {len(SPAWNS)} biome modifiers")


# ---------------------------------------------------------------------------------------------------------------------
# Preview sheets (look at these before shipping a creature)


def preview(outdir):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    ms = [m for m in fauna() if m["model"] in MODELS]  # the named beasts' hero bodies: heroes.py preview
    cell = (300, 240)
    sheet = Image.new("RGBA", (cell[0] * 4, (cell[1] + 18) * len(ms)), (24, 26, 34, 255))
    dr = ImageDraw.Draw(sheet)
    night_bg = (8, 10, 22, 255)
    for k, m in enumerate(ms):
        skin, glow = PAINTERS[m["id"]](m, rng_for("fauna", m["id"]))
        lit, base, g = composite(m, skin, glow)
        size, tree = MODELS[m["model"]]()
        views = [render(tree, lit, yaw=35, pitch=20, size=cell), render(tree, lit, yaw=110, pitch=12, size=cell),
                 render(tree, lit, yaw=-150, pitch=35, size=cell), render(tree, base, yaw=35, pitch=20, size=cell, night=g)]
        y = k * (cell[1] + 18)
        for i, im in enumerate(views):
            bg = Image.new("RGBA", cell, night_bg if i == 3 else (70, 92, 70, 255) if m["theme"] == "GREENWILD" else (120, 100, 70, 255) if m["theme"] == "SUNSCORCH" else (150, 165, 185, 255))
            bg.alpha_composite(im, (0, 0))
            sheet.paste(bg, (i * cell[0], y))
        dr.text((6, y + cell[1] + 2), f"{m['name']} ({m['role'].lower()}, {m['model'].lower()}, x{m['scale']})", fill=(220, 225, 235, 255))
    sheet.save(os.path.join(outdir, "fauna_views.png"))
    # line-up at true relative size (4 screen px per model pixel, 64 per block), a 1.8-block player bar for scale
    unit = 4.0
    rendered = []
    for m in ms:
        skin, glow = PAINTERS[m["id"]](m, rng_for("fauna", m["id"]))
        lit, base, g = composite(m, skin, glow)
        size, tree = MODELS[m["model"]]()
        rendered.append((m, render(tree, lit, yaw=60, pitch=8, px=unit * m["scale"])))
    W = sum(im.width for _, im in rendered) + 60 + 20 * len(rendered)
    H = max(max(im.height for _, im in rendered), int(1.8 * 16 * unit)) + 60
    lu = Image.new("RGBA", (W, H), (150, 170, 150, 255))
    ld = ImageDraw.Draw(lu)
    ground = H - 30
    ld.rectangle((10, ground - int(1.8 * 16 * unit), 26, ground), fill=(40, 40, 60, 255))
    x = 40
    for m, im in rendered:
        lu.alpha_composite(im, (x, ground - im.height + 8))
        ld.text((x, ground + 6), m["id"], fill=(20, 24, 30, 255))
        x += im.width + 20
    ld.line((0, ground, W, ground), fill=(60, 80, 60, 255))
    lu.save(os.path.join(outdir, "fauna_lineup.png"))
    print(os.path.join(outdir, "fauna_views.png"), os.path.join(outdir, "fauna_lineup.png"))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/fauna_preview")
    else:
        print(__doc__)
