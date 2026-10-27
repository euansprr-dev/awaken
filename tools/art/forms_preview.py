"""A flat-shaded 3D look at the forms before the game shows them (SPEC 21.11): vanilla body + the form's overlay + its kit + its glow,
rendered orthographically from the front-left, for every layout and rung. The vanilla textures are read from the reference assets only to
draw this preview (they are never copied into the mod).

    python3 tools/art/forms_preview.py OUT.png [family 0|1|2] [layout ...]      a sheet: a row per layout, a column per rung 0-5
    python3 tools/art/forms_preview.py OUT.png 0 zombie --rung 4 --yaw 200      one layout, one rung, bigger
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "specs"))
import forms as F  # noqa: E402

RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources/assets/awaken"))
VAN = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/entity/"
VANILLA_TEX = {"pig": "pig/pig_temperate.png", "cow": "cow/cow_temperate.png", "sheep": "sheep/sheep.png", "chicken": "chicken/chicken_temperate.png",
               "zombie": "zombie/zombie.png", "skeleton": "skeleton/skeleton.png", "spider": "spider/spider.png", "creeper": "creeper/creeper.png"}
PI2 = math.pi / 2
GLOW = {1: (228, 208, 99), 2: (242, 154, 72), 3: (236, 86, 86), 4: (176, 124, 255), 5: (140, 123, 255)}

# the vanilla bodies: part -> (offset, rotation, [(u, v, (x, y, z, w, h, d), mirror)])
def quad(legs, box, uv=(0, 16)):
    return [(n, o, (0, 0, 0), [(uv[0], uv[1], box, m)]) for n, o, m in legs]

BODY = {
    "pig": [("head", (0, 12, -6), (0, 0, 0), [(0, 0, (-4, -4, -8, 8, 8, 8), False), (16, 16, (-2, 0, -9, 4, 3, 1), False)]),
            ("body", (0, 11, 2), (PI2, 0, 0), [(28, 8, (-5, -10, -7, 10, 16, 8), False)])]
           + quad([("l1", (-3, 18, 7), False), ("l2", (3, 18, 7), True), ("l3", (-3, 18, -5), False), ("l4", (3, 18, -5), True)], (-2, 0, -2, 4, 6, 4)),
    "cow": [("head", (0, 4, -8), (0, 0, 0), [(0, 0, (-4, -4, -6, 8, 8, 6), False), (1, 33, (-3, 1, -7, 6, 3, 1), False), (22, 0, (-5, -5, -5, 1, 3, 1), False), (22, 0, (4, -5, -5, 1, 3, 1), False)]),
            ("body", (0, 5, 2), (PI2, 0, 0), [(18, 4, (-6, -10, -7, 12, 18, 10), False), (52, 0, (-2, 2, -8, 4, 6, 1), False)])]
           + quad([("l1", (-4, 12, 7), False), ("l2", (4, 12, 7), True), ("l3", (-4, 12, -5), False), ("l4", (4, 12, -5), True)], (-2, 0, -2, 4, 12, 4)),
    "sheep": [("head", (0, 6, -8), (0, 0, 0), [(0, 0, (-3, -4, -6, 6, 6, 8), False)]),
              ("body", (0, 5, 2), (PI2, 0, 0), [(28, 8, (-4, -10, -7, 8, 16, 6), False)])]
             + quad([("l1", (-3, 12, 7), False), ("l2", (3, 12, 7), True), ("l3", (-3, 12, -5), False), ("l4", (3, 12, -5), True)], (-2, 0, -2, 4, 12, 4)),
    "chicken": [("head", (0, 15, -4), (0, 0, 0), [(0, 0, (-2, -6, -2, 4, 6, 3), False), (14, 0, (-2, -4, -4, 4, 2, 2), False), (14, 4, (-1, -2, -3, 2, 2, 2), False)]),
                ("body", (0, 16, 0), (PI2, 0, 0), [(0, 9, (-3, -4, -3, 6, 8, 6), False)]),
                ("right_leg", (-2, 19, 1), (0, 0, 0), [(26, 0, (-1, 0, -3, 3, 5, 3), False)]), ("left_leg", (1, 19, 1), (0, 0, 0), [(26, 0, (-1, 0, -3, 3, 5, 3), False)]),
                ("rw", (-4, 13, 0), (0, 0, 0), [(24, 13, (0, 0, -3, 1, 4, 6), False)]), ("lw", (4, 13, 0), (0, 0, 0), [(24, 13, (-1, 0, -3, 1, 4, 6), True)])],
    "zombie": [("head", (0, 0, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 0, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)]),
               ("right_arm", (-5, 2, 0), (-1.45, 0.1, 0), [(40, 16, (-3, -2, -2, 4, 12, 4), False)]), ("left_arm", (5, 2, 0), (-1.45, -0.1, 0), [(40, 16, (-1, -2, -2, 4, 12, 4), True)]),
               ("right_leg", (-1.9, 12, 0), (0, 0, 0), [(0, 16, (-2, 0, -2, 4, 12, 4), False)]), ("left_leg", (1.9, 12, 0), (0, 0, 0), [(0, 16, (-2, 0, -2, 4, 12, 4), True)])],
    "skeleton": [("head", (0, 0, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 0, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)]),
                 ("right_arm", (-5, 2, 0), (-1.2, 0.2, 0), [(40, 16, (-1, -2, -1, 2, 12, 2), False)]), ("left_arm", (5, 2, 0), (-1.5, -0.2, 0), [(40, 16, (-1, -2, -1, 2, 12, 2), True)]),
                 ("right_leg", (-2, 12, 0), (0, 0, 0), [(0, 16, (-1, 0, -1, 2, 12, 2), False)]), ("left_leg", (2, 12, 0), (0, 0, 0), [(0, 16, (-1, 0, -1, 2, 12, 2), True)])],
    "spider": [("head", (0, 15, -3), (0, 0, 0), [(32, 4, (-4, -4, -8, 8, 8, 8), False)]), ("body0", (0, 15, 0), (0, 0, 0), [(0, 0, (-3, -3, -3, 6, 6, 6), False)]),
               ("body1", (0, 15, 9), (0, 0, 0), [(0, 12, (-5, -4, -6, 10, 8, 12), False)])]
              + [(f"leg{i}", (s * 4, 15, z), (0, s * ry, s * rz), [(18, 0, (-15 if s < 0 else -1, -1, -1, 16, 2, 2), s > 0)])
                 for s in (-1, 1) for i, (z, ry, rz) in enumerate(((2, -0.78, 0.78), (1, -0.39, 0.58), (0, 0.39, 0.58), (-1, 0.78, 0.78)))],
    "creeper": [("head", (0, 6, 0), (0, 0, 0), [(0, 0, (-4, -8, -4, 8, 8, 8), False)]), ("body", (0, 6, 0), (0, 0, 0), [(16, 16, (-4, 0, -2, 8, 12, 4), False)])]
              + quad([("l1", (-2, 18, 4), False), ("l2", (2, 18, 4), True), ("l3", (-2, 18, -4), False), ("l4", (2, 18, -4), True)], (-2, 0, -2, 4, 6, 4)),
}
PART_ALIAS = {"pig": {}, "chicken": {}, "spider": {}, "zombie": {}, "skeleton": {}, "creeper": {}, "cow": {}, "sheep": {}}


def R(rx, ry, rz):
    return np.array(F.rot(rx, ry, rz))


def polys(box, mirror, corner):
    """The six quads of a cube (vertices in the game's order, texture rects), as ModelPart.Cube builds them."""
    x, y, z, w, h, d = box
    x0, x1 = (x + w, x) if mirror else (x, x + w)
    y0, y1, z0, z1 = y, y + h, z, z + d
    t = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)]
    l = [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    u, v = corner
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
    v0, v1, v2 = v, v + d, v + d + h
    P = [([l[1], l[0], t[0], t[1]], (u1, v0, u2, v1)), ([t[2], t[3], l[3], l[2]], (u2, v1, u22, v0)), ([t[0], l[0], l[3], t[3]], (u0, v1, u1, v2)),
         ([t[1], t[0], t[3], t[2]], (u1, v1, u2, v2)), ([l[1], t[1], t[2], l[2]], (u2, v1, u3, v2)), ([l[0], l[1], l[2], l[3]], (u3, v1, u4, v2))]
    centre = np.array([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])
    out = []
    for verts, (a0, b0, a1, b1) in P:
        uv = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        if mirror:
            verts = verts[::-1]
            uv = uv[::-1]
        out.append((np.array(verts, float), uv, centre))
    return out


def render(layout, rung, fam, yaw, pitch, S=9, size=(300, 260)):
    W, H = size
    img = np.zeros((H, W, 4), float)
    img[..., :3] = (36, 40, 52)
    img[..., 3] = 255
    depth = np.full((H, W), 1e9)
    tex = Image.open(VAN + VANILLA_TEX[layout]).convert("RGBA")
    base = np.array(tex)
    ov = go = kt = None
    kit = []
    if rung:
        ov = np.array(Image.open(f"{RES}/textures/entity/forms/{layout}/o{rung}_{fam}.png").convert("RGBA"))
        go = np.array(Image.open(f"{RES}/textures/entity/forms/{layout}/g{rung}.png").convert("RGBA"))
        kt = np.array(Image.open(f"{RES}/textures/entity/forms/{layout}/k{rung}_{fam}.png").convert("RGBA"))
        kit = json.load(open(f"{RES}/forms/kit.json"))["layouts"][layout]["ranks"][str(rung)]
    parts = {p[0]: p for p in BODY[layout]}
    view = R(pitch, 0, 0) @ R(0, yaw, 0)
    items = []  # (verts_world, uv, centre_world, sampler)
    for name, off, rt, cubes in BODY[layout]:
        M = R(*rt)
        for (u, v, box, mirror) in cubes:
            for verts, uv, c in polys(box, mirror, (u, v)):
                items.append((verts @ M.T + np.array(off), uv, c @ M.T + np.array(off), "base"))
    for k in kit:
        if k["part"] not in parts:
            continue
        _, off, rt, _ = parts[k["part"]]
        M = R(*rt)
        pose = k["pose"]
        Mc = R(*pose[3:])
        for verts, uv, c in polys(k["box"], False, k["uv"]):
            vw = ((verts @ Mc.T + np.array(pose[:3])) @ M.T) + np.array(off)
            cw = ((c @ Mc.T + np.array(pose[:3])) @ M.T) + np.array(off)
            items.append((vw, uv, cw, "kit"))
    allv = np.concatenate([i[0] for i in items])
    centre = (allv.max(0) + allv.min(0)) / 2
    flat = (allv - centre) @ view.T
    S = min(W * 0.86 / max(1e-6, np.ptp(flat[:, 0])), H * 0.86 / max(1e-6, np.ptp(flat[:, 1])))
    mid = (flat[:, :2].max(0) + flat[:, :2].min(0)) / 2
    light = np.array([-0.4, -0.8, -0.5])
    light /= np.linalg.norm(light)
    gl = np.array(GLOW.get(rung, (255, 255, 255)), float)
    for verts, uv, c, kind in items:
        pv = (verts - centre) @ view.T
        pc = (c - centre) @ view.T
        sx = (pv[:, 0] - mid[0]) * S + W / 2
        sy = (pv[:, 1] - mid[1]) * S + H / 2
        z = pv[:, 2]
        p0, p1, p3 = np.array([sx[0], sy[0]]), np.array([sx[1], sy[1]]), np.array([sx[3], sy[3]])
        e1, e2 = p1 - p0, p3 - p0
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        # outward normal in view space; facing the camera when its z is negative (the camera looks along +z)
        n = np.cross(pv[1] - pv[0], pv[3] - pv[0])
        n /= (np.linalg.norm(n) + 1e-9)
        if np.dot(n, pv[:3].mean(0) - pc) < 0:
            n = -n
        if n[2] > 0.001:
            continue
        nw = np.linalg.solve(view, n)
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(nw, light)))
        x0, x1 = int(max(0, sx.min())), int(min(W - 1, sx.max()))
        y0, y1 = int(max(0, sy.min())), int(min(H - 1, sy.max()))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        dx, dy = gx - p0[0], gy - p0[1]
        a = (dx * e2[1] - dy * e2[0]) / det
        b = (e1[0] * dy - e1[1] * dx) / det
        ok = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        U = uv[0][0] + a * (uv[1][0] - uv[0][0])
        V = uv[0][1] + b * (uv[3][1] - uv[0][1])
        ti = np.clip(U.astype(int), 0, 63)
        tj = np.clip(V.astype(int), 0, base.shape[0] - 1)
        zz = z[0] + a * (z[1] - z[0]) + b * (z[3] - z[0])
        if kind == "base":
            col = base[tj, ti].astype(float)
            if ov is not None:
                o = ov[tj, ti]
                m = o[..., 3] > 0
                col[m] = o[m]
                g = go[tj, ti][..., 3] > 0
            else:
                g = np.zeros_like(ok)
            rgb = col[..., :3] * shade
            rgb[g] = gl
            alpha = col[..., 3] > 0
        else:
            col = kt[tj, ti].astype(float)
            rgb = col[..., :3] * shade
            alpha = col[..., 3] > 0
        sub = depth[y0:y1 + 1, x0:x1 + 1]
        take = ok & alpha & (zz < sub)
        sub[take] = zz[take]
        img[y0:y1 + 1, x0:x1 + 1, :3][take] = rgb[take]
    return Image.fromarray(img.astype(np.uint8)[..., :3])


def main():
    out = sys.argv[1]
    fam = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else 0
    args = [a for a in sys.argv[3:] if not a.startswith("--")]
    opts = {a.split("=")[0]: a.split("=")[1] for a in sys.argv if a.startswith("--") and "=" in a}
    layouts = args or list(BODY)
    yaw = math.radians(float(opts.get("--yaw", -35)))
    pitch = math.radians(float(opts.get("--pitch", 18)))
    rungs = [int(opts["--rung"])] if "--rung" in opts else [0, 1, 2, 3, 4, 5]
    S = int(opts.get("--scale", 9 if len(rungs) > 1 else 16))
    tile = (300, 260) if len(rungs) > 1 else (640, 560)
    sheet = Image.new("RGB", (tile[0] * len(rungs), tile[1] * len(layouts)), (20, 22, 30))
    d = ImageDraw.Draw(sheet)
    for j, lay in enumerate(layouts):
        for i, r in enumerate(rungs):
            sheet.paste(render(lay, r, fam, yaw, pitch, S, tile), (i * tile[0], j * tile[1]))
            d.text((i * tile[0] + 6, j * tile[1] + 4), f"{lay} rung {r} family {fam}", fill=(210, 215, 225))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
