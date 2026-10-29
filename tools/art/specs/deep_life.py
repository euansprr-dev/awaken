"""The Living Deep's underlife (SPEC §42, P1): the sprite sheet the client draws cave creatures from, as §29's insects are
drawn from theirs (wildlife.client.InsectRender): a few quads cut from 16 x 16 cells, wings hinged on the body.

  textures/entity/caves/underlife.png (64 x 64):
    row 0: moth wings (right pair, the hinge on the cell's left edge, the head toward the top): the Rootways' dusk moth,
           the Glimmer Hollows' glass moth, the Black Vaults' ash moth; then their three bodies
    row 1: the Pale Halls' glow-worm threads, the Windcleft's cave swift (from above), the Ochre Deep's rust beetle,
           a cave beetle (from above)
    row 2: a millipede, the Drowned Galleries' cave crayfish, a blind fish (all from above), a cave cricket (side)
    row 3: a roosting bat (hanging, wings wrapped), a bat's wing (the hinge on the left), a bat's body (from above), the
           Violet Deep's chitin crawler (from above)

Run: python3 tools/art/build_all.py deep_life
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, hex_rgba  # noqa: E402


def H(h, a=255):
    return hex_rgba(h, a)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


class Cell:
    """Draws into one 16 x 16 cell of the sheet."""

    def __init__(self, cv, col, row):
        self.cv, self.ox, self.oy = cv, col * 16, row * 16

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.cv.set(self.ox + int(x), self.oy + int(y), c)

    def get(self, x, y):
        return self.cv.get(self.ox + x, self.oy + y)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for k in range(n + 1):
            self.set(round(x0 + (x1 - x0) * k / n), round(y0 + (y1 - y0) * k / n), c)

    def oval(self, cx, cy, rx, ry, fn):
        """Fills an ellipse; fn(u, v, d) gives each pixel's colour (u, v from -1 to 1, d the distance from the middle)."""
        for y in range(16):
            for x in range(16):
                u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = math.hypot(u, v)
                if d <= 1:
                    col = fn(u, v, d)
                    if col:
                        self.set(x, y, col)


def inside(poly, x, y):
    n, hit = len(poly), False
    for i in range(n):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


FORE = [(0, 1.5), (5, 1.2), (11, 2.6), (15.4, 5.2), (14.6, 7.4), (11, 9.6), (7.5, 11), (0, 9.5)]
HIND = [(0, 8.5), (6, 9.2), (9, 11), (8.6, 13.4), (5.5, 15), (2, 14.6), (0, 13)]


def moth_wing(c, fore, hind, edge, line, spots=(), fringe=None):
    """A moth's right wings: a long triangular forewing (straight leading edge out to a pointed tip, the outer edge
    slanting back) over a small round hindwing. Two wavy cross-lines and a pale fringe on the outer edge, as a real moth's
    markings run; spots for the eye-spots and glints."""
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            if inside(FORE, px, py):
                col = fore(x, y)
                # the cross-lines run from the leading edge back to the inner edge, wavy
                for k in (4.2, 8.6):
                    if abs(px - (k + (py - 3) * 0.5 + 0.6 * math.sin(py * 1.7))) < 0.55:
                        col = line
                if not inside(FORE, px + 1, py) or not inside(FORE, px + 0.6, py + 0.8):
                    col = fringe or edge
                c.set(x, y, col)
            elif inside(HIND, px, py):
                col = hind(x, y)
                if not inside(HIND, px + 1, py) or not inside(HIND, px, py + 1):
                    col = edge
                c.set(x, y, col)
    for x, y, col in spots:
        c.set(x, y, col)


def moth_body(c, x0, head, thorax, abdomen):
    for y in range(1, 14):
        for x in (x0, x0 + 1):
            col = head if y < 3 else thorax if y < 6 else abdomen
            if (y > 6 and y % 2 == 0):
                col = mix(col, (0, 0, 0, 255), 0.25)
            c.set(x, y, col)
    c.line(x0 - 1, 0, x0 - 2, -1, head)
    c.set(x0 - 1, 1, head)
    c.set(x0 + 2, 1, head)


def sheet():
    cv = Canvas(64, 64)

    # ---- row 0: moths ---------------------------------------------------------------------------------------------
    umber, umber2, cream, teal = H("#c9b48e"), H("#b39c74"), H("#f2e6c4"), H("#5fd8c4")
    # the dusk moth: pale buff wings that catch your torch against the brown rock, umber cross-lines, a cream eye-spot
    # ringed dark, its wingtips dusted teal from the glowcaps it feeds on
    moth_wing(Cell(cv, 0, 0), lambda x, y: umber if (x * 3 + y * 5) % 7 else umber2, lambda x, y: H("#d8c6a0"), H("#7a5a3a"),
              H("#5a3e26"), fringe=H("#efe2c0"),
              spots=[(7, 5, cream), (7, 6, cream), (6, 5, H("#1a120c")), (8, 6, H("#1a120c")), (13, 5, teal), (14, 6, teal),
                     (12, 7, teal), (4, 12, H("#a87a4c")), (5, 12, H("#a87a4c"))])
    # the glass moth: wings like the crystals it lives among, see-through amber with bright veins and glints
    moth_wing(Cell(cv, 1, 0), lambda x, y: (232, 176, 90, 110 + (x * 7 + y * 3) % 40), lambda x, y: (240, 196, 120, 100),
              (255, 226, 160, 220), (255, 236, 190, 200), fringe=(255, 244, 214, 240),
              spots=[(6, 3, (255, 255, 255, 255)), (11, 5, (255, 250, 230, 240)), (3, 11, (255, 255, 255, 220))])
    ashc, ash2, ember = H("#3c3a3a"), H("#4e4b49"), H("#ff8a3a")
    # the ash moth: charcoal, its outer edge still glowing like a cinder
    moth_wing(Cell(cv, 2, 0), lambda x, y: ashc if (x + 2 * y) % 5 else ash2, lambda x, y: H("#2e2c2c"), H("#1e1c1c"), H("#24221f"),
              fringe=H("#c2501e"), spots=[(14, 6, ember), (13, 7, ember), (11, 9, ember), (7, 13, H("#c2501e"))])
    c = Cell(cv, 3, 0)
    moth_body(c, 2, H("#5a3e26"), H("#d8c6a0"), H("#a88e66"))
    moth_body(c, 7, (255, 236, 190, 230), (240, 190, 110, 200), (230, 170, 90, 170))
    moth_body(c, 12, H("#1e1c1c"), H("#4e4b49"), H("#3c3a3a"))
    c.set(13, 12, ember)

    # ---- row 1 ------------------------------------------------------------------------------------------------------
    c = Cell(cv, 0, 1)  # glow-worm threads: silk hanging from the roof, beaded with glowing glue; the larva at the top
    for x, length in ((3, 15), (7, 10), (11, 13), (14, 7)):
        for y in range(1, length):
            c.set(x, y, (200, 230, 240, 90))
            if y % 3 == (x % 3):
                c.set(x, y, (150, 255, 236, 255))
        c.set(x, length, (190, 255, 245, 255))
    for x in range(2, 14):
        c.set(x, 0, H("#8a7e68"))
    for x in range(6, 10):
        c.set(x, 1, H("#d8f4e8") if x == 8 else H("#a8d8c8"))
    c = Cell(cv, 1, 1)  # cave swift from above: long sickle wings, broad at the shoulder, forked tail, sooty, pale throat
    soot, soot2, sheen = H("#2c2622"), H("#463c34"), H("#6a5e54")
    for k in range(8):
        y = 6 + k * 0.5 + k * k * 0.05
        for sx, x in ((-1, 6 - k), (1, 9 + k)):
            c.set(x, y, soot2 if k < 5 else soot)
            if k < 6:
                c.set(x, y + 1, soot)
            if k < 3:
                c.set(x, y - 1, sheen)
    c.oval(7.5, 7.5, 1.6, 4.2, lambda u, v, d: sheen if (u < 0 and v < 0) else soot2)
    c.set(7, 4, H("#cfc6b6"))
    c.set(8, 4, H("#cfc6b6"))
    c.line(7, 11, 6, 15, soot)
    c.line(8, 11, 9, 15, soot)
    c = Cell(cv, 2, 1)  # rust beetle from above: rust elytra with a metallic seam, dark shield and head, six legs
    rust = [H("#5a2410"), H("#8f3c18"), H("#b85a24"), H("#e08a44")]
    for (x0, y0), (x1, y1) in (((5, 6), (2, 4)), ((5, 8), (2, 9)), ((5, 10), (2, 13)), ((10, 6), (13, 4)), ((10, 8), (13, 9)), ((10, 10), (13, 13))):
        c.line(x0, y0, x1, y1, H("#24140c"))
    c.oval(7.5, 9.5, 3.6, 4.8, lambda u, v, d: rust[0] if d > 0.86 else rust[3] if (u < -0.3 and v < -0.2) else rust[2] if u < 0 else rust[1])
    for y in range(6, 14):
        c.set(7, y, H("#2a120a"))
    c.oval(7.5, 4.5, 2.4, 1.6, lambda u, v, d: H("#2a1a12") if d > 0.7 else H("#4a2a1a"))
    c.oval(7.5, 2.5, 1.4, 1.1, lambda u, v, d: H("#1a0e08"))
    c.set(6, 1, H("#1a0e08"))
    c.set(9, 1, H("#1a0e08"))
    c = Cell(cv, 3, 1)  # cave beetle: slender, dark, long feelers
    for (x0, y0), (x1, y1) in (((7, 5), (3, 0)), ((8, 5), (12, 0)), ((7, 8), (4, 7)), ((8, 8), (11, 7)), ((7, 10), (4, 12)), ((8, 10), (11, 12)), ((7, 12), (5, 15)), ((8, 12), (10, 15))):
        c.line(x0, y0, x1, y1, H("#2a2018"))
    c.oval(7.5, 10, 2.2, 3.6, lambda u, v, d: H("#1a1410") if d > 0.8 else H("#5a4636") if (u < -0.2 and v < -0.1) else H("#2e241c"))
    c.oval(7.5, 5.5, 1.5, 1.4, lambda u, v, d: H("#241a14"))

    # ---- row 2 ------------------------------------------------------------------------------------------------------
    c = Cell(cv, 0, 2)  # millipede, curled into a lazy S, chestnut with pale legs
    pts = [(3 + 10 * (k / 13), 2 + k + math.sin(k * 0.5) * 1.5) for k in range(14)]
    for k, (x, y) in enumerate(pts):
        c.set(x - 1.5, y, H("#d8c8a8"))
        c.set(x + 1.5, y, H("#d8c8a8"))
    for k, (x, y) in enumerate(pts):
        col = H("#6a3018") if k % 2 else H("#8a4422")
        c.set(x, y, col)
        c.set(x + 0.9, y, mix(col, H("#c87a44"), 0.4))
    c.set(pts[0][0], pts[0][1], H("#2a140a"))
    c = Cell(cv, 1, 2)  # cave crayfish: ghost-pale, claws forward
    pale, pale2 = H("#d8e2e6"), H("#aebcc4")
    c.oval(7.5, 9, 2.0, 5.2, lambda u, v, d: pale2 if d > 0.8 or (v > 0 and int((v + 1) * 6) % 2) else pale)
    for sx in (-1, 1):
        c.line(int(7.5 + sx * 1.5), 5, int(7.5 + sx * 3.5), 2, pale2)
        c.oval(7.5 + sx * 4.0, 1.5, 1.3, 1.6, lambda u, v, d: pale if d < 0.6 else pale2)
        for k in range(3):
            c.line(int(7.5 + sx * 1.5), 7 + k * 2, int(7.5 + sx * 4), 8 + k * 2, pale2)
        c.line(int(7.5 + sx * 0.5), 4, int(7.5 + sx * 5), 0, (220, 230, 236, 160))
    c.line(6, 14, 9, 14, pale)
    c.line(5, 15, 10, 15, pale2)
    c = Cell(cv, 2, 2)  # blind fish from above: pale and pinkish (blood under thin skin), see-through fins, no eyes
    c.oval(7.5, 7.5, 1.9, 6.0, lambda u, v, d: H("#f0d6d0") if d < 0.5 else H("#dcbcb4") if d < 0.85 else H("#b8968e"))
    for sx in (-1, 1):
        c.line(int(7.5 + sx * 1.7), 5, int(7.5 + sx * 4), 7, (240, 220, 216, 130))
        c.line(int(7.5 + sx * 1.7), 6, int(7.5 + sx * 3.5), 8, (240, 220, 216, 100))
    c.line(7, 14, 5, 15, (236, 214, 208, 170))
    c.line(8, 14, 10, 15, (236, 214, 208, 170))
    c = Cell(cv, 3, 2)  # cave cricket from the side: humped, long jumping legs, feelers longer than its body
    tan, tan2, dark = H("#a8865a"), H("#7a5e3c"), H("#4a3824")
    c.oval(8, 9, 4.2, 2.6, lambda u, v, d: tan2 if v > 0.3 else tan if d < 0.8 else tan2)
    for x in range(5, 12, 2):
        c.set(x, 7, dark)
    c.oval(3.6, 9.6, 1.4, 1.5, lambda u, v, d: tan)
    c.line(3, 9, 0, 1, (190, 160, 120, 200))
    c.line(4, 9, 6, 0, (190, 160, 120, 160))
    c.line(10, 10, 14, 6, tan2)
    c.line(14, 6, 13, 14, tan2)
    c.line(6, 11, 5, 14, dark)
    c.line(8, 11, 8, 14, dark)

    # ---- row 3: bats and the chitin crawler ---------------------------------------------------------------------------
    fur, fur2, membrane = H("#5a4a40"), H("#3e322c"), H("#2c2226")
    c = Cell(cv, 0, 3)  # roosting bat, upside down: feet at the top, wrapped in its wings, ears at the bottom
    c.set(7, 0, H("#2a2020"))
    c.set(9, 0, H("#2a2020"))
    c.oval(8, 7, 3.2, 5.6, lambda u, v, d: membrane if abs(u) > 0.45 else fur if v < 0.4 else fur2)
    for y in range(3, 12):
        c.set(5, y, mix(membrane, H("#5a4648"), 0.4))
    c.set(7, 12, H("#c08a7a"))
    c.set(6, 13, fur2)
    c.set(10, 13, fur2)
    c.set(6, 14, H("#7a5a50"))
    c.set(10, 14, H("#7a5a50"))
    c = Cell(cv, 1, 3)  # a bat's right wing: membrane between finger bones, the hinge on the left
    for y in range(16):
        for x in range(16):
            top = 3 + x * 0.1
            bottom = 13 - x * 0.35 + 1.5 * abs(math.sin(x * math.pi / 5))
            if top <= y <= bottom and x < 15:
                c.set(x, y, (60, 44, 50, 235) if (x + y) % 5 else (74, 56, 62, 235))
    for fx, fy in ((15, 3), (14, 8), (11, 12), (6, 14)):
        c.line(0, 4, fx, fy, H("#1e1618"))
    c.line(0, 3, 15, 3, H("#3a2c2e"))
    c = Cell(cv, 2, 3)  # bat body from above: furry, big ears
    c.oval(7.5, 8, 2.4, 4.6, lambda u, v, d: fur if (u < 0 and v < 0.2) else fur2)
    c.oval(7.5, 3.6, 1.8, 1.4, lambda u, v, d: fur)
    c.line(6, 2, 5, 0, H("#7a5a50"))
    c.line(9, 2, 10, 0, H("#7a5a50"))
    c = Cell(cv, 3, 3)  # chitin crawler: flat plates of violet-black chitin, a pale sheen on each, many legs
    plates = [H("#1c1426"), H("#2e2240"), H("#4a3a66"), H("#9a7ad0")]
    for y in range(2, 15):
        w = 3.2 - abs(y - 8) * 0.18
        for x in range(16):
            dx = x + 0.5 - 8
            if abs(dx) < w:
                k = 1 if abs(dx) > w - 1 else 2
                if y % 2 == 0:
                    k = 0 if abs(dx) > w - 1.2 else 1
                if y % 2 == 1 and -1.5 < dx < -0.3:
                    k = 3
                c.set(x, y, plates[k])
        if y % 2 == 0 and 3 < y < 14:
            c.set(8 - w - 1, y + 1, plates[1])
            c.set(8 + w, y + 1, plates[1])
    c.line(6, 2, 4, 0, plates[2])
    c.line(9, 2, 11, 0, plates[2])
    return cv


def build(out):
    out.entity("caves/underlife", sheet())


if __name__ == "__main__":
    print(__doc__)
