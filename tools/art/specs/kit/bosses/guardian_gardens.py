"""The Thornback (SPEC §35.11, docs/bosses/ROLLOUT.md): the Hanging Gardens' Guardian, a Beast. The Thorn Pangolin,
picked from its silhouette round 2026-10-29: "Something heavy moves in the hanging gardens."

A pangolin the length of a cart. Its scales are broad leaves of holly, dark and glossy and thorned at their edges,
overlapping like roof tiles down its arched back to a long heavy tail scaled all round; between them grow pink garden
roses. Its skin underneath is soft and brown. It walks on its knuckles, its great front claws folded under, its small
head low on a long snout that never stops sniffing, a long tongue flicking out of it.

Moves (SPEC §35.11): it rakes you with one great front claw, then the other (LIGHT); the Maul (it rears half up, both
claws raised, holds, and comes down on you: HEAVY); its tail swung round at whoever is behind it; the Thornback Charge
(it rolls itself up into a ball of thorns and comes at you across the floor, a gap closer: HEAVY); the Gale of Petals
(it rears right up on its hind legs on its tail and shakes itself, and the roses blow off it in a storm); Lurkers (it
digs at the floor with both claws and the vine lurkers come up out of it); and Thorn Spray (the signature: it hunches,
its scales lifting and rattling all down its back, and snaps them open, and the thorns fly). Broken, it curls up into
its ball; that is how it waits for you, too. It dies on its side, curling loosely round itself.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_gardens"
SCALE = 1.0  # the hitbox on the def's RAVAGER model at size 2.2 x 2.3: round its body, the tail out behind

# The Thorn Pangolin (docs/bosses/ROLLOUT.md, B). 7 blocks from snout to tail tip, the arch of its back 2.3 blocks up,
# its hips 1.4. Curled, a ball 2.8 blocks across. Value story: the roses brightest, then the pale rims and thorns of
# its leaf scales, then their dark glossy green, then its brown skin, darkest under it; the claws pale.
HIP = 22.0  # the hips' pivot (pixels above the floor)
HIPZ = 10.0  # and how far behind its middle
BALL = 23.0  # the ball's middle, curled: the roll bone sits here
SIDE = (1, -1, 1, -1)  # legs: 0 front left (+x), 1 front right, 2 back left, 3 back right
FRONT, BACK = (0, 1), (2, 3)
LIMB = {0: ("arm0", "fore0", "hand0"), 1: ("arm1", "fore1", "hand1"), 2: ("thigh2", "shin2", "foot2"), 3: ("thigh3", "shin3", "foot3")}
# the wrist, the ankle: the hands and feet a hair over the floor (the ground pass takes 0.2 px under it as in it)
REST_FOOT = {0: (8.0, 4.3, -17.0), 1: (-8.0, 4.3, -17.0), 2: (8.5, 3.3, 12.0), 3: (-8.5, 3.3, 12.0)}
POLE = {0: (0.2, 0.0, 1.0), 1: (-0.2, 0.0, 1.0), 2: (0.2, 0.0, -1.0), 3: (-0.2, 0.0, -1.0)}  # elbows back, knees forward
SPINE = ("rear", "mid", "front", "neck", "head")
TAIL = ("t0", "t1", "t2", "t3", "t4")
# the scale rows that lift: (bone, on, hinge (x, up, z) on it, length, width, rest pitch +tip up)
ROWS = (("sc_neck", "neck", (0, 4.0, -8.5), 10, 11, 14.0), ("sc_front", "front", (0, 7.0, -12.0), 14, 19, 16.0),
        ("sc_mid", "mid", (0, 10.0, -14.0), 16, 22, -8.0), ("sc_rear", "rear", (0, 8.0, -6.0), 16, 20, -9.0),
        ("sc_t0", "t0", (0, 4.0, 0.0), 11, 14, -6.0), ("sc_t1", "t1", (0, 4.0, 0.0), 11, 12, -6.0),
        ("sc_t2", "t2", (0, 3.5, 0.0), 11, 11, -6.0), ("sc_t3", "t3", (0, 2.5, 0.0), 10, 9, -6.0))
ROW_NAMES = tuple(r[0] for r in ROWS)
# curled into the ball (degrees on each joint: the front half nose down, the tail tip down and under it), and the legs folded in under it
CURL = {"rear": 0.0, "mid": 62.0, "front": 66.0, "neck": 58.0, "head": 70.0, "t0": -34.0, "t1": -40.0, "t2": -40.0, "t3": -40.0, "t4": -36.0}
FOLD = {0: {"arm0": (-80.0, 0, 0), "fore0": (110.0, 0, 0)}, 1: {"arm1": (-80.0, 0, 0), "fore1": (110.0, 0, 0)},
        2: {"thigh2": (-95.0, 0, 0), "shin2": (120.0, 0, 0)}, 3: {"thigh3": (-95.0, 0, 0), "shin3": (120.0, 0, 0)}}
TW = (0.14, 0.18, 0.21, 0.23, 0.24)  # a tail turn shared down its length, more toward the tip


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    rl = _bone(base, "roll", 0, BALL, 0)
    rear = _bone(rl, "rear", 0, HIP - BALL, HIPZ)
    mid = _bone(rear, "mid", 0, 1.0, -6.0)
    fr = _bone(mid, "front", 0, -1.0, -14.0)
    nk = _bone(fr, "neck", 0, -2.0, -12.0, (12, 0, 0))
    hd = _bone(nk, "head", 0, -1.0, -8.0, (16, 0, 0))
    # the body under its scales: soft brown skin, the belly a little paler and bare
    _box(rear, "pelvis", 0, -7, 1, 18, 15, 14)
    _box(rear, "rbelly", 0, -8, 0, 14, 2, 12)
    _box(mid, "chest", 0, -8, -7, 20, 18, 14)
    _box(mid, "mbelly", 0, -9, -7, 15, 2, 12)
    _box(fr, "shoulders", 0, -7, -6, 17, 14, 12)
    _box(fr, "fbelly", 0, -8, -6, 12, 2, 10)
    _box(nk, "neckc", 0, -5, -4, 10, 9, 9)
    # the small head on its long snout, a leaf scale on its crown, small ears, and the tongue inside the snout
    _box(hd, "skull", 0, -5, -4, 9, 8, 8)
    _box(hd, "snout", 0, -5, -14, 5, 4, 12)
    _box(hd, "nose", 0, -4.5, -20.5, 4, 3, 1)
    _box(hd, "crown", 0, 3, -3.5, 10, 2, 9)
    _box(hd, "brow", 0, 2, -8.5, 7, 2, 3)
    for s, nm in ((1, "l"), (-1, "r")):
        _box(hd, "ear_" + nm, s * 4.8, 0, -0.5, 1, 2, 2)
    tg = _bone(hd, "tongue", 0, -3.5, -12)
    _box(tg, "tongue", 0, -0.5, -2, 1, 1, 10)
    # the tail, five segments tapering to its tip, drooping toward the floor
    par, prev = rear, None
    for i, (w, h, y0) in enumerate(((12, 10, -6), (10, 9, -5), (9, 8, -4.5), (7, 6, -3.5), (5, 5, -2.5))):
        t = _bone(par, f"t{i}", 0, 2.0 if i == 0 else 0.0, 8.0 if i == 0 else 9.0, (-10 if i == 0 else -8, 0, 0))
        _box(t, f"tail{i}", 0, y0, 4.5, w, h, 9)
        par = t
    _box(par, "tailtip", 0, -2, 10, 3, 3, 2)
    _box(par, "tipscale", 0, 2.5, 5.5, 6, 2, 10)
    # the scale rows: each a broad holly leaf hinged at its front edge, its tip lying over the row behind, thorned at its
    # tip and at its edges; the flanks below them scaled too, and roses growing in between
    segs = {b.name: b for b in (rear, mid, fr, nk) + tuple(r.by_name[n] for n in TAIL)}
    for nm, on, (x, up, z), ln, w, pitch in ROWS:
        pb = _bone(segs[on], nm, x, up, z, (pitch, 0, 0))
        _box(pb, nm + "_a", 0, 0, ln * 0.35, w, 2, round(ln * 0.7))
        _box(pb, nm + "_b", 0, -0.5, ln * 0.8, round(w * 0.62), 2, round(ln * 0.45))
        _box(pb, nm + "_t", 0, 1.5, ln * 0.82, 1, 3, 1)
        for s, side in ((1, "l"), (-1, "r")):
            _box(pb, f"{nm}_e{side}", s * (w / 2 + 0.5), 0.5, ln * 0.5, 1, 1, 2)
    # the flanks, scaled down to the belly: (segment, bottom, height, middle z, length, half width)
    for nm, seg, y0, h, zc, ln, half in (("fl_mid", mid, -3, 12, -7, 14, 10), ("fl_rear", rear, -2, 9, 1, 14, 9), ("fl_front", fr, -2, 8, -6, 12, 8.5),
                                         ("fl_neck", nk, -1, 4, -4, 9, 5), ("fl_t0", segs["t0"], -4, 7, 4.5, 9, 6), ("fl_t1", segs["t1"], -3, 6, 4.5, 9, 5),
                                         ("fl_t2", segs["t2"], -3, 5, 4.5, 9, 4.5), ("fl_t3", segs["t3"], -2.5, 4, 4.5, 9, 3.5)):
        for s, side in ((1, "l"), (-1, "r")):
            _box(seg, f"{nm}{side}", s * (half + 0.5), y0, zc, 1, h, ln)
    for k, (on, x, z) in enumerate((("sc_mid", 7, 3), ("sc_mid", -6, 6), ("sc_rear", 5, 4), ("sc_rear", -7, 2), ("sc_front", 6, 3), ("sc_front", -5, 5),
                                    ("sc_t0", 4, 3), ("sc_t1", -3, 3), ("sc_neck", 3, 2), ("sc_mid", 0, 12))):
        _box(r.by_name[on], f"rose{k}", x, 1.5, z, 3, 2, 3)
    # four legs: the front ones knuckle-walking, the great claws folded back under the hand; the back ones flat-footed
    for k in range(4):
        s = SIDE[k]
        if k in FRONT:
            a = _bone(fr, f"arm{k}", s * 7.0, -4.0, -6.0)
            a.cube(f"arm{k}", -2.5, -1, -2.5, 5, 10, 5)
            a.cube(f"armp{k}", 2.5 if s > 0 else -3.5, -1, -3.5, 1, 8, 7)
            f = a.bone(f"fore{k}", (0, 9, 0))
            f.cube(f"fore{k}", -2, 0, -2, 4, 9, 4)
            h = f.bone(f"hand{k}", (0, 8, 0))
            h.cube(f"hand{k}", -2.5, 0, -3, 5, 4, 5)
            c = h.bone(f"claws{k}", (0, 3.5, -3))
            for j, (cx, w, ln) in enumerate(((-2.5, 1, 6), (-1, 2, 8), (1.5, 1, 6))):  # the middle claw the great one
                c.cube(f"claw{k}_{j}", cx, -2, 0, w, 2, ln)
        else:
            t = _bone(rear, f"thigh{k}", s * 7.5, -4.0, 2.0)
            t.cube(f"thigh{k}", -3, -1, -3.5, 6, 11, 7)
            t.cube(f"thighp{k}", 3 if s > 0 else -4, -1, -4, 1, 8, 8)
            sh = t.bone(f"shin{k}", (0, 10, 0))
            sh.cube(f"shin{k}", -2, 0, -2, 4, 9, 4)
            ft = sh.bone(f"foot{k}", (0, 9, 0))
            ft.cube(f"foot{k}", -2.5, 0, -6, 5, 3, 8)
            for j, cx in enumerate((-2, -0.5, 1)):
                ft.cube(f"toe{k}_{j}", cx, 2, -8, 1, 1, 2)
    for k in range(4):
        ik.rest_limb(r, *LIMB[k], REST_FOOT[k], POLE[k])
    fr0 = core.frames(r, {})
    for k in range(4):  # the hands and feet set flat on the floor at rest
        end = LIMB[k][2]
        r.by_name[end].rot = tuple(v + w for v, w in zip(r.by_name[end].rot, ik.orient(r, fr0, end, (0, 1, 0), (1, 0, 0))))
        fr0 = core.frames(r, {})
    r.look = [("head", 0.35, 0.3), ("neck", 0.25, 0.2)]
    r.shadow = 2.2
    r.size = 1.0
    # secondary parts (SPEC §35.10): the tail's tip swings, the roses nod
    r.springs = [("t3", 0.5, 1.2, 0.08), ("t4", 0.5, 1.6, 0.12)]
    r.guard = [f"hand{k}" for k in FRONT] + [f"foot{k}" for k in BACK] + ["tail4", "tailtip", "snout", "nose"]
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

SKIN = Ramp("#1a110c", "#241811", "#302016", "#3d2a1d", "#4b3424", "#5a3f2c", "#6a4b35", "#7b5940")
LEAF = Ramp("#0a1408", "#0f1d0c", "#152711", "#1c3317", "#24401d", "#2d4e24", "#385e2c", "#456f35", "#54823f")
RIM = Ramp("#5a6a2a", "#76863a", "#94a44e", "#b0bc68", "#cad488")
THORN = Ramp("#3a2a14", "#54401e", "#6e582a", "#8a7238", "#a68e4c", "#c2aa64")
PETAL = Ramp("#7a1c3a", "#a02c50", "#c4446a", "#de6488", "#ee8aa6", "#f8b4c6", "#ffdce6")
CLAW = Ramp("#5c5040", "#7a6c58", "#988a72", "#b6a88e", "#d0c4aa")
EYE = ("#120a06", "#3a2410", "#c08a30", "#f0c060")


def paint(r, P_):
    """Brown skin with a short coat, the belly paler and bare; each leaf scale dark glossy green with a pale rim and a
    pale midrib running back to its thorned tip, the thorns ochre; roses pink with pale centres; the claws pale horn."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    coat = R.p_fur(depth=0.6, every=3, seed=2, length=3)
    for c in ("pelvis", "chest", "shoulders", "neckc", "skull") + tuple(of("tail")):
        P_.paint(c, SKIN, 4.4, coat, grad=1.0, rim=0.5, ao=0.9, noise=0.5, nscale=4)
    for c in ("rbelly", "mbelly", "fbelly"):
        P_.paint(c, SKIN, 3.6, None, grad=0.4, rim=0.2, ao=0.3, noise=0.4)
    for c in ("snout", "ear_l", "ear_r"):
        P_.paint(c, SKIN, 3.8, None, grad=0.6, rim=0.4, ao=0.5, noise=0.3)
    P_.paint("nose", SKIN, 1.0, None, rim=0.2)
    P_.paint("tongue", PETAL, 2.0, None, rim=0.3)
    for c in of("arm", "fore", "thigh", "shin"):
        if c.startswith(("armp", "thighp")):
            continue
        P_.paint(c, SKIN, 3.9, coat, grad=1.0, rim=0.4, ao=0.8, noise=0.4, nscale=4)
    for c in of("hand", "foot"):
        P_.paint(c, SKIN, 3.0, None, grad=0.8, rim=0.4, ao=0.6, noise=0.3)
    for c in of("claw", "toe"):
        P_.paint(c, CLAW, 3.0, None, grad=0.8, rim=0.6, ao=0.2)
    # the leaf scales: dark glossy green, a pale midrib back along the leaf, the rim lit, darker toward the base
    leaves = [c for c in of("sc_", "fl_", "armp", "thighp") if not c.endswith(("_t", "_el", "_er"))] + ["crown", "brow", "tipscale"]
    for c in leaves:
        P_.paint(c, LEAF, 4.6, R.p_blotch(c, scale=2, amount=0.5), grad=0.8, rim=1.0, ao=0.6, noise=0.5, nscale=3)
        f = P_.face(c, "top")
        for i, j in f.each():
            edge = i == 0 or i == f.w - 1
            mid_ = f.w > 3 and i == f.w // 2
            if edge:
                f.set(i, j, RIM.at(1.4 + (R._h(c, i, j) % 3) * 0.5))
            elif mid_:
                f.set(i, j, RIM.at(1.0 + 0.4 * j / max(1, f.h - 1)))
            elif (i + j) % 4 == 0 and R._h(c, j) % 2:
                f.set(i, j, LEAF.at(6.6))  # the gloss
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                if R._h(c, fname, i) % 3 == 0:
                    f.set(i, 0, RIM.at(2.5))  # its thorned edge, pale points
    for c in of("fl_"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():  # rounded leaves overlapping like tiles, each row set half a leaf along
                lx, ly = (i + (j // 3) % 2 * 2) % 4, j % 3
                tone = (R._h(c, fname, i // 4, j // 3) % 3) * 0.3
                if (ly == 0 and lx in (1, 2)) or (ly == 1 and lx in (0, 3)):
                    f.set(i, j, LEAF.at(6.0 + tone))  # each leaf's lit rounded edge
                elif ly == 2:
                    f.set(i, j, LEAF.at(1.4 if lx == 0 else 2.6))  # the shadow under it, darkest at the seam
                else:
                    f.set(i, j, LEAF.at(3.8 + tone + (0.6 if ly == 1 else 0.0)))
    for c in [c for c in of("sc_") if c.endswith(("_t", "_el", "_er"))]:
        P_.paint(c, THORN, 3.6, None, grad=1.2, rim=1.0, ao=0.3)
    # the roses: pink, paler at the heart of each, a deep red where the petals fold
    for c in of("rose"):
        P_.paint(c, PETAL, 3.6, None, grad=0.8, rim=0.8, ao=0.4, noise=0.6, nscale=2)
        f, g = P_.face(c, "top"), P_.face(c, "top", glow=True)
        for i, j in f.each():
            heart = 0 < i < f.w - 1 and 0 < j < f.h - 1
            f.set(i, j, PETAL.at(6.0) if heart else PETAL.at(4.2 + (i + j) % 2 * 0.8))
            g.set(i, j, hexc("#ffc0d2", 70 if heart else 30))
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                if (i + R._h(c, fname)) % 2:
                    f.set(i, f.h - 1, PETAL.at(1.0))
    # the eyes: small and dark, a gold glint
    with P_.eyes():
        for fname in ("west", "east"):
            f, g = P_.face("skull", fname), P_.face("skull", fname, glow=True)
            for i in (1, 2):
                f.set(i, 3, hexc(EYE[0], 255))
                g.set(i, 3, hexc(EYE[2], 200))
            f.set(1, 2, hexc(EYE[1], 255))
            g.set(2, 2, hexc(EYE[3], 255))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the body set and bent, each foot reaching where it stands, the scales lifted, the ball rolled

def _ease(f):
    f = max(0.0, min(1.0, f))
    return f * f * (3 - 2 * f)


def _flat(yaw):
    a = yaw * DEG
    return (math.cos(a), 0.0, -math.sin(a))


BALL_SHIFT = [0.0, 0.0, 0.0]  # set once the body is built: where the curled body's middle is, moved onto the roll bone


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), bend=0.0, side=0.0, twist=0.0, neck=(0, 0, 0), head=(0, 0, 0),
          tail=(0.0, 0.0), curl=0.0, spin=0.0, tilt=0.0, sink=0.0, feet=None, poles=None, hands=None, limbs=None, claws=(0.0, 0.0),
          flare=0.0, rows=None, tongue=0.0, extra=None):
    """lift (+ up), pitch (+ its front dipping), yaw (+ to its left), roll (+ its left side down) and shift (x, z) move
    its hips; bend (+ its front half curling down), side (+ bending to its left) and twist go through its back; neck and
    head add their own turns; tail (lift + up, sweep + to its left) shares a turn down the tail. curl (0 to 1) rolls it
    up into its ball, the legs folding in, and spin (+ rolling forward) and tilt (+ onto its left side) turn the ball on
    its roll bone, sink lowering it. feet {leg: (x, height, z)}: where the wrists and ankles go (the rest stand where they
    stand at rest); limbs {leg: {bone: turn}} sets a leg's turns outright instead (an arm drawn in off the floor); hands
    {leg: "free" | (down, across)}: how that hand or foot lies (flat on the floor if not given); claws (left, right)
    opens the front claws (1 spread to rake); flare lifts every scale row (degrees), rows {row: degrees} each one more;
    tongue (0 to 1) puts it out."""
    c = max(0.0, curl)
    e = _ease(c)
    fo = _ease(min(1.0, c * 1.6))
    # a ball held a little loose (curled 0.93) has its legs a little less tight in under it: they clench with it
    give = 1.0 - 0.14 * max(0.0, min(1.0, (1.0 - c) / 0.07)) * max(0.0, min(1.0, (c - 0.86) / 0.07))
    p = {"roll": {"rot": (spin, 0, tilt), "pos": (0, -sink, 0)},
         "rear": {"rot": (pitch, yaw, roll), "pos": (shift[0] + BALL_SHIFT[0] * e, lift + BALL_SHIFT[1] * e, shift[1] + BALL_SHIFT[2] * e)},
         "mid": (bend * 0.5 + CURL["mid"] * c, side * 0.5, twist * 0.5),
         "front": (bend * 0.5 + CURL["front"] * c, side * 0.5, twist * 0.5),
         "neck": (neck[0] + CURL["neck"] * c, neck[1], neck[2]),
         "head": (head[0] + CURL["head"] * c, head[1], head[2])}
    for i, t in enumerate(TAIL):
        p[t] = (tail[0] * TW[i] + CURL[t] * c, tail[1] * TW[i], 0)
    for j, nm in enumerate(ROW_NAMES):
        p[nm] = (flare * (0.8 + 0.08 * j) + (rows or {}).get(nm, 0.0), 0, 0)
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    limbs = limbs or {}
    for k in range(4):
        if k in limbs:
            lp = {b: limbs[k].get(b, (0.0, 0.0, 0.0)) for b in LIMB[k][:2]}
        else:
            lp = ik.limb(r, fr, *LIMB[k], ik.model(feet.get(k, REST_FOOT[k])), poles.get(k, POLE[k]))
        if fo > 0:
            lp = {b: tuple(v * (1 - fo) + w * fo * give for v, w in zip(lp[b], FOLD[k].get(b, lp[b]))) for b in lp}
        p.update(lp)
    fr = core.frames(r, core.as_sample(p))
    hands = hands or {}
    flat = _flat(yaw)
    for k in range(4):
        end = LIMB[k][2]
        how = hands.get(k)
        if how == "free":
            o = (0.0, 0.0, 0.0)
        else:
            down, across = how if how else ((0, 1, 0), flat)
            o = ik.orient(r, fr, end, down, across)
        if fo > 0:
            o = tuple(v * (1 - fo) + w * fo for v, w in zip(o, (60.0, 0, 0) if k in FRONT else (-40.0, 0, 0)))
        p[end] = o
    for k, a in zip(FRONT, claws):
        p[f"claws{k}"] = (-140.0 * a, 0, 0)
    p["tongue"] = {"rot": (0, 0, 0), "pos": (0, 0, -11.0 * tongue)}
    if extra:
        p.update(extra)
    return p


def _set_ball(r):
    """Where the curled body's middle sits with nothing moved, so the curl can carry it onto the roll bone (whose pivot
    is the ball's middle): the middle of the curled body's bounds, its spine, tail and scales."""
    BALL_SHIFT[:] = [0.0, 0.0, 0.0]
    fr = core.frames(r, core.as_sample(_pose(r, curl=1.0)))
    bones = SPINE + TAIL + ROW_NAMES
    pts = []
    for b in bones:
        for cb in r.by_name[b].cubes:
            for dx in (0, cb.w):
                for dy in (0, cb.h):
                    for dz in (0, cb.d):
                        pts.append(core.point(fr, b, (cb.x + dx, cb.y + dy, cb.z + dz)))
    mid = [(min(q[i] for q in pts) + max(q[i] for q in pts)) / 2 for i in range(3)]
    piv = fr["roll"][1]
    # the rear's pos is in the roll bone's space (unturned here): +y up in a pose
    BALL_SHIFT[:] = [piv[0] - mid[0], -(piv[1] - mid[1]), piv[2] - mid[2]]
    r.ball_size = [max(q[i] for q in pts) - min(q[i] for q in pts) for i in range(3)]


def _foot(k, up=0.0, dz=0.0, dx=0.0):
    x, h, z = REST_FOOT[k]
    return (x + dx, h + up, z + dz)


def _plant(r, clip, legs, where=None):
    """The feet that stand through a move keep where they stand on the floor, every tick."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(ik.limb(r, fr, *LIMB[k], ik.model(where.get(k, REST_FOOT[k])), POLE[k]))
        s2 = dict(s)
        s2.update(core.as_sample(out))
        fr2 = core.frames(r, s2)
        for k in legs:  # and lies flat on it, heading the way it heads
            end = LIMB[k][2]
            M = fr2[end][0]
            across = (M[0][0], 0.0, M[2][0])
            if abs(across[0]) + abs(across[2]) > 1e-3:
                out[end] = ik.orient(r, fr2, end, (0, 1, 0), across)
        return out
    ik.retrack(r, clip, fn)


def _with(a, b):
    return {**a, **b}


def _mix(a, b, f):
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


def _unwrap(keys):
    """Each key's turns written within half a turn of the key before (each axis by whole turns, the same pose)."""
    out, last = [], {}
    for t, p in keys:
        q = {}
        for b, v in p.items():
            rot = v["rot"] if isinstance(v, dict) else v
            if b in last:
                rot = tuple(c + 360 * round((last[b][i] - c) / 360) for i, c in enumerate(rot))
            last[b] = rot
            q[b] = {**v, "rot": rot} if isinstance(v, dict) else rot
        out.append((t, q))
    return out


def _floor(r, p, gap=0.3):
    """The pose let down or lifted on its roll bone until its lowest part just rests on the floor (a ball, a body lying
    on its side)."""
    fr = core.frames(r, core.as_sample(p))
    up = core.lowest(r, fr, list(r.by_name)) - (24.0 - gap)
    q = dict(p)
    rl = q["roll"]
    q["roll"] = {"rot": rl["rot"], "pos": (rl["pos"][0], rl["pos"][1] + up, rl["pos"][2])}
    return q


TAIL_PARTS = None


def _prop(r, p, gap=0.2):
    """A reared pose with its tail set down on the floor behind it as a prop: the tail's root lifted, and its tip laid
    back along the floor, until no part of it is under the floor."""
    global TAIL_PARTS
    if TAIL_PARTS is None:
        TAIL_PARTS = list(TAIL) + [n for n in ROW_NAMES if n.startswith("sc_t")]
    q = dict(p)
    for a in range(0, 124, 4):
        q = dict(p)
        for t, f in zip(TAIL, (0.5, 0.3, 0.2, -0.05, -0.1)):
            v = p[t]
            q[t] = (v[0] + a * f, v[1], v[2])
        if core.lowest(r, core.frames(r, core.as_sample(q)), TAIL_PARTS) <= 24.0 - gap:
            break
    return q


def _spin_track(r, mv, spins):
    """Re-keys the roll bone's turn through a staged move from [(seconds, degrees)] written unwrapped, so a ball that
    rolls a whole turn ends where it began instead of spinning back (stage_clips' settle scales the last pose's turn)."""
    keyed(r, "_spin", mv.seconds("windup") + mv.seconds("active") + mv.seconds("recover"), [(t, {"roll": (a, 0, 0)}) for t, a in spins])
    core.bake(r.clips["_spin"])
    r.clips[core.clip_name(mv)].tracks[("roll", "rot")] = r.clips.pop("_spin").tracks[("roll", "rot")]


def _wave(u, at, width):
    """A bump 0..1..0 centred on at, width wide."""
    x = (u - at) / width
    return math.cos(math.pi * x) ** 2 if abs(x) < 0.5 else 0.0


# ---------------------------------------------------------------------------------------------------------------------
# Clips

def _idle(r):
    """It stands on its knuckles breathing, its snout low and sniffing over the floor from side to side, the tongue
    flicking out of it; it lifts its head to scent the air, one front foot lifting a little; a ripple runs back down
    its scales from its neck to its tail; its tail sways."""
    L, n = 6.0, 90
    keys = []
    for k in range(n + 1):
        u = k / n
        w = 2 * math.pi * u
        up = _ease((u - 0.55) / 0.08) * (1 - _ease((u - 0.8) / 0.08))  # the head raised to scent the air
        sniff = math.sin(2 * w) * (1 - up)
        tg = _wave(u, 0.2, 0.05) + _wave(u, 0.42, 0.04) + 0.7 * _wave(u, 0.68, 0.04)
        rows = {nm: 9.0 * _wave(u, 0.28 + 0.025 * j, 0.06) for j, nm in enumerate(ROW_NAMES)}
        paw = _wave(u, 0.67, 0.16)
        keys.append((L * u, _pose(r, lift=0.5 * math.sin(3 * w) - 0.4 * up, pitch=-3 * up, yaw=1.2 * math.sin(w), roll=0.8 * math.sin(w + 1.2),
                                  side=2.0 * math.sin(w + 0.4), neck=(-14 * up, 4 * sniff, 0), head=(5 - 22 * up + 3 * math.sin(4 * w), 16 * sniff, 3 * sniff),
                                  tail=(3 * math.sin(w + 1.0), 12 * math.sin(w + 0.3)), feet={0: _foot(0, up=5 * paw, dz=-2 * paw)} if paw > 0.01 else None,
                                  claws=(0.25 * paw, 0), tongue=tg, rows=rows)))
    keyed(r, "idle", L, keys, loop=True)


WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # its walk: each foot by turns, back left, front left, back right, front right
TROT = {0: 0.0, 3: 0.0, 1: 0.5, 2: 0.5}  # its run: the diagonals together


def _gait(u, stride_px, beta, lift, groups):
    """The feet through a stride at u: down, each moves back as far as it travels, so it stays put on the floor; up, it
    lifts and reaches forward again."""
    feet = {}
    st = stride_px * beta
    for k, ph in groups.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f) ** 0.8, dz=st / 2 - st * core.ease_io(f))
    return feet


def _locomotion(r):
    """Its walk: one foot at a time, its back rolling with the steps and its snout swinging low over the floor, the
    tail swinging the other way. Running, it lumbers in a trot, the diagonals together, its back bounding and its tail
    swinging wide."""
    for name, L, stride, beta, lift, groups, bob, lean in (("walk", 1.2, 1.4, 0.72, 5.0, WALK, 0.5, 0.0), ("run", 0.7, 2.4, 0.5, 8.0, TROT, 1.8, 3.0)):
        sp = stride * 16
        keys = []
        n = 30
        for k in range(n + 1):
            u = k / n
            s = math.sin(2 * math.pi * u)
            s2 = math.sin(4 * math.pi * u)
            keys.append((L * u, _pose(r, lift=-bob * 0.5 * (1 + math.cos(4 * math.pi * u)), pitch=lean + 1.5 * s2 * (bob > 1), roll=2.0 * s, yaw=1.5 * s,
                                      side=4.0 * s, bend=2.0 * s2 * (bob > 1), neck=(-4 * (bob > 1), -3 * s, 0), head=(6 + 2 * s2, -8 * s, 0),
                                      tail=(4 + 4 * s2, -14 * math.sin(2 * math.pi * u - 0.6) * (1.6 if bob > 1 else 1.0)),
                                      feet=_gait(u, sp, beta, lift, groups), claws=(0.1, 0.1))))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.4, 2.4
    r.run_speed = (0.14, 0.3)
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        keys = []
        n = 18
        A = 16.0 * DEG
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in WALK.items():  # each foot steps round the way it turns while the others hold
                q = (u - ph) % 1.0
                if q < 0.7:
                    a = sgn * A * (0.5 - q / 0.7)
                    up_ = 0.0
                else:
                    f = (q - 0.7) / 0.3
                    a = sgn * A * (-0.5 + core.ease_io(f))
                    up_ = 5.0 * math.sin(math.pi * f)
                x, h, z = REST_FOOT[j]
                feet[j] = (x * math.cos(a) + z * math.sin(a), h + up_, -x * math.sin(a) + z * math.cos(a))
            keys.append((0.9 * u, _pose(r, yaw=sgn * 2.0, side=sgn * 5.0, roll=1.2 * math.sin(4 * math.pi * u), head=(5, sgn * 14, 0), tail=(2, -sgn * 10),
                                        feet=feet)))
        keyed(r, nm, 0.9, keys, loop=True)


def _ball(r, spin=0.0, flare=0.0, rows=None, curl=1.0, **kw):
    return _floor(r, _pose(r, curl=curl, spin=spin, flare=flare, rows=rows, **kw))


def _reactions(r):
    """Struck, it flinches, half curling with its head tucked and its scales jumping up; from the side it bends away.
    Its stance broken, it rolls itself up into its ball, as a pangolin does, and lies there; it unrolls out of it, its
    snout first."""
    hit = _pose(r, lift=1.5, bend=14, neck=(10, 0, 0), head=(18, 0, 0), tail=(12, 0), flare=20, claws=(0.4, 0.4))
    back = _pose(r, lift=-0.5, bend=-3, head=(-2, 0, 0), tail=(-3, 0), flare=4)
    keyed(r, "hurt", 0.55, [(0, _pose(r)), (0.08, hit), (0.3, back), (0.55, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it bends away to its right
        h = _pose(r, lift=1, roll=-6 * sgn, yaw=-6 * sgn, side=-12 * sgn, shift=(-sgn * 1.5, 0), head=(6, -16 * sgn, 0), tail=(6, 16 * sgn), flare=14)
        b = _pose(r, roll=2 * sgn, side=3 * sgn, head=(2, 4 * sgn, 0), tail=(0, -4 * sgn), flare=3)
        keyed(r, nm, 0.6, [(0, _pose(r)), (0.08, h), (0.32, b), (0.6, _pose(r))])
    tuck = _floor(r, _pose(r, lift=-3, bend=16, neck=(14, 0, 0), head=(26, 0, 0), tail=(-14, 0), curl=0.25, flare=6))
    half = _floor(r, _pose(r, curl=0.7, flare=8))
    keyed(r, "kneel", 1.0, [(0, _pose(r)), (0.3, tuck), (0.62, half), (0.85, _ball(r, spin=3, flare=6)), (1.0, _ball(r))])
    peek = _floor(r, _pose(r, curl=0.82, spin=-6, neck=(-30, 0, 0), head=(-40, 0, 0), flare=4))
    open_ = _floor(r, _pose(r, curl=0.4, spin=-4, neck=(-14, 0, 0), head=(-10, 0, 0), tail=(-6, 0), flare=2))
    keyed(r, "rise", 1.2, [(0, _ball(r)), (0.3, peek), (0.7, open_), (0.95, _pose(r, lift=-1.5, bend=4, head=(8, 0, 0))), (1.2, _pose(r))])
    _death(r)


def _death(r):
    """Its last: it rears up a little, its head flung up and its scales lifting; it staggers to its left; it goes over
    onto its side and lies there; it draws itself in, curling loosely round itself, its tail coming over; it breathes
    once, its scales rising and falling, and is still."""
    pain = _pose(r, lift=3, pitch=-14, neck=(-22, 0, 0), head=(-18, 0, 0), tail=(14, 0), feet={0: _foot(0, up=9, dz=-3), 1: _foot(1, up=9, dz=-3)},
                 hands={0: "free", 1: "free"}, flare=30, claws=(0.5, 0.5), tongue=0.6)
    stagger = _pose(r, lift=-2, pitch=-4, roll=10, side=12, yaw=4, neck=(-6, 8, 0), head=(0, 12, 0), tail=(4, -16), feet={0: _foot(0, dx=5, up=3)}, flare=16,
                    claws=(0.3, 0.3))
    over = _floor(r, _pose(r, tilt=52, roll=4, side=10, neck=(4, 6, 0), head=(6, 10, 0), tail=(-4, -10), curl=0.25, flare=8))
    down = _floor(r, _pose(r, tilt=88, side=8, neck=(6, 4, 0), head=(8, 6, 0), tail=(-6, -8), curl=0.32, flare=4))
    bounce = _floor(r, _pose(r, tilt=84, side=7, neck=(4, 4, 0), head=(4, 6, 0), tail=(-4, -6), curl=0.3, flare=6), gap=1.6)
    curl = lambda f, fl=0.0: _floor(r, _pose(r, tilt=88, side=8 + 10 * f, neck=(6 + 16 * f, 4, 0), head=(8 + 24 * f, 6, 0), tail=(-6 - 30 * f, -8 - 20 * f),
                                             curl=0.32 + 0.3 * f, flare=fl))
    keys = [(0, _pose(r)), (0.35, pain), (0.85, stagger), (1.3, over), (1.6, down), (1.75, bounce), (1.95, down), (2.8, curl(0.6)), (3.4, curl(0.8, 12)),
            (3.9, curl(0.85, 2)), (4.8, curl(0.88))]
    keyed(r, "death", 4.8, _unwrap(keys))


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b: 0 for k in range(4) for b in LIMB[k]}


def _lag(**kw):
    out = dict(NO_LAG)
    out.update(kw)
    return out


def _own(r, mv, keys):
    """Re-keys a staged move's clip from its own keys: for a move whose last active pose (a ball) is no pose to settle
    from, so the recovery is the move's own and not the library's settle past rest."""
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    lag = {b: 0.0 if b in NO_LAG else core.BREAK * max(0, core._depth(r, b) - 2) for b in r.by_name}
    core.bake(keyed(r, core.clip_name(mv), W + A + R_, keys, lag=lag, hold_until=W + A))


def _back(r, end):
    home = _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _cl(k, a):
    return (a, 0.0) if k == 0 else (0.0, a)


def claw(r, k=1, mid="claw"):
    """A great front claw raised high and out to its side, its claws spread, as it rears a little onto its other three
    feet and turns that way; then it rakes it down and across in front of it, its body swinging after, and drags the
    claws back through the floor (LIGHT)."""
    s = SIDE[k]
    free = {k: "free"}
    gather = _pose(r, lift=1, pitch=-3, yaw=5 * s, roll=-3 * s, side=6 * s, neck=(-3, -2 * s, 0), head=(-4, -8 * s, 0), tail=(6, -12 * s),
                   feet={k: _foot(k, up=13, dz=1, dx=4 * s)}, poles={k: (s * 0.8, -0.4, 0.5)}, claws=_cl(k, 0.7), hands=free, flare=12)
    rear = _pose(r, lift=1.5, pitch=-5, yaw=7 * s, roll=-4 * s, side=8 * s, neck=(-6, -4 * s, 0), head=(-6, -10 * s, 0), tail=(8, -16 * s),
                 feet={k: (s * 12, 27, -24)}, poles={k: (s * 1, 0.2, 0.4)}, claws=_cl(k, 1.0), hands=free, flare=18)
    # half way down the rake, the claw out in front at its shoulder's height, so it comes over the top and down
    swing = _pose(r, lift=0, pitch=-2, yaw=-1 * s, roll=0, side=1 * s, neck=(-6, 0, 0), head=(-6, 2 * s, 0), tail=(4, 0),
                  feet={k: (s * 6, 15, -34)}, poles={k: (s * 0.8, 0.0, 0.8)}, claws=_cl(k, 1.0), hands=free, flare=20)
    hit = _pose(r, lift=-2, pitch=3, yaw=-8 * s, roll=3 * s, side=-6 * s, neck=(-4, 3 * s, 0), head=(-4, 8 * s, 0), tail=(0, 12 * s),
                feet={k: (-s * 3, 2, -30)}, poles={k: (s * 0.6, -0.3, 1)}, claws=_cl(k, 1.0), hands=free, flare=20)
    held = _pose(r, lift=-2.5, pitch=4, yaw=-9 * s, roll=3.5 * s, side=-7 * s, neck=(-3, 3 * s, 0), head=(-2, 9 * s, 0), tail=(-1, 14 * s),
                 feet={k: (-s * 5, 1.5, -27)}, poles={k: (s * 0.6, -0.3, 1)}, claws=_cl(k, 0.9), hands=free, flare=17)
    through = _pose(r, lift=-2, pitch=3, yaw=-7 * s, roll=2.5 * s, side=-5 * s, neck=(-2, 2 * s, 0), head=(-1, 6 * s, 0), tail=(0, 10 * s), feet={k: (-s * 4, 2, -22)},
                    poles={k: (s * 0.6, -0.3, 1)}, claws=_cl(k, 0.6), hands=free, flare=18)
    back = _pose(r, lift=-1, yaw=-3 * s, side=-2 * s, feet={k: _foot(k, up=5, dz=-5, dx=-s)}, poles={k: (s * 0.6, -0.3, 1)}, claws=_cl(k, 0.25),
                 tail=(0, 5 * s), hands=free, flare=10)
    return Move(mid, "LIGHT", 10, 4, 14, through=through, track=8.0, rng=(0, 3.2), kinds=("combo", "near"), damage=1.0, knock=0.5,
                hit=[(f"claws{k}", (0, -1, 8), 3.0), (f"hand{k}", (0, 2, 0), 3.0)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.25, swing), (0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def maul(r):
    """The Maul (HEAVY): it rears half up on its hind legs, its tail set down behind it as a prop, both great claws
    raised and spread over you, its scales lifting; holds; then it drops its whole front onto you, claws first."""
    gather = _pose(r, lift=-3, pitch=4, bend=4, head=(-6, 0, 0), tail=(-6, 0), feet={0: _foot(0, dz=3), 1: _foot(1, dz=3)}, flare=14)
    up = {0: (11, 40, -22), 1: (-11, 40, -22)}
    out = {0: (1, -0.3, 0.4), 1: (-1, -0.3, 0.4)}
    both = {0: "free", 1: "free"}
    rear = _pose(r, lift=2, pitch=-34, bend=-6, neck=(-16, 0, 0), head=(-8, 0, 0), tail=(80, 0), feet=up, poles=out, hands=both, claws=(1, 1), flare=16,
                 tongue=0.4)
    down = {0: (9, 4.5, -29), 1: (-9, 4.5, -29)}
    fwd = {0: (0.6, -0.3, 1), 1: (-0.6, -0.3, 1)}
    slam = _pose(r, lift=-4, pitch=4, bend=3, neck=(-8, 0, 0), head=(-8, 0, 0), tail=(-4, 0), feet=down, poles=fwd, hands=both, claws=(1, 1), flare=20)
    held = _pose(r, lift=-4.5, pitch=5, bend=4, neck=(-7, 0, 0), head=(-5, 0, 0), tail=(-5, 0), feet={k: (v[0], v[1] - 0.5, v[2] - 0.5) for k, v in down.items()},
                 poles=fwd, hands=both, claws=(0.9, 0.9), flare=26)
    through = _pose(r, lift=-3.5, pitch=4, bend=3, neck=(-6, 0, 0), head=(-4, 0, 0), tail=(-4, 0), feet={k: (v[0], v[1] + 0.5, v[2] + 1.5) for k, v in down.items()},
                    poles=fwd, hands=both, claws=(0.7, 0.7), flare=12)
    return Move("maul", "HEAVY", 16, 6, 20, through=through, title="The Maul", hold=6, track=4.0, rng=(0, 3.5), kinds=("area", "near"), damage=1.3,
                knock=1.2, disc=2.5, hit=[("claws0", (0, -1, 8), 3.5), ("claws1", (0, -1, 8), 3.5)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.35, slam), (1.0, held)], "recover": [(0.5, _mix(through, _pose(r), 0.5))]},
                lag=_lag())


def tail_lash(r):
    """For whoever is behind it (LIGHT): it bends its body round to one side, looking back along itself, its tail drawn
    the same way; then it swings its heavy scaled tail across behind it the other way."""
    gather = _pose(r, lift=0.5, bend=3, side=8, yaw=3, roll=-2, neck=(-2, 10, 0), head=(0, 24, 4), tail=(10, 40), flare=8,
                   rows={nm: 4 * math.sin(0.9 * j) for j, nm in enumerate(ROW_NAMES)})
    wind = _pose(r, lift=1.5, bend=5, side=14, yaw=6, roll=-3, neck=(-4, 16, 0), head=(-4, 36, 8), tail=(18, 80), flare=14,
                 rows={nm: 5 * math.sin(0.9 * j + 1.5) for j, nm in enumerate(ROW_NAMES)})
    hit = _pose(r, lift=-1, side=-12, yaw=-6, roll=3, neck=(0, 8, 0), head=(0, 22, 0), tail=(8, -70), flare=14)
    held = _pose(r, lift=-1.5, side=-14, yaw=-7, roll=3.5, neck=(0, 6, 0), head=(0, 18, 0), tail=(6, -84), flare=10)
    back = _pose(r, side=-4, yaw=-2, head=(0, 8, 0), tail=(4, -20))
    return Move("tail", "LIGHT", 10, 4, 14, through=held, track=0.0, rng=(0, 3.5), kinds=("behind",), damage=0.9, knock=1.0,
                hit=[("t4", (0, -2, 9), 4.0), ("t3", (0, -2, 6), 4.0), ("t2", (0, -3, 6), 4.0)], plant=(),
                stages={"windup": [(0.3, gather), (1.0, wind)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


ADVANCE = 8.0  # blocks the charge rolls
BALL_R = 22.5  # the ball's radius, pixels: it turns a radian for each of these it rolls
ROLL_TURN = ADVANCE * 16.0 / BALL_R / DEG


def charge(r):
    """The Thornback Charge (HEAVY, a gap closer): it tucks its head and rolls itself up into a ball of thorns, rocks
    back on itself and holds, its scales lifting; then it rolls at you across the floor, turning as it goes; it skids to
    a stop, rocks, and unrolls, its snout first."""
    tuck = _floor(r, _pose(r, lift=-3, bend=16, neck=(14, 0, 0), head=(26, 0, 0), tail=(-14, 0), curl=0.3, flare=4))
    mid_ = _floor(r, _pose(r, curl=0.8, spin=-4, flare=8))
    rocked = _ball(r, spin=-16, flare=14)
    # rolling, it clenches and loosens with each turn, and its scales clatter up and down as they come round
    act = [(f, _ball(r, spin=-16 + ROLL_TURN * f, flare=14 - 6 * f, curl=1.0 - 0.035 * (1 - math.cos(2 * math.pi * 1.5 * f)),
                     rows={nm: 9 * math.sin(2 * math.pi * 1.75 * f + 0.8 * j) for j, nm in enumerate(ROW_NAMES)})) for f in [k / 10 for k in range(1, 11)]]
    through = _ball(r, spin=-16 + ROLL_TURN + 30, flare=6)
    peek = _floor(r, _pose(r, curl=0.8, spin=360 + 4, neck=(-30, 0, 0), head=(-40, 0, 0), flare=4))
    open_ = _floor(r, _pose(r, curl=0.45, spin=360, neck=(-14, 0, 0), head=(-10, 0, 0), tail=(-6, 0), flare=2))
    feet_ = _floor(r, _pose(r, curl=0.16, spin=360, lift=-1, neck=(-6, 0, 0), head=(-4, 0, 0), tail=(-4, 0), flare=1))
    mv = Move("charge", "HEAVY", 18, 14, 22, through=through, title="Thornback Charge", hold=8, track=6.0, lock=12, advance=ADVANCE, ability="Thornback Charge",
              rng=(5, 16), kinds=("gap",), damage=1.3, knock=1.4, hit=[("roll", (0, 0, 0), 24.0)], plant=(),
              stages={"windup": [(0.25, tuck), (0.6, mid_), (1.0, rocked)], "active": act, "recover": [(0.45, peek), (0.7, open_)]}, lag=_lag())
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    stand = _pose(r, lift=-1.2, bend=3, neck=(2, 0, 0), head=(4, 0, 0), tail=(-2, 0), flare=1)
    mv.own = [(0, _pose(r)), (0.25 * W, tuck), (0.6 * W, mid_), (W, rocked)] + [(W + f * A, p) for f, p in act] + \
             [(W + A + 0.25 * R_, through), (W + A + 0.45 * R_, peek), (W + A + 0.62 * R_, open_), (W + A + 0.78 * R_, feet_), (W + A + 0.9 * R_, stand),
              (W + A + R_, _pose(r))]
    mv.spins = [(0, 0), (0.25 * W, 0), (0.6 * W, -4), (W, -16)] + [(W + f * A, -16 + ROLL_TURN * f) for f, _ in act] + \
               [(W + A + 0.25 * R_, -16 + ROLL_TURN + 30), (W + A + 0.45 * R_, 364), (W + A + 0.62 * R_, 360), (W + A + 0.78 * R_, 361), (W + A + R_, 360)]
    return mv


def gale(r):
    """The Gale of Petals: it rears right up on its hind legs, its tail set on the floor behind it, its arms drawn in to
    its chest; and it shakes itself from its snout to its tail like a dog shaking off water, its scales lifting and
    clattering, and the roses blow off it in a storm."""
    arms = {0: {"arm0": (-60.0, 0, -10), "fore0": (-50.0, 0, 0)}, 1: {"arm1": (-60.0, 0, 10), "fore1": (-50.0, 0, 0)}}  # tucked to its chest
    crouch = _pose(r, lift=-3, pitch=4, head=(-10, 0, 0), tail=(-4, 0), flare=4)
    up = _prop(r, _pose(r, lift=1, pitch=-62, bend=-10, neck=(-14, 0, 0), head=(18, 0, 0), tail=(58, 0), limbs=arms, hands={0: "free", 1: "free"}, flare=10))

    def shake(s, a):
        return _prop(r, _pose(r, lift=1, pitch=-60, bend=-10, twist=24 * s * a, side=8 * s * a, roll=5 * s * a, neck=(-14, 12 * s * a, 0),
                              head=(18, 22 * s * a, -12 * s * a), tail=(56, -30 * s * a), limbs=arms, hands={0: "free", 1: "free"}, flare=26 + 10 * a,
                              rows={nm: 10 * s * a * (1 if j % 2 else -1) for j, nm in enumerate(ROW_NAMES)}))
    act = [(0.12, shake(1, 1)), (0.25, shake(-1, 1)), (0.38, shake(1, 1)), (0.5, shake(-1, 0.9)), (0.62, shake(1, 0.75)), (0.75, shake(-1, 0.55)),
           (0.88, shake(1, 0.3)), (1.0, shake(-1, 0.1))]
    # it comes down onto its front feet, reaching for the floor with them, and lands
    through = _prop(r, _pose(r, lift=1, pitch=-52, bend=-8, neck=(-12, 0, 0), head=(14, 0, 0), tail=(50, 0), limbs=arms, hands={0: "free", 1: "free"}, flare=16))
    reach = _prop(r, _pose(r, lift=0, pitch=-26, bend=-4, neck=(-8, 0, 0), head=(6, 0, 0), tail=(26, 0), feet={0: _foot(0, up=15, dz=-3), 1: _foot(1, up=15, dz=-3)},
                           poles={0: (0.3, -0.3, 1), 1: (-0.3, -0.3, 1)}, hands={0: "free", 1: "free"}, flare=10))
    land = _pose(r, lift=-2.5, pitch=3, bend=2, neck=(-2, 0, 0), head=(4, 0, 0), tail=(-2, 0), flare=4)
    return Move("gale", "NONE", 14, 20, 16, through=through, title="Gale of Petals", track=4.0, ability="Gale of Petals", rng=(3, 12),
                kinds=("ranged",), damage=0, knock=0, plant=(), stages={"windup": [(0.4, crouch), (1.0, up)], "active": act, "recover": [(0.45, reach), (0.7, land)]},
                lag=_lag())


def spray(r):
    """Thorn Spray (PERILOUS, the signature): it hunches, its back drawn up high and its head tucked under, its tail
    curled up over it, and its scales rise row by row from its neck to its tail, rattling; then it throws itself open,
    arching back, every scale flung up on end, and the thorns fly off it all round; it shudders and lets them down."""
    def hunch(f, rat):
        rows = {nm: 44 * _ease((f * 1.3 - j / 10) * 2.5) + rat * (1 if j % 2 else -1) - 30 * f for j, nm in enumerate(ROW_NAMES)}
        return _pose(r, lift=2.5 * f, pitch=-5 * f, bend=20 * f, neck=(-6 * f, 0, 0), head=(8 * f, 0, 0), tail=(80 * f, 0), flare=30 * f, rows=rows)
    wind = [(0.18, hunch(0.2, 0)), (0.32, hunch(0.38, 5)), (0.44, hunch(0.52, -5)), (0.56, hunch(0.66, 6)), (0.68, hunch(0.78, -6)), (0.8, hunch(0.88, 6)),
            (0.9, hunch(0.95, -6)), (1.0, hunch(1.0, 4))]
    burst = _pose(r, lift=1, pitch=-3, bend=-10, neck=(-16, 0, 0), head=(-20, 0, 0), tail=(40, 0), flare=62, tongue=0.6)
    shudder = lambda a: _pose(r, lift=0.5, pitch=-2, bend=-8, twist=4 * a, neck=(-12, 3 * a, 0), head=(-14, 4 * a, 0), tail=(34, 6 * a), flare=52,
                              rows={nm: 6 * a * (1 if j % 2 else -1) for j, nm in enumerate(ROW_NAMES)}, tongue=0.3)
    end = _pose(r, lift=0.5, bend=-6, neck=(-6, 0, 0), head=(-6, 0, 0), tail=(20, 0), flare=36)
    return Move("spray", "PERILOUS", 26, 10, 22, through=_mix(end, _pose(r), 0.3), title="Thorn Spray", signature=True, track=4.0, ability="Thorn Spray",
                rng=(0, 12), kinds=("ranged", "area"), damage=0, knock=0, plant=(),
                stages={"windup": wind, "active": [(0.25, burst), (0.5, shudder(1)), (0.75, shudder(-1)), (1.0, end)], "recover": _back(r, _mix(end, _pose(r), 0.3))},
                lag=_lag())


def lurkers(r):
    """Lurkers: it puts its snout to the floor and digs at it with its great claws, one and then the other, throwing
    the earth back under it, its tail up; and the vine lurkers come up out of the ground."""
    sniff = _pose(r, lift=-1, pitch=2, bend=2, neck=(-4, 0, 0), head=(2, 0, 0), tail=(14, 0), tongue=0.3)

    def dig(a):
        L_ = (a + 1) / 2  # 1: the left claw reaching, the right pulled back under it
        reach = lambda k: (SIDE[k] * 7, 8.5, -27.0)
        pull = lambda k: (SIDE[k] * 7.5, 7.0, -14.0)
        lerp = lambda p0, p1, f: tuple(x + (y - x) * f for x, y in zip(p0, p1))
        return _pose(r, lift=-1.5, pitch=2, bend=2, roll=3 * a, yaw=2 * a, neck=(-4, -3 * a, 0), head=(2, -6 * a, 0), tail=(18, 10 * a),
                     feet={0: lerp(pull(0), reach(0), L_), 1: lerp(pull(1), reach(1), 1 - L_)},
                     poles={0: (0.4, -0.2, 1), 1: (-0.4, -0.2, 1)}, hands={0: "free", 1: "free"}, claws=(0.55 + 0.3 * L_, 0.55 + 0.3 * (1 - L_)))
    act = [(0.12, dig(1)), (0.25, dig(-1)), (0.37, dig(1)), (0.5, dig(-1)), (0.62, dig(1)), (0.75, dig(-1)), (0.88, dig(1)), (1.0, dig(0))]
    return Move("lurkers", "NONE", 12, 24, 14, through=_mix(dig(0), _pose(r), 0.3), title="Lurkers", track=0.0, ability="Lurkers", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), stages={"windup": [(0.45, sniff), (1.0, dig(0))], "active": act,
                                                     "recover": _back(r, _mix(dig(0), _pose(r), 0.3))}, lag=_lag())


def wake(r):
    """Its intro, once it has unrolled: it lifts its snout and scents the air, its tongue flicking; it rises half up on
    its hind legs to look at you, and its scales lift all down its back with a rattle; it comes down onto its claws."""
    arms = {0: {"arm0": (-40.0, 0, -6), "fore0": (-50.0, 0, 0)}, 1: {"arm1": (-40.0, 0, 6), "fore1": (-50.0, 0, 0)}}
    scent = lambda a, tg=0.0: _pose(r, lift=1 + 0.8 * a, pitch=-4 - 2 * a, yaw=3 * a, roll=3 * a, side=5 * a, neck=(-18, 8 * a, 0), head=(-16, 14 * a, -4 * a),
                                    tail=(6 + 4 * a, -12 * a), tongue=tg, flare=3 + 3 * a,
                                    rows={nm: 5 * a * math.sin(j * 0.9) for j, nm in enumerate(ROW_NAMES)})
    up = lambda fl, a=0.0: _prop(r, _pose(r, lift=1, pitch=-34, bend=-4, neck=(-12, 4 * a, 0), head=(6, 8 * a, 0), tail=(32, -6 * a), limbs=arms,
                                          hands={0: "free", 1: "free"}, flare=fl, rows={nm: 6 * a * (1 if j % 2 else -1) for j, nm in enumerate(ROW_NAMES)}))
    down = _pose(r, lift=-2, pitch=1, neck=(-3, 0, 0), head=(-2, 0, 0), tail=(-2, 0), flare=4)
    return Move("wake", "NONE", 14, 30, 14, through=_mix(down, _pose(r), 0.4), track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, scent(-1)), (0.65, scent(1, 0.9)), (1.0, scent(-0.4))],
                        "active": [(0.12, scent(0.8, 0.8)), (0.35, up(6)), (0.45, up(26, 1)), (0.55, up(30, -1)), (0.65, up(28, 1)), (0.75, up(20, 0)),
                                   (1.0, down)],
                        "recover": _back(r, _mix(down, _pose(r), 0.4))}, lag=_lag())


# the ground (SPEC §35.10): the hands and claws, the feet, the tail's tip and the snout stay out of the floor
LEG_GROUND = [([f"hand{k}"] + [f"claw{k}_{j}" for j in range(3)], [(f"arm{k}", 0, 30), (f"fore{k}", 0, 40)]) for k in FRONT] + \
             [([f"foot{k}"] + [f"toe{k}_{j}" for j in range(3)], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in BACK]
TAIL_GROUND = [(["tail4", "tailtip", "tipscale"], [("t3", 0, 40, 1), ("t2", 0, 30, 1)]), (["snout", "nose"], [("head", 0, 30, -1), ("neck", 0, 20, -1)])]
STANDS = {"claw": (0, 2, 3), "claw_b": (1, 2, 3), "maul": (2, 3), "tail": (0, 1, 2, 3), "charge": (), "gale": (2, 3), "spray": (0, 1, 2, 3),
          "lurkers": (2, 3), "wake": (2, 3)}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = [claw(r, 1, "claw"), claw(r, 0, "claw_b"), maul(r), tail_lash(r), charge(r), gale(r), spray(r), lurkers(r), wake(r)]
    for m in mv:
        stage_clips(r, m)
        if getattr(m, "own", None):
            _own(r, m, m.own)
        if getattr(m, "spins", None):
            _spin_track(r, m, m.spins)
        if STANDS[m.id]:
            _plant(r, core.clip_name(m), STANDS[m.id])
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, TAIL_GROUND, clips=everyday)
    return mv


# Its sounds (SPEC §35.8): the armadillo's own voice and its roll, leaves and thorny bushes, the ravager's weight under
# it; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.armadillo.ambient", 0.8, 0.7), ("block.azalea_leaves.step", 0.8, 0.8)],
    "tell_heavy": [("entity.armadillo.roll", 0.9, 0.7), ("entity.ravager.ambient", 0.6, 0.6)],
    "tell_perilous": [("block.sweet_berry_bush.break", 1.4, 0.5), ("entity.armadillo.scute_drop", 1.0, 0.6), ("entity.ravager.roar", 0.8, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.8, 0.8), ("block.sweet_berry_bush.place", 1.0, 0.9)],
    "swing_heavy": [("entity.player.attack.knockback", 0.8, 0.6), ("entity.ravager.attack", 1.0, 0.7)],
    "cast": [("block.pink_petals.break", 1.4, 0.7), ("entity.breeze.wind_burst", 0.8, 0.8)],
    "impact": [("block.rooted_dirt.break", 1.4, 0.6), ("block.azalea_leaves.break", 1.2, 0.6), ("entity.ravager.step", 1.0, 0.6)],
    "roar": [("entity.armadillo.hurt", 1.4, 0.5), ("entity.ravager.roar", 1.0, 0.5)],
    "step": [("entity.armadillo.step", 0.6, 0.7), ("block.azalea_leaves.step", 0.4, 0.8)],
}
MOVE_SOUNDS = {"gale": {"swing": "cast"}, "spray": {"swing": "roar"}, "lurkers": {"swing": "cast", "impact": ""}, "wake": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # it waits for you rolled up in its ball, and unrolls
    "phases": [
        {"pool": [
            {"move": "claw", "weight": 3, "when": "near", "then": [["claw_b"], ["claw_b", "maul"]]},
            {"move": "maul", "weight": 2, "when": "near"},
            {"move": "charge", "weight": 3, "when": "far", "then": [["claw"]]},
            {"move": "gale", "weight": 2, "when": "far"},
            {"move": "tail", "weight": 3, "when": "behind"},
        ]},
        # the vines tighten with the second phase, as in DeepBestiary: the lurkers, and the thorns
        {"transition": "lurkers", "pool": [
            {"move": "claw", "weight": 3, "when": "near", "then": [["claw_b"], ["claw_b", "maul"]]},
            {"move": "maul", "weight": 2, "when": "near"},
            {"move": "charge", "weight": 3, "when": "far", "then": [["claw", "claw_b"]]},
            {"move": "gale", "weight": 1, "when": "far"},
            {"move": "spray", "weight": 2},
            {"move": "lurkers", "weight": 1},
            {"move": "tail", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    _set_ball(r)
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.0, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}
