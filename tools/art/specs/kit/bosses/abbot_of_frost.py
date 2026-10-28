"""The Abbot of Frost (SPEC §35.11, docs/bosses/ROLLOUT.md): the Frost Monastery's Lord. The Pilgrim, picked from his
silhouette round: a huge monk bent nearly double under a block of glacier ice bigger than he is, roped and chained to
his back, and frozen deep inside it, glowing gold through the ice, the monastery's heart. He carries it everywhere.

Indigo wool gone grey with frost, snow lying thick on his hood and his shoulders and on top of the ice, a hemp rope
for a belt and hemp straps over his shoulders that his fists never let go of. His face is a dark hollow in the hood
with two pale eyes in it and a beard of icicles hanging out of it; a string of ice beads swings from his neck, his
hands and feet are wrapped in rags, and a line of faded prayer flags is strung between the ice's spikes. He has no
staff and no weapon: he fights with his fists, his weight and the glacier (the Pedlar's pack is a proud showman's shop;
this one is a penance, and it crushes him).

Moves (SPEC §35.10): the Backhand (a fist off a strap and across, LIGHT) and Two Fists after it (both fists raised
over the hood and brought down on the ground, HEAVY, with a held top), the Weight for whoever is behind him (he throws
himself back and the glacier crushes down on them), the Frost Hymn (he lifts his face and sings five notes of frost),
Ice Spikes (he heaves the glacier up off his back and lets it drop: the shock comes up through the ground), the Choir
(he prays, palms together before his face, and the heart beats), Blizzard (arms flung wide, swaying), the Avalanche (he
puts his head down and charges), Frost Nova (curled up tight, then thrown open with a roar) and the Glacial Vow (he
straightens to his full height, then throws himself down on his knees and swings the whole glacier over his head onto
the ground before him like a hammer: the signature). He opens on his knees in prayer and heaves himself up under the
weight. He dies on his knees, pressed down onto his face by the glacier.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "abbot_of_frost"
SCALE = 1.9  # the hitbox on the EVOKER model: 1.7 x 3.8 blocks; the body is modelled at true size

P = plans.Biped(leg=22, thigh=11, foot=(7, 3, 10), pelvis=(16, 6, 12), spine=(14, 10, 11), chest=(22, 16, 15), neck=(6, 3, 6),
                head=(12, 12, 13), arm=(7, 15, 7), forearm=(7, 12, 7), hand=(6, 7, 6), stance=4.5, shoulder=11, hunch=36, leg_w=(7, 7))
HUNCH = (14, 22)  # the spine's and the chest's bend at rest: he is bent nearly double under it
LEAN = 14  # the glacier leans forward with his back (it rests on it), short of his bend
GRIP = {"l": (5.5, -8, -9.6), "r": (-5.5, -8, -9.6)}  # his fists on the straps, in the chest's space
POLE = {"l": (1, 0, 0.5), "r": (-1, 0, 0.5)}
FIST = {"l": ((0.1, -0.35, -0.93), (1, 0, 0)), "r": ((-0.1, -0.35, -0.93), (1, 0, 0))}  # a fist round a strap: knuckles up
ARMS = tuple(f"{b}_{s}" for b in ("shoulder", "arm", "forearm", "hand") for s in "lr")
ON_TIME = {b: 0 for b in ARMS}  # his fists stay where they're sent (on the straps, on the ground): no trailing arms
MOUTH = (0, -4, -7)  # the hollow of the hood where his breath comes out, in the head's space
GLACIER_TOP = (2, -36, 10)  # the ice's crown, in the glacier's space


def body():
    r = R.Rig(ID, tex=(512, 512), translucent=True)
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -8, -4, -6, 16, 6, 12)
    # short, thick legs under a heavy wool robe that falls to the snow and drags behind him; the robe hangs from the
    # thighs (front, outside, back) and its lower half is on a spring
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -3.5, -1, -3.5, 7, 12, 7)
        xf = -4.5 if side > 0 else -5.5  # the front and back panels meet in the middle
        zf = -5.8 if side > 0 else -5.5  # the left laps over the right
        xs = 5.5 if side > 0 else -6.5  # the outside panel
        th.cube("robef_" + nm, xf, -1, zf, 10, 12, 1)
        th.cube("robes_" + nm, xs, -1, -5, 1, 12, 10)
        th.cube("robeb_" + nm, xf, -1, 4.5, 10, 12, 1)
        sk = th.bone("skirt_" + nm, (0, 11, 0), (0, 0, -side * 6))
        sk.cube("skirtf_" + nm, xf, 0, zf, 10, 9, 1)
        sk.cube("skirts_" + nm, xs, 0, -5, 1, 9, 10)
        sk.cube("skirtb_" + nm, xf, 0, 4.5, 10, 10, 1)
        sk.cube("train_" + nm, xf, 9, 5.5, 10, 1, 3)  # the hem dragging behind in the snow
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -3, 0, -3, 6, 8, 6)
        sn.cube("wrap_" + nm, -3.5, 3, -3.5, 7, 5, 7)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -3.5, 0, -7, 7, 3, 10)
        ft.cube("sole_" + nm, -4, 2, -7.5, 8, 1, 11)
    # the belly under a rope belt, knotted on his left with its ends hanging
    sp = pel.bone("spine", (0, -2, 0), (HUNCH[0], 0, 0))
    sp.cube("spine", -7, -10, -5.5, 14, 10, 11)
    sp.cube("belt", -7.5, -2, -6, 15, 2, 12)
    sp.cube("knot", 2, -2.5, -6.8, 3, 3, 1)
    rt = sp.bone("rope_tail", (3.5, -1, -6.8), (-HUNCH[0], 0, 0))
    rt.cube("rope_tail0", -1.5, 0, -0.5, 1, 9, 1)
    rt.cube("rope_tail1", 0, 0, -0.5, 1, 7, 1)
    # the great bent back, snow lying on the hump of it and thick on both shoulders; two hemp straps over the shoulders
    # and a rope across his breast hold the ice to him
    ch = sp.bone("chest", (0, -10, 0), (HUNCH[1], 0, 0))
    ch.cube("chest", -11, -16, -7.5, 22, 16, 15)
    ch.cube("hump", -9, -17, -3, 18, 3, 11)
    ch.cube("snow_l", 3, -18.5, -6.5, 9, 3, 13)
    ch.cube("snow_r", -12, -18.5, -6.5, 9, 3, 13)
    ch.cube("snow_b", -8, -19, -1, 16, 2, 9)
    for nm, x in (("strap_l", 4), ("strap_r", -7)):
        ch.cube(nm, x, -16, -8.5, 3, 14, 1)
    ch.cube("breast", -5, -10, -8.9, 10, 2, 1)
    # the string of ice beads round his neck, hung plumb from the collar (a spring swings it)
    bd = ch.bone("beads", (0, -15, -8.2), (-sum(HUNCH), 0, 0))
    for k, (x, y) in enumerate(((3, 1), (2.6, 4.5), (2, 8), (-3, 1), (-2.6, 4.5), (-2, 8))):
        bd.cube(f"bead{k}", x - 1, y, -1, 2, 2, 2)
    bd.cube("bead_big", -1.5, 10.5, -1.5, 3, 3, 3)
    bd.cube("tassel", -0.5, 13.5, -0.5, 1, 4, 1)
    # the hood: a deep cowl with snow on it and a point trailing behind; inside it only dark, two pale eyes and a
    # frost-white beard hanging icicles
    nk = ch.bone("neck", (0, -16, -5), (-HUNCH[1] + 4, 0, 0))
    nk.cube("neck", -3, -3, -3, 6, 3, 6)
    hd = nk.bone("head", (0, -3, -1), (-HUNCH[0], 0, 0))
    hd.cube("hood", -6, -11, -7, 12, 12, 13)
    hd.cube("hood_tip", -3, -9, 5.5, 6, 6, 3)
    hd.cube("hoodsnow", -6.5, -12, -6, 13, 2, 11)
    hd.cube("rim", -6.5, -11.5, -7.8, 13, 2, 1)
    hd.cube("rim_l", 4.5, -10, -7.8, 2, 10, 1)
    hd.cube("rim_r", -6.5, -10, -7.8, 2, 10, 1)
    hd.cube("hollow", -5, -9.5, -7.3, 10, 9, 1)
    hd.cube("eye_l", 1, -7, -7.6, 2, 1, 1)
    hd.cube("eye_r", -3, -7, -7.6, 2, 1, 1)
    br = hd.bone("breath", (MOUTH[0], MOUTH[1], MOUTH[2] - 1))  # his breath smoking in the cold (scaled to nothing between breaths)
    br.cube("breath", -2, -1.5, -4, 4, 3, 4)
    bdr = hd.bone("beard", (0, -2.5, -7), (HUNCH[0] - 4, 0, 0))
    bdr.cube("beard0", -3.5, 0, -1, 7, 3, 2)
    for k, (x, ln) in enumerate(((-3, 5), (-1.5, 8), (0, 6), (1.5, 9), (2.5, 4))):
        bdr.cube(f"icicle{k}", x, 2.5, -0.5, 1, ln, 1)
    # the arms in wide monk's sleeves, icicles hanging off the cuffs; big hands wrapped in rags
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 10, -13, 0))
        arm = sh.bone("arm_" + nm, (side * 1, 0, 0))
        arm.cube("arm_" + nm, -3.5, -1, -3.5, 7, 15, 7)
        fa = arm.bone("forearm_" + nm, (0, 14, 0))
        fa.cube("forearm_" + nm, -3, 0, -3, 6, 12, 6)
        fa.cube("cuff_" + nm, -4.5, 5, -4.5, 9, 7, 9)
        fa.cube("sicicle_" + nm, side * 2 - 0.5, 12, -2, 1, 3, 1)
        hd_ = fa.bone("hand_" + nm, (0, 12, 0))
        hd_.cube("hand_" + nm, -3, 0, -3, 6, 7, 6)
        hd_.cube("rag_" + nm, -3.5, 1, -3.5, 7, 3, 7)
    # the glacier, stood upright on his bent back from the top of it: the heart first (the ice is drawn over it), then
    # the block, its spikes, the icicles under it, the rope and the chain round it, the snow on it and the flags
    gl = ch.bone("glacier", (0, -15, 7.5), (-sum(HUNCH) + LEAN, 0, 0))
    ht = gl.bone("heart", (0, -12, 9))
    ht.cube("heart_l", -4, -3, -2, 4, 4, 4)
    ht.cube("heart_r", 0, -3, -2, 4, 4, 4)
    ht.cube("heart_b", -3, 1, -2, 6, 3, 4)
    ht.cube("heart_p", -1, 4, -1.5, 2, 2, 3)
    ht.cube("heart_f", -1, -7, -1, 2, 4, 2)
    # the block is no box: a core with lumps of older ice frozen onto it all round, stepped and uneven
    ice = gl.bone("ice", (0, 0, 0))
    ice.cube("ice0", -12, -26, 1, 24, 32, 16)
    for k, box in enumerate(((-16, -12, 3, 6, 16, 12), (9, -31, 3, 8, 15, 12), (-11, -32, 4, 13, 7, 12), (-8, 5, 2, 15, 5, 13),
                             (1, -20, 16, 10, 15, 4), (-13, -26, 12, 7, 12, 6), (11, -14, 6, 5, 12, 9))):
        ice.cube(f"ice{k + 1}", *box)
    for k, (at, rot, box) in enumerate(((((-6, -31, 8), (10, 0, 16), (-4, -12, -4, 8, 13, 8))),
                                         (((11, -30, 11), (-10, 0, -14), (-4, -15, -4, 8, 16, 8))),
                                         (((1, -26, 15), (-8, 0, 6), (-3, -9, -3, 6, 10, 6))),
                                         (((15, -10, 9), (0, 0, -34), (-3, -10, -4, 6, 11, 8))),
                                         (((-16, 0, 11), (10, 0, 36), (-3, -9, -4, 6, 10, 7))))):
        sb = ice.bone(f"spike{k}", at, rot)
        sb.cube(f"spike{k}", *box)
    for k, box in enumerate(((-9, 10, 3, 2, 5, 2), (-3, 10, 11, 1, 7, 1), (4, 10, 5, 2, 4, 2), (9, 6, 14, 1, 6, 1))):
        ice.cube(f"drip{k}", *box)
    ice.cube("ropeband", -12.5, -23, 0.5, 25, 2, 17)
    ice.cube("chainband", -12.5, -2, 0.5, 25, 2, 17)
    ice.cube("gsnow", -10, -33.5, 3, 14, 2, 12)
    fl = ice.bone("flags", (2, -43, 10))
    fl.cube("cord", -11, 0, -0.5, 23, 1, 1)
    for k in range(5):
        fl.cube(f"flag{k}", -10.5 + 4.5 * k, 1 + (1 if k in (1, 2, 3) else 0) + (1 if k == 2 else 0), -0.5, 4, 6, 1)
    # the fists at rest on the straps
    fr = core.frames(r, {})
    for s in "lr":
        q = core.point(fr, "chest", GRIP[s])
        ik.rest_limb(r, "arm_" + s, "forearm_" + s, "hand_" + s, (q[0], 24 - q[1], q[2]), POLE[s])
    fr = core.frames(r, {})
    for s in "lr":
        r.by_name["hand_" + s].rot = ik.orient(r, fr, "hand_" + s, *FIST[s])
    r.look = [("head", 0.5, 0.45), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0 * 1.6
    r.shadow = 1.1
    # secondary parts (SPEC §35.10): the robe's heavy hem, the rope's ends, the beads, the beard's icicles and the flags
    # trail him; the glacier itself carries a little weight on his back, and the heart beats on its own
    r.springs = [(f"skirt_{s}", 0.4, 1.8, 0.32) for s in "lr"] + [("rope_tail", 0.6, 2.0, 0.25), ("beads", 0.55, 1.6, 0.2),
                                                                  ("beard", 0.25, 3.0, 0.3), ("flags", 0.8, 2.2, 0.14),
                                                                  ("glacier", 0.05, 2.4, 0.5)]
    r.guard = ["hand_l", "hand_r", "ice"]  # his fists and the ice never go into the ground
    return r


WOOL = Ramp("#0d1018", "#131823", "#1a202d", "#222a39", "#2c3547", "#384256", "#465166", "#576379")
SNOW = Ramp("#6f7d8e", "#8d9bac", "#aab8c8", "#c4d1de", "#dbe6ef", "#edf3f8", "#fafcff")
ICE = Ramp("#0b2235", "#11344f", "#194a6a", "#236284", "#307c9e", "#4596b6", "#62b0cc", "#8ccbe0", "#bde4f0")
GOLD = Ramp("#3a2006", "#5c340c", "#86521a", "#b47624", "#dc9c30", "#f6c450", "#ffe590", "#fff6d0")
HEMP = Ramp("#2a2014", "#3a2d1c", "#4e3e28", "#635034", "#7c6744", "#968258")
IRON = Ramp("#0b0d10", "#14171b", "#1f2328", "#2d3238", "#40464e", "#5a616a")
SKIN = Ramp("#1c232e", "#28313f", "#36414f", "#465263", "#5a677a")
RAG = Ramp("#24211e", "#332e29", "#443d36", "#574e45", "#6c6157", "#857869")
STRAW = Ramp("#3a2c10", "#52401a", "#6c5524", "#886c30", "#a4863e")
EYE = ("#3c9cd0", "#7ccaf0", "#c8f0ff")
FLAGS = ("#7c2c24", "#9c7826", "#cfc8b8", "#2c4a78", "#3a643a")  # faded red, saffron, white, blue, green


def paint(r, P_):
    """Values (SPEC §35.4): the gold heart in the ice brightest and warmest, then the snow and the ice, the hemp and the
    rags in the middle, the frosted wool dark, and the hollow of his hood the darkest thing on him (two pale eyes)."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    # the wool: a coarse weave, darned patches in a paler wool, frost crusting the lower edges
    weave = lambda f, i, j: (-0.35 if (i + 2 * j) % 5 == 0 else 0.25 if R._h(f.cube.name + f.name, i // 3 * 7 + j // 3) % 11 == 0 else 0)
    wool = of("robe", "skirt", "spine", "pelvis", "chest", "hump", "arm_", "forearm_", "cuff_", "hood", "rim", "neck", "thigh_")
    for c in wool:
        P_.paint(c, WOOL, 2.8, weave, grad=1.1, rim=0.8, ao=0.9, noise=0.25)
    # the front of the robe: a seam down the middle, a darned patch or two in paler wool, frost in the folds
    for c in of("robef_", "skirtf_"):
        f = P_.face(c, "north")
        seam = 0 if c.endswith("_l") else f.w - 1
        for i, j in f.each():
            if i == seam:
                f.set(i, j, WOOL.at(4.4))
            elif i % 4 == 2 and R._h(c, i * 7 + j // 3) % 3 == 0:
                f.set(i, j, WOOL.at(4.0 + (0.6 if j % 5 == 0 else 0)))
    for c, (x0, y0, w, h) in (("robef_r", (2, 3, 4, 4)), ("skirtf_l", (4, 1, 4, 3)), ("robeb_l", (3, 5, 5, 4))):
        f = P_.face(c, "north" if c.startswith("robef") or c.startswith("skirtf") else "south")
        for i, j in f.each():
            if x0 <= i < x0 + w and y0 <= j < y0 + h:
                edge = i in (x0, x0 + w - 1) or j in (y0, y0 + h - 1)
                f.set(i, j, WOOL.at(5.2 if edge and (i + j) % 2 else 4.4 if edge else 3.8 + 0.4 * ((i + j) % 2)))
    for c in of("skirtf_", "skirts_", "skirtb_", "train_", "cuff_"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                hem = f.h - 1 - R._h(c + fname, i // 2) % 3  # the frost along the hem, ragged
                if j >= hem and R._h(c, i * 3 + j) % 4:
                    f.set(i, j, SNOW.at(1.4 + 0.6 * (j - hem) + 0.5 * (R._h(c, i * 5 + j) % 3)))
    P_.paint("rim", SNOW, 3.0, lambda f, i, j: -1.0 if f.name in SIDES and j >= 1 + R._h("rim" + f.name, i) % 2 else 0, rim=0.8, noise=0.3)
    for c in ("rim_l", "rim_r"):
        P_.paint(c, WOOL, 3.4, lambda f, i, j: SNOW.c[2] if R._h(f.cube.name + f.name, i * 7 + j) % 5 == 0 else 0, rim=0.6, noise=0.3)
    P_.paint("hollow", WOOL, 0.0, grad=0.0, rim=0.0, ao=0.0)
    P_.paint("neck", WOOL, 0.6)
    # the snow: bright on top, its edges slumping over the sides in drips
    for c in of("snow_", "hoodsnow", "gsnow"):
        P_.paint(c, SNOW, 4.2, lambda f, i, j: (1.2 if f.name == "top" and R._h(f.cube.name, i * 13 + j) % 5 == 0 else
                                               -1.4 if f.name in SIDES and j >= 1 + R._h(f.cube.name + f.name, i) % 2 else 0),
                 grad=0.6, rim=1.0, ao=0.3, noise=0.2)
    # the hemp: straps, the breast rope, the belt and its ends, the rope round the ice, twisted in diagonal strands
    twist = lambda f, i, j: (-0.7 if (i + j) % 3 == 0 else 0.3 if (i + j) % 3 == 1 else 0)
    for c in of("strap_", "breast", "belt", "knot", "rope_tail", "ropeband", "cord"):
        P_.paint(c, HEMP, 3.0, twist, rim=0.8, ao=0.7, noise=0.2)
    P_.paint("chainband", IRON, 2.6, lambda f, i, j: (1.6 if (i % 4 in (1, 2)) == (j % 2 == 0) else -0.6), rim=1.0, noise=0.2)
    # the hands and feet: frostbitten grey skin under grey rags, straw soles
    for c in of("hand_"):
        P_.paint(c, SKIN, 2.4, lambda f, i, j: -0.8 if f.name == "bottom" or (f.name in SIDES and j % 3 == 2) else 0, rim=0.7, noise=0.2)
    for c in of("rag_", "wrap_", "foot_", "shin_"):
        P_.paint(c, RAG, 3.6 if c.startswith("rag_") else 2.8, lambda f, i, j: (-0.9 if (2 * i + j) % 4 == 0 else 0.4 if (2 * i + j) % 4 == 2 else 0), rim=0.8, noise=0.3)
    for c in of("sole_"):
        P_.paint(c, STRAW, 2.6, lambda f, i, j: -0.8 if i % 2 else 0.5, noise=0.2)
    # the eyes: two pale cold lights in the dark
    with P_.eyes():
        for c in ("eye_l", "eye_r"):
            P_.paint(c, Ramp(*EYE), 1.6, rim=0.0, ao=0.0)
            g = P_.face(c, "north", glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(EYE[2] if i == (0 if c == "eye_l" else 1) else EYE[1], 235))
    # the beard: frost-white, icicles growing out of it; the beads: ice on a red cord, a red tassel
    P_.paint("beard0", SNOW, 3.4, lambda f, i, j: -1.0 if (i + j) % 3 == 0 else 0, rim=0.6, noise=0.3)
    for c in of("icicle", "sicicle_", "drip"):
        _ice(P_, c, 6.2, 205, streak=True)
    for c in of("bead"):
        _ice(P_, c, 6.6, 220)
    for fname in ("north", "south", "east", "west", "top", "bottom"):
        f = P_.face("breath", fname)
        for i, j in f.each():
            f.set(i, j, SNOW.at(5.0 + (R._h("breath" + fname, i * 3 + j) % 2))[:3] + (70 + 30 * (min(i, j, f.w - 1 - i, f.h - 1 - j) > 0),))
    P_.paint("tassel", Ramp("#3a100c", "#5a1a14", "#7c2c24", "#9a3c30"), 2.0, noise=0.2)
    # the heart: gold, lit from inside; it glows through the ice (the ice round it carries the glow, below)
    for c in of("heart_"):
        P_.paint(c, GOLD, 4.8, lambda f, i, j: 1.0 if (i + j) % 3 == 0 else 0, rim=1.0, ao=0.2)
        for fname in ("north", "south", "east", "west", "top", "bottom"):
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, (GOLD.at(6.5) if c == "heart_f" else GOLD.at(5.6 + 0.8 * ((i + j) % 2)))[:3] + (230,))
    # the glacier: deep blue, clear ice with white cracks and bubbles in it, the edges frosted pale; round the heart
    # the ice itself glows gold
    _ice(P_, "ice0", 3.2, 165, cracks=True, heart=(12, 14, 9))
    for c in of("ice"):
        if c != "ice0":
            _ice(P_, c, 3.8, 180, cracks=True)
    for c in of("spike"):
        _ice(P_, c, 4.6, 185, cracks=True)
    # the flags: faded cloth, each with a printed square, the cord hemp
    for k in range(5):
        col = FLAGS[k]
        ramp = Ramp(*(_hx(tuple(int(v * m) for v in hexc(col)[:3])) for m in (0.55, 0.75, 1.0, 1.15)))
        P_.paint(f"flag{k}", ramp, 2.2, lambda f, i, j: -1.0 if (1 <= i <= f.w - 2 and 1 <= j <= f.h - 2 and (i + j) % 2 == 0) else 0,
                 rim=0.3, ao=0.2, noise=0.25)
    return P_.skin, P_.glow


def _hx(rgb):
    return "#%02x%02x%02x" % tuple(int(max(0, min(255, v))) for v in rgb[:3])


def _cracks(c, fname, w, h):
    """A few jagged white cracks across a face: each a walk from one edge, wandering a pixel at a time."""
    out = set()
    n = (w * h) // 420
    for k in range(n):
        seed = R._h(c + fname, 97 + k)
        x, y = seed % max(1, w), 0
        dx = 1 if seed % 2 else -1
        for step in range(3 * (w + h)):
            if not (0 <= y < h and 0 <= x < w):
                break
            out.add((x, y))
            if R._h(c + fname, x * 13 + y * 7 + k * 5 + step) % 3 == 0:
                x += dx
                if R._h(c, x + y * 3 + k) % 5 == 0:
                    dx = -dx
            else:
                y += 1
    return out


def _ice(P_, c, base, alpha, cracks=False, streak=False, heart=None):
    """Clear glacier ice: an ICE colour at alpha in old layers (bands down the faces), frosted pale at its edges and
    white on top; a few jagged cracks and rare bubbles (cracks); long streaks down it (streak, the icicles); heart=(cx,
    cy, r): a gold glow on the front, back and sides round that point (face pixels), the ice thinner over it."""
    for fname in ("north", "south", "east", "west", "top", "bottom"):
        f = P_.face(c, fname)
        g = P_.face(c, fname, glow=True) if heart else None
        crack = _cracks(c, fname, f.w, f.h) if cracks and fname != "bottom" else set()
        for i, j in f.each():
            edge = min(i, j, f.w - 1 - i, f.h - 1 - j)
            band = R._h(c + "band", (j + R._h(c + fname, i // 5) % 2) // 3) % 3  # the old layers, a little uneven
            v = base + (1.6 if edge == 0 else 0.6 if edge == 1 else 0) + 0.5 * (band - 1)
            a = alpha + (30 if edge == 0 else 0)
            if fname == "top":
                v += 1.2
            if streak and i % 2 == 0:
                v += 0.6
            if (i, j) in crack:
                v, a = v + 2.6, min(255, a + 30)
            elif cracks and R._h(c + fname, i * 31 + j * 17) % 173 == 0:
                v, a = 8.0, 230  # a bubble
            col = ICE.at(min(8.0, v))
            if heart and fname in SIDES:
                cx, cy, rr = heart
                cx = cx if fname in ("north", "south") else f.w / 2
                d = math.hypot((i - cx) / rr, (j - cy) / (rr * 1.15))
                if d < 1.0:
                    w = (1 - d) ** 1.6
                    gold = GOLD.at(4.6 + 1.6 * w)
                    col = tuple(int(x + (y - x) * 0.6 * w) for x, y in zip(col[:3], gold[:3]))
                    a = int(a - 70 * w)
                    g.set(i, j, GOLD.at(5.0 + 1.5 * w)[:3] + (int(30 + 150 * w),))
            f.set(i, j, col[:3] + (a,))


# ---- motion ----------------------------------------------------------------------------------------------------------

def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), glacier=None,
          left=None, right=None, lhand=None, rhand=None, legs=None, feet=("l", "r"), step=None, more=None):
    """A whole-body key. He bends at the hips (hips: turn, at: x, up, z), the spine, the chest, the neck and the head;
    the glacier turns on its own about the top of his back (rot, or {"rot", "pos"}: lifted). The feet are planted
    where they stand at rest (step: {side: (dx, dy, dz)} model pixels, y down) unless legs pose them; left, right:
    where that fist goes, (x, up, z) model pixels or ("on", bone, point) a point on a bone (left alone, it stays on
    its strap); lhand, rhand: (ydir, xdir) for that hand."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    if glacier is not None:
        p["glacier"] = glacier
    if legs:
        p.update(legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    for side, o in (("l", lhand), ("r", rhand)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *o)
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


def _back(r, end, settle=None):
    """A recovery in two steps to rest (or to settle), so nothing snaps home."""
    home = settle or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.8))]


def _on(r, end, f=0.1):
    """The follow-through: the end carried on a little past where it was going (away from rest)."""
    return _mix(end, _pose(r), -f)


def _kneel(both=True, drop=10.0):
    """His legs folded under him: on both knees (the shins flat behind, toes tucked), or on his right knee only with the
    left foot planted ahead."""
    if both:
        return {"thigh_l": (-4, 0, -3), "shin_l": (88, 0, 0), "foot_l": (-30, 0, 0),
                "thigh_r": (-4, 0, 3), "shin_r": (88, 0, 0), "foot_r": (-30, 0, 0)}
    return {"thigh_l": (-78, 0, -4), "shin_l": (80, 0, 0), "foot_l": (-2, 0, 0), "thigh_r": (6, 0, 4), "shin_r": (88, 0, 0), "foot_r": (-30, 0, 0)}


def _heart(r, mv, keys):
    """The heart's beat in a move: [(stage, f, scale)] (it swells inside the ice and settles)."""
    W, A, Rr = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = {"windup": 0.0, "active": W, "recover": W + A}
    ln = {"windup": W, "active": A, "recover": Rr}
    c = r.clips[core.clip_name(mv)]
    pts = [(0.0, 1.0, 1.0, 1.0)] + [(t0[s] + f * ln[s], k, k, k) for s, f, k in keys] + [(c.length, 1.0, 1.0, 1.0)]
    c.scale("heart", pts, "cat")


def _puffs(r, clip, spans, length=None):
    """His breath: a puff swelling out of the hood and thinning away over each (start, end) seconds of a clip (it is
    scaled to nothing the rest of the time). Call it before the clip exists when length is given (then after keying)."""
    r._puffs = getattr(r, "_puffs", {})
    r._puffs[clip] = spans


def _breathe(r):
    """Writes the breath's scale track into every clip that asked for puffs (after the clips exist)."""
    for clip, spans in getattr(r, "_puffs", {}).items():
        c = r.clips[clip]
        pts = [(0.0, 0.05, 0.05, 0.05)]
        for t0, t1 in spans:
            d = t1 - t0
            pts += [(t0, 0.05, 0.05, 0.05), (t0 + 0.3 * d, 0.9, 0.8, 0.9), (t0 + 0.7 * d, 1.25, 1.0, 1.35), (t1, 0.05, 0.05, 0.05)]
        pts.append((c.length, 0.05, 0.05, 0.05))
        c.scale("breath", sorted(pts), "linear")


def _gait(u, swing=26.0, bend=34.0):
    """A heavy stride at phase u (0..1, left foot planted first): thigh, knee and foot turns for both legs."""
    s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
    lk, rk = max(0.0, c) * bend + 4, max(0.0, -c) * bend + 4
    return {"thigh_l": (-swing * s - lk * 0.4, 0, 0), "shin_l": (lk, 0, 0), "foot_l": (swing * s * 0.6 - lk * 0.5, 0, 0),
            "thigh_r": (swing * s - rk * 0.4, 0, 0), "shin_r": (rk, 0, 0), "foot_r": (-swing * s * 0.6 - rk * 0.5, 0, 0)}


def idle(r):
    """Standing bent under it: slow, laboured breaths that lift the glacier a little and let it down; his head turning
    to look round from under the hood; now and then he hitches the load up his back with a shrug and it settles with
    a thump (the beads, the beard and the flags swinging)."""
    k0 = _pose(r)
    inhale = _pose(r, at=(0, 0.4, 0), spine=(-1, 0, 0), chest=(-3, 0, 0.5), neck=(-1, 0, 0), head=(-2, 3, 0), glacier=(0.8, 0, 0))
    exhale = _pose(r, at=(0, -0.5, 0), spine=(0.5, 0, 0), chest=(1.6, 0, -0.4), head=(2, -2, 0), glacier=(-0.4, 0, 0))
    look = _pose(r, hips=(0, 3, 0.6), at=(0.3, -0.2, 0), chest=(-0.5, 5, 1), neck=(0, 8, 0), head=(-1, 16, 2))
    hitch = _pose(r, hips=(0, 0, 0), at=(0, 1.2, 0), spine=(-2, 0, 0), chest=(-6, 0, 0), head=(-4, 4, 0), glacier={"rot": (2, 0, 0), "pos": (0, 1.6, 0)})
    drop = _pose(r, at=(0, -1.2, 0), spine=(1.5, 0, 0), chest=(3, 0, 0), head=(4, 0, 0), glacier={"rot": (-1, 0, 0), "pos": (0, -0.6, 0)})
    _puffs(r, "idle", [(2.0, 2.9)], 6.0)
    keyed(r, "idle", 6.0, [(0, k0), (1.1, inhale), (2.3, exhale), (3.4, look), (4.3, _mix(look, k0, 0.6)), (4.7, hitch), (4.95, drop),
                           (5.4, _mix(drop, k0, 0.6)), (6.0, k0)], loop=True)


def _trudge(r):
    """The walk, rewritten as a trudge: the library's legs (slow and deep), and over them his whole body lurching onto
    each planted foot, the head bobbing, the glacier swaying a beat behind; his fists never leave the straps."""
    c = r.clips["walk"]
    T = c.length
    for b in ("arm_l", "arm_r", "forearm_l", "forearm_r", "hand_l", "hand_r", "shoulder_l", "shoulder_r"):
        c.tracks.pop((b, "rot"), None)
    n = 16
    keys = {b: [] for b in ("spine", "chest", "neck", "head", "glacier")}
    for k in range(n + 1):
        u = k / n
        s, s2 = math.sin(2 * math.pi * u), math.sin(4 * math.pi * u)
        keys["spine"].append((T * u, (HUNCH[0] + 1.5 * s2, 4 * s, -2.5 * s), "cat"))
        keys["chest"].append((T * u, (HUNCH[1] + 2.0 * s2, 5 * s, -3.5 * s), "cat"))
        keys["neck"].append((T * u, (-HUNCH[1] + 4 - 1.5 * s2, -4 * s, 2 * s), "cat"))
        keys["head"].append((T * u, (-HUNCH[0] - 2.0 * math.sin(4 * math.pi * u - 0.6), -4 * s, 2.5 * s), "cat"))
        keys["glacier"].append((T * u, (-sum(HUNCH) + 1.5 * math.sin(4 * math.pi * u - 1.2), 0, 2.5 * math.sin(2 * math.pi * u - 0.9)), "cat"))
    for b, ks in keys.items():
        c.tracks[(b, "rot")] = ks


def reactions(r):
    """The kneel when his stance breaks: down on his right knee with his left fist planted on the ground before him to
    hold the weight up, the head hung. The rise heaves up off the fist. The death: his knees give, he goes down on both,
    his fists slide off the straps to the ground, and the glacier presses him down onto his face."""
    kneel = _pose(r, hips=(6, 0, 0), at=(0, -9.5, 1), legs=_kneel(False), feet=(), spine=(6, 0, 0), chest=(10, -6, 3), head=(14, 6, 0),
                  left=(8, 1, -16), lhand=((0, 1, -0.2), (1, 0, 0)), glacier=(4, 0, 0))
    sag = _pose(r, hips=(4, 0, 0), at=(0, -4, 0), chest=(6, 0, 0), head=(8, 0, 0), glacier=(2, 0, 0))
    keyed(r, "kneel", 0.6, [(0, _pose(r)), (0.25, sag), (0.6, kneel)])
    push = _pose(r, hips=(4, 0, 0), at=(0, -5, 0.5), chest=(6, -3, 1), head=(4, 0, 0), left=(8, 6, -14), lhand=((0, 1, -0.2), (1, 0, 0)))
    keyed(r, "rise", 0.85, [(0, kneel), (0.45, push), (0.85, _pose(r))])
    stag = _pose(r, at=(0, 0, 1.5), chest=(-8, 6, 0), head=(-12, 0, 0), glacier=(-4, 0, 0))
    buckle = _pose(r, hips=(4, 0, 0), at=(0, -5, 1), chest=(6, 0, 0), head=(10, 0, 0), glacier=(3, 0, 0))
    knees = _pose(r, hips=(-2, 0, 0), at=(0, -10, 1.5), legs=_kneel(True), feet=(), spine=(-4, 0, 0), chest=(-6, 0, 0), head=(-6, 0, 0),
                  glacier=(-2, 0, 0))
    fall = _pose(r, hips=(24, 0, 0), at=(0, -10, -1), legs=_kneel(True), feet=(), spine=(10, 0, 0), chest=(18, 0, 0), head=(6, 0, 0),
                 left=(10, 2, -24), right=(-10, 2, -24), lhand=((0, 1, -0.3), (1, 0, 0)), rhand=((0, 1, -0.3), (1, 0, 0)), glacier=(8, 0, 0))
    press = _pose(r, hips=(50, 0, 0), at=(0, -12, -3), legs={"thigh_l": (-30, 0, -4), "shin_l": (100, 0, 0), "foot_l": (-20, 0, 0),
                                                           "thigh_r": (-30, 0, 4), "shin_r": (100, 0, 0), "foot_r": (-20, 0, 0)},
                  feet=(), spine=(14, 0, 0), chest=(16, 0, 0), head=(4, 10, 8),
                  left=(14, 1, -28), right=(-14, 1, -26), lhand=((0.3, 0.5, -0.8), (1, 0, 0)), rhand=((-0.3, 0.5, -0.8), (1, 0, 0)),
                  glacier=(14, 0, 4))
    down = _pose(r, hips=(68, 0, 0), at=(0, -15, -6), legs={"thigh_l": (-52, 0, -4), "shin_l": (60, 0, 0), "foot_l": (-10, 0, 0),
                                                          "thigh_r": (-52, 0, 4), "shin_r": (60, 0, 0), "foot_r": (-10, 0, 0)},
                 feet=(), spine=(12, 0, 0), chest=(12, 0, 0), head=(-6, 22, 10),
                 left=(20, 1, -26), right=(-20, 1, -24), lhand=((0.6, 0.1, -0.8), (0, 1, 0)), rhand=((-0.6, 0.1, -0.8), (0, 1, 0)),
                 glacier=(24, 0, 6))
    keyed(r, "death", 4.0, [(0, _pose(r)), (0.3, stag), (0.8, buckle), (1.3, knees), (1.9, fall), (2.7, press), (3.4, down), (4.0, down)])


# ---- the moves -------------------------------------------------------------------------------------------------------

def swat(r):
    """The Backhand: his right fist comes off its strap and back across to his left shoulder, the body twisting away;
    then it swings out across in front of him the back of the hand first, the whole weight of him turning behind it."""
    cock = _pose(r, hips=(0, -10, 0), chest=(-4, -18, -4), head=(-2, -8, 0), right=(10, 44, -6), rhand=((0.6, -0.5, -0.6), (0, 0, -1)), step={"r": (0, 0, 1)})
    hit = _pose(r, hips=(2, 14, 0), at=(0, -1, -1), chest=(4, 26, 3), head=(2, 14, 0), right=(-20, 38, -16), rhand=((-0.9, 0.1, -0.4), (0, 0, -1)),
                step={"r": (0, 0, -2)})
    after = _pose(r, hips=(2, 18, 0), at=(0, -1, -1), chest=(5, 34, 4), head=(2, 18, 0), right=(-22, 36, -6), rhand=((-0.8, 0.3, 0.4), (0, 0, -1)),
                  step={"r": (0, 0, -2)})
    return Move("swat", "LIGHT", 11, 4, 16, title="Backhand", track=9.0, rng=(0, 3.6), kinds=("near",), damage=1.0, knock=1.0, plant=(),
                hit=[("hand_r", (0, 4, 0), 4.0), ("forearm_r", (0, 6, 0), 3.5)], lag=ON_TIME, through=_on(r, after, 0.05),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.6, hit), (1.0, after)],
                        "recover": [(0.45, _mix(after, _pose(r), 0.45)), (0.7, _mix(after, _pose(r), 0.82))]})


def hammer(r):
    """Two Fists: both fists come off the straps and up over his hood as high as the ice lets him reach, his back
    straightening under it; he holds them there, then brings them down together on the ground before him, his knees
    giving with the blow."""
    up = _pose(r, at=(0, 1, 1), spine=(-6, 0, 0), chest=(-16, 0, 0), neck=(-4, 0, 0), head=(-10, 0, 0),
               left=(5, 62, -8), right=(-5, 62, -8), lhand=((0, -0.6, -0.8), (1, 0, 0)), rhand=((0, -0.6, -0.8), (1, 0, 0)), glacier=(-6, 0, 0))
    smash = _pose(r, hips=(10, 0, 0), at=(0, -6, -2), spine=(10, 0, 0), chest=(18, 0, 0), head=(6, 0, 0),
                  left=(5, 3, -24), right=(-5, 3, -24), lhand=((0, 0.8, -0.6), (1, 0, 0)), rhand=((0, 0.8, -0.6), (1, 0, 0)), glacier=(8, 0, 0))
    after = _pose(r, hips=(11, 0, 0), at=(0, -6.5, -2.2), spine=(11, 0, 0), chest=(19, 0, 0), head=(8, 0, 0),
                  left=(5.5, 2, -25), right=(-5.5, 2, -25), lhand=((0, 0.9, -0.4), (1, 0, 0)), rhand=((0, 0.9, -0.4), (1, 0, 0)), glacier=(10, 0, 0))
    lift = _pose(r, hips=(6, 0, 0), at=(0, -3, -1), spine=(4, 0, 0), chest=(8, 0, 0), head=(2, 0, 0),
                 left=(6, 14, -20), right=(-6, 14, -20), lhand=((0, 0.9, -0.4), (1, 0, 0)), rhand=((0, 0.9, -0.4), (1, 0, 0)), glacier=(4, 0, 0))
    return Move("hammer", "HEAVY", 18, 6, 22, title="Two Fists", hold=8, track=5.0, rng=(0, 3.4), kinds=("near",), damage=1.5, knock=1.3,
                plant=(), disc=2.5, disc_at="hit", hit=[("hand_l", (0, 4, 0), 4.0), ("hand_r", (0, 4, 0), 4.0)], lag=ON_TIME, through=after,
                stages={"windup": [(0.3, _mix(_pose(r), up, 0.3)), (1.0, up)], "active": [(0.5, smash), (1.0, after)],
                        "recover": [(0.4, lift), (0.7, _mix(lift, _pose(r), 0.75))]})


def crush(r):
    """The Weight (for whoever is behind him): he grips the straps and bends lower, then throws all his weight back,
    straightening and sitting back on his heels so the glacier swings down behind him onto them."""
    gather = _pose(r, hips=(6, 0, 0), at=(0, -3, -1.5), spine=(4, 0, 0), chest=(8, 0, 0), head=(6, 0, 0), glacier=(4, 0, 0))
    throw = _pose(r, hips=(-14, 0, 0), at=(0, -6, 5), spine=(-10, 0, 0), chest=(-22, 0, 0), neck=(-4, 0, 0), head=(-6, 0, 0),
                  glacier=(-30, 0, 0), step={"l": (0, 0, -2), "r": (0, 0, -2)})
    after = _pose(r, hips=(-16, 0, 0), at=(0, -7, 5.5), spine=(-11, 0, 0), chest=(-24, 0, 0), neck=(-4, 0, 0), head=(-6, 0, 0),
                  glacier=(-34, 0, 0), step={"l": (0, 0, -2), "r": (0, 0, -2)})
    return Move("crush", "LIGHT", 12, 5, 18, title="The Weight", track=0.0, rng=(0, 3.4), kinds=("behind",), damage=1.2, knock=1.2, plant=(),
                hit=[("ice", (0, -24, 20), 7.0), ("ice", (0, -2, 20), 6.0)], lag=ON_TIME, through=after,
                stages={"windup": [(0.3, _mix(_pose(r), gather, 0.4)), (1.0, gather)], "active": [(0.55, throw), (1.0, after)],
                        "recover": [(0.4, _mix(after, gather, 0.5)), (0.68, _mix(gather, _pose(r), 0.7))]})


HYMN_FROM = (0.0, 3.6, 1.2)  # where the notes leave him: the hollow of his hood at the top of a note (blocks: x, up, forward)


def hymn(r):
    """The Frost Hymn: his left fist opens off its strap and lifts to the side, palm up like a cantor's; he lifts his
    face as far as the weight lets him and sings five long notes, his chest swelling and his face pushing out on each."""
    lift = dict(left=(17, 46, -12), lhand=((0.6, 0.1, -0.8), (0, -1, 0)))
    raised = _pose(r, spine=(-3, 0, 0), chest=(-8, 0, 0), neck=(-12, 0, 0), head=(-26, 0, 0), **lift)
    note = lambda k: _pose(r, at=(0, 0.6, 0), spine=(-4, 0, 0), chest=(-11, 2 * (k % 2) - 1, 0), neck=(-10, 0, 0), head=(-19, 3 * (k % 2) - 1.5, 0),
                           glacier=(-1, 0, 0), left=(18, 47 + 0.8 * (k % 2), -13), lhand=((0.6, 0.1, -0.8), (0, -1, 0)))
    act = []
    for k in range(5):
        act += [((k + 0.45) / 5, note(k)), ((k + 0.95) / 5, raised)]
    return Move("hymn", "LIGHT", 12, 20, 14, title="Frost Hymn", track=9.0, ability="Frost Hymn", origin=HYMN_FROM, rng=(4, 18),
                kinds=("ranged",), damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(raised, _pose(r), 0.2),
                stages={"windup": [(0.3, _mix(_pose(r), raised, 0.3)), (1.0, raised)], "active": act[:-1] + [(1.0, _mix(raised, _pose(r), 0.15))],
                        "recover": _back(r, _mix(raised, _pose(r), 0.3))})


def spikes(r):
    """Ice Spikes: he sinks low on his knees, hauling down on both straps; then heaves up with everything he has so the
    glacier jumps off his back, and lets it drop back on him with a thump that he takes in his knees (the shock comes
    up through the ground at you)."""
    sink = _pose(r, hips=(4, 0, 0), at=(0, -5, -0.5), spine=(4, 0, 0), chest=(8, 0, 0), head=(6, 0, 0), glacier=(2, 0, 0))
    heave = _pose(r, hips=(-4, 0, 0), at=(0, 1.8, 0.5), spine=(-4, 0, 0), chest=(-10, 0, 0), neck=(-4, 0, 0), head=(-10, 0, 0),
                  glacier={"rot": (-4, 0, 0), "pos": (0, 5, 0)})
    land = _pose(r, hips=(6, 0, 0), at=(0, -6, -0.5), spine=(5, 0, 0), chest=(9, 0, 0), head=(10, 0, 0), glacier={"rot": (3, 0, 0), "pos": (0, -1, 0)})
    return Move("spikes", "NONE", 14, 10, 16, title="Ice Spikes", track=0.0, ability="Ice Spikes", rng=(0, 99), kinds=("area",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=_on(r, land, 0.04),
                stages={"windup": [(0.3, _mix(_pose(r), sink, 0.4)), (1.0, sink)], "active": [(0.35, heave), (0.7, land), (1.0, _mix(land, sink, 0.3))],
                        "recover": _back(r, land)})


def choir(r):
    """The Choir: both fists open off the straps and his palms press together before his face; he bows his head to
    them and rocks, praying, and the heart beats in the ice with each rock."""
    pray = lambda k: _pose(r, hips=(1 + k, 0, 0), at=(0, -0.5 * k, 0), chest=(4 + 3 * k, 0, 0), neck=(2, 0, 0), head=(10 + 4 * k, 0, 0),
                           left=(1.4, 46 - 2 * k, -19), right=(-1.4, 46 - 2 * k, -19), lhand=((-0.2, -0.95, -0.2), (0, 0, -1)),
                           rhand=((0.2, -0.95, -0.2), (0, 0, -1)), glacier=(1 + k, 0, 0))
    return Move("choir", "NONE", 14, 20, 14, title="The Choir", track=0.0, ability="The Choir", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=pray(0),
                stages={"windup": [(0.3, _mix(_pose(r), pray(0), 0.3)), (1.0, pray(0))],
                        "active": [(0.25, pray(1)), (0.5, pray(0)), (0.75, pray(1)), (1.0, pray(0.3))],
                        "recover": _back(r, pray(0))})


def blizzard(r):
    """Blizzard: he flings both arms out wide off the straps, his face up to the sky, and sways the whole of himself
    from side to side under the ice, the wind tearing at his robe."""
    wide = lambda y, k=0.0: _pose(r, hips=(-2, y * 0.5, 0), at=(0, 0.5, 0), spine=(-4, y * 0.3, 0), chest=(-12, y, -y * 0.2), neck=(-6, 0, 0),
                                  head=(-14, y * 0.4, 0), left=(26, 46 - k, -4), right=(-26, 46 + k, -4),
                                  lhand=((1, 0.1, -0.1), (0, -1, 0)), rhand=((-1, 0.1, -0.1), (0, -1, 0)), glacier=(-3, 0, y * 0.15))
    return Move("blizzard", "NONE", 16, 24, 16, title="Blizzard", track=0.0, ability="Blizzard", rng=(0, 99), kinds=("area",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=wide(0),
                stages={"windup": [(0.3, _mix(_pose(r), wide(0), 0.3)), (1.0, wide(0))],
                        "active": [(0.25, wide(16, 3)), (0.5, wide(0)), (0.75, wide(-16, -3)), (1.0, wide(0))],
                        "recover": _back(r, wide(0))})


def avalanche(r):
    """The Avalanche (a gap closer): he puts his head down, his left shoulder and the hood leading, and charges, the
    glacier bucking on his back; three heavy strides, and he skids to a stop leaning back against the weight."""
    low = _pose(r, hips=(6, -10, 0), at=(0, -3, 0), spine=(6, -4, 0), chest=(10, -10, 0), head=(14, 6, 0), glacier=(3, 0, 0))
    act = []
    n = 6
    for j in range(1, n + 1):
        u = j / n
        b = abs(math.sin(math.pi * 3 * u))
        g = _gait((1.5 * u) % 1.0)
        for sd in "lr":  # a charging crouch: the knees never pass straight under him
            g["thigh_" + sd] = (g["thigh_" + sd][0] - 9, 0, 0)
            g["shin_" + sd] = (g["shin_" + sd][0] + 18, 0, 0)
            g["foot_" + sd] = (g["foot_" + sd][0] - 9, 0, 0)
        act.append((u, _pose(r, hips=(14, -10, 0), at=(0, -2.5 + 2.5 * b, -1), spine=(9, -4, 0), chest=(15 - 3 * b, -10, 3 * math.sin(math.pi * 3 * u)),
                             head=(19, 6, 0), legs=g, feet=(), glacier=(4 - 4 * b, 0, 0))))
    skid = _pose(r, hips=(-12, -8, 0), at=(0, -5, 3), spine=(-6, 0, 0), chest=(-12, -8, 2), head=(-8, 6, 0), glacier=(-10, 0, 0),
                 step={"l": (0, 0, -7), "r": (0, 0, 3)})
    return Move("avalanche", "HEAVY", 16, 16, 20, title="Avalanche", track=6.0, lock=12, advance=8.0, rng=(5, 14), kinds=("gap",),
                damage=1.4, knock=1.6, plant=(), hit=[("chest", (6, -12, -9), 7.0), ("head", (0, -6, -8), 5.0)], lag=ON_TIME, through=skid,
                stages={"windup": [(0.3, _mix(_pose(r), low, 0.4)), (1.0, low)], "active": act, "recover": [(0.45, _mix(skid, _pose(r), 0.4)),
                                                                                                         (0.7, _mix(skid, _pose(r), 0.8))]})


def nova(r):
    """Frost Nova: he curls up tight on himself, fists pulled into his breast and the hood down on them; then throws
    himself open, arms flung back, chest out and face up, roaring, the glacier jolting on his back."""
    curl = _pose(r, hips=(6, 0, 0), at=(0, -4, -0.5), spine=(6, 0, 0), chest=(12, 0, 0), neck=(4, 0, 0), head=(16, 0, 0),
                 left=("on", "chest", (2, -6, -10)), right=("on", "chest", (-2, -6, -10)), glacier=(3, 0, 0))
    burst = _pose(r, hips=(-6, 0, 0), at=(0, 1.5, 1), spine=(-6, 0, 0), chest=(-20, 0, 0), neck=(-8, 0, 0), head=(-22, 0, 0),
                  left=(22, 42, 8), right=(-22, 42, 8), lhand=((0.8, 0.2, 0.5), (0, -1, 0)), rhand=((-0.8, 0.2, 0.5), (0, -1, 0)),
                  glacier={"rot": (-6, 0, 0), "pos": (0, 2, 0)})
    hold = _mix(burst, _pose(r), 0.12)
    return Move("nova", "NONE", 14, 10, 18, title="Frost Nova", track=0.0, ability="Frost Nova", rng=(0, 99), kinds=("area",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=hold,
                stages={"windup": [(0.3, _mix(_pose(r), curl, 0.4)), (1.0, curl)], "active": [(0.3, burst), (1.0, hold)], "recover": _back(r, hold)})


def vow(r):
    """The Glacial Vow (the signature): he plants his feet wide and straightens up to his full height for the only time,
    the glacier towering, his face to the sky; then throws himself down on his knees and swings the whole glacier up
    over his head and down onto the ground before him like a hammer. He hauls it back over onto his back and rises."""
    wide = {"l": (2, 0, 0), "r": (-2, 0, 0)}
    # his fists go to the glacier's sides and stay there: the ice's own angle is (hips + spine + chest + 14 + its key)
    # from upright, so each key below is set for where the ice should point
    grip = dict(left=("on", "ice", (15, -8, 6)), right=("on", "ice", (-15, -8, 6)))
    gather = _pose(r, hips=(10, 0, 0), at=(0, -5, -0.5), spine=(6, 0, 0), chest=(8, 0, 0), neck=(4, 0, 0), head=(12, 0, 0),
                   glacier=(14, 0, 0), step=wide, **grip)
    tall = _pose(r, hips=(-3, 0, 0), at=(0, 0, 0.5), spine=(-8, 0, 0), chest=(-16, 0, 0), neck=(8, 0, 0), head=(-6, 0, 0),
                 glacier=(34, 0, 0), step=wide, **grip)
    rise = _pose(r, hips=(-11, 0, 0), at=(0, 4.5, 2), spine=(-20, 0, 0), chest=(-32, 0, 0), neck=(2, 0, 0), head=(-28, 0, 0),
                 glacier=(52, 0, 0), step=wide, **grip)
    swing = _pose(r, hips=(10, 0, 0), at=(0, -7, -1), legs=_kneel(True), feet=(), spine=(6, 0, 0), chest=(6, 0, 0), neck=(10, 0, 0), head=(18, 0, 0),
                  glacier=(44, 0, 0), **grip)
    slam = _pose(r, hips=(26, 0, 0), at=(0, -10, -2), legs=_kneel(True), feet=(), spine=(12, 0, 0), chest=(16, 0, 0), neck=(14, 0, 0), head=(22, 0, 0),
                 glacier=(60, 0, 0), **grip)
    after = _pose(r, hips=(31, 0, 0), at=(0, -11.5, -2.6), legs=_kneel(True), feet=(), spine=(14, 0, 0), chest=(20, 4, 0), neck=(8, 0, 0),
                  head=(6, 8, 0), glacier=(60, 0, 0), **grip)
    haul = _pose(r, hips=(4, 0, 0), at=(0, -8, 0), legs=_kneel(False), feet=(), spine=(-4, 0, 0), chest=(-8, 0, 0), neck=(4, 0, 0), head=(-8, 0, 0),
                 glacier=(20, 0, 0), **grip)
    up = _pose(r, hips=(6, 0, 0), at=(0, -4, 0), legs=_kneel(False), feet=(), spine=(4, 0, 0), chest=(6, 0, 0), head=(6, 0, 0), glacier=(-12, 0, 0))
    return Move("vow", "PERILOUS", 30, 12, 30, title="Glacial Vow", signature=True, track=0.0, ability="Glacial Vow", rng=(0, 99), kinds=("area",),
                damage=0, knock=0, plant=(), lag=ON_TIME, through=after, hit=[("ice", GLACIER_TOP, 9.0)],
                ground=[(["ice"], [("glacier", 0, 70)])], bite=0.7,
                stages={"windup": [(0.3, gather), (0.65, tall), (1.0, rise)],
                        "active": [(0.35, swing), (0.7, slam), (1.0, after)],
                        "recover": [(0.3, haul), (0.55, up), (0.74, _mix(up, _pose(r), 0.7))]})


def wake(r):
    """His intro, "Chanting above a sea of clouds.": he bows his head and brings his palms together, chanting, and sinks
    down on one knee under the weight; then heaves himself up with a great effort, hitches the glacier up his back, and
    lifts his face to you, his fists back on the straps."""
    pray = _pose(r, chest=(6, 0, 0), head=(14, 0, 0), left=(1.4, 44, -19), right=(-1.4, 44, -19),
                 lhand=((-0.2, -0.95, -0.2), (0, 0, -1)), rhand=((0.2, -0.95, -0.2), (0, 0, -1)))
    chant = _pose(r, at=(0, -0.6, 0), chest=(8, 0, 0), head=(18, 0, 0), left=(1.4, 43, -19), right=(-1.4, 43, -19),
                  lhand=((-0.2, -0.95, -0.2), (0, 0, -1)), rhand=((0.2, -0.95, -0.2), (0, 0, -1)))
    low = _pose(r, hips=(6, 0, 0), at=(0, -9.5, 1), legs=_kneel(False), feet=(), spine=(6, 0, 0), chest=(10, 0, 0), head=(20, 0, 0),
                left=(1.4, 34, -18), right=(-1.4, 34, -18), lhand=((-0.2, -0.95, -0.2), (0, 0, -1)), rhand=((0.2, -0.95, -0.2), (0, 0, -1)),
                glacier=(3, 0, 0))
    heave = _pose(r, hips=(4, 0, 0), at=(0, -5, 0.5), spine=(4, 0, 0), chest=(8, -4, 2), head=(6, 0, 0), right=("on", "thigh_r", (0, 9, -5)),
                  rhand=((0, 1, 0), (1, 0, 0)), glacier=(2, 0, 0))
    hitch = _pose(r, at=(0, 1.4, 0), spine=(-3, 0, 0), chest=(-7, 0, 0), head=(-8, 0, 0), glacier={"rot": (2, 0, 0), "pos": (0, 2, 0)})
    ready = _pose(r, hips=(0, 0, 0), at=(0, -0.6, 0), chest=(-2, 0, 0), neck=(-4, 0, 0), head=(-10, 0, 0))
    return Move("wake", "NONE", 16, 30, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
                stages={"windup": [(0.5, _mix(_pose(r), pray, 0.5)), (1.0, pray)],
                        "active": [(0.15, chant), (0.3, pray), (0.5, low), (0.72, heave), (0.86, hitch), (1.0, ready)],
                        "recover": _back(r, ready)})


def moves(r):
    return [swat(r), hammer(r), crush(r), hymn(r), spikes(r), choir(r), blizzard(r), avalanche(r), nova(r), vow(r), wake(r)]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.6, temper="regal")
    plans.biped_reactions(r, weight=1.6)
    idle(r)
    _trudge(r)
    reactions(r)
    mv = moves(r)
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    W, A = by["hymn"].seconds("windup"), by["hymn"].seconds("active")
    _puffs(r, core.clip_name(by["hymn"]), [(W + A * (k + 0.2) / 5, W + A * (k + 0.95) / 5) for k in range(5)])
    _breathe(r)
    # the heart beats with the prayer and the vow
    _heart(r, by["choir"], [("active", 0.25, 1.22), ("active", 0.5, 1.0), ("active", 0.75, 1.22), ("recover", 0.3, 1.0)])
    _heart(r, by["vow"], [("windup", 0.7, 1.15), ("active", 0.7, 1.3), ("recover", 0.4, 1.0)])
    _heart(r, by["nova"], [("windup", 1.0, 0.9), ("active", 0.3, 1.3), ("recover", 0.5, 1.0)])
    # his fists and the ice stay out of the ground between the keys (the wrists turn first); the robe's hem lifts back
    # off the floor where it would meet it, the death included
    core.ground_pass(r, [(["hand_l"], [("hand_l", 0, 50), ("forearm_l", 0, 30)]), (["hand_r"], [("hand_r", 0, 50), ("forearm_r", 0, 30)])],
                     clips=[n for n in r.clips if n != "death"])
    for n in r.clips:
        _pool(r, n)
    return mv


def _pool(r, clip):
    """Where he sinks low enough that the robe's hem would go into the ground, it pools there instead: the lower robe
    shortens (scaled up into the knee) by just that much, tick by tick."""
    c = r.clips[clip]
    n = int(round(c.length / core.TICK))
    for side in "lr":
        b = "skirt_" + side
        pts, any_ = [], False
        for k in range(n + 1):
            t = min(c.length, k * core.TICK)
            fr = core.frames(r, core.sample(r, clip, t))
            low = core.lowest(r, fr, [b]) - core.GROUND
            top = fr[b][1][1]
            sy = 1.0
            if low > 0.3:
                ext = max(1.0, low + core.GROUND - top)
                sy = max(0.3, (ext - low) / ext)
                any_ = True
            pts.append((t, 1.0, sy, 1.0))
        if any_:
            c.scale(b, pts, "linear")


FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "swat", "weight": 3, "when": "near", "then": [["hammer"]]},
            {"move": "hymn", "weight": 3},
            {"move": "spikes", "weight": 2},
            {"move": "avalanche", "weight": 2, "when": "far", "then": [["swat"]]},
            {"move": "crush", "weight": 3, "when": "behind"},
        ]},
        # the choir joins with the second phase, and the blizzard, as in GateBestiary
        {"transition": "choir", "pool": [
            {"move": "swat", "weight": 3, "when": "near", "then": [["hammer"]]},
            {"move": "hymn", "weight": 2},
            {"move": "spikes", "weight": 2},
            {"move": "blizzard", "weight": 2},
            {"move": "choir", "weight": 1},
            {"move": "avalanche", "weight": 2, "when": "far", "then": [["swat"]]},
            {"move": "crush", "weight": 3, "when": "behind"},
        ]},
        {"transition": "vow", "pool": [
            {"move": "vow", "weight": 1},
            {"move": "nova", "weight": 2},
            {"move": "spikes", "weight": 3},
            {"move": "hymn", "weight": 2},
            {"move": "swat", "weight": 3, "when": "near", "then": [["hammer"]]},
            {"move": "avalanche", "weight": 2, "when": "far", "then": [["swat", "hammer"]]},
            {"move": "crush", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}

# His sounds (SPEC §35.8): ice and snow, wool and rope, a deep voice; he walks heavily. Chosen by ear on paper, not yet
# heard in game.
SOUNDS = {
    "tell_light": [("block.wool.step", 1.2, 0.7), ("block.powder_snow.step", 1.0, 0.8)],
    "tell_heavy": [("entity.polar_bear.warning", 1.2, 0.6), ("block.glass.hit", 1.0, 0.6)],
    "tell_perilous": [("block.amethyst_block.resonate", 2.0, 0.5), ("entity.evoker.prepare_summon", 1.4, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 0.8), ("block.wool.step", 1.0, 0.6)],
    "swing_heavy": [("entity.player.attack.sweep", 1.4, 0.6), ("block.powder_snow.break", 1.2, 0.6)],
    "cast": [("entity.evoker.cast_spell", 1.2, 0.7), ("block.amethyst_block.chime", 1.0, 0.6)],
    "impact": [("block.glass.break", 1.4, 0.5), ("entity.generic.big_fall", 1.2, 0.6)],
    "roar": [("entity.ravager.roar", 1.6, 0.6), ("entity.player.hurt_freeze", 1.2, 0.5)],
    "step": [("block.snow.step", 0.8, 0.7), ("entity.iron_golem.step", 0.5, 0.6)],
}
MOVE_SOUNDS = {"hymn": {"swing": "cast"}, "choir": {"swing": "cast", "impact": ""}, "blizzard": {"swing": "cast", "impact": ""},
               "spikes": {"swing": "swing_heavy"}, "nova": {"swing": "roar"}, "vow": {"swing": "roar"}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.85, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where his parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_l", (0, 0, 0)), ("hand_r", (0, 0, 0)), ("head", (0, -11, 0)), ("head", MOUTH), ("glacier", (0, 0, 0)),
                 ("ice", GLACIER_TOP), ("flags", (0, 0, 0)), ("beads", (0, 14, 0)), ("foot_l", (0, 3, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))


if __name__ == "__main__":
    probe()
