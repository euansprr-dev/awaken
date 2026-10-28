import json, math, sys
import numpy as np
from PIL import Image
S = sys.argv[1]
scan = json.load(open(S + '/scan.json')); Wr = np.load(S + '/wild_real.npy')
cx0, cz0, STEP = -1100, -2700, 16; n = Wr.shape[0]
ANIM = ('sheep', 'pig', 'cow', 'chicken', 'rabbit', 'horse', 'donkey', 'goat', 'wolf', 'mossback', 'fox', 'llama')
g = {k: np.full((n, n), np.nan) for k in ('tall', 'grass', 'bush', 'anim', 'flower', 'leaves')}
for c in scan:
    i = (c['cx'] * 16 - cx0) // STEP; j = (c['cz'] * 16 - cz0) // STEP
    if not (0 <= i < n and 0 <= j < n) or c['h'] is None or 'ocean' in c['biome'] or 'river' in c['biome']: continue
    if math.hypot(c['cx'] * 16 - 490, c['cz'] * 16 + 411) < 330: continue
    g['tall'][j, i] = c['c'].get('tall', 0); g['grass'][j, i] = c['c'].get('grass', 0) + c['c'].get('fern', 0)
    g['bush'][j, i] = c['c'].get('bush', 0); g['flower'][j, i] = c['c'].get('flower', 0); g['leaves'][j, i] = c['c'].get('leaves', 0)
    g['anim'][j, i] = sum(c['e'].get(a, 0) + c['e'].get(a + '#ledger', 0) for a in ANIM)
ok = ~np.isnan(g['tall'])
bands = [(0, .2, 'Tamed'), (.2, .45, 'Pasture'), (.45, .7, 'Wild'), (.7, 1.01, 'Deep wild')]
out = []
for a, b, name in bands:
    m = ok & (Wr >= a) & (Wr < b)
    row = {'band': name, 'chunks': int(m.sum())}
    for k in g: row[k] = round(float(np.nanmean(g[k][m])), 2)
    row['anim_km2'] = round(row['anim'] * 3906)
    out.append(row)
print(json.dumps(out, indent=0))
# correlation of today's cover with wildness
for k in ('tall', 'grass', 'bush', 'anim'):
    print(k, 'corr with W %.2f' % np.corrcoef(Wr[ok], g[k][ok])[0, 1])
json.dump(out, open(S + '/today_by_band.json', 'w'))
# heat pictures (5x5 chunk mean), same crop, for the page
def smooth(a):
    a2 = np.where(np.isnan(a), 0, a); w = (~np.isnan(a)).astype(float); k = 5
    from numpy.lib.stride_tricks import sliding_window_view as sw
    pad = lambda x: np.pad(x, k // 2)
    s = sw(pad(a2), (k, k)).sum((-1, -2)); c = sw(pad(w), (k, k)).sum((-1, -2))
    r = np.where(c > 3, s / np.maximum(c, 1), np.nan); return r
def heat(a, vmax, name, col):
    s = smooth(a); t = np.clip(s / vmax, 0, 1)
    base = np.array([238, 234, 224]); top = np.array(col)
    img = (base * (1 - t[..., None]) + top * t[..., None])
    img[np.isnan(s)] = (210, 210, 214)
    Image.fromarray(img.astype(np.uint8)).resize((n * 3, n * 3), Image.NEAREST).save(S + '/img/' + name)
heat(g['tall'] + g['bush'] * 3, 12, 'today_tall.png', (60, 120, 50))
heat(g['anim'], 1.2, 'today_anim.png', (150, 70, 40))
# proposed (illustrative, from the plan's curves): tall grass and big bushes, animals clumped into herds
rs = np.random.default_rng(3)
def sst(a, b, x): t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)
prop_tall = np.where(ok, 1 + 30 * sst(.3, .95, Wr), np.nan)
heat(prop_tall, 12, 'prop_tall.png', (60, 120, 50))
# herds: expected animals per chunk = farm (tamed) + herds (wild); drawn as clumps
anim = np.zeros((n, n))
farm = 0.11 * (1 - sst(.2, .5, Wr)); anim += farm
herds_per_km2 = 0.6 * sst(.3, .6, Wr) + 1.4 * sst(.6, .95, Wr)
size = 4 + 22 * sst(.35, 1, Wr)
for j in range(n):
    for i in range(n):
        if ok[j, i] and rs.random() < herds_per_km2[j, i] / 3906 * 25:   # one herd centre in this chunk (5x5 smoothing spreads it)
            anim[j, i] += size[j, i]
anim = np.where(ok, anim, np.nan)
heat(anim, 1.2, 'prop_anim.png', (150, 70, 40))
