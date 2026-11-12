"""The first-person placement (WingLayer.placeFirst and AscentRender.firstPersonWings since 12 Nov 2026), mirrored for
tools/ascent/fpwings.py.

Your wings are drawn in the camera's own frame, not the body's: a figure lying along the view (or standing in it for a
hover, a landing or an opening) whose neck sits `back` behind the eye and `up` over it, each wing turned toward the view
about its root by `sweep` degrees, giving way softly so the turn and the tip's swing forward never pass `reach`; and the
whole scaled across the frame by the real field of view against a 70 degree 16:9 one, so the feathers land on the same
place of the frame at any field of view or window shape.
"""
import math

D = math.pi / 180
TAN_V0 = math.tan(35 * D)
TAN_H0 = TAN_V0 * 16 / 9
# a pair larger than the first rung's is drawn at its size (Skyborn's 1.18 and the Sovereign's 1.26 reached a fifth of the frame)
SIZE_MAX = 0.94
# WingFeathers.ROOT_X, ROOT_Y, ROOT_Z, PAIR_Z (texels)
ROOT_X, ROOT_Y, ROOT_Z, PAIR_Z = 3.2, 3.0, 4.0, 0.9

P = {
    "back": 1.0,    # the neck this far behind the eye (blocks)
    "up": 0.25,     # and this far over it
    "sweep": 60.0,  # each wing turned toward the view by this (degrees)
    "reach": 65.0,  # and the turn with the tip's swing forward never past this, softly
}


def drawn(pair):
    """A pair as your own eyes get it: no larger than the first rung's, and a main pair (rolled under 10 degrees) not rolled."""
    return [min(pair[0], SIZE_MAX), 0.0 if abs(pair[1]) <= 10 else pair[1], pair[2]]


def tip_flap(pose):
    return pose[3] + pose[4] + pose[5]


def smin(a, b, r):
    h = max(0.0, min(1.0, 0.5 + 0.5 * (b - a) / r))
    return b + (a - b) * h - r * h * (1 - h)


def sweep(pose, prm, lean):
    # a wing swung toward the belly reaches forward by the sine of the lean: the turn gives way
    return smin(prm["sweep"], prm["reach"] + tip_flap(pose) * math.sin(lean * D), 8.0)


def place(pts, s, index, lean, pose, cam, fov, aspect, prm=None):
    """Model points (blocks; x left, y down from the neck, z off the back) of side s (+1 left) to world points."""
    prm = prm or P
    right, up, fwd = cam
    tan_v = math.tan(fov / 2 * D)
    tan_h = tan_v * aspect
    kx, ky = tan_h / TAN_H0, tan_v / TAN_V0
    sl, cl = math.sin(lean * D), math.cos(lean * D)

    def cam_of(x, y, z):
        # camera-local: x right, y up, z behind
        return -x, prm["up"] - y * sl + z * cl, prm["back"] + y * cl + z * sl

    r0 = cam_of(s * ROOT_X / 16, ROOT_Y / 16, (ROOT_Z + index * PAIR_Z) / 16)
    a = -s * sweep(pose, prm, lean) * D
    c, sn = math.cos(a), math.sin(a)
    res = []
    for x, y, z in pts:
        cx, cy, cz = cam_of(x, y, z)
        # about the camera's up through the root (JOML rotateY(a)): the outer end toward the view
        dx, dz = cx - r0[0], cz - r0[2]
        cx, cz = r0[0] + dx * c + dz * sn, r0[2] - dx * sn + dz * c
        res.append(right * (cx * kx) + up * (cy * ky) - fwd * cz)
    return res
