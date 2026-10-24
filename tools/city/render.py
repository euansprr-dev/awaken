#!/usr/bin/env python3
"""Draws a painted city dump (/awaken city dump, or the city_studio_dump GameTest) from the sky, in block colours.

  python3 -I tools/city/render.py <city-dump.bin> <out-dir> [scale]

Writes <out-dir>/render-top.png (straight down, north up, hill-shaded by height) and <out-dir>/render-nw.png (from the
north-west, about 40 degrees down, looking south-east across the spire: the opening's sky shot, without fog or Lens).
Colours are the average of each block's vanilla texture (top face for the top view), so the read of districts, roofs,
streets and landmarks is honest; textures, lighting and shaders are not modelled.
"""
import gzip
import math
import os
import re
import struct
import sys

import numpy as np

ASSETS = '/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block'
_cache = {}

TINT = {'grass': (124, 189, 107), 'leaves': (89, 160, 60), 'birch_leaves': (128, 167, 85), 'water': (63, 118, 228)}


def tex_colour(name):
    if name in _cache:
        return _cache[name]
    from PIL import Image
    path = os.path.join(ASSETS, name + '.png')
    if not os.path.exists(path):
        _cache[name] = None
        return None
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    if h > w:
        im = im.crop((0, 0, w, w))
    a = np.asarray(im).reshape(-1, 4).astype(float)
    a = a[a[:, 3] > 0]
    c = tuple(int(v) for v in a[:, :3].mean(axis=0)) if len(a) else None
    _cache[name] = c
    return c


BASES = [('deepslate_tile', 'deepslate_tiles'), ('deepslate_brick', 'deepslate_bricks'), ('stone_brick', 'stone_bricks'),
         ('mossy_stone_brick', 'mossy_stone_bricks'), ('polished_blackstone_brick', 'polished_blackstone_bricks'),
         ('tuff_brick', 'tuff_bricks'), ('mud_brick', 'mud_bricks'), ('resin_brick', 'resin_bricks'),
         ('cinnabar_brick', 'cinnabar_bricks'), ('end_stone_brick', 'end_stone_bricks'), ('nether_brick', 'nether_bricks'),
         ('brick', 'bricks'), ('prismarine_brick', 'prismarine_bricks'), ('quartz', 'quartz_block_top'),
         ('smooth_quartz', 'quartz_block_bottom'), ('smooth_stone', 'smooth_stone'), ('smooth_sandstone', 'sandstone_top'),
         ('cobbled_deepslate', 'cobbled_deepslate'), ('polished_deepslate', 'polished_deepslate'),
         ('waxed_', '')]


def block_colour(state, face):
    m = re.match(r'Block\{minecraft:([a-z0-9_]+)\}(\[(.*)\])?', state)
    if not m:
        return (255, 0, 255)
    bid = m.group(1)
    props = dict(kv.split('=') for kv in m.group(3).split(',')) if m.group(3) else {}
    if bid == 'air':
        return None
    if bid == 'water':
        return TINT['water']
    if bid in ('smooth_quartz', 'waxed_smooth_quartz'):
        return tex_colour('quartz_block_bottom')
    if bid == 'smooth_sandstone':
        return tex_colour('sandstone_top')
    if bid == 'smooth_red_sandstone':
        return tex_colour('red_sandstone_top')
    if bid == 'grass_block':
        return TINT['grass'] if face == 'top' else tex_colour('dirt')
    if bid in ('short_grass', 'tall_grass'):
        return TINT['grass']
    if bid.endswith('_leaves'):
        if bid in ('cherry_leaves', 'azalea_leaves', 'flowering_azalea_leaves', 'pale_oak_leaves'):
            return tex_colour(bid)
        return TINT['birch_leaves'] if bid.startswith('birch') else TINT['leaves']
    name = bid.replace('waxed_', '')
    for suffix in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button'):
        if name.endswith(suffix):
            base = name[:-len(suffix)]
            for a, b in BASES:
                if base == a:
                    base = b
                    break
            for cand in (base, base + '_planks', base + 's', base + '_block', base + '_top'):
                c = tex_colour(cand)
                if c:
                    return c
            return (128, 128, 128)
    if name.endswith('_pane'):
        n2 = name[:-5]
        return tex_colour(n2) or (200, 220, 230)
    if name in ('farmland',):
        return tex_colour('farmland_moist') if face == 'top' else tex_colour('dirt')
    if name in ('wheat', 'carrots', 'potatoes', 'beetroots'):
        stage = {'wheat': 7, 'carrots': 3, 'potatoes': 3, 'beetroots': 3}[name]
        age = int(props.get('age', stage))
        st = min(stage, age if name == 'wheat' else age // 2 if name != 'beetroots' else age)
        return tex_colour(f'{name}_stage{st}') or (120, 140, 60)
    for cand in ((name + '_top', name) if face == 'top' else (name, name + '_side', name + '_top')):
        c = tex_colour(cand)
        if c:
            return c
    if name.endswith('_wood'):
        return tex_colour(name.replace('_wood', '_log')) or (100, 80, 60)
    if name.startswith('potted_') or name == 'flower_pot':
        return tex_colour('flower_pot') or (120, 70, 50)
    if name.endswith('_door'):
        return tex_colour(name + '_top') or (100, 70, 40)
    if name.endswith('_carpet'):
        return tex_colour(name.replace('_carpet', '_wool')) or (200, 200, 200)
    if name == 'iron_chain':
        return (60, 64, 72)
    return (255, 0, 255)


def load(path):
    with gzip.open(path, 'rb') as f:
        assert f.read(8) == b'CITYDUMP'
        version, ymin, h, npal = struct.unpack('>4i', f.read(16))
        pal = []
        for _ in range(npal):
            (n,) = struct.unpack('>H', f.read(2))
            pal.append(f.read(n).decode('utf-8'))
        nchunks, offx, offz = struct.unpack('>3i', f.read(12))
    chunks = []
    with gzip.open(path + '.chunks', 'rb') as f:
        for _ in range(nchunks):
            dx, dz = struct.unpack('>2i', f.read(8))
            cells = np.frombuffer(f.read(16 * 16 * h * 2), dtype='>u2').reshape(h, 16, 16)
            chunks.append((dx, dz, cells))
    xs = [c[0] for c in chunks]
    zs = [c[1] for c in chunks]
    x0, z0 = min(xs), min(zs)
    W, Dd = (max(xs) - x0 + 1) * 16, (max(zs) - z0 + 1) * 16
    vox = np.zeros((h, Dd, W), np.uint16)
    for dx, dz, cells in chunks:
        vox[:, (dz - z0) * 16:(dz - z0) * 16 + 16, (dx - x0) * 16:(dx - x0) * 16 + 16] = cells
    # plan origin in array coordinates
    ox = -x0 * 16 - offx
    oz = -z0 * 16 - offz
    return vox, pal, ymin, ox, oz


def palettes(pal):
    top = np.zeros((len(pal), 3), np.float32)
    side = np.zeros((len(pal), 3), np.float32)
    solid = np.zeros(len(pal), bool)
    for i, s in enumerate(pal):
        if i == 0:
            continue
        ct = block_colour(s, 'top')
        cs = block_colour(s, 'side')
        if ct is None:
            continue
        solid[i] = True
        top[i] = ct
        side[i] = cs if cs is not None else ct
    return top, side, solid


def render_top(vox, pal, ymin, out, scale):
    from PIL import Image
    top, side, solid = palettes(pal)
    H, Dd, W = vox.shape
    s = solid[vox]
    # highest solid cell per column
    idx = np.where(s.any(axis=0), H - 1 - np.argmax(s[::-1], axis=0), -1)
    zz, xx = np.meshgrid(np.arange(Dd), np.arange(W), indexing='ij')
    ids = np.where(idx >= 0, vox[np.clip(idx, 0, H - 1), zz, xx], 0)
    col = top[ids]
    col[idx < 0] = (98, 120, 70)
    hgt = np.where(idx >= 0, idx + ymin, 0).astype(np.float32)
    # hill shade from the north-west
    gx = np.zeros_like(hgt)
    gz = np.zeros_like(hgt)
    gx[:, 1:] = hgt[:, 1:] - hgt[:, :-1]
    gz[1:, :] = hgt[1:, :] - hgt[:-1, :]
    shade = np.clip(1.0 + 0.06 * (gx + gz), 0.55, 1.25)
    shade *= 0.85 + 0.15 * np.clip((hgt + 4) / 40.0, 0, 1)
    img = np.clip(col * shade[..., None], 0, 255).astype(np.uint8)
    im = Image.fromarray(img, 'RGB')
    if scale != 1:
        im = im.resize((W * scale, Dd * scale), Image.NEAREST)
    im.save(out)


def render_oblique(vox, pal, ymin, ox, oz, out, scale, elev=38.0, sky=(176, 200, 222)):
    from PIL import Image
    top, side, solid = palettes(pal)
    H, Dd, W = vox.shape
    s = solid[vox]
    # ground plane where the dump left columns untouched
    untouched = ~s.any(axis=0)
    gy = -1 - ymin
    s[gy][untouched] = True
    grass_id = 0
    vox = vox.copy()
    # visible faces: top, north (z-1) and west (x-1) for a camera in the north-west
    up = np.zeros_like(s)
    up[:-1] = ~s[1:]
    up[-1] = True
    north = np.zeros_like(s)
    north[:, 1:, :] = ~s[:, :-1, :]
    north[:, 0, :] = True
    west = np.zeros_like(s)
    west[:, :, 1:] = ~s[:, :, :-1]
    west[:, :, 0] = True
    vis = s & (up | north | west)
    y, z, x = np.nonzero(vis)
    ids = vox[y, z, x]
    face_top = up[y, z, x]
    face_n = north[y, z, x] & ~face_top
    col = np.where(face_top[:, None], top[ids], side[ids] * np.where(face_n, 0.82, 0.66)[:, None])
    plane = untouched[z, x] & (y == gy)
    col[plane] = (98, 120, 70)
    e = math.radians(elev)
    # camera looking south-east, tilted down by elev
    f = np.array([math.cos(e) / math.sqrt(2), -math.sin(e), math.cos(e) / math.sqrt(2)])
    r = np.array([-1 / math.sqrt(2), 0.0, 1 / math.sqrt(2)])
    u = np.cross(r, f)
    px = (x - ox) + 0.5
    py = y + ymin + 0.5
    pz = (z - oz) + 0.5
    sx = px * r[0] + py * r[1] + pz * r[2]
    sy = px * u[0] + py * u[1] + pz * u[2]
    depth = px * f[0] + py * f[1] + pz * f[2]
    sx = (sx - sx.min()) * scale
    sy = (sy.max() - sy) * scale
    Wd, Hd = int(sx.max()) + 4, int(sy.max()) + 4
    img = np.zeros((Hd, Wd, 3), np.float32)
    img[:] = sky
    # light haze with distance, so depth reads
    dn = (depth - depth.min()) / (depth.max() - depth.min() + 1e-6)
    col = col * (1 - 0.25 * dn[:, None]) + np.array(sky) * (0.25 * dn[:, None])
    # a z-buffer: every voxel splats a small square; the nearest one wins each pixel
    near = (-depth).astype(np.float64)
    k = max(1, int(math.ceil(scale)))
    flat = img.reshape(-1, 3)
    zbuf = np.full(Hd * Wd, -np.inf)
    pts = []
    for ddx in range(k + 1):
        for ddy in range(k + 1):
            xi = np.clip(sx.astype(int) + ddx, 0, Wd - 1)
            yi = np.clip(sy.astype(int) + ddy, 0, Hd - 1)
            pix = yi * Wd + xi
            np.maximum.at(zbuf, pix, near)
            pts.append(pix)
    for pix in pts:
        win = near >= zbuf[pix]
        flat[pix[win]] = col[win]
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB').save(out)


def main():
    path, outdir = sys.argv[1], sys.argv[2]
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 2.0
    os.makedirs(outdir, exist_ok=True)
    vox, pal, ymin, ox, oz = load(path)
    print(f'{vox.shape} voxels, {len(pal)} states; origin at {ox},{oz}')
    missing = sorted({p for p in pal[1:] if block_colour(p, 'side') == (255, 0, 255)})
    if missing:
        print('no colour for:', missing[:20])
    render_top(vox, pal, ymin, os.path.join(outdir, 'render-top.png'), 2)
    render_oblique(vox, pal, ymin, ox, oz, os.path.join(outdir, 'render-nw.png'), scale)
    print('wrote', outdir)


if __name__ == '__main__':
    main()
