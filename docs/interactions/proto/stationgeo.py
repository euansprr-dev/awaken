"""The station scenes' geometry, ported from dev.awaken.anims.station.StationView (SPEC 24), so the plan's mock-ups are
drawn in the game's own camera: the layout, the view that frames a station, where a world point lands on the screen.
Planning tool only (docs/interactions/PLAN.md); nothing in the game reads it."""
import math
import numpy as np

CELL = 3 / 16.0
GRID_HALF = 1.5 * CELL
RESULT_IN, RESULT_HALF = 0.41, 0.085
FURNACE_DEPTH = 3.0 / 16
IMAGE_W, IMAGE_H, LINE = 176, 166, 10
MARGIN = 1.06


def gui_scale(fb_w, fb_h):
    s = 1
    while fb_w // (s + 1) >= 320 and fb_h // (s + 1) >= 240:
        s += 1
    return s


def layout(gui_w, gui_h, hint_lines, side_panels=False):
    left, top = (gui_w - IMAGE_W) // 2, gui_h - 165
    px0, py0, px1, py1 = left - 4, top + 80, left + IMAGE_W + 4, top + 162
    hint_y = py0 - 7 - LINE * hint_lines
    half = max(IMAGE_W // 2 + 4, gui_w // 4) if side_panels else gui_w // 2 - 8
    return dict(left=left, top=top, px0=px0, py0=py0, px1=px1, py1=py1, hintY=hint_y,
                rx0=gui_w // 2 - half, ry0=6, rx1=gui_w // 2 + half, ry1=hint_y - 4)


def rot(yaw, pitch):
    """Camera axes (right, up, forward) for a Minecraft look, world space."""
    y, p = math.radians(yaw), math.radians(pitch)
    f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    r = np.cross(f, np.array([0.0, 1.0, 0.0]))
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return r, u, f


class Station:
    def __init__(self, pos, kind, fx, fz):
        self.pos, self.kind, self.fx, self.fz = pos, kind, fx, fz
        self.rx, self.rz = -fz, fx
        self.slots = 10 if kind == 'table' else 3

    def spot(self, slot):
        bx, by, bz = self.pos
        if self.kind == 'furnace':
            fx0, fz0 = bx + 0.5 - self.fx * 0.5, bz + 0.5 - self.fz * 0.5
            side = 0.15 if slot == 2 else -0.15 if slot == 0 else 0
            return np.array([fx0 + self.fx * FURNACE_DEPTH + self.rx * side, by + (0.26 if slot == 1 else 0.57),
                             fz0 + self.fz * FURNACE_DEPTH + self.rz * side])
        cx, cy, cz = bx + 0.5, by + 1.0, bz + 0.5
        if slot == 0:
            return np.array([cx - self.fx * RESULT_IN, cy + 0.012, cz - self.fz * RESULT_IN])
        row, col = (slot - 1) // 3, (slot - 1) % 3
        u, v = (col - 1) * CELL, (1 - row) * CELL
        return np.array([cx + self.rx * u + self.fx * v, cy + 0.01, cz + self.rz * u + self.fz * v])

    def reach(self, slot, axis):
        if self.kind == 'furnace':
            return 0.05 if axis == 2 else 0.15
        if axis == 1:
            return 0.03
        return RESULT_HALF if slot == 0 else CELL / 2

    def corners(self, slot):
        p = self.spot(slot)
        ma, mu, mi = self.reach(slot, 0), self.reach(slot, 1), self.reach(slot, 2)
        out = []
        for c in range(8):
            a = -ma if c & 1 == 0 else ma
            up = -mu if c & 2 == 0 else mu
            i = -mi if c & 4 == 0 else mi
            out.append(p + np.array([self.rx * a + self.fx * i, up, self.rz * a + self.fz * i]))
        return out


def frame(st, eye, lay, gui_w, gui_h, aspect, max_fov=100.0):
    eye = np.array(eye, dtype=float)
    lo, hi = np.full(3, 1e9), np.full(3, -1e9)
    for i in range(st.slots):
        p = st.spot(i)
        m, mu = max(st.reach(i, 0), st.reach(i, 2)), st.reach(i, 1)
        lo = np.minimum(lo, p - np.array([m, mu, m]))
        hi = np.maximum(hi, p + np.array([m, mu, m]))
    a = (lo + hi) / 2 - eye
    yaw = math.degrees(math.atan2(-a[0], a[2]))
    pitch = -math.degrees(math.atan2(a[1], math.hypot(a[0], a[2])))
    nx0, nx1 = lay['rx0'] * 2.0 / gui_w - 1, lay['rx1'] * 2.0 / gui_w - 1
    ny0, ny1 = 1 - lay['ry0'] * 2.0 / gui_h, 1 - lay['ry1'] * 2.0 / gui_h
    hx, hy, ncx, ncy = (nx1 - nx0) / 2, (ny0 - ny1) / 2, (nx0 + nx1) / 2, (ny0 + ny1) / 2
    tan_max = math.tan(math.radians(max_fov) / 2)
    tan = tan_max
    for _ in range(4):
        r, u, f = rot(yaw, pitch)
        b = [1e9, -1e9, 1e9, -1e9]
        for i in range(st.slots):
            for w in st.corners(i):
                d = w - eye
                z = max(0.05, float(d @ f))
                tx, ty = float(d @ r) / z, float(d @ u) / z
                b = [min(b[0], tx), max(b[1], tx), min(b[2], ty), max(b[3], ty)]
        tan = min(tan_max, max(0.05, max((b[1] - b[0]) / (2 * aspect * hx), (b[3] - b[2]) / (2 * hy)) * MARGIN))
        yaw += math.degrees(math.atan((b[0] + b[1]) / 2) - math.atan(ncx * tan * aspect))
        pitch += math.degrees(math.atan(ncy * tan) - math.atan((b[2] + b[3]) / 2))
    return yaw, pitch, math.degrees(2 * math.atan(tan))


class Camera:
    def __init__(self, eye, yaw, pitch, fov, aspect):
        self.eye = np.array(eye, dtype=float)
        self.yaw, self.pitch, self.fov, self.aspect = yaw, pitch, fov, aspect
        self.r, self.u, self.f = rot(yaw, pitch)
        self.tan = math.tan(math.radians(fov) / 2)

    def project(self, p):
        """World point to (x, y) in 0..1 of the frame (y down), and its depth."""
        d = np.array(p, dtype=float) - self.eye
        z = float(d @ self.f)
        if z <= 1e-4:
            return None
        nx, ny = float(d @ self.r) / z / (self.tan * self.aspect), float(d @ self.u) / z / self.tan
        return (nx + 1) / 2, (1 - ny) / 2, z

    def ray(self, sx, sy):
        nx, ny = sx * 2 - 1, 1 - sy * 2
        d = self.f + self.r * (nx * self.tan * self.aspect) + self.u * (ny * self.tan)
        return d / np.linalg.norm(d)
