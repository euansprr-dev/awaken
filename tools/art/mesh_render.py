"""Renders an Arms weapon mesh the way the game's weapon shader does (assets/awaken/shaders/core/arms_weapon.fsh): the
same texture halves, the same lighting terms, the same constants. Keep the two in step: shade() below is that shader
line for line, so a look judged here is the look in the game (the game adds its own world light and fog).

    python3 tools/art/mesh_render.py OUT.png <id> [yaw pitch]    one weapon from the built resources
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mesh as MS  # noqa: E402

RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
BG = np.array([24, 26, 34], dtype=float)

# vanilla's two item lights (Lighting.DIFFUSE_LIGHT_0 / _1), world space
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
KEY = np.array([-0.45, 0.7, 0.55]) / np.linalg.norm([-0.45, 0.7, 0.55])  # the studio key, view space (upper left front)
SKY = np.array([0.78, 0.86, 1.0])
GROUND = np.array([0.40, 0.36, 0.34])
STRIP = np.array([1.0, 0.97, 0.9])


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def env(R):
    """The studio a metal reflects: a cool sky over a warm dark floor, a soft-box strip above."""
    base = GROUND[None] + (SKY - GROUND)[None] * smoothstep(-0.3, 0.5, R[:, 1:2])
    strip = np.exp(-((R[:, 1:2] - 0.62) * 5.0) ** 2) * smoothstep(-0.6, 0.2, -R[:, 0:1] + 0.1)
    return base * 0.8 + STRIP[None] * strip * 0.8


def shade(alb, mat, n_world, n_view, along, accent, light=1.0, time=0.0, energy=0.0, flash=0.0, foil=False):
    """The weapon shader. alb, mat: (P,4) 0..1; normals (P,3); along (P,); accent (3,) 0..1; time in GameTime units."""
    metal, gloss, emit, flow = mat[:, 0:1], mat[:, 1:2], mat[:, 2:3], mat[:, 3:4]
    d = np.maximum(n_world @ L0, 0) + np.maximum(n_world @ L1, 0)
    diffuse = np.minimum(1.0, d * 0.6 + 0.4)[:, None]
    diffuse = np.floor(diffuse * 5.0 + 0.5) / 5.0 * 0.6 + diffuse * 0.4  # partly banded: painted light, not plastic
    V = np.array([0.0, 0.0, 1.0])
    R = 2 * (n_view @ V)[:, None] * n_view - V[None]
    e = env(R)
    rgb = alb[:, :3]
    dielectric = rgb * diffuse
    metallic = rgb * (0.28 + 0.9 * e) * (0.75 + 0.25 * diffuse)
    col = dielectric * (1 - metal) + metallic * metal
    spec = np.maximum(R @ KEY, 0)[:, None] ** (6.0 + 84.0 * gloss) * gloss
    col = col + spec * (np.ones(3) * (1 - metal) + np.minimum(rgb * 1.7, 1.0) * metal) * 0.85
    rim = (1 - np.clip(n_view @ V, 0, 1))[:, None] ** 3
    col = col + accent[None] * rim * (0.12 + 0.55 * energy * rim) * 0.6
    col = col * light
    cycle = (time * 240.0) % 1.0
    band = np.exp(-(((along - (cycle * 1.8 - 0.4)) * 7.0) ** 2))[:, None]
    col = col + band * (0.12 + 0.55 * metal) * gloss * (0.6 + flash)
    pulse = np.where(flow > 0, 0.55 + 0.45 * np.sin(time * 240.0 * 2 * math.pi * 0.6 - flow * 12.0), 1.0)
    col = col + rgb * emit * pulse * (1.0 + 1.5 * energy) * 1.6
    col = col + accent[None] * flash * (0.18 + 0.6 * emit + 0.35 * rim)
    if foil:
        g = np.sin((along * 9.0 + n_view[:, 0] * 2.0) - time * 240.0 * 2 * math.pi * 0.35) * 0.5 + 0.5
        col = col + np.array([0.55, 0.32, 1.0])[None] * (g ** 6)[:, None] * 0.45
    return np.clip(col, 0, 1)


ICON_LIGHT = np.array([-0.5, 0.6, 0.62]) / np.linalg.norm([-0.5, 0.6, 0.62])


def shade_icon(alb, mat, n_world, n_view, along, accent, **kw):
    """The inventory's light: a sprite, not a photograph. The albedo lit flat from the upper left front, what glows
    brighter; no reflections (they would only be noise at that size)."""
    rgb = alb[:, :3]
    lit = 0.58 + 0.62 * np.maximum(n_view @ ICON_LIGHT, 0)[:, None] ** 1.5
    lit = lit * (1 + 0.45 * mat[:, 0:1])  # metal reads bright in a sprite, as if it caught the light
    return np.clip(rgb * lit + rgb * mat[:, 2:3] * 0.6, 0, 1)


def rot(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return rx @ ry


def render(m, tex, yaw=30, pitch=15, scale=24.0, size=(512, 512), centre=(0.5, 0.5, 0.5), bg=BG, shader=shade, **kw):
    """m: read() dict (item space 0..1); tex: (H, 2W, 4) uint8. Orthographic; scale: pixels per item unit/16."""
    W, H = size
    view = rot(yaw, pitch)
    c = np.asarray(centre)
    pv = (view @ (m["pos"] - c).T).T * 16.0
    screen = {"sx": W / 2 + pv[:, 0] * scale, "sy": H / 2 - pv[:, 1] * scale, "sz": pv[:, 2], "nv": (view @ m["nrm"].T).T}
    return _raster(m, tex, screen, size, bg, shader=shader, **kw)


def _raster(m, tex, screen, size, bg, shader=shade, **kw):
    W, H = size
    sx, sy, sz, nv = screen["sx"], screen["sy"], screen["sz"], screen["nv"]
    th, tw2 = tex.shape[:2]
    tw = tw2 // 2
    texf = tex.astype(float) / 255.0
    accent = np.asarray(m["accent"], dtype=float) / 255.0
    img = np.tile(bg / 255.0, (H, W, 1))
    depth = np.full((H, W), -1e9)
    for t in range(0, len(sx), 3):
        xs, ys, zs = sx[t:t + 3], sy[t:t + 3], sz[t:t + 3]
        # facing the viewer (counter-clockwise on screen with y down)
        area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
        if area >= -1e-9:
            continue
        x0, x1 = max(0, int(xs.min())), min(W - 1, int(math.ceil(xs.max())))
        y0, y1 = max(0, int(ys.min())), min(H - 1, int(math.ceil(ys.max())))
        if x1 < x0 or y1 < y0:
            continue
        gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
        w0 = ((xs[1] - gx) * (ys[2] - gy) - (xs[2] - gx) * (ys[1] - gy)) / area
        w1 = ((xs[2] - gx) * (ys[0] - gy) - (xs[0] - gx) * (ys[2] - gy)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
        reg = depth[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (z > reg)
        if not ok.any():
            continue
        wts = np.stack([w0[ok], w1[ok], w2[ok]], axis=1)
        uv = wts @ m["uv"][t:t + 3]
        tu = np.clip((uv[:, 0] * tw2).astype(int), 0, tw - 1)
        tv = np.clip((uv[:, 1] * th).astype(int), 0, th - 1)
        alb, mat = texf[tv, tu], texf[tv, tu + tw]
        keep = alb[:, 3] >= 0.1
        if not keep.any():
            continue
        nw = wts @ m["nrm"][t:t + 3]
        nw /= np.maximum(np.linalg.norm(nw, axis=1, keepdims=True), 1e-9)
        nvv = wts @ nv[t:t + 3]
        nvv /= np.maximum(np.linalg.norm(nvv, axis=1, keepdims=True), 1e-9)
        al = wts @ m["along"][t:t + 3]
        col = shader(alb[keep], mat[keep], nw[keep], nvv[keep], al[keep], accent, **kw)
        ys_, xs_ = np.nonzero(ok)
        ys_, xs_ = ys_[keep], xs_[keep]
        reg[ys_, xs_] = z[ok][keep]
        img[y0:y1 + 1, x0:x1 + 1][ys_, xs_] = col
    return Image.fromarray((img * 255).astype(np.uint8), "RGB")


def euler(rx, ry, rz):
    """Vanilla's ItemTransform rotation (rotationXYZ, degrees)."""
    x, y, z = (math.radians(v) for v in (rx, ry, rz))
    RX = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    RY = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    RZ = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return RX @ RY @ RZ


HANDHELD_FP = ((0, -90, 25), (1.13, 3.2, 1.13), 0.68)  # vanilla item/handheld's firstperson_righthand


def held(wid):
    """A weapon's own first-person hold, from its base model (models/item/<id>_in_hand.json), else vanilla's."""
    path = os.path.join(RES, "assets/awaken/models/item", wid + "_in_hand.json")
    d = json.load(open(path)).get("display", {}).get("firstperson_righthand") if os.path.exists(path) else None
    return HANDHELD_FP if d is None else (tuple(d["rotation"]), tuple(d["translation"]), d["scale"][0])


def render_fp(m, tex, display=HANDHELD_FP, hand=(0.56, -0.52, -0.72), roll=0.0, size=(1280, 720), fov=70.0, bg=BG, **kw):
    """The weapon as the right hand holds it in first person, at rest: vanilla's arm offset (ArmsHands' rest), the
    item's display transform, the hand's 70-degree perspective. roll turns the hand in the screen's plane (a pose)."""
    (rx, ry, rz), t, sc = display
    R = euler(rx, ry, rz)
    P = (m["pos"] - 0.5) * sc
    P = P @ R.T + np.asarray(t) / 16.0
    ca, sa = math.cos(math.radians(roll)), math.sin(math.radians(roll))
    Rr = np.array([[ca, -sa, 0], [sa, ca, 0], [0, 0, 1]])
    P = P @ Rr.T + np.asarray(hand)
    N = m["nrm"] @ (Rr @ R).T
    W, H = size
    f = 1.0 / math.tan(math.radians(fov) / 2)
    sx = W / 2 + (P[:, 0] / -P[:, 2]) * f * (H / 2)
    sy = H / 2 - (P[:, 1] / -P[:, 2]) * f * (H / 2)
    screen = {"sx": sx, "sy": sy, "sz": P[:, 2], "nv": N}
    return _raster(m, tex, screen, size, bg, **kw)


def load(wid):
    m = MS.read(os.path.join(RES, "assets/awaken/arms/weapons", wid + ".mesh"))
    tex = np.asarray(Image.open(os.path.join(RES, "assets/awaken/textures/item/arms", wid + ".png")).convert("RGBA"))
    return m, tex


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    yaw = float(sys.argv[3]) if len(sys.argv) > 3 else 30
    pitch = float(sys.argv[4]) if len(sys.argv) > 4 else 15
    m, tex = load(sys.argv[2])
    render(m, tex, yaw, pitch).save(sys.argv[1])
    print(sys.argv[1])
