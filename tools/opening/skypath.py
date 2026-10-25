#!/usr/bin/env python3
"""The opening's sky shot (SPEC §16.4): checks the designed flight against the painted city before a tour.

  python3 -I tools/opening/skypath.py <city-dump.bin> [out.png]

Mirrors dev.awaken.opening.client.SkyPath exactly (keyframes in plan coordinates, centripetal Catmull-Rom through the
eyes, uniform through the look targets, a monotone cubic from time to spline parameter) and reports, for every tick of
the flight, the clearance to the nearest painted block, the height above the ground, the distance from the Roost (the
loaded circle) and the camera's speed. Writes a top view of the city with the path drawn on it when asked.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'city'))

# ---- keep in step with SkyPath.java ----------------------------------------------------------------------------------
ROOST_EYE = (-169.5, 41.62, -129.5)  # the start (plan), feet ry 40 + eye height
KEYS = [
    # t (s), eye (x, ry, z), look at (x, ry, z)
    (0.0, (-268, 168, -246), (0, 50, -20)),
    (3.2, (-218, 140, -206), (-10, 40, -30)),
    (5.6, (-150, 108, -182), (-40, 15, -50)),
    (7.6, (-100, 62, -152), (-72, 10, -60)),
    (9.2, (-86, 36, -100), (-66, 18, -30)),
    (10.6, (-72, 38, -50), (-40, 10, -8)),
    (12.0, (-47, 33, -13), (2, 3, 10)),
    (13.4, (-40, 44, -38), (0, 72, -46)),
    (15.0, (-80, 58, -80), (-160, 46, -125)),
    (16.3, (-132, 52, -108), (-170, 42, -130)),
    (17.3, (-160.3, 46, -130.2), (-169.5, 41.6, -129.5)),
    (18.0, (-167.4, 44, -134.5), (-158, 40, -118)),
    (18.8, None, None),  # behind the player (third-person spot), looking along their view
]
FLIGHT_S = 19.0
CAM_DIST = 4.0
START_YAW = -45.0


def end_points():
    yr = math.radians(START_YAW)
    dx, dz = -math.sin(yr), math.cos(yr)
    ex, ey, ez = ROOST_EYE
    eye = (ex - dx * CAM_DIST, ey + 0.4, ez - dz * CAM_DIST)
    look = (ex + dx * 200, ey - 8, ez + dz * 200)
    return eye, look


def knots():
    pts, looks, ts = [], [], []
    for t, e, l in KEYS:
        if e is None:
            e, l = end_points()
        ts.append(t)
        pts.append(e)
        looks.append(l)
    return ts, pts, looks


def catmull(P, seg, t, centripetal):
    n = len(P)
    p0 = P[seg - 1] if seg > 0 else tuple(2 * a - b for a, b in zip(P[0], P[1]))
    p1, p2 = P[seg], P[seg + 1]
    p3 = P[seg + 2] if seg + 2 < n else tuple(2 * a - b for a, b in zip(P[-1], P[-2]))

    def knot(ti, a, b):
        if not centripetal:
            return ti + 1
        d = math.dist(a, b)
        return ti + max(math.sqrt(d), 1e-3)
    t0 = 0
    t1 = knot(t0, p0, p1)
    t2 = knot(t1, p1, p2)
    t3 = knot(t2, p2, p3)
    tt = t1 + (t2 - t1) * t

    def lerp(a, b, ta, tb):
        return a + (b - a) * (tt - ta) / (tb - ta)
    out = []
    for c in range(3):
        a1 = lerp(p0[c], p1[c], t0, t1)
        a2 = lerp(p1[c], p2[c], t1, t2)
        a3 = lerp(p2[c], p3[c], t2, t3)
        b1 = lerp(a1, a2, t0, t2)
        b2 = lerp(a2, a3, t1, t3)
        out.append(lerp(b1, b2, t1, t2))
    return out


def monotone(ts):
    """Fritsch-Carlson slopes for u(t) through (ts[i], i)."""
    n = len(ts)
    d = [1.0 / (ts[i + 1] - ts[i]) for i in range(n - 1)]
    m = [0.0] * n
    m[0], m[-1] = d[0] * 0.25, 0.0  # ease out of the start a little, settle fully at the end
    for i in range(1, n - 1):
        m[i] = 0.0 if d[i - 1] * d[i] <= 0 else 2.0 / (1.0 / d[i - 1] + 1.0 / d[i])
    return m


def u_of(t, ts, m):
    if t <= ts[0]:
        return 0.0
    if t >= ts[-1]:
        return len(ts) - 1.0
    i = max(k for k in range(len(ts) - 1) if ts[k] <= t)
    h = ts[i + 1] - ts[i]
    s = (t - ts[i]) / h
    h00 = 2 * s ** 3 - 3 * s ** 2 + 1
    h10 = s ** 3 - 2 * s ** 2 + s
    h01 = -2 * s ** 3 + 3 * s ** 2
    h11 = s ** 3 - s ** 2
    return h00 * i + h10 * h * m[i] + h01 * (i + 1) + h11 * h * m[i + 1]


def sample(t):
    ts, pts, looks = knots()
    m = monotone(ts)
    u = u_of(t, ts, m)
    seg = min(int(u), len(pts) - 2)
    lt = u - seg
    return catmull(pts, seg, lt, True), catmull(looks, seg, lt, False)


def main():
    import pickle
    import numpy as np
    import render
    path = sys.argv[1]
    vox, pal, ymin, ox, oz = render.load(path)
    top, side, solid = render.palettes(pal)
    s = solid[vox]
    H = s.shape[0]
    idx = np.where(s.any(axis=0), H - 1 - np.argmax(s[::-1], axis=0), -1)
    worst = (1e9, None)
    prev = None
    rows = []
    for k in range(int(FLIGHT_S * 20) + 1):
        t = k / 20.0
        e, l = sample(t)
        x, y, z = e
        ix, iz = int(math.floor(x)) + ox, int(math.floor(z)) + oz
        ground = -99
        clear = 1e9
        for ddx in range(-3, 4):
            for ddz in range(-3, 4):
                jx, jz = ix + ddx, iz + ddz
                if 0 <= jz < idx.shape[0] and 0 <= jx < idx.shape[1] and idx[jz, jx] >= 0:
                    h = idx[jz, jx] + ymin + 1
                    if ddx == 0 and ddz == 0:
                        ground = h
                    dh = y - h
                    dd = math.hypot(max(0, abs(ddx) - 0.5), max(0, abs(ddz) - 0.5))
                    if dh < 0:
                        clear = min(clear, dd)
                    else:
                        clear = min(clear, math.hypot(dd, dh) if dd > 0 else dh)
        roost = math.hypot(x - ROOST_EYE[0], z - ROOST_EYE[2])
        speed = 0 if prev is None else math.dist(prev, e) * 20
        prev = e
        rows.append((t, x, y, z, clear, y - ground, roost, speed))
        if t < FLIGHT_S - 0.6 and clear < worst[0]:
            worst = (clear, t)
    for r in rows[::10]:
        print('t %5.1f  eye (%7.1f %6.1f %7.1f)  clear %6.1f  above %6.1f  from roost %6.1f  speed %6.1f b/s' % r)
    print('closest approach %.1f blocks at t %.1f; max from the Roost %.0f; top ry %.0f (y %.0f at street 70)' % (
        worst[0], worst[1], max(r[6] for r in rows), max(r[2] for r in rows), 70 + max(r[2] for r in rows)))
    if len(sys.argv) > 2:
        from PIL import Image, ImageDraw
        zz, xx = np.meshgrid(np.arange(idx.shape[0]), np.arange(idx.shape[1]), indexing='ij')
        ids = np.where(idx >= 0, vox[np.clip(idx, 0, H - 1), zz, xx], 0)
        col = top[ids]
        col[idx < 0] = (98, 120, 70)
        img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)).resize((idx.shape[1] * 2, idx.shape[0] * 2), Image.NEAREST)
        d = ImageDraw.Draw(img)
        pts = [((r[1] + ox) * 2, (r[3] + oz) * 2) for r in rows]
        d.line(pts, fill=(255, 40, 40), width=2)
        for r in rows[::20]:
            e, l = sample(r[0])
            d.line([((e[0] + ox) * 2, (e[2] + oz) * 2), ((e[0] + (l[0] - e[0]) * 0.15 + ox) * 2, (e[2] + (l[2] - e[2]) * 0.15 + oz) * 2)], fill=(255, 255, 0))
        cx, cz = (ROOST_EYE[0] + ox) * 2, (ROOST_EYE[2] + oz) * 2
        d.ellipse([cx - 430, cz - 430, cx + 430, cz + 430], outline=(255, 255, 255))
        img.save(sys.argv[2])
        print('wrote', sys.argv[2])


if __name__ == '__main__':
    main()
