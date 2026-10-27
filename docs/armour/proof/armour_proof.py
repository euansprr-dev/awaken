"""Armour proof of concept (docs/armour/PLAN.md §5): disposable, changes no production file.

Question it answers: if a set's model gets real parts (layered cloth, plates, hanging pieces, glass) painted to the weapon
style's rules (whole-texel boxes, one-texel bevels lit from the upper left, contact shadows, three or four hue-shifted
ramps, a few deliberate marks, no noise), does it read as deep and material-rich in the game's own pixel density, or
is it still a flat skin?

What it is: gear.py's own software renderer (the same cube, UV and pose maths as ModelPart, vanilla-style light, no
shader), a part list, and a painter. The base layer is drawn the way the game's equipment layer would draw it (vanilla
entity light, albedo only). The glow pass is an approximation (an additive add of the glow texture over a dimmed night
frame, blurred a little for bloom), not the game's emissive pipeline.

What it does NOT prove: that this runs at 60 players (it is a still image), that the parts animate (the walking frame
poses the swinging parts by hand), that Lens bloom looks like the blur, or that the real model bakes (no Java was run).

    python3 docs/armour/proof/armour_proof.py OUTDIR        sheet.png, closeup.png, materials.png, night.png
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
from PIL import Image, ImageFilter, ImageChops, ImageDraw  # noqa: E402

import gear as G  # noqa: E402
from pixelart import Canvas, RAMPS, hex_rgba  # noqa: E402

# ---------------------------------------------------------------------------------------------------------------------
# Ramps: the gear palette plus the materials the proof needs. Each is dark to light, hue-shifted.

R = dict(G.PAL)
R.update({
    "silk": ["#140e2e", "#21174a", "#33246f", "#4a35a0", "#6a52d0", "#9a86f2"],
    "rose": ["#2a0a22", "#5a1442", "#8f2563", "#c4408a", "#f06ab0", "#ffb8dc"],
    "silver": ["#2a2f38", "#454d5a", "#69737f", "#9aa4ae", "#cdd5dc", "#f5f8fb"],
    "glass": ["#120f2c", "#2a2377", "#4a45c0", "#7a86e6", "#b5c0ff", "#eef1ff"],
    "steel": ["#1b1e25", "#2d323d", "#444b59", "#5f6877", "#7e8998", "#a7b2c1", "#d2dce8"],
    "alloy": ["#121a22", "#1b2733", "#2b4054", "#3f5d78", "#5f86a6", "#9cc4e0"],
    "deep": ["#06121c", "#0b2a3c", "#12506a", "#1b8596", "#4fd0c4", "#c4fff2"],
    "tusk": ["#3b2f22", "#6a5638", "#9c8458", "#c9b78a", "#e8dcb8", "#fbf4dc"],
    "leather": ["#1a0f08", "#2e1b0e", "#472b16", "#62401f", "#80592c", "#a07840"],
    "ember": ["#3a0e04", "#7a2308", "#b8400d", "#e8741a", "#ffb03a", "#ffe9a0"],
})
for k, v in R.items():
    R[k] = [c if isinstance(c, str) else c for c in v]


def col(name, i, a=255):
    r = R[name]
    return hex_rgba(r[max(0, min(len(r) - 1, i))], a)


SIDES = ("north", "south", "east", "west")
FACE = {"up": 1, "north": 0, "west": 0, "east": -1, "south": -1, "down": -1}


def bevel(face, i, j, fw, fh, top=2):
    """The painted bevel: a one-texel band on every outer edge, lit up and toward the upper left, dark down and right."""
    if min(fw, fh) < 3:
        return 0
    c = []
    if face in SIDES:
        if j == 0:
            c.append(top)
        if j == fh - 1:
            c.append(-1)
        if i == 0:
            c.append(1)
        if i == fw - 1:
            c.append(-1)
    elif face == "up":
        if i == 0 or j == fh - 1:
            c.append(1)
        if i == fw - 1:
            c.append(-1)
    else:
        if i == 0 or j == 0:
            c.append(-1)
    return max(c, key=abs) if c else 0


# ---------------------------------------------------------------------------------------------------------------------
# Materials: f(face, i, j, fw, fh, o) -> (albedo or None, glow or None). o is the part's options.

def in_window(w, i, j):
    if not w:
        return False
    if isinstance(w[0], int):
        w = [w]
    return any(a <= i <= b and c <= j <= d for a, b, c, d in w)


def silk(face, i, j, fw, fh, o):
    r = o.get("ramp", "silk")
    if face == "north" and in_window(o.get("window"), i, j):
        return None, None                   # the face's opening
    step = o.get("base", 3) + (1 if face == "up" else 0)
    big = fw >= 3 and fh >= 3
    if big and face in SIDES and o.get("fold"):
        f = o["fold"]
        if i % f == f - 1:
            step -= 1                       # a pleat's shadow line
        elif i % f == 0 and i > 0:
            step += 1                       # and the ridge beside it
    step += bevel(face, i, j, fw, fh, top=1) if o.get("bevel", True) else 0
    if o.get("contact") and face in SIDES and j == 0:
        step = o.get("base", 3) - 2
    hem = o.get("hem")
    if hem and face in SIDES and fh >= 3 and j >= fh - o.get("hem_rows", 1):
        return col(hem, 4 + (1 if j == fh - o.get("hem_rows", 1) else 0)), None
    th = o.get("thread")
    if th and face in SIDES and th(i, j, fw, fh):
        return col("glass", 4), col("glass", 3)
    return col(r, step), None


def plate(face, i, j, fw, fh, o):
    if face == "north" and in_window(o.get("window"), i, j):
        return None, None
    r = o.get("ramp", "silver")
    step = o.get("base", 3) + FACE[face] + bevel(face, i, j, fw, fh)
    if face in SIDES and fw >= 5 and fh >= 3 and j == 1 and (i % 5) < 3 and 1 < i < fw - 2:
        step += 1                           # the broken highlight streak on the lit edge
    if o.get("rivet") and face in SIDES and fw >= 5 and fh >= 5 and ((i == 2 or i == fw - 3) and j == fh // 2):
        step += 2
    if o.get("contact") and face in SIDES and j == 0:
        step = o.get("base", 3) - 3
    if o.get("trim") and face in SIDES and fh >= 4 and j == fh - 2:
        return col(o["trim"], 4), None
    rune = o.get("rune")
    if rune and face in SIDES and rune(i, j, fw, fh):
        return col(o.get("glowc", "signal"), 4), col(o.get("glowc", "signal"), 5)
    return col(r, step), None


def mail(face, i, j, fw, fh, o):
    r = o.get("ramp", "steel")
    step = o.get("base", 3) + FACE[face]
    ii = (i + 2 * ((j // 4) % 2)) % 4       # rings of four texels, each row of rings offset by half a ring
    jj = j % 4
    ring = (ii in (1, 2) and jj in (0, 3)) or (ii in (0, 3) and jj in (1, 2))
    step += 1 if ring else -1               # a link, then the dark of its hole
    step += bevel(face, i, j, fw, fh, top=1)
    return col(r, step), None


def scale(face, i, j, fw, fh, o):
    """Scales three texels wide and three tall, each row offset by one: a lit crown, a base tone, a shadowed lower lip and a seam."""
    r = o.get("ramp", "lord")
    row = j // 3
    c = (i + (row % 2)) % 3
    k = j % 3
    step = o.get("base", 3) + (1 if face == "up" else 0)
    step += {0: 2, 1: 0, 2: -2}[k]
    if c == 0:
        step -= 1                           # the seam between two scales
    elif c == 1 and k == 0:
        step += 1                           # the crown catches the light
    step += bevel(face, i, j, fw, fh, top=1) if o.get("bevel", True) else 0
    if o.get("rim") and face in SIDES and (j >= fh - 1 or i == 0) and fh > 3:
        return col(o["rim"], 4), None
    return col(r, step), None


def hide(face, i, j, fw, fh, o):
    r = o.get("ramp", "pelt")
    step = o.get("base", 3) + FACE[face]
    if (i * 5 + (j // 2) * 3) % 7 in (0, 1):
        step -= 1                           # a strand, two texels long
    elif (i * 3 + (j // 2) * 5) % 9 == 0:
        step += 1
    if o.get("fringe") and face in SIDES and j >= fh - 1:
        step += 1 if i % 2 == 0 else -2
    step += bevel(face, i, j, fw, fh, top=1) if o.get("bevel", True) else 0
    return col(r, step), None


def studded(face, i, j, fw, fh, o):
    base, _ = hide(face, i, j, fw, fh, dict(o, ramp=o.get("ramp", "leather"), bevel=False))
    if fw >= 4 and fh >= 4 and face in SIDES and i % 3 == 1 and j % 3 == 1:
        return col(o.get("stud", "bronze"), 4), None
    return base, None


def glass(face, i, j, fw, fh, o):
    r = o.get("ramp", "glass")
    cx, cy = (fw - 1) / 2, (fh - 1) / 2
    d = max(abs(i - cx) / max(0.5, fw / 2), abs(j - cy) / max(0.5, fh / 2))
    if d < 0.45:
        s = 5 if (i == int(cx) and j == int(cy) - 1) else 4
    elif d < 0.8:
        s = 3
    else:
        s = 1
    s += FACE[face] if face != "north" else 0
    c = col(r, s)
    return c, (col(r, 3) if d < 0.8 else None)


def weave(face, i, j, fw, fh, o):
    r = o.get("ramp", "weave")
    step = o.get("base", 3) + FACE[face]
    if (i + j) % 4 == 0:
        step += 1
    elif (i + j) % 4 == 2:
        step -= 1
    step += bevel(face, i, j, fw, fh, top=1)
    if o.get("trim") and face in SIDES and (j == 0 or j == fh - 1 or i == 0 or i == fw - 1) and fw > 3 and fh > 3:
        return col(o["trim"], 4 + (1 if j == 0 else 0)), None
    return col(r, step), None


def alloy(face, i, j, fw, fh, o):
    r = o.get("ramp", "alloy")
    step = o.get("base", 3) + FACE[face] + bevel(face, i, j, fw, fh)
    if face in SIDES and fw >= 6 and fh >= 4:
        if j == fh // 2 and 1 < i < fw - 2:      # a seam of the circuit
            return col("signal", 3), col("signal", 5)
        if i % 8 == 0 and j > 0:
            step -= 1
    return col(r, step), None


MATS = {"silk": silk, "plate": plate, "mail": mail, "scale": scale, "hide": hide, "studded": studded, "glass": glass,
        "weave": weave, "alloy": alloy}

# ---------------------------------------------------------------------------------------------------------------------
# Parts

BASE = {"head": (0, 0, 0, 0, 0, 0), "body": (0, 0, 0, 0, 0, 0), "right_arm": (-5, 2, 0, 0, 0, 0.0),
        "left_arm": (5, 2, 0, 0, 0, -0.0), "right_leg": (-1.9, 12, 0, 0, 0, 0), "left_leg": (1.9, 12, 0, 0, 0, 0)}


def part(name, parent, box, mat, pose=(0, 0, 0, 0, 0, 0), grow=0.0, twin=None, swing=0.0, **o):
    return dict(name=name, parent=parent, box=list(map(float, box)), pose=list(map(float, pose)), mat=mat, o=o, grow=grow,
                twin=twin, swing=swing, uv=None)


def mirrored(parts, rename):
    out = []
    for p in parts:
        x, y, z, w, h, d = p["box"]
        px, py, pz, xr, yr, zr = p["pose"]
        out.append(dict(p, name=rename[p["name"]], parent=rename.get(p["parent"], p["parent"]),
                        box=[-x - w, y, z, w, h, d], pose=[-px, py, pz, xr, -yr, -zr], twin=p["name"], uv=None))
    return out


def uvsize(box):
    x, y, z, w, h, d = box
    return int(math.ceil(2 * (d + w))), int(math.ceil(d + h))


def pack(parts, size):
    """Shelf-pack every owning part's UV rectangle (twins share); shell parts keep their vanilla places."""
    used = []
    for p in parts:
        if p["uv"] is not None:
            w, h = uvsize(p["box"])
            used.append((p["uv"][0], p["uv"][1], w, h))
    x = y = sh = 0
    tw, th = size
    free_y = 0
    owners = sorted([p for p in parts if p["uv"] is None and not p["twin"]], key=lambda p: -uvsize(p["box"])[1])
    # start below the vanilla 64x32 layer and the leggings/second-skin area
    y = 64
    sh = 0
    for p in owners:
        w, h = uvsize(p["box"])
        if x + w > tw:
            x, y, sh = 0, y + sh, 0
        if y + h > th:
            raise SystemExit("atlas full at " + p["name"])
        p["uv"] = (x, y)
        x += w
        sh = max(sh, h)
    byname = {p["name"]: p for p in parts}
    for p in parts:
        if p["twin"]:
            p["uv"] = byname[p["twin"]]["uv"]


def faces_of(u, v, box):
    x, y, z, w, h, d = box
    w, h, d = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
    return {"down": (u + d + w, v, w, d), "up": (u + d, v, w, d), "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def paint(parts, size):
    alb, glw = Canvas(*size), Canvas(*size)
    for p in parts:
        if p["twin"]:
            continue
        fn = MATS[p["mat"]]
        for face, (x0, y0, fw, fh) in faces_of(p["uv"][0], p["uv"][1], p["box"]).items():
            for j in range(fh):
                for i in range(fw):
                    a, g = fn(face, i, j, fw, fh, p["o"])
                    if a is not None:
                        alb.set(x0 + i, y0 + j, a)
                    if g is not None:
                        glw.set(x0 + i, y0 + j, g)
    return alb.image(), glw.image()


# ---------------------------------------------------------------------------------------------------------------------
# The figure: skin, then each part with its parent chain, posed.

def build_poses(walk, parts):
    """walk in -1..1: the stride (legs and arms opposite), the cloth lagging behind it."""
    a = 0.55 * walk
    root = G._Pose()
    base = {k: list(v) for k, v in BASE.items()}
    base["right_leg"][3] = a
    base["left_leg"][3] = -a
    base["right_arm"][3] = -a * 0.8
    base["left_arm"][3] = a * 0.8
    base["right_arm"][5] = 0.05
    base["left_arm"][5] = -0.05
    poses = {k: root.child(v) for k, v in base.items()}
    for p in parts:
        pose = list(p["pose"])
        if p["swing"]:
            pose[3] += p["swing"] * (0.5 + 0.5 * abs(walk)) * (1 if walk >= 0 else 1)
        poses[p["name"]] = poses[p["parent"]].child(pose)
    return poses


def render(parts, atlas, yaw, walk=0.0, scale=8, size=(240, 330), centre=(120, 100), pitch=-0.2, skin=True, light=True):
    sc = G.Scene(scale, yaw, pitch, size, centre)
    poses = build_poses(walk, parts)
    if skin:
        s = G._skin()
        sc.cube(poses["head"], s, 0, 0, (-4, -8, -4, 8, 8, 8))
        sc.cube(poses["body"], s, 16, 16, (-4, 0, -2, 8, 12, 4))
        sc.cube(poses["right_arm"], s, 40, 16, (-3, -2, -2, 4, 12, 4))
        sc.cube(poses["left_arm"], s, 32, 48, (-1, -2, -2, 4, 12, 4))
        sc.cube(poses["right_leg"], s, 0, 16, (-2, 0, -2, 4, 12, 4))
        sc.cube(poses["left_leg"], s, 16, 48, (-2, 0, -2, 4, 12, 4))
    # far parts first is not needed: the depth buffer decides
    for p in parts:
        sc.cube(poses[p["name"]], atlas, p["uv"][0], p["uv"][1], p["box"], p["grow"], bool(p["twin"]), shade=light)
    return sc.img


# ---------------------------------------------------------------------------------------------------------------------
# The Veilwoven set, version 2: layered silk over mirror-silver, spirit glass, thread of light.

def thread_line(i, j, fw, fh):
    return i == fw // 2 and 2 < j < fh - 2 and j % 4 != 0


def thread_diamond(i, j, fw, fh):
    cx, cy = (fw - 1) / 2, fh / 2 - 1
    return abs(abs(i - cx) + abs(j - cy) - 2.5) < 0.6


def shell_parts(kind="silk", **o):
    """The base layer: vanilla's armour boxes (outer 1.0, inner 0.5), in their vanilla UV places."""
    sh = [part("sh_head", "head", (-4, -8, -4, 8, 8, 8), kind, grow=1.0, **o),
          part("sh_body", "body", (-4, 0, -2, 8, 12, 4), kind, grow=1.0, **o),
          part("sh_arm_r", "right_arm", (-3, -2, -2, 4, 12, 4), kind, grow=1.0, **o),
          part("sh_leg_r", "right_leg", (-2, 0, -2, 4, 12, 4), kind, grow=0.9, **o)]
    sh[0]["uv"], sh[1]["uv"], sh[2]["uv"], sh[3]["uv"] = (0, 0), (16, 16), (40, 16), (0, 16)
    left = {"sh_arm_r": "sh_arm_l", "sh_leg_r": "sh_leg_l", "right_arm": "left_arm", "right_leg": "left_leg"}
    return sh + mirrored(sh[2:], left)


def veilwoven_v2():
    """B, the Veil's weavers: dark silk robe under a lighter hood and mantle, mirror-silver trim, spirit glass."""
    s = []
    for p in shell_parts("silk", base=2, fold=4):
        if p["name"] == "sh_head":
            p["o"].update(window=(1, 6, 3, 7))
        if p["name"] in ("sh_arm_r", "sh_arm_l"):
            p["o"].update(fold=0)
        if p["name"] == "sh_body":
            p["o"].update(thread=thread_line)
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=1, fold=0)
        s.append(p)
    # --- Cowl: a hood of three slabs and a brim, a curling peak, a silver edge and a gem at the brow
    hood = [
        part("hood_top", "head", (-5.5, -10.5, -5.5, 11, 2, 11), "silk", base=3),
        part("hood_back", "head", (-5.5, -9, 4.5, 11, 8, 2), "silk", base=3, fold=4),
        part("hood_side_r", "head", (-7, -9, -5.5, 2, 9, 10), "silk", base=3, fold=3, hem="silver", hem_rows=1),
        part("hood_brim", "head", (-5.5, -9, -7, 11, 3, 2), "silk", base=4, hem="silver", hem_rows=1),
        part("peak_1", "head", (-2, -4, -2, 4, 4, 4), "silk", pose=(0, -9.5, 3.5, -0.5, 0, 0), swing=-0.15, base=3),
        part("peak_2", "peak_1", (-1.5, -4, -1.5, 3, 4, 3), "silk", pose=(0, -4, 0, -0.7, 0, 0), swing=-0.3, base=4),
        part("brow_gem", "head", (-1, -6.5, -7.8, 2, 2, 1), "glass"),
        part("cape_a", "body", (-4.5, 0, 0, 9, 8, 1), "silk", pose=(0, 1.0, 4.6, 0.04, 0, 0), swing=0.28, base=3, fold=3,
             contact=True, thread=thread_diamond),
        part("cape_b", "cape_a", (-4.5, 0, 0, 9, 8, 1), "silk", pose=(0, 8, 0, 0.1, 0, 0), swing=0.5, base=3, fold=3,
             hem="silver", hem_rows=2),
    ]
    s += hood + mirrored([hood[2]], {"hood_side_r": "hood_side_l"})
    # --- Mantle: two layers of capelet with a silver edge, a clasp, a glass sigil, a sash with swinging tails, cuffs
    s += [
        part("mantle_1", "body", (-8.5, 0.3, -4, 17, 2, 8), "silk", base=4),
        part("mantle_2", "body", (-9.5, 1.5, -4.5, 19, 2, 9), "silk", base=3, hem="silver", hem_rows=1, contact=True),
        part("clasp", "body", (-1.5, 1.2, -5.7, 3, 3, 1), "plate", base=4),
        part("clasp_gem", "body", (-0.5, 2.0, -6.6, 1, 1, 1), "glass", ramp="rose"),
        part("tabard", "body", (-3.5, 0, 0, 7, 8, 1), "silk", pose=(0, 3.8, -4.1, 0, 0, 0), swing=-0.08, base=4,
             hem="silver", hem_rows=2, thread=thread_diamond, contact=True),
        part("belt", "body", (-4.5, 9, -3.5, 9, 2, 7), "silk", base=1, bevel=True),
        part("buckle", "body", (-1.5, 8.8, -4.5, 3, 2.4, 1), "plate", base=4),
        part("sash_tail_l", "body", (-1, 0, 0, 2, 9, 1), "silk", pose=(2.4, 10.5, -4.6, 0, 0, 0), swing=-0.35, base=4,
             hem="silver", hem_rows=1, contact=True),
        part("sash_tail_r", "body", (-1, 0, 0, 2, 7, 1), "silk", pose=(-2.4, 10.5, -4.6, 0, 0, 0), swing=-0.55, base=4,
             hem="silver", hem_rows=1, contact=True),
        part("cuff_r", "right_arm", (-4.5, 6.5, -3.5, 7, 4, 7), "plate", base=3, rivet=True),
        part("cuff_gem_r", "right_arm", (-1.5, 7.5, -4.4, 2, 2, 1), "glass"),
        part("sleeve_r", "right_arm", (-4.5, 10.2, -3.5, 7, 3, 7), "silk", base=4, hem="silver", hem_rows=1, fold=2),
    ]
    s += mirrored([s[-3], s[-2], s[-1]], {"cuff_r": "cuff_l", "cuff_gem_r": "cuff_gem_l", "sleeve_r": "sleeve_l",
                                          "right_arm": "left_arm"})
    # --- Leggings: split robe panels that hang from each thigh, front and back
    s += [
        part("panel_f_r", "right_leg", (-2.5, 0, 0, 5, 10, 1), "silk", pose=(0, -1, -3.9, 0, 0, 0), swing=-0.1, base=3, fold=3,
             hem="silver", hem_rows=1, contact=True),
        part("panel_b_r", "right_leg", (-2.5, 0, 0, 5, 10, 1), "silk", pose=(0, -1, 2.9, 0, 0, 0), swing=0.25, base=2, fold=3,
             hem="silver", hem_rows=1, contact=True),
        part("knee_r", "right_leg", (-2, 0, 0, 4, 3, 1), "plate", pose=(0, 4, -4.9, 0, 0, 0), base=4, rivet=True),
    ]
    s += mirrored(s[-3:], {"panel_f_r": "panel_f_l", "panel_b_r": "panel_b_l", "knee_r": "knee_l", "right_leg": "left_leg"})
    # --- Treads: a turned cuff of silk, a silver toe cap, a glass stud at the ankle
    s += [
        part("cuff_boot_r", "right_leg", (-3.5, 8, -3.5, 7, 2, 7), "silk", base=4, hem="silver", hem_rows=1),
        part("toe_r", "right_leg", (-2.5, 10, -4.6, 5, 2, 1.7), "plate", base=4),
        part("ankle_gem_r", "right_leg", (-3.9, 9, -0.5, 1, 1, 1.5), "glass"),
    ]
    s += mirrored(s[-3:], {"cuff_boot_r": "cuff_boot_l", "toe_r": "toe_l", "ankle_gem_r": "ankle_gem_l", "right_leg": "left_leg"})
    return s


def rune_line(i, j, fw, fh):
    return i == fw // 2 and 1 < j < fh - 1


def rune_bar(i, j, fw, fh):
    return j == fh // 2 and 1 < i < fw - 2


def rune_cross(i, j, fw, fh):
    return (i == fw // 2 and 1 < j < fh - 1) or (j == fh // 3 and 1 < i < fw - 2)


def gatewrought_v2():
    """C, the Gate's own plate: gate-slate lames, brass rims, a signal-blue rune that runs up the set."""
    S = "slate"
    s = []
    for p in shell_parts("plate", ramp=S, base=2):
        if p["name"] == "sh_head":
            p["o"].update(window=[(1, 6, 4, 4), (3, 4, 5, 7)])
        if p["name"] == "sh_body":
            p["o"].update(rune=rune_line, glowc="signal")
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            p["o"].update(base=1)
        s.append(p)
    P_ = lambda *a, **k: part(*a, **dict(dict(ramp=S), **k))  # noqa: E731
    # --- Helm: a great helm with a T-slot, a brass crest, cheek guards and a rune on the brow
    helm = [
        P_("helm_crown", "head", (-5.5, -10, -5.5, 11, 2, 11), "plate", base=3),
        P_("helm_brow", "head", (-5.5, -8.5, -6.5, 11, 3, 1.5), "plate", base=3, trim="brass", rune=rune_bar, glowc="signal"),
        P_("helm_cheek_r", "head", (-6.5, -4, -6, 2, 5, 4), "plate", base=3, rivet=True),
        P_("crest", "head", (-0.5, 0, 0, 1, 5, 11), "plate", pose=(0, -14, -5.5, 0, 0, 0), base=4, ramp="brass"),
        P_("crest_fin", "head", (-0.5, 0, 0, 1, 3, 4), "plate", pose=(0, -16, 1.5, 0, 0, 0), base=4, ramp="brass"),
        P_("gorget", "head", (-5, 0, -3.5, 10, 2, 7), "plate", pose=(0, 0.2, 0, 0, 0, 0), base=3, trim="brass"),
    ]
    s += helm + mirrored([helm[2]], {"helm_cheek_r": "helm_cheek_l"})
    # --- Cuirass: breastplate and three belly lames, pauldrons of three lames each with a brass rim, a rune channel
    s += [
        P_("breast", "body", (-4.5, 0.5, -4.3, 9, 7, 1.5), "plate", base=3, rivet=True, trim="brass", rune=rune_line, glowc="signal"),
        P_("lame_1", "body", (-4.5, 7.5, -3.9, 9, 1.5, 1.5), "plate", base=3),
        P_("lame_2", "body", (-4.8, 8.8, -3.9, 9.6, 1.5, 1.8), "plate", base=4),
        P_("lame_3", "body", (-5.0, 10.1, -3.9, 10, 1.5, 2.1), "plate", base=3, trim="brass"),
        P_("back_plate", "body", (-4.5, 0.5, 2.8, 9, 8, 1.5), "plate", base=3, rivet=True, rune=rune_cross, glowc="signal"),
        P_("pauld_1_r", "right_arm", (-5, -3.5, -4, 8, 2, 8), "plate", base=4, trim="brass"),
        P_("pauld_2_r", "right_arm", (-5.5, -1.7, -4.4, 9, 2, 8.8), "plate", base=3),
        P_("pauld_3_r", "right_arm", (-6, 0.1, -4.8, 10, 2, 9.6), "plate", base=3, trim="brass"),
        P_("elbow_r", "right_arm", (-4.5, 4.5, -3.5, 7, 3, 7), "plate", base=4, ramp="brass", rivet=True),
        P_("vambrace_r", "right_arm", (-4.5, 7.5, -3.5, 7, 4, 7), "plate", base=3, rune=rune_bar, glowc="signal", trim="brass"),
    ]
    s += mirrored(s[-5:], {"pauld_1_r": "pauld_1_l", "pauld_2_r": "pauld_2_l", "pauld_3_r": "pauld_3_l", "elbow_r": "elbow_l",
                           "vambrace_r": "vambrace_l", "right_arm": "left_arm"})
    # --- Greaves: tassets that hang from the hip, a knee cop, a shin plate, sabatons with a brass toe
    s += [
        P_("tasset_r1", "right_leg", (-3, 0, 0, 6, 4, 1.5), "plate", pose=(0, -1.5, -4.2, 0, 0, 0), swing=-0.08, base=3),
        P_("tasset_r2", "right_leg", (-3, 0, 0, 6, 4, 1.5), "plate", pose=(0, 2, -4.4, 0, 0, 0), swing=-0.12, base=4, trim="brass"),
        P_("knee_r", "right_leg", (-2.5, 0, 0, 5, 3, 2), "plate", pose=(0, 4.8, -4.5, 0, 0, 0), base=4, ramp="brass", rivet=True),
        P_("shin_r", "right_leg", (-2.5, 8, -3.9, 5, 4, 1.2), "plate", base=3, rune=rune_line, glowc="signal"),
        P_("sabaton_r", "right_leg", (-3.2, 9.8, -4.8, 6.4, 2.2, 2.3), "plate", base=4, ramp="brass"),
        P_("boot_cuff_r", "right_leg", (-3.5, 7.2, -3.5, 7, 1.5, 7), "plate", base=3, trim="brass"),
    ]
    s += mirrored(s[-6:], {"tasset_r1": "tasset_l1", "tasset_r2": "tasset_l2", "knee_r": "knee_l", "shin_r": "shin_l",
                           "sabaton_r": "sabaton_l", "boot_cuff_r": "boot_cuff_l", "right_leg": "left_leg"})
    # --- the one piece of cloth: a surcoat panel in gate-blue under the lames
    s += [part("surcoat", "body", (-3.5, 0, 0, 7, 9, 1), "silk", pose=(0, 11.5, -3.6, 0, 0, 0), swing=-0.15, ramp="signal", base=3,
               hem="brass", hem_rows=1, contact=True, fold=3)]
    return s


# ---------------------------------------------------------------------------------------------------------------------
# The materials board: the same shoulder in nine materials.

def board_set(name):
    """A shoulder, upper arm and chest edge: the base layer in the material and a three-lame pauldron over it."""
    cfg = {
        "Hide": ("hide", dict(ramp="pelt", base=3), dict(ramp="hide", fringe=True, base=3), dict(ramp="bone", base=3)),
        "Studded": ("studded", dict(ramp="leather", stud="bronze"), dict(ramp="leather", stud="bronze"), dict(ramp="bronze", base=3)),
        "Plate": ("plate", dict(ramp="slate", base=2), dict(ramp="slate", base=3, trim="brass"), dict(ramp="brass", base=4, rivet=True)),
        "Mail": ("mail", dict(ramp="steel", base=3), dict(ramp="steel", base=3), dict(ramp="steel", base=4)),
        "Silk": ("silk", dict(base=2, fold=4), dict(base=3, fold=3, hem="silver", hem_rows=1), dict(base=4, ramp="silver")),
        "Scale": ("scale", dict(ramp="lord", base=3), dict(ramp="lord", base=3, rim="gold"), dict(ramp="gold", base=4)),
        "Weave": ("weave", dict(ramp="weave", base=3), dict(ramp="weave", base=3, trim="gold"), dict(ramp="gold", base=4)),
        "Alloy": ("alloy", dict(ramp="alloy", base=3), dict(ramp="alloy", base=3), dict(ramp="steel", base=4)),
        "Deepglass": ("glass", dict(ramp="deep"), dict(ramp="deep"), dict(ramp="deep")),
    }[name]
    mat, base_o, mid_o, top_o = cfg
    s = shell_parts(mat, **base_o)
    s = [p for p in s if p["name"] in ("sh_body", "sh_arm_r")]
    pm = "plate" if mat in ("silk", "studded", "hide", "mail") else mat
    s += [
        part("p1", "right_arm", (-5, -3.5, -4, 8, 2, 8), mat, **top_o),
        part("p2", "right_arm", (-5.5, -1.7, -4.4, 9, 2, 8.8), mat, **mid_o),
        part("p3", "right_arm", (-6, 0.1, -4.8, 10, 2, 9.6), mat, **mid_o),
    ]
    return s


def materials(out):
    names = ["Hide", "Studded", "Plate", "Mail", "Silk", "Scale", "Weave", "Alloy", "Deepglass"]
    w, h = 250, 250
    sheet = Image.new("RGBA", (w * 3, h * 3), BG)
    d = ImageDraw.Draw(sheet)
    for k, n in enumerate(names):
        parts, alb, glw = prepare(board_set(n), (128, 128))
        im = render(parts, alb, 0.55, 0.0, scale=11, size=(w, h), centre=(165, 105), pitch=-0.3, skin=False)
        sheet.alpha_composite(im, ((k % 3) * w, (k // 3) * h))
        d.text(((k % 3) * w + 8, (k // 3) * h + 6), n, fill=(210, 216, 230, 255))
    sheet.save(os.path.join(out, "materials.png"))


def prepare(parts, size=(256, 192)):
    seen = set()
    uniq = []
    for p in parts:
        if p["name"] in seen:
            continue
        seen.add(p["name"])
        uniq.append(p)
    # parents before children
    order = {k: 0 for k in BASE}
    pending = list(uniq)
    out = []
    while pending:
        nxt = [p for p in pending if p["parent"] in order]
        if not nxt:
            raise SystemExit("no parent for " + ", ".join(p["name"] for p in pending))
        for p in nxt:
            order[p["name"]] = 1
            out.append(p)
            pending.remove(p)
    pack(out, size)
    alb, glw = paint(out, size)
    return out, alb, glw


# ---------------------------------------------------------------------------------------------------------------------
# Sheets

import numpy as np  # noqa: E402

W, H, CTR = 260, 380, (130, 128)
BG = (18, 22, 32, 255)


def glow_texture(alb, glw):
    """Black where the albedo is opaque (so the glow pass is hidden behind what hides it), the glow colour where lit."""
    a = np.array(alb)
    g = np.array(glw)
    out = np.zeros_like(a)
    out[..., 3] = np.where(a[..., 3] >= 128, 255, 0)
    lit = g[..., 3] >= 128
    out[lit] = g[lit]
    return Image.fromarray(out, "RGBA")


def render_night(parts, alb, glw, yaw, walk=0.0, scale=8, size=(W, H), centre=CTR, pitch=-0.2, dim=0.30, bloom=1.0):
    """A dimmed frame with the glow texels added at full brightness and a soft bloom: an approximation of the game's
    emissive pass plus Lens's bloom, not the real thing."""
    day = render(parts, alb, yaw, walk, scale, size, centre, pitch)
    base = Image.new("RGBA", size, (10, 12, 20, 255))
    d = day.convert("RGBA")
    dm = Image.eval(d, lambda v: int(v * dim))
    dm.putalpha(d.getchannel("A"))
    base.alpha_composite(dm)
    gt = glow_texture(alb, glw)
    s = G.Scene(scale, yaw, pitch, size, centre)
    poses = build_poses(walk, parts)
    black = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    s.cube(poses["head"], black, 0, 0, (-4, -8, -4, 8, 8, 8))
    s.cube(poses["body"], black, 16, 16, (-4, 0, -2, 8, 12, 4))
    s.cube(poses["right_arm"], black, 40, 16, (-3, -2, -2, 4, 12, 4))
    s.cube(poses["left_arm"], black, 32, 48, (-1, -2, -2, 4, 12, 4))
    s.cube(poses["right_leg"], black, 0, 16, (-2, 0, -2, 4, 12, 4))
    s.cube(poses["left_leg"], black, 16, 48, (-2, 0, -2, 4, 12, 4))
    for p in parts:
        s.cube(poses[p["name"]], gt, p["uv"][0], p["uv"][1], p["box"], p["grow"], bool(p["twin"]), shade=False)
    g = s.img.convert("RGB")
    halo = g.filter(ImageFilter.GaussianBlur(scale * 0.8)).point(lambda v: min(255, int(v * 0.9 * bloom)))
    wide = g.filter(ImageFilter.GaussianBlur(scale * 2.2)).point(lambda v: min(255, int(v * 0.6 * bloom)))
    out = base.convert("RGB")
    out = ImageChops.add(out, wide)
    out = ImageChops.add(out, halo)
    out = ImageChops.add(out, g)
    return out.convert("RGBA")


def label(img, text, xy=(8, 6)):
    d = ImageDraw.Draw(img)
    d.text(xy, text, fill=(200, 208, 224, 255))


def grid(rows, w=W, h=H, bg=BG, labels=None):
    cols = max(len(r) for r in rows)
    sheet = Image.new("RGBA", (w * cols, h * len(rows)), bg)
    for r, row in enumerate(rows):
        for c, im in enumerate(row):
            sheet.alpha_composite(im.convert("RGBA"), (c * w, r * h))
    return sheet


def main(out):
    os.makedirs(out, exist_ok=True)
    size = (256, 192)
    kw = dict(scale=8, size=(W, H), centre=CTR, pitch=-0.2)
    yaws = [0.0, 0.7, math.pi / 2, math.pi]
    for tier, fn, tag in (("B", veilwoven_v2, "B"), ("C", gatewrought_v2, "C")):
        parts, alb, glw = prepare(fn(), size)
        alb.resize((alb.width * 4, alb.height * 4), Image.NEAREST).save(os.path.join(out, "atlas_%s.png" % tag))

        def today(yaw, t=tier):
            return G.render_set(t, yaw, scale=8, size=(W, H), centre=CTR, pitch=-0.2)

        grid([[today(y) for y in yaws], [render(parts, alb, y, 0.0, **kw) for y in yaws]]).save(os.path.join(out, "sheet_%s.png" % tag))
        grid([[render(parts, alb, y, w, **kw) for y, w in ((0.7, 0.9), (math.pi / 2, 1.0), (math.pi - 0.6, 0.9))]]).save(
            os.path.join(out, "walk_%s.png" % tag))
        kn = {k: v for k, v in kw.items() if k != "scale"}
        grid([[today(y) for y in (0.0, 0.7)] + [render_night(parts, alb, glw, y, **kn) for y in (0.0, 0.7)]]).save(
            os.path.join(out, "night_%s.png" % tag))
        big = dict(scale=20, size=(520, 560), centre=(260, 360), pitch=-0.15)
        grid([[render(parts, alb, 0.55, 0.0, **big), render_night(parts, alb, glw, 0.55, **big)]], w=520, h=560).save(
            os.path.join(out, "closeup_%s.png" % tag))
        print(tier, "parts:", len(parts), "owners:", len([p for p in parts if not p["twin"]]))
    materials(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "out"))
