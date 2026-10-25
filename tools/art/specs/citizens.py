"""CARILLON's people and their things: 244 painted skins (240 citizens minus the four named ones, plus 8 thieves), the lamp,
laundry, fire and crate blocks, the props the citizens hold, the chimney/forge/lamp/ember particles.

    python3 tools/art/build_all.py citizens          write every texture
    python3 tools/art/specs/citizens.py preview DIR  contact sheets (front, back, things) + the checks
    python3 tools/art/specs/citizens.py check        only the checks (variety numbers, skin format)

Reads src/main/resources/data/awaken/city/citizens.json (tools/city/citizens.py): per citizen job, build, age,
look = [tone, hairStyle, hairColour, outfit, accent, face]. Everything is composed from seeded layers, so the same roster
always gives the same pixels. One town: bronze buttons and buckles, a bell motif on every crest and badge, wool with fold
lines, leather a little glossy at its edges. 240 people: skin, hair, face, build and a palette per citizen.

Skin format: 64x64 player skins, classic layout; build 1 = slim (3 px arms, the layout shifts as in vanilla slim skins).
Base parts are fully opaque, overlay parts (hat, jacket, sleeves, pants) are transparent where unused, never partial alpha.
"""
import colorsys
import hashlib
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import *  # noqa: E402,F401,F403
from pixelart import _outline  # noqa: E402,F401
import hearth  # noqa: E402
from hearth import LOCAL, _faces, h01, col, tex, EYE_WHITE  # noqa: E402,F401

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ROSTER = os.path.join(ROOT, "src/main/resources/data/awaken/city/citizens.json")

JOBS = ["lamplighter", "bellringer", "porter", "stallkeeper", "baker", "smith", "watch", "crier", "boatman", "washer",
        "mason", "runner", "shopkeeper", "tavernkeeper", "clerk", "archivist", "healer", "miller", "child", "resident"]

MALE_NAMES = set("""Abel Alder Ambrose Anselm Aren Arno Bastian Bertil Bram Brannoc Cato Cillian Clem Corin Dorran Edric Emrys
Fenwick Finch Gideon Hale Hollis Ivo Jago Jory Kasimir Kit Lucan Mattis Nico Olek Orrin Pell Perrin Quill Rafe Roan Rook
Silas Tam Tobin Ulric Wendel Wim Yorick Ansel Benedek Caspar Davin Eben Florian Garrick Hamish Ignas Joss Konrad Lorcan
Marek Nils Osric Piet Radek Stellan Teague Vasco Oren Teodor""".split())

# ---------------------------------------------------------------------------------------------------------------------
# Colour: ramps made from one base colour, registered by name so hearth's tex()/col() can read them.

COOL, WARM = (26, 24, 52), (255, 240, 205)


def _mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mk(base: str) -> str:
    """A six-step cloth ramp (dark -> light, base at index 3): shadows lean cool, lights lean warm."""
    name = "~" + base
    if name in LOCAL:
        return name
    b = tuple(int(base[i:i + 2], 16) for i in (1, 3, 5))
    out = []
    for f in (0.36, 0.55, 0.78, 1.0, 1.2, 1.42):
        c = tuple(min(255, v * f) for v in b) if f <= 1 else b
        if f < 1:
            c = _mix(c, COOL, (1 - f) * 0.2)
        elif f > 1:
            c = _mix(tuple(min(255.0, v * (1 + (f - 1) * 0.5)) for v in b), WARM, (f - 1) * 0.28)
        out.append("#%02x%02x%02x" % tuple(int(round(max(0, min(255, v)))) for v in c))
    LOCAL[name] = out
    return name


def mk_soft(base: str) -> str:
    """A ramp with small steps, for pale cloth (flour whites, linen) where mk()'s shadows would read as grey stripes."""
    name = "~s" + base
    if name in LOCAL:
        return name
    b = tuple(int(base[i:i + 2], 16) for i in (1, 3, 5))
    out = []
    for f in (0.62, 0.74, 0.87, 1.0, 1.04, 1.08):
        c = tuple(min(255, v * f) for v in b)
        if f < 1:
            c = _mix(c, COOL, (1 - f) * 0.12)
        out.append("#%02x%02x%02x" % tuple(int(round(max(0, min(255, v)))) for v in c))
    LOCAL[name] = out
    return name


def jig(c, base: str, amt=0.05, tag="") -> str:
    """The base colour nudged a little (hue, value) by the citizen's own seed, so two people in the same outfit differ."""
    r, g, b = (int(base[i:i + 2], 16) / 255 for i in (1, 3, 5))
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    h = (h + (c.v("jh" + tag) - 0.5) * amt * 0.5) % 1.0
    v = max(0.0, min(1.0, v * (1 + (c.v("jv" + tag) - 0.5) * amt * 2)))
    s = max(0.0, min(1.0, s * (1 + (c.v("js" + tag) - 0.5) * amt * 2)))
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return "#%02x%02x%02x" % (round(r * 255), round(g * 255), round(b * 255))


def reg(name, ramp):
    LOCAL[name] = ramp
    return name


SKIN = [reg("sk0", ["#24110a", "#38190f", "#4e2816", "#663820", "#7d4a2b", "#945d38"]),
        reg("sk1", ["#33190f", "#4f2b1b", "#6b3d27", "#865133", "#a06842", "#b98056"]),
        reg("sk2", ["#4a2c1c", "#6e432b", "#8e5a3a", "#ad7450", "#c48d66", "#d9a983"]),
        reg("sk3", ["#5a3a2a", "#82573f", "#a8765a", "#c4906f", "#d8a784", "#e8bf9f"]),
        reg("sk4", ["#6e4a3c", "#9c6f5c", "#c4917a", "#deb096", "#ecc5aa", "#f6dcc6"]),
        reg("sk5", ["#7a6862", "#a08b84", "#c4aea5", "#dcc8be", "#ecdcd2", "#f8eee8"])]
HAIRC = [reg("hc0", ["#07070a", "#0f0e14", "#19171f", "#26232e", "#37333f", "#4b4757"]),
         reg("hc1", ["#120b07", "#201309", "#33200f", "#4a2f17", "#654023", "#7d5530"]),
         reg("hc2", ["#1d0f08", "#33190c", "#512a14", "#6f3d1f", "#8d522c", "#a96a3b"]),
         reg("hc3", ["#26090a", "#421210", "#661d14", "#86291a", "#a33a22", "#bf5230"]),
         reg("hc4", ["#3a1009", "#5e1b0e", "#8a2a13", "#b33e1b", "#d65b2a", "#ef8a4c"]),
         reg("hc5", ["#4a3a1c", "#6f5a2a", "#977d3a", "#bd9f52", "#dcc078", "#f0dc9d"]),
         reg("hc6", ["#3a3b40", "#55575e", "#73757d", "#92949b", "#b0b2b8", "#cfd0d4"]),
         reg("hc7", ["#6a6b72", "#8d8e95", "#adaeb4", "#c9cacf", "#e0e1e5", "#f4f4f6"])]
EYE_COL = ["#4a2c16", "#3f6fb0", "#4c8a52", "#8a6a2f", "#7a8a96", "#2a160c", "#6f9ac2", "#6b4a1e"]

# the town's metals and leathers
BRONZE = reg("bronze", ["#2c1e0c", "#4f3512", "#76501a", "#a07426", "#c8983a", "#e8c86a"])
BRASS = reg("brass2", ["#3a2a10", "#5e4416", "#8a6a20", "#b8902e", "#e0b845", "#f6dc7e"])
IRON = reg("iron", ["#14161b", "#25282f", "#3b3f49", "#555b68", "#737a89", "#9aa2b2"])
MAIL = reg("mail", ["#1c1f26", "#30343e", "#4a505c", "#667080", "#8590a0", "#aab4c2"])
LEATHER = reg("leather", ["#1e120a", "#33200f", "#4d3019", "#684523", "#835c30", "#a07a45"])
DARKLEATHER = reg("dleather", ["#120b07", "#201409", "#32200f", "#47301a", "#5c4125", "#74562f"])
LINEN = reg("linen", ["#5e584a", "#847d6c", "#aaa390", "#cdc6b2", "#e4ded0", "#f6f2e8"])
SOOT = reg("soot", ["#0c0b0b", "#171514", "#25211f", "#37312d", "#4b443e", "#625a52"])


def cl(r, i):
    rp = LOCAL.get(r) or RAMPS[r]
    return hex_rgba(rp[max(0, min(len(rp) - 1, i))])


# ---------------------------------------------------------------------------------------------------------------------
# Skin geometry (slim arms are 3 wide, the layout shifts as in vanilla slim skins)

PARTS_WIDE = hearth.PARTS
PARTS_SLIM = dict(PARTS_WIDE)
for _k in ("rarm", "rsleeve", "larm", "lsleeve"):
    _u, _v, _w, _h, _d = PARTS_WIDE[_k]
    PARTS_SLIM[_k] = (_u, _v, 3, _h, _d)
BASE_PARTS = ("head", "body", "rarm", "larm", "rleg", "lleg")
ALL_PARTS = tuple(PARTS_WIDE)
HEAD_PARTS = ("head", "hat")


class F:
    """One pixel on one face of a skin box. x,y are inside the face. u = column across the front view of the part
    (limbs: 0 = the outer edge, 3 = the inner edge, as a wide limb, so one grid works for both builds)."""

    def __init__(self, part, face, x, y, w, h, d, slim):
        self.part, self.face, self.x, self.y, self.w, self.h, self.d = part, face, x, y, w, h, d
        self.slim = slim
        self.side = part[0] if part[0] in "rl" and part not in ("rleg", "lleg") or part in ("rleg", "lleg") else None
        self.limb = part in ("rarm", "larm", "rsleeve", "lsleeve", "rleg", "lleg", "rpants", "lpants")
        self.arm = part in ("rarm", "larm", "rsleeve", "lsleeve")
        self.leg = part in ("rleg", "lleg", "rpants", "lpants")
        self.fd = {"right": d - 1 - x, "left": x, "front": 0, "back": d}.get(face)
        self.outer = (self.side == "r" and face == "right") or (self.side == "l" and face == "left")
        self.inner = (self.side == "r" and face == "left") or (self.side == "l" and face == "right")
        self.wall = face in ("front", "back", "left", "right")
        # u: horizontal column
        if self.limb and face in ("front", "back", "top", "bottom"):
            x4 = x
            if slim and self.arm:  # the missing column is the inner one
                shift = {("r", "front"): 0, ("l", "front"): 1, ("r", "back"): 1, ("l", "back"): 0,
                         ("r", "top"): 0, ("l", "top"): 1, ("r", "bottom"): 0, ("l", "bottom"): 1}[(self.side, face)]
                x4 = x + shift
            right_outer = face in ("front", "top", "bottom")
            self.u = x4 if (self.side == "r") == right_outer else 3 - x4
        else:
            self.u = x

    def bx(self):
        """x across the whole body front (0..7) for a limb's front face."""
        if self.face == "front":
            return self.x + (4 if self.side == "l" else 0)
        if self.face == "back":
            return self.x + (0 if self.side == "l" else 4)
        return None


def shade(f, i):
    if f.face == "top":
        return i + 1
    if f.face == "bottom":
        return i - 1
    if f.inner:
        return i - 1
    return i


def paint_part(c: Canvas, part, parts, slim, fn):
    u, v, w, h, d = parts[part]
    for face, (fx, fy, fw, fh) in _faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                px = fn(F(part, face, x, y, w, h, d, slim))
                if px is not None:
                    c.set(fx + x, fy + y, px)


# ---------------------------------------------------------------------------------------------------------------------
# The citizen's context

class Ctx:
    def __init__(self, cid, job, look, build, age, given, role=None):
        self.id, self.job, self.build, self.age, self.given = cid, job, build, age, given
        self.role = role or JOBS[job] if isinstance(job, int) else job
        self.jobname = self.role
        self.key = f"cz{cid}"
        self.tone, self.hstyle, self.hcol, self.outfit, self.accent, self.face = look
        self.sk, self.hr = SKIN[self.tone], HAIRC[self.hcol]
        self.male = given in MALE_NAMES or role == "thief" and cid % 2 == 0
        self.slim = build == 1
        self.child, self.old = age == 0, age == 3

    def v(self, tag, *more) -> float:
        return h01(self.key, tag, *more)

    def S(self, i):  # skin colour
        return cl(self.sk, i)

    def H(self, i):  # hair colour
        return cl(self.hr, i)


def T(c, r, idx, f, **kw):
    """Cloth with fold lines and specks, lit by the face."""
    return tex(r, shade(f, idx), f, c.key, **kw)


def P(r, idx, f=None):
    return cl(r, shade(f, idx) if f is not None else idx)


def res(c, v, f):
    if callable(v):
        return v(f)
    if isinstance(v, tuple) and isinstance(v[0], str):
        if len(v) == 3:
            return P(v[0], v[1], f)
        return T(c, v[0], v[1], f)
    return v


def grid(c, legend, front=None, back=None, side=None, top=None, bottom=None, inner=None, outer=None, base=None):
    """A part from character grids. front/back: rows of the part's width (limbs: 4, outer edge first); side: rows of
    the part's depth (column 0 next to the front); top/bottom: rows of the depth. '.' falls through to base."""
    def fn(f):
        if f.face == "front":
            rows, i = front, f.u
        elif f.face == "back":
            rows, i = back, f.u
        elif f.face == "top":
            rows, i = top, f.u
        elif f.face == "bottom":
            rows, i = bottom, f.u
        else:
            rows, i = side, f.fd
            if f.outer and outer is not None:
                rows = outer
            elif f.inner and inner is not None:
                rows = inner
        if rows is not None:
            ch = rows[f.y][i]
            if ch != ".":
                return res(c, legend[ch], f)
        return base(f) if base is not None else None
    return fn


def lay(*fns):
    """First layer that paints wins."""
    def fn(f):
        for g in fns:
            if g is None:
                continue
            r = g(f)
            if r is not None:
                return r
        return None
    return fn


def region(x0, y0, x1, y1, painter, faces=("front",), side=None):
    """Paint inside a rectangle of the front/back face (limbs: u columns)."""
    def fn(f):
        if f.face in faces and x0 <= f.u <= x1 and y0 <= f.y <= y1:
            return painter(f)
        return None
    return fn


def speck(c, f, tag, p, colour):
    return colour if h01(c.key, tag, f.part, f.face, f.x, f.y) < p else None


def skin_px(c, f, hi=3):
    """Bare skin on a body part, lit by the face."""
    return cl(c.sk, shade(f, hi) + (0 if f.face in ("front", "top") else -1 if f.face in ("left", "right") else 0))


# ---------------------------------------------------------------------------------------------------------------------
# Heads: faces, hair, beards

HAIR_STYLES = {
    # front: the hair mask on the face (rows 0..); side: hair rows per depth column (front..back); back: rows on the back;
    # vol: the hat layer repeats the coverage (hair standing proud); the extras are named in hair_hat / hair_jacket
    0: dict(front=["########", "########", "#......#"], side=[2, 2, 2, 3, 3, 3, 3, 3], back=4),
    1: dict(front=["########", "########", "####...#", "##......"], side=[2, 2, 3, 3, 3, 4, 4, 4], back=5, part=True),
    2: dict(front=["########", "########"] + ["#......#"] * 6, side=[8] * 8, back=8, vol=True, long=8),
    3: dict(front=["########", "#......#", "#......#"], side=[2, 2, 2, 3, 3, 4, 4, 4], back=5, tail=7, tie=True),
    4: dict(front=["########", "###..###", "##....##"], side=[2, 3, 3, 3, 3, 4, 5, 5], back=5, bun=True),
    5: dict(front=["########", "########", "##....##"], side=[2, 3, 3, 4, 5, 6, 6, 6], back=6, braid=True),
    6: dict(front=["########", "#.######", "##.##.##", "#......#"], side=[3, 3, 4, 4, 5, 5, 5, 5], back=6, curly=True, vol=True),
    7: dict(front=[], side=[0, 0, 0, 4, 4, 5, 5, 5], back=0, bald=True),
    8: dict(front=["########", "#.####.#"], side=[1, 1, 1, 2, 2, 2, 2, 2], back=3, buzz=True),
    9: dict(front=["########", "########", "#......#"], side=[2, 2, 2, 2, 3, 3, 3, 4], back=4, tail=9, pony=True),
    10: dict(front=["########", "########", "########", "##....##", "#......#", "#......#", "#......#"],
             side=[3, 5, 6, 6, 6, 6, 6, 6], back=6, vol=True, bob=True),
    11: dict(front=["########", "#######.", "##....##", "#......#", "#......#", "#......#", "#......#", "#......#"],
             side=[3, 5, 7, 8, 8, 8, 8, 8], back=8, vol=True, long=5, wavy=True),
}


class Look:
    """Everything about one head that the outfit does not decide."""

    def __init__(self, c: Ctx):
        self.c = c
        st = c.hstyle
        if c.child and st in (7,):
            st = 8
        if st == 7 and not (c.male and c.age >= 2):
            st = 8  # bald is for grown men; everyone else gets a close crop
        self.st = st
        self.hs = HAIR_STYLES[st]
        self.flip = c.v("flip") < 0.5
        # facial hair for grown men
        self.beard = None
        if c.male and c.age >= 2 and not c.child:
            b = c.v("beard")
            if c.face in (5,) and b < 0.9:
                self.beard = "full"
            elif c.face == 6 and b < 0.8:
                self.beard = "moustache"
            elif c.face == 3:
                self.beard = "stubble"
            elif c.face == 2 and b < 0.35:
                self.beard = "goatee"
            elif c.old and b < 0.4:
                self.beard = "full" if b < 0.2 else "goatee"
        self.specs = c.old and c.v("spec") < 0.3
        self.mask = None      # a scarf over the nose and mouth (thieves)
        self.flour = False    # a smear of flour on one cheek
        self.ruddy = False    # red cheeks and nose (the tavern)


def hairpx(c, f, base=2):
    n = h01(c.key, "strand", f.face, f.x if f.face != "top" else f.x + f.y)
    m = h01(c.key, "hairpx", f.part, f.face, f.x, f.y)
    idx = base + (1 if n > 0.72 else -1 if n < 0.18 else 0)
    if m < 0.1:
        idx -= 1
    return cl(c.hr, idx)


def hair_covers_front(L, x, y):
    fr = L.hs["front"]
    if y >= len(fr):
        return False
    row = fr[y]
    if L.flip and (L.hs.get("part") or L.hs.get("wavy")):
        row = row[::-1]
    return row[x] == "#"


def head_front(L: Ctx, f):
    c = L.c
    x, y = f.x, f.y
    hs = L.hs
    S = c.S
    # skin: lit forehead, shaded sides and chin
    if y == 7:
        px = S(2)
    elif x in (0, 7):
        px = S(2) if y > 2 else S(3)
    elif y in (1, 2) and 2 <= x <= 5:
        px = S(4)
    elif y in (5, 6) and x in (1, 6):
        px = S(3)
    else:
        px = S(3)
    if c.child and y in (4, 5, 6) and x in (1, 2, 5, 6):
        px = S(4)  # round cheeks
    # hair
    if not hs.get("bald") and hair_covers_front(L, x, y):
        return hairpx(c, f, 2)
    if hs.get("buzz") and (y == 1 and h01(c.key, "bz", x) < 0.5):
        return cl(c.hr, 1)
    if hs.get("bald") and y <= 1 and 2 <= x <= 5:
        px = S(4)
    # features
    px = face_features(L, f, px)
    return post_face(L, f, px)


def post_face(L, f, px):
    c = L.c
    x, y = f.x, f.y
    if L.mask and y >= 5:
        return T(c, L.mask, 3 if y > 5 else 4, f, folds=0.0, lo=0.1) if not (y == 5 and x in (0, 7)) else px
    if L.flour:
        side = 1 if c.v("fside") < 0.5 else 6
        d = -1 if side == 6 else 1
        if (x, y) in ((side, 5), (side + d, 5), (side + d, 6)):
            return _mixpx(px, (246, 243, 234), 0.85 if (x, y) == (side, 5) else 0.6)
    if L.ruddy and y in (5, 6) and x in (1, 6) or (L.ruddy and (x, y) in ((3, 5), (4, 5), (2, 5), (5, 5))):
        return _mixpx(px, (205, 78, 68), 0.32)
    return px


def face_features(L, f, px):
    c = L.c
    x, y = f.x, f.y
    S = c.S
    fid = c.face
    tone_dark = c.tone <= 1
    eye = EYE_COL[fid]
    if tone_dark and fid in (1, 2, 4, 6):
        eye = EYE_COL[5 if fid != 4 else 0]
    iris = hex_rgba(eye)
    lid = S(2)
    white = EYE_WHITE if not tone_dark else hex_rgba("#e0d8cc")
    thick = fid in (2, 3, 5) or (c.old)
    brow_hair = cl("hc6" if (c.old and c.hcol < 6) else c.hr, 1 if not c.old else 3)
    browc = _mixpx(S(3), tuple(brow_hair[:3]), 0.8 if thick else 0.6)
    # brows (y 3): two pixels over each eye, the hair's colour thinned into the skin
    if y == 3 and not hair_covers_front(L, x, y):
        if x in (1, 2, 5, 6) and (thick or x in (2, 5)):
            return browc if not (thick and x in (1, 6) and not c.old and fid != 5) else _mixpx(S(3), tuple(brow_hair[:3]), 0.4)
        if c.old and x in (0, 7):
            return _mixpx(S(3), tuple(brow_hair[:3]), 0.5)
    # eyes (y 4)
    if y == 4:
        if c.child:
            if x in (1, 6):
                return white
            if x in (2, 5):
                return iris
        elif fid in (2, 6):  # narrow
            if x in (1, 6):
                return lid
            if x in (2, 5):
                return iris
        elif fid == 7:  # heavy lids: the iris sits low, the lid above it is shaded
            if x in (1, 6):
                return white
            if x in (2, 5):
                return iris
        else:
            if x in (1, 6):
                return white
            if x in (2, 5):
                return iris
    if y == 5 and c.child and x in (2, 5):
        return iris
    if y == 5:
        if fid == 7 and x in (1, 2, 5, 6):
            return S(2)  # bags under the eyes
        if x in (3, 4):
            return S(2) if not c.child else S(3)  # the nose
    if y == 3 and fid == 7 and x in (1, 2, 5, 6) and not hair_covers_front(L, x, y):
        return S(3)
    # old faces: crow's feet, forehead lines, cheek lines
    if c.old:
        if y == 4 and x in (0, 7):
            return S(1)
        if y == 2 and x in (2, 5) and not hair_covers_front(L, x, y):
            return S(2)
        if y == 6 and x in (2, 5):
            return S(2)
    # mouth (y 6)
    lipc = _mix(tuple(S(2)[:3]), (150, 50, 50), 0.35 if c.tone >= 3 else 0.22)
    lip = (round(lipc[0]), round(lipc[1]), round(lipc[2]), 255)
    smile = fid in (1, 4, 5) or c.child
    if y == 6:
        if x in (3, 4):
            return lip
        if fid in (3, 5, 0) and x in (2, 5) and not c.child:
            return _px_shift(lip, 1.1)
        if smile and x in (2, 5):
            return S(2)
    if y == 7 and fid in (1, 4) and x in (3, 4):
        return S(2)
    # extras
    ex = fid
    if ex == 1 or (c.child and c.v("frk") < 0.5):  # freckles
        if y in (5, 4) and x in (1, 2, 5, 6) and h01(c.key, "frk", x, y) < 0.55 and not (y == 4 and x in (2, 5)):
            return S(2) if c.tone >= 2 else S(1)
        if y == 5 and x in (3, 4) and h01(c.key, "frk2", x) < 0.4:
            return S(2)
    if ex == 2 and (x, y) in ((2, 2), (2, 3)) or (ex == 2 and (x, y) == (1, 5)):
        return _px_shift(S(4), 1.12) if (x, y) != (2, 3) else _px_shift(S(3), 1.18)  # a pale scar through the brow
    if ex == 4 or c.child:  # rosy cheeks
        if y == 5 and x in (1, 6):
            return _mixpx(S(3), (214, 98, 98), 0.28 if not c.child else 0.38)
        if y == 6 and x in (1, 6) and c.child:
            return _mixpx(S(3), (214, 98, 98), 0.2)
    if ex == 3 and not c.male and (x, y) == (5, 6):
        return S(1)  # a beauty mark
    if ex == 5 and not c.male and (x, y) == (6, 5):
        return S(1)
    # facial hair on the base head (stubble and beards add to the hat layer in volume)
    if L.beard:
        b = beard_base(L, f)
        if b is not None:
            return b
    return px


def _mixpx(px, rgb, t):
    m = _mix(tuple(px[:3]), rgb, t)
    return (round(m[0]), round(m[1]), round(m[2]), 255)


def _px_shift(px, k):
    return (min(255, round(px[0] * k)), min(255, round(px[1] * k)), min(255, round(px[2] * k)), px[3])


def beard_base(L, f):
    c = L.c
    x, y = f.x, f.y
    kind = L.beard
    bc = lambda i: cl(c.hr, i)  # noqa: E731
    if kind == "stubble":
        if y in (5, 6, 7) and h01(c.key, "stub", x, y) < (0.55 if y > 5 else 0.3) and x not in (3, 4) or \
                (y == 7 and x in (3, 4)):
            return _mixpx(c.S(3), tuple(bc(1)[:3]), 0.55)
        return None
    if kind == "moustache":
        if y == 5 and 2 <= x <= 5 and x not in (3, 4) or (y == 5 and x in (3, 4) and False):
            return bc(2)
        return None
    if kind == "goatee":
        if (y == 7 and 2 <= x <= 5) or (y == 6 and x in (2, 5)):
            return bc(2 + (h01(c.key, "gt", x, y) > 0.7))
        if y == 5 and x in (2, 5):
            return bc(2)
        return None
    if kind == "full":
        if y == 5 and (x in (0, 1, 2, 5, 6, 7)):
            return bc(2 + (h01(c.key, "fb", x, y) > 0.7))
        if y == 6 and (x in (0, 1, 2, 5, 6, 7)):
            return bc(2 + (h01(c.key, "fb", x, y) > 0.6))
        if y == 7:
            return bc(2 + (h01(c.key, "fb", x, y) > 0.5))
        if y == 6 and x in (3, 4):
            return None
    return None


def head_base(L, f):
    c = L.c
    hs = L.hs
    S = c.S
    if f.face == "front":
        return head_front(L, f)
    if f.face == "top":
        if hs.get("bald"):
            return S(4 if f.x > 2 else 3)
        if hs.get("buzz"):
            return _buzz(c, f)
        px = hairpx(c, f, 2 + (1 if f.x < 3 and f.y > 4 else 0))
        if hs.get("part") and f.x == (3 if not L.flip else 4) and f.y <= 3:
            return cl(c.hr, 4)  # the parting
        if hs.get("pony") and 3 <= f.x <= 4 and f.y >= 5:
            return cl(c.hr, 3)
        return px
    if f.face == "bottom":
        return cl(c.sk, 1)
    if f.face == "back":
        rows = hs["back"]
        if hs.get("buzz"):
            return _buzz(c, f) if f.y < rows else S(2)
        if hs.get("bald"):
            return hairpx(c, f, 1) if 3 <= f.y <= 4 and (f.x < 7) else S(2)
        if f.y < rows:
            if hs.get("bob") and f.y == rows - 1:
                return cl(c.hr, 1)  # the straight cut
            return hairpx(c, f, 2 - (1 if f.y > rows - 2 else 0))
        return S(2)
    # sides
    fd = f.fd
    n = hs["side"][fd]
    if f.y < n:
        if hs.get("buzz"):
            return _buzz(c, f)
        if hs.get("bald"):
            return hairpx(c, f, 1) if 3 <= f.y <= 4 else S(2)
        if hs.get("bob") and f.y == n - 1:
            return cl(c.hr, 1)
        return hairpx(c, f, 2)
    if fd in (3, 4) and f.y in (4, 5):
        return S(1 if (fd == 4 and f.y == 5) else 2) if not (fd == 3 and f.y == 5) else S(2)
    if L.beard in ("full",) and fd <= 1 and f.y >= 6:
        return cl(c.hr, 2)
    return S(3 if fd < 2 else 2)


def _buzz(c, f):
    n = h01(c.key, "bzz", f.face, f.x, f.y)
    if n < 0.55:
        return cl(c.hr, 1)
    return _mixpx(c.S(3), tuple(cl(c.hr, 1)[:3]), 0.55)


def hair_hat(L, f):
    """Hair that stands proud of the head: volume, curls, buns, ponytail roots."""
    c = L.c
    hs = L.hs
    x, y = f.x, f.y
    if hs.get("curly"):
        if f.face == "bottom":
            return None
        if f.face == "front":
            if y == 0 or (y == 1 and x in (1, 3, 4, 6)) or (y == 2 and x in (0, 7)):
                return cl(c.hr, 3 if (x + y) % 2 else 2)
            return None
        n = hs["side"][f.fd] if f.face in ("left", "right") else hs["back"] if f.face == "back" else 9
        if y >= n:
            return None
        h = h01(c.key, "curl", f.face, x, y)
        if h < 0.12:
            return cl(c.hr, 1)
        return cl(c.hr, 4 if h > 0.86 else 3 if (x + y) % 2 else 2)
    if hs.get("bun"):
        if f.face == "top" and 2 <= x <= 5 and y >= 4:
            return cl(c.hr, 4 if (x, y) in ((3, 5), (2, 4), (3, 4)) else 3 if y < 7 else 2)
        if f.face == "back" and y <= 3 and 2 <= x <= 5 and not (y == 3 and x in (2, 5)):
            return cl(c.hr, 3 if y < 2 else 2)
        if f.face in ("left", "right") and f.fd >= 5 and y <= 1:
            return cl(c.hr, 3)
    if hs.get("pony"):
        if f.face == "back" and 3 <= x <= 4 and y <= 3:
            return cl(c.hr, 3 if y == 0 else 2)
        if f.face == "top" and 3 <= x <= 4 and y >= 6:
            return cl(c.hr, 3)
        if f.face in ("left", "right") and f.fd == 7 and y <= 1:
            return cl(c.hr, 3)
    if hs.get("vol"):
        if f.face == "bottom":
            return None
        if f.face == "top":
            return hairpx(c, f, 3) if h01(c.key, "vt", x, y) < 0.7 else None
        if f.face == "front":
            if y == 0:
                return cl(c.hr, 3) if x in (1, 2, 5, 6) else None
            return None
        n = hs["side"][f.fd] if f.face in ("left", "right") else hs["back"]
        if f.face in ("left", "right") and f.fd <= 1:
            return None
        if y < n - 1 and (f.face == "back" or f.fd >= 2) and y >= 1:
            if hs.get("wavy"):
                return cl(c.hr, 3 if (x + y) % 3 == 0 else 2)
            return cl(c.hr, 3 if y % 2 else 2) if h01(c.key, "vs", f.face, x, y) < 0.8 else None
    return None


def hair_jacket(L, f):
    """Long hair over the body: down the back, braids and strands over the shoulders."""
    c = L.c
    hs = L.hs
    if f.part != "jacket":
        return None
    x, y = f.x, f.y
    if hs.get("long") and f.face == "back":
        n = hs["long"]
        if y < n and 1 <= x <= 6:
            if y == n - 1 and h01(c.key, "lt", x) < 0.5:
                return None
            wob = ((x + y) % 2) if hs.get("wavy") else 0
            return hairpx(c, f, 2 + wob)
    if hs.get("long") and f.face == "front" and not hs.get("wavy") and y <= 3 and x in (0, 7):
        return hairpx(c, f, 2)
    if hs.get("long") and f.face == "front" and hs.get("wavy") and y <= 2 and x in ((0,) if L.flip else (7,)):
        return hairpx(c, f, 2 + (y % 2))
    if hs.get("tail") and f.face == "back" and 3 <= x <= 4:
        n = hs["tail"]
        if y < n:
            if hs.get("tie") and y == 0:
                return cl(mk(RIBBON[int(c.v("rib") * len(RIBBON))]), 4 if x == 3 else 3)
            if hs.get("pony") and y == 0:
                return cl(c.hr, 4)
            if y == n - 1:
                return cl(c.hr, 1)
            return hairpx(c, f, 2 + (1 if x == 3 else 0))
    if hs.get("braid"):
        bx = 1 if L.flip else 6
        if f.face == "front" and x == bx and y <= 9:
            if y == 9:
                return cl(mk(RIBBON[int(c.v("rib") * len(RIBBON))]), 3)  # the ribbon at the end
            return cl(c.hr, 3 if y % 2 == 0 else 1)
        if f.face == "front" and x == (bx + 1 if bx == 1 else bx - 1) and y <= 8 and y % 2 == 1:
            return cl(c.hr, 2)
        if f.face == "back" and y <= 1 and 3 <= x <= 4:
            return hairpx(c, f, 2)
    return None


def build_head(c: Ctx, L: Look, headwear, specs=None):
    """(head fn, hat fn) for the citizen: base head, and the hat layer = headwear / glasses / beard / proud hair."""
    def head(f):
        px = head_base(L, f)
        return px

    def hat(f):
        if headwear is not None:
            r = headwear(f)
            if r is not None:
                return r
        if L.specs and f.face == "front" and f.y == 4:
            fr = cl("bronze", 2)
            if f.x in (0, 3, 4, 7):
                return fr
        if L.specs and f.face == "front" and f.y in (3, 5) and f.x in (1, 2, 5, 6):
            return cl("bronze", 3) if f.y == 3 else cl("bronze", 2)  # the rims of the lenses
        return hair_hat(L, f)
    return head, hat


# ---------------------------------------------------------------------------------------------------------------------
# Composing a skin

OVERLAY_OF = {"body": "jacket", "rarm": "rsleeve", "larm": "lsleeve", "rleg": "rpants", "lleg": "lpants"}
OUTFITS = {}


def outfit(name):
    def deco(fn):
        OUTFITS[name] = fn
        return fn
    return deco


PATCH_COL = ["#8a6a48", "#6a7a5a", "#a0a090", "#7a5a5a", "#5a6a82"]
RIBBON = ["#b0453a", "#4f78a8", "#c8983a", "#7a9a5a", "#9a5a8a"]
NO_PATCH = ("watch", "crier", "clerk", "healer")
PIN_JOBS = {"stallkeeper": 0.4, "baker": 0.4, "miller": 0.4, "mason": 0.4, "boatman": 0.4, "washer": 0.4, "tavernkeeper": 0.4,
            "shopkeeper": 0.4, "porter": 0.3, "clerk": 0.9, "runner": 0.0, "smith": 0.0, "resident": 0.0}


def personal(c: Ctx):
    """What is nobody else's: a mend sewn on an elbow, knee or hem, the Association's bell pin, an earring."""
    cols_ = PATCH_COL if c.role != "thief" else ["#3a3a34", "#2e3a32", "#4a3a30", "#3a2a2e"]
    pc = mk(cols_[int(c.v("patchc") * len(cols_))])
    out = {}
    if c.role not in NO_PATCH:
        if c.v("patchA") < 0.4:
            side = "r" if c.v("patchAs") < 0.5 else "l"

            def pa(f, side=side):
                if f.side == side and f.face in ("front", "back") and 4 <= f.y <= 5 and 1 <= f.u <= 2:
                    edge = f.y == 5 or f.u == 2
                    return P(pc, 2 if edge else 3, f) if (f.y, f.u) != (4, 1) else P(pc, 4, f)
            out["rarm"] = out["larm"] = pa
        if c.v("patchL") < 0.35 and c.role not in ("bellringer", "washer", "archivist"):
            side = "r" if c.v("patchLs") < 0.5 else "l"

            def pl(f, side=side):
                if f.side == side and f.face in ("front", "back") and 5 <= f.y <= 6 and 1 <= f.u <= 2:
                    return P(pc, 2 if (f.y == 6 or f.u == 2) else 3, f)
            out["rleg"] = out["lleg"] = pl
        if c.v("patchB") < 0.28 and c.role not in ("watch",):
            bx = 1 if c.v("patchBx") < 0.5 else 5   # clear of the buckle and buttons down the middle

            def pb(f, bx=bx):
                if f.face == "front" and 8 <= f.y <= 9 and bx <= f.x <= bx + 1:
                    return P(pc, 2 if (f.y == 9 or f.x == bx + 1) else 3, f)
            out["jacket"] = pb
    pin_p = PIN_JOBS.get(c.role, 0.0)
    if c.v("pin") < pin_p:
        pn = spr(c, 5, 2, ["B", "d"], {"B": (BRONZE, 4, "f"), "d": (BRONZE, 2, "f")})
        prev = out.get("jacket")
        out["jacket"] = lay(prev, pn) if prev else pn
    if c.v("ear") < (0.10 if c.male else 0.22) and not c.child:
        def er(f):
            if f.face == "right" and f.fd == 4 and f.y == 6:
                return cl(BRASS, 4)
        out["head"] = er
    return out


def compose(c: Ctx, name=None) -> Canvas:
    name = name or c.role
    parts = PARTS_SLIM if c.slim else PARTS_WIDE
    L = Look(c)
    o = OUTFITS[name](c, L)
    headwear = o.get("hat")
    head, hat = build_head(c, L, headwear)
    # long hair and braids ride on the jacket layer unless the outfit already paints there
    pers = personal(c)
    jacket = lay(pers.get("jacket"), o.get("jacket"), lambda f: hair_jacket(L, f))
    head0 = head
    head = lay(pers.get("head"), head0) if pers.get("head") else head0
    o["rarm"] = lay(pers.get("rarm"), o["rarm"])
    o["larm"] = lay(pers.get("larm"), o.get("larm", o["rarm"])) if "larm" in o else o["rarm"]
    o["rleg"] = lay(pers.get("rleg"), o["rleg"])
    o["lleg"] = lay(pers.get("lleg"), o.get("lleg", o["rleg"])) if "lleg" in o else o["rleg"]
    fns = {"head": head, "hat": hat, "body": o["body"], "jacket": jacket,
           "rarm": o["rarm"], "larm": o.get("larm", o["rarm"]),
           "rsleeve": o.get("rsleeve"), "lsleeve": o.get("lsleeve", o.get("rsleeve")),
           "rleg": o["rleg"], "lleg": o.get("lleg", o["rleg"]),
           "rpants": o.get("rpants"), "lpants": o.get("lpants", o.get("rpants"))}
    cv = Canvas(64, 64)
    for part in ALL_PARTS:
        fn = fns[part]
        if fn is None:
            continue
        paint_part(cv, part, parts, c.slim, lay(fn, (lambda f: skin_px(c, f)) if part in BASE_PARTS else None))
    validate(cv, c.slim, c.key)
    return cv


def validate(cv: Canvas, slim, name):
    parts = PARTS_SLIM if slim else PARTS_WIDE
    for part in BASE_PARTS:
        u, v, w, h, d = parts[part]
        for face, (fx, fy, fw, fh) in _faces(u, v, w, h, d).items():
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    assert cv.get(x, y)[3] == 255, f"{name}: {part}.{face} not opaque at {x},{y}"
    for y in range(64):
        for x in range(64):
            assert cv.get(x, y)[3] in (0, 255), f"{name}: partial alpha at {x},{y}"
    # the slim arms' unused columns (and everything outside the boxes) stay transparent
    used = set()
    for part in ALL_PARTS:
        u, v, w, h, d = parts[part]
        for face, (fx, fy, fw, fh) in _faces(u, v, w, h, d).items():
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    used.add((x, y))
    for y in range(64):
        for x in range(64):
            if (x, y) not in used:
                assert cv.get(x, y)[3] == 0, f"{name}: paint outside the part boxes at {x},{y}"


# ---------------------------------------------------------------------------------------------------------------------
# Roster

def load_roster():
    d = json.load(open(ROSTER))
    f = d["citizens"]["fields"]
    ix = {k: i for i, k in enumerate(f)}
    ppl = []
    for r in d["citizens"]["rows"]:
        if r[ix["named"]]:
            continue
        ppl.append(Ctx(r[ix["id"]], r[ix["job"]], tuple(r[ix["look"]]), r[ix["build"]], r[ix["age"]], r[ix["given"]]))
        ppl[-1].household = r[ix["household"]]
        ppl[-1].jobid = r[ix["job"]]
        ppl[-1].file = "c%03d" % r[ix["id"]]
    for e in d["extras"]:
        c = Ctx(e["id"], "thief", tuple(e["look"]), e["build"], 2, "", role="thief")
        c.role = c.jobname = "thief"
        c.household = -1 - (e["id"] - 240)
        c.jobid = 99
        c.file = "t%d" % (e["id"] - 240)
        ppl.append(c)
    return ppl


# ---------------------------------------------------------------------------------------------------------------------
# Previews

def crop(img: Image.Image, part, parts, face):
    u, v, w, h, d = parts[part]
    fx, fy, fw, fh = _faces(u, v, w, h, d)[face]
    return img.crop((fx, fy, fx + fw, fy + fh))


def figure(img, slim, back=False) -> Image.Image:
    if isinstance(img, Canvas):
        img = img.image()
    parts = PARTS_SLIM if slim else PARTS_WIDE
    face = "back" if back else "front"
    fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def put(base, over, x, y):
        for part in (base, over):
            im = crop(img, part, parts, face)
            fig.alpha_composite(im, (x, y))

    put("head", "hat", 4, 0)
    put("body", "jacket", 4, 8)
    aw = 3 if slim else 4
    left_x, right_x = (12, 16 - aw - 0)
    # the viewer's left of the front is the right arm; from behind it is the left arm
    a_left, a_right = ("larm", "rarm") if back else ("rarm", "larm")
    s_left, s_right = ("lsleeve", "rsleeve") if back else ("rsleeve", "lsleeve")
    for (a, s), x in (((a_left, s_left), 4 - aw), ((a_right, s_right), 12)):
        for part in (a, s):
            fig.alpha_composite(crop(img, part, parts, face), (x, 8))
    l_left, l_right = ("lleg", "rleg") if back else ("rleg", "lleg")
    p_left, p_right = ("lpants", "rpants") if back else ("rpants", "lpants")
    for (a, s), x in (((l_left, p_left), 4), ((l_right, p_right), 8)):
        for part in (a, s):
            fig.alpha_composite(crop(img, part, parts, face), (x, 20))
    return fig


def _font(n):
    from PIL import ImageFont
    try:
        return ImageFont.load_default(size=n)
    except TypeError:
        return ImageFont.load_default()


def sheet(people, skins, back, path, cols=24, scale=4, bg=(58, 60, 66), only=None):
    """Every citizen as a standing figure, grouped by job, with the job name and id under each."""
    cw, ch = 16 * scale + 10, 32 * scale + 34
    groups = {}
    for c in people:
        groups.setdefault(c.jobid, []).append(c)
    order = sorted(groups)
    rows_total = 0
    layout = []
    for j in order:
        lst = groups[j]
        if only and lst[0].role not in only:
            continue
        n = (len(lst) + cols - 1) // cols
        layout.append((lst, rows_total))
        rows_total += n
    W = cols * cw + 12
    H = rows_total * ch + 12 + 18 * len(layout)
    im = Image.new("RGBA", (W, H), bg + (255,))
    from PIL import ImageDraw
    dr = ImageDraw.Draw(im)
    f_small, f_head = _font(11), _font(14)
    y0 = 6
    for lst, _ in layout:
        dr.text((8, y0), f"{lst[0].role}  ({len(lst)})", fill=(235, 220, 170), font=f_head)
        y0 += 18
        for k, c in enumerate(lst):
            gx, gy = 6 + (k % cols) * cw, y0 + (k // cols) * ch
            fig = figure(skins[c.file], c.slim, back).resize((16 * scale, 32 * scale), Image.NEAREST)
            im.alpha_composite(fig, (gx + 5, gy))
            dr.text((gx + 5, gy + 32 * scale + 2), c.file, fill=(225, 225, 230), font=f_small)
            dr.text((gx + 5, gy + 32 * scale + 15), c.given[:9], fill=(160, 165, 175), font=f_small)
        y0 += ((len(lst) + cols - 1) // cols) * ch
    im.convert("RGB").save(path)
    return im.size


# ---------------------------------------------------------------------------------------------------------------------
# Outfit building blocks

BELL4 = [".hB.",
         "hBBb",
         "hBBb",
         "BBbb",
         ".dd."]
BELL3 = [".B.",
         "hBb",
         "bbb",
         ".d."]
BELLG = {"h": (BRONZE, 5, "f"), "B": (BRONZE, 4, "f"), "b": (BRONZE, 3, "f"), "d": (BRONZE, 1, "f")}


def spr(c, x0, y0, rows, legend, face="front", parts=None):
    """A little sprite stamped on a face (limbs: columns are u)."""
    def fn(f):
        if f.face != face or (parts and f.part not in parts):
            return None
        i, j = f.u - x0, f.y - y0
        if 0 <= j < len(rows) and 0 <= i < len(rows[j]):
            ch = rows[j][i]
            if ch != ".":
                return res(c, legend[ch], f)
        return None
    return fn


def torso(c, cloth, idx, neck=True, extra=None, hem=None):
    """The base body: cloth all round, the neck's shadow on the front, an optional hem."""
    def fn(f):
        if extra is not None:
            r = extra(f)
            if r is not None:
                return r
        if f.face == "front" and f.y == 0 and f.x in (3, 4) and neck:
            return c.S(2)
        if f.face == "bottom":
            return P(cloth, 1, f)
        if hem is not None and f.y == 11 and f.face != "top":
            return P(hem, 3, f)
        return T(c, cloth, idx, f)
    return fn


def arm(c, cloth, idx, end, cuff=None, cuff_idx=3, glove=None, glove_from=9, extra=None, soot=None, bare_hi=3, folds=0.25):
    """An arm: cloth down to row `end`, an optional cuff row under it, then bare skin or a glove."""
    def fn(f):
        if extra is not None:
            r = extra(f)
            if r is not None:
                return r
        y = f.y
        if f.face == "top":
            return T(c, cloth, idx, f)
        if f.face == "bottom":
            return P(glove, 2, f) if glove else c.S(2)
        if y <= end:
            return T(c, cloth, idx, f, folds=folds)
        if cuff is not None and y == end + 1:
            return P(cuff, cuff_idx, f)
        if glove is not None and y >= glove_from:
            return glove_px(c, glove, f, y == glove_from)
        px = cl(c.sk, shade(f, bare_hi) - (1 if f.face == "back" else 0))
        if soot and h01(c.key, "armsoot", f.part, f.face, f.x, y) < soot:
            return _mixpx(px, (44, 36, 30), 0.5)  # soot ground into the skin
        return px
    return fn


def glove_px(c, r, f, top):
    return T(c, r, 4 if top else 3, f, folds=0.0, lo=0.1)


def trousers(c, cloth, idx, boot_from=9, boot=LEATHER, boot_idx=3, extra=None, cuff=True, patch=None):
    """A leg: trousers down to boot_from, then a boot (cuff highlight, shaded shaft, dark sole), scuffed."""
    def fn(f):
        if extra is not None:
            r = extra(f)
            if r is not None:
                return r
        y = f.y
        if f.face == "top":
            return T(c, cloth, idx, f)
        if f.face == "bottom":
            return P(DARKLEATHER, 1)
        if y >= boot_from:
            if y == 11:
                return P(DARKLEATHER, 2 if f.face != "front" else 1)
            if y == boot_from and cuff:
                return P(boot, boot_idx + 1, f)
            n = h01(c.key, "scuff", f.part, f.face, f.x, y)
            if f.face == "front" and y >= 10 and n < 0.22:
                return P(boot, boot_idx + 1, f)
            return T(c, boot, boot_idx if y < 10 else boot_idx - 1, f, folds=0.0, lo=0.08)
        return T(c, cloth, idx, f)
    return fn


# --- headwear ---------------------------------------------------------------------------------------------------------

def cap_flat(c, r, idx=3, brim=True, band=None):
    """A flat cloth cap: a low crown, a short peak on the front."""
    def fn(f):
        if f.face == "top":
            return T(c, r, idx + 1, f, folds=0.0)
        if f.face == "bottom":
            return None
        y = f.y
        lim = 1 if f.face != "front" else 1
        if f.face in ("left", "right"):
            if y <= 1 or (y == 2 and f.fd <= 1 and brim):
                return T(c, r, idx - (1 if y == 2 else 0), f, folds=0.0)
            if y == 2 and f.fd >= 6 and False:
                return None
            return None
        if f.face == "back":
            return T(c, r, idx - 1, f, folds=0.0) if y <= 1 else None
        if y <= 1:
            if band and y == 1:
                return P(band, 3, f)
            return T(c, r, idx if y == 0 else idx - 1, f, folds=0.0)
        if y == 2 and brim and 1 <= f.x <= 6:
            return P(r, idx - 2, f)
        return None
    return fn


def cap_knit(c, r, cuffr=None, idx=3, drop=3):
    """A knitted cap: ribbed, a turned cuff, closer round the ears."""
    cuffr = cuffr or r

    def fn(f):
        if f.face == "bottom":
            return None
        if f.face == "top":
            return P(r, idx + (1 if (f.x + f.y) % 2 else 0), f)
        y = f.y
        n = {"front": 2, "back": drop, "left": drop, "right": drop}[f.face]
        if f.face in ("left", "right") and f.fd >= 5:
            n = drop
        if y <= n:
            if y == n or (y == n - 1 and False):
                return P(cuffr, idx + 1 + (f.x % 2), f)
            return P(r, idx + (1 if (f.x + y) % 2 else -1 if f.x % 2 == 0 else 0), f)
        return None
    return fn


def kerchief(c, r, dot=None, idx=3, knot_side=True):
    """A headscarf: over the hair, tied at one side of the nape."""
    def fn(f):
        if f.face == "bottom":
            return None
        y = f.y
        if f.face == "top":
            return T(c, r, idx, f, folds=0.1)
        if f.face == "front":
            if y == 0 or (y == 1 and f.x in (0, 7)):
                return T(c, r, idx - 1 + (y == 0), f, folds=0.0)
            return None
        if f.face in ("left", "right"):
            lim = 4 if f.fd >= 3 else 2 if f.fd == 2 else 1
            if y < lim:
                return T(c, r, idx, f, folds=0.15)
            return None
        # back with the knot
        if y <= 4:
            if dot and h01(c.key, "dot", f.x, y) < 0.2:
                return P(dot, 4, f)
            return T(c, r, idx - 1, f, folds=0.2)
        if y in (5, 6) and 5 <= f.x <= 6:
            return P(r, idx + (y == 5), f)  # the knot's tails
        return None
    return fn


def headband(c, r, idx=3, stud=None):
    def fn(f):
        if f.face in ("top", "bottom"):
            return None
        y = f.y
        if y == 1 or (f.face == "front" and y == 1):
            if stud and f.face == "front" and f.x == 6 and y == 1:
                return P(stud, 4, f)
            return T(c, r, idx, f, folds=0.0)
        return None
    return fn


def hood_up(c, r, idx=3, open_rows=(2, 6), open_x=(1, 6)):
    """A hood worn up, on the hat layer; the face shows through the opening."""
    def fn(f):
        if f.face == "bottom":
            return None
        y, x = f.y, f.x
        if f.face == "front":
            if y <= 1:
                return T(c, r, idx + (y == 0), f, folds=0.0)
            if y <= 6 and (x == 0 or x == 7):
                return T(c, r, idx - 1, f, folds=0.0)
            if y == 2 and x in (1, 6):
                return P(r, idx - 2, f)
            return None
        return T(c, r, idx - (1 if f.face == "back" else 0), f, folds=0.3)
    return fn


def skirt(c, r, to=8, idx=3, hem=None, hem_idx=2, bronze_hem=False):
    """The pants layer as a long skirt / coat tails: the top `to`+1 rows of the legs."""
    def fn(f):
        if f.face == "bottom":
            return None
        if f.y > to:
            return None
        if f.face == "top":
            return T(c, r, idx, f)
        if f.y == to:
            if bronze_hem:
                return P(BRONZE, 3, f)
            if hem:
                return P(hem, hem_idx, f)
            return T(c, r, idx - 1, f)
        return T(c, r, idx, f)
    return fn


def mailpx(c, f, idx=3):
    """Chain mail: a lattice of lit rings with dark holes between them, quiet at a distance."""
    x, y = f.x + (1 if f.face in ("left", "right") else 0), f.y
    i = idx
    if y % 2 == 0 and (x + y // 2) % 2 == 0:
        i += 1
    elif y % 2 == 1 and (x + y // 2) % 2 == 1:
        i -= 1
    n = h01(c.key, "mail", f.part, f.face, f.x, f.y)
    if n < 0.05:
        i += 1
    return cl(MAIL, shade(f, i))


def pick(c, seq, tag=""):
    return seq[(c.outfit + (0 if not tag else 0)) % len(seq)]


# ---------------------------------------------------------------------------------------------------------------------
# The jobs

# --- resident -----------------------------------------------------------------------------------------------------------

@outfit("resident")
def resident(c, L):
    wool = mk(jig(c, ["#7b6f8c", "#5f7048", "#b4a581", "#59606d"][c.outfit], 0.07))
    dark = mk(jig(c, ["#43382f", "#3a3a36", "#4a4333", "#2f3541"][(c.outfit + c.accent) % 4], 0.06))
    acc_cols = ["#8a5a58", "#a58a48", "#4f6d6d", "#5d4560"]
    a = c.accent
    skirtish = (not c.male) and c.v("skirt") < 0.55
    shawl_r = mk(jig(c, acc_cols[(c.outfit + 1) % 4], 0.05))
    vest_r = mk(jig(c, ["#4a3426", "#2f3a30", "#3c3c46", "#5a3a2a"][c.outfit], 0.05))
    shirt = LINEN

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        if a == 3 and f.face == "front" and f.x in (3, 4) and 1 <= f.y <= 10:
            return T(c, shirt, 3, f)
        if f.y >= 10 and f.face not in ("top", "bottom"):
            return P(wool, 2 + ((f.x + f.y) % 2), f)  # a ribbed hem
        return T(c, wool, 3, f)

    jacket = None
    if a == 1:  # a shawl, pinned
        def jacket(f):
            x, y = f.x, f.y
            if f.face == "front":
                rows = ["########", "###..###", ".##..##.", "..#..#..", "..#..#..", "..#..#.."]
                return _shawl(c, f, shawl_r, rows)
            if f.face == "back":
                rows = ["########", "########", "#######.", ".######.", "..####..", "..####..", "...##...", "...##..."]
                return _shawl(c, f, shawl_r, rows)
            if f.face in ("left", "right") and y <= 1:
                return T(c, shawl_r, 3, f)
            if f.face == "top":
                return T(c, shawl_r, 4, f)
    elif a == 2:  # a waistcoat
        jacket = grid(c, {"W": (vest_r, 3), "e": (vest_r, 2, "f"), "B": (BRASS, 4, "f")},
                      front=[".WW..WW.", ".WW..WW.", ".WWeWWW.", ".WWeBWW.", ".WWeWWW.", ".WWeBWW.", ".WWeWWW.", ".WWeBWW.",
                             ".WWeWWW.", ".WWeBWW.", "..WWWW..", "........"],
                      back=[".WWWWWW."] * 2 + [".WeeeeW."] + [".WWWWWW."] * 7 + ["..WWWW..", "........"])
    elif a == 3 and c.male:  # a cardigan's front panels
        jacket = grid(c, {"W": (dark, 3), "b": (BRONZE, 3, "f")},
                      front=["WWW..WWW"] * 11 + ["WWW..WWW"],
                      side=["WWWW"] * 10 + ["WWWW", "WWWW"])
        jacket = lay(jacket)
    pin = spr(c, 5, 2, ["B", "d"], {"B": (BRONZE, 4, "f"), "d": (BRONZE, 2, "f")})  # the town's bell pin
    brooch = spr(c, 3, 2, ["B", "d"], {"B": (BRONZE, 4, "f"), "d": (BRONZE, 2, "f")})  # holds the shawl closed
    buttons = spr(c, 2, 3, ["B", ".", ".", "B", ".", ".", "B"], {"B": (BRONZE, 3, "f")})
    jk = lay(pin if a in (0, 2) else brooch if a == 1 else buttons if a == 3 and c.male else pin, jacket)

    def patch(f):
        if a in (0, 3) and f.face in ("front", "back") and 4 <= f.y <= 5 and 1 <= f.u <= 2:
            return T(c, LEATHER, 3 if f.y == 4 else 2, f, folds=0.0)
        return None
    sl_end = 9
    arm_f = arm(c, wool, 3, sl_end, extra=patch)
    if a == 3 and not c.male:  # women's cardigan accent: sleeves a shade darker
        arm_f = arm(c, dark, 3, sl_end, extra=patch)
    legs = trousers(c, dark, 3, boot_from=9, boot=LEATHER, boot_idx=2)
    rp = skirt(c, dark if a != 2 else wool, 8, hem=dark, hem_idx=1) if skirtish else None
    hat = None
    if c.old and a == 1 and not c.male:
        hat = kerchief(c, mk(jig(c, acc_cols[(c.outfit + 3) % 4], 0.05)), dot=None)
    elif a == 3 and c.male:
        hat = cap_flat(c, mk(jig(c, "#6b655a", 0.06)), 3, True)
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, rpants=rp, hat=hat)


def _shawl(c, f, r, rows):
    x, y = f.x, f.y
    if y < len(rows) and rows[y][x] == "#":
        if y >= len(rows) - 2 and (x + y) % 2 == 0:
            return P(r, 1, f)  # fringe
        return T(c, r, 3, f, folds=0.3)
    return None


# --- child ---------------------------------------------------------------------------------------------------------

@outfit("child")
def child(c, L):
    tun = mk(jig(c, ["#4f93c8", "#d0707e", "#58a050", "#d0a030"][c.outfit], 0.06))
    trim = mk(jig(c, ["#f2e6c8", "#7a4a38", "#f2e6c8", "#6a4430"][c.outfit], 0.04))
    shorts = mk(jig(c, ["#5a4a3a", "#3f4a5c", "#4a4034", "#55503f"][(c.outfit + c.accent) % 4], 0.06))
    girl = not c.male

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        if f.face == "front" and f.y == 1 and f.x in (3, 4):
            return P(trim, 3, f)  # a round collar
        if f.y == 10 or (f.y == 11 and f.face != "bottom"):
            return P(trim, 3 if f.y == 10 else 2, f)  # a hem band
        return T(c, tun, 3, f)
    jk = lay(spr(c, 3, 2, ["B."], {"B": (BRONZE, 4, "f")}))

    def patch(f):
        if f.face in ("front", "back") and 4 <= f.y <= 5 and 1 <= f.u <= 2 and c.accent != 3:
            return T(c, trim, 3 if f.y == 4 else 2, f, folds=0.0)
        if f.face in ("left", "right") and 4 <= f.y <= 5 and f.outer and f.fd in (1, 2):
            return T(c, trim, 3, f, folds=0.0)
        return None
    arm_f = arm(c, tun, 3, 4, cuff=trim, cuff_idx=3, extra=patch)  # short sleeves, a bare forearm
    sh = lambda f: None  # noqa: E731
    legs = kid_legs(c, shorts, 4 if not girl else 3)
    rp = None
    if girl:  # a dress: the skirt flares over the thighs
        rp = skirt(c, tun, 4, hem=trim, hem_idx=3)
    hat = None
    if c.accent == 3:
        hat = cap_knit(c, mk(jig(c, ["#b84a40", "#5a7a9a", "#c9a24a", "#7a8a5a"][c.outfit], 0.08)),
                       mk(jig(c, "#e8e0c8", 0.04)), 3, 3)
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, rpants=rp, hat=hat)


def kid_legs(c, cloth, to):
    """Shorts to row `to`, bare knees and shins, socks, scuffed shoes."""
    sock = LINEN

    def fn(f):
        y = f.y
        if f.face == "top":
            return T(c, cloth, 3, f)
        if f.face == "bottom":
            return P(DARKLEATHER, 1)
        if y <= to:
            return T(c, cloth, 3, f) if y < to else P(cloth, 2, f)
        if y >= 10:
            if y == 11:
                return P(DARKLEATHER, 1 if f.face == "front" else 2, f)
            n = h01(c.key, "kscuff", f.part, f.face, f.x, y)
            return P(LEATHER, 4 if n < 0.3 else 2, f)
        if y == 9:
            return T(c, sock, 3, f, folds=0.0) if c.v("sock") < 0.6 else cl(c.sk, shade(f, 3))
        px = cl(c.sk, shade(f, 3))
        if y in (5, 6) and f.face == "front" and f.u in (1, 2) and h01(c.key, "knee", f.part, f.u, y) < 0.18:
            return cl(c.sk, shade(f, 2))  # a scraped knee
        return px
    return fn


# --- watch -----------------------------------------------------------------------------------------------------------

@outfit("watch")
def watch(c, L):
    blue = mk(jig(c, ["#243f73", "#27447a", "#213a6a", "#2a4678"][c.outfit], 0.015))
    trous = mk(jig(c, "#2b303f", 0.03))
    rank = int(c.v("rank") * 3)          # 0 watchman, 1 a bronze band on the sleeve, 2 two bands
    pouch_x = 0 if c.v("pouch") < 0.5 else 6
    has_pouch = c.v("haspouch") < 0.6

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        if f.face == "bottom":
            return P(MAIL, 1, f)
        return mailpx(c, f, 2)

    def tab(f):
        x, y = f.x, f.y
        if f.face == "top":
            return P(blue, 3, f) if 1 <= x <= 6 else None
        if f.face == "bottom":
            return None
        if f.face in ("left", "right"):
            if y == 8:
                return P(LEATHER, 3, f)  # the belt runs round
            return None
        if y == 8:
            if f.face == "front" and x in (3, 4):
                return P(BRONZE, 5 if x == 3 else 4, f)  # the buckle
            return T(c, LEATHER, 3, f, folds=0.0)
        if has_pouch and f.face == "front" and 9 <= y <= 10 and pouch_x <= x <= pouch_x + 1:
            return T(c, LEATHER, 3 if y == 9 else 2, f, folds=0.0)  # a belt pouch
        if y == 9 and f.face == "front" and x in (3, 4) and False:
            return None
        if not (1 <= x <= 6):
            return None
        if y == 0:
            return T(c, blue, 3, f, folds=0.0) if x in (1, 2, 5, 6) else None
        if y == 11:
            return P(BRONZE, 3 if (x + (0 if f.face == "front" else 1)) % 2 else 2, f)  # bronze hem
        if x in (1, 6):
            return P(blue, 2 if y > 8 else 4, f)  # piped edge
        if h01(c.key, "tabstain", f.face, x, y) < 0.035 and y > 6:
            return P(blue, 1, f)
        bell = spr(c, 2, 2, BELL4, BELLG, face=f.face)(f)
        if bell is not None:
            return bell
        return T(c, blue, 3, f, folds=0.2)

    def sleeve(f):  # a lighter ring of mail at the shoulder, a bronze stud
        if f.face in ("top",):
            return mailpx(c, f, 4)
        if f.face == "bottom" or f.inner:
            return None
        if f.y <= 1:
            return mailpx(c, f, 4) if not (f.face == "front" and f.u == 0 and f.y == 1) else P(BRONZE, 4, f)
        if rank >= 1 and f.y == 3:
            return P(BRONZE, 4, f)  # a sergeant's band
        if rank >= 2 and f.y == 5:
            return P(BRONZE, 3, f)
        return None

    def armf(f):
        y = f.y
        if f.face == "top":
            return mailpx(c, f, 3)
        if f.face == "bottom":
            return P(DARKLEATHER, 1)
        if y <= 7:
            return mailpx(c, f, 3)
        if y in (8, 9):  # leather bracers
            return T(c, LEATHER, 4 if y == 8 else 3, f, folds=0.0)
        return T(c, DARKLEATHER, 4 if y == 10 else 3, f, folds=0.0, lo=0.1)  # gloves

    legs = trousers(c, trous, 3, boot_from=8, boot=DARKLEATHER, boot_idx=3,
                    extra=lambda f: P(BRONZE, 4, f) if f.face == "front" and f.u == 1 and f.y == 9 else None)
    # headgear: a kettle helmet or a blue cap with a brass badge
    if c.accent in (0, 1):
        def hat(f):
            if f.face == "bottom":
                return None
            x, y = f.x, f.y
            if f.face == "top":
                return P(IRON, 5 if x in (3, 4) else 4 if x in (2, 5) else 3, f)
            if y == 3:
                return P(BRONZE, 4 if (x + y) % 3 else 3, f)  # the bronze rim
            if y > 3:
                return None
            if f.face == "front" and y == 0 and x in (3, 4):
                return P(IRON, 5, f)
            if y == 1 and x in (1, 6) and f.face == "front":
                return P(BRONZE, 4, f)  # rivets
            return T(c, IRON, 3 if y > 0 else 4, f, folds=0.12, lo=0.08)
    else:
        capc = blue

        def hat(f):
            if f.face == "bottom":
                return None
            x, y = f.x, f.y
            if f.face == "top":
                return T(c, capc, 4, f, folds=0.0)
            if f.face == "front":
                if y <= 1:
                    if x in (3, 4) and y <= 1:
                        return P(BRONZE, [[5, 4], [3, 2]][y][x - 3], f)  # the badge
                    return T(c, capc, 3, f, folds=0.0)
                if y == 2 and 1 <= x <= 6:
                    return P(DARKLEATHER, 3, f)  # the peak
                return None
            if y <= 1:
                return T(c, capc, 3 - (f.face == "back"), f, folds=0.0)
            return None
    return dict(body=body, jacket=tab, rarm=armf, rsleeve=sleeve, rleg=legs, hat=hat)


# --- shopkeeper --------------------------------------------------------------------------------------------------------

@outfit("shopkeeper")
def shopkeeper(c, L):
    vest = mk(jig(c, ["#6a3b5e", "#2f6a6b", "#b08a2e", "#2e5a3a"][c.outfit], 0.05))
    shirt = LINEN
    cols = ["#b3322e", "#c8983a", "#26262c", "#2f5fa8"]
    gart = mk(cols[c.accent])
    trou = mk(jig(c, ["#3a3640", "#4a4034", "#2e3446", "#46403a"][(c.outfit + c.accent) % 4], 0.05))
    long_skirt = (not c.male) and c.v("skirt") < 0.6

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, shirt, 3, f, folds=0.18)

    jk = grid(c, {"W": (vest, 3), "e": (vest, 1, "f"), "B": (BRONZE, 4, "f"), "L": (vest, 4), "c": (BRONZE, 3, "f"),
                  "l": (LINEN, 2, "f")},
              front=[".WW..WW.", ".WWl.lWW", ".WWWeWWW"[:8], "WWWBeWWW", "WWWWeWWW", "WWWBeWWW", "WWWWeWWW", "WWWBeWWW",
                     "WWWWeWWW", "WWWBeWWW", ".WWWWWW.", "........"],
              back=[".WWWWWW."] * 8 + ["WWWeeWWW"] + [".WWWWWW."] + ["..WWWW..", "........"],
              side=["WW.."] * 3 + ["WWW."] * 6 + ["WW..", "....", "...."])
    jk = lay(spr(c, 1, 6, ["cc"], {"c": (BRONZE, 3, "f")}) if c.v("chain") < 0.6 else None, jk)

    def sleeves_arm(f):
        if f.face in ("front", "back") and f.y == 3 and f.face != "top":
            return P(gart, 3, f)  # the sleeve garter
        return None
    arm_f = arm(c, shirt, 3, 9, extra=lambda f: sleeves_arm(f) if f.face in ("front", "back", "left", "right") and f.y in (3, 4) and f.y == 3 else None)
    legs = trousers(c, trou, 3, boot_from=9, boot=DARKLEATHER, boot_idx=3,
                    extra=lambda f: P(trou, 4, f) if f.face == "front" and f.u in (1, 2) and f.y < 9 and f.y > 1 and False else None)
    rp = skirt(c, trou, 8, hem=BRONZE, hem_idx=2) if long_skirt else None
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, rpants=rp, hat=None)


# --- porter ------------------------------------------------------------------------------------------------------------

@outfit("porter")
def porter(c, L):
    shirt = mk(jig(c, ["#a2542c", "#a8895a", "#76744a", "#a8742c"][c.outfit], 0.06))
    trou = mk(jig(c, ["#5a4630", "#4f4a3e", "#4a3a2a", "#5a5240"][(c.outfit + 1) % 4], 0.06))
    tweed = mk(jig(c, ["#6a5a46", "#58606a", "#3c3a36", "#5a6446"][c.outfit], 0.06))

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, shirt, 3, f)

    kerch = mk(["#b0453a", "#4f78a8", "#c8983a", "#e0d8c0"][int(c.v("kerch") * 4)])

    def straps(f):
        x, y = f.x, f.y
        if f.face == "top":
            return None
        if f.face == "front" and ((y == 0 and 2 <= x <= 5) or (y == 1 and 3 <= x <= 4)) and c.v("kerch2") < 0.65:
            return T(c, kerch, 3 if y == 0 else 4, f, folds=0.0)  # a knotted neckerchief
        if f.face == "front":
            if x in (1, 6) and y <= 7:
                return T(c, LEATHER, 3, f, folds=0.0)
            if x in (1, 6) and y == 4:
                return P(BRONZE, 4, f)
            return None
        if f.face == "back":
            sx = round(1 + y * 5 / 9) if y <= 9 else -9
            ex = round(6 - y * 5 / 9) if y <= 9 else -9
            if x in (sx, ex) and y <= 9 and not (3 <= x <= 4 and 4 <= y <= 5):
                return T(c, LEATHER, 3 if x == sx else 4, f, folds=0.0)
            if 3 <= x <= 4 and 4 <= y <= 5:
                return P(BRONZE, 5 if (x, y) == (3, 4) else 4 if y == 4 or x == 3 else 3, f)  # the buckle plate
        return None

    def rolled(f):  # the rolled sleeve: a fat lighter cuff
        if f.face in ("front", "back", "left", "right") and f.y in (3, 4) and not f.inner:
            return T(c, shirt, 4 if f.y == 3 else 2, f, folds=0.1)
        return None
    arm_f = arm(c, shirt, 3, 2, cuff=shirt, cuff_idx=4, bare_hi=3)

    def armx(f):
        if f.face not in ("top", "bottom") and f.y in (3, 4):
            return T(c, shirt, 4 if f.y == 3 else 2, f, folds=0.1)
        if f.face not in ("top", "bottom") and f.y > 4:
            n = h01(c.key, "rope", f.part, f.face, f.x, f.y)
            if f.y in (7, 8) and f.face == "front" and f.u == 1 and n < 0.7:
                return cl(c.sk, 2)  # a rope burn
        return None
    arm_f = lay(armx, arm(c, shirt, 3, 2, bare_hi=3))
    legs = trousers(c, trou, 3, boot_from=8, boot=LEATHER, boot_idx=2,
                    extra=lambda f: P(trou, 4, f) if f.face == "front" and f.u in (1, 2) and f.y in (5, 6) and h01(c.key, "kn", f.part, f.u, f.y) < 0.5 and False else None)
    hat = cap_flat(c, tweed, 3, True) if c.accent != 3 else headband(c, mk(jig(c, "#b0a090", 0.05)), 3)
    return dict(body=body, jacket=straps, rarm=arm_f, rleg=legs, hat=hat)


# --- stallkeeper ---------------------------------------------------------------------------------------------------------

AWNING = ["#b13a34", "#2f5fa8", "#d3a72c", "#3f8a4a", "#d2742a", "#7a4a9a"]


@outfit("stallkeeper")
def stallkeeper(c, L):
    awn = AWNING[(c.outfit + 2 * (c.accent >> 1)) % 6]
    ar = mk(jig(c, awn, 0.04))
    shirt = LINEN
    trou = mk(jig(c, ["#45392c", "#3a3d46", "#4b4437", "#3b3633"][c.outfit], 0.06))
    cream = LINEN

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, shirt, 3, f, folds=0.12)

    def stripe(f, i):
        return T(c, ar, 3, f, folds=0.1) if (i % 2 == 0) else T(c, cream, 4, f, folds=0.05)

    def apron(f):
        x, y = f.x, f.y
        if f.face == "top" or f.face == "bottom":
            return None
        if f.face == "front":
            if y == 0 and x in (2, 5):
                return P(ar, 2, f)  # straps over the shoulders
            if (x, y) == (4, 1):
                return P(BRONZE, 4, f)  # a brass button where the bib is pinned
            if y in (1, 2) and 2 <= x <= 5:
                return stripe(f, x) if y == 2 else (P(ar, 4, f) if x in (2, 5) else stripe(f, x))
            if y >= 3 and 1 <= x <= 6:
                if y == 11:
                    return P(ar, 2, f)
                if y == 4 and x in (1, 6):
                    return P(ar, 2, f)
                if y == 7 and 2 <= x <= 5 and False:
                    return None
                if y == 8 and x in (2, 3, 4, 5):  # a pocket, stitched
                    return P(ar, 2, f) if (y == 8) else stripe(f, x)
                return stripe(f, x)
            return None
        if f.face == "back":
            if y == 0 and x in (2, 5):
                return P(ar, 2, f)
            if y == 6 and 1 <= x <= 6:
                return P(ar, 3, f)  # the apron strings
            if y == 6:
                return P(ar, 3, f)
            if y in (6,) and x in (0, 7):
                return P(ar, 3, f)
            return None
        if y == 6:
            return P(ar, 3, f)
        return None
    arm_f = arm(c, shirt, 3, 3, cuff=shirt, cuff_idx=4)
    armx = lambda f: (T(c, shirt, 4 if f.y == 3 else 2, f, folds=0.1) if f.face not in ("top", "bottom") and f.y in (3, 4) else None)  # noqa: E731
    arm_f = lay(armx, arm(c, shirt, 3, 2))
    legs = trousers(c, trou, 3, boot_from=9, boot=LEATHER, boot_idx=2)
    rp = lambda f: (T(c, ar, 3, f) if f.face != "bottom" and f.y <= 4 and f.face == "front" and 1 <= (f.bx() or 0) <= 6 and False else None)  # noqa: E731
    if c.accent & 1:
        hat = cap_flat(c, mk(jig(c, ["#6a5a46", "#58606a", "#3c3a36", "#5a6446"][c.outfit], 0.06)), 3, True)
    else:
        hat = kerchief(c, ar, dot=cream)
    return dict(body=body, jacket=apron, rarm=arm_f, rleg=legs, hat=hat)


# --- baker / miller ----------------------------------------------------------------------------------------------------

FLOUR = hex_rgba("#f6f3ea")
FLOUR2 = hex_rgba("#d9d6cf")


def dust(c, f, tag, p, colour=FLOUR):
    return colour if h01(c.key, tag, f.part, f.face, f.x, f.y) < p else None


@outfit("baker")
def baker(c, L):
    shirt = mk_soft(jig(c, "#e8e2d2", 0.02))
    apron = mk_soft(jig(c, "#dfe3e8", 0.02))
    trou = mk(jig(c, ["#6a5a46", "#4a4a50", "#7a6a54", "#5a4a3a"][c.outfit], 0.06))
    L.flour = True

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return dust(c, f, "fl", 0.05) or T(c, shirt, 3, f, folds=0.15)

    def ap(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            if y == 0 and x in (2, 5):
                return P(apron, 2, f)
            if (x, y) == (3, 1):
                return P(BRONZE, 4, f)  # a bell-shaped button on the bib
            if y in (1, 2) and 2 <= x <= 5:
                return P(apron, 3, f) if x not in (2, 5) else P(apron, 4, f)
            if y >= 3 and 1 <= x <= 6:
                if x in (1, 6) or y == 11:
                    return P(apron, 2, f)
                if y == 7 and x in (2, 3, 4, 5):
                    return P(apron, 2, f)  # the pocket's top edge
                return dust(c, f, "fl2", 0.10) or dust(c, f, "fl3", 0.04, FLOUR2) or T(c, apron, 4, f, folds=0.1, lo=0.03)
            return None
        if f.face == "back":
            if y == 6 and 0 <= x <= 7:
                return P(apron, 3, f)
            if y == 0 and x in (2, 5):
                return P(apron, 2, f)
            return None
        if y == 6:
            return P(apron, 3, f)
        return None

    def armx(f):
        if f.face not in ("top", "bottom") and f.y in (5, 6):
            return T(c, shirt, 4 if f.y == 5 else 2, f, folds=0.1)
        return None
    arm_f = lay(armx, arm(c, shirt, 3, 4, folds=0.05))
    arm_f = (lambda inner: (lambda f: (dust(c, f, "farm", 0.2) if f.y >= 7 and f.face not in ("top", "bottom") else None) or inner(f)))(arm_f)
    legs = trousers(c, trou, 3, boot_from=9, boot=LEATHER, boot_idx=2,
                    extra=lambda f: dust(c, f, "flt", 0.09) if f.y < 9 and f.face not in ("top", "bottom") else None)

    def apr_legs(f):
        if f.face == "front" and f.y <= 5 and 1 <= (f.bx() or 0) <= 6:
            if f.y == 5:
                return P(apron, 2, f)
            return dust(c, f, "fl4", 0.08) or T(c, apron, 4, f, folds=0.1, lo=0.03)
        return None
    if c.accent in (0, 1):  # a puffed white cap with a band
        def hat(f):
            if f.face == "bottom":
                return None
            y = f.y
            if f.face == "top":
                return T(c, apron, 5, f, folds=0.0)
            if y <= 1 or (y == 2 and f.face in ("left", "right", "back")):
                return T(c, apron, 4 if y else 5, f, folds=0.1) if y < 2 else P(apron, 2, f)
            if y == 2 and f.face == "front":
                return P(apron, 2, f) if True else None
            return None
    else:
        hat = kerchief(c, apron, dot=mk("#5a7ab0"))
    return dict(body=body, jacket=ap, rarm=arm_f, rleg=legs, rpants=apr_legs, hat=hat)


@outfit("miller")
def miller(c, L):
    shirt = mk_soft(jig(c, "#eeeadf", 0.02))
    vest = mk(jig(c, ["#6a4a2e", "#5a4430", "#74503a", "#4e3c2a"][c.outfit], 0.06))
    trou = mk(jig(c, ["#7a6a54", "#6a5a46", "#5a5448", "#80705a"][c.outfit], 0.05))
    L.flour = True

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return dust(c, f, "fl", 0.10) or T(c, shirt, 3, f, folds=0.15)

    def wc(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            if y <= 1 and x in (1, 2, 5, 6) or 2 <= y <= 10 and 1 <= x <= 6:
                if x in (3, 4) and y >= 2:
                    return P(vest, 1, f) if x == 3 else (P(BRONZE, 4, f) if y % 2 == 0 else None)
                if h01(c.key, "wf", x, y) < 0.16:
                    return FLOUR
                return T(c, vest, 3, f, folds=0.2)
            return None
        if f.face == "back" and 1 <= x <= 6 and y <= 9:
            return dust(c, f, "wfb", 0.14) or T(c, vest, 3, f, folds=0.2)
        return None
    arm_f = arm(c, shirt, 3, 7, cuff=shirt, cuff_idx=4, folds=0.05)
    arm_f = (lambda inner: (lambda f: (dust(c, f, "farm", 0.16) if f.y >= 3 and f.face not in ("top", "bottom") else None) or inner(f)))(arm_f)
    legs = trousers(c, trou, 3, boot_from=9, boot=LEATHER, boot_idx=2,
                    extra=lambda f: dust(c, f, "flt", 0.14) if f.y < 9 and f.face not in ("top", "bottom") else None)
    if c.accent in (0, 1, 2):
        def hat(f):
            if f.face == "bottom":
                return None
            y = f.y
            if f.face == "top":
                return T(c, shirt, 5, f, folds=0.0)
            if y <= 1 or (y == 2 and f.face != "front") or (y == 2 and f.face == "front" and f.x in (0, 7)):
                return dust(c, f, "hf", 0.1, FLOUR2) or T(c, shirt, 4, f, folds=0.1)
            return None
    else:
        hat = kerchief(c, shirt, dot=None)
    return dict(body=body, jacket=wc, rarm=arm_f, rleg=legs, hat=hat)


# --- smith -----------------------------------------------------------------------------------------------------------

@outfit("smith")
def smith(c, L):
    shirt = mk(jig(c, ["#4a5568", "#7a3a28", "#6a5a40", "#3a4a38"][c.outfit], 0.06))
    apr = mk(jig(c, ["#4a2f1a", "#3a2a1e", "#5a3a22", "#2e2620"][(c.outfit + c.accent) % 4], 0.07))
    trou = mk(jig(c, ["#3a3a40", "#4a3d33", "#2f3138", "#463e34"][c.outfit], 0.05))
    glove = mk(jig(c, "#2e2216", 0.1))

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, shirt, 3, f, folds=0.2)

    def ap(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            if y == 0 and x in (2, 5):
                return P(apr, 2, f)
            if y in (1, 2) and 2 <= x <= 5:
                if x == 3 and y == 1:
                    return P(BRONZE, 4, f)  # a bell-stamped rivet on the bib
                return P(apr, 4, f) if x in (2, 5) else T(c, apr, 3, f, folds=0.1)
            if y >= 3 and 1 <= x <= 6:
                if h01(c.key, "scorch", x, y) < 0.10:
                    return cl(SOOT, 1)
                if x == 1 or y == 3:
                    return P(apr, 4, f)
                if x == 6:
                    return P(apr, 2, f)
                if y == 11:
                    return P(apr, 1, f)
                if y == 8 and x in (2, 3, 4, 5):
                    return P(apr, 2, f)  # pocket
                return T(c, apr, 3, f, folds=0.1)
            return None
        if f.face == "back":
            if y == 0 and x in (2, 5):
                return P(apr, 2, f)
            if y in (6, 7) and 0 <= x <= 7:
                return P(apr, 3 if y == 6 else 2, f)
            return None
        if y in (6, 7):
            return P(apr, 3 if y == 6 else 2, f)
        return None

    def armx(f):
        if f.face not in ("top", "bottom") and f.y in (3, 4):
            return T(c, shirt, 4 if f.y == 3 else 2, f, folds=0.1)
        return None
    arm_f = lay(armx, arm(c, shirt, 3, 2, glove=glove, glove_from=9, soot=0.22, bare_hi=3))
    legs = trousers(c, trou, 3, boot_from=8, boot=DARKLEATHER, boot_idx=3, cuff=True,
                    extra=lambda f: (cl(SOOT, 3) if f.y < 8 and f.face != "top" and h01(c.key, "lsoot", f.part, f.face, f.x, f.y) < 0.05 else None))
    if c.accent == 1:
        hat = headband(c, mk(jig(c, ["#a8442c", "#c8983a", "#d8d0b8", "#3a5a8a"][c.outfit], 0.05)), 3)
    elif c.accent == 2:
        def hat(f):
            if f.face == "front" and f.y in (1, 2):
                if f.y == 1 and f.x in (1, 2, 5, 6):
                    return P(BRASS, 4 if f.x in (1, 5) else 3, f)
                if f.y == 2 and f.x in (1, 2, 5, 6):
                    return P(MAIL, 4, f) if (f.x + f.y) % 2 else P(MAIL, 5, f)  # lenses pushed up
                if f.y == 1 and f.x in (3, 4):
                    return P(DARKLEATHER, 3, f)
            if f.face in ("left", "right", "back") and f.y == 1:
                return P(DARKLEATHER, 3, f)  # the strap
            return None
    elif c.accent == 3:
        hat = cap_flat(c, LEATHER, 2, True)
    else:
        hat = None
    return dict(body=body, jacket=ap, rarm=arm_f, rleg=legs, hat=hat)


# --- lamplighter -----------------------------------------------------------------------------------------------------

@outfit("lamplighter")
def lamplighter(c, L):
    coat = mk(jig(c, ["#2e3646", "#2a3d43", "#3b3344", "#2c3b57"][c.outfit], 0.04))
    scarf = mk(jig(c, ["#d19a2e", "#b9792a", "#c8872e", "#d4aa44"][(c.outfit + c.accent) % 4], 0.05))
    capc = mk(jig(c, ["#1f2430", "#3a2e26", "#2f3a3a", "#262a3a"][c.accent], 0.04))
    trou = mk(jig(c, "#22252d", 0.03))
    sooty = c.v("sootarm") < 0.5  # which sleeve took the soot

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, coat, 3, f, folds=0.3)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "top":
            return T(c, coat, 4, f, folds=0.1)
        if f.face == "bottom":
            return None
        if f.face in ("left", "right"):
            return T(c, coat, 3 if y > 1 else 4, f, folds=0.3) if y != 11 else P(coat, 1, f)
        if f.face == "front":
            if y <= 1 and 2 <= x <= 5:
                if y == 1 and x in (3, 4):
                    return T(c, scarf, 4, f, folds=0.0)  # the knot of the scarf
                return T(c, scarf, 3 if y == 0 else 2, f, folds=0.0)
            if y in (2, 3, 4) and x == 2:
                return P(scarf, 3 - (y == 4), f) if (y != 4 or h01(c.key, "tl", y) < 0.5) else None  # a scarf end
            if y <= 3 and x in (1, 6) or (y <= 2 and x in (2, 5) and False):
                return P(coat, 5, f) if y != 3 else P(coat, 4, f)  # a lapel
            if y == 11:
                return P(coat, 1, f)
            if x in (2, 5) and 1 <= y <= 10 and y % 2 == 0:
                return P(BRASS, 4, f)  # two rows of brass buttons
            if x in (2, 5) and 1 <= y <= 10:
                return P(coat, 2, f)
            if x in (3, 4) and y >= 3:
                return P(coat, 2, f) if x == 3 else P(coat, 4, f)  # the overlap
            if y == 8 and x in (0, 1, 6, 7):
                return P(coat, 1, f)  # a belted waist
            return T(c, coat, 3, f, folds=0.3)
        # back: a long vent
        if f.face == "back":
            if y <= 1 and 2 <= x <= 5:
                return T(c, scarf, 3, f, folds=0.0)
            if x in (3, 4) and y >= 7:
                return P(coat, 1 if x == 3 else 2, f)
            if y == 11:
                return P(coat, 1, f)
            return T(c, coat, 3, f, folds=0.3)
        return None

    def armf(f):
        sooty_here = (f.side == "r") == sooty
        base = arm(c, coat, 3, 8, cuff=coat, cuff_idx=4, soot=None)(f)
        if sooty_here and f.y in (3, 4, 5, 6, 7, 8) and f.face not in ("top", "bottom"):
            if h01(c.key, "soot", f.part, f.face, f.x, f.y) < 0.35:
                return cl(SOOT, 2 if f.y < 7 else 3)
        return base
    legs = trousers(c, trou, 3, boot_from=9, boot=LEATHER, boot_idx=2)
    tails = skirt(c, coat, 7, hem=coat, hem_idx=1)

    def tails_f(f):
        if f.face == "back" and f.y <= 6 and f.u in (2, 3) and False:
            return None
        return tails(f)
    hat = cap_flat(c, capc, 3, True)

    def hat2(f):
        px = hat(f)
        if f.face == "front" and (f.x, f.y) == (3, 1) and px is not None:
            return P(BRASS, 4, f)
        return px
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=tails_f, hat=hat2)


# --- bellringer ------------------------------------------------------------------------------------------------------

@outfit("bellringer")
def bellringer(c, L):
    robe = mk(jig(c, ["#6e6252", "#6a5f58", "#7a6c56", "#60584c"][c.outfit], 0.05))
    rope = mk(["#d8c9a0", "#a8895a", "#d8c9a0", "#c4b48a"][c.accent])
    hood_up = c.accent == 3

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, robe, 3, f, folds=0.45)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "bottom":
            return None
        if f.face == "top":
            return T(c, robe, 4, f, folds=0.1)
        if y == 7:  # the rope belt
            if f.face == "front" and x in (5, 6):
                return None
            return P(rope, 4 if (x + (f.fd or 0)) % 2 else 3, f)
        if f.face == "front":
            if y in (8, 9, 10) and x == 5:
                return P(rope, 3 if y < 10 else 4, f)  # the hanging rope end
            if y in (8, 9) and x == 6 and False:
                return None
            if not hood_up and y <= 1:
                return T(c, robe, 4 if y == 0 else 2, f, folds=0.0) if not (x in (3, 4) and y == 0) else None  # the cowl
            bell = spr(c, 2, 2, BELL4, BELLG)(f)
            if bell is not None:
                return bell
            if y == 11:
                return P(robe, 1, f)
        if f.face == "back":
            if not hood_up and y <= 4:
                inner = 1 <= x <= 6 and 2 <= y <= 4 and not (y == 4 and x in (1, 6))
                return P(robe, 4 if y < 2 else 2 if inner else 3, f) if (inner or y <= 1 or x in (0, 7)) else T(c, robe, 3, f, folds=0.3)
            if y == 11:
                return P(robe, 1, f)
        if f.face in ("left", "right") and not hood_up and y <= 1:
            return P(robe, 3, f)
        return T(c, robe, 3, f, folds=0.45)

    def armf(f):
        return arm(c, robe, 3, 8, cuff=robe, cuff_idx=1)(f)

    def sl(f):  # wide sleeves
        if f.face in ("top", "bottom") or f.inner:
            return None
        if f.y <= 8 and f.y >= 3:
            return T(c, robe, 3 if f.y < 8 else 1, f, folds=0.4)
        return None
    legs = trousers(c, robe, 3, boot_from=10, boot=DARKLEATHER, boot_idx=3)
    skirt_f = skirt(c, robe, 9, hem=robe, hem_idx=1)

    def sk2(f):
        if f.face == "front" and f.y <= 9 and f.u in (1, 2) and False:
            return None
        return skirt_f(f)
    hat = None
    if hood_up:
        hat = hood_up_fn(c, robe)
    return dict(body=body, jacket=jk, rarm=armf, rsleeve=sl, rleg=legs, rpants=sk2, hat=hat)


def hood_up_fn(c, r):
    def fn(f):
        if f.face == "bottom":
            return None
        y, x = f.y, f.x
        if f.face == "front":
            if y <= 1:
                return T(c, r, 4 if y == 0 else 3, f, folds=0.0)
            if y == 2 and 1 <= x <= 6:
                return P(r, 1, f)  # the shadow of the cowl on the brow
            if y <= 6 and x in (0, 7):
                return T(c, r, 2, f, folds=0.0)
            return None
        return T(c, r, 3 if f.face != "back" else 2, f, folds=0.3)
    return fn


# --- crier -----------------------------------------------------------------------------------------------------------

@outfit("crier")
def crier(c, L):
    scar = mk(jig(c, "#b3262a", 0.03))
    gold = mk("#d8b04a")
    black = mk("#1a1a1f")
    cream = mk("#e4dcc4")

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, scar, 3, f, folds=0.25)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "bottom":
            return None
        if f.face == "top":
            return T(c, scar, 4, f, folds=0.1)
        if f.face in ("left", "right"):
            if y == 8:
                return P(black, 3, f)
            if y == 11:
                return P(gold, 3, f)
            return T(c, scar, 3, f, folds=0.25)
        if y == 8:  # the belt
            if f.face == "front" and x in (3, 4):
                return P(gold, 5 if x == 3 else 4, f)
            return P(black, 3, f)
        if f.face == "front":
            if y == 0 and 2 <= x <= 5:
                return P(cream, 5, f)  # white collar
            if y == 1 and 2 <= x <= 5:
                return P(cream, 4 if (x + y) % 2 else 3, f)
            if y == 2 and x in (3, 4):
                return P(cream, 3, f)  # the cravat
            if x == 3 and y >= 3:
                return P(gold, 3, f)  # trim down the front
            if x == 4 and y in (4, 6, 10):
                return P(gold, 5, f)  # buttons
            if y == 11:
                return P(gold, 3, f)
            if x in (6, 7) and y in (9, 10, 11) and False:
                return None
            hb = spr(c, 6, 9, ["Bb", "bb"], {"B": (BRASS, 5, "f"), "b": (BRASS, 3, "f")})(f)  # the handbell at the hip
            if hb is not None:
                return hb
            if y == 9 and x in (6, 7):
                return None
            if x == 0 or x == 7:
                return P(scar, 2, f)
            return T(c, scar, 3, f, folds=0.25)
        if f.face == "back":
            if y == 0 and 2 <= x <= 5:
                return P(cream, 4, f)
            if y == 11:
                return P(gold, 3, f)
            if x in (3, 4) and y >= 6:
                return P(gold, 2 if x == 3 else 3, f)  # the vent's trim
            return T(c, scar, 3, f, folds=0.25)
        return None

    def armf(f):
        return arm(c, scar, 3, 8, cuff=gold, cuff_idx=3)(f)

    def sl(f):
        if f.face in ("top", "bottom") or f.inner:
            return None
        if f.y == 9:
            return P(cream, 4, f)  # a lace cuff
        if f.y == 8:
            return P(gold, 3, f)
        return None
    legs = trousers(c, cream, 3, boot_from=6, boot=DARKLEATHER, boot_idx=3,
                    extra=lambda f: P(gold, 3, f) if f.y == 6 and f.face in ("front", "back", "left", "right") and False else None)
    tails = skirt(c, scar, 4, hem=gold, hem_idx=3)

    plume = {("left", 2, 1), ("left", 3, 0), ("left", 4, 0), ("left", 5, 0), ("left", 6, 1), ("left", 7, 2), ("left", 7, 3),
             ("left", 6, 2), ("left", 5, 1), ("left", 4, 1)}

    def hat(f):
        x, y = f.x, f.y
        if f.face == "bottom":
            return None
        if f.face == "top":
            if x in (6, 7) and y >= 1:  # the feather lies along the crown
                return P(cream if (x == 7 or y % 2) else scar, 5 if x == 7 else 3, f)
            return T(c, black, 3, f, folds=0.0)
        if (f.face, f.fd if f.face in ("left", "right") else x, y) in plume:
            return P(cream if y == 0 or f.fd >= 6 else scar, 5 if y == 0 else 4, f)  # the plume sweeps back over the left ear
        if f.face == "front" and x == 7 and y <= 1:
            return P(cream, 5, f) if y == 0 else P(scar, 4, f)  # its tip rises at the brim
        if y <= 2:
            if y == 2:
                return P(gold, 3, f) if f.face != "front" or x not in (3, 4) else P(gold, 5, f)  # hatband, a buckle
            if f.face == "front" and y == 0:
                return T(c, black, 4, f, folds=0.0, lo=0.15)
            return T(c, black, 3, f, folds=0.0, lo=0.1)
        if y == 3 and f.face != "front":
            return P(black, 2, f)  # the broad brim over the ears and nape
        if y == 3 and f.face == "front" and x in (0, 7):
            return P(black, 2, f)
        if y == 4 and f.face in ("left", "right") and f.fd >= 3:
            return P(black, 1, f)
        return None
    return dict(body=body, jacket=jk, rarm=armf, rsleeve=sl, rleg=legs, rpants=tails, hat=hat)


# --- boatman -----------------------------------------------------------------------------------------------------------

@outfit("boatman")
def boatman(c, L):
    stripe = mk(jig(c, ["#27406e", "#2f6a72", "#a8332e", "#2a2c34"][c.outfit], 0.04))
    cream = mk_soft("#e8e2d0")
    oil = mk(jig(c, "#3b4030", 0.05))
    wool = mk(jig(c, ["#c9a24a", "#27406e", "#a8332e", "#8a8a84"][(c.outfit + c.accent) % 4], 0.06))
    burn = (190, 80, 70)

    def jersey(f, k=0):
        i = (f.y + k) % 2
        return T(c, stripe, 3, f, folds=0.12) if i == 0 else T(c, cream, 4, f, folds=0.08, lo=0.02)

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (2, 3, 4, 5):
            return c.S(2)  # a wide boat neck
        return jersey(f)

    def jk(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front" and y == 8 and x == 3:
            return P(BRONZE, 4, f)  # a bronze ring where the rope is made fast
        if y == 8 and f.face != "front" or (f.face == "front" and y == 8 and x not in (3, 4)):
            return P(LINEN, 2, f)  # a rope round the waist
        if y == 9 and f.face == "front" and x in (5, 6):
            return P(LINEN, 3, f)
        if c.accent in (1, 2) and f.face == "front":  # a coil of rope over one shoulder
            d = x - (y - 0)
            if 0 <= y <= 8 and x == min(7, 1 + y * 7 // 9 + 0) and x <= 7 and y > 0:
                return T(c, LINEN, 4 if y % 2 else 3, f, folds=0.0)
        return None
    arm_f = arm(c, stripe, 3, 8, extra=lambda f: (jersey(f) if f.face not in ("top", "bottom") and f.y <= 8 else None) if f.y <= 8 else None)

    def armx(f):
        if f.face in ("top",):
            return T(c, stripe, 3, f)
        if f.face not in ("bottom",) and f.y <= 8:
            return jersey(f)
        if f.y in (10, 11) and f.face not in ("top", "bottom") and h01(c.key, "rb", f.face, f.y) < 0.8:
            if f.y == 10 or h01(c.key, "rb2", f.part, f.x) < 0.5:
                return _mixpx(c.S(3), burn, 0.5)  # rope-burned palms
        return None
    arm_f = lay(armx, arm(c, stripe, 3, 8, cuff=cream, cuff_idx=4))
    legs = trousers(c, oil, 3, boot_from=9, boot=DARKLEATHER, boot_idx=3,
                    extra=lambda f: P(oil, 4, f) if f.face == "front" and f.u == 1 and f.y in (2, 3, 4, 5) and False else (P(oil, 2, f) if f.y == 8 and f.face not in ("top", "bottom") else None))
    hat = cap_knit(c, wool, mk(jig(c, "#e8e0c8", 0.05)) if c.accent != 3 else wool, 3, 3)
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, hat=hat)


# --- washer ------------------------------------------------------------------------------------------------------------

@outfit("washer")
def washer(c, L):
    smock = mk(jig(c, ["#cfd3d8", "#cbc4b0", "#b8c8c0", "#d8c0c4"][c.outfit], 0.04))
    damp = mk(jig(c, "#a4aab2", 0.04))
    scarfc = mk(jig(c, ["#5a7aa8", "#a8442c", "#c8983a", "#e8e2d0"][c.accent], 0.05))
    red = (200, 96, 86)

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, smock, 3, f, folds=0.3)

    def ap(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            if y == 0 and x in (2, 5):
                return P(damp, 2, f)
            if (x, y) == (3, 1):
                return P(BRONZE, 4, f)  # a bronze button at the bib
            if y in (1, 2) and 2 <= x <= 5:
                return T(c, damp, 3, f, folds=0.1)
            if y >= 3 and 1 <= x <= 6:
                i = 3 if y < 7 else 2
                if x in (1, 6):
                    i -= 1
                if y >= 7 and h01(c.key, "drip", x, y) < 0.2:
                    i -= 1
                return T(c, damp, i, f, folds=0.1)
            return None
        if f.face == "back" and y == 6:
            return P(damp, 3, f)
        if f.face in ("left", "right") and y == 6:
            return P(damp, 3, f)
        return None

    def armf(f):
        px = arm(c, smock, 3, 4, cuff=smock, cuff_idx=4)(f)
        if f.y >= 6 and f.face not in ("top",):
            if f.face == "bottom":
                return _mixpx(c.S(3), red, 0.4)
            return _mixpx(cl(c.sk, shade(f, 3)), red, 0.42 if f.y >= 9 else 0.28)  # red forearms
        return px
    armx = lambda f: (T(c, smock, 4 if f.y == 5 else 2, f, folds=0.1) if f.face not in ("top", "bottom") and f.y == 5 else None)  # noqa: E731
    arm_f = lay(armx, armf)
    legs = trousers(c, smock, 3, boot_from=10, boot=DARKLEATHER, boot_idx=3)
    skirt_f = skirt(c, smock, 9, hem=damp, hem_idx=2)

    def sk(f):
        px = skirt_f(f)
        if px is not None and f.y >= 6 and f.face not in ("top", "bottom") and h01(c.key, "wet", f.part, f.face, f.x, f.y) < 0.25:
            return P(smock, 2, f)  # the hem is wet
        return px
    hat = kerchief(c, scarfc, dot=mk("#e8e2d0") if c.accent != 3 else mk("#5a7aa8"))
    return dict(body=body, jacket=ap, rarm=arm_f, rleg=legs, rpants=sk, hat=hat)


# --- mason -------------------------------------------------------------------------------------------------------------

@outfit("mason")
def mason(c, L):
    smock = mk(jig(c, ["#7a7a78", "#8a8678", "#6e7078", "#807a70"][c.outfit], 0.05))
    trou = mk(jig(c, ["#55504a", "#4a4a50", "#5a5448", "#44403a"][(c.outfit + 1) % 4], 0.05))
    pale = hex_rgba("#c4c2bc")

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return dust(c, f, "sd", 0.03, pale) or T(c, smock, 3, f, folds=0.3)

    def jk(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if y in (8, 9) and (f.face != "front" or True):
            if y == 9:
                if f.face == "front" and x in (6, 7):
                    return None
                return P(LEATHER, 2, f)
            if f.face == "front" and x in (3, 4):
                return P(BRONZE, 5 if x == 3 else 4, f)
            return P(LEATHER, 4, f)
        if f.face == "front":
            # a trowel and a hammer in the belt
            tw = spr(c, 6, 9, ["w", "s", "s", "t"], {"w": (LEATHER, 5, "f"), "s": (MAIL, 5, "f"), "t": (MAIL, 4, "f")})(f)
            if tw is not None:
                return tw
            hm = spr(c, 1, 9, ["m", "w", "w"], {"m": (IRON, 4, "f"), "w": (LEATHER, 5, "f")})(f)
            if hm is not None:
                return hm
            if y == 11 and False:
                return None
        return None
    sleeve_end = 5
    arm_f = arm(c, smock, 3, sleeve_end, cuff=smock, cuff_idx=4)
    arm_f = (lambda inner: (lambda f: (dust(c, f, "sda", 0.07, pale) if f.face not in ("top", "bottom") else None) or inner(f)))(arm_f)
    legs = trousers(c, trou, 3, boot_from=8, boot=LEATHER, boot_idx=2,
                    extra=lambda f: dust(c, f, "sdl", 0.03, pale) if f.y < 8 and f.face not in ("top", "bottom") else None)

    def knees(f):  # leather knee pads
        if f.face in ("front", "left", "right") and 5 <= f.y <= 7 and not (f.face in ("left", "right") and f.fd > 1):
            if f.face == "front" and not (1 <= f.u <= 3):
                return None
            return T(c, LEATHER, 4 if f.y == 5 else 3 if f.y == 6 else 2, f, folds=0.0)
        return None
    hat = cap_flat(c, mk(jig(c, "#6a6860", 0.06)), 3, True) if c.accent == 3 else None
    hat0 = hat

    def hatdust(f):
        r = hat0(f) if hat0 else None
        if r is not None:
            return dust(c, f, "hd", 0.1, pale) or r
        # stone dust in the hair
        if f.face in ("top", "back", "left", "right", "front") and f.y <= 3 and f.face != "bottom":
            return dust(c, f, "hdust", 0.14 if f.y < 2 else 0.07, pale)
        return None
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, rpants=knees, hat=hatdust)


# --- runner ------------------------------------------------------------------------------------------------------------

@outfit("runner")
def runner(c, L):
    tun = mk(jig(c, ["#3f7a46", "#4a8a3a", "#2f6a4a", "#5a8a3a"][c.outfit], 0.05))
    sash = mk("#e07a2a")
    trou = mk(jig(c, ["#6a5a42", "#46464e", "#58503c", "#4a4036"][(c.outfit + 2) % 4], 0.05))
    boot = mk(jig(c, "#b09060", 0.06))

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, tun, 3, f, folds=0.12)

    def jk(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            sx = round(1 + y * 5 / 10)
            if y <= 10 and x in (sx, sx + 1):
                if y == 10 and x == sx + 1:
                    return P(BRONZE, 4, f)  # the sash knotted round a bell
                return T(c, sash, 4 if x == sx else 3, f, folds=0.1)
            if y == 8 and False:
                return None
        if f.face == "back":
            sx = round(6 - y * 5 / 10)
            if y <= 10 and x in (sx, sx - 1):
                return T(c, sash, 3 if x == sx else 2, f, folds=0.1)
        if y == 8 and f.face in ("left", "right"):
            return P(LEATHER, 3, f)
        return None
    glove = mk(jig(c, "#4a3a2a", 0.06))

    def armf(f):
        y = f.y
        if f.face == "bottom":
            return c.S(2)
        if f.face == "top":
            return T(c, tun, 3, f)
        if y <= 7:
            return T(c, tun, 3, f, folds=0.12)
        if y == 8:
            return P(tun, 2, f)
        if y in (9, 10):
            return T(c, glove, 3 if y == 9 else 2, f, folds=0.0)  # fingerless gloves: the palm
        return cl(c.sk, shade(f, 3))  # fingertips bare
    legs = trousers(c, trou, 3, boot_from=8, boot=boot, boot_idx=3)
    hat = headband(c, sash, 3, stud=BRONZE)
    if c.accent == 3:
        hat = headband(c, mk(jig(c, "#e8e0c8", 0.04)), 3, stud=BRONZE)
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, hat=hat)


# --- tavernkeeper -------------------------------------------------------------------------------------------------------

@outfit("tavernkeeper")
def tavernkeeper(c, L):
    shirt = mk(jig(c, "#d8cfba", 0.03))
    vest = mk(jig(c, ["#4a3426", "#3a2a30", "#2f3a30", "#4a2a2a"][c.outfit], 0.05))
    apr = mk(jig(c, ["#cfc8b4", "#b8b09a", "#d8d0bc", "#a8a08a"][(c.outfit + c.accent) % 4], 0.04))
    trou = mk(jig(c, ["#3a3640", "#4a4034", "#2e3446", "#46403a"][(c.outfit + 1) % 4], 0.05))
    towel_left = True
    L.ruddy = True

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, shirt, 3, f, folds=0.15)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "top":
            return P(LINEN, 5, f) if x in (6, 7) else None  # the towel over the shoulder
        if f.face == "bottom":
            return None
        if f.face == "front":
            if x in (6, 7) and y <= 6:
                return T(c, LINEN, 5 if x == 6 else 4, f, folds=0.0) if not (x == 7 and y == 4) else P(mk("#5a7ab0"), 3, f)
            if y <= 5 and x in (1, 2, 5) or (1 <= y <= 5 and x == 5):
                if x == 5 and y <= 5 and False:
                    return None
            if x == 2 and y in (2, 4):
                return P(BRONZE, 4, f)  # brass buttons down the vest
            if y <= 5 and 1 <= x <= 6 and not (x in (3, 4)):
                if x == 5 and y >= 1 and False:
                    return None
                return T(c, vest, 3, f, folds=0.2) if (x <= 2 or x == 5) and y >= 1 or (y == 0 and x in (1, 2, 5)) else None
            if y >= 6 and 1 <= x <= 6:
                if y == 6:
                    return P(apr, 4, f)
                if y == 11:
                    return P(apr, 2, f)
                if x in (1, 6):
                    return P(apr, 2, f)
                if y == 9 and 2 <= x <= 5 and False:
                    return None
                n = h01(c.key, "stain", x, y)
                if n < 0.07:
                    return P(apr, 1, f)  # ale stains
                return T(c, apr, 3, f, folds=0.15)
        if f.face == "back":
            if 1 <= x <= 6 and 1 <= y <= 5:
                return T(c, vest, 3, f, folds=0.2)
            if x in (0, 1) and y <= 3:
                return T(c, LINEN, 5 if x == 0 else 4, f, folds=0.0)  # the towel's back end
            if y == 6:
                return P(apr, 3, f)
        return None
    arm_f = lay(lambda f: (T(c, shirt, 4 if f.y == 3 else 2, f, folds=0.1) if f.face not in ("top", "bottom") and f.y in (3, 4) else None),
                arm(c, shirt, 3, 2))
    legs = trousers(c, trou, 3, boot_from=9, boot=DARKLEATHER, boot_idx=3)

    def apr_legs(f):
        if f.face == "front" and f.y <= 3 and 1 <= (f.bx() or 0) <= 6:
            return P(apr, 2 if f.y == 3 else 3, f)
        return None
    return dict(body=body, jacket=jk, rarm=arm_f, rleg=legs, rpants=apr_legs, hat=None)


# --- clerk -------------------------------------------------------------------------------------------------------------

@outfit("clerk")
def clerk(c, L):
    coat = mk(jig(c, ["#5a6b82", "#556578", "#62707f", "#4f6078"][c.outfit], 0.03))
    trou = mk(jig(c, "#33384a", 0.04))
    L.specs = c.accent >= 2

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, LINEN, 3, f, folds=0.1)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "bottom":
            return None
        if f.face == "top":
            return T(c, coat, 4, f, folds=0.1)
        if f.face in ("left", "right"):
            return P(BRONZE, 3, f) if y == 11 else T(c, coat, 3, f, folds=0.2)
        if y == 11:
            return P(BRONZE, 3, f)
        if f.face == "front":
            if y <= 2 and x in (3, 4):
                return T(c, mk("#2a2e3a"), 3, f, folds=0.0) if y == 2 else (None if y == 0 else T(c, LINEN, 4, f, folds=0.0))
            if y <= 4 and x in (2, 5):
                return P(BRONZE, 3 if y % 2 else 4, f)  # bronze piping on the lapels
            if y <= 4 and x in (3, 4) and y > 2:
                return T(c, LINEN, 3, f, folds=0.0)
            if y <= 1 and x in (1, 6):
                return P(BRONZE, 3, f)  # a bronze-trimmed collar
            if x == 3 and y >= 5 and y <= 10:
                return P(BRONZE, 4 if y % 2 else 3, f)  # buttons
            if x == 4 and y >= 5:
                return P(coat, 2, f)
            return T(c, coat, 3, f, folds=0.2)
        if f.face == "back":
            if y <= 1 and 1 <= x <= 6:
                return P(BRONZE, 3, f)
            if x in (3, 4) and y >= 7:
                return P(coat, 1, f)
            return T(c, coat, 3, f, folds=0.2)
        return None

    def armf(f):
        y = f.y
        if f.face == "bottom":
            return c.S(2)
        if f.face == "top":
            return T(c, coat, 3, f)
        if y <= 7:
            return T(c, coat, 3, f, folds=0.2)
        if y == 8:
            return P(BRONZE, 3, f)  # bronze cuffs
        if y == 9:
            return T(c, LINEN, 4, f, folds=0.0)
        if y >= 10 and f.face not in ("top", "bottom") and h01(c.key, "ink", f.part, f.face, f.x, f.y) < 0.18 and f.side == "r":
            return mk("#242a52") and cl(mk("#242a52"), 2)
        return cl(c.sk, shade(f, 3))
    legs = trousers(c, trou, 3, boot_from=10, boot=DARKLEATHER, boot_idx=3)
    tails = skirt(c, coat, 3, hem=BRONZE, hem_idx=2, bronze_hem=True)
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=tails, hat=None)


# --- archivist -----------------------------------------------------------------------------------------------------------

@outfit("archivist")
def archivist(c, L):
    robe = mk(jig(c, "#27432f", 0.04))
    ink = cl(mk("#1c2048"), 2)

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, robe, 3, f, folds=0.35)

    def jk(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return T(c, robe, 4, f, folds=0.1) if f.face == "top" else None
        if y == 7:
            return P(BRASS, 3, f) if f.face != "front" or x not in (3, 4) else P(BRASS, 5, f)
        if f.face == "front":
            if y <= 1 and x in (1, 2, 5, 6):
                return P(robe, 4, f)  # a standing collar
            if x in (3, 4) and y >= 1 and y <= 6:
                return P(BRASS, 3, f) if x == 3 else P(robe, 2, f)  # a trimmed opening
            bell = spr(c, 5, 2, BELL3, BELLG)(f)  # the Association's bell pin
            if bell is not None:
                return bell
            if y == 11:
                return P(robe, 1, f)
            if y >= 8 and h01(c.key, "inkr", x, y) < 0.05:
                return ink
        if y == 11:
            return P(robe, 1, f)
        return T(c, robe, 3, f, folds=0.35)

    def armf(f):
        y = f.y
        if f.face == "bottom":
            return c.S(2)
        if f.face == "top":
            return T(c, robe, 3, f)
        if y <= 8:
            return T(c, robe, 3 if y < 8 else 2, f, folds=0.35)
        if y == 9:
            return P(BRASS, 2, f)
        px = cl(c.sk, shade(f, 3))
        if f.y >= 10 and h01(c.key, "inkf", f.part, f.face, f.x, f.y) < 0.28:
            return ink  # ink-stained fingers
        return px
    legs = trousers(c, robe, 3, boot_from=10, boot=DARKLEATHER, boot_idx=3)
    skirt_f = skirt(c, robe, 9, hem=BRASS, hem_idx=2)
    capc = mk("#1d2a22")

    def hat(f):
        if f.face == "bottom":
            return None
        if f.face == "top":
            return T(c, capc, 4, f, folds=0.0)
        if f.y <= 1:
            return T(c, capc, 3, f, folds=0.0)
        return None
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=skirt_f, hat=hat)


# --- healer ------------------------------------------------------------------------------------------------------------

@outfit("healer")
def healer(c, L):
    dress = mk(jig(c, "#9ebcd8", 0.03))
    white = mk_soft("#f0f0ec")

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, dress, 3, f, folds=0.2)

    def jk(f):
        x, y = f.x, f.y
        if f.face in ("top", "bottom"):
            return None
        if f.face == "front":
            if y == 0 and x in (2, 5):
                return P(white, 3, f)
            if y in (1, 2) and 2 <= x <= 5:
                pass
            if y >= 1 and 1 <= x <= 6:
                badge = spr(c, 3, 2, [".h.", "BBB", ".B.", ".d."], {"B": (BRONZE, 4, "f"), "h": (BRONZE, 5, "f"), "d": (BRONZE, 2, "f")})(f)
                if badge is not None:
                    return badge
                if y == 3 and False:
                    return None
                if y == 11 or x in (1, 6):
                    return P(white, 2, f)
                if y == 1 and x in (1, 6):
                    return None
                return T(c, white, 4, f, folds=0.1, lo=0.02)
            return None
        if f.face == "back":
            if y == 0 and x in (2, 5):
                return P(white, 3, f)
            if y == 6:
                return P(white, 3, f)
            if y == 1 and False:
                return None
        if f.face in ("left", "right") and y == 6:
            return P(white, 3, f)
        return None

    def armf(f):
        return arm(c, dress, 3, 8, cuff=white, cuff_idx=4)(f)
    legs = trousers(c, white, 3, boot_from=10, boot=LINEN, boot_idx=3)
    skirt_f = skirt(c, dress, 8, hem=white, hem_idx=3)

    def hat(f):
        if f.face == "bottom":
            return None
        y, x = f.y, f.x
        if f.face == "top":
            return T(c, white, 5, f, folds=0.0)
        if f.face == "front":
            if y == 0 and x in (3, 4):
                return P(BRONZE, 4, f)  # a bronze pin
            if y <= 1:
                return T(c, white, 4, f, folds=0.0)
            return None
        if y <= 2 or (f.face == "back" and y <= 4):
            return T(c, white, 4 if y else 5, f, folds=0.1)
        return None
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=skirt_f, hat=hat)


# --- thief -------------------------------------------------------------------------------------------------------------

@outfit("thief")
def thief(c, L):
    cloak = mk(jig(c, ["#2c2e34", "#4a1c22", "#1f3a2c", "#3f2f22"][c.outfit], 0.04))
    scarf = mk(jig(c, ["#3a3a3e", "#2a2230", "#3a3a2c", "#2c2c30"][c.accent], 0.06))
    L.mask = scarf
    leather = mk("#2e2216")

    def body(f):
        if f.face == "front" and f.y == 0 and f.x in (3, 4):
            return c.S(2)
        return T(c, leather, 3, f, folds=0.2)

    def jk(f):
        x, y = f.x, f.y
        if f.face == "bottom":
            return None
        if f.face == "top":
            return T(c, cloak, 4, f, folds=0.1)
        if y >= 10 and h01(c.key, "rag", f.face, f.x, y) < (0.2 if y == 10 else 0.5):
            return None  # a ragged hem
        if f.face == "front" and c.accent == 0 and x == 1 and 1 <= y <= 5:
            return P(scarf, 3 if y < 5 else 2, f) if (y < 5 or h01(c.key, "fr", y) < 0.5) else None  # a scarf end hanging over the cloak
        if f.face == "front" and c.accent == 1:
            sx = round(1 + y * 5 / 9)
            if y <= 9 and x == sx:
                return T(c, DARKLEATHER, 4, f, folds=0.0)  # a baldric
            if x in (6, 7) and 9 <= y <= 10:
                return T(c, DARKLEATHER, 3 if y == 9 else 2, f, folds=0.0)  # and the pouch it carries
        if f.face == "front" and c.accent == 3 and x == 6 and 8 <= y <= 10:
            return P(IRON, 5 if y == 8 else 3, f) if y > 8 else P(BRONZE, 3, f)  # a dagger hilt and sheath
        if f.face == "front" and c.accent == 2 and 5 <= y <= 6 and 5 <= x <= 6:
            return P(mk("#5a5a52"), 3 if y == 5 else 2, f)  # a patch on the cloak
        if f.face == "front":
            if x in (3, 4):
                if y == 0:
                    return P(BRONZE, 2, f)  # the clasp, tarnished
                if y <= 1:
                    return P(cloak, 2, f)
                if y == 8:
                    return P(IRON, 3, f)  # a dull buckle
                if y >= 2:
                    return None
            if y == 8 and False:
                return None
            if x in (2, 5) and y >= 1:
                return P(cloak, 2, f)  # the cloak's inner edge
            return T(c, cloak, 3, f, folds=0.4)
        return T(c, cloak, 3 if f.face != "back" else 2, f, folds=0.4)

    def sl(f):
        if f.face in ("top", "bottom") or f.inner:
            return None
        if f.y <= 4:
            return T(c, cloak, 3, f, folds=0.4)
        return None

    def armf(f):
        y = f.y
        if f.face == "top":
            return T(c, cloak, 3, f)
        if f.face == "bottom":
            return P(DARKLEATHER, 1)
        if y <= 5:
            return T(c, cloak, 3, f, folds=0.3)
        if y in (6, 7, 8):
            return T(c, leather, 4 if y == 6 else 3, f, folds=0.0)  # bracers
        return T(c, DARKLEATHER, 3, f, folds=0.0, lo=0.1)  # gloves
    trou = mk("#24231f")
    legs = trousers(c, trou, 3, boot_from=8, boot=DARKLEATHER, boot_idx=2)
    hat = hood_up_fn(c, cloak)
    return dict(body=body, jacket=jk, rarm=armf, rsleeve=sl, rleg=legs, hat=hat)


# =====================================================================================================================
# Blocks, items, particles

def ascii_art(rows, legend, w=16, h=16, c=None):
    c = c or Canvas(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch != " ":
                v = legend[ch]
                c.set(x, y, hex_rgba(v) if isinstance(v, str) else v)
    return c


def line(c, p0, p1, colours, thick=1):
    """A pixel line; colours is a list cycled along it (core, highlight...)."""
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n if n else 0
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        c.set(x, y, colours[i % len(colours)] if isinstance(colours, list) else colours)


def fill(c, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, colour)


def rgba(h):
    return hex_rgba(h)


# --- street lamp ------------------------------------------------------------------------------------------------------

VERD = reg("verd", ["#10261f", "#1b3d33", "#2a5d4b", "#3f8068", "#62a688", "#8fcaa8"])


def street_lamp_frame() -> Canvas:
    """Dark bronze, verdigris where water gathers, a bright edge. The model samples: row 0 x4-11 (hood), row 1 x5-10
    (cap), x6-8 rows 2-3 (finial), x7 rows 4-5 (spike), row 15 x5-9 (base), and the 8x8 centre for every top and bottom."""
    c = Canvas()
    rng = rng_for("lamp_frame")
    for y in range(16):
        for x in range(16):
            n = rng.random()
            streak = h01("lampstreak", x) > 0.6  # brushed vertical streaks
            i = 2 - (n < 0.14) + (n > 0.9) + (1 if streak and n > 0.7 else 0)
            c.set(x, y, cl(BRONZE, i))
    # concentric tops: hood ring, cap, finial, tip
    for y in range(4, 12):
        for x in range(4, 12):
            r = max(abs(x - 7.5), abs(y - 7.5))
            if r > 3.0:
                i = 3 if (x + y) < 12 else 1  # the hood's rim, lit from the top left
            elif r > 2.0:
                i = 3 if (x + y) < 13 else 2  # cap
            elif r > 1.0:
                i = 4 if (x + y) < 13 else 3  # finial block
            else:
                i = 5  # the tip
            c.set(x, y, cl(BRONZE, i))
    for x, y in ((7, 4), (7, 5), (6, 4), (8, 4)):
        c.set(x, y, cl(BRONZE, 5 if x == 7 else 4))
    # the thin strips the sides use
    for x in range(4, 12):
        c.set(x, 0, cl(BRONZE, 4 if x % 3 else 3))
    for x in range(5, 11):
        c.set(x, 1, cl(BRONZE, 4 if x in (5, 6) else 3))
    for y in (2, 3):
        for x in range(6, 9):
            c.set(x, y, cl(BRONZE, 4 if (x, y) in ((6, 2), (7, 2)) else 3))
    for x in range(5, 10):
        c.set(x, 15, cl(BRONZE, 2 if x % 2 else 1))
    # verdigris in the recesses: low corners and under the lips
    for (x, y) in ((4, 0), (11, 0), (5, 1), (10, 1), (6, 3), (8, 3), (7, 5), (5, 15), (9, 15), (4, 11), (11, 11), (11, 7),
                   (4, 8), (10, 10), (5, 10), (9, 9)):
        if rng.random() < 0.8:
            c.set(x, y, cl(VERD, 3 if rng.random() < 0.6 else 4))
    return c


def street_lamp_glass_lit() -> Canvas:
    c = Canvas()
    a = ramp("amber")
    # the pane x0-5, y0-7: bronze bars at its edges, glowing glass inside, a small flame on a wick
    for y in range(8):
        for x in range(6):
            edge = x in (0, 5) or y in (0, 7)
            if edge:
                c.set(x, y, cl(BRONZE, 4 if (x == 0 or y == 0) else 2))
                continue
            d = math.hypot(x - 2.5, y - 4.2)
            i = 5 if d < 1.2 else 4 if d < 2.2 else 3
            c.set(x, y, a[i])
    # flame
    for (x, y, col_) in ((3, 3, "#fff2b0"), (2, 4, "#ffd870"), (3, 4, "#fffbe0"), (2, 5, "#ffb640"), (3, 5, "#ffe590"),
                         (2, 6, "#5a3a1a"), (3, 6, "#3a2410")):
        c.set(x, y, hex_rgba(col_))
    c.set(1, 6, a[4]); c.set(4, 6, a[4])
    # the lid and floor seen through the side: warm
    for y in range(6, 10):
        for x in range(6, 10):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, a[5 if d < 1.4 else 4 if d < 2.2 else 3])
    # unused corners: the same warm glass, so nothing odd shows through a stray sample
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3] == 0:
                c.set(x, y, a[3])
    return c


def street_lamp_glass_out() -> Canvas:
    c = Canvas()
    rng = rng_for("lamp_out")
    g = ramp("slate")
    for y in range(8):
        for x in range(6):
            edge = x in (0, 5) or y in (0, 7)
            if edge:
                c.set(x, y, cl(BRONZE, 2 if (x == 0 or y == 0) else 1))
                continue
            soot = max(0.0, 1 - y / 5.0) * 0.8 + rng.random() * 0.35
            i = 2 + (soot < 0.55) + (soot < 0.3)
            c.set(x, y, g[min(4, i + 1)] if x in (1, 2) and y in (2, 3) and soot < 0.6 else g[min(4, i)])
    # a dead wick, a bead of cold wax
    for (x, y, col_) in ((3, 4, "#2a2622"), (2, 5, "#3a352f"), (3, 5, "#26231f"), (2, 6, "#4a4a46"), (3, 6, "#34322e")):
        c.set(x, y, hex_rgba(col_))
    c.set(3, 3, hex_rgba("#14110f"))
    for y in range(6, 10):
        for x in range(6, 10):
            c.set(x, y, g[1 + (x + y) % 2])
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3] == 0:
                c.set(x, y, g[1])
    return c


def street_lamp_glass_broken() -> Canvas:
    c = Canvas()
    rng = rng_for("lamp_broken")
    g = ramp("slate")
    hole = {(1, 1), (2, 1), (1, 2), (2, 2), (3, 2), (2, 3), (3, 3), (1, 3), (1, 4), (2, 4), (4, 4), (4, 5), (3, 5), (4, 6)}
    for y in range(8):
        for x in range(6):
            edge = x in (0, 5) or y in (0, 7)
            if edge:
                if (x, y) in ((1, 0), (2, 0), (3, 0)):  # the top bar is bent away
                    continue
                c.set(x, y, cl(BRONZE, 2 if (x == 0 or y == 0) else 1))
                continue
            if (x, y) in hole:
                continue
            i = 2 + (rng.random() > 0.6)
            c.set(x, y, g[i])
    # cracks across the surviving shards
    for (x, y) in ((4, 1), (3, 1), (4, 2), (4, 3), (1, 5), (2, 5), (1, 6), (2, 6), (3, 6)):
        if c.get(x, y)[3]:
            c.set(x, y, g[4] if (x + y) % 2 else g[1])
    c.set(3, 6, hex_rgba("#3a352f"))
    for y in range(6, 10):
        for x in range(6, 10):
            if (x + y) % 3 != 0:
                c.set(x, y, g[2])
    return c


# --- laundry ---------------------------------------------------------------------------------------------------------

def laundry_line() -> Canvas:
    c = Canvas()
    rope = ["#bba376", "#9a8458", "#a38c60", "#7f6c46"]
    for y in (0, 1):
        for x in range(16):
            c.set(x, y, hex_rgba(rope[(x // 2 + y) % 2 * 1 + (0 if (x % 2) else 2)]))
    return c


def _peg(c, x, y):
    c.set(x, y, hex_rgba("#7a5a34"))
    c.set(x, y + 1, hex_rgba("#5e4426"))


def _cloth(c, shape, base_r, idx=3, damp=0.0, folds=0.3, key="cl"):
    """shape: set of (x, y); lit from the top left; the lower part a little damp."""
    for (x, y) in shape:
        n = h01(key, x, y)
        fold = h01(key, "f", x) < folds and y > 2
        i = idx + (1 if (x < 6 and y < 5 and n > 0.5) else 0)
        if fold:
            i -= 1
        if y > 8 and damp:
            i -= 1 if n < damp else 0
        edge = (x + 1, y) not in shape or (x, y + 1) not in shape
        if edge:
            i -= 1
        c.set(x, y, cl(base_r, i))


def laundry_1() -> Canvas:  # a linen shirt
    c = Canvas()
    shape = set()
    for y in range(1, 12):
        for x in range(3, 13):
            shape.add((x, y))
    for y in range(1, 5):
        for x in (0, 1, 2, 13, 14, 15):
            if y <= 4 - (1 if x in (0, 15) else 0):
                shape.add((x, y))
    for (x, y) in ((6, 1), (7, 1), (8, 1), (9, 1), (7, 2), (8, 2)):
        shape.discard((x, y))  # the neck
    _cloth(c, shape, mk("#e2dcc8"), 3, 0.5, 0.35, "lsh")
    for y in (3, 5, 7):
        c.set(8 if y > 2 else 7, y, hex_rgba("#b8a888"))  # buttons down the front
    for y in range(2, 11):
        c.set(7, y, cl(mk("#e2dcc8"), 2)) if y % 2 == 0 and (7, y) in shape else None
    for (x, y) in ((3, 4), (12, 4)):
        c.set(x, y, cl(mk("#e2dcc8"), 2))  # armpit seam
    _peg(c, 4, 1); _peg(c, 11, 1)
    return c


def laundry_2() -> Canvas:  # a bedsheet with a blue stripe
    c = Canvas()
    shape = {(x, y) for y in range(1, 12) for x in range(1, 15)}
    for x in range(1, 15):
        if x % 3 == 0:
            shape.discard((x, 11))  # an uneven hem
    base = mk("#ece6d2")
    _cloth(c, shape, base, 3, 0.6, 0.45, "lsheet")
    blue = mk("#4f78a8")
    for y in (8, 9):
        for x in range(1, 15):
            if (x, y) in shape:
                c.set(x, y, cl(blue, 3 if y == 8 else 2))
    for x in range(1, 15):
        if (x, 7) in shape:
            c.set(x, 7, cl(blue, 4))
    _peg(c, 3, 1); _peg(c, 12, 1)
    return c


def laundry_3() -> Canvas:  # an apron
    c = Canvas()
    shape = set()
    for y in range(1, 4):
        for x in range(6, 10):
            shape.add((x, y))
    for y in range(4, 12):
        w = 5 if y < 9 else 6
        for x in range(8 - w, 8 + w):
            shape.add((x, y))
    base = mk("#b0594a")
    _cloth(c, shape, base, 3, 0.5, 0.3, "lap")
    for x in range(3, 13):
        if (x, 4) in shape:
            c.set(x, 4, cl(base, 4))  # the waistband
    cream = mk("#e8e0c8")
    for y in range(7, 10):  # a stitched pocket
        for x in range(6, 10):
            if x in (6, 9) or y in (7, 9):
                c.set(x, y, cl(cream, 3))
    for (x, y) in ((5, 1), (10, 1), (4, 2), (11, 2), (4, 3), (11, 3)):
        c.set(x, y, cl(base, 2))  # strings
    _peg(c, 6, 1); _peg(c, 9, 1)
    return c


def laundry_4() -> Canvas:  # a pair of stockings
    c = Canvas()
    shape = set()
    for y in range(1, 12):
        for x in (4, 5, 6):
            shape.add((x, y))
        for x in (9, 10, 11):
            shape.add((x, y))
    shape |= {(3, 11), (7, 11)}
    shape -= {(6, 11), (5, 11), (11, 11)}
    shape |= {(8, 11), (7, 10), (12, 11)}
    base = mk("#8a8a90")
    _cloth(c, shape, base, 3, 0.4, 0.4, "lst")
    red = mk("#b0453a")
    for x in (4, 5, 6, 9, 10, 11):
        for y in (2, 3):
            c.set(x, y, cl(red, 3 if y == 2 else 2))  # a coloured band at the top
    for (x, y) in ((4, 9), (5, 9), (10, 9), (11, 9)):
        c.set(x, y, cl(base, 4))  # a darned heel
    _peg(c, 5, 1); _peg(c, 10, 1)
    return c


def laundry_5() -> Canvas:  # a towel
    c = Canvas()
    shape = {(x, y) for y in range(1, 10) for x in range(3, 13)}
    for x in range(3, 13):
        if x % 2:
            shape.add((x, 10))  # a fringe
    base = mk("#dedad0")
    _cloth(c, shape, base, 3, 0.5, 0.25, "ltw")
    teal = mk("#3f8a8a")
    for y in (7, 8):
        for x in range(3, 13):
            c.set(x, y, cl(teal, 3 if y == 7 else 2))
    for x in range(3, 13):
        c.set(x, 4, cl(teal, 4)) if x % 2 == 0 else None
    _peg(c, 4, 1); _peg(c, 11, 1)
    return c


def laundry_6() -> Canvas:  # a child's dress
    c = Canvas()
    shape = set()
    for y in range(1, 4):
        for x in (5, 6, 9, 10):
            shape.add((x, y))
    for y in range(3, 5):
        for x in range(5, 11):
            shape.add((x, y))
    for y in range(5, 10):
        w = 3 + (y - 5) // 2
        for x in range(8 - w, 8 + w):
            shape.add((x, y))
    base = mk("#d99aa6")
    _cloth(c, shape, base, 3, 0.4, 0.3, "ldr")
    lace = mk("#f4eee0")
    for x in range(4, 12):
        if (x, 9) in shape:
            c.set(x, 9, cl(lace, 3 + (x % 2)))
    for x in range(5, 11):
        c.set(x, 4, cl(mk("#c8983a"), 3)) if (x, 4) in shape else None  # a sash
    _peg(c, 5, 1); _peg(c, 10, 1)
    return c


# --- the house fire ---------------------------------------------------------------------------------------------------

FIRE_FRAMES = 32


def _fnoise(x, y, t, seed):
    """Smooth noise that loops in time (t in 0..1): a sum of drifting waves."""
    r = random.Random(seed)
    v = 0.0
    for k in range(5):
        kx, ky = r.uniform(0.35, 0.9), r.uniform(0.25, 0.65)
        cyc = r.choice((1, 2, 3))
        ph = r.uniform(0, math.tau)
        v += math.sin(kx * x + ky * y * 1.4 - cyc * math.tau * t + ph) * (1 - k * 0.12)
    return v / 3.4


def city_fire_frames():
    """Deeper than vanilla's: a hot gold core low down, orange-red body, brown-black smoky tips, loose embers rising."""
    core = ["#ffd878", "#ffa832", "#ee7a1e", "#c8461a", "#8a2a14", "#4f2218", "#2c1c1c"]
    frames = []
    for f in range(FIRE_FRAMES):
        t = f / FIRE_FRAMES
        cv = Canvas()
        for x in range(16):
            sway = 1.4 * math.sin(math.tau * t + x * 0.5)
            prof = 1 - abs(x - 7.5) / 9.0
            prof += 0.22 * math.sin(x * 1.3 + math.tau * t * 2)
            for y in range(16):
                yb = 15 - y  # height above the floor
                n = _fnoise(x + sway, y, t, 17)
                v = (1 - yb / (19.5 * max(0.3, prof) + 1.5)) + n * 0.42 - 0.05
                if v <= 0.02:
                    continue
                # v: 1 = hottest; the tips fade to smoke
                if v > 0.9:
                    i = 0
                elif v > 0.7:
                    i = 1
                elif v > 0.5:
                    i = 2
                elif v > 0.33:
                    i = 3
                elif v > 0.2:
                    i = 4
                elif v > 0.1:
                    i = 5
                else:
                    i = 6
                if i >= 5 and h01("fire", x, y, f) < 0.45:
                    continue  # the smoky tips break up
                cv.set(x, y, hex_rgba(core[i]))
        # embers lifting off
        r = random.Random(33)
        for k in range(7):
            ex = r.randrange(2, 14)
            e0 = r.uniform(0, 1)
            spd = r.choice((1, 2))
            u = (t * spd + e0) % 1.0
            ey = int(14 - u * 15)
            ex2 = ex + round(math.sin((u + e0) * math.tau) * 1.2)
            if 0 <= ey < 12 and cv.get(ex2, ey)[3] == 0:
                cv.set(ex2, ey, hex_rgba("#ffd25a" if u < 0.6 else "#ff8a30"))
                if u < 0.3:
                    cv.set(ex2, ey + 1, hex_rgba("#f0701f"))
        frames.append(cv)
    return frames


# --- cargo crate ---------------------------------------------------------------------------------------------------------

CRATE = reg("crate", ["#22160d", "#38241a", "#523823", "#6e4e30", "#8a6641", "#a6805a"])


def cargo_crate() -> Canvas:
    c = Canvas()
    rng = rng_for("crate")
    for y in range(16):
        plank = y // 4
        for x in range(16):
            n = h01("cc", plank, x // 5 + (plank % 2) * 2)  # each plank its own tone
            base = 3 + (n > 0.6) - (n < 0.3)
            g = h01("cg", y, x // 3) < 0.3  # grain
            i = base - (1 if g else 0)
            if y % 4 == 3:
                i = 1 if x % 7 else 2  # the gaps between planks
            elif y % 4 == 0:
                i += 1
            c.set(x, y, cl(CRATE, i))
    # nails
    for y in (1, 5, 9, 13):
        for x in (1, 14):
            c.set(x, y, cl(IRON, 5))
    # two iron bands round the crate, riveted, and a stencilled bell between them
    for x in range(16):
        for y in (2, 3, 12, 13):
            c.set(x, y, cl(IRON, 3 if y in (2, 12) else 2))
        c.set(x, 2, cl(IRON, 4))
        c.set(x, 12, cl(IRON, 4))
    for x in (2, 6, 10, 13):
        for y in (2, 12):
            c.set(x, y, cl(IRON, 5))
    paint = hex_rgba("#d9cdb0")
    pd = hex_rgba("#b8a888")
    bell = [".....##.....",
            "....####....",
            "...######...",
            "...######...",
            "..########..",
            "..########..",
            ".##########.",
            "............",
            "....#..#....",
            ]
    rows = ["...pppp...",
            "..pppppp..",
            "..pppppp..",
            "..pppppp..",
            ".pppppppp.",
            "pppppppppp",
            "....pp....",
            ]
    ox, oy = 3, 4
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "p":
                px = paint if h01("stencil", i, j) > 0.12 else pd
                if i >= 6 or j == 5:
                    px = pd  # the shaded side and the lip
                c.set(ox + i, oy + j, px)
    return c


def cargo_crate_top() -> Canvas:
    c = Canvas()
    for x in range(16):
        plank = x // 5
        for y in range(16):
            n = h01("ct", plank)
            i = 3 + (n > 0.6) - (n < 0.3)
            if h01("ctg", x, y // 4) < 0.25:
                i -= 1
            if x % 5 == 4:
                i = 1
            c.set(x, y, cl(CRATE, i))
    for i in range(16):  # a frame and a diagonal brace
        for e in (0, 15):
            c.set(i, e, cl(CRATE, 2))
            c.set(e, i, cl(CRATE, 2))
        c.set(i, i, cl(CRATE, 5 if i % 2 else 4))
        c.set(i, 15 - i, cl(CRATE, 2))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 7), (8, 8)):
        c.set(x, y, cl(IRON, 5))
    return c


# --- items -------------------------------------------------------------------------------------------------------------

def _shaft(c, p0, p1, ramp_, i0=3, i1=4):
    line(c, p0, p1, [cl(ramp_, i0), cl(ramp_, i1)])
    # a darker edge on the lower right of the shaft
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0))
    for k in range(n + 1):
        t = k / n
        x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
        if c.get(x + 1, y)[3] == 0:
            c.set(x + 1, y, cl(ramp_, i0 - 2))


ASH = reg("ash", ["#1c1a18", "#2e2a26", "#443d36", "#5c534a", "#766a5e", "#928576"])


def lamplighter_pole() -> Canvas:
    c = Canvas()
    _shaft(c, (0, 15), (10, 5), "wood", 3, 4)
    # brass ferrule and a hooked head with a little wick
    rows = ["..........bb..y.",
            "...........bh.oy",
            "...........bhOo.",
            "..........bbh...",
            ".........Hb.....",
            ]
    leg = {"b": cl(BRASS, 3), "h": cl(BRASS, 5), "H": cl(BRASS, 4), "y": hex_rgba("#ffe58a"), "o": hex_rgba("#ff9a30"),
           "O": hex_rgba("#ffd060")}
    ascii_art(rows, leg, c=c)
    # the collar where wood meets brass
    c.set(9, 6, cl(BRASS, 4)); c.set(10, 5, cl(BRASS, 5))
    return _outline(c)


def watch_halberd() -> Canvas:
    c = Canvas()
    _shaft(c, (0, 15), (10, 5), ASH, 3, 4)
    rows = ["............iii.",
            "..........iIIi..",
            ".........iIIii..",
            "........iIIIi...",
            ".......iiIii....",
            "......iiIbb.....",
            ".....iii.bB.....",
            "......ii.b......",
            ]
    leg = {"i": cl(IRON, 3), "I": cl(IRON, 5), "b": cl(BRONZE, 3), "B": cl(BRONZE, 5)}
    # build the head on its own canvas: spike along the diagonal, the axe blade swinging up and left from the collar
    hd = Canvas()
    line(hd, (10, 5), (14, 1), [cl(IRON, 4), cl(IRON, 5)])
    c.set(15, 0, cl(IRON, 5))
    for (x, y, i) in ((10, 5, 4), (11, 4, 5), (12, 3, 4), (13, 2, 5), (14, 1, 4)):
        c.set(x, y, cl(IRON, i))
    blade = [(8, 4), (7, 3), (6, 2), (5, 2), (6, 3), (7, 4), (8, 5), (9, 4), (9, 3), (8, 3), (8, 2), (7, 2), (9, 2), (10, 3), (10, 4)]
    blade = [(9, 5), (8, 4), (7, 3), (7, 2), (8, 2), (9, 3), (10, 4), (6, 3), (6, 4), (7, 4), (8, 3), (9, 4), (5, 4), (5, 3), (6, 5), (7, 5), (8, 5)]
    for (x, y) in blade:
        c.set(x, y, cl(IRON, 4 if (x + y) % 3 else 5))
    for (x, y) in ((5, 4), (6, 5), (7, 5)):
        c.set(x, y, cl(IRON, 3))
    for (x, y) in ((6, 3), (7, 2)):
        c.set(x, y, cl(IRON, 5))
    c.set(9, 6, cl(BRONZE, 4)); c.set(10, 5, cl(BRONZE, 5)); c.set(8, 7, cl(BRONZE, 3)); c.set(9, 7, cl(BRONZE, 2))
    c.set(8, 6, cl(BRONZE, 3))
    return _outline(c)


def smith_hammer() -> Canvas:
    c = Canvas()
    _shaft(c, (1, 14), (10, 5), "wood", 3, 4)
    head = [(8, 3), (9, 2), (10, 1), (11, 2), (12, 3), (13, 4), (12, 5), (11, 6),
            (9, 4), (10, 3), (11, 4), (10, 5), (12, 4), (11, 3), (10, 2), (9, 3), (10, 4), (11, 5), (8, 4), (9, 5), (12, 6), (13, 5)]
    for (x, y) in head:
        c.set(x, y, cl(IRON, 3))
    for (x, y) in ((10, 1), (9, 2), (8, 3), (10, 2), (9, 3)):
        c.set(x, y, cl(IRON, 5 if (x + y) % 2 else 4))
    for (x, y) in ((13, 5), (12, 6), (11, 6), (12, 5)):
        c.set(x, y, cl(IRON, 2))
    c.set(9, 4, cl(BRONZE, 4)); c.set(10, 5, cl(BRONZE, 3))  # a bronze wedge where the haft is set
    return _outline(c)


def mason_trowel() -> Canvas:
    c = Canvas()
    _shaft(c, (0, 15), (5, 10), "wood", 3, 4)
    c.set(6, 9, cl(BRONZE, 4)); c.set(5, 10, cl(BRONZE, 5)); c.set(7, 8, cl(BRONZE, 3))
    blade = [(8, 7), (9, 6), (10, 5), (11, 4), (12, 3), (13, 2), (14, 1),
             (8, 6), (9, 5), (10, 4), (11, 3), (12, 2), (13, 1),
             (9, 7), (10, 6), (11, 5), (12, 4), (13, 3), (14, 2), (15, 1)[:2] if False else (14, 3),
             (10, 7), (11, 6), (12, 5), (13, 4),
             (8, 8), (9, 8), (10, 8), (11, 7), (12, 6), (13, 5)]
    for (x, y) in blade:
        c.set(x, y, cl(MAIL, 4 if x + y < 17 else 3 if x + y < 19 else 2))
    for (x, y) in ((9, 5), (10, 4), (11, 3), (12, 2), (13, 1), (8, 6)):
        c.set(x, y, cl(MAIL, 5))
    return _outline(c)


def crier_scroll() -> Canvas:
    c = Canvas()
    par = mk("#e2d3a8")
    for y in range(2, 14):
        for x in range(2, 14):
            i = 3 + (1 if x < 6 and y < 6 else 0) - (1 if x > 11 or y > 11 else 0)
            c.set(x, y, cl(par, i))
    # rolled ends
    for x in range(1, 15):
        c.set(x, 1, cl(par, 4)); c.set(x, 2, cl(par, 2))
        c.set(x, 13, cl(par, 4)); c.set(x, 14, cl(par, 2))
    c.set(1, 1, cl(par, 5)); c.set(14, 14, cl(par, 1))
    ink = hex_rgba("#4a3a2a")
    for y in (4, 6, 8):
        for x in range(4, 12):
            if h01("scroll", x, y) < 0.7:
                c.set(x, y, ink)
    for x in range(4, 8):
        if h01("scroll2", x) < 0.8:
            c.set(x, 10, ink)
    seal = ramp("crimson")
    for (x, y, i) in ((10, 10, 3), (11, 10, 4), (12, 10, 3), (10, 11, 3), (11, 11, 4), (12, 11, 2), (11, 12, 2), (10, 12, 2)):
        c.set(x, y, seal[i])
    c.set(11, 11, cl(BRONZE, 5))  # a bell's dot pressed in the wax
    for (x, y) in ((8, 12), (9, 13)):
        c.set(x, y, seal[3])  # the ribbon
    return _outline(c)


def hand_lantern() -> Canvas:
    c = Canvas()
    rows = ["......iiii......",
            ".....i....i.....",
            ".....i....i.....",
            "......iiii......",
            ".....bBBBBb.....",
            "....bbbbbbbb....",
            "....ig....gi....",
            "....igyyyygi....",
            "....igyWWygi....",
            "....igyWWygi....",
            "....igyyyygi....",
            "....ig....gi....",
            "....bbbbbbbb....",
            ".....biiiib.....",
            "......f..f......",
            ]
    leg = {"i": cl(IRON, 3), "b": cl(BRONZE, 3), "B": cl(BRONZE, 5), "g": hex_rgba("#e09a30"), "y": hex_rgba("#ffd060"),
           "W": hex_rgba("#fff6c8"), "f": cl(IRON, 2)}
    return _outline(ascii_art(rows, leg, c=c))


WICKER = reg("wicker", ["#3a2a14", "#5c4220", "#80602e", "#a8843e", "#c8a458", "#e0c47c"])


def _wicker(c, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            wv = (x + (y // 2) * 2) % 4 < 2
            i = 3 if wv else 2
            if y == y0:
                i = 5
            elif y == y1:
                i = 1
            if x in (x0, x1):
                i = max(1, i - 1)
            c.set(x, y, cl(WICKER, i))


def bread_basket() -> Canvas:
    c = Canvas()
    crust = ["#7a4418", "#a85f24", "#c87a34", "#e09a4e", "#f0b86c"]
    for (cx, cy, rx, ry) in ((4.5, 5.0, 3.6, 2.8), (10.0, 4.6, 3.8, 3.0), (7.4, 6.8, 4.0, 2.6)):
        for y in range(16):
            for x in range(16):
                d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                if d <= 1.0:
                    light = (cx - x) * 0.25 + (cy - y) * 0.45
                    i = 2 + (light > 0.2) + (light > 0.9) - (d > 0.75) - (d > 0.9)
                    c.set(x, y, hex_rgba(crust[max(0, min(4, i))]))
        # two scores across the top, flour dusted in them
        for k in (-1, 1):
            c.set(round(cx + k * 1.0), round(cy - 1), hex_rgba(crust[4]))
            c.set(round(cx + k * 1.0 + 1), round(cy), hex_rgba(crust[0]))
    _wicker(c, 2, 9, 13, 14)
    for x in range(1, 15):  # the rim
        c.set(x, 8, cl(WICKER, 5 if x % 2 else 4)); c.set(x, 9, cl(WICKER, 3))
    # a linen cloth tucked at one side
    for (x, y) in ((11, 7), (12, 7), (12, 8), (13, 8), (13, 9)):
        c.set(x, y, cl(LINEN, 5 - (y - 7)))
    # the bell mark woven into the basket
    for (x, y) in ((7, 11), (8, 11), (7, 12), (8, 12), (6, 13), (7, 13), (8, 13), (9, 13)):
        c.set(x, y, cl(WICKER, 1 if y < 13 else 0))
    return _outline(c)


def laundry_basket() -> Canvas:
    c = Canvas()
    _wicker(c, 1, 8, 14, 14)
    for x in range(0, 16):
        c.set(x, 8, cl(WICKER, 5 if x % 2 else 4)) if 1 <= x <= 14 else None
        c.set(x, 9, cl(WICKER, 3)) if 1 <= x <= 14 else None
    # folded linen piled above the rim
    for y in range(3, 8):
        for x in range(3, 13):
            i = 4 if y in (3, 4) else 3
            c.set(x, y, cl(LINEN, i + (1 if x < 6 else 0)))
    for x in range(3, 13):
        c.set(x, 5, cl(LINEN, 2))  # the fold line between the two sheets
    for x in range(4, 12):
        c.set(x, 3, cl(LINEN, 5))
    for x in range(8, 13):
        c.set(x, 6, cl(mk("#4f78a8"), 3)); c.set(x, 7, cl(mk("#4f78a8"), 2))  # a blue-striped towel
    c.set(5, 4, cl(mk("#b0594a"), 3)); c.set(6, 4, cl(mk("#b0594a"), 3))
    return _outline(c)


def ledger_book() -> Canvas:
    c = Canvas()
    gr = mk("#2f5a3c")
    for y in range(1, 15):
        for x in range(2, 14):
            i = 3 + (1 if x < 6 else 0) - (1 if x > 11 or y > 12 else 0)
            if h01("ledg", x, y) < 0.08:
                i -= 1
            c.set(x, y, cl(gr, i))
    for y in range(2, 14):
        c.set(2, y, cl(gr, 2)); c.set(3, y, cl(gr, 1))  # the spine
    for y in range(2, 14):
        c.set(13, y, hex_rgba("#e9e2cf")); 
    for y in range(3, 13, 2):
        c.set(13, y, hex_rgba("#cfc6ad"))  # page edges
    for (x, y) in ((4, 1), (13, 1), (4, 14), (13, 14), (5, 1), (12, 1), (5, 14), (12, 14), (4, 2), (13, 2), (4, 13), (13, 13)):
        c.set(x, y, cl(BRASS, 4 if (x, y) in ((4, 1), (5, 1), (4, 2)) else 3))
    # a title plate with the bell mark
    for y in range(5, 8):
        for x in range(6, 11):
            c.set(x, y, cl(BRASS, 3 if y > 5 else 4))
    c.set(8, 5, cl(BRASS, 5)); c.set(8, 6, cl(BRASS, 5)); c.set(7, 6, cl(BRASS, 4)); c.set(9, 6, cl(BRASS, 4)); c.set(8, 7, cl(BRASS, 1))
    for x in range(6, 12):
        c.set(x, 10, cl(gr, 4 if x % 2 else 2))  # blind-tooled lines
    c.set(12, 8, cl(BRASS, 5)); c.set(12, 9, cl(BRASS, 3))  # a clasp
    return _outline(c)


def courier_satchel() -> Canvas:
    c = Canvas()
    org = mk("#e07a2a")
    # the bag
    for y in range(5, 14):
        for x in range(2, 14):
            i = 3 + (1 if x < 6 and y < 9 else 0) - (1 if x > 11 or y > 11 else 0)
            c.set(x, y, cl(org, i))
    # the flap
    for y in range(5, 9):
        w = 6 - (y - 5) // 2 * 0
        for x in range(2, 14):
            if y == 8 and (x < 4 or x > 11):
                continue
            c.set(x, y, cl(org, 4 if y == 5 else 3 - (y == 8) + (1 if x < 6 else 0)))
    for x in range(2, 14):
        c.set(x, 8, cl(org, 1)) if 4 <= x <= 11 else None
    c.set(7, 9, cl(BRASS, 5)); c.set(8, 9, cl(BRASS, 4)); c.set(7, 10, cl(BRASS, 3)); c.set(8, 10, cl(BRASS, 3))  # the buckle
    # a strap over the top
    for (x, y) in ((4, 4), (5, 3), (6, 2), (7, 1), (8, 1), (9, 1), (10, 2), (11, 3), (12, 4), (4, 5), (12, 5)):
        c.set(x, y, cl(LEATHER, 4 if x < 8 else 3))
    # the guild's bell stamped on the flap
    c.set(5, 6, cl(BRASS, 5)); c.set(4, 7, cl(BRASS, 4)); c.set(5, 7, cl(BRASS, 4)); c.set(6, 7, cl(BRASS, 3)); c.set(5, 5, cl(BRASS, 4)) if False else None
    for y in range(9, 13):
        c.set(2, y, cl(org, 4)); 
    return _outline(c)


def stolen_bag() -> Canvas:
    c = Canvas()
    sack = mk("#3a3640")
    for y in range(5, 15):
        for x in range(2, 14):
            d = math.hypot((x - 7.5) / 5.8, (y - 10.5) / 4.8)
            if d <= 1.0:
                i = 3 + (1 if (x + y) < 15 else 0) - (1 if d > 0.82 else 0)
                if h01("sack", x, y) < 0.07:
                    i -= 1
                c.set(x, y, cl(sack, i))
    # the neck, gathered and tied with cord
    for y in range(2, 6):
        for x in range(5, 11):
            if abs(x - 7.5) <= 2.2 - (y - 2) * 0.0 and not (y == 2 and x in (5, 10)):
                c.set(x, y, cl(sack, 3 + (x < 8) - (y == 2)))
    for x in range(5, 11):
        c.set(x, 5, hex_rgba("#b89a62") if x % 2 else hex_rgba("#8a7248"))  # cord
    c.set(10, 6, hex_rgba("#b89a62")); c.set(11, 7, hex_rgba("#8a7248")); c.set(5, 6, hex_rgba("#b89a62"))
    # a coin and a glint showing at the mouth
    c.set(7, 2, hex_rgba("#f6dc7e")); c.set(8, 2, hex_rgba("#e0b845")); c.set(7, 3, hex_rgba("#b8902e"))
    c.set(9, 1, hex_rgba("#ffffff"))
    return _outline(c)


# --- particles (white and grey; the game tints them; partial alpha is welcome) -------------------------------------------

def _soft(c, cx, cy, r, alpha=1.0, grey=(236, 236, 240), power=1.6, shade=0.0):
    """Adds a soft round blob: alpha falls off smoothly, a little darker toward the lower right."""
    for y in range(c.h):
        for x in range(c.w):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d >= 1:
                continue
            a = (1 - d) ** power * alpha
            if a <= 0.02:
                continue
            old = c.get(x, y)
            oa = old[3] / 255
            na = 1 - (1 - oa) * (1 - a)
            g = grey[0] - shade * ((x - cx) + (y - cy)) / max(r, 1) * 14
            g = max(120, min(255, g))
            col_ = (round(g), round(g), round(min(255, g + 4)))
            if oa > 0:
                w = a / (a + oa * (1 - a))
                col_ = tuple(round(old[i] + (col_[i] - old[i]) * w) for i in range(3))
            c.px[y][x] = (*col_, round(na * 255))


def chimney_smoke(i) -> Canvas:
    """Four stages of one rising puff: a small dense ball, then it swells, goes soft and ragged, and comes apart."""
    c = Canvas(16, 16)
    r = random.Random(500 + i)
    n = (1, 4, 6, 7)[i]
    spread = (0.0, 1.8, 3.0, 3.9)[i]
    rad = (3.6, 4.0, 3.9, 3.2)[i]
    for k in range(n):
        ang = r.uniform(0, math.tau)
        d = r.uniform(0.3, 1.0) * spread if k else 0
        _soft(c, 8 + math.cos(ang) * d, 8.4 + math.sin(ang) * d * 0.8, rad * r.uniform(0.85, 1.2), (0.95, 0.85, 0.72, 0.6)[i],
              shade=1.0, power=(1.3, 1.5, 1.7, 1.9)[i])
    if i >= 2:  # the puff breaks up: ragged holes eaten out of it
        for y in range(16):
            for x in range(16):
                a = c.get(x, y)[3]
                if a and h01("smk", i, x, y) < 0.2 * (i - 1):
                    c.px[y][x] = (*c.px[y][x][:3], round(a * 0.3))
    for y in range(16):
        for x in range(16):
            if c.px[y][x][3] < 14:
                c.px[y][x] = (0, 0, 0, 0)
    return c


def forge_spark(i) -> Canvas:
    c = Canvas(8, 8)
    if i == 0:  # a bright streak with a fading tail, down and to the left
        pts = [(5.5, 1.5, 1.0), (4.5, 2.5, 0.85), (3.5, 3.5, 0.6), (2.5, 4.5, 0.35), (1.5, 5.5, 0.15)]
        for (x, y, a) in pts:
            c.px[int(y)][int(x)] = (255, 255, 255, round(255 * a))
        c.px[1][5] = (255, 255, 255, 255)
        c.px[1][6] = (255, 255, 250, 110); c.px[2][5] = (255, 255, 250, 110)
    else:  # a dot with a little halo
        for y in range(8):
            for x in range(8):
                d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
                if d < 2.6:
                    a = (1 - d / 2.6) ** 1.1
                    c.px[y][x] = (255, 255, 255, round(255 * min(1.0, a * 1.35)))
    return c


def lamp_flare(i) -> Canvas:
    c = Canvas(16, 16)
    spikes = (4, 8)[i]
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            glow = max(0.0, 1 - d / (6.5 - i * 1.5)) ** 2.2
            ang = math.atan2(dy, dx)
            star = 0.0
            for k in range(spikes):
                a0 = k * math.tau / spikes + (math.pi / 4 if i else 0)
                dd = abs(math.sin(ang - a0)) * d
                along = abs(math.cos(ang - a0)) * d
                if math.cos(ang - a0) > 0.0:
                    star = max(star, max(0.0, 1 - dd / (0.9 - 0.25 * i)) * max(0.0, 1 - along / (7.6 - i * 2.6)) ** 1.4)
            a = min(1.0, glow + star * (0.95 - 0.2 * i))
            if a > 0.05:
                c.px[y][x] = (255, 255, 252, round(255 * a))
    return c


def fire_ember(i) -> Canvas:
    c = Canvas(8, 8)
    if i == 0:
        for y in range(8):
            for x in range(8):
                d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
                if d < 3.0:
                    c.px[y][x] = (255, 255, 255, round(255 * min(1.0, (1 - d / 3.0) ** 0.9 * 1.25)))
    elif i == 1:
        for (x, y, a) in ((3, 3, 1.0), (4, 3, 1.0), (3, 4, 1.0), (4, 4, 0.85), (2, 3, 0.4), (5, 4, 0.4), (3, 2, 0.4), (4, 5, 0.4)):
            c.px[y][x] = (255, 255, 255, round(255 * a))
    else:
        for (x, y, a) in ((4, 3, 1.0), (3, 4, 0.5), (5, 2, 0.35), (4, 4, 0.45)):
            c.px[y][x] = (255, 255, 255, round(255 * a))
    return c


# =====================================================================================================================
# Build, preview, checks

BLOCKS = {"street_lamp_frame": street_lamp_frame, "street_lamp_glass_lit": street_lamp_glass_lit,
          "street_lamp_glass_out": street_lamp_glass_out, "street_lamp_glass_broken": street_lamp_glass_broken,
          "laundry_line": laundry_line, "laundry_1": laundry_1, "laundry_2": laundry_2, "laundry_3": laundry_3,
          "laundry_4": laundry_4, "laundry_5": laundry_5, "laundry_6": laundry_6, "cargo_crate": cargo_crate,
          "cargo_crate_top": cargo_crate_top}
ITEMS = {"lamplighter_pole": lamplighter_pole, "watch_halberd": watch_halberd, "smith_hammer": smith_hammer,
         "mason_trowel": mason_trowel, "crier_scroll": crier_scroll, "hand_lantern": hand_lantern,
         "bread_basket": bread_basket, "laundry_basket": laundry_basket, "ledger_book": ledger_book,
         "courier_satchel": courier_satchel, "stolen_bag": stolen_bag}
PARTICLES = {**{f"chimney_smoke_{i}": (lambda i=i: chimney_smoke(i)) for i in range(4)},
             **{f"forge_spark_{i}": (lambda i=i: forge_spark(i)) for i in range(2)},
             **{f"lamp_flare_{i}": (lambda i=i: lamp_flare(i)) for i in range(2)},
             **{f"fire_ember_{i}": (lambda i=i: fire_ember(i)) for i in range(3)}}


def build(out):
    people = load_roster()
    for c in people:
        out.entity("citizen/" + c.file, compose(c))
    for name, fn in BLOCKS.items():
        out.block(name, fn())
    out.block("city_fire", city_fire_frames(), frametime=1)
    # the fire cycles; blending its frames would smear its dark tips, so it does not interpolate
    meta = os.path.join(out.root, "assets/awaken/textures/block/city_fire.png.mcmeta")
    with open(meta, "w") as m:
        json.dump({"animation": {"frametime": 1, "interpolate": False}}, m)
    for name, fn in ITEMS.items():
        out.item(name, fn())
    for name, fn in PARTICLES.items():
        out.particle(name, fn())


def mad(a: Image.Image, b: Image.Image) -> float:
    """Mean absolute RGB difference over the pixels either skin uses, both laid over mid grey."""
    import numpy as np
    A, B = np.asarray(a.convert("RGBA")).astype(float), np.asarray(b.convert("RGBA")).astype(float)
    used = (A[..., 3] > 0) | (B[..., 3] > 0)
    grey = np.full(A.shape[:2] + (3,), 128.0)
    ca = np.where(A[..., 3:4] > 0, A[..., :3], grey)
    cb = np.where(B[..., 3:4] > 0, B[..., :3], grey)
    return float(np.abs(ca - cb)[used].mean())


def variety(people, skins, verbose=True):
    import itertools
    import random as rnd
    import hashlib as hl
    rep = {}
    # no two skins identical
    seen = {}
    for c in people:
        h = hl.sha256(skins[c.file].tobytes()).hexdigest()
        assert h not in seen, f"{c.file} is identical to {seen[h]}"
        seen[h] = c.file
    rep["skins"] = len(people)
    # distinct looks among any 40 citizens at random
    cit = [c for c in people if c.role != "thief"]
    tup = lambda c: (c.jobname, c.outfit, c.hstyle, c.hcol, c.tone)  # noqa: E731
    R = rnd.Random(2468)
    counts = []
    for _ in range(4000):
        counts.append(len({tup(c) for c in R.sample(cit, 40)}))
    rep["distinct_min"], rep["distinct_mean"] = min(counts), sum(counts) / len(counts)
    rep["distinct_all"] = len({tup(c) for c in cit})
    # image level: same job, different households
    d_all = []
    worst = (1e9, None)
    by_job = {}
    for c in people:
        by_job.setdefault(c.role, []).append(c)
    per_job = {}
    for job, lst in by_job.items():
        ds = []
        for a, b in itertools.combinations(lst, 2):
            if a.household == b.household:
                continue
            d = mad(skins[a.file], skins[b.file])
            ds.append(d)
            if d < worst[0]:
                worst = (d, (a.file, b.file))
        if ds:
            per_job[job] = (min(ds), sorted(ds)[len(ds) // 2])
            d_all += ds
    rep["mad_min"], rep["mad_worst_pair"] = worst
    rep["mad_median"] = sorted(d_all)[len(d_all) // 2]
    rep["mad_per_job"] = per_job
    return rep


MAD_THRESHOLD = 6.0    # same-job, different-household skins differ by at least this (mean abs RGB over the used pixels, 0-255)
DISTINCT_THRESHOLD = 24


def town_detail(img) -> bool:
    """Bronze or brass somewhere: a button, a buckle, a bell."""
    want = {tuple(hex_rgba(h)) for r in (BRONZE, BRASS) for h in LOCAL[r][2:]}
    px = zip(*[iter(img.convert("RGBA").tobytes())] * 4)
    return any(p in want for p in px)


def check(people=None, skins=None):
    people = people or load_roster()
    skins = skins or {c.file: compose(c).image() for c in people}
    for c in people:
        assert c.file in skins
        if c.role != "thief":
            assert town_detail(skins[c.file]), f"{c.file} ({c.role}) has no bronze or brass on it"
    rep = variety(people, skins)
    named = 4
    print(f"skins: {rep['skins']} (240 citizens - {named} named + 8 thieves)")
    print(f"distinct (job, outfit, hairStyle, hairColour, tone) among 40 random citizens: min {rep['distinct_min']}, "
          f"mean {rep['distinct_mean']:.1f} of 40 (threshold {DISTINCT_THRESHOLD}); {rep['distinct_all']} distinct among all")
    print(f"image difference, same job, different households: min {rep['mad_min']:.1f} {rep['mad_worst_pair']}, "
          f"median {rep['mad_median']:.1f} (threshold {MAD_THRESHOLD})")
    for j, (mn, md) in sorted(rep["mad_per_job"].items()):
        print(f"   {j:12s} min {mn:5.1f} median {md:5.1f}")
    assert rep["distinct_min"] >= DISTINCT_THRESHOLD, "too few distinct looks among 40 citizens"
    assert rep["mad_min"] >= MAD_THRESHOLD, f"two same-job skins are too alike: {rep['mad_worst_pair']}"
    # textures: every block, item and particle builds, sizes right
    for name, fn in {**BLOCKS, **ITEMS, **PARTICLES}.items():
        cv = fn()
        assert cv.w in (8, 16) and cv.h in (8, 16), name
        assert any(cv.get(x, y)[3] for x in range(cv.w) for y in range(cv.h)), name + " is empty"
    fr = city_fire_frames()
    assert 16 <= len(fr) <= 32
    for name, fn in {**ITEMS, **BLOCKS}.items():
        cv = fn()
        if name in ITEMS or name.startswith("laundry"):
            assert all(cv.get(x, y)[3] in (0, 255) for x in range(16) for y in range(16)), f"{name}: partial alpha"
    print("all textures ok")
    disk_check(people)
    return rep


def disk_check(people):
    """Every skin, block, item and particle is on disk (run build_all.py citizens first) and is what the spec makes."""
    tex = os.path.join(ROOT, "src/main/resources/assets/awaken/textures")
    if not os.path.isdir(os.path.join(tex, "entity/citizen")):
        print("on disk: nothing built yet (python3 tools/art/build_all.py citizens)")
        return
    have = sorted(f for f in os.listdir(os.path.join(tex, "entity/citizen")) if f.endswith(".png"))
    want = sorted(c.file + ".png" for c in people)
    assert have == want, f"skins on disk differ from the roster: missing {set(want) - set(have)}, extra {set(have) - set(want)}"
    for c in people:
        im = Image.open(os.path.join(tex, "entity/citizen", c.file + ".png"))
        assert im.size == (64, 64) and im.mode == "RGBA", c.file
    for kind, table in (("block", BLOCKS), ("item", ITEMS), ("particle", PARTICLES)):
        for name in table:
            assert os.path.isfile(os.path.join(tex, kind, name + ".png")), f"{kind}/{name}.png missing"
    assert os.path.isfile(os.path.join(tex, "block/city_fire.png")) and os.path.isfile(os.path.join(tex, "block/city_fire.png.mcmeta"))
    fire = Image.open(os.path.join(tex, "block/city_fire.png"))
    assert fire.size == (16, 16 * FIRE_FRAMES), fire.size
    print(f"on disk: {len(have)} skins, {len(BLOCKS) + 1} blocks, {len(ITEMS)} items, {len(PARTICLES)} particles, all present")


def preview(d):
    os.makedirs(d, exist_ok=True)
    people = load_roster()
    skins = {c.file: compose(c).image() for c in people}
    sz = sheet(people, skins, False, os.path.join(d, "citizens_front.png"), cols=20, scale=4)
    sheet(people, skins, True, os.path.join(d, "citizens_back.png"), cols=20, scale=4)
    things_sheet(os.path.join(d, "citizens_things.png"))
    print("sheets", sz)
    return people, skins


def things_sheet(path, scale=8):
    from PIL import ImageDraw
    cells = [("block", n, fn()) for n, fn in BLOCKS.items()] + \
            [("item", n, fn()) for n, fn in ITEMS.items()] + \
            [("particle", n, fn()) for n, fn in PARTICLES.items()]
    fire = city_fire_frames()
    cells += [("fire", f"city_fire {i}", fire[i]) for i in (0, 5, 10, 16, 22, 28)]
    cw = 16 * scale + 12
    cols = 14
    rows = (len(cells) + cols - 1) // cols
    bgs = [(112, 116, 122), (24, 26, 32)]
    W = cols * cw + 8
    H = rows * (16 * scale + 28) * 2 + 16
    im = Image.new("RGB", (W, H), (60, 62, 68))
    dr = ImageDraw.Draw(im)
    f = _font(11)
    for b, bg in enumerate(bgs):
        y0 = 8 + b * rows * (16 * scale + 28)
        dr.rectangle((0, y0 - 4, W, y0 + rows * (16 * scale + 28) - 6), fill=bg)
        for k, (kind, name, cv) in enumerate(cells):
            x = 8 + (k % cols) * cw
            y = y0 + (k // cols) * (16 * scale + 28)
            t = cv.image().resize((cv.w * scale, cv.h * scale), Image.NEAREST)
            im.paste(t, (x, y), t)
            dr.text((x, y + 16 * scale + 3), name[:20], fill=(225, 225, 230), font=f)
    im.save(path)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    if cmd == "preview":
        ppl, sk = preview(sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "build/art/citizens"))
        check(ppl, sk)
    else:
        check()
