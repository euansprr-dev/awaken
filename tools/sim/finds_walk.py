"""Walk simulation for the finds as built (Sites.java numbers): how often you meet one, and how long the quiet stretches are.
A model, not the game: value noise like Sites.field, the cell chances, an assumed share of cells whose ground suits the kind."""
import numpy as np, sys
rng = np.random.default_rng(11)
CELL = [96, 256]; EDGE = [18, 44]
BASE = [float(sys.argv[1]), float(sys.argv[2])] if len(sys.argv) > 2 else [0.50, 0.44]
QUIET = float(sys.argv[3]) if len(sys.argv) > 3 else 0.42
N = 24000  # blocks a side
def lattice(n, sx): return np.random.default_rng(sx).random((n + 2, n + 2))
def value(L, x, z):
    ix = np.floor(x).astype(int); iz = np.floor(z).astype(int)
    fx = x - ix; fz = z - iz
    fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz)
    a = L[ix, iz]; b = L[ix + 1, iz]; c = L[ix, iz + 1]; d = L[ix + 1, iz + 1]
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz
L1 = lattice(N // 1100 + 2, 1); L2 = lattice(N // 420 + 2, 2)
def field(x, z): return 0.62 * value(L1, x / 1100.0, z / 1100.0) + 0.38 * value(L2, x / 420.0, z / 420.0)
def rich(x, z, layer):
    n = field(x, z)
    t = np.clip((n - QUIET) / (1 - QUIET), 0, 1); t = t * t * (3 - 2 * t)
    return np.where(n < QUIET, 0.0 if layer == 0 else 0.12, 0.45 + 1.9 * t)
gx, gz = np.meshgrid(np.arange(0, N, 40), np.arange(0, N, 40))
f = field(gx.astype(float), gz.astype(float))
print("quiet land: %.0f%% of the map is below the line (no curios, few finds)" % (100 * (f < QUIET).mean()))
for fit in (0.6, 0.8):
    sites = []
    for layer in (0, 1):
        n = N // CELL[layer]
        cx, cz = np.meshgrid(np.arange(n), np.arange(n))
        x = cx * CELL[layer] + EDGE[layer] + rng.integers(0, CELL[layer] - 2 * EDGE[layer], cx.shape)
        z = cz * CELL[layer] + EDGE[layer] + rng.integers(0, CELL[layer] - 2 * EDGE[layer], cx.shape)
        chance = np.minimum(1, BASE[layer] * rich(x.astype(float), z.astype(float), layer)) * fit
        keep = rng.random(cx.shape) < chance
        sites.append(np.stack([x[keep], z[keep]], 1))
        print("  fit %.1f layer %d: %d sites, one per %.0f x %.0f blocks on average" % (fit, layer, keep.sum(), (N * N / keep.sum()) ** .5, (N * N / keep.sum()) ** .5))
    P = np.concatenate(sites).astype(float)
    gaps = []; quiet_time = 0; total = 0
    SEE = 36.0; SPEED = 5.0
    for w in range(400):
        a = rng.uniform(3000, N - 3000, 2); ang = rng.uniform(0, 2 * np.pi); length = 6000.0
        d = np.array([np.cos(ang), np.sin(ang)]); b = a + d * length
        if not (0 < b[0] < N and 0 < b[1] < N): continue
        rel = P - a; along = rel @ d; off = np.abs(rel @ np.array([-d[1], d[0]]))
        hit = np.sort(along[(along > 0) & (along < length) & (off < SEE)])
        if len(hit) < 2: gaps.append(length / SPEED); continue
        g = np.diff(np.concatenate([[0], hit, [length]])) / SPEED
        gaps.extend(g.tolist()); total += length / SPEED
    g = np.array(gaps)
    print("  fit %.1f walking (a find counts when you pass within %d blocks): mean %.0f s between finds, median %.0f s, one gap in ten over %.0f s, longest %.1f min"
          % (fit, SEE, g.mean(), np.median(g), np.quantile(g, .9), g.max() / 60))
    print("    share of walking time spent in a stretch of 3 minutes or more with nothing: %.0f%%;  of 6 minutes or more: %.0f%%"
          % (100 * g[g >= 180].sum() / g.sum(), 100 * g[g >= 360].sum() / g.sum()))
