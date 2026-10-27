"""
A small voxel ray-caster for docs/terrain-immersion/PLAN.md (a proof; not part of the mod).

It draws a block world the way the game does (16x16 pixel textures, vanilla's per-face shading, per-vertex ambient
occlusion) plus a soft sun shadow and haze (what Lens adds), so a scene can be shown before and after a change.
Textures are read from the local reference copy of the game's assets at render time and are never written to the repo;
only the rendered pictures are.

  blocks:  0 air; kinds: FULL (cube), BOX (an axis box inside the cell: slabs, pebbles, carpets), CROSS (a plant's two
           crossed quads, cut out by alpha), CUT (a cube with cut-out holes: leaves).
"""
import os
import numpy as np
from PIL import Image

TEX_DIR = os.environ.get("MC_TEX", "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/block/")

AIR, FULL, BOX, CROSS, CUT, BOXC = 0, 1, 2, 3, 4, 5
# faces: 0 top(+y) 1 bottom(-y) 2 north(-z) 3 south(+z) 4 east(+x) 5 west(-x)
FACE_SHADE = np.array([1.0, 0.5, 0.8, 0.8, 0.62, 0.62], np.float32)


class Atlas:
    def __init__(self):
        self.tex = []        # (16,16,4) float32 0..1
        self.names = {}

    def load(self, name, tint=None, key=None):
        key = key or name
        if key in self.names:
            return self.names[key]
        im = Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")
        a = np.asarray(im, np.float32) / 255.0
        a = a[:16, :16]                      # first frame of an animated strip
        if tint is not None:
            a = a.copy()
            a[..., :3] *= np.asarray(tint, np.float32)
        return self.add(key, a)

    def add(self, key, arr):
        self.tex.append(arr.astype(np.float32))
        self.names[key] = len(self.tex) - 1
        return self.names[key]

    def grass_side(self, tint, key):
        base = np.asarray(Image.open(TEX_DIR + "grass_block_side.png").convert("RGBA"), np.float32) / 255.0
        ov = np.asarray(Image.open(TEX_DIR + "grass_block_side_overlay.png").convert("RGBA"), np.float32) / 255.0
        out = base.copy()
        m = ov[..., 3:4]
        out[..., :3] = base[..., :3] * (1 - m) + ov[..., :3] * np.asarray(tint, np.float32) * m
        return self.add(key, out)

    def build(self):
        return np.stack(self.tex)


class Defs:
    """Per block id: kind, six face textures, six face tints, box, flags."""
    def __init__(self, n=1024):
        self.kind = np.zeros(n, np.uint8)
        self.face = np.zeros((n, 6), np.int32)
        self.tint = np.ones((n, 6, 3), np.float32)
        self.box = np.tile(np.array([0, 0, 0, 1, 1, 1], np.float32), (n, 1))
        self.anchor_top = np.zeros(n, bool)      # side texture hangs from the box's top (a turf slab keeps its fringe)
        self.solid = np.zeros(n, bool)           # for ambient occlusion and shadows
        self.shadow = np.zeros(n, np.float32)    # how much sun a block blocks (0..1)
        self.tuft_tex = np.zeros(n, np.int32)    # BOXC: a plant standing in the same cell, on the box's top
        self.tuft_h = np.zeros(n, np.float32)
        self.tuft_y = np.zeros(n, np.float32)
        self.tuft_tint = np.ones((n, 3), np.float32)
        self.next = 1

    def add(self, kind, faces, tints=None, box=None, anchor_top=False, solid=None, shadow=None, tuft=None):
        i = self.next
        self.next += 1
        self.kind[i] = kind
        f = list(faces) if isinstance(faces, (list, tuple)) else [faces] * 6
        self.face[i] = f
        if tints is not None:
            t = tints if isinstance(tints, list) else [tints] * 6
            for k in range(6):
                if t[k] is not None:
                    self.tint[i, k] = t[k]
        if box is not None:
            self.box[i] = box
        self.anchor_top[i] = anchor_top
        self.solid[i] = (kind in (FULL, BOX)) if solid is None else solid
        self.shadow[i] = (1.0 if kind == FULL else 0.7 if kind == CUT else 0.5 if kind in (BOX, BOXC) else 0.0) if shadow is None else shadow
        if kind == BOXC:
            self.solid[i] = True
        if tuft is not None:
            self.tuft_tex[i], self.tuft_h[i], self.tuft_y[i] = tuft[0], tuft[1], tuft[2]
            if len(tuft) > 3:
                self.tuft_tint[i] = tuft[3]
        return i


def _unit(v):
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def render(blk, defs, atlas, cam, look, fov=70.0, size=(960, 540), sun=(0.4, 0.72, -0.6), fog=420.0,
           sky_top=(0.36, 0.58, 0.92), sky_hor=(0.78, 0.86, 0.95), fog_col=(0.74, 0.82, 0.90), max_steps=420, shadows=True,
           warm=(1.06, 1.0, 0.92), seed=3):
    W, H = size
    X, Y, Z = blk.shape
    cam = np.asarray(cam, np.float32)
    fwd = _unit(np.asarray(look, np.float32) - cam)
    right = _unit(np.cross(fwd, np.array([0, 1, 0], np.float32)))
    up = np.cross(right, fwd)
    aspect = W / H
    th = np.tan(np.radians(fov) / 2)
    xs = (np.arange(W, dtype=np.float32) + 0.5) / W * 2 - 1
    ys = 1 - (np.arange(H, dtype=np.float32) + 0.5) / H * 2
    px, py = np.meshgrid(xs * th * aspect, ys * th)
    d = fwd[None, None] + right[None, None] * px[..., None] + up[None, None] * py[..., None]
    d = _unit(d).reshape(-1, 3).astype(np.float32)
    N = d.shape[0]
    TEX = atlas.build()
    solid = defs.solid[blk]

    # per-pixel results
    hit_t = np.full(N, np.inf, np.float32)
    hit_v = np.zeros((N, 3), np.int32)
    hit_f = np.full(N, -1, np.int8)
    hit_rgb = np.zeros((N, 3), np.float32)
    hit_ao = np.zeros(N, bool)

    idx = np.arange(N)
    o = np.repeat(cam[None], N, 0)
    with np.errstate(divide="ignore", invalid="ignore"):
        inv = np.where(np.abs(d) < 1e-9, np.float32(1e9), 1.0 / d).astype(np.float32)
    # start voxel and DDA
    v = np.floor(o).astype(np.int32)
    stp = np.where(d >= 0, 1, -1).astype(np.int32)
    tdel = np.abs(inv)
    tmax = ((v + (stp > 0)) - o) * inv
    t_in = np.zeros(N, np.float32)
    ax_last = np.full(N, -1, np.int8)
    lim = np.array([X, Y, Z], np.int32)

    for it in range(max_steps):
        if idx.size == 0:
            break
        inb = np.all((v >= 0) & (v < lim), axis=1)
        # a ray that left the volume going further away is done
        gone = ~inb & (((v < 0) & (stp < 0)) | ((v >= lim) & (stp > 0))).any(1)
        keep = ~gone
        if not keep.all():
            idx, o, d, inv, v, stp, tdel, tmax, t_in, ax_last, inb = (a[keep] for a in (idx, o, d, inv, v, stp, tdel, tmax, t_in, ax_last, inb))
            if idx.size == 0:
                break
        ids = np.zeros(idx.size, np.uint16)
        vi = v[inb]
        ids[inb] = blk[vi[:, 0], vi[:, 1], vi[:, 2]]
        kind = defs.kind[ids]
        done = np.zeros(idx.size, bool)

        # ---- full cubes and cut-outs: the face the ray came through
        m = (kind == FULL) | (kind == CUT)
        m &= ax_last >= 0
        if m.any():
            k = np.nonzero(m)[0]
            a = ax_last[k].astype(np.int64)
            s = stp[k, a]
            face = np.where(a == 1, np.where(s < 0, 0, 1), np.where(a == 2, np.where(s > 0, 2, 3), np.where(s > 0, 5, 4))).astype(np.int64)
            p = o[k] + d[k] * t_in[k][:, None]
            fr = p - v[k]
            fr = np.clip(fr, 0, 0.9999)
            u, w = _uv(face, fr, np.zeros(k.size, np.float32), np.ones(k.size, np.float32), np.zeros(k.size, bool))
            rgb, alpha = _sample(TEX, defs, ids[k], face, u, w)
            ok = alpha > 0.5
            if ok.any():
                kk = k[ok]
                _store(hit_t, hit_v, hit_f, hit_rgb, hit_ao, idx[kk], t_in[kk], v[kk], face[ok], rgb[ok], True)
                done[kk] = True

        # ---- boxes (slabs, carpets, pebbles), and a plant standing in the same cell on top of one (a turf slab's tuft)
        m = (kind == BOX) | (kind == BOXC)
        if m.any():
            k = np.nonzero(m)[0]
            bx = defs.box[ids[k]]
            lo = v[k] + bx[:, :3]
            hi = v[k] + bx[:, 3:]
            t1 = (lo - o[k]) * inv[k]
            t2 = (hi - o[k]) * inv[k]
            tn = np.minimum(t1, t2)
            tf = np.maximum(t1, t2)
            tnear = tn.max(1)
            tfar = tf.min(1)
            hitm = (tnear <= tfar) & (tfar >= t_in[k] - 1e-4) & (tnear >= t_in[k] - 1e-4)
            cand_t = np.full(k.size, np.inf, np.float32)
            cand_f = np.zeros(k.size, np.int64)
            cand_rgb = np.zeros((k.size, 3), np.float32)
            cand_ao = np.ones(k.size, bool)
            if hitm.any():
                kh = np.nonzero(hitm)[0]
                a_ = np.argmax(tn[kh], 1)
                s_ = stp[k[kh], a_]
                face = np.where(a_ == 1, np.where(s_ < 0, 0, 1), np.where(a_ == 2, np.where(s_ > 0, 2, 3), np.where(s_ > 0, 5, 4))).astype(np.int64)
                p = o[k[kh]] + d[k[kh]] * tnear[kh][:, None]
                fr = np.clip(p - v[k[kh]], 0, 0.9999)
                b2 = bx[kh]
                u, w = _uv(face, fr, b2[:, 1], b2[:, 4], defs.anchor_top[ids[k[kh]]])
                rgb, alpha = _sample(TEX, defs, ids[k[kh]], face, u, w)
                ok = alpha > 0.5
                kk = kh[ok]
                cand_t[kk] = tnear[kk]
                cand_f[kk] = face[ok]
                cand_rgb[kk] = rgb[ok]
            tuft = defs.kind[ids[k]] == BOXC
            if tuft.any():
                kt = np.nonzero(tuft)[0]
                tt_, rgb_ = _cross(o[k[kt]], d[k[kt]], v[k[kt]], t_in[k[kt]], TEX, defs.tuft_tex[ids[k[kt]]], defs.tuft_h[ids[k[kt]]],
                                   defs.tuft_y[ids[k[kt]]], defs.tuft_tint[ids[k[kt]]])
                better = tt_ < cand_t[kt]
                cand_t[kt[better]] = tt_[better]
                cand_f[kt[better]] = 0
                cand_rgb[kt[better]] = rgb_[better]
                cand_ao[kt[better]] = False
            got = np.isfinite(cand_t)
            if got.any():
                kk = k[got]
                # faces of boxes get ambient occlusion; a plant's quads do not
                for ao_flag in (True, False):
                    sel = got & (cand_ao == ao_flag)
                    if sel.any():
                        _store(hit_t, hit_v, hit_f, hit_rgb, hit_ao, idx[k[sel]], cand_t[sel], v[k[sel]],
                               cand_f[sel] if ao_flag else np.zeros(sel.sum(), np.int64), cand_rgb[sel], ao_flag)
                done[kk] = True

        # ---- crossed plants: two diagonal quads
        m = (kind == CROSS) & ~done
        if m.any():
            k = np.nonzero(m)[0]
            bx = defs.box[ids[k]]
            best_t, best_rgb = _cross(o[k], d[k], v[k], t_in[k], TEX, defs.face[ids[k], 0], bx[:, 4], np.zeros(k.size, np.float32), defs.tint[ids[k], 0])
            ok = np.isfinite(best_t)
            if ok.any():
                kk = k[ok]
                _store(hit_t, hit_v, hit_f, hit_rgb, hit_ao, idx[kk], best_t[ok], v[kk], np.zeros(kk.size, np.int64), best_rgb[ok], False)
                done[kk] = True

        # ---- advance
        if done.any():
            keep = ~done
            idx, o, d, inv, v, stp, tdel, tmax, t_in, ax_last = (a[keep] for a in (idx, o, d, inv, v, stp, tdel, tmax, t_in, ax_last))
            if idx.size == 0:
                break
        a = np.argmin(tmax, 1)
        r = np.arange(idx.size)
        t_in = tmax[r, a].copy()
        tmax[r, a] += tdel[r, a]
        v[r, a] += stp[r, a]
        ax_last = a.astype(np.int8)

    # ---- shading
    sun = _unit(np.asarray(sun, np.float32))
    hit = np.isfinite(hit_t)
    col = np.zeros((N, 3), np.float32)
    # recompute the ray directions for every pixel (d was filtered while marching)
    D = _unit((fwd[None, None] + right[None, None] * px[..., None] + up[None, None] * py[..., None]).reshape(-1, 3)).astype(np.float32)
    sky = np.asarray(sky_hor, np.float32)[None] * (1 - np.clip(D[:, 1:2] * 1.8, 0, 1)) + np.asarray(sky_top, np.float32)[None] * np.clip(D[:, 1:2] * 1.8, 0, 1)
    glow = np.clip((D @ sun - 0.92) / 0.08, 0, 1)[:, None] ** 2
    sky = sky + glow * np.array([0.55, 0.45, 0.25], np.float32)
    col[:] = sky
    h = np.nonzero(hit)[0]
    if h.size:
        f = hit_f[h].astype(np.int64)
        t = hit_t[h]
        vv = hit_v[h]
        p = cam[None] + D[h] * t[:, None]
        base = hit_rgb[h] * FACE_SHADE[np.where(f < 0, 0, f)][:, None]
        # plants take the top light
        base = np.where((f == 0)[:, None] | ~hit_ao[h][:, None], hit_rgb[h] * (0.9 + 0.1 * FACE_SHADE[np.where(f < 0, 0, f)][:, None]), base)
        ao = np.ones(h.size, np.float32)
        am = hit_ao[h] & (f >= 0)
        if am.any():
            ao[am] = _ao(solid, vv[am], f[am], p[am])
        lit = base * ao[:, None]
        if shadows:
            sh = _shadow(defs, blk, p, hit_f[h], D[h], sun, max_steps=90)
            n_dot = np.array([1.0, 0.0, 0.55, 0.55, 0.7, 0.7], np.float32)[np.where(f < 0, 0, f)]
            # faces turned from the sun are in shade already; a cast shadow cools and darkens the rest
            lit = lit * (1 - 0.42 * sh[:, None]) * np.where(sh[:, None] > 0.5, np.array([0.93, 0.97, 1.08], np.float32), np.array(warm, np.float32))
        else:
            lit = lit * np.array(warm, np.float32)
        fg = 1 - np.exp(-t / fog)
        fg = np.clip(fg, 0, 1)[:, None]
        # sky haze toward the horizon colour
        lit = lit * (1 - fg) + np.asarray(fog_col, np.float32)[None] * fg
        col[h] = lit
    img = col.reshape(H, W, 3)
    # a light bloom/vignette so it reads as a lit picture, not a diagram
    yy, xx = np.mgrid[0:H, 0:W]
    vig = 1 - 0.22 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) ** 1.2
    img = np.clip(img * vig[..., None], 0, 1)
    return (img * 255 + 0.5).astype(np.uint8)


def _cross(o, d, v, t_in, TEX, tex, ph, y0, tint):
    """Two crossed quads in a cell (x = z and x + z = 1), height ph from y0, cut out by alpha. Returns (t, rgb) per ray."""
    n = o.shape[0]
    best_t = np.full(n, np.inf, np.float32)
    best_rgb = np.zeros((n, 3), np.float32)
    for plane in (0, 1):
        ox_, oz_ = o[:, 0] - v[:, 0], o[:, 2] - v[:, 2]
        dx_, dz_ = d[:, 0], d[:, 2]
        with np.errstate(divide="ignore", invalid="ignore"):
            if plane == 0:
                den = dx_ - dz_
                tt = np.where(np.abs(den) < 1e-7, np.inf, -(ox_ - oz_) / den)
            else:
                den = dx_ + dz_
                tt = np.where(np.abs(den) < 1e-7, np.inf, (1 - ox_ - oz_) / den)
        tt = tt.astype(np.float32)
        tt[~np.isfinite(tt)] = np.inf
        p = o + d * np.where(np.isfinite(tt), tt, 0)[:, None]
        fr = p - v
        valid = np.isfinite(tt) & (tt >= t_in - 1e-4) & (fr[:, 0] >= 0) & (fr[:, 0] <= 1) & (fr[:, 2] >= 0) & (fr[:, 2] <= 1) \
            & (fr[:, 1] >= y0) & (fr[:, 1] <= y0 + ph) & (tt < best_t)
        if valid.any():
            kv = np.nonzero(valid)[0]
            u = np.clip(fr[kv, 0], 0, 0.9999)
            w = np.clip(1 - (fr[kv, 1] - y0[kv]) / ph[kv], 0, 0.9999)
            px = TEX[tex[kv], (w * 16).astype(np.int32), (u * 16).astype(np.int32)]
            ok = px[:, 3] > 0.5
            kv = kv[ok]
            best_t[kv] = tt[kv]
            best_rgb[kv] = px[ok][:, :3] * tint[kv]
    return best_t, best_rgb


def _uv(face, fr, y0, y1, anchor_top):
    top = (face <= 1)
    u = np.where(top, fr[:, 0], np.where(face >= 4, fr[:, 2], fr[:, 0]))
    # south and east/west run the other way so a texture reads upright
    u = np.where(face == 3, 1 - u, u)
    u = np.where(face == 5, 1 - u, u)
    w = np.where(top, fr[:, 2], 1 - fr[:, 1])
    # a box's side shows the part of the texture its height covers: from its top down when anchored, else the bottom part
    side = ~top
    w = np.where(side & anchor_top, (y1 - fr[:, 1]), w)
    return np.clip(u, 0, 0.9999), np.clip(w, 0, 0.9999)


def _sample(TEX, defs, ids, face, u, w):
    ti = defs.face[ids, face]
    ix = (u * 16).astype(np.int32)
    iy = (w * 16).astype(np.int32)
    px = TEX[ti, iy, ix]
    rgb = px[:, :3] * defs.tint[ids, face]
    return rgb, px[:, 3]


def _store(hit_t, hit_v, hit_f, hit_rgb, hit_ao, pix, t, v, face, rgb, ao):
    hit_t[pix] = t
    hit_v[pix] = v
    hit_f[pix] = face
    hit_rgb[pix] = rgb
    hit_ao[pix] = ao


_AXES = {0: (0, 1, 0), 1: (0, -1, 0), 2: (0, 0, -1), 3: (0, 0, 1), 4: (1, 0, 0), 5: (-1, 0, 0)}


def _ao(solid, v, f, p):
    """Vanilla-style vertex AO, bilinear over the face. v: hit voxel, f: face, p: hit point."""
    X, Y, Z = solid.shape
    n = np.array([_AXES[i] for i in range(6)], np.int32)[f]
    # tangents
    ta = np.where((f <= 1)[:, None], np.array([1, 0, 0]), np.where((f <= 3)[:, None], np.array([1, 0, 0]), np.array([0, 0, 1]))).astype(np.int32)
    tb = np.where((f <= 1)[:, None], np.array([0, 0, 1]), np.array([0, 1, 0])).astype(np.int32)
    base = v + n

    def occ(q):
        ok = np.all((q >= 0) & (q < np.array([X, Y, Z])), axis=1)
        out = np.zeros(q.shape[0], bool)
        qq = q[ok]
        out[ok] = solid[qq[:, 0], qq[:, 1], qq[:, 2]]
        return out.astype(np.float32)

    fr = p - v
    ua = np.sum(fr * ta, 1)
    ub = np.sum(fr * tb, 1)
    ua = np.clip(ua, 0, 1)
    ub = np.clip(ub, 0, 1)
    vals = {}
    for sa in (-1, 1):
        for sb in (-1, 1):
            s1 = occ(base + sa * ta)
            s2 = occ(base + sb * tb)
            c = occ(base + sa * ta + sb * tb)
            o = np.where((s1 > 0) & (s2 > 0), 3.0, s1 + s2 + c)
            vals[(sa, sb)] = 1.0 - 0.2 * o
    wa1, wb1 = ua, ub
    return (vals[(-1, -1)] * (1 - wa1) * (1 - wb1) + vals[(1, -1)] * wa1 * (1 - wb1) + vals[(-1, 1)] * (1 - wa1) * wb1 + vals[(1, 1)] * wa1 * wb1).astype(np.float32)


def _shadow(defs, blk, p, face, D, sun, max_steps=90):
    """1 where the sun is blocked: a short voxel march toward the sun from just off the surface."""
    X, Y, Z = blk.shape
    n = len(p)
    nrm = np.array([_AXES[i] for i in range(6)], np.float32)[np.where(face < 0, 0, face)]
    o = p + nrm * 0.02 + np.array([0, 0.02, 0], np.float32)
    s = np.broadcast_to(sun, (n, 3)).astype(np.float32)
    with np.errstate(divide="ignore", invalid="ignore"):
        inv = np.where(np.abs(s) < 1e-9, np.float32(1e9), 1.0 / s).astype(np.float32)
    v = np.floor(o).astype(np.int32)
    stp = np.where(s >= 0, 1, -1).astype(np.int32)
    tdel = np.abs(inv)
    tmax = ((v + (stp > 0)) - o) * inv
    res = np.zeros(n, np.float32)
    alive = np.arange(n)
    lim = np.array([X, Y, Z], np.int32)
    for _ in range(max_steps):
        if alive.size == 0:
            break
        inb = np.all((v >= 0) & (v < lim), axis=1)
        out_up = (v[:, 1] >= Y) & (stp[:, 1] > 0)
        dead = ~inb & (out_up | (v < 0).any(1) | (v >= lim).any(1))
        # leaving the sides of the picture's world: treat as lit
        ids = np.zeros(alive.size, np.uint16)
        vi = v[inb]
        ids[inb] = blk[vi[:, 0], vi[:, 1], vi[:, 2]]
        blocking = defs.shadow[ids]
        blocked = blocking > 0.45
        res[alive[blocked]] = blocking[blocked]
        keep = ~(blocked | dead)
        if not keep.all():
            alive, v, stp, tdel, tmax = alive[keep], v[keep], stp[keep], tdel[keep], tmax[keep]
        if alive.size == 0:
            break
        a = np.argmin(tmax, 1)
        r = np.arange(alive.size)
        tmax[r, a] += tdel[r, a]
        v[r, a] += stp[r, a]
    return res
