#!/usr/bin/env python3
"""Your own wings in first person, drawn offline (SPEC §62 "First person").

A port of WingForm's poses and WingFeathers' first-person boxes (every primary and the outer coverts; --old also the
secondaries 4-7), placed as WingLayer.placeFirst places them (tools/ascent/fpwings_place.py), projected through a camera at a field of view and aspect, and
drawn flat: each box's outline filled by depth, far first. It answers one question before a tour: where on the frame
do the feathers land, in a glide, through a beat, in a dive and a hover, at a normal and a wide field of view.

  python3 tools/ascent/fpwings.py [out.png] [--old] [--fov 70,110] [--aspect 1.7778]

--old draws the placement before 12 Nov (the body's own frame pushed 0.8 ahead of the eye and 0.12 under it).
Prints, per frame, the share of the frame the feathers cover and the innermost feather's distance from the middle
(0 the middle, 1 the side edge), and writes a contact sheet.
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

D = math.pi / 180

# ---- WingForm -------------------------------------------------------------------------------------------------------
A0, A1, A2, P0, P1, P2, FAN_IN, FAN_OUT, SEC, TER, CASC, BOB, N = range(13)
FOLD = [92, 38, -101, 16, 10, 8, -92, -89, -90, -92, 0, 0]
OPEN = [66, 16, -18, 8, 0, 0, -72, -8, -84, -97, 1, 0]
GLIDE = [50, 6, -15, 6, 5, 7, -70, -13, -86, -95, 1, 0]
DIVE = [26, -128, -64, 22, 2, 4, -84, -70, -100, -98, 1, 0]
HOVER = [a + (b - a) * 0.3 for a, b in zip(OPEN, GLIDE)]
FLARE = [64, 18, -6, -34, -12, -10, -78, 2, -84, -96, 1, 0]
FLY_PERIOD, HOVER_PERIOD, DOWN = 0.95, 1.6, 0.42
BONE = [9, 13, 14]
SECS = [15, 16, 17, 18, 19, 19, 20, 20]
PRIM = [21, 24, 26, 28, 28, 27, 25, 22, 18]
COV, COV2 = 13, 11
PAIRS = {"wings": [[0.94, 0, 0]], "skyborn": [[1.18, -6, 0], [0.6, 44, 0.12]],
         "sovereign": [[0.82, -50, 0], [1.26, -2, 0.1], [0.86, 40, 0.2]]}


def clamp(x, a, b):
    return a if x < a else b if x > b else x


def ease(x):
    x = clamp(x, 0, 1)
    return x * x * (3 - 2 * x)


def lerp(t, a, b):
    return a + (b - a) * t


def jround(x):
    return math.floor(x + 0.5)


def beat(base, t, period, amp, lift):
    u = (t % period) / period
    th = math.pi * u / DOWN if u < DOWN else math.pi * (1 + (u - DOWN) / (1 - DOWN))
    sn = math.sin(th)
    up = sn * sn if th > math.pi else 0
    dn = 0 if th > math.pi else sn * sn
    o = list(base)
    o[P0] = base[P0] + amp * 9 * math.cos(th)
    o[P1] = base[P1] + amp * 25 * math.cos(th - 0.55)
    o[P2] = base[P2] + amp * 15 * math.cos(th - 1.15)
    o[A0] = base[A0] + lift * 9 * math.cos(th)
    o[A1] = base[A1] + lift * 7 * math.cos(th - 0.5) - amp * 8 * up
    o[A2] = base[A2] - amp * 18 * up
    o[FAN_OUT] = base[FAN_OUT] - amp * 9 * up + amp * 5 * dn
    o[FAN_IN] = base[FAN_IN] - amp * 3 * up
    o[BOB] = -1.3 * amp * math.cos(th - 0.7)
    return o


def flapk(p0, p1, p2, sp):
    return p0 + p1 * ease((sp - 0.7) / 0.7) + p2 * ease((sp - 1.7) / 0.7)


def covert(row, i):
    return (6 if i < 4 else 8 if i < 9 else 9) if row == 0 else (4 if i < 4 else 5)


# ---- WingFeathers (first person): boxes in model space (blocks; x the player's left, y down from the neck, z behind) --
# first person since 12 Nov: only what hangs from the outer bone (the primaries and their coverts); OLD_FIRST draws the
# secondaries 4-7 as well, as before
OLD_FIRST = False


def wing_boxes(p, pair, index, s):
    size, roll = pair[0], pair[1]
    rc, rs = math.cos(-s * roll * D), math.sin(-s * roll * D)
    rx0, ry0, rz0 = s * 3.2, 3.0, 4.0 + index * 0.9
    st = {}

    def dir_(an, ph):
        c, sn = math.cos(-s * ph), math.sin(-s * ph)
        x, y = s * math.cos(an), math.sin(an)
        f = (x * c, y, -x * sn)
        n = (sn, 0.0, c)
        w = (f[1] * n[2] - f[2] * n[1], f[2] * n[0] - f[0] * n[2], f[0] * n[1] - f[1] * n[0])
        st["f"], st["n"], st["w"] = f, n, w

    J = [(0.0, 0.0, 0.0)]
    p0, p1, p2, c = p[P0], p[P1], p[P2], p[CASC]
    for i in range(3):
        dir_(p[A0 + i] * D, (p0 if i == 0 else p0 + p1 if i == 1 else p0 + p1 + p2) * D)
        f = st["f"]
        J.append(tuple(J[i][k] + f[k] * BONE[i] for k in range(3)))
    out = []

    def model(o, w, fl, n):
        f, nn, ww = st["f"], st["n"], st["w"]
        x = o[0] + ww[0] * w + f[0] * fl + nn[0] * n
        y = o[1] + ww[1] * w + f[1] * fl + nn[1] * n
        z = o[2] + ww[2] * w + f[2] * fl + nn[2] * n
        rx, ry, rz = (x * rc - y * rs) * size, (x * rs + y * rc) * size, z * size
        return ((rx0 + rx) / 16, (ry0 - ry) / 16, (rz0 + rz) / 16)

    def box(o, v0, width, ln, col, t):
        hw = width / 2
        pts = [model(o, w, fl, n) for w in (-hw, hw) for fl in (v0, v0 + ln) for n in (-0.5, 0.5)]
        out.append((pts, col, t))

    def feather(b, sp, ang, fdeg, off, lenk, ln, width, col, t):
        o = [lerp(sp, J[b][k], J[b + 1][k]) for k in range(3)]
        dir_(ang * D, fdeg * D)
        n = st["n"]
        o = (o[0] + n[0] * off, o[1] + n[1] * off, o[2] + n[2] * off)
        a = max(2, jround(ln * 0.42))
        bb = max(a + 1, ln - 4)
        box(o, 0, width, a * lenk, col[0], t)
        box(o, a * lenk, width, (bb - a) * lenk, col[1], t)
        if ln - 1 > bb:
            box(o, bb * lenk, max(1, width - 1), (ln - 1 - bb) * lenk, col[2], t)
        box(o, (ln - 1) * lenk, max(1, width - 2), lenk, col[3], t)

    sec, fan_in, fan_out = p[SEC], p[FAN_IN], p[FAN_OUT]
    pal = [(244, 253, 255), (196, 236, 255), (134, 205, 255), (238, 252, 255)]
    for i in range(4 if OLD_FIRST else len(SECS), len(SECS)):
        t = i / (len(SECS) - 1)
        ci = ease((c - 0.1 - 0.15 * t) / 0.6)
        sp = 0.04 + 0.94 * t
        feather(1, sp, lerp(ci, -90, sec + t * (fan_in - sec) * 0.55), flapk(p0, p1, p2, 1 + sp), 0.5 + i * 0.16, lerp(ci, 0.8, 1),
                SECS[i], 3, pal, 1 + sp)
    for i in range(len(PRIM)):
        t = i / (len(PRIM) - 1)
        ci = ease((c - 0.25 - 0.3 * t) / 0.45)
        sp = 0.08 + 0.92 * t
        op = lerp(t ** 0.85, fan_in, fan_out)
        feather(2, sp, lerp(ci, -97 + 9 * t, op), flapk(p0, p1, p2, 2 + sp), 1.9 + i * 0.16, lerp(ci, 0.66, 1), PRIM[i], 3, pal, 2 + sp)
    for row in range(2):
        n = COV if row == 0 else COV2
        for i in range(n):
            t = i / (n - 1)
            if t < 0.68:
                continue
            ci = ease((c - 0.2 * t) / 0.6)
            f = (t - 0.68) / 0.32
            sp = (0.06 + 0.9 * f) if row == 0 else (0.1 + 0.86 * f)
            base = lerp(f * (0.7 if row == 0 else 0.6), fan_in, fan_out)
            off = (3.6 if row == 0 else 4.7) + (i % 2) * 0.2
            feather(2, sp, lerp(ci, -90, base), flapk(p0, p1, p2, 2 + sp), off, 1, covert(row, i), 3,
                    [(226, 247, 255)] * 4, 2 + sp)
    return out


# ---- the states -----------------------------------------------------------------------------------------------------
def pose_at(state, t):
    if state == "glide":
        return list(GLIDE)
    if state == "dive":
        return list(DIVE)
    if state == "fly":
        return beat(GLIDE, t, FLY_PERIOD, 1, 0)
    if state == "hover":
        return beat(HOVER, t, HOVER_PERIOD, 0.9, 1)
    if state == "land":
        k = ease(t / 0.3)
        o = [a + (b - a) * k for a, b in zip(GLIDE, FLARE)]
        if t > 0.3:
            s = math.exp(-(t - 0.3) * 4) * math.sin((t - 0.3) * 11)
            o[P0] += 8 * s
            o[FAN_OUT] += 5 * s
        return o
    if state in ("open", "openfly"):
        return unfold(t)
    raise ValueError(state)


def back_out(x):
    x = clamp(x, 0, 1)
    c, y = 1.9, x - 1
    return 1 + (c + 1) * y * y * y + c * y * y


def unfold(t):
    if t < 0.22:
        k = ease(t / 0.22)
        o = list(FOLD)
        o[A0] += 6 * k
        o[P0] += 18 * k
        o[A1] += 14 * k
        return o
    k = back_out((t - 0.22) / 0.42)
    o = [a + (b - a) * k for a, b in zip(FOLD, OPEN)]
    o[CASC] = min(1, (t - 0.22) / 0.5 * 1.25)
    o[P0] = lerp(k, FOLD[P0] + 18, OPEN[P0])
    if t > 0.64:
        s = math.exp(-(t - 0.64) * 6) * math.sin((t - 0.64) * 14)
        o[FAN_OUT] += 7 * s
        o[P2] += 6 * s
        o[CASC] = 1
    return o


UPRIGHT = ("hover", "land", "open")


# ---- camera ---------------------------------------------------------------------------------------------------------
def norm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def camera(pitch):
    """Camera looking along +z (yaw 0) pitched down by pitch degrees: right, up, forward in the world."""
    fwd = norm([0, -math.sin(pitch * D), math.cos(pitch * D)])
    right = np.array([-1.0, 0, 0])
    up = np.cross(right, fwd)  # right x forward = up for this handedness
    if up[1] < 0:
        up = -up
    return right, up, fwd


def body_frame(state, pitch_flight):
    """The figure's frame (AscentAnims.frame): facing (chest), up (head), right; flying along +z pitched by pitch_flight."""
    if state in UPRIGHT:
        u = norm([0, 1, 0.2])
        c = np.array([0.0, 0, 1])
    else:
        dv = 60 if state == "dive" else pitch_flight
        u = norm([0, -math.sin(dv * D), math.cos(dv * D)])
        c = np.array([0.0, -1, 0])
    c = norm(c - u * np.dot(c, u))
    r = np.cross(c, u)
    return c, u, r


def old_place(pts, state, cam, pitch_flight, ahead=0.8, lift=-0.12):
    """AscentRender.firstPersonWings before 12 Nov: the body's frame, pushed ahead of the eye and under it."""
    right, up, fwd = cam
    c, u, r = body_frame(state, pitch_flight)
    out = []
    eye = np.array([0, 1.62, 0])
    off = fwd * ahead + up * lift
    for x, y, z in pts:
        # model (x left, y down from the neck, z behind) -> the standing figure (right, up from the feet, facing)
        lr, lu, lf = -x, 1.501 - y, -z
        w = np.array([0, 0.9, 0]) + r * lr + u * (lu - 0.9) + c * lf
        out.append(w - eye + off)
    return out


def project(v, cam, tan_h, tan_v):
    right, up, fwd = cam
    zf = np.dot(v, fwd)
    return np.dot(v, right) / (zf * tan_h) if zf > 1e-3 else None, np.dot(v, up) / (zf * tan_v) if zf > 1e-3 else None, zf


def render(boxes_world, cam, fov, aspect, w=480):
    h = int(w / aspect)
    tan_v = math.tan(fov / 2 * D)
    tan_h = tan_v * aspect
    img = Image.new("RGB", (w, h), (70, 110, 60))
    dr = ImageDraw.Draw(img, "RGBA")
    # the hands: vanilla's forearms at the frame's lower middle (where nothing may stand in front of them)
    for sx in (-1, 1):
        cx = w / 2 + sx * w * 0.28
        dr.polygon([(cx - w * 0.05, h), (cx + w * 0.05, h), (cx + w * 0.03, h * 0.72), (cx - w * 0.03, h * 0.72)], fill=(200, 150, 120, 255))
    items = []
    inner, behind_count = 9.0, 0
    for pts, col, t in boxes_world:
        pr = [project(v, cam, tan_h, tan_v) for v in pts]
        if any(p[0] is None for p in pr):
            # a box through the camera plane: drawn only if every corner is ahead (the game clips; here we skip)
            behind_count += 1
            vis = [p for p in pr if p[0] is not None]
            if not vis:
                continue
            pr = vis
        xs = [p[0] for p in pr]
        ys = [p[1] for p in pr]
        if max(xs) < -1 or min(xs) > 1 or max(ys) < -1 or min(ys) > 1:
            continue
        for x, y in zip(xs, ys):
            if -1 <= x <= 1 and -1 <= y <= 1:
                inner = min(inner, abs(x))
        depth = sum(p[2] for p in pr) / len(pr)
        poly = [((x + 1) / 2 * w, (1 - y) / 2 * h) for x, y in zip(xs, ys)]
        items.append((depth, poly, col))
    items.sort(key=lambda a: -a[0])
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    for depth, poly, col in items:
        hull = convex_hull(poly)
        if len(hull) >= 3:
            dr.polygon(hull, fill=col + (204,))
            md.polygon(hull, fill=255)
    cover = np.asarray(mask).mean() / 255
    return img, cover, inner


def convex_hull(points):
    pts = sorted(set((round(x, 2), round(y, 2)) for x, y in points))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def frames():
    out = [("glide", 0.0, 5), ("dive", 0.0, 55)]
    for k in range(8):
        out.append(("fly", k / 8 * FLY_PERIOD, 5))
    for k in range(4):
        out.append(("hover", k / 4 * HOVER_PERIOD, 5))
    for k in range(2):
        out.append(("land", 0.15 + k * 0.3, 5))
    out.append(("open", 0.45, 5))
    out.append(("openfly", 0.45, 5))
    return out


def main():
    args = sys.argv[1:]
    out = "fpwings.png"
    old = "--old" in args
    global OLD_FIRST
    OLD_FIRST = old
    fovs = [70.0, 110.0]
    aspect = 16 / 9
    rung = "wings"
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--fov":
            fovs = [float(x) for x in args[i + 1].split(",")]
            i += 1
        elif a == "--aspect":
            aspect = float(args[i + 1])
            i += 1
        elif a == "--rung":
            rung = args[i + 1]
            i += 1
        elif not a.startswith("--"):
            out = a
        i += 1
    import fpwings_place as place  # the new placement (tools/ascent/fpwings_place.py), kept apart so it mirrors the Java
    tiles = []
    for fov in fovs:
        row = []
        for state, t, look_pitch in frames():
            cam = camera(look_pitch)
            boxes = []
            for pi, pair in enumerate(PAIRS[rung]):
                if pair[1] < -30:  # a pair raised over the shoulders is not drawn through your own eyes
                    continue
                p = pose_at(state, max(0.0, t - pair[2]))
                drawn = pair if old else place.drawn(pair)
                for s in (1, -1):
                    for pts, col, tt in wing_boxes(p, drawn, pi, s):
                        if old:
                            wpts = old_place(pts, state, cam, look_pitch)
                        else:
                            wpts = place.place(pts, s, pi, 90 if state in UPRIGHT else 0, p, cam, fov, aspect)
                        boxes.append((wpts, col, tt))
            img, cover, inner = render(boxes, cam, fov, aspect)
            label = f"{state} t={t:.2f} fov {fov:.0f}: cover {cover * 100:.0f}% inner {inner if inner < 9 else '-'}"
            print(label if inner >= 9 else f"{state} t={t:.2f} fov {fov:.0f}: cover {cover * 100:.0f}% inner {inner:.2f}")
            ImageDraw.Draw(img).text((4, 4), label.replace(" inner 9.0", ""), fill=(255, 255, 255))
            row.append(img)
        tiles.append(row)
    cols = len(tiles[0])
    per = 4
    tw, th = tiles[0][0].size
    rows = []
    for row in tiles:
        for k in range(0, cols, per):
            rows.append(row[k:k + per])
    sheet = Image.new("RGB", (tw * per + 4 * (per - 1), th * len(rows) + 4 * (len(rows) - 1)), (20, 20, 20))
    for ri, r in enumerate(rows):
        for ci, im in enumerate(r):
            sheet.paste(im, (ci * (tw + 4), ri * (th + 4)))
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    sys.path.insert(0, __import__("os").path.dirname(__file__))
    main()
