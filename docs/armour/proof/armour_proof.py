"""The armour proof engine (docs/armour/PLAN.md §5): materials, painters, the part model and a renderer. Disposable, changes no production file.
The sets are in proof_sets.py, the drivers in render_all.py, the overlap checker in audit.py.

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

    python3 docs/armour/proof/render_all.py OUTDIR
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
    """Scales three texels wide and three tall, each row offset by one: a lit crown, a body, a shadowed lip and a seam."""
    r = o.get("ramp", "lord")
    row = j // 3
    c = (i + (row % 2)) % 3
    k = j % 3
    step = o.get("base", 3) + (1 if face == "up" else 0)
    step += {0: 1, 1: 0, 2: -1}[k]
    if c == 0 and k != 0:
        step -= 1                           # the seam between two scales
    step += bevel(face, i, j, fw, fh, top=1) if o.get("bevel", True) else 0
    if o.get("rim") and face in SIDES and (j >= fh - 1 or i == 0) and fh > 3:
        return col(o["rim"], 4), None
    return col(r, step), None


def hide(face, i, j, fw, fh, o):
    r = o.get("ramp", "pelt")
    step = o.get("base", 3) + FACE[face]
    if i % 3 == 0 and ((j // 3) + i // 3) % 2 == 0:
        step -= 1                           # a strand, three texels long, on a regular beat
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
    """The base layer. Head, body and arms are vanilla's armour boxes (outer 1.0). The legs are slimmer and outer-biased (0.5 out
    on the outside and front and back, flush with the centre line inside), so the two legs' layers never share a plane the way
    vanilla's overlapping leg boxes do. The player's own leg is 0.1 wider than that on the inside, where nothing shows."""
    sh = [part("sh_head", "head", (-4, -8, -4, 8, 8, 8), kind, grow=1.0, **o),
          part("sh_body", "body", (-4, 0, -2, 8, 12, 4), kind, grow=1.0, **o),
          part("sh_arm_r", "right_arm", (-3, -2, -2, 4, 12, 4), kind, grow=1.0, **o),
          part("sh_leg_r", "right_leg", (-2.5, -0.5, -2.5, 4.4, 13.4, 5), kind, grow=0.0, **o)]
    left = {"sh_arm_r": "sh_arm_l", "sh_leg_r": "sh_leg_l", "right_arm": "left_arm", "right_leg": "left_leg"}
    return sh + mirrored(sh[2:], left)




def rune_line(i, j, fw, fh):
    return i == fw // 2 and 1 < j < fh - 1


def rune_bar(i, j, fw, fh):
    return j == fh // 2 and 1 < i < fw - 2


def rune_cross(i, j, fw, fh):
    return (i == fw // 2 and 1 < j < fh - 1) or (j == fh // 3 and 1 < i < fw - 2)




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
