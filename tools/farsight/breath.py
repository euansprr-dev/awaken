#!/usr/bin/env python3
"""Shimmer on a far steep face under Camera Overhaul's idle sway, measured offline (SPEC §20, "Look"): a replica of Far
Sight's column walk (farsight_terrain.fsh columns()), its light (farLight at noon) and Pixels' steady pass
(pixels_steady.fsh), 132 frames at 60 fps under a sway of a few tenths of a degree, scored as shimmer.py scores a
burst (the last 13 frames, over the face). "old" shows the face the walk meets; "new" the staircase's average where
a top or riser is under a few pixels, as the shader now does. Writes the last steady frame (twice its size) to the
out dir, the current folder by default.

    python3 tools/farsight/breath.py [old|new] [slope] [distance] [camera height] [out dir]
"""
import sys
import numpy as np
from PIL import Image, ImageFilter

MODE = sys.argv[1] if len(sys.argv) > 1 else "old"
SLOPE = float(sys.argv[2]) if len(sys.argv) > 2 else 2.0
DIST = float(sys.argv[3]) if len(sys.argv) > 3 else 900.0
CAMY = float(sys.argv[4]) if len(sys.argv) > 4 else 80.0
W, H = 360, 260                        # a crop of the screen around the face
SCREEN_H = 1898
FOV = np.radians(70.0)                 # fov option 0.5
PIX = FOV / SCREEN_H                   # radians a pixel spans
FPS = 60.0


def hash2(ix, iz):
    h = (ix.astype(np.int64) * 374761393 + iz.astype(np.int64) * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def basis(yaw, pitch, roll):
    # camera looks along +x
    f = np.array([np.cos(pitch) * np.cos(yaw), np.sin(pitch), np.cos(pitch) * np.sin(yaw)])
    r = np.cross(f, [0.0, 1.0, 0.0]); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    c, s = np.cos(roll), np.sin(roll)
    return f, r * c + u * s, u * c - r * s


# the face: ground rising along +x from x = DIST with the slope, flat before it
G = np.array([SLOPE, 0.0])
N = np.array([-SLOPE, 1.0, 0.0]); N /= np.linalg.norm(N)
AIM_PITCH = np.arctan2(SLOPE * 40.0 - CAMY, DIST + 40.0)  # the crop centred on the face 40 blocks up its run


def rays(yaw, pitch, roll):
    f, r, u = basis(yaw, pitch, roll)
    px = (np.arange(W) - W / 2 + 0.5) * PIX
    py = (H / 2 - np.arange(H) - 0.5) * PIX
    d = f[None, None] + np.tan(px)[None, :, None] * r[None, None] + np.tan(py)[:, None, None] * u[None, None]
    return d / np.linalg.norm(d, axis=-1, keepdims=True)


def plane_hit(d):
    # the far mesh: y + CAMY = SLOPE * (x - DIST) (camera at the origin, CAMY above the face's foot)
    t = (-CAMY - (-SLOPE * DIST)) / (SLOPE * d[..., 0] - d[..., 1])
    t = np.where(t > 0, t, np.nan)
    return d * t[..., None]


def fwidth(a):
    dx = np.abs(np.diff(a, axis=1, append=a[:, -1:]))
    dy = np.abs(np.diff(a, axis=0, append=a[-1:, :]))
    return dx + dy


def farlight(n):
    return (n[..., 1] + np.abs(n[..., 0]) * 0.6 + np.abs(n[..., 2]) * 0.8) / np.maximum(n[..., 1] + np.abs(n[..., 0]) + np.abs(n[..., 2]), 1e-3)


def columns(rel, w):
    """The shader's columns(): returns ok, t, normal, below, cell."""
    camY = CAMY
    tp = np.linalg.norm(rel, axis=-1)
    d = rel / tp[..., None]
    g = np.broadcast_to(G, rel.shape[:-1] + (2,))
    approach = d[..., 1] - (g * d[..., [0, 2]]).sum(-1)
    slope = np.linalg.norm(g, axis=-1)
    above = 0.6 + w * (0.5 + 0.75 * slope)
    t = np.maximum(tp + above / approach, tp * 0.5)
    tEnd = tp + (tp - t) * 1.5 + w * 4.0
    p = d[..., [0, 2]] * t[..., None]
    cell = np.floor(p / w[..., None])
    stepDir = np.sign(d[..., [0, 2]])
    inv = 1.0 / np.maximum(np.abs(d[..., [0, 2]]), 1e-6)
    nxt = (cell + np.maximum(stepDir, 0.0)) * w[..., None]
    tMax = t[..., None] + (nxt - p) * stepDir * inv
    tDelta = w[..., None] * inv
    axis = np.full(t.shape, -1)
    done = approach > -0.015
    ok = np.zeros(t.shape, bool); T = np.zeros(t.shape); nrm = np.zeros(rel.shape); below = np.zeros(t.shape)
    cellOut = np.zeros(cell.shape)
    for _ in range(28):
        tOut = tMax.min(-1)
        centre = (cell + 0.5) * w[..., None]
        top = np.floor(rel[..., 1] + (g * (centre - rel[..., [0, 2]])).sum(-1) + camY + 0.5) - camY
        yIn = d[..., 1] * t
        side = (~done) & (yIn <= top) & (axis >= 0)
        if side.any():
            ok |= side; T = np.where(side, t, T)
            n = np.zeros(rel.shape)
            n[..., 0] = np.where(axis == 0, -stepDir[..., 0], 0.0)
            n[..., 2] = np.where(axis == 1, -stepDir[..., 1], 0.0)
            nrm = np.where(side[..., None], n, nrm); below = np.where(side, top - yIn, below)
            cellOut = np.where(side[..., None], cell, cellOut); done |= side
        topHit = (~done) & (d[..., 1] < 0) & (d[..., 1] * tOut <= top)
        if topHit.any():
            ok |= topHit; T = np.where(topHit, np.maximum(t, top / d[..., 1]), T)
            nrm = np.where(topHit[..., None], np.array([0.0, 1.0, 0.0]), nrm); below = np.where(topHit, 0.0, below)
            cellOut = np.where(topHit[..., None], cell, cellOut); done |= topHit
        done |= tOut > tEnd
        ax = np.where(tMax[..., 0] < tMax[..., 1], 0, 1)
        t = np.where(done, t, tOut)
        sel = np.stack([ax == 0, ax == 1], -1) & ~done[..., None]
        cell = np.where(sel, cell + stepDir, cell)
        tMax = np.where(sel, tMax + tDelta, tMax)
        axis = np.where(done, axis, ax)
    return ok, T, nrm, below, cellOut, d


def shade(d, d_dx_len):
    rel = plane_hit(d)
    land = ~np.isnan(rel[..., 0])
    rel = np.where(land[..., None], rel, np.array([DIST + 200.0, SLOPE * 200.0 - CAMY, 0.0]))
    footprint = np.maximum(fwidth(rel[..., 0]), fwidth(rel[..., 2]))
    lf = np.clip(np.log2(np.maximum(footprint * 3.0, 1.0)), 0, 6); base = np.floor(lf)
    cw = np.exp2(base + 1.0)
    pick = hash2(np.floor(rel[..., 0] / cw) * 3 + 31, np.floor(rel[..., 2] / cw) * 3 + 77)
    level = np.minimum(base + (pick < lf - base), 6.0)
    w = np.exp2(np.floor(level))
    ok, t, hn, below, cell, dn = columns(rel, w)
    sideN = np.array([-1.0, 0.0, 0.0])         # the slope's horizontal normal (it faces -x, toward the camera)
    faceN = np.where((ok & (hn[..., 1] < 0.5))[..., None], sideN, np.where(ok[..., None], hn, N))
    grain = hash2(cell[..., 0] * 7 + 3, cell[..., 1] * 7 + 5)
    rock = 0.5
    c = rock * (0.9 + 0.2 * grain)
    lit = farlight(faceN)
    out = c * lit
    if MODE == "new":
        # the pixel's average over the staircase where its top or riser is under a few pixels across the ray
        tp = np.linalg.norm(rel, axis=-1)
        across = tp * d_dx_len
        dy = dn[..., 1]
        drop = np.maximum(-dy, 0.0); rise = np.maximum(G[0] * dn[..., 0], 0.0) + np.maximum(G[1] * dn[..., 2], 0.0)
        x = np.clip((w * np.minimum(drop, rise) / across - 1.0) / 2.0, 0, 1)
        fine = 1.0 - x * x * (3 - 2 * x)
        share = drop / np.maximum(drop + rise, 1e-6)
        avg = c * (share * farlight(np.broadcast_to(np.array([0.0, 1.0, 0.0]), faceN.shape)) + (1 - share) * farlight(np.broadcast_to(sideN, faceN.shape)))
        out = np.where(ok, out + (avg - out) * fine, out)
    out = np.where(land, out, 0.8)            # the sky
    return out * 255.0, land


def catmull(img, x, y):
    # 4x4 Catmull-Rom, clamped
    h, w = img.shape
    x0 = np.floor(x - 0.5); y0 = np.floor(y - 0.5)
    fx = x - 0.5 - x0; fy = y - 0.5 - y0

    def wts(f):
        return [f * (-0.5 + f * (1.0 - 0.5 * f)), 1.0 + f * f * (-2.5 + 1.5 * f), f * (0.5 + f * (2.0 - 1.5 * f)), f * f * (-0.5 + 0.5 * f)]
    wx, wy = wts(fx), wts(fy)
    out = np.zeros_like(x)
    for j in range(4):
        yy = np.clip(y0 - 1 + j, 0, h - 1).astype(int)
        for i in range(4):
            xx = np.clip(x0 - 1 + i, 0, w - 1).astype(int)
            out += img[yy, xx] * wx[i] * wy[j]
    return np.maximum(out, 0)


def main():
    frames, steady = [], []
    hist = None; prev = None
    n = 132
    for k in range(n):
        tt = k / FPS
        # Camera Overhaul's idle sway (0.6 intensity at 0.16 Hz): a smooth wander in pitch, yaw and roll, a few tenths of a degree
        yaw = np.radians(0.5 * np.sin(2 * np.pi * 0.16 * tt) + 0.3 * np.sin(2 * np.pi * 0.27 * tt + 1.3))
        pitch = AIM_PITCH + np.radians(0.4 * np.sin(2 * np.pi * 0.13 * tt + 0.4) + 0.2 * np.sin(2 * np.pi * 0.31 * tt))
        roll = np.radians(0.3 * np.sin(2 * np.pi * 0.11 * tt + 2.0))
        d = rays(yaw, pitch, roll)
        dlen = np.maximum(np.linalg.norm(np.diff(d, axis=1, append=d[:, -1:]), axis=-1), np.linalg.norm(np.diff(d, axis=0, append=d[-1:]), axis=-1))
        dlen[:, -1] = dlen[:, -2]; dlen[-1] = dlen[-2]
        img, land = shade(d, dlen)
        frames.append(img)
        if hist is None:
            s = img
        else:
            # reproject: this frame's ray in last frame's camera
            f, r, u = prev
            fz = (d * f).sum(-1); ax = np.arctan((d * r).sum(-1) / fz); ay = np.arctan((d * u).sum(-1) / fz)
            x = ax / PIX + W / 2; y = H / 2 - ay / PIX
            heldm = (x >= 0) & (x <= W) & (y >= 0) & (y <= H)
            hval = catmull(hist, x, y)
            pad = np.pad(img, 1, mode="edge")
            nb = np.stack([pad[1:-1, 1:-1], pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]])
            hval = np.clip(hval, nb.min(0), nb.max(0))
            s = np.where(heldm, hval + (img - hval) * 0.1, img)
            s = np.round(s / 255 * 1023) / 1023 * 255   # RGB10A2
        hist = s
        prev = basis(yaw, pitch, roll)
        steady.append(s)

    def score(seq):
        seq = [np.clip(a, 0, 255) for a in seq[-13:]]
        blur = [np.asarray(Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)), dtype=np.float32) for a in seq]
        L = [np.round(a) for a in seq]
        fine, hard = [], []
        for k in range(1, len(L)):
            f = np.clip(np.abs(L[k] - L[k - 1]) - np.abs(blur[k] - blur[k - 1]), 0, None)[land]
            fine.append(f.mean()); hard.append((f > 24).mean())
        return np.mean(fine), np.mean(hard) * 100

    a, b = score(frames), score(steady)
    print(f"{MODE} slope {SLOPE} at {DIST:.0f} m, {CAMY:.0f} up: raw fine {a[0]:5.2f} hard {a[1]:6.3f}%   steady fine {b[0]:5.2f} hard {b[1]:6.3f}%   mean {np.mean(steady[-1][land]):.1f}")
    out = sys.argv[5] if len(sys.argv) > 5 else "."
    Image.fromarray(np.clip(steady[-1], 0, 255).astype(np.uint8)).resize((W * 2, H * 2), Image.NEAREST).save(f"{out}/breath_{MODE}_{SLOPE}_{DIST:.0f}.png")


if __name__ == "__main__":
    main()
