"""The Overgrown Temple's monsters (SPEC §60). Room: green stone and leaves in gold light. So they are grey carved stone
and dark root, with one flower's red among them, and each carries a green light in its cracks."""
import math

from . import kit as K
from .kit import Rig, Ramp, hexc, p_bands, p_blotch, p_plates, p_stone, p_strokes, s, c, bump, SIDES2

STONE = Ramp("#3a4038", "#4e564a", "#666e60", "#828a7a", "#a0a896", "#bec6b2")
ROOT = Ramp("#1c140e", "#2c2016", "#403022", "#564230", "#6e5640", "#8a6e52")
LEAF = Ramp("#1e3418", "#2e4e22", "#426c30", "#5c8c42", "#7cac5a")
PETAL = Ramp("#4a0e1c", "#74162a", "#a0263c", "#c84458", "#e87a84", "#f8b8b8")
BRONZE = Ramp("#2a1c0c", "#46300f", "#6a4a18", "#8f6a24", "#b88c38", "#dcb458")
GREEN, HOT = "#7cff8a", "#e6ffd8"


def cracks(P, cube, faces=K.SIDES, n=1, a=200, seed=0):
    """Green light in the cracks of a stone."""
    for fname in faces:
        f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
        for k in range(n):
            x = K._h(cube, fname, k, seed) % max(1, f.w - 2) + 1
            for j in range(1, f.h - 1):
                x = max(1, min(f.w - 2, x + K._h(cube, fname, j, k) % 3 - 1))
                f.set(x, j, hexc(GREEN))
                g.set(x, j, hexc(GREEN, a))


def moss(P, cube, faces=("top",), every=7):
    for fname in faces:
        f = P.face(cube, fname)
        for i, j in f.each():
            if (i * 5 + j * 3) % every == 0:
                f.set(i, j, LEAF.at(1 + (i + j) % 3))


# ---------------------------------------------------------------------------------------------------------------------
# The Creaker (vine_creaker): a broken temple statue carried about on the roots that broke it, like a crab in a shell.
# Watched, it is a statue kneeling. It only walks while nobody looks.

def creaker():
    r = Rig("vine_creaker", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 16, 0)), 16)
    body.cube("plinth", -5, -3, -4, 10, 4, 8)
    K.splay_pairs(body, 0.5, (5, 5, 5), (-3, 0, 3), 6, 10, rise=28, thick=2)
    bust = body.bone("chest", (0, -3, 0), (6, 0, 0))
    bust.cube("torso", -4, -10, -3, 8, 10, 6)
    bust.cube("knee", -4, -4, -6, 8, 4, 3)
    head = bust.bone("head", (0, -10, 0), (8, 0, 6))
    head.cube("skullc", -3, -6, -3, 6, 6, 6)
    arm = bust.bone("arm_l", (4, -9, 0), (-30, 0, -8))
    arm.cube("armc", 0, 0, -1.5, 3, 8, 3)
    arm.bone("fore_l", (1.5, 8, 0), (-60, 0, 0)).cube("forec", -1.5, 0, -1.5, 3, 6, 3)
    bust.cube("stump", -6, -9, -1.5, 2, 3, 3)
    for k in range(2):
        bust.bone(f"leaf{k}", (-4 + 7 * k, -10, 3)).cube(f"vine{k}", -1, 0, 0, 2, 9 + 2 * k, 0, share=None)
    r.look = [("head", 0.35, 0.2)]
    r.pack()

    SIX = K.tags(3)
    K.moves(r, grasp="Root Grasp")
    K.act(r, "grasp", {"body": {"rot": (-22, 0, 0), "pos": (0, 3, 1)}, "chest": (-8, 0, 0), "leg0_l": (0, 30, -44), "leg0_r": (0, -30, 44), "shin0_l": (0, 0, -30), "shin0_r": (0, 0, 30)},
          {"body": {"rot": (12, 0, 0), "pos": (0, -1.5, -1.5)}, "chest": (8, 0, 0)}, w=1.3, length=0.5, t_hit=0.1, plant=SIX,
          open_pose={"body": {"rot": (8, 0, 0), "pos": (0, -2, -1)}, "chest": (6, 0, 0), "head": (10, 0, 0)}, lag={"leaf0": 0.08, "leaf1": 0.1})
    K.plain(r, {"body": {"rot": (-12, 0, 0), "pos": (0, 1.5, 1)}, "leg0_r": (0, -34, 40), "shin0_r": (0, 0, 30)}, {"body": {"rot": (8, 0, 0), "pos": (0, 0, -2.5)}, "leg0_r": (0, 30, -6)},
            plant=SIX)
    K.loop(r, "idle", 9.0, lambda p: {"chest": (0.6 * s(p), 0, 0.5 * s(p, 0.3)), "head": (0, 6 * bump(p, 0.5, 0.02), 0)}, n=18)   # it barely moves: one small turn of the head
    K.loop(r, "walk", 0.9, lambda p: {"chest": (2 * s(p * 2), 0, 3 * s(p)), "head": (-2 * s(p * 2, 0.1), 0, -2 * s(p, 0.1))})
    K.loop(r, "run", 0.55, lambda p: {"chest": (6 + 3 * s(p * 2), 0, 4 * s(p)), "head": (-6, 0, 0)})
    K.fallback_walk(r, K.alternating(3), axis="y")
    K.loop(r, "angry", 2.0, lambda p: {"body": {"pos": (0, 1.2 + 0.4 * s(p * 2), 0)}, "chest": (4, 0, 0), "head": (6, 0, 4 * s(p))}, plant=SIX)
    K.flinch(r, {"chest": (-8, 0, 6), "head": (-10, 0, 0), "body": {"pos": (0, -1, 1)}}, plant=SIX)
    curl = {f"leg{t}": (0, 0, (-46 if t.endswith("l") else 46)) for t in SIX}
    curl.update({f"shin{t}": (0, 0, (40 if t.endswith("l") else -40)) for t in SIX})
    start = K.planted(r, {"chest": (-10, 0, 0), "body": {"pos": (0, 2, 0)}}, SIX)
    over = dict(curl, body=(0, 0, 80), chest=(10, 0, 10), head=(20, 0, 30))
    K.keyed(r, "death", 1.3, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.8, over, steps=4)
            + [(0.92, K.on_floor(r, K.plus(over, {"body": (0, 0, -6)}))), (1.3, K.on_floor(r, over))], lag={"leaf0": 0.08, "leaf1": 0.1})
    return r


def paint_creaker(r, P):
    rng = P.rng
    for nm, base in (("plinth", 2.0), ("torso", 2.8), ("knee", 2.4), ("skullc", 3.2), ("armc", 2.6), ("forec", 2.8), ("stump", 1.8)):
        P.paint(nm, STONE, base, p_stone(rng, 0.7, 4), rim=0.6, ao=0.6)
    moss(P, "torso", ("top", "west"))
    moss(P, "skullc")
    f = P.face("skullc", "north")  # a carved face worn to a slit and a brow
    for i in range(1, f.w - 1):
        f.set(i, 2, STONE.at(1.0))
    K.lit(P, "skullc", "north", 2, 3, 2, 1, GREEN, HOT, a=240)
    cracks(P, "torso", ("north", "east"), n=1)
    f = P.face("stump", "west")
    for i, j in f.each():
        f.set(i, j, STONE.at(4.6 - (i + j) % 2))  # the broken arm's bright break
    for k in range(2):
        K.tatter(P, f"vine{k}", LEAF, 2.8, seed=k, fall=0.15)
    P.paint("thigh0_l", ROOT, 3.0, p_strokes(2, 3, seed=1))
    P.paint("shinb0_l", ROOT, 2.4, p_strokes(2, 4, seed=2))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Thorn Bud (thorn_archer): a tall stalk on root feet with a shut bud for a head. The bud opens like a flower to spit
# thorns, and stays open a moment after.

def thorn_bud():
    r = Rig("thorn_archer", (64, 64))
    r.shadow = 0.5
    body = K.at(r.bone("body", (0, 15, 0)), 15)
    body.cube("bulb", -2.5, -2, -2.5, 5, 4, 5)
    K.legs2(body, "1", (2, 1, 0), 4, 5, 24, thick=(2, 1), foot=(3, 2, 3, -1.5))
    stalk = body.bone("chest", (0, -2, 0), (5, 0, 0))
    stalk.cube("stem", -1, -14, -1, 2, 14, 2)
    for side, nm in SIDES2:
        stalk.bone("frill_" + nm, (side * 1, -6 - (2 if side > 0 else 0), 0), (0, 0, side * -50)).cube("leafb_" + nm, 0 if side > 0 else -5, 0, -1.5, 5, 0, 3,
                                                                                                    mirror=side < 0, share=None if side > 0 else "leafb_l")
    head = stalk.bone("head", (0, -14, 0), (-5, 0, 0))
    head.cube("calyx", -2, -2, -2, 4, 2, 4)
    head.cube("pistil", -1, -6, -1, 2, 4, 2)
    for k in range(4):
        a = math.tau * k / 4
        sx, sz = round(math.sin(a)), -round(math.cos(a))
        pb = head.bone(f"leaf{k}", (sx * 2, -2, sz * 2), (0, -math.degrees(a), 0))
        pb.cube(f"petal{k}", -2.5, -8, -1, 5, 8, 1, share=None if k == 0 else "petal0")
    r.look = [("head", 0.5, 0.4), ("chest", 0.15, 0.1)]
    r.pack()

    legs = ("1_l", "1_r")
    bloom = lambda a: {f"leaf{k}": (-a, 0, 0) for k in range(4)}  # noqa: E731  (each petal falls outward on its own hinge)
    lagc = {f"leaf{k}": 0.03 * k for k in range(4)}
    K.moves(r, thorns="Thorn Arrows")
    K.act(r, "thorns", dict(bloom(78), chest=(-12, 0, 0), head=(-20, 0, 0), body={"pos": (0, -1, 0)}), dict(bloom(96), chest=(14, 0, 0), head=(22, 0, 0)),
          w=0.9, length=0.45, t_hit=0.08, plant=legs, lag=lagc, open_pose=dict(bloom(70), chest=(6, 0, 0), head=(26, 0, 0)))
    K.plain(r, dict(bloom(-6), chest=(-16, 0, 0), head=(-20, 0, 0)), dict(bloom(8), chest=(22, 0, 0), head=(30, 0, 0)), plant=legs)
    K.loop(r, "idle", 6.0, lambda p: dict(bloom(2 + 2 * s(p * 2) + 14 * bump(p, 0.55, 0.05)), chest=(1.5 * s(p), 0, 2 * s(p, 0.3)), head=(3 * s(p, 0.2), 0, 0),
                                          frill_l=(0, 0, 6 * s(p * 2)), frill_r=(0, 0, -6 * s(p * 2, 0.3))), n=16)
    K.loop(r, "walk", 0.8, lambda p: dict(bloom(3 + 3 * s(p * 2)), chest=(4 + 3 * s(p * 2), 0, 4 * s(p)), head=(-6 - 4 * s(p * 2, 0.15), 0, -3 * s(p, 0.1))))
    K.loop(r, "run", 0.5, lambda p: dict(bloom(6 + 4 * s(p * 2)), chest=(12 + 4 * s(p * 2), 0, 5 * s(p)), head=(-12, 0, 0)))
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)])
    K.loop(r, "angry", 1.6, lambda p: dict(bloom(24 + 8 * s(p * 2)), chest=(4, 0, 0), head=(12, 0, 3 * s(p))), plant=legs)
    K.flinch(r, dict(bloom(30), chest=(-12, 0, 8), head=(-16, 0, 0)), plant=legs)
    down = dict(bloom(100), body={"rot": (0, 0, 20)}, chest=(64, 0, 20), head=(20, 0, 0), frill_l=(0, 0, 40), frill_r=(0, 0, -40))
    K.keyed(r, "death", 1.0, [(0, {}), (0.14, K.planted(r, dict(bloom(60), chest=(-14, 0, 0)), legs))]
            + K.fall(r, 0.14, K.planted(r, dict(bloom(60), chest=(-14, 0, 0)), legs), 0.7, down, steps=3) + [(1.0, K.on_floor(r, K.plus(down, bloom(8))))], lag=lagc)
    return r


def paint_thorn_bud(r, P):
    P.paint("bulb", ROOT, 3.0, p_strokes(2, 3, seed=3), rim=0.5)
    P.paint("stem", LEAF, 2.2, rim=0.3)
    for fname in K.SIDES:  # thorns up the stem
        f = P.face("stem", fname)
        for j in range(1, f.h, 3):
            f.set(j % 2, j, hexc("#d8e4b0"))
    for fname in ("top", "bottom"):
        f = P.face("leafb_l", fname)
        for i, j in f.each():
            f.set(i, j, LEAF.at(3.2 - 0.3 * i + (0.8 if j == 1 else 0)))
    P.paint("calyx", LEAF, 2.6, rim=0.6)
    P.paint("petal0", PETAL, 3.0, rim=0.9, ao=0.2)
    f, g = P.face("petal0", "south"), P.face("petal0", "south", glow=True)  # the inside of a petal: paler, a lit vein
    for i, j in f.each():
        f.set(i, j, PETAL.at(4.6 - j * 0.25))
        if i == f.w // 2 and j > 2:
            f.set(i, j, hexc(GREEN))
            g.set(i, j, hexc(GREEN, 170))
    K.lit_cube(P, "pistil", GREEN, HOT, a=230, hot=lambda fn, i, j, f: fn == "top" or j == 0)
    P.paint("thigh1_l", ROOT, 3.0)
    P.paint("shinb1_l", ROOT, 2.4)
    P.paint("footb1_l", ROOT, 2.0)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Bramble Hound (thorn_hound): a hound woven of thorn, its ribs a cage you can see through, a lit seed inside. It
# lies as a bush until it springs; the seed gives it away.

def bramble_hound():
    r = Rig("thorn_hound", (64, 64))
    r.shadow = 0.6
    body = K.at(r.bone("body", (0, 12, 0)), 12)
    body.cube("spine", -1, -3, -6, 2, 2, 12)
    body.cube("keel", -0.5, 2, -5, 1, 1, 9)
    for k in range(4):
        body.cube(f"hoop{k}", -3, -2, -5 + 3 * k, 6, 5, 1, share=None if k == 0 else "hoop0")
    body.bone("seed", (0, 0.5, -1)).cube("seed", -1, -1, -1, 2, 2, 2)
    K.legs2(body, "0", (2.5, 2, -5), 5, 7, 40, thick=(2, 1))    # a foreleg: its knee ahead of it
    K.legs2(body, "1", (2.5, 1, 5), 8, 5, -24, thick=(2, 1))    # a hind leg: its hock behind it
    neck = body.bone("neck", (0, -2, -6), (30, 0, 0))
    neck.cube("neckc", -1, -4, -1, 2, 4, 2)
    head = neck.bone("head", (0, -4, 0), (-22, 0, 0))
    head.cube("skullh", -2, -2, -3, 4, 3, 4)
    head.cube("muzzle", -1, -1, -6, 2, 2, 3)
    head.bone("jaw", (0, 1, -3)).cube("jawc", -1, 0, -3, 2, 1, 3)
    for side, nm in SIDES2:
        head.bone("ear_" + nm, (side * 1.5, -2, 0), (-20, 0, side * 20)).cube("ear_" + nm, -0.5, -4, 0, 1, 4, 1, share=None if side > 0 else "ear_l")
    tail = body.bone("tail", (0, -2, 6), (50, 0, 0))
    tail.cube("tail0", -0.5, 0, 0, 1, 1, 6)
    tail.bone("tail_end", (0, 0, 6), (-20, 0, 0)).cube("tail1", -0.5, 0, 0, 1, 1, 6)
    for k in range(3):
        body.bone(f"sprig{k}", (-1 + k, -3, -4 + 4 * k), (0, 0, -30 + 30 * k)).cube(f"sprig{k}", -0.5, -4, 0, 1, 4, 1, share=None if k == 0 else "sprig0")
    r.look = [("neck", 0.35, 0.2), ("head", 0.45, 0.3)]
    r.pack()

    QUAD = ("0_l", "0_r", "1_l", "1_r")
    lagc = {"tail": 0.06, "tail_end": 0.12, "ear_l": 0.05, "ear_r": 0.05}
    K.moves(r, pounce="Bramble Pounce")
    spent = K.planted(r, {"body": {"pos": (0, -2, 0), "rot": (6, 0, 0)}, "neck": (20, 0, 0), "head": (10, 0, 0), "jaw": (14, 0, 0), "tail": (-30, 0, 0)}, QUAD)
    K.act(r, "pounce", {"body": {"pos": (0, -3, 2), "rot": (-8, 0, 0)}, "neck": (-16, 0, 0), "head": (10, 0, 0), "tail": (-50, 0, 0), "ear_l": (30, 0, 0), "ear_r": (30, 0, 0)},
          {"body": {"pos": (0, 2.5, -5), "rot": (-16, 0, 0)}, "neck": (10, 0, 0), "head": (-10, 0, 0), "jaw": (30, 0, 0), "leg0_l": (-60, 0, 0), "leg0_r": (-60, 0, 0),
           "leg1_l": (50, 0, 0), "leg1_r": (50, 0, 0)}, w=0.8, length=0.45, t_hit=0.08, plant=QUAD, lag=lagc, stuck=spent)
    K.plain(r, {"neck": (-18, 0, 0), "head": (6, 0, 0), "jaw": (26, 0, 0), "body": {"pos": (0, -0.6, 1.2)}}, {"neck": (16, 0, 0), "head": (-6, 0, 0), "jaw": (-2, 0, 0),
                                                                                                      "body": {"pos": (0, 0, -2.6)}}, plant=QUAD, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: {"neck": (2 * s(p), 6 * s(p, 0.3), 0), "head": (0, 0, 8 * bump(p, 0.6, 0.04)), "ear_l": (14 * bump(p, 0.3, 0.02), 0, 0),
                                      "ear_r": (14 * bump(p, 0.75, 0.02), 0, 0), "tail": (0, 10 * s(p * 2), 0), "seed": {"scale": (1 + 0.2 * s(p * 3),) * 3}}, n=16)
    K.loop(r, "walk", 0.7, lambda p: {"neck": (4 * s(p * 2, 0.1), 0, 2 * s(p)), "head": (-3 * s(p * 2, 0.2), 0, 0), "tail": (0, 14 * s(p), 0), "body": (1.2 * s(p * 2), 0, 2 * s(p))})
    K.loop(r, "run", 0.42, lambda p: {"neck": (14 + 6 * s(p * 2, 0.1), 0, 0), "head": (-10, 0, 0), "tail": (-40, 8 * s(p), 0), "body": (5 * s(p * 2, 0.25), 0, 0),
                                      "ear_l": (30, 0, 0), "ear_r": (30, 0, 0)})
    K.fallback_walk(r, [("leg0_l", 0.0, 1), ("leg0_r", 0.5, -1), ("leg1_l", 0.5, 1), ("leg1_r", 0.0, -1)])
    K.loop(r, "angry", 1.2, lambda p: {"neck": (14, 0, 0), "head": (-6, 0, 0), "jaw": (10 + 6 * s(p * 4), 0, 0), "body": {"pos": (0, -1.2, 0)}, "tail": (-44, 0, 0)}, plant=QUAD)
    K.flinch(r, {"neck": (-20, 0, 12), "body": {"pos": (0, 0.5, 1.5), "rot": (0, 0, 6)}, "tail": (20, 0, 0)}, plant=QUAD)
    side = {"body": (0, 0, 84), "neck": (20, 0, 0), "head": (10, 20, 0), "leg0_l": (30, 0, 0), "leg0_r": (8, 0, 0), "leg1_l": (-30, 0, 0), "leg1_r": (-8, 0, 0),
            "tail": (0, 0, 30), "jaw": (20, 0, 0)}
    start = K.planted(r, {"neck": (-30, 0, 0), "body": {"pos": (0, 1, 1)}}, QUAD)
    K.keyed(r, "death", 1.0, [(0, {}), (0.12, start)] + K.fall(r, 0.12, start, 0.6, side, steps=3)
            + [(0.72, K.on_floor(r, K.plus(side, {"body": (0, 0, -6)}))), (1.0, K.on_floor(r, K.plus(side, {"seed": {"scale": (0.02, 0.02, 0.02)}})))], lag=lagc)
    return r


def paint_bramble_hound(r, P):
    for nm in ("spine", "keel", "neckc", "tail0", "tail1"):
        P.paint(nm, ROOT, 3.0, p_strokes(2, 3, seed=2))
    P.paint("hoop0", ROOT, 2.6)
    for fname in ("north", "south"):  # a rib: a hoop, open in the middle
        f = P.face("hoop0", fname)
        for i, j in f.each():
            if 0 < i < f.w - 1 and 0 < j < f.h - 1:
                f.set(i, j, (0, 0, 0, 0))
    K.lit_cube(P, "seed", GREEN, HOT, a=245, hot=lambda fn, i, j, f: (i + j) % 2 == 0)
    P.paint("skullh", ROOT, 3.4, p_strokes(2, 2, seed=4), rim=0.6)
    P.paint("muzzle", ROOT, 3.0)
    P.paint("jawc", ROOT, 2.2)
    for i in (0, 3):
        K.lit(P, "skullh", "north", i, 1, 1, 1, GREEN, HOT, a=255)
    P.paint("ear_l", LEAF, 2.6)
    P.paint("sprig0", LEAF, 3.0)
    for tag in ("0", "1"):
        P.paint(f"thigh{tag}_l", ROOT, 3.0)
        P.paint(f"shinb{tag}_l", ROOT, 3.6)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Censer (incense_priest, the elite): a bronze incense burner hanging from four long stilts, smoke trailing. Its
# smoke heals the others; a blow while it gathers itself stops it.

def censer():
    r = Rig("incense_priest", (128, 64))
    r.shadow = 0.9
    body = K.at(r.bone("body", (0, 5, 0)), 5)
    body.cube("hub", -3, -2, -3, 6, 3, 6)
    body.cube("finial", -1, -5, -1, 2, 3, 2)
    K.splay_pairs(body, 0, (3, 3), (-2, 2), 6, 24, rise=40, yaws=(40, -40), thick=1)
    ch = body.bone("chain", (0, 1, 0))
    ch.cube("links", -0.5, -4, -0.5, 1, 10, 1)  # four px of it wait inside the hub: the chain pays out when the bowl drops
    ch.cube("lidc", -3, 6, -3, 6, 2, 6)
    ch.cube("bowl", -4, 8, -4, 8, 5, 8)
    ch.cube("footc", -2, 13, -2, 4, 1, 4)
    for k in range(2):
        ch.bone(f"tassel{k}", (-4 + 8 * k, 9, 0)).cube(f"tassel{k}", -0.5, 0, 0, 1, 5, 0, share=None if k == 0 else "tassel0")
    r.pack()

    FOUR = K.tags(2)
    lagc = {"chain": 0.1, "tassel0": 0.16, "tassel1": 0.16}
    K.moves(r, fangs="Root Fangs", heal="Incense", smoke="Incense Smoke")
    K.act(r, "fangs", {"body": {"pos": (0, 4, 0), "rot": (-8, 0, 0)}, "chain": (-30, 0, 0)}, {"body": {"pos": (0, -1.5, 0), "rot": (4, 0, 0)}, "chain": {"rot": (-4, 0, 0), "pos": (0, -2.5, 0)}},
          w=1.0, length=0.5, t_hit=0.1, plant=FOUR, lag=lagc, open_pose={"body": {"pos": (0, -1.5, 0)}, "chain": {"rot": (6, 0, 0), "pos": (0, -2.5, 0)}})
    K.act(r, "heal", {"body": {"pos": (0, 2, 0)}, "chain": (0, 0, 34)}, {"body": {"pos": (0, 1, 0)}, "chain": (0, 0, -40)}, w=1.5, length=0.7, t_hit=0.2,
          plant=FOUR, lag=lagc, after={"chain": (0, 0, 26)})
    K.act(r, "smoke", {"body": {"pos": (0, 3, 1), "rot": (-10, 0, 0)}, "chain": (36, 0, 0)}, {"body": {"pos": (0, 0, -2), "rot": (10, 0, 0)}, "chain": (-50, 0, 0)},
          w=0.9, length=0.6, t_hit=0.12, plant=FOUR, lag=lagc, after={"chain": (30, 0, 0)})
    K.plain(r, {"body": {"pos": (0, 2, 1.5)}, "chain": (30, 0, 0)}, {"body": {"pos": (0, -1, -2)}, "chain": (-56, 0, 0)}, plant=FOUR, lag=lagc)
    K.loop(r, "idle", 6.0, lambda p: {"chain": (4 * s(p), 0, 6 * s(p, 0.25)), "body": {"pos": (0, 0.6 * s(p * 2), 0)}}, n=12)
    K.loop(r, "walk", 1.0, lambda p: {"chain": (8 * s(p * 2, 0.2), 0, 8 * s(p, 0.2)), "body": (1.5 * s(p * 2), 0, 2 * s(p))})
    K.loop(r, "run", 0.7, lambda p: {"chain": (16 + 8 * s(p * 2, 0.2), 0, 10 * s(p, 0.2)), "body": (5, 0, 2.5 * s(p))})
    K.fallback_walk(r, K.alternating(2), axis="y")
    K.loop(r, "angry", 1.6, lambda p: {"chain": (0, 0, 20 * s(p)), "body": {"pos": (0, -2, 0)}}, plant=FOUR)
    K.flinch(r, {"body": {"pos": (0, -2, 1.5), "rot": (0, 0, 8)}, "chain": (0, 0, 34)}, plant=FOUR)
    fold = {f"leg{t}": (0, 0, (-30 if t.endswith("l") else 30)) for t in FOUR}
    fold.update({f"shin{t}": (0, 0, (60 if t.endswith("l") else -60)) for t in FOUR})
    start = K.planted(r, {"body": {"pos": (0, 3, 0)}, "chain": (0, 0, 30)}, FOUR)
    low = dict(fold, body=(0, 0, 24), chain=(0, 0, -24))
    K.keyed(r, "death", 1.3, [(0, {}), (0.15, start)] + K.fall(r, 0.15, start, 0.85, low, steps=4) + [(1.0, K.on_floor(r, K.plus(low, {"chain": (0, 0, 16)}))),
                                                                                                    (1.3, K.on_floor(r, low))], lag=lagc)
    return r


def paint_censer(r, P):
    P.paint("hub", BRONZE, 2.6, rim=0.9)
    P.paint("finial", BRONZE, 3.4)
    P.paint("links", BRONZE, 2.0, p_bands(2, 1.2))
    P.paint("lidc", BRONZE, 3.0, rim=0.9)
    P.paint("bowl", BRONZE, 2.4, p_bands(5, 0.5), rim=0.9, ao=0.9)
    P.paint("footc", BRONZE, 1.8)
    for fname in K.SIDES:  # holes cut in the bowl, the coals behind them
        for i in (1, 3, 5):
            K.lit(P, "bowl", fname, i, 1 + (i // 2) % 2, 1, 2, GREEN, HOT, a=235)
    f, g = P.face("lidc", "top"), P.face("lidc", "top", glow=True)
    for i, j in f.each():
        if (i + j) % 2 == 0 and 0 < i < f.w - 1 and 0 < j < f.h - 1:
            f.set(i, j, hexc(GREEN))
            g.set(i, j, hexc(GREEN, 150))
    for fname in ("north", "south"):
        f = P.face("tassel0", fname)
        for i, j in f.each():
            f.set(i, j, PETAL.at(3.0 - j * 0.3))
    P.paint("thigh0_l", BRONZE, 2.4)
    P.paint("shinb0_l", ROOT, 3.4, p_bands(5, 0.6))
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------
# The Overgrowth (the_overgrowth, the Warden): a carved pillar torn loose and stood up, a slab of roof still on its head,
# roots for arms. It is slow to turn; the roots it drives into the floor come up in a ring.

def overgrowth():
    r = Rig("the_overgrowth", (128, 128))
    r.shadow = 1.4
    body = K.at(r.bone("body", (0, 10, 0)), 10)
    body.cube("base", -6, -4, -6, 12, 6, 12)
    K.legs2(body, "1", (4, 2, 0), 7, 7, 28, thick=(5, 4), foot=(6, 3, 6, -3))
    col = body.bone("chest", (0, -4, 0), (3, 0, 0))
    col.cube("drum0", -5, -10, -5, 10, 10, 10)
    col.cube("drum1", -4, -20, -4, 8, 10, 8)
    col.cube("capital", -6, -23, -6, 12, 3, 12)
    head = col.bone("head", (0, -23, 0), (0, 0, 7))
    head.cube("roof", -9, -3, -8, 18, 3, 16)
    head.cube("ridge", -5, -5, -6, 10, 2, 12)
    for side, nm in SIDES2:
        arm = col.bone("arm_" + nm, (side * 5, -15, 0), (-10, 0, side * -16))
        arm.cube("rootu_" + nm, -2, 0, -2, 4, 13, 4, share=None if side > 0 else "rootu_l")
        fore = arm.bone("fore_" + nm, (0, 12, 0), (-22, 0, side * 10))
        fore.cube("rootl_" + nm, -1.5, 0, -1.5, 3, 12, 3, share=None if side > 0 else "rootl_l")
        fore.bone("fore_tip_" + nm, (0, 11, 0), (20, 0, 0)).cube("rootf_" + nm, -1, 0, -1, 2, 5, 2, share=None if side > 0 else "rootf_l")
    for k in range(3):
        head.bone(f"moss{k}", (-7 + 6 * k, 0, -8 + 0.01 * k)).cube(f"hang{k}", -1.5, 0, 0, 3, 6 + 2 * (k % 2), 0, share=None)
    r.look = [("head", 0.2, 0.1)]
    r.pack()

    legs = ("1_l", "1_r")
    lagc = {"fore_l": 0.05, "fore_r": 0.05, "fore_tip_l": 0.1, "fore_tip_r": 0.1, "head": 0.06, **{f"moss{k}": 0.12 for k in range(3)}}
    both = lambda u, f_, z=0: {"arm_l": (u, 0, -z), "arm_r": (u, 0, z), "fore_l": (f_, 0, 0), "fore_r": (f_, 0, 0)}  # noqa: E731
    K.moves(r, fist="Temple Fist", ring="Root Ring", vines="Strangling Vines")
    K.act(r, "fist", dict(both(-150, -24, 8), chest=(-14, 0, 0), head=(0, 0, -10), body={"pos": (0, 1.5, 0)}),
          dict(both(-36, 18), chest=(20, 0, 0), head=(0, 0, 6), body={"pos": (0, -2, -1)}), w=1.5, length=0.6, t_hit=0.11, plant=legs, lag=lagc,
          open_pose=dict(both(-30, 20), chest=(22, 0, 0), body={"pos": (0, -2.5, -1)}))
    K.act(r, "ring", dict(both(-60, -50, 30), chest=(-6, 0, 0), body={"pos": (0, 1, 0)}), dict(both(14, 30, 10), chest=(10, 0, 0), body={"pos": (0, -4, 0)}),
          w=1.0, length=0.6, t_hit=0.12, plant=legs, lag=lagc, open_pose=dict(both(14, 30, 10), chest=(8, 0, 0), body={"pos": (0, -4, 0)}))
    K.act(r, "vines", {"chest": (0, 24, 0), "arm_r": (10, 0, 30), "fore_r": (-50, 0, 0)}, {"chest": (6, -26, 0), "arm_r": (-86, 0, -6), "fore_r": (10, 0, 0), "fore_tip_r": (-20, 0, 0)},
          w=1.3, length=0.6, t_hit=0.1, plant=legs, lag=lagc)
    K.plain(r, {"chest": (0, -20, 0), "arm_l": (10, 0, -34), "fore_l": (-50, 0, 0)}, {"chest": (6, 26, 0), "arm_l": (-80, 0, 10), "fore_l": (12, 0, 0)},
            heavy=(dict(both(-140, -20, 6), chest=(-12, 0, 0)), dict(both(-40, 16), chest=(18, 0, 0), body={"pos": (0, -1.5, 0)})), plant=legs, lag=lagc)
    K.loop(r, "idle", 9.0, lambda p: {"chest": (0.8 * s(p), 0, 0.6 * s(p, 0.3)), "head": (0, 0, 1.2 * s(p, 0.5)), "arm_l": (2 * s(p, 0.2), 0, 0), "arm_r": (2 * s(p, 0.7), 0, 0),
                                      "fore_tip_l": (8 * s(p * 2), 0, 0), "fore_tip_r": (8 * s(p * 2, 0.4), 0, 0)}, n=12)
    K.loop(r, "walk", 1.4, lambda p: {"chest": (2 + 1.5 * s(p * 2), 0, 3 * s(p)), "head": (0, 0, -2.5 * s(p, 0.12)), "arm_l": (9 * s(p), 0, 0), "arm_r": (9 * s(p, 0.5), 0, 0)})
    K.loop(r, "run", 0.9, lambda p: {"chest": (8 + 2.5 * s(p * 2), 0, 4 * s(p)), "head": (0, 0, -3.5 * s(p, 0.12)), "arm_l": (16 * s(p), 0, 0), "arm_r": (16 * s(p, 0.5), 0, 0)})
    K.fallback_walk(r, [("leg1_l", 0.0, 1), ("leg1_r", 0.5, -1)], swing=(16, 24))
    K.loop(r, "angry", 2.4, lambda p: dict(both(-24, -30, 12), chest=(5 + s(p * 2), 0, 0), body={"pos": (0, -1.2, 0)}), plant=legs)
    K.flinch(r, {"chest": (-7, 0, 4), "head": (0, 0, -8)}, plant=legs)
    knees = K.planted(r, dict(both(20, 20, 10), body={"pos": (0, -4, 0)}, chest=(8, 0, 0), head=(0, 0, 16)), legs)
    down = dict(both(-80, -10, 30), body={"rot": (82, 0, 4)}, chest=(4, 0, 0), head=(20, 0, 30), leg1_l=(-50, 0, 0), leg1_r=(-60, 0, 0), shin1_l=(70, 0, 0), shin1_r=(80, 0, 0))
    skip = ("rootu", "rootl", "rootf", "hang")
    K.keyed(r, "death", 2.0, [(0, {}), (0.25, K.planted(r, {"chest": (-10, 0, 0), "head": (0, 0, -12)}, legs)), (0.8, knees)]
            + K.fall(r, 0.8, knees, 1.4, down, steps=4, skip=skip) + [(1.56, K.on_floor(r, K.plus(down, {"body": (-5, 0, 0)}), skip=skip)), (2.0, K.on_floor(r, down, skip=skip))],
            lag=lagc)
    return r


def paint_overgrowth(r, P):
    rng = P.rng
    for nm, base in (("base", 2.0), ("drum0", 2.6), ("drum1", 2.8), ("capital", 3.4), ("roof", 2.2), ("ridge", 2.6)):
        P.paint(nm, STONE, base, p_stone(rng, 0.7, 5), rim=0.6, ao=0.6)
    for nm in ("drum0", "drum1"):  # fluting, and a carved band
        for fname in K.SIDES:
            f = P.face(nm, fname)
            for i, j in f.each():
                if i % 2 == 1 and 1 < j < f.h - 2:
                    f.set(i, j, STONE.at(1.6))
            for i in range(f.w):
                f.set(i, 1, STONE.at(4.2))
    cracks(P, "drum0", ("north", "west"), n=1, a=220)
    cracks(P, "drum1", ("north", "east"), n=1, a=220, seed=3)
    moss(P, "roof", ("top",), every=5)
    moss(P, "ridge", ("top",), every=4)
    moss(P, "capital", ("top", "north"), every=6)
    for nm in ("rootu_l", "rootl_l", "rootf_l"):
        P.paint(nm, ROOT, 3.0, p_strokes(2, 5, seed=6), rim=0.4)
    for k in range(3):
        K.tatter(P, f"hang{k}", LEAF, 2.8, seed=k, fall=0.2)
    P.paint("thigh1_l", STONE, 2.4, p_stone(rng, 0.6, 4))
    P.paint("shinb1_l", STONE, 2.0, p_stone(rng, 0.6, 4))
    P.paint("footb1_l", STONE, 2.8, rim=0.8)
    return P.skin, P.glow


# ---------------------------------------------------------------------------------------------------------------------

STILL = {"blink": [0, 0]}
MONSTERS = [
    K.Monster("vine_creaker", "The Creaker", creaker, paint_creaker, kind="crawler", box=(1.1, 1.9), sunk=("vine", "thigh", "shinb"),
              motion=lambda r: {"gait": K.crawl_gait(K.room(r, K.legs_of(r), 0.6), duty=(0.76, 0.56), lift=(2.6, 4.0), swing=0.26, bob=0.4, speed=0.9),
                                "body": K.body(headLevel=0.5, twist=0.15, slope=0.8, land=1.3, shift=0.3, still=True)},
              life=dict(STILL, temperament="monster", breathDepth=0.0, idles={"settle": 1}, fidget=[14.0, 30.0], headSpeed=3.0, mass=2.2, arms=0.0),
              voice={"ambient": [("entity.creaking.ambient", 0.6, 0.8), ("block.stone.step", 0.5, 0.6)], "hurt": [("block.stone.hit", 0.9, 0.7), ("entity.creaking.sway", 0.6, 0.8)],
                     "death": [("block.stone.break", 1.0, 0.6), ("entity.creaking.death", 0.7, 0.8)], "step": [("block.roots.step", 0.4, 0.7)]}),
    K.Monster("thorn_archer", "The Thorn Bud", thorn_bud, paint_thorn_bud, kind="strider", box=(0.6, 1.9),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.62, 0.44), lift=(1.8, 3.0), crouch=0.5, swing=0.22, bob=0.4, roll=0.05),
                                "body": K.body(headLevel=0.5, twist=0.3, land=1.2, shift=0.4)},
              life=dict(STILL, temperament="monster", idles={"look": 3, "settle": 1}, springHz=2.2, springGain=1.3, bob=0.8, sway=2.4),
              voice={"ambient": [("block.vine.step", 0.6, 0.8), ("block.cherry_leaves.step", 0.5, 0.8)], "hurt": [("block.bamboo.hit", 0.8, 0.9), ("block.vine.hit", 0.8, 0.8)],
                     "death": [("block.bamboo.break", 0.9, 0.8), ("block.cherry_leaves.break", 0.8, 0.8)], "step": [("block.vine.step", 0.35, 1.0)]}),
    K.Monster("thorn_hound", "The Bramble Hound", bramble_hound, paint_bramble_hound, kind="runner", box=(0.7, 1.2),
              motion=lambda r: {"gait": K.gait("ripple-alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.66, 0.4), lift=(2.0, 3.6), crouch=0.5, swing=0.22, bob=0.4, roll=0.035,
                                               lag=-0.25, lead=0.12, rear=1.0),
                                "body": K.body(headLevel=0.5, twist=0.25, slope=0.85, land=1.2, shift=0.4, nod=0.05, still=True, creep=1.2)},
              life=dict(STILL, temperament="hunter", idles={"look": 3, "sniff": 2, "flick": 2, "stretch": 1}, springHz=2.6),
              voice={"ambient": [("entity.fox.aggro", 0.5, 0.7), ("block.vine.step", 0.5, 0.7)], "hurt": [("entity.fox.hurt", 0.7, 0.7), ("block.bamboo.hit", 0.6, 0.8)],
                     "death": [("entity.fox.death", 0.8, 0.7), ("block.bamboo.break", 0.7, 0.8)], "step": [("block.grass.step", 0.3, 1.1)]}),
    K.Monster("incense_priest", "The Censer", censer, paint_censer, kind="crawler", box=(1.0, 1.7), sunk=("tassel",),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.6), duty=(0.7, 0.5), lift=(3.0, 5.0), crouch=0.6, swing=0.34, bob=0.5, roll=0.03, speed=0.5),
                                "body": K.body(headLevel=0.0, twist=0.1, slope=0.7, land=1.3, shift=0.9)},
              life=dict(STILL, temperament="spirit", breathDepth=0.0, idles={"settle": 2}, springHz=0.9, springDamping=0.12, springGain=1.6, mass=1.4),
              voice={"ambient": [("block.chain.step", 0.6, 0.7), ("block.campfire.crackle", 0.5, 0.8)], "hurt": [("block.copper.hit", 0.9, 0.7), ("block.chain.hit", 0.7, 0.7)],
                     "death": [("block.copper.break", 1.0, 0.6), ("block.fire.extinguish", 0.7, 0.8)], "step": [("block.bamboo.step", 0.4, 0.7)]}),
    K.Monster("the_overgrowth", "The Overgrowth", overgrowth, paint_overgrowth, kind="strider", box=(1.4, 3.1), sunk=("rootf", "rootl", "hang"),
              motion=lambda r: {"gait": K.gait("alternate", K.room(r, K.legs_of(r), 0.5), duty=(0.68, 0.5), lift=(2.5, 4.0), crouch=0.5, swing=0.4, bob=1.0, roll=0.06),
                                "body": K.body(headLevel=0.0, twist=0.12, slope=0.5, land=1.6, shift=0.6)},
              life=dict(STILL, temperament="beast", breathDepth=0.0, idles={"settle": 2, "look": 1}, mass=3.4, arms=0.6, springHz=1.0, springGain=1.3, headSpeed=2.6),
              voice={"ambient": [("block.stone.step", 0.8, 0.45), ("entity.creaking.ambient", 0.6, 0.6)], "hurt": [("block.stone.hit", 1.0, 0.5), ("entity.iron_golem.hurt", 0.6, 0.6)],
                     "death": [("block.stone.break", 1.0, 0.4), ("entity.iron_golem.death", 0.8, 0.6)], "step": [("entity.iron_golem.step", 0.7, 0.6)]}),
]
