"""The Sleeper (SPEC §35.11, docs/bosses/ROLLOUT.md): the Nightmare gate's Lord, a Brute. The Four-Poster, picked
2026-10-29: "You've been here before. You haven't."

A great old four-poster bed that walks on its posts. Below the frame each post is a turned leg with a carved knee and a
claw gripping a ball for a foot; above it the posts carry a canopy hung with torn velvet curtains, bunched at the
posts, and a dreamcatcher hangs from its front. In the bed, propped on his pillows under a patchwork quilt with the
sheet turned down over it, the Sleeper: a grey giant in a striped nightcap, asleep, one hand clutching the sheet to his
chin and the other arm, far too long, hanging out over the side so that its fist drags on the floor. Under the bed,
behind the torn dust ruffle, it is dark, and there are eyes.

Moves (SPEC §35.11): the bed rears a front post and stamps with it, then the other; the Restless Fist (the Sleeper
heaves his arm up over the bed and brings the fist down beside it); whoever is behind gets kicked by both back posts;
Runaway Bed (it lowers its foot end and charges); Heavy Eyelids (he yawns, the bed settles, the curtains draw shut);
Night Terrors (he tosses and turns, the bed rocking with him, and things shake loose from under it); Dreamstep (he
sinks into his pillows and the bed with him, and it is somewhere else); Sleepwalkers (he sits half up, head lolling,
and reaches out ahead of him); and Rude Awakening (the signature: the bed rears up on its back posts, the Sleeper sits
bolt upright with his eyes open and screams, and the bed crashes down). It waits lying low on its folded legs like any
bed, and gets up when you come in.
"""
import math

from kit import core, ik, parts
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "the_sleeper"
SCALE = 2.2  # the hitbox on the def's ZOMBIE model at size 1.0 x 1.8: 2.2 x 4.0 blocks; the bed is modelled at true size

# The bed: 38 px wide and 54 long, its frame's middle 23 px up, the canopy's rails at 61-64 and the finials to 71 (4.4
# blocks). Value story: the Sleeper's face and the sheet palest, then the quilt's faded patches and the gilt of the
# carving, then the velvet and the dark wood; the light is the dreamcatcher's, the eyes under the bed, and his eyes
# when they open.
FRAME = 23.0  # the frame's middle, pixels up
BOW = 4.0  # the bed stands with its head end a little raised, so the Sleeper's face is turned toward you
LEGS = ((1, -1), (-1, -1), (1, 1), (-1, 1))  # front left, front right, back left, back right (x, z signs)
HIP = (17.0, 25.0, 22.0)  # each hip: x and z out from the middle, height
THIGH, SHIN = 11, 10
FOOT_UP = 5.0  # the foot's pivot above the floor: the carved ball under it
REST_FOOT = [(sx * 19.0, FOOT_UP + 0.3, sz * 29.0) for sx, sz in LEGS]
POLE = [(sx * 0.4, 0.0, sz * 0.9) for sx, sz in LEGS]  # the knees toward the ends: the front ones forward, the back ones back
TORSO = (0.0, 34.0, -2.0)  # his hips under the quilt: x, height, z
LIE = -62.0  # the torso propped on its pillows (0 would be sitting bolt upright)
PROP = 22.0  # the head bowed forward on the pillows
FIST = (-30.0, 7.5, 6.0)  # the wrist when the fist lies on the floor, in the bed's own frame: x, height, z
ELBOW = (-1.0, -0.6, 0.4)  # the elbow out and up over the bed's side
FRONT_DRAW, SIDE_DRAW = 3.0, 5.6  # how far each curtain's bunch stretches when it is drawn shut
OPEN_LIDS = 1.6  # pixels the lids sink into his head when his eyes open


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed by height: x, how far up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    """A cube centred on (cx, cz), from y0 up to y0 + h over its bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


def _up(h):
    """A height above the floor as a height over the frame's pivot."""
    return h - FRAME


# the dust ruffle: per side, where it hangs, along which axis, and its torn strips' widths and lengths
SKIRTS = {"f": ((0, -26.5), "x", (7, 6, 7, 6), (8, 6, 9, 7)), "b": ((0, 26.5), "x", (6, 7, 6, 7), (7, 9, 6, 8)),
          "l": ((18.5, 0), "z", (9, 8, 8, 8, 9), (7, 9, 6, 8, 7)), "r": ((-18.5, 0), "z", (9, 8, 8, 8, 9), (8, 6, 9, 7, 6))}
CURTAINS = ("cf_l", "cf_r", "cs_l", "cs_r")


def body():
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    fr = _bone(base, "frame", 0, FRAME, 0, (BOW, 0, 0))
    # the frame, the dark under it, and the dust ruffle hanging round it in torn strips
    _box(fr, "frame", 0, _up(20), 0, 38, 6, 54)
    ud = _bone(fr, "under", 0, _up(20), 0)
    _box(ud, "under", 0, -16, 0, 30, 16, 44)
    for s, ((x, z), along, ws, ls) in SKIRTS.items():
        sk = _bone(fr, "skirt_" + s, x, _up(20), z)
        a = -sum(ws) / 2
        for k, (w, L) in enumerate(zip(ws, ls)):
            if along == "x":
                sk.cube(f"sk{s}{k}", a, 0, -0.5, w, L, 1)
            else:
                sk.cube(f"sk{s}{k}", -0.5, 0, a, 1, L, w)
            a += w
    # the mattress, the quilt over it hanging down its sides, the shape of his legs under it with his toes up at the
    # foot, and the pillows heaped against the headboard
    _box(fr, "mattress", 0, _up(26), 0, 34, 6, 50)
    _box(fr, "quilt", 0, _up(32), -5, 38, 2, 36)
    _box(fr, "drape_l", 19.5, _up(25), -5, 1, 9, 36)
    _box(fr, "drape_r", -19.5, _up(25), -5, 1, 9, 36)
    _box(fr, "mound", 0, _up(34), -12, 22, 3, 20)
    _box(fr, "toes_l", 5.5, _up(37), -19.5, 7, 3, 5)
    _box(fr, "toes_r", -5.5, _up(37), -19.5, 7, 3, 5)
    _box(fr, "pillow0", 0, _up(32), 18, 30, 5, 12)
    pl = _bone(fr, "pillow1", 0, _up(37), 20, (-20, 0, 0))
    _box(pl, "pillow1", 0, 0, 0, 26, 5, 9)
    # the four posts, turned: a corner block where the leg meets the frame, a column, a bulb, a column, a capital; a
    # finial over the canopy
    for k, (sx, sz) in enumerate(LEGS):
        x, z = sx * HIP[0], sz * HIP[1]
        _box(fr, f"newel{k}", x, _up(19), z, 6, 10, 6)
        _box(fr, f"col{k}", x, _up(29), z, 4, 11, 4)
        _box(fr, f"bulb{k}", x, _up(40), z, 6, 5, 6)
        _box(fr, f"colb{k}", x, _up(45), z, 4, 13, 4)
        _box(fr, f"capital{k}", x, _up(58), z, 6, 3, 6)
        _box(fr, f"finial{k}", x, _up(64), z, 4, 5, 4)
        _box(fr, f"knob{k}", x, _up(69), z, 2, 2, 2)
    # the headboard, carved with a moon, and the low footboard, carved with a shut eye
    _box(fr, "headboard", 0, _up(26), 25, 30, 26, 2)
    _box(fr, "crest", 0, _up(52), 25, 20, 5, 2)
    _box(fr, "crest1", 0, _up(57), 25, 10, 3, 2)
    _box(fr, "footboard", 0, _up(26), -25, 30, 9, 2)
    _box(fr, "footrail", 0, _up(35), -25, 32, 2, 3)
    # the canopy: rails on the posts, a valance hung round them, the cloth over the top torn through in the middle
    _box(fr, "rail_f", 0, _up(61), -25.5, 38, 3, 4)
    _box(fr, "rail_b", 0, _up(61), 25.5, 38, 3, 4)
    _box(fr, "rail_l", 17, _up(61), 0, 3, 3, 47)
    _box(fr, "rail_r", -17, _up(61), 0, 3, 3, 47)
    _box(fr, "val_f", 0, _up(57), -28, 40, 7, 1)
    _box(fr, "val_b", 0, _up(57), 28, 40, 7, 1)
    _box(fr, "val_l", 19, _up(57), 0, 1, 7, 55)
    _box(fr, "val_r", -19, _up(57), 0, 1, 7, 55)
    _box(fr, "cloth_f", 0, _up(63), -14, 31, 1, 19)
    _box(fr, "cloth_b", 0, _up(63), 13, 31, 1, 21)
    tt = _bone(fr, "tatter", 2, _up(63), 2.5)
    tt.cube("tatter", -4, 0, -0.5, 8, 7, 1)
    # the curtains, bunched: the front pair at the front posts (drawn, they meet in the middle), the side pair at the
    # back posts (drawn, they run the bed's length); each torn off at a different length, a hem that swings below
    for s, nm in ((1, "cf_l"), (-1, "cf_r")):
        cb = _bone(fr, nm, s * 15.5, _up(61), -27.4)
        hm = cb.bone(nm + "h", (0, 16, 0))
        a = 0
        for k, (w, L) in enumerate(zip((2, 2, 1), (9, 12, 7) if s > 0 else (11, 8, 10))):
            x0 = -(a + w) if s > 0 else a
            cb.cube(f"{nm}{k}", x0, 0, -0.5, w, 16, 1)
            hm.cube(f"{nm}h{k}", x0, 0, -0.5, w, L, 1)
            a += w
    for s, nm in ((1, "cs_l"), (-1, "cs_r")):
        cb = _bone(fr, nm, s * 17.0, _up(61), 22.5)
        hm = cb.bone(nm + "h", (0, 16, 0))
        for k, L in enumerate((9, 7, 10, 8) if s > 0 else (4, 2, 5, 3)):  # his right side's torn short over his arm
            cb.cube(f"{nm}{k}", -0.5, 0, -2 * (k + 1), 1, 16, 2)
            hm.cube(f"{nm}h{k}", -0.5, 0, -2 * (k + 1), 1, L, 2)
    # the dreamcatcher on its cord from the valance: a hoop, its web, three feathers and a bead
    dc = _bone(fr, "dream", 11.5, _up(57), -28.9)
    dc.cube("cord", -0.5, 0, -0.5, 1, 3, 1)
    hp = dc.bone("hoop", (0, 3, 0))
    hp.cube("hoop_t", -2, 0, -0.5, 4, 1, 1)
    hp.cube("hoop_b", -2, 8, -0.5, 4, 1, 1)
    hp.cube("hoop_l", 3, 2, -0.5, 1, 5, 1)
    hp.cube("hoop_r", -4, 2, -0.5, 1, 5, 1)
    for nm, x, y, a in (("hc0", 2.6, 1.1, -45), ("hc1", -2.6, 1.1, 45), ("hc2", 2.6, 7.9, 45), ("hc3", -2.6, 7.9, -45)):
        hp.bone(nm, (x, y, 0), (0, 0, a)).cube(nm, -0.5, -1, -0.5, 1, 2, 1)
    hp.cube("web0", -3, 2, 0, 6, 5, 1)
    hp.cube("web1", -2, 1, 0, 4, 7, 1)
    pm = hp.bone("plumes", (0, 9, 0))
    pm.cube("bead", -0.5, 0, -1, 1, 1, 1)
    pm.cube("plume0", -3, 0, -0.5, 1, 5, 1)
    pm.cube("plume1", -0.5, 1, -0.5, 1, 6, 1)
    pm.cube("plume2", 2, 0, -0.5, 1, 4, 1)
    # the Sleeper, propped on his pillows: his body under the quilt with the sheet turned down over it at his chest
    # and his left hand clutching it, his head bowed on the pillows in its nightcap
    tp = _bone(fr, "torso", TORSO[0], _up(TORSO[1]), TORSO[2], (LIE, 0, 0))
    tp.cube("torso", -12, -17, -5, 24, 17, 10)
    tp.cube("sheet", -12.5, -17, -5.5, 25, 3, 11)
    tp.cube("hand_l", 2, -13.5, -8, 7, 4, 3)
    nk = tp.bone("neck", (0, -17, 0))
    nk.cube("neck", -4, -3, -4, 8, 3, 8)
    hd = nk.bone("head", (0, -3, 0), (PROP, 0, 0))
    hd.cube("head", -6, -12, -6, 12, 10, 12)
    hd.cube("brow", -5.5, -9, -7, 11, 2, 1)
    hd.cube("nose", -1, -7, -8, 2, 4, 2)
    hd.cube("nosetip", -1, -4, -9, 2, 2, 1)
    hd.cube("ear_l", 6, -8, -1, 1, 5, 3)
    hd.cube("ear_r", -7, -8, -1, 1, 5, 3)
    ld = hd.bone("lids", (0, -7, -6.5))
    ld.cube("lid_l", 1, 0, -0.5, 4, 2, 1)
    ld.cube("lid_r", -5, 0, -0.5, 4, 2, 1)
    jw = hd.bone("jaw", (0, -2, 1))
    jw.cube("jaw", -5, 0, -7, 10, 3, 10)
    cp = hd.bone("cap", (0, -12, 0))
    cp.cube("cap0", -6.5, 0, -6.5, 13, 4, 13)
    c1 = cp.bone("cap1", (0, 0, 1), (-8, 0, 38))
    c1.cube("cap1", -5.5, -6, -5.5, 11, 6, 11)
    c2 = c1.bone("cap2", (0, -6, 0), (4, 0, 34))
    c2.cube("cap2", -4, -6, -4, 8, 6, 8)
    c3 = c2.bone("cap3", (0, -6, 0), (10, 0, 28))
    c3.cube("cap3", -2.5, -6, -2.5, 5, 6, 5)
    c3.bone("pom", (0, -6, 0)).cube("pom", -2.5, -5, -2.5, 5, 5, 5)
    # his right arm, far too long, out of its torn sleeve and over the side, the fist on the floor
    ar = tp.bone("arm_r", (-12.5, -14, 0.5))
    ar.cube("sleeve", -3.5, -1, -3.5, 7, 12, 7)
    ar.cube("arm_r", -2.5, 0, -2.5, 5, 21, 5)
    fa = ar.bone("forearm_r", (0, 21, 0))
    fa.cube("elbow_r", -2.5, -1.5, -2.5, 5, 3, 5)
    fa.cube("forearm_r", -2, 0, -2, 4, 20, 4)
    hn = fa.bone("hand_r", (0, 20, 0))
    hn.cube("fist", -3.5, 0, -3.5, 7, 7, 7)
    hn.cube("thumb", 3.5, 1, -2.5, 1, 4, 3)
    # the legs: below the frame each post is a leg, a turned thigh, a carved knee, a shin, a claw gripping a ball
    for k, (sx, sz) in enumerate(LEGS):
        hp_ = _bone(fr, f"hip{k}", sx * HIP[0], _up(HIP[2]), sz * HIP[1])
        th = hp_.bone(f"thigh{k}", (0, 0, 0))
        th.cube(f"thigh{k}", -2, 0, -2, 4, THIGH, 4)
        th.cube(f"tbulb{k}", -3, 3, -3, 6, 4, 6)
        sh = th.bone(f"shin{k}", (0, THIGH, 0))
        sh.cube(f"knee{k}", -3, -2.5, -3, 6, 5, 6)
        sh.cube(f"shin{k}", -1.5, 2, -1.5, 3, SHIN - 2, 3)
        ft = sh.bone(f"foot{k}", (0, SHIN, 0))
        ft.cube(f"claw{k}", -3, -1, -3, 6, 3, 6)
        ft.cube(f"ball{k}", -2.5, 1, -2.5, 5, 4, 5)
    for k in range(4):
        ik.rest_limb(r, f"thigh{k}", f"shin{k}", f"foot{k}", REST_FOOT[k], POLE[k])
    ik.rest_limb(r, "arm_r", "forearm_r", "hand_r", FIST, ELBOW)
    f0 = core.frames(r, {})
    for b, x in [(f"foot{k}", (1, 0, 0)) for k in range(4)] + [("hand_r", (1, 0, 0))]:
        d = ik.orient(r, f0, b, (0, 1, 0), x)
        bn = r.by_name[b]
        bn.rot = tuple(bn.rot[i] + d[i] for i in range(3))
    r.look = [("head", 0.3, 0.25)]
    r.shadow = 2.6
    r.size = 1.0
    # secondary parts (SPEC §35.10): the curtains and their hems, the dust ruffle, the dreamcatcher, the canopy's
    # tatter and the nightcap all swing
    r.springs = ([(c, 0.6, 1.2, 0.07) for c in CURTAINS] + [(c + "h", 0.5, 1.8, 0.16) for c in CURTAINS]
                 + [("skirt_" + s, 0.6, 1.4, 0.1) for s in SKIRTS]
                 + [("dream", 0.5, 1.5, 0.22), ("hoop", 0.5, 1.8, 0.16), ("plumes", 0.5, 2.2, 0.25), ("tatter", 0.5, 2.0, 0.2),
                    ("cap1", 0.6, 1.6, 0.12), ("cap2", 0.55, 2.0, 0.16), ("cap3", 0.5, 2.3, 0.2), ("pom", 0.5, 2.5, 0.22)])
    r.guard = ["hand_r"] + [f"foot{k}" for k in range(4)]  # bones, not cubes: core.lowest walks a bone's cubes
    return r


# ---------------------------------------------------------------------------------------------------------------------
# Paint

WOOD = Ramp("#0e090c", "#160e13", "#20151b", "#2b1c24", "#37252e", "#442e38", "#523844", "#624450", "#74525c", "#88626a")
GILT = Ramp("#2e2010", "#4a3418", "#664a22", "#86622e", "#a67e3c", "#c49c52", "#dcbc72", "#eed8a0")
ROSE = Ramp("#2c141c", "#42202c", "#5a2c3c", "#743a4c", "#8e4c5e", "#a86272", "#c07c8a", "#d498a2")
PLUM = Ramp("#1a1022", "#281a32", "#362644", "#463456", "#58466a", "#6c5a7e", "#806e92")
DUSK = Ramp("#10182a", "#18243a", "#22304c", "#2e3e60", "#3c4e74", "#4e6088", "#62749a")
MOTH = Ramp("#221e1c", "#302a28", "#403936", "#524a46", "#665d58", "#7c726c", "#928880")
OCHRE = Ramp("#2a1e0c", "#3e2c12", "#56401a", "#6e5424", "#886a30", "#a2823e")
LINEN = Ramp("#40362e", "#57493e", "#6e5f50", "#877563", "#a08c76", "#b8a48c", "#ccbba2", "#ddcfb9", "#ece2d0")
SKIN = Ramp("#14161e", "#1e212c", "#2a2e3c", "#383d4e", "#484e60", "#5a6174", "#6e7688", "#848c9e", "#9ca3b4", "#b6bccb")
VELVET = Ramp("#0c0408", "#16080f", "#220c18", "#301222", "#40182e", "#52203a", "#662a48", "#7c3658")
DARK = Ramp("#020104", "#050308", "#09060e", "#0e0a14")
PINK = ("#ff5aa8", "#ff8cc4", "#ffbadc", "#ffe4f2")
PATCHES = ((ROSE, 4.4), (PLUM, 3.8), (DUSK, 3.6), (MOTH, 4.0), (OCHRE, 3.4), (ROSE, 3.6))


def _quilt(P_, c, cell=6, holes=1):
    """Patchwork: squares of faded rose, plum, dusk blue, moth grey and ochre, darker seams stitched across, and a
    moth hole or two with the stuffing showing."""
    for fname in ("top", "bottom") + SIDES:
        f = P_.face(c, fname)
        if f.w <= 0 or f.h <= 0:
            continue
        for i, j in f.each():
            pi, pj = i // cell, j // cell
            ramp, base = PATCHES[R._h(c, fname, pi, pj) % len(PATCHES)]
            v = base + R.FACE_LIGHT[fname] * 0.9 + (R._h(c, fname, i, j) % 3 - 1) * 0.25
            if fname in SIDES:
                v += 0.5 * (0.5 - j / max(1, f.h - 1))
            if i % cell == 0 or j % cell == 0:
                v -= 1.1
                if (i + j) % 2 == 0 and (i % cell == 0) != (j % cell == 0):
                    f.set(i, j, LINEN.at(3.5 + R.FACE_LIGHT[fname]))  # the stitches across a seam
                    continue
            f.set(i, j, ramp.at(v))
        for n in range(holes if f.w * f.h > 40 else 0):
            h = R._h(c, fname, "hole", n)
            hi, hj = 1 + h % max(1, f.w - 2), 1 + (h // 7) % max(1, f.h - 2)
            f.set(hi, hj, MOTH.at(0))
            f.set(hi + 1, hj, LINEN.at(5))
            f.set(hi, hj + 1, LINEN.at(4))


def _wood(P_, c, base=4.0, gilt_rows=(), grain=True):
    """Old dark wood with its grain running down it; gilt where the carver ran a ring round it."""
    def g(f, i, j):
        if f.name in SIDES:
            if j in gilt_rows:
                return GILT.at(4.6 if (i + j) % 2 else 3.8)
            h = R._h(c, f.name, i)
            return (-0.7 if h % 5 == 0 else 0.4 if h % 5 == 1 else 0) if grain else 0
        return 0
    P_.paint(c, WOOD, base, g, grad=0.8, rim=0.7, ao=0.6, noise=0.35, nscale=3)


def paint(r, P_):
    of = lambda *ps: parts.cubes_of(r, *ps)
    # the posts and legs: turned dark wood, gilt rings on the bulbs and capitals, the knees carved, the claws gilt-tipped
    for c in of("newel"):
        _wood(P_, c, 4.2, gilt_rows=(0, 9))
        for fname in SIDES:  # a sunk panel in each face with a gilt star in it
            f = P_.face(c, fname)
            for i, j in f.each():
                if 1 <= i <= f.w - 2 and 2 <= j <= f.h - 3:
                    f.set(i, j, WOOD.at(2.6 + (0.5 if j == 2 else 0)))
            f.set(f.w // 2, f.h // 2, GILT.at(6))
            f.set(f.w // 2 - 1, f.h // 2, GILT.at(4))
            f.set(f.w // 2, f.h // 2 - 1, GILT.at(4))
    for c in of("col", "thigh", "shin"):
        _wood(P_, c, 4.0)
    for c in of("bulb", "tbulb"):
        _wood(P_, c, 4.8, gilt_rows=(2,))
    for c in of("capital"):
        _wood(P_, c, 4.6, gilt_rows=(0,))
    for c in of("finial"):
        _wood(P_, c, 4.6, gilt_rows=(4,))
    for c in of("knob"):
        P_.paint(c, GILT, 4.8, None, rim=1.0)
    for c in of("knee"):  # carved leaves round the knee, the gilt worn into their edges
        P_.paint(c, WOOD, 4.6, lambda f, i, j: (GILT.at(4.0) if f.name in SIDES and j in (0, f.h - 1) and i % 2 == 0 else 0.8 if f.name in SIDES and (i + j) % 4 == 0 else 0),
                 grad=1.0, rim=0.8, ao=0.8)
    for c in of("claw"):  # the talons gilt down each face, over the ball
        P_.paint(c, WOOD, 4.2, lambda f, i, j: (GILT.at(5.0 - j) if f.name in SIDES and i in (1, 4) and j >= 1 else 0.6 if f.name in SIDES and i % 2 else 0), grad=0.8, rim=0.8)
    for c in of("ball"):
        P_.paint(c, WOOD, 5.0, lambda f, i, j: (1.4 if f.name in SIDES and j == 0 and 1 <= i <= 3 else 0), grad=1.4, rim=0.6, ao=1.0)
    # the frame, gilt along its top edge, a row of carved stars down its sides
    _wood(P_, "frame", 3.8, gilt_rows=(0,))
    for fname in SIDES:
        f = P_.face("frame", fname)
        for i in range(3, f.w - 2, 7):
            f.set(i, 3, GILT.at(5.5))
            for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                f.set(i + di, 3 + dj, GILT.at(3.6))
    for c in ("rail_f", "rail_b", "rail_l", "rail_r", "footrail", "crest1"):
        _wood(P_, c, 4.2, gilt_rows=(0,))
    # the headboard: a carved crescent moon among stars, gilt, faintly lit; a beaded gilt border
    for c in ("headboard", "crest", "footboard"):
        _wood(P_, c, 3.6)
    for c, fname in (("headboard", "north"), ("headboard", "south"), ("footboard", "north"), ("footboard", "south")):
        f = P_.face(c, fname)
        for i, j in f.each():
            if i in (1, f.w - 2) or j in (1, f.h - 2):
                if 1 <= i <= f.w - 2 and 1 <= j <= f.h - 2:
                    f.set(i, j, GILT.at(4.5 if (i + j) % 2 else 3.2))
    f, g = P_.face("headboard", "north"), P_.face("headboard", "north", glow=True)
    cx, cy = f.w / 2 - 0.5, 10.5
    for i, j in f.each():
        d0 = math.hypot(i - cx, j - cy)
        d1 = math.hypot(i - cx - 3.2, j - cy + 1.8)
        if d0 < 7.2 and d1 > 6.0:
            f.set(i, j, GILT.at(5.6 + (0.8 if d0 > 6.2 else 0)))
            g.set(i, j, hexc("#ffd6a8", 55))
    for i, j in ((5, 4), (24, 6), (22, 17), (7, 18), (12, 22), (19, 3), (4, 12), (26, 13)):
        f.set(i, j, GILT.at(7))
        g.set(i, j, hexc("#ffe8c8", 90))
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            f.set(i + di, j + dj, GILT.at(4.2))
    f = P_.face("crest", "north")
    for i in range(2, f.w - 2):
        f.set(i, 1, GILT.at(4.4))
    # the footboard: a shut eye carved across it, its lashes hanging
    f = P_.face("footboard", "north")
    for i in range(6, f.w - 6):
        t = (i - (f.w - 1) / 2) / ((f.w - 12) / 2)
        j = int(round(3.0 + 2.2 * t * t))
        f.set(i, j, GILT.at(6.2))
        if i % 3 == 0 and 7 < i < f.w - 8:
            f.set(i, j + 1, GILT.at(4.0))
            f.set(i - (1 if i < f.w / 2 else -1), j + 2, GILT.at(3.4))
    # the canopy: velvet valance with a gilt fringe and its scallops torn; the cloth faded, moth-eaten
    for c in of("val_"):
        P_.paint(c, VELVET, 4.2, lambda f, i, j: (GILT.at(4.6 if i % 2 else 3.0) if f.name in SIDES and j == f.h - 1 and R._h(c, i) % 4 else
                                                  (0.6 if j == 1 else -0.8 if (i // 3) % 2 and j == f.h - 2 else 0)), grad=0.8, rim=0.4)
    for c in ("cloth_f", "cloth_b", "tatter"):
        P_.paint(c, PLUM, 2.6, lambda f, i, j: (MOTH.at(5) if R._h(c, f.name, i, j) % 23 == 0 else 0), grad=0.6, rim=0.2, noise=0.6)
    # the curtains: deep velvet in folds (a fold every pixel of the bunch; drawn, every few), moth holes, torn hems
    for c in of("cf_", "cs_"):
        def fold(f, i, j, c=c):
            if f.name not in SIDES:
                return -0.5
            if R._h(c, f.name, i, j) % 31 == 0:
                return VELVET.at(0)
            lo = c[4] == "h"
            return (0.7 if (i % 2 == 0) else -0.6) + (-0.9 if lo and j >= f.h - 2 else 0)
        P_.paint(c, VELVET, 4.4, fold, grad=1.0, rim=0.5, ao=0.2, noise=0.3)
    # the dust ruffle: faded linen in pleats, frayed at the foot; under it the dark, and eyes in the dark
    for c in of("sk"):
        P_.paint(c, LINEN, 2.8, lambda f, i, j: (-1.0 if i % 2 else 0.3) + (-1.4 if j >= f.h - 1 and R._h(c, i) % 2 else 0), grad=1.0, rim=0.4)
    P_.paint("under", DARK, 1.0, None, grad=0, rim=0, ao=0)
    for fname, eyes in (("north", ((6, 5), (19, 7), (25, 4))), ("south", ((8, 6), (20, 4))), ("east", ((10, 5), (30, 7))),
                        ("west", ((14, 6), (33, 4)))):
        g = P_.face("under", fname, glow=True)
        f = P_.face("under", fname)
        for i, j in eyes:
            for di in (0, 3):
                f.set(i + di, j, hexc("#3a2030"))
                g.set(i + di, j, hexc(PINK[3] if di == 0 else PINK[2], 255))
    # the bedding: ticking on the mattress, linen pillows with a dent where his head lies, the patchwork quilt
    P_.paint("mattress", LINEN, 3.0, lambda f, i, j: (-1.6 if f.name in ("top",) + SIDES and i % 4 == 1 else 0) if f.name != "bottom" else -1,
             grad=0.6, rim=0.3)
    for c in ("pillow0", "pillow1"):
        P_.paint(c, LINEN, 5.2, lambda f, i, j: (1.2 if (i in (0, f.w - 1) or j in (0, f.h - 1)) and (i + j) % 2 else 0), grad=0.8, rim=0.6, ao=1.0)
    f = P_.face("pillow1", "top")
    for i, j in f.each():
        d = math.hypot((i - f.w / 2) / 6, (j - f.h / 2) / 3)
        if d < 1:
            f.set(i, j, LINEN.at(3.6 + 1.4 * d))
    for c in ("quilt", "drape_l", "drape_r", "mound", "toes_l", "toes_r", "torso"):
        _quilt(P_, c)
    for fname in SIDES:  # the quilt's hem along the drapes, torn
        for c in ("drape_l", "drape_r"):
            f = P_.face(c, fname)
            for i in range(f.w):
                if R._h(c, fname, i) % 3 == 0:
                    f.set(i, f.h - 1, MOTH.at(0))
    # the sheet turned down over it, the nightshirt's collar where the sheet ends
    P_.paint("sheet", LINEN, 6.4, lambda f, i, j: (-1.2 if f.name in SIDES and j == 1 and i % 2 else 0), grad=0.6, rim=0.8, ao=0.8)
    f = P_.face("torso", "top")
    for i, j in f.each():
        f.set(i, j, (DUSK.at(4.2) if i % 3 == 0 else LINEN.at(5.4)) if abs(i - f.w / 2) > 4 else SKIN.at(4.4))
    # the Sleeper's skin: grey, his face long and gaunt, the sockets deep and dark round his lids, bags under them,
    # a big crooked nose, grey brows grown wild, big ears, his mouth hanging open
    for c in ("head", "neck", "nose", "nosetip", "brow", "jaw", "hand_l", "thumb", "ear_l", "ear_r"):
        P_.paint(c, SKIN, 5.8 if c not in ("neck",) else 4.4, None, grad=0.8, rim=0.6, ao=0.8, noise=0.4, nscale=3)
    f = P_.face("head", "north")
    for i, j in f.each():
        if j == 4 and (1 <= i <= 4 or 7 <= i <= 10):
            f.set(i, j, SKIN.at(3.0))
        if j in (5, 6) and i in (0, 11):
            f.set(i, j, SKIN.at(1.8))  # the sockets round the lids
        if j == 7 and (1 <= i <= 4 or 7 <= i <= 10):
            f.set(i, j, SKIN.at(1.6 if i in (2, 3, 8, 9) else 2.6))  # the bags under them
        if j == 8 and i in (2, 3, 8, 9):
            f.set(i, j, SKIN.at(3.8))
        if j in (7, 8) and i in (0, 11):
            f.set(i, j, SKIN.at(3.4))  # hollow cheeks
        if j == 9:
            f.set(i, j, SKIN.at(0.0 if 3 <= i <= 8 else 2.0 if i in (2, 9) else 4.8))  # his open mouth
    for i, j in ((0, 4), (11, 4), (0, 5), (11, 5)):
        f.set(i, j, SKIN.at(3.6))  # crow's feet
    for fname in ("east", "west"):  # his thin grey hair, long over his ears, under the cap
        f = P_.face("head", fname)
        for i, j in f.each():
            if j <= 6 and R._h(fname, i, j) % 3:
                f.set(i, j, LINEN.at(4.4 + R._h(fname, i) % 3))
    for c in ("ear_l", "ear_r"):
        P_.paint(c, SKIN, 5.4, lambda f, i, j: (-2.0 if f.name in ("east", "west") and 1 <= i <= 1 and 1 <= j <= 3 else 0), grad=0.8, rim=0.8)
    f = P_.face("head", "bottom")
    for i, j in f.each():
        if j < 6:
            f.set(i, j, SKIN.at(0.6))  # the roof of his mouth
    f = P_.face("jaw", "top")
    for i, j in f.each():
        if j >= f.h - 7 and 1 <= i <= f.w - 2:
            f.set(i, j, SKIN.at(0.6) if i not in (2, 7) else LINEN.at(4.6))  # inside his mouth, two long yellowed teeth
    f = P_.face("jaw", "north")
    for i, j in f.each():
        f.set(i, j, SKIN.at(2.8 if j == 0 and 2 <= i <= 7 else 4.6 + (0.7 if R._h("stub", i, j) % 3 == 0 else 0) - (0.6 if j == 2 else 0)))
    for fname in ("north", "top"):  # the wild grey brows
        f = P_.face("brow", fname)
        for i, j in f.each():
            if i not in (5,):
                f.set(i, j, LINEN.at(6.4 + R._h("brow", fname, i, j) % 3 - (1.6 if j == f.h - 1 and fname == "north" else 0)))
    P_.paint("nose", SKIN, 6.2, lambda f, i, j: (1.4 if f.name == "top" else -2.4 if f.name == "bottom" else 0), grad=1.4, rim=1.0, ao=0.6)
    P_.paint("nosetip", SKIN, 5.6, lambda f, i, j: (SKIN.at(1.0) if f.name == "bottom" else 0), grad=0.6, rim=0.6, ao=0.6)
    for c in ("lid_l", "lid_r"):  # his lids, heavy, the lashes dark along their edge
        P_.paint(c, SKIN, 6.6, lambda f, i, j: (-6.0 if f.name == "north" and j == 1 else 0.6 if j == 0 else 0), grad=0, rim=0.2, ao=0)
    # his eyes, under the lids: when they open they burn
    with P_.eyes():
        f, g = P_.face("head", "north"), P_.face("head", "north", glow=True)
        for i0 in (1, 7):
            for di in range(4):
                for j in (5, 6):
                    centre = di in (1, 2) and j == 5 or di == (1 if i0 == 1 else 2)
                    f.set(i0 + di, j, hexc(PINK[3]) if centre else LINEN.at(6))
                    g.set(i0 + di, j, hexc(PINK[1] if centre else PINK[3], 255 if centre else 150))
    # the hand at his chin: grey knuckles over the sheet
    for fname in ("north", "top"):
        f = P_.face("hand_l", fname)
        for i, j in f.each():
            if i % 2 == 1:
                f.set(i, j, SKIN.at(3.6))
    # his nightcap: rose and cream stripes, a cream cuff, the bobble on its end
    for c in ("cap1", "cap2", "cap3"):
        P_.paint(c, ROSE, 4.6, lambda f, i, j: (LINEN.at(5.6 + R.FACE_LIGHT[f.name]) if (j // 2) % 2 and f.name in SIDES else 0), grad=0.6, rim=0.5)
    P_.paint("cap0", LINEN, 5.6, lambda f, i, j: (ROSE.at(4) if f.name in SIDES and j == 2 else 0), grad=0.8, rim=0.8, ao=0.8)
    P_.paint("pom", LINEN, 6.0, None, grad=0.8, rim=0.6, noise=1.2, nscale=2)
    # his sleeve, striped flannel torn off at the elbow; the long bare arm, knobbed at the elbow, dirty at the fist
    P_.paint("sleeve", LINEN, 5.0, lambda f, i, j: (DUSK.at(3.6) if f.name in SIDES and i % 3 == 0 else
                                                    MOTH.at(1) if j >= f.h - 2 and R._h("fray", f.name, i) % 2 else 0), grad=0.8, rim=0.6)
    for c in ("arm_r", "forearm_r", "elbow_r"):
        P_.paint(c, SKIN, 4.6 if c != "elbow_r" else 5.4, lambda f, i, j, c=c: (-0.8 if f.name in SIDES and R._h(c, f.name, i) % 4 == 0 else 0)
                 + (-0.06 * j if c == "forearm_r" and f.name in SIDES else 0), grad=0.8, rim=0.4, ao=0.5, noise=0.4, nscale=3)
    P_.paint("fist", SKIN, 4.6, lambda f, i, j: (-2.0 if f.name == "bottom" else 1.0 if f.name == "north" and i % 2 == 0 and j < 2 else
                                                 MOTH.at(4.6) if f.name == "north" and j >= f.h - 2 and i % 2 == 0 else 0), grad=1.0, rim=0.8, ao=1.0)
    # the dreamcatcher: a leather-bound hoop, its web dark with silver threads and a dream caught in it, grey feathers
    for c in of("hoop_", "hc", "cord"):
        P_.paint(c, OCHRE, 3.6, lambda f, i, j: (0.8 if (i + j) % 2 else -0.4), grad=0.4, rim=0.4)
    for c in ("web0", "web1"):
        f, g = P_.face(c, "north"), P_.face(c, "north", glow=True)
        ox, oy = (1, 0) if c == "web0" else (0, 1)  # both on the same 8 x 7 grid
        for i, j in f.each():
            x, y = i + ox - 2.5, j + oy - 3.0
            d = math.hypot(x, y)
            thread = (i + ox + j + oy) % 3 == 0 or (i + ox - j - oy) % 3 == 0 or abs(d - 2.4) < 0.45
            f.set(i, j, LINEN.at(6.2) if thread else PLUM.at(0.8))
            if d < 0.8:
                f.set(i, j, hexc(PINK[2]))
                g.set(i, j, hexc(PINK[1], 230))
            elif thread:
                g.set(i, j, hexc(PINK[3], 60))
        for fname in ("south", "top", "bottom", "east", "west"):
            P_.paint(c, PLUM, 1.0, None, faces=(fname,), grad=0, rim=0, ao=0)
    for c in ("plume0", "plume1", "plume2"):
        P_.paint(c, MOTH, 4.6, lambda f, i, j: (2.0 if i == 0 else 0) + (-0.4 * (j % 2)), grad=1.0, rim=0.6)
        for fname in SIDES:
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            f.set(0, f.h - 1, hexc(PINK[1]))
            g.set(0, f.h - 1, hexc(PINK[1], 160))
    P_.paint("bead", ROSE, 6.0, None, rim=0.6)
    for fname in ("north", "east", "west"):
        P_.glow_face("bead", fname, lambda f, i, j: hexc(PINK[2], 140))
    return P_.skin, P_.glow


# ---------------------------------------------------------------------------------------------------------------------
# Posing: the bed set, each leg reaching its claw to where it stands, the Sleeper set on it, his fist where it goes,
# the hanging things hung plumb

def _swing_dir(fwd, side):
    """A hanging part's way down, swung fwd degrees toward the front and side degrees toward its left."""
    f, s = fwd * DEG, side * DEG
    return (math.cos(f) * math.sin(s), math.cos(f) * math.cos(s), -math.sin(f))


def _flat(p, yaw=0.0, shift=(0.0, 0.0)):
    """A point (x, height, z) of the bed's own floor plan once the bed has turned by yaw and moved by shift."""
    a = yaw * DEG
    x, h, z = p
    return (x * math.cos(a) + z * math.sin(a) + shift[0], h, -x * math.sin(a) + z * math.cos(a) + shift[1])


HANG = CURTAINS + ("dream", "tatter")


def _pose(r, lift=0.0, pitch=0.0, yaw=0.0, roll=0.0, shift=(0.0, 0.0), feet=None, poles=None, torso=(0, 0, 0), neck=(0, 0, 0),
          head=(0, 0, 0), jaw=0.0, lids=0.0, fist=None, elbow=None, hand=None, dream=(0.0, 0.0), spin=0.0, tuck=0.0, splay=None,
          extra=None):
    """The bed: lift (+ up), pitch (+ its foot end down, its head end up), yaw (+ turning to its right), roll (+ its top
    to its left) and shift (x, z); feet {leg: (x, height, z)} where the feet stand (the rest where they stand at rest).
    The Sleeper: torso (+x sits him up from his pillows), neck and head (on top of how they lie), jaw (+ open), lids
    (0 shut, 1 open); fist: where his wrist goes (x, height, z) (None: on the floor beside the bed, where it drags);
    hand: None sets the fist knuckles down, "free" lets it follow his forearm, or (down, across) directions. dream
    (forward, side) swings the dreamcatcher and spin turns it; tuck draws the dark under the bed up into it and splays
    the dust ruffle out on the floor (when it lies down); splay, when given, spreads the ruffle by its own amount (a
    rearing bed draws its dark up but its ruffle hangs)."""
    p = {"frame": {"rot": (pitch, yaw, roll), "pos": (shift[0], lift, shift[1])}, "torso": tuple(torso), "neck": tuple(neck),
         "head": tuple(head), "jaw": (jaw, 0, 0), "lids": {"rot": (0, 0, 0), "pos": (0, 0, OPEN_LIDS * lids)},
         "under": {"rot": (0, 0, 0), "pos": (0, tuck, 0)}}
    fr = core.frames(r, core.as_sample(p))
    feet = feet or {}
    poles = poles or {}
    for k in range(4):
        p.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", ik.model(feet.get(k, REST_FOOT[k])), poles.get(k, POLE[k])))
    tgt = fist if fist is not None else _flat(FIST, yaw, shift)
    p.update(ik.limb(r, fr, "arm_r", "forearm_r", "hand_r", ik.model(tgt), elbow or ELBOW))
    fr = core.frames(r, core.as_sample(p))
    M = fr["frame"][0]
    across = (M[0][0], M[1][0], M[2][0])  # the bed's own x
    flat = _flat((1, 0, 0), yaw)
    for k in range(4):
        p[f"foot{k}"] = ik.orient(r, fr, f"foot{k}", (0, 1, 0), flat)
    if hand != "free":
        down, x = hand if hand else ((0, 1, 0), flat)
        p["hand_r"] = ik.orient(r, fr, "hand_r", down, x)
    for b in HANG:
        if b == "dream":
            s = spin * DEG
            p[b] = ik.orient(r, fr, b, _swing_dir(*dream), (math.cos(s) * across[0] - math.sin(s) * across[2], 0, math.sin(s) * across[0] + math.cos(s) * across[2]))
        else:
            p[b] = ik.orient(r, fr, b, (0, 1, 0), across)
    for s, ax, sg in (("f", 0, -1), ("b", 0, 1), ("l", 2, -1), ("r", 2, 1)):
        e = [0.0, 0.0, 0.0]
        e[ax] = sg * 7.0 * (tuck if splay is None else splay)
        p["skirt_" + s] = tuple(e)
    if extra:
        p.update(extra)
    return p


def _foot(k, up=0.0, dz=0.0, dx=0.0):
    """Leg k's foot moved from where it stands at rest: up, and by (dx, dz)."""
    x, h, z = REST_FOOT[k]
    return (x + dx, h + up, z + dz)


def _fist(x=0.0, up=0.0, z=0.0, yaw=0.0, shift=(0.0, 0.0)):
    """The wrist moved from where the fist lies on the floor at rest, in the bed's own frame."""
    return _flat((FIST[0] + x, FIST[1] + up, FIST[2] + z), yaw, shift)


def _plant(r, clip, legs, where=None, fist=False):
    """The legs that stand through a move keep their feet on the floor where they stand, every tick (the staged
    clip's follow-through and settle would otherwise slide them); with fist the fist lies still on the floor too."""
    where = where or {}

    def fn(t, s, fr):
        out = {}
        for k in legs:
            out.update(ik.limb(r, fr, f"thigh{k}", f"shin{k}", f"foot{k}", ik.model(where.get(k, REST_FOOT[k])), POLE[k]))
        if fist:
            out.update(ik.limb(r, fr, "arm_r", "forearm_r", "hand_r", ik.model(FIST), ELBOW))
        return out
    ik.retrack(r, clip, fn)


def _curtains(r, clip, pts):
    """The curtains drawn: [(seconds, how shut: 0 open .. 1 drawn)] as scale tracks on their bunches (front ones
    stretch across, side ones along)."""
    c = r.clips[clip]
    for b in CURTAINS:
        if b.startswith("cf"):
            c.scale(b, [(t, 1 + (FRONT_DRAW - 1) * s, 1, 1) for t, s in pts], "cat")
        else:
            c.scale(b, [(t, 1, 1, 1 + (SIDE_DRAW - 1) * s) for t, s in pts], "cat")


def _staged_at(mv, stage, f):
    """Seconds into a staged move's clip at a fraction of one of its stages."""
    t0 = {"windup": 0.0, "active": mv.seconds("windup"), "recover": mv.seconds("windup") + mv.seconds("active")}[stage]
    return t0 + f * mv.seconds(stage)


# ---------------------------------------------------------------------------------------------------------------------
# Clips

WALK = {2: 0.0, 0: 0.25, 3: 0.5, 1: 0.75}  # a heavy four-beat walk: back left, front left, back right, front right
GALLOP = {0: 0.0, 1: 0.06, 2: 0.5, 3: 0.56}  # the run: a rocking-horse bound, the front pair then the back pair


def _gait(u, stride_px, beta, lift, phases):
    """The feet through a gait at u (0..1 of a cycle): down, each moves back exactly as far as the bed travels, so it
    stays put on the floor; up, it lifts and reaches forward again."""
    feet = {}
    st = stride_px * beta
    for k, ph in phases.items():
        q = (u - ph) % 1.0
        if q < beta:
            feet[k] = _foot(k, dz=-st / 2 + st * q / beta)
        else:
            f = (q - beta) / (1 - beta)
            feet[k] = _foot(k, up=lift * math.sin(math.pi * f), dz=st / 2 - st * core.ease_io(f))
    return feet


def _idle(r):
    """Asleep on its legs: the Sleeper's slow breaths lift the quilt and he snores on each, his nightcap stirring; the
    bed rests a front leg and sets it down again; then he twitches in his sleep, his head rolling on the pillow and his
    knuckles curling on the floor, and settles."""
    L, n = 6.0, 30
    keys = []
    for k in range(n + 1):
        u = k / n
        br = math.sin(2 * math.pi * 2 * u)  # two breaths a loop
        snore = max(0.0, math.sin(2 * math.pi * 2 * u - 0.4))
        rest = math.sin(math.pi * min(1.0, max(0.0, (u - 0.3) / 0.22)))  # the front right rests, late in the first breath
        tw = math.sin(math.pi * min(1.0, max(0.0, (u - 0.72) / 0.2)))  # the twitch
        feet = {1: _foot(1, up=3.5 * rest, dz=1.5 * rest)} if rest > 0 else {}
        keys.append((L * u, _pose(r, lift=0.4 * br - 0.6 * rest, roll=-1.2 * rest + 1.0 * tw, pitch=0.5 * br, feet=feet,
                                  torso=(2.4 * br, 0, -3 * tw), head=(-2.5 * snore + 3 * tw, -16 * tw, 4 * tw), jaw=3 + 7 * snore,
                                  fist=_fist(x=-1.5 * tw, up=1.2 * tw, z=-1 * tw), dream=(1.5 * br, 2 * math.sin(2 * math.pi * u)),
                                  spin=12 * math.sin(2 * math.pi * u))))
    keyed(r, "idle", L, keys, loop=True)


def _locomotion(r):
    """It walks on its posts, a heavy four-beat walk, the frame rolling over each leg as it takes the weight, the
    curtains swinging and the Sleeper's fist dragging on the floor beside it; it runs like a rocking horse, the front
    pair and then the back pair, pitching fore and aft."""
    for name, L, stride, beta, lift, phases in (("walk", 1.5, 1.4, 0.75, 6.0, WALK), ("run", 0.7, 2.6, 0.42, 9.0, GALLOP)):
        sp = stride * 16
        keys = []
        n = 24
        run = name == "run"
        for k in range(n + 1):
            u = k / n
            s, c = math.sin(2 * math.pi * u), math.cos(2 * math.pi * u)
            feet = _gait(u, sp, beta, lift, phases)
            if run:
                pose = _pose(r, lift=-1.5 + 2.2 * abs(math.sin(2 * math.pi * u)), pitch=6.5 * c, roll=1.0 * s, feet=feet,
                             torso=(-3 * c, 0, -1.5 * s), head=(5 * math.sin(2 * math.pi * u - 1.2), 0, 0), jaw=4 + 3 * max(0, c),
                             fist=_fist(x=-1, up=2.5 * max(0.0, math.sin(2 * math.pi * u + 0.6)), z=9 + 2 * s),
                             dream=(-9 * c, 3 * s))
            else:
                four = math.sin(4 * math.pi * u)
                pose = _pose(r, lift=-0.9 * (0.5 + 0.5 * math.cos(8 * math.pi * u)), pitch=1.2 * four, roll=2.6 * s, yaw=0.8 * s,
                             feet=feet, torso=(1.0 * four, 0, -1.4 * s), head=(2 * math.sin(8 * math.pi * u - 1.0), 3 * s, 0), jaw=3,
                             fist=_fist(x=-0.8 * c, up=0.6 * max(0.0, four), z=4 + 2.5 * math.sin(2 * math.pi * u - 0.8)),
                             dream=(-2 * four, -4 * s))
            keys.append((L * u, pose))
        keyed(r, name, L, keys, loop=True)
    r.walk_stride, r.run_stride = 1.4, 2.6
    r.run_speed = (0.12, 0.28)
    for nm, sgn in (("turn_l", -1), ("turn_r", 1)):
        keys = []
        n = 16
        for k in range(n + 1):
            u = k / n
            feet = {}
            for j, ph in ((0, 0.0), (3, 0.0), (1, 0.5), (2, 0.5)):  # each diagonal pair steps round while the other holds
                q = (u - ph) % 1.0
                side = LEGS[j][1]  # the front feet step one way, the back ones the other
                if q < 0.5:
                    f = q / 0.5
                    feet[j] = _foot(j, dx=sgn * side * 6 * (core.ease_io(f) - 0.5), up=5 * math.sin(math.pi * f))
                else:
                    feet[j] = _foot(j, dx=sgn * side * 6 * (0.5 - (q - 0.5) / 0.5))
            keys.append((1.0 * u, _pose(r, yaw=sgn * 2.0, roll=1.5 * math.sin(4 * math.pi * u), feet=feet, torso=(0, 0, -sgn * 2),
                                        head=(0, sgn * 6, 0), fist=_fist(x=-1, z=2 * math.sin(2 * math.pi * u)))))
        keyed(r, nm, 1.0, keys, loop=True)


def _sat(r, low=1.0):
    """Lying low like any bed, its legs folded under it, the knees out toward its ends; the dark drawn up under it and
    the ruffle spread on the floor."""
    feet = {k: (LEGS[k][0] * 18.5, FOOT_UP + 0.3, LEGS[k][1] * 27.0) for k in range(4)}
    poles = {k: (LEGS[k][0] * 0.3, -0.5, LEGS[k][1] * 0.9) for k in range(4)}
    return _pose(r, lift=-9.5 * low, pitch=-BOW * 0.5 * low, feet=feet, poles=poles, tuck=9.5 * low, fist=_fist(x=2, up=0, z=0))


def _reactions(r):
    """Struck, the bed jolts and the Sleeper starts, his eyes flickering half open, and sinks back. Its stance broken,
    it sinks onto its folded legs like any bed (and waits for you that way); it gets up back legs first."""
    hit = _pose(r, lift=-1.5, pitch=-5, torso=(9, 0, 0), head=(-12, 0, 0), jaw=12, lids=0.45, dream=(14, 0))
    back = _pose(r, lift=-0.5, pitch=2, torso=(2, 0, 0), head=(3, 0, 0), jaw=4, lids=0.1, dream=(-6, 0))
    keyed(r, "hurt", 0.6, [(0, _pose(r)), (0.08, hit), (0.32, back), (0.6, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it rocks away to its right
        h = _pose(r, lift=-1.5, roll=-8 * sgn, yaw=-4 * sgn, torso=(6, 0, -8 * sgn), head=(-6, 14 * sgn, 0), jaw=10, lids=0.4, dream=(0, 12 * sgn))
        b = _pose(r, lift=-0.5, roll=3 * sgn, torso=(1, 0, 2 * sgn), head=(0, -3 * sgn, 0), jaw=3, dream=(0, -6 * sgn))
        keyed(r, nm, 0.65, [(0, _pose(r)), (0.08, h), (0.34, b), (0.65, _pose(r))])
    half = _sat(r, 0.5)
    keyed(r, "kneel", 0.9, [(0, _pose(r)), (0.3, half), (0.62, _sat(r, 1.06)), (0.9, _sat(r))])
    back_up = _pose(r, lift=-5, pitch=7, feet={0: (18.5, FOOT_UP + 0.3, -27.0), 1: (-18.5, FOOT_UP + 0.3, -27.0)},
                    poles={0: (0.3, -0.5, -0.9), 1: (-0.3, -0.5, -0.9)}, tuck=4)
    keyed(r, "rise", 1.1, [(0, _sat(r)), (0.4, back_up), (0.75, _pose(r, lift=0.8, pitch=-1.5)), (1.1, _pose(r))])
    _death(r)


def _death(r):
    """Its last night: the bed shudders, a front leg gives and it lurches down that way, then all four splay and it
    crashes onto its frame. The Sleeper sits up one last time, his eyes open, and looks round; he lies back, his fist
    slides limp along the floor, and the curtains draw shut round him."""
    shake = _pose(r, lift=-1, roll=4, pitch=-3, torso=(6, 0, 3), head=(-8, 10, 0), jaw=12)
    give = _pose(r, tuck=3, splay=0, lift=-8, pitch=9, roll=-7, feet={1: _foot(1, dz=-8, dx=-6), 0: _foot(0, dz=-3)}, torso=(10, 0, -6), head=(6, -8, 0), jaw=16)
    flat = {k: _foot(k, dx=LEGS[k][0] * 12, dz=LEGS[k][1] * 7) for k in range(4)}
    lows = {k: (LEGS[k][0] * 0.8, -0.6, LEGS[k][1] * 0.4) for k in range(4)}
    crash = _pose(r, lift=-14, pitch=-BOW + 1.5, roll=-2.5, feet=flat, poles=lows, tuck=10, torso=(-4, 0, 0), head=(4, 0, 0), jaw=10, dream=(20, 0),
                  fist=_fist(x=-2, z=-2))
    lay = lambda **kw: _pose(r, lift=-14.5, pitch=-BOW + 1, roll=-2, feet=flat, poles=lows, tuck=10, **kw)
    up = lay(torso=(46, 0, 0), head=(-14, 0, 0), jaw=6, lids=1.0, fist=_fist(up=4, z=-4))
    look = lay(torso=(48, 0, 0), head=(-10, 26, 6), jaw=4, lids=1.0, fist=_fist(up=4, z=-4))
    look2 = lay(torso=(46, 0, 0), head=(-10, -22, -4), jaw=4, lids=0.9, fist=_fist(up=3, z=-4))
    down = lay(torso=(-2, 0, 0), head=(6, 0, 0), jaw=3, lids=0.15, fist=_fist(z=-2))
    still = lay(torso=(-4, 0, 0), head=(8, 4, 0), jaw=2, lids=0.0, fist=_fist(x=2, z=6))
    keys = [(0, _pose(r)), (0.3, shake), (0.75, give), (1.15, crash), (1.4, lay(torso=(-6, 0, 0), head=(6, 0, 0), jaw=8, fist=_fist(z=-2))),
            (1.9, lay(torso=(10, 0, 0), head=(-4, 0, 0), jaw=8, lids=0.4, fist=_fist(up=2, z=-3))), (2.3, up), (2.8, look), (3.25, look2),
            (3.75, down), (4.2, still), (4.6, still)]
    keyed(r, "death", 4.6, keys)
    _curtains(r, "death", [(0, 0), (3.9, 0), (4.5, 1.0), (4.6, 1.0)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

NO_LAG = {b + str(k): 0 for b in ("thigh", "shin", "foot") for k in range(4)}


def _lag(**kw):
    out = dict(NO_LAG, head=0.06, neck=0.04, jaw=0.05)
    out.update(kw)
    return out


def _leg_hit(k):
    return [(f"foot{k}", (0, 3, 0), 3.0), (f"shin{k}", (0, 4, 0), 2.5)]


FIST_GROUND = [(["hand_r"], [("hand_r", 0, 40), ("forearm_r", 0, 30), ("arm_r", 0, 25)])]


def stomp(r, k=0, mid="stomp"):
    """A front post rears up, its knee high and the claw drawn back under it, the bed leaning back off it; then it
    stamps down a long step ahead and the bed drops onto it, the Sleeper jolted on his pillows (LIGHT)."""
    sx = LEGS[k][0]
    gather = _pose(r, lift=-2.5, pitch=-3.5, roll=-3 * sx, yaw=-1 * sx, feet={k: _foot(k, up=9, dz=3)}, torso=(3, 0, -1 * sx), head=(-2, -3 * sx, 0), jaw=4)
    rear = _pose(r, lift=-1.5, pitch=-6.5, roll=-4 * sx, yaw=-2 * sx, feet={k: _foot(k, up=21, dz=4)}, poles={k: (sx * 0.3, -0.6, -0.8)},
                 torso=(4, 0, -2 * sx), head=(-5, 0, 0), jaw=5, dream=(-6, 0))
    hit = _pose(r, lift=-5, pitch=7, roll=4 * sx, yaw=3 * sx, feet={k: _foot(k, up=0.2, dz=-13)}, torso=(-5, 0, 3 * sx), head=(10, 4 * sx, 0),
                jaw=1, dream=(14, 0))
    held = _pose(r, lift=-5.5, pitch=7.5, roll=4.5 * sx, yaw=3 * sx, feet={k: _foot(k, dz=-13)}, torso=(-5.5, 0, 3 * sx), head=(11, 4 * sx, 0),
                 jaw=2, dream=(8, 0))
    sink = _pose(r, lift=-6, pitch=8, roll=5 * sx, yaw=3.5 * sx, feet={k: _foot(k, dz=-13)}, torso=(-4, 0, 3 * sx), head=(8, 3 * sx, 0), jaw=4,
                 dream=(2, 0))
    back = _pose(r, lift=-2, pitch=3, roll=1.5 * sx, feet={k: _foot(k, up=5, dz=-5)}, torso=(1, 0, 1 * sx), jaw=3)
    return Move(mid, "LIGHT", 11, 4, 14, through=sink, track=8.0, rng=(0, 3.6), kinds=("combo", "near"), damage=1.0, knock=0.7, hit=_leg_hit(k), plant=(),
                stages={"windup": [(0.3, gather), (1.0, rear)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


FIST_AT = (-26.0, 7.5, -24.0)  # where the Restless Fist comes down: by the bed's front right corner


def fist(r):
    """The Restless Fist (HEAVY): the Sleeper stirs and rolls away, dragging his fist back along the floor; then he
    heaves the arm up high over the bed's side, half sitting up, and holds it there; and brings it down by the bed's
    front corner with all the bed's weight rolling after it."""
    gather = _pose(r, lift=-3.5, pitch=-1.5, roll=6, yaw=-2.5, torso=(16, 4, 8), head=(-6, 10, 0), jaw=8, fist=_fist(x=-2, z=8))
    lift_ = _pose(r, lift=0, pitch=-3, roll=6.5, torso=(34, -4, 10), head=(-10, 10, 0), jaw=10, lids=0.15, fist=(-26.0, 46.0, 6.0), hand="free",
                  elbow=(-0.6, -0.3, 0.8), dream=(-6, 4))
    peak = _pose(r, lift=2.5, pitch=-3.5, roll=7, torso=(40, -6, 12), head=(-12, 12, 0), jaw=12, lids=0.2, fist=(-23.0, 62.0, 6.0), hand="free",
                 elbow=(-0.8, 0.2, 0.6), dream=(-8, 5))
    hit = _pose(r, tuck=3, splay=0, lift=-4, pitch=6, roll=-7, yaw=-3, torso=(60, -20, -14), head=(10, -12, 0), jaw=14, lids=0.1, fist=FIST_AT,
                elbow=(-0.8, -0.5, -0.3), dream=(16, -6))
    held = _pose(r, tuck=3, splay=0, lift=-4.5, pitch=6.5, roll=-7.5, yaw=-3, torso=(61, -22, -15), head=(12, -12, 0), jaw=10, lids=0.05,
                 fist=(FIST_AT[0], FIST_AT[1] - 0.3, FIST_AT[2] - 0.6), elbow=(-0.8, -0.5, -0.3), dream=(8, -4))
    sink = _pose(r, lift=-5, pitch=7, roll=-8, yaw=-3.5, torso=(58, -22, -14), head=(14, -10, 0), jaw=6,
                 fist=(FIST_AT[0], FIST_AT[1] - 0.3, FIST_AT[2] - 0.8), elbow=(-0.8, -0.5, -0.3), dream=(2, -2))
    drag = _pose(r, lift=-1.5, pitch=2, roll=-2, torso=(22, -6, -4), head=(4, -4, 0), jaw=4, fist=_fist(x=-1, z=-12))
    return Move("fist", "HEAVY", 18, 6, 22, through=sink, title="Restless Fist", hold=8, track=6.0, ability="Restless Fist",
                origin=(FIST_AT[0] / 16, 0, -FIST_AT[2] / 16), rng=(0, 4.2), kinds=("area", "near"), damage=1.4, knock=1.2, plant=(),
                hit=[("hand_r", (0, 3.5, 0), 4.5), ("forearm_r", (0, 12, 0), 3.0)],
                stages={"windup": [(0.3, gather), (0.7, lift_), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, drag)]},
                lag=_lag(), ground=FIST_GROUND, bite=0.45)


def kick(r):
    """For whoever is behind it (LIGHT): the bed drops its foot end and takes its weight on its front posts, tucks its
    back legs under, and kicks both out behind it like a mule, the Sleeper bounced on his pillows."""
    tuck_ = {2: _foot(2, up=8, dz=-8), 3: _foot(3, up=8, dz=-8)}
    gather = _pose(r, lift=-1, pitch=5, feet={2: _foot(2, up=3, dz=-3), 3: _foot(3, up=3, dz=-3)}, torso=(-2, 0, 0), jaw=3)
    load = _pose(r, lift=-2.5, pitch=9, feet=tuck_, poles={2: (0.3, -0.4, 0.9), 3: (-0.3, -0.4, 0.9)}, torso=(-3, 0, 0), head=(4, 0, 0), jaw=4,
                 dream=(6, 0))
    out = {2: (19.0, 15.0, 52.0), 3: (-19.0, 14.0, 52.0)}
    hit = _pose(r, tuck=5, splay=0, lift=2, pitch=14, feet=out, poles={2: (0.3, 0.5, 0.8), 3: (-0.3, 0.5, 0.8)}, torso=(8, 0, 0), head=(-10, 0, 0), jaw=12,
                dream=(22, 0))
    held = _pose(r, tuck=5, splay=0, lift=2, pitch=14, feet={2: (19.0, 12.5, 51.5), 3: (-19.0, 11.5, 51.5)}, poles={2: (0.3, 0.5, 0.8), 3: (-0.3, 0.5, 0.8)},
                 torso=(9, 0, 0), head=(-11, 0, 0), jaw=10, dream=(14, 0))
    sink = _pose(r, tuck=4, splay=0, lift=1, pitch=11.5, feet={2: (19.0, 9.5, 45.5), 3: (-19.0, 9.0, 45.5)}, poles={2: (0.3, 0.5, 0.8), 3: (-0.3, 0.5, 0.8)},
                 torso=(5, 0, 0), head=(-5, 0, 0), jaw=6, dream=(6, 0))
    down = _pose(r, lift=-1, pitch=3.5, feet={2: _foot(2, up=3, dz=3), 3: _foot(3, up=3, dz=3)}, torso=(-2, 0, 0), head=(4, 0, 0), jaw=3)
    return Move("kick", "LIGHT", 10, 4, 14, through=sink, track=0.0, rng=(0, 3.8), kinds=("behind",), damage=1.0, knock=1.3,
                hit=_leg_hit(2) + _leg_hit(3), plant=(),
                stages={"windup": [(0.3, gather), (1.0, load)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.55, down)]}, lag=_lag())


def charge(r):
    """Runaway Bed (HEAVY, a gap closer): it sinks back on its back legs with its foot end lowered like a ram, a front
    claw pawing the floor; then it bolts at you, bounding like a rocking horse, the curtains streaming and the fist
    dragged bouncing behind, and skids up on its back legs."""
    paw = _pose(r, lift=-3, pitch=5, feet={0: _foot(0, up=5, dz=-3), 2: _foot(2, dz=-3), 3: _foot(3, dz=-3)}, torso=(-2, 0, 0), jaw=4,
                fist=_fist(z=8))
    scrape = _pose(r, lift=-5, pitch=7.5, feet={0: _foot(0, up=1.5, dz=1.5), 2: _foot(2, dz=-4.5), 3: _foot(3, dz=-4.5)}, torso=(-2.5, 0, 0),
                   head=(2, 0, 0), jaw=4, fist=_fist(z=11), dream=(6, 0))
    low = _pose(r, lift=-6, pitch=9, feet={0: _foot(0, dz=-1), 2: _foot(2, dz=-5), 3: _foot(3, dz=-5)}, torso=(-3, 0, 0), head=(4, 0, 0),
                jaw=4, fist=_fist(z=12), dream=(10, 0))
    act = []
    n = 6
    for j in range(n + 1):
        u = j / n
        q = (3 * u) % 1.0
        c = math.cos(2 * math.pi * q)
        act.append((u, _pose(r, lift=-2 + 2.5 * abs(math.sin(2 * math.pi * q)), pitch=7 + 6 * c, feet=_gait(q, 42.0, 0.4, 10.0, GALLOP),
                             torso=(-4 * c, 0, 0), head=(4 * math.sin(2 * math.pi * q - 1), 0, 0), jaw=6,
                             fist=_fist(up=3 * max(0.0, math.sin(2 * math.pi * q)), z=16), dream=(-14 - 4 * c, 0))))
    skid = _pose(r, tuck=4, splay=0, lift=-3, pitch=-8, feet={0: _foot(0, dz=-10), 1: _foot(1, dz=-9), 2: _foot(2, dz=-2), 3: _foot(3, dz=-2)},
                 poles={0: (0.4, 0.2, -0.9), 1: (-0.4, 0.2, -0.9)}, torso=(6, 0, 0), head=(-8, 0, 0), jaw=10, fist=_fist(z=6), dream=(24, 0))
    end = _pose(r, lift=-1, pitch=3, torso=(-2, 0, 0), head=(4, 0, 0), jaw=4, dream=(-8, 0))
    return Move("charge", "HEAVY", 14, 14, 18, through=skid, title="Runaway Bed", track=6.0, lock=10, advance=7.0, rng=(5, 14), kinds=("gap",),
                damage=1.3, knock=1.5, plant=(), hit=[("frame", (0, -8, -29), 7.0)],
                stages={"windup": [(0.3, paw), (0.65, scrape), (1.0, low)], "active": act[1:], "recover": [(0.5, end)]}, lag=_lag())


def eyelids(r):
    """Heavy Eyelids: the Sleeper yawns, his head tipping back on the pillows, and the bed settles lower on its legs
    like someone getting comfortable; the curtains draw shut all round, and the dark comes in. They open again when it
    stirs."""
    yawn = _pose(r, lift=1.5, pitch=-1, torso=(7, 0, 0), head=(-14, 0, 0), jaw=26, fist=_fist(up=1, z=1))
    settle = _pose(r, lift=-4.5, pitch=1, torso=(-3, 0, 0), head=(5, 6, 0), jaw=6, dream=(0, 0), spin=30)
    # deep asleep behind the curtains: long breaths heave the bed on its legs and it rocks, the way a sleeper settles
    deep = lambda b, s, sp: _pose(r, lift=-6.5 + 2.5 * b, pitch=1 - 1.5 * b, roll=3.5 * s, yaw=-1.5 * s, torso=(-4 + 5 * b, 0, -4 * s),
                                  head=(6 - 6 * b, 8 + 14 * s, 3 * s), jaw=3 + 12 * b, spin=sp, dream=(0, -6 * s))
    stir = _pose(r, lift=-2, pitch=0, torso=(2, 0, 0), head=(0, 0, 0), jaw=3, spin=-20)
    mv = Move("eyelids", "NONE", 14, 24, 16, through=deep(0.4, 0.3, 110), title="Heavy Eyelids", track=0.0, ability="Heavy Eyelids", rng=(0, 99),
              kinds=("area",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.4, yawn), (1.0, settle)], "active": [(0.3, deep(1.0, 1.0, 60)), (0.62, deep(0.0, -0.6, 90)), (1.0, deep(1.0, -1.0, 120))],
                      "recover": [(0.55, stir)]}, lag=_lag())
    mv._curtains = [("windup", 0.0, 0), ("windup", 0.5, 0), ("windup", 1.0, 1.0), ("recover", 0.15, 1.0), ("recover", 0.8, 0.0)]
    return mv


def terrors(r):
    """Night Terrors: he tosses and turns in his sleep, rolling one way and the other under the quilt, his head
    turning on the pillow, and the whole bed rocks with him on its braced legs, the curtains swinging; things shake
    loose from under it."""
    brace = {k: _foot(k, dx=LEGS[k][0] * 3) for k in range(4)}
    toss = lambda s, a: _pose(r, lift=-1, roll=10 * s * a, yaw=-2 * s * a, feet=brace, torso=(4 * a, 6 * s * a, 16 * s * a),
                              head=(2, 22 * s * a, 6 * s * a), jaw=6 + 6 * a, lids=0.0, fist=_fist(x=-3 * s * a, up=2 * a, z=-2 * s * a),
                              dream=(0, -18 * s * a), tuck=1.5 * a)
    gather = _pose(r, lift=-1.5, feet=brace, torso=(3, 0, -4), head=(2, -10, 0), jaw=6)
    act = [(0.17, toss(1, 0.8)), (0.34, toss(-1, 1.0)), (0.5, toss(1, 1.0)), (0.67, toss(-1, 1.0)), (0.84, toss(1, 0.7)), (1.0, toss(-1, 0.35))]
    return Move("terrors", "NONE", 12, 24, 16, through=toss(-1, 0.15), title="Night Terrors", track=0.0, ability="Night Terrors", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, gather), (1.0, toss(-1, 0.4))], "active": act, "recover": [(0.5, gather)]}, lag=_lag())


def dreamstep(r):
    """Dreamstep (a gap closer): he sinks back into his pillows with his chin on his chest, the bed crouching low under
    him and its curtains swinging half shut, and it is gone: it rises out of its crouch somewhere else."""
    sink_ = _pose(r, lift=-6, pitch=1, torso=(-6, 0, 0), head=(10, 0, 0), jaw=2, spin=40, dream=(4, 0))
    low = _pose(r, lift=-9, pitch=1.5, torso=(-7, 0, 0), head=(12, 0, 0), jaw=1, spin=80, tuck=2)
    shiver = lambda s: _pose(r, lift=-10 + 1.5 * abs(s), pitch=1.5, roll=3 * s, yaw=-1.5 * s, torso=(-7.5, 0, -4 * s), head=(12, 10 * s, 4 * s),
                             jaw=1, spin=100 + 40 * s, tuck=2.5, dream=(0, -8 * s))
    lower = shiver(0.0)
    rise = _pose(r, lift=-3, pitch=-1, torso=(2, 0, 0), head=(2, 0, 0), jaw=4, spin=20, dream=(-8, 0))
    mv = Move("dreamstep", "NONE", 10, 8, 14, through=shiver(0.2), title="Dreamstep", track=0.0, ability="Dreamstep", rng=(0, 99), kinds=("gap",),
              damage=0, knock=0, plant=(), stages={"windup": [(0.45, sink_), (1.0, low)], "active": [(0.3, shiver(1)), (0.65, shiver(-1)), (1.0, shiver(0.6))], "recover": [(0.5, rise)]},
              lag=_lag())
    mv._curtains = [("windup", 0.1, 0), ("windup", 1.0, 0.55), ("recover", 0.25, 0.5), ("recover", 0.9, 0.0)]
    return mv


def sleepwalkers(r):
    """Sleepwalkers: he sits half up out of his pillows with his head lolling on his chest, and his long arm rises off
    the floor and reaches out straight ahead of the bed, the hand hanging limp, the way sleepwalkers reach; it sways
    there, and falls back to the floor with a thump."""
    limp = ((0.15, 0.85, -0.5), (1, 0, 0))
    up_ = _pose(r, lift=0.5, torso=(26, 0, 0), head=(14, -8, 10), jaw=6, fist=(-24.0, 26.0, -6.0), hand=limp, elbow=(-0.8, 0.4, 0.3))
    reach = lambda s: _pose(r, lift=0.5, roll=1.5 * s, torso=(34, 2 * s, 2 * s), head=(18, -6 + 8 * s, 12 * s), jaw=8,
                            fist=(-15.0 + 2 * s, 44.0, -40.0), hand=limp, elbow=(-0.9, 0.3, 0.2), dream=(0, -4 * s))
    drop = _pose(r, lift=-1, torso=(10, 0, 0), head=(6, 0, 0), jaw=4, fist=_fist(z=-4), dream=(6, 0))
    return Move("sleepwalkers", "NONE", 14, 24, 16, through=reach(0.0), title="Sleepwalkers", track=6.0, ability="Sleepwalkers", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.45, up_), (1.0, reach(-1))], "active": [(0.33, reach(1)), (0.66, reach(-1)), (1.0, reach(0.5))],
                        "recover": [(0.45, drop)]}, lag=_lag(), ground=FIST_GROUND)


def awaken(r):
    """Rude Awakening (PERILOUS, the signature): the bed shudders, then rears up on its back legs, its front claws
    pawing high; the Sleeper sits bolt upright with his eyes open for the first time, flings his arm up and screams;
    and the bed crashes down onto its front posts, his fist with it, and everything wakes. He sinks back and his eyes
    close."""
    shud = _pose(r, lift=-0.5, roll=3, torso=(6, 0, 4), head=(-4, 12, 0), jaw=8)
    rear0 = _pose(r, tuck=3, splay=0, lift=4, pitch=-14, feet={0: _foot(0, up=14, dz=4), 1: _foot(1, up=11, dz=3)}, poles={0: (0.3, 0.6, -0.8), 1: (-0.3, 0.6, -0.8)},
                  torso=(24, 0, 0), head=(-6, 4, 0), jaw=12, lids=0.2, fist=(-28.0, 22.0, 6.0), hand="free")
    rear1 = _pose(r, tuck=7, splay=0, lift=7, pitch=-24, feet={0: _foot(0, up=28, dz=2), 1: _foot(1, up=32, dz=-2)}, poles={0: (0.3, 0.6, -0.8), 1: (-0.3, 0.6, -0.8)},
                  torso=(48, 0, 0), head=(-6, 0, 0), jaw=18, lids=0.6, fist=(-25.0, 56.0, 8.0), hand="free", elbow=(-0.8, 0.3, 0.5), dream=(-14, 0))
    peak = _pose(r, tuck=8, splay=0, lift=8, pitch=-26, feet={0: _foot(0, up=33, dz=-1), 1: _foot(1, up=30, dz=1)}, poles={0: (0.3, 0.6, -0.8), 1: (-0.3, 0.6, -0.8)},
                 torso=(62, 0, 0), head=(-10, 0, 0), jaw=28, lids=1.0, fist=(-22.0, 66.0, 8.0), hand="free", elbow=(-0.8, 0.3, 0.5), dream=(-16, 0))
    slam = _pose(r, tuck=2, splay=0, lift=-5, pitch=8, feet={0: _foot(0, dz=-8), 1: _foot(1, dz=-8)}, torso=(60, 0, -4), head=(8, 0, 0), jaw=24, lids=1.0,
                 fist=(-28.0, 7.5, -16.0), elbow=(-0.8, -0.4, -0.2), dream=(22, 0))
    ring = _pose(r, lift=-6, pitch=9, roll=1.5, feet={0: _foot(0, dz=-8), 1: _foot(1, dz=-8)}, torso=(58, 0, -3), head=(10, 0, 0), jaw=20, lids=1.0,
                 fist=(-28.0, 7.2, -16.5), elbow=(-0.8, -0.4, -0.2), dream=(10, 0))
    sink = _pose(r, lift=-6.5, pitch=9.5, roll=-1, feet={0: _foot(0, dz=-8), 1: _foot(1, dz=-8)}, torso=(54, 0, -2), head=(10, 0, 0), jaw=14,
                 lids=1.0, fist=(-28.0, 7.2, -16.8), elbow=(-0.8, -0.4, -0.2), dream=(2, 0))
    back = _pose(r, lift=-2, pitch=3, feet={0: _foot(0, up=4, dz=-4), 1: _foot(1, up=3, dz=-4)}, torso=(20, 0, 0), head=(4, 0, 0), jaw=6, lids=0.35,
                 fist=_fist(z=-8))
    calm = _pose(r, lift=-0.5, pitch=1, torso=(4, 0, 0), head=(1, 0, 0), jaw=3, lids=0.0, fist=_fist(z=-2))
    return Move("awaken", "PERILOUS", 30, 12, 30, through=sink, title="Rude Awakening", signature=True, track=0.0, ability="Rude Awakening",
                origin=(0, 0, 1.0), rng=(0, 99), kinds=("area", "ranged"), damage=1.0, knock=1.0, plant=(),
                stages={"windup": [(0.2, shud), (0.5, rear0), (0.8, rear1), (1.0, peak)], "active": [(0.3, slam), (1.0, ring)],
                        "recover": [(0.4, back), (0.8, calm)]}, lag=_lag(), ground=FIST_GROUND, bite=0.3)


def wake(r):
    """Its intro, once it is up on its legs: it shakes itself like a wet dog from its foot end to its canopy, the
    curtains and the nightcap flying, and the Sleeper's fist lifts off the floor and thumps it once; his head rolls
    toward you, still asleep."""
    breath = _pose(r, lift=1.5, pitch=-1, torso=(6, 0, 0), head=(-6, 0, 0), jaw=10)
    sh = lambda s, a: _pose(r, lift=0.5, roll=9 * s * a, yaw=-3 * s * a, torso=(2, 0, -6 * s * a), head=(0, -10 * s * a, 0), jaw=6,
                            dream=(0, -20 * s * a))
    lift_ = _pose(r, lift=0, torso=(8, 0, 0), head=(2, -6, 0), jaw=4, fist=_fist(x=-2, up=16, z=2), hand="free")
    thump = _pose(r, lift=-1.5, pitch=1, torso=(4, 0, 0), head=(4, -8, 0), jaw=3, fist=_fist(x=-1, z=1))
    return Move("wake", "NONE", 10, 24, 16, through=thump, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, breath)], "active": [(0.14, sh(1, 1.0)), (0.28, sh(-1, 0.85)), (0.42, sh(1, 0.65)), (0.56, sh(-1, 0.45)),
                                                               (0.68, sh(1, 0.2)), (0.82, lift_), (1.0, thump)],
                        "recover": [(0.5, _pose(r, jaw=3, head=(1, -3, 0)))]}, lag=_lag())


def moves(r):
    return [stomp(r), stomp(r, 1, "stomp_b"), fist(r), kick(r), charge(r), eyelids(r), terrors(r), dreamstep(r), sleepwalkers(r), awaken(r), wake(r)]


# the ground (SPEC §35.10): the claws and the fist stay out of the floor
LEG_GROUND = [([f"foot{k}", f"shin{k}"], [(f"thigh{k}", 0, 30), (f"shin{k}", 0, 40)]) for k in range(4)]
# the feet that stand through each move, and whether the fist lies still on the floor through it
STANDS = {"stomp": ((1, 2, 3), True), "stomp_b": ((0, 2, 3), True), "fist": ((0, 1, 2, 3), False), "kick": ((0, 1), True),
          "charge": ((), False), "eyelids": ((0, 1, 2, 3), False), "terrors": ((), False), "dreamstep": ((0, 1, 2, 3), True),
          "sleepwalkers": ((0, 1, 2, 3), False), "awaken": ((2, 3), False), "wake": ((0, 1, 2, 3), False)}
WHERE = {"terrors": {k: _foot(k, dx=LEGS[k][0] * 3) for k in range(4)}}


def clips(r):
    _idle(r)
    _locomotion(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        stage_clips(r, m)
        legs, still = STANDS[m.id]
        if legs or still:
            _plant(r, core.clip_name(m), legs, WHERE.get(m.id), fist=still)
        if getattr(m, "_curtains", None):
            _curtains(r, core.clip_name(m), [(_staged_at(m, st, f), s) for st, f, s in m._curtains])
    own = {core.clip_name(m) for m in mv if m.ground}
    everyday = [n for n in r.clips if n != "death"]
    core.ground_pass(r, LEG_GROUND, clips=everyday)
    core.ground_pass(r, FIST_GROUND, clips=[n for n in everyday if n not in own])
    core.ground_pass(r, FIST_GROUND, clips=["death"], skip=())
    return mv


# Its sounds (SPEC §35.8): old wood creaking and banging, cloth, a snore, phantoms; chosen by ear on paper, not yet heard
# in game.
SOUNDS = {
    "tell_light": [("block.wooden_trapdoor.close", 0.9, 0.7), ("block.wool.step", 1.0, 0.8)],
    "tell_heavy": [("entity.fox.sleep", 1.4, 0.5), ("block.wooden_door.close", 1.0, 0.6)],
    "tell_perilous": [("entity.warden.heartbeat", 2.0, 0.6), ("entity.phantom.ambient", 1.6, 0.6), ("entity.fox.sleep", 1.4, 0.45)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 0.7), ("item.armor.equip_leather", 1.2, 0.7)],
    "swing_heavy": [("entity.phantom.swoop", 1.4, 0.7), ("block.wool.place", 1.2, 0.6)],
    "cast": [("entity.phantom.ambient", 1.4, 0.7), ("entity.illusioner.mirror_move", 1.2, 0.7)],
    "impact": [("entity.zombie.attack_wooden_door", 1.6, 0.6), ("block.wood.hit", 1.4, 0.6), ("item.mace.smash_ground", 1.2, 0.7)],
    "roar": [("entity.ghast.scream", 2.2, 0.5), ("entity.zombie.break_wooden_door", 1.4, 0.6)],
    "step": [("block.wood.step", 1.0, 0.6), ("block.ladder.step", 0.8, 0.7)],
}
MOVE_SOUNDS = {"wake": {"swing": "", "impact": "impact"}, "terrors": {"swing": "cast"}, "eyelids": {"swing": "cast", "impact": ""},
               "dreamstep": {"swing": "cast", "impact": ""}, "sleepwalkers": {"swing": "cast", "impact": ""}, "awaken": {"swing": "roar"}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "stomp", "weight": 3, "when": "near", "then": [["stomp_b"], ["stomp_b", "fist"]]},
            {"move": "fist", "weight": 3, "when": "near"},
            {"move": "eyelids", "weight": 2},
            {"move": "kick", "weight": 3, "when": "behind"},
            {"move": "charge", "weight": 2, "when": "far", "then": [["stomp"]]},
        ]},
        {"transition": "terrors", "pool": [
            {"move": "stomp", "weight": 3, "when": "near", "then": [["stomp_b"], ["stomp_b", "fist"]]},
            {"move": "fist", "weight": 3, "when": "near"},
            {"move": "eyelids", "weight": 1},
            {"move": "terrors", "weight": 1},
            {"move": "dreamstep", "weight": 2, "when": "far", "then": [["fist"]]},
            {"move": "charge", "weight": 2, "when": "far", "then": [["stomp"]]},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
        {"transition": "awaken", "pool": [
            {"move": "awaken", "weight": 2},
            {"move": "sleepwalkers", "weight": 1},
            {"move": "fist", "weight": 3, "when": "near", "then": [["stomp"]]},
            {"move": "stomp", "weight": 3, "when": "near", "then": [["stomp_b", "fist"]]},
            {"move": "dreamstep", "weight": 2, "when": "far", "then": [["fist"]]},
            {"move": "charge", "weight": 2, "when": "far", "then": [["stomp", "stomp_b"]]},
            {"move": "eyelids", "weight": 1},
            {"move": "kick", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.2, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where its parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("torso", (0, 0, 0)), ("arm_r", (0, 0, 0)), ("hand_r", (0, 0, 0)), ("hand_r", (0, 7, 0)), ("head", (0, -6, -6)),
                 ("head", (0, -12, 0)), ("pom", (0, -5, 0)), ("foot0", (0, 5, 0)), ("foot2", (0, 5, 0)), ("shin0", (0, 0, 0)),
                 ("hoop", (0, 6, 0)), ("knob0", None)):
        if p is None:
            continue
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
