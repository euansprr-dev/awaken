"""The Living Wilds' Wayfarers: seven named people painted as 64x64 player skins, the way CARILLON's citizens are made.

    python3 tools/art/build_all.py wayfarers           write src/main/resources/assets/awaken/textures/entity/wayfarer/<id>.png
    python3 tools/art/specs/wayfarers.py preview DIR   contact sheet (front and back of each), faces, flat skins + the checks
    python3 tools/art/specs/wayfarers.py check         only the checks (skin format, distinct, town metal, deterministic)

    wren    slim   a young Runner of Carillon: green hooded cloak, long red scarf, light leather harness, rope at the hip
    hettie  slim   the shepherd: grey-white bun, weathered face, wool smock over a blue dress, sheepskin vest, crook-strap
    ilse    slim   the herbalist: dark auburn braid, round spectacles, moss-green apron of pockets and pressed flowers
    gus     wide   the hermit of the Crags: wild grey hair and beard, patched furs, a horn on a cord, bare forearms
    pell    wide   the wounded Runner: bandaged head and leg, torn courier tunic, a message tube on his back
    hale    wide   the Association's quartermaster at the High Gate: dark-blue coat, bronze buttons, bell crest, ledger strap
    marta   slim   the Merchants' Guild caravan master: dark curls under a wide-brim hat, ochre coat, purse and map case

Built on citizens.py (Ctx, Look, the seeded face and hair painters, cloth ramps, arms, legs, skirts) and hearth.py (the shared
tex/col helpers), so these seven share the town's visual language: bronze buttons and buckles, wool with fold lines, leather a
little glossy at its edges, Carillon's bell on Association badges. Everything is seeded: the same ids always give the same pixels.

Skin format: 64x64, classic layout (slim = 3 px arms, the layout shifts as in vanilla slim skins). Base parts are fully opaque,
overlay parts (hat, jacket, sleeves, pants) are transparent where unused, never partial alpha.
"""
import hashlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import *  # noqa: E402,F401,F403
import citizens as C  # noqa: E402
from citizens import (BRONZE, BRASS, LEATHER, DARKLEATHER, LINEN, IRON, F, Ctx, Look, P, T, arm, cl, h01, hex_rgba,  # noqa: E402,F401
                      jig, lay, mk, mk_soft, reg, shade, skirt, spr, trousers, _mixpx, _px_shift, hairpx, hair_covers_front,
                      PARTS_SLIM, PARTS_WIDE, ALL_PARTS, BASE_PARTS, BELL3, BELL4, BELLG)
from hearth import LOCAL  # noqa: E402

ROOT = C.ROOT
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/awaken/textures/entity/wayfarer")

HORN = reg("horn", ["#3a3226", "#6a5e48", "#988a6c", "#c4b794", "#e4dac0", "#f6f0dc"])
BLOOD = reg("blood", ["#2a0a0c", "#4a1214", "#6e1c1c", "#8e2a28", "#a83a34", "#c04a42"])
GREY_HIDE = reg("greyhide", ["#24231f", "#3a3935", "#55534c", "#706d64", "#8d897d", "#aaa595"])
COIN = reg("coin", ["#6a4a10", "#9a7020", "#c8982c", "#e8c048", "#f6dc7e", "#fff4b8"])

SKIN_PX = C.skin_px
EYE_WHITE_PX = C.EYE_WHITE


# ---------------------------------------------------------------------------------------------------------------------
# Small shared painters

def neck(f):
    return f.face == "front" and f.y == 0 and f.x in (3, 4)


def wool(c, r, idx, f, bump=0.12, folds=0.22):
    """Wool: fold lines plus bumpy lighter and darker flecks."""
    return T(c, r, idx, f, folds=folds, lo=bump, hi=bump)


def diag(f, x0, x1, ymax, y0=0):
    """The column of a one-pixel strap running from x0 (at row y0) to x1 (at row ymax), on this face."""
    if f.y < y0 or f.y > ymax:
        return None
    return round(x0 + (x1 - x0) * (f.y - y0) / max(1, ymax - y0))


def baldric(c, r, idx=3, front=(1, 6), back=(6, 1), y0=0, ymax=9, thick=1):
    """A strap across the chest and, mirrored, across the back."""
    def fn(f):
        if f.face not in ("front", "back"):
            return None
        a, b = front if f.face == "front" else back
        x = diag(f, a, b, ymax, y0)
        if x is not None and x <= f.x < x + thick:
            return T(c, r, idx + (1 if f.x == x else 0), f, folds=0.0, lo=0.06)
        return None
    return fn


def weather(c, f, p=0.14, hi=3, tint=(86, 58, 40)):
    """Bare skin on an arm or hand, with weather in it: darker flecks."""
    px = cl(c.sk, shade(f, hi) - (1 if f.face == "back" else 0))
    if h01(c.key, "wx", f.part, f.face, f.x, f.y) < p:
        return _mixpx(px, tint, 0.28)
    return px


def eyes(hexcol):
    iris = hex_rgba(hexcol)

    def fx(f, px):
        if f.face == "front" and f.y == 4 and f.x in (2, 5):
            return iris
        return px
    return fx


def ragged(c, f, tag, y_from, p0, p1):
    """True where a ragged hem has been eaten away: rare at the first row, commoner below it."""
    if f.y < y_from:
        return False
    return h01(c.key, tag, f.part, f.face, f.x, f.y) < (p0 if f.y == y_from else p1)


def furpx(c, r, idx, f, tag="fur"):
    """Shaggy fur: vertical strands two rows tall, a few dark pits."""
    n = h01(c.key, tag, f.part, f.face, f.x, f.y // 2)
    m = h01(c.key, tag + "p", f.part, f.face, f.x, f.y)
    i = idx + (1 if n > 0.74 else -1 if n < 0.24 else 0)
    if m < 0.07:
        i -= 1
    return cl(r, shade(f, i))


def patch(c, f, rect, r, idx, stitch=1, key="pt"):
    """A sewn patch on a flat face (front/back of the body): a stitched border round a coloured square."""
    x0, y0, x1, y1 = rect
    if not (x0 <= f.x <= x1 and y0 <= f.y <= y1):
        return None
    edge = f.x in (x0, x1) or f.y in (y0, y1)
    if edge:
        return cl(LINEN, shade(f, 3)) if (stitch and (f.x + f.y) % 2) else P(r, idx - 2, f)  # pale thread, dark seam
    return T(c, r, idx, f, folds=0.1)


# ---------------------------------------------------------------------------------------------------------------------
# Wren, the young Runner

def wren(c, L):
    cloak = mk("#33683f")
    scarf = mk("#c4382e")
    tunic = mk("#25448a")
    lea = mk("#d2b074")      # the harness: light, new leather
    trou = mk("#6a5a44")
    boot = mk("#b39565")
    rope = mk("#d6c28e")
    glove = mk("#7a5a38")
    L.hs = dict(L.hs, front=["########", "########", "#......#"])  # a fringe under the hood

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(tunic, 1, f)
        if f.face == "front" and f.y == 0 and f.x in (2, 5):
            return P(BRONZE, 3, f)  # piped collar
        if f.y == 11 and f.face != "top":
            return P(BRONZE, 2, f)  # hem piping
        if f.face == "front" and f.x == 3 and 1 <= f.y <= 10:
            return P(BRONZE, 2, f)  # the placket
        return T(c, tunic, 3, f, folds=0.14)

    strap_front = baldric(c, lea, 3, front=(1, 6), back=(1, 6), y0=2, ymax=9)
    strap_back_a = baldric(c, lea, 3, front=(1, 6), back=(1, 6), y0=0, ymax=8)
    strap_back_b = baldric(c, lea, 3, front=(6, 1), back=(6, 1), y0=0, ymax=8)
    tail_front = {2: (5,), 3: (5,), 4: (4, 5), 5: (4, 5), 6: (4, 5), 7: (4, 5), 8: (4, 5), 9: (4, 5), 10: (4, 5)}
    coil = ["RrR", "rDr", "RrR"]
    coil_leg = {"R": (rope, 4, "f"), "r": (rope, 3, "f"), "D": (rope, 1, "f")}

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return T(c, cloak, 4, f, folds=0.0)
        if fc == "front":
            if y <= 1 and 2 <= x <= 5:  # the scarf round the neck
                return T(c, scarf, 4 if y == 0 else 3, f, folds=0.0)
            if x in tail_front.get(y, ()):  # and its end hanging over the chest
                if y == 10:
                    return P(scarf, 1 if x == 4 else 2, f)
                return T(c, scarf, 4 if x == 5 else 3, f, folds=0.2)
            if y == 8 and 1 <= x <= 6:
                return P(BRONZE, 5, f) if x in (2, 3) else T(c, lea, 3, f, folds=0.0, lo=0.05)  # belt and its buckle
            if y == 4 and x in (3, 4) and False:
                return None
            r = strap_front(f)
            if r is not None:
                return P(BRONZE, 4, f) if (x, y) == (3, 5) else r
            r = spr(c, 1, 9, coil, coil_leg)(f)
            if r is not None:
                return r  # the rope coil hanging from the belt
            if x == 0 or x == 7 or (y <= 1 and x in (1, 6)):
                if (x, y) == (6, 1):
                    return P(BRONZE, 5, f)  # the cloak's clasp
                return T(c, cloak, 3 if x in (0, 7) else 4, f, folds=0.25)
            return None
        if fc == "back":
            if y <= 1 and 2 <= x <= 5:
                return T(c, scarf, 4 if y == 0 else 3, f, folds=0.0)
            if x in (2, 3) and 2 <= y <= 10:  # the long tail down the back
                if y == 10:
                    return P(scarf, 1 if x == 2 else 2, f)
                return T(c, scarf, 4 if x == 3 else 3, f, folds=0.25)
            if y == 8:
                return T(c, lea, 3, f, folds=0.0, lo=0.05)
            for s in (strap_back_a, strap_back_b):
                r = s(f)
                if r is not None:
                    return P(BRONZE, 4, f) if (x, y) in ((3, 4), (4, 4)) else r
            if y > 9 + (1 if x in (3, 4) else 0):
                return None  # a pointed hem
            if y == 9:
                return P(cloak, 1, f)
            return T(c, cloak, 3, f, folds=0.35)
        # sides: the cloak hangs behind the arms; the belt runs round
        if y == 8:
            return T(c, lea, 3, f, folds=0.0, lo=0.05)
        if f.fd >= 1 and y <= 9:
            return T(c, cloak, 2 if f.fd == 1 else 3, f, folds=0.3)
        if y <= 1:
            return T(c, cloak, 3, f, folds=0.0)
        return None

    def armf(f):
        y = f.y
        if f.face == "top":
            return T(c, tunic, 3, f)
        if f.face == "bottom":
            return c.S(2)
        if y <= 5:
            return T(c, tunic, 3, f, folds=0.12)
        if y in (6, 7):  # leather bracer with a bronze stud
            if f.face == "front" and f.u == 1 and y == 6:
                return P(BRONZE, 4, f)
            return T(c, glove, 4 if y == 6 else 3, f, folds=0.0, lo=0.05)
        if y == 8:
            return P(glove, 2, f)
        if y in (9, 10):
            return T(c, glove, 3 if y == 9 else 2, f, folds=0.0)  # fingerless gloves
        return cl(c.sk, shade(f, 3))

    legs = trousers(c, trou, 3, boot_from=8, boot=boot, boot_idx=3)

    def hood(f):
        if f.face == "bottom":
            return None
        x, y = f.x, f.y
        if f.face == "top":
            return T(c, cloak, 4, f, folds=0.1)
        if f.face == "front":
            if y == 0:
                return T(c, cloak, 4, f, folds=0.0)
            if y == 1:
                return T(c, cloak, 3, f, folds=0.0) if x in (0, 1, 6, 7) else None
            if y == 2 and x in (1, 6):
                return P(cloak, 1, f)  # the shadow inside the hood
            if y <= 6 and x in (0, 7):
                return T(c, cloak, 3 if y < 6 else 2, f, folds=0.0)
            return None
        if f.face == "back":
            return P(cloak, 2, f) if y == 7 else T(c, cloak, 3 if y < 6 else 2, f, folds=0.3)
        if f.fd == 0:
            return T(c, cloak, 2, f, folds=0.0) if y <= 6 else None
        if y == 7 and f.fd < 2:
            return None
        return T(c, cloak, 3 if f.fd > 1 else 2, f, folds=0.3)

    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, hat=hood)


# ---------------------------------------------------------------------------------------------------------------------
# Hettie, the shepherd

def hettie(c, L):
    smock = mk_soft("#d8cba6")   # undyed wool: warm cream, no cool shadows
    dress = mk("#4a6ea2")
    suede = mk("#a98458")
    fleece = mk_soft("#f2ead4")
    cordr = mk("#5a3a22")
    sock = mk("#8a8474")

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(smock, 1, f)
        if f.face == "front":
            if f.y == 0 and f.x in (2, 5):
                return P(dress, 4, f)  # the blue dress shows in the neckline
            if f.y == 1 and f.x in (3, 4):
                return P(dress, 3, f)
            if f.y in (9, 10) and 5 <= f.x <= 6:  # a patch pocket
                return P(smock, 2 if (f.y == 9 or f.x == 6) else 4, f)
        if f.y >= 10 and f.face != "top":
            return wool(c, smock, 2, f, 0.1, 0.3)  # a darker, felted hem
        return wool(c, smock, 3, f, 0.16, 0.3)

    def fl(f, base=4):  # the fleece: curls of light and a little shadow
        n = h01(c.key, "fl", f.part, f.face, f.x, f.y)
        i = base + (0 if (f.x + f.y) % 2 == 0 else -1)
        i += 1 if n > 0.86 else -1 if n < 0.1 else 0
        return cl(fleece, shade(f, i))

    def su(f, idx=3):
        return T(c, suede, idx, f, folds=0.08, lo=0.1)

    strap = baldric(c, DARKLEATHER, 3, front=(1, 6), back=(6, 1), y0=1, ymax=9)

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return su(f, 4) if x in (0, 1, 2, 5, 6, 7) else None
        if fc == "front":
            r = strap(f)
            if r is not None and y >= 1:
                return P(BRONZE, 5, f) if y == 4 else (P(BRONZE, 3, f) if y == 5 else r)  # the strap's buckle
            if y <= 6 and (x <= 2 or x >= 5):  # a short waistcoat, open over the smock
                if y == 0:
                    return fl(f, 5) if x in (1, 2, 5, 6) else su(f, 4)  # the shawl collar
                if x in (2, 5) or y == 6:
                    return fl(f)  # fleece edges down the opening and round the hem
                if y == 1 and x in (0, 7):
                    return fl(f, 4)
                return su(f)
            if y == 7:
                if x in (3, 4):
                    return P(BRONZE, 5 if x == 3 else 3, f)  # a bronze ring where the cord is tied
                return P(cordr, 3 + (x % 2), f)  # a cord belt over the smock
            if x == 5 and 8 <= y <= 10:
                return P(BRONZE, 4, f) if y == 10 else P(cordr, 3, f)  # its end, with a sheep-bell on it
            return None
        if fc == "back":
            r = strap(f)
            if r is not None:
                return r
            if y == 0:
                return fl(f, 5) if 1 <= x <= 6 else su(f, 4)
            if y == 7:
                return fl(f)
            if y > 7:
                return None
            if x in (3, 4) and 2 <= y <= 6:
                return P(suede, 2, f)  # the centre seam
            return su(f)
        # sides
        if y == 6 and f.fd >= 1:
            return fl(f)
        if f.fd >= 1 and 1 <= y <= 5:
            return su(f)
        if y <= 1:
            return su(f, 4)
        if y == 7:
            return P(cordr, 3 + (f.fd % 2), f)
        return None

    def armf(f):
        return arm(c, dress, 3, 8, cuff=smock, cuff_idx=3, bare_hi=3)(f)

    legs = trousers(c, sock, 3, boot_from=10, boot=LEATHER, boot_idx=2)
    skirt_f = skirt(c, dress, 9, hem=dress, hem_idx=1)

    def rp(f):
        if f.face == "front" and f.y == 7 and f.u in (1, 2) and False:
            return None
        r = skirt_f(f)
        if r is not None and f.face in ("front", "back") and f.y == 8:
            return P(dress, 2, f)  # a deeper turn of the hem
        return r

    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=rp, hat=None)


def hettie_head(c, L):
    """Sixty years of wind and sun: lines by the mouth and across the brow, a reddened nose, a few age spots."""
    S = c.S

    def post(f, px):
        x, y = f.x, f.y
        if f.face == "front":
            if (x, y) in ((2, 5), (5, 5)):
                return S(2)  # the lines from nose to mouth
            if (x, y) in ((3, 2), (4, 2)):
                return S(2)  # the furrowed brow
            if (x, y) in ((3, 5), (4, 5)):
                return _mixpx(S(2), (196, 108, 98), 0.3)  # a nose the wind has had
            if (x, y) in ((6, 3), (1, 6)) and h01(c.key, "spot", x, y) < 1:
                return _mixpx(px, (120, 84, 60), 0.3)  # age spots
        return px
    return post


# ---------------------------------------------------------------------------------------------------------------------
# Ilse, the herbalist

FLOWERS = {"p": "#dc86a6", "y": "#eac84a", "l": "#9f86d0", "w": "#f4efe4", "g": "#7fa05a"}


def ilse(c, L):
    shirt = mk_soft("#ebdfc2")
    apron = mk("#5d7b3c")
    lea = mk("#8c5d32")
    trou = mk("#5b4a3a")
    mend = mk("#7a6a8a")
    L.flip = False  # the braid hangs over the right of the chest (viewer's right), away from the strap

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(shirt, 1, f)
        if f.face == "front" and f.y == 0 and f.x in (2, 5):
            return P(shirt, 4, f)
        return T(c, shirt, 3, f, folds=0.12)

    def flower(k):
        return hex_rgba(FLOWERS[k])

    pocketsA = [(1, 3), (4, 6)]      # x0..x1 of the upper pockets, y 4..6
    pocketsB = [(1, 2), (3, 4), (5, 6)]  # lower pockets y 8..10
    sprouts = {(2, 4): "p", (5, 4): "y", (2, 8): "l", (4, 8): "w", (6, 8): "p", (3, 4): "g"}
    strap = baldric(c, lea, 3, front=(1, 6), back=(6, 1), y0=0, ymax=8)

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return T(c, apron, 4, f, folds=0.0) if x in (2, 3, 4, 5) else None
        if fc == "front":
            r = strap(f)
            if r is not None:
                return P(BRONZE, 4, f) if (x, y) == (4, 5) else r
            if y == 0:
                return P(apron, 2, f) if x in (2, 5) else None  # the bib's straps
            if y <= 3 and 2 <= x <= 5:
                if (x, y) == (3, 2):
                    return flower("p")
                if (x, y) == (4, 2):
                    return flower("y")
                if (x, y) == (4, 3):
                    return flower("g")
                return P(apron, 4, f) if x in (2, 5) else T(c, apron, 3, f, folds=0.1)
            if y >= 4 and 1 <= x <= 6:
                if y == 11:
                    return P(apron, 2, f)
                for (a, b) in pocketsA if y <= 6 else (pocketsB if 8 <= y <= 10 else []):
                    if a <= x <= b:
                        top = 4 if y <= 6 else 8
                        if y == top:
                            k = sprouts.get((x, y))
                            if k:
                                return flower(k)
                            return P(apron, 5, f)  # the pocket's stitched top edge
                        if x in (a, b) and (y - top) % 2 == 1:
                            return P(apron, 2, f)
                        return T(c, apron, 3, f, folds=0.0, lo=0.1)
                if x in (1, 6):
                    return P(apron, 4, f)
                return T(c, apron, 3, f, folds=0.12)
            # satchel at the left hip (viewer's right)
            if x == 7 and 8 <= y <= 10:
                return T(c, lea, 3 if y > 8 else 2, f, folds=0.0)
            return None
        if fc == "back":
            r = strap(f)
            if r is not None:
                return r
            if y == 0 and x in (2, 5):
                return P(apron, 2, f)
            if y == 6:
                return P(apron, 4 if 3 <= x <= 4 else 3, f)  # apron strings and the bow
            if 3 <= x <= 4 and 7 <= y <= 9:
                return P(apron, 4 if (x + y) % 2 else 3, f)
            if 1 <= x <= 6 and y >= 4:
                if y == 11:
                    return P(apron, 2, f)
                return T(c, apron, 3, f, folds=0.12)
            if x in (2, 3, 4, 5) and 1 <= y <= 3:
                return T(c, apron, 3, f, folds=0.1)
            # the satchel (player's left hip = back x 0..2)
            if x <= 2 and 8 <= y <= 11:
                if (x, y) == (1, 9):
                    return P(BRONZE, 5, f)
                return T(c, lea, 3 if y > 9 else 2, f, folds=0.0, lo=0.08)
            return None
        # sides: strings round the waist; the satchel on the left
        if y == 6 and f.fd < 4:
            return P(apron, 3, f)
        if f.face == "left" and 8 <= y <= 11 and f.fd >= 0:
            if (f.fd, y) == (1, 9):
                return P(BRONZE, 5, f)
            return T(c, lea, 3 if y > 9 else 2, f, folds=0.0, lo=0.08)
        return None

    def armf(f):
        y = f.y
        if f.face == "top":
            return T(c, shirt, 3, f)
        if f.face == "bottom":
            return c.S(2)
        if y <= 2:
            return T(c, shirt, 3, f, folds=0.12)
        if y == 3:
            return T(c, shirt, 4, f, folds=0.05)  # the roll
        if y == 4:
            return P(shirt, 2, f)
        return weather(c, f, 0.2, 3, (72, 56, 36))  # forearms with garden earth on them

    def knee(f):
        if f.face == "front" and f.u in (1, 2) and f.y in (5, 6) and f.side == "l":
            return P(mend, 3 if f.y == 5 else 2, f)
        return None

    legs = trousers(c, trou, 3, boot_from=9, boot=LEATHER, boot_idx=2, extra=knee)
    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, hat=None, jacket_first=lambda f: C.hair_jacket(L, f))


def ilse_head(c, L):
    """Pale and freckled across the nose, a warm mouth."""
    S = c.S

    def post(f, px):
        x, y = f.x, f.y
        if f.face == "front":
            if y == 6 and x in (3, 4):
                return _mixpx(S(2), (186, 86, 96), 0.5)
            if (x, y) in ((2, 5), (5, 5), (3, 5), (1, 5), (6, 6)) and h01(c.key, "frk", x, y) < 0.7:
                return _mixpx(S(3), (168, 110, 84), 0.45)  # freckles
            if (x, y) in ((1, 5), (6, 5)):
                return _mixpx(S(3), (220, 130, 130), 0.25)
        return px
    return post


def ilse_specs(c):
    """Round wire spectacles: a ring round each eye in bronze, a glint on the top left, arms running to the ears."""
    def fn(f):
        x, y = f.x, f.y
        if f.face == "front":
            if y == 3 and x in (1, 2, 5, 6):
                return cl(BRASS, 4 if x in (1, 5) else 3)
            if y == 5 and x in (1, 2, 5, 6):
                return cl(BRASS, 2)
            if y == 4 and x in (0, 3, 4, 7):
                return cl(BRASS, 3 if x in (3, 4) else 2)
        if f.face in ("left", "right") and y == 4 and f.fd <= 2:
            return cl(BRASS, 2)
        return None
    return fn


# ---------------------------------------------------------------------------------------------------------------------
# Gus, the hermit of the Crags

def gus(c, L):
    fur = mk("#5c4129")
    fur2 = mk("#86633c")
    tan = mk("#a98557")
    hide = mk("#3d2c1c")
    shirt = mk("#4b4636")
    cord = mk("#7a5a34")
    # silver-grey hair with real contrast between its strands, so it reads as wild rather than as a helmet
    c.hr = reg("gushair", ["#4a4c52", "#7a7d84", "#a4a7ad", "#c6c8cc", "#e2e3e6", "#f4f4f5"])
    L.hs = dict(front=["########", "########", "##....##", "#......#", "#......#"],
                side=[3, 4, 5, 6, 6, 6, 6, 6], back=8, vol=True)
    L.beard = None

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(shirt, 1, f)
        return T(c, shirt, 3, f, folds=0.2)

    patches_front = [((0, 3, 3, 6), GREY_HIDE, 3), ((5, 8, 7, 10), tan, 3)]
    patches_back = [((1, 2, 4, 5), fur2, 3), ((4, 6, 7, 9), GREY_HIDE, 3)]
    cord_px = baldric(c, cord, 3, front=(1, 5), back=(6, 2), y0=0, ymax=5)
    horn = ["BBB",
            "hHH",
            ".HH",
            ".hH",
            "..h",
            "..D"]
    horn_leg = {"B": (BRONZE, 4, "f"), "h": (HORN, 5, "f"), "H": (HORN, 4, "f"), "D": (HORN, 1, "f")}
    beard_tip = {0: (2, 3, 4, 5), 1: (2, 3, 4, 5), 2: (3, 4), 3: (3, 4)}

    def fur_coat(f, patches):
        x, y = f.x, f.y
        for rect, r, i in patches:
            px = patch(c, f, rect, r, i)
            if px is not None:
                return px
        if y <= 1:
            return furpx(c, fur2, 3, f)  # the shaggy mantle
        if y == 2:
            return furpx(c, fur2 if h01(c.key, "mantle", f.face, x) < 0.5 else fur, 3, f)
        return furpx(c, fur, 3, f)

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return furpx(c, fur2, 4, f)
        if ragged(c, f, "gh", 10, 0.3, 0.62) or (y == 11 and fc != "top" and h01(c.key, "gh2", f.part, fc, x) < 0.2):
            return None
        if fc == "front":
            if y in beard_tip and x in beard_tip[y]:  # the beard falls over the chest
                n = h01(c.key, "cb", x, y)
                return cl(c.hr, 3 if n > 0.7 else 2 if n > 0.2 else 1)
            r = cord_px(f)
            if r is not None and y >= 1:
                return r
            r = spr(c, 5, 5, horn, horn_leg)(f)
            if r is not None:
                return r
            if (x, y) == (1, 1):
                return P(BRONZE, 4, f)  # a bronze toggle holds the furs shut at the shoulder
            if x in (3, 4) and 3 <= y <= 9 and y % 2 == 0 and f.face == "front":
                return P(cord, 4 if x == 3 else 3, f)  # lacing across the opening
            if x == 3 and y >= 2:
                return P(fur, 1, f)
            return fur_coat(f, patches_front)
        if fc == "back":
            r = cord_px(f)
            if r is not None:
                return r
            return fur_coat(f, patches_back)
        return furpx(c, fur, 3, f)

    def sl(f):  # shaggy shoulder fur
        if f.face in ("bottom",) or f.inner:
            return None
        if f.face == "top":
            return furpx(c, fur2, 4, f)
        if f.y <= 2 or (f.y == 3 and h01(c.key, "sh", f.part, f.face, f.x) < 0.55):
            return furpx(c, fur2 if f.y < 2 else fur, 3, f)
        return None

    def armf(f):
        y = f.y
        if f.face == "top":
            return furpx(c, fur, 3, f)
        if f.face == "bottom":
            return c.S(2)
        if y <= 3:
            return T(c, shirt, 3, f, folds=0.2)
        if y == 4:
            return P(cord, 3, f)  # a cord binding the fur
        r = weather(c, f, 0.24, 3, (60, 42, 30))
        if f.face == "front" and f.u == 1 and y in (7, 8) and f.side == "r":
            return _mixpx(r, (190, 140, 120), 0.5)  # an old scar
        if y == 10 and f.face in ("front", "back"):
            return P(BRONZE, 4, f) if (f.face == "front" and f.u == 1) else P(DARKLEATHER, 3, f)  # a wrist strap, buckled
        return r

    def legx(f):
        if f.face in ("top", "bottom"):
            return None
        if f.y == 4:
            return P(cord, 4, f)  # a tie below the kilt
        if f.y in (7, 8):
            return furpx(c, fur2, 3, f, "bootfur")  # fur turned over the boot tops
        if f.face == "front" and f.u in (1, 2) and f.y in (5, 6) and f.side == "r":
            return T(c, tan, 3, f, folds=0.0)  # a patch on the knee
        return None

    legs = trousers(c, hide, 3, boot_from=8, boot=DARKLEATHER, boot_idx=3, extra=legx)

    def kilt(f):
        if f.face == "bottom" or f.y > 4:
            return None
        if f.y == 4 and h01(c.key, "kl", f.part, f.face, f.x) < 0.45:
            return None
        return furpx(c, fur, 3, f, "kilt")

    def beard_hat(f):
        x, y, fc = f.x, f.y, f.face
        bc = lambda i: cl(c.hr, i)  # noqa: E731
        n = h01(c.key, "wb", f.part, fc, x, y)
        if fc == "front":
            if y == 3 and x in (1, 2, 5, 6) and n < 0.8:
                return bc(2)  # the brow overhangs
            if y == 4 and x in (0, 7):
                return bc(3)
            if y == 5 and x in (0, 1, 6, 7):
                return bc(3 if n > 0.5 else 2)
            if y == 6 and not (x in (3, 4) and n < 0.5):
                return bc(3 if n > 0.6 else 2)
            if y == 7:
                return bc(2 if n > 0.35 else 1)
            return None
        if fc == "bottom":
            return bc(2)
        if fc in ("left", "right") and f.fd <= 3 and y >= 5:
            return bc(2 if n > 0.3 else 1)
        return None

    return dict(body=body, jacket=jk, rarm=armf, rsleeve=sl, rleg=legs, rpants=kilt, hat_first=beard_hat)


def gus_head(c, L):
    """Deep-set eyes under a heavy brow, a big weathered nose, a wild beard on the base head."""
    S = c.S
    bc = lambda i: cl(c.hr, i)  # noqa: E731

    dim_white = _mixpx(EYE_WHITE_PX, (70, 54, 44), 0.5)
    hairset = {cl(c.hr, i) for i in range(6)}

    def post(f, px):
        x, y = f.x, f.y
        if px in hairset:  # more light and dark among the strands than a tidy head has
            k = h01(c.key, "gwv", f.face, x, y)
            px = bc(1 if k < 0.14 else 2 if k < 0.5 else 3 if k < 0.86 else 4)
        if f.face != "front":
            return px
        n = h01(c.key, "gb", x, y)
        if y == 2 and x in (3, 4):
            return S(1)  # a deep furrow between the brows
        if y == 3 and 1 <= x <= 6 and x not in (3, 4):
            return bc(3 if n > 0.35 else 2)  # heavy, pale brows over the eyes
        if y == 4 and x in (1, 6):
            return dim_white  # the whites sit back in shadow
        if y == 4 and x in (2, 5):
            return hex_rgba("#2a1c12")
        if y == 4 and x in (3, 4):
            return S(4)
        if y == 5 and x in (3, 4):
            return _mixpx(S(3), (190, 90, 80), 0.32)  # a big red nose
        if y == 5 and x in (0, 1, 2, 5, 6, 7):
            return bc(2 if n > 0.35 else 3)
        if y == 6:
            if x in (3, 4):
                return bc(1)  # the mouth lost in the beard
            return bc(2 if n > 0.3 else 1)
        if y == 7:
            return bc(2 if n > 0.4 else 3)
        return px
    return post


# ---------------------------------------------------------------------------------------------------------------------
# Pell, the wounded Runner

def pell(c, L):
    tunic = mk("#27488a")
    under = mk_soft("#d8d0b8")
    lea = mk("#8a5a30")
    trou = mk("#5a5448")
    boot = mk("#b09060")
    glove = mk("#4a3a2a")
    bandage = mk_soft("#ebe4cf")
    mud = (84, 64, 44)

    def blood_spot(f):
        return cl(BLOOD, 3 if (f.x + f.y) % 2 else 2)

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(tunic, 1, f)
        if f.face == "front":
            if f.y == 0 and f.x in (2, 5):
                return P(BRONZE, 3, f)
            if f.x == 3 and 1 <= f.y <= 10:
                return P(BRONZE, 2, f)  # the piped placket
        if f.y == 11 and f.face != "top":
            return P(BRONZE, 2, f)
        px = T(c, tunic, 3, f, folds=0.18)
        if h01(c.key, "mud", f.part, f.face, f.x, f.y) < 0.06 and f.y > 5:
            return _mixpx(tuple(px[:3]) and px, mud, 0.45)
        return px

    strap = baldric(c, lea, 3, front=(6, 1), back=(1, 6), y0=1, ymax=9)
    tube = mk("#a8743c")
    tube_cols = lambda y: (4, 5, 6) if y <= 4 else (3, 4, 5)  # noqa: E731  (the tube hangs a little slanted)

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return T(c, tunic, 4, f, folds=0.0) if x in (0, 1, 6, 7) else None
        if fc == "front":
            if (x, y) in ((6, 1), (7, 1), (7, 2), (6, 2)):
                return T(c, under, 3 if y == 1 else 2, f, folds=0.0)  # the torn shoulder: the linen shows
            r = strap(f)
            if r is not None:
                return P(BRONZE, 4, f) if (x, y) == (4, 5) else r
            if 5 <= x <= 6 and 6 <= y <= 7 and (x + y) % 2 == 1 or (x, y) == (6, 6):
                return blood_spot(f)  # dried blood at the ribs
            if y == 8 and 1 <= x <= 6:
                return T(c, lea, 3, f, folds=0.0, lo=0.05) if x not in (3, 4) else P(BRONZE, 4, f)
            if y >= 10 and h01(c.key, "tr", x, y) < (0.3 if y == 10 else 0.65):
                return None
            return None
        if fc == "back":
            r = None
            cols = tube_cols(y)
            if y <= 9 and x in cols:
                k = cols.index(x)
                if y == 0 or y == 9:
                    return P(BRONZE, 5 - k, f)  # the caps
                if y == 5:
                    return P(BRONZE, 5 - k, f)  # a band
                if y == 8 and k == 1:
                    return cl(BLOOD, 4)  # the Association's wax seal
                return T(c, tube, 5 - k, f, folds=0.0, lo=0.03)  # lit on the left, shaded on the right
            r = strap(f)
            if r is not None:
                return r
            if y >= 10 and h01(c.key, "trb", x, y) < (0.3 if y == 10 else 0.65):
                return None
            if y >= 10:
                return T(c, tunic, 2, f, folds=0.2)
            return None
        if y == 8:
            return T(c, lea, 3, f, folds=0.0)
        return None

    def sleeve_len(f):
        """How far down the sleeve reaches: the right arm is torn short and ragged."""
        if f.side == "r":
            return {0: 6, 1: 4, 2: 5, 3: 6}[f.u]
        return 7

    def armf(f):
        y = f.y
        if f.face == "top":
            return T(c, tunic, 3, f)
        if f.face == "bottom":
            return c.S(2)
        n = sleeve_len(f) if f.face in ("front", "back") else (5 if f.side == "r" else 7)
        if y <= n:
            return T(c, tunic, 3, f, folds=0.14)
        if y == n + 1 and f.side == "r" and f.face != "bottom":
            return P(tunic, 1, f)  # frayed
        if f.side == "l" and y == 8:
            return P(BRONZE, 3, f)  # the left cuff is still piped
        if y in (9, 10):
            return T(c, glove, 3 if y == 9 else 2, f, folds=0.0)
        r = weather(c, f, 0.1, 3, (92, 56, 40))
        if f.side == "r" and f.face == "front" and f.u == 1 and y == n + 3:
            return cl(BLOOD, 3)  # a graze under the torn sleeve
        return r

    def legx(f):
        if f.side == "l" and f.face == "front" and f.u in (1, 2) and f.y in (5, 6):
            return cl(c.sk, shade(f, 2))  # a rip at the knee shows the skin
        return None

    legs = trousers(c, trou, 3, boot_from=9, boot=boot, boot_idx=3, extra=legx)

    def wrap(f):
        """The bandaged right leg (viewer's left): a pale wrap round the thigh and calf, a seep of blood."""
        if f.side != "r" or f.face in ("top", "bottom"):
            return None
        y = f.y
        if 3 <= y <= 9:
            i = 4 if (f.x + y) % 3 == 0 else 3 if (f.x + y) % 3 == 1 else 2
            if y in (3, 9):
                i = 2
            if f.face == "front" and f.u in (1, 2) and y in (5, 6) and (f.u + y) % 2 == 0:
                return cl(BLOOD, 4 if f.u == 1 else 3)
            if f.face == "front" and f.u == 1 and y == 7:
                return _mixpx(cl(bandage, shade(f, i)), (150, 50, 46), 0.4)
            return cl(bandage, shade(f, i))
        return None

    def head_band(f):
        """The bandage round the head: two rows over the brow, the knot and its tails, a seep of blood."""
        x, y, fc = f.x, f.y, f.face
        if fc in ("top", "bottom"):
            return None
        band = (y in (1, 2)) if fc != "front" else (y in (1, 2))
        if band:
            if fc == "front" and ((x, y) in ((2, 1), (2, 2), (3, 2)) or (x, y) == (3, 1) and h01(c.key, "bs", x) < 0.8):
                return cl(BLOOD, 4 if (x, y) != (3, 2) else 3)
            return cl(bandage, shade(f, 4 if y == 1 else 3 if (x + y) % 2 else 2))
        if fc == "front" and x == 7 and 3 <= y <= 4:
            return cl(bandage, 3)  # a loose end by the temple
        if fc == "left" and f.fd == 1 and 3 <= y <= 5:
            return cl(bandage, 3 if y == 3 else 2)
        if fc == "back" and x in (5, 6) and 3 <= y <= 5:
            return cl(bandage, 3 if x == 5 else 2)
        return None

    def rp(f):
        r = wrap(f)
        if r is not None:
            return r
        return None

    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=rp, hat=head_band)


def pell_head(c, L):
    S = c.S

    def post(f, px):
        x, y = f.x, f.y
        if f.face == "front":
            if (x, y) in ((6, 6), (5, 5)) and h01(c.key, "dirt", x, y) < 0.9:
                return _mixpx(px, (90, 66, 46), 0.4)  # grime on the cheek
            if (x, y) == (6, 4):
                return px
            if (x, y) == (1, 6):
                return cl(BLOOD, 3)  # a split lip
            if y == 6 and x in (3, 4):
                return _mixpx(S(2), (120, 60, 60), 0.4)
        return px
    return post


# ---------------------------------------------------------------------------------------------------------------------
# Hale, the quartermaster

def hale(c, L):
    coat = mk("#1f3258")
    trou = mk("#2b303f")
    lea = mk("#6a4426")
    L.beard = None
    ledger = {"B": ("book", 2, "f"), "b": ("book", 3, "f"), "c": (BRONZE, 4, "f"), "p": (LINEN, 3, "f"), "d": ("book", 1, "f")}

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(coat, 1, f)
        return T(c, coat, 3, f, folds=0.14)

    strap = baldric(c, lea, 3, front=(1, 6), back=(6, 1), y0=1, ymax=9)
    crest = spr(c, 4, 1, BELL4, BELLG)
    book = ["cBBc",
            "pbbb",
            "pbbb",
            "cbbc"]
    book_x = 4

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return P(BRONZE, 4, f) if x in (0, 1, 6, 7) else T(c, coat, 4, f, folds=0.0)  # shoulder boards
        if fc == "front":
            if y == 0:
                if x in (0, 1, 6, 7):
                    return P(BRONZE, 4 if x in (1, 6) else 3, f)
                return T(c, LINEN, 4, f, folds=0.0) if x in (3, 4) else P(coat, 5, f)  # the stock and the collar
            if y == 1 and x in (3, 4):
                return T(c, LINEN, 3, f, folds=0.0)
            r = strap(f)
            if r is not None and not (4 <= x <= 7 and 1 <= y <= 5):
                return P(BRONZE, 4, f) if (x, y) == (3, 4) else r
            r = crest(f)
            if r is not None:
                return r  # the Association's bell
            # the ledger on the strap's end, at the right hip (viewer's right)
            r = spr(c, book_x, 7, book, ledger)(f)
            if r is not None:
                return r
            if x in (2, 3) and 1 <= y <= 2 and (x + y) % 2 == 0:
                return P(coat, 5, f)  # lapel
            if x == 3 and 2 <= y <= 10 and y % 2 == 1:
                return P(BRONZE, 5, f)  # bronze buttons
            if x == 4 and 6 <= y <= 10:
                return P(BRONZE, 2, f)  # the front edge, piped in bronze
            if y == 11:
                return P(coat, 1, f)
            return None
        if fc == "back":
            if y == 0 and x in (0, 1, 6, 7):
                return P(BRONZE, 3, f)
            r = strap(f)
            if r is not None:
                return r
            if x in (3, 4) and y >= 6:
                return P(coat, 1 if x == 3 else 2, f)  # the vent
            if y == 11:
                return P(coat, 1, f)
            return None
        if y == 0 and f.fd in (1, 2):
            return P(BRONZE, 4, f)
        return None

    def armf(f):
        return arm(c, coat, 3, 8, cuff=BRONZE, cuff_idx=3, bare_hi=3)(f)

    boots = lambda f: P(BRONZE, 4, f) if f.face == "front" and f.u == 1 and f.y == 9 else None  # noqa: E731
    legs = trousers(c, trou, 3, boot_from=8, boot=DARKLEATHER, boot_idx=3, extra=boots)
    tails = skirt(c, coat, 7, hem=coat, hem_idx=1, bronze_hem=True)

    def tails_f(f):
        if f.face in ("front", "back") and f.u in (1, 2) and f.y <= 6 and False:
            return None
        if f.face == "back" and f.u in (1, 2) and f.y <= 7 and f.y >= 2 and f.side == "r" and False:
            return None
        return tails(f)

    return dict(body=body, jacket=jk, rarm=armf, rleg=legs, rpants=tails_f)


def hale_head(c, L):
    S = c.S
    bc = lambda i: cl(c.hr, i)  # noqa: E731

    hair1 = cl(c.hr, 1)

    def post(f, px):
        x, y = f.x, f.y
        if f.face == "front":
            if y == 5 and 2 <= x <= 5:
                return bc(2)  # a neat moustache
            if y == 6 and x in (2, 5):
                return bc(2)  # its ends turned down
            if y == 6 and x in (3, 4):
                return _mixpx(S(2), (110, 54, 50), 0.4)
            if y == 3 and x in (1, 2, 5, 6):
                return bc(2)  # straight, dark brows
        if px == hair1 and h01(c.key, "salt", f.face, f.x, f.y) < 0.4:
            return cl("hc6", 3)  # the close crop is going grey, most at the temples
        return px
    return post


# ---------------------------------------------------------------------------------------------------------------------
# Marta, the caravan master

def marta(c, L):
    coat = mk("#aa7a2a")
    collar = mk("#4a3220")   # a dark velvet collar, lapels and cuffs on the ochre
    shirt = mk_soft("#efe6d0")
    lea = mk("#5a3a22")
    felt = mk("#4e3a2a")
    band = mk("#8a3a2e")
    trou = mk("#3e3028")
    boot = mk("#2a1e16")
    glove = mk("#7a5a38")
    feather = mk("#2f8a86")

    def body(f):
        if neck(f):
            return c.S(2)
        if f.face == "bottom":
            return P(shirt, 1, f)
        return T(c, shirt, 3, f, folds=0.12)

    strap = baldric(c, lea, 3, front=(1, 6), back=(6, 1), y0=1, ymax=8)
    purse = ["tt",
             "LL",
             "LC"]
    purse_leg = {"t": (BRASS, 3, "f"), "L": (lea, 3, "f"), "C": (COIN, 4, "f")}
    case = ["pp.",
            "LLL",
            "LcL"]
    case_leg = {"p": (LINEN, 3, "f"), "L": (lea, 3, "f"), "l": (lea, 2, "f"), "c": (BRASS, 5, "f")}

    def jk(f):
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            return T(c, coat, 4, f, folds=0.0)
        if fc == "front":
            if y == 0:
                return None if x in (3, 4) else (T(c, collar, 4, f, folds=0.0) if x in (1, 2, 5, 6) else T(c, coat, 4, f, folds=0.0))
            if y <= 3 and x in (3, 4):
                return None  # the open neck shows the shirt
            if y <= 3 and x in (2, 5):
                return T(c, collar, 4 if y < 3 else 3, f, folds=0.0)  # lapels
            if y == 8 and 0 <= x <= 7:
                return P(BRASS, 5, f) if x in (3, 4) else T(c, lea, 3, f, folds=0.0, lo=0.05)  # belt and buckle
            r = strap(f)
            if r is not None:
                return r
            r = spr(c, 1, 9, purse, purse_leg)(f)
            if r is not None:
                return r  # the coin purse
            r = spr(c, 5, 9, case, case_leg)(f)
            if r is not None:
                return r  # the map case at the left hip, a roll of parchment sticking out
            if y >= 3 and x in (2, 5) and y % 2 == 1 and y <= 9:
                return P(BRASS, 5, f)  # brass buttons, two rows
            if y >= 1 and x in (3, 4) and y >= 4:
                return P(coat, 2 if x == 3 else 3, f)
            if y == 11:
                return P(coat, 1, f)
            return T(c, coat, 3, f, folds=0.22)
        if fc == "back":
            if y == 0:
                return T(c, collar, 4, f, folds=0.0) if 1 <= x <= 6 else None
            if y == 8:
                return T(c, lea, 3, f, folds=0.0, lo=0.05)
            r = strap(f)
            if r is not None:
                return r
            if x in (3, 4) and y >= 5:
                return P(coat, 1 if x == 3 else 2, f)  # the vent
            if x <= 1 and 9 <= y <= 11:
                return P(lea, 3 if y > 9 else 2, f)  # the map case behind the left hip
            if y == 11:
                return P(coat, 1, f)
            return T(c, coat, 3, f, folds=0.22)
        if y == 8:
            return T(c, lea, 3, f, folds=0.0, lo=0.05)
        if f.face == "left" and 9 <= y <= 11 and f.fd >= 1:
            return P(lea, 3 if y > 9 else 2, f)
        return T(c, coat, 3, f, folds=0.22) if y <= 10 else P(coat, 1, f)

    def armf(f):
        return arm(c, coat, 3, 8, cuff=collar, cuff_idx=3, glove=glove, glove_from=10)(f)

    def sl(f):
        if f.face in ("top", "bottom") or f.inner:
            return None
        if f.y == 9 and f.face == "front" and f.u == 1:
            return P(BRASS, 4, f)  # a cuff button
        return None

    legs = trousers(c, trou, 3, boot_from=9, boot=boot, boot_idx=3,
                    extra=lambda f: P(BRASS, 4, f) if f.face == "front" and f.u == 1 and f.y == 10 and False else None)
    tails = skirt(c, coat, 8, hem=collar, hem_idx=3)

    def hat(f):
        """A wide-brim felt hat: crown, a dark red band with a brass buckle and a feather, the brim a shaded rim."""
        x, y, fc = f.x, f.y, f.face
        if fc == "bottom":
            return None
        if fc == "top":
            if x in (0, 7) or f.y in (0, 7):
                return P(felt, 2, f)  # the brim seen from above
            return P(felt, 3 if x in (3, 4) and f.y > 1 else 4, f)  # the crown, creased down the middle
        if fc == "front":
            if y <= 1:
                return T(c, felt, 3 if y == 1 else 4, f, folds=0.0)
            if y == 2:
                return P(BRASS, 5, f) if x in (3, 4) else T(c, band, 3, f, folds=0.0)
            if y == 3:
                return P(felt, 3, f)  # the brim's edge
            if y in (4, 5, 6) and (x in (0, 7) or (x in (1, 6) and y == 5)):
                n = h01(c.key, "mc", x, y)
                return cl(c.hr, 3 if n > 0.6 else 2 if n > 0.2 else 1)  # curls hanging by the face
            return None
        if y <= 1:
            return T(c, felt, 3 if y == 1 else 4, f, folds=0.0)
        if y == 2:
            if fc == "right" and f.fd in (1, 2):
                return T(c, feather, 4, f, folds=0.0)  # a jay's feather tucked in the band
            return T(c, band, 3, f, folds=0.0)
        if y == 3:
            if fc == "right" and f.fd in (1, 2):
                return T(c, feather, 3, f, folds=0.0)
            return P(felt, 3, f) if f.fd != 0 or fc == "back" else P(felt, 2, f)  # the brim's edge
        return None

    def hat_fx(f):
        return hat(f)

    return dict(body=body, jacket=jk, rarm=armf, rsleeve=sl, rleg=legs, rpants=tails, hat=hat)


def marta_head(c, L):
    S = c.S

    def post(f, px):
        x, y = f.x, f.y
        if f.face == "front":
            if y == 6 and x in (3, 4):
                return _mixpx(S(2), (150, 48, 52), 0.55)  # painted lips
            if y == 6 and x in (2, 5):
                return S(2)  # a half smile
        if f.face == "right" and f.fd == 4 and y == 6:
            return cl(BRASS, 4)  # a hoop earring
        return px
    return post


# ---------------------------------------------------------------------------------------------------------------------
# Composing

class Wayfarer:
    def __init__(self, wid, name, role, slim, look, age, male, outfit, head=None, eye=None, specs=None):
        self.id, self.name, self.role, self.slim = wid, name, role, slim
        self.c = Ctx(wid, wid, look, 1 if slim else 0, age, name)
        self.c.male = male
        self.outfit, self.head, self.eye, self.specs = outfit, head, eye, specs
        self.file = wid


# look = [tone, hairStyle, hairColour, outfit, accent, face]
WAYFARERS = [
    Wayfarer("wren", "Wren", "young Runner", True, (3, 0, 5, 0, 0, 1), 1, False, wren, eye="#3f72b8"),
    Wayfarer("hettie", "Hettie", "shepherd", True, (4, 4, 7, 0, 0, 4), 3, False, hettie, head=hettie_head, eye="#7a9ab4"),
    Wayfarer("ilse", "Ilse", "herbalist", True, (5, 5, 3, 0, 0, 0), 2, False, ilse, head=ilse_head, eye="#4c8a52", specs=ilse_specs),
    Wayfarer("gus", "Gus", "hermit", False, (2, 2, 6, 0, 0, 5), 3, True, gus, head=gus_head),
    Wayfarer("pell", "Pell", "wounded Runner", False, (3, 0, 1, 0, 0, 7), 1, True, pell, head=pell_head),
    Wayfarer("hale", "Hale", "quartermaster", False, (2, 8, 1, 0, 0, 3), 2, True, hale, head=hale_head, eye="#3a2a1a"),
    Wayfarer("marta", "Marta", "caravan master", True, (1, 6, 0, 0, 0, 4), 2, False, marta, head=marta_head, eye="#2a160c"),
]
BY_ID = {w.id: w for w in WAYFARERS}


def compose(w: Wayfarer) -> Canvas:
    c = w.c
    parts = PARTS_SLIM if c.slim else PARTS_WIDE
    L = Look(c)
    L.specs = False  # the citizens' random spectacles are theirs; only Ilse wears a pair, painted by her own painter
    o = w.outfit(c, L)
    head0, hat0 = C.build_head(c, L, o.get("hat"))
    post = w.head(c, L) if w.head else None
    eye = eyes(w.eye) if w.eye else None

    def head(f):
        px = head0(f)
        if eye is not None:
            px = eye(f, px)
        if post is not None:
            px = post(f, px)
        return px

    hat_first = o.get("hat_first")
    sp = w.specs(c) if w.specs else None
    hat = lay(hat_first, sp, hat0) if (hat_first or sp) else hat0
    jacket = lay(o.get("jacket_first"), o.get("jacket"), lambda f: C.hair_jacket(L, f))
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
        C.paint_part(cv, part, parts, c.slim, lay(fn, (lambda f: SKIN_PX(c, f)) if part in BASE_PARTS else None))
    C.validate(cv, c.slim, c.key)
    return cv


STONE = [(46, 45, 42), (66, 65, 61), (88, 86, 81), (112, 110, 104), (138, 135, 128), (166, 163, 155), (190, 187, 178)]
MOSS = [(52, 70, 34), (72, 96, 44), (98, 124, 58)]
# top faces of the head, hat, body and both arms (classic layout): where the moss grew while they sat
TOPS = [(8, 0, 16, 8), (40, 0, 48, 8), (20, 16, 28, 20), (44, 16, 48, 20), (36, 48, 40, 52), (20, 32, 28, 36), (44, 32, 48, 36)]


def risen() -> Canvas:
    """One of the Stilled, stood up after the Wake (SPEC 43.14): a traveller's clothes gone to weathered stone, moss on
    the head and shoulders where it grew while they sat, lichen flecks. Wren's cut, because she dressed for the road."""
    im = compose(BY_ID["wren"]).image()
    out = Canvas(64, 64)
    for y in range(64):
        for x in range(64):
            r, g, bl, al = im.getpixel((x, y))
            if al == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * bl) / 255
            px = STONE[min(len(STONE) - 1, int(lum * 1.15 * len(STONE)))]
            top = any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in TOPS)
            n = h01("risen", "moss", x, y)
            if (top and n < 0.62) or n < 0.035:
                px = MOSS[int(h01("risen", "mt", x, y) * len(MOSS))]
            elif h01("risen", "lichen", x, y) < 0.03:
                px = (150, 148, 108)
            out.set(x, y, (*px, 255))
    return out


def build(out):
    for w in WAYFARERS:
        out.entity("wayfarer/" + w.file, compose(w))
    out.entity("wayfarer/risen", risen())


# ---------------------------------------------------------------------------------------------------------------------
# Checks

def town_metal(img) -> int:
    """How many pixels are bronze or brass (a button, a buckle, a bell)."""
    want = {tuple(hex_rgba(h)) for r in (BRONZE, BRASS) for h in LOCAL[r][2:]}
    px = zip(*[iter(img.convert("RGBA").tobytes())] * 4)
    return sum(1 for p in px if p in want)


MAD_THRESHOLD = 14.0   # any two wayfarers differ by at least this (mean abs RGB over the used pixels, 0-255)


def check(skins=None):
    skins = skins or {w.id: compose(w).image() for w in WAYFARERS}
    assert len(skins) == 7, "seven wayfarers"
    seen = {}
    for w in WAYFARERS:
        im = skins[w.id]
        assert im.size == (64, 64) and im.mode == "RGBA", w.id
        h = hashlib.sha256(im.tobytes()).hexdigest()
        assert h not in seen, f"{w.id} is identical to {seen[h]}"
        seen[h] = w.id
        assert town_metal(im) >= 2, f"{w.id} has no bronze or brass on it"
        # deterministic: the same ids give the same pixels
        assert hashlib.sha256(compose(w).image().tobytes()).hexdigest() == h, f"{w.id} is not deterministic"
        # slim skins leave the third column of the arm boxes' unused strip transparent (validate() also covers every box)
        tone = w.c.tone
        print(f"  {w.id:7s} {'slim' if w.slim else 'wide'}  bronze/brass px {town_metal(im):3d}  tone {tone}")
    worst = (1e9, None)
    for i, a in enumerate(WAYFARERS):
        for b in WAYFARERS[i + 1:]:
            d = C.mad(skins[a.id], skins[b.id])
            if d < worst[0]:
                worst = (d, (a.id, b.id))
    print(f"image difference between any two wayfarers: min {worst[0]:.1f} {worst[1]} (threshold {MAD_THRESHOLD})")
    assert worst[0] >= MAD_THRESHOLD, f"two wayfarers are too alike: {worst[1]}"
    print("skin format ok: 64x64, base parts opaque, overlays transparent where unused, no partial alpha")
    disk_check()
    return skins


def disk_check():
    """The pngs on disk (run build_all.py wayfarers first) are what the spec makes."""
    if not os.path.isdir(OUT_DIR):
        print("on disk: nothing built yet (python3 tools/art/build_all.py wayfarers)")
        return
    have = sorted(f for f in os.listdir(OUT_DIR) if f.endswith(".png"))
    want = sorted([w.file + ".png" for w in WAYFARERS] + ["risen.png"])
    assert have == want, f"wayfarer skins on disk differ from the spec: missing {set(want) - set(have)}, extra {set(have) - set(want)}"
    for w in WAYFARERS:
        im = Image.open(os.path.join(OUT_DIR, w.file + ".png")).convert("RGBA")
        assert im.size == (64, 64), w.id
        assert im.tobytes() == compose(w).image().tobytes(), f"{w.id}.png on disk is stale (run build_all.py wayfarers)"
    print(f"on disk: {len(have)} wayfarer skins, all present and current")


# ---------------------------------------------------------------------------------------------------------------------
# Preview

def sheet(skins, path, scale=9, bg=(58, 60, 66)):
    """Front on the top row, back underneath, every wayfarer with their name, build and role."""
    from PIL import ImageDraw
    n = len(WAYFARERS)
    cw = 16 * scale + 28
    fh = 32 * scale
    W = n * cw + 20
    H = 2 * fh + 20 + 66
    im = Image.new("RGBA", (W, H), bg + (255,))
    dr = ImageDraw.Draw(im)
    f_head, f_small = C._font(15), C._font(12)
    for i, w in enumerate(WAYFARERS):
        x = 10 + i * cw + 14
        for j, back in enumerate((False, True)):
            fig = C.figure(skins[w.id], w.slim, back).resize((16 * scale, fh), Image.NEAREST)
            im.alpha_composite(fig, (x, 10 + j * (fh + 4)))
        y = 10 + 2 * fh + 10
        dr.text((x, y), w.name, fill=(235, 220, 170), font=f_head)
        dr.text((x, y + 20), f"{w.role} ({'slim' if w.slim else 'wide'})", fill=(175, 180, 190), font=f_small)
        dr.text((x, y + 36), w.id + ".png", fill=(140, 145, 155), font=f_small)
    im.convert("RGB").save(path)
    return im.size


def faces_sheet(skins, path, scale=22, bg=(58, 60, 66)):
    """The head close up, front then side-on, base head and hat layer together."""
    n = len(WAYFARERS)
    cw = 8 * scale + 16
    im = Image.new("RGBA", (n * cw + 8, 8 * scale * 2 + 24), bg + (255,))
    for i, w in enumerate(WAYFARERS):
        parts = PARTS_SLIM if w.slim else PARTS_WIDE
        for j, face in enumerate(("front", "right")):
            h = C.crop(skins[w.id], "head", parts, face).copy()
            h.alpha_composite(C.crop(skins[w.id], "hat", parts, face))
            im.alpha_composite(h.resize((8 * scale, 8 * scale), Image.NEAREST), (8 + i * cw, 8 + j * (8 * scale + 8)))
    im.convert("RGB").save(path)
    return im.size


def skins_sheet(skins, path, scale=4, bg=(58, 60, 66)):
    """The flat 64x64 textures on a checkerboard (transparent overlay pixels show as the board)."""
    n = len(WAYFARERS)
    cw = 64 * scale + 12
    im = Image.new("RGBA", (4 * cw + 12, 2 * (64 * scale + 12) + 12), bg + (255,))
    for i, w in enumerate(WAYFARERS):
        x, y = 12 + (i % 4) * cw, 12 + (i // 4) * (64 * scale + 12)
        tile = Image.new("RGBA", (64 * scale, 64 * scale))
        for yy in range(0, 64 * scale, scale * 2):
            for xx in range(0, 64 * scale, scale * 2):
                for dy, dx in ((0, 0), (1, 1)):
                    tile.paste((96, 98, 104, 255), (xx + dx * scale, yy + dy * scale, xx + (dx + 1) * scale, yy + (dy + 1) * scale))
                for dy, dx in ((0, 1), (1, 0)):
                    tile.paste((72, 74, 80, 255), (xx + dx * scale, yy + dy * scale, xx + (dx + 1) * scale, yy + (dy + 1) * scale))
        tile.alpha_composite(skins[w.id].resize((64 * scale, 64 * scale), Image.NEAREST))
        im.alpha_composite(tile, (x, y))
    im.convert("RGB").save(path)
    return im.size


def preview(d):
    os.makedirs(d, exist_ok=True)
    skins = {w.id: compose(w).image() for w in WAYFARERS}
    print("sheet", sheet(skins, os.path.join(d, "wayfarers_sheet.png")))
    faces_sheet(skins, os.path.join(d, "wayfarers_faces.png"))
    skins_sheet(skins, os.path.join(d, "wayfarers_skins.png"))
    return skins


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    if cmd == "preview":
        sk = preview(sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "build/art/wayfarers"))
        check(sk)
    else:
        check()
