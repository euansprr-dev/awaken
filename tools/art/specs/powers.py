"""Powers module textures: 63 power icons (GUI sprites, family-coloured tiles), constructs, the Power Core, Stunned.

Icons are 16x16 GUI sprites at textures/gui/sprites/power/<id>.png. Each sits on a tile in its family's ramp with a
bright glyph outlined in the darkest shade so it reads at HUD size.
"""
import math

from pixelart import *
from pixelart import _outline

RAMPS.setdefault("teal", ["#08201e", "#0f3833", "#17564e", "#23786c", "#47c6b0", "#a6f0e2"])
RAMPS.setdefault("jade", ["#0f2a22", "#1b4436", "#2a6650", "#3f8f6e", "#62b98f", "#b8f0cf"])

FAMILY_RAMP = {
    "kinetic": "amber", "velocity": "crimson", "flight": "frost", "energy": "signal", "shadow": "spirit",
    "mind": "dream", "architect": "sand", "tech": "teal", "spirit": "jade",
}

# power id -> (family, glyph)
POWERS = {
    "ground_slam": ("kinetic", "slam"), "leap": ("kinetic", "leap"), "haymaker": ("kinetic", "fist"),
    "terrain_rip": ("kinetic", "rip"), "shockwave": ("kinetic", "wave"), "brace": ("kinetic", "brace"),
    "thunderclap": ("kinetic", "clap"),
    "dash": ("velocity", "dash"), "flow": ("velocity", "flow"), "afterimage": ("velocity", "afterimage"),
    "wall_run": ("velocity", "wall_run"), "water_run": ("velocity", "water_run"), "lightning_step": ("velocity", "bolt"),
    "hover": ("flight", "hover"), "flight": ("flight", "wing"), "gravity_well": ("flight", "spiral"),
    "telekinesis": ("flight", "telekinesis"), "meteor_dive": ("flight", "meteor"), "gravity_flip": ("flight", "flip"),
    "beam": ("energy", "beam"), "nova": ("energy", "nova"), "barrier": ("energy", "barrier"),
    "charged_shot": ("energy", "shot"), "overload": ("energy", "overload"), "prism_lance": ("energy", "prism"),
    "shadow_step": ("shadow", "shadow_step"), "cloak": ("shadow", "cloak"), "echo_swap": ("shadow", "swap"),
    "legion": ("shadow", "legion"),
    "danger_sense": ("mind", "danger"), "dominate": ("mind", "dominate"), "read_intent": ("mind", "intent"),
    "decoy": ("mind", "decoy"), "foresight": ("mind", "foresight"),
    "wall": ("architect", "wall"), "ramp": ("architect", "ramp"), "floor": ("architect", "floor"),
    "cone": ("architect", "cone"), "rampart": ("architect", "rampart"), "spire": ("architect", "spire"),
    "collapse": ("architect", "collapse"), "citadel": ("architect", "citadel"),
    "grapple": ("tech", "grapple"), "jet_burst": ("tech", "jet"), "drone": ("tech", "drone"), "turret": ("tech", "turret"),
    "railgun": ("tech", "rail"), "nanite_skin": ("tech", "nanite"), "drone_swarm": ("tech", "swarm"),
    "aura": ("spirit", "aura"), "spirit_palm": ("spirit", "palm"), "mend": ("spirit", "mend"), "third_eye": ("spirit", "eye"),
    "cord_lash": ("spirit", "lash"), "bulwark": ("spirit", "bulwark"), "hundred_palms": ("spirit", "palms"),
    "void_maw": ("spirit", "maw"), "waking_dream": ("spirit", "dream"), "storm_call": ("spirit", "storm"),
    "ember_step": ("spirit", "ember"), "frost_nova": ("spirit", "frost"), "ghost_step": ("spirit", "ghost"),
    "twin_strike": ("spirit", "twin"),
}

W = (255, 255, 255, 255)


# ---- drawing primitives on a glyph canvas (1 = main, 2 = highlight, 3 = dim) -------------------------------------

class G:
    def __init__(self):
        self.px = {}

    def p(self, x, y, v=1):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(int(x), int(y))] = v

    def line(self, x0, y0, x1, y1, v=1):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.p(x0, y0, v)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def rect(self, x0, y0, x1, y1, v=1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.p(x, y, v)

    def box(self, x0, y0, x1, y1, v=1):
        self.line(x0, y0, x1, y0, v); self.line(x0, y1, x1, y1, v)
        self.line(x0, y0, x0, y1, v); self.line(x1, y0, x1, y1, v)

    def disc(self, cx, cy, r, v=1):
        for x in range(16):
            for y in range(16):
                if math.hypot(x - cx, y - cy) <= r:
                    self.p(x, y, v)

    def ring(self, cx, cy, r, v=1, th=0.75):
        for x in range(16):
            for y in range(16):
                if abs(math.hypot(x - cx, y - cy) - r) <= th:
                    self.p(x, y, v)

    def arc(self, cx, cy, r, a0, a1, v=1, th=0.7):
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - cx, y - cy)
                a = math.degrees(math.atan2(-(y - cy), x - cx)) % 360
                inside = (a0 <= a <= a1) if a0 <= a1 else (a >= a0 or a <= a1)
                if abs(d - r) <= th and inside:
                    self.p(x, y, v)

    def poly(self, pts, v=1):
        n = len(pts)
        for y in range(16):
            for x in range(16):
                px, py = x + 0.5, y + 0.5
                ins, j = False, n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi):
                        ins = not ins
                    j = i
                if ins:
                    self.p(x, y, v)

    def arrowhead(self, x, y, d, v=1):
        # small 3-row arrowhead pointing in direction d: 'u','d','l','r'
        if d == "r":
            self.line(x, y, x - 2, y - 2, v); self.line(x, y, x - 2, y + 2, v)
        elif d == "l":
            self.line(x, y, x + 2, y - 2, v); self.line(x, y, x + 2, y + 2, v)
        elif d == "u":
            self.line(x, y, x - 2, y + 2, v); self.line(x, y, x + 2, y + 2, v)
        else:
            self.line(x, y, x - 2, y - 2, v); self.line(x, y, x + 2, y - 2, v)

    def bolt(self, x, y, v=1, s=1.0):
        pts = [(x + 2 * s, y), (x + 5 * s, y), (x + 3 * s, y + 4 * s), (x + 6 * s, y + 4 * s), (x + 1 * s, y + 10 * s),
               (x + 2 * s, y + 6 * s), (x - 1 * s, y + 6 * s)]
        self.poly(pts, v)

    def figure(self, x, y, v=1):
        self.disc(x, y, 1.2, v)
        self.line(x, y + 2, x, y + 6, v)
        self.line(x - 2, y + 3, x + 2, y + 3, v)
        self.line(x, y + 6, x - 2, y + 9, v); self.line(x, y + 6, x + 2, y + 9, v)

    def eye(self, cx, cy, v=1):
        for x in range(16):
            for y in range(16):
                dx, dy = (x - cx) / 6.0, (y - cy) / 3.2
                if abs(dx * dx + dy * dy - 1) < 0.28:
                    self.p(x, y, v)
        self.disc(cx, cy, 1.6, 2)


def glyph(name):
    g = G()
    if name == "slam":
        g.rect(6, 2, 9, 7); g.line(6, 3, 9, 3, 2); g.line(7, 8, 8, 9)
        g.line(2, 12, 13, 12); g.line(3, 13, 1, 14, 3); g.line(12, 13, 14, 14, 3); g.line(5, 10, 3, 9, 3); g.line(10, 10, 12, 9, 3)
    elif name == "leap":
        g.arc(9, 13, 7, 95, 175); g.arrowhead(9, 6, "r"); g.figure(4, 9, 3)
    elif name == "fist":
        g.rect(4, 5, 11, 11); g.line(4, 5, 11, 5, 2); g.line(6, 5, 6, 8, 3); g.line(8, 5, 8, 8, 3); g.line(10, 5, 10, 8, 3)
        g.rect(2, 7, 3, 10); g.line(12, 4, 14, 2, 2); g.line(12, 7, 14, 7, 2)
    elif name == "rip":
        g.poly([(4, 3), (11, 2), (13, 6), (9, 9), (3, 8)]); g.line(5, 4, 10, 3, 2)
        g.line(2, 13, 6, 11, 3); g.line(6, 11, 9, 13, 3); g.line(9, 13, 13, 11, 3)
    elif name == "wave":
        for r in (3, 6, 9):
            g.arc(2, 8, r, 300, 60, 1 if r < 9 else 2)
    elif name == "brace":
        g.poly([(3, 2), (12, 2), (12, 8), (7.5, 13.5), (3, 8)]); g.line(4, 3, 11, 3, 2); g.rect(7, 4, 8, 10, 3)
    elif name == "clap":
        g.rect(2, 4, 4, 12); g.rect(11, 4, 13, 12); g.bolt(5, 2, 2, 0.9)
    elif name == "dash":
        for o in (0, 4):
            g.line(4 + o, 4, 8 + o, 8); g.line(4 + o, 12, 8 + o, 8)
        g.line(0, 6, 3, 6, 3); g.line(0, 10, 3, 10, 3)
    elif name == "flow":
        g.line(4, 2, 11, 2, 2); g.line(4, 13, 11, 13, 2)
        g.poly([(4, 3), (11, 3), (8, 8), (7, 8)]); g.poly([(7, 8), (8, 8), (11, 13), (4, 13)], 3)
    elif name == "afterimage":
        g.figure(5, 3, 3); g.figure(10, 3, 1)
    elif name == "wall_run":
        g.rect(12, 1, 13, 14, 3); g.figure(7, 4); g.arrowhead(9, 2, "u", 2)
    elif name == "water_run":
        for y in (10, 13):
            for x in range(1, 15):
                g.p(x, y + (1 if (x // 2) % 2 else 0), 3 if y == 13 else 1)
        g.figure(7, 0, 2)
    elif name == "bolt":
        g.bolt(5, 2, 1, 1.15); g.line(1, 5, 3, 5, 3); g.line(1, 9, 3, 9, 3)
    elif name == "hover":
        g.figure(7, 1); g.ring(7.5, 13, 3.5, 3, 0.6)
        for x in (3, 7, 11):
            g.p(x, 12, 2)
    elif name == "wing":
        g.poly([(2, 4), (13, 2), (12, 5), (10, 6), (12, 8), (8, 9), (9, 11), (4, 11)]); g.line(3, 5, 12, 3, 2)
    elif name == "spiral":
        for i in range(60):
            a = i * 0.32
            r = 0.4 + i * 0.11
            g.p(7.5 + math.cos(a) * r, 7.5 + math.sin(a) * r, 2 if i > 45 else 1)
    elif name == "telekinesis":
        g.disc(7.5, 4, 2.5, 2); g.rect(4, 10, 11, 13); g.line(4, 9, 4, 8); g.line(6, 9, 6, 7); g.line(8, 9, 8, 7); g.line(10, 9, 10, 8)
    elif name == "meteor":
        g.disc(10, 10, 3, 1); g.p(9, 9, 2); g.line(2, 2, 7, 7, 3); g.line(4, 1, 8, 6, 3); g.line(1, 5, 6, 8, 3)
    elif name == "flip":
        g.line(5, 2, 5, 13); g.arrowhead(5, 2, "u", 2); g.line(10, 2, 10, 13); g.arrowhead(10, 13, "d", 2)
    elif name == "beam":
        g.disc(3, 8, 2.2, 2); g.rect(4, 7, 14, 9); g.line(5, 8, 14, 8, 2)
    elif name == "nova":
        for k in range(8):
            a = k * math.pi / 4
            g.line(7.5 + math.cos(a) * 2, 7.5 + math.sin(a) * 2, 7.5 + math.cos(a) * 6.5, 7.5 + math.sin(a) * 6.5, 1 if k % 2 else 2)
        g.disc(7.5, 7.5, 1.6, 2)
    elif name == "barrier":
        g.poly([(7.5, 1), (13, 4), (13, 11), (7.5, 14), (2, 11), (2, 4)]); g.poly([(7.5, 4), (10.5, 5.5), (10.5, 9.5), (7.5, 11), (4.5, 9.5), (4.5, 5.5)], 3)
    elif name == "shot":
        g.disc(10, 8, 3, 1); g.disc(10, 7, 1.2, 2); g.line(1, 8, 6, 8, 3); g.line(2, 5, 6, 6, 3); g.line(2, 11, 6, 10, 3)
    elif name == "overload":
        for k in range(12):
            a = k * math.pi / 6
            g.line(7.5, 7.5, 7.5 + math.cos(a) * (7 if k % 2 else 4), 7.5 + math.sin(a) * (7 if k % 2 else 4), 1)
        g.disc(7.5, 7.5, 2.5, 2)
    elif name == "prism":
        g.poly([(7.5, 2), (12, 10), (3, 10)]); g.line(0, 6, 5, 6, 2)
        g.line(10, 6, 15, 3, 2); g.line(11, 8, 15, 8, 2); g.line(10, 10, 15, 13, 2)
    elif name == "shadow_step":
        g.arc(7, 8, 5, 60, 300); g.arc(9, 8, 4, 70, 290, 3); g.line(9, 8, 14, 8, 2); g.arrowhead(14, 8, "r", 2)
    elif name == "cloak":
        g.poly([(7.5, 1), (12, 6), (13, 14), (2, 14), (3, 6)]); g.poly([(6, 6), (9, 6), (9, 9), (6, 9)], 3)
    elif name == "swap":
        g.arc(7.5, 7.5, 5, 20, 160, 1); g.arrowhead(3, 6, "d", 1); g.arc(7.5, 7.5, 5, 200, 340, 2); g.arrowhead(12, 9, "u", 2)
    elif name == "legion":
        g.figure(3, 4, 3); g.figure(12, 4, 3); g.figure(7, 3, 1)
    elif name == "danger":
        g.poly([(7.5, 1), (14, 13), (1, 13)]); g.rect(7, 5, 8, 9, 2); g.rect(7, 11, 8, 11, 2)
    elif name == "dominate":
        g.poly([(2, 11), (2, 5), (5, 8), (7.5, 3), (10, 8), (13, 5), (13, 11)]); g.line(2, 12, 13, 12, 2)
    elif name == "intent":
        g.eye(7.5, 6); g.line(7, 11, 7, 14, 2); g.arrowhead(7, 14, "d", 2)
    elif name == "decoy":
        for y in range(1, 15, 2):
            g.p(4, y, 3); g.p(11, y, 3)
        g.figure(7, 2, 1)
    elif name == "foresight":
        g.eye(7.5, 7.5); g.line(7, 7, 7, 3, 1); g.line(7, 7, 10, 9, 1)
    elif name == "wall":
        g.rect(2, 3, 13, 12)
        for y in (6, 9):
            g.line(2, y, 13, y, 3)
        for x, y in ((7, 3), (4, 6), (10, 6), (7, 9), (4, 12)):
            g.line(x, y, x, y + 2, 3)
    elif name == "ramp":
        g.poly([(2, 13), (13, 13), (13, 3)]); g.line(3, 12, 12, 4, 2)
    elif name == "floor":
        g.poly([(1, 9), (7, 6), (14, 9), (8, 12)]); g.line(2, 9, 7, 7, 2); g.line(1, 10, 8, 13, 3)
    elif name == "cone":
        g.poly([(7.5, 2), (14, 12), (1, 12)]); g.line(7, 3, 2, 11, 2); g.line(1, 13, 14, 13, 3)
    elif name == "rampart":
        g.rect(2, 6, 13, 13)
        for x in (2, 6, 10):
            g.rect(x, 3, x + 2, 5)
        g.rect(6, 10, 9, 13, 3)
    elif name == "spire":
        g.rect(6, 6, 9, 14); g.line(6, 6, 9, 6, 2); g.line(7, 1, 7, 4, 2); g.arrowhead(7, 1, "u", 2)
    elif name == "collapse":
        g.rect(2, 2, 5, 5); g.rect(9, 1, 12, 4); g.rect(6, 7, 9, 10); g.line(3, 7, 3, 9, 3); g.line(10, 6, 10, 8, 3)
        g.line(1, 13, 14, 13, 2)
    elif name == "citadel":
        g.arc(7.5, 12, 6, 0, 180, 1, 0.9); g.line(1, 13, 14, 13, 2); g.rect(7, 10, 8, 12, 3)
    elif name == "grapple":
        g.line(3, 13, 9, 5, 3); g.line(9, 5, 12, 2); g.line(12, 2, 14, 4); g.line(12, 2, 10, 1); g.arc(11, 5, 2, 180, 330, 2)
    elif name == "jet":
        g.rect(5, 2, 10, 7); g.line(5, 2, 10, 2, 2); g.poly([(5, 8), (10, 8), (7.5, 14)], 3); g.line(7, 9, 7, 12, 2)
    elif name == "drone":
        g.rect(5, 6, 10, 9); g.line(2, 4, 5, 6); g.line(13, 4, 10, 6); g.line(1, 4, 4, 4, 2); g.line(11, 4, 14, 4, 2)
        g.p(7, 7, 2); g.p(8, 7, 2); g.line(7, 10, 7, 12, 3)
    elif name == "turret":
        g.rect(3, 10, 12, 13); g.disc(7.5, 8, 3); g.rect(9, 6, 14, 7, 2)
    elif name == "rail":
        g.rect(1, 6, 9, 9); g.line(10, 7, 14, 7, 2); g.line(10, 8, 14, 8, 2); g.line(2, 5, 8, 5, 3); g.line(2, 10, 8, 10, 3)
    elif name == "nanite":
        for cx, cy in ((5, 5), (10, 5), (7.5, 9.5), (2.5, 9.5), (12.5, 9.5), (5, 14), (10, 14)):
            g.ring(cx, cy, 2.2, 1, 0.5)
        g.disc(7.5, 9.5, 1, 2)
    elif name == "swarm":
        for cx, cy in ((4, 4), (11, 3), (7, 8), (3, 11), (12, 10), (8, 13)):
            g.rect(int(cx) - 1, int(cy) - 1, int(cx) + 1, int(cy))
            g.p(cx, cy - 1, 2)
    elif name == "aura":
        g.figure(7, 3, 1)
        for k in range(10):
            a = k * math.pi / 5
            g.p(7.5 + math.cos(a) * 6.3, 8 + math.sin(a) * 6.3, 2)
    elif name == "palm":
        g.rect(4, 7, 10, 13)
        for x in (4, 6, 8, 10):
            g.line(x, 2 + (1 if x in (4, 10) else 0), x, 6)
        g.line(11, 8, 13, 6, 1); g.line(1, 4, 2, 6, 2); g.line(13, 2, 13, 4, 2)
    elif name == "mend":
        g.rect(6, 2, 9, 13); g.rect(2, 6, 13, 9); g.rect(7, 3, 8, 12, 2)
    elif name == "eye":
        g.eye(7.5, 7.5)
        for x in (3, 7, 12):
            g.p(x, 1, 3)
    elif name == "lash":
        for i in range(30):
            t = i / 29
            g.p(2 + t * 11, 12 - t * 8 + math.sin(t * 9) * 2, 1 if i < 25 else 2)
        g.rect(1, 12, 2, 14, 3)
    elif name == "bulwark":
        g.arc(7.5, 12, 6.5, 0, 180, 1, 0.8); g.arc(7.5, 12, 4, 0, 180, 3, 0.6); g.figure(7, 6, 2)
    elif name == "palms":
        for ox, oy in ((1, 1), (8, 3), (3, 8), (9, 9)):
            g.rect(ox, oy + 2, ox + 4, oy + 5); g.line(ox, oy, ox, oy + 2, 2); g.line(ox + 2, oy, ox + 2, oy + 2, 2); g.line(ox + 4, oy + 1, ox + 4, oy + 2, 2)
    elif name == "maw":
        g.disc(7.5, 7.5, 6, 1); g.disc(7.5, 7.5, 3.5, 3)
        for k in range(8):
            a = k * math.pi / 4
            g.p(7.5 + math.cos(a) * 4.3, 7.5 + math.sin(a) * 4.3, 2)
    elif name == "dream":
        g.disc(7, 8, 5, 1)
        for x in range(16):
            for y in range(16):
                if math.hypot(x - 9.5, y - 6.5) <= 4.5:
                    g.px.pop((x, y), None)
        g.p(12, 3, 2); g.p(13, 9, 2); g.p(11, 12, 2)
    elif name == "storm":
        g.disc(5, 5, 3); g.disc(9, 4, 3.5); g.rect(3, 5, 12, 7); g.bolt(6, 7, 2, 0.8)
    elif name == "ember":
        g.poly([(7.5, 1), (11, 7), (12, 11), (9.5, 14), (5.5, 14), (3, 11), (4, 7)]); g.poly([(7.5, 6), (9.5, 10), (8.5, 13), (6.5, 13), (5.5, 10)], 2)
    elif name == "frost":
        for k in range(6):
            a = k * math.pi / 3
            ex, ey = 7.5 + math.cos(a) * 6, 7.5 + math.sin(a) * 6
            g.line(7.5, 7.5, ex, ey, 1)
            g.p(7.5 + math.cos(a) * 4, 7.5 + math.sin(a) * 4, 2)
        g.p(7, 7, 2); g.p(8, 8, 2)
    elif name == "ghost":
        g.disc(7.5, 6, 4.5); g.rect(3, 6, 12, 12)
        for x in (3, 7, 11):
            g.rect(x, 13, x + 1, 13)
        g.p(6, 6, 3); g.p(9, 6, 3)
    elif name == "twin":
        g.figure(5, 3, 1); g.figure(10, 3, 2)
    else:
        g.disc(7.5, 7.5, 4)
    return g


def tile(ramp_name):
    rp = ramp(ramp_name)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            corner = (x in (0, 15)) and (y in (0, 15))
            if corner:
                continue
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                c.set(x, y, rp[0])
            else:
                t = (x + y) / 30
                c.set(x, y, rp[1] if t > 0.45 else rp[2])
    for x in range(2, 14):
        c.set(x, 1, rp[3])
    return c


def icon(power, family, glyph_name):
    rp = ramp(FAMILY_RAMP[family])
    c = tile(FAMILY_RAMP[family])
    g = glyph(glyph_name)
    cols = {1: rp[4], 2: rp[5] if len(rp) > 5 else W, 3: rp[3]}
    # outline: dark ring around every glyph pixel for contrast
    for (x, y) in list(g.px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in g.px and 1 <= q[0] <= 14 and 1 <= q[1] <= 14:
                c.set(q[0], q[1], rp[0])
    for (x, y), v in g.px.items():
        if 1 <= x <= 14 and 1 <= y <= 14:
            c.set(x, y, cols[v])
    return c


def construct(ramp_name, age, seed):
    """Hard light: a translucent fill with a bright frame. Cracks spread and the fill thins as it ages."""
    rp = ramp(ramp_name)
    c = Canvas()
    rnd = rng_for("construct", ramp_name, seed)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            if edge:
                r, g, b, _ = rp[4]
                c.set(x, y, (r, g, b, 235 - age * 20))
            elif inner:
                r, g, b, _ = rp[3]
                c.set(x, y, (r, g, b, 150 - age * 15))
            else:
                r, g, b, _ = rp[3]
                shimmer = 24 if (x + y + seed) % 7 == 0 else 0
                c.set(x, y, (r, g, b, 105 - age * 12 + shimmer))
    # corner glints
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        r, g, b, _ = rp[5]
        c.set(x, y, (r, g, b, 230))
    # cracks grow with age
    for k in range(age * 2):
        x, y = rnd.randint(3, 12), rnd.randint(3, 12)
        for _ in range(rnd.randint(2, 4)):
            r, g, b, _ = rp[5]
            c.set(x, y, (r, g, b, 200))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((-1, 1))
    return c


def core_shell():
    c = Canvas()
    shell = ramp("obsidian")
    brass = ramp("brass")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.5 and d > 4.2:
                light = ((x - 7.5) * -0.6 + (y - 7.5) * -0.8) / 6.5
                c.set(x, y, shell[2 + (1 if light > 0.1 else 0) + (1 if light > 0.5 else 0)])
    for x in range(16):
        for y in range(16):
            d = math.hypot((x - 7.5) * 1.0, (y - 7.5) * 2.6)
            if abs(d - 8.0) < 0.7 and y >= 8:
                c.set(x, y, brass[4])
    c.set(5, 3, (255, 255, 255, 255))
    return c


def core_glow():
    """Greyscale: the item tint colours it by the power's family."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 4.2:
                v = int(255 - d * 28)
                c.set(x, y, (v, v, v, 255))
    c.set(6, 6, (255, 255, 255, 255))
    return c


def stunned():
    c = Canvas(18, 18)
    rp = ramp("signal")
    am = ramp("amber")
    for k in range(3):
        a = k * 2 * math.pi / 3
        cx, cy = 9 + math.cos(a) * 5, 9 + math.sin(a) * 5
        for x in range(18):
            for y in range(18):
                if math.hypot(x - cx, y - cy) <= 2.2:
                    c.set(x, y, am[4] if k == 0 else rp[4])
    for x in range(4, 14):
        c.set(x, 9, rp[5])
    return _outline(c)


def build(out):
    for power, (family, g) in POWERS.items():
        out.gui("sprites/power/" + power, icon(power, family, g))
    for age in range(4):
        out.block(f"construct_{age}", construct("signal", age, 3))
        out.block(f"dream_construct_{age}", construct("dream", age, 5))
    out.item("power_core", core_shell())
    out.item("power_core_glow", core_glow())
    out._save("mob_effect/stunned", stunned())
