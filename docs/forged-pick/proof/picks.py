"""Disposable proof for the Forged Pick plan: concept picks built with the real kit (voxel boxes, the weapon materials,
the mesh pipeline, the game's weapon shader in mesh_render), rendered to docs/forged-pick/shots. Writes nothing under
src/main and registers nothing: it imports the weapon spec for its helpers and never calls its build.

    python3 docs/forged-pick/proof/picks.py line|forms|all|one <form> <rank>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
import arms_weapons as AW  # noqa: E402
import mesh_render as MR  # noqa: E402
import voxel as VX  # noqa: E402

OUT = os.path.join(HERE, "../shots")
Mat = AW.Mat
RAMPS = AW.RAMPS
HEAD = 23.5
RANKS = "EDCBAS"
FORMS = ("delver", "breaker", "mattock", "sounder")

# ---- this pick's own materials, on the kit's ramps and paint ----------------------------------------------------------
AW.MATS.update({
    "p_ash": Mat("bark", 5, 0.0, 0.3, details=(AW.streak(period=5, run=3),)),
    "p_ash_dark": Mat("bark", 3, 0.0, 0.3, details=(AW.streak(period=5, run=3),)),
    "p_ash_wrap": Mat("bark", 3, 0.0, 0.25, details=(AW.rows(2, -2),)),
    "p_iron": Mat("slate", 3, 0.65, 0.72, details=(AW.streak(),)),
    "p_iron_bright": Mat("slate", 5, 0.75, 0.85),
    "p_iron_tip": Mat("mirror", 4, 0.8, 0.9),
    "p_bloom": Mat("slate", 2, 0.55, 0.6),
    "p_copper": Mat("bronze", 4, 0.55, 0.8),
    "p_diamond": Mat("pearl", 2, 0.5, 1.0, emit=0.3, details=(AW.gem(),), contact=2),
    "p_slit_moss": Mat("moss", 6, 0.0, 0.5, emit=0.95, bevel=False),
    "p_slit_signal": Mat("signal", 6, 0.0, 0.5, emit=0.95, bevel=False),
    "p_slit_spirit": Mat("spirit", 6, 0.0, 0.5, emit=0.95, bevel=False),
    "p_slit_ember": Mat("ember", 6, 0.0, 0.5, emit=0.95, bevel=False),
    "p_slit_crimson": Mat("crimson", 6, 0.0, 0.5, emit=0.95, bevel=False),
    "p_slate": Mat("slate", 3, 0.5, 0.8, details=(AW.streak(),)),
    "p_steel": Mat("slate", 3, 0.75, 0.88, details=(AW.streak(),)),
    "p_steel_tip": Mat("mirror", 5, 0.85, 0.95),
    "p_glass": Mat("glass", 4, 0.3, 1.0, emit=0.5, details=(AW.glassy(),)),
    "p_amethyst": Mat("dream", 3, 0.3, 1.0, emit=0.32),
    "p_veil": Mat("veil", 3, 0.65, 0.88, details=(AW.sheen(),)),
    "p_netherite": Mat("lord", 2, 0.6, 0.85),
    "p_netherite_scale": Mat("lord", 3, 0.55, 0.85, details=(AW.scales(),)),
    "p_ember": Mat("ember", 5, 0.0, 0.6, emit=0.88, bevel=False),
    "p_star": Mat("ivory", 7, 0.3, 0.9, details=(AW.streak(period=9, run=5, phase=2),)),
    "p_star_point": Mat("ivory", 8, 0.35, 0.95, emit=0.2),
    "p_star_light": Mat("pearl", 3, 0.0, 0.5, emit=0.95, bevel=False),
    "p_crimson": Mat("crimson", 5, 0.0, 0.6, emit=0.9, bevel=False),
})


# ---- each rank's dress: what a part of a pick is made of at that rank -----------------------------------------------------
DRESS = {
    "E": dict(body="flint", point="flint", tip="flint_edge", boss="antler", bind="sinew", haft="bone", grip="bone_grip",
              foot="antler", gem="amber_gem", glow="amber", accent="amber"),
    "D": dict(body="p_iron", point="p_iron", tip="p_iron_tip", boss="p_bloom", bind="p_copper", haft="p_ash", grip="p_ash_wrap",
              foot="p_iron", gem="moss_gem", glow="moss", accent="moss"),
    "C": dict(body="p_steel", point="p_steel", tip="p_diamond", boss="brass", bind="brass", haft="p_slate", grip="slate_grip",
              foot="brass", gem="signal_gem", glow="signal", accent="signal"),
    "B": dict(body="p_veil", point="p_veil", tip="mirror", boss="mirror", bind="mirror", haft="obsidian", grip="obsidian",
              foot="mirror", gem="p_amethyst", glow="spirit", accent="spirit"),
    "A": dict(body="p_netherite_scale", point="p_netherite", tip="gold_edge", boss="gold", bind="gold", haft="lord_grip", grip="lord_grip",
              foot="gold", gem="pearl_gem", glow="ember", accent="ember"),
    "S": dict(body="p_star", point="p_star", tip="gold_edge", boss="gold", bind="gold", haft="weave", grip="weave_grip",
              foot="gold", gem="crimson_gem", glow="crimson", accent="crimson"),
}

# The bands the earlier ranks leave on the haft: each reforge keeps a ring of the metal before it (It shows).
RING_OF = {"E": "sinew", "D": "p_copper", "C": "brass", "B": "mirror", "A": "gold"}


def rank_rings(rank, top=17.0, width=4, depth=4):
    """A ring per rank already passed, just under the head, the oldest lowest."""
    B = []
    n = RANKS.index(rank)
    for i in range(n):
        r = RANKS[i]
        y = top - 2 * (n - i)
        B.append(VX.slab(-width / 2, width / 2, y, y + 1, depth, RING_OF[r], f"rank_{r}"))
    return B


def foot(d, y=-5):
    return [VX.slab(-2.5, 2.5, y - 3, y, 5, d["foot"], "foot")]


def haft_(d, y0=-5, y1=21, w=3, grip_to=4):
    return [AW.haft(y0, y1, d["haft"], span=(4, 18), width=w, depth=w), AW.haft(y0, grip_to, d["grip"], "grip", width=w, depth=w)]


SLIT = {"D": "p_slit_moss", "C": "p_slit_signal", "B": "p_slit_spirit", "A": "p_slit_ember", "S": "p_slit_crimson"}


def arc(x0, y0, start, step, seg, widths, depths, mats, part, **kw):
    """A tapering curve out from the eye: a link per width, each a little more drooped than the last."""
    links = [(start + i * step, seg, w, depths[i], mats[i]) for i, w in enumerate(widths)]
    return AW.chain(x0, y0, links, part, stagger=0.05, **kw)


def run(d, n, tips=2):
    """Which material each of n links is: the body, then the point, then the tip."""
    return [d["body"] if i < n * 0.45 else d["point"] if i < n - tips else d["tip"] for i in range(n)]


def eye(rank, d, w=5.0, h=8.0, depth=5, y=HEAD):
    """The boss round the eye. Every forged pick shows its eye: the slit you cut in it, lit from within."""
    B = [VX.slab(-w / 2, w / 2, y - h / 2, y + h / 2, depth, d["boss"], "boss")]
    if rank == "E":
        B.append(VX.slab(-w / 2 - 0.5, w / 2 + 0.5, y - 1.5, y + 1.5, depth + 1, d["bind"], "lash"))
        B.append(VX.rbox((0, y, 0), (2, 2, depth + 3), 45, d["gem"], "bead"))
    else:
        B.append(VX.slab(-0.5, 0.5, y - 2.5, y + 2.5, depth + 1, SLIT[rank], "slit"))
    B.append(VX.slab(-1.5, 1.5, y + h / 2, y + h / 2 + 1, 2, d["haft"], "wedge"))   # the haft's end through the top of the eye
    return B


def collar_(rank, d, width=5):
    B = []
    if rank != "E":
        B.append(VX.slab(-width / 2, width / 2, 18, 20, width, d["bind"], "collar"))
    if rank in "CBAS":
        B += AW.langets(13, 18, d["bind"])
    return B


def delver(rank):
    """The Delver: the true pick. A long drooped crescent, slim as a real pick head, two points."""
    d = DRESS[rank]
    B = haft_(d) + foot(d)
    W = (5, 5, 5, 4, 4, 4, 3, 3, 2, 2, 1)
    D = (4, 4, 4, 4, 3, 3, 3, 2, 2, 2, 1)
    B += AW.both(arc(-2.0, HEAD + 0.5, 74, 6.5, 3, W, D, run(d, len(W)), "arm"))
    B += eye(rank, d) + collar_(rank, d) + rank_rings(rank) + _crest(rank, d, "delver")
    return B, _anchors(rank, d), _look(rank, d)


def breaker(rank):
    """The Breaker: all weight. One great square-shouldered point, a blunt poll behind for the heavy blow, a thick haft."""
    d = DRESS[rank]
    B = [AW.haft(-5, 21, d["haft"], span=(4, 18), width=4, depth=4), AW.haft(-5, 4, d["grip"], "grip", width=4, depth=4)]
    B += foot(d)
    W = (9, 8, 7, 6, 5, 4, 3, 2, 1)
    D = (7, 7, 6, 5, 5, 4, 3, 2, 1)
    B += arc(-3.0, HEAD, 82, 5.5, 3, W, D, run(d, len(W), tips=1), "point")
    B += eye(rank, d, w=7.0, h=10.0, depth=8)
    B.append(VX.slab(3.5, 8.5, 19.5, 27.5, 7, d["body"], "poll"))
    B.append(VX.slab(8.5, 11.5, 18.5, 28.5, 9, d["bind"], "face"))
    B.append(VX.slab(11.5, 12.5, 19.5, 27.5, 7, d["boss"], "strike"))
    B += collar_(rank, d, width=6) + rank_rings(rank, width=5, depth=5) + _crest(rank, d, "breaker")
    return B, _anchors(rank, d), _look(rank, d)


def mattock(rank):
    """The Mattock: the earth's tool. A broad adze curling down on one side, a short pick on the other."""
    d = DRESS[rank]
    B = haft_(d) + foot(d)
    n = 6
    links = [(84, 3, 5, 6, d["body"]), (98, 3, 4, 8, d["body"]), (114, 3, 3, 10, d["body"]), (130, 3, 3, 11, d["point"]), (146, 3, 2, 12, d["point"]), (160, 3, 1, 12, d["tip"])]
    B += AW.chain(-2.0, HEAD + 0.5, links, "adze", stagger=0.05)
    W = (5, 4, 4, 3, 2, 1)
    D = (4, 4, 3, 3, 2, 1)
    B += VX.mirror(arc(-2.0, HEAD + 0.5, 84, 9, 3, W, D, run(d, len(W)), "point"))
    B += eye(rank, d) + collar_(rank, d) + rank_rings(rank) + _crest(rank, d, "mattock")
    return B, _anchors(rank, d), _look(rank, d)


def sounder(rank):
    """The Sounder: the listener. A long thin needle of a point, and behind the eye a tuning fork whose tines ring."""
    d = DRESS[rank]
    B = [AW.haft(-5, 21, d["haft"], span=(4, 18), width=2, depth=2), AW.haft(-5, 4, d["grip"], "grip", width=3, depth=3)]
    B += foot(d)
    W = (4, 3, 3, 3, 2, 2, 2, 1, 1)
    D = (3, 3, 3, 2, 2, 2, 1, 1, 1)
    B += arc(-2.0, HEAD, 86, 3.2, 3, W, D, run(d, len(W)), "needle")
    B += eye(rank, d, w=4.0, h=7.0, depth=4)
    B.append(VX.slab(2.0, 5.0, HEAD - 1.5, HEAD + 1.5, 3, d["body"], "stem"))
    B.append(VX.slab(5.0, 6.0, HEAD - 3.5, HEAD + 3.5, 3, d["boss"], "yoke"))
    tine = d["tip"] if rank not in "E" else d["body"]
    for sy in (-1, 1):
        B.append(VX.slab(6.0, 14.0, HEAD + sy * 3 - 0.5, HEAD + sy * 3 + 0.5, 2, tine, f"tine{sy}"))
    B.append(VX.rbox((10.0, HEAD, 0), (2, 2, 2), 45, d["gem"], "held"))
    B += collar_(rank, d, width=3) + rank_rings(rank, width=3, depth=3) + _crest(rank, d, "sounder")
    return B, _anchors(rank, d), _look(rank, d)


def _crest(rank, d, form):
    """What only this rank brings: bronze bands at D, diamonds at C, the glass fin at B, the ember seam at A, the star's crown at S."""
    B = []
    if rank == "D":
        for x in (-3.0, 3.0):
            B.append(VX.box(x - 0.5, x + 0.5, HEAD - 3.5, HEAD + 3.5, -2.5, 2.5, d["bind"], f"band{x}"))
    if rank == "B":   # Deepglass threaded along the head, standing proud of it like a fin
        reach = {"delver": (72, 6.5, 6), "breaker": (80, 5.5, 5), "mattock": (84, 9.0, 3), "sounder": (84, 3.2, 5)}[form]
        links = [(reach[0] + i * reach[1], 3, 2, 2, "p_glass") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 3.0 if form != "breaker" else HEAD + 4.5, links, "fin", stagger=0.05)
        B += AW.both(out) if form == "delver" else out
    if rank == "A":   # an ember seam along the arm
        reach = {"delver": (74, 6.5, 6), "breaker": (82, 5.5, 5), "mattock": (84, 9.0, 3), "sounder": (86, 3.2, 5)}[form]
        depth = {"delver": 5, "breaker": 8, "mattock": 5, "sounder": 4}[form]
        links = [(reach[0] + i * reach[1], 4, 1, depth + 1 - (i // 2), "p_ember") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 0.5, links, "seam", stagger=0.0)
        B += AW.both(out) if form == "delver" else out
    if rank == "S":
        reach = {"delver": (74, 6.5, 3), "breaker": (82, 5.5, 3), "mattock": (84, 9.0, 2), "sounder": (86, 3.2, 3)}[form]
        depth = {"delver": 5, "breaker": 8, "mattock": 5, "sounder": 4}[form]
        links = [(reach[0] + i * reach[1], 4, 1, depth + 1, "p_star_light") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 0.5, links, "star_seam", stagger=0.0)
        B += AW.both(out) if form == "delver" else out
        for x, h in ((-2.0, 2), (2.0, 2), (0.0, 3)):
            B.append(VX.rbox((x, HEAD + 5.5 + h / 2, 0), (2, h, 2), 0, "gold", f"crown{x}"))
    return B


def _anchors(rank, d):
    return [((0, HEAD, 0), 2.2, RAMPS[d["glow"]][5], 1)] if rank != "E" else []


def _look(rank, d):
    return dict(accent=RAMPS[d["accent"]][4], grip=(-5, 3), focus=(0, HEAD - 1))


DESIGN = dict(delver=delver, breaker=breaker, mattock=mattock, sounder=sounder)


NOT_HEAD = ("haft", "grip", "foot", "collar", "langets", "wedge")


def build(form, rank, head_only=False):
    boxes, anchors, look = DESIGN[form](rank)
    if head_only:
        boxes = [b for b in boxes if b.part not in NOT_HEAD and not b.part.startswith("rank_")]
    item_anchors = [(tuple(AW.to_item(np.array([c]))[0]), r * AW.SCALE, AW.hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
    built = VX.build(boxes, AW.painter(False), AW.to_item, width=64, anchors=item_anchors)
    return built, look, AW.as_render(built, look)


BG = np.array([17, 16, 22])


def turn(form, rank, yaw=28, pitch=14, scale=15, size=(460, 620), bg=BG, centre=(0.5, 0.55, 0.5), **kw):
    built, look, m = build(form, rank)
    up = AW.upright(m)
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    return MR.render(up, built.texture(), yaw, pitch, scale=scale, size=size, centre=tuple(c), time=0.27, bg=bg, **kw)


def font(px):
    for p in ("/System/Library/Fonts/Supplemental/Georgia.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def line(form="delver"):
    W, H = 400, 640
    sheet = Image.new("RGB", (W * 6, H), tuple(int(v) for v in BG))
    for i, r in enumerate(RANKS):
        sheet.paste(turn(form, r, scale=11.4, size=(W, H)), (i * W, 0))
    sheet.save(os.path.join(OUT, f"line_{form}.png"))


def forms(rank="C"):
    W, H = 560, 660
    sheet = Image.new("RGB", (W * 4, H), tuple(int(v) for v in BG))
    for i, f in enumerate(FORMS):
        sheet.paste(turn(f, rank, scale=15.2, size=(W, H)), (i * W, 0))
    sheet.save(os.path.join(OUT, f"forms_{rank}.png"))


def table():
    W, H = 260, 330
    sheet = Image.new("RGB", (W * 6, H * 4), tuple(int(v) for v in BG))
    for j, f in enumerate(FORMS):
        for i, r in enumerate(RANKS):
            sheet.paste(turn(f, r, scale=8.2, size=(W, H)), (i * W, j * H))
    sheet.save(os.path.join(OUT, "table.png"))


# ---- the temper colours, the hot head, and the views for the page -----------------------------------------------------------

# Oxide colours in the order they run along steel (FAO's and every smith's table): pale straw first, blue last.
TEMPER = [(0.00, (247, 236, 190)), (0.16, (226, 186, 104)), (0.34, (168, 104, 52)), (0.52, (128, 62, 118)), (0.74, (56, 76, 176)), (1.00, (136, 148, 168))]


def temper_rgb(t):
    t = np.clip(t, 0, 1)
    xs = [p[0] for p in TEMPER]
    return np.stack([np.interp(t, xs, [p[1][c] / 255.0 for p in TEMPER]) for c in range(3)], axis=-1)


def across(up):
    """Distance out from the eye along the head, 0 at the eye and 1 at the points."""
    eye = float((up["pos"][:, 0].min() + up["pos"][:, 0].max()) / 2)
    d = np.abs(up["pos"][:, 0] - eye)
    return d / d.max()


def temper_shader(stop):
    """The game's shader, then the oxide colours laid over bare steel: straw at the running front, blue behind it toward
    the eye; past the front the steel is still bright. stop: how far the colours have run from the eye (0..1)."""
    def f(alb, mat, n_world, n_view, along, accent, **kw):
        rgb = MR.shade(alb, mat, n_world, n_view, along, accent, **kw)
        # along = 0 at the eye, 1 at the point. The front sits at `stop`; behind it the colour deepens toward blue.
        behind = (stop - along) / 1.6
        on = (along < stop)
        tint = temper_rgb(behind)
        metal = mat[:, 0:1]
        lum = rgb.mean(axis=1, keepdims=True)
        tinted = tint * (0.42 + 1.05 * lum)
        out = np.where(on[:, None], tinted * metal + rgb * (1 - metal), rgb)
        return np.clip(out, 0, 1)
    return f


def head_view(form, rank, yaw=8, pitch=8, scale=22, size=(1000, 560), bg=BG, up_shift=0.0, **kw):
    built, look, m = build(form, rank, head_only=True)
    up = AW.upright(m)
    up = dict(up, along=across(up))
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    return MR.render(up, built.texture(), yaw, pitch, scale=scale, size=size, centre=tuple(c), time=0.27, bg=bg, **kw)


def temper_strip():
    W, H = 640, 330
    stops = [("straw", 1.0), ("bronze", 1.55), ("peacock", 1.85), ("blue", 2.18)]
    sheet = Image.new("RGB", (W * 2, H * 2), tuple(int(v) for v in BG))
    for i, (_, st) in enumerate(stops):
        sheet.paste(head_view("delver", "D", scale=18, size=(W, H), shader=temper_shader(st)), ((i % 2) * W, (i // 2) * H))
    sheet.save(os.path.join(OUT, "temper_four.png"))


def demo_maps():
    """For the page's live demo: the head in bare steel, and how far along the head each pixel is."""
    W, H = 760, 280
    key = np.array([255, 0, 255])
    base = head_view("delver", "D", scale=21, size=(W, H), bg=key)
    def along_only(alb, mat, n_world, n_view, along, accent, **kw):
        v = np.repeat(np.clip(along, 0, 1)[:, None], 3, axis=1)
        return v * mat[:, 0:1].clip(0.0, 1.0).__gt__(0.3) + (1 - mat[:, 0:1].__gt__(0.3)) * 0.0 + 0.0
    dist = head_view("delver", "D", scale=21, size=(W, H), bg=key, shader=along_only)
    a = np.asarray(base).astype(np.uint8)
    mask = ~((a[..., 0] > 250) & (a[..., 1] < 5) & (a[..., 2] > 250))
    rgba = np.dstack([a, (mask * 255).astype(np.uint8)])
    Image.fromarray(rgba, "RGBA").save(os.path.join(OUT, "demo_base.png"))
    d = np.asarray(dist).astype(np.uint8)
    Image.fromarray(d[..., 0], "L").save(os.path.join(OUT, "demo_along.png"))


def cutout(img, key=(255, 0, 255)):
    a = np.asarray(img.convert("RGB")).astype(np.int32)
    on = ~((a[..., 0] > 245) & (a[..., 1] < 12) & (a[..., 2] > 245))
    return Image.fromarray(np.dstack([a.astype(np.uint8), (on * 255).astype(np.uint8)]), "RGBA")


SHOTS = "/Volumes/MEDIA/Awaken/.claude/worktrees/armour-forge-plan-c98ed9/run-autopilot/screenshots-ci/arms-anvil-ranks-41507062640"
KEY = np.array([255, 0, 255])


def hot_view(form, rank, size, scale, yaw=18, pitch=26, heat=0.72, front=0.0, roll=None, shader=None, **kw):
    built, look, m = build(form, rank, head_only=True)
    up = AW.upright(m)
    up = dict(up, along=across(up))
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    args = dict(time=0.27, bg=KEY)
    if shader:
        args["shader"] = shader
    else:
        args.update(heat=heat, front=front)
    args.update(kw)
    return cutout(MR.render(up, built.texture(), yaw, pitch, scale=scale, size=size, centre=tuple(c), **args))


def glow_under(base, piece, at, radius, colour, strength=0.9):
    """A soft light on the scene under a hot piece."""
    from PIL import ImageFilter
    W, H = base.size
    pw, ph = piece.size
    layer = Image.new("L", (W, H), 0)
    layer.paste(piece.getchannel("A"), at)
    layer = layer.filter(ImageFilter.GaussianBlur(radius))
    arr = np.asarray(base.convert("RGB")).astype(float)
    g = np.asarray(layer).astype(float)[..., None] / 255.0 * strength
    col = np.array(colour, float)
    arr = arr + g * col * 0.9
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def crop_scene(name, top=120, bottom=1180):
    return Image.open(os.path.join(SHOTS, name)).convert("RGB").crop((0, top, 2560, bottom))


def darken_rect(img, box, amount=0.0):
    pass


def moment_eye():
    """Mockup: the eye slit into the hot bar. Real anvil shot; the bar and the slit are shaded by the game's shader, hot."""
    base = crop_scene("ranks_balance.png", 215, 1175)
    bar = bar_hot()
    at = (500, 40)
    base = glow_under(base, bar, at, 70, (255, 120, 30), 0.9)
    base.paste(bar, at, bar)
    base.save(os.path.join(OUT, "moment_eye.jpg"), quality=86)


def bar_hot():
    """A billet: a thick bar with the slit cut through it, its ends already starting to draw out."""
    B = [VX.slab(-5, 5, 19, 28, 7, "p_iron", "bar")]
    for sx in (-1, 1):
        B.append(VX.slab(min(sx * 5, sx * 9), max(sx * 5, sx * 9), 20, 27, 6, "p_iron", f"end{sx}"))
        B.append(VX.slab(min(sx * 9, sx * 12), max(sx * 9, sx * 12), 21, 26, 4, "p_iron", f"tip{sx}"))
    B.append(VX.slab(-1, 1, 21, 26, 9, "p_slit_ember", "slit"))
    built = VX.build(B, AW.painter(False), AW.to_item, width=64, anchors=[])
    look = dict(accent=RAMPS["ember"][5], grip=(-5, 3), focus=(0, HEAD))
    m = AW.as_render(built, look)
    up = AW.upright(m)
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    img = MR.render(up, built.texture(), 24, 40, scale=44, size=(1250, 760), centre=tuple(c), time=0.27, bg=KEY, heat=0.8, front=0.0)
    return cutout(img)


def moment_colours():
    """Mockup: the colours running along a finished head on the anvil."""
    base = crop_scene("ranks_balance.png", 215, 1175)
    head = hot_view("delver", "D", (1560, 780), 52, yaw=16, pitch=34, shader=temper_shader(0.86))
    at = (130, 40)
    base.paste(head, at, head)
    base.save(os.path.join(OUT, "moment_colours.jpg"), quality=86)


def moment_hang():
    """Mockup: the haft rapped on the stone, the head climbing it and seating."""
    base = crop_scene("ranks_lord_close.png", 120, 1180)
    built, look, m = build("delver", "D")
    up = AW.upright(m)
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    img = cutout(MR.render(up, built.texture(), 30, 10, scale=27, size=(1200, 1060), centre=tuple(c), time=0.27, bg=KEY))
    at = (640, 8)
    base.paste(img, at, img)
    d = ImageDraw.Draw(base, "RGBA")
    # the note rising as the head seats: rings off the eye, a shock where the foot lands
    cx, cy = at[0] + 600, at[1] + 250
    for r, a in ((230, 150), (290, 90), (350, 50)):
        d.arc((cx - r, cy - r, cx + r, cy + r), 205, 335, fill=(255, 226, 170, a), width=5)
    fx, fy = at[0] + 590, at[1] + 985
    for r, a in ((60, 170), (105, 95)):
        d.ellipse((fx - r * 1.6, fy - r * 0.42, fx + r * 1.6, fy + r * 0.42), outline=(255, 226, 170, a), width=4)
    base.save(os.path.join(OUT, "moment_hang.jpg"), quality=86)


def in_hand():
    """Real mesh, a real weapon's first-person hold (the Maul's), the game's shader, over a darkened real scene."""
    scene = crop_scene("ranks_lord_close.png", 120, 1180).resize((1280, 530))
    scene = Image.fromarray((np.asarray(scene).astype(float) * 0.5).astype(np.uint8), "RGB")
    built, look, m = build("delver", "C")
    fp = cutout(MR.render_fp(m, built.texture(), display=MR.held("wildhide_maul"), size=(1280, 720), time=0.27, bg=KEY))
    c = fp.crop((0, 95, 1280, 625))
    scene.paste(c, (0, 0), c)
    scene.save(os.path.join(OUT, "in_hand.jpg"), quality=86)


def sprites():
    """Today's seven verse icons, real textures from the game, large."""
    tdir = os.path.join(ROOT, "src/main/resources/assets/awaken/textures/item")
    names = sorted(f for f in os.listdir(tdir) if f.startswith("waking_pick"))
    print(names)
    sheet = Image.new("RGBA", (len(names) * 150, 150), (17, 16, 22, 255))
    for i, n in enumerate(names):
        ic = Image.open(os.path.join(tdir, n)).convert("RGBA").resize((128, 128), Image.NEAREST)
        sheet.paste(ic, (i * 150 + 11, 11), ic)
    sheet.convert("RGB").save(os.path.join(OUT, "today_sprites.png"))


def main(argv):
    os.makedirs(OUT, exist_ok=True)
    cmd = argv[0] if argv else "all"
    if cmd in ("line", "all"):
        line()
    if cmd in ("forms", "all"):
        forms()
    if cmd in ("table", "all"):
        table()
    if cmd in ("views", "all"):
        temper_strip()
        demo_maps()
        moment_eye()
        moment_colours()
        moment_hang()
        in_hand()
        sprites()
    if cmd == "one":
        turn(argv[1], argv[2], scale=22, size=(900, 900)).save(os.path.join(OUT, f"one_{argv[1]}_{argv[2]}.png"))


if __name__ == "__main__":
    main(sys.argv[1:])
