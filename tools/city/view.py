#!/usr/bin/env python3
"""A quick perspective preview of a CityViews camera from a painted city dump (block colours, flat shading by face,
no textures or light): checks a tour's framings before the tour.

  python3 -I tools/city/view.py <city-dump.bin> <out.png> <x> <ry> <z> <tx> <tr> <tz> [width] [fov]

Coordinates are plan coordinates as in CityViews (eye, then the point it looks at); fov is vertical, default 70 (the
tours' camera).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render  # noqa: E402


def view(dump, out, eye, target, width=720, fov=70.0, far=220):
    vox, pal, ymin, ox, oz = render.load(dump)
    top, side, solid = render.palettes(pal)
    H, D, W = vox.shape
    height = width * 900 // 1440
    e = np.array(eye, float)
    f = np.array(target, float) - e
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    t = math.tan(math.radians(fov) / 2)
    ys, xs = np.mgrid[0:height, 0:width]
    px = (xs + 0.5) / width * 2 - 1
    py = 1 - (ys + 0.5) / height * 2
    d = f[None, None] + px[..., None] * t * width / height * r[None, None] + py[..., None] * t * u[None, None]
    d = d.reshape(-1, 3)
    d /= np.linalg.norm(d, axis=1)[:, None]
    n = d.shape[0]
    # voxel grid coords: x -> ix = x + ox, y -> iy = y - ymin, z -> iz = z + oz (a voxel spans [i, i+1))
    pos = np.tile(np.array([e[0] + ox, e[1] - ymin, e[2] + oz]), (n, 1))
    cell = np.floor(pos).astype(int)
    step = np.sign(d).astype(int)
    step[step == 0] = 1
    inv = 1.0 / np.where(np.abs(d) < 1e-9, 1e-9, d)
    nxt = np.where(step > 0, cell + 1 - pos, pos - cell)
    tmax = np.abs(nxt * inv)
    tdelta = np.abs(inv)
    hit = np.zeros(n, int)
    face = np.zeros(n, int)
    dist = np.zeros(n)
    alive = np.ones(n, bool)
    for _ in range(int(far * 1.8)):
        idx = np.nonzero(alive)[0]
        if len(idx) == 0:
            break
        tm = tmax[idx]
        ax = np.argmin(tm, axis=1)
        dist[idx] = tm[np.arange(len(idx)), ax]
        cell[idx, ax] += step[idx, ax]
        tmax[idx, ax] += tdelta[idx, ax]
        c = cell[idx]
        inside = (c[:, 0] >= 0) & (c[:, 0] < W) & (c[:, 1] >= 0) & (c[:, 1] < H) & (c[:, 2] >= 0) & (c[:, 2] < D)
        sid = np.zeros(len(idx), int)
        sid[inside] = vox[c[inside, 1], c[inside, 2], c[inside, 0]]
        got = solid[sid] & inside
        gi = idx[got]
        hit[gi] = sid[got]
        face[gi] = ax[got]
        alive[gi] = False
        alive[idx[dist[idx] > far]] = False
        below = c[:, 1] < 0
        alive[idx[below]] = False
    col = np.where((face == 1)[:, None], top[hit], side[hit] * np.where(face == 0, 0.8, 0.65)[:, None])
    fog = np.clip(dist / far, 0, 1)[:, None] ** 1.5
    sky = np.array([170, 196, 222], float)
    col = col * (1 - 0.6 * fog) + sky * 0.6 * fog
    col[hit == 0] = sky
    img = col.reshape(height, width, 3)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(out)
    print('wrote', out)


if __name__ == '__main__':
    a = sys.argv
    eye = tuple(float(v) for v in a[3:6])
    tgt = tuple(float(v) for v in a[6:9])
    view(a[1], a[2], eye, tgt, int(a[9]) if len(a) > 9 else 720, float(a[10]) if len(a) > 10 else 70.0)
