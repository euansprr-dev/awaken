"""Forms (SPEC 21.11, 21.13): the eyes of the vanilla bodies a creature keeps in D land (and with forms on LITE, or for a body not built).

From C up a formed creature wears a body of its own (tools/art/specs/forms_bodies.py). The vanilla body keeps only the forms' eyes,
painted on each vanilla texture layout (pig, cow, sheep, chicken, zombie, skeleton, spider, creeper):
  textures/entity/forms/<layout>/o1.png   the sockets: the eye pixels dark, drawn over the body (cutout)
  textures/entity/forms/<layout>/g<r>.png the light in them at rung r: a hot core toward the nose, a rim of the rank's colour (emissive)
Vanilla textures are never copied: only where their eyes are (u, v, w, h) is known here.
Run: python3 tools/art/build_all.py forms
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
from pixelart import Canvas, hex_rgba  # noqa: E402

# Vanilla layouts: texture size, the cubes of the vanilla body (u, v, w, h, d, role) and the eyes (u, v, w, h)
LAYOUT = {
    "pig": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 4, 3, 1, "head"), (28, 8, 10, 16, 8, "body"), (0, 16, 4, 6, 4, "leg")], eyes=[(8, 11, 2, 1), (14, 11, 2, 1)]),
    "cow": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 6, "head"), (18, 4, 12, 18, 10, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(6, 9, 2, 1), (12, 9, 2, 1)]),
    "sheep": dict(size=(64, 32), cubes=[(0, 0, 6, 6, 8, "head"), (28, 8, 8, 16, 6, "body"), (0, 16, 4, 12, 4, "leg")], eyes=[(8, 10, 2, 1), (12, 10, 2, 1)]),
    "chicken": dict(size=(64, 32), cubes=[(0, 0, 4, 6, 3, "head"), (0, 9, 6, 8, 6, "body"), (26, 0, 3, 5, 3, "leg"), (24, 13, 1, 4, 6, "limb")], eyes=[(3, 4, 1, 1), (6, 4, 1, 1)]),
    "zombie": dict(size=(64, 64), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 4, 12, 4, "limb"), (0, 16, 4, 12, 4, "leg"), (32, 48, 4, 12, 4, "limb"), (16, 48, 4, 12, 4, "leg")], eyes=[(9, 12, 2, 1), (13, 12, 2, 1)]),
    "skeleton": dict(size=(64, 32), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 2, 12, 2, "limb"), (0, 16, 2, 12, 2, "leg")], eyes=[(9, 11, 2, 2), (13, 11, 2, 2)]),
    "spider": dict(size=(64, 32), cubes=[(32, 4, 8, 8, 8, "head"), (0, 0, 6, 6, 6, "body"), (0, 12, 10, 8, 12, "body"), (18, 0, 16, 2, 2, "leg")], eyes=[(41, 14, 2, 1), (45, 14, 2, 1), (42, 16, 1, 1), (45, 16, 1, 1), (40, 13, 1, 1), (47, 13, 1, 1)]),
    "creeper": dict(size=(64, 32), cubes=[(0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (0, 16, 4, 6, 4, "leg")], eyes=[(9, 10, 2, 2), (13, 10, 2, 2)]),
}

# The rank colours (D gold, C orange, B red, A violet, S indigo), as the plate's
RANK = {1: "#e4d063", 2: "#f29a48", 3: "#ec5656", 4: "#b07cff", 5: "#8c7bff"}
SOCKET = hex_rgba("#070806")


def _mix(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


def eyes(layout, r):
    """(sockets, glow) for a layout at rung r."""
    size = LAYOUT[layout]["size"]
    sock, gl = Canvas(*size), Canvas(*size)
    rects = LAYOUT[layout]["eyes"]
    mid = (min(u for u, _, _, _ in rects) + max(u + w for u, _, w, _ in rects)) / 2.0
    rank = hex_rgba(RANK[r])
    core = _mix(rank, (255, 255, 255), 0.62) + (255,)
    for (u, v, w, h) in rects:
        for yy in range(v, v + h):
            for xx in range(u, u + w):
                sock.set(xx, yy, SOCKET)
        # the core: the pixel nearest the nose on the eye's lowest row (a look from under the brow); the rest a dimmer rim
        cx = u + w - 1 if u + w / 2.0 < mid else u
        cy = v + h - 1
        for yy in range(v, v + h):
            for xx in range(u, u + w):
                a = 255 if (xx, yy) == (cx, cy) else 150 if yy == cy else 80
                gl.set(xx, yy, core if a == 255 else rank[:3] + (a,))
    return sock, gl


def build(out):
    for layout in LAYOUT:
        out.entity(f"forms/{layout}/o1", eyes(layout, 1)[0])
        for r in range(1, 6):
            out.entity(f"forms/{layout}/g{r}", eyes(layout, r)[1])
