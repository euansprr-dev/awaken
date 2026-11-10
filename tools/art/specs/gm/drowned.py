"""The Drowned Archive's monsters (SPEC §60). Room: teal water, wet stone, paper. So they are sodden cream paper and
black ink, and each carries a teal light between its pages."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

PAPER = Ramp("#4e4838", "#6e664e", "#928868", "#b6ac88", "#d6cca8", "#efe6c8")
INK = Ramp("#05070a", "#0b0f16", "#131924", "#1d2534", "#2a3448", "#3c4a62")
BRASS = Ramp("#2a1c0c", "#46300f", "#6a4a18", "#8f6a24", "#b88c38", "#dcb458")
SALT = Ramp("#56606a", "#74808a", "#94a0a6", "#b4bec2", "#d2dadc", "#eef2f0")
WOOD = Ramp("#1a120c", "#2a1c12", "#3c2a1a", "#523a24", "#6a4c30", "#84623e")
TEAL, HOT = "#3fe0d0", "#d8fff8"

QUAD = ("0_l", "0_r", "1_l", "1_r")
TROT = [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)]


def book(P, cube, spine="south", base=2.6, glow=False):
    """A shut book: dark boards, and the sodden edges of its pages on the three sides that are not its spine."""
    P.paint(cube, INK, base, rim=0.5)
    for fname in K.SIDES:
        if fname == spine:
            continue
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for i, j in f.each():
            if 0 < j < f.h - 1:
                f.set(i, j, PAPER.at(3.4 + (j % 2) * 0.9 - (1.2 if K._h(cube, fname, i, j) % 6 == 0 else 0)))
        if glow and f.h > 2:
            j = f.h // 2
            for i in range(1, f.w - 1):
                if K._h(cube, fname, i, 7) % 3:
                    f.set(i, j, hexc(TEAL))
                    g.set(i, j, hexc(TEAL, 150))


def writing(P, cube, fname, a=200, every=2):
    """A page of lit writing: lines of teal on cream."""
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for i, j in f.each():
        f.set(i, j, PAPER.at(4.8))
        if j % every == 1 and 0 < i < f.w - 1 and K._h(cube, fname, i, j) % 5:
            f.set(i, j, hexc(TEAL))
            g.set(i, j, hexc(TEAL, a))


def sheet(P, cube, faces, seed=0, lines=True):
    """A loose page (both faces of a flat box): cream, a few lines of ink."""
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            f.set(i, j, PAPER.at(4.4 + 0.5 * ((i + j + seed) % 2)))
            if lines and j % 2 == 1 and 0 < i < f.w - 1 and K._h(cube, i, j, seed) % 4:
                f.set(i, j, INK.at(3))


# ---------------------------------------------------------------------------------------------------------------------
# The Blotter (ink_drowned): a walking heap of drowned books, its head one open book. The book opens wide before it
# spits ink, and snaps shut on a blow.

def blotter():
    r = Rig("ink_drowned", (128, 64))
    r.shadow = 0.8
    body = K.at(r.bone("body", (0, 15, 0)), 15)
    body.cube("book0", -6, -3, -5, 12, 4, 10)
    K.legs2(body, "1", (3.5, 1, 0), 4, 5, 22, thick=(3, 2), foot=(4, 2, 4, -2))
    chest = body.bone("chest", (0, -3, 0), (4, 14, 0))
    chest.cube("book1", -5, -4, -5, 10, 4, 9)
    chest.bone("leaf0", (5, -1, 1), (0, 0, 24)).cube("sheet0", 0, 0, -2, 4, 0, 4)
    top = chest.bone("stack", (0, -4, 0), (-3, -26, 3))
    top.cube("book2", -4.5, -3, -4, 9, 3, 8)
    top.bone("leaf1", (-4.5, -1, -1), (0, 0, -30)).cube("sheet1", -4, 0, -2, 4, 0, 4)
    neck = top.bone("neck", (0, -3, -1), (8, 12, -3))
    neck.cube("book3", -2, -3, -2, 4, 3, 4)
    head = neck.bone("head", (0, -4.5, 3))      # the hinge of the open book: its spine, at the back
    head.cube("spine", -4.5, -2, 0, 9, 4, 1)
    cover = head.bone("cover", (0, 0, 0), (-9, 0, 0))
    cover.cube("cover_u", -4.5, -2, -10, 9, 1, 10)
    cover.cube("pages_u", -4, -1, -9, 8, 1, 9)
    jaw = head.bone("jaw", (0, 0, 0), (9, 0, 0))
    jaw.cube("pages_l", -4, 0, -9, 8, 1, 9)
    jaw.cube("cover_l", -4.5, 1, -10, 9, 1, 10)
    for side, nm in SIDES2:
        arm = chest.bone("arm_" + nm, (side * 5.5, -3, 0), (6, 0, side * -8))
        arm.cube("armu_" + nm, -1, 0, -1, 2, 6, 2, mirror=side < 0, share=None if side > 0 else "armu_l")
        fore = arm.bone("fore_" + nm, (0, 6, 0), (-24, 0, 0))
        fore.cube("armf_" + nm, -1, 0, -1, 2, 4, 2, mirror=side < 0, share=None if side > 0 else "armf_l")
        fore.cube("fist_" + nm, -1.5, 4, -2, 3, 3, 4, mirror=side < 0, share=None if side > 0 else "fist_l")
    r.look = [("neck", 0.3, 0.2), ("head", 0.4, 0.3)]
    r.pack()

    legs = ("1_l", "1_r")
    gape = lambda a: {"cover": (-a, 0, 0), "jaw": (a * 0.8, 0, 0)}  # noqa: E731
    lagc = {"head": 0.04, "arm_l": 0.05, "arm_r": 0.05, "fore_l": 0.1, "fore_r": 0.1, "leaf0": 0.12, "leaf1": 0.12}
    K.moves(r, spit="Ink Spit")
    K.act(r, "spit", dict(gape(58), chest=(-12, 0, 0), stack=(-6, 0, 0), neck=(-18, 0, 0), arm_l=(20, 0, -14), arm_r=(20, 0, 14), body={"pos": (0, -0.8, 1)}),
          dict(gape(70), chest=(12, 0, 0), stack=(6, 0, 0), neck=(16, 0, 0), head=(6, 0, 0), arm_l=(-16, 0, -6), arm_r=(-16, 0, 6), body={"pos": (0, 0, -1.5)}),
          w=0.9, length=0.5, t_hit=0.08, plant=legs, lag=lagc,
          open_pose=dict(gape(40), chest=(10, 0, 0), neck=(24, 0, 0), head=(12, 0, 0), arm_l=(6, 0, -4), arm_r=(6, 0, 4)))
    K.plain(r, dict(gape(42), chest=(-8, 0, 0), neck=(-22, 0, 0), body={"pos": (0, 0, 1.2)}), dict(gape(-9), chest=(12, 0, 0), neck=(20, 0, 0), body={"pos": (0, -0.5, -2.5)}),
            plant=legs, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: dict(gape(3 + 3 * s(p * 2) + 16 * bump(p, 0.62, 0.03)), chest=(1.2 * s(p), 2 * s(p, 0.3), 0), stack=(0, 3 * s(p, 0.5), 0.8 * s(p, 0.2)),
                                          neck=(2 * s(p, 0.15), 0, 0), arm_l=(3 * s(p, 0.2), 0, 0), arm_r=(3 * s(p, 0.7), 0, 0)), n=16)
    K.loop(r, "walk", 0.9, lambda p: dict(gape(4 + 3 * s(p * 2)), chest=(3 + 1.5 * s(p * 2), 4 * s(p), 2.5 * s(p)), stack=(0, -3 * s(p, 0.1), -3 * s(p, 0.1)),
                                          neck=(-3 * s(p * 2, 0.15), 0, 0), arm_l=(14 * s(p), 0, 0), arm_r=(14 * s(p, 0.5), 0, 0),
                                          fore_l=(-6 + 6 * s(p, 0.1), 0, 0), fore_r=(-6 + 6 * s(p, 0.6), 0, 0)))
    K.loop(r, "run", 0.6, lambda p: dict(gape(14 + 6 * s(p * 2)), chest=(10 + 2.5 * s(p * 2), 5 * s(p), 3 * s(p)), stack=(0, -4 * s(p, 0.1), -4 * s(p, 0.1)), neck=(-10, 0, 0),
                                         arm_l=(-30 + 10 * s(p), 0, -8), arm_r=(-30 + 10 * s(p, 0.5), 0, 8), fore_l=(-20, 0, 0), fore_r=(-20, 0, 0)))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.0, lambda p: dict(gape(18 + 14 * max(0, s(p * 2))), chest=(-4, 0, 0), neck=(-8, 0, 4 * s(p)), arm_l=(-20, 0, -10), arm_r=(-20, 0, 10),
                                           body={"pos": (0, -0.6, 0)}), plant=legs)
    K.flinch(r, dict(gape(-9), chest=(-8, 0, 5), neck=(22, 0, 0), stack=(0, 10, 0), arm_l=(-30, 0, -20), arm_r=(-30, 0, 20), body={"pos": (0, -0.5, 1)}), plant=legs)
    start = K.planted(r, dict(gape(50), chest=(-12, 0, 0), neck=(-20, 0, 0)), legs)
    down = dict(gape(80), chest={"rot": (16, 20, 8), "pos": (2, -1, -3)}, stack={"rot": (20, -30, -14), "pos": (-3, 0, -4)}, neck=(50, 0, 10), head=(20, 0, 0),
                arm_l=(-70, 0, -40), arm_r=(-60, 0, 46), leg1_l=(-70, 0, 14), leg1_r=(-64, 0, -14), shin1_l=(90, 0, 0), shin1_r=(84, 0, 0))
    skip = ("sheet0", "sheet1")
    K.keyed(r, "death", 1.2, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.75, down, steps=3, skip=skip)
            + [(0.9, K.on_floor(r, K.plus(down, {"chest": (4, 0, 0)}), skip=skip)), (1.2, K.on_floor(r, down, skip=skip))], lag=lagc)
    return r


def paint_blotter(r, P):
    for nm, sp, lit in (("book0", "south", False), ("book1", "west", True), ("book2", "east", False), ("book3", "south", True)):
        book(P, nm, sp, glow=lit)
    P.paint("spine", INK, 2.2, p_bands(3, 0.8), rim=0.6)
    for nm in ("cover_u", "cover_l"):
        P.paint(nm, INK, 2.8, rim=0.8)
    for nm in ("pages_u", "pages_l"):
        P.paint(nm, PAPER, 4.2, p_bands(2, 0.5))
    writing(P, "pages_u", "bottom")
    writing(P, "pages_l", "top")
    for nm in ("sheet0", "sheet1"):
        sheet(P, nm, ("top", "bottom"), seed=len(nm))
    P.paint("armu_l", PAPER, 3.0, p_bands(2, 0.8))
    P.paint("armf_l", PAPER, 2.6, p_bands(2, 0.8))
    book(P, "fist_l", "south", base=2.2)
    P.paint("thigh1_l", PAPER, 2.6, p_bands(2, 0.7))
    P.paint("shinb1_l", PAPER, 2.2, p_bands(2, 0.7))
    P.paint("footb1_l", INK, 2.4, rim=0.5)
    for nm in ("book0", "book1"):  # ink run down from above
        f = P.face(nm, "north")
        for i in (2, 5, f.w - 3):
            for j in range(0, 1 + K._h(nm, i) % 3):
                f.set(i, j, INK.at(1))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Paper Crane (page_wraith): a bird folded from a wet page, all flat planes and creases. They rustle, then cut in
# a line; a cut that misses leaves it sodden on the floor.

def crane():
    r = Rig("page_wraith", (64, 32))
    r.shadow = 0.3
    b = r.bone("body", (0, 16, 0))
    b.cube("keel", -1, -1, -3, 2, 3, 6)
    b.cube("deck", -3, -1.1, -3, 6, 0, 6)
    neck = b.bone("neck", (0, -1, -3), (36, 0, 0))
    neck.cube("neckp", -0.5, -7, -1, 1, 7, 2)
    neck.bone("head", (0, -7, 0), (84, 0, 0)).cube("beak", -0.5, -4, -1, 1, 4, 2)
    b.bone("tail", (0, -1, 3), (-52, 0, 0)).cube("tailp", -0.5, -7, -1, 1, 7, 2)
    for side, nm in SIDES2:
        w = b.bone("wing_" + nm, (side * 1, -1, 0), (0, 0, side * -16))
        w.cube("wingp_" + nm, 0 if side > 0 else -7, 0, -3, 7, 0, 6, mirror=side < 0, share=None if side > 0 else "wingp_l")
        t = w.bone("wing_tip_" + nm, (side * 7, 0, 0), (0, 0, side * 28))
        t.cube("wingt_" + nm, 0 if side > 0 else -5, 0, -2, 5, 0, 5, mirror=side < 0, share=None if side > 0 else "wingt_l")
    r.look = [("neck", 0.3, 0.3), ("head", 0.3, 0.2)]
    r.pack()

    flap = lambda a, t=0.0: {"wing_l": (0, 0, -a), "wing_r": (0, 0, a), "wing_tip_l": (0, 0, -t), "wing_tip_r": (0, 0, t)}  # noqa: E731  (up is positive)
    lagc = {"wing_tip_l": 0.05, "wing_tip_r": 0.05, "tail": 0.06}
    K.moves(r, cut="Papercut")
    wind = dict(flap(62, -40), body={"rot": (-24, 0, 0), "pos": (0, 1.5, 1.5)}, neck=(-30, 0, 0), head=(20, 0, 0), tail=(20, 0, 0))
    hit = dict(flap(-10, -24), body={"rot": (14, 0, 0), "pos": (0, -1, -5)}, neck=(46, 0, 0), head=(-78, 0, 0), tail=(-30, 0, 0))
    soggy = K.on_floor(r, K.lying(r, dict(flap(-22, -30), body=(8, 0, 64), neck=(40, 0, 0), head=(-30, 0, 0), tail=(-20, 0, 0))))     # flat on the floor, not propped on a wing
    K.act(r, "cut", wind, hit, w=0.6, length=0.4, t_hit=0.07, stuck=soggy, lag=lagc)
    K.plain(r, {"neck": (-34, 0, 0), "head": (-20, 0, 0), "body": {"rot": (-10, 0, 0), "pos": (0, 0.5, 1)}},
            {"neck": (40, 0, 0), "head": (20, 0, 0), "body": {"rot": (12, 0, 0), "pos": (0, 0, -3)}}, lag=lagc)
    K.loop(r, "idle", 1.6, lambda p: dict(flap(18 * s(p), 22 * s(p, -0.2)), body={"pos": (0, 0.9 * s(p, 0.25), 0)}, neck=(3 * s(p, 0.3), 0, 0), tail=(4 * s(p, 0.4), 0, 0)))
    K.loop(r, "fly", 0.5, lambda p: dict(flap(30 * s(p), 30 * s(p, -0.2)), body={"rot": (6, 0, 0), "pos": (0, 0.7 * s(p, 0.25), 0)}, neck=(8, 0, 0)))
    K.loop(r, "angry", 0.6, lambda p: dict(flap(40 + 14 * s(p), 20 * s(p, -0.2)), neck=(-12, 0, 0), head=(-14, 0, 0), body=(-10, 0, 0)))
    K.flinch(r, dict(flap(50, -50), body={"rot": (-20, 0, 30), "pos": (0, 0, 2)}, neck=(-20, 0, 0)))
    crumple = dict(flap(-70, -60), body=(30, 0, 100), neck=(60, 0, 0), head=(-40, 0, 0), tail=(40, 0, 0))
    K.keyed(r, "death", 0.9, [(0, {}), (0.1, dict(flap(60, -40), body=(-20, 0, 20))), (0.5, K.on_floor(r, dict(flap(-40, -30), body=(20, 0, 70), neck=(40, 0, 0)))),
                              (0.9, K.on_floor(r, crumple))], lag=lagc)
    return r


def paint_crane(r, P):
    P.paint("keel", PAPER, 3.6, rim=0.5)
    for nm in ("neckp", "beak", "tailp"):
        P.paint(nm, PAPER, 4.2, rim=0.4)
    f = P.face("beak", "top")
    for i, j in f.each():
        f.set(i, j, INK.at(2))  # the beak dipped in ink
    for nm, faces in (("deck", ("top", "bottom")), ("wingp_l", ("top", "bottom")), ("wingt_l", ("top", "bottom"))):
        for fname in faces:
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, PAPER.at(4.6 - 0.25 * (abs(i - j) % 3)))
                if j % 2 == 1 and 0 < i < f.w - 1 and K._h(nm, i, j) % 3:
                    f.set(i, j, INK.at(3))        # the writing it was folded from
                if i == j or i + j == f.w - 1:
                    f.set(i, j, PAPER.at(2.6))    # creases
            for i in range(f.w):                  # the cutting edge, lit
                f.set(i, 0, hexc(TEAL))
                g.set(i, 0, hexc(TEAL, 190))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Reading Glass (archive_eye): a brass reading glass that floats, its handle trailing like a tail. Light gathers
# in the glass before the beam, and it has to hold still to read you.

def reading_glass():
    r = Rig("archive_eye", (64, 64))
    r.shadow = 0.4
    b = r.bone("body", (0, 8, 0))
    head = b.bone("head", (0, 0, 0))
    head.cube("rim_t", -4, -5, -1, 8, 1, 2)
    head.cube("rim_b", -4, 4, -1, 8, 1, 2, share="rim_t")
    head.cube("rim_l", 4, -4, -1, 1, 8, 2)
    head.cube("rim_r", -5, -4, -1, 1, 8, 2, share="rim_l")
    head.cube("pane", -4, -4, 0, 8, 8, 0)
    head.bone("pupil", (0, 0, 0)).cube("pupil", -1.5, -1.5, -1, 3, 3, 1)
    b.cube("ferrule", -1.5, 5, -1.5, 3, 2, 3)
    tail = b.bone("tail", (0, 7, 0), (24, 0, 0))
    tail.cube("handle0", -1, 0, -1, 2, 4, 2)
    mid = tail.bone("tail_mid", (0, 4, 0), (22, 0, 0))
    mid.cube("handle1", -1, 0, -1, 2, 4, 2, share="handle0")
    end = mid.bone("tail_end", (0, 4, 0), (22, 0, 0))
    end.cube("handle2", -0.5, 0, -0.5, 1, 4, 1)
    end.cube("pommel", -1, 4, -1, 2, 2, 2)
    r.look = [("head", 0.6, 0.5)]
    r.pack()

    glow = lambda k: {"pupil": {"scale": (k, k, 1)}}  # noqa: E731
    lagc = {"tail_mid": 0.06, "tail_end": 0.12}
    K.moves(r, beam="Reading Beam")
    K.act(r, "beam", K.plus(glow(1.9), {"body": {"rot": (-8, 0, 0), "pos": (0, 1, 1)}, "tail": (-36, 0, 0), "tail_mid": (-20, 0, 0), "tail_end": (-16, 0, 0)}),
          K.plus(glow(2.5), {"body": {"rot": (6, 0, 0), "pos": (0, 0, 2.5)}, "tail": (20, 0, 0), "tail_mid": (14, 0, 0)}), w=1.0, length=0.5, t_hit=0.08, lag=lagc,
          open_pose=K.plus(glow(0.35), {"body": {"rot": (22, 0, 8), "pos": (0, -2, 0)}, "tail": (-14, 0, 0)}))
    K.plain(r, {"body": {"rot": (-34, 0, 0), "pos": (0, 1, 1.5)}, "tail": (-30, 0, 0)}, {"body": {"rot": (44, 0, 0), "pos": (0, -1.5, -3.5)}, "tail": (30, 0, 0), "tail_mid": (20, 0, 0)}, lag=lagc)
    K.loop(r, "idle", 5.0, lambda p: K.plus(glow(1 + 0.12 * s(p * 3)), {"body": {"pos": (0, 0.9 * s(p * 2), 0), "rot": (2 * s(p), 0, 2 * s(p, 0.25))}, "head": (0, 16 * s(p), 0),
                                                                        "pupil": {"pos": (1.6 * s(p, 0.08), 0.8 * s(p * 2, 0.3), 0)}, "tail": (4 * s(p, 0.3), 0, 5 * s(p, 0.5))}), n=16)
    K.loop(r, "fly", 1.2, lambda p: {"body": {"rot": (10 + 2 * s(p * 2), 0, 3 * s(p))}, "tail": (16 + 4 * s(p, 0.2), 0, 0), "tail_mid": (8, 0, 0)})
    K.loop(r, "angry", 0.8, lambda p: K.plus(glow(1.4 + 0.3 * s(p * 2)), {"body": (-6, 0, 5 * s(p)), "tail": (-10, 0, 10 * s(p, 0.2))}))
    K.flinch(r, K.plus(glow(0.5), {"body": {"rot": (16, 50, 20), "pos": (0, 0, 2)}, "tail": (-30, 0, 20)}))
    flat = K.plus(glow(0.02), {"body": (84, 0, 14), "tail": (-70, 0, 0), "tail_mid": (-20, 0, 0), "tail_end": (-20, 0, 0)})
    K.keyed(r, "death", 1.1, [(0, {}), (0.12, K.plus(glow(2.2), {"body": (-16, 0, 0)})), (0.3, K.plus(glow(0.02), {"body": (10, 30, 10)})),
                              (0.75, K.on_floor(r, flat)), (0.86, K.on_floor(r, K.plus(flat, {"body": (-8, 0, 0)}))), (1.1, K.on_floor(r, flat))], lag=lagc)
    return r


def paint_reading_glass(r, P):
    for nm in ("rim_t", "rim_l"):
        P.paint(nm, BRASS, 3.4, rim=1.0)
    P.paint("ferrule", BRASS, 2.6, p_bands(2, 0.8), rim=0.6)
    P.paint("handle0", WOOD, 3.0, p_strokes(2, 3, seed=5), rim=0.5)
    P.paint("handle2", WOOD, 2.4)
    P.paint("pommel", BRASS, 3.0, rim=0.8)
    for fname in ("north", "south"):  # the glass: pale and lit, a glint across one corner
        f, g = P.face("pane", fname), P.face("pane", fname, glow=True)
        for i, j in f.each():
            d = math.hypot(i - 3.5, j - 3.5)
            if d > 4.6:
                f.set(i, j, (0, 0, 0, 0))
                continue
            f.set(i, j, hexc("#9fded6" if (i + j) in (3, 4) else "#5fb4ac"))
            g.set(i, j, hexc(TEAL, 70 if d > 2 else 110))
    P.paint("pupil", INK, 1.0)
    K.lit(P, "pupil", "north", 1, 1, 1, 1, TEAL, HOT, a=255)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Quillback (salt_stray): a salt-crusted beast with a back full of quill pens. It bows its head and the quills come
# over to point at you; bowed, it cannot see you, and that is when to close.

def quillback():
    r = Rig("salt_stray", (128, 64))
    r.shadow = 0.8
    body = K.at(r.bone("body", (0, 15, 0)), 15)
    body.cube("barrelq", -4, -4, -6, 8, 7, 12)
    body.cube("crust", -4.5, -5, -5, 9, 2, 10)
    K.legs2(body, "0", (3, 2, -4), 4, 5, 44, thick=(3, 2))     # forelegs: knees ahead
    K.legs2(body, "1", (3, 1, 4), 6, 4, -29, thick=(3, 2))     # hind legs: hocks behind
    head = body.bone("head", (0, -1, -6), (12, 0, 0))
    head.cube("skullq", -2.5, -2, -5, 5, 4, 5)
    head.cube("snoutq", -1.5, 0, -7, 3, 2, 2)
    head.bone("jaw", (0, 2, -1)).cube("jawq", -1.5, 0, -5, 3, 1, 5)
    for side, nm in SIDES2:
        head.bone("ear_" + nm, (side * 2.5, -2, -1), (-20, 0, side * 30)).cube("earq_" + nm, -0.5, -2, 0, 1, 2, 1, share=None if side > 0 else "earq_l")
    body.bone("tail", (0, -2, 6), (-25, 0, 0)).cube("tailq", -1, 0, 0, 2, 2, 4)
    for k in range(3):
        cb = body.bone(f"crest{k}", (0, -5, -3 + 3 * k), (-28 - 16 * k, 0, 0))
        for q, x in enumerate((-2.5, 0, 2.5)):
            first = k == 0 and q == 0
            cb.cube(f"shaft{k}{q}", x - 0.5, -8, -0.5, 1, 8, 1, share=None if first else "shaft00")
            cb.cube(f"vane{k}{q}", x, -6, 0.5, 0, 5, 2, share=None if first else "vane00")   # the feather, down by its back; the nib points out
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    fan = lambda a, sp=0.0: {f"crest{k}": (a + sp * k, 0, 0) for k in range(3)}  # noqa: E731  (forward is positive)
    lagc = {"crest0": 0.03, "crest1": 0.06, "crest2": 0.09, "tail": 0.06, "ear_l": 0.05, "ear_r": 0.05}
    K.moves(r, quills="Brine Arrows")
    K.act(r, "quills", dict(fan(74, 16), body={"rot": (16, 0, 0), "pos": (0, -1, 0.5)}, head=(34, 0, 0), tail=(40, 0, 0)),
          dict(fan(104, 16), body={"rot": (20, 0, 0), "pos": (0, -1.5, 1.5)}, head=(40, 0, 0)), w=0.9, length=0.45, t_hit=0.07, plant=QUAD, lag=lagc,
          open_pose=dict(fan(64, 12), body={"rot": (18, 0, 0), "pos": (0, -1.5, 0)}, head=(44, 0, 0), tail=(-10, 0, 0)))
    K.plain(r, {"head": (-22, 0, 0), "jaw": (24, 0, 0), "body": {"pos": (0, -0.5, 1.2)}}, {"head": (14, 0, 0), "jaw": (-2, 0, 0), "body": {"pos": (0, 0, -2.6)}}, plant=QUAD, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: dict(fan(2 * s(p) + 9 * bump(p, 0.4, 0.03)), head=(3 * s(p, 0.2), 8 * s(p, 0.45), 0), jaw=(4 * bump(p, 0.7, 0.04), 0, 0),
                                          tail=(0, 8 * s(p * 2), 0), ear_l=(12 * bump(p, 0.3, 0.02), 0, 0), ear_r=(12 * bump(p, 0.8, 0.02), 0, 0)), n=20)
    K.loop(r, "walk", 0.8, lambda p: dict(fan(3 * s(p * 2, 0.1)), head=(3 * s(p * 2, 0.15), 0, 2 * s(p)), body=(1.2 * s(p * 2), 0, 2 * s(p)), tail=(0, 10 * s(p), 0)))
    K.loop(r, "run", 0.5, lambda p: dict(fan(-8 + 4 * s(p * 2, 0.1)), head=(-8 + 4 * s(p * 2, 0.15), 0, 0), body=(4 * s(p * 2, 0.25), 0, 0), tail=(20, 6 * s(p), 0),
                                         ear_l=(26, 0, 0), ear_r=(26, 0, 0)))
    K.fallback_walk(r, TROT)
    K.loop(r, "angry", 0.9, lambda p: dict(fan(22 + 8 * s(p * 3)), head=(10, 0, 0), jaw=(10 + 6 * s(p * 4), 0, 0), body={"pos": (0, -1, 0)}), plant=QUAD)
    K.flinch(r, dict(fan(-16), head=(-16, 0, 10), body={"pos": (0, 0.4, 1.5), "rot": (0, 0, 6)}, tail=(30, 0, 0)), plant=QUAD)
    side = dict(fan(30, 6), body=(0, 0, 84), head=(10, 20, 0), leg0_l=(30, 0, 0), leg0_r=(10, 0, 0), leg1_l=(-30, 0, 0), leg1_r=(-10, 0, 0), tail=(0, 0, 30), jaw=(20, 0, 0))
    start = K.planted(r, {"head": (-26, 0, 0), "body": {"pos": (0, 1, 1)}}, QUAD)
    skip = tuple(f"{n}{k}{q}" for n in ("shaft", "vane") for k in range(3) for q in range(3))
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, start)] + K.fall(r, 0.12, start, 0.6, side, steps=3, skip=skip)
            + [(0.72, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}), skip=skip)), (1.0, K.on_floor(r, side, skip=skip))], lag=lagc)
    return r


def paint_quillback(r, P):
    hide = Ramp("#14181e", "#1e242c", "#2a323c", "#38424e", "#4a5662")
    P.paint("barrelq", hide, 2.2, p_blotch("qb", 4, 0.8), rim=0.4, ao=0.8)
    P.paint("crust", SALT, 3.4, p_stone(P.rng, 0.9, 3), rim=0.9)
    for fname in K.SIDES:  # salt that has run down its flanks and dried
        f = P.face("barrelq", fname)
        for i in range(f.w):
            for j in range(0, K._h(fname, i, 3) % 4):
                f.set(i, j, SALT.at(3.0 + (K._h(fname, i, j) % 3) * 0.7))
    P.paint("skullq", hide, 2.8, rim=0.6)
    P.paint("snoutq", hide, 2.2)
    P.paint("jawq", hide, 1.8)
    f = P.face("skullq", "top")
    for i, j in f.each():
        if (i + j) % 2 == 0:
            f.set(i, j, SALT.at(3.6))
    for fname in ("west", "east"):
        K.lit(P, "skullq", fname, 1, 1, 1, 1, TEAL, HOT, a=255)
    P.paint("earq_l", hide, 3.0)
    P.paint("tailq", hide, 2.4)
    P.paint("shaft00", PAPER, 4.4)
    for fname in K.SIDES:  # a pen: the nib end black with ink, the very tip lit
        f, g = P.face("shaft00", fname), P.face("shaft00", fname, glow=True)
        f.set(0, 1, INK.at(1))
        f.set(0, 0, hexc(TEAL))
        g.set(0, 0, hexc(TEAL, 220))
    for fname in ("west", "east"):
        f = P.face("vane00", fname)
        for i, j in f.each():
            f.set(i, j, (0, 0, 0, 0) if (i == f.w - 1 and j % 2 == 0) or (j == 0 and i > 0) else PAPER.at(4.8 - 0.5 * i))
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", hide, 2.6)
        P.paint(f"shinb{tag}_l", SALT, 2.4, p_stone(P.rng, 0.6, 2))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Scribe (drowned_scribe, the elite): an inkwell that walks on four long pens, one more pen held up to write with.
# What it writes in the air becomes a crane; a blow while it writes loses the page.

def scribe():
    r = Rig("drowned_scribe", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("well", -4, -3, -4, 8, 7, 8)
    body.cube("neckw", -2.5, -5, -2.5, 5, 2, 5)
    body.cube("lipw", -3.5, -6, -3.5, 7, 1, 7)
    body.bone("drip0", (0, -6, 0)).cube("inkw", -2.5, -0.3, -2.5, 5, 1, 5)
    K.splay_pairs(body, 1, (4, 4), (-3, 3), 5, 16, rise=35, yaws=(42, -42), thick=1)
    arm = body.bone("arm", (0, -6, 1), (-22, 0, 0))
    arm.cube("armq", -0.5, -7, -0.5, 1, 7, 1)
    fore = arm.bone("fore", (0, -7, 0), (72, 0, 0))
    fore.cube("foreq", -0.5, -7, -0.5, 1, 7, 1, share="armq")
    quill = fore.bone("quill", (0, -7, 0), (76, 0, 0))
    quill.cube("pen", -0.5, -6, -0.5, 1, 7, 1)
    quill.cube("vanes", 0, 0, -1.5, 0, 5, 3)
    r.pack()

    FOUR = K.tags(2)
    SK = ("pen", "vanes", "armq", "foreq")
    lagc = {"fore": 0.05, "quill": 0.1, "drip0": 0.08}
    K.moves(r, write="Torn Pages", fangs="Ink Fangs", spit="Ink Spit")
    up = {"arm": (-16, 0, 0), "fore": (-46, 0, 0), "quill": (-30, 0, 0), "body": {"pos": (0, 1.5, 0), "rot": (-6, 0, 0)}}
    K.act(r, "write", up, {"arm": (20, 0, 0), "fore": (10, 0, 0), "quill": (24, 0, 0), "body": {"pos": (0, -1, -1)}}, w=1.2, length=0.5, t_hit=0.1, plant=FOUR, lag=lagc)
    # the wind-up is the writing itself: strokes in the air, each wider than the last, ending where the strike begins
    strokes = [(0.0, {}), (0.18, K.plus(K.scaled(up, 0.5), {"fore": (0, 18, 0), "quill": (10, 0, 0)})), (0.36, K.plus(K.scaled(up, 0.6), {"fore": (0, -22, 0), "quill": (-8, 0, 0)})),
               (0.56, K.plus(K.scaled(up, 0.75), {"fore": (0, 26, 0), "quill": (12, 0, 0)})), (0.78, K.plus(K.scaled(up, 0.9), {"fore": (0, -28, 0), "quill": (-10, 0, 0)})),
               (1.2, K.plus(up, K.scaled(up, 0.06)))]
    K.keyed(r, "write_windup", 1.2, [(t, K.planted(r, p, FOUR)) for t, p in strokes], lag=lagc)
    stab = {"arm": (134, 0, 0), "fore": (-58, 0, 0), "quill": (-26, 0, 0), "body": {"pos": (0, -1.2, -1.5), "rot": (12, 0, 0)}}
    K.act(r, "fangs", {"arm": (-30, 0, 0), "fore": (-60, 0, 0), "quill": (-40, 0, 0), "body": {"pos": (0, 2, 1)}}, stab, w=1.0, length=0.5, t_hit=0.09, plant=FOUR, lag=lagc,
          over=0.03, open_pose=stab)
    K.act(r, "spit", {"arm": (-40, 0, 0), "fore": (-30, 0, 0), "quill": (-60, 0, 0), "body": {"rot": (-8, 0, 0), "pos": (0, 0.5, 1)}},
          {"arm": (34, 0, 0), "fore": (28, 0, 0), "quill": (40, 0, 0), "body": {"rot": (8, 0, 0), "pos": (0, 0, -1.5)}}, w=0.7, length=0.45, t_hit=0.07, plant=FOUR, lag=lagc)
    K.plain(r, {"arm": (-24, 0, 0), "fore": (-40, 0, 0), "body": {"pos": (0, 1, 1)}}, {"arm": (50, 0, 0), "fore": (10, 0, 0), "quill": (-20, 0, 0), "body": {"pos": (0, -1, -2)}},
            plant=FOUR, lag=lagc)
    K.loop(r, "idle", 7.0, lambda p: {"arm": (3 * s(p), 0, 2 * s(p, 0.3)), "fore": (4 * s(p, 0.2) + 30 * bump(p, 0.5, 0.05), 0, 0), "quill": (5 * s(p * 2) + 40 * bump(p, 0.5, 0.05), 0, 0),
                                      "drip0": {"scale": (1, 1 + 0.5 * s(p * 2), 1)}, "body": {"pos": (0, 0.5 * s(p * 2), 0)}}, n=16)
    K.loop(r, "walk", 1.0, lambda p: {"body": (1.5 * s(p * 2), 0, 2 * s(p)), "arm": (4 * s(p * 2, 0.2), 0, 3 * s(p, 0.2)), "drip0": {"scale": (1, 1 + 0.6 * s(p * 2, 0.3), 1)}})
    K.loop(r, "run", 0.7, lambda p: {"body": (5 + 2 * s(p * 2), 0, 2.5 * s(p)), "arm": (-14 + 4 * s(p * 2, 0.2), 0, 0), "fore": (-16, 0, 0)})
    K.fallback_walk(r, K.alternating(2), axis="y")
    K.loop(r, "angry", 1.4, lambda p: {"arm": (-10, 0, 14 * s(p)), "fore": (-20, 0, 0), "quill": (20 * s(p * 2), 0, 0), "body": {"pos": (0, -1.5, 0)}}, plant=FOUR)
    K.flinch(r, {"body": {"pos": (0, -1.5, 1.5), "rot": (0, 0, 8)}, "arm": (-30, 0, 20), "fore": (-20, 0, 0), "drip0": {"scale": (1, 3, 1)}}, plant=FOUR)
    fold = {f"leg{t}": (0, 0, (-30 if t.endswith("l") else 30)) for t in FOUR}
    fold.update({f"shin{t}": (0, 0, (60 if t.endswith("l") else -60)) for t in FOUR})
    start = K.planted(r, {"body": {"pos": (0, 2, 0)}, "arm": (-30, 0, 0)}, FOUR)
    low = dict(fold, body=(0, 0, 70), arm=(40, 0, 30), fore=(30, 0, 0), quill=(20, 0, 0), drip0={"scale": (1, 0.05, 1)})
    K.keyed(r, "death", 1.3, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.85, low, steps=4, skip=SK)
            + [(1.0, K.on_floor(r, K.plus(low, {"body": (0, 0, 8)}), skip=SK)), (1.3, K.on_floor(r, low, skip=SK))], lag=lagc)
    return r


def paint_scribe(r, P):
    pewter = Ramp("#1c2226", "#2c3438", "#40494e", "#586268", "#748086", "#94a0a4")
    P.paint("well", pewter, 2.4, p_bands(6, 0.5), rim=0.9, ao=0.8)
    P.paint("neckw", pewter, 2.0, rim=0.5)
    P.paint("lipw", pewter, 3.4, rim=1.0)
    for fname in K.SIDES:  # ink that has run over the lip, and a lit line of it round the belly
        f, g = P.face("well", fname), P.face("well", fname, glow=True)
        for i in (1, 4, 6):
            for j in range(0, 2 + K._h(fname, i) % 3):
                f.set(i, j, INK.at(1))
        for i in range(f.w):
            if K._h(fname, i, 9) % 4:
                f.set(i, 4, hexc(TEAL))
                g.set(i, 4, hexc(TEAL, 170))
    P.paint("inkw", INK, 1.6)
    f, g = P.face("inkw", "top"), P.face("inkw", "top", glow=True)
    for i, j in f.each():
        if (i + j) % 3 == 0:
            f.set(i, j, hexc(TEAL))
            g.set(i, j, hexc(TEAL, 210))
    for nm in ("armq", "pen", "thigh0_l"):
        P.paint(nm, INK, 3.4)
    P.paint("shinb0_l", PAPER, 4.2)           # a quill's shaft, its nib end in ink
    for fname in K.ALL:
        f = P.face("shinb0_l", fname)
        for i, j in f.each():
            if max(i, j) >= max(f.w, f.h) - 3:
                f.set(i, j, INK.at(1))
    for fname in K.SIDES:
        f, g = P.face("pen", fname), P.face("pen", fname, glow=True)
        f.set(0, 0, hexc(TEAL))
        g.set(0, 0, hexc(TEAL, 240))
        f.set(0, 1, INK.at(0))
    for fname in ("west", "east"):
        f = P.face("vanes", fname)
        for i, j in f.each():
            f.set(i, j, (0, 0, 0, 0) if (i == 0 and j % 2 == 0) or (i == f.w - 1 and j % 2 == 1) else PAPER.at(4.8 - 0.3 * abs(i - 1)))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Curator (the_curator, the Warden): a tall cabinet of small drawers drifting on a skirt of loose cards. Each attack
# is a drawer sliding open; when they all slam shut at once, it has shushed you.

COLS, ROWS = (-4, 0, 4), (-11.5, -7.5, -3.5, 0.5, 4.5)
LIVE = [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2), (1, 3)]   # the drawers that move: (column, row)


def curator():
    r = Rig("the_curator", (128, 128))
    r.shadow = 1.2
    body = r.bone("body", (0, 11, 0))
    body.cube("cabinet", -6.5, -14, -4, 13, 22, 8)
    body.cube("cornice", -7.5, -16, -5, 15, 2, 10)
    body.cube("plinth", -7.5, 7, -5, 15, 1, 10)
    for k, (cx, ry) in enumerate(LIVE):
        d = body.bone(f"drawer{k}", (COLS[cx], ROWS[ry], -4))
        d.cube(f"front{k}", -1.5, -1.5, -0.4, 3, 3, 5, share=None if k == 0 else "front0")
        d.cube(f"knob{k}", -0.5, -0.5, -1.4, 1, 1, 1, share=None if k == 0 else "knob0")
    spots = [(-3.5, -5, 0), (3.5, -5, 0), (-3.5, 5, 0), (3.5, 5, 0), (-7.5, 0, 1), (7.5, 0, 1)]
    for k, (x, z, turned) in enumerate(spots):
        sk = body.bone(f"skirt{k}", (x, 8, z))
        if turned:
            sk.cube(f"cards{k}", 0, 0, -4, 0, 5, 8, share=None if k == 4 else "cards4")
        else:
            sk.cube(f"cards{k}", -3.5, 0, 0, 7, 5, 0, share=None if k == 0 else "cards0")
    r.pack()

    def out(ks, d=4.0, tilt=0.0):
        return {f"drawer{k}": {"pos": (0, 0, -d), "rot": (tilt, 0, 0)} for k in ks}
    every = range(6)
    K.moves(r, beam="Silence in the Stacks", shush="Shush", tide="Ink Tide")
    # the beam: the middle drawer comes right out and tips to look at you
    K.act(r, "beam", K.plus(out((2,), 4.4, 14), {"body": {"rot": (-5, 0, 0), "pos": (0, 0.8, 0.8)}}), K.plus(out((2,), 4.6, 18), {"body": {"rot": (4, 0, 0), "pos": (0, 0, 1.6)}}),
          w=1.2, length=0.55, t_hit=0.09, open_pose=K.plus(out((2,), 4.4, 30), {"body": {"rot": (8, 0, 3)}}))
    # the shush: every drawer slides open, then all slam shut at once
    ajar = {f"drawer{k}": {"pos": (0, 0, -(2.4 + 0.4 * k))} for k in every}
    K.act(r, "shush", K.plus(ajar, {"body": {"rot": (-4, 0, 0), "pos": (0, 1.2, 0)}}), {"body": {"rot": (6, 0, 0), "pos": (0, -1, -1.5)}}, w=1.2, length=0.5, t_hit=0.06, over=0.3)
    # the tide: they rattle, then all shoot out, and hang there spilling
    K.act(r, "tide", K.plus(out(every, 1.2), {"body": {"rot": (-8, 0, 0), "pos": (0, 1.5, 1)}}), K.plus(out(every, 4.6, 6), {"body": {"rot": (8, 0, 0), "pos": (0, -0.5, -2)}}),
          w=1.0, length=0.55, t_hit=0.08, open_pose=K.plus(out(every, 4.4, 22), {"body": {"rot": (11, 0, 0), "pos": (0, -1, -1)}}))
    K.plain(r, K.plus(out((5,), -0.3), {"body": {"rot": (-6, 0, 0), "pos": (0, 0.5, 1)}}), K.plus(out((5,), 4.6), {"body": {"rot": (5, 0, 0), "pos": (0, 0, -2)}}),
            heavy=(K.plus(out((3, 4), -0.3), {"body": {"rot": (-9, 0, 0), "pos": (0, 1, 1.5)}}), K.plus(out((3, 4), 4.6), {"body": {"rot": (8, 0, 0), "pos": (0, -0.5, -3)}})))

    def idle(p):
        q = {"body": {"pos": (0, 1.0 * s(p * 2), 0), "rot": (1.2 * s(p), 0, 1.0 * s(p, 0.25))}}
        for k in every:  # one drawer at a time eases out and back, as if it were looking something up
            q[f"drawer{k}"] = {"pos": (0, 0, -2.2 * bump(p, (0.1 + 0.16 * ((k * 5) % 6)) % 1.0, 0.035))}
        return q
    K.loop(r, "idle", 9.0, idle, n=36)
    K.loop(r, "fly", 1.6, lambda p: {"body": {"rot": (7 + 1.5 * s(p * 2), 0, 2 * s(p)), "pos": (0, 0.6 * s(p * 2), 0)}})
    K.loop(r, "angry", 0.8, lambda p: K.plus({f"drawer{k}": {"pos": (0, 0, -1.6 * max(0, s(p * 2, k / 6.0)))} for k in every}, {"body": (-4, 0, 2 * s(p))}))
    K.flinch(r, K.plus(out(every, 1.6), {"body": {"rot": (-7, 0, 5), "pos": (0, 0, 1.5)}}))
    skip = tuple(f"cards{k}" for k in range(6))
    spill = {f"drawer{k}": {"pos": (0.6 * (k % 3 - 1), -1.0 - 0.5 * k, -4.8), "rot": (30 + 6 * k, 0, 8 * (k % 3 - 1))} for k in every}
    down = K.plus(spill, {"body": (84, 0, 5)})
    K.keyed(r, "death", 1.8, [(0, {}), (0.2, K.plus(out(every, 2.0), {"body": (-8, 0, 0)})), (0.7, K.plus(out(every, 4.6, 20), {"body": {"rot": (14, 0, -3), "pos": (0, -3, 0)}})),
                              (1.3, K.on_floor(r, down, skip=skip)), (1.45, K.on_floor(r, K.plus(down, {"body": (-5, 0, 0)}), skip=skip)), (1.8, K.on_floor(r, down, skip=skip))])
    return r


def paint_curator(r, P):
    P.paint("cabinet", WOOD, 1.6, p_strokes(3, 6, seed=4), rim=0.5, ao=0.5)
    P.paint("cornice", WOOD, 3.2, rim=0.9)
    P.paint("plinth", WOOD, 1.8, rim=0.5)
    live = set(LIVE)
    f, g = P.face("cabinet", "north"), P.face("cabinet", "north", glow=True)
    for cx in range(3):
        for ry in range(5):
            i0, j0 = 1 + 4 * cx, 1 + 4 * ry
            for di in range(3):
                for dj in range(3):
                    if (cx, ry) in live:   # the hole a drawer that moves slides in (the set is the same mirrored)
                        f.set(i0 + di, j0 + dj, INK.at(0))
                    else:
                        f.set(i0 + di, j0 + dj, WOOD.at(4.6 - 0.5 * dj))
            if (cx, ry) not in live:
                f.set(i0 + 1, j0 + 1, BRASS.at(4))
    for j in (0, 4, 8, 12, 16, 20):  # the frame between the rows, light leaking through it here and there
        for i in range(f.w):
            f.set(i, j, WOOD.at(0.8))
        for i in range(1, f.w - 1):
            if K._h("cab", i, j) % 5 == 0:
                f.set(i, j, hexc(TEAL))
                g.set(i, j, hexc(TEAL, 130))
    P.paint("front0", WOOD, 3.2, rim=0.7)
    fr = P.face("front0", "north")
    for i, j in fr.each():
        fr.set(i, j, WOOD.at(4.6 - 0.5 * j))
    writing(P, "front0", "top", a=210)       # the cards inside it, seen when it slides out
    P.paint("knob0", BRASS, 3.6)
    for nm, faces in (("cards0", ("north", "south")), ("cards4", ("west", "east"))):
        K.tatter(P, nm, PAPER, 4.2, seed=3, faces=faces, fall=0.3)
        for fname in faces:
            ff = P.face(nm, fname)
            for i, j in ff.each():
                if ff.get(i, j)[3] and i % 2 == 0:
                    ff.set(i, j, PAPER.at(3.2))  # card after card
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
PAGES = [("item.book.page_turn", 0.8, 0.7)]
MONSTERS = [
    K.Monster("ink_drowned", "The Blotter", blotter, paint_blotter, kind="strider", box=(0.9, 1.7), sunk=("sheet",),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(1.8, 3.0), crouch=0.5, swing=0.3, bob=0.7, roll=0.06),
                                "body": K.body(headLevel=0.3, twist=0.3, land=1.3, shift=0.6)},
              life=dict(STILL, temperament="monster", idles={"look": 2, "settle": 2}, mass=1.8, arms=0.8, springHz=1.6, springGain=1.3),
              voice={"ambient": [("entity.drowned.ambient", 0.6, 0.6)] + PAGES, "hurt": [("entity.drowned.hurt", 0.7, 0.6), ("block.chiseled_bookshelf.hit", 0.8, 0.8)],
                     "death": [("entity.drowned.death", 0.8, 0.6), ("block.chiseled_bookshelf.break", 0.9, 0.7)], "step": [("block.chiseled_bookshelf.step", 0.4, 0.7)]}),
    K.Monster("page_wraith", "The Paper Crane", crane, paint_crane, kind="floater", box=(0.6, 0.6), lift=8,
              motion={"fly": K.flight(fast=5, bank=0.5, tilt=0.35, climb=0.5)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=2.6),
              voice={"ambient": [("item.book.page_turn", 0.7, 1.3), ("entity.phantom.flap", 0.3, 1.8)], "hurt": [("item.book.page_turn", 1.0, 1.6), ("entity.vex.hurt", 0.4, 1.4)],
                     "death": [("item.book.put", 0.9, 1.2), ("entity.vex.death", 0.4, 1.4)]}),
    K.Monster("archive_eye", "The Reading Glass", reading_glass, paint_reading_glass, kind="floater", box=(0.7, 1.3), lift=4, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.2, tilt=0.2, climb=0.4)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=1.4, springGain=1.4, headSpeed=3.0),
              voice={"ambient": [("item.spyglass.use", 0.7, 0.7), ("block.amethyst_block.chime", 0.3, 0.7)], "hurt": [("block.glass.hit", 0.9, 0.9), ("block.copper.hit", 0.5, 1.2)],
                     "death": [("block.glass.break", 0.9, 0.9), ("item.spyglass.stop_using", 0.8, 0.6)]}),
    K.Monster("salt_stray", "The Quillback", quillback, paint_quillback, kind="runner", box=(0.9, 1.0), sunk=("shaft", "vane", "thigh", "shinb"),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.68, 0.44), lift=(1.8, 3.0), crouch=0.5, swing=0.24, bob=0.4, roll=0.04,
                                               lag=-0.25, lead=0.12, rear=1.0),
                                "body": K.body(headLevel=0.4, twist=0.2, slope=0.85, land=1.2, shift=0.5, nod=0.05)},
              life=dict(STILL, temperament="beast", idles={"look": 3, "sniff": 2, "shake": 1, "settle": 1}, mass=1.4, springHz=2.4, springGain=1.4),
              voice={"ambient": [("entity.armadillo.ambient", 0.7, 0.7), ("entity.armadillo.brush", 0.5, 0.8)], "hurt": [("entity.armadillo.hurt", 0.8, 0.7)],
                     "death": [("entity.armadillo.death", 0.9, 0.7)], "step": [("entity.armadillo.step", 0.4, 0.8)]}),
    K.Monster("drowned_scribe", "The Scribe", scribe, paint_scribe, kind="crawler", box=(1.0, 1.9), sunk=("pen", "vanes"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.6), duty=(0.7, 0.5), lift=(2.6, 4.2), crouch=0.6, swing=0.3, bob=0.4, roll=0.03, speed=0.6),
                                "body": K.body(headLevel=0.0, twist=0.1, slope=0.7, land=1.2, shift=0.7)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 2}, arms=0.0, springHz=1.8, springGain=1.4, mass=1.4),
              voice={"ambient": [("item.ink_sac.use", 0.7, 0.7), ("ui.cartography_table.take_result", 0.6, 0.7)], "hurt": [("block.glass.hit", 0.8, 0.6), ("item.ink_sac.use", 0.8, 1.2)],
                     "death": [("block.glass.break", 0.9, 0.6), ("entity.squid.squirt", 0.8, 0.8)], "step": [("block.bamboo.step", 0.35, 1.2)]}),
    K.Monster("the_curator", "The Curator", curator, paint_curator, kind="floater", box=(1.2, 1.9), lift=1, grounds=False,
              motion={"fly": K.flight(fast=3, bank=0.12, tilt=0.14, climb=0.3)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 1}, springHz=1.1, springDamping=0.14, springGain=1.5, mass=3.0),
              voice={"ambient": [("block.barrel.open", 0.6, 0.6), ("block.barrel.close", 0.6, 0.6)] + PAGES, "hurt": [("block.chiseled_bookshelf.hit", 1.0, 0.6), ("block.wood.hit", 0.9, 0.6)],
                     "death": [("block.chiseled_bookshelf.break", 1.0, 0.5), ("block.barrel.close", 1.0, 0.5)]}),
]
