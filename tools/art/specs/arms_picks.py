"""The Forged Picks (SPEC §49): four forms at six ranks, 24 sculpted picks from the weapons' voxel kit and materials. Each
writes what a weapon does (mesh, texture, icon, item JSON), named <prefix>_pick_<form>, plus the shared lang and tags.

The mesh's "along" is not height here. It carries the two things the anvil and the shader ask of a pick: the haft runs
0..0.74 from the foot up; the head runs 0.76..1 from the eye out to its points, so a cut at 0.75 draws the head alone, and
heat, the temper colours and wear all run along the head the way they do on real steel.

Every triangle has a group (mesh flag GROUPS): 0 always drawn; 1-5 the ring a rank E..A leaves on the haft, drawn only for
ranks this pick was really raised through.

    python3 tools/art/specs/arms_picks.py build [ids...] | check | sheet <out.png> | fp <out.png>
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, ".."))
import arms_holds as AH  # noqa: E402
import arms_weapons as AW  # noqa: E402
import gear as G  # noqa: E402
import mesh as MS  # noqa: E402
import mesh_render as MR  # noqa: E402
import voxel as VX  # noqa: E402

RES = AW.RES
MERGE = os.path.join(AW.ROOT, "src/main/merge/delve")
Mat = AW.Mat
RAMPS = AW.RAMPS
HEAD = 23.5
RANKS = "EDCBAS"
FORMS = ("delver", "breaker", "mattock", "sounder")
FORM_NAME = {"delver": "Delver", "breaker": "Breaker", "mattock": "Mattock", "sounder": "Sounder"}

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

# Each raise leaves a ring of the metal before it on the haft, climbing from the grip: E lowest, A highest, at fixed heights
# so a pick first made at C shows two empty places below its first ring.
RING_OF = {"E": "sinew", "D": "p_copper", "C": "brass", "B": "mirror", "A": "gold"}
RING_Y = {"E": 4, "D": 6, "C": 8, "B": 10, "A": 12}


def rank_rings(rank, width=4, depth=4):
    return [VX.slab(-width / 2, width / 2, RING_Y[r], RING_Y[r] + 1, depth, RING_OF[r], f"rank_{r}") for r in RANKS[:RANKS.index(rank)]]


def foot(d, y=-5):
    return [VX.slab(-2.5, 2.5, y - 3, y, 5, d["foot"], "foot")]


def haft_(d, y0=-5, y1=21, w=3, grip_to=4):
    return [AW.haft(y0, y1, d["haft"], span=(4, 18), width=w, depth=w), AW.haft(y0, grip_to, d["grip"], "grip", width=w, depth=w)]


SLIT = {"D": "p_slit_moss", "C": "p_slit_signal", "B": "p_slit_spirit", "A": "p_slit_ember", "S": "p_slit_crimson"}


def arc(x0, y0, start, step, seg, widths, depths, mats, part, **kw):
    """A tapering curve out from the eye: a link per width, each a little more drooped than the last."""
    links = [(start + i * step, seg, w, depths[i], mats[i]) for i, w in enumerate(widths)]
    return AW.chain(x0, y0, links, part, stagger=0.05, cycle=4, **kw)


def run(d, n, tips=2):
    return [d["body"] if i < n * 0.45 else d["point"] if i < n - tips else d["tip"] for i in range(n)]


def eye(rank, d, w=5.0, h=8.0, depth=5, y=HEAD):
    """The boss round the eye. Every forged pick shows its eye: the slit you cut in it, lit from within."""
    B = [VX.slab(-w / 2, w / 2, y - h / 2, y + h / 2, depth, d["boss"], "boss")]
    if rank == "E":
        B.append(VX.slab(-w / 2 - 0.5, w / 2 + 0.5, y - 1.5, y + 1.5, depth + 1, d["bind"], "lash"))
        B.append(VX.rbox((0, y, 0), (2, 2, depth + 3), 45, d["gem"], "bead"))
    else:
        B.append(VX.slab(-0.5, 0.5, y - 2.5, y + 2.5, depth + 1, SLIT[rank], "slit"))
    B.append(VX.slab(-1.5, 1.5, y + h / 2, y + h / 2 + 1, 2, d["haft"], "wedge"))
    return B


def collar_(rank, d, width=5):
    B = []
    if rank != "E":
        B.append(VX.slab(-width / 2, width / 2, 17.5, 19.5, width, d["bind"], "collar"))
    if rank in "CBAS":
        B += AW.langets(13.5, 17.5, d["bind"])
    return B


def _crest(rank, d, form):
    """What only this rank brings: bronze bands at D, the glass fin at B, the ember seam at A, the star's crown at S."""
    B = []
    if rank == "D":
        for x in (-3.0, 3.0):
            B.append(VX.box(x - 0.5, x + 0.5, HEAD - 3.5, HEAD + 3.5, -2.5, 2.5, d["bind"], f"band{x}"))
    if rank == "B":
        reach = {"delver": (72, 6.5, 6), "breaker": (80, 5.5, 5), "mattock": (84, 9.0, 3), "sounder": (84, 3.2, 5)}[form]
        links = [(reach[0] + i * reach[1], 3, 2, 2, "p_glass") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 3.0 if form != "breaker" else HEAD + 4.5, links, "fin", stagger=0.05)
        B += AW.both(out) if form == "delver" else out
    if rank == "A":
        reach = {"delver": (74, 6.5, 6), "breaker": (82, 5.5, 5), "mattock": (84, 15.0, 3), "sounder": (86, 3.2, 5)}[form]
        depth = {"delver": 5, "breaker": 8, "mattock": 5, "sounder": 4}[form]
        links = [(reach[0] + i * reach[1], 4, 1, (9, 10, 12)[i] if form == "mattock" else depth + 1 - (i // 2), "p_ember") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 0.5, links, "seam", stagger=0.05, cycle=4)
        B += AW.both(out) if form == "delver" else out
    if rank == "S":
        reach = {"delver": (74, 6.5, 3), "breaker": (82, 5.5, 3), "mattock": (84, 15.0, 2), "sounder": (86, 3.2, 3)}[form]
        depth = {"delver": 5, "breaker": 8, "mattock": 5, "sounder": 4}[form]
        links = [(reach[0] + i * reach[1], 4, 1, (9, 10)[i] if form == "mattock" else depth + 1, "p_star_light") for i in range(reach[2])]
        out = AW.chain(-2.0, HEAD + 0.5, links, "star_seam", stagger=0.05, cycle=4)
        B += AW.both(out) if form == "delver" else out
        base = HEAD + (6.0 if form == "breaker" else 5.5)  # on top of the haft's wedge
        for x, h in ((-2.0, 2), (2.0, 2), (0.0, 3)):
            B.append(VX.rbox((x, base + h / 2, 0), (2, h, 2), 0, "gold", f"crown{x}"))
    return B


def _anchors(rank, d):
    return [((0, HEAD, 0), 2.2, RAMPS[d["glow"]][5], 1)] if rank != "E" else []


def _look(rank, d):
    return dict(accent=RAMPS[d["accent"]][4], grip=(-5, 3), focus=(0, HEAD - 1))


def delver(rank):
    """The true pick: a long drooped crescent, slim as a real pick head, two points."""
    d = DRESS[rank]
    B = haft_(d) + foot(d)
    W = (5, 5, 5, 4, 4, 4, 3, 3, 2, 2, 1)
    D = (4, 4, 4, 4, 3, 3, 3, 2, 2, 2, 1)
    B += AW.both(arc(-2.0, HEAD + 0.5, 74, 6.5, 3, W, D, run(d, len(W)), "arm"))
    B += eye(rank, d) + collar_(rank, d) + rank_rings(rank) + _crest(rank, d, "delver")
    return B, _anchors(rank, d), _look(rank, d)


def breaker(rank):
    """All weight: one great square-shouldered point, a blunt poll behind for the heavy blow, a thick haft."""
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
    """The earth's tool: a broad adze curling down on one side (-x), a short pick on the other (+x). The hand spins it so
    the right end leads: the pick on stone, the adze on earth and wood."""
    d = DRESS[rank]
    B = haft_(d) + foot(d)
    links = [(84, 3, 5, 7, d["body"]), (98, 3, 4, 8, d["body"]), (114, 3, 3, 10, d["body"]), (130, 3, 3, 11, d["point"]),
             (146, 3, 2, 12, d["point"]), (160, 3, 1, 12, d["tip"])]
    B += AW.chain(-2.0, HEAD + 0.5, links, "adze", stagger=0.05)
    W = (5, 4, 4, 3, 2, 1)
    D = (4, 4, 3, 3, 2, 1)
    B += VX.mirror(arc(-2.0, HEAD + 0.5, 84, 9, 3, W, D, run(d, len(W)), "point"))
    B += eye(rank, d) + collar_(rank, d) + rank_rings(rank) + _crest(rank, d, "mattock")
    return B, _anchors(rank, d), _look(rank, d)


def sounder(rank):
    """The listener: a long thin needle of a point, and behind the eye a tuning fork whose tines ring."""
    d = DRESS[rank]
    B = [AW.haft(-5, 21, d["haft"], span=(4, 18), width=2, depth=2), AW.haft(-5, 4, d["grip"], "grip", width=3, depth=3)]
    B += foot(d)
    W = (4, 3, 3, 3, 2, 2, 2, 1, 1)
    D = (3, 3, 3, 2, 2, 2, 1, 1, 1)
    B += arc(-2.0, HEAD, 86, 3.2, 3, W, D, run(d, len(W)), "needle")
    B += eye(rank, d, w=4.0, h=7.0, depth=4)
    B.append(VX.slab(2.0, 5.0, HEAD - 1.0, HEAD + 1.0, 3, d["body"], "stem"))
    B.append(VX.slab(5.0, 6.0, HEAD - 3.5, HEAD + 3.5, 3, d["boss"], "yoke"))
    tine = d["tip"] if rank not in "E" else d["body"]
    for sy in (-1, 1):
        B.append(VX.slab(6.0, 14.0, HEAD + sy * 3 - 0.5, HEAD + sy * 3 + 0.5, 2, tine, f"tine{sy}"))
    B.append(VX.rbox((10.0, HEAD, 0), (2, 2, 2), 45, d["gem"], "held"))
    B += collar_(rank, d, width=3) + rank_rings(rank, width=3, depth=3) + _crest(rank, d, "sounder")
    return B, _anchors(rank, d), _look(rank, d)


DESIGN = dict(delver=delver, breaker=breaker, mattock=mattock, sounder=sounder)
NOT_HEAD = ("haft", "grip", "foot", "collar", "langets", "wedge")
ID = {(f, r): f"{G.SET[r]}_pick_{f}" for f in FORMS for r in RANKS}
HAFT_TOP = 21.0


def is_head(part):
    p = (part or "").rstrip("~'")
    return not p.startswith(NOT_HEAD) and not p.startswith("rank_")


def design_xy(pos):
    a = math.radians(AW.ANGLE)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    return (pos - np.asarray(AW.HAND)) @ R


def finish(built):
    """The pick's own "along" and its triangles' groups (the module's note)."""
    d = design_xy(built.pos)
    head = np.array([is_head(p) for p in built.parts])
    low = d[:, 1].min()
    radial = np.hypot(d[:, 0], d[:, 1] - HEAD)
    rmax = max(radial[head].max(), 1e-6)
    along = np.where(head, 0.76 + 0.24 * np.clip(radial / rmax, 0, 1), 0.74 * np.clip((d[:, 1] - low) / (HAFT_TOP - low), 0, 1))
    built.along = along
    groups = []
    for t in range(len(built.parts) // 3):
        p = built.parts[t * 3] or ""
        groups.append(RANKS.index(p[5]) + 1 if p.startswith("rank_") else 0)
    built.groups = groups
    return built


def make(form, rank, clean=False):
    boxes, anchors, look = DESIGN[form](rank)
    item_anchors = [(tuple(AW.to_item(np.array([c]))[0]), r * AW.SCALE, AW.hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
    built = VX.build(boxes, AW.painter(clean), AW.to_item, width=64, anchors=item_anchors)
    return finish(built), look


# First person, at rest: low on the haft, the head up and away to the right, its curve toward you so the shape reads while
# the middle of the view stays clear for what you're digging.
HOLD = dict(yaw=2, pitch=64, roll=38, grip=(-0.02, -0.10, 0.04), size=0.86)


def hold_of(pos):
    d = AH.solve(pos, HOLD, 0)
    lrot, ltr, _ = AH.mirror_display(d)
    rot, tr, sc = d
    return {"firstperson_righthand": {"rotation": list(rot), "translation": list(tr), "scale": [sc] * 3},
            "firstperson_lefthand": {"rotation": lrot, "translation": ltr, "scale": [sc] * 3},
            "thirdperson_righthand": _third(False), "thirdperson_lefthand": _third(True)}


def _third(left):
    # vanilla's handheld, the fist further down the haft (a pick is held near its foot, not at the middle)
    (rx, ry, rz), t, sc = AH.HANDHELD_3P
    k = 4.5
    moved = np.asarray(t, dtype=float) + 16 * sc * (MR.euler(rx, ry, rz) @ (AH.AXIS_ITEM * k / (16 * sc)))
    if left:
        return {"rotation": [rx, -ry, -rz], "translation": [round(float(-moved[0]), 3), round(float(moved[1]), 3), round(float(moved[2]), 3)], "scale": [sc] * 3}
    return {"rotation": [rx, ry, rz], "translation": [round(float(v), 3) for v in moved], "scale": [sc] * 3}


def write(form, rank, lang):
    wid = ID[form, rank]
    built, look = make(form, rank)
    MS.write(os.path.join(RES, "assets/awaken/arms/weapons", wid + ".mesh"), built, AW.hex_rgb(look["accent"]))
    tex_dir = os.path.join(RES, "assets/awaken/textures/item/arms")
    Image.fromarray(built.texture(), "RGBA").save(os.path.join(tex_dir, wid + ".png"))
    AW.icon(make(form, rank, clean=True)[0], look).save(os.path.join(tex_dir, wid + "_icon.png"))
    tex = "awaken:item/arms/" + wid
    AW._write(os.path.join(RES, "assets/awaken/models/item", wid + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": tex + "_icon"}})
    AW._write(os.path.join(RES, "assets/awaken/models/item", wid + "_in_hand.json"), {
        "parent": "minecraft:item/handheld", "textures": {"particle": tex + "_icon"}, "display": hold_of(built.pos / 16.0)})
    AW._write(os.path.join(RES, "assets/awaken/items", wid + ".json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": "awaken:item/" + wid}}],
        "fallback": {"type": "minecraft:special", "base": "awaken:item/" + wid + "_in_hand", "model": {"type": "awaken:pick", "pick": wid}}}})
    lang["item.awaken." + wid] = f"{G.SET[rank].capitalize()} {FORM_NAME[form]}"


def write_shared(lang):
    ids = [ID[f, r] for r in RANKS for f in FORMS]
    AW._write(os.path.join(MERGE, "data/awaken/tags/item/forged_picks.json"), {"values": ["awaken:" + i for i in ids]})
    pick_tag = os.path.join(MERGE, "data/minecraft/tags/item/pickaxes.json")
    AW._write(pick_tag, {"values": ["awaken:waking_pick"] + ["awaken:" + i for i in ids]})
    lpath = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur.update(lang)
    with open(lpath, "w") as fh:
        json.dump(dict(sorted(cur.items())), fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def write_all(only=None):
    os.makedirs(os.path.join(RES, "assets/awaken/arms/weapons"), exist_ok=True)
    os.makedirs(os.path.join(RES, "assets/awaken/textures/item/arms"), exist_ok=True)
    lang = {}
    for f in FORMS:
        for r in RANKS:
            if only and ID[f, r] not in only:
                lang["item.awaken." + ID[f, r]] = f"{G.SET[r].capitalize()} {FORM_NAME[f]}"
                continue
            write(f, r, lang)
    write_shared(lang)


def check():
    bad = []
    for f in FORMS:
        for r in RANKS:
            built, look = make(f, r)
            n = len(built.pos)
            lo, hi = built.pos.min(axis=0) / 16.0, built.pos.max(axis=0) / 16.0
            if n > AW.BUDGET:
                bad.append(f"{ID[f, r]}: {n} corners")
            if lo.min() < -1 or hi.max() > 2.4:  # (the head reaches past the item box along the diagonal: drawn as a mesh, nothing clips it)
                bad.append(f"{ID[f, r]}: outside the item box {lo.round(2)} {hi.round(2)}")
            if built.albedo.shape[0] > 256:
                bad.append(f"{ID[f, r]}: atlas {built.albedo.shape[0]} tall")
            for w in sorted(set(VX.WARNINGS)):
                bad.append(f"{ID[f, r]}: {w}")
            if built.along.max() > 1.0 or not (built.along >= 0.76 - 1e-6).any():
                bad.append(f"{ID[f, r]}: along")
            if max(built.groups) != RANKS.index(r):
                bad.append(f"{ID[f, r]}: rings {max(built.groups)}")
            print(f"  {ID[f, r]:26s} {n // 3:5d} tris  atlas {built.albedo.shape[1]}x{built.albedo.shape[0]}")
    return bad


def sheet(out):
    bg = np.array([22, 24, 31])
    W, H = 250, 330
    im = Image.new("RGB", (W * 6, H * 4), tuple(int(v) for v in bg))
    for j, f in enumerate(FORMS):
        for i, r in enumerate(RANKS):
            built, look = make(f, r)
            up = AW.upright(AW.as_render(built, look))
            lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
            im.paste(MR.render(up, built.texture(), 28, 14, scale=8.0, size=(W, H), centre=tuple((lo + hi) / 2), time=0.27, bg=bg), (i * W, j * H))
            ic = AW.icon(make(f, r, clean=True)[0], look).resize((64, 64), Image.NEAREST)
            im.paste(ic, (i * W + 4, j * H + 4), ic)
    im.save(out)
    return out


def fp(out, rows=(("delver", "D"), ("breaker", "C"), ("mattock", "B"), ("sounder", "A"))):
    W, H = 640, 360
    im = Image.new("RGB", (W * 2, H * 2))
    for k, (f, r) in enumerate(rows):
        built, look = make(f, r)
        m = AW.as_render(built, look)
        d = hold_of(built.pos / 16.0)["firstperson_righthand"]
        img = MR.render_fp(m, built.texture(), display=(tuple(d["rotation"]), tuple(d["translation"]), d["scale"][0]), size=(W, H), time=0.27, bg=np.array([88, 96, 112]))
        im.paste(img, ((k % 2) * W, (k // 2) * H))
    im.save(out)
    return out


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd in ("build", "check"):
        bad = check()
        for b in bad:
            print("CHECK", b)
        if bad:
            return 1
        if cmd == "build":
            write_all(argv[1:] or None)
            print("written")
        return 0
    if cmd == "sheet":
        print(sheet(argv[1]))
        return 0
    if cmd == "fp":
        print(fp(argv[1]))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
