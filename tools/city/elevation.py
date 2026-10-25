#!/usr/bin/env python3
"""Draws a street elevation of a painted city dump with the real block models and textures (vanilla's from the .ref
assets, the kit's from src/main/resources), orthographic, with a sun shadow under everything that stands proud of the
wall: the facade grammar judged between tours.

  python3 -I tools/city/elevation.py <city-dump.bin> <out.png> <x0> <z0> <x1> <z1> <look> [ry0 ry1] [scale]

<look> is the direction the camera looks (n, s, e, w): "s" stands north of the box looking south at its north faces.
The box is in plan coordinates; ry0..ry1 the rows drawn (default -1..44); scale multiplies the 16 px per block.
"""
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import render  # noqa: E402  (the dump loader)

VANILLA = '/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft'
OURS = os.path.abspath(os.path.join(HERE, '../../src/main/resources/assets/awaken'))
TINT = {'grass': (124, 189, 107), 'foliage': (89, 160, 60)}

_json, _tex = {}, {}


def asset(ns, kind, path):
    root = VANILLA if ns == 'minecraft' else OURS
    return os.path.join(root, kind, path)


def split(rid):
    if ':' in rid:
        ns, p = rid.split(':', 1)
    else:
        ns, p = 'minecraft', rid
    return ns, p


def load_json(ns, kind, path):
    key = (ns, kind, path)
    if key not in _json:
        f = asset(ns, kind, path + '.json')
        _json[key] = json.load(open(f)) if os.path.exists(f) else None
    return _json[key]


def texture(rid):
    if rid not in _tex:
        ns, p = split(rid)
        f = asset(ns, 'textures', p + '.png')
        im = None
        if os.path.exists(f):
            im = Image.open(f).convert('RGBA')
            if im.height > im.width:
                im = im.crop((0, 0, im.width, im.width))
            im = np.asarray(im).astype(np.float32)
        _tex[rid] = im
    return _tex[rid]


def model(rid):
    """A model with its parents resolved: (elements, textures)."""
    ns, p = split(rid)
    m = load_json(ns, 'models', p)
    if m is None:
        return None, {}
    tex = dict(m.get('textures', {}))
    els = m.get('elements')
    parent = m.get('parent')
    while parent and (els is None or True):
        pns, pp = split(parent)
        pm = load_json(pns, 'models', pp)
        if pm is None:
            break
        for k, v in pm.get('textures', {}).items():
            tex.setdefault(k, v)
        if els is None:
            els = pm.get('elements')
        parent = pm.get('parent')
    return els, tex


def resolve(tex, ref, depth=0):
    while depth < 10:
        if isinstance(ref, dict):
            ref = ref.get('sprite')
        if not ref or not ref.startswith('#'):
            break
        ref = tex.get(ref[1:])
        depth += 1
    return ref if isinstance(ref, str) else None


def parse_state(s):
    m = re.match(r'Block\{([a-z0-9_:]+)\}(\[(.*)\])?', s)
    if not m:
        return None, {}
    props = dict(kv.split('=') for kv in m.group(3).split(',')) if m.group(3) else {}
    return m.group(1), props


def matches(cond, props):
    if 'OR' in cond:
        return any(matches(c, props) for c in cond['OR'])
    if 'AND' in cond:
        return all(matches(c, props) for c in cond['AND'])
    for k, v in cond.items():
        if props.get(k) not in str(v).split('|'):
            return False
    return True


def parts(bid, props):
    """The (model, x, y) list a block state draws (variants or multipart; the first of a random list)."""
    ns, p = split(bid)
    bs = load_json(ns, 'blockstates', p)
    if bs is None:
        return []
    out = []
    if 'variants' in bs:
        best = None
        for key, v in bs['variants'].items():
            conds = dict(kv.split('=') for kv in key.split(',')) if key else {}
            if all(props.get(k) == val for k, val in conds.items()):
                best = v
                break
        if best is not None:
            v = best[0] if isinstance(best, list) else best
            out.append((v['model'], v.get('x', 0), v.get('y', 0)))
    for part in bs.get('multipart', []):
        if 'when' in part and not matches(part['when'], props):
            continue
        a = part['apply']
        a = a[0] if isinstance(a, list) else a
        out.append((a['model'], a.get('x', 0), a.get('y', 0)))
    return out


# faces as directions: which face of an element looks at the camera, for each look direction
LOOK_FACE = {'s': 'north', 'n': 'south', 'e': 'west', 'w': 'east'}
ROT_Y = {0: {'north': 'north', 'east': 'east', 'south': 'south', 'west': 'west'},
         90: {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'},
         180: {'north': 'south', 'east': 'west', 'south': 'north', 'west': 'east'},
         270: {'north': 'west', 'east': 'north', 'south': 'east', 'west': 'south'}}


def rot_box(f, t, y, x):
    """An element's box after the variant's rotations (x about the x axis first, then y), in 0..16 block space."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    if x % 360 == 180:
        y0, y1, z0, z1 = 16 - y1, 16 - y0, 16 - z1, 16 - z0
    elif x % 360 == 90:
        y0, y1, z0, z1 = z0, z1, 16 - y1, 16 - y0
    elif x % 360 == 270:
        y0, y1, z0, z1 = 16 - z1, 16 - z0, y0, y1
    for _ in range((y // 90) % 4):
        x0, x1, z0, z1 = 16 - z1, 16 - z0, x0, x1
    return (x0, y0, z0), (x1, y1, z1)


def face_before_rot(face, y):
    """Which model face ends up looking `face` after a y rotation."""
    inv = {v: k for k, v in ROT_Y[y % 360].items()}
    return inv[face]


def draw(dump, out, x0, z0, x1, z1, look, ry0=-1, ry1=44, scale=1):
    vox, pal, ymin, ox, oz = render.load(dump)
    states = [parse_state(s) for s in pal]
    S = 16
    if look in 'ns':
        across = range(x0, x1 + 1) if look == 's' else range(x1, x0 - 1, -1)
        depths = range(z0, z1 + 1) if look == 's' else range(z1, z0 - 1, -1)
    else:
        across = range(z1, z0 - 1, -1) if look == 'e' else range(z0, z1 + 1)
        depths = range(x0, x1 + 1) if look == 'e' else range(x1, x0 - 1, -1)
    across, depths = list(across), list(depths)
    W, H = len(across) * S, (ry1 - ry0 + 1) * S
    img = np.zeros((H, W, 3), np.float32)
    img[:] = (170, 196, 222)
    zbuf = np.full((H, W), 1e9, np.float32)
    want = LOOK_FACE[look]
    nd = len(depths)
    for di in range(nd - 1, -1, -1):
        dv = depths[di]
        for ai, av in enumerate(across):
            px, pz = (av, dv) if look in 'ns' else (dv, av)
            ix, iz = px + ox, pz + oz
            if not (0 <= ix < vox.shape[2] and 0 <= iz < vox.shape[1]):
                continue
            col = vox[:, iz, ix]
            for ry in range(ry0, ry1 + 1):
                iy = ry - ymin
                if iy < 0 or iy >= vox.shape[0]:
                    continue
                sid = int(col[iy])
                if sid == 0:
                    continue
                bid, props = states[sid]
                if bid in (None, 'minecraft:air', 'minecraft:cave_air'):
                    continue
                cell(img, zbuf, bid, props, look, want, ai, ry1 - ry, di, S)
    shade(img, zbuf, S)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
    if scale != 1:
        im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    im.save(out)
    print('wrote', out, im.size)


def cell(img, zbuf, bid, props, look, want, ai, row, di, S):
    if bid == 'minecraft:water':
        tile(img, zbuf, np.full((S, S, 4), (60, 100, 200, 200), np.float32), ai * S, row * S, S, S, di + 0.5)
        return
    for mrid, rx, ry in parts(bid, props):
        els, tex = model(mrid)
        if not els:
            continue
        for el in els:
            (bx0, by0, bz0), (bx1, by1, bz1) = rot_box(el['from'], el['to'], ry, rx)
            src_face = face_before_rot(want, ry) if rx % 360 == 0 else want
            fd = el.get('faces', {}).get(src_face)
            if fd is None:
                # crossed planes and other rotated elements: draw any face we have over the box
                if 'rotation' in el and el.get('faces'):
                    fd = next(iter(el['faces'].values()))
                else:
                    continue
            ref = resolve(tex, fd.get('texture'))
            if not ref:
                continue
            t = texture(ref)
            if t is None:
                continue
            # the element's footprint across and up, and its near edge in depth
            if look == 's':
                a0, a1, near = bx0, bx1, bz0
            elif look == 'n':
                a0, a1, near = 16 - bx1, 16 - bx0, 16 - bz1
            elif look == 'e':
                a0, a1, near = 16 - bz1, 16 - bz0, bx0
            else:
                a0, a1, near = bz0, bz1, 16 - bx1
            w, h = a1 - a0, by1 - by0
            if w <= 0.01 or h <= 0.01:
                continue
            uv = fd.get('uv')
            if uv is None:
                uv = [a0, 16 - by1, a1, 16 - by0]
            u0, v0, u1, v1 = uv
            tw = t.shape[1] / 16.0
            sx0, sx1 = sorted((u0 * tw, u1 * tw))
            sy0, sy1 = sorted((v0 * tw, v1 * tw))
            sub = t[int(sy0):max(int(sy0) + 1, int(math.ceil(sy1))), int(sx0):max(int(sx0) + 1, int(math.ceil(sx1)))]
            if u0 > u1:
                sub = sub[:, ::-1]
            if v0 > v1:
                sub = sub[::-1]
            pw, ph = max(1, int(round(w * S / 16))), max(1, int(round(h * S / 16)))
            ys = (np.arange(ph) * sub.shape[0] / ph).astype(int)
            xs = (np.arange(pw) * sub.shape[1] / pw).astype(int)
            patch = sub[ys][:, xs].copy()
            if 'tintindex' in fd:
                tint = TINT['grass'] if 'grass' in bid else TINT['foliage']
                patch[..., :3] = patch[..., :3] * np.array(tint) / 255.0
            px0 = ai * S + int(round(a0 * S / 16))
            py0 = row * S + int(round((16 - by1) * S / 16))
            tile(img, zbuf, patch, px0, py0, pw, ph, di + near / 16.0)


def tile(img, zbuf, patch, x0, y0, w, h, depth):
    H, W = zbuf.shape
    xa, ya = max(0, x0), max(0, y0)
    xb, yb = min(W, x0 + w), min(H, y0 + h)
    if xa >= xb or ya >= yb:
        return
    p = patch[ya - y0:yb - y0, xa - x0:xb - x0]
    alpha = p[..., 3] > 127
    nearer = depth <= zbuf[ya:yb, xa:xb] + 1e-4
    m = alpha & nearer
    region = img[ya:yb, xa:xb]
    region[m] = p[..., :3][m]
    zb = zbuf[ya:yb, xa:xb]
    zb[m] = depth


def shade(img, zbuf, S):
    """Depth haze (deeper is a little darker) and a sun shadow straight down under whatever stands proud."""
    H, W = zbuf.shape
    sky = zbuf > 1e8
    front = zbuf[~sky].min() if (~sky).any() else 0
    d = np.where(sky, 0, zbuf - front)
    img *= np.where(sky, 1.0, np.clip(1.0 - 0.05 * d, 0.55, 1.0))[..., None]
    shadow = np.zeros((H, W), bool)
    reach = int(S * 0.75)
    for k in range(1, reach + 1):
        above = np.full((H, W), 1e9, np.float32)
        above[k:] = zbuf[:-k]
        shadow |= (zbuf - above > 0.3) & ~sky
    img[shadow] *= 0.62


if __name__ == '__main__':
    a = sys.argv
    dump, out = a[1], a[2]
    x0, z0, x1, z1 = (int(v) for v in a[3:7])
    look = a[7]
    ry0 = int(a[8]) if len(a) > 8 else -1
    ry1 = int(a[9]) if len(a) > 9 else 44
    scale = int(a[10]) if len(a) > 10 else 1
    draw(dump, out, x0, z0, x1, z1, look, ry0, ry1, scale)
