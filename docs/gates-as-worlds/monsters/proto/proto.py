"""Prototype bodies for the gate monsters plan (docs/gates-as-worlds/monsters/PLAN.md). PLANNING ONLY.

Nothing here is wired into the mod: no bestiary line, no texture under src/, no Java. It uses the mod's own rig
toolkit (tools/art/specs/fauna_rig.py) read-only, so a prototype is the same kind of body the Drip Lurker is, drawn
by the same offline renderer its preview uses. The art is a first pass made in an afternoon to show direction and
silhouette; the real bodies get the full paint, clips and close tours the plan describes.

    python3 -I docs/gates-as-worlds/monsters/proto/proto.py <outdir>
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../../.."))
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
import fauna_rig as R  # noqa: E402
from fauna_rig import Rig, Ramp, hexc, p_stone, p_bands, p_strokes, p_blotch, SIDES, _h  # noqa: E402

DEG = math.pi / 180.0
ALL = ("top", "bottom") + SIDES

# The crypt is dark blue stone lit by warm candles, so its monsters are warm (bronze, bone, wax, old cloth) and carry
# one cold light: they stand out of the room by value and by hue, and the cold light is where you look.
BRONZE = Ramp("#2a1c0c", "#46300f", "#6a4a18", "#8f6a24", "#b88c38", "#dcb458", "#f4dc90")
VERDI = Ramp("#1c3a34", "#2a5a4e", "#3e7a68", "#5aa088")
BONE = Ramp("#4a4234", "#6a604c", "#8c8068", "#b0a488", "#d0c6aa", "#ece4cc")
SHROUD = Ramp("#1a1820", "#26232e", "#34303e", "#464152", "#5c5668", "#767084")
CLOTH = Ramp("#4a3e30", "#6a5a46", "#8a7860", "#a8967a", "#c4b294", "#dccdb0")
VEIL = Ramp("#2e2a38", "#423d50", "#5a5468", "#746d84", "#8e88a0", "#aaa4bc")
LID = Ramp("#4e5260", "#6c7080", "#8c90a0", "#aeb2c0", "#ccd0da", "#e6e8ee")
STONE = Ramp("#22242c", "#30333e", "#424654", "#585d6e", "#72788a", "#9096a8")
IRON = Ramp("#141418", "#1e1e24", "#2c2c34", "#3e3e48")
COLD = "#9ad8ff"
COLD_HOT = "#e8f8ff"


def glow_rect(P, cube, fname, x0, y0, w, h, col=COLD, a=235, core=COLD_HOT):
    f, g = P.face(cube, fname), P.face(cube, fname, glow=True)
    for j in range(y0, y0 + h):
        for i in range(x0, x0 + w):
            edge = i in (x0, x0 + w - 1) or j in (y0, y0 + h - 1)
            c = hexc(col if edge and (w > 2 or h > 2) else core)
            f.set(i, j, c)
            g.set(i, j, c[:3] + (a,))


def fill(P, cube, fname, col):
    f = P.face(cube, fname)
    for i, j in f.each():
        f.set(i, j, hexc(col))


def poses_deg(d):
    """{bone: (rx, ry, rz) or {"rot":..., "pos":..., "scale":...}} in degrees -> the renderer's pose (radians)."""
    out = {}
    for b, v in d.items():
        if isinstance(v, dict):
            e = {}
            if "rot" in v:
                e["rot"] = tuple(a * DEG for a in v["rot"])
            if "pos" in v:
                e["pos"] = (v["pos"][0], -v["pos"][1], v["pos"][2])
            if "scale" in v:
                e["scale"] = v["scale"]
            out[b] = e
        else:
            out[b] = {"rot": tuple(a * DEG for a in v)}
    return out


# ---------------------------------------------------------------------------------------------------------------------
# The Toller: a ringer whose head is its bell. Hunched, knuckles on the floor, a cold flame where the clapper hangs.

def toller():
    r = Rig("toller", (128, 64))
    body = r.bone("body", (0, 13, 1))
    body.cube("hips", -3, -2, -2, 6, 4, 4)
    body.cube("tatter", -3, 2, 2, 6, 6, 0)
    for side, nm in ((1, "l"), (-1, "r")):
        leg = body.bone("leg_" + nm, (side * 2.5, 2, 0), (-25, 0, 0))
        leg.cube("thigh_" + nm, -1.5, 0, -1.5, 3, 5, 3, mirror=side < 0, share=None if side > 0 else "thigh_l")
        leg.bone("shin_" + nm, (0, 5, 0), (50, 0, 0)).cube("shin_" + nm, -1, 0, -1, 2, 5, 2, mirror=side < 0, share=None if side > 0 else "shin_l")
    chest = body.bone("chest", (0, -2, 0), (38, 0, 0))
    chest.cube("ribs", -4, -7, -2.5, 8, 7, 5)
    chest.cube("yoke", -6, -9, -3, 12, 3, 6)
    neck = chest.bone("neck", (0, -9, -1), (-8, 0, 0))
    neck.cube("neck", -1.5, -2, -1.5, 3, 2, 3)
    # the bell is the head: its own axis runs crown (+y) to mouth (-y); turned so the mouth looks at you and a little down,
    # and set so its lower wall sits on the neck
    bell = neck.bone("bell", (0, -2, 0), (90, 0, 0))
    bell.cube("knob", -1, 4, 4, 2, 2, 2)
    bell.cube("crown", -2, 2, 3, 4, 2, 4)
    bell.cube("shoulder", -4, -1, 1, 8, 3, 8)
    bell.cube("waist", -5, -5, 0, 10, 4, 10)
    bell.cube("lip", -6, -7, -1, 12, 2, 12)
    bell.bone("clapper", (0, -5, 5)).cube("clapper", -1, -5, -1, 2, 4, 2)
    bell.bone("rope", (0, 6, 5), (-120, 0, 0)).cube("rope", -0.5, 0, -0.5, 1, 15, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        arm = chest.bone("arm_" + nm, (side * 6, -8, 0), (-40, 0, side * -7))
        arm.cube("upper_" + nm, -1.5, 0, -1.5, 3, 8, 3, mirror=side < 0, share=None if side > 0 else "upper_l")
        fore = arm.bone("fore_" + nm, (0, 8, 0), (-14, 0, side * 5))
        fore.cube("fore_" + nm, -1.5, 0, -1.5, 3, 7, 3, mirror=side < 0, share=None if side > 0 else "fore_l")
        fore.bone("fist_" + nm, (0, 7, 0), (16, 0, 0)).cube("fist_" + nm, -2, 0, -2.5, 4, 4, 4, mirror=side < 0, share=None if side > 0 else "fist_l")
    r.pack()
    return r


def paint_toller(r, P):
    for k, nm in enumerate(("knob", "crown", "shoulder", "waist", "lip")):
        P.paint(nm, BRONZE, 2.2 + k * 0.4, p_bands(4, 0.5), rim=0.9, ao=0.9)
    # verdigris runs down from the crown (the bell's "down" is its crown end, so streaks start at the cubes' bottom rows)
    for nm in ("shoulder", "waist"):
        for fname in SIDES:
            f = P.face(nm, fname)
            for i in range(f.w):
                if _h(nm, fname, i) % 3 == 0:
                    for j in range(f.h - 1 - _h(fname, i, 7) % 2, f.h):
                        f.set(i, j, VERDI.at(1 + (_h(i, j, nm) % 3)))
    # the mouth: a dark throat inside a bronze rim, and the cold light deep in it
    f = P.face("lip", "top")
    for i, j in f.each():
        rim = i in (0, f.w - 1) or j in (0, f.h - 1)
        f.set(i, j, BRONZE.at(4) if rim else hexc("#07060a") if 1 < i < f.w - 2 and 1 < j < f.h - 2 else hexc("#1c1408"))
    glow_rect(P, "lip", "top", 4, 4, 4, 4)
    P.paint("clapper", Ramp("#5a8cb0", "#9ad8ff", "#e8f8ff"), 1.4)
    for fname in ALL:
        g = P.face("clapper", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc(COLD_HOT if fname == "top" else COLD, 230))
    P.paint("ribs", CLOTH, 2.0, p_bands(2, 0.9), rim=0.4)
    P.paint("yoke", CLOTH, 3.0, p_bands(3, 0.7), rim=0.6)
    P.paint("hips", CLOTH, 1.4, p_bands(2, 0.7))
    P.paint("neck", BONE, 2.4)
    P.paint("rope", Ramp("#4a3a22", "#6e5834", "#92784a"), 1.2, p_bands(2, 0.8, vertical=False))
    f = P.face("tatter", "north")
    for i, j in f.each():
        if j < 2 or (i + j * 2) % 5 not in (0, 3) and j < 5 - (i % 3):
            col = CLOTH.at(1.5 - j * 0.2)
            f.set(i, j, col)
            P.face("tatter", "south").set(f.w - 1 - i, j, col)
    for nm in ("upper_l", "fore_l"):
        P.paint(nm, CLOTH, 1.8, p_bands(3, 1.0), rim=0.3)
    P.paint("fist_l", BONE, 2.6, p_bands(2, 0.6, vertical=True))
    P.paint("thigh_l", CLOTH, 1.3, p_bands(3, 0.8))
    P.paint("shin_l", BONE, 1.8)
    return P.skin, P.glow


TOLLER_POSES = [
    ("waits", {}),
    ("the toll: its bell swings up", {"chest": (-26, 0, 0), "neck": (-10, 0, 0), "bell": (-72, 0, 0), "clapper": (30, 0, 0), "rope": (108, 0, 0),
                                      "arm_l": (-30, 0, -18), "arm_r": (-30, 0, 18), "fore_l": (-25, 0, 0), "fore_r": (-25, 0, 0)}),
    ("it rings: everything near it slows", {"chest": (14, 0, 0), "bell": (16, 0, 0), "clapper": (-34, 0, 0), "rope": (-30, 0, 0),
                                            "arm_l": (12, 0, -10), "arm_r": (12, 0, 10), "body": {"pos": (0, -1.5, 0)}}),
    ("still ringing: your opening", {"chest": (6, 0, 0), "bell": (-4, 0, 20), "clapper": (-10, 0, -30), "rope": (-2, 0, -20), "arm_l": (4, 0, 0), "arm_r": (4, 0, 0)}),
]


# ---------------------------------------------------------------------------------------------------------------------
# The Mourner: a tall veiled column with no arms. Its ribs lie shut across its front; to shoot it leans back and they
# open like wings, and three of them go. You can see how many it has left.

RIB_Y = (-11.5, -9.5, -7.5, -5.5, -3.5)


def mourner():
    r = Rig("mourner", (128, 64))
    base = r.bone("base", (0, 24, 0))
    base.cube("hem", -5, -3, -4, 10, 3, 8)
    base.cube("skirt", -4, -14, -3, 8, 11, 6)
    spine = base.bone("spine", (0, -14, 0))
    spine.cube("torso", -4, -13, -3, 8, 13, 6)
    spine.cube("heart", -1, -10, -4, 2, 4, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        for k, y in enumerate(RIB_Y):
            first = k == 0 and side > 0
            rib = spine.bone(f"rib{k}_{nm}", (side * 4, y, 3))
            rib.cube(f"rib{k}_{nm}", 0 if side > 0 else -1, -0.5, -8, 1, 1, 8, mirror=side < 0, share=None if first else "rib0_l")
            rib.bone(f"hook{k}_{nm}", (side * 0.5, 0, -8)).cube(f"hook{k}_{nm}", -4 if side > 0 else 0, -0.5, -1, 4, 1, 1, mirror=side < 0,
                                                             share=None if first else "hook0_l")
    head = spine.bone("head", (0, -13, 0), (6, 0, 0))
    head.cube("cowl", -4, -7, -4, 8, 7, 8)
    head.cube("peak", -3, -9, -2, 6, 2, 7)
    head.bone("train", (0, -7, 4), (7, 0, 0)).cube("train", -3, 0, 0, 6, 26, 0)
    r.pack()
    return r


def paint_mourner(r, P):
    P.paint("hem", VEIL, 1.6, p_strokes(2, 3, seed=2), rim=0.3)
    P.paint("skirt", VEIL, 2.6, p_strokes(2, 6, seed=3), rim=0.3)
    P.paint("torso", VEIL, 1.0, p_strokes(2, 5, seed=5), rim=0.2)
    P.paint("cowl", VEIL, 3.4, rim=0.8)
    P.paint("peak", VEIL, 3.0, rim=0.7)
    # the veil: no face, a flat dark with two cold points low under the brow
    f = P.face("cowl", "north")
    for i, j in f.each():
        f.set(i, j, hexc("#0a090e") if 0 < i < f.w - 1 and j > 0 else VEIL.at(4.4))
    for i in (2, 5):
        glow_rect(P, "cowl", "north", i, 4, 1, 1, a=255)
    P.paint("rib0_l", BONE, 3.8, rim=0.6)
    P.paint("hook0_l", BONE, 4.6, rim=0.5)
    for fname in ALL:
        fill(P, "heart", fname, COLD)
        g = P.face("heart", fname, glow=True)
        for i, j in g.each():
            g.set(i, j, hexc(COLD_HOT if (i + j) % 2 == 0 else COLD, 240))
    for fname in ("north", "south"):
        f = P.face("train", fname)
        for i, j in f.each():
            if j < f.h - 1 - (i * 3 + 1) % 4:
                f.set(i, j, VEIL.at(3.2 - j * 0.09 + (0.5 if i in (0, f.w - 1) else 0)))
    return P.skin, P.glow


def _ribs(open_y, fan, gone=(), droop=0.0):
    d = {}
    for side, nm in ((1, "l"), (-1, "r")):
        for k in range(5):
            lift = (2 - k) * fan - droop
            d[f"rib{k}_{nm}"] = {"rot": (0, side * -open_y, side * -lift)}
            if (k, nm) in gone:
                d[f"rib{k}_{nm}"]["scale"] = (0.001, 0.001, 0.001)
    return d


GONE = ((0, "l"), (1, "r"), (2, "l"))
MOURNER_POSES = [
    ("waits, shut", {}),
    ("draws a long breath: the ribs open", dict(_ribs(104, 19), spine=(-13, 0, 0), head=(-14, 0, 0), train=(16, 0, 0))),
    ("three ribs fly", dict(_ribs(46, 10, gone=GONE), spine=(15, 0, 0), head=(8, 0, 0), train=(-12, 0, 0))),
    ("open, three short: your opening", dict(_ribs(92, 5, gone=GONE, droop=16), spine=(5, 0, 0), head=(12, 0, 0))),
]


# ---------------------------------------------------------------------------------------------------------------------
# The Chime: a hand-bell that flies, the wisp burning where its clapper was. It tips its mouth at you before it dives,
# and a dive that misses leaves it ringing on the floor.

def chime():
    r = Rig("chime", (64, 32))
    b = r.bone("body", (0, 13, 0))
    b.cube("loop", -1.5, -11, -0.5, 3, 1, 1)
    b.cube("handle", -0.5, -10, -0.5, 1, 3, 1)
    b.cube("ccrown", -1.5, -7, -1.5, 3, 2, 3)
    b.cube("cbody", -2.5, -5, -2.5, 5, 3, 5)
    b.cube("clip", -3.5, -2, -3.5, 7, 2, 7)
    fl = b.bone("flame", (0, 0, 0))
    fl.cube("flame", -1.5, -1, -1.5, 3, 4, 3)
    fl.bone("flametip", (0, 3, 0)).cube("flametip", -0.5, 0, -0.5, 1, 3, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        b.bone("ribbon_" + nm, (side * 1.5, -10.5, 0), (16, 0, side * -12)).cube("ribbon_" + nm, 0, 0, 0, 0, 9, 1, share=None if side > 0 else "ribbon_l")
    r.pack()
    return r


def paint_chime(r, P):
    P.paint("loop", BRONZE, 3.2)
    P.paint("handle", BRONZE, 2.6)
    P.paint("ccrown", BRONZE, 3.4, rim=0.9)
    P.paint("cbody", BRONZE, 3.0, p_bands(3, 0.5), rim=0.9)
    P.paint("clip", BRONZE, 3.8, rim=1.0, ao=1.0)
    f = P.face("clip", "bottom")
    for i, j in f.each():
        f.set(i, j, BRONZE.at(3) if i in (0, f.w - 1) or j in (0, f.h - 1) else hexc("#0a0806"))
    for nm in ("flame", "flametip"):
        for fname in ALL:
            f, g = P.face(nm, fname), P.face(nm, fname, glow=True)
            for i, j in f.each():
                hot = (fname in SIDES and j < 2 and 0 < i < f.w - 1) or nm == "flametip" and j < 1
                f.set(i, j, hexc(COLD_HOT if hot else COLD))
                g.set(i, j, hexc(COLD_HOT if hot else COLD, 240 if nm == "flame" else 180))
    for fname in ("west", "east"):
        f = P.face("ribbon_l", fname)
        for i, j in f.each():
            f.set(i, j, CLOTH.at(4.6 - j * 0.25))
    return P.skin, P.glow


CHIME_POSES = [
    ("hangs in the air", {}),
    ("tips its mouth at you", {"body": {"rot": (-68, 0, 0), "pos": (0, 2, 2)}, "flame": {"scale": (1.25, 1.7, 1.25)}, "ribbon_l": (30, 0, 0), "ribbon_r": (30, 0, 0)}),
    ("dives", {"body": {"rot": (-100, 0, 0), "pos": (0, -3, -5)}, "flame": {"scale": (0.8, 2.4, 0.8)}, "ribbon_l": (52, 0, 10), "ribbon_r": (52, 0, -10)}),
    ("missed: rings on the floor", {"body": {"rot": (-96, 0, 74), "pos": (0, -9.5, 0)}, "flame": {"scale": (0.7, 0.5, 0.7)}, "ribbon_l": (10, 0, 30), "ribbon_r": (80, 0, 0)}),
]


# ---------------------------------------------------------------------------------------------------------------------
# The Pallbearer (the crypt's elite): a stone coffin that carries itself. Blows on its lid do nothing. It lifts a
# headstone over its head and brings it down, and for a breath afterwards its open back is turned to you.

SEGS = (("cfoot", 6, -6, 6), ("c1", 8, -12, 6), ("c2", 10, -18, 6), ("c3", 12, -23, 5), ("cshoulder", 14, -27, 4), ("chead", 10, -31, 4), ("ctop", 8, -34, 3))


def pallbearer():
    r = Rig("pallbearer", (128, 128))
    body = r.bone("body", (0, 24, 0), (4, 0, 0))
    for nm, w, y, h in SEGS:
        body.cube(nm, -w / 2, y, -3, w, h, 6)
    lid = body.bone("lid", (7, -17, -3))
    for nm, w, y, h in SEGS:
        lid.cube("lid_" + nm, -w / 2 - 7, y + 17, -1, w, h, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        arm = body.bone("arm_" + nm, (side * 7, -25, 0.5), (-6, 0, side * -15))
        arm.cube("upper_" + nm, -1.5, 0, -1.5, 3, 11, 3, mirror=side < 0, share=None if side > 0 else "upper_l")
        fore = arm.bone("fore_" + nm, (0, 11, 0), (-8, 0, side * 11))
        fore.cube("fore_" + nm, -1.5, 0, -1.5, 3, 11, 3, mirror=side < 0, share=None if side > 0 else "fore_l")
        fore.bone("hand_" + nm, (0, 11, 0), (10, 0, 0)).cube("hand_" + nm, -2.5, 0, -3.5, 5, 3, 6, mirror=side < 0, share=None if side > 0 else "hand_l")
    r.pack()
    return r


def paint_pallbearer(r, P):
    rng = P.rng
    for nm, w, y, h in SEGS:
        P.paint(nm, STONE, 1.8, p_stone(rng, 0.7, 4), rim=0.5, ao=0.6)
        # the back stands open: dark inside a stone rim, the bearer's cold light in it
        f = P.face(nm, "south")
        for i, j in f.each():
            f.set(i, j, STONE.at(3.0) if i in (0, f.w - 1) else hexc("#050508"))
        P.paint("lid_" + nm, LID, 3.0, p_stone(rng, 0.45, 5), rim=0.6, ao=0.3)
        # the lid's face: iron bands and a seam that leaks the cold light
        f, g = P.face("lid_" + nm, "north"), P.face("lid_" + nm, "north", glow=True)
        if nm in ("c1", "c3", "chead"):
            for i in range(w):
                f.set(i, h - 2, IRON.at(2.6))
                f.set(i, h - 1, IRON.at(1))
        for j in range(h):
            for i in (w // 2 - 1, w // 2):
                if f.get(i, j) not in (IRON.at(2.6), IRON.at(1)):
                    f.set(i, j, hexc("#0a1420") if i == w // 2 - 1 else hexc(COLD))
                    if i == w // 2:
                        g.set(i, j, hexc(COLD, 200))
    for i in (4, 9):
        glow_rect(P, "cshoulder", "south", i, 1, 1, 1, a=255)
    glow_rect(P, "c3", "south", 5, 1, 2, 3, a=200)
    P.paint("upper_l", CLOTH, 2.2, p_bands(3, 1.0), rim=0.4)
    P.paint("fore_l", CLOTH, 2.8, p_bands(3, 1.0), rim=0.4)
    P.paint("hand_l", BONE, 3.4, p_bands(2, 0.7, vertical=True), rim=0.6)
    return P.skin, P.glow


PALL_POSES = [
    ("comes on, lid first: blows on the lid do nothing", {}),
    ("heaves itself up on its arms", {"body": {"rot": (-22, 0, 0), "pos": (0, 6, 1)}, "lid": (0, -22, 0), "arm_l": (34, 0, -12), "arm_r": (34, 0, 12),
                                      "fore_l": (-14, 0, 0), "fore_r": (-14, 0, 0), "hand_l": (-10, 0, 0), "hand_r": (-10, 0, 0)}),
    ("drops on you, and lies there: its open back is your opening", {"body": {"rot": (82, 0, 0), "pos": (0, 4, -2)}, "arm_l": (6, 0, -92), "arm_r": (6, 0, 92),
                                                                     "fore_l": (8, 0, -26), "fore_r": (8, 0, 26), "hand_l": (-10, 0, 0), "hand_r": (-10, 0, 0)}),
]


# ---------------------------------------------------------------------------------------------------------------------
# The Sexton (the crypt's Warden): a gravedigger bent double under the headstones it carries, eyeless under its hood,
# a lantern swinging in front of it on a pole. It hunts by sound; when it snuffs the lantern the room goes dark.

def sexton():
    r = Rig("sexton", (128, 128))
    body = r.bone("body", (0, 8, 3))
    body.cube("pelvis", -5, -4, -3, 10, 6, 6)
    for side, nm in ((1, "l"), (-1, "r")):
        leg = body.bone("leg_" + nm, (side * 3.5, 0, 0), (-20, 0, 0))
        leg.cube("thigh_" + nm, -2, 0, -2, 4, 9, 4, mirror=side < 0, share=None if side > 0 else "thigh_l")
        shin = leg.bone("shin_" + nm, (0, 9, 0), (35, 0, 0))
        shin.cube("shin_" + nm, -1.5, 0, -1.5, 3, 8, 3, mirror=side < 0, share=None if side > 0 else "shin_l")
        shin.bone("foot_" + nm, (0, 7, 0), (-15, 0, 0)).cube("foot_" + nm, -2, 0, -4, 4, 2, 6, mirror=side < 0, share=None if side > 0 else "foot_l")
    chest = body.bone("chest", (0, -4, 0), (50, 0, 0))
    chest.cube("back", -7, -15, -4, 14, 15, 8)
    for k, (x, h, lean) in enumerate(((-4.5, 10, -9), (0, 14, 0), (4.5, 9, 10))):
        chest.bone(f"stone{k}", (x, -11 + k, 4), (-50, 0, lean)).cube(f"stone{k}", -2.5, -h, -1, 5, h, 2)
    pole = chest.bone("pole", (0, -14, 3), (-62, 0, 0))
    pole.cube("pole", -0.5, -22, -0.5, 1, 22, 1)
    boom = pole.bone("boom", (0, -22, 0), (12, 0, 0))
    boom.cube("boom", -0.5, -1, -15, 1, 1, 15)
    lan = boom.bone("lantern", (0, 0, -14.5))
    lan.cube("chain", -0.5, 0, -0.5, 1, 5, 1)
    lan.cube("lamp", -2, 5, -2, 4, 5, 4)
    lan.cube("lampcap", -2.5, 4, -2.5, 5, 1, 5)
    head = chest.bone("head", (0, -15, -1), (-34, 0, 0))
    head.cube("hood", -3.5, -6, -5, 7, 7, 8)
    arm = chest.bone("arm_r", (-8, -12, 0), (-62, 0, 8))
    arm.cube("upper_r", -2, 0, -2, 4, 12, 4)
    fore = arm.bone("fore_r", (0, 12, 0), (-24, 0, 0))
    fore.cube("fore_r", -2, 0, -2, 4, 11, 4)
    sp = fore.bone("spade", (0, 10, 0), (-147, 0, 0))
    sp.cube("shaft", -0.5, -8, -0.5, 1, 22, 1)
    sp.cube("blade", -3.5, 14, -0.5, 7, 9, 1)
    arm = chest.bone("arm_l", (8, -12, 0), (-44, 0, -8))
    arm.cube("upper_l", -2, 0, -2, 4, 12, 4)
    fore = arm.bone("fore_l", (0, 12, 0), (-30, 0, 0))
    fore.cube("fore_l", -2, 0, -2, 4, 11, 4)
    fore.bone("fist_l", (0, 11, 0)).cube("fist_l", -2.5, 0, -2.5, 5, 4, 5)
    r.pack()
    return r


def paint_sexton(r, P):
    rng = P.rng
    coat = Ramp("#2c2620", "#40372c", "#574b3c", "#70614e", "#8a7962", "#a4937a")
    P.paint("back", coat, 2.4, p_strokes(3, 5, seed=1), rim=0.5)
    P.paint("pelvis", coat, 1.6, p_strokes(3, 4, seed=2))
    P.paint("hood", coat, 3.2, rim=0.8)
    f = P.face("hood", "north")
    for i, j in f.each():
        f.set(i, j, hexc("#08070a") if 0 < i < f.w - 1 and j > 0 else coat.at(4.2))
    for i in range(1, f.w - 1, 2):
        f.set(i, 3, hexc("#3a3028"))   # stitched shut where eyes would be
    for nm in ("thigh_l", "upper_r", "upper_l"):
        P.paint(nm, coat, 2.0, p_bands(4, 0.6))
    for nm in ("shin_l", "fore_r", "fore_l"):
        P.paint(nm, CLOTH, 2.2, p_bands(3, 0.9))
    P.paint("foot_l", IRON, 2.0)
    P.paint("fist_l", BONE, 3.0, p_bands(2, 0.7, vertical=True))
    for k in range(3):
        P.paint(f"stone{k}", LID, 2.6, p_stone(rng, 0.6, 4), rim=0.8)
        f = P.face(f"stone{k}", "south")
        for j in (2, 4, 6):
            for i in range(1, f.w - 1 - (j % 4) // 2):
                f.set(i, j, LID.at(0.6))
    for nm in ("pole", "boom", "shaft"):
        P.paint(nm, Ramp("#3a2c1c", "#57432a", "#75603e"), 1.2)
    P.paint("chain", IRON, 2.4)
    P.paint("lampcap", IRON, 2.6)
    P.paint("blade", IRON, 2.8, rim=1.2)
    warm, hot = "#ffb04a", "#fff0c0"
    for fname in ALL:
        f, g = P.face("lamp", fname), P.face("lamp", fname, glow=True)
        for i, j in f.each():
            bar = fname in SIDES and i in (0, f.w - 1)
            c = IRON.at(2.4) if bar else hexc(hot if 0 < j < f.h - 1 and fname in SIDES and i in (1, 2) and j in (2, 3) else warm)
            f.set(i, j, c)
            if not bar:
                g.set(i, j, c[:3] + (235,))
    return P.skin, P.glow


SEXTON_POSES = [
    ("listens for you", {}),
    ("drives the spade down", {"chest": (-22, 0, 0), "arm_r": (-96, 0, 0), "fore_r": (-30, 0, 0), "spade": (128, 0, 0), "head": (14, 0, 0),
                               "lantern": (22, 0, 0), "arm_l": (-10, 0, -10)}),
    ("snuffs its light: the room goes dark", {"chest": (-10, 0, 0), "arm_l": (-124, 0, 16), "fore_l": (-40, 0, 0), "head": (-10, 0, 0), "lantern": (10, 0, 0),
                                              "lamp": None}),
]


# ---------------------------------------------------------------------------------------------------------------------

MONSTERS = {
    "toller": (toller, paint_toller, TOLLER_POSES),
    "mourner": (mourner, paint_mourner, MOURNER_POSES),
    "chime": (chime, paint_chime, CHIME_POSES),
    "pallbearer": (pallbearer, paint_pallbearer, PALL_POSES),
    "sexton": (sexton, paint_sexton, SEXTON_POSES),
}
NAMES = {"toller": "The Toller", "mourner": "The Mourner", "chime": "The Chime", "pallbearer": "The Pallbearer", "sexton": "The Sexton"}

BG_CRYPT = (22, 26, 44)
BG_DAY = (96, 122, 138)
_CACHE = {}


def built(name):
    if name not in _CACHE:
        from PIL import Image
        make, paint, poses = MONSTERS[name]
        rig = make()
        P = R.Painter(rig, name)
        skin, glow = paint(rig, P)
        sk, gl = skin.image(), glow.image()
        # what a candle-lit crypt shows: the skin dim and warm, the glow at full strength; and a mask of the glow alone
        base, mask = sk.copy(), sk.copy()
        bp, mp, gp = base.load(), mask.load(), gl.load()
        for y in range(base.height):
            for x in range(base.width):
                r_, g_, b_, a_ = bp[x, y]
                bp[x, y] = (int(r_ * 0.55), int(g_ * 0.47), int(b_ * 0.40), a_)
                ga = gp[x, y]
                mp[x, y] = (ga[0], ga[1], ga[2], 255) if ga[3] >= 16 else (0, 0, 0, a_)
        _CACHE[name] = (rig, Image.alpha_composite(sk, gl), Image.alpha_composite(base, gl), mask, poses)
    return _CACHE[name]


def tree_of(name, pose=None, lift=0.0):
    rig = built(name)[0]
    pose = {k: v for k, v in (pose or {}).items() if v is not None}
    t = rig.tree(poses_deg(pose))
    t["pivot"] = (0, -lift, 0)
    return t


def place(canvas, name, tree, yaw, pitch, px, at, mode="crypt", halo=True):
    """Draws one posed body onto an RGB canvas with its feet (model 0, 24, 0) at the pixel `at`."""
    from PIL import Image, ImageChops, ImageFilter
    _, lit, dim, mask, _ = built(name)
    W, H = canvas.size
    ya, pa = math.radians(yaw), math.radians(pitch)
    g = R._mv(R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0)), (0, 24, 0))
    origin = (W, H, at[0] - g[0] * px, at[1] - g[1] * px)
    bd = (0, 1, 0, 1)
    if mode == "black":
        im = R.render(tree, lit, yaw=yaw, pitch=pitch, px=px, bounds=bd, origin=origin)
        flat = Image.new("RGBA", (W, H), (8, 9, 14, 255))
        canvas.paste(flat, (0, 0), im.split()[3].point(lambda v: 255 if v > 40 else 0))
        return
    im = R.render(tree, lit if mode == "day" else dim, yaw=yaw, pitch=pitch, px=px, bounds=bd, origin=origin)
    canvas.paste(im, (0, 0), im)
    if mode != "day" and halo:
        m = R.render(tree, mask, yaw=yaw, pitch=pitch, px=px, bounds=bd, origin=origin).convert("RGB")
        soft = m.filter(ImageFilter.GaussianBlur(px * 1.6)).point(lambda v: min(255, int(v * 0.9)))
        wide = m.filter(ImageFilter.GaussianBlur(px * 4.5)).point(lambda v: min(255, int(v * 0.8)))
        canvas.paste(ImageChops.add(ImageChops.add(canvas, soft), wide))


VIEW = {"mourner": (22, 10), "pallbearer": (40, 22)}


def strip(name, outdir, cell=(300, 320), px=None, yaw=48, pitch=10):
    """The fight, pose by pose, in crypt light."""
    from PIL import Image, ImageDraw
    yaw, pitch = VIEW.get(name, (yaw, pitch))
    poses = built(name)[4]
    lift = 14 if name == "chime" else 0
    trees = [tree_of(name, p, lift) for _, p in poses]
    bd = R.bounds_of(trees, yaw, pitch)
    px = px or min((cell[0] - 30) / (bd[1] - bd[0]), (cell[1] - 40) / (bd[3] - bd[2]))
    out = Image.new("RGB", (cell[0] * len(poses), cell[1] + 26), (16, 18, 28))
    d = ImageDraw.Draw(out)
    g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
    for k, ((label, _), t) in enumerate(zip(poses, trees)):
        c = Image.new("RGB", cell, BG_CRYPT)
        ImageDraw.Draw(c).rectangle((0, cell[1] - 22, cell[0], cell[1]), fill=(30, 34, 54))
        place(c, name, t, yaw, pitch, px, (cell[0] / 2 - ((bd[0] + bd[1]) / 2 - g[0]) * px, cell[1] - 14 - (bd[3] - g[1]) * px))
        out.paste(c, (k * cell[0], 0))
        d.text((k * cell[0] + 8, cell[1] + 7), f"{k + 1}. {label}", fill=(226, 229, 240))
    path = os.path.join(outdir, f"fight_{name}.png")
    out.save(path)
    return path


def turn(name, outdir, cell=(300, 320)):
    """Three sides in plain light, one in the crypt."""
    from PIL import Image
    out = Image.new("RGB", (cell[0] * 4, cell[1]), BG_DAY)
    lift = 14 if name == "chime" else 0
    t = tree_of(name, built(name)[4][0][1], lift)
    for k, (yaw, pitch, mode) in enumerate(((32, 14, "day"), (118, 8, "day"), (-150, 16, "day"), (32, 14, "crypt"))):
        bd = R.bounds_of([t], yaw, pitch)
        px = min((cell[0] - 40) / (bd[1] - bd[0]), (cell[1] - 40) / (bd[3] - bd[2]))
        g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
        c = Image.new("RGB", cell, BG_CRYPT if mode == "crypt" else BG_DAY)
        place(c, name, t, yaw, pitch, px, (cell[0] / 2 - ((bd[0] + bd[1]) / 2 - g[0]) * px, cell[1] - 18 - (bd[3] - g[1]) * px), mode)
        out.paste(c, (k * cell[0], 0))
    path = os.path.join(outdir, f"turn_{name}.png")
    out.save(path)
    return path


def _biped(head, body, arm, leg, arms_fwd=0.0, wings=False):
    """A plain box figure for a silhouette of what a monster wears today (shape only: no texture is read)."""
    def b(x, y, z, w, h, d):
        return {"u": 0, "v": 0, "x": x, "y": y, "z": z, "w": w, "h": h, "d": d, "mirror": False, "grow": 0.0}

    def p(pivot, boxes, rot=(0, 0, 0)):
        return {"pivot": pivot, "rot": rot, "scale": (1, 1, 1), "boxes": boxes, "children": []}
    hw, hh, hd = head
    bw, bh, bd_ = body
    aw, ah = arm
    lw, lh = leg
    top = 24 - lh - bh
    kids = [p((0, top, 0), [b(-hw / 2, -hh, -hd / 2, hw, hh, hd)]), p((0, top, 0), [b(-bw / 2, 0, -bd_ / 2, bw, bh, bd_)]),
            p((-bw / 2 - aw / 2, top + 2, 0), [b(-aw / 2, -2, -aw / 2, aw, ah, aw)], (-arms_fwd * DEG, 0, 0)),
            p((bw / 2 + aw / 2, top + 2, 0), [b(-aw / 2, -2, -aw / 2, aw, ah, aw)], (-arms_fwd * DEG, 0, 0)),
            p((-bw / 4, 24 - lh, 0), [b(-lw / 2, 0, -lw / 2, lw, lh, lw)]), p((bw / 4, 24 - lh, 0), [b(-lw / 2, 0, -lw / 2, lw, lh, lw)])]
    if wings:
        kids += [p((s_ * 1, top + 1, 2), [b(0 if s_ > 0 else -10, 0, 0, 10, 6, 0)], (0, 0, s_ * 0.3)) for s_ in (1, -1)]
    return {"pivot": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1), "boxes": [], "children": kids}


TODAY = (("Toll Ghoul", "a zombie", _biped((8, 8, 8), (8, 12, 4), (4, 12), (4, 12), 80), 1.0, 0),
         ("Crypt Archer", "a skeleton", _biped((8, 8, 8), (8, 12, 4), (2, 12), (2, 12), 0), 1.0, 0),
         ("Bell Wisp", "a vex", _biped((5, 5, 5), (3, 4, 2), (1, 4), (1, 4), 40, True), 1.0, 14),
         ("Crypt Keeper", "a wither skeleton", _biped((8, 8, 8), (8, 12, 4), (2, 12), (2, 12), 0), 1.2, 0),
         ("The Sexton", "the Warden", _biped((16, 16, 10), (18, 21, 11), (8, 28), (6, 13), 0), 1.0, 0))
ORDER = ("toller", "mourner", "chime", "pallbearer", "sexton")


def silhouettes(outdir, px=3.8, col=250):
    """Shape first: what each Bell Crypt monster's outline is today and what it would be, at one scale."""
    from PIL import Image, ImageDraw
    W, H = col * 5 + 40, 520
    out = Image.new("RGB", (W, H), (58, 66, 96))
    d = ImageDraw.Draw(out)
    yaw, pitch = 62, 6
    blank = Image.new("RGBA", (128, 128), (0, 0, 0, 255))
    for k, (nm, wears, tree, sc, lift) in enumerate(TODAY):
        x = 20 + col * k + col / 2
        t = dict(tree)
        t["scale"] = (sc, sc, sc)
        t["pivot"] = (0, 24 - 24 * sc - lift, 0)
        bd = (0, 1, 0, 1)
        g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
        im = R.render(t, blank, yaw=yaw, pitch=pitch, px=px, bounds=bd, origin=(W, H, x - g[0] * px, 222 - g[1] * px))
        out.paste(Image.new("RGB", (W, H), (8, 9, 14)), (0, 0), im.split()[3].point(lambda v: 255 if v > 40 else 0))
        d.text((x - 60, 232), f"{nm}: {wears}", fill=(226, 229, 240))
    for k, name in enumerate(ORDER):
        x = 20 + col * k + col / 2
        place(out, name, tree_of(name, {}, 14 if name == "chime" else 0), yaw, pitch, px, (x, 480), "black")
        d.text((x - 60, 490), NAMES[name], fill=(226, 229, 240))
    d.line((0, 222, W, 222), fill=(40, 46, 70))
    d.line((0, 480, W, 480), fill=(40, 46, 70))
    d.text((8, 6), "TODAY", fill=(180, 188, 214))
    d.text((8, 262), "PROPOSED", fill=(180, 188, 214))
    path = os.path.join(outdir, "silhouettes.png")
    out.save(path)
    return path


def lineup(outdir, px=5.0):
    """The proposed company in crypt light, the player beside them for size."""
    from PIL import Image, ImageDraw
    W, H = 1500, 420
    out = Image.new("RGB", (W, H), BG_CRYPT)
    d = ImageDraw.Draw(out)
    d.rectangle((0, H - 46, W, H), fill=(30, 34, 54))
    yaw, pitch = 30, 8
    ground = H - 30
    pl = R.render(R.player_tree(), R.steve(), yaw=yaw, pitch=pitch, px=px)
    dim = pl.point(lambda v: int(v * 0.5))
    dim.putalpha(pl.split()[3])
    out.paste(dim, (40, ground - pl.height + 12), dim)
    d.text((70, H - 16), "you", fill=(200, 205, 222))
    xs = {"toller": 330, "chime": 520, "mourner": 700, "pallbearer": 930, "sexton": 1230}
    for name, x in xs.items():
        place(out, name, tree_of(name, {}, 20 if name == "chime" else 0), yaw, pitch, px, (x, ground))
        d.text((x - 36, H - 16), NAMES[name], fill=(200, 205, 222))
    path = os.path.join(outdir, "lineup.png")
    out.save(path)
    return path


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "../img")
    os.makedirs(out, exist_ok=True)
    for n in MONSTERS:
        rig = built(n)[0]
        print(n, len(rig.bones), "bones", len(rig.cubes), "cubes")
        print(strip(n, out))
        print(turn(n, out))
    print(silhouettes(out))
    print(lineup(out))
