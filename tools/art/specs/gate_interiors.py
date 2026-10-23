"""Gate interiors (docs/modules/gates.md "Interiors"): the interior blocks' textures and content, and the offline preview.

    python3 tools/art/build_all.py gate_interiors        textures of the gate_* interior blocks
    python3 tools/art/specs/gate_interiors.py content    blockstates, models, item definitions, loot, lang, mining tags
    python3 tools/art/specs/gate_interiors.py preview DIR [filters]
        renders every interior the GameTests dumped (run/interiors/*.json: one floor and the Lord's floor per theme):
        a cut-away plan from above and eye-level shots from the cameras the dump names (the first room facing the way on,
        the biggest set piece, the door to the Rest Shrine, the antechamber, the arena seen from its door), with the real
        block textures, block light flood-filled like the game's and the theme's fog.

The eye-level shots are a numpy voxel ray-caster (Amanatides-Woo DDA): full cubes, boxes (slabs, carpets, lanterns),
crossed planes (chains, plants), alpha-tested cut-outs and translucent glass and water composited front to back.
"""
from __future__ import annotations

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge")
RUN = os.path.join(ROOT, "run/interiors")
BIOMES = os.path.join(RES, "data/awaken/worldgen/biome")

# light our blocks give (GatesRegistry)
OUR_LIGHT = {"gate_glowcap": 12, "gate_glowcap_gills": 9, "gate_ember_ore": 7, "gate_sun_tile": 6, "gate_brood_cell": 6,
             "gate_royal_jelly": 12, "gate_paper_lantern": 15, "gate_frost_window": 9, "gate_mirror": 3, "gate_lab_panel": 9,
             "gate_lab_rack": 5, "gate_void_glass": 3, "gate_signal_lamp": 14, "gate_sky": 6, "rest_shrine": 13, "gate_seal": 7, "gate_cache": 4,
             "gate_bars": 6, "gate_entrance": 8, "lost_lantern": 12, "gate_updraft": 6, "gate_rune": 11, "ruin_glyph": 6,
             "gate_frame_cracked": 2}


# =====================================================================================================================
# Preview: a voxel ray-caster

def _np():
    import numpy as np
    return np


FACES = ("up", "down", "north", "south", "west", "east")  # +y -y -z +z -x +x
FACE_SHADE = (1.0, 0.5, 0.8, 0.8, 0.6, 0.6)


class Looks:
    """Block looks as arrays: textures [n, 6, 16, 16, 4], shape kind, box bounds, emission, opacity, translucency."""

    def __init__(self):
        np = _np()
        import structures as S
        self.S = S
        self.keys = [("air", "")]
        self.index = {("air", ""): 0}
        self.tex = [np.zeros((6, 16, 16, 4), np.float32)]
        self.kind = [0]          # 0 air, 1 cube, 2 box, 3 cross, 4 liquid
        self.box = [(0, 1, 0, 1, 0, 1)]
        self.emit = [0]
        self.opaque = [False]
        self.bright = [False]
        self.translucent = [False]

    def id(self, name, props):
        key = (name, props)
        i = self.index.get(key)
        if i is not None:
            return i
        np = _np()
        S = self.S
        if name == "__cap__":
            # the cut surface of a cut-away plan: flat dark grey, so walls read as walls from above
            arr = np.zeros((6, 16, 16, 4), np.float32)
            arr[..., :3] = 0.16
            arr[..., 3] = 1.0
            i = len(self.keys)
            self.keys.append(key)
            self.index[key] = i
            for lst, v in ((self.tex, arr), (self.kind, 1), (self.box, (0, 1, 0, 1, 0, 1)), (self.emit, 0), (self.opaque, True),
                           (self.bright, True), (self.translucent, False)):
                lst.append(v)
            return i
        shape, faces = S.block_look(name, props)
        ns, path = S._split(name)
        arr = np.zeros((6, 16, 16, 4), np.float32)
        for fi, f in enumerate(FACES):
            arr[fi] = np.asarray(faces[f].convert("RGBA"), np.float32) / 255.0
        kind, box = 1, (0, 1, 0, 1, 0, 1)
        if shape == "liquid":
            kind = 4
        elif shape == "cross":
            kind = 3
            if path in ("rail", "powered_rail", "detector_rail", "activator_rail"):
                kind, box = 2, (0, 0.06, 0, 1, 0, 1)
        elif isinstance(shape, tuple):
            kind = 2
            _, y0, y1, x0, x1, z0, z1 = shape
            box = (y0, y1, x0, x1, z0, z1)
        if path.endswith("carpet"):
            kind, box = 2, (0, 0.0625, 0, 1, 0, 1)
        if path == "snow":
            kind, box = 2, (0, 0.125 * int(dict(kv.split("=") for kv in props.split(",") if "=" in kv).get("layers", "1")), 0, 1, 0, 1)
        if path == "gate_paper_lantern":
            kind, box = 2, (0.06, 0.88, 0.19, 0.81, 0.19, 0.81)
        if path == "gate_bell":
            kind, box = 2, (0.25, 0.9, 0.25, 0.75, 0.25, 0.75)
        if path in ("light", "structure_void", "barrier"):
            kind = 0  # invisible: a light the game draws as nothing
        a = arr[..., 3]
        partial = ((a > 0.12) & (a < 0.88)).mean()
        translucent = kind in (1, 2) and partial > 0.25
        opaque = kind == 1 and a.min() > 0.9
        if ns == "minecraft":
            em = S._emission(name)
            if "copper_lantern" in path or path in ("lava_cauldron",):
                em = 15
            if "copper_torch" in path:
                em = 14
            if path.startswith("cave_vines") and "berries=true" in props:
                em = 14
            if path == "sea_pickle":
                em = 3 + 3 * int(dict(kv.split("=") for kv in props.split(",") if "=" in kv).get("pickles", "1"))
            if path == "light":
                em = int(dict(kv.split("=") for kv in props.split(",") if "=" in kv).get("level", "15"))
            if path.endswith("candle") and "lit=true" not in props:
                em = 0
            if path in ("waxed_copper_bulb", "copper_bulb") and "lit=false" in props:
                em = 0
            bright = S._full_bright(name) or "copper_lantern" in path
        else:
            em = OUR_LIGHT.get(path, S.our_light(path))
            bright = path in ("gate_sky", "gate_cloud", "gate_mirror", "gate_signal_lamp", "gate_royal_jelly", "gate_paper_lantern", "rest_shrine") or em >= 12
        i = len(self.keys)
        self.keys.append(key)
        self.index[key] = i
        self.tex.append(arr)
        self.kind.append(kind)
        self.box.append(box)
        self.emit.append(em)
        self.opaque.append(opaque)
        self.bright.append(bright)
        self.translucent.append(translucent)
        return i

    def finish(self):
        np = _np()
        self.T = np.stack(self.tex)
        self.K = np.array(self.kind, np.int8)
        self.B = np.array(self.box, np.float32)
        self.E = np.array(self.emit, np.int16)
        self.O = np.array(self.opaque, bool)
        self.BR = np.array(self.bright, bool)
        self.TR = np.array(self.translucent, bool)


class Volume:
    """A dumped floor as a dense grid of look ids, with its block light."""

    def __init__(self, data, looks):
        np = _np()
        import base64
        ox, oy, oz = data["origin"]
        nx, ny, nz = data["dims"]
        raw = np.frombuffer(base64.b64decode(data["grid"]), "<u2").reshape(ny, nz, nx).transpose(2, 0, 1)
        lut = np.array([looks.id(n, p) for n, p in data["palette"]], np.int32)
        # one block of air round it so rays can enter
        g = np.zeros((nx + 2, ny + 2, nz + 2), np.int32)
        g[1:-1, 1:-1, 1:-1] = lut[raw]
        self.x0, self.y0, self.z0 = ox - 1, oy - 1, oz - 1
        self.nx, self.ny, self.nz = g.shape
        looks.finish()
        self.g = g
        self.looks = looks
        self.light = self._light()

    def _light(self):
        np = _np()
        L = self.looks
        emit = L.E[self.g].astype(np.int16)
        opq = L.O[self.g]
        lv = emit.copy()
        for _ in range(15):
            src = np.where(opq & (emit == 0), 0, lv)
            nb = np.zeros_like(lv)
            nb[1:] = np.maximum(nb[1:], src[:-1]); nb[:-1] = np.maximum(nb[:-1], src[1:])
            nb[:, 1:] = np.maximum(nb[:, 1:], src[:, :-1]); nb[:, :-1] = np.maximum(nb[:, :-1], src[:, 1:])
            nb[:, :, 1:] = np.maximum(nb[:, :, 1:], src[:, :, :-1]); nb[:, :, :-1] = np.maximum(nb[:, :, :-1], src[:, :, 1:])
            lv = np.where(opq, lv, np.maximum(lv, nb - 1))
        return np.clip(lv, 0, 15)

    def cut(self, y_abs, box=None):
        """A copy with everything above y_abs removed (cut-away plans), cropped to box = (x0, z0, x1, z1) floor-local."""
        v = object.__new__(Volume)
        v.__dict__.update(self.__dict__)
        g, light = self.g, self.light
        if box is not None:
            ax0, az0 = max(0, box[0] - self.x0), max(0, box[1] - self.z0)
            ax1, az1 = min(self.nx, box[2] - self.x0 + 1), min(self.nz, box[3] - self.z0 + 1)
            g, light = g[ax0:ax1, :, az0:az1], light[ax0:ax1, :, az0:az1]
            v.x0, v.z0 = self.x0 + ax0, self.z0 + az0
            v.nx, v.nz = g.shape[0], g.shape[2]
        v.g = g.copy()
        v.light = light
        k = max(0, y_abs - self.y0 + 1)
        if 0 < k < v.g.shape[1]:
            cap = self.looks.id("__cap__", "")
            self.looks.finish()
            top = v.g[:, k - 1, :]
            cut = (v.g[:, k, :] > 0) & (top > 0)
            top[cut] = cap
        v.g[:, k:, :] = 0
        return v


def _brightness(level):
    """The game's block-light curve and warm lightmap tint at the default brightness (50%): N -> N x 3 multipliers."""
    np = _np()
    f = np.asarray(level, np.float32) / 15.0
    b = f / (4.0 - 3.0 * f)
    rgb = np.stack([b, b * ((b * 0.6 + 0.4) * 0.6 + 0.4), b * (b * b * 0.6 + 0.4)], -1)
    rgb = rgb + ((1 - (1 - rgb) ** 4) - rgb) * 0.5
    return np.clip(0.03 + 0.97 * rgb, 0, 1)


def raycast(vol, origins, dirs, fog_rgb, fog_start, fog_end, max_steps=420):
    """Casts rays (N x 3 origins and directions, voxel units relative to the volume) and returns N x 3 colours."""
    np = _np()
    L = vol.looks
    N = origins.shape[0]
    dims = np.array([vol.nx, vol.ny, vol.nz])
    d = dirs / np.linalg.norm(dirs, axis=1, keepdims=True)
    d = np.where(np.abs(d) < 1e-9, 1e-9, d)
    o = origins.copy()
    # advance rays from outside to the grid
    inv = 1.0 / d
    t0 = np.maximum.reduce([np.minimum((0 - o[:, i]) * inv[:, i], (dims[i] - o[:, i]) * inv[:, i]) for i in range(3)])
    t1 = np.minimum.reduce([np.maximum((0 - o[:, i]) * inv[:, i], (dims[i] - o[:, i]) * inv[:, i]) for i in range(3)])
    tstart = np.maximum(t0, 0) + 1e-4
    alive = t1 > tstart
    p = o + d * tstart[:, None]
    vox = np.floor(p).astype(np.int64)
    vox = np.clip(vox, 0, dims - 1)
    step = np.sign(d).astype(np.int64)
    tdelta = np.abs(inv)
    nextb = vox + (step > 0)
    tmax = (nextb - o) * inv
    t = tstart.copy()
    last_axis = np.full(N, -1, np.int64)
    col = np.zeros((N, 3), np.float32)
    trans = np.ones(N, np.float32)
    dist = np.full(N, np.inf, np.float32)
    idx = np.nonzero(alive)[0]
    first = np.ones(N, bool)
    for _ in range(max_steps):
        if idx.size == 0:
            break
        vx, vy, vz = vox[idx, 0], vox[idx, 1], vox[idx, 2]
        inb = (vx >= 0) & (vy >= 0) & (vz >= 0) & (vx < vol.nx) & (vy < vol.ny) & (vz < vol.nz)
        idx, vx, vy, vz = idx[inb], vx[inb], vy[inb], vz[inb]
        if idx.size == 0:
            break
        gid = vol.g[vx, vy, vz]
        solid = L.K[gid] > 0
        if solid.any():
            hit_i = idx[solid]
            _shade_hits(vol, hit_i, gid[solid], o, d, t, tmax, vox, last_axis, col, trans, dist, first, fog_rgb, fog_start, fog_end)
        first[idx] = False
        # step every live ray to its next voxel
        ax = np.argmin(tmax[idx], axis=1)
        rows = idx
        t[rows] = tmax[rows, ax]
        vox[rows, ax] += step[rows, ax]
        tmax[rows, ax] += tdelta[rows, ax]
        last_axis[rows] = ax
        keep = (trans[idx] > 0.03) & (t[idx] < fog_end * 1.4)
        idx = idx[keep]
    # what nothing stopped: fog
    col += trans[:, None] * np.array(fog_rgb, np.float32)[None, :]
    return np.clip(col, 0, 1)


def _shade_hits(vol, rays, gid, o, d, t, tmax, vox, last_axis, col, trans, dist, first, fog_rgb, fog_start, fog_end):
    np = _np()
    L = vol.looks
    kind = L.K[gid]
    tin = t[rays]
    tout = np.min(tmax[rays], axis=1)
    vx = vox[rays].astype(np.float64)
    hit = np.zeros(rays.size, bool)
    th = np.zeros(rays.size)
    face = np.zeros(rays.size, np.int64)
    u = np.zeros(rays.size)
    v = np.zeros(rays.size)
    dd = d[rays]
    oo = o[rays]
    # full cubes: the face we entered through
    cube = (kind == 1) & ~first[rays]
    if cube.any():
        la = last_axis[rays][cube]
        sgn = np.sign(dd[cube, :][np.arange(cube.sum()), la])
        f = np.where(la == 1, np.where(sgn > 0, 1, 0), np.where(la == 2, np.where(sgn > 0, 2, 3), np.where(sgn > 0, 4, 5)))
        ph = oo[cube] + dd[cube] * tin[cube][:, None]
        loc = ph - vx[cube]
        uu, vv = _uv(f, loc)
        hit[cube], th[cube], face[cube], u[cube], v[cube] = True, tin[cube], f, uu, vv
    # boxes: slab test against the sub-box inside the voxel
    bx = kind == 2
    if bx.any():
        b = L.B[gid[bx]]
        lo = np.stack([b[:, 2], b[:, 0], b[:, 4]], 1) + vx[bx]
        hi = np.stack([b[:, 3], b[:, 1], b[:, 5]], 1) + vx[bx]
        ob, db = oo[bx], dd[bx]
        ta = (lo - ob) / db
        tb = (hi - ob) / db
        tn = np.minimum(ta, tb)
        tf = np.maximum(ta, tb)
        tnear = tn.max(1)
        tfar = tf.min(1)
        ok = (tnear <= tfar) & (tfar >= tin[bx]) & (tnear <= tout[bx] + 1e-6)
        tnear = np.maximum(tnear, tin[bx])
        axn = tn.argmax(1)
        sg = np.sign(db[np.arange(db.shape[0]), axn])
        f = np.where(axn == 1, np.where(sg > 0, 1, 0), np.where(axn == 2, np.where(sg > 0, 2, 3), np.where(sg > 0, 4, 5)))
        loc = ob + db * tnear[:, None] - vx[bx]
        uu, vv = _uv(f, loc)
        hit[bx], th[bx], face[bx], u[bx], v[bx] = ok, tnear, f, uu, vv
    # crossed planes
    cr = kind == 3
    if cr.any():
        ob, db = oo[cr] - vx[cr], dd[cr]
        best = np.full(ob.shape[0], np.inf)
        bu = np.zeros(ob.shape[0])
        bv = np.zeros(ob.shape[0])
        for sgn in (1.0, -1.0):
            # plane x - z = 0 (sgn 1) and x + z = 1 (sgn -1)
            if sgn > 0:
                den = db[:, 0] - db[:, 2]
                tt = -(ob[:, 0] - ob[:, 2]) / np.where(np.abs(den) < 1e-9, 1e-9, den)
            else:
                den = db[:, 0] + db[:, 2]
                tt = (1 - ob[:, 0] - ob[:, 2]) / np.where(np.abs(den) < 1e-9, 1e-9, den)
            ph = ob + db * tt[:, None]
            inside = (ph[:, 0] >= 0) & (ph[:, 0] <= 1) & (ph[:, 1] >= 0) & (ph[:, 1] <= 1) & (ph[:, 2] >= 0) & (ph[:, 2] <= 1) & (tt >= tin[cr] - 1e-6)
            better = inside & (tt < best)
            best = np.where(better, tt, best)
            bu = np.where(better, ph[:, 0], bu)
            bv = np.where(better, ph[:, 1], bv)
        hit[cr], th[cr], face[cr], u[cr], v[cr] = np.isfinite(best), np.where(np.isfinite(best), best, 0), 2, bu, bv
    # water and lava: the surface as a tinted veil
    lq = kind == 4
    if lq.any():
        ph = oo[lq] + dd[lq] * tin[lq][:, None]
        loc = ph - vx[lq]
        hit[lq], th[lq], face[lq] = True, tin[lq], 0
        u[lq], v[lq] = loc[:, 0], loc[:, 2]
    if not hit.any():
        return
    sel = np.nonzero(hit)[0]
    r = rays[sel]
    g = gid[sel]
    tx = np.clip((u[sel] * 16).astype(np.int64), 0, 15)
    ty = np.clip(((1 - v[sel]) * 16).astype(np.int64), 0, 15)
    rgba = L.T[g, face[sel], ty, tx]
    a = rgba[:, 3].copy()
    k = L.K[g]
    a = np.where(k == 4, 0.55, a)
    tr = L.TR[g] | (k == 4)
    a = np.where(tr, a, np.where(a > 0.5, 1.0, 0.0))
    # light: the cell the ray came from for cubes, the block's own cell (or above it) for everything else
    vv = vox[r].copy()
    la = last_axis[r]
    back = np.zeros_like(vv)
    rows = np.arange(r.size)
    valid = la >= 0
    back[rows[valid], la[valid]] = -np.sign(d[r[valid], la[valid]]).astype(np.int64)
    lc = np.where((k == 1)[:, None], vv + back, vv)
    lc = np.clip(lc, 0, np.array([vol.nx - 1, vol.ny - 1, vol.nz - 1]))
    lvl = vol.light[lc[:, 0], lc[:, 1], lc[:, 2]]
    above = np.clip(vv + np.array([0, 1, 0]), 0, np.array([vol.nx - 1, vol.ny - 1, vol.nz - 1]))
    lvl = np.where(k == 1, lvl, np.maximum(lvl, vol.light[above[:, 0], above[:, 1], above[:, 2]]))
    br = _brightness(lvl.astype(np.float32))
    shade = np.array(FACE_SHADE, np.float32)[face[sel]]
    bright = L.BR[g]
    lit = np.where(bright[:, None], 1.0, br * shade[:, None])
    c = rgba[:, :3] * lit
    # fog by distance
    dist_ = th[sel]
    fz = np.clip((dist_ - fog_start) / max(1e-3, fog_end - fog_start), 0, 1)
    c = c * (1 - fz[:, None]) + np.array(fog_rgb, np.float32)[None, :] * fz[:, None]
    w = trans[r] * a
    col[r] += c * w[:, None]
    trans[r] = trans[r] * (1 - a)


def _uv(face, loc):
    np = _np()
    # MC's default UVs follow the block position: sides use the horizontal coordinate and height, up/down x and z
    u = np.where(face <= 1, loc[:, 0], np.where(face <= 3, np.where(face == 2, 1 - loc[:, 0], loc[:, 0]),
                                                np.where(face == 4, loc[:, 2], 1 - loc[:, 2])))
    v = np.where(face <= 1, 1 - loc[:, 2], loc[:, 1])
    return np.clip(u, 0, 0.999), np.clip(v, 0, 0.999)


def fog_of(theme):
    p = os.path.join(BIOMES, f"gate_{theme}.json")
    rgb, start, end = (0.1, 0.1, 0.12), 4.0, 60.0
    try:
        a = json.load(open(p))["attributes"]
        h = a.get("minecraft:visual/fog_color", "#1a1a20").lstrip("#")
        rgb = tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
        start = float(a.get("minecraft:visual/fog_start_distance", start))
        end = float(a.get("minecraft:visual/fog_end_distance", end))
    except (OSError, KeyError, ValueError):
        pass
    return rgb, start, end


def eye_shot(vol, cam, fog, W=960, H=540, fov=70):
    """A first-person view: cam pos is floor-local (x, eye y, z), yaw and pitch in Minecraft degrees."""
    np = _np()
    yaw, pitch = math.radians(cam["yaw"]), math.radians(cam["pitch"])
    f = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([-math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(right, f)
    tv = math.tan(math.radians(fov) / 2)
    th = tv * W / H
    ii, jj = np.meshgrid(np.arange(W), np.arange(H))
    uu = ((ii + 0.5) / W * 2 - 1) * th
    vv = (1 - (jj + 0.5) / H * 2) * tv
    dirs = f[None, None, :] + uu[..., None] * right[None, None, :] + vv[..., None] * up[None, None, :]
    dirs = dirs.reshape(-1, 3)
    pos = np.array(cam["pos"], np.float64) - np.array([vol.x0, vol.y0, vol.z0])
    origins = np.repeat(pos[None, :], dirs.shape[0], 0)
    rgb, fs, fe = fog
    out = raycast(vol, origins, dirs, rgb, fs, fe)
    return (out.reshape(H, W, 3) * 255).astype(np.uint8)


def plan_shot(vol, fog, yaw_deg=45, pitch_deg=58, px_per_block=4.0, cut=None, box=None):
    """An orthographic cut-away from above (no fog, a little ambient so unlit floors still read)."""
    np = _np()
    v = vol.cut(cut if cut is not None else 10 ** 6, box)
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    f = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([-math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(right, f)
    # project the volume's corners to size the image
    cs = np.array([[x, y, z] for x in (0, v.nx) for y in (0, v.ny) for z in (0, v.nz)], np.float64)
    pu, pv = cs @ right, cs @ up
    W = int((pu.max() - pu.min()) * px_per_block)
    H = int((pv.max() - pv.min()) * px_per_block)
    ii, jj = np.meshgrid(np.arange(W), np.arange(H))
    a = pu.min() + (ii + 0.5) / px_per_block
    b = pv.max() - (jj + 0.5) / px_per_block
    centre = np.array([v.nx, v.ny, v.nz]) / 2.0
    base = centre - f * 400 - right * (centre @ right) - up * (centre @ up)
    origins = base[None, None, :] + a[..., None] * right + b[..., None] * up
    origins = origins.reshape(-1, 3)
    dirs = np.repeat(f[None, :], origins.shape[0], 0)
    lit = v.light
    v.light = np.maximum(lit, 6)  # plans show the build; the eye shots show the light
    out = raycast(v, origins, dirs, (0.06, 0.07, 0.09), 1e6, 2e6, max_steps=900)
    v.light = lit
    return (out.reshape(H, W, 3) * 255).astype(np.uint8)


def floor_light(data, vol):
    """Block light where you stand, per room: {room id: [levels...]} for every walkable column of its interior."""
    np = _np()
    L = vol.looks
    out = {}
    for r in data["rooms"]:
        vals = []
        for x in range(r["x0"], r["x1"] + 1):
            for z in range(r["z0"], r["z1"] + 1):
                gx, gz = x - vol.x0, z - vol.z0
                if not (0 <= gx < vol.nx and 0 <= gz < vol.nz):
                    continue
                # the feet: the lowest air cell with a solid (or liquid) cell under it, within the room's band
                for y in range(r["y"] - 4, r["y"] + 3):
                    gy = y - vol.y0
                    if gy <= 0 or gy + 1 >= vol.ny:
                        continue
                    below, here, head = vol.g[gx, gy - 1, gz], vol.g[gx, gy, gz], vol.g[gx, gy + 1, gz]
                    if L.K[below] in (1, 4) and L.K[here] in (0, 2, 3) and L.K[head] in (0, 2, 3):
                        vals.append(int(vol.light[gx, gy, gz]))
                        break
        out[r["id"]] = vals
    return out


def light_report(names=()):
    """Median floor light on the critical path and in the Lord's arena, per dumped floor (targets: 7 and 8)."""
    np = _np()
    rows = []
    for fn in sorted(f for f in os.listdir(RUN) if f.endswith(".json")):
        if names and not any(n in fn for n in names):
            continue
        data = json.load(open(os.path.join(RUN, fn)))
        vol = Volume(data, Looks())
        fl = floor_light(data, vol)
        path = [v for r in data["rooms"] if r["onPath"] and r["type"] != "BOSS" for v in fl[r["id"]]]
        arena = [v for r in data["rooms"] if r["type"] == "BOSS" for v in fl[r["id"]]]
        p = float(np.median(path)) if path else -1
        a = float(np.median(arena)) if arena else -1
        a8 = float(np.mean(np.array(arena) >= 8)) if arena else -1
        rows.append((data["name"], p, a, a8))
        print(f"{data['name']:34s} path median {p:4.1f}  arena median {a:4.1f}  arena >=8 {a8:4.2f}")
    return rows


def _label(im, text):
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 8 + 6 * len(text), 16), fill=(0, 0, 0, 170))
    d.text((4, 3), text, fill=(235, 238, 245, 255))
    return im


def preview(outdir, only=()):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    files = sorted(f for f in os.listdir(RUN) if f.endswith(".json"))
    if only:
        files = [f for f in files if any(o in f for o in only)]
    for fn in files:
        data = json.load(open(os.path.join(RUN, fn)))
        name = data["name"]
        looks = Looks()
        vol = Volume(data, looks)
        fog = fog_of(data["theme"])
        shots = []
        for cam in data.get("cams", []):
            img = Image.fromarray(eye_shot(vol, cam, fog), "RGB").convert("RGBA")
            img.save(os.path.join(outdir, f"{name}_{cam['name']}.png"))
            shots.append(_label(img, f"{name}  {cam['name']}"))
        cut = data["baseY"] + (14 if data.get("boss") else 6)
        rooms = data["rooms"]
        box = (min(r["x0"] for r in rooms) - 3, min(r["z0"] for r in rooms) - 3, max(r["x1"] for r in rooms) + 3, max(r["z1"] for r in rooms) + 3)
        span = max(box[2] - box[0], box[3] - box[1])
        plan = Image.fromarray(plan_shot(vol, fog, cut=cut, box=box, px_per_block=max(4.0, 640 / span)), "RGB").convert("RGBA")
        plan.save(os.path.join(outdir, f"{name}_plan.png"))
        _label(plan, f"{name}  plan (cut at +{cut - data['baseY']})")
        # sheet: the plan on the left, the eye shots stacked on the right
        if shots:
            sw = shots[0].width
            sh = sum(s.height for s in shots)
            Hs = max(plan.height, sh)
            sheet = Image.new("RGBA", (plan.width + sw, Hs), (10, 10, 14, 255))
            sheet.paste(plan, (0, 0))
            yy = 0
            for s in shots:
                sheet.paste(s, (plan.width, yy))
                yy += s.height
            sheet.save(os.path.join(outdir, f"{name}_sheet.png"))
        print("drew", name, len(shots), "views")


# =====================================================================================================================
# Textures: where vanilla runs out. 16x16, from the shared palette toolkit; every theme gets its own ramp.

def _px():
    import pixelart as P
    P.RAMPS.update({
        "bronze": ["#24170a", "#3e2a12", "#5e4019", "#805a24", "#a77a36", "#cfa45a"],
        "verdigris": ["#173c34", "#245a4c", "#337a66", "#4f9e86", "#7cc4a8"],
        "copper": ["#3a1c0e", "#632f17", "#8e4823", "#b8642f", "#dc8a4c", "#f4b47c"],
        "blackstone": ["#0d0b0e", "#17141a", "#221e26", "#2e2933", "#3c3642"],
        "ember_hot": ["#5a1606", "#a3300a", "#e0560f", "#ff8a1e", "#ffc14a", "#fff0a0"],
        "teal_book": ["#0d2a2a", "#164443", "#21615c", "#2f7f74", "#46a08e"],
        "kelp": ["#11240f", "#1d3a17", "#2d5422", "#43712d"],
        "darkwood": ["#1c120a", "#2a1b0e", "#3b2714", "#4f351c", "#664627"],
        "rubber": ["#121214", "#1b1b1f", "#26262b", "#323238", "#43434a"],
        "steel": ["#2a2e35", "#3b414a", "#525a65", "#6d7682", "#8d97a3", "#b4bdc8"],
        "amberglass": ["#5a2e06", "#8a4a0c", "#b86c16", "#e0922a", "#f4b44a", "#ffdc8a"],
        "honey": ["#4a2604", "#7a4208", "#a8600e", "#d68618", "#f2ac2c", "#ffd36a"],
        "wax": ["#7a5a1e", "#a07a2e", "#c49c46", "#dcbc68", "#f0dc96"],
        "larva": ["#e8e0b0", "#f6f0d0", "#fffbea"],
        "jelly": ["#7a4a00", "#b07400", "#e0a010", "#ffc830", "#ffe27a", "#fff6c8"],
        "relief": ["#2b2d2a", "#3d403b", "#53574f", "#6a6f65", "#848a7e", "#a0a698"],
        "jade": ["#0b2a1c", "#124230", "#1b5e44", "#257a58", "#3c9c74", "#7cd0a4"],
        "paper": ["#7a1a12", "#a8261a", "#d23a22", "#f0603a", "#ff9a5a", "#ffd2a0"],
        "quartz": ["#8f8a86", "#aaa6a2", "#c4c0bc", "#dad7d3", "#ecebe8", "#fbfaf8"],
        "rime": ["#9cc4dc", "#b8d8ec", "#d4ecf8", "#ecf8ff"],
        "frostglass": ["#2a5a7a", "#4a86a8", "#78b0cc", "#a8d4e8", "#d4ecf6", "#f4fbff"],
        "silver": ["#4a5260", "#6a7484", "#8e98a8", "#b4bdcb", "#d6dde6", "#f4f8fc"],
        "labwhite": ["#7c8288", "#9aa0a6", "#b6bcc2", "#cfd4d8", "#e4e7ea", "#f4f6f7"],
        "cyanlight": ["#063a44", "#0a5e6c", "#12889a", "#2ab4c4", "#6ae0ea", "#c8fbff"],
        "rack": ["#0e1114", "#161a1f", "#1f252b", "#2a3139", "#384049"],
        "wallpaper": ["#2a1630", "#3c2044", "#52305a", "#6a4472", "#86608c", "#a884aa"],
        "eye": ["#120812", "#d8c8d0", "#f4e8ee", "#5a1a2a"],
        "cloudy": ["#9aa6bc", "#b4bed0", "#ccd4e2", "#e0e6ef", "#eef2f7", "#ffffff"],
        "void": ["#030207", "#08060f", "#0e0a1a", "#161026", "#20183a"],
        "nebula": ["#1a0f33", "#2a1650", "#3c2070", "#55308f"],
    })
    return P


def _canvas(P, fill):
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, fill(x, y))
    return c


def _blank(P):
    return P.Canvas(16, 16)


def tex_ossuary(P):
    """Skulls stacked in rows between courses of long bones, dark gaps behind (catacombs at 16 pixels)."""
    bone, dark = P.ramp("bone"), P.ramp("abyss")
    rng = P.rng_for("ossuary")
    c = _canvas(P, lambda x, y: dark[1] if rng.random() < 0.7 else dark[2])

    def skull(ox, oy, shade):
        rows = ["..####..", ".######.", "##o##o##", "##o##o##", ".###n##.", "..#tt#.."]
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                x, y = (ox + i) % 16, oy + j
                if ch == "#":
                    c.set(x, y, bone[min(5, max(1, 4 + shade - (j > 3) - (i in (0, 7)) + (i in (2, 3) and j < 2)))])
                elif ch in "on":
                    c.set(x, y, dark[0])
                elif ch == "t":
                    c.set(x, y, bone[2])

    def long_bone(oy, ox):
        for i in range(16):
            x = (ox + i) % 16
            c.set(x, oy, bone[3])
            c.set(x, oy + 1, bone[2])
        for k in (0, 7):
            for j in (0, 1):
                c.set((ox + k) % 16, oy + j, bone[4] if j == 0 else bone[3])

    skull(0, 0, 0)
    skull(8, 0, -1)
    long_bone(6, 3)
    skull(4, 8, 0)
    skull(12, 8, -1)
    for x in range(16):  # a course of bone ends
        if x % 3 != 2:
            c.set(x, 14, bone[3] if x % 3 == 0 else bone[2])
            c.set(x, 15, bone[1])
    return c


def tex_bronze(P, seed="bronze"):
    br, vg = P.ramp("bronze"), P.ramp("verdigris")
    n = P.value_noise(16, 16, P.rng_for(seed), scale=3, octaves=3)
    m = P.value_noise(16, 16, P.rng_for(seed, "patina"), scale=4, octaves=2)

    def f(x, y):
        if m[y][x] > 0.66:
            return vg[min(4, 1 + int((m[y][x] - 0.66) * 12))]
        v = n[y][x] + (0.12 if y in (3, 4) else 0) - (0.1 if y in (11, 12) else 0)
        return br[max(1, min(5, int(v * 6)))]
    return _canvas(P, f)


def tex_bell(P):
    br = P.ramp("bronze")
    return _canvas(P, lambda x, y: (br[5] if y == 12 else br[2]) if y in (12, 13) else br[3 + (x in (5, 6))] if y > 2 else br[2])


def tex_glowcap(P):
    """Violet cap flesh with luminous spots (the spots are the emissive layer)."""
    fu = P.ramp("fungal")
    n = P.value_noise(16, 16, P.rng_for("glowcap"), scale=3, octaves=2)
    base = _canvas(P, lambda x, y: fu[2 + min(3, int(n[y][x] * 3))])
    glow = _blank(P)
    for (sx, sy, r) in ((3, 4, 1.6), (11, 3, 1.2), (8, 10, 2.0), (13, 12, 1.2), (2, 12, 1.0)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - sx, y - sy)
                if d <= r:
                    col = (120, 255, 230, 255) if d < r * 0.5 else (70, 200, 200, 255)
                    base.set(x, y, col)
                    glow.set(x, y, col)
    return base, glow


def tex_gills(P):
    fu = P.ramp("fungal")
    lit = (88, 180, 196, 255)

    def f(x, y):
        a = math.atan2(y - 7.5, x - 7.5)
        stripe = int((a + math.pi) / (2 * math.pi) * 24) % 2
        if math.hypot(x - 7.5, y - 7.5) < 1.6:
            return fu[1]
        return lit if stripe else fu[3]
    base = _canvas(P, f)
    glow = _blank(P)
    for y in range(16):
        for x in range(16):
            if base.get(x, y) == lit:
                glow.set(x, y, (110, 220, 230, 255))
    return base, glow


def tex_ember_ore(P):
    bs, em = P.ramp("blackstone"), P.ramp("ember_hot")
    n = P.value_noise(16, 16, P.rng_for("ember_ore"), scale=4, octaves=3)
    base = _canvas(P, lambda x, y: bs[min(4, int(n[y][x] * 5))])
    glow = _blank(P)
    for seed, (x, y), dx in (("a", (0, 4), 1), ("b", (15, 11), -1)):
        r = P.rng_for("vein", seed)
        for step in range(22):
            heat = 3 + (1 if 5 < step < 15 else 0) + (1 if r.random() < 0.2 else 0)
            for (px, py, hh) in ((x, y, heat), (x, y + 1, heat - 2)):
                if 0 <= px < 16 and 0 <= py < 16:
                    base.set(px, py, em[max(0, min(5, hh))])
                    glow.set(px, py, em[max(0, min(5, hh))])
            x += dx
            y = max(1, min(14, y + r.choice((-1, 0, 0, 1))))
            if r.random() < 0.15:
                by = y
                for _ in range(3):
                    by += r.choice((-1, 1))
                    if 0 <= x < 16 and 0 <= by < 16:
                        base.set(x, by, em[2])
                        glow.set(x, by, em[2])
    return base, glow


def tex_sodden(P):
    """A shelf of swollen books, green with algae, water stains running down."""
    wood, tb, kp = P.ramp("darkwood"), P.ramp("teal_book"), P.ramp("kelp")
    rng = P.rng_for("sodden")
    c = _canvas(P, lambda x, y: wood[2])
    for sy in (0, 7, 8, 15):
        for x in range(16):
            c.set(x, sy, wood[3] if sy in (0, 8) else wood[1])
    for (y0, y1) in ((1, 6), (9, 14)):
        x = 1
        while x < 15:
            w = rng.choice((1, 2, 2, 3))
            col = rng.choice((tb, tb, kp, P.ramp("wood")))
            top = y0 + rng.choice((0, 0, 1))
            for i in range(w):
                for y in range(top, y1 + 1):
                    if x + i < 15:
                        c.set(x + i, y, col[2 if i == 0 else 3] if y > top else col[-1])
            x += w + (1 if rng.random() < 0.3 else 0)
    for x in (2, 6, 11, 13):
        for y in range(1, 6 + rng.randrange(0, 6)):
            if rng.random() < 0.7:
                c.blend(x, y, kp[3], 0.45)
    return c


def tex_sodden_top(P):
    wood = P.ramp("darkwood")
    return _canvas(P, lambda x, y: wood[3] if y % 4 == 0 else wood[2] if (x + y // 4) % 5 else wood[1])


def tex_gear(P):
    cu = P.ramp("copper")

    def f(x, y):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        tooth = (int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 12) % 2) == 0
        if d > 7.6 or (d > 6.2 and not tooth):
            return (40, 22, 14, 255)
        if d > 5.2:
            return cu[4] if dy < 0 else cu[3]
        if d > 4.4:
            return cu[1]
        if d < 1.8:
            return cu[5] if d < 1.0 else cu[2]
        return cu[3] if (abs(dx) < 1.0 or abs(dy) < 1.0) else (60, 32, 18, 255)
    return _canvas(P, f)


def tex_conveyor(P):
    rb, st = P.ramp("rubber"), P.ramp("steel")
    side = _canvas(P, lambda x, y: (rb[3] if y % 4 == 0 else rb[1] if y % 4 == 3 else rb[2]) if 1 <= x <= 14 else st[2 if x == 0 else 1])
    end = _canvas(P, lambda x, y: st[1])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.5:
                end.set(x, y, st[3] if d > 5.4 else st[2] if d > 2 else st[4])
    return side, end


def tex_sandglass(P):
    ag = P.ramp("amberglass")
    rng = P.rng_for("sandglass")
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)

    def f(x, y):
        v = n[y][x]
        if rng.random() < 0.05:
            return (*ag[5][:3], 230)
        r, g, b, _ = ag[2 + min(3, int(v * 3))]
        return (r, g, b, 150 + int(v * 60))
    c = _canvas(P, f)
    for (bx, by) in ((4, 5), (10, 9), (6, 12), (12, 3)):
        c.set(bx, by, (*ag[1][:3], 200))
        c.set(bx + 1, by, (*ag[5][:3], 220))
    return c


def tex_sun_tile(P):
    sa, am = P.ramp("sand"), P.ramp("amber")
    rng = P.rng_for("sun_tile")
    base = _canvas(P, lambda x, y: sa[3] if (x in (0, 15) or y in (0, 15)) else sa[2 + (rng.random() < 0.3)])
    glow = _blank(P)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            ray = 3.2 < d < 6.2 and int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 16) % 2 == 0
            if d < 2.8 or ray:
                col = am[5] if d < 1.6 else am[4] if d < 2.8 else am[3]
                base.set(x, y, col)
                glow.set(x, y, col)
            elif 2.8 <= d < 3.4:
                base.set(x, y, sa[1])
    return base, glow


def tex_brood(P):
    hn, wx, lv = P.ramp("honey"), P.ramp("wax"), P.ramp("larva")
    base = _canvas(P, lambda x, y: hn[1])
    glow = _blank(P)
    # a hexagonal lattice that tiles in 16: Voronoi cells of staggered centres are the comb's cells
    cells = [((col * 8 + (4 if row % 2 else 0)) % 16, row * 5.33 + 1) for row in range(3) for col in range(2)]
    for y in range(16):
        for x in range(16):
            ds = []
            for i, (cx, cy) in enumerate(cells):
                for ox in (-16, 0, 16):
                    for oy in (-16, 0, 16):
                        ds.append((math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy), i))
            ds.sort()
            (d1, bi), (d2, _) = ds[0], ds[1]
            kind = P.rng_for("cell", bi).random()
            if d2 - d1 < 1.1:
                base.set(x, y, hn[0] if d2 - d1 < 0.55 else hn[2])  # the wax walls between cells
            elif kind < 0.4:
                base.set(x, y, wx[4] if d1 < 1.5 else wx[3])  # capped
            elif kind < 0.7:
                col = lv[2] if d1 < 1.4 else lv[1]
                base.set(x, y, col)  # a larva, glowing
                glow.set(x, y, col)
            else:
                base.set(x, y, hn[5] if d1 < 1.3 else hn[4])  # honey
    return base, glow


def tex_jelly(P):
    jl = P.ramp("jelly")
    n = P.value_noise(16, 16, P.rng_for("jelly"), scale=2, octaves=3)

    def f(x, y):
        v = n[y][x]
        swirl = math.sin((x + y * 0.6) * 0.9 + v * 6) > 0.6
        r, g, b, _ = jl[5 if swirl else 2 + min(3, int(v * 3))]
        return (r, g, b, 235 if swirl else 205)
    return _canvas(P, f)


RELIEF_FACE = [
    "................",
    ".##############.",
    ".#............#.",
    ".#.BBBB..BBBB.#.",
    ".#.SSSS..SSSS.#.",
    ".#..EE....EE..#.",
    ".#..JJ....JJ..#.",
    ".#.....NN.....#.",
    ".#.....NN.....#.",
    ".#....NNNN....#.",
    ".#............#.",
    ".#...MMMMMM...#.",
    ".#....mmmm....#.",
    ".#............#.",
    ".##############.",
    "................",
]


def tex_relief(P):
    """A carved face: heavy brows, jade eyes, a broad nose, closed lips; moss in the cuts."""
    rl, jd, ms = P.ramp("relief"), P.ramp("jade"), P.ramp("moss")
    n = P.value_noise(16, 16, P.rng_for("relief"), scale=4, octaves=2)
    c = _canvas(P, lambda x, y: rl[2 + min(2, int(n[y][x] * 2))])
    pal = {"#": rl[1], "B": rl[5], "S": rl[0], "E": jd[2], "J": jd[5], "M": rl[1], "m": rl[4]}
    for y, row in enumerate(RELIEF_FACE):
        for x, ch in enumerate(row):
            if ch == "N":
                c.set(x, y, rl[4] if x < 8 else rl[3])
            elif ch in pal:
                c.set(x, y, pal[ch])
    rng = P.rng_for("relief_moss")
    for x in range(16):
        for y in range(16):
            if (y > 12 or x < 2) and rng.random() < 0.35:
                c.set(x, y, ms[3 + (rng.random() < 0.4)])
    return c


def tex_relief_side(P):
    ms = P.ramp("moss")
    c = P.bricks("relief", seed="relief_side", brick_w=8, brick_h=8)
    rng = P.rng_for("relief_side_moss")
    for x in range(16):
        for y in range(10, 16):
            if rng.random() < 0.3:
                c.set(x, y, ms[3])
    return c


def tex_jade(P):
    jd = P.ramp("jade")
    n = P.value_noise(16, 16, P.rng_for("jade"), scale=3, octaves=3)

    def f(x, y):
        v = n[y][x]
        if x == 0 or y == 0:
            return jd[4]
        if x == 15 or y == 15:
            return jd[0]
        return jd[5] if abs(math.sin(x * 0.55 + y * 0.3 + v * 5)) < 0.12 else jd[1 + min(2, int(v * 3))]
    return _canvas(P, f)


def tex_paper_lantern(P):
    pp, wd = P.ramp("paper"), P.ramp("darkwood")

    def side(x, y):
        if y < 2 or y > 13:
            return wd[2] if y in (1, 14) else wd[3]
        idx = 2 + int((1 - abs(y - 7.5) / 7) * 3.2)
        return pp[max(1, idx - 1)] if x % 5 == 2 else pp[min(5, idx)]
    s = _canvas(P, side)
    for x in range(6, 10):
        s.set(x, 6, (255, 214, 120, 255))
        s.set(x, 9, (255, 214, 120, 255))
    t = _canvas(P, lambda x, y: wd[3] if (x - 7.5) ** 2 + (y - 7.5) ** 2 > 12 else wd[1])
    return s, t


def tex_rime(P):
    ri = P.ramp("rime")
    c = P.bricks("quartz", seed="rime", brick_w=8, brick_h=4, mortar=1)
    rng = P.rng_for("rime_frost")
    for y in range(16):
        for x in range(16):
            if y % 4 == 0 and rng.random() < 0.8:
                c.set(x, y, ri[3])
            elif y % 4 == 1 and rng.random() < 0.3:
                c.set(x, y, ri[2])
            elif y % 4 == 3 and rng.random() < 0.12:
                c.set(x, y, ri[1])
    return c


def tex_frost_window(P):
    fg = P.ramp("frostglass")
    rng = P.rng_for("frost_window")

    def f(x, y):
        if (x + y) % 8 == 0 or (x - y) % 8 == 0 or x in (0, 15) or y in (0, 15):
            return (58, 66, 78, 255)
        fern = math.sin(x * 1.3 + math.sin(y * 0.9) * 2) * math.cos(y * 1.1) > 0.55
        r, g, b, _ = fg[4 if fern else 2 + (rng.random() < 0.3)]
        return (r, g, b, 235 if fern else 180)
    return _canvas(P, f)


def tex_mirror(P):
    sv = P.ramp("silver")

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return sv[1]
        streak = (x + y) % 11 in (0, 1, 6)
        return sv[5] if streak else sv[2 + int((x * 0.4 + (15 - y) * 0.6) / 15 * 2.5)]
    base = _canvas(P, f)
    glow = _blank(P)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x + y) % 11 in (0, 1):
                glow.set(x, y, sv[5])
    return base, glow


def tex_lab_panel(P):
    lw, cl = P.ramp("labwhite"), P.ramp("cyanlight")

    def f(x, y):
        if y in (0, 15) or x in (0, 15):
            return lw[1]
        if y in (7, 8):
            return cl[4] if 2 <= x <= 13 else lw[2]
        if y in (6, 9):
            return cl[2] if 2 <= x <= 13 else lw[3]
        return lw[4] if (x + y) % 9 else lw[3]
    base = _canvas(P, f)
    for (sx, sy) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        base.set(sx, sy, lw[0])
    glow = _blank(P)
    for x in range(2, 14):
        glow.set(x, 7, cl[5])
        glow.set(x, 8, cl[4])
    return base, glow


def tex_lab_rack(P):
    rk = P.ramp("rack")
    colours = [(80, 230, 240, 255), (255, 190, 70, 255), (120, 255, 150, 255)]
    bases, glows = [], []
    for fr in range(4):
        rng = P.rng_for("rack", fr)
        base = _canvas(P, lambda x, y: rk[1] if x in (0, 15) or y in (0, 15) else rk[2] if y % 3 else rk[3])
        glow = _blank(P)
        for row in range(1, 14, 3):
            for col in range(2, 14, 2):
                if P.rng_for("led", row, col).random() < 0.6 and rng.random() < 0.75:
                    cc = colours[(row + col) % 3]
                    base.set(col, row + 1, cc)
                    glow.set(col, row + 1, cc)
                else:
                    base.set(col, row + 1, rk[0])
        bases.append(base)
        glows.append(glow)
    side = _canvas(P, lambda x, y: rk[1] if x in (0, 15) else rk[2] if (y % 4) else rk[0])
    return bases, glows, side


def tex_dream_paper(P):
    wp, ey = P.ramp("wallpaper"), P.ramp("eye")

    def f(x, y):
        cx, cy = (x // 8) * 8 + 4, (y // 8) * 8 + 4
        dx, dy = x - cx + 0.5, y - cy + 0.5
        if 3.4 < abs(dx) + abs(dy) < 4.2:
            return wp[4]
        eye = (dx / 2.8) ** 2 + (dy / 1.4) ** 2
        if eye < 0.35:
            return ey[0]
        if eye < 1.0:
            return ey[2] if dy < 0 else ey[1]
        return wp[2] if (x + y) % 2 else wp[1]
    c = _canvas(P, f)
    for y in range(11, 16):
        for x in range(16 - (y - 10), 16):
            c.set(x, y, (64, 58, 52, 255))
    return c


def tex_cloud(P):
    cd = P.ramp("cloudy")
    n = P.value_noise(16, 16, P.rng_for("cloud"), scale=2, octaves=3)
    return _canvas(P, lambda x, y: cd[max(1, min(5, int((n[y][x] * 0.7 + (1 - y / 15) * 0.4) * 5.2)))])


def tex_sky(P):
    """Open sky: a soft blue with the faintest haze, no pattern to give the tiling away."""
    n = P.value_noise(16, 16, P.rng_for("sky"), scale=2, octaves=2)
    return _canvas(P, lambda x, y: (int(128 + n[y][x] * 14), int(184 + n[y][x] * 12), int(236 + n[y][x] * 8), 255))


def tex_void(P):
    vd, nb = P.ramp("void"), P.ramp("nebula")
    n = P.value_noise(16, 16, P.rng_for("void"), scale=2, octaves=3)
    base = _canvas(P, lambda x, y: nb[min(3, int((n[y][x] - 0.55) * 9))] if n[y][x] > 0.55 else vd[min(4, int(n[y][x] * 4))])
    glow = _blank(P)
    rng = P.rng_for("stars")
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        col = rng.choice(((255, 255, 255, 255), (190, 210, 255, 255), (230, 200, 255, 255)))
        base.set(x, y, col)
        glow.set(x, y, col)
    return base, glow


def tex_signal_lamp(P):
    sg, pc = P.ramp("signal"), P.ramp("precursor")

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return pc[2]
        if x in (1, 14) or y in (1, 14):
            return pc[4]
        dx, dy = abs(x - 7.5), abs(y - 7.5)
        hexd = max(dx * 0.87 + dy * 0.5, dy)
        return sg[5] if hexd < 2.2 else sg[4] if hexd < 4.0 else (sg[3] if (x + y) % 2 else sg[4])
    return _canvas(P, f)


def textures():
    """Texture name -> Canvas (a list of Canvases for an animation)."""
    P = _px()
    t = {"gate_ossuary": tex_ossuary(P), "gate_bell_bronze": tex_bronze(P), "gate_bell": tex_bell(P)}
    t["gate_glowcap"], t["gate_glowcap_glow"] = tex_glowcap(P)
    t["gate_glowcap_gills"], t["gate_glowcap_gills_glow"] = tex_gills(P)
    t["gate_ember_ore"], t["gate_ember_ore_glow"] = tex_ember_ore(P)
    t["gate_sodden_shelf"], t["gate_sodden_shelf_top"] = tex_sodden(P), tex_sodden_top(P)
    t["gate_gear"] = tex_gear(P)
    t["gate_conveyor_side"], t["gate_conveyor_end"] = tex_conveyor(P)
    t["gate_sandglass"] = tex_sandglass(P)
    t["gate_sun_tile"], t["gate_sun_tile_glow"] = tex_sun_tile(P)
    t["gate_brood_cell"], t["gate_brood_cell_glow"] = tex_brood(P)
    t["gate_royal_jelly"] = tex_jelly(P)
    t["gate_temple_relief"], t["gate_temple_relief_side"] = tex_relief(P), tex_relief_side(P)
    t["gate_jade"] = tex_jade(P)
    t["gate_paper_lantern_side"], t["gate_paper_lantern_top"] = tex_paper_lantern(P)
    t["gate_rime_brick"] = tex_rime(P)
    t["gate_frost_window"] = tex_frost_window(P)
    t["gate_mirror"], t["gate_mirror_glow"] = tex_mirror(P)
    t["gate_lab_panel"], t["gate_lab_panel_glow"] = tex_lab_panel(P)
    t["gate_lab_rack_front"], t["gate_lab_rack_glow"], t["gate_lab_rack_side"] = tex_lab_rack(P)
    t["gate_dream_paper"] = tex_dream_paper(P)
    t["gate_cloud"] = tex_cloud(P)
    t["gate_sky"] = tex_sky(P)
    t["gate_void_glass"], t["gate_void_glass_glow"] = tex_void(P)
    t["gate_signal_lamp"] = tex_signal_lamp(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, frametime=12 if isinstance(c, list) else None)


# =====================================================================================================================
# Content: blockstates, models, item definitions, loot tables, lang, mining tags

BLOCKS = {
    # name: (English name, model kind, mining tool or None)
    "gate_ossuary": ("Ossuary Wall", "cube", "pickaxe"),
    "gate_bell_bronze": ("Bell Bronze", "cube", "pickaxe"),
    "gate_bell": ("Crypt Bell", "bell", "pickaxe"),
    "gate_glowcap": ("Glowcap", "glow", "axe"),
    "gate_glowcap_gills": ("Glowcap Gills", "glow", "axe"),
    "gate_ember_ore": ("Ember Seam", "glow", "pickaxe"),
    "gate_sodden_shelf": ("Sodden Bookshelf", "column", "axe"),
    "gate_gear": ("Gear Plate", "cube", "pickaxe"),
    "gate_conveyor": ("Conveyor Belt", "axis", "pickaxe"),
    "gate_sandglass": ("Sandglass", "translucent", None),
    "gate_sun_tile": ("Sun Tile", "glow", "pickaxe"),
    "gate_brood_cell": ("Brood Comb", "glow", None),
    "gate_royal_jelly": ("Royal Jelly", "translucent_bright", None),
    "gate_temple_relief": ("Temple Relief", "facing", "pickaxe"),
    "gate_jade": ("Jade", "cube", "pickaxe"),
    "gate_paper_lantern": ("Paper Lantern", "lantern", None),
    "gate_rime_brick": ("Rime Brick", "cube", "pickaxe"),
    "gate_frost_window": ("Frosted Window", "translucent", None),
    "gate_mirror": ("Mirror", "bright", "pickaxe"),
    "gate_lab_panel": ("Lab Panel", "glow", "pickaxe"),
    "gate_lab_rack": ("Machine Rack", "rack", "pickaxe"),
    "gate_dream_paper": ("Dream Wallpaper", "cube", None),
    "gate_cloud": ("Cloudbank", "bright", None),
    "gate_sky": ("Painted Sky", "bright", None),
    "gate_void_glass": ("Void Glass", "glow", "pickaxe"),
    "gate_signal_lamp": ("Signal Lamp", "bright", "pickaxe"),
}
SIDES = ("down", "up", "north", "south", "west", "east")


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    _write(path, cur)


def _cube_elements(faces, emission=0, tint=None):
    e = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": faces[d], "cullface": d} for d in SIDES}}
    if emission:
        e["light_emission"] = emission
    return e


def _model(name, kind):
    t = f"awaken:block/{name}"
    if kind == "cube":
        return {"parent": "minecraft:block/cube_all", "textures": {"all": t}}
    if kind == "glow":
        # the base, then the same cube again with only the glowing pixels, at full light
        return {"parent": "minecraft:block/block", "textures": {"particle": t, "base": t, "glow": t + "_glow"},
                "elements": [_cube_elements({d: "#base" for d in SIDES}),
                             dict(_cube_elements({d: "#glow" for d in SIDES}, 15), shade_direction_override="up")]}
    if kind == "bright":
        return {"parent": "minecraft:block/block", "textures": {"particle": t, "all": t},
                "elements": [_cube_elements({d: "#all" for d in SIDES}, 15)]}
    if kind == "translucent":
        return {"parent": "minecraft:block/cube_all", "textures": {"all": {"sprite": t, "force_translucent": True}}}
    if kind == "translucent_bright":
        return {"parent": "minecraft:block/block", "textures": {"particle": t, "all": {"sprite": t, "force_translucent": True}},
                "elements": [_cube_elements({d: "#all" for d in SIDES}, 15)]}
    if kind == "column":
        return {"parent": "minecraft:block/cube_column", "textures": {"end": t + "_top", "side": t}}
    if kind == "axis":
        return {"parent": "minecraft:block/cube_column", "textures": {"end": t + "_end", "side": t + "_side"}}
    if kind == "facing":
        return {"parent": "minecraft:block/orientable", "textures": {"front": t, "side": t + "_side", "top": t + "_side"}}
    if kind == "rack":
        side = t + "_side"
        faces = {d: "#side" for d in SIDES}
        faces["north"] = "#front"
        return {"parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "front": t + "_front", "glow": t + "_glow"},
                "elements": [_cube_elements(faces),
                             {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                              "faces": {"north": {"texture": "#glow", "cullface": "north"}}}]}
    if kind == "lantern":
        s, top = t + "_side", t + "_top"
        body = {"from": [3, 1, 3], "to": [13, 14, 13], "light_emission": 15,
                "faces": {"north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"},
                          "east": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#top"}}}
        cap = {"from": [6, 14, 6], "to": [10, 16, 10],
               "faces": {d: {"texture": "#top"} for d in SIDES}}
        return {"parent": "minecraft:block/block", "textures": {"particle": s, "side": s, "top": top}, "elements": [body, cap]}
    if kind == "bell":
        def box(a, b):
            return {"from": a, "to": b, "faces": {d: {"texture": "#bell"} for d in SIDES}}
        return {"parent": "minecraft:block/block", "textures": {"particle": t, "bell": t},
                "elements": [box([4, 3, 4], [12, 5, 12]), box([5, 5, 5], [11, 12, 11]), box([6, 12, 6], [10, 14, 10]), box([7, 14, 7], [9, 16, 9])]}
    raise ValueError(kind)


def content():
    for name, (english, kind, tool) in BLOCKS.items():
        _write(os.path.join(ASSETS, f"models/block/{name}.json"), _model(name, kind))
        if kind == "axis":
            bs = {"variants": {"axis=y": {"model": f"awaken:block/{name}"},
                               "axis=x": {"model": f"awaken:block/{name}", "x": 90, "y": 90},
                               "axis=z": {"model": f"awaken:block/{name}", "x": 90}}}
        elif kind in ("facing", "rack"):
            bs = {"variants": {f"facing={f}": dict({"model": f"awaken:block/{name}"}, **({"y": r} if r else {}))
                               for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
        else:
            bs = {"variants": {"": {"model": f"awaken:block/{name}"}}}
        _write(os.path.join(ASSETS, f"blockstates/{name}.json"), bs)
        _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"awaken:block/{name}"}})
        _write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
            "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "gates/assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v[0] for k, v in BLOCKS.items()})
    for tool in ("pickaxe", "axe"):
        _merge(os.path.join(MERGE, f"gates/data/minecraft/tags/block/mineable/{tool}.json"),
               {"values": [f"awaken:{k}" for k, v in BLOCKS.items() if v[2] == tool]})
    print("gate_interiors: content written for", len(BLOCKS), "blocks")


def contact_sheet(path):
    """Every interior texture at 6x, named."""
    from PIL import Image, ImageDraw
    items = []
    for name, c in textures().items():
        im = (c[0] if isinstance(c, list) else c).image()
        items.append((name, im))
    S, cols = 96, 8
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (S + 16), rows * (S + 30) + 10), (40, 42, 50, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(items):
        x, y = (i % cols) * (S + 16) + 8, (i // cols) * (S + 30) + 8
        big = im.resize((S, S), Image.NEAREST)
        sheet.paste(big, (x, y), big)
        d.text((x, y + S + 2), name[5:][:16], fill=(220, 220, 230, 255))
    sheet.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/interiors", sys.argv[3:])
    elif len(sys.argv) > 1 and sys.argv[1] == "content":
        content()  # noqa: F821
    elif len(sys.argv) > 1 and sys.argv[1] == "light":
        light_report(sys.argv[2:])
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        contact_sheet(sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_interiors.png")
    else:
        print(__doc__)
