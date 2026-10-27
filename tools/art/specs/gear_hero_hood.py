"""Veilwoven's hero hood (branch pack/armour-hero-piece, never merged): the one-piece retry after Look A was called clunky.

Look A built armour from inflated boxes at one texel per skin pixel. This piece drops both: the hood is a real mesh with smooth
normals (a domed silk shell with thickness, drape folds low on the back, a rolled hem), a twisted satin cord bound around the face,
a silver circlet that rises to a mount at the brow, and one cut gem with flat facets. Every surface has its own island in a dense
atlas (about 12 texels to a skin pixel) with the shade baked from the geometry, and a material map in the right half
(r metal, g gloss, b glow) that the hero shader (shaders/core/armour_hero.fsh) reads so silk, satin, silver and gem answer the
light differently.

    python3 tools/art/specs/gear_hero_hood.py               writes the mesh and the atlas, then runs the checks
    python3 tools/art/specs/gear_hero_hood.py check         only the checks
    python3 tools/art/specs/gear_hero_hood.py preview OUT   also a software preview (four views) to OUT.png; geometry only,
                                                            the game is the judge

What it writes (src/main/resources/assets/awaken/):
    gear_premium/veilwoven_hood.mesh                     big-endian: "AWHM", int schema, int quads, then per vertex
                                                         x y z nx ny nz u v (floats; head-local model units, y down)
    textures/entity/equipment/premium/veilwoven_hood.png 1024x512: albedo left, material right

Frame while building: Y up, the head cube is x -4..4, Y 0..8, z -4..4 with the face at z = -4. Written as the model's y = -Y.
"""
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources/assets/awaken")
MESH = os.path.join(RES, "gear_premium/veilwoven_hood.mesh")
TEX = os.path.join(RES, "textures/entity/equipment/premium/veilwoven_hood.png")
SCHEMA = 1
AW, AH = 1024, 512          # atlas; the albedo is u < 512, the material map the same island + 512
HALF = 512

# ---- the shape -----------------------------------------------------------------------------------------------------
TH = 0.34                   # silk shell thickness
W_OPEN = 3.75               # half-width of the face opening (the eyes are within x = +-3)
Y5, H_ARCH = 4.9, 1.6       # the opening is straight-sided to Y5, then a round arch up to the apex
APEX = Y5 + H_ARCH
YS, YT = 7.7, 11.3          # the cowl starts to close above the head at YS; its point is at YT
AX, AZF, AZB = 5.12, 5.25, 5.62
PEAK_BACK = 2.6             # how far back the point falls
CORD_R = 0.30               # the satin cord bound around the face
BAND_OUT, BAND_IN = 0.12, 0.07
NU = 72                     # columns of the shell, left rim around the back to the right rim
NB = 96                     # columns of the circlet
BAND_CAP, BAND_FACE = 5, 9  # rows of each rounded edge and of the face
THREAD_FROM = 3.4           # the thread starts this far down the face edge from the arch's top (the circlet owns the brow)


def smooth(a, b, x):
    t = np.clip((np.asarray(x, dtype=float) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def surf(theta, Y):
    """The outer silk surface at polar angle theta (0 at the back, +-pi at the front) and height Y."""
    theta = np.asarray(theta, dtype=float)
    Y = np.asarray(Y, dtype=float)
    t = np.clip((Y - YS) / (YT - YS), 0.0, 1.0)
    k = np.maximum(1 - t ** 1.4, 0.0) ** (1 / 1.4)              # a soft point, not a dome
    fx = 1 + 0.235 * smooth(4.2, -0.2, Y) ** 1.2                 # the cowl flares out over the shoulders
    fz = 1 + 0.12 * smooth(4.2, -1.5, Y)                       # and a little less in front and behind
    s, c = np.sin(theta), np.cos(theta)
    # square enough at the front to frame the face, round at the back so it reads as cloth, rounder still toward the point
    p = 2.7 + 1.3 * smooth(1.2, 2.2, np.abs(theta))
    p = p - (p - 2.4) * smooth(8.4, YT, Y) - 0.3 * smooth(2.0, -1.4, Y)
    az = np.where(c > 0, AZB, AZF)
    ax = AX * k * fx
    az = az * k * fz
    r = 1.0 / np.maximum((np.abs(s) / np.maximum(ax, 1e-6)) ** p + (np.abs(c) / np.maximum(az, 1e-6)) ** p, 1e-12) ** (1 / p)
    r = np.where(k > 1e-6, r, 0.0)
    x = r * s
    z = r * c
    cz = PEAK_BACK * smooth(7.8, YT, Y) ** 1.5                  # the point falls back
    # the brow leans forward a little over the face
    z = z - 0.85 * np.exp(-(x / 2.0) ** 2) * np.exp(-((Y - 7.1) / 1.2) ** 2) * np.where(c < 0, c * c, 0.0)
    # drape folds from the point down the back and sides, none near the face
    A = 0.44 * smooth(8.0, -1.4, Y)
    wf = 1 - smooth(2.1, 2.7, np.abs(theta))
    u = (1 - np.cos(7 * theta + 0.6 * np.cos(2 * theta))) / 2
    fold = (1 - 2 * u ** 1.6) * A * wf                           # broad ridges, sharper valleys
    rr = np.sqrt(x * x + z * z) + 1e-9
    x = x + fold * x / rr
    z = z + fold * z / rr + cz
    return np.stack([x, Y + 0 * x, z], -1), fold / 0.44


def inside_outer(Q):
    """How far inside the outer silk each point is (negative: outside), measured across the slice."""
    Y = Q[..., 1]
    cz = PEAK_BACK * smooth(7.8, YT, Y) ** 1.5
    th = np.arctan2(Q[..., 0], Q[..., 2] - cz)
    S = surf(th, Y)[0]
    rs = np.hypot(S[..., 0], S[..., 2] - cz)
    rq = np.hypot(Q[..., 0], Q[..., 2] - cz)
    return rs - rq


def surf_normal(theta, Y):
    e = 1e-3
    p0, _ = surf(theta, Y)
    pt = surf(theta + e, Y)[0] - surf(theta - e, Y)[0]
    py = surf(theta, Y + e)[0] - surf(theta, Y - e)[0]
    n = np.cross(pt, py)
    ln = np.linalg.norm(n, axis=-1, keepdims=True)
    n = np.where(ln > 1e-9, n / np.maximum(ln, 1e-12), np.array([0.0, 1.0, 0.0]))
    centre = np.stack([0 * p0[..., 0], p0[..., 1] - 1.5, 0 * p0[..., 0] + 0.3], -1)
    flip = np.sum(n * (p0 - centre), -1, keepdims=True) < 0
    return np.where(flip, -n, n)


def hem_y(theta):
    """The hem rests on the shoulders and hangs lower in front and behind."""
    return -1.55 + 1.6 * np.sin(theta) ** 2


LOW = 15                    # rows from the hem to Y5, each a fraction of its column's height


def rows():
    """(Y, opening half-width) per row above Y5, arch to crown; the low rows are made per column in shell_grid."""
    out = []
    for g in np.linspace(0, math.pi / 2, 15):
        out.append((Y5 + H_ARCH * math.sin(g), W_OPEN * math.cos(g)))
    for u in np.linspace(0, 1, 15)[1:]:
        out.append((APEX + (YT - APEX) * (1 - (1 - u) ** 2), 0.0))
    return out


def edge_theta(Y, w):
    """The angle on the front half where the surface is w from the middle (bisection)."""
    if w <= 1e-6:
        return math.pi
    lo, hi = math.pi / 2, math.pi
    for _ in range(50):
        mid = (lo + hi) / 2
        x = float(surf(mid, Y)[0][0])
        if x > w:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def shell_grid():
    rs = rows()
    nv = LOW + len(rs) - 1
    TH_, YY = np.zeros((nv + 1, NU + 1)), np.zeros((nv + 1, NU + 1))
    apex_row = None
    for j in range(LOW):
        a = j / LOW
        tr = 2.6
        for _ in range(3):
            ye = float(hem_y(tr) + (Y5 - hem_y(tr)) * a)
            tr = edge_theta(ye, W_OPEN + 0.35 * float(smooth(1.0, -1.3, ye)))
        th = np.linspace(-tr, tr, NU + 1)
        TH_[j] = th
        YY[j] = hem_y(th) + (Y5 - hem_y(th)) * a
    for jj, (Y, w) in enumerate(rs):
        j = LOW + jj
        tr = edge_theta(Y, w)
        if w <= 1e-6 and apex_row is None:
            apex_row = j
        TH_[j] = np.linspace(-tr, tr, NU + 1)
        YY[j] = Y
    P, F = surf(TH_, YY)
    N = surf_normal(TH_, YY)
    P[-1] = P[-1].mean(0)
    N[-1] = np.array([0.0, 1.0, 0.0])
    return TH_, YY, P, N, F, apex_row


# ---- mesh assembly ---------------------------------------------------------------------------------------------------
class Mesh:
    def __init__(self):
        self.quads = []   # each: 4 x (pos3, nrm3, uv2 in atlas pixels)

    def grid(self, P, N, U, V):
        """A grid of quads from arrays (rows, cols, ...); U, V in atlas pixels."""
        R, C = P.shape[0], P.shape[1]
        for j in range(R - 1):
            for i in range(C - 1):
                idx = [(j, i), (j, i + 1), (j + 1, i + 1), (j + 1, i)]
                self.quads.append([(P[a, b], N[a, b], (U[a, b], V[a, b])) for a, b in idx])

    def quad(self, ps, n, uvs):
        self.quads.append([(ps[k], n, uvs[k]) for k in range(4)])

    def write(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(b"AWHM")
            f.write(struct.pack(">ii", SCHEMA, len(self.quads)))
            for q in self.quads:
                for p, n, uv in q:
                    nn = np.asarray(n, dtype=float)
                    nn = nn / max(np.linalg.norm(nn), 1e-9)
                    f.write(struct.pack(">8f", p[0], -p[1], p[2], nn[0], -nn[1], nn[2], uv[0] / AW, uv[1] / AH))


ISL = {  # albedo islands (x, y, w, h); the material map is the same + 512
    "outer": (2, 2, 384, 180),
    "inner": (2, 190, 384, 180),
    "cord": (2, 378, 330, 22),
    "hem": (2, 408, 384, 12),
    "band": (2, 428, 420, 30),
    "gem": (396, 2, 64, 8),
    "bezel": (396, 16, 96, 8),
}


def isl_uv(name, s, t):
    """Island texel-centre coordinates for s, t in 0..1 (t = 0 at the island's bottom row)."""
    x, y, w, h = ISL[name]
    return x + 0.5 + np.asarray(s) * (w - 1), y + 0.5 + (1 - np.asarray(t)) * (h - 1)


def frames_along(C, Nref):
    """Tangent, normal (from the reference normals) and binormal along a polyline."""
    T = np.gradient(C, axis=0)
    T /= np.linalg.norm(T, axis=-1, keepdims=True)
    Nn = Nref - np.sum(Nref * T, -1, keepdims=True) * T
    Nn /= np.linalg.norm(Nn, axis=-1, keepdims=True)
    B = np.cross(T, Nn)
    return T, Nn, B


def build():
    TH_, YY, P, N, F, apex = shell_grid()
    nv = P.shape[0] - 1
    m = Mesh()
    s = np.linspace(0, 1, NU + 1)[None, :].repeat(nv + 1, 0)
    t = np.linspace(0, 1, nv + 1)[:, None].repeat(NU + 1, 1)
    U, V = isl_uv("outer", s, t)
    m.grid(P, N, U, V)
    Pin = P - TH * N
    U2, V2 = isl_uv("inner", s, t)
    # the lining only where it can be seen: round the face and up from the hem (the rest is hidden by the head)
    for j in range(nv):
        for i in range(NU):
            if j <= 6 or (j <= apex + 5 and (i < 12 or i >= NU - 12)):
                idx = [(j, i), (j, i + 1), (j + 1, i + 1), (j + 1, i)]
                m.quads.append([(Pin[a, b], -N[a, b], (U2[a, b], V2[a, b])) for a, b in idx])

    # the rolled hem: a half-round from the outer edge to the lining's edge
    E, En = P[0], N[0]
    T_, Nn, B = frames_along(E, En)
    down = E - P[1]
    B = np.where(np.sum(B * down, -1, keepdims=True) < 0, -B, B)
    steps = 7
    HP = np.zeros((steps, NU + 1, 3))
    HN = np.zeros((steps, NU + 1, 3))
    for k, b in enumerate(np.linspace(0, math.pi, steps)):
        HN[k] = np.cos(b) * En + np.sin(b) * B
        HP[k] = E - TH / 2 * En + TH / 2 * HN[k]
    hs = np.linspace(0, 1, NU + 1)[None, :].repeat(steps, 0)
    ht = np.linspace(0, 1, steps)[:, None].repeat(NU + 1, 1)
    m.grid(HP, HN, *isl_uv("hem", hs, ht))

    # the face edge: up the left rim, over the arch, down the right rim
    left = list(range(0, apex + 1))
    edge = [(j, 0) for j in left] + [(j, NU) for j in reversed(left[:-1])]
    EP = np.array([P[j, i] for j, i in edge])
    EN = np.array([N[j, i] for j, i in edge])
    inward = np.array([P[min(j + 1, nv), i] - P[j, i] if j == apex else P[j, 1 if i == 0 else NU - 1] - P[j, i] for j, i in edge])
    T_, Nn, B = frames_along(EP, EN)
    B = np.where(np.sum(B * inward, -1, keepdims=True) > 0, -B, B)   # B points out of the silk, into the opening
    CC = EP - TH / 2 * Nn + 0.10 * B
    arc = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(CC, axis=0), axis=-1))])
    ring = 12
    phis = np.linspace(0, 2 * math.pi, ring + 1)
    CP = np.zeros((len(CC), ring + 1, 3))
    CN = np.zeros_like(CP)
    for k, ph in enumerate(phis):
        CN[:, k] = np.cos(ph) * Nn + np.sin(ph) * B
        CP[:, k] = CC + CORD_R * CN[:, k]
    cs = (arc / arc[-1])[:, None].repeat(ring + 1, 1)
    ct = (phis / (2 * math.pi))[None, :].repeat(len(CC), 0)
    # the cord's island runs u along the cord, v round it: transpose so rows are rings
    CU, CV = isl_uv("cord", cs, ct)
    m.grid(CP, CN, CU, CV)
    # round ends where the cord meets the hem
    for end, sign in ((0, -1.0), (len(CC) - 1, 1.0)):
        c0, tt, nn, bb = CC[end], T_[end] * sign, Nn[end], B[end]
        prev_p = CP[end]
        prev_n = CN[end]
        for a in np.linspace(0, math.pi / 2, 5)[1:]:
            rad = CORD_R * math.cos(a)
            cp = np.zeros((ring + 1, 3))
            cn = np.zeros((ring + 1, 3))
            for k, ph in enumerate(phis):
                radial = math.cos(ph) * nn + math.sin(ph) * bb
                cn[k] = math.cos(a) * radial + math.sin(a) * tt
                cp[k] = c0 + rad * radial + CORD_R * math.sin(a) * tt
            uu, vv = isl_uv("cord", end / (len(CC) - 1), phis / (2 * math.pi))
            for k in range(ring):
                m.quads.append([(prev_p[k], prev_n[k], (uu, vv[k])), (prev_p[k + 1], prev_n[k + 1], (uu, vv[k + 1])),
                                (cp[k + 1], cn[k + 1], (uu, vv[k + 1])), (cp[k], cn[k], (uu, vv[k]))])
            prev_p, prev_n = cp, cn

    band_mesh(m)
    gem_mesh(m)
    return m, (TH_, YY, P, N, F, apex, EP, Nn, B, CC)


def band_rise(theta):
    """How much of the brow mount there is at this angle (1 at the front middle, a point)."""
    d = math.pi - np.abs(theta)
    return np.maximum(0.0, 1 - d / 0.42) ** 1.6


def band_mid(theta):
    """The circlet sits low on the brow and higher at the back, the way a circlet is worn."""
    return 7.75 + 1.45 * ((1 + np.cos(theta)) / 2) ** 1.3


def band_span(theta):
    mm = band_rise(theta)
    mid = band_mid(theta)
    return mid - (0.21 + 0.2 * mm), mid + (0.21 + 0.66 * mm)


def band_mesh(m):
    th = np.linspace(-math.pi, math.pi, NB + 1)
    lo, hi = band_span(th)
    cap, face = BAND_CAP, BAND_FACE
    PP, NN = [], []
    up_of = lambda T, Y: (lambda d: d / np.linalg.norm(d, axis=-1, keepdims=True))(surf(T, Y + 1e-3)[0] - surf(T, Y - 1e-3)[0])
    # bottom cap: from inside the silk round the lower edge to the face
    S0, N0, U0 = surf(th, lo)[0], surf_normal(th, lo), up_of(th, lo)
    C0 = S0 + (BAND_OUT - BAND_IN) / 2 * N0
    rr = (BAND_OUT + BAND_IN) / 2
    for b in np.linspace(math.pi, 0, cap)[:-1]:
        n = math.cos(b) * N0 - math.sin(b) * U0
        PP.append(C0 + rr * n)
        NN.append(n)
    for q in np.linspace(0, 1, face):
        Y = lo + (hi - lo) * q
        S, Nf, Uf = surf(th, Y)[0], surf_normal(th, Y), up_of(th, Y)
        h = BAND_OUT + 0.03 * math.sin(math.pi * q)
        dh = 0.03 * math.pi * math.cos(math.pi * q) / (hi - lo)
        n = Nf - dh[..., None] * Uf
        n /= np.linalg.norm(n, axis=-1, keepdims=True)
        PP.append(S + h * Nf)
        NN.append(n)
    S1, N1, U1 = surf(th, hi)[0], surf_normal(th, hi), up_of(th, hi)
    C1 = S1 + (BAND_OUT - BAND_IN) / 2 * N1
    for b in np.linspace(0, math.pi, cap)[1:]:
        n = math.cos(b) * N1 + math.sin(b) * U1
        PP.append(C1 + rr * n)
        NN.append(n)
    PP, NN = np.array(PP), np.array(NN)
    R = PP.shape[0]
    bs = np.linspace(0, 1, NB + 1)[None, :].repeat(R, 0)
    bt = np.linspace(0, 1, R)[:, None].repeat(NB + 1, 1)
    m.grid(PP, NN, *isl_uv("band", bs, bt))


def gem_frame():
    lo, hi = band_span(math.pi)
    yg = (float(lo) + float(hi)) / 2
    S = surf(math.pi, yg)[0]
    n = surf_normal(math.pi, yg)
    up = surf(math.pi, yg + 1e-3)[0] - surf(math.pi, yg - 1e-3)[0]
    up /= np.linalg.norm(up)
    side = np.cross(up, n)
    side /= np.linalg.norm(side)
    G = S + (BAND_OUT + 0.03) * n
    return G, n, up, side


def gem_mesh(m):
    G, n, up, side = gem_frame()
    K = 8
    phis = [2 * math.pi * k / K + math.pi / K for k in range(K)]
    girdle = [G + 0.29 * math.cos(a) * side + 0.40 * math.sin(a) * up for a in phis]
    table = [G + 0.55 * (g - G) + 0.22 * n for g in girdle]
    apex = G - 0.36 * n

    def cell(c):
        x, y, w, h = ISL["gem"]
        u0 = x + 4 * c + 0.5
        return [(u0, y + 1.5), (u0 + 3, y + 1.5), (u0 + 3, y + 6.5), (u0, y + 6.5)]

    def facet(ps, c):
        nn = np.cross(ps[1] - ps[0], ps[2] - ps[0])
        if np.linalg.norm(nn) < 1e-9:
            nn = np.cross(ps[2] - ps[0], ps[3] - ps[0])
        nn /= np.linalg.norm(nn)
        mid = sum(ps) / 4
        if np.dot(nn, mid - (G - 0.1 * n)) < 0:
            nn = -nn
        m.quad(ps, nn, cell(c))

    for k in range(K):
        a, b = k, (k + 1) % K
        facet([girdle[a], girdle[b], table[b], table[a]], k)            # crown
        facet([girdle[b], girdle[a], apex, apex], 8 + (k % 4))          # pavilion (mostly inside the silk)
    for quad in ([0, 1, 2, 3], [0, 3, 4, 7], [4, 5, 6, 7]):
        facet([table[q] for q in quad], 12)                           # the table
    # the bezel: a silver ring round the girdle, sunk into the circlet
    segs, ring = 40, 8
    ring_pts = []
    for a in np.linspace(0, 2 * math.pi, segs + 1):
        ring_pts.append(G - 0.04 * n + 1.12 * (0.29 * math.cos(a) * side + 0.40 * math.sin(a) * up))
    ring_pts = np.array(ring_pts)
    T = np.gradient(ring_pts, axis=0)
    T /= np.linalg.norm(T, axis=-1, keepdims=True)
    out = ring_pts - (G - 0.04 * n)
    out = out - np.sum(out * T, -1, keepdims=True) * T
    out -= np.outer(out @ n, n)
    out /= np.linalg.norm(out, axis=-1, keepdims=True)
    RP = np.zeros((segs + 1, ring + 1, 3))
    RN = np.zeros_like(RP)
    for k, ph in enumerate(np.linspace(0, 2 * math.pi, ring + 1)):
        RN[:, k] = math.cos(ph) * n + math.sin(ph) * out
        RP[:, k] = ring_pts + 0.075 * RN[:, k]
    rs_ = np.linspace(0, 1, segs + 1)[:, None].repeat(ring + 1, 1)
    rt_ = np.linspace(0, 1, ring + 1)[None, :].repeat(segs + 1, 0)
    m.grid(RP, RN, *isl_uv("bezel", rs_, rt_))


# ---- the atlas -------------------------------------------------------------------------------------------------------
def srgb(c):
    return np.array(c, dtype=float) / 255.0


SILK = srgb((50, 36, 104))        # midnight violet silk
SILK_DEEP = srgb((30, 20, 66))
LINING = srgb((76, 30, 84))       # plum satin
CORD = srgb((196, 190, 226))      # silver-lavender satin cord
THREAD = srgb((222, 206, 255))
SILVER = srgb((206, 210, 224))
GEM = srgb((176, 92, 255))


def bilerp(G, fj, fi):
    j0 = np.clip(np.floor(fj).astype(int), 0, G.shape[0] - 2)
    i0 = np.clip(np.floor(fi).astype(int), 0, G.shape[1] - 2)
    a, b = (fj - j0)[..., None], (fi - i0)[..., None]
    return (G[j0, i0] * (1 - a) * (1 - b) + G[j0, i0 + 1] * (1 - a) * b
            + G[j0 + 1, i0] * a * (1 - b) + G[j0 + 1, i0 + 1] * a * b)


def island_param(name):
    x, y, w, h = ISL[name]
    a = np.arange(w)[None, :].repeat(h, 0)
    b = np.arange(h)[:, None].repeat(w, 1)
    return a / (w - 1), 1 - b / (h - 1)


def nearest_on(points, Q, chunk=4096):
    """Distance from each Q to the polyline through points, and the arc length there."""
    seg_a, seg_b = points[:-1], points[1:]
    d_ab = seg_b - seg_a
    L = np.linalg.norm(d_ab, axis=-1)
    arc0 = np.concatenate([[0], np.cumsum(L)])[:-1]
    flat = Q.reshape(-1, 3)
    dist = np.empty(len(flat))
    arcs = np.empty(len(flat))
    for s0 in range(0, len(flat), chunk):
        q = flat[s0:s0 + chunk][:, None, :]
        tt = np.clip(np.sum((q - seg_a) * d_ab, -1) / np.maximum(L * L, 1e-12), 0, 1)
        proj = seg_a + tt[..., None] * d_ab
        dd = np.linalg.norm(q - proj, axis=-1)
        k = np.argmin(dd, axis=1)
        dist[s0:s0 + chunk] = dd[np.arange(len(k)), k]
        arcs[s0:s0 + chunk] = arc0[k] + tt[np.arange(len(k)), k] * L[k]
    return dist.reshape(Q.shape[:-1]), arcs.reshape(Q.shape[:-1]), arc0[-1] + L[-1]


def vnoise(x, y, seed):
    """Smooth value-ish noise from a few sines; enough to keep a large surface from reading flat."""
    r = np.random.RandomState(seed)
    v = 0
    for _ in range(5):
        a, b, c, d = r.uniform(0.6, 2.2), r.uniform(0.6, 2.2), r.uniform(0, 6.3), r.uniform(0, 6.3)
        v = v + np.sin(a * x + c) * np.sin(b * y + d)
    return v / 5


def paint(geo):
    TH_, YY, P, N, F, apex, EP, Nn, B, CC = geo
    nv = P.shape[0] - 1
    alb = np.zeros((AH, HALF, 3))
    mat = np.zeros((AH, HALF, 3))
    filled = np.zeros((AH, HALF), bool)

    def put(name, col, mtl):
        x, y, w, h = ISL[name]
        alb[y:y + h, x:x + w] = np.clip(col, 0, 1)
        mat[y:y + h, x:x + w] = np.clip(mtl, 0, 1)
        filled[y:y + h, x:x + w] = True

    # the arch of the face edge measured from its top, so the pattern is the same on both sides
    for name in ("outer", "inner"):
        s, t = island_param(name)
        fi, fj = s * NU, t * nv
        Q = bilerp(P, fj, fi)
        if name == "inner":
            Q = Q - TH * bilerp(N, fj, fi)
        fold = bilerp(F[..., None], fj, fi)[..., 0]
        Yq = Q[..., 1]
        d_e, arc, L = nearest_on(EP, Q)
        sarc = np.abs(arc - L / 2)                                   # from the arch's top, both sides alike
        hem_y = bilerp(P[0][None, :, 1:2].repeat(2, 0), fj * 0, fi)[..., 0]
        d_hem = Yq - hem_y
        # surface coordinates in skin pixels, for the weave
        circ = np.abs(s - 0.5) * 32.0
        if name == "outer":
            lo, hi = band_span(bilerp(TH_[..., None], fj, fi)[..., 0])
            d_band = np.maximum(lo - Yq, Yq - hi)
            ao = (1 - 0.34 * np.clip(-fold, 0, 1)) * (1 + 0.08 * np.clip(fold, 0, 1))
            ao *= 0.60 + 0.40 * smooth(0.12, 0.55, d_e)
            ao *= 1 - 0.30 * np.exp(-(np.maximum(d_band, 0) / 0.16) ** 2) * (d_band > -0.05)
            ao *= 0.86 + 0.14 * smooth(-1.4, 9.0, Yq)
            ao *= 1 - 0.18 * np.exp(-(np.maximum(d_hem, 0) / 0.35) ** 2)
            weave = 1 + 0.035 * np.sign(np.sin(2 * math.pi * (circ + Yq) / 0.34)) * np.sign(np.sin(2 * math.pi * (circ - Yq) / 1.7))
            tone = 1 + 0.07 * vnoise(circ * 0.9, Yq * 0.9, 3)
            col = SILK * (ao * weave * tone)[..., None]
            col = col * (1 - 0.25 * smooth(2.0, -1.4, Yq))[..., None] + SILK_DEEP * (0.25 * smooth(2.0, -1.4, Yq) * ao)[..., None]
            mtl = np.stack([0.0 * ao, 0.22 + 0 * ao, 0 * ao], -1)
            # the embroidered thread: one line round the face with a small diamond every 1.35 px, faintly lit from within
            px = 0.08                                                 # about a texel, in skin pixels: soft edges
            cov = lambda dist: np.clip(0.5 - dist / px, 0, 1)
            line = cov(np.abs(d_e - 0.62) - 0.065) * smooth(THREAD_FROM - 0.05, THREAD_FROM + 0.05, sarc)
            ph = ((sarc - THREAD_FROM) / 1.35 + 0.5) % 1.0
            dd = np.abs(ph - 0.5) * 1.35 + np.abs(d_e - 0.62)
            diamond = cov(dd - 0.19) * (sarc > THREAD_FROM - 0.3)
            hollow = cov(dd - 0.09)
            thread = np.maximum(line, diamond) * (1 - hollow) * (d_e < 1.0) * (arc > 0.7) * (arc < L - 0.7)
            col = col * (1 - thread)[..., None] + THREAD * (0.85 + 0.15 * ao)[..., None] * thread[..., None]
            mtl = mtl * (1 - thread)[..., None] + np.array([0.30, 0.62, 0.55]) * thread[..., None]
            put("outer", col, mtl)
        else:
            depth = np.minimum(d_e, np.maximum(d_hem, 0) * 1.2)
            ao = 0.20 + 0.80 * np.exp(-depth / 0.85)
            weave = 1 + 0.025 * np.sign(np.sin(2 * math.pi * (circ * 2 + Yq) / 0.3))
            col = LINING * (ao * weave)[..., None]
            mtl = np.stack([0 * ao, 0.55 + 0 * ao, 0 * ao], -1)
            put("inner", col, mtl)

    # the cord: a two-ply twist
    s, t = island_param("cord")
    x, y, w, h = ISL["cord"]
    L_c = float(np.sum(np.linalg.norm(np.diff(CC, axis=0), axis=-1)))
    ply = ((s * L_c) / 0.42 + t * 2.0) % 1.0
    shade = 0.58 + 0.42 * np.sin(math.pi * ply) ** 0.7
    under = 0.88 + 0.12 * np.cos(2 * math.pi * t)                     # the side against the silk is darker
    col = CORD * (shade * under)[..., None]
    mtl = np.stack([0.50 + 0 * s, 0.55 + 0.25 * np.sin(math.pi * ply), 0 * s], -1)
    put("cord", col, mtl)

    # the hem roll: the same silk, darker as it turns under
    s, t = island_param("hem")
    col = SILK_DEEP * (0.9 - 0.35 * t)[..., None] + 0 * s[..., None]
    put("hem", col, np.stack([0 * s, 0.25 + 0 * s, 0 * s], -1))

    # the circlet: polished silver, an engraved vine along its middle, the edges bright
    s, t = island_param("band")
    x, y, w, h = ISL["band"]
    rows_ = h
    q = t                                                              # 0 bottom edge (inside the silk) .. 1 top edge
    rows_b = 2 * (BAND_CAP - 1) + BAND_FACE
    face_lo, face_hi = (BAND_CAP - 1) / (rows_b - 1), (BAND_CAP - 2 + BAND_FACE) / (rows_b - 1)
    qq = np.clip((q - face_lo) / (face_hi - face_lo), 0, 1)
    around = s * 2 * math.pi * 5.0
    vine = np.abs(qq - 0.5 - 0.16 * np.sin(around * 1.4)) < 0.07
    leaf = (np.abs(qq - 0.5 - 0.16 * np.sin(around * 1.4) - 0.14 * np.cos(around * 1.4)) < 0.05) & (np.cos(around * 1.4) > 0.6)
    eng = vine | leaf
    edge = (q < face_lo) | (q > face_hi)
    col = SILVER * np.where(eng, 0.52, np.where(edge, 1.05, 0.96))[..., None]
    mtl = np.stack([1.0 + 0 * s, np.where(eng, 0.35, np.where(edge, 0.95, 0.85)), 0 * s], -1)
    put("band", col, mtl)

    # the gem: one cell per facet, deeper below, a bright table
    x, y, w, h = ISL["gem"]
    gc = np.zeros((h, w, 3))
    gm = np.zeros((h, w, 3))
    for c in range(16):
        if c < 8:
            v = 0.85 + 0.25 * math.cos(c * 2.1)
        elif c < 12:
            v = 0.5
        else:
            v = 1.25
        gc[:, 4 * c:4 * c + 4] = np.clip(GEM * v + (0.12 if c == 12 else 0.0), 0, 1)
        gm[:, 4 * c:4 * c + 4] = (0.0, 1.0, 0.62 if c != 12 else 0.75)
    alb[y:y + h, x:x + w] = gc
    mat[y:y + h, x:x + w] = gm
    filled[y:y + h, x:x + w] = True

    s, t = island_param("bezel")
    col = SILVER * (0.9 + 0.15 * np.sin(math.pi * t))[..., None] + 0 * s[..., None]
    put("bezel", col, np.stack([1.0 + 0 * s, 0.9 + 0 * s, 0 * s], -1))

    # bleed every island outward so the linear sampler never reads an empty texel
    for _ in range(4):
        grow = filled.copy()
        a2, m2 = alb.copy(), mat.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(filled, (dy, dx), (0, 1))
            take = src & ~grow
            a2[take] = np.roll(alb, (dy, dx), (0, 1))[take]
            m2[take] = np.roll(mat, (dy, dx), (0, 1))[take]
            grow |= take
        alb, mat, filled = a2, m2, grow
    img = np.zeros((AH, AW, 4))
    img[:, :HALF, :3] = alb
    img[:, HALF:, :3] = mat
    img[..., 3] = 1.0
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


# ---- checks ----------------------------------------------------------------------------------------------------------
def box_dist(Q, lo, hi):
    """Signed distance to an axis box (negative inside)."""
    c = (lo + hi) / 2
    e = (hi - lo) / 2
    d = np.abs(Q - c) - e
    out = np.linalg.norm(np.maximum(d, 0), axis=-1)
    inside = np.minimum(np.max(d, axis=-1), 0)
    return out + inside


def checks(m, geo):
    TH_, YY, P, N, F, apex, EP, Nn, B, CC = geo
    bad = []
    arr = np.array([[v[0] for v in q] for q in m.quads])
    if not np.all(np.isfinite(arr)):
        bad.append("a vertex is not finite")
    head_lo, head_hi = np.array([-4.0, 0.0, -4.0]), np.array([4.0, 8.0, 4.0])
    # the lining must clear the head wherever it can be seen: everywhere round the opening and below the crown
    Pin = P - TH * N
    near = np.zeros(P.shape[:2], bool)
    near[: apex + 3, :8] = True
    near[: apex + 3, -8:] = True
    d = box_dist(Pin[near], head_lo, head_hi)
    if d.min() < 0.08:
        bad.append(f"the lining near the face comes within {d.min():.2f} px of the head (want 0.08; reversed-Z depth resolves far less)")
    # the cord must not cover the eyes (x within 3 of the middle, Y 3..4.2) seen from the front
    xin = np.min(np.abs(CC[:, 0][(CC[:, 1] > 2.8) & (CC[:, 1] < 4.4)])) - CORD_R
    if xin < 3.1:
        bad.append(f"the cord reaches x = {xin:.2f} at the eyes (want 3.1 or more)")
    # the face's middle must be open: no outer point in front of the face inside the opening's box
    front = P[..., 2] < -4.0
    inside = (np.abs(P[..., 0]) < 3.0) & (P[..., 1] > 0.0) & (P[..., 1] < 5.0)
    if np.any(front & inside):
        bad.append("the silk crosses the face")
    # the hem roll and the shell share their edge vertices (watertight), and the circlet sits on the silk, not above it
    lo, hi = band_span(np.linspace(-math.pi, math.pi, 64))
    if np.any(hi - lo < 0.4):
        bad.append("the circlet is too thin somewhere")
    if float(band_span(math.pi)[0]) < APEX + CORD_R + 0.45:
        bad.append("the circlet crowds the cord above the face")
    # the head (its edges and corners) must be inside the silk, with a margin
    g = np.linspace(-4, 4, 17)
    pts = []
    for a in g:
        for b in g:
            for Yh in (0.0, 8.0):
                pts.append((a, Yh, b))
            for Yh in np.linspace(0, 8, 17):
                if abs(a) == 4 or abs(b) == 4:
                    pts.append((a, Yh, b))
    pts = np.array(pts)
    # the face itself is open: only check what is not inside the opening's window
    pts = pts[~((pts[:, 2] == -4) & (np.abs(pts[:, 0]) < 3.6) & (pts[:, 1] < 6.0))]
    pts = pts[pts[:, 1] > 1.2]   # below the jaw the chest armour takes over
    depth = inside_outer(pts)
    if depth.min() < 0.06:
        q = pts[np.argmin(depth)]
        bad.append(f"the head pokes out of the silk at {q} ({depth.min():.2f})")
    uv = np.array([[v[2] for v in q] for q in m.quads])
    if uv[..., 0].min() < 0 or uv[..., 0].max() > HALF or uv[..., 1].min() < 0 or uv[..., 1].max() > AH:
        bad.append("a uv is outside the albedo half")
    return bad


# ---- preview ---------------------------------------------------------------------------------------------------------
def preview(m, img, out):
    """A plain software render (four views) with the hero shader's terms, on a skin-coloured head and a body block."""
    tex = img.astype(float) / 255.0
    quads = m.quads
    tris_p, tris_n, tris_uv = [], [], []
    for q in quads:
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            tris_p.append([q[a][0], q[b][0], q[c][0]])
            tris_n.append([q[a][1], q[b][1], q[c][1]])
            tris_uv.append([q[a][2], q[b][2], q[c][2]])
    # context: the head (8^3, face at -z) and the body/shoulders of the shipped chest (11 x 13 x 6 at Y 1 .. -12)
    ctx = []

    def box(lo, hi, col):
        lo, hi = np.array(lo, float), np.array(hi, float)
        for ax in range(3):
            for side in (0, 1):
                n = np.zeros(3)
                n[ax] = 1 if side else -1
                o = [(ax + 1) % 3, (ax + 2) % 3]
                cs = []
                for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    p = lo.copy()
                    p[ax] = hi[ax] if side else lo[ax]
                    p[o[0]] = hi[o[0]] if u else lo[o[0]]
                    p[o[1]] = hi[o[1]] if v else lo[o[1]]
                    cs.append(p)
                ctx.append((cs, n, col))

    box((-4, 0, -4), (4, 8, 4), (0.86, 0.66, 0.52))
    box((-5, -12, -3), (5, 1, 3), (0.16, 0.15, 0.32))
    box((-9, -12, -3), (-5, 1, 3), (0.18, 0.17, 0.36))
    box((5, -12, -3), (9, 1, 3), (0.18, 0.17, 0.36))
    for cs, n, col in ctx:
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            tris_p.append([cs[a], cs[b], cs[c]])
            tris_n.append([n, n, n])
            tris_uv.append([(-1 - col[0], -1 - col[1]), (-1 - col[2], 0), (0, 0)])
    TP, TN, TU = np.array(tris_p), np.array(tris_n, float), np.array(tris_uv, float)
    L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
    L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)
    views = [("front", 0), ("three-quarter", -45), ("side", -90), ("back", 180)]
    S = 420
    panels = []
    for _, yaw in views:
        a = math.radians(yaw)
        target = np.array([0, 3.0, 0])
        dist = 34.0
        eye = target + dist * np.array([math.sin(a), 0.05, -math.cos(a)])
        fwd = (target - eye) / np.linalg.norm(target - eye)
        right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
        upv = np.cross(right, fwd)
        rel = TP - eye
        cx, cy, cz = rel @ right, rel @ upv, rel @ fwd
        f = S / (2 * math.tan(math.radians(17)))
        sx = S / 2 + f * cx / cz
        sy = S / 2 - f * cy / cz
        zb = np.full((S, S), np.inf)
        tid = np.full((S, S), -1)
        bw = np.zeros((S, S, 3))
        for k in range(len(TP)):
            xs, ys = sx[k], sy[k]
            x0, x1 = max(int(xs.min()), 0), min(int(xs.max()) + 1, S - 1)
            y0, y1 = max(int(ys.min()), 0), min(int(ys.max()) + 1, S - 1)
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-12:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1 - w0 - w1
            ins = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not ins.any():
                continue
            z = w0 * cz[k][0] + w1 * cz[k][1] + w2 * cz[k][2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            upd = ins & (z < sub)
            sub[upd] = z[upd]
            tid[y0:y1 + 1, x0:x1 + 1][upd] = k
            bw[y0:y1 + 1, x0:x1 + 1][upd] = np.stack([w0, w1, w2], -1)[upd]
        img_o = np.zeros((S, S, 3)) + np.array([0.55, 0.68, 0.86])
        hit = tid >= 0
        kk = tid[hit]
        w = bw[hit]
        n = np.einsum("pk,pkc->pc", w, TN[kk])
        n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
        uv = np.einsum("pk,pkc->pc", w, TU[kk])
        pos = np.einsum("pk,pkc->pc", w, TP[kk])
        is_ctx = uv[:, 0] < 0
        u = np.clip(uv[:, 0], 0, AW - 1.001) - 0.5
        v = np.clip(uv[:, 1], 0, AH - 1.001) - 0.5
        u0, v0 = np.clip(np.floor(u).astype(int), 0, AW - 2), np.clip(np.floor(v).astype(int), 0, AH - 2)
        fu, fv = (u - u0)[:, None], (v - v0)[:, None]

        def samp(off):
            uu = np.clip(u0 + off, 0, AW - 2)
            return (tex[v0, uu, :3] * (1 - fu) * (1 - fv) + tex[v0, uu + 1, :3] * fu * (1 - fv)
                    + tex[v0 + 1, uu, :3] * (1 - fu) * fv + tex[v0 + 1, uu + 1, :3] * fu * fv)
        alb = samp(0)
        mt = samp(HALF)
        metal, gloss, emit = mt[:, 0], mt[:, 1], mt[:, 2]
        alb_lin = alb
        vdir = eye - pos
        vdir /= np.linalg.norm(vdir, axis=-1, keepdims=True)
        wrap = 0.3
        dl = (np.clip((n @ L0 + wrap) / (1 + wrap), 0, 1) + np.clip((n @ L1 + wrap) / (1 + wrap), 0, 1)) * 0.6 + 0.4
        dl = np.minimum(dl, 1.0)
        ndv = np.clip(np.sum(n * vdir, -1), 0, 1)
        sheen = (1 - metal) * (1 - gloss) * np.clip(1 - ndv, 0, 1) ** 2.5 * 0.9
        col = alb_lin * dl[:, None] + (alb_lin * 0.6 + np.array([0.42, 0.36, 0.62])) * (sheen * (0.5 + 0.5 * dl))[:, None]
        spec = np.zeros(len(n))
        for L in (L0, L1):
            hv = L + vdir
            hv /= np.linalg.norm(hv, axis=-1, keepdims=True)
            ex = 8 + 150 * gloss ** 2
            spec += np.clip(np.sum(n * hv, -1), 0, 1) ** ex * gloss * (0.15 + 0.85 * metal) * (0.3 + 1.2 * gloss)
        rvec = 2 * ndv[:, None] * n - vdir
        env = np.clip(0.45 + 0.55 * rvec[:, 1], 0, 1)[:, None] * np.array([0.8, 0.86, 1.0]) + 0.2
        col_m = alb_lin * (0.22 + 0.9 * env) * (0.7 + 0.3 * dl)[:, None]
        col = col * (1 - metal)[:, None] + col_m * metal[:, None]
        col += spec[:, None] * (np.ones(3) * (1 - metal)[:, None] + alb_lin * metal[:, None])
        col += alb_lin * (emit * 0.35)[:, None]
        ctxc = np.stack([-1 - TU[kk][:, 0, 0], -1 - TU[kk][:, 0, 1], -1 - TU[kk][:, 1, 0]], -1)
        cd = np.minimum((np.clip(n @ L0, 0, 1) + np.clip(n @ L1, 0, 1)) * 0.6 + 0.4, 1)
        col = np.where(is_ctx[:, None], ctxc * cd[:, None], col)
        img_o[hit] = np.clip(col, 0, 1)
        panels.append((img_o * 255).astype(np.uint8))
    sheet = np.concatenate(panels, 1)
    Image.fromarray(sheet).save(out)
    print("preview", out)


def main():
    args = sys.argv[1:]
    m, geo = build()
    bad = checks(m, geo)
    print(f"{len(m.quads)} quads")
    for b in bad:
        print("CHECK:", b)
    if args[:1] == ["check"]:
        sys.exit(1 if bad else 0)
    img = paint(geo)
    if bad:
        print("not written: fix the checks first")
        sys.exit(1)
    m.write(MESH)
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    Image.fromarray(img, "RGBA").save(TEX)
    print("wrote", os.path.relpath(MESH, ROOT), os.path.relpath(TEX, ROOT))
    if args[:1] == ["preview"]:
        preview(m, img, args[1])


if __name__ == "__main__":
    main()
