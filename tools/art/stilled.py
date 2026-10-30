#!/usr/bin/env python3
"""The Stilled's textures (SPEC 43.3): five stone figures (man, woman, old man, old woman, young), their belongings,
the left chest (an old travelling trunk) and the letter, the tagged key and the keepsake.

The figures are weathered stone with the faintest colour of the clothes they had on, lichen on the tops and on one side,
and fine cracks; what they carried keeps its own faded colour, so they read as people turned to stone with real things.
The layout matches StilledModel (128 x 128): each box's faces as Minecraft lays them out.

    python3 tools/art/stilled.py            # writes into src/main/resources/assets/awaken/textures/
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "awaken", "textures")


def faces(u, v, w, h, d):
    """A box's faces as (x0, y0, x1, y1): top, bottom, right, front, left, back."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


class Painter:
    def __init__(self, img, seed):
        self.img = img
        self.px = img.load()
        self.r = random.Random(seed)

    def put(self, x, y, c, a=255):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def stone(self, rect, tint, t=0.16, light=1.0):
        """Weathered stone over a rectangle, tinted with what the cloth or skin was."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                g = self.r.randint(118, 146)
                base = (g, g, int(g * 0.97))
                c = mix(base, tint, t)
                if self.r.random() < 0.07:
                    c = shade(c, 0.86)
                elif self.r.random() < 0.05:
                    c = shade(c, 1.1)
                self.put(x, y, shade(c, light))

    def lichen(self, rect, amount):
        x0, y0, x1, y1 = rect
        for _ in range(int((x1 - x0) * (y1 - y0) * amount)):
            x, y = self.r.randrange(x0, x1), self.r.randrange(y0, y1)
            c = self.r.choice([(154, 166, 74), (176, 178, 112), (122, 140, 66), (201, 199, 160)])
            self.put(x, y, c)
            if self.r.random() < 0.5:
                self.put(x + self.r.choice([-1, 1]), y, shade(c, 0.9))

    def crack(self, rect):
        x0, y0, x1, y1 = rect
        if x1 - x0 < 3 or y1 - y0 < 3:
            return
        x, y = self.r.randrange(x0, x1), y0 + self.r.randrange(0, max(1, (y1 - y0) // 2))
        for _ in range(self.r.randint(2, 5)):
            if not (x0 <= x < x1 and y0 <= y < y1):
                break
            self.put(x, y, (84, 84, 82))
            y += 1
            x += self.r.choice([-1, 0, 0, 1])

    def clear(self, rect):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[x, y] = (0, 0, 0, 0)

    def fill(self, rect, colour, jitter=10, a=255):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                j = self.r.randint(-jitter, jitter)
                self.put(x, y, tuple(max(0, min(255, c + j)) for c in colour), a)


# ---------------------------------------------------------------------------------------------------------------------
# The figures (SPEC 43.3): Carillon's own people gone to stone. Their bodies are the citizens' (the player mesh, with
# elbows and knees split so a pose can bend), their faces and clothes are citizens' skins with the eyes shut, turned to
# weathered stone with moss on whatever faced the sky. Everything is one rig (assets/awaken/rigs/stilled.json, read by
# the game and by tools/art/rig.py) and one 128 x 128 texture per figure: the skin top left, the props round it.

RES = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources"))
RIG = os.path.join(RES, "assets", "awaken", "rigs", "stilled.json")
CITIZEN = os.path.join(ROOT, "entity", "citizen")
ROSTER = os.path.join(RES, "data", "awaken", "city", "citizens.json")
PER_LOOK = 6
# look -> (roster age, male?); young and child take either
LOOK_AGES = {"man": (2, True), "woman": (2, False), "old_man": (3, True), "old_woman": (3, False), "young": (1, False), "child": (0, None)}  # "young" is a girl (StilledText.Look)
TEX = 128
E = -0.05  # a joint's filler sits just inside its limb: straight, it never shows; bent, it rounds the joint


def C(uv, frm, size, g=0.0, mirror=False):
    return {"uv": list(uv), "from": list(frm), "size": list(size), "inflate": g, "mirror": mirror}


def part(name, parent, pivot, cubes, rot=(0, 0, 0), group="figure"):
    return {"name": name, "parent": parent, "pivot": list(pivot), "rot": list(rot), "cubes": cubes, "group": group}


# the props' boxes in the texture (box UV), as (u, v): the skin keeps 0..64 x 0..64
PROP_UV = {"pack": (64, 0), "bedroll": (82, 0), "pack_ground": (64, 11), "bedroll_ground": (84, 6), "milestone": (64, 24),
           "grave": (92, 24), "grave_cap": (106, 6), "flower": (112, 24), "lantern": (64, 38), "lantern_handle": (80, 38),
           "bundle": (88, 38), "bundle_knot": (116, 38), "collar": (64, 50), "rope": (80, 50), "cart_bed": (0, 64),
           "cart_side": (0, 80), "cart_wheel": (30, 80), "cart_tail": (0, 97)}


def rig_parts():
    P = [part("figure", "", (0, 0, 0), []),
         part("hips", "figure", (0, 12, 0), []),
         part("body", "hips", (0, 0, 0), [C((16, 16), (-4, -12, -2), (8, 12, 4)), C((16, 32), (-4, -12, -2), (8, 12, 4), 0.25)]),
         part("head", "body", (0, -12, 0), [C((0, 0), (-4, -8, -4), (8, 8, 8)), C((32, 0), (-4, -8, -4), (8, 8, 8), 0.5)])]
    # arms and legs: the upper half on the skin's upper rows, the lower half (pivot at the elbow or knee) on the lower,
    # a filler at the joint; sleeves and trousers the same, the lower 0.01 thinner so the two never fight where they meet
    for side, sx, a_uv, s_uv in (("right", -1, (40, 16), (40, 32)), ("left", 1, (32, 48), (48, 48))):
        P.append(part(side + "_arm", "body", (5 * sx, -10, 0),
                      [C(a_uv, (-1 if sx > 0 else -3, -2, -2), (4, 6, 4)), C(s_uv, (-1 if sx > 0 else -3, -2, -2), (4, 6, 4), 0.25)]))
        P.append(part(side + "_fore", side + "_arm", (sx, 4, 0),
                      [C((a_uv[0], a_uv[1] + 6), (-2, 0, -2), (4, 6, 4)), C((s_uv[0], s_uv[1] + 6), (-2, 0, -2), (4, 6, 4), 0.24),
                       C((a_uv[0], a_uv[1] + 4), (-2, -2, -2), (4, 4, 4), E)]))
    for side, sx, l_uv, p_uv in (("right", -1, (0, 16), (0, 32)), ("left", 1, (16, 48), (0, 48))):
        P.append(part(side + "_leg", "figure", (1.9 * sx, 12, 0), [C(l_uv, (-2, 0, -2), (4, 6, 4)), C(p_uv, (-2, 0, -2), (4, 6, 4), 0.25)]))
        P.append(part(side + "_shin", side + "_leg", (0, 6, 0),
                      [C((l_uv[0], l_uv[1] + 6), (-2, 0, -2), (4, 6, 4)), C((p_uv[0], p_uv[1] + 6), (-2, 0, -2), (4, 6, 4), 0.24),
                       C((l_uv[0], l_uv[1] + 4), (-2, -2, -2), (4, 4, 4), E)]))
    U = PROP_UV
    # what they wore or held (stone, like them) and what stood beside them (the world's own), each shown only by its poses
    P += [part("pack", "body", (0, 0, 0), [C(U["pack"], (-3, -11, 2), (6, 8, 3)), C(U["bedroll"], (-4, -13, 2), (8, 3, 3))], group="props"),
          part("lantern", "right_fore", (0, 6, 0), [C(U["lantern_handle"], (-1, 0, -0.5), (2, 2, 1)), C(U["lantern"], (-2, 2, -2), (4, 5, 4))], group="props"),
          part("pack_ground", "", (9, 24, 3), [C(U["pack_ground"], (-3, -8, -2), (6, 8, 4)), C(U["bedroll_ground"], (-4, -11, -1.5), (8, 3, 3))],
               rot=(0, -20, 0), group="props"),
          part("milestone", "", (0, 24, 1), [C(U["milestone"], (-4, -8, -3), (8, 8, 6))], group="props"),
          part("grave", "", (0, 24, -9), [C(U["grave"], (-4, -11, -1), (8, 11, 2)), C(U["grave_cap"], (-3, -12, -1), (6, 1, 2)),
                                          C(U["flower"], (-3, -2, -2.5), (1, 2, 1)), C(U["flower"], (2, -2, -2.2), (1, 2, 1))], group="props"),
          part("bundle", "", (0, 24, 1), [C(U["bundle"], (-4, -5, -3), (8, 5, 6)), C(U["bundle_knot"], (-1, -7, -1), (2, 2, 2))], group="props"),
          part("collar", "", (-5, 24, -13), [C(U["collar"], (-2, -1, -2), (4, 1, 4)), C(U["rope"], (-0.5, -0.6, -1), (1, 1, 14), mirror=False)],
               rot=(0, 16, 0), group="props"),
          part("cart", "", (4.5, 15, 2), [C(U["cart_bed"], (-13, 0, -1), (13, 2, 14)), C(U["cart_bed"], (0, 0, -1), (13, 2, 14)),
                                          C(U["cart_side"], (-14, -3, -1), (1, 3, 14)), C(U["cart_side"], (13, -3, -1), (1, 3, 14)),
                                          C(U["cart_tail"], (-13, 2, -1), (13, 3, 1)), C(U["cart_tail"], (0, 2, -1), (13, 3, 1)),
                                          C(U["cart_wheel"], (-16, -1, 3), (2, 10, 10)), C(U["cart_wheel"], (14, -1, 3), (2, 10, 10))], group="props")]
    return P


# Poses: rotations in degrees (z, then y, then x, as ModelPart), "lift" "auto" (the lowest point meets the ground, sunk
# a quarter pixel) or pixels down, "props" shown, "second" the other figure of two (its pose, where, how big, who).
POSES = {
    "milestone": {"rot": {"hips": [14, 0, 0], "head": [20, -8, 4], "right_leg": [-80, 8, 0], "right_shin": [80, 0, 0],
                          "left_leg": [-76, -10, 0], "left_shin": [74, 0, 0], "right_arm": [-30, 0, 6], "right_fore": [-34, 0, 0],
                          "left_arm": [-26, 0, -6], "left_fore": [-38, 0, 0]},
                  "lift": "auto", "props": ["milestone", "pack_ground"]},
    "walking": {"rot": {"hips": [10, 0, 0], "head": [-8, 0, 0], "right_leg": [-28, 0, 0], "right_shin": [16, 0, 0],
                        "left_leg": [24, 0, 0], "left_shin": [34, 0, 0], "right_arm": [26, 0, 5], "right_fore": [-12, 0, 0],
                        "left_arm": [-28, 0, -5], "left_fore": [-26, 0, 0]},
                "lift": "auto", "props": ["pack"]},
    "grave": {"rot": {"hips": [12, 0, 0], "head": [30, 0, 0], "right_leg": [4, 0, 4], "right_shin": [86, 0, 0],
                      "left_leg": [-84, 0, -4], "left_shin": [86, 0, 0], "right_arm": [-64, 0, 4], "right_fore": [-16, 0, 0],
                      "left_arm": [-30, 0, -4], "left_fore": [-40, 0, 0]},
              "lift": "auto", "props": ["grave"]},
    "pair": {"rot": {"hips": [4, 0, 0], "head": [10, -12, 0], "right_leg": [-86, 4, 0], "right_shin": [70, 0, 0],
                     "left_leg": [-86, -4, 0], "left_shin": [76, 0, 0], "right_arm": [-28, 0, 4], "right_fore": [-38, 0, 0],
                     "left_arm": [-22, 0, -2], "left_fore": [-42, 0, 0]},
             "lift": 1.0, "props": ["cart"],
             "second": {"pose": "pair_b", "x": 9.5, "z": 0.0, "yaw": 0.0, "scale": 1.0, "look": "woman"}},
    "pair_b": {"rot": {"hips": [6, 0, -6], "head": [14, 8, -16], "right_leg": [-84, 6, 0], "right_shin": [80, 0, 0],
                       "left_leg": [-88, -2, 0], "left_shin": [72, 0, 0], "right_arm": [-26, 0, 10], "right_fore": [-44, 0, 0],
                       "left_arm": [-30, 0, -2], "left_fore": [-40, 0, 0]},
               "lift": 1.0},
    "lead": {"rot": {"hips": [-30, 0, 0], "head": [36, 0, 14], "right_leg": [-62, 8, 0], "right_shin": [-28, 0, 0],
                     "left_leg": [-70, -12, 0], "left_shin": [34, 0, 0], "right_arm": [-14, 0, 26], "right_fore": [-26, 0, 0],
                     "left_arm": [-8, 0, -14], "left_fore": [-22, 0, 0]},
             "lift": "auto", "props": ["pack_ground", "collar"], "move": {"pack_ground": [-8, 0, 6]}},
    "reaching": {"rot": {"hips": [14, 0, 0], "head": [-22, 0, 0], "right_arm": [-98, 0, 6], "right_fore": [-6, 0, 0],
                         "left_arm": [18, 0, -6], "left_fore": [-10, 0, 0], "right_leg": [-22, 0, 0], "right_shin": [10, 0, 0],
                         "left_leg": [16, 0, 0], "left_shin": [22, 0, 0]},
                 "lift": "auto", "props": ["pack"]},
    "looking_back": {"rot": {"hips": [0, 24, 0], "head": [-6, 56, 0], "right_arm": [10, 0, 8], "right_fore": [-6, 0, 0],
                             "left_arm": [-34, 20, -4], "left_fore": [-74, 0, 0], "right_leg": [-14, 0, 0], "right_shin": [8, 0, 0],
                             "left_leg": [10, 0, 0], "left_shin": [12, 0, 0]},
                     "lift": "auto", "props": ["pack"]},
    "waiting": {"rot": {"hips": [22, 0, 0], "head": [32, 0, 0], "right_leg": [-126, 6, 0], "right_shin": [116, 0, 0],
                        "left_leg": [-122, -6, 0], "left_shin": [112, 0, 0], "right_arm": [-58, -10, 8], "right_fore": [-38, 0, -34],
                        "left_arm": [-58, 10, -8], "left_fore": [-38, 0, 34]},
                "lift": "auto"},
    "lantern": {"rot": {"hips": [-4, 0, 0], "head": [-4, -10, 0], "right_arm": [-70, 0, 6], "right_fore": [-12, 0, 0],
                        "left_arm": [6, 0, -6], "left_fore": [-14, 0, 0], "right_leg": [-12, 0, 0], "right_shin": [6, 0, 0],
                        "left_leg": [10, 0, 0], "left_shin": [8, 0, 0], "lantern": [86, 0, -6]},
                "lift": "auto", "props": ["lantern"]},
    "child": {"rot": {"hips": [6, 0, 0], "head": [18, -16, 6], "left_arm": [6, 0, -18], "left_fore": [-12, 0, 0],
                      "right_arm": [-16, 0, 4], "right_fore": [-32, 0, 0], "right_leg": [-16, 0, 0], "right_shin": [10, 0, 0],
                      "left_leg": [14, 0, 0], "left_shin": [16, 0, 0]},
              "lift": "auto", "props": ["pack"],
              "second": {"pose": "child_b", "x": 8.5, "z": -1.0, "yaw": 0.0, "scale": 0.62, "look": "child"}},
    "child_b": {"rot": {"head": [-20, 24, 0], "right_arm": [-14, 0, 40], "right_fore": [-10, 0, 0], "left_arm": [16, 0, -6],
                        "left_fore": [-8, 0, 0], "right_leg": [12, 0, 0], "right_shin": [18, 0, 0], "left_leg": [-18, 0, 0],
                        "left_shin": [10, 0, 0]},
                "lift": "auto"},
    "curled": {"rot": {"figure": [0, 0, 90], "hips": [18, 0, 0], "head": [30, 0, 0], "right_leg": [-74, 0, 0], "right_shin": [92, 0, 0],
                       "left_leg": [-64, 0, 0], "left_shin": [96, 0, 0], "right_arm": [-72, 0, 0], "right_fore": [-62, 0, 0],
                       "left_arm": [-60, 0, 0], "left_fore": [-70, 0, 0]},
               "lift": "auto", "centre": True},
    "bundle": {"rot": {"hips": [30, 0, 0], "head": [-16, 0, 0], "right_leg": [-74, 10, 0], "right_shin": [84, 0, 0],
                       "left_leg": [-74, -10, 0], "left_shin": [84, 0, 0], "right_arm": [-12, 0, 4], "right_fore": [-150, 0, -10],
                       "left_arm": [-12, 0, -4], "left_fore": [-150, 0, 10]},
               "lift": "auto", "props": ["bundle"]},
}
SINK = 0.25


def build_rig():
    sys.path.insert(0, os.path.dirname(__file__))
    import rig as R
    parts = rig_parts()
    props = [q["name"] for q in parts if q["group"] == "props"]
    poses = {}
    for name, p in POSES.items():
        q = {"rot": dict(p.get("rot", {})), "move": {k: list(v) for k, v in p.get("move", {}).items()},
             "hide": [x for x in props if x not in p.get("props", [])]}
        tmp = {"texture": [TEX, TEX], "parts": parts, "poses": {}}
        move = q["move"].setdefault("figure", [0.0, 0.0, 0.0])
        if p.get("centre"):  # a figure lying down: its middle over the block's
            xs, zs = R.extent(tmp, q)
            move[0] -= (xs[0] + xs[1]) / 2
            move[2] -= (zs[0] + zs[1]) / 2
        if p.get("lift") == "auto":
            move[1] += 24 + SINK - R.lowest(tmp, q)
        else:
            move[1] += p.get("lift", 0)
        if "second" in p:
            q["second"] = p["second"]
        poses[name] = q
    rig = {"texture": [TEX, TEX], "parts": parts, "poses": poses}
    os.makedirs(os.path.dirname(RIG), exist_ok=True)
    with open(RIG, "w") as f:
        json.dump(rig, f, separators=(",", ":"))
    return rig


# ---- the skins ------------------------------------------------------------------------------------------------------

MALE = None


def male_names():
    global MALE
    if MALE is None:
        src = open(os.path.join(os.path.dirname(__file__), "specs", "citizens.py")).read()
        block = src.split('MALE_NAMES = set("""', 1)[1].split('"""', 1)[0]
        MALE = set(block.split())
    return MALE


def pick_citizens():
    """Six citizens per look, spread over jobs, from the roster: their closed-eyes skins become the stone."""
    d = json.load(open(ROSTER))
    f = d["citizens"]["fields"]
    ix = {k: i for i, k in enumerate(f)}
    out = {}
    for look, (age, male) in LOOK_AGES.items():
        cands = []
        for r in d["citizens"]["rows"]:
            if r[ix["named"]] or r[ix["age"]] != age or r[ix["job"]] in (6,):  # not the watch: their helmets
                continue
            if male is not None and (r[ix["given"]] in male_names()) != male:
                continue
            cands.append(r)
        cands.sort(key=lambda r: (r[ix["job"]], r[ix["id"]]))
        seen, chosen = set(), []
        for r in cands:  # one a job first, then the rest
            if r[ix["job"]] not in seen:
                seen.add(r[ix["job"]])
                chosen.append(r)
        for r in cands:
            if len(chosen) >= PER_LOOK:
                break
            if r not in chosen:
                chosen.append(r)
        rnd = random.Random(look)
        rnd.shuffle(chosen)
        out[look] = [(r[ix["id"]], r[ix["build"]] == 1) for r in chosen[:PER_LOOK]]
    return out


# a box's place on a 64 x 64 skin: u, v, w, h, d (wide); the slim arms' w is 3
BOXES = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
         "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4), "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
         "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4), "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4)}
OVER = {"hat", "jacket", "rsleeve", "lsleeve", "rpants", "lpants"}


def widen(img):
    """A slim skin made wide: each arm face 3 columns wide gets 4 (its middle column twice), as the stone body is wide."""
    src = img.copy()
    out = img.copy()
    for k in ("rarm", "rsleeve", "larm", "lsleeve"):
        u, v, _, h, d = BOXES[k]
        sf, wf = faces(u, v, 3, h, d), faces(u, v, 4, h, d)
        for name in ("top", "bottom", "front", "back", "right", "left"):
            a, b = sf[name], wf[name]
            face = src.crop(a)
            if face.width == 3:
                face = face.resize((4, face.height), Image.NEAREST)
            out.paste(Image.new("RGBA", (b[2] - b[0], b[3] - b[1]), (0, 0, 0, 0)), b[:2])
            out.paste(face, b[:2])
    return out


def noise2(w, h, seed, scale=4):
    """Smooth value noise in 0..1 (bilinear over a coarse random grid)."""
    rnd = np.random.default_rng(seed)
    g = rnd.random((h // scale + 2, w // scale + 2))
    ys, xs = np.mgrid[0:h, 0:w] / scale
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    return (g[y0, x0] * (1 - fx) * (1 - fy) + g[y0, x0 + 1] * fx * (1 - fy) + g[y0 + 1, x0] * (1 - fx) * fy + g[y0 + 1, x0 + 1] * fx * fy)


# stone: light that faced the sky, dark that didn't; a little warm in the lights, cool in the shadows
STONE = np.array([(62, 63, 64), (88, 88, 87), (114, 113, 109), (138, 136, 130), (162, 159, 151), (186, 182, 172)], dtype=float)
MOSS = np.array([(52, 70, 30), (68, 90, 38), (86, 110, 46), (104, 128, 56)], dtype=float)
LICHEN = [(170, 172, 128), (150, 160, 104), (186, 150, 92)]


def ramp(t):
    t = np.clip(t, 0, 0.999) * (len(STONE) - 1)
    i = t.astype(int)
    f = (t - i)[..., None]
    return STONE[i] * (1 - f) + STONE[i + 1] * f


def stone_skin(src, seed):
    """A citizen's skin (closed eyes, made wide) as weathered stone: their own light and dark, low contrast, a ghost of
    their colours; moss where rain and seed settle (the head's top, the shoulders, the feet), streaks under it, lichen
    on the back, a crack or two."""
    a = np.asarray(src.convert("RGBA"), dtype=float).copy()
    rgb, al = a[..., :3], a[..., 3]
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]) / 255
    t = 0.52 + (lum - 0.42) * 0.62
    grain = noise2(64, 64, seed, 2) * 0.10 + np.random.default_rng(seed + 1).random((64, 64)) * 0.06 - 0.08
    t = t + grain
    out = ramp(t)
    sat = rgb - rgb.mean(axis=2, keepdims=True)
    out += sat * 0.10  # the ghost of what they wore
    rnd = random.Random(seed)
    mossn = noise2(64, 64, seed + 7, 3)
    tone = np.random.default_rng(seed + 3).integers(0, len(MOSS), (64, 64))

    def moss(rect, amount, rows=None):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                if al[y, x] < 128:
                    continue
                k = amount if rows is None else amount * max(0.0, 1 - (y - y0) / rows)
                if mossn[y, x] < k:
                    out[y, x] = MOSS[tone[y, x]] * (0.9 + 0.2 * mossn[y, x])

    for k, (u, v, w, h, d) in BOXES.items():
        f = faces(u, v, w, h, d)
        top_amt = {"head": 0.62, "hat": 0.62, "body": 0.5, "jacket": 0.5, "rarm": 0.55, "rsleeve": 0.55, "larm": 0.55, "lsleeve": 0.55}.get(k, 0.0)
        if top_amt:
            moss(f["top"], top_amt)
            for side in ("front", "back", "left", "right"):
                moss(f[side], top_amt * 0.5, rows=3)
        if k in ("rleg", "lleg", "rpants", "lpants"):  # the ground creeping up the boots
            for side in ("front", "back", "left", "right"):
                x0, y0, x1, y1 = f[side]
                moss((x0, y1 - 3, x1, y1), 0.45)
        # rain streaks: a few columns darker under the tops
        for side in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = f[side]
            if x1 - x0 < 2:
                continue
            for _ in range(1 if w < 8 else 2):
                x = rnd.randrange(x0, x1)
                n = rnd.randint(2, max(2, (y1 - y0) // 2))
                for y in range(y0, min(y1, y0 + n)):
                    if al[y, x] >= 128:
                        out[y, x] *= 0.9
        # lichen, a few spots on the back and the outer sides, never the face
        for side in ("back", "left", "right"):
            x0, y0, x1, y1 = f[side]
            for _ in range(int((x1 - x0) * (y1 - y0) * 0.035)):
                x, y = rnd.randrange(x0, x1), rnd.randrange(y0, y1)
                if al[y, x] >= 128:
                    out[y, x] = LICHEN[rnd.randrange(len(LICHEN))]
        # a crack down a front or a side
        if k in ("body", "rleg", "lleg", "rarm", "larm") and rnd.random() < 0.6:
            side = rnd.choice(("front", "left", "right"))
            x0, y0, x1, y1 = f[side]
            x, y = rnd.randrange(x0, x1), rnd.randrange(y0, y0 + max(1, (y1 - y0) // 2))
            for _ in range(rnd.randint(3, 6)):
                if not (x0 <= x < x1 and y0 <= y < y1):
                    break
                if al[y, x] >= 128:
                    out[y, x] *= 0.62
                y += 1
                x += rnd.choice((-1, 0, 0, 1))
    a[..., :3] = np.clip(out, 0, 255)
    a[..., 3] = np.where(al >= 128, 255, 0)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# ---- the props ------------------------------------------------------------------------------------------------------

def box_faces(name, w, h, d):
    u, v = PROP_UV[name]
    return faces(u, v, w, h, d)


def props_layer(seed=4300):
    """The props' pixels (transparent where the skin goes): stone for what they carried, the world's own for the rest."""
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    p = Painter(img, seed)
    stone = (130, 128, 122)
    canvas, leather, wood, dark_wood = (128, 112, 86), (104, 76, 54), (112, 96, 76), (78, 66, 52)

    def all_faces(name, w, h, d, fn):
        for k, rect in box_faces(name, w, h, d).items():
            fn(k, rect)

    def stone_box(name, w, h, d, moss_top=True, light=1.0):
        def fn(k, rect):
            p.stone(rect, stone, 0.0, light * (1.08 if k == "top" else 0.92 if k == "bottom" else 1.0))
            if moss_top and k == "top":
                p.lichen(rect, 0.25)
        all_faces(name, w, h, d, fn)

    stone_box("pack", 6, 8, 3)
    stone_box("bedroll", 8, 3, 3)
    stone_box("lantern", 4, 5, 4, moss_top=False)
    stone_box("lantern_handle", 2, 2, 1, moss_top=False)
    for k, rect in box_faces("lantern", 4, 5, 4).items():  # the dark glass behind stone bars
        if k in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = rect
            for y in range(y0 + 1, y1 - 1):
                for x in range(x0, x1):
                    if (x - x0) in (1, 2):
                        p.put(x, y, (52, 54, 58))

    def cloth(name, w, h, d, base, folds=True):
        def fn(k, rect):
            p.fill(rect, base, 7)
            x0, y0, x1, y1 = rect
            if folds:
                for x in range(x0, x1, 3):
                    for y in range(y0, y1):
                        if p.r.random() < 0.5:
                            p.put(x, y, shade(base, 0.86))
            if k == "top":
                p.lichen(rect, 0.12)
            for y in range(y0, y1):  # sun-faded at the top, damp at the foot
                for x in range(x0, x1):
                    if y == y1 - 1 and k not in ("top", "bottom"):
                        p.put(x, y, shade(base, 0.72))
        all_faces(name, w, h, d, fn)

    cloth("pack_ground", 6, 8, 4, canvas)
    cloth("bedroll_ground", 8, 3, 3, (110, 84, 70))
    cloth("bundle", 8, 5, 6, (122, 104, 92))
    cloth("bundle_knot", 2, 2, 2, (110, 92, 80), folds=False)
    cloth("collar", 4, 1, 4, leather, folds=False)
    cloth("rope", 1, 1, 14, (126, 104, 72), folds=False)

    def milestone(k, rect):
        p.stone(rect, (176, 158, 122), 0.0, 1.06 if k == "top" else 1.0)  # the road's sandstone, not theirs
        x0, y0, x1, y1 = rect
        if k == "top":
            p.lichen(rect, 0.35)
        if k == "front":  # a carved mark and a worn number: the miles to Carillon
            for x in range(x0 + 2, x1 - 2):
                p.put(x, y0 + 2, (96, 96, 92))
            for x in (x0 + 2, x0 + 4, x0 + 5):
                for y in range(y0 + 4, y0 + 7):
                    p.put(x, y, (92, 92, 88))
        if k != "bottom":
            for x in range(x0, x1):  # the grass round its foot
                if p.r.random() < 0.6:
                    p.put(x, y1 - 1, (82, 108, 52))
    all_faces("milestone", 8, 8, 6, milestone)

    def grave(k, rect):
        p.stone(rect, (116, 122, 130), 0.0, 1.05 if k == "top" else 1.0)  # slate: a grave is cut from other stone
        x0, y0, x1, y1 = rect
        if k == "front":  # a name worn smooth, a line under it
            for y in (y0 + 3, y0 + 5):
                for x in range(x0 + 2, x1 - 2):
                    if p.r.random() < 0.7:
                        p.put(x, y, (84, 88, 96))
            for x in range(x0 + 1, x1 - 1):
                p.put(x, y0 + 7, (80, 84, 92))
        if k != "bottom":
            for x in range(x0, x1):
                if p.r.random() < 0.5:
                    p.put(x, y1 - 1, (82, 108, 52))
    all_faces("grave", 8, 11, 2, grave)
    all_faces("grave_cap", 6, 1, 2, lambda k, rect: p.stone(rect, (116, 122, 130), 0.0, 1.05))

    def flower(k, rect):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                p.put(x, y, (238, 214, 96) if y == y0 or k == "top" else (76, 120, 52))
    all_faces("flower", 1, 2, 1, flower)

    def planks(k, rect, base):
        x0, y0, x1, y1 = rect
        p.fill(rect, base, 6)
        horizontal = (x1 - x0) >= (y1 - y0)
        for i in range((y1 - y0) if horizontal else (x1 - x0)):
            if i % 3 == 2:
                for j in range((x1 - x0) if horizontal else (y1 - y0)):
                    x, y = (x0 + j, y0 + i) if horizontal else (x0 + i, y0 + j)
                    p.put(x, y, shade(base, 0.74))
        for _ in range(max(1, (x1 - x0) * (y1 - y0) // 30)):
            p.put(p.r.randrange(x0, x1), p.r.randrange(y0, y1), (108, 124, 72))  # moss in the grain
    all_faces("cart_bed", 13, 2, 14, lambda k, r: planks(k, r, (116, 104, 90)))
    all_faces("cart_side", 1, 3, 14, lambda k, r: planks(k, r, (104, 92, 80)))
    all_faces("cart_tail", 13, 3, 1, lambda k, r: planks(k, r, (110, 98, 84)))

    def wheel(k, rect):
        x0, y0, x1, y1 = rect
        if k in ("left", "right"):  # a spoked wheel, round, on its flat sides
            cx, cy = (x0 + x1 - 1) / 2, (y0 + y1 - 1) / 2
            for y in range(y0, y1):
                for x in range(x0, x1):
                    r = math.hypot(x - cx, y - cy)
                    ang = math.atan2(y - cy, x - cx)
                    if r <= 4.8 and (r >= 3.7 or r <= 1.2 or min(abs(math.sin(ang * 3)), 1) < 0.35):
                        p.put(x, y, dark_wood if r >= 3.7 else wood)
        else:
            p.fill(rect, dark_wood, 5)
    all_faces("cart_wheel", 2, 10, 10, wheel)
    return img


def figure_textures():
    picks = pick_citizens()
    layer = props_layer()
    out_dir = os.path.join(ROOT, "entity", "stilled")
    os.makedirs(out_dir, exist_ok=True)
    for f in os.listdir(out_dir):
        if f.endswith(".png"):
            os.remove(os.path.join(out_dir, f))
    for look, people in picks.items():
        for i, (cid, slim) in enumerate(people):
            src = Image.open(os.path.join(CITIZEN, "c%03d_blink.png" % cid)).convert("RGBA")
            if slim:
                src = widen(src)
            tex = layer.copy()
            tex.paste(stone_skin(src, 9000 + cid), (0, 0))
            tex.save(os.path.join(out_dir, "%s_%d.png" % (look, i)))
    return picks


def chest():
    """The left chest: an old travelling trunk, dark wood, iron bands, a heavy lock."""
    r = random.Random(43)
    out = os.path.join(ROOT, "block")
    wood, band, dark = (98, 72, 50), (70, 68, 66), (56, 40, 30)

    def base(seed):
        img = Image.new("RGBA", (16, 16))
        p = Painter(img, seed)
        p.fill((0, 0, 16, 16), wood, 9)
        for y in range(0, 16, 4):
            for x in range(16):
                p.put(x, y, dark)
        for x in (2, 13):
            for y in range(16):
                p.put(x, y, band)
                p.put(x + 1, y, shade(band, 1.15))
        for x, y in ((2, 1), (13, 1), (2, 14), (13, 14)):
            p.put(x, y, (120, 116, 108))  # rivets
        return img, p

    side, _ = base(1)
    side.save(os.path.join(out, "left_chest_side.png"))
    top, tp = base(2)
    for x in range(16):  # a strap over the lid
        tp.put(x, 7, (82, 60, 44))
        tp.put(x, 8, (82, 60, 44))
    top.save(os.path.join(out, "left_chest_top.png"))
    front, fp = base(3)
    for y in range(16):
        fp.put(7, y, band)
        fp.put(8, y, band)
    fp.fill((5, 3, 11, 9), (64, 62, 60), 4)  # the lock plate
    fp.fill((6, 4, 10, 8), (88, 84, 76), 4)
    fp.put(7, 5, (24, 22, 20))  # the keyhole
    fp.put(8, 5, (24, 22, 20))
    fp.put(8, 6, (24, 22, 20))
    for x, y in ((r.randrange(16), r.randrange(10, 16)) for _ in range(6)):
        fp.put(x, y, (126, 92, 60))  # scuffs
    front.save(os.path.join(out, "left_chest_front.png"))


def items():
    out = os.path.join(ROOT, "item")
    # the letter: folded paper gone yellow, a brown wax seal, a string
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 7)
    p.fill((2, 4, 14, 12), (226, 212, 172), 6)
    for x in range(2, 14):
        p.put(x, 4, (186, 170, 132))
        p.put(x, 11, (172, 156, 120))
    for y in range(4, 12):
        p.put(2, y, (186, 170, 132))
        p.put(13, y, (172, 156, 120))
    for i in range(6):  # the fold
        p.put(2 + i, 4 + i, (198, 184, 146))
        p.put(13 - i, 4 + i, (198, 184, 146))
    p.fill((7, 8, 9, 10), (132, 52, 40), 4)
    p.put(8, 7, (110, 40, 32))
    for y in range(3, 13):
        p.put(5, y, (150, 130, 100))
    img.save(os.path.join(out, "stilled_letter.png"))
    # the key: old iron, a paper tag on a string
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 8)
    iron, iron_d = (112, 108, 100), (70, 68, 64)
    for x, y in ((3, 3), (4, 2), (5, 2), (6, 3), (6, 4), (5, 5), (4, 5), (3, 4)):
        p.put(x, y, iron)  # the bow
    for i in range(7):
        p.put(6 + i, 5 + i, iron if i % 2 == 0 else iron_d)  # the shaft
        p.put(7 + i, 5 + i, iron_d)
    p.put(11, 12, iron)
    p.put(12, 13, iron)
    p.put(10, 13, iron_d)
    for i in range(3):
        p.put(3 - i, 6 + i * 2, (170, 150, 110))  # the string
    p.fill((0, 11, 5, 15), (222, 208, 170), 6)  # the tag
    p.put(1, 12, (120, 110, 90))
    p.put(2, 12, (120, 110, 90))
    p.put(1, 13, (120, 110, 90))
    img.save(os.path.join(out, "stilled_key.png"))
    # the keepsake: something small wrapped in a handkerchief, tied, a name scratched on a tag
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = Painter(img, 9)
    for y in range(5, 14):
        w = 5 - abs(y - 10) // 2
        p.fill((8 - w, y, 8 + w, y + 1), (200, 186, 196), 8)
    p.fill((6, 3, 10, 6), (190, 176, 186), 6)  # the gathered top
    p.put(5, 2, (186, 170, 180))
    p.put(10, 2, (186, 170, 180))
    for x in range(5, 11):
        p.put(x, 6, (128, 88, 70))  # the ribbon
    for y in range(7, 10):
        p.put(11 + (y - 7), y, (128, 88, 70))
    p.fill((12, 9, 15, 13), (222, 206, 168), 4)
    p.put(13, 10, (110, 96, 80))
    p.put(13, 11, (110, 96, 80))
    for x, y in ((7, 9), (9, 11), (6, 12)):
        p.put(x, y, (176, 160, 172))  # folds
    img.save(os.path.join(out, "stilled_keepsake.png"))


def preview(out, poses=None, look="man", i=0):
    """Every pose (or the ones named) from four sides, as the game draws it, into one sheet."""
    sys.path.insert(0, os.path.dirname(__file__))
    import rig as R
    rig = R.load(RIG)
    rows = []
    for name in poses or [k for k in POSES if not k.endswith("_b")]:
        q = rig["poses"][name]
        tex = np.asarray(Image.open(os.path.join(ROOT, "entity", "stilled", "%s_%d.png" % (look, i))).convert("RGBA"), dtype=float)
        qs = R.quads(rig, q, tex, R.block_world())
        sec = q.get("second")
        if sec:
            t2 = np.asarray(Image.open(os.path.join(ROOT, "entity", "stilled", "%s_%d.png" % (sec["look"], i))).convert("RGBA"), dtype=float)
            q2 = dict(rig["poses"][sec["pose"]])
            q2["hide"] = list(set(q2.get("hide", [])) | {x["name"] for x in rig["parts"] if x["group"] == "props"})
            qs += R.quads(rig, q2, t2, R.block_world() @ R.second(sec))
        rows.append((name, qs, (0.5, 0.75, 0.5)))
    return R.sheet(rows, out)


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        print(preview(sys.argv[2], sys.argv[3:] or None))
        return
    build_rig()
    picks = figure_textures()
    chest()
    items()
    print("stilled: rig and %d figure textures written" % sum(len(v) for v in picks.values()))


if __name__ == "__main__":
    main()
