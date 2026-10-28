"""Paint helpers shared by the kit bodies (fauna_rig.Painter underneath): light stories the bosses read by."""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
import fauna_rig as R  # noqa: E402


def cracks(P, cube, faces, skin_col, glow_cols, n=2, seed=0, alpha=230, length=None):
    """Branching fissures that wander down a face: dark in the skin, lit in the glow (magma, cold light, spores)."""
    rr = random.Random(R._h(cube, seed))
    for fname in faces:
        f = P.face(cube, fname)
        g = P.face(cube, fname, glow=True)
        if f.w < 4 or f.h < 4:
            continue
        for k in range(n):
            x, y = rr.randrange(1, f.w - 1), rr.randrange(0, max(1, f.h // 3))
            steps = length or rr.randint(f.h // 2 + 2, f.h + 4)
            for s in range(steps):
                if not (0 <= x < f.w and 0 <= y < f.h):
                    break
                f.set(x, y, skin_col)
                g.set(x, y, R.hexc(glow_cols[s % len(glow_cols)], alpha if s % 4 else alpha - 50))
                if rr.random() < 0.18 and 0 < x < f.w - 1:  # a short side branch
                    bx = x + rr.choice((-1, 1))
                    f.set(bx, y, skin_col)
                    g.set(bx, y, R.hexc(glow_cols[-1], alpha - 80))
                y += 1
                x += rr.choice((-1, 0, 0, 0, 1))


def radial(P, cube, fname, cols, skin=None, alpha=255):
    """A face lit from its centre outwards (a core, an eye, a lamp): cols run from the centre to the rim."""
    f = P.face(cube, fname)
    g = P.face(cube, fname, glow=True)
    cx, cy = (f.w - 1) / 2, (f.h - 1) / 2
    rmax = max(cx, cy) or 1
    for i, j in f.each():
        d = max(abs(i - cx), abs(j - cy)) / rmax
        k = min(len(cols) - 1, int(d * len(cols)))
        if skin is not None:
            f.set(i, j, skin.at(len(skin) - 1 - k))
        g.set(i, j, R.hexc(cols[k], alpha))


def edge_glow(P, cube, fname, col, rows=1, alpha=220, side="bottom"):
    """A lit edge along one side of a face (a blade's edge, a rim)."""
    g = P.face(cube, fname, glow=True)
    for i, j in g.each():
        on = (j >= g.h - rows) if side == "bottom" else (j < rows) if side == "top" else (i < rows) if side == "left" else (i >= g.w - rows)
        if on:
            g.set(i, j, R.hexc(col, alpha))
