"""The Mirror Palace's monsters (SPEC §60). Room: silver, white, your own reflection. So they are mirror plate and
clear glass edged dark, to stand out from the walls, and each carries one white light. (The Mirror Shade keeps the
player's own face: it has no body here.)"""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

SILVER = Ramp("#4c5462", "#6c7684", "#909aa8", "#b4bec8", "#d8dee6", "#f4f7fa")
DARK = Ramp("#0a0c10", "#14171c", "#20242c", "#2e343e", "#404854", "#586270")
WHITE, HOT = "#eef4ff", "#ffffff"


def plate(P, cube, base=3.4, faces=K.ALL, glint=True):
    """Mirror plate: silver, a dark line round every face, a glint slanting across it."""
    P.paint(cube, SILVER, base, rim=0.3)
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            if (i in (0, f.w - 1) or j in (0, f.h - 1)) and f.w > 2 and f.h > 2:
                f.set(i, j, DARK.at(2.4))
            elif glint and (i + j) % 5 == 2:
                f.set(i, j, SILVER.at(5))
            elif glint and (i + j) % 5 == 3:
                f.set(i, j, SILVER.at(4.4))


def pane(P, cube, faces=K.ALL, seed=0):
    """Clear glass edged dark: its outline, and slanting streaks of pale glass with nothing between them."""
    P.paint(cube, SILVER, 4.2, rim=0.2)
    for fname in faces:
        f = P.face(cube, fname)
        if f.w < 3 or f.h < 3:
            for i, j in f.each():
                if (j in (0, f.h - 1) and f.h > 2) or (i in (0, f.w - 1) and f.w > 2):
                    f.set(i, j, DARK.at(2.6))
            continue
        for i, j in f.each():
            if i in (0, f.w - 1) or j in (0, f.h - 1):
                f.set(i, j, DARK.at(2.6))
            elif (i + j + seed) % 3 != 0:
                f.set(i, j, (0, 0, 0, 0))


# ---------------------------------------------------------------------------------------------------------------------
# The Empty Suit (silver_knight): a suit of mirror plate with nobody in it, the plates hanging a little apart. Its
# shield is a mirror with you in it. It parries; wait for its own swing.

def empty_suit():
    r = Rig("silver_knight", (128, 64))
    r.shadow = 0.7
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("tasset", -3.5, -1, -2.5, 7, 3, 5)
    K.legs2(body, "1", (2, 3, 0), 4, 6, 20, thick=(3, 2), foot=(3, 3, 5, -2.5))
    chest = body.bone("chest", (0, -2, 0))
    chest.cube("cuirass", -4, -8, -2.5, 8, 8, 5)
    head = chest.bone("head", (0, -9.5, 0))
    head.cube("helm", -2.5, -5, -2.5, 5, 5, 5)
    head.bone("crest", (0, -5, 0)).cube("comb", 0, -3, -2, 0, 3, 5)
    for side, nm in SIDES2:
        arm = chest.bone("arm_" + nm, (side * 6.5, -7, 0), (-30 if side > 0 else 10, 0, side * -4))
        arm.cube("pauldron_" + nm, -2, -2, -2, 4, 3, 4, mirror=side < 0, share=None if side > 0 else "pauldron_l")
        fore = arm.bone("fore_" + nm, (0, 6, 0), (-40 if side > 0 else -30, 0, 0))     # five px of nothing between shoulder and glove
        fore.cube("gauntlet_" + nm, -1.5, 0, -1.5, 3, 4, 3, mirror=side < 0, share=None if side > 0 else "gauntlet_l")
        if side > 0:
            fore.bone("shield", (0, 2, -2), (70, 0, 0)).cube("shieldc", -4, -5, -1, 8, 10, 1)
        else:
            sw = fore.bone("blade", (0, 3, 0), (-60, 0, 0))
            sw.cube("edge", -0.5, -12, -0.5, 1, 12, 1)
            sw.cube("guard", -1.5, 0, -0.5, 3, 1, 1)
    r.look = [("head", 0.5, 0.3)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"head": 0.05, "fore_l": 0.05, "fore_r": 0.05, "blade": 0.08, "shield": 0.06, "crest": 0.08}
    K.moves(r, parry="Parry")
    K.act(r, "parry", {"arm_l": (-24, 0, 26), "fore_l": (-16, 0, 0), "arm_r": (20, 0, 10), "chest": {"rot": (4, -14, 0)}, "body": {"pos": (0, -0.6, 0.5)}},
          {"arm_l": (-50, 0, 20), "fore_l": (10, 0, 0), "chest": {"rot": (8, 10, 0)}, "body": {"pos": (0, -0.4, -2)}}, w=0.4, length=0.45, t_hit=0.08, plant=legs, lag=lagc)
    K.plain(r, {"arm_r": (-160, 0, 8), "fore_r": (10, 0, 0), "blade": (30, 0, 0), "arm_l": (10, 0, -34), "chest": {"rot": (-8, 16, 0)}},
            {"arm_r": (-50, 0, -10), "fore_r": (20, 0, 0), "blade": (-20, 0, 0), "arm_l": (10, 0, -20), "chest": {"rot": (12, -20, 0)}, "body": {"pos": (0, -0.5, -2)}},
            plant=legs, lag=lagc)
    # nobody holds the plates together: each one rides a breath behind the last
    K.loop(r, "idle", 5.0, lambda p: {"chest": {"pos": (0, 0.4 * s(p), 0), "rot": (0, 3 * s(p, 0.2), 0)}, "head": {"pos": (0, 0.5 * s(p, 0.15), 0), "rot": (0, 10 * s(p, 0.4), 0)},
                                      "arm_l": {"pos": (0, 0.5 * s(p, 0.3), 0)}, "arm_r": {"pos": (0, 0.5 * s(p, 0.45), 0)}, "fore_l": {"pos": (0, 0.4 * s(p, 0.5), 0)},
                                      "fore_r": {"pos": (0, 0.4 * s(p, 0.6), 0)}}, n=14)
    K.loop(r, "walk", 1.0, lambda p: {"chest": {"pos": (0, 0.5 * s(p * 2, 0.1), 0), "rot": (3, 4 * s(p), 2 * s(p))}, "head": {"pos": (0, 0.6 * s(p * 2, 0.25), 0)}, "arm_r": (12 * s(p), 0, 0),
                                      "arm_l": (4 * s(p, 0.5), 0, 0)})
    K.loop(r, "run", 0.62, lambda p: {"chest": {"pos": (0, 0.7 * s(p * 2, 0.1), 0), "rot": (10, 5 * s(p), 3 * s(p))}, "head": {"pos": (0, 0.8 * s(p * 2, 0.25), 0), "rot": (-6, 0, 0)},
                                      "arm_r": (-30 + 10 * s(p), 0, 0), "arm_l": (-10, 0, 10)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.2, lambda p: {"arm_l": (-20, 0, 20), "arm_r": (-40, 0, 0), "blade": (-20 + 8 * s(p * 2), 0, 0), "head": (6, 0, 3 * s(p)), "chest": {"pos": (0, 0.6 * s(p * 2), 0)}}, plant=legs)
    K.flinch(r, {"chest": {"rot": (-8, 6, 4), "pos": (0, 0.8, 1)}, "head": {"pos": (0, 1.4, 0.5), "rot": (-12, 0, 6)}, "arm_l": {"pos": (1, 0.8, 0)}, "arm_r": {"pos": (-1, 0.8, 0)}}, plant=legs)
    # the plates come down one after another into a heap
    heap = {"chest": {"pos": (0, -7, 1), "rot": (70, 0, 12)}, "head": {"pos": (3, -3, -4), "rot": (20, 40, 80)}, "arm_l": {"pos": (2, -4, 2), "rot": (40, 0, -60)},
            "arm_r": {"pos": (-2, -4, 1), "rot": (60, 0, 70)}, "fore_l": {"pos": (0, -1, -2), "rot": (30, 0, 0)}, "fore_r": {"pos": (0, -1, 0), "rot": (40, 0, 20)},
            "leg1_l": (-70, 0, 20), "leg1_r": (-60, 0, -24), "shin1_l": (60, 0, 0), "shin1_r": (80, 0, 0), "body": (10, 0, 6)}
    skip = ("shieldc", "edge", "guard", "comb")
    K.keyed(r, "death", 1.3, [(0, {}), (0.14, K.planted(r, {"chest": {"pos": (0, 1.5, 0)}, "head": {"pos": (0, 2.5, 0)}}, legs)), (0.5, K.on_floor(r, K.scaled(heap, 0.5), skip=skip)),
                              (0.9, K.on_floor(r, heap, skip=skip)), (1.02, K.on_floor(r, K.plus(heap, {"head": {"pos": (0, 1, 0)}}), skip=skip)), (1.3, K.on_floor(r, heap, skip=skip))], lag=lagc)
    return r


def paint_empty_suit(r, P):
    for nm in ("tasset", "cuirass", "helm", "pauldron_l", "gauntlet_l"):
        plate(P, nm, 3.4)
    for nm, fname in (("cuirass", "top"), ("cuirass", "bottom"), ("helm", "bottom"), ("gauntlet_l", "top"), ("tasset", "top")):
        K.fill(P, nm, fname, DARK.at(0.2))     # look in at the neck, the wrist, the waist: nothing
    f, g = P.face("helm", "north"), P.face("helm", "north", glow=True)
    for i in range(1, f.w - 1):
        f.set(i, 2, DARK.at(0))
    f.set(2, 2, hexc(WHITE))
    g.set(2, 2, hexc(WHITE, 240))
    for fname in ("west", "east"):
        fc = P.face("comb", fname)
        for i, j in fc.each():
            fc.set(i, j, DARK.at(3) if j >= abs(i - 2) - 0.5 else (0, 0, 0, 0))
    plate(P, "shieldc", 4.0, glint=False)
    f, g = P.face("shieldc", "north"), P.face("shieldc", "north", glow=True)   # the mirror, and somebody small in it
    for i, j in f.each():
        if 0 < i < f.w - 1 and 0 < j < f.h - 1:
            f.set(i, j, SILVER.at(4.2 + 0.8 * ((i - j) % 4 == 0)))
            g.set(i, j, hexc(WHITE, 60))
    for i, j in ((3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5), (2, 5), (5, 5), (3, 6), (4, 6), (3, 7), (4, 7)):
        f.set(i, j, DARK.at(3.4))
    P.paint("edge", SILVER, 4.6, rim=0.4)
    P.paint("guard", DARK, 3.0)
    for tag in ("thigh1_l", "shinb1_l", "footb1_l"):
        plate(P, tag, 3.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Glint (glint_mite): a shard of mirror on six needle legs, thin as a blade edge-on. It jumps from one reflection
# to the next.

def glint():
    r = Rig("glint_mite", (64, 64))
    r.shadow = 0.5
    body = K.at(r.bone("body", (0, 19, 0)), 19)
    body.cube("shardg", -0.5, -10, -3, 1, 11, 6)
    body.cube("tipg", -0.5, -13, -1, 1, 3, 3)
    body.cube("heelg", -0.5, -7, 3, 1, 6, 2)
    K.splay_pairs(body, 0, (0.5, 0.5, 0.5), (-2, 0, 2), 5, 8, rise=30, thick=1, mounts=True)
    r.pack()

    SIX = K.tags(3)
    K.moves(r, glint="Glint")
    K.act(r, "glint", {"body": {"rot": (0, 86, 0), "scale": (1, 1.12, 0.5), "pos": (0, 0.6, 0)}}, {"body": {"rot": (0, -20, 0), "scale": (1, 1.2, 1.1), "pos": (0, 1.5, -2)}},
          w=0.6, length=0.45, t_hit=0.08)
    K.plain(r, {"body": {"rot": (-16, 0, -30), "pos": (0, 0.5, 1)}}, {"body": {"rot": (20, 0, 36), "pos": (0, -0.4, -2.4)}}, plant=SIX)
    K.loop(r, "idle", 4.0, lambda p: {"body": {"rot": (2 * s(p), 30 * (bump(p, 0.3, 0.05) - bump(p, 0.7, 0.05)), 3 * s(p * 2)), "pos": (0, 0.3 * s(p * 2), 0)}}, n=16)
    K.loop(r, "walk", 0.5, lambda p: {"body": (2 * s(p * 2), 4 * s(p), 4 * s(p, 0.25))})
    K.loop(r, "run", 0.36, lambda p: {"body": (10 + 2 * s(p * 2), 5 * s(p), 6 * s(p, 0.25))})
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 0.6, lambda p: {"body": {"rot": (-10, 0, 10 * s(p)), "pos": (0, 0.8, 0)}}, plant=SIX)
    K.flinch(r, {"body": {"rot": (-14, 20, 14), "pos": (0, 0, 1.5)}}, plant=SIX)
    curl = {f"leg{t}": (0, 0, (-50 if t.endswith("l") else 50)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (50 if t.endswith("l") else -50)) for t in SIX})
    flat = dict(curl, body=(0, 0, 88))
    start = K.planted(r, {"body": {"pos": (0, 1.2, 0), "rot": (-10, 0, 0)}}, SIX)
    K.keyed(r, "death", 0.9, [(0, {}), (0.1, start)] + K.fall(r, 0.1, start, 0.55, flat, steps=5) + [(0.66, K.on_floor(r, K.plus(flat, {"body": (0, 0, -8)}))), (0.9, K.on_floor(r, flat))])
    return r


def paint_glint(r, P):
    for nm in ("shardg", "tipg", "heelg"):
        P.paint(nm, SILVER, 4.0, rim=0.2)
        for fname in ("west", "east"):   # its two broad faces: mirror, a glint, a dark line round them
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                if i in (0, f.w - 1) or j in (0, f.h - 1):
                    f.set(i, j, DARK.at(2.4))
                elif (i + j) % 4 == 1:
                    f.set(i, j, hexc(WHITE))
                    g.set(i, j, hexc(WHITE, 130))
        for fname in ("north", "south", "top", "bottom"):
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                f.set(i, j, hexc(WHITE))       # edge-on it is one bright line
                g.set(i, j, hexc(WHITE, 200))
    P.paint("thigh0_l", DARK, 3.0)
    P.paint("shinb0_l", DARK, 2.2)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Glass Duelist (glass_duelist, the elite): a fencer of clear glass with one arm far longer than the other. It
# answers every blow you land badly. It cannot answer from its short side.

def glass_duelist():
    r = Rig("glass_duelist", (128, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 9, 0)), 9)
    body.cube("hipsd", -2, -1, -1.5, 4, 3, 3)
    K.legs2(body, "1", (1.5, 2, 0), 6, 8, 18, thick=(2, 1), foot=(2, 2, 4, -2))
    chest = body.bone("chest", (0, -1, 0), (4, 0, 0))
    chest.cube("torsod", -2, -9, -1.5, 4, 9, 3)
    chest.cube("yoked", -3.5, -10, -1.5, 7, 1, 3)
    head = chest.bone("head", (0, -10, 0))
    head.cube("headd", -1.5, -4, -1.5, 3, 4, 3)
    arm = chest.bone("arm_r", (-4, -9, 0), (-70, 0, 6))       # its sword arm, and the sword is the end of it
    arm.cube("upperd", -0.5, 0, -0.5, 1, 7, 1)
    fore = arm.bone("fore_r", (0, 7, 0), (-20, 0, 0))
    fore.cube("forearmd", -0.5, 0, -0.5, 1, 7, 1, share="upperd")
    foil = fore.bone("foil", (0, 7, 0))
    foil.cube("guardd", -1.5, 0, -1.5, 3, 1, 3)
    foil.cube("foilc", -0.5, 1, -0.5, 1, 12, 1)
    short = chest.bone("arm_l", (4, -9, 0), (30, 0, -10))
    short.cube("shortu", -0.5, 0, -0.5, 1, 4, 1)
    short.bone("fore_l", (0, 4, 0), (60, 0, 0)).cube("shortf", -0.5, 0, -0.5, 1, 3, 1)
    r.look = [("head", 0.5, 0.3), ("chest", 0.2, 0.1)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"fore_r": 0.04, "foil": 0.08, "head": 0.04}
    K.moves(r, riposte="Riposte", lunge="Mirror Lunge")
    K.act(r, "riposte", {"arm_r": (30, 0, -20), "fore_r": (-110, 0, 0), "foil": (0, 0, 0), "chest": (0, 16, 0), "body": {"pos": (0, 0, 1)}},
          {"arm_r": (-14, 0, 0), "fore_r": (16, 0, 0), "chest": (6, -20, 0), "body": {"pos": (0, -0.5, -2.5)}}, w=0.35, length=0.4, t_hit=0.07, plant=legs, lag=lagc)
    spent = K.planted(r, {"arm_r": (20, 0, 0), "fore_r": (10, 0, 0), "foil": (10, 0, 0), "chest": (26, -24, 0), "head": (16, 0, 0), "body": {"pos": (0, -3, -2)}}, legs)
    K.act(r, "lunge", {"arm_r": (40, 0, -10), "fore_r": (-100, 0, 0), "chest": (-6, 24, 0), "body": {"pos": (0, -2, 2)}},
          {"arm_r": (-20, 0, 0), "fore_r": (20, 0, 0), "chest": (14, -30, 0), "body": {"pos": (0, -2, -5)}}, w=0.8, length=0.45, t_hit=0.07, plant=legs, lag=lagc, stuck=spent)
    K.plain(r, {"arm_r": (-30, 0, -30), "fore_r": (-40, 0, 0), "chest": (0, 14, 0)}, {"arm_r": (0, 0, 30), "fore_r": (16, 0, 0), "chest": (4, -18, 0), "body": {"pos": (0, 0, -2)}}, plant=legs, lag=lagc)
    K.loop(r, "idle", 4.0, lambda p: {"arm_r": (3 * s(p), 0, 4 * s(p, 0.25)), "foil": (4 * s(p * 2), 0, 4 * s(p * 2, 0.25)), "chest": (1.0 * s(p), 3 * s(p, 0.2), 0), "head": (0, 6 * s(p, 0.4), 0),
                                      "body": {"pos": (0, 0.5 * s(p * 2), 0)}}, n=14)
    K.loop(r, "walk", 0.8, lambda p: {"chest": (2, 5 * s(p), 2 * s(p)), "arm_r": (4 * s(p * 2), 0, 0), "head": (0, -4 * s(p), 0), "arm_l": (8 * s(p), 0, 0)})
    K.loop(r, "run", 0.5, lambda p: {"chest": (12, 6 * s(p), 3 * s(p)), "arm_r": (30 + 5 * s(p * 2), 0, 0), "fore_r": (-40, 0, 0), "head": (-8, 0, 0), "arm_l": (20 * s(p), 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 0.9, lambda p: {"arm_r": (-10, 0, 0), "foil": (0, 0, 14 * s(p * 2)), "chest": (-4, 10, 0), "body": {"pos": (0, -1, 0)}}, plant=legs)
    K.flinch(r, {"chest": (-10, 10, 5), "head": (-12, 0, 0), "arm_r": (30, 0, 10), "body": {"pos": (0, 0, 1.5)}}, plant=legs)
    knees = K.planted(r, {"chest": (16, 0, 0), "head": (24, 0, 0), "arm_r": (60, 0, 0), "body": {"pos": (0, -4, 0)}}, legs)
    flat = {"body": (82, 0, 8), "chest": (4, 0, 0), "head": (10, 30, 0), "arm_r": (-100, 0, 30), "fore_r": (20, 0, 0), "arm_l": (-70, 0, -40), "leg1_l": (-40, 0, 0), "leg1_r": (-56, 0, 0),
            "shin1_l": (40, 0, 0), "shin1_r": (60, 0, 0)}
    skip = ("foilc", "guardd")
    K.keyed(r, "death", 1.3, [(0, {}), (0.16, K.planted(r, {"chest": (-12, 0, 0), "arm_r": (40, 0, 0)}, legs)), (0.55, knees)] + K.fall(r, 0.55, knees, 1.0, flat, steps=5, skip=skip)
            + [(1.1, K.on_floor(r, K.plus(flat, {"body": (-5, 0, 0)}), skip=skip)), (1.3, K.on_floor(r, flat, skip=skip))], lag=lagc)
    return r


def paint_glass_duelist(r, P):
    for k, nm in enumerate(("hipsd", "torsod", "headd")):
        pane(P, nm, seed=k)
    P.paint("yoked", DARK, 3.0, rim=0.6)
    f, g = P.face("headd", "north"), P.face("headd", "north", glow=True)   # no face: one upright line of light
    for j in range(1, f.h - 1):
        f.set(1, j, hexc(WHITE))
        g.set(1, j, hexc(WHITE, 240))
    for nm in ("upperd", "shortu", "shortf", "thigh1_l", "shinb1_l"):
        P.paint(nm, SILVER, 4.2, rim=0.2)
        for fname in K.SIDES:
            ff = P.face(nm, fname)
            ff.set(0, 0, DARK.at(2.6))
            ff.set(ff.w - 1, ff.h - 1, DARK.at(2.6))
    P.paint("footb1_l", DARK, 3.0, rim=0.6)
    P.paint("guardd", DARK, 3.4, rim=0.8)
    P.paint("foilc", SILVER, 5.0)
    for fname in K.SIDES:
        ff, gg = P.face("foilc", fname), P.face("foilc", fname, glow=True)
        ff.set(0, ff.h - 1, hexc(WHITE))
        gg.set(0, ff.h - 1, hexc(WHITE, 240))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Unblinking (the_unblinking, the Warden): a tall standing mirror on clawed feet, one eye filling the glass. It
# moves when you look away. Its gaze cuts in a line: the eye narrows first.

def unblinking():
    r = Rig("the_unblinking", (128, 128))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 18, 0)), 18)
    body.cube("crossbar", -7, -1, -1, 14, 2, 2)
    K.legs2(body, "1", (5, -1, 0), 4, 5, 30, thick=(2, 2), foot=(3, 2, 4, -2))
    fr = body.bone("chest", (0, -1, 0), (-4, 0, 0))            # the frame: it tilts on its stand
    fr.cube("post_l", 6, -28, -1, 2, 28, 2)
    fr.cube("post_r", -8, -28, -1, 2, 28, 2, share="post_l")
    fr.cube("rail", -8, -30, -1, 16, 2, 2)
    fr.cube("finial", -1.5, -33, -1, 3, 3, 2)
    fr.cube("glassm", -6, -28, -0.5, 12, 27, 1)
    eye = fr.bone("eye", (0, -14.5, -1.0))                      # one eye, upright, as tall as the glass
    eye.cube("white", -5, -11, 0, 10, 22, 0)
    eye.bone("pupil", (0, 0, -0.5)).cube("iris", -3, -5, 0, 6, 10, 0)     # half a px proud of the white, so the two never fight for the same depth
    r.look = [("chest", 0.5, 0.2)]
    r.pack()

    legs = ("1_l", "1_r")

    def stare(shut=0.0, wide=1.0, tall=1.0, dx=0.0, dy=0.0):
        """The eye narrowed from its sides (5 shuts it to a line; minus: wider open), the pupil scaled and moved."""
        return {"eye": {"scale": (max(0.04, 1 - shut / 5.2), 1, 1)}, "pupil": {"scale": (wide, tall, 1), "pos": (dx, dy, 0)}}
    K.moves(r, gaze="Silver Gaze", never="Never Blinks", reflections="Reflections")
    K.act(r, "gaze", K.plus(stare(4.0, 0.4, 1.0), {"chest": (-8, 0, 0), "body": {"pos": (0, 0, 1)}}), K.plus(stare(4.2, 0.5, 1.5), {"chest": (6, 0, 0), "body": {"pos": (0, 0, 2)}}),
          w=1.1, length=0.5, t_hit=0.08, plant=legs, open_pose=K.plus(stare(-1.5, 1.4, 1.3), {"chest": (14, 0, 3)}))
    K.act(r, "never", K.plus(stare(5.0, 0.8, 0.6), {"chest": (4, 0, 0)}), K.plus(stare(-2.0, 1.3, 1.3), {"chest": (-6, 0, 0)}), w=0.5, length=0.5, t_hit=0.08, plant=legs)
    K.act(r, "reflections", K.plus(stare(1.0), {"chest": (0, 80, 0), "body": {"pos": (0, 0.5, 0)}}), K.plus(stare(-1.0, 1.2, 1.2), {"chest": (0, -24, 0)}), w=0.9, length=0.6, t_hit=0.12, plant=legs)
    K.plain(r, K.plus(stare(1.0, 0.8), {"chest": (-26, 0, 0), "body": {"pos": (0, 0, 1)}}), K.plus(stare(0, 1.2, 1.2), {"chest": (46, 0, 0), "body": {"pos": (0, 0, -2)}}), plant=legs)
    K.loop(r, "idle", 8.0, lambda p: K.plus(stare(0.3 * s(p * 2), 1 + 0.08 * s(p * 3), 1, 1.6 * s(p, 0.1), 1.0 * s(p * 2, 0.3)), {"chest": (1.0 * s(p), 0, 0.8 * s(p, 0.25))}), n=20)
    K.loop(r, "walk", 0.8, lambda p: K.plus(stare(), {"chest": (2 * s(p * 2), 0, 3 * s(p))}))
    K.loop(r, "run", 0.5, lambda p: K.plus(stare(1.0, 0.8), {"chest": (8 + 2 * s(p * 2), 0, 4 * s(p))}))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.2, lambda p: K.plus(stare(2.5 + 0.8 * s(p * 2), 0.6, 1.2), {"chest": (-6, 0, 2 * s(p))}), plant=legs)
    K.flinch(r, K.plus(stare(5.0, 1, 0.5), {"chest": (-12, 0, 5)}), plant=legs)
    back = K.plus(stare(5.0, 0.02, 0.02), {"chest": (-84, 0, 4), "leg1_l": (30, 0, 0), "leg1_r": (20, 0, 0)})
    K.keyed(r, "death", 1.6, [(0, {}), (0.2, K.plus(stare(-2, 1.5, 1.5), {"chest": (8, 0, 0)})), (0.5, K.plus(stare(5.0, 0.02, 0.02), {"chest": (-16, 0, 3)})),
                              (1.1, K.on_floor(r, back)), (1.24, K.on_floor(r, K.plus(back, {"chest": (6, 0, 0)}))), (1.6, K.on_floor(r, back))])
    return r


def paint_unblinking(r, P):
    P.paint("crossbar", DARK, 2.8, rim=0.8)
    P.paint("post_l", DARK, 2.6, p_bands(7, 0.6), rim=0.8)
    P.paint("rail", DARK, 3.0, rim=0.9)
    P.paint("finial", SILVER, 3.4, rim=0.6)
    P.paint("glassm", SILVER, 3.8)
    for fname in ("north", "south"):
        f = P.face("glassm", fname)
        for i, j in f.each():
            f.set(i, j, SILVER.at(3.0 + 1.4 * ((i + j) % 9 == 3) + 0.7 * ((i + j) % 9 == 4)))
        f, g = P.face("white", fname), P.face("white", fname, glow=True)   # the eye: an upright almond, dark at its rim
        for i, j in f.each():
            hw = 5.0 * max(0.0, math.cos((j - 10.5) / 11.4 * math.pi / 2)) ** 0.8
            d = abs(i - 4.5)
            if d > hw:
                f.set(i, j, (0, 0, 0, 0))
            elif d > hw - 1.0:
                f.set(i, j, DARK.at(0.6))
            else:
                f.set(i, j, hexc("#f6f2ea"))
                g.set(i, j, hexc(WHITE, 90))
        f, g = P.face("iris", fname), P.face("iris", fname, glow=True)
        for i, j in f.each():
            d = math.hypot((i - 2.5) / 3.0, (j - 4.5) / 5.0)
            if d > 1.0:
                f.set(i, j, (0, 0, 0, 0))
            elif i in (2, 3) and 1 < j < f.h - 2:
                f.set(i, j, DARK.at(0))                 # the pupil: a slit
            elif d > 0.78:
                f.set(i, j, DARK.at(1.6))
            else:
                f.set(i, j, SILVER.at(1.8 + 0.6 * ((i + j) % 2)))
        f.set(2, 3, hexc(WHITE))
        g.set(2, 3, hexc(WHITE, 255))
    P.paint("thigh1_l", DARK, 3.0)
    P.paint("shinb1_l", DARK, 3.4)
    P.paint("footb1_l", SILVER, 3.0, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("silver_knight", "The Empty Suit", empty_suit, paint_empty_suit, kind="strider", box=(0.8, 1.9), sunk=("shieldc", "edge", "comb"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.64, 0.46), lift=(2.0, 3.2), crouch=0.5, swing=0.3, bob=0.6, roll=0.05),
                                "body": K.body(headLevel=0.4, twist=0.25, land=1.3, shift=0.5)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"look": 3, "settle": 2}, arms=0.2, mass=1.6, springHz=1.6),
              voice={"ambient": [("item.armor.equip_iron", 0.5, 0.7), ("block.chain.step", 0.4, 0.8)], "hurt": [("item.armor.equip_iron", 0.9, 1.1), ("block.iron.hit", 0.6, 1.2)],
                     "death": [("item.armor.equip_netherite", 1.0, 0.8), ("block.iron.break", 0.8, 1.0)], "step": [("block.iron.step", 0.4, 1.1)]}),
    K.Monster("glint_mite", "The Glint", glint, paint_glint, kind="crawler", box=(0.6, 0.9),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.4), crouch=0.4, speed=1.2), "body": K.body(headLevel=0.0, twist=0.25, slope=0.9, land=0.8, shift=0.2)},
              life=dict(STILL, temperament="insect", breathDepth=0.0, idles={"settle": 2, "flick": 1}),
              voice={"ambient": [("block.amethyst_block.chime", 0.4, 1.8), ("entity.endermite.ambient", 0.4, 1.4)], "hurt": [("block.glass.hit", 0.8, 1.6), ("entity.endermite.hurt", 0.4, 1.4)],
                     "death": [("block.glass.break", 0.9, 1.5), ("entity.endermite.death", 0.4, 1.4)], "step": [("entity.endermite.step", 0.3, 1.4)]}),
    K.Monster("glass_duelist", "The Glass Duelist", glass_duelist, paint_glass_duelist, kind="strider", box=(0.7, 2.0), sunk=("foilc", "guardd"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.6, 0.42), lift=(2.0, 3.4), crouch=0.5, swing=0.24, bob=0.4, roll=0.03),
                                "body": K.body(headLevel=0.6, twist=0.35, land=1.0, shift=0.5)},
              life=dict(STILL, temperament="hunter", breathDepth=0.0, idles={"look": 3, "settle": 2}, arms=0.0, headSpeed=5.0, springHz=2.2),
              voice={"ambient": [("block.amethyst_block.resonate", 0.5, 1.2), ("block.glass.step", 0.5, 1.0)], "hurt": [("block.glass.hit", 0.9, 1.0), ("block.amethyst_cluster.hit", 0.6, 1.2)],
                     "death": [("block.glass.break", 1.0, 0.9), ("block.amethyst_cluster.break", 0.8, 1.1)], "step": [("block.glass.step", 0.35, 1.2)]}),
    K.Monster("the_unblinking", "The Unblinking", unblinking, paint_unblinking, kind="strider", box=(1.1, 2.4),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.62, 0.46), lift=(1.6, 2.6), crouch=0.5, swing=0.2, bob=0.4, roll=0.04),
                                "body": K.body(headLevel=0.0, twist=0.2, slope=0.4, land=1.4, shift=0.6)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, mass=2.4, springHz=1.2),
              voice={"ambient": [("block.amethyst_block.resonate", 0.6, 0.5), ("entity.enderman.stare", 0.15, 0.6)], "hurt": [("block.glass.hit", 1.0, 0.6), ("block.wood.hit", 0.7, 0.6)],
                     "death": [("block.glass.break", 1.0, 0.5), ("block.wood.break", 0.9, 0.6)], "step": [("block.wood.step", 0.4, 1.3)]}),
]
