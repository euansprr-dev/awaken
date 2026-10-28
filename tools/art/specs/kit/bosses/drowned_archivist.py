"""The Drowned Archivist (SPEC §34, §35.11): the Lectern, the Drowned gate's Lord. A Caster.

"Every book here was written by someone who drowned." A tall drowned scholar grown into his own reading stand. From
the waist down he is a heavy lectern of black, waterlogged oak on a plinth crusted with barnacles, and his sodden robe
runs down into it. A great book lies open on the desk before him, chained to it the way books were chained in the old
libraries, with a ship's anchor hanging off the chain for a weight; the sea pours off the book's pages over the desk's
edge and spreads across the floor. He stoops over it in a peaked hood with weed in its brim, a bloated blue-grey face
with no eyes in it, only two drowned green pricks of light deep in the pits, his left hand's long fingers flat on the
page and his right holding a quill as long as a spear, its nib dripping ink. He has no feet: the lectern drifts on the
water it pours, rocking like a moored boat. His light story is the sea-green of that water: the falls off the book,
the words that swim on its pages, the pool and the pricks in his eyes.

Moves: Quill Thrust (the quill drawn back over his shoulder and driven down at you over the desk) and a backhand rake
with it (LIGHTs), the Shutting (both hands take the covers, lift the book wide open, and slam it shut: HEAVY), Tidal
Page (he tears a page up off the book and flings it at you, the sea surging off the desk after it: HEAVY), Ink Volley
(the quill dipped in the book and flicked at you three times), Loose Pages (the book riffles and every leaf flies up),
Ink Cloud (he bows over the book with his hands spread on it and breathes out ink), Undertow (an open hand reached at
you and drawn back to his chest, closing), the Flood (the signature: he straightens to his full height, arms wide and
face to the roof, and the book pours), a turn of his top half right round on the lectern to thrust at whoever is
behind him, and his intro: reading, he turns a page, and lifts his face to you.
"""
import math

from kit import core, ik
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES, p_strokes

ID = "drowned_archivist"
SCALE = 1.9  # the hitbox: size 0.9 x 2.4 scaled by 1.9, 1.7 x 4.6 blocks; the body is modelled at true size
SIDES_ = (("l", 1), ("r", -1))
DESK_UP, DESK_Z, DESK_TILT = 33, -6, -14  # the desk's back edge over the floor and behind the middle; its front lifts
TORSO_UP, STOOP = 39, 28  # where he leaves the lectern, and how far he stoops over the book
FALLS = ((-9, 3, 36), (1, 5, 37), (9, 2, 35))  # the sea off the desk's front edge: x, width, length
LEAVES = 3  # loose leaves standing up off each side of the open book
VOLLEY_FROM = (-1.1, 3.0, 3.1)  # where the ink leaves him: the quill's nib at the top of a flick (blocks: x, up, forward)
LEFT_ON_PAGE = (6.0, -4.0, 1.0)  # the left hand's rest on the book (the book's own model space: x, y down, z)


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed as the silhouette round placed it: x, height up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d):
    """A cube as the round drew it: centred on (cx, cz), from y0 up to y0 + h over the bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d)


def body():
    r = R.Rig(ID, tex=(512, 512), translucent=True)
    c = r.bone("core", (0, 24, 2))
    # the lectern: a barnacled plinth, the carved post under the robe, the robe running down into it
    _box(c, "plinth", 0, 0, 0, 26, 4, 20)
    _box(c, "plinth2", 0, 4, 0, 20, 3, 16)
    for k, (x, z, w) in enumerate(((9, -7, 3), (-10, -5, 2), (-6, 7, 3), (10, 6, 2), (2, -9, 2))):
        _box(c, f"barnacle{k}", x, 4 if abs(x) < 10 else 3, z, w, 2, w)
    _box(c, "column", 0, 7, 0, 12, 16, 11)  # the lectern's carved column, the robe hanging over its top
    _box(c, "collar", 0, 17, 0, 15, 3, 13)
    _box(c, "post", 0, 7, -5.9, 6, 10, 1)
    _box(c, "robe0", 0, 22, 0, 17, 8, 14)
    _box(c, "robe1", 0, 30, 0, 19, 9, 15)
    sk = _bone(c, "skirt", 0, 22, 0)  # its hem, ragged, hanging down over the column
    for k, (x, z, w, h, d) in enumerate(((-6, -7.5, 3, 5, 1), (-1, -7.5, 2, 7, 1), (4, -7.5, 3, 4, 1), (8.5, -2, 1, 6, 3), (-8.5, 1, 1, 5, 3),
                                         (2, 7.5, 3, 6, 1), (-4, 7.5, 2, 4, 1))):
        _box(sk, f"rag{k}", x, -h, z, w, h, d)
    _box(c, "pool", 0, 0, -17, 34, 1, 16)  # the sea spread on the floor before him
    # the desk, jutting out before him at his chest with its front edge lifted; its bracket down into the robe
    dk = _bone(c, "desk", 0, DESK_UP, DESK_Z, (DESK_TILT, 0, 0))
    _box(dk, "desk", 0, -2, -8, 30, 2, 16)
    _box(dk, "lip", 0, 0, -15.5, 30, 2, 1)
    _box(dk, "bracket", 0, -9, -3, 6, 7, 6)
    # the book open on it: two halves hinged at the spine, each a board and its block of pages, loose leaves standing up
    bk = _bone(dk, "book", 0, 0, -8)
    for nm, sg in SIDES_:
        pg = _bone(bk, "page_" + nm, sg * 0.5, 0, 0, (0, 0, -sg * 8))
        _box(pg, "cover_" + nm, sg * 7, 0, 0, 14, 1, 13)
        _box(pg, "pages_" + nm, sg * 6.5, 1, 0, 13, 2, 12)
        for k in range(LEAVES):
            lf = _bone(pg, f"leaf_{nm}{k}", sg * 0.5, 3, 0, (0, 0, -sg * (10 + 15 * k)))
            _box(lf, f"leaf_{nm}{k}", sg * 5.5, 0, 0, 11, 1, 11)
    # the sea pouring off the book over the desk's front edge, hanging plumb
    for k, (x, w, ln) in enumerate(FALLS):
        f = _bone(dk, f"fall{k}", x, 0.5, -16, (-DESK_TILT, 0, 0))
        _box(f, f"fall{k}", 0, -ln, 0, w, ln, 1)
    # the book's chain, from its spine over the desk's left edge, and the anchor hanging off it for a weight
    ch = _bone(dk, "chain", 15.5, 0, -6, (-DESK_TILT, 0, 0))
    for k in range(3):
        _box(ch, f"link{k}", 0, -4 * k - 4, 0, 1 if k % 2 else 2, 4, 2 if k % 2 else 1)
    an = _bone(ch, "anchor", 0, -12, 0)
    _box(an, "anchor_ring", 0, -2, 0, 3, 2, 1)
    _box(an, "stock", 0, -4, 0, 9, 2, 2)
    _box(an, "shank", 0, -15, 0, 2, 11, 2)
    _box(an, "crown", 0, -17, 0, 10, 2, 2)
    for nm, sg in SIDES_:
        _box(an, "fluke_" + nm, sg * 5, -16, 0, 2, 4, 2)
        _box(an, "palm_" + nm, sg * 5.5, -13, 0, 3, 2, 2)
    # him: stooped over the book from the waist, a sodden mantle, a stole down his front
    t = _bone(c, "torso", 0, TORSO_UP, 1, (STOOP, 0, 0))
    _box(t, "waist", 0, -2, 0, 17, 6, 14)
    _box(t, "chest", 0, 4, 0, 20, 11, 13)
    _box(t, "mantle", 0, 13, 0.5, 25, 7, 15)
    _box(t, "stole", 0, 1, -6.9, 6, 13, 1)
    _box(t, "clasp", 0, 13, -7.6, 4, 3, 1)
    for nm, sg in SIDES_:  # weed draped over his shoulders, hanging down his front and back
        _box(t, "drape_" + nm, sg * 8, 6, -7.6, 3, 14, 1)
        _box(t, "drape_top_" + nm, sg * 8, 19.5, 0.5, 3, 1, 15)
        _box(t, "drape_back_" + nm, sg * 8, 9, 8.6, 3, 11, 1)
    nk = _bone(t, "neck", 0, 19, -1.5, (-8, 0, 0))
    _box(nk, "neck", 0, 0, 0, 6, 3, 6)
    hd = _bone(nk, "head", 0, 2, -1, (2, 0, 0))
    # the hood: a deep cowl behind the face, its brim framing it, the peak flopping back
    _box(hd, "hood", 0, 0, 2, 14, 13, 12)
    _box(hd, "brim_top", 0, 11, -6, 14, 3, 4)
    for nm, sg in SIDES_:
        _box(hd, "brim_" + nm, sg * 6, 0, -6, 2, 11, 4)
    _box(hd, "hood_peak", 0, 13, 3, 9, 4, 9)
    tp = _bone(hd, "hood_tip", 0, 17, 5, (-30, 0, 0))
    _box(tp, "hood_tip", 0, 0, 0, 4, 6, 4)
    # the drowned face sunk in it, bloated, eyeless; a slack jaw
    _box(hd, "face", 0, 2, -5.5, 9, 9, 3)
    jw = _bone(hd, "jaw", 0, 3, -4.5, (4, 0, 0))
    _box(jw, "jaw", 0, -3, -1, 7, 3, 3)
    for nm, sg in SIDES_:  # weed hanging from the brim
        w = _bone(hd, "weed_" + nm, sg * 6, 0.5, -6.5)
        _box(w, "weed_" + nm, 0, -7, 0, 1, 7, 1)
        w2 = _bone(w, "weed2_" + nm, 0, -7, 0)
        _box(w2, "weed2_" + nm, 0, -5, 0, 1, 5, 1)
    # long arms in wide sodden sleeves, drowned hands with long fingers
    for nm, sg in SIDES_:
        ar = _bone(t, "arm_" + nm, sg * 11.5, 15, 0)
        _box(ar, "arm_" + nm, 0, -14, 0, 5, 14, 5)
        fa = _bone(ar, "forearm_" + nm, 0, -14, 0)
        _box(fa, "forearm_" + nm, 0, -12, 0, 5, 12, 5)
        cf = _bone(fa, "cuff_" + nm, 0, -7, 2)
        _box(cf, "cuff_" + nm, 0, -7, 0, 8, 7, 6)
        kl = _bone(cf, "kelp_" + nm, sg * 2.5, -7, 1.5)
        _box(kl, "kelp_" + nm, 0, -7, 0, 2, 7, 1)
        hn = _bone(fa, "hand_" + nm, 0, -12, 0)
        _box(hn, "hand_" + nm, 0, -4, 0, 4, 4, 3)
        for k, (x, ln) in enumerate(((-1.3, 5), (0, 6), (1.3, 5))):
            _box(hn, f"finger_{nm}{k}", x, -4 - ln, -0.5, 1, ln, 1)
        _box(hn, "thumb_" + nm, -sg * 2.2, -6, 0.5, 1, 4, 1)
    # the quill, as long as a spear: a pale shaft, a dark sea-bird's feather behind his hand, a brass nib before it
    q = _bone(r.by_name["hand_r"], "quill", 0, -3, 0)
    _box(q, "shaft", 0, -16, 0, 1, 40, 1)
    _box(q, "nib", 0, -22, 0, 2, 6, 1)
    _box(q, "nib_tip", 0, -24, 0, 1, 2, 1)
    _box(q, "vane0", 0, 5, 0, 1, 16, 7)
    _box(q, "vane1", 0, 21, 0, 1, 6, 4)
    # the rest: the left hand flat on the page, the right holding the quill across him, nib down before the desk
    fr = core.frames(r, {})
    ik.rest_limb(r, "arm_l", "forearm_l", "hand_l", ik.at(fr, "book", LEFT_ON_PAGE), (1, -0.6, 0.6))
    ik.rest_limb(r, "arm_r", "forearm_r", "hand_r", (-15, 42, -14), (-1, 0.2, 0.8))
    fr = core.frames(r, {})
    r.by_name["hand_l"].rot = ik.orient(r, fr, "hand_l", (0, 0.25, -1), (1, 0, 0))
    r.by_name["hand_r"].rot = ik.orient(r, fr, "hand_r", (0, 1, -0.2), (0, 0, -1))
    fr = core.frames(r, {})
    r.by_name["quill"].rot = ik.orient(r, fr, "quill", (0.25, 0.75, -0.6), (1, 0, 0))
    r.look = [("head", 0.5, 0.4), ("torso", 0.2, 0.1)]
    r.shadow = 1.1
    # secondary parts (SPEC §35.10): the falls and the weed trail, the sleeves and the anchor swing, the hood flops
    r.springs = [(f"fall{k}", 0.25, 1.6, 0.35) for k in range(len(FALLS))] \
        + [("chain", 0.5, 1.2, 0.2), ("anchor", 0.4, 1.4, 0.25), ("hood_tip", 0.4, 2.0, 0.3)] \
        + [(f"cuff_{s}", 0.35, 1.8, 0.3) for s in "lr"] + [(f"kelp_{s}", 0.6, 1.6, 0.25) for s in "lr"] \
        + [(f"weed_{s}", 0.4, 2.0, 0.25) for s in "lr"] + [(f"weed2_{s}", 0.6, 1.8, 0.25) for s in "lr"] \
        + [(f"leaf_{s}{k}", 0.15, 3.0, 0.4) for s in "lr" for k in range(LEAVES)] + [("skirt", 0.3, 1.8, 0.3)]
    r.guard = ["anchor", "cuff_l", "cuff_r", "quill"]
    return r



def paint(r, P_):
    """Values (SPEC §35.4): the sea-green light brightest (the falls, the pool, the words swimming on the pages, the
    pricks in his eyes), then the pale sodden pages and the quill's shaft, the drowned blue-grey skin and the tarnished
    brass, the black oak and the robe darkest, wet and streaked."""
    from kit import parts
    of = lambda *ps: parts.cubes_of(r, *ps)
    oak = Ramp("#090807", "#110f0c", "#191612", "#221e18", "#2d2820", "#3a3329", "#494034")
    cloth = Ramp("#04060a", "#090d12", "#0f151c", "#161e27", "#1f2a35", "#2a3744", "#384756")
    skin = Ramp("#222b30", "#2f3a41", "#3d4a52", "#4e5e66", "#62747c", "#7a8d94", "#94a6aa")
    paper = Ramp("#4a4636", "#5e5a46", "#767058", "#8e886c", "#a69e80", "#bcb496", "#d2caac")
    leather = Ramp("#170d07", "#22140c", "#2e1c12", "#3c2618", "#4c3220", "#5e3e2a")
    brass = Ramp("#2a200e", "#3e3014", "#56421c", "#6e5626", "#886c34", "#a28644")
    weed = Ramp("#0a1408", "#11200d", "#192e12", "#223e17", "#2e501d", "#3c6426")
    shell = Ramp("#3c3c36", "#56564e", "#727068", "#8e8c82", "#aaa89c", "#c6c4b6")
    sea = ("#0a4e50", "#127470", "#1e9a8c", "#3cc2ac", "#7ee6cc", "#c4fbe8")
    verd = "#3e7a68"
    # the black oak: grain running along each board, green with weed low down, wet where the sea runs over it
    grain = lambda f, i, j: (-0.9 if (i * 5 + (j // 3) * 3) % 11 == 0 else 0.25 if (i + j * 3) % 13 == 0 else 0)
    for c in ("plinth", "plinth2", "post", "column", "collar", "desk", "lip", "bracket"):
        P_.paint(c, oak, 3.2, grain, grad=0.8, rim=0.8, ao=0.9, noise=0.35)
    for fname in SIDES:
        for c in ("plinth", "plinth2"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if R._h(c + fname, i * 7 + j) % 3 == 0:
                    f.set(i, j, weed.at(2.4 + (i % 3) * 0.3))
    f = P_.face("post", "north")  # the post's carving: a channel down it between two beads
    for i, j in f.each():
        if i in (1, f.w - 2):
            f.set(i, j, oak.at(5.0))
        elif 2 <= i <= f.w - 3:
            f.set(i, j, oak.at(1.2 if j % 5 else 3.6))
    for c in of("barnacle"):
        P_.paint(c, shell, 3.6, rim=1.0, noise=0.4)
        f = P_.face(c, "top")
        f.set(f.w // 2, f.h // 2, shell.at(0))
    # the robe and the hood: black cloth soaked through, streaked with wet light, its hems stained with weed
    wet = lambda f, i, j: (1.1 if f.name in SIDES and R._h(f.cube.name + f.name, i) % 5 == 0 and j % 6 < 4 else
                           -0.5 if f.name in SIDES and (i + j) % 9 == 0 else 0)
    for c in of("robe", "rag", "hood", "brim_", "arm_", "forearm_", "cuff_") + ["waist", "chest", "mantle", "hood_peak", "hood_tip"]:
        P_.paint(c, cloth, 3.0, wet, grad=1.1, rim=0.7, ao=0.9, noise=0.3)
    for c in of("robe0", "rag", "cuff_"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                f.set(i, f.h - 1, weed.at(1.6 + (i % 2) * 0.6))
    for c, rows in (("robe0", (7,)), ("robe1", (8,)), ("mantle", (6,)), ("brim_top", (0,)), ("brim_l", ()), ("brim_r", ()), ("cuff_l", (6,)), ("cuff_r", (6,))):
        for fname in SIDES:  # a worn brass thread round the hems, half of it rotted away
            f = P_.face(c, fname)
            for i, j in f.each():
                edge = j in rows or (c.startswith("brim_") and c != "brim_top" and fname == "north")
                if edge and R._h(c + fname, i * 3 + j) % 4:
                    f.set(i, j, brass.at(3.0 + (i % 2) * 0.8))
    P_.paint("stole", leather, 3.0, lambda f, i, j: (1.8 if f.name == "north" and (i in (1, f.w - 2) or j % 4 == 0) else 0))
    for fname in ("north",):
        f = P_.face("stole", fname)
        for i, j in f.each():
            if i in (1, f.w - 2) or j % 4 == 0:
                f.set(i, j, brass.at(3.4))
    P_.paint("clasp", brass, 3.4, rim=1.0)
    # the drowned skin: blue-grey, bloated and mottled
    mottle = lambda f, i, j: (-0.8 if R._h(f.cube.name + f.name, i * 13 + j * 7) % 7 == 0 else 0.4 if (i + 2 * j) % 11 == 0 else 0)
    for c in ["face", "jaw", "neck"] + of("hand_", "finger_", "thumb_"):
        P_.paint(c, skin, 3.4, mottle, grad=0.7, rim=0.6, noise=0.3)
    # no eyes: two deep pits, a drowned green prick of light far in each; a dark slit of a mouth
    fc = P_.face("face", "north")
    gc = P_.face("face", "north", glow=True)
    for sg in (1, -1):
        x0 = fc.w // 2 + (1 if sg > 0 else -3)
        for i in range(x0, x0 + 2):
            for j in range(2, 4):
                fc.set(i, j, skin.at(0))
        fc.set(x0 + (0 if sg > 0 else 1), 4, skin.at(1))
        gc.set(x0 + (0 if sg > 0 else 1), 3, hexc(sea[4], 230))
    for i in range(fc.w):
        fc.set(i, 0, cloth.at(0))  # the hood's shadow across the brow
    jt = P_.face("jaw", "north")
    for i in range(1, jt.w - 1):
        jt.set(i, 0, skin.at(0))
    # the book: leather boards with brass corners, pages of sodden paper written over in lines; some words swim green
    for c in of("cover_"):
        P_.paint(c, leather, 3.0, rim=0.8, noise=0.3)
        for fname in ("top", "bottom") + SIDES:
            f = P_.face(c, fname)
            for i, j in ((0, 0), (f.w - 1, 0), (0, f.h - 1), (f.w - 1, f.h - 1)):
                f.set(i, j, brass.at(4.0))

    def write(c, fname, light=True):
        f = P_.face(c, fname)
        g = P_.face(c, fname, glow=True)
        for i, j in f.each():
            if i in (0, f.w - 1) or j in (0, f.h - 1):
                continue
            if j % 2 == 0 and R._h(c + fname, i * 3 + j * 17) % 4:
                f.set(i, j, paper.at(1.0))
                if light and R._h(c + fname + "g", i * 5 + j) % 6 == 0:
                    g.set(i, j, hexc(sea[3], 200))
    for c in of("pages_", "leaf_"):
        P_.paint(c, paper, 4.0, lambda f, i, j: (-0.6 if (i + j) % 7 == 0 else 0), grad=0.6, rim=0.5, noise=0.5)
        write(c, "top")
        if c.startswith("leaf_"):
            write(c, "bottom", light=False)
    # the sea: see-through green, streaked as it falls, lit from inside; on the floor a pool with rings of foam
    for k in range(len(FALLS)):
        c = f"fall{k}"
        for fname in ("top", "bottom") + SIDES:
            f = P_.face(c, fname)
            g = P_.face(c, fname, glow=True)
            for i, j in f.each():
                # running water: bright threads down it, broken here and there, foaming white where it lands
                thread = (i + R._h(c + fname, j // 5)) % 2 == 0 and (j + 3 * i + R._h(c, i)) % 9 < 6
                foot = j >= f.h - 2
                f.set(i, j, hexc(sea[5] if foot else sea[3] if thread else sea[1], 200 if foot else 150 if thread else 80))
                if thread or foot:
                    g.set(i, j, hexc(sea[5] if foot else sea[4], 170 if foot else 120))
    for fname in ("top", "bottom") + SIDES:
        f = P_.face("pool", fname)
        g = P_.face("pool", fname, glow=True)
        for i, j in f.each():
            d = math.hypot((i - f.w / 2) / 1.6, (j - f.h * 0.15))
            ring = fname == "top" and int(d) % 4 == 0
            f.set(i, j, hexc(sea[4] if ring else sea[1], 160 if ring else 110))
            if ring:
                g.set(i, j, hexc(sea[4], 110))
    # the chain and the anchor: brass gone dark, green with verdigris
    for c in of("link", "anchor_ring", "stock", "shank", "crown", "fluke_", "palm_"):
        P_.paint(c, brass, 2.8, lambda f, i, j: hexc(verd) if R._h(f.cube.name + f.name, i * 11 + j * 5) % 5 == 0 else 0, rim=1.0, noise=0.4)
    # the quill: a pale shaft, a dark sea-bird's feather with an oily green sheen on its edge, a brass nib, black ink
    P_.paint("shaft", Ramp("#8c8674", "#a8a290", "#c4beaa", "#ddd8c4"), 2.2)
    for c in ("vane0", "vane1"):  # a gull's flight feather, pale with dark bars, its tip dipped in the sea
        P_.paint(c, Ramp("#4e5250", "#6e7270", "#90948e", "#b2b4ac", "#d0d0c6", "#e8e6dc"), 4.0,
                 lambda f, i, j: (-2.6 if (j + i // 2) % 5 == 0 else 0.3 if (i + j) % 3 == 0 else 0) if f.name in ("east", "west") else 0)
    for fname in ("east", "west"):
        edge_glow(P_, "vane1", fname, sea[3], rows=2, alpha=170, side="top")
    P_.paint("nib", brass, 3.8, rim=1.0)
    P_.paint("nib_tip", Ramp("#020304", "#06080a"), 1.0)
    for c in ("weed_l", "weed_r", "weed2_l", "weed2_r", "kelp_l", "kelp_r") + tuple(of("drape")):
        P_.paint(c, weed, 3.0, lambda f, i, j: (0.8 if j % 3 == 0 else 0), noise=0.4)
    return P_.skin, P_.glow




# where his parts rest: the quill's lie (a model direction: x, y down, z), the poles his elbows point along
QUILL_REST = (0.25, 0.75, -0.6)
POLE = {"l": (1, -0.6, 0.6), "r": (-1, 0.2, 0.8)}
PAGE_DIR = ((0, 0.25, -1), (1, 0, 0))  # the left hand laid flat on the page, fingers forward


def _add(pose, b, rot=(0, 0, 0)):
    v = pose.get(b, (0, 0, 0))
    if isinstance(v, dict):
        pose[b] = {"rot": tuple(x + y for x, y in zip(v.get("rot", (0, 0, 0)), rot)), "pos": v.get("pos", (0, 0, 0))}
    else:
        pose[b] = tuple(x + y for x, y in zip(v, rot))
    return pose


def _book(open_l=0.0, open_r=0.0, leaves_l=(0, 0, 0), leaves_r=(0, 0, 0)):
    """The book: each half lifted up off the desk (+, degrees, past 90 it closes over the other half), each leaf lifted
    up off its half (+)."""
    p = {"page_l": (0, 0, -open_l), "page_r": (0, 0, open_r)}
    for k in range(LEAVES):
        p[f"leaf_l{k}"] = (0, 0, -leaves_l[k])
        p[f"leaf_r{k}"] = (0, 0, leaves_r[k])
    return p


def _riffle(u, amp=30.0, lift=0.0):
    """The leaves of both halves flapping, each a beat behind the last (u: a phase in turns)."""
    f = lambda k, off: lift + amp * (0.5 + 0.5 * math.sin(2 * math.pi * (u - 0.18 * k) + off))
    return _book(lift * 0.3, lift * 0.3, tuple(f(k, 0) for k in range(LEAVES)), tuple(f(k, math.pi) for k in range(LEAVES)))


def _falls(pose, swing=0.0, spread=0.0, ripple=0.0, t=0.0):
    """The sea off the desk: flung out forward (+), fanned apart (+), rippling."""
    for k, (x, _, _) in enumerate(FALLS):
        _add(pose, f"fall{k}", (-swing - ripple * math.sin(2 * math.pi * t + 1.7 * k), 0, -spread * x / 10))
    return pose


def _pose(r, rock=(0, 0, 0), at=(0, 0, 0), bend=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), jaw=0.0, left="page", right=None,
          quill=None, lhand=None, rhand=None, book=None, falls=None, page_at=None):
    """A whole-body key. The lectern rocks and drifts (rock, at: x, up, z); he bends at the waist (bend) and the neck and
    head; the jaw drops. The hands go where they are sent: left "page" lays it on the book where it rests (or at page_at,
    a point in the book's model space), a (x, up, z) point puts the wrist there, None leaves the arm as the torso carries
    it; the right likewise. quill: the way the quill points (a model direction), lhand/rhand: (ydir, xdir) for a hand."""
    p = {"core": {"rot": rock, "pos": at}, "torso": bend, "neck": neck, "head": head, "jaw": (jaw, 0, 0)}
    p.update(book or _book())
    if falls:
        _falls(p, *falls)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        if tgt == "page":
            q = core.point(fr, "book", page_at or LEFT_ON_PAGE)
        elif tgt[0] == "on":
            q = core.point(fr, tgt[1], tgt[2])
        else:
            q = ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    if left == "page" and lhand is None:
        lhand = PAGE_DIR
    if lhand or rhand:
        fr = core.frames(r, core.as_sample(p))
        for side, hd in (("l", lhand), ("r", rhand)):
            if hd:
                p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *hd)
    if quill:
        fr = core.frames(r, core.as_sample(p))
        p["quill"] = ik.orient(r, fr, "quill", quill, (1, 0, 0) if abs(quill[0]) < 0.8 else (0, 0, -1))
    return p


def _mix(a, b, f):
    """Between two keys (the bones of either), f of the way from a to b."""
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra = va["rot"] if isinstance(va, dict) else va
        rb = vb["rot"] if isinstance(vb, dict) else vb
        rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def _smooth(u):
    return u * u * (3 - 2 * u)


def clips(r):
    two = 2 * math.pi
    # idle: moored, he rocks on the swell while he reads, his face following the lines from his left to his right and
    # back, a finger tracing down the page; the leaves stir in the current, the quill sways, the falls ripple
    keys, n, L = [], 48, 4.0
    for k in range(n + 1):
        p = k / n
        u = (p * 2) % 1.0
        line = _smooth(u / 0.8) if u < 0.8 else 1 - _smooth((u - 0.8) / 0.2)
        stir = tuple(5 * math.sin(two * (p * 2 - 0.2 * i)) + 3 for i in range(LEAVES))
        stir2 = tuple(5 * math.sin(two * (p * 2 - 0.2 * i) + 2.0) + 3 for i in range(LEAVES))
        pose = _pose(r, rock=(1.2 * math.sin(two * p * 2), 0, 1.4 * math.sin(two * p + 1.3)), at=(0, 0.5 * math.sin(two * p * 2 + 0.6), 0),
                     bend=(1.2 * math.sin(two * p * 2 - 0.8), -2 + 4 * line, 0), neck=(1 + 2 * line, 0, 0), head=(2 * line, -8 + 16 * line, 1.5 * math.sin(two * p)),
                     jaw=2 + 2 * math.sin(two * p * 2), page_at=(9 - 6 * line, -5.0, 2 - 2 * line),
                     right=(-15 + 0.8 * math.sin(two * p * 2), 42 + 1.2 * math.sin(two * p * 2 + 0.4), -14), rhand=None,
                     quill=(0.25 + 0.05 * math.sin(two * p * 2), 0.75, -0.6), book=_book(0, 0, stir, stir2), falls=(0, 0, 3, p * 4))
        keys.append((L * p, pose))
    keyed(r, "idle", L, keys, loop=True)
    # drifting: the lectern glides on its own water, leaning into the way it goes and rising on the swell; he holds the
    # book down with his left hand and the quill up out of the way
    for name, L, lean, bob, stride in (("walk", 2.0, 3.0, 1.0, 2.0), ("run", 1.2, 6.0, 1.6, 3.0)):
        wk, wn = [], 16
        for k in range(wn + 1):
            p = k / wn
            sw = math.sin(two * p)
            pose = _pose(r, rock=(lean + 1.5 * sw, 0, 1.6 * math.sin(two * p + 1.0)), at=(0, bob * (0.5 + 0.5 * math.sin(two * p * 2)), 0),
                         bend=(2 + lean * 0.4 - 1.2 * sw, 0, -1.2 * math.sin(two * p + 1.0)), neck=(-2, 0, 0), head=(-4 - 2 * sw, 0, 0),
                         right=(-16, 46, -10), quill=(0.3, 0.55, -0.75), book=_riffle(p * 2, 8 + lean, 2), falls=(-lean * 2, 0, 4, p * 2))
            wk.append((L * p, pose))
        keyed(r, name, L, wk, loop=True)
    r.walk_stride, r.run_stride = 2.0, 3.0
    rest = _pose(r)
    jolt = _pose(r, rock=(-3, 0, 0), bend=(-10, 0, 0), neck=(-6, 0, 0), head=(-14, 0, 6), jaw=12, right=(-20, 52, -2), quill=(0.3, -0.2, -0.9),
                 book=_book(0, 0, (16, 24, 30), (12, 20, 26)), falls=(-14, 4))
    keyed(r, "hurt", 0.5, [(0, rest), (0.07, jolt), (0.3, _pose(r, bend=(3, 0, 0), head=(4, 0, 0))), (0.5, rest)])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):
        side = _pose(r, rock=(-1, 0, -3 * sg), bend=(-5, -16 * sg, -8 * sg), head=(-8, -20 * sg, 10 * sg), jaw=10, right=(-20, 50, -4),
                     quill=(0.4, 0.0, -0.9), book=_book(0, 0, (10, 16, 22), (10, 16, 22)), falls=(-6, 2))
        keyed(r, nm, 0.55, [(0, rest), (0.08, side), (0.3, _pose(r, bend=(2, 4 * sg, 2 * sg))), (0.55, rest)])
    # broken: he slumps over the book, his left hand slid off it and hanging, leaning on the quill's nib on the floor
    slump = _pose(r, rock=(2, 0, 0), bend=(20, 0, 4), neck=(10, 0, 0), head=(16, 0, 8), jaw=14, left=(16, 32, -8), lhand=((0.1, 1, -0.2), (1, 0, 0)),
                  right=(-16, 32, -16), quill=(0.1, 0.95, -0.3), book=_book(0, 0, (2, 4, 6), (2, 4, 6)))
    half = _mix(rest, slump, 0.45)
    keyed(r, "kneel", 0.6, [(0, rest), (0.22, half), (0.6, slump)])
    keyed(r, "rise", 0.8, [(0, slump), (0.4, _pose(r, bend=(-6, 0, 0), head=(-8, 0, 0), right=(-18, 46, -12), quill=(0.3, 0.5, -0.8))), (0.8, rest)])
    # death: the sea goes out of him, his head thrown back and every limb flung out; he slumps on the book, it shuts
    # under him, and the lectern goes down into the water he made, tilting, the hood the last of him
    gasp = _pose(r, rock=(-4, 0, 0), at=(0, 2, 0), bend=(-18, 0, 0), neck=(-12, 0, 0), head=(-30, 0, 8), jaw=34, left=(22, 54, -8), lhand=((0.6, -0.6, -0.4), (0, 0, -1)),
                 right=(-22, 58, -4), quill=(-0.3, -0.9, -0.2), book=_book(12, 12, (40, 60, 80), (40, 60, 80)), falls=(-30, 8))
    over = _pose(r, rock=(3, 0, 2), bend=(30, 0, 6), neck=(12, 0, 0), head=(22, 0, 10), jaw=20, left=(16, 30, -10), lhand=((0.1, 1, -0.2), (1, 0, 0)),
                 right=(-17, 30, -10), quill=(0.2, 0.9, 0.3), book=_book(0, 0, (0, 2, 4), (0, 2, 4)), falls=(4, 0))
    shut = dict(over)
    shut.update(_book(164, 0, (-10, -25, -40), (0, 2, 4)))
    def sunk(d, tilt):
        q = dict(shut)
        q["core"] = {"rot": (3 + tilt, 0, 2 + tilt * 0.6), "pos": (0, -d, 0)}
        return q
    keyed(r, "death", 3.2, [(0, rest), (0.3, gasp), (0.75, gasp), (1.3, over), (1.7, shut), (2.2, sunk(10, 2)), (2.8, sunk(30, 6)), (3.2, sunk(44, 8))])
    mv = [thrust(r), rake(r), shutting(r), tidal(r), volley(r), pages(r), cloud(r), undertow(r), flood(r), turn(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
    return mv


def _back(r, end, settle=None):
    """A recovery eased in two steps from where the move ends back toward rest (or settle), never all at once."""
    home = settle or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.8))]


def _on(r, end, f=0.1):
    """The follow-through: the blow carried on a little past where it ends, away from rest."""
    return _mix(end, _pose(r), -f)


NIB = (0, 24, 0)  # the quill's point, on its bone
COVER_EDGE = ("on", "page_l", (13, -1, 0))  # the left board's outer edge, where his hand takes it


def thrust(r):
    """Quill Thrust: he draws the quill back and up over his right shoulder, its nib on you, his body turning away with
    it; then leans in over the desk and drives it down at you like a spear. His left hand never leaves the book."""
    pre = _pose(r, bend=(-2, -6, 0), head=(0, 6, 0), right=(-17, 48, -8), quill=(0.2, 0.6, -0.75))
    coil = _pose(r, rock=(-1.5, 0, 0), bend=(-8, -16, 0), neck=(-4, 0, 0), head=(-4, 14, 0), jaw=4, right=(-18, 60, 2), quill=(0.1, 0.55, -0.83),
                 book=_book(0, 0, (14, 28, 42), (10, 25, 40)))
    hit = _pose(r, rock=(3, 0, 0), bend=(12, 10, 0), head=(6, -6, 0), jaw=10, right=(-8, 40, -32), quill=(0.05, 0.45, -0.89), falls=(-10, 0))
    after = _pose(r, rock=(3.5, 0, 0), bend=(14, 12, 0), head=(7, -7, 0), jaw=8, right=(-7, 38, -34), quill=(0.05, 0.5, -0.87), falls=(-8, 0))
    return Move("thrust", "LIGHT", 10, 4, 12, title="Quill Thrust", track=10.0, rng=(0, 3.0), kinds=("near",), damage=0.9, knock=0.7, plant=(),
                hit=[("quill", NIB, 2.5)], lag={"quill": 0.03}, through=_on(r, after),
                stages={"windup": [(0.3, pre), (1.0, coil)], "active": [(0.5, hit), (1.0, after)], "recover": _back(r, after)})


def rake(r):
    """The rake (the thrust's follow-up): from the thrust he sweeps the quill on across to his left, low, and rakes it
    back across you."""
    pre = _pose(r, rock=(1.5, 0, 0.5), bend=(6, -4, 0), head=(2, 4, 0), right=(-3, 40, -26), quill=(0.45, 0.4, -0.8))
    wind = _pose(r, rock=(0, 0, 1.5), bend=(-4, -16, 0), head=(-2, 12, 0), jaw=4, right=(6, 46, -18), quill=(0.75, 0.35, -0.55))
    hit = _pose(r, rock=(1, 0, -2), bend=(6, 16, 0), head=(2, -8, 0), jaw=8, right=(-24, 40, -20), quill=(-0.75, 0.35, -0.55), falls=(-6, -3))
    after = _pose(r, rock=(1, 0, -2.5), bend=(6, 20, 0), head=(2, -10, 0), jaw=6, right=(-26, 40, -14), quill=(-0.85, 0.3, -0.4))
    return Move("rake", "LIGHT", 9, 4, 12, track=8.0, rng=(0, 3.0), kinds=("near",), damage=0.8, knock=0.8, plant=(),
                hit=[("quill", NIB, 2.5)], lag={"quill": 0.03}, through=_on(r, after),
                stages={"windup": [(0.3, pre), (1.0, wind)], "active": [(0.5, hit), (1.0, after)], "recover": _back(r, after)})


def shutting(r):
    """The Shutting: he takes the left board of the book and lifts it up and over, past upright, rearing back with the
    quill raised and the right leaves lifting; holds; then slams the book shut on the desk, leaning in with all his
    weight, and the sea bursts out of it at you. He opens it again."""
    grip = _pose(r, bend=(2, 0, 0), left=COVER_EDGE, right=(-16, 46, -10), quill=(0.3, 0.6, -0.75), book=_book(20, 0, (6, 14, 24), (10, 25, 40)))
    lift = _pose(r, rock=(-2, 0, 0), at=(0, 1, 0), bend=(-12, 0, 0), neck=(-4, 0, 0), head=(-8, 0, 0), jaw=8, left=COVER_EDGE, right=(-22, 56, 0),
                 quill=(-0.3, -0.9, 0.3), book=_book(100, 0, (-8, -20, -34), (20, 35, 50)), falls=(-6, 3))
    slam = _pose(r, rock=(4, 0, 0), bend=(20, 0, 0), neck=(6, 0, 0), head=(12, 0, 0), jaw=16, left=COVER_EDGE, right=(-18, 44, -18),
                 quill=(0.2, 0.7, -0.7), book=_book(166, 0, (-10, -25, -40), (-10, -25, -40)), falls=(-36, 10))
    after = _pose(r, rock=(4.5, 0, 0), bend=(22, 0, 0), neck=(6, 0, 0), head=(13, 0, 0), jaw=14, left=COVER_EDGE, right=(-18, 43, -19),
                  quill=(0.2, 0.72, -0.68), book=_book(166, 0, (-10, -25, -40), (-10, -25, -40)), falls=(-28, 8))
    reopen = _pose(r, rock=(1, 0, 0), bend=(6, 0, 0), head=(4, 0, 0), left=COVER_EDGE, right=(-16, 44, -14), quill=(0.25, 0.7, -0.65),
                   book=_book(70, 0, (0, 0, 0), (4, 14, 26)), falls=(-6, 2))
    return Move("shutting", "HEAVY", 16, 6, 20, title="The Shutting", hold=6, track=6.0, rng=(0, 2.6), kinds=("near",), damage=1.4, knock=1.4,
                plant=(), hit=[("book", (0, -2, -18), 6), ("desk", (0, -1, -16), 5)], through=_mix(after, reopen, 0.15),
                stages={"windup": [(0.3, grip), (1.0, lift)], "active": [(0.45, slam), (1.0, after)],
                        "recover": [(0.45, _mix(after, reopen, 0.6)), (0.7, _mix(reopen, _pose(r), 0.6))]})


def tidal(r):
    """Tidal Page: his left hand takes a leaf and lifts it up high, rearing, the quill drawn back; holds; then he turns
    the page over with all his strength, the other leaves flying after it, leans in and points the quill at you, and
    the sea surges off the desk after it."""
    take = _pose(r, bend=(2, 0, 0), head=(2, 0, 0), left=("on", "leaf_l2", (9, -1, 0)), right=(-16, 44, -12), quill=(0.3, 0.6, -0.75),
                 book=_book(0, 0, (10, 25, 52), (10, 25, 40)))
    up = _pose(r, rock=(-2, 0, 0), at=(0, 1, 0), bend=(-10, 4, 0), neck=(-4, 0, 0), head=(-10, -4, 0), jaw=8, left=("on", "leaf_l2", (9, -1, 0)),
               right=(-18, 54, -2), quill=(0.1, 0.2, -0.95), book=_book(0, 0, (16, 34, 96), (12, 30, 50)), falls=(-4, 2))
    top = _pose(r, rock=(-3, 0, 0), at=(0, 2, 0), bend=(-14, 6, 0), neck=(-6, 0, 0), head=(-14, -6, 0), jaw=12, left=("on", "leaf_l2", (9, -1, 0)),
                right=(-20, 58, 2), quill=(0.1, 0.1, -0.98), book=_book(0, 0, (20, 40, 112), (14, 32, 54)), falls=(-6, 3))
    turn = _pose(r, rock=(4, 0, -1), bend=(16, -6, 0), neck=(4, 0, 0), head=(6, 6, 0), jaw=20, left=(-6, 44, -16), lhand=((-0.6, 0.3, -0.7), (0, -1, 0)),
                 right=(-10, 48, -32), quill=(0.0, 0.1, -1.0), book=_book(0, 0, (150, 165, 176), (10, 25, 40)), falls=(-44, 12))
    after = _pose(r, rock=(4.5, 0, -1), bend=(18, -6, 0), neck=(4, 0, 0), head=(7, 6, 0), jaw=16, left=(-8, 42, -16), lhand=((-0.6, 0.3, -0.7), (0, -1, 0)),
                  right=(-9, 47, -33), quill=(0.0, 0.12, -1.0), book=_book(0, 0, (120, 150, 172), (12, 28, 44)), falls=(-34, 9))
    settle = _pose(r, rock=(1, 0, 0), bend=(5, 0, 0), head=(2, 0, 0), right=(-14, 44, -18), quill=(0.2, 0.6, -0.75),
                   book=_book(0, 0, (30, 60, 90), (10, 25, 40)), falls=(-6, 2))
    return Move("tidal", "HEAVY", 20, 8, 20, title="Tidal Page", hold=8, track=4.0, ability="Tidal Page", rng=(0, 7), kinds=("area", "ranged"),
                damage=0, knock=0, plant=(), through=after,
                stages={"windup": [(0.3, take), (0.7, up), (1.0, top)], "active": [(0.35, turn), (1.0, after)],
                        "recover": [(0.45, _mix(after, settle, 0.6)), (0.7, _mix(settle, _pose(r), 0.5))]})


def volley(r):
    """Ink Volley: he dips the quill's nib into the book and flicks it at you, three times, ink flying off the nib."""
    dip = lambda d: _pose(r, rock=(1, 0, 0), bend=(6, -4, 0), head=(6, 4, 0), right=(-10, 44 - d, -10), quill=(0.25, 0.92, -0.3),
                          book=_book(0, 0, (8, 20, 34), (6, 18, 30)))
    flick = lambda k: _pose(r, rock=(-1.5, 0, 0), bend=(-4, 6, 0), head=(-4, -4, 0), jaw=6, right=(-12, 52, -26 - k), quill=(0.05, -0.25, -0.97),
                            book=_book(0, 0, (14, 28, 44), (12, 26, 42)), falls=(-8, 2))
    return Move("volley", "LIGHT", 12, 14, 14, title="Ink Volley", track=9.0, ability="Ink Volley", origin=VOLLEY_FROM, rng=(4, 18),
                kinds=("ranged",), damage=0, knock=0, plant=(), through=_mix(flick(2), dip(0), 0.5),
                stages={"windup": [(0.3, _mix(_pose(r), dip(0), 0.4)), (1.0, dip(2))],
                        "active": [(0.15, flick(0)), (0.33, dip(1)), (0.5, flick(1)), (0.66, dip(1)), (0.83, flick(2)), (1.0, _mix(flick(2), dip(0), 0.3))],
                        "recover": _back(r, _mix(flick(2), dip(0), 0.5))})


def pages(r):
    """Loose Pages: he lifts both hands off the book, spread palm down over it, and rears back with his face to the roof
    as the book riffles faster and faster and every leaf flies up out of it."""
    up = _pose(r, rock=(-2, 0, 0), at=(0, 1, 0), bend=(-10, 0, 0), neck=(-6, 0, 0), head=(-16, 0, 0), jaw=12, left=(12, 50, -18),
               lhand=((0, 0.3, -1), (1, 0, 0)), right=(-14, 60, -10), quill=(0.1, -0.95, -0.2), book=_riffle(0.0, 24, 10))
    beat = lambda k: _pose(r, rock=(-2 - 0.5 * (k % 2), 0, 0.8 * (1 - 2 * (k % 2))), at=(0, 1.5, 0), bend=(-13, 0, 0), neck=(-6, 0, 0), head=(-20, 0, 2),
                           jaw=16, left=(13, 52, -18), lhand=((0, 0.3, -1), (1, 0, 0)), right=(-15, 62, -10), quill=(0.1, -0.95, -0.2),
                           book=_riffle(0.5 * k + 0.25, 50 + 10 * k, 20), falls=(-10, 4))
    return Move("pages", "NONE", 14, 20, 14, title="Loose Pages", track=0.0, ability="Loose Pages", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), stages={"windup": [(0.4, _mix(_pose(r), up, 0.5)), (1.0, up)],
                                  "active": [(0.25, beat(0)), (0.5, beat(1)), (0.75, beat(2)), (1.0, beat(3))],
                                  "recover": _back(r, beat(3), _pose(r, bend=(-2, 0, 0), book=_riffle(0.2, 16, 4)))})


def cloud(r):
    """Ink Cloud: he bows right down over the book, both hands spread on it, then heaves and breathes ink out over it."""
    bow = _pose(r, rock=(2, 0, 0), bend=(18, 0, 0), neck=(8, 0, 0), head=(12, 0, 0), jaw=4, page_at=(7, -5, -2), right=("on", "book", (-7, -5, -2)),
                rhand=((0, 0.25, -1), (1, 0, 0)), quill=(-0.2, 0.35, -0.92), book=_book(0, 0, (2, 6, 10), (2, 6, 10)))
    heave = lambda k: _pose(r, rock=(1 + k, 0, 0), at=(0, 0.5, 0), bend=(12 - 2 * k, 0, 0), neck=(2, 0, 0), head=(-6 - 4 * k, 0, 0), jaw=28 + 4 * k,
                            page_at=(7, -5, -2), right=("on", "book", (-7, -5, -2)), rhand=((0, 0.25, -1), (1, 0, 0)), quill=(-0.2, 0.35, -0.92),
                            book=_book(0, 0, (4, 10, 16), (4, 10, 16)), falls=(-4 * k, 2))
    return Move("cloud", "NONE", 14, 16, 14, title="Ink Cloud", track=0.0, ability="Ink Cloud", rng=(0, 99), kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, _mix(_pose(r), bow, 0.5)), (1.0, bow)],
                        "active": [(0.3, heave(0)), (0.55, _mix(heave(0), bow, 0.6)), (0.8, heave(1)), (1.0, heave(1.8))],
                        "recover": _back(r, heave(1.8))})


def undertow(r):
    """Undertow: his left hand leaves the book at last and reaches out at you, open, his whole body leaning after it;
    he holds; then draws it back to his chest, closing, and the sea pulls you in with it."""
    reach = _pose(r, rock=(2.5, 0, 0), bend=(10, -10, 0), neck=(2, 0, 0), head=(-2, 8, 0), jaw=8, left=(12, 50, -40), lhand=((0, -0.3, -1), (1, 0, 0)),
                  right=(-18, 46, -6), quill=(0.3, 0.6, -0.7), book=_book(0, 0, (14, 30, 46), (10, 25, 40)))
    pull = _pose(r, rock=(-3, 0, 0), at=(0, 1, 0), bend=(-12, 6, 0), neck=(-4, 0, 0), head=(-8, -4, 0), jaw=14, left=(8, 52, -12), lhand=((0, 0.6, 0.8), (1, 0, 0)),
                 right=(-20, 50, 0), quill=(0.3, 0.4, -0.85), book=_book(0, 0, (30, 50, 70), (24, 44, 64)), falls=(14, -2))
    held = _pose(r, rock=(-3.5, 0, 0), at=(0, 1, 0), bend=(-13, 6, 0), neck=(-4, 0, 0), head=(-9, -4, 0), jaw=12, left=(8, 52, -11), lhand=((0, 0.6, 0.8), (1, 0, 0)),
                 right=(-20, 50, 0), quill=(0.3, 0.4, -0.85), book=_book(0, 0, (26, 46, 66), (20, 40, 60)), falls=(10, -2))
    return Move("undertow", "NONE", 14, 14, 16, title="Undertow", track=6.0, ability="Undertow", rng=(3, 14), kinds=("gap",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.35, _mix(_pose(r), reach, 0.45)), (1.0, reach)], "active": [(0.5, pull), (1.0, held)],
                        "recover": _back(r, held)})


def flood(r):
    """The Flood (the signature): he sinks over the book, gathering; then straightens to his full height for the first
    time, his arms flung wide, the quill raised, his face to the roof and his jaw hanging open, and every leaf of the
    book flies up as the sea pours out of it. He shudders there while it pours, and folds back down."""
    sink = _pose(r, rock=(2, 0, 0), at=(0, -1, 0), bend=(14, 0, 0), neck=(6, 0, 0), head=(14, 0, 0), right=(-15, 38, -14), quill=(0.2, 0.85, -0.5),
                 book=_book(0, 0, (2, 4, 8), (2, 4, 8)))
    tall = lambda k: _pose(r, rock=(-3 - 0.6 * k, 0, 0.8 * (1 - 2 * (k % 2))), at=(0, 3 + 0.5 * (k % 2), 0), bend=(-28, 2 * (1 - 2 * (k % 2)), 0),
                           neck=(-10, 0, 0), head=(-26 - 2 * (k % 2), 0, 4), jaw=30 + 3 * (k % 2), left=(28, 62, -6), lhand=((0.8, -0.5, -0.3), (0, 0, -1)),
                           right=(-27, 66, -4), quill=(-0.25, -0.95, -0.1), book=_book(14, 14, (70, 100, 130), (70, 100, 130)),
                           falls=(-30 - 4 * (k % 2), 14))
    return Move("flood", "PERILOUS", 26, 14, 22, title="Flood", signature=True, track=0.0, ability="Flood", rng=(0, 99), kinds=("area",),
                damage=0, knock=0, plant=(), through=tall(1),
                stages={"windup": [(0.3, _mix(_pose(r), sink, 0.6)), (0.6, sink), (1.0, tall(0))],
                        "active": [(0.3, tall(1)), (0.6, tall(2)), (1.0, tall(3))],
                        "recover": _back(r, tall(3), _pose(r, bend=(-6, 0, 0), head=(-6, 0, 0), right=(-17, 46, -12), quill=(0.25, 0.6, -0.75),
                                                          book=_book(0, 0, (20, 40, 60), (20, 40, 60))))})


def turn(r):
    """For whoever is behind him (LIGHT): his top half turns right round on the lectern, slowly, the face first, his left
    hand lifting off the book; then he drives the quill down at you behind him and turns back."""
    look = _pose(r, bend=(0, 30, 0), head=(0, 40, 0), right=(-18, 46, -6), quill=(0.3, 0.6, -0.7))
    round_ = _pose(r, rock=(0, 6, 0), bend=(-6, 150, 0), neck=(0, 10, 0), head=(-4, 10, 0), jaw=8, left=(-14, 46, 6), right=(16, 58, -2),
                   quill=(-0.1, 0.55, 0.83))
    hit = _pose(r, rock=(-2, 6, 0), bend=(8, 160, 0), neck=(2, 8, 0), head=(4, 6, 0), jaw=12, left=(-14, 44, 8), right=(6, 40, 30), quill=(-0.05, 0.45, 0.89))
    after = _pose(r, rock=(-2.5, 6, 0), bend=(10, 162, 0), neck=(2, 8, 0), head=(5, 6, 0), jaw=10, left=(-14, 44, 9), right=(5, 38, 32), quill=(-0.05, 0.5, 0.86))
    return Move("turn", "LIGHT", 12, 4, 16, track=0.0, rng=(0, 3.0), kinds=("behind",), damage=0.9, knock=0.9, plant=(),
                hit=[("quill", NIB, 2.5)], lag={"quill": 0.03}, through=_on(r, after, 0.05),
                stages={"windup": [(0.3, look), (1.0, round_)], "active": [(0.5, hit), (1.0, after)], "recover": _back(r, after)})


def wake(r):
    """His intro: he is reading, bowed low over the book; he takes the last leaf and turns it over slowly, then lifts
    his face to you, the jaw falling open, and raises the quill."""
    read = _pose(r, bend=(6, 0, 0), neck=(4, 0, 0), head=(12, 0, 0), left=("on", "leaf_l2", (9, -1, 0)), book=_book(0, 0, (10, 25, 46), (10, 25, 40)))
    over = _pose(r, bend=(5, -4, 0), neck=(4, 0, 0), head=(10, 6, 0), left=("on", "leaf_l2", (9, -1, 0)), book=_book(0, 0, (10, 25, 120), (10, 25, 40)))
    laid = _pose(r, bend=(4, -2, 0), neck=(2, 0, 0), head=(8, 4, 0), book=_book(0, 0, (10, 25, 170), (10, 25, 40)))
    look = _pose(r, rock=(-1, 0, 0), bend=(-6, 0, 0), neck=(-6, 0, 0), head=(-12, 0, 2), jaw=14, right=(-18, 56, -6), quill=(0.1, 0.4, -0.9),
                 book=_book(0, 0, (10, 25, 170), (10, 25, 40)))
    return Move("wake", "NONE", 16, 26, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.5, _mix(_pose(r), read, 0.5)), (1.0, read)], "active": [(0.35, over), (0.6, laid), (1.0, look)],
                        "recover": _back(r, look)})


# His sounds (SPEC §35.8): water, paper and old wood; he drifts, so no steps. Chosen by ear on paper, not yet heard.
SOUNDS = {
    "tell_light": [("entity.drowned.ambient_water", 1.0, 0.8), ("item.book.page_turn", 1.4, 0.9)],
    "tell_heavy": [("entity.drowned.ambient_water", 1.6, 0.6), ("block.wood.break", 1.2, 0.5)],
    "tell_perilous": [("ambient.underwater.loop.additions.ultra_rare", 2.0, 0.8), ("entity.elder_guardian.ambient", 1.4, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 1.2), ("item.trident.throw", 1.0, 0.8)],
    "swing_heavy": [("item.book.put", 2.0, 0.5), ("entity.player.splash.high_speed", 1.6, 0.7)],
    "cast": [("item.book.page_turn", 2.0, 0.7), ("entity.generic.splash", 1.4, 0.8)],
    "impact": [("entity.player.splash.high_speed", 2.0, 0.7), ("block.wood.place", 1.6, 0.5)],
    "roar": [("entity.drowned.death_water", 2.4, 0.5), ("entity.elder_guardian.curse", 1.2, 0.7)],
}
MOVE_SOUNDS = {"pages": {"swing": "cast"}, "cloud": {"swing": "cast", "impact": ""}, "flood": {"swing": "roar"}, "undertow": {"swing": "cast"},
               "tidal": {"swing": "cast"}}

FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "thrust", "weight": 3, "when": "near", "then": [["rake"]]},
            {"move": "shutting", "weight": 2, "when": "near"},
            {"move": "tidal", "weight": 2},
            {"move": "volley", "weight": 3, "then": [["volley"]]},
            {"move": "turn", "weight": 3, "when": "behind"},
        ]},
        # the shelves empty with the second phase: Loose Pages and the Ink Cloud, as in GateBestiary
        {"transition": "pages", "pool": [
            {"move": "thrust", "weight": 3, "when": "near", "then": [["rake"], ["rake", "shutting"]]},
            {"move": "shutting", "weight": 2, "when": "near"},
            {"move": "tidal", "weight": 2},
            {"move": "volley", "weight": 2, "then": [["volley"]]},
            {"move": "cloud", "weight": 2},
            {"move": "pages", "weight": 1},
            {"move": "turn", "weight": 3, "when": "behind"},
        ]},
        {"transition": "flood", "pool": [
            {"move": "undertow", "weight": 3, "then": [["shutting"], ["thrust", "rake"]]},
            {"move": "thrust", "weight": 3, "when": "near", "then": [["rake", "shutting"]]},
            {"move": "tidal", "weight": 3},
            {"move": "volley", "weight": 2, "then": [["volley"]]},
            {"move": "cloud", "weight": 1},
            {"move": "flood", "weight": 1},
            {"move": "turn", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}

def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.85, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where his parts are at rest (blocks: x, up, forward), to place him against his round."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_l", (0, 0, 0)), ("hand_r", (0, 0, 0)), ("quill", (0, -24, 0)), ("quill", (0, 26, 0)), ("head", (0, 18, 0)),
                 ("anchor", (0, -17, 0)), ("book", (0, 0, 0)), ("fall1", (0, -37, 0)), ("arm_l", (0, 0, 0)), ("hood_tip", (0, 6, 0)),
                 ("finger_l", None)):
        if p is None:
            p, b = (0, -9, 0), "hand_l"
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, (p[0], -p[1], p[2])))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones))


if __name__ == "__main__":
    probe()
