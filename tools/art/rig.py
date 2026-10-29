"""Jointed models without the game: a rig JSON the game reads (dev.awaken.core.client.rig.RigModel) drawn here the way
ModelPart draws it, so a pose or a texture is judged in seconds, not in a tour.

A rig (assets/awaken/rigs/<name>.json):
    {"texture": [64, 64],
     "parts": [{"name": "body", "parent": "", "pivot": [0, 0, 0], "rot": [0, 0, 0],
                "cubes": [{"uv": [16, 16], "from": [-4, 0, -2], "size": [8, 12, 4], "inflate": 0, "mirror": false}]}],
     "poses": {"stand": {"rot": {"head": [10, 0, 0]}, "move": {"body": [0, 1, 0]}, "scale": {}, "hide": []}}}
Model space as ModelPart's: pixels, y down, a part's pivot is its offset from its parent's; rotations are degrees, applied
z, then y, then x (rotationZYX). A pose's rot replaces a part's, move adds to its pivot, scale sets its scale; hide skips it.
A cube's faces and UVs are vanilla's ModelPart.Cube's (box UV, mirror, inflate).

    python3 tools/art/rig.py OUT.png <rig> <texture.png> <pose> [<pose> ...]   four views of each pose, a row each
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
REF = "/Volumes/MEDIA/Awaken/.ref/assets/assets"
BG = np.array([150, 186, 236], dtype=float)

# vanilla's world lights for entities (Lighting.setupLevel), world space, and its mix: 0.4 ambient + 0.6 per light
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
CULL = 1


def load(name):
    p = name if name.endswith(".json") else os.path.join(RES, "assets/awaken/rigs", name + ".json")
    return json.load(open(p))


def rot_zyx(deg):
    """ModelPart's rotation: Quaternionf().rotationZYX(z, y, x), as a 3x3 matrix."""
    x, y, z = (math.radians(a) for a in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def mat(r=None, t=(0, 0, 0), s=(1, 1, 1)):
    m = np.eye(4)
    if r is not None:
        m[:3, :3] = r
    m[:3, :3] = m[:3, :3] @ np.diag(s)
    m[:3, 3] = t
    return m


def cube_polys(c, tw, th):
    """Vanilla's six polygons of a cube: (four corners in part space, pixels; their (u, v) in pixels)."""
    x0, y0, z0 = c["from"]
    w, h, d = c["size"]
    g = c.get("inflate", 0)
    gx, gy, gz = g if isinstance(g, list) else (g, g, g)
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    x0, y0, z0, x1, y1, z1 = x0 - gx, y0 - gy, z0 - gz, x1 + gx, y1 + gy, z1 + gz
    mirror = c.get("mirror", False)
    if mirror:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = c["uv"]
    U0, U1, U2, U22, U3, U4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    V0, V1, V2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], U1, V0, U2, V1), ([t2, t3, l3, l2], U2, V1, U22, V0),
             ([t0, l0, l3, t3], U0, V1, U1, V2), ([t1, t0, t3, t2], U1, V1, U2, V2),
             ([l1, t1, t2, l2], U2, V1, U3, V2), ([l0, l1, l2, l3], U3, V1, U4, V2)]
    out = []
    for vs, a0, b0, a1, b1 in polys:
        uv = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        vs = list(vs)
        if mirror:
            vs.reverse()
            uv.reverse()
        out.append((vs, uv))
    return out


def posed(rig, pose, extra=None):
    """Every part's matrix (part space pixels -> model space pixels) in a pose."""
    p = rig.get("poses", {}).get(pose, {}) if isinstance(pose, str) else (pose or {})
    rots, moves, scales, hide = p.get("rot", {}), p.get("move", {}), p.get("scale", {}), set(p.get("hide", []))
    by = {q["name"]: q for q in rig["parts"]}
    out = {}

    def m_of(name):
        if name in out:
            return out[name]
        q = by[name]
        parent = np.eye(4) if not q.get("parent") else m_of(q["parent"])
        piv = np.array(q.get("pivot", [0, 0, 0]), dtype=float) + np.array(moves.get(name, [0, 0, 0]))
        r = rot_zyx(rots.get(name, q.get("rot", [0, 0, 0])))
        s = scales.get(name, q.get("scale", [1, 1, 1]))
        if not isinstance(s, list):
            s = [s, s, s]
        out[name] = parent @ mat(r, piv, s)
        return out[name]

    for q in rig["parts"]:
        m_of(q["name"])
    hidden = set()
    for q in rig["parts"]:  # a hidden part hides its children
        n = q["name"]
        while n:
            if n in hide:
                hidden.add(q["name"])
                break
            n = by[n].get("parent", "")
    return out, hidden


def quads(rig, pose, tex, world=np.eye(4), groups=None):
    """World-space quads (blocks) of a rig in a pose: (4 points, 4 uvs in 0..1, texture array)."""
    ms, hidden = posed(rig, pose)
    tw, th = rig.get("texture", [64, 64])
    out = []
    for q in rig["parts"]:
        if q["name"] in hidden or groups and q.get("group", "") not in groups:
            continue
        m = world @ ms[q["name"]]
        for c in q.get("cubes", []):
            for vs, uv in cube_polys(c, tw, th):
                pts = [(m @ np.array([*v, 1.0]))[:3] for v in vs]
                out.append((pts, [(a / tw, b / th) for a, b in uv], tex))
    return out


def lowest(rig, pose):
    """The pose's lowest point in model space (largest y, pixels): where the ground must meet it."""
    ms, hidden = posed(rig, pose)
    y = -1e9
    for q in rig["parts"]:
        if q["name"] in hidden or q.get("group", "") == "props":
            continue
        for c in q.get("cubes", []):
            for vs, _ in cube_polys(c, 1, 1):
                for v in vs:
                    y = max(y, (ms[q["name"]] @ np.array([*v, 1.0]))[1])
    return y


def extent(rig, pose):
    """The figure's (not the props') x and z range in model space, pixels: ((xmin, xmax), (zmin, zmax))."""
    ms, hidden = posed(rig, pose)
    xs, zs = [1e9, -1e9], [1e9, -1e9]
    for q in rig["parts"]:
        if q["name"] in hidden or q.get("group", "") == "props":
            continue
        for c in q.get("cubes", []):
            for vs, _ in cube_polys(c, 1, 1):
                for v in vs:
                    w = ms[q["name"]] @ np.array([*v, 1.0])
                    xs = [min(xs[0], w[0]), max(xs[1], w[0])]
                    zs = [min(zs[0], w[2]), max(zs[1], w[2])]
    return xs, zs


def second(sec):
    """The other figure of two, in the first's model space: moved (pixels), turned, and scaled about its feet."""
    a = math.radians(sec.get("yaw", 0.0))
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    k = sec.get("scale", 1.0)
    return mat(None, (sec.get("x", 0.0), 24, sec.get("z", 0.0))) @ mat(ry) @ mat(None, (0, 0, 0), (k, k, k)) @ mat(None, (0, -24, 0))


def block_world(yaw=0.0, x=0.0, z=0.0):
    """The Stilled's transform from model pixels to world blocks (StilledRenderer: translate(.5,0,.5), rotate Y by
    180 - yaw, scale(-1,-1,1), translate(0,-1.501,0), then a pixel is 1/16)."""
    a = math.radians(180 - yaw)
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    m = mat(None, (0.5 + x, 0, 0.5 + z)) @ mat(ry) @ mat(None, (0, 0, 0), (-1, -1, 1)) @ mat(None, (0, -1.501, 0)) @ mat(None, (0, 0, 0), (1 / 16, 1 / 16, 1 / 16))
    return m


def vanilla(path):
    for root in (os.path.join(RES, "assets"), REF):
        p = os.path.join(root, path)
        if os.path.exists(p):
            return np.asarray(Image.open(p).convert("RGBA"), dtype=float)
    raise FileNotFoundError(path)


def ground_quads(r=2, tint=(124, 189, 107)):
    """A grass floor round the block (the grass top, tinted as plains grass is)."""
    g = vanilla("minecraft/textures/block/grass_block_top.png")[:16].copy()
    g[..., :3] *= np.array(tint) / 255.0
    out = []
    for bx in range(-r, r + 1):
        for bz in range(-r, r + 1):
            pts = [np.array([bx, 0, bz]), np.array([bx + 1, 0, bz]), np.array([bx + 1, 0, bz + 1]), np.array([bx, 0, bz + 1])]
            out.append((pts, [(0, 0), (1, 0), (1, 1), (0, 1)], g))
    return out


def render(qs, yaw, pitch, centre=(0.5, 1.0, 0.5), S=150, W=520, H=600, floor=None):
    """Orthographic view from yaw (degrees, 0 = looking north from the south) and pitch (down), entity-lit."""
    img = np.zeros((H, W, 3)) + BG
    depth = np.full((H, W), -1e9)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp  # pitched down: what's nearer sits lower, what's higher is nearer
        return np.array([W / 2 + x * S, H * 0.5 - y * S, z])

    for lit, group in ((False, floor or []), (True, qs)):
        for pts, uv, tx in group:
            P = [proj(p) for p in pts]
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            ln = np.linalg.norm(n)
            if ln < 1e-12:
                continue
            n = n / ln
            # back faces are culled, as the game's entity render types do (the floor is drawn both ways)
            area = (P[1][0] - P[0][0]) * (P[2][1] - P[0][1]) - (P[1][1] - P[0][1]) * (P[2][0] - P[0][0])
            if lit and area * CULL > 0:
                continue
            k = min(1.0, 0.4 + 0.6 * (max(0, n @ L0) + max(0, n @ L1))) if lit else 0.85
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (P[i] for i in tri)
                ta, tb, tc = (uv[i] for i in tri)
                x0, x1 = max(0, int(min(a[0], b[0], c[0]))), min(W - 1, int(max(a[0], b[0], c[0])) + 1)
                y0, y1 = max(0, int(min(a[1], b[1], c[1]))), min(H - 1, int(max(a[1], b[1], c[1])) + 1)
                den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(den) < 1e-9 or x0 > x1 or y0 > y1:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                w0 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
                w1 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not inside.any():
                    continue
                z = w0 * a[2] + w1 * b[2] + w2 * c[2]
                u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0]
                v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1]
                th, tw = tx.shape[0], tx.shape[1]
                ti = np.clip(np.floor(v * th - 1e-4).astype(int), 0, th - 1)
                tj = np.clip(np.floor(u * tw - 1e-4).astype(int), 0, tw - 1)
                col = tx[ti, tj]
                region = depth[y0:y1 + 1, x0:x1 + 1]
                ok = inside & (col[..., 3] > 25) & (z > region)
                if not ok.any():
                    continue
                dst = img[y0:y1 + 1, x0:x1 + 1]
                dst[ok] = (col[..., :3] * k)[ok]
                region[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


VIEWS = [(-35, 12), (35, 12), (90, 10), (180, 12)]


def sheet(rows, out, views=VIEWS, S=150, W=380, H=470):
    """rows: [(label, quads, centre)]; each row the views side by side."""
    from PIL import ImageDraw
    tiles = []
    for label, qs, centre in rows:
        row = [render(qs, y, p, centre, S, W, H, floor=ground_quads()) for y, p in views]
        im = Image.new("RGB", (W * len(views), H), (0, 0, 0))
        for i, t in enumerate(row):
            im.paste(t, (i * W, 0))
        ImageDraw.Draw(im).text((8, 6), label, fill=(20, 20, 30))
        tiles.append(im)
    S_ = Image.new("RGB", (tiles[0].width, sum(t.height for t in tiles)))
    y = 0
    for t in tiles:
        S_.paste(t, (0, y))
        y += t.height
    S_.save(out)
    return out


if __name__ == "__main__":
    out, name, texp, *poses = sys.argv[1:]
    rig = load(name)
    tex = np.asarray(Image.open(texp).convert("RGBA"), dtype=float)
    rows = [(p, quads(rig, p, tex, block_world()), (0.5, 0.9, 0.5)) for p in poses]
    print(sheet(rows, out))
