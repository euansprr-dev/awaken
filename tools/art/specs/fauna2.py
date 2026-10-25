"""The Awakened layer, part two (P2): the six new biome families' creatures and the named beasts' lairs.

    python3 tools/art/build_all.py fauna2          textures (skin + glow layers, eggs, beast parts, family materials)
    python3 tools/art/specs/fauna2.py content      biome modifiers (P2's), loot tables, item models, lang, lair structures
                                                   and structure sets (src/main/...; their biomes: tools/terrain/biomes.py)
    python3 tools/art/specs/fauna2.py preview DIR  every P2 creature on its real model (three day views, one night
                                                   view with the glow layer) and a size line-up with a player bar

The creature list is read from mobs/def/WildBestiary.java. Box layouts mirror the Java models box for box: our own
bodies in mobs/client/model/WildModels.java (lizard, snail, mushroom, marmot), vanilla bodies (frog, goat, ocelot,
bee, phantom, spider, creaking, polar bear) from the 26.3 sources, and P1's mirrors (moth, parrot, serpent) from
fauna.py. On vanilla bodies the skin layer repaints every face; thin planes (webbed feet, wings, the creaking's
twigs) follow the vanilla texture's own outline so the body underneath never shows round them.
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
import fauna as F  # noqa: E402  (P1's spec: geometry helpers, renderer, palettes)
from fauna import box, part, faces, boxes_of, render, rgba, mix, shade, fill, noise, vgrad, hgrad, streaks, dots, border, paint_box, each_side  # noqa: E402,F401
from pixelart import Canvas, ramp, rng_for, value_noise, quantize, polygon, shard, _outline  # noqa: E402
import mobs_bestiary as MB  # noqa: E402

ROOT = F.ROOT
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/WildBestiary.java")
RES = F.RES
MERGE = F.MERGE
VANILLA_TEX = F.VANILLA_TEX


def creatures():
    """Every P2 creature in declaration order (parsed like P1's)."""
    src = open(JAVA).read()
    out = []
    for st in [m.start() for m in MB.M_RE.finditer(src)]:
        block = src[st:src.find(".build());", st)]
        mid, name, theme, tier, model, sig = MB.M_RE.match(block).groups()
        tint = MB.TINT_RE.search(block)
        glow = MB.GLOW_RE.search(block)
        sc = MB.SCALE_RE.search(block)
        out.append({
            "id": mid, "name": MB.unescape(name), "theme": theme, "tier": tier, "model": model, "sig": MB.unescape(sig),
            "role": F.ROLE_RE.search(block).group(1), "talent": F.TALENT_RE.search(block).group(1),
            "parts": [{"id": p, "name": MB.unescape(n), "ramp": r} for p, n, r in MB.P_RE.findall(block)],
            "tint": ("#" + tint.group(1).lower()) if tint else None,
            "glow": ("#" + glow.group(1).lower()) if glow else "#ffffff",
            "scale": float(sc.group(1)) if sc else 1.0,
        })
    return out


def nbox(name, *a, **k):
    b = box(*a, **k)
    b["name"] = name
    return b


def named(tree):
    """{name: box} for every named box of a tree."""
    return {b["name"]: b for b in boxes_of(tree) if "name" in b}


# ---------------------------------------------------------------------------------------------------------------------
# Geometry. Our own bodies (WildModels.java) ...


def _lizard():
    leg_l = [nbox("leg", 0, 26, 0, -1, -1, 4, 2, 2)]
    leg_r = [nbox("leg", 0, 26, -4, -1, -1, 4, 2, 2, mirror=True)]
    head = part((0, -0.5, -6), (0, 0, 0), [nbox("head", 0, 16, -2.5, -2, -5, 5, 4, 5), nbox("snout", 20, 16, -1.5, -0.5, -8, 3, 2, 3)], [
        part((0, -2, -1), (0, 0, 0), [nbox("crest", 36, 0, 0, -4, -3, 0, 4, 6)])])
    tail = part((0, 0, 6), (0.12, 0, 0), [nbox("tail", 0, 32, -1.5, -1.5, 0, 3, 3, 8)], [
        part((0, 0, 8), (0.1, 0, 0), [nbox("tail_end", 24, 32, -1, -1, 0, 2, 2, 7)])])
    body = part((0, 20, 0), (0, 0, 0), [nbox("body", 0, 0, -3, -2, -6, 6, 4, 12)], [
        head, part((0, -2, 0), (0, 0, 0), [nbox("sail", 36, 10, 0, -3, -5, 0, 3, 10)]), tail,
        part((3, 1, -4), (0, 0, 0.6), leg_l), part((-3, 1, -4), (0, 0, -0.6), leg_r),
        part((3, 1, 4), (0, 0, 0.6), leg_l), part((-3, 1, 4), (0, 0, -0.6), leg_r)])
    return (64, 64), part(children=[body])


def _snail():
    head = part((0, -2, -6), (0, 0, 0), [nbox("head", 0, 16, -2, -3, -3, 4, 3, 3)], [
        part((1.2, -3, -1.5), (0, 0, 0.25), [nbox("stalk", 16, 16, -0.5, -4, -0.5, 1, 4, 1)]),
        part((-1.2, -3, -1.5), (0, 0, -0.25), [nbox("stalk", 16, 16, -0.5, -4, -0.5, 1, 4, 1, mirror=True)])])
    shell = part((0, -2, 2), (-0.15, 0, 0), [nbox("shell", 0, 24, -4, -8, -4, 8, 8, 8)], [
        part((0, -8, 0), (0, 0, 0), [nbox("stem", 24, 40, -1, -2, -1, 2, 2, 2), nbox("cap", 0, 40, -3, -4, -3, 6, 2, 6)])])
    foot = part((0, 24, 0), (0, 0, 0), [nbox("foot", 0, 0, -2.5, -2, -7, 5, 2, 14)], [head, shell])
    return (64, 64), part(children=[foot])


def _mushroom():
    body = part((0, 21, 0), (0, 0, 0), [nbox("stem", 0, 0, -3, -9, -3, 6, 9, 6)], [
        part((0, -8, 0), (0, 0, 0), [nbox("cap", 0, 16, -6, -4, -6, 12, 4, 12), nbox("crown", 0, 32, -4.5, -6, -4.5, 9, 2, 9)]),
        part((3, -6, 0), (0, 0, -0.5), [nbox("arm", 44, 0, 0, 0, -1, 1, 3, 2)]),
        part((-3, -6, 0), (0, 0, 0.5), [nbox("arm", 44, 0, -1, 0, -1, 1, 3, 2, mirror=True)])])
    return (64, 64), part(children=[body,
                                     part((1.5, 21, 0), (0, 0, 0), [nbox("leg", 36, 0, -1, 0, -1, 2, 3, 2)]),
                                     part((-1.5, 21, 0), (0, 0, 0), [nbox("leg", 36, 0, -1, 0, -1, 2, 3, 2, mirror=True)])])


def _marmot():
    head = part((0, -1, -5), (0, 0, 0), [nbox("head", 0, 16, -2.5, -2.5, -4, 5, 4, 4)], [
        part((1.8, -2.5, -1), (0, 0, 0), [nbox("ear", 18, 16, -0.5, -1, -0.5, 1, 1, 1)]),
        part((-1.8, -2.5, -1), (0, 0, 0), [nbox("ear", 18, 16, -0.5, -1, -0.5, 1, 1, 1, mirror=True)])])
    body = part((0, 19, 1), (0, 0, 0), [nbox("body", 0, 0, -3, -3, -5, 6, 6, 10)], [
        head, part((0, 1, 5), (-0.3, 0, 0), [nbox("tail", 32, 0, -1.5, -0.5, 0, 3, 2, 5)])])
    legs = [part((sx * 2, 22, sz), (0, 0, 0), [nbox("leg", 24, 16, -1, 0, -1, 2, 2, 2, mirror=sx < 0)]) for sx in (1, -1) for sz in (-2, 4)]
    return (64, 32), part(children=[body] + legs)


# ... and vanilla bodies (26.3 sources).


def _frog():
    eyes = part((-0.5, 0, 2), (0, 0, 0), [], [
        part((-1.5, -3, -6.5), (0, 0, 0), [nbox("eye_r", 0, 0, -1.5, -1, -1.5, 3, 2, 3)]),
        part((2.5, -3, -6.5), (0, 0, 0), [nbox("eye_l", 0, 5, -1.5, -1, -1.5, 3, 2, 3)])])
    head = part((0, -2, -1), (0, 0, 0), [nbox("head_plane", 23, 13, -3.5, -1, -7, 7, 0, 9), nbox("head", 0, 13, -3.5, -2, -7, 7, 3, 9)], [eyes])
    body = part((0, -2, 4), (0, 0, 0), [nbox("body", 3, 1, -3.5, -2, -8, 7, 3, 9), nbox("belly_plane", 23, 22, -3.5, -1, -8, 7, 0, 9)], [
        head,
        part((0, -1, -5), (0, 0, 0), [nbox("throat", 26, 5, -3.5, -0.1, -2.9, 7, 2, 3, grow=-0.1)]),
        part((0, -1.01, 1), (0, 0, 0), [nbox("tongue", 17, 13, -2, 0, -7.1, 4, 0, 7)]),
        part((4, -1, -6.5), (0, 0, 0), [nbox("arm_l", 0, 32, -1, 0, -1, 2, 3, 3)], [part((0, 3, -1), (0, 0, 0), [nbox("hand_l", 18, 40, -4, 0.01, -4, 8, 0, 8)])]),
        part((-4, -1, -6.5), (0, 0, 0), [nbox("arm_r", 0, 38, -1, 0, -1, 2, 3, 3)], [part((0, 3, 0), (0, 0, 0), [nbox("hand_r", 2, 40, -4, 0.01, -5, 8, 0, 8)])])])
    root = part((0, 24, 0), (0, 0, 0), [], [
        body,
        part((3.5, -3, 4), (0, 0, 0), [nbox("leg_l", 14, 25, -1, 0, -2, 3, 3, 4)], [part((2, 3, 0), (0, 0, 0), [nbox("foot_l", 2, 32, -4, 0.01, -4, 8, 0, 8)])]),
        part((-3.5, -3, 4), (0, 0, 0), [nbox("leg_r", 0, 25, -2, 0, -2, 3, 3, 4)], [part((-2, 3, 0), (0, 0, 0), [nbox("foot_r", 18, 32, -4, 0.01, -4, 8, 0, 8)])])])
    return (48, 48), part(children=[root])


def _goat():
    head = part((1, 14, 0), (0, 0, 0), [nbox("ear", 2, 61, -6, -11, -10, 3, 2, 1), nbox("ear", 2, 61, 2, -11, -10, 3, 2, 1, mirror=True),
                                        nbox("goatee", 23, 52, -0.5, -3, -14, 0, 7, 5)], [
        part((0, 0, 0), (0, 0, 0), [nbox("horn", 12, 55, -0.01, -16, -10, 2, 7, 2)]),
        part((0, 0, 0), (0, 0, 0), [nbox("horn", 12, 55, -2.99, -16, -10, 2, 7, 2)]),
        part((0, -8, -8), (0.9599, 0, 0), [nbox("nose", 34, 46, -3, -4, -8, 5, 7, 10)])])
    body = part((0, 24, 0), (0, 0, 0), [nbox("body", 1, 1, -4, -17, -7, 9, 11, 16), nbox("fleece", 0, 28, -5, -18, -8, 11, 14, 11)])
    return (64, 64), part(children=[head, body,
                                     part((1, 14, 4), (0, 0, 0), [nbox("hind_l", 36, 29, 0, 4, 0, 3, 6, 3)]),
                                     part((-3, 14, 4), (0, 0, 0), [nbox("hind_r", 49, 29, 0, 4, 0, 3, 6, 3)]),
                                     part((1, 14, -6), (0, 0, 0), [nbox("front_l", 49, 2, 0, 0, 0, 3, 10, 3)]),
                                     part((-3, 14, -6), (0, 0, 0), [nbox("front_r", 35, 2, 0, 0, 0, 3, 10, 3)])])


def _ocelot():
    head = part((0, 15, -9), (0, 0, 0), [nbox("head", 0, 0, -2.5, -2, -3, 5, 4, 5), nbox("nose", 0, 24, -1.5, -0.001, -4, 3, 2, 2),
                                         nbox("ear", 0, 10, -2, -3, 0, 1, 1, 2), nbox("ear", 6, 10, 1, -3, 0, 1, 1, 2)])
    return (64, 32), part(children=[
        head,
        part((0, 12, -10), (math.pi / 2, 0, 0), [nbox("body", 20, 0, -2, 3, -8, 4, 16, 6)]),
        part((0, 15, 8), (0.9, 0, 0), [nbox("tail1", 0, 15, -0.5, 0, 0, 1, 8, 1)]),
        part((0, 20, 14), (1.73, 0, 0), [nbox("tail2", 4, 15, -0.5, 0, 0, 1, 8, 1)]),
        part((1.1, 18, 5), (0, 0, 0), [nbox("hind", 8, 13, -1, 0, 1, 2, 6, 2)]),
        part((-1.1, 18, 5), (0, 0, 0), [nbox("hind", 8, 13, -1, 0, 1, 2, 6, 2)]),
        part((1.2, 14.1, -5), (0, 0, 0), [nbox("front", 40, 0, -1, 0, 0, 2, 10, 2)]),
        part((-1.2, 14.1, -5), (0, 0, 0), [nbox("front", 40, 0, -1, 0, 0, 2, 10, 2)])])


def _bee():
    bone = part((0, 19, 0), (0, 0, 0), [], [
        part((0, 0, 0), (0, 0, 0), [nbox("body", 0, 0, -3.5, -4, -5, 7, 7, 10)], [
            part((0, 0, 0), (0, 0, 0), [nbox("stinger", 26, 7, 0, -1, 5, 0, 1, 2)]),
            part((0, -2, -5), (0, 0, 0), [nbox("antenna", 2, 0, 1.5, -2, -3, 1, 2, 3)]),
            part((0, -2, -5), (0, 0, 0), [nbox("antenna", 2, 3, -2.5, -2, -3, 1, 2, 3)])]),
        part((-1.5, -4, -3), (0, -0.2618, 0), [nbox("wing", 0, 18, -9, 0, 0, 9, 0, 6, grow=0.001)]),
        part((1.5, -4, -3), (0, 0.2618, 0), [nbox("wing", 0, 18, 0, 0, 0, 9, 0, 6, mirror=True, grow=0.001)]),
        part((1.5, 3, -2), (0, 0, 0), [nbox("legs", 26, 1, -5, 0, 0, 7, 2, 0)]),
        part((1.5, 3, 0), (0, 0, 0), [nbox("legs", 26, 3, -5, 0, 0, 7, 2, 0)]),
        part((1.5, 3, 2), (0, 0, 0), [nbox("legs", 26, 5, -5, 0, 0, 7, 2, 0)])])
    return (64, 64), part(children=[bone])


def _phantom():
    body = part((0, 0, 0), (-0.1, 0, 0), [nbox("body", 0, 8, -3, -2, -8, 5, 3, 9)], [
        part((0, -2, 1), (0, 0, 0), [nbox("tail_base", 3, 20, -2, 0, 0, 3, 2, 6)], [part((0, 0.5, 6), (0, 0, 0), [nbox("tail_tip", 4, 29, -1, 0, 0, 1, 1, 6)])]),
        part((2, -2, -8), (0, 0, 0.1), [nbox("wing_base", 23, 12, 0, 0, 0, 6, 2, 9)], [part((6, 0, 0), (0, 0, 0.1), [nbox("wing_tip", 16, 24, 0, 0, 0, 13, 1, 9)])]),
        part((-3, -2, -8), (0, 0, -0.1), [nbox("wing_base", 23, 12, -6, 0, 0, 6, 2, 9, mirror=True)], [
            part((-6, 0, 0), (0, 0, -0.1), [nbox("wing_tip", 16, 24, -13, 0, 0, 13, 1, 9, mirror=True)])]),
        part((0, 1, -7), (0.2, 0, 0), [nbox("head", 0, 0, -4, -2, -5, 7, 3, 5)])])
    return (64, 64), part(children=[body])


def _spider():
    q, e = math.pi / 4, math.pi / 8
    legs = []
    for side, rot_y, rot_z, z in ((-1, q, -q, 2), (1, -q, q, 2), (-1, e, -0.58119464, 1), (1, -e, 0.58119464, 1),
                                  (-1, -e, -0.58119464, 0), (1, e, 0.58119464, 0), (-1, -q, -q, -1), (1, q, q, -1)):
        b = nbox("leg", 18, 0, -15, -1, -1, 16, 2, 2) if side < 0 else nbox("leg", 18, 0, -1, -1, -1, 16, 2, 2, mirror=True)
        legs.append(part((side * 4, 15, z), (0, rot_y, rot_z), [b]))
    return (64, 32), part(children=[
        part((0, 15, -3), (0, 0, 0), [nbox("head", 32, 4, -4, -4, -8, 8, 8, 8)]),
        part((0, 15, 0), (0, 0, 0), [nbox("neck", 0, 0, -3, -3, -3, 6, 6, 6)]),
        part((0, 15, 9), (0, 0, 0), [nbox("abdomen", 0, 12, -5, -4, -6, 10, 8, 12)])] + legs)


def _creaking():
    upper = part((-1, -19, 0), (0, 0, 0), [], [
        part((-3, -11, 0), (0, 0, 0), [nbox("head", 0, 0, -3, -10, -3, 6, 10, 6), nbox("brow", 28, 31, -3, -13, -3, 6, 3, 6),
                                       nbox("twig_r", 12, 40, 3, -13, 0, 9, 14, 0), nbox("twig_l", 34, 12, -12, -14, 0, 9, 14, 0)]),
        part((0, -7, 1), (0, 0, 0), [nbox("torso", 0, 16, 0, -3, -3, 6, 13, 5), nbox("chest", 24, 0, -6, -4, -3, 6, 7, 5)]),
        part((-7, -9.5, 1.5), (0, 0, 0), [nbox("arm", 22, 13, -2, -1.5, -1.5, 3, 21, 3), nbox("hand", 46, 0, -2, 19.5, -1.5, 3, 4, 3)]),
        part((6, -9, 0.5), (0, 0, 0), [nbox("arm", 30, 40, 0, -1, -1.5, 3, 16, 3), nbox("shoulder", 52, 12, 0, -5, -1.5, 3, 4, 3),
                                       nbox("hand", 52, 19, 0, 15, -1.5, 3, 4, 3)])])
    root = part((0, 24, 0), (0, 0, 0), [], [
        upper,
        part((1.5, -16, 0.5), (0, 0, 0), [nbox("leg", 42, 40, -1.5, 0, -1.5, 3, 16, 3), nbox("root_l", 45, 55, -1.5, 15.7, -4.5, 5, 0, 9)]),
        part((-1, -17.5, 0.5), (0, 0, 0), [nbox("leg", 0, 34, -3, -1.5, -1.5, 3, 19, 3), nbox("root_r", 45, 46, -5, 17.2, -4.5, 5, 0, 9),
                                           nbox("knee", 12, 34, -3, -4.5, -1.5, 3, 3, 3)])])
    return (64, 64), part(children=[root])


def _polar_bear():
    head = part((0, 10, -16), (0, 0, 0), [nbox("head", 0, 0, -3.5, -3, -3, 7, 7, 7), nbox("mouth", 0, 44, -2.5, 1, -6, 5, 3, 3),
                                          nbox("ear", 26, 0, -4.5, -4, -1, 2, 2, 1), nbox("ear", 26, 0, 2.5, -4, -1, 2, 2, 1, mirror=True)])
    body = part((-2, 9, 12), (math.pi / 2, 0, 0), [nbox("body", 0, 19, -5, -13, -7, 14, 14, 11), nbox("shoulders", 39, 0, -4, -25, -7, 12, 12, 10)])
    return (128, 64), part(children=[head, body,
                                      part((-4.5, 14, 6), (0, 0, 0), [nbox("hind", 50, 22, -2, 0, -2, 4, 10, 8)]),
                                      part((4.5, 14, 6), (0, 0, 0), [nbox("hind", 50, 22, -2, 0, -2, 4, 10, 8)]),
                                      part((-3.5, 14, -8), (0, 0, 0), [nbox("front", 50, 40, -2, 0, -2, 4, 10, 6)]),
                                      part((3.5, 14, -8), (0, 0, 0), [nbox("front", 50, 40, -2, 0, -2, 4, 10, 6)])])


def _named(fn, names):
    """P1's mirrors with names added to their boxes in tree order."""
    size, tree = fn()
    for b, n in zip(boxes_of(tree), names):
        b["name"] = n
    return size, tree


def _moth():
    return _named(F._moth, ["thorax", "abdomen", "head", "antenna", "antenna", "fore", "fore", "hind", "hind"])


def _parrot():
    return _named(F._parrot, ["body", "tail", "wing", "wing", "head", "crest_a", "beak_a", "beak_b", "feather", "leg", "leg"])


def _serpent():
    return F._serpent()


MODELS = {"LIZARD": _lizard, "SNAIL": _snail, "MUSHROOM": _mushroom, "MARMOT": _marmot, "FROG": _frog, "GOAT": _goat, "OCELOT": _ocelot,
          "BEE": _bee, "PHANTOM": _phantom, "SPIDER": _spider, "CREAKING": _creaking, "POLAR_BEAR": _polar_bear, "MOTH": _moth,
          "PARROT": _parrot, "SERPENT": _serpent}
OWN = {"LIZARD", "SNAIL", "MUSHROOM", "MARMOT", "MOTH", "SERPENT"}
VANILLA = {"FROG": "frog/frog_temperate", "GOAT": "goat/goat", "OCELOT": "cat/ocelot", "BEE": "bee/bee_angry", "PHANTOM": "phantom/phantom",
           "SPIDER": "spider/spider", "CREAKING": "creaking/creaking", "POLAR_BEAR": "bear/polarbear", "PARROT": "parrot/parrot_grey"}


# ---------------------------------------------------------------------------------------------------------------------
# Painting helpers (rects are (x, y, w, h) in texture pixels; see fauna.faces for which way each face runs)

_VAN = {}


def vanilla(model):
    """The vanilla texture under a body (read only, for plane outlines and the preview)."""
    from PIL import Image
    if model not in _VAN:
        _VAN[model] = Image.open(os.path.join(VANILLA_TEX, VANILLA[model] + ".png")).convert("RGBA")
    return _VAN[model]


def P(*hexes):
    return [rgba(h) for h in hexes]


def solid(c, r, col):
    fill(c, r, lambda i, j, w, h: col)


def coat(c, b, pal, rng, scale=3, top=0.15, bottom=-0.25, belly=None, jitter=0.12):
    """A whole box in one coat: lighter above, darker below, an optional belly palette for the bottom face."""
    f = faces(b)
    noise(c, f["top"], pal, rng, scale, grad=lambda i, j, w, h: top, jitter=jitter)
    noise(c, f["bottom"], belly or pal, rng, scale, grad=lambda i, j, w, h: bottom if belly is None else 0.0, jitter=jitter)
    for name in ("west", "north", "east", "south"):
        noise(c, f[name], pal, rng, scale, grad=vgrad(top * 0.6, bottom * 0.6), jitter=jitter)


def fur(c, b, pal, rng, n=0.25, length=(2, 4), belly=None):
    coat(c, b, pal, rng, belly=belly)
    for name, r in faces(b).items():
        if name == "bottom" and belly is not None:
            continue
        streaks(c, r, pal[-1], rng, r[2] * r[3] * n * 0.5, length)
        streaks(c, r, pal[0], rng, r[2] * r[3] * n * 0.3, length)


def spot(c, x, y, rad, col, g=None, gcol=None):
    for j in range(-int(rad) - 1, int(rad) + 2):
        for i in range(-int(rad) - 1, int(rad) + 2):
            if i * i + j * j <= rad * rad + 0.3:
                c.set(x + i, y + j, col)
                if g is not None and gcol is not None:
                    g.set(x + i, y + j, gcol)


def spots(c, r, cols, rng, n, rad=0.6, g=None, gcol=None):
    x, y, w, h = r
    for _ in range(int(n)):
        sx, sy = x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h))
        rr = rad * (0.7 + rng.random() * 0.6)
        for j in range(-2, 3):
            for i in range(-2, 3):
                if i * i + j * j <= rr * rr + 0.3 and x <= sx + i < x + w and y <= sy + j < y + h:
                    c.set(sx + i, sy + j, rng.choice(cols))
                    if g is not None:
                        g.set(sx + i, sy + j, gcol)


def eye(c, g, x, y, col, gcol, w=1, h=1, shine=True):
    for j in range(h):
        for i in range(w):
            c.set(x + i, y + j, col)
            if gcol is not None:
                g.set(x + i, y + j, gcol)
    if shine and w * h > 1:
        c.set(x, y, mix(col, rgba("#ffffff"), 0.6))


def plane(c, b, model, fn, g=None, gfn=None):
    """A thin plane (wing, foot, twig): paint where the vanilla texture is solid (so nothing vanilla peeks round it);
    our own planes everywhere fn returns a colour."""
    img = vanilla(model).resize((c.w, c.h)) if model in VANILLA else None
    for name, r in faces(b).items():
        x, y, w, h = r
        if w <= 0 or h <= 0:
            continue
        for j in range(h):
            for i in range(w):
                if img is not None and img.getpixel((x + i, y + j))[3] < 24:
                    continue
                col = fn(i, j, w, h, name)
                if col is not None:
                    c.set(x + i, y + j, col)
                if g is not None and gfn is not None:
                    gc = gfn(i, j, w, h, name)
                    if gc is not None:
                        g.set(x + i, y + j, gc)


def cover(c, model, fill_col):
    """Belt and braces on vanilla bodies: any texel the vanilla texture uses that we left empty gets fill_col."""
    if model not in VANILLA:
        return
    img = vanilla(model).resize((c.w, c.h))
    for y in range(c.h):
        for x in range(c.w):
            if img.getpixel((x, y))[3] >= 24 and c.get(x, y)[3] < 128:
                c.set(x, y, fill_col)


def ring_bands(c, r, col, every=3, offset=0, vertical=False):
    x, y, w, h = r
    for j in range(h):
        for i in range(w):
            k = i if vertical else j
            if (k + offset) % every == 0:
                c.set(x + i, y + j, col)


def back(name, i, j, w, h):
    """How far towards the tail a texel of an unrotated box lies (0 front .. 1 back)."""
    if name in ("top", "bottom"):
        return 1.0 - j / max(1, h - 1)
    if name == "west":
        return 1.0 - i / max(1, w - 1)
    if name == "east":
        return i / max(1, w - 1)
    return 0.0 if name == "north" else 1.0


def setup(m):
    size, tree = MODELS[m["model"]]()
    return Canvas(*size), Canvas(*size), named(tree), tree


# ---------------------------------------------------------------------------------------------------------------------
# The Mire


def paint_bogwick(m, rng):
    skin, glow, nb, _ = setup(m)
    dark = P("#1a160e", "#262014", "#342c1a", "#443a22")
    lamp = P("#c4e45e", "#d8f27e", "#eaffa0", "#f8ffd0")
    f = faces(nb["body"])
    for name, r in f.items():
        x, y, w, h = r
        for j in range(h):
            for i in range(w):
                t = back(name, i, j, w, h)
                if t > 0.48:
                    col = lamp[min(3, int((t - 0.48) * 7) + rng.randrange(2))]
                    skin.set(x + i, y + j, col)
                    glow.set(x + i, y + j, rgba("#b8ff6a", 200 if t > 0.6 else 140))
                else:
                    col = dark[rng.randrange(4)]
                    if name in ("west", "east", "top") and int(t * 12) % 3 == 0:
                        col = rgba("#6a5a30")
                    skin.set(x + i, y + j, col)
    solid(glow, f["south"], rgba("#d8ff9a", 255))
    x, y, w, h = f["north"]
    for ex in (x + 1, x + w - 3):
        eye(skin, glow, ex, y + 1, rgba("#0a0806"), rgba("#c8ff8a", 90), 2, 2)
    for i in range(2, w - 2):
        skin.set(x + i, y + h - 2, rgba("#5a4a28"))
    for a in [b for b in boxes_of(MODELS["BEE"]()[1]) if b.get("name") == "antenna"]:
        for name, r in faces(a).items():
            solid(skin, r, dark[2])
            fill(glow, r, lambda i, j, w, h: rgba("#d8ff9a", 200) if (name == "north" or j == 0) else None)
    plane(skin, nb["wing"], "BEE", lambda i, j, w, h, n: rgba("#5e5848") if (i + j) % 4 == 0 or j == 0 else rgba("#8c8670") if i < w * 0.4 else rgba("#a8a28a"))
    for lg in [b for b in boxes_of(MODELS["BEE"]()[1]) if b.get("name") == "legs"]:
        plane(skin, lg, "BEE", lambda i, j, w, h, n: dark[1])
    plane(skin, nb["stinger"], "BEE", lambda i, j, w, h, n: dark[0])
    cover(skin, "BEE", dark[1])
    return skin, glow


def _lizard_paint(m, rng, body_pal, belly, spot_cols, spot_glow, sail_fn, crest, eye_col, eye_glow, fin_pal):
    skin, glow, nb, _ = setup(m)
    for key in ("body", "head", "tail", "tail_end"):
        b = nb[key]
        coat(skin, b, body_pal, rng, scale=2)
        f = faces(b)
        solid(skin, f["bottom"], belly[1])
        dots(skin, f["bottom"], belly, rng, f["bottom"][2] * f["bottom"][3] / 3)
        for name in ("west", "east"):
            x, y, w, h = f[name]
            for i in range(w):
                skin.set(x + i, y + h - 1, belly[0])
        for name in ("top", "west", "east"):
            spots(skin, f[name], spot_cols, rng, f[name][2] * f[name][3] / 14, rad=0.6, g=glow if spot_glow else None, gcol=spot_glow)
    coat(skin, nb["snout"], body_pal, rng, scale=2)
    solid(skin, faces(nb["snout"])["bottom"], belly[1])
    x, y, w, h = faces(nb["snout"])["north"]
    skin.set(x, y + h - 1, (12, 10, 8, 255))
    skin.set(x + w - 1, y + h - 1, (12, 10, 8, 255))
    fh = faces(nb["head"])
    with F.EYE.eyes(skin, glow):
        for name in ("west", "east"):
            x, y, w, h = fh[name]
            ex = x + w - 2 if name == "west" else x + 1
            eye(skin, glow, ex, y, eye_col, eye_glow, 1, 2, shine=False)
            skin.set(ex, y + 1, (10, 8, 6, 255))
        x, y, w, h = fh["top"]
        for ex in (x, x + w - 1):
            skin.set(ex, y + h - 2, eye_col)
            glow.set(ex, y + h - 2, eye_glow)
    for name, r in faces(nb["leg"]).items():
        noise(skin, r, fin_pal, rng, 2)
    x, y, w, h = faces(nb["leg"])["east"]
    for name in ("west", "east", "north", "south"):
        xx, yy, ww, hh = faces(nb["leg"])[name]
        for i in range(ww):
            skin.set(xx + i, yy + hh - 1, fin_pal[0])
    for name, r in faces(nb["sail"]).items():
        x, y, w, h = r
        for j in range(h):
            for i in range(w):
                col, gcol = sail_fn(i, j, w, h)
                if col is not None:
                    skin.set(x + i, y + j, col)
                if gcol is not None:
                    glow.set(x + i, y + j, gcol)
    if crest is not None:
        for name, r in faces(nb["crest"]).items():
            x, y, w, h = r
            for j in range(h):
                for i in range(w):
                    col, gcol = crest(i, j, w, h)
                    if col is not None:
                        skin.set(x + i, y + j, col)
                    if gcol is not None:
                        glow.set(x + i, y + j, gcol)
    return skin, glow


def paint_mudskipper(m, rng):
    body = P("#3a3420", "#4e4628", "#635a32", "#7a6e40", "#94864e")
    belly = P("#b8ac84", "#cabe98", "#dcd2b0")

    def sail(i, j, w, h):
        # a low spotted dorsal fin, its rim catching the light
        if j == 0:
            return rgba("#7adcc0"), rgba("#9cffd8", 150)
        if (i + j * 2) % 5 == 0:
            return rgba("#2e2a1a"), None
        return (rgba("#6a6038") if (i // 2) % 2 else rgba("#5a5230")), (rgba("#9cffd8", 60) if (i * 7 + j) % 11 == 0 else None)
    return _lizard_paint(m, rng, body, belly, P("#5cc8e8", "#3aa8d0"), rgba("#9cffd8", 130), sail, None,
                         rgba("#e8d870"), rgba("#9cffd8", 160), P("#4a4428", "#5e5634", "#746a40"))


def _frog_paint(m, rng, back_pal, belly, wart, blotch, eye_col, eye_glow, foot, mouth, extra=None):
    skin, glow, nb, _ = setup(m)
    for key in ("body", "head"):
        b = nb[key]
        coat(skin, b, back_pal, rng, scale=2)
        f = faces(b)
        noise(skin, f["bottom"], belly, rng, 2)
        for name in ("west", "east", "north", "south"):
            x, y, w, h = f[name]
            for i in range(w):
                skin.set(x + i, y + h - 1, belly[1])
        for name in ("top", "west", "east", "south"):
            if blotch is not None:
                spots(skin, f[name], [blotch], rng, f[name][2] * f[name][3] / 16, rad=0.9)
            dots(skin, f[name], wart, rng, f[name][2] * f[name][3] / 9)
    # the plane through the head is the mouth's roof (seen when the tongue flicks); the one through the body the belly
    plane(skin, nb["head_plane"], "FROG", lambda i, j, w, h, n: mouth)
    plane(skin, nb["belly_plane"], "FROG", lambda i, j, w, h, n: belly[0])
    x, y, w, h = faces(nb["head"])["north"]
    for i in range(w):
        skin.set(x + i, y + h - 2, shade(back_pal[0], 0.8))
    for key in ("eye_r", "eye_l"):
        for name, r in faces(nb[key]).items():
            solid(skin, r, eye_col)
            fill(glow, r, lambda i, j, w, h: eye_glow)
        x, y, w, h = faces(nb[key])["top"]
        for i in range(w):
            skin.set(x + i, y + h // 2, (8, 8, 6, 255))
        for name in ("west", "east", "north"):
            x, y, w, h = faces(nb[key])[name]
            for i in range(w):
                skin.set(x + i, y + h - 1, back_pal[1])
            skin.set(x + w // 2, y, (8, 8, 6, 255))
    coat(skin, nb["throat"], P("#d8cc8a", "#e6dca0", "#f2eabc"), rng, scale=2)
    plane(skin, nb["tongue"], "FROG", lambda i, j, w, h, n: rgba("#d86a7a") if i in (1, 2) or j < h - 1 else rgba("#b84a5a"))
    for key in ("arm_l", "arm_r", "leg_l", "leg_r"):
        coat(skin, nb[key], back_pal, rng, scale=2)
        for name in ("west", "north", "east", "south"):
            ring_bands(skin, faces(nb[key])[name], shade(back_pal[0], 0.85), every=3, offset=1)
    for key in ("hand_l", "hand_r", "foot_l", "foot_r"):
        plane(skin, nb[key], "FROG", lambda i, j, w, h, n: foot[(i + j) % len(foot)])
    if extra is not None:
        extra(skin, glow, nb)
    cover(skin, "FROG", back_pal[1])
    return skin, glow


def paint_bog_toad(m, rng):
    return _frog_paint(m, rng, P("#2a3418", "#38441e", "#465628", "#566832", "#6a7a40"), P("#c0b47a", "#d0c690", "#ddd6a8"),
                       P("#7c8a48", "#92a058"), rgba("#20281a"), rgba("#d8b040"), rgba("#b8ff6a", 110), P("#2e3a1a", "#3a4822"),
                       rgba("#6a2a2a"))


def paint_mire_mother(m, rng):
    def crown(skin, glow, nb):
        # moss on her shoulders, then the toadstone: a glowing gem set in her brow, smaller stones down her back
        for key in ("body", "head"):
            for name in ("top", "west", "east"):
                r = faces(nb[key])[name]
                spots(skin, r, P("#3a5a24", "#4e7430", "#62903c"), rng, r[2] * r[3] / 10, rad=1.0)
        x, y, w, h = faces(nb["head"])["top"]
        cx, cy = x + w // 2, y + 2  # behind the eyes, which sit on the front of the head
        for j in range(-1, 2):
            for i in range(-1, 2):
                col = rgba("#e8ffb0") if i == 0 and j == 0 else rgba("#b8ff6a")
                skin.set(cx + i, cy + j, col)
                glow.set(cx + i, cy + j, rgba("#d8ffa0", 255))
        for i in range(-2, 3):
            skin.set(cx + i, cy + 2, rgba("#4a3a20"))
            skin.set(cx + i, cy - 2, rgba("#4a3a20"))
        x, y, w, h = faces(nb["body"])["top"]
        for j in range(1, h, 3):
            skin.set(x + w // 2, y + j, rgba("#b8ff6a"))
            glow.set(x + w // 2, y + j, rgba("#b8ff6a", 200))
    return _frog_paint(m, rng, P("#1e2612", "#2a3418", "#36421e", "#445226", "#566430"), P("#a89a68", "#bcae7c", "#cec290"),
                       P("#6a7838", "#7c8a48"), rgba("#141a0e"), rgba("#ffb040"), rgba("#ffc060", 160), P("#222c14", "#2e3a1a"),
                       rgba("#5a1e1e"), crown)


# ---------------------------------------------------------------------------------------------------------------------
# The Canopy


def paint_dart_frog(m, rng):
    def shine(skin, glow, nb):
        for key in ("body", "head"):
            for name in ("top", "west", "east"):
                r = faces(nb[key])[name]
                x, y, w, h = r
                for _ in range(int(w * h / 18)):
                    sx, sy = x + rng.randrange(w), y + rng.randrange(h)
                    glow.set(sx, sy, rgba("#5cf0ff", 110))
    return _frog_paint(m, rng, P("#0c2e78", "#1446a8", "#1e62d4", "#3a86ee", "#6aaefc"), P("#2a5ab8", "#3a6ec8", "#5a8ad8"),
                       P("#0a0c14", "#101420"), rgba("#06070c"), rgba("#0a0a10"), rgba("#7ff4ff", 45), P("#0e3a8a", "#1650b8"),
                       rgba("#3a1030"), shine)


def paint_crested_basilisk(m, rng):
    body = P("#0c3420", "#14502e", "#1e6e3e", "#2e8c52", "#48ac6a")
    belly = P("#b8d48c", "#c8e0a0", "#dcecb8")

    def sail(i, j, w, h):
        t = j / max(1, h - 1)
        col = mix(rgba("#ffd24a"), rgba("#ff7a1e"), t)
        if i % 3 == 0:
            col = rgba("#7a3a10")
        g = rgba("#ffd24a", 170) if j == 0 else (rgba("#ffd24a", 60) if i % 3 == 1 else None)
        return col, g

    def crest(i, j, w, h):
        if j < h - 1 and (i + j) % 2 == 0 and j < 1:
            return rgba("#ffe07a"), rgba("#ffd24a", 200)
        col = mix(rgba("#ffd24a"), rgba("#e85a14"), j / max(1, h - 1))
        if i % 2 == 1 and j > 0:
            col = shade(col, 0.82)
        return col, (rgba("#ffd24a", 120) if j == 0 else None)
    return _lizard_paint(m, rng, body, belly, P("#5ce8d8", "#3ac8b8"), rgba("#5ce8d8", 80), sail, crest,
                         rgba("#ffd24a"), rgba("#ffd24a", 200), P("#14502e", "#1e6e3e", "#2e8c52"))


def paint_canopy_prowler(m, rng):
    skin, glow, nb, _ = setup(m)
    fur_ = P("#121016", "#1a1720", "#221e2a", "#2c2736", "#363042")
    for key in ("head", "body", "tail1", "tail2", "hind", "front", "nose", "ear"):
        b = nb[key]
        coat(skin, b, fur_, rng, scale=2, jitter=0.18)
    # rosettes: faint darker rings that catch a jade glow at night
    for key in ("body", "head", "front", "hind"):
        for name, r in faces(nb[key]).items():
            x, y, w, h = r
            for _ in range(max(1, int(w * h / 20))):
                cx, cy = x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h))
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    if x <= cx + dx < x + w and y <= cy + dy < y + h:
                        skin.set(cx + dx, cy + dy, rgba("#0a080e"))
                        if rng.random() < 0.35:
                            glow.set(cx + dx, cy + dy, rgba("#7cffb0", 80))
                if x <= cx < x + w and y <= cy < y + h:
                    skin.set(cx, cy, rgba("#3a344a"))
    x, y, w, h = faces(nb["head"])["north"]
    for ex in (x + 1, x + w - 2):
        eye(skin, glow, ex, y + 1, rgba("#b8ffd8"), rgba("#7cffb0", 255))
    x, y, w, h = faces(nb["nose"])["north"]
    for i in range(w):
        skin.set(x + i, y, rgba("#3a3448"))
    skin.set(x + w // 2, y, (8, 6, 10, 255))
    for name in ("west", "east", "north", "south"):
        x, y, w, h = faces(nb["tail2"])[name]
        for j in range(h - 2, h):
            for i in range(w):
                glow.set(x + i, y + j, rgba("#7cffb0", 70))
    cover(skin, "OCELOT", fur_[1])
    return skin, glow


def paint_plumed_coil(m, rng):
    size, tree = MODELS["SERPENT"]()
    skin, glow = Canvas(*size), Canvas(*size)
    jade = P("#0a3a2e", "#11523f", "#1a6e54", "#2a8e6c", "#46b48a")
    belly = P("#c8d8a0", "#d8e6b4", "#e8f2cc")
    bs = boxes_of(tree)
    for b in bs:
        f = faces(b)
        if b["w"] == 1:
            # plumes: red, orange and teal feathers
            for name, r in f.items():
                x, y, w, h = r
                for j in range(h):
                    for i in range(w):
                        k = (i + j // 2) % 3
                        skin.set(x + i, y + j, [rgba("#e8402a"), rgba("#ff9a2a"), rgba("#2ad8c8")][k])
                        if j == 0 or k == 2:
                            glow.set(x + i, y + j, rgba("#5cf0ff", 110 if k == 2 else 160))
            continue
        for name, r in f.items():
            x, y, w, h = r
            if name == "bottom":
                noise(skin, r, belly, rng, 2)
                for j in range(0, h, 2):
                    for i in range(w):
                        skin.set(x + i, y + j, rgba("#a8b884"))
                continue
            noise(skin, r, jade, rng, 3, grad=(lambda i, j, w, h: 0.1) if name == "top" else vgrad(0.1, -0.2))
            for j in range(h):
                for i in range(w):
                    if (i + j) % 3 == 0 and rng.random() < 0.4:
                        skin.set(x + i, y + j, jade[0])
            if name == "top":
                for j in range(h):
                    skin.set(x + w // 2, y + j, rgba("#5ce8d8"))
                    glow.set(x + w // 2, y + j, rgba("#5cf0ff", 190 if j % 3 else 255))
            if name in ("west", "east"):
                for i in range(0, w, 4):
                    for j in range(h):
                        skin.set(x + i, y + j, rgba("#c8941c"))
            if name == "top":
                for j in range(0, h, 4):
                    for i in range(w):
                        skin.set(x + i, y + j, rgba("#c8941c"))
    head = bs[0]
    fh = faces(head)
    for name in ("top", "west", "east", "south", "north"):
        noise(skin, fh[name], P("#0a3024", "#104232", "#185a44", "#22745a"), rng, 2, grad=(lambda i, j, w, h: 0.1) if name == "top" else vgrad(0.15, -0.2))
    x, y, w, h = fh["top"]
    for i in range(1, w - 1):
        skin.set(x + i, y + h - 2, rgba("#e8b830"))
        glow.set(x + i, y + h - 2, rgba("#ffd24a", 200))
    for name in ("west", "east"):
        x, y, w, h = fh[name]
        ex = x + w - 3 if name == "west" else x + 2
        for dx in (0, 1):
            skin.set(ex + dx, y + 1, rgba("#ff5a3a"))
            glow.set(ex + dx, y + 1, rgba("#ff8a5a", 255))
    x, y, w, h = fh["north"]
    for i in range(w):
        skin.set(x + i, y + h - 1, (20, 30, 24, 255))
    fill(skin, faces(bs[2])["top"], lambda i, j, w, h: rgba("#5a1a20") if 0 < i < w - 1 else rgba("#f0ecd0"))
    return skin, glow


# ---------------------------------------------------------------------------------------------------------------------
# Duskwood


def _snail_paint(m, rng, body, spots_col, shell, seam, seam_glow, stalk_glow, garden=None):
    skin, glow, nb, _ = setup(m)
    coat(skin, nb["foot"], body, rng, scale=2)
    dots(skin, faces(nb["foot"])["top"], spots_col, rng, 14)
    for name in ("west", "east"):
        x, y, w, h = faces(nb["foot"])[name]
        for i in range(w):
            skin.set(x + i, y + h - 1, shade(body[0], 0.85))
    coat(skin, nb["head"], body, rng, scale=2)
    for name, r in faces(nb["stalk"]).items():
        noise(skin, r, body[1:], rng, 2)
        if name in ("west", "east", "north", "south"):
            x, y, w, h = r
            skin.set(x, y, rgba("#101014"))
            glow.set(x, y, stalk_glow)
        if name == "top":
            solid(skin, r, rgba("#101014"))
            fill(glow, r, lambda i, j, w, h: stalk_glow)
    # the shell: a dark whorl, its seams spiral in to the centre of each side
    fs = faces(nb["shell"])
    for name, r in fs.items():
        x, y, w, h = r
        noise(skin, r, shell, rng, 3, grad=vgrad(0.12, -0.2))
        if name in ("west", "east"):
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            k = 0.0
            while k < 3.3 * math.pi:
                rr = 0.3 + k * 0.36
                px_, py_ = int(round(cx + math.cos(k) * rr)), int(round(cy + math.sin(k) * rr))
                if 0 <= px_ < w and 0 <= py_ < h:
                    skin.set(x + px_, y + py_, seam)
                    if seam_glow is not None:
                        glow.set(x + px_, y + py_, seam_glow)
                k += 0.12
        elif name in ("top", "north", "south"):
            for j in range(h):
                for i in range(w):
                    if (j + i // 3) % 4 == 0:
                        skin.set(x + i, y + j, seam)
                        if seam_glow is not None and rng.random() < 0.5:
                            glow.set(x + i, y + j, seam_glow)
    if garden is not None:
        garden(skin, glow, nb)
    return skin, glow


def paint_gloam_snail(m, rng):
    return _snail_paint(m, rng, P("#3e3848", "#524a5e", "#6a6278", "#827a92"), P("#a49cb4", "#b8b0c8"),
                        P("#141820", "#1c2230", "#262e40", "#323c52"), rgba("#5cc8d8"), rgba("#9cf0ff", 210), rgba("#9cf0ff", 230))


def paint_hollow_owl(m, rng):
    skin, glow, nb, _ = setup(m)
    feathers = P("#463f34", "#5e5546", "#786e5c", "#948a76", "#b0a690")
    bar = rgba("#2e2820")
    pale = P("#cfc6b2", "#ddd5c2", "#ebe4d4")
    for key in ("body", "wing", "tail", "head", "crest_a", "beak_a", "beak_b", "leg"):
        coat(skin, nb[key], feathers, rng, scale=2)
    for key in ("wing", "tail"):
        for name in ("west", "east", "north", "south", "top"):
            ring_bands(skin, faces(nb[key])[name], bar, every=3, offset=rng.randrange(3))
    for name in ("west", "east", "south", "top"):
        r = faces(nb["body"])[name]
        dots(skin, r, [bar, feathers[3]], rng, r[2] * r[3] / 4)
    # a pale breast, a facial disc with a dark rim, amber eyes
    for name in ("north",):
        r = faces(nb["body"])[name]
        noise(skin, r, pale, rng, 2)
        x, y, w, h = r
        for j in range(1, h, 2):
            for i in range(w):
                if (i + j) % 3 == 0:
                    skin.set(x + i, y + j, feathers[2])
    x, y, w, h = faces(nb["head"])["north"]
    solid(skin, (x, y, w, h), pale[1])
    for i in range(w):
        skin.set(x + i, y + h - 1, feathers[1])
    eye(skin, glow, x, y + 1, rgba("#ffb030"), rgba("#ffd27a", 255), 1, 1)
    eye(skin, glow, x + w - 1, y + 1, rgba("#ffb030"), rgba("#ffd27a", 255), 1, 1)
    for key in ("beak_a", "beak_b"):
        for r in faces(nb[key]).values():
            solid(skin, r, rgba("#2a2620"))
    # ear tufts: the parrot's crest feather, darkened
    plane(skin, nb["feather"], "PARROT", lambda i, j, w, h, n: feathers[0] if (i + j) % 2 else feathers[1])
    for r in faces(nb["crest_a"]).values():
        noise(skin, r, feathers[:3], rng, 2)
    for r in faces(nb["leg"]).values():
        solid(skin, r, rgba("#b8a870"))
    cover(skin, "PARROT", feathers[1])
    return skin, glow


def paint_thornling(m, rng):
    skin, glow, nb, tree = setup(m)
    bark = P("#1a120a", "#261a0e", "#342414", "#44301c", "#563e24")
    thorn = rgba("#cbb88e")
    for b in boxes_of(tree):
        if b["w"] == 0 or b["h"] == 0 or b["d"] == 0:
            continue
        coat(skin, b, bark, rng, scale=2, jitter=0.2)
        for name, r in faces(b).items():
            x, y, w, h = r
            streaks(skin, r, bark[0], rng, w * h / 6, (2, 5))
            for _ in range(int(w * h / 12)):
                tx, ty = x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h))
                skin.set(tx, ty, thorn)
            if name in ("west", "east", "south") and rng.random() < 0.6:
                spots(skin, r, P("#2e4a22", "#3a5a2a"), rng, max(1, w * h / 30), rad=0.8)
            for _ in range(int(w * h / 40)):
                gx, gy = x + rng.randrange(max(1, w)), y + rng.randrange(max(1, h))
                glow.set(gx, gy, rgba("#ff8a3a", 70))
    x, y, w, h = faces(nb["head"])["north"]
    for ex in (x + 1, x + w - 2):
        eye(skin, glow, ex, y + 4, rgba("#ffb060"), rgba("#ff8a3a", 255), 1, 1)
        glow.set(ex, y + 5, rgba("#ff8a3a", 120))
    for key in ("twig_r", "twig_l", "root_l", "root_r"):
        plane(skin, nb[key], "CREAKING", lambda i, j, w, h, n: thorn if (i * 3 + j) % 7 == 0 else bark[(i + j) % 3 + 1],
              glow, lambda i, j, w, h, n: rgba("#ff8a3a", 50) if (i + j * 5) % 13 == 0 else None)
    cover(skin, "CREAKING", bark[2])
    return skin, glow


def paint_weaver_queen(m, rng):
    skin, glow, nb, tree = setup(m)
    chitin = P("#0e0a14", "#18121f", "#22192c", "#2c2239", "#382c48")
    pale = rgba("#a898c8")
    for key in ("head", "neck", "abdomen", "leg"):
        coat(skin, nb[key], chitin, rng, scale=2)
    # a web drawn on her back, an hourglass that glows
    x, y, w, h = faces(nb["abdomen"])["top"]
    cx, cy = x + w / 2.0 - 0.5, y + h / 2.0 - 0.5
    for j in range(h):
        for i in range(w):
            dx, dy = x + i - cx, y + j - cy
            ang = math.atan2(dy, dx)
            rr = math.hypot(dx, dy)
            if abs((ang / (math.pi / 4)) % 1.0 - 0.5) > 0.44 or abs(rr % 2.4 - 1.2) < 0.25:
                skin.set(x + i, y + j, pale)
                glow.set(x + i, y + j, rgba("#d8c8ff", 70))
    for j in range(h):
        half = abs(j - (h - 1) / 2.0)
        wdt = int(half * 0.45) + 1
        for i in range(-wdt, wdt):
            skin.set(int(cx) + 1 + i, y + j, rgba("#e8dcff") if half > 1 else rgba("#d8c8ff"))
            glow.set(int(cx) + 1 + i, y + j, rgba("#d8c8ff", 220))
    for name in ("west", "east", "south"):
        r = faces(nb["abdomen"])[name]
        ring_bands(skin, r, chitin[4], every=3)
    # eight eyes in two rows
    x, y, w, h = faces(nb["head"])["north"]
    for ex, ey, s in ((x + 1, y + 2, 2), (x + w - 3, y + 2, 2), (x + 3, y + 1, 1), (x + w - 4, y + 1, 1), (x + 2, y + 4, 1),
                      (x + w - 3, y + 4, 1), (x + 3, y + 5, 1), (x + w - 4, y + 5, 1)):
        eye(skin, glow, ex, ey, rgba("#e8dcff"), rgba("#d8c8ff", 255), s, s)
    for i in range(2, w - 2):
        skin.set(x + i, y + h - 1, rgba("#4a3a5a"))
    # banded legs
    for name in ("west", "east", "north", "south", "top", "bottom"):
        r = faces(nb["leg"])[name]
        if name in ("north", "south"):
            continue
        ring_bands(skin, r, pale, every=5, offset=2, vertical=True)
    cover(skin, "SPIDER", chitin[1])
    return skin, glow


# ---------------------------------------------------------------------------------------------------------------------
# Blossomwood


def _moth_wings(skin, glow, nb, rng, base, vein, edge, spot_fn):
    for key in ("fore", "hind"):
        for name, r in faces(nb[key]).items():
            x, y, w, h = r
            if w <= 0 or h <= 0:
                continue
            nz = value_noise(max(2, w), max(2, h), rng, scale=3, octaves=2)
            for j in range(h):
                for i in range(w):
                    t = i / max(1, w - 1)
                    v = 0.2 + 0.6 * t + (nz[j][i] - 0.5) * 0.12
                    col = base[quantize(max(0.0, min(0.999, v)), len(base))]
                    if i == 0 or (j % 3 == 1 and i % 5 == 2):
                        col = vein
                    if i == w - 1 or j == 0 or j == h - 1:
                        col = edge
                    skin.set(x + i, y + j, col)
            spot_fn(key, name, r)


def paint_petalwing(m, rng):
    skin, glow, nb, _ = setup(m)
    body = P("#2a1e26", "#3a2a34", "#4a3642")
    for key in ("thorax", "abdomen", "head"):
        coat(skin, nb[key], body, rng, scale=2)
    for name, r in faces(nb["antenna"]).items():
        plane(skin, nb["antenna"], "MOTH", lambda i, j, w, h, n: body[0] if i == 0 or j % 2 == 0 else None)
        break

    def spots_(key, name, r):
        x, y, w, h = r
        # petal-shaped spots and a dusting of white at the edge
        for k in range(3 if key == "fore" else 2):
            sx, sy = x + int(w * (0.35 + 0.22 * k)), y + int(h * (0.35 + (k % 2) * 0.3))
            for dj in range(-1, 2):
                for di in range(-1, 2):
                    if abs(di) + abs(dj) <= 1:
                        skin.set(sx + di, sy + dj, rgba("#d070b8") if di or dj else rgba("#ffe0f4"))
                        glow.set(sx + di, sy + dj, rgba("#ffb8e0", 170))
        for j in range(0, h, 2):
            skin.set(x + w - 1, y + j, rgba("#ffffff"))
            glow.set(x + w - 1, y + j, rgba("#ffd8ee", 90))
    _moth_wings(skin, glow, nb, rng, P("#f2b6dc", "#f7c9e6", "#fbdcef", "#fff0f8"), rgba("#c070a8"), rgba("#d890c4"), spots_)
    return skin, glow


def paint_empress_moth(m, rng):
    skin, glow, nb, _ = setup(m)
    fuzz = P("#cfbcc8", "#e0d0da", "#f0e4ec", "#fff6fb")
    for key in ("thorax", "abdomen", "head"):
        fur(skin, nb[key], fuzz, rng, n=0.3, length=(1, 2))
    x, y, w, h = faces(nb["head"])["north"]
    for i in range(w):
        skin.set(x + i, y, rgba("#1a0c16"))
        glow.set(x + i, y, rgba("#ff9ad8", 160))
    plane(skin, nb["antenna"], "MOTH", lambda i, j, w, h, n: rgba("#ffd27a") if (i == 0 or j % 2 == 0) else None,
          glow, lambda i, j, w, h, n: rgba("#ffd27a", 200) if (i == 0 or j % 2 == 0) else None)

    def eyespots(key, name, r):
        x, y, w, h = r
        cx, cy = x + int(w * (0.6 if key == "fore" else 0.5)), y + int(h * 0.5)
        rad = 2.6 if key == "fore" else 1.8
        for j in range(h):
            for i in range(w):
                d = math.hypot(x + i - cx, y + j - cy)
                if d <= 0.7:
                    skin.set(x + i, y + j, rgba("#12060e"))
                    glow.set(x + i, y + j, rgba("#ffffff", 120))
                elif d <= rad - 1.0:
                    skin.set(x + i, y + j, rgba("#ff8ad0"))
                    glow.set(x + i, y + j, rgba("#ff9ad8", 230))
                elif d <= rad:
                    skin.set(x + i, y + j, rgba("#ffd27a"))
                    glow.set(x + i, y + j, rgba("#ffd27a", 240))
        for j in range(1, h, 2):
            glow.set(x + w - 1, y + j, rgba("#ffb8e0", 120))
    _moth_wings(skin, glow, nb, rng, P("#3a1230", "#56204a", "#782e66", "#9c4082", "#bc5c9e"), rgba("#2a0a22"), rgba("#e8a0d0"), eyespots)
    return skin, glow


def mass(c, b, ramp, base, light=None, pattern=None, grad=0.8, axis="j", rim=0.5, ao=0.6, flip=False):
    """Clean value masses on one named box (the hero painter's rules): a base step per face (light from above), a
    soft gradient along the face's vertical axis, a lit top edge and a dark foot, then a sparse pattern. `light`
    overrides FACE_LIGHT for rotated boxes; axis="i" when a face's vertical runs across the texture (rotated bodies)."""
    import fauna_rig as FR
    cube = _B(b)
    lt = dict(FR.FACE_LIGHT)
    if light:
        lt.update(light)
    for fname in ("top", "bottom", "west", "north", "east", "south"):
        f = FR.Face(c, cube, fname)
        if f.w <= 0 or f.h <= 0:
            continue
        for i, j in f.each():
            v = base + lt[fname]
            if fname in FR.SIDES:
                along, size = (i, f.w) if axis == "i" else (j, f.h)
                t = along / max(1, size - 1)
                if flip:
                    t = 1 - t
                v += grad * (0.5 - t)
                if (along == 0 and not flip) or (along == size - 1 and flip):
                    v += rim
                if size > 2 and ((along == size - 1 and not flip) or (along == 0 and flip)):
                    v -= ao
            if pattern is not None:
                p = pattern(f, i, j)
                if p is None:
                    continue
                if isinstance(p, tuple):
                    f.set(i, j, p)
                    continue
                v += p
            f.set(i, j, ramp.at(v))


class _B:
    """A fauna2 named box seen as a rig cube (for fauna_rig's face helpers)."""

    def __init__(self, b):
        self.u, self.v, self.w, self.h, self.d = b["u"], b["v"], int(round(b["w"])), int(round(b["h"])), int(round(b["d"]))
        self.name = b.get("name", "box")


def paint_honey_bear(m, rng):
    import fauna_rig as FR
    skin, glow, nb, tree = setup(m)
    coat = FR.Ramp("#2a1709", "#3d220e", "#552f13", "#6e3f19", "#8a5322", "#a66a2e", "#c0853f")
    cream = FR.Ramp("#8a6a44", "#a8855a", "#c4a274", "#dcc094", "#eedab4")
    paw = FR.Ramp("#160d06", "#24160a", "#34200f", "#462c16")
    fur_ = FR.p_strokes(3, 3, 0.5, 0.4, 0.55, seed=31)
    # the body and shoulders are rotated a quarter turn: their south face is the back, the north the belly
    rotated = {"top": -0.2, "bottom": -0.2, "south": 1.0, "north": -1.4, "west": 0.0, "east": -0.35}
    for key in ("body", "shoulders"):
        mass(skin, nb[key], coat, 3.2, light=rotated, pattern=fur_, axis="i", flip=True)
    mass(skin, nb["head"], coat, 3.5, pattern=FR.p_strokes(2, 2, 0.5, 0.4, 0.6, seed=32))
    for key in ("hind", "front"):
        mass(skin, nb[key], coat, 2.8, pattern=FR.p_strokes(3, 3, 0.5, 0.4, 0.6, seed=33))
    mass(skin, nb["ear"], coat, 2.6)
    mass(skin, nb["mouth"], cream, 2.6)
    # a pale crescent across the chest (the shoulders' front end is their bottom face after the turn)
    f = FR.Face(skin, _B(nb["shoulders"]), "bottom")
    for i, j in f.each():
        cx = (f.w - 1) / 2
        if abs(j - (2 + abs(i - cx) * 0.45)) < 1.2 and abs(i - cx) < f.w / 2 - 1:
            f.set(i, j, cream.at(3.2 - (0.6 if abs(i - cx) > 3 else 0)))
    # face: cream muzzle, dark nose and mouth line, small dark eyes with an amber glint
    x, y, w, h = faces(nb["mouth"])["north"]
    for i in range(w):
        skin.set(x + i, y + h - 1, rgba("#3a2814"))
    for i in range(w // 2 - 1, w // 2 + 1):
        skin.set(x + i, y, rgba("#120a05"))
        skin.set(x + i, y + 1, rgba("#120a05"))
    x, y, w, h = faces(nb["head"])["north"]
    for i in range(1, w - 1):  # a lighter brow over the eyes
        skin.set(x + i, y + 1, coat.at(4.3))
    for ex in (x + 1, x + w - 3):
        skin.set(ex, y + 3, rgba("#0c0704"))
        skin.set(ex + 1, y + 3, rgba("#0c0704"))
        skin.set(ex + 1, y + 2, coat.at(2))
        glow.set(ex, y + 3, rgba("#ffc850", 70))
    # paws: dark, with honey smeared on the toes (it glows a little at night)
    for key in ("hind", "front"):
        for name in ("west", "north", "east", "south"):
            x, y, w, h = faces(nb[key])[name]
            for j in range(h - 3, h):
                for i in range(w):
                    skin.set(x + i, y + j, paw.at(2.2 - (j - h + 3) * 0.6 + (0.5 if j == h - 3 else 0)))
            if name == "north":
                for i in (1, w - 2):
                    skin.set(x + i, y + h - 3, rgba("#e8a020"))
                    skin.set(x + i, y + h - 2, rgba("#b87810"))
                    glow.set(x + i, y + h - 3, rgba("#ffb020", 110))
    for k in range(3):
        x, y, w, h = faces(nb["mouth"])["north"]
        skin.set(x + 1 + k, y + h - 1, rgba("#ffb020"))
        glow.set(x + 1 + k, y + h - 1, rgba("#ffb020", 140))
    cover(skin, "POLAR_BEAR", coat.at(3))
    return skin, glow


# ---------------------------------------------------------------------------------------------------------------------
# The Crags


def paint_scree_marmot(m, rng):
    import fauna_rig as FR
    skin, glow, nb, _ = setup(m)
    coat = FR.Ramp("#2a2018", "#3b2e22", "#4f3f2f", "#65523e", "#7e6850", "#988266", "#b39d80")
    cream = FR.Ramp("#8a7a62", "#a6957a", "#c0b094", "#d6c8ae", "#e8dcc6")
    dark = FR.Ramp("#120d09", "#1e1610", "#2c2118", "#3a2c20")
    # agouti: grizzled tips in short rows along the back, a darker saddle, a pale underside
    grizzle = lambda f, i, j: (0.9 if f.name in ("top",) and (i + j * 2) % 5 == 0 else 0) + (  # noqa: E731
        0.7 if f.name in FR.SIDES and j <= 1 and (i * 3 + j) % 4 == 0 else 0)
    mass(skin, nb["body"], coat, 3.3, pattern=grizzle)
    for fname in FR.SIDES + ("bottom",):
        f = FR.Face(skin, _B(nb["body"]), fname)
        for i, j in f.each():
            if fname == "bottom" or j >= f.h - 2:
                f.set(i, j, cream.at(2.6 - (0.6 if fname != "bottom" and j == f.h - 2 else 0)))
    mass(skin, nb["head"], coat, 3.6, pattern=grizzle)
    mass(skin, nb["ear"], dark, 2.4)
    mass(skin, nb["leg"], dark, 2.0, grad=0.3)
    # the tail: brown with a near-black tip
    mass(skin, nb["tail"], coat, 2.8)
    for fname in ("west", "east", "top", "bottom"):
        f = FR.Face(skin, _B(nb["tail"]), fname)
        for i, j in f.each():
            # the tail points back: on the side faces the last columns are its tip
            tip = (i <= 1) if fname == "west" else (i >= f.w - 2) if fname == "east" else (j <= 1)
            if tip:
                f.set(i, j, dark.at(1.4))
    f = FR.Face(skin, _B(nb["tail"]), "south")
    for i, j in f.each():
        f.set(i, j, dark.at(1.2))
    # face: a cream muzzle and cheeks, a dark nose, bright black eyes with an amber glint
    x, y, w, h = faces(nb["head"])["north"]
    for j in range(h // 2, h):
        for i in range(w):
            skin.set(x + i, y + j, cream.at(3.0 - (0.6 if i in (0, w - 1) else 0)))
    skin.set(x + w // 2, y + h - 2, rgba("#140e0a"))
    with F.EYE.eyes(skin, glow):
        for ex in (x + 1, x + w - 2):
            skin.set(ex, y + 1, rgba("#0a0705"))
            glow.set(ex, y + 1, rgba("#ffd27a", 70))
    for name in ("west", "east"):
        x, y, w, h = faces(nb["head"])[name]
        for i in range(w):
            skin.set(x + i, y + h - 1, cream.at(2.6))
    return skin, glow


def paint_peak_kite(m, rng):
    skin, glow, nb, _ = setup(m)
    white = P("#c4c8d0", "#d6dae2", "#e6e9ef", "#f6f7fa")
    grey = P("#2e323a", "#3e434c", "#50565f")
    for key in ("body", "head", "tail_base", "tail_tip", "wing_base", "wing_tip"):
        coat(skin, nb[key], white, rng, scale=2, jitter=0.08)
    # dark primaries: the outer half of each wing tip, barred where it meets the white
    for name, r in faces(nb["wing_tip"]).items():
        x, y, w, h = r
        for j in range(h):
            for i in range(w):
                t = i / max(1, w - 1)
                outer = t > 0.5 if name in ("top", "bottom", "north", "south") else True
                if outer and name in ("top", "bottom"):
                    skin.set(x + i, y + j, grey[(i + j) % 3] if t > 0.62 or (j % 2 == 0) else white[1])
                    if t > 0.85 and j % 2 == 0:
                        glow.set(x + i, y + j, rgba("#a8d8ff", 90))
    for name in ("top", "west", "east"):
        ring_bands(skin, faces(nb["tail_base"])[name], grey[2], every=3)
        ring_bands(skin, faces(nb["tail_tip"])[name], grey[2], every=3)
    x, y, w, h = faces(nb["head"])["north"]
    for i in range(w // 2 - 1, w // 2 + 1):
        skin.set(x + i, y + h - 1, rgba("#e8b830"))
        skin.set(x + i, y + h - 2, rgba("#d8a020"))
    for ex in (x, x + w - 1):
        eye(skin, glow, ex, y, rgba("#e8c040"), rgba("#a8d8ff", 160))
    for name in ("west", "east"):
        x, y, w, h = faces(nb["head"])[name]
        ex = x + w - 2 if name == "west" else x + 1
        skin.set(ex, y + 1, rgba("#141210"))
        skin.set(ex + (1 if name == "west" else -1), y + 1, rgba("#3a3e48"))
    cover(skin, "PHANTOM", white[1])
    return skin, glow


def _goat_paint(m, rng, wool, legs, horn, ridge, ridge_glow, face, eye_col, eye_glow, lichen=None):
    skin, glow, nb, tree = setup(m)
    for key in ("body", "fleece"):
        coat(skin, nb[key], wool[1:4], rng, scale=3, jitter=0.06)
        for name, r in faces(nb[key]).items():
            streaks(skin, r, wool[4], rng, r[2] * r[3] * 0.06, (2, 4))
            streaks(skin, r, wool[0], rng, r[2] * r[3] * 0.04, (2, 3))
        if lichen is not None:
            for name in ("top", "west", "east", "south"):
                r = faces(nb[key])[name]
                spots(skin, r, lichen, rng, r[2] * r[3] / 22, rad=1.2)
    for key in ("hind_l", "hind_r", "front_l", "front_r"):
        coat(skin, nb[key], legs, rng, scale=2)
        for name in ("west", "north", "east", "south"):
            x, y, w, h = faces(nb[key])[name]
            for j in range(h - 2, h):
                for i in range(w):
                    skin.set(x + i, y + j, (22, 20, 20, 255))
    for name, r in faces(nb["nose"]).items():
        noise(skin, r, face, rng, 2)
    x, y, w, h = faces(nb["nose"])["north"]
    for i in range(1, w - 1):
        skin.set(x + i, y + h - 2, (24, 22, 22, 255))
    for name in ("west", "east"):
        x, y, w, h = faces(nb["nose"])[name]
        ex = x + 2 if name == "west" else x + w - 3
        eye(skin, glow, ex, y + 1, eye_col, eye_glow)
    for b in [b for b in boxes_of(tree) if b.get("name") == "ear"]:
        coat(skin, b, face, rng, scale=2)
    plane(skin, nb["goatee"], "GOAT", lambda i, j, w, h, n: wool[1] if (i + j) % 2 else wool[2])
    for b in [b for b in boxes_of(tree) if b.get("name") == "horn"]:
        for name, r in faces(b).items():
            x, y, w, h = r
            for j in range(h):
                for i in range(w):
                    col = horn[min(len(horn) - 1, int(j / max(1, h - 1) * (len(horn) - 1)))]
                    if name not in ("top", "bottom") and j % 2 == 1:
                        col = ridge
                        if ridge_glow is not None:
                            glow.set(x + i, y + j, ridge_glow)
                    skin.set(x + i, y + j, col)
    cover(skin, "GOAT", wool[2])
    return skin, glow


def paint_crag_ram(m, rng):
    return _goat_paint(m, rng, P("#857e72", "#9e968a", "#b6aea2", "#ccc4b8", "#e2dcd2"), P("#4a453e", "#5a544c", "#6a645a"),
                       P("#5a5048", "#4a423a", "#3a342e"), rgba("#2a2420"), rgba("#a8d8ff", 50), P("#a49c90", "#bcb4a8", "#d0c8bc"),
                       rgba("#d8a030"), rgba("#ffd27a", 90))


def paint_stonehorn(m, rng):
    return _goat_paint(m, rng, P("#34343a", "#45454c", "#5a5a62", "#707078", "#8a8a92"), P("#2a2a30", "#34343a", "#404048"),
                       P("#6a6a72", "#4a4a52", "#2e2e34"), rgba("#7ab8e8"), rgba("#a8d8ff", 210), P("#4a4a52", "#5a5a62", "#6a6a72"),
                       rgba("#c8ecff"), rgba("#a8d8ff", 255), lichen=P("#6a7a3e", "#7e8c4a", "#8e8a6a"))


# ---------------------------------------------------------------------------------------------------------------------
# Sporewild


def _mushroom_paint(m, rng, cap, spots_cols, spot_glow, gills, gill_glow, stem, vein, eye_col, eye_glow, big):
    skin, glow, nb, _ = setup(m)
    for key in ("cap", "crown"):
        f = faces(nb[key])
        for name, r in f.items():
            if name == "bottom":
                x, y, w, h = r
                for j in range(h):
                    for i in range(w):
                        cx, cy = i - (w - 1) / 2.0, j - (h - 1) / 2.0
                        ang = math.atan2(cy, cx)
                        line = abs((ang / (math.pi / 10)) % 1.0 - 0.5) < 0.18
                        skin.set(x + i, y + j, gills[1] if line else gills[0])
                        if line and gill_glow is not None:
                            glow.set(x + i, y + j, gill_glow)
                continue
            noise(skin, r, cap, rng, 3, grad=(lambda i, j, w, h: 0.12) if name == "top" else vgrad(0.15, -0.2))
            if name in ("west", "east", "north", "south"):
                x, y, w, h = r
                for i in range(w):
                    skin.set(x + i, y + h - 1, shade(cap[0], 0.85))
            n = r[2] * r[3] / (26 if big else 14)
            spots(skin, r, spots_cols, rng, n, rad=1.6 if big else 0.8, g=glow, gcol=spot_glow)
    for key in ("stem", "arm", "leg"):
        coat(skin, nb[key], stem, rng, scale=2)
        for name in ("west", "north", "east", "south"):
            r = faces(nb[key])[name]
            x, y, w, h = r
            for _ in range(int(w * h / 10)):
                vx, vy = x + rng.randrange(w), y + rng.randrange(h)
                for k in range(rng.randint(2, 4)):
                    skin.set(vx, vy + k, vein)
    x, y, w, h = faces(nb["stem"])["north"]
    with F.EYE.eyes(skin, glow):
        for ex in (x + 1, x + w - 2):
            eye(skin, glow, ex, y + 2, eye_col, eye_glow, 1, 2 if big else 1, shine=False)
    for i in range(2, w - 2):
        skin.set(x + i, y + 5, shade(stem[0], 0.6))
    for name in ("west", "north", "east", "south"):
        x, y, w, h = faces(nb["leg"])[name]
        for i in range(w):
            skin.set(x + i, y + h - 1, shade(stem[0], 0.6))
    return skin, glow


def paint_shroomlet(m, rng):
    return _mushroom_paint(m, rng, P("#7a3250", "#984666", "#b65e7e", "#cf7e98", "#e29cb2"), P("#f6ecdc", "#ece0cc"),
                           rgba("#ff9ae0", 90), P("#cfae9e", "#b8907e"), None, P("#d6cab4", "#e4dac6", "#f0e8d8"), rgba("#bcae96"),
                           rgba("#1a1014"), rgba("#ff9ae0", 120), False)


def paint_sporeback_snail(m, rng):
    def garden(skin, glow, nb):
        for name, r in faces(nb["stem"]).items():
            noise(skin, r, P("#e2d8c8", "#ece4d6", "#f6f0e6"), rng, 2)
        for name, r in faces(nb["cap"]).items():
            if name == "bottom":
                solid(skin, r, rgba("#e0c8b8"))
                continue
            noise(skin, r, P("#a01c24", "#b8262e", "#d0383c"), rng, 2)
            spots(skin, r, P("#f8f0e6"), rng, r[2] * r[3] / 7, rad=0.6, g=glow, gcol=rgba("#ff9ae0", 80))
        fs = faces(nb["shell"])
        for name in ("top", "west", "east", "south", "north"):
            dots(skin, fs[name], P("#9a8aa0", "#b4a6b8"), rng, fs[name][2] * fs[name][3] / 6)
    return _snail_paint(m, rng, P("#7e5e56", "#98766c", "#b28e82", "#c8a698"), P("#d8bcb0", "#e8d0c4"),
                        P("#3a2618", "#523822", "#6c4c30", "#866240"), rgba("#2a1a10"), None, rgba("#ff9ae0", 140), garden)


def paint_mycelord(m, rng):
    return _mushroom_paint(m, rng, P("#360a22", "#521230", "#701c40", "#922852", "#b43a66"), P("#ffb8e8", "#ffd6f2"),
                           rgba("#ff9ae0", 230), P("#7a3a5a", "#ffb8e8"), rgba("#ffb8e8", 170), P("#c4b6a8", "#d4c8bc", "#e2d8ce"),
                           rgba("#8a7a8c"), rgba("#ff5ad0"), rgba("#ff7ae0", 255), True)


PAINTERS = {
    "bogwick": paint_bogwick, "mudskipper": paint_mudskipper, "bog_toad": paint_bog_toad, "mire_mother": paint_mire_mother,
    "dart_frog": paint_dart_frog, "crested_basilisk": paint_crested_basilisk, "canopy_prowler": paint_canopy_prowler,
    "plumed_coil": paint_plumed_coil, "gloam_snail": paint_gloam_snail, "hollow_owl": paint_hollow_owl, "thornling": paint_thornling,
    "weaver_queen": paint_weaver_queen, "petalwing": paint_petalwing, "honey_bear": paint_honey_bear, "empress_moth": paint_empress_moth,
    "scree_marmot": paint_scree_marmot, "peak_kite": paint_peak_kite, "crag_ram": paint_crag_ram, "stonehorn": paint_stonehorn,
    "shroomlet": paint_shroomlet, "sporeback_snail": paint_sporeback_snail, "mycelord": paint_mycelord,
}

# Egg colours: (shell, spots)
EGG = {
    "bogwick": ("#3a3320", "#d8ff9a"), "mudskipper": ("#635a32", "#5cc8e8"), "bog_toad": ("#465628", "#d0c690"), "mire_mother": ("#2a3418", "#b8ff6a"),
    "dart_frog": ("#1e62d4", "#0a0c14"), "crested_basilisk": ("#1e6e3e", "#ffd24a"), "canopy_prowler": ("#1a1720", "#7cffb0"),
    "plumed_coil": ("#1a6e54", "#e8402a"), "gloam_snail": ("#524a5e", "#9cf0ff"), "hollow_owl": ("#786e5c", "#ffb030"),
    "thornling": ("#342414", "#ff8a3a"), "weaver_queen": ("#22192c", "#d8c8ff"), "petalwing": ("#f7c9e6", "#c070a8"),
    "honey_bear": ("#784818", "#ffb020"), "empress_moth": ("#56204a", "#ffd27a"), "scree_marmot": ("#655343", "#d8ccb4"),
    "peak_kite": ("#e6e9ef", "#3e434c"), "crag_ram": ("#b6aea2", "#4a423a"), "stonehorn": ("#5a5a62", "#a8d8ff"),
    "shroomlet": ("#b65e7e", "#f6ecdc"), "sporeback_snail": ("#98766c", "#d0383c"), "mycelord": ("#701c40", "#ffb8e8"),
}


def egg(mid, beast, rng):
    saved = dict(F.EGG)
    F.EGG.clear()
    F.EGG.update(EGG)
    try:
        return F.egg(mid, beast, rng)
    finally:
        F.EGG.clear()
        F.EGG.update(saved)


# ---- item icons (beast parts and family materials) ------------------------------------------------------------------


def _icon(points, colours, outline=True):
    """A filled polygon shaded diagonally from colours[0] (light) to colours[-1] (dark)."""
    c = Canvas()
    xs, ys = [p[0] for p in points], [p[1] for p in points]

    def inside(px, py):
        ins, j = False, len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi):
                ins = not ins
            j = i
        return ins
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                t = ((x - min(xs)) / max(1, max(xs) - min(xs)) * 0.5 + (y - min(ys)) / max(1, max(ys) - min(ys)) * 0.5)
                c.set(x, y, colours[min(len(colours) - 1, int(t * len(colours)))])
    return _outline(c) if outline else c


def _feather(light, mid, dark, bars=None):
    c = _icon([(13, 1), (15, 3), (9, 11), (5, 14), (3, 13), (6, 8)], [rgba(light), rgba(mid), rgba(dark)])
    for k in range(10):
        c.set(3 + k, 14 - k, rgba(dark))
    c.set(2, 15, rgba(dark))
    if bars:
        for k in range(2, 10, 2):
            c.set(5 + k, 12 - k, rgba(bars))
            c.set(6 + k, 12 - k, rgba(bars))
    return c


def _pile(cols, seed):
    rng = rng_for("fauna2_pile", seed)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 11.5) / 3.6)
            if d <= 1.0 and rng.random() < 0.92:
                c.set(x, y, rgba(cols[min(len(cols) - 1, int((1 - d) * len(cols) + rng.random() * 0.8))]))
    for _ in range(7):
        c.set(rng.randrange(3, 13), rng.randrange(3, 9), rgba(cols[-1]))
    return _outline(c)


def _blob(cols, hi):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 5.8, (y - 8.8) / 5.0)
            if d <= 1.0:
                c.set(x, y, rgba(cols[min(len(cols) - 1, int(d * len(cols)))]))
    c.set(5, 6, rgba(hi))
    c.set(6, 5, rgba(hi))
    return _outline(c)


def material_icon(mid):
    if mid == "mire_hide":
        c = _icon([(2, 4), (7, 2), (13, 3), (14, 9), (11, 14), (4, 13), (1, 9)], P("#7a8848", "#566832", "#38441e", "#2a3418"))
        for x, y in ((5, 6), (9, 5), (11, 9), (6, 10), (8, 8)):
            c.set(x, y, rgba("#92a058"))
        return c
    if mid == "mire_glowsac":
        return _blob(["#f8ffd0", "#d8f27e", "#b8e05a", "#7aa83a"], "#ffffff")
    if mid == "canopy_venom":
        c = _icon([(8, 1), (12, 8), (12, 11), (10, 14), (6, 14), (4, 11), (4, 8)], P("#7ff4ff", "#3a86ee", "#1e62d4", "#0c2e78"))
        c.set(6, 9, rgba("#ffffff"))
        return c
    if mid == "canopy_scale":
        c = _icon([(8, 1), (14, 6), (12, 13), (8, 15), (4, 13), (2, 6)], P("#52b874", "#2e8c52", "#1e6e3e", "#14502e"))
        for y in range(4, 13, 3):
            for x in range(4, 13):
                if c.get(x, y)[3] and (x + y) % 2 == 0:
                    c.set(x, y, rgba("#5ce8d8"))
        return c
    if mid == "dusk_shell":
        c = _blob(["#485266", "#384052", "#2a3040", "#1e2230"], "#9cf0ff")
        for k in range(14):
            a = k * 0.55
            r = 1.0 + k * 0.32
            c.set(int(round(7.5 + math.cos(a) * r)), int(round(8.8 + math.sin(a) * r)), rgba("#5cc8d8"))
        return c
    if mid == "dusk_feather":
        return _feather("#b0a690", "#786e5c", "#463f34", bars="#2e2820")
    if mid == "blossom_dust":
        return _pile(["#c070a8", "#e8a0d0", "#f7c9e6", "#fff0f8"], mid)
    if mid == "blossom_fur":
        c = _icon([(3, 13), (5, 5), (8, 2), (11, 5), (13, 13), (8, 11)], P("#c49048", "#a8742e", "#8c5a22", "#6a4018"))
        for k in range(4, 12, 2):
            c.set(k, 9 + (k % 3), rgba("#ffb020"))
        return c
    if mid == "crag_horn":
        return _icon([(3, 14), (2, 9), (4, 4), (8, 1), (13, 2), (14, 5), (10, 4), (7, 6), (6, 10), (6, 14)],
                     P("#bcb4a8", "#857e72", "#5a5048", "#3a342e"))
    if mid == "crag_feather":
        return _feather("#ffffff", "#dce0e8", "#8a8e98", bars="#3e434c")
    if mid == "spore_dust":
        return _pile(["#4f3157", "#9a6f86", "#c9a0b0", "#ffd6f2"], mid)
    if mid == "spore_gel":
        return _blob(["#ffd6f2", "#e29cb2", "#b65e7e", "#7a3250"], "#ffffff")
    return shard("dream")


def part_icon(p):
    pid = p["id"]
    if pid == "toadstone":
        c = Canvas()
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 7.5) / 5.0, (y - 8.0) / 4.2)
                if d <= 1.0:
                    c.set(x, y, mix(rgba("#e8ffb0"), rgba("#4e7a2a"), d))
        for x, y in ((5, 6), (6, 5), (7, 5)):
            c.set(x, y, rgba("#ffffff"))
        return _outline(c)
    if pid == "coil_plume":
        c = _feather("#ff9a2a", "#e8402a", "#7a1a14")
        for k in range(2, 9):
            c.set(6 + k, 11 - k, rgba("#2ad8c8"))
        return c
    if pid == "queen_silk":
        c = Canvas()
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 7.5) / 5.5, (y - 8.0) / 5.5)
                if d <= 1.0:
                    c.set(x, y, rgba("#e8dcff") if (x + y * 2) % 4 else rgba("#a898c8"))
        for x in range(2, 14):
            c.set(x, 8, rgba("#d8c8ff"))
        return _outline(c)
    if pid == "empress_scale":
        c = _icon([(8, 1), (14, 6), (13, 12), (8, 15), (3, 12), (2, 6)], P("#ffb8e0", "#bc5c9e", "#782e66", "#3a1230"))
        for y in range(16):
            for x in range(16):
                if c.get(x, y)[3] and abs(math.hypot(x - 8, y - 8) - 2.5) < 0.8:
                    c.set(x, y, rgba("#ffd27a"))
        return c
    if pid == "stonehorn_shard":
        c = _icon([(4, 15), (2, 10), (4, 4), (9, 1), (12, 3), (8, 6), (7, 11), (8, 15)], P("#94949c", "#62626a", "#45454c", "#2e2e34"))
        for k in range(6):
            c.set(5 + (k % 2), 4 + k * 2, rgba("#a8d8ff"))
        return c
    if pid == "mycel_heart":
        c = _blob(["#ffd6f2", "#ff9ae0", "#b43a66", "#521230"], "#ffffff")
        for k in range(8):
            c.set(7 + (k % 3) - 1, 2 + k, rgba("#8a7a8c")) if k > 5 else None
        return c
    return shard(p["ramp"])


def build(out):
    for m in creatures():
        rng = rng_for("fauna2", m["id"])
        if m["model"] in MODELS:  # hero bodies (beasts, the owl, the bogwick) are painted by heroes.py
            F.EYE.clear()
            skin, glow = PAINTERS[m["id"]](m, rng)
            out.entity("mobs/" + m["id"], skin)
            out.entity("mobs/" + m["id"] + "_glow", glow)
            F.write_blink(out, m, skin, glow)
        out.item(m["id"] + "_spawn_egg", egg(m["id"], m["role"] == "BEAST", rng_for("fauna2_egg", m["id"])))
        for p in m["parts"]:
            out.item(p["id"], part_icon(p))
    for mid in MATERIALS:
        out.item(mid, material_icon(mid))


# ---------------------------------------------------------------------------------------------------------------------
# Content: biome modifiers, loot tables, item models, lang, lairs. Weights are relative inside the fauna category.

# Spawners per modifier (biomes: the tag #awaken:fauna/<group> of the biome table, tools/terrain/biomes.py)
SPAWNS = {
    "fauna_mire": [("bogwick", 26, 2, 4), ("mudskipper", 24, 2, 3), ("bog_toad", 18, 1, 2), ("mire_mother", 1, 1, 1)],
    "fauna_canopy": [("dart_frog", 28, 2, 3), ("crested_basilisk", 18, 1, 2), ("canopy_prowler", 22, 1, 2), ("plumed_coil", 1, 1, 1)],
    "fauna_duskwood": [("gloam_snail", 26, 1, 3), ("hollow_owl", 14, 1, 1), ("thornling", 24, 2, 3), ("weaver_queen", 1, 1, 1)],
    "fauna_blossom": [("petalwing", 30, 2, 4), ("honey_bear", 14, 1, 2), ("lumen_moth", 14, 2, 3), ("empress_moth", 1, 1, 1)],
    "fauna_blossom_meadow": [("petalwing", 12, 1, 3)],
    "fauna_crag": [("scree_marmot", 26, 2, 3), ("peak_kite", 16, 1, 2), ("crag_ram", 18, 1, 3), ("stonehorn", 1, 1, 1)],
    "fauna_spore": [("shroomlet", 30, 2, 4), ("sporeback_snail", 20, 1, 2), ("mycelord", 1, 1, 1)],
}

# Materials: (item, min, max, chance, looting). A chance below 1 is a killed-by-player roll.
DROPS = {
    "bogwick": [("awaken:mire_glowsac", 1, 1, 0.5, True), ("minecraft:glowstone_dust", 0, 1, 1.0, True)],
    "mudskipper": [("minecraft:clay_ball", 1, 2, 1.0, True), ("minecraft:slime_ball", 0, 1, 1.0, True)],
    "bog_toad": [("awaken:mire_hide", 1, 1, 0.45, True), ("minecraft:slime_ball", 1, 2, 1.0, True)],
    "mire_mother": [("awaken:mire_hide", 3, 5, 1.0, True), ("awaken:mire_glowsac", 2, 3, 1.0, True), ("minecraft:slime_ball", 3, 6, 1.0, True)],
    "dart_frog": [("awaken:canopy_venom", 1, 1, 0.6, True)],
    "crested_basilisk": [("awaken:canopy_scale", 1, 1, 0.5, True), ("minecraft:leather", 0, 1, 1.0, True)],
    "canopy_prowler": [("minecraft:leather", 1, 2, 1.0, True), ("minecraft:bone", 0, 1, 1.0, True)],
    "plumed_coil": [("awaken:canopy_scale", 3, 5, 1.0, True), ("awaken:canopy_venom", 2, 3, 1.0, True), ("minecraft:feather", 2, 4, 1.0, True)],
    "gloam_snail": [("awaken:dusk_shell", 1, 1, 0.5, True), ("minecraft:glow_lichen", 0, 1, 1.0, False)],
    "hollow_owl": [("awaken:dusk_feather", 1, 2, 0.6, True), ("minecraft:feather", 1, 2, 1.0, True)],
    "thornling": [("minecraft:stick", 1, 3, 1.0, True), ("minecraft:string", 0, 1, 1.0, True)],
    "weaver_queen": [("awaken:dusk_shell", 2, 3, 1.0, True), ("minecraft:string", 4, 8, 1.0, True), ("minecraft:spider_eye", 1, 2, 1.0, False)],
    "petalwing": [("awaken:blossom_dust", 1, 1, 0.6, True), ("minecraft:pink_petals", 0, 1, 1.0, False)],
    "honey_bear": [("awaken:blossom_fur", 1, 1, 0.5, True), ("minecraft:honeycomb", 0, 1, 1.0, True)],
    "empress_moth": [("awaken:blossom_dust", 3, 6, 1.0, True), ("awaken:blossom_fur", 1, 2, 1.0, False), ("minecraft:pink_petals", 2, 4, 1.0, False)],
    "scree_marmot": [("minecraft:flint", 0, 1, 1.0, True), ("minecraft:rabbit_hide", 0, 1, 1.0, True)],
    "peak_kite": [("awaken:crag_feather", 1, 2, 0.6, True), ("minecraft:feather", 0, 1, 1.0, True)],
    "crag_ram": [("awaken:crag_horn", 1, 1, 0.35, True), ("minecraft:white_wool", 1, 2, 1.0, False), ("minecraft:mutton", 1, 2, 1.0, True)],
    "stonehorn": [("awaken:crag_horn", 2, 4, 1.0, True), ("minecraft:iron_ingot", 1, 2, 1.0, True), ("minecraft:mutton", 2, 4, 1.0, True)],
    "shroomlet": [("awaken:spore_dust", 1, 1, 0.6, True), ("minecraft:red_mushroom", 0, 1, 1.0, False), ("minecraft:brown_mushroom", 0, 1, 1.0, False)],
    "sporeback_snail": [("awaken:spore_gel", 1, 1, 0.5, True), ("minecraft:brown_mushroom", 0, 2, 1.0, False)],
    "mycelord": [("awaken:spore_dust", 3, 6, 1.0, True), ("awaken:spore_gel", 2, 3, 1.0, True), ("minecraft:red_mushroom", 2, 4, 1.0, False)],
}

MATERIALS = ["mire_hide", "mire_glowsac", "canopy_venom", "canopy_scale", "dusk_shell", "dusk_feather", "blossom_dust", "blossom_fur",
             "crag_horn", "crag_feather", "spore_dust", "spore_gel"]
MATERIAL_NAMES = {"mire_hide": "Mire Hide", "mire_glowsac": "Mire Glowsac", "canopy_venom": "Canopy Venom", "canopy_scale": "Canopy Scale",
                  "dusk_shell": "Dusk Shell", "dusk_feather": "Dusk Feather", "blossom_dust": "Blossom Dust", "blossom_fur": "Blossom Fur",
                  "crag_horn": "Crag Horn", "crag_feather": "Crag Feather", "spore_dust": "Spore Dust", "spore_gel": "Spore Gel"}

# Lairs: beast -> (style, spacing, separation, salt). Spacing in chunks: 112 = 1,792 blocks, 96 = 1,536; the mushroom
# fields are rare islands, so the Mycelord's set is denser (40 = 640 blocks) or its lair would almost never be. Where a
# lair may stand is the biome table's tag #awaken:has_lair/<beast> (tools/terrain/biomes.py).
LAIRS = {
    "briar_sow": ("wallow", 112, 48, 371920451),
    "dune_wyrm": ("glass_hollow", 112, 48, 593017722),
    "old_antler": ("antler_grove", 112, 48, 820374615),
    "mire_mother": ("toad_throne", 96, 40, 145882309),
    "plumed_coil": ("serpent_shrine", 96, 40, 672910384),
    "weaver_queen": ("silk_hollow", 96, 40, 238475611),
    "empress_moth": ("petal_bower", 96, 40, 904716253),
    "stonehorn": ("rams_cairn", 96, 40, 517382946),
    "mycelord": ("spore_ring", 40, 16, 763018245),
}


def merge_lang(entries):
    path = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(path)) if os.path.exists(path) else {}
    lang.update(entries)
    F.write_json(path, dict(sorted(lang.items())))


def content():
    ms = creatures()
    for name, spawners in SPAWNS.items():
        F.spawn_modifier(name, spawners)
    F.DROPS.update(DROPS)
    lang = {}
    for m in ms:
        F.write_json(os.path.join(RES, "data/awaken/loot_table/entities", m["id"] + ".json"), F.loot_table(m))
        egg_id = m["id"] + "_spawn_egg"
        F.item_model(egg_id)
        lang["entity.awaken." + m["id"]] = m["name"]
        lang["item.awaken." + egg_id] = m["name"] + " Spawn Egg"
        for p in m["parts"]:
            F.item_model(p["id"])
            lang["item.awaken." + p["id"]] = p["name"]
    for mid in MATERIALS:
        F.item_model(mid)
        lang["item.awaken." + mid] = MATERIAL_NAMES[mid]
    merge_lang(lang)
    for beast, (style, spacing, separation, salt) in LAIRS.items():
        F.write_json(os.path.join(RES, "data/awaken/worldgen/structure/lair", beast + ".json"), {
            "type": "awaken:fauna_lair", "lair": style, "biomes": "#awaken:has_lair/" + beast, "step": "surface_structures",
            "spawn_overrides": {}, "terrain_adaptation": "beard_thin"})
        F.write_json(os.path.join(RES, "data/awaken/worldgen/structure_set/lair", beast + ".json"), {
            "structures": [{"structure": "awaken:lair/" + beast, "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
    F.write_json(os.path.join(RES, "data/awaken/tags/worldgen/structure/fauna_lairs.json"), {"values": ["awaken:lair/" + b for b in LAIRS]})
    print(f"fauna2: {len(ms)} creatures, {len(SPAWNS)} biome modifiers, {len(MATERIALS)} materials, {len(LAIRS)} lairs")


# ---------------------------------------------------------------------------------------------------------------------
# Preview sheets (look at these before shipping a creature)


def composite(m, skin, glow):
    from PIL import Image
    if m["model"] in OWN:
        base = skin.image()
    else:
        vb = vanilla(m["model"]).resize((skin.w, skin.h), Image.NEAREST).copy()
        t = rgba(m["tint"] or "#ffffff")
        vpx = vb.load()
        for y in range(vb.height):
            for x in range(vb.width):
                r, g, b, a = vpx[x, y]
                vpx[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
        sk = skin.image()
        base = Image.alpha_composite(vb, Image.composite(sk, Image.new("RGBA", sk.size), sk.split()[3].point(lambda a: 255 if a >= 128 else 0)))
    g = glow.image()
    return Image.alpha_composite(base, g.copy()), base, g


BG = {"MIRE": (70, 84, 62), "CANOPY": (56, 96, 60), "DUSKWOOD": (58, 52, 66), "BLOSSOM": (170, 140, 160), "CRAG": (140, 146, 156), "SPORE": (120, 100, 120)}


def preview(outdir, only=None):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    ms = [m for m in creatures() if (not only or m["id"] in only) and m["model"] in MODELS]  # hero bodies: heroes.py preview
    cell = (300, 240)
    sheet = Image.new("RGBA", (cell[0] * 4, (cell[1] + 18) * len(ms)), (24, 26, 34, 255))
    dr = ImageDraw.Draw(sheet)
    for k, m in enumerate(ms):
        skin, glow = PAINTERS[m["id"]](m, rng_for("fauna2", m["id"]))
        lit, base, g = composite(m, skin, glow)
        size, tree = MODELS[m["model"]]()
        views = [render(tree, lit, yaw=35, pitch=20, size=cell), render(tree, lit, yaw=110, pitch=12, size=cell),
                 render(tree, lit, yaw=-150, pitch=35, size=cell), render(tree, base, yaw=35, pitch=20, size=cell, night=g)]
        y = k * (cell[1] + 18)
        for i, im in enumerate(views):
            bg = Image.new("RGBA", cell, (8, 10, 22, 255) if i == 3 else BG.get(m["theme"], (90, 90, 90)) + (255,))
            bg.alpha_composite(im, (0, 0))
            sheet.paste(bg, (i * cell[0], y))
        dr.text((6, y + cell[1] + 2), f"{m['name']} ({m['role'].lower()}, {m['model'].lower()}, x{m['scale']})", fill=(220, 225, 235, 255))
    sheet.save(os.path.join(outdir, "fauna2_views.png"))
    unit = 4.0
    rendered = []
    for m in ms:
        skin, glow = PAINTERS[m["id"]](m, rng_for("fauna2", m["id"]))
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
    lu.save(os.path.join(outdir, "fauna2_lineup.png"))
    # the icons, big
    icons = [(mid, material_icon(mid)) for mid in MATERIALS] + [(p["id"], part_icon(p)) for m in creatures() for p in m["parts"]]
    ic = Image.new("RGBA", (len(icons) * 72 + 8, 96), (40, 42, 50, 255))
    idr = ImageDraw.Draw(ic)
    for i, (mid, c) in enumerate(icons):
        ic.alpha_composite(c.image().resize((64, 64), Image.NEAREST), (8 + i * 72, 4))
        idr.text((8 + i * 72, 72), mid[:11], fill=(220, 225, 235, 255))
    ic.save(os.path.join(outdir, "fauna2_icons.png"))
    print(os.path.join(outdir, "fauna2_views.png"))


# ---------------------------------------------------------------------------------------------------------------------
# Lair look: the GameTest fauna_lair_styles_build writes each built lair to run/fauna_lairs/; this draws them.

BLOCK_TEX = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block"
TINTS = {"grass_block": "#79c05a", "oak_leaves": "#59ae30", "jungle_leaves": "#30bb0b", "dark_oak_leaves": "#59ae30", "vine": "#59ae30",
         "fern": "#79c05a", "lily_pad": "#208030", "bush": "#79c05a", "big_dripleaf": "#79c05a"}
FIXED = {"water": "#3f76e4", "snow": "#f8fcff", "snow_block": "#f0f6fa", "glass": "#c8e4ec", "cobweb": "#e8e8ee", "lantern": "#e8a040",
         "soul_lantern": "#5ad8e8", "sweet_berry_bush": "#3a6a2a", "shroomlight": "#f0a050", "spore_blossom": "#e070b0",
         "pink_petals": "#f0a8d0", "leaf_litter": "#a0703a", "firefly_bush": "#4a6a2a", "glow_lichen": "#7ad8b0", "skeleton_skull": "#d8d8d0",
         "spruce_fence": "#6a4a2a", "stone_brick_wall": "#7a7a7a", "red_mushroom": "#c03030", "brown_mushroom": "#9a6a48", "bamboo": "#6aa83a",
         "dead_bush": "#8a6a3a", "flowering_azalea": "#6a8a3a", "azalea": "#5a7a32", "pink_tulip": "#e898c0", "allium": "#b070d8",
         "smooth_sandstone": "#e0d4a0", "smooth_red_sandstone": "#b85a20", "orange_stained_glass": "#e8902a", "yellow_stained_glass": "#f0d850"}
_COL = {}


def block_colour(name, face):
    key = (name, face)
    if key in _COL:
        return _COL[key]
    from PIL import Image
    col = None
    if name in FIXED:
        col = rgba(FIXED[name])
    else:
        cands = ([name + "_top"] if face == "top" else []) + [name, name.replace("_block", ""), name + "_side", name + "_front"]
        if name.endswith("_log"):
            cands = ([name + "_top"] if face == "top" else []) + [name]
        if name == "grass_block":
            cands = ["grass_block_top"] if face == "top" else ["dirt"]
        if name == "mycelium":
            cands = ["mycelium_top"] if face == "top" else ["mycelium_side"]
        if name == "podzol":
            cands = ["podzol_top"] if face == "top" else ["podzol_side"]
        for c in cands:
            path = os.path.join(BLOCK_TEX, c + ".png")
            if os.path.exists(path):
                im = Image.open(path).convert("RGBA").resize((1, 1), Image.BOX)
                col = im.getpixel((0, 0))
                break
        if col is None:
            col = (200, 0, 200, 255)
        tint = TINTS.get(name)
        if tint is not None and not (name == "grass_block" and face != "top"):
            t = rgba(tint)
            col = (col[0] * t[0] // 255, col[1] * t[1] // 255, col[2] * t[2] // 255, 255)
    _COL[key] = col[:3] + (255,)
    return _COL[key]


def render_lair(data, scale=9):
    from PIL import Image, ImageDraw
    blocks = data["blocks"]
    W, H = 64 * scale, 52 * scale
    img = Image.new("RGBA", (W, H), (150, 170, 190, 255))
    dr = ImageDraw.Draw(img)
    ox, oy = W // 2, int(H * 0.62)
    occ = {(b[0], b[1], b[2]) for b in blocks}

    def iso(x, y, z):
        return (ox + (x - z) * scale, oy + (x + z) * scale * 0.5 - y * scale)
    small = {"snow", "pink_petals", "leaf_litter", "moss_carpet", "pale_moss_carpet", "lily_pad", "glow_lichen"}
    for x, y, z, name in sorted(blocks, key=lambda b: (b[0] + b[2], b[1])):
        h = 0.15 if name in small else 1.0
        top = [iso(x, y + h, z), iso(x + 1, y + h, z), iso(x + 1, y + h, z + 1), iso(x, y + h, z + 1)]
        left = [iso(x, y + h, z + 1), iso(x + 1, y + h, z + 1), iso(x + 1, y, z + 1), iso(x, y, z + 1)]
        right = [iso(x + 1, y + h, z), iso(x + 1, y + h, z + 1), iso(x + 1, y, z + 1), iso(x + 1, y, z)]
        if (x, y + 1, z) not in occ or h < 1:
            dr.polygon(top, fill=block_colour(name, "top"))
        if (x, y, z + 1) not in occ:
            dr.polygon(left, fill=shade(block_colour(name, "side"), 0.78))
        if (x + 1, y, z) not in occ:
            dr.polygon(right, fill=shade(block_colour(name, "side"), 0.62))
    dx, dy, dz = data["den"]
    px, py = iso(dx + 0.5, dy, dz + 0.5)
    dr.ellipse((px - 4, py - 4, px + 4, py + 4), outline=(255, 40, 40, 255), width=2)
    dr.text((8, 8), f"{data['style']} ({data['variant']})  den ring in red", fill=(20, 20, 30, 255))
    return img


def lairs(outdir, src=os.path.join(ROOT, "run/fauna_lairs")):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    files = sorted(f for f in os.listdir(src) if f.endswith(".json"))
    ims = [render_lair(json.load(open(os.path.join(src, f)))) for f in files]
    for f, im in zip(files, ims):
        im.save(os.path.join(outdir, "lair_" + f[:-5] + ".png"))
    print(len(ims), "lairs drawn to", outdir)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "lairs":
        lairs(sys.argv[2] if len(sys.argv) > 2 else "/tmp/fauna2_lairs")
    elif len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/fauna2_preview", sys.argv[3:] or None)
    else:
        print(__doc__)
