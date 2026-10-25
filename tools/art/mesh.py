"""Sculpting from code (SPEC §24 "Weapons"): smooth, UV-unwrapped, hand-paintable meshes for Arms' weapons.

A weapon is a list of Parts. Every Part is a grid of points (rows along it, columns around it) built by one of:
    sweep(...)   cross-sections carried along a path (blades, tangs, claws, horns; any section, any path)
    lathe(...)   a profile turned round an axis (pommels, collars, beads, round gems)
    tube(...)    a circle along a curve (wire, wraps, tendrils)
Normals are smooth within a crease angle and hard across it (a blade's edge stays sharp, its flat stays soft). Each
Part unfolds into its own chart (rows laid flat at their real lengths, centred), so texels stay square and uniform at
the density asked for; the painter gets, per texel, the surface point, its normal, the section's tag (what that
column is: an edge, a bevel, a flat) and how far along the Part it lies. Charts pack into one atlas; the texture is
the albedo beside its material map (metal, gloss, emissive, flow).

Written as the versioned binary mesh the game reads (dev.awaken.arms.WeaponMesh), positions in item space (0..1, the
same box as a JSON item model, so vanilla's hand transforms hold it).
"""
import math
import struct

import numpy as np

MAGIC = b"AWMS"
VERSION = 1


class Part:
    def __init__(self, name, grid, closed, tags, mat, crease=38.0, along=None, data=None):
        self.name = name
        self.grid = np.asarray(grid, dtype=float)           # (R, C, 3)
        self.closed = closed                                  # whether columns wrap round
        self.tags = np.asarray(tags, dtype=float)            # (C,) one label per section point
        self.mat = mat                                        # the painter's key
        self.crease = crease
        R = self.grid.shape[0]
        self.along = np.linspace(0, 1, R) if along is None else np.asarray(along, dtype=float)  # (R,) 0..1
        self.data = data or {}                                # anything the painter wants (e.g. which end is the tip)


# ---------------------------------------------------------------------------------------------------------------------
# Frames and paths

def straight(y0, y1, n, x=0.0, z=0.0):
    """Stations up the y axis: centres (n,3) and frames (n,3,3) as rows (right, tangent, out)."""
    ys = np.linspace(y0, y1, n)
    centres = np.stack([np.full(n, x), ys, np.full(n, z)], axis=1)
    frames = np.repeat(np.array([[[1.0, 0, 0], [0, 1.0, 0], [0, 0, 1.0]]]), n, axis=0)
    return centres, frames


def transport(points, up=(0, 0, 1)):
    """Parallel-transport frames along a polyline (no twist): rows (right, tangent, out), out starting nearest up."""
    P = np.asarray(points, dtype=float)
    n = len(P)
    T = np.gradient(P, axis=0)
    T /= np.linalg.norm(T, axis=1, keepdims=True)
    out = np.asarray(up, dtype=float)
    out = out - T[0] * (out @ T[0])
    out /= np.linalg.norm(out)
    frames = np.zeros((n, 3, 3))
    for i in range(n):
        if i:
            out = out - T[i] * (out @ T[i])
            out /= np.linalg.norm(out)
        right = np.cross(T[i], out)
        frames[i] = [right, T[i], out]
    return P, frames


def bezier(p0, p1, p2, p3, n):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = (np.asarray(p, dtype=float) for p in (p0, p1, p2, p3))
    return (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3


# ---------------------------------------------------------------------------------------------------------------------
# Primitives

def sweep(name, centres, frames, sections, tags, mat, closed=True, crease=38.0, along=None, data=None):
    """sections: (n, C, 2) points in each station's (right, out) plane, either way round: a closed one is turned to run
    from right toward out (counter-clockwise), so its faces wind outward and its normals point out."""
    S = np.asarray(sections, dtype=float)
    tags = np.asarray(tags, dtype=float)
    if closed:
        big = S[np.argmax(np.ptp(S[..., 0], axis=1) + np.ptp(S[..., 1], axis=1))]
        x, y = big[:, 0], big[:, 1]
        if np.sum(x * np.roll(y, -1) - np.roll(x, -1) * y) < 0:
            S, tags = S[:, ::-1], tags[::-1]
    grid = centres[:, None, :] + S[..., 0:1] * frames[:, None, 0, :] + S[..., 1:2] * frames[:, None, 2, :]
    return Part(name, grid, closed, tags, mat, crease, along, data)


def lathe(name, profile, sides, mat, origin=(0, 0, 0), crease=50.0, phase=0.0, data=None):
    """profile: [(radius, height)] bottom to top, turned round the y axis through origin."""
    o = np.asarray(origin, dtype=float)
    a = np.linspace(0, 2 * math.pi, sides, endpoint=False) + phase
    grid = np.array([[o + [r * math.cos(t), h, r * math.sin(t)] for t in a] for r, h in profile])
    return Part(name, grid, True, np.linspace(0, 1, sides, endpoint=False), mat, crease, None, data)


def tube(name, points, radius, sides, mat, up=(0, 0, 1), crease=60.0, data=None):
    P, F = transport(points, up)
    r = np.broadcast_to(np.asarray(radius, dtype=float), (len(P),))
    a = np.linspace(0, 2 * math.pi, sides, endpoint=False)
    S = np.stack([np.outer(r, np.cos(a)), np.outer(r, np.sin(a))], axis=-1)
    return sweep(name, P, F, S, np.linspace(0, 1, sides, endpoint=False), mat, True, crease, None, data)


# Sections (the same point count for every station of one part)

def rounded(w, d, r, per_corner=3):
    """A rounded rectangle, w across and d deep."""
    r = min(r, w / 2, d / 2)
    pts = []
    for cx, cy, a0 in ((w / 2 - r, d / 2 - r, 0), (-w / 2 + r, d / 2 - r, 90), (-w / 2 + r, -d / 2 + r, 180), (w / 2 - r, -d / 2 + r, 270)):
        for k in range(per_corner + 1):
            a = math.radians(a0 + 90 * k / per_corner)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts, list(np.linspace(0, 1, len(pts), endpoint=False))


# ---------------------------------------------------------------------------------------------------------------------
# Triangles, normals

def triangles(part):
    """Per-corner triangles: positions (T,3,3), normals (T,3,3), the (r, c) of each corner (T,3,2)."""
    G = part.grid
    R, C, _ = G.shape
    cols = C if part.closed else C - 1
    # face normals per quad, from its diagonals (robust for a quad squeezed to a point)
    fn = np.zeros((R - 1, cols, 3))
    for c in range(cols):
        c1 = (c + 1) % C
        d1 = G[1:, c1] - G[:-1, c]
        d2 = G[1:, c] - G[:-1, c1]
        fn[:, c] = np.cross(d2, d1)
    ln = np.linalg.norm(fn, axis=2, keepdims=True)
    valid = ln[..., 0] > 1e-9
    fn = np.where(ln > 1e-9, fn / np.maximum(ln, 1e-12), 0)
    cos_crease = math.cos(math.radians(part.crease))

    def corner_normal(r, c, f):
        """The normal at grid point (r, c) for face f: the faces round it that lie within the crease of f."""
        own = fn[f]
        acc = np.zeros(3)
        for fr in (r - 1, r):
            for fc in (c - 1, c):
                if part.closed:
                    fc %= C
                if 0 <= fr < R - 1 and 0 <= fc < cols and valid[fr, fc] and fn[fr, fc] @ own >= cos_crease:
                    acc += fn[fr, fc]
        n = np.linalg.norm(acc)
        return acc / n if n > 1e-9 else own

    tris, norms, rc = [], [], []
    for r in range(R - 1):
        for c in range(cols):
            if not valid[r, c]:
                continue
            # columns unwrapped (c + 1 may be C, the seam's far side in the chart); positions wrap
            quad = [(r, c), (r + 1, c), (r + 1, c + 1), (r, c + 1)]
            for a, b, d in ((0, 1, 2), (0, 2, 3)):
                ids = (quad[a], quad[b], quad[d])
                p = np.array([G[i, j % C] for i, j in ids])
                if np.linalg.norm(np.cross(p[1] - p[0], p[2] - p[0])) < 1e-9:
                    continue
                tris.append(p)
                norms.append([corner_normal(i, j % C, (r, c)) for i, j in ids])
                rc.append(ids)
    return np.array(tris), np.array(norms), np.array(rc, dtype=float)


def halves(sections, tags):
    """A closed section split at its two edges (tag 0) into the face toward +out and the face toward -out, each running
    so it winds outward as an open sweep. Returns ((sections, tags), (sections, tags)); the edges belong to both."""
    S = np.asarray(sections, dtype=float)
    t = np.asarray(tags, dtype=float)
    big = S[np.argmax(np.ptp(S[..., 0], axis=1))]
    x, y = big[:, 0], big[:, 1]
    if np.sum(x * np.roll(y, -1) - np.roll(x, -1) * y) < 0:  # counter-clockwise, as sweep() would turn it
        S, t = S[:, ::-1], t[::-1]
        big = big[::-1]
    edges = [i for i in range(len(t)) if t[i] == 0]
    i, j = (edges[0], edges[1]) if big[edges[0], 0] > big[edges[1], 0] else (edges[1], edges[0])  # i: the +right edge
    n = len(t)
    front = [(i + k) % n for k in range(((j - i) % n) + 1)]   # +right edge, over +out, to -right edge
    back = [(j + k) % n for k in range(((i - j) % n) + 1)]    # -right edge, under, back to +right
    return (S[:, front], t[front]), (S[:, back], t[back])


def cap(name, part, end, mat, tag=None):
    """Closes an end of a closed part with a fan to its centre (a flat face, unfolded as seen head-on)."""
    row = part.grid[0 if end == 0 else -1]
    centre = row.mean(axis=0)
    grid = np.array([row, np.repeat(centre[None], len(row), axis=0)])
    if end == 0:
        grid = grid[::-1]
    t = np.full(len(row), part.tags.max() + 1 if tag is None else tag)
    p = Part(name, grid, True, t, mat, crease=20.0, along=np.full(2, part.along[0 if end == 0 else -1]), data=dict(part.data, cap=True))
    return p


# ---------------------------------------------------------------------------------------------------------------------
# Charts: each part unfolded flat at real lengths

class Chart:
    def __init__(self, part, density, pad=1):
        G = part.grid
        R, C, _ = G.shape
        ring = np.concatenate([G, G[:, :1]], axis=1) if part.closed else G
        seg = np.linalg.norm(np.diff(ring, axis=1), axis=2)            # (R, C or C-1)
        self.arc = np.concatenate([np.zeros((R, 1)), np.cumsum(seg, axis=1)], axis=1)  # (R, C+1 or C)
        self.perim = self.arc[:, -1]
        rows = np.linalg.norm(np.diff(G, axis=0), axis=2).mean(axis=1)  # mean distance between rows
        self.v = np.concatenate([[0.0], np.cumsum(rows)])
        self.part, self.density, self.pad = part, density, pad
        # each row is laid flat about one column: its middle by default, or the part's "align" column (a blade's fuller
        # then runs down one straight column of texels, so what is painted along it stays square)
        a = part.data.get("align")
        self.anchor = self.perim / 2 if a is None else self.arc[:, a]
        left, right = (self.anchor).max(), (self.perim - self.anchor).max()
        self.mid = pad + math.ceil(left * density)
        self.w = self.mid + int(math.ceil(right * density)) + pad
        self.h = int(math.ceil(self.v[-1] * density)) + 2 * pad
        self.x = self.y = 0  # placed by the packer

    def uv(self, r, c):
        """Texel coordinates of grid point (r, c) (c may be C: the seam's far side)."""
        r = int(r)
        c = int(c)
        u = self.mid + (self.arc[r, c] - self.anchor[r]) * self.density
        v = self.pad + self.v[r] * self.density
        return self.x + u, self.y + v

    def lookup(self):
        """For every texel of the chart: (mask, point, normal, tag, along, (row, col) as floats, (texels across from the
        anchor column, texels down from the first row))."""
        part = self.part
        G = part.grid
        R, C, _ = G.shape
        ring = np.concatenate([G, G[:, :1]], axis=1) if part.closed else G
        tags = np.concatenate([part.tags, part.tags[:1]]) if part.closed else part.tags
        W, H = self.w, self.h
        ty, tx = np.mgrid[0:H, 0:W] + 0.5
        vv = (ty - self.pad) / self.density
        rr = np.interp(vv, self.v, np.arange(R))
        r0 = np.clip(np.floor(rr).astype(int), 0, R - 2) if R > 1 else np.zeros_like(rr, dtype=int)
        fr = np.clip(rr - r0, 0, 1)
        out_v = (vv < -0.5 / self.density) | (vv > self.v[-1] + 0.5 / self.density)
        r1 = np.minimum(r0 + 1, R - 1)
        perim = self.perim[r0] * (1 - fr) + self.perim[r1] * fr
        anchor = self.anchor[r0] * (1 - fr) + self.anchor[r1] * fr
        arc_u = (tx - self.mid) / self.density + anchor
        mask = ~out_v & (arc_u >= -0.5 / self.density) & (arc_u <= perim + 0.5 / self.density)
        # the column under this texel on each bounding row (each row laid flat about its own anchor), then blended
        cols = np.arange(self.arc.shape[1])
        rel = (tx - self.mid) / self.density
        per_row = np.stack([np.interp(rel + self.anchor[r], self.arc[r], cols) for r in range(R)])  # (R, H, W)
        hh, ww = np.indices(rel.shape)
        cc = per_row[r0, hh, ww] * (1 - fr) + per_row[r1, hh, ww] * fr
        c0 = np.clip(np.floor(cc).astype(int), 0, ring.shape[1] - 2)
        fc = np.clip(cc - c0, 0, 1)

        def bil(A):
            a00, a01 = A[r0, c0], A[r0, c0 + 1]
            a10, a11 = A[r1, c0], A[r1, c0 + 1]
            f1, f2 = fr[..., None], fc[..., None]
            return (a00 * (1 - f2) + a01 * f2) * (1 - f1) + (a10 * (1 - f2) + a11 * f2) * f1

        point = bil(ring)
        # a smooth normal for painting: from the grid's local tangents
        dr = np.gradient(ring, axis=0) if R > 1 else np.zeros_like(ring)
        dc = np.gradient(ring, axis=1)
        nrm = np.cross(dc, dr)
        nrm /= np.maximum(np.linalg.norm(nrm, axis=2, keepdims=True), 1e-9)
        normal = bil(nrm)
        normal /= np.maximum(np.linalg.norm(normal, axis=-1, keepdims=True), 1e-9)
        tag = tags[c0] * (1 - fc) + tags[c0 + 1] * fc
        along = np.interp(rr, np.arange(R), part.along)
        du = np.floor(tx - self.mid).astype(int)          # texels across from the row's anchor column
        dv = np.floor(ty - self.pad).astype(int)          # texels down from the part's first row
        return mask, point, normal, tag, along, rr, cc, du, dv


def pack(charts, width=128):
    """Shelf-packs charts tallest first; returns the atlas height (a power of two)."""
    x = y = row = 0
    for ch in sorted(charts, key=lambda c: (-c.h, -c.w)):
        if ch.w > width:
            raise ValueError(f"{ch.part.name}: chart {ch.w} wide, atlas {width}")
        if x + ch.w > width:
            x, y, row = 0, y + row, 0
        ch.x, ch.y = x, y
        x += ch.w
        row = max(row, ch.h)
    height = y + row
    p = 16
    while p < height:
        p *= 2
    return p


def dilate(img, mask, steps=2):
    """Bleeds each chart's edge texels outward so the sampler never reads a gap."""
    img, mask = img.copy(), mask.copy()
    for _ in range(steps):
        grown = mask.copy()
        acc = np.zeros(img.shape, dtype=float)
        cnt = np.zeros(mask.shape, dtype=float)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            m = np.roll(np.roll(mask, dy, 0), dx, 1)
            v = np.roll(np.roll(img, dy, 0), dx, 1)
            add = m & ~mask
            acc[add] += v[add]
            cnt[add] += 1
            grown |= add
        new = grown & ~mask
        img[new] = (acc[new] / cnt[new][:, None]).astype(img.dtype)
        mask = grown
    return img, mask


# ---------------------------------------------------------------------------------------------------------------------
# Building a weapon: triangles, charts, painting, the file

class Built:
    """A weapon ready to write: de-indexed triangles with normals, uvs (into the whole texture; the albedo half's) and
    along."""

    def __init__(self, pos, nrm, uv, along, albedo, material, anchors):
        self.pos, self.nrm, self.uv, self.along = pos, nrm, uv, along
        self.albedo, self.material, self.anchors = albedo, material, anchors
        # light lifting off the weapon: (kind, rgb, rate per second, spread radius, polyline points), item space
        self.streams = []

    def texture(self):
        """The game's texture: the albedo and its material map side by side."""
        return np.concatenate([self.albedo, self.material], axis=1)


def build(parts, painter, density=2.0, width=128, to_item=None, anchors=()):
    """parts in sword space; painter(part, mask, point, normal, tag, along, rr, cc, du, dv) -> (albedo, material) RGBA uint8;
    to_item maps sword space to item space (0..16 units)."""
    charts = [Chart(p, density) for p in parts]
    height = pack(charts, width)
    albedo = np.zeros((height, width, 4), dtype=np.uint8)
    material = np.zeros((height, width, 4), dtype=np.uint8)
    filled = np.zeros((height, width), dtype=bool)
    P, N, UV, A = [], [], [], []
    for part, ch in zip(parts, charts):
        looked = ch.lookup()
        mask = looked[0]
        a, m = painter(part, *looked)
        sl = (slice(ch.y, ch.y + ch.h), slice(ch.x, ch.x + ch.w))
        albedo[sl][mask] = a[mask]
        material[sl][mask] = m[mask]
        filled[sl] |= mask
        tris, norms, rc = triangles(part)
        for t, n, k in zip(tris, norms, rc):
            for corner in range(3):
                u, v = ch.uv(k[corner][0], k[corner][1])
                P.append(t[corner])
                N.append(n[corner])
                UV.append((u / (2 * width), v / height))  # texture coordinates: the albedo is the left half
                A.append(part.along[int(k[corner][0])])
    albedo, grown = dilate(albedo, filled)
    material, _ = dilate(material, filled)
    P = np.array(P)
    if to_item is not None:
        P = to_item(P)
        N = to_item(np.array(N), direction=True)
    return Built(P, np.array(N), np.array(UV), np.array(A), albedo, material, list(anchors))


STREAMS = 1  # header flag: streams follow the anchors (kind, rgb, rate, radius, then their polyline)


def write(path, built, accent):
    """The game's mesh (read by dev.awaken.arms.WeaponMesh): header, corners (item space /16, normal, along, uv), glow
    anchors, then (flag STREAMS) the streams of light that lift off it: embers, wisps, motes."""
    n = len(built.pos)
    streams = getattr(built, "streams", [])
    with open(path, "wb") as fh:
        fh.write(MAGIC)
        fh.write(struct.pack("<HHI", VERSION, STREAMS if streams else 0, n))
        fh.write(struct.pack("<BBBB", *accent, 0))
        for p, q, uv, al in zip(built.pos / 16.0, built.nrm, built.uv, built.along):
            nq = np.clip(np.round(q * 127), -127, 127).astype(int)
            fh.write(struct.pack("<fffbbbBHH", p[0], p[1], p[2], nq[0], nq[1], nq[2], int(round(al * 255)),
                                 int(round(min(max(uv[0], 0), 1) * 65535)), int(round(min(max(uv[1], 0), 1) * 65535))))
        fh.write(struct.pack("<H", len(built.anchors)))
        for (x, y, z), radius, rgb, kind in built.anchors:
            fh.write(struct.pack("<ffffBBBB", x / 16.0, y / 16.0, z / 16.0, radius / 16.0, *rgb, kind))
        if streams:
            fh.write(struct.pack("<H", len(streams)))
            for kind, rgb, rate, radius, pts in streams:
                fh.write(struct.pack("<BBBBBBf", kind, *rgb, int(round(rate)), len(pts), radius / 16.0))
                for x, y, z in pts:
                    fh.write(struct.pack("<fff", x / 16.0, y / 16.0, z / 16.0))


def read(path):
    """Reads a mesh back (for checks and the preview): dict of arrays."""
    data = open(path, "rb").read()
    assert data[:4] == MAGIC
    version, flags, n = struct.unpack_from("<HHI", data, 4)
    accent = struct.unpack_from("<BBBB", data, 12)[:3]
    off = 16
    rec = struct.Struct("<fffbbbBHH")
    rows = [rec.unpack_from(data, off + i * rec.size) for i in range(n)]
    off += n * rec.size
    (k,) = struct.unpack_from("<H", data, off)
    off += 2
    anchors = [struct.unpack_from("<ffffBBBB", data, off + i * 20) for i in range(k)]
    off += k * 20
    streams = []
    if flags & STREAMS:
        (m,) = struct.unpack_from("<H", data, off)
        off += 2
        for _ in range(m):
            kind, r, g, b, rate, npts, radius = struct.unpack_from("<BBBBBBf", data, off)
            off += 10
            pts = [struct.unpack_from("<fff", data, off + j * 12) for j in range(npts)]
            off += npts * 12
            streams.append((kind, (r, g, b), rate, radius, pts))
    a = np.array(rows, dtype=float)
    return dict(version=version, accent=accent, pos=a[:, 0:3], nrm=a[:, 3:6] / 127.0, along=a[:, 6] / 255.0,
                uv=a[:, 7:9] / 65535.0, anchors=anchors, streams=streams)
