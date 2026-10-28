"""Wildness field prototype over Euan's world (seed 764383433107166945). Planning harness only."""
import json, math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
S = sys.argv[1]
W = json.load(open(S + '/world.json'))
X0, Z0, SIZE, STEP = -5500, -6400, 11000, 16
N = SIZE // STEP
lv = np.array(W['levels'], np.float32)[:N, :N]          # [z][x]
xs = X0 + np.arange(N) * STEP + STEP / 2; zs = Z0 + np.arange(N) * STEP + STEP / 2
XX, ZZ = np.meshgrid(xs, zs)
CITY = (490, -411)
places = [('city', 490, -411, 330, 520)]                 # kind, x, z, built radius, reach of its touch
places += [('town', -352, -2080, 116, 380), ('hamlet', 464, -1283, 38, 200)]   # Goldbury (first town), Bramgrove (first hamlet)
hearth = (403, -1961)
def far(a, b, d): return math.hypot(a[0] - b[0], a[1] - b[1]) > d
for gx, gz, hx, hz, town in W['hearts']:
    if town and far((hx, hz), CITY, 1300) and far((hx, hz), (-352, -2080), 900):
        places.append(('town', hx, hz, 120, 380))
anch = []
for kind, x, z in W['anchors']:
    if not far((x, z), (464, -1283), 440): continue
    if any(p[0] == 'town' and not far((x, z), (p[1], p[2]), 500) for p in places): continue
    anch.append((kind, x, z))
    places.append((kind, x, z, 60 if kind == 'village' else 38, 260 if kind == 'village' else 200))
# hubs: a town at a heart, else the anchor nearest it within 450, else a wayhouse at the heart
hubs = {}
for gx, gz, hx, hz, town in W['hearts']:
    if not far((hx, hz), CITY, 700):
        hubs[(gx, gz)] = CITY; continue
    t = [p for p in places if p[0] == 'town' and not far((p[1], p[2]), (hx, hz), 450)]
    if t: hubs[(gx, gz)] = (t[0][1], t[0][2]); continue
    a = sorted([(math.hypot(x - hx, z - hz), x, z) for k, x, z in anch if math.hypot(x - hx, z - hz) < 450])
    if a: hubs[(gx, gz)] = (a[0][1], a[0][2]); continue
    hubs[(gx, gz)] = (hx, hz); places.append(('wayhouse', hx, hz, 14, 120))
roads = []
for (gx, gz), h in hubs.items():
    for d in ((1, 0), (0, 1)):
        o = hubs.get((gx + d[0], gz + d[1]))
        if o and o != h: roads.append((h, o, 'high'))
roads.append((CITY, (464, -1283), 'high'))
for k, x, z in anch:   # a lane to the nearest highroad point
    best = None
    for a, b, t in roads:
        if t != 'high': continue
        ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az; L = vx * vx + vz * vz or 1
        u = max(0, min(1, ((x - ax) * vx + (z - az) * vz) / L)); px, pz = ax + u * vx, az + u * vz
        d = math.hypot(px - x, pz - z)
        if best is None or d < best[0]: best = (d, (px, pz))
    if best and best[0] > 30: roads.append(((x, z), best[1], 'lane'))
def seg_dist(a, b):
    ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az; L = vx * vx + vz * vz or 1
    u = np.clip(((XX - ax) * vx + (ZZ - az) * vz) / L, 0, 1)
    return np.hypot(XX - (ax + u * vx), ZZ - (az + u * vz))
def vnoise(cell, seed):
    rs = np.random.default_rng(seed); g = rs.random((int(SIZE / cell) + 3,) * 2)
    fx = (XX - X0) / cell; fz = (ZZ - Z0) / cell; x0 = fx.astype(int); z0 = fz.astype(int); tx = fx - x0; tz = fz - z0
    tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz)
    return (g[z0, x0] * (1 - tx) + g[z0, x0 + 1] * tx) * (1 - tz) + (g[z0 + 1, x0] * (1 - tx) + g[z0 + 1, x0 + 1] * tx) * tz
# ---- the field -----------------------------------------------------------------------------------------------------------
def field(with_hearth=True, with_roads=True, noise=True, rank=True):
    keep = np.ones_like(XX)                         # 1 - touch, multiplied over every source (a soft union)
    for k, x, z, r, reach in places:
        d = np.maximum(0, np.hypot(XX - x, ZZ - z) - r)
        keep *= 1 - np.exp(-(d / reach) ** 1.6)
    if with_hearth:
        d = np.maximum(0, np.hypot(XX - hearth[0], ZZ - hearth[1]) - 48)
        keep *= 1 - 0.8 * np.exp(-(d / 140) ** 1.6)
    if with_roads:
        for a, b, t in roads:
            d = seg_dist(a, b)
            s, w = (0.7, 70) if t == 'high' else (0.5, 40)
            keep *= 1 - s * np.exp(-(d / w) ** 1.6)
    w = keep
    if rank:
        rk = np.searchsorted(np.array([10, 25, 45, 70, 100]), lv, side='right')
        w = w + 0.035 * rk * keep
    if noise:
        w = w + (vnoise(1100, 7) - 0.5) * 0.32 * np.sqrt(keep) + (vnoise(260, 9) - 0.5) * 0.14 * np.sqrt(keep)
    return np.clip(w, 0, 1)
Wf = field()
np.save(S + '/wild.npy', Wf)
# ---- pictures -------------------------------------------------------------------------------------------------------------
STOPS = [(0.0, (232, 222, 196)), (0.2, (214, 216, 160)), (0.45, (150, 186, 98)), (0.7, (70, 132, 66)), (0.85, (34, 92, 58)), (1.0, (18, 58, 44))]
def ramp(v):
    out = np.zeros(v.shape + (3,), np.float32)
    for (a, ca), (b, cb) in zip(STOPS[:-1], STOPS[1:]):
        m = (v >= a) & (v <= b); t = ((v - a) / (b - a))[m][:, None]
        out[m] = np.array(ca) * (1 - t) + np.array(cb) * t
    return out.astype(np.uint8)
def to_px(x, z, x0=X0, z0=Z0, step=STEP): return ((x - x0) / step, (z - z0) / step)
def draw_map(fld, name, crop=None, scale=1, title=None, scan=None):
    img = ramp(fld)
    x0, z0 = X0, Z0
    if crop:
        cx0, cz0, cs = crop; i0 = int((cx0 - X0) / STEP); j0 = int((cz0 - Z0) / STEP); n = int(cs / STEP)
        img = img[j0:j0 + n, i0:i0 + n]; x0, z0 = cx0, cz0
    im = Image.fromarray(img).resize((img.shape[1] * scale, img.shape[0] * scale), Image.NEAREST)
    d = ImageDraw.Draw(im)
    P = lambda x, z: tuple(v * scale for v in to_px(x, z, x0, z0))
    if scan is not None:
        for (cx, cz) in scan:
            px, pz = P(cx * 16, cz * 16); d.rectangle([px, pz, px + scale, pz + scale], fill=(120, 80, 40))
    for a, b, t in roads:
        d.line([P(*a), P(*b)], fill=(150, 112, 70) if t == 'high' else (170, 140, 100), width=max(1, scale * (2 if t == 'high' else 1)))
    for k, x, z, r, reach in places:
        px, pz = P(x, z); rr = max(2.5, r / STEP * scale)
        col = {'city': (60, 60, 70), 'town': (120, 60, 50), 'village': (150, 80, 60), 'hamlet': (170, 100, 70), 'wayhouse': (190, 140, 90)}[k]
        d.ellipse([px - rr, pz - rr, px + rr, pz + rr], fill=col, outline=(255, 255, 255))
    hx, hz = P(*hearth); d.rectangle([hx - 4, hz - 4, hx + 4, hz + 4], fill=(230, 120, 40), outline=(255, 255, 255))
    im.save(S + '/img/' + name, quality=92)
    return im
draw_map(Wf, 'wild_map.png')
# the area Euan has walked, at 2x
draw_map(Wf, 'wild_map_zoom.png', crop=(-1100, -2700, 2600), scale=3)
# ---- numbers --------------------------------------------------------------------------------------------------------------
D = np.hypot(XX - CITY[0], ZZ - CITY[1])
bands = [(0, .2, 'Tamed'), (.2, .45, 'Pasture'), (.45, .7, 'Wild'), (.7, 1.01, 'Deep wild')]
rows = []
for lo, hi in [(0, 1000), (1000, 2000), (2000, 3500), (3500, 5500)]:
    m = (D >= lo) & (D < hi)
    rows.append([lo, hi] + [float(((Wf >= a) & (Wf < b) & m).sum() / m.sum()) for a, b, _ in bands])
# how far from the nearest settled thing until it is wild, measured along the field
keep_only = field(noise=False, rank=False)
stats = {'rings': rows, 'share_all': [float(((Wf >= a) & (Wf < b)).mean()) for a, b, _ in bands],
         'places': len(places), 'roads': len(roads),
         'max_step_per_100': float(max(np.abs(np.diff(Wf, axis=0)).max(), np.abs(np.diff(Wf, axis=1)).max()) * 100 / STEP)}
# a walk: city -> north road -> Bramgrove -> Hearth -> Goldbury -> beyond west
path = [(490, -411), (464, -1283), (403, -1961), (-352, -2080), (-1600, -2600), (-3000, -3200)]
walk = []
dist = 0
for (ax, az), (bx, bz) in zip(path[:-1], path[1:]):
    L = math.hypot(bx - ax, bz - az)
    for s in range(0, int(L), 32):
        x = ax + (bx - ax) * s / L; z = az + (bz - az) * s / L
        i = int((x - X0) / STEP); j = int((z - Z0) / STEP)
        walk.append([round(dist + s), round(float(Wf[j, i]), 3), int(lv[j, i])])
    dist += L
stats['walk'] = walk
stats['walk_marks'] = [[0, 'Carillon'], [round(math.hypot(26, 872)), 'Bramgrove'], [round(math.hypot(26, 872) + math.hypot(61, 678)), 'your Hearth'],
                       [round(math.hypot(26, 872) + math.hypot(61, 678) + math.hypot(755, 119)), 'Goldbury']]
json.dump(stats, open(S + '/wild_stats.json', 'w'))
print(json.dumps({k: v for k, v in stats.items() if k != 'walk'}, indent=1))
# ---- check the road guess against the real path blocks in Euan's chunks -------------------------------------------------
scan = json.load(open(S + '/scan.json'))
real = [(c['cx'], c['cz']) for c in scan if c['c'].get('path', 0) >= 12]
def near_road(x, z):
    best = 1e9
    for a, b, t in roads:
        ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az; L = vx * vx + vz * vz or 1
        u = max(0, min(1, ((x - ax) * vx + (z - az) * vz) / L)); best = min(best, math.hypot(ax + u * vx - x, az + u * vz - z))
    return best
dd = [near_road(cx * 16 + 8, cz * 16 + 8) for cx, cz in real]
inside_city = [math.hypot(cx * 16 - 490, cz * 16 + 411) < 380 for cx, cz in real]
dd2 = [d for d, ic in zip(dd, inside_city) if not ic]
print('real road chunks outside city', len(dd2), 'within 64 of guess %.0f%%, within 128 %.0f%%' % (100 * np.mean(np.array(dd2) < 64), 100 * np.mean(np.array(dd2) < 128)))
draw_map(Wf, 'wild_map_zoom_real.png', crop=(-1100, -2700, 2600), scale=3, scan=real)
# ---- the field over the land Euan has generated, from the real roads and settlements only ------------------------------------
cx0, cz0, cs = -1100, -2700, 2600
n = cs // STEP
gx = cx0 + np.arange(n) * STEP + 8; gz = cz0 + np.arange(n) * STEP + 8
GX, GZ = np.meshgrid(gx, gz)
realplaces = [('city', 490, -411, 330, 520), ('town', -352, -2080, 116, 380), ('hamlet', 464, -1283, 38, 200)]
keep = np.ones_like(GX, dtype=np.float64)
for k, x, z, r, reach in realplaces:
    d = np.maximum(0, np.hypot(GX - x, GZ - z) - r); keep *= 1 - np.exp(-(d / reach) ** 1.6)
d = np.maximum(0, np.hypot(GX - hearth[0], GZ - hearth[1]) - 48); keep *= 1 - 0.8 * np.exp(-(d / 140) ** 1.6)
rp = np.array([(cx * 16 + 8, cz * 16 + 8) for cx, cz in real if math.hypot(cx * 16 - 490, cz * 16 + 411) > 330], np.float64)
dr = np.full(GX.shape, 1e9)
for px, pz in rp: dr = np.minimum(dr, np.hypot(GX - px, GZ - pz))
keep *= 1 - 0.7 * np.exp(-(dr / 70) ** 1.6)
i0 = int((cx0 - X0) / STEP); j0 = int((cz0 - Z0) / STEP)
lvc = lv[j0:j0 + n, i0:i0 + n]
rk = np.searchsorted(np.array([10, 25, 45, 70, 100]), lvc, side='right')
def vn(cell, seed):
    rs = np.random.default_rng(seed); g = rs.random((int(SIZE / cell) + 3,) * 2)
    fx = (GX - X0) / cell; fz = (GZ - Z0) / cell; x0 = fx.astype(int); z0 = fz.astype(int); tx = fx - x0; tz = fz - z0
    tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz)
    return (g[z0, x0] * (1 - tx) + g[z0, x0 + 1] * tx) * (1 - tz) + (g[z0 + 1, x0] * (1 - tx) + g[z0 + 1, x0 + 1] * tx) * tz
Wr = np.clip(keep + 0.035 * rk * keep + (vn(1100, 7) - .5) * .32 * np.sqrt(keep) + (vn(260, 9) - .5) * .14 * np.sqrt(keep), 0, 1)
np.save(S + '/wild_real.npy', Wr)
im = Image.fromarray(ramp(Wr)).resize((n * 3, n * 3), Image.NEAREST); dw = ImageDraw.Draw(im)
for px, pz in rp: dw.rectangle([(px - cx0) / STEP * 3 - 1, (pz - cz0) / STEP * 3 - 1, (px - cx0) / STEP * 3 + 2, (pz - cz0) / STEP * 3 + 2], fill=(140, 100, 60))
for k, x, z, r, reach in realplaces:
    px, pz = (x - cx0) / STEP * 3, (z - cz0) / STEP * 3; rr = max(5, r / STEP * 3)
    dw.ellipse([px - rr, pz - rr, px + rr, pz + rr], fill={'city': (60, 60, 70), 'town': (120, 60, 50), 'hamlet': (170, 100, 70)}[k], outline=(255, 255, 255), width=2)
hx, hz = (hearth[0] - cx0) / STEP * 3, (hearth[1] - cz0) / STEP * 3; dw.rectangle([hx - 6, hz - 6, hx + 6, hz + 6], fill=(230, 120, 40), outline=(255, 255, 255), width=2)
im.save(S + '/img/wild_real.png')
# the land actually generated, outlined: which chunks exist
mask = np.zeros((n, n), bool)
for c in scan:
    i = (c['cx'] * 16 - cx0) // STEP; j = (c['cz'] * 16 - cz0) // STEP
    if 0 <= i < n and 0 <= j < n: mask[j, i] = True
np.save(S + '/real_mask.npy', mask)
print('zoom share', [round(float(((Wr >= a) & (Wr < b) & mask).sum() / mask.sum()), 3) for a, b, _ in bands])
