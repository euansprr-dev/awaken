"""The Mirror Knight (SPEC §35.11, docs/bosses/ROLLOUT.md): the Mirror Palace's Lord. The Masked Duelist, picked from
his silhouette round: a tall fencer in a royal blue doublet over a skirt of mirror plates, a wide black cavalier hat
pinned up on one side under a white plume, and for a face an oval mirror with two thin slits in it. A rapier with a
swept gilt hilt in his right hand and a parrying dagger in his left; the only fencer in the roster.

Royal blue with silver braid across the breast, a white lace collar falling over the shoulders and a baldric from his
right shoulder to his left hip that holds an empty scabbard; dark breeches, tall bucket-top boots, buff gloves with
flared cuffs, his black hair tied back in a queue with a ribbon. The mirrors (the mask and the plates of his skirt) are
the brightest thing on him; the hat and the boots the darkest.

He stands en garde: side-on, his right foot toward you and the left turned out behind, knees bent, the rapier's point
at your eyes and the dagger up before his chest. That is his rest pose: every clip starts and ends in it. He walks in
a fencer's advance (the front foot reaches, the back one follows; they never cross) and runs with the blade out ahead.

Moves (SPEC §35.10): the Quick Thrust (a straight thrust with a step, LIGHT) and the Cutover after it (the point goes
up and over and cuts down across, LIGHT), the Feint (the point held high over his head, then a deep low thrust: the
held HEAVY), the Mirror Lunge (a running attack across the room with the blade straight out), the Perfect Parry (both
blades crossed in a high bind before his mask) and the Riposte from it (a rising backhand cut), the Blind Thrust for
whoever is behind him (he looks back over his shoulder and thrusts back under his own arm), the Mirror Split (arms
flung wide, blade and dagger out like a mirror's wings, a stamp: his reflections step out), Through the Glass (he
draws in small and slips sideways, as through a pane) and Shatter (the signature: the rapier raised point-down in both
hands over his hat and driven into the floor on one knee). He opens with a fencer's salute. He dies going down on one
knee, his blade's point on the floor, and tipping over onto his side, the mask falling from his face last.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "mirror_knight"
SCALE = 1.9  # the hitbox on the MIMIC model: 1.1 x 3.4 blocks (to the hat's crown); the body is modelled at true size

P = plans.Biped(leg=24, thigh=12, foot=(4, 2, 7), pelvis=(9, 4, 6), spine=(7, 6, 5), chest=(12, 10, 7), neck=(3, 2, 3),
                head=(7, 8, 7), arm=(3, 10, 3), forearm=(3, 8, 3), hand=(3, 4, 3), stance=2.5, shoulder=6.5, leg_w=(4, 3))
DROP = 2.5  # en garde: his hips sit this much lower than straight legs would put them
TURN = (-40, -15, 22, 28)  # the stance's turns about the vertical: hips, chest, neck, head (his right side to you, his face to you)
FOOT = {"r": (-3.0, 2.0, -10.0), "l": (4.0, 2.0, 9.0)}  # the ankles en garde (x, height, z): the right foot forward
TOE = {"r": (1, 0, 0), "l": (0.34, 0, 0.94)}  # each foot's +x: the front foot points at you, the back one is turned out
GRIP = {"r": (-4.0, 31.0, -15.0), "l": (5.0, 33.0, -7.0)}  # the hands en garde (x, height, z)
POLE = {"r": (-0.6, 0.6, 0.4), "l": (0.7, 0.6, 0.3)}  # the elbows: down and out
BLADE = 30.5  # the rapier's tip from the hilt's middle (its bone)
DAGGER = 13.0
TIP = (0, BLADE, 0)
REST_HAND = {"r": ((0.12, 0.15, -1), (0, -1, 0)), "l": ((0.1, -0.7, -0.7), (1, 0, 0))}  # the blades' lines en garde
ARMS = tuple(f"{b}_{s}" for b in ("arm", "forearm", "hand") for s in "lr")
ON_TIME = {b: 0 for b in ARMS}  # his hands land where they're sent: a fencer's point is never late
MASK = (0, -4, -5)  # the mirror's middle, in the head's space


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg + DROP, 0), (0, TURN[0], 0))
    pel.cube("pelvis", -4.5, -1, -3, 9, 4, 6)
    # long legs in dark breeches into tall bucket-top boots; the mirror plates of his skirt hang from the thighs (front,
    # outside, back) so they kick with the knees, the lower row on a spring
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0))
        th.cube("thigh_" + nm, -2, -1, -2, 4, 12, 4)
        xs = 2.5 if side > 0 else -3.5
        th.cube("panf_" + nm, -2.5, -1.5, -3, 5, 6, 1)
        th.cube("pans_" + nm, xs, -1.5, -2.5, 1, 6, 5)
        th.cube("panb_" + nm, -2.5, -1.5, 2, 5, 6, 1)
        sk = th.bone("skirt_" + nm, (0, 4.5, 0), (0, 0, -side * 4))
        sk.cube("skf_" + nm, -2.5, 0, -3.4, 5, 4, 1)
        sk.cube("sks_" + nm, xs + side * 0.4, 0, -2.5, 1, 4, 5)
        sk.cube("skb_" + nm, -2.5, 0, 2.4, 5, 4, 1)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -1.5, 0, -1.5, 3, 10, 3)
        sn.cube("boot_" + nm, -2, 3, -2, 4, 7, 4)
        sn.cube("bucket_" + nm, -3, -0.5, -3, 6, 3, 6)  # the boot's top turned down below the knee, flaring
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -2, 0, -5, 4, 2, 7)
        ft.cube("toe_" + nm, -1.5, 1, -6, 3, 1, 1)
        ft.cube("heel_" + nm, -1.5, 1.5, 1, 3, 1, 1)
    # the doublet's waist, a belt with a silver buckle, and on his left hip the frog that carries the empty scabbard
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -3.5, -6, -2.5, 7, 6, 5)
    sp.cube("belt", -4, -2, -3, 8, 2, 6)
    sp.cube("buckle", -1, -2.5, -3.6, 2, 2, 1)
    sp.cube("frog", 3.5, -2.5, -1, 1, 3, 2)
    sc = sp.bone("scabbard", (4.4, -1.5, 0), (62, 0, 8))  # hung back and down from the frog (it trails him)
    sc.cube("scabbard", -1, 0, -1, 2, 24, 2)
    sc.cube("locket", -1.5, 0, -1.5, 3, 3, 3)
    sc.cube("chape", -1.5, 22, -1.5, 3, 3, 3)
    # the doublet: a peascod front, silver braid across the breast, a white lace collar falling over the shoulders
    # with its points on his breast
    ch = sp.bone("chest", (0, -6, 0), (0, TURN[1], 0))
    ch.cube("chest", -6, -10, -3.5, 12, 10, 7)
    ch.cube("peascod", -3, -5, -4.3, 6, 5, 1)
    ch.cube("collar", -6.5, -10.5, -4.5, 13, 2, 9)
    ch.cube("collar_f", -3.5, -9, -4.7, 7, 3, 1)
    nk = ch.bone("neck", (0, -10, -0.5), (0, TURN[2], 0))
    nk.cube("neck", -1.5, -2, -1.5, 3, 2, 3)
    # the head: black hair drawn back into a queue tied with a blue ribbon; for a face an oval mirror with two slits
    hd = nk.bone("head", (0, -2, 0), (0, TURN[3], 0))
    hd.cube("head", -3.5, -8, -3.5, 7, 8, 7)
    mk = hd.bone("mask", (0, -4, -4.4))  # on its own bone: it falls from his face when he dies
    mk.cube("mask0", -3, -2.5, 0.2, 6, 5, 1)
    mk.cube("mask1", -2, -3.5, -0.2, 4, 7, 1)
    mk.cube("eye_l", 0.4, -1, -0.4, 2, 1, 1)
    mk.cube("eye_r", -2.4, -1, -0.4, 2, 1, 1)
    qu = hd.bone("queue", (0, -3.5, 3.4), (24, 0, 0))
    qu.cube("queue", -1, 0, -1, 2, 7, 2)
    qu.cube("ribbon", -2, 0.5, 0.2, 4, 2, 1)
    qu.cube("ribbon_t", -1.5, 2.5, 0.4, 1, 4, 1)
    # the hat: a wide black brim pinned up on his left with a silver brooch, a tall crown with a blue band, and a long
    # white plume rising from the brooch and curling back over the crown
    ht = hd.bone("hat", (0, -7.5, 0), (-3, 0, 5))
    ht.cube("brim", -8, -1, -7, 12, 1, 14)
    up = ht.bone("brim_up", (4, -0.5, 0), (0, 0, -62))
    up.cube("brim_up", 0, -0.5, -6, 5, 1, 12)
    ht.cube("crown", -3.5, -5, -3.5, 7, 4, 7)
    ht.cube("crown_top", -3, -6, -3, 6, 1, 6)
    ht.cube("band", -4, -2, -4, 8, 1, 8)
    ht.cube("brooch", 3.6, -4, -1.5, 2, 2, 2)
    pl = ht.bone("plume0", (4.5, -4.5, -0.5), (-50, 0, -12))  # out of the brooch, sweeping up and back over the brim
    pl.cube("plume0", -3, -6, -1, 6, 6, 2)
    for k, (rot, w, d) in enumerate((((-24, 0, 4), 6, 2), ((-26, 0, 4), 5, 2), ((-24, 0, 0), 4, 1), ((-20, 0, 0), 3, 1)), start=1):
        pl = pl.bone(f"plume{k}", (0, -5.6, 0), rot)
        pl.cube(f"plume{k}", -w / 2, -6, -d / 2, w, 6, d)
    # the arms in slashed sleeves under puffed shoulders; buff gloves with flared cuffs
    for side, nm in ((1, "l"), (-1, "r")):
        arm = ch.bone("arm_" + nm, (side * P.shoulder, -8.5, 0))
        arm.cube("puff_" + nm, -2.5, -1.5, -2.5, 5, 4, 5)
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 10, 3)
        fa = arm.bone("forearm_" + nm, (0, 9.5, 0))
        fa.cube("forearm_" + nm, -1.5, 0, -1.5, 3, 8, 3)
        fa.cube("cuff_" + nm, -2, 5, -2, 4, 3, 4)
        hnd = fa.bone("hand_" + nm, (0, 8, 0))
        hnd.cube("hand_" + nm, -1.5, 0, -1.5, 3, 4, 3)
    # the hands en garde, then the blades along their lines
    fr = core.frames(r, {})
    for s in "rl":
        ik.rest_limb(r, "arm_" + s, "forearm_" + s, "hand_" + s, GRIP[s], POLE[s])
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["hand_" + s].rot = ik.orient(r, fr, "hand_" + s, *REST_HAND[s])
    # the rapier: a cup and swept rings in gilt, long quillons, a knuckle bow along the fist, a blade tapering from a
    # broad forte to a needle
    rp = r.by_name["hand_r"].bone("rapier", (0, 3.5, 0))
    rp.cube("cup", -2, 0, -2, 4, 1, 4)
    rp.cube("quillon", -4, 1, -0.5, 8, 1, 1)
    rp.cube("qtip_l", 3.5, 0, -0.5, 1, 1, 1)
    rp.cube("qtip_r", -4.5, 2, -0.5, 1, 1, 1)
    rp.cube("ring_l", 1.5, 1, -0.5, 1, 2, 1)
    rp.cube("ring_r", -2.5, 1, -0.5, 1, 2, 1)
    rp.cube("bow", -0.5, -3.5, -2.4, 1, 4, 1)
    rp.cube("forte", -1, 1.5, -0.5, 2, 7, 1)
    rp.cube("blade0", -0.5, 8.5, -0.5, 1, 11, 1)
    rp.cube("blade1", -0.5, 19.5, -0.5, 1, 11, 1)
    # the parrying dagger: a shell over the fist, long quillons swept toward the point, a side ring, a broad blade
    dg = r.by_name["hand_l"].bone("dagger", (0, 3.5, 0))
    dg.cube("shell", -2, 0, -1.5, 4, 1, 3)
    dg.cube("dquil", -3.5, 1, -0.5, 7, 1, 1)
    dg.cube("dqtip_l", 3, 2, -0.5, 1, 2, 1)
    dg.cube("dqtip_r", -4, 2, -0.5, 1, 2, 1)
    dg.cube("dring", -0.5, 0.5, -2.4, 1, 2, 1)
    dg.cube("dforte", -1, 2, -0.5, 2, 5, 1)
    dg.cube("dblade", -0.5, 7, -0.5, 1, 6, 1)
    # the feet where they stand en garde, flat on the floor (the right toward you, the left turned out)
    fr = core.frames(r, {})
    for s in "rl":
        ik.rest_limb(r, "thigh_" + s, "shin_" + s, "foot_" + s, FOOT[s], (0, 0, -1))
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["foot_" + s].rot = ik.orient(r, fr, "foot_" + s, (0, 1, 0), TOE[s])
    # and the legs' rest taken from the planter's own solution for those feet (the thigh without a twist), so that a
    # planted key and the rest are the same legs and nothing swings between them
    p = core.plant(r, {}, ("l", "r"))
    for s in "lr":
        for b in ("thigh_", "shin_", "foot_"):
            bn = r.by_name[b + s]
            bn.rot = tuple(bn.rot[i] + p[b + s][i] for i in range(3))
    r.look = [("head", 0.5, 0.45), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.7
    # secondary parts (SPEC §35.10): the plume's feathers swing one after another, the skirt's lower plates and the
    # scabbard trail him, the queue and its ribbon swing
    r.springs = [(f"plume{k}", 0.55 + 0.1 * k, 2.4, 0.16) for k in range(1, 5)] + [(f"skirt_{s}", 0.45, 2.6, 0.3) for s in "lr"] \
        + [("scabbard", 0.5, 1.8, 0.22), ("queue", 0.55, 2.2, 0.22)]
    r.guard = ["rapier", "dagger", "hand_r", "hand_l"]  # the blades never go into the floor
    _REST.clear()
    _REST.update({b.name: tuple(b.rot) for b in r.bones})
    return r


BLUE = Ramp("#070b24", "#0b1233", "#111c48", "#18285e", "#203675", "#2a468c", "#3858a3", "#4c6db8")
SILVER = Ramp("#2a2e36", "#3e434d", "#565c67", "#727985", "#9097a2", "#b0b6bf", "#d0d4da", "#eef0f3")
LACE = Ramp("#5c6068", "#7c8088", "#9ea2a8", "#c0c3c8", "#dcdee2", "#f2f3f5")
FELT = Ramp("#050507", "#0a0a0e", "#101016", "#17171f", "#202029", "#2a2a35")
CLOTH = Ramp("#07070b", "#0d0d13", "#14141c", "#1c1c26", "#262632")
LEATHER = Ramp("#0c0806", "#140e0a", "#1e150f", "#2a1d15", "#37261b", "#463123")
BUFF = Ramp("#3a2c1c", "#4e3c27", "#665034", "#806742", "#9a7f54", "#b49a6c")
HAIR = Ramp("#040408", "#08080e", "#0e0e16", "#161620", "#20202c")
GILT = Ramp("#3a2a0c", "#5a4214", "#7c5e1e", "#a07c2c", "#c49e40", "#e2c060", "#f6e090")
STEEL = Ramp("#2a3038", "#3c444e", "#525c68", "#6c7884", "#8a96a2", "#aab4be", "#ccd4dc", "#eef2f6")
# the mirrors: they show the room (pale above, darker below where the floor is) with a bright streak across
MIRROR = Ramp("#1e2834", "#2e3a48", "#44526a", "#5e6e88", "#7c8ca6", "#9eacc2", "#c2ccdc", "#e4eaf2", "#ffffff")
EYE = ("#3a2a60", "#8a70d8", "#e0d4ff")


def paint(r, P_):
    """Values (SPEC §35.4): the mirrors (the mask, the plates of his skirt) and the blade brightest, then the lace and
    the silver, the royal blue in the middle, the hat, the breeches and the boots the darkest thing on him."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    # the doublet: royal blue with a fine diagonal twill, slashed sleeves showing silver-white beneath
    twill = lambda f, i, j: (0.35 if (i + j) % 3 == 0 else -0.2 if (i - j) % 5 == 0 else 0)
    for c in of("chest", "spine", "peascod", "arm_", "forearm_", "puff_"):
        P_.paint(c, BLUE, 3.6, twill, grad=1.0, rim=0.9, ao=0.8, noise=0.15)
    # the silver braid (frogging) across the breast: bars with a knot in the middle of each, down the front
    for c in ("chest", "peascod"):
        f = P_.face(c, "north")
        for i, j in f.each():
            mid = f.w // 2
            if c == "chest" and j % 3 == 1 and 1 <= j <= f.h - 2 and abs(i - mid + 0.5) <= 4:
                f.set(i, j, SILVER.at(5.6 if abs(i - mid + 0.5) < 1 else 4.6 - 0.3 * abs(i - mid + 0.5) / 4))
            elif c == "chest" and i in (mid - 1, mid) and j % 3 != 1:
                f.set(i, j, BLUE.at(1.6))  # the closing down the front
            elif c == "peascod" and j % 2 == 0:
                f.set(i, j, SILVER.at(4.8 - 0.4 * abs(i - f.w / 2 + 0.5)))
    # the baldric: a dark leather band from his right shoulder down to his left hip, a gilt stud on it
    for c, fname in (("chest", "north"), ("chest", "south"), ("spine", "north")):
        f = P_.face(c, fname)
        for i, j in f.each():
            u = i if fname == "north" else f.w - 1 - i  # the face's x from his right (north faces see him from in front)
            yy = j + (0 if c == "chest" else 10)
            d = u - (yy * 0.75 - 1)
            if 0 <= d < 2.5:
                f.set(i, j, LEATHER.at(3.6 if d < 0.6 else 2.6))
            if c == "chest" and fname == "north" and abs(d - 1.2) < 0.7 and yy == 6:
                f.set(i, j, GILT.at(5.5))
    # the sleeves' slashes: silver-white silk showing through long cuts
    for c in of("arm_", "puff_"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if i % 2 == 0 and 0 < j < f.h - 1 and c.startswith("puff_"):
                    f.set(i, j, LACE.at(3.6 + 0.4 * (j % 2)))
                elif c.startswith("arm_") and i == f.w // 2 and j % 4 != 3:
                    f.set(i, j, LACE.at(3.2))
    # the lace collar: white, scalloped at its edge with a lacy open pattern
    lace = lambda f, i, j: (-1.6 if (i + j) % 4 == 0 else -0.6 if (i * j) % 3 == 1 else 0.3)
    for c in ("collar", "collar_f"):
        P_.paint(c, LACE, 4.0, lace, grad=0.4, rim=0.6, ao=0.3, noise=0.1)
    # the mirror plates of his skirt: each a pane in a silver frame, showing the room
    for c in of("panf_", "pans_", "panb_", "skf_", "sks_", "skb_"):
        _mirror(P_, c, frame=True)
    # the mask: an oval mirror rimmed in silver, two thin dark slits; and a fine crack across one side of it
    for c in ("mask0", "mask1"):
        _mirror(P_, c, frame=False)
    # the mask's face: polished bright, a dark line of the room's floor across its lower part, one clean streak of
    # light from his brow down across the cheek, a silver rim; and a hairline crack
    for c in ("mask0", "mask1"):
        f = P_.face(c, "north")
        g = P_.face(c, "north", glow=True)
        dy = 1 if c == "mask1" else 0  # mask1 stands a pixel higher than mask0
        for i, j in f.each():
            x = i + (1 if c == "mask1" else 0)  # the column across the whole oval (mask0's width)
            y = j - dy
            v = 7.5 - 0.15 * y
            if y == 4:
                v = 5.2  # a faint line where the room's floor meets its wall
            if x - 1 == y or x - 2 == y:
                v = 8.0
                g.set(i, j, MIRROR.at(8.0)[:3] + (120,))
            f.set(i, j, MIRROR.at(v))
        if c == "mask0":
            for i, j in f.each():
                if i in (0, f.w - 1):
                    f.set(i, j, SILVER.at(5.2 if j % 2 else 4.6))
        else:
            for i, j in f.each():
                if j in (0, f.h - 1):
                    f.set(i, j, SILVER.at(5.4))
            for i, j in ((3, 2), (2, 3), (2, 4)):
                f.set(i, j, MIRROR.at(3.0))  # the crack, dark against the polish
    with P_.eyes():
        for c in ("eye_l", "eye_r"):
            P_.paint(c, Ramp("#000000", "#06040c", "#100a20"), 0.6, rim=0.0, ao=0.0)
            g = P_.face(c, "north", glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(EYE[1] if i == (1 if c == "eye_l" else 0) else EYE[0], 200))
    # the hat: black felt, the brim's edge bound, a blue band, the brooch silver set with a little mirror
    felt = lambda f, i, j: (0.4 if R._h(f.cube.name + f.name, i * 5 + j * 3) % 7 == 0 else 0)
    for c in ("brim", "brim_up", "crown", "crown_top"):
        P_.paint(c, FELT, 2.6, felt, grad=0.6, rim=1.0, ao=0.7, noise=0.15)
    for c in ("brim", "brim_up"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, FELT.at(4.6))
    P_.paint("band", BLUE, 4.2, lambda f, i, j: 1.0 if j == 0 else 0, rim=0.6, noise=0.1)
    P_.paint("brooch", SILVER, 5.0, lambda f, i, j: 2.4 if (i, j) == (1, 0) else 0, rim=1.0)
    # the plume: white ostrich feathers, a quill down the middle, the barbs fanning out and greying to their tips
    for k in range(5):
        c = f"plume{k}"
        P_.paint(c, LACE, 5.0 - 0.2 * k, lambda f, i, j: (0.6 if i == f.w // 2 else -1.2 if (i + 2 * j) % 4 == 0 else 0.2),
                 grad=0.5, rim=0.6, ao=0.2, noise=0.08)
    # the hair, the queue and its ribbon
    P_.paint("head", HAIR, 2.6, lambda f, i, j: 0.8 if f.name != "north" and (i + 3 * j) % 4 == 0 else 0, rim=0.8, noise=0.2)
    P_.paint("queue", HAIR, 2.6, lambda f, i, j: 0.8 if j % 2 == 0 and i % 2 == 0 else 0, rim=0.6)
    for c in ("ribbon", "ribbon_t"):
        P_.paint(c, BLUE, 4.6, lambda f, i, j: -0.8 if i == f.w // 2 else 0, rim=0.8)
    P_.paint("neck", HAIR, 1.4)
    # the breeches, the boots and the belt: black cloth, black leather polished at the shin, buff gloves
    for c in of("thigh_", "pelvis"):
        P_.paint(c, CLOTH, 2.4, lambda f, i, j: 0.5 if i % 3 == 0 else 0, grad=0.8, rim=0.6, ao=0.8, noise=0.15)
    for c in of("shin_", "boot_", "bucket_", "foot_", "toe_", "heel_"):
        P_.paint(c, LEATHER, 2.8, lambda f, i, j: (1.4 if f.name in SIDES and i == 1 and c.startswith("boot_") else
                                                   -0.6 if c.startswith("bucket_") and j == 0 else 0), grad=1.0, rim=1.0, ao=0.6, noise=0.15)
    for c in ("belt", "frog", "scabbard"):
        P_.paint(c, LEATHER, 2.6 if c != "scabbard" else 2.2, lambda f, i, j: 0.6 if j % 4 == 0 else 0, rim=0.8, noise=0.15)
    for c in ("buckle", "locket", "chape"):
        P_.paint(c, SILVER, 4.6, lambda f, i, j: 1.0 if (i + j) % 2 == 0 else 0, rim=1.0)
    for c in of("hand_", "cuff_"):
        P_.paint(c, BUFF, 3.0, lambda f, i, j: (-0.8 if c.startswith("cuff_") and j == f.h - 1 else 0.4 if (i + j) % 3 == 0 else 0),
                 rim=0.8, ao=0.5, noise=0.15)
    # the blades: steel bright along their edges, a darker fuller down the forte; the hilts gilt
    for c in ("forte", "blade0", "blade1", "dforte", "dblade"):
        P_.paint(c, STEEL, 5.4, lambda f, i, j: (1.4 if f.name in ("east", "west") else -1.0 if c in ("forte", "dforte") and i == 0 and
                                                 f.name in ("north", "south") else 0.4), rim=1.2, ao=0.0, noise=0.05)
    for c in ("cup", "quillon", "qtip_l", "qtip_r", "ring_l", "ring_r", "bow", "shell", "dquil", "dqtip_l", "dqtip_r", "dring"):
        P_.paint(c, GILT, 4.4, lambda f, i, j: 1.2 if (i + j) % 3 == 0 else -0.4 if (i + j) % 3 == 1 else 0, rim=1.2, ao=0.3)
    return P_.skin, P_.glow


def _mirror(P_, c, frame=True, streak=0.0):
    """A mirror: a clean pane showing the room (bright above, a little darker toward the floor), one sharp streak of
    light across it that also glints in the glow layer; frame: a silver rim round each face."""
    for fname in ("north", "south", "east", "west", "top", "bottom"):
        f = P_.face(c, fname)
        g = P_.face(c, fname, glow=True)
        off = R._h(c + fname, 3) % 3
        for i, j in f.each():
            v = 7.3 - 1.6 * j / max(1, f.h - 1)
            if fname in SIDES and i - j == off - 1:
                v = 8.0
                g.set(i, j, MIRROR.at(8.0)[:3] + (130,))
            if frame and (i in (0, f.w - 1) or j in (0, f.h - 1)) and fname in SIDES:
                f.set(i, j, SILVER.at(4.6 if (i + j) % 2 else 4.0))
            elif fname in ("top", "bottom"):
                f.set(i, j, SILVER.at(4.2))
            else:
                f.set(i, j, MIRROR.at(min(8.0, v + streak)))


# ---- motion ----------------------------------------------------------------------------------------------------------

UP = (0, -1, 0)  # a hand's +x turned up (the knuckles of a sword hand in tierce)


def _abs(r, bone, rot):
    """A key for a bone given as its whole turn (the rest taken off: a pose holds what it adds to the rest)."""
    rest = r.by_name[bone].rot
    return tuple(rot[i] - rest[i] for i in range(3))


def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), right=None, left=None,
          rblade=None, lblade=None, legs=None, feet=("l", "r"), step=None, turn=None, more=None):
    """A whole-body key over the en garde rest. He turns at the hips (hips: turn, at: x, up, z), the spine, the chest, the
    neck and the head. The feet stay where they stand en garde (step: {side: (dx, dy, dz)} model pixels, y down) unless
    legs pose them (whole turns). right, left: where that hand goes, (x, height, z) model pixels or ("on", bone, point);
    rblade, lblade: (ydir, xdir) the line of that hand's blade (model directions, y down)."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    if legs:
        p.update({b: _abs(r, b, v) for b, v in legs.items()})
        feet = tuple(s for s in feet if "thigh_" + s not in legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    for side, o in (("l", lblade), ("r", rblade)):
        if o:
            fr = core.frames(r, core.as_sample(p))
            p["hand_" + side] = ik.orient(r, fr, "hand_" + side, *o)
    return p


FLIP = False
_REST = {}  # each bone's rest turn (set by body()): the same turn has two Euler triples, and which is nearer depends on it


def _near(bone, v, ref):
    """The key v for a bone (degrees over its rest) written as near as it goes to ref: each angle by whole turns, and
    the other Euler triple of the same turn when that one is nearer. IK solves each key on its own, so one key may
    say +145 where the next says -162 for nearly the same arm; between them the clip would spin the long way round."""
    rest = _REST.get(bone, (0.0, 0.0, 0.0))
    f = tuple(rest[i] + v[i] for i in range(3))
    best, bd = None, None
    for g in ((f,) if not FLIP else (f, (f[0] + 180, 180 - f[1], f[2] + 180))):
        w = tuple(g[i] - rest[i] for i in range(3))
        w = tuple(w[i] + 360 * round((ref[i] - w[i]) / 360) for i in range(3))
        d = sum(abs(w[i] - ref[i]) for i in range(3))
        if bd is None or d < bd - 1e-6:
            best, bd = w, d
    return best


def _chain(keys, start=None):
    """Keys [pose] rewritten in order, each bone's turn as near as it goes to the key before (from rest, or start)."""
    prev = dict(start or {})
    out = []
    for p in keys:
        q = {}
        for b, v in p.items():
            if isinstance(v, dict):
                q[b] = v
                continue
            q[b] = _near(b, v, prev.get(b, (0.0, 0.0, 0.0)))
            prev[b] = q[b]
        out.append(q)
    return out


def _unwrap(mv):
    """A move's keys in the order its clip plays them (the wind-up, the strike, the follow-through, the recovery),
    each as near as it goes to the one before."""
    w, a, rc = mv.stages.get("windup", []), mv.stages.get("active", []), mv.stages.get("recover", [])
    seq = [p for _, p in w] + [p for _, p in a] + ([mv.through] if mv.through is not None else []) + [p for _, p in rc]
    fixed = _chain(seq)
    k = 0
    for st, ks in (("windup", w), ("active", a)):
        mv.stages[st] = [(f, fixed[k + i]) for i, (f, _) in enumerate(ks)]
        k += len(ks)
    if mv.through is not None:
        mv.through = fixed[k]
        k += 1
    mv.stages["recover"] = [(f, fixed[k + i]) for i, (f, _) in enumerate(rc)]
    return mv


def _keyed(r, name, length, keys, loop=False):
    """keyed() with the keys unwrapped in order."""
    ps = _chain([p for _, p in keys])
    keyed(r, name, length, [(t, p) for (t, _), p in zip(keys, ps)], loop=loop)


def _mix(a, b, f):
    """Between two keys (the bones of either), f of the way from a to b (b's turns taken as near as they go to a's)."""
    out = {}
    for k in set(a) | set(b):
        va, vb = a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0))
        ra = va["rot"] if isinstance(va, dict) else va
        rb = vb["rot"] if isinstance(vb, dict) else vb
        if not isinstance(va, dict) and not isinstance(vb, dict):
            rb = _near(k, rb, ra)
        rot = tuple(x + (y - x) * f for x, y in zip(ra, rb))
        if isinstance(va, dict) or isinstance(vb, dict):
            pa = va.get("pos", (0, 0, 0)) if isinstance(va, dict) else (0, 0, 0)
            pb = vb.get("pos", (0, 0, 0)) if isinstance(vb, dict) else (0, 0, 0)
            out[k] = {"rot": rot, "pos": tuple(x + (y - x) * f for x, y in zip(pa, pb))}
        else:
            out[k] = rot
    return out


def _back(r, end, settle=None):
    """A recovery in two steps to en garde (or to settle), so nothing snaps home."""
    home = settle or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _on(r, end, f=0.1):
    """The follow-through: the end carried on a little past where it was going (away from rest)."""
    return _mix(end, _pose(r), -f)


def _ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def _rest_feet(r):
    fr = core.frames(r, {})
    return {s: fr["foot_" + s][1] for s in "lr"}


def _advance_feet(u, D):
    """The fencer's advance at phase u (0..1): the front foot reaches first (toes lifted), then the back one follows low;
    they never cross. Model offsets from en garde (y down), with the body travelling D pixels a cycle."""
    out = {}
    for s, (a, b, z0, lift) in (("r", (0.0, 0.34, 0.34, 2.6)), ("l", (0.5, 0.8, -0.15, 1.2))):
        e = _ease((u - a) / (b - a))
        k = (u - a) / (b - a)
        dy = -lift * math.sin(math.pi * k) if 0 < k < 1 else 0.0
        out[s] = (0.0, dy, (z0 - e + u) * D)
    return out


def _run_feet(r, u, D, lift=6.0, stance=0.38):
    """A running stride at phase u (the right foot planting at 0, the left at 0.5): each foot planted for a share of
    the cycle, sliding back under him, then swung forward through the air. Offsets from en garde (model px, y down),
    the feet back under the squared hips."""
    rest = _rest_feet(r)
    out = {}
    half = stance * D / 2
    for s, ph, x in (("r", 0.0, -2.6), ("l", 0.5, 2.6)):
        v = (u - ph) % 1.0
        if v < stance:
            z, dy = -half + 2 * half * v / stance, 0.0
        else:
            k = (v - stance) / (1 - stance)
            z, dy = half - 2 * half * _ease(k), -lift * math.sin(math.pi * k)
        out[s] = (x - rest[s][0], dy, z - rest[s][2])
    return out


def idle(r):
    """En garde: a fencer's light bounce in the knees, the point drawing slow circles at your eyes, the head tilting to
    look you over, and now and then a sharp beat of the blade (a flick of the wrist), then still again."""
    k0 = _pose(r)
    tip = lambda rx, rz, y: _pose(r, at=(0, y, 0), chest=(0.6 * (y < 0), 0, 0), more={"hand_r": (rx, 0, rz), "hand_l": (-rz * 0.3, 0, rx * 0.3)})
    tilt = _pose(r, at=(0, -0.5, 0), neck=(0, 3, 0), head=(-3, 6, 9), more={"hand_r": (2, 0, -2)})
    beat = _pose(r, at=(0, -0.7, 0), chest=(0, -2, 0), more={"hand_r": (-12, 0, 6)})
    _keyed(r, "idle", 5.2, [(0, k0), (0.65, tip(4, 3, -0.7)), (1.3, tip(-1, 5, 0.2)), (1.95, tip(-5, -1, -0.7)), (2.6, tip(1, -4, 0.2)),
                           (3.3, tilt), (3.9, _mix(tilt, k0, 0.5)), (4.1, beat), (4.3, _mix(beat, k0, 0.7)), (4.8, tip(1, 1, -0.4)), (5.2, k0)],
          loop=True)


def walk(r):
    """The fencer's advance: the front foot reaches with its toes lifted and lands heel first, the back foot follows
    low; the body glides level, side-on, the point never leaving you."""
    r.walk_stride = 1.15  # blocks a cycle (one step of each foot)
    D = r.walk_stride * 16
    T = 0.8
    keys = []
    n = 16
    for k in range(n + 1):
        u = k / n
        st = _advance_feet(u, D)
        bob = -0.6 * math.sin(2 * math.pi * u) ** 2
        keys.append((T * u, _pose(r, at=(0, bob, 0), hips=(0, 2 * math.sin(2 * math.pi * u), 0), chest=(1, -1.5 * math.sin(2 * math.pi * u), 0),
                                  step=st, more={"hand_r": (1.5 * math.sin(2 * math.pi * u), 0, 0)})))
    _keyed(r, "walk", T, keys, loop=True)


def run(r):
    """The run: the hips squared to where he's going, the blade carried out ahead of him, the dagger arm pumping."""
    r.run_stride = 2.6
    D = r.run_stride * 16
    T = 0.62
    keys = []
    n = 12
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(4 * math.pi * u)
        keys.append((T * u, _pose(r, hips=(8, 32 + 4 * s, 2 * s), at=(0, -1.6 + 1.1 * c, -1), spine=(3, 0, 0), chest=(6, 4 - 4 * s, 0),
                                  neck=(0, -10, 0), head=(-8, -26 + 3 * s, 0), right=(-3, 33 + 1.2 * c, -24), rblade=((0.02, 0.05, -1), UP),
                                  left=(8, 31 - 3 * s, -4 + 9 * s), lblade=((0.2, 0.3, -0.9), (1, 0, 0)), step=_run_feet(r, u, D))))
    _keyed(r, "run", T, keys, loop=True)


def turns(r):
    """Turning on the spot: small steps of the front foot and then the back, the stance kept."""
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        a = _pose(r, hips=(0, 5 * sgn, 0), at=(0, -0.4, 0), step={"r": (-1.5 * sgn, -2, -1)})
        b = _pose(r, hips=(0, 3 * sgn, 0))
        c = _pose(r, hips=(0, 5 * sgn, 0), at=(0, -0.4, 0), step={"l": (1.5 * sgn, -1.5, 1)})
        _keyed(r, nm, 0.44, [(0, _pose(r)), (0.11, a), (0.22, b), (0.33, c), (0.44, _pose(r))], loop=True)


# the legs kneeling (whole turns, the hips squared to the front): down on his left knee, the right foot planted ahead
KNEEL = {"thigh_l": (6, 0, -4), "shin_l": (88, 0, 0), "foot_l": (-30, 0, 0), "thigh_r": (-78, 0, 4), "shin_r": (80, 0, 0), "foot_r": (-2, 0, 0)}


def reactions(r):
    """The kneel when his stance breaks: down on his left knee with the rapier's point on the floor like a cane, his
    left hand on his knee; the rise pushes up off it. The death: struck, he staggers back, his knee goes, he leans on
    the blade, it slides, and he tips over onto his side; the hat rolls off and last of all the mask falls from his
    face."""
    cane = dict(right=(-3, 31.5, -13), rblade=((0, 1, -0.08), UP))
    kneel = _pose(r, hips=(8, 40, 0), at=(0, -9, -1), legs=KNEEL, spine=(8, 0, 0), chest=(10, 6, 0), neck=(4, -10, 0), head=(14, -18, 0),
                  left=("on", "thigh_r", (0, 9, -3)), lblade=((0.2, -0.6, 0.8), (1, 0, 0)), **cane)
    sag = _pose(r, hips=(4, 16, 0), at=(0, -4, 0), chest=(6, 0, 0), head=(8, 0, 0), right=(-3, 26, -14), rblade=((0, 0.8, -0.6), UP))
    _keyed(r, "kneel", 0.6, [(0, _pose(r)), (0.25, sag), (0.6, kneel)])
    push = _pose(r, hips=(6, 30, 0), at=(0, -5, -0.5), legs=None, chest=(6, 4, 0), head=(4, -10, 0), step={"r": (0, 0, -2)}, **cane)
    _keyed(r, "rise", 0.8, [(0, kneel), (0.4, push), (0.8, _pose(r))])
    stag = _pose(r, hips=(-6, -6, 0), at=(0, 0, 2.5), chest=(-12, -8, 0), head=(-16, 6, 0), right=(-8, 30, -8), rblade=((0.2, 0.2, -1), UP),
                 left=(10, 36, 0), step={"r": (0, 0, 3)})
    buckle = _pose(r, hips=(4, 20, 0), at=(0, -5, 0.5), chest=(6, 0, 0), head=(10, -6, 0), right=(-3, 32, -13), rblade=((0, 1, -0.1), UP))
    lean = _pose(r, hips=(10, 40, 0), at=(0, -9.5, -1.5), legs=KNEEL, spine=(10, 0, 0), chest=(14, 4, 0), neck=(6, -10, 0), head=(20, -18, 0),
                 left=("on", "thigh_r", (0, 9, -3)), lblade=((0.2, -0.6, 0.8), (1, 0, 0)), right=(-3, 31, -13), rblade=((0, 1, -0.1), UP))
    slide = _pose(r, hips=(14, 40, 10), at=(0, -10.5, -2), legs=KNEEL, spine=(12, 0, 6), chest=(16, 4, 8), neck=(6, -10, 0), head=(16, -14, 10),
                  left=(10, 8, -8), lblade=((0.4, 0.7, -0.6), (1, 0, 0)), right=(-6, 10, -18), rblade=((-0.4, 0.05, -0.9), UP))
    side = {"thigh_l": (-30, 0, -6), "shin_l": (60, 0, 0), "foot_l": (-20, 0, 0), "thigh_r": (-50, 0, 0), "shin_r": (70, 0, 0), "foot_r": (-10, 0, 0)}
    tip = _pose(r, hips=(10, 40, 50), at=(4, -14, -2), legs=side, spine=(6, 0, 10), chest=(8, 4, 10), neck=(0, -10, 0), head=(-6, -16, 10),
                left=(14, 3, -6), lblade=((0.5, 0.1, -0.8), (1, 0, 0)), right=(-8, 4, -20), rblade=((-0.45, 0.04, -0.9), UP))
    down = _pose(r, hips=(8, 40, 82), at=(6, -17, -2), legs=side, spine=(4, 0, 8), chest=(6, 4, 4), neck=(0, -10, 0), head=(-8, -16, -6),
                 left=(18, 1.5, -4), lblade=((0.6, 0.0, -0.8), (1, 0, 0)), right=(-10, 1.5, -22), rblade=((-0.5, 0.0, -0.85), UP))
    hat_off = dict(down)
    hat_off["hat"] = {"rot": (-40, 30, 70), "pos": (2, -10, 6)}
    mask_off = dict(hat_off)
    mask_off["mask"] = {"rot": (80, 0, 0), "pos": (0, -3.5, -1.5)}
    _keyed(r, "death", 4.4, [(0, _pose(r)), (0.3, stag), (0.8, buckle), (1.3, lean), (1.9, _mix(lean, slide, 0.6)), (2.4, slide), (2.9, tip),
                            (3.3, down), (3.6, hat_off), (4.0, _mix(hat_off, mask_off, 0.6)), (4.4, mask_off)])


# ---- the moves -------------------------------------------------------------------------------------------------------

def thrust(r):
    """The Quick Thrust: his hand draws back a little and the front toes lift; then the arm shoots straight out ahead of
    a step, the point leading, the back leg straightening behind him."""
    cock = _pose(r, hips=(0, -6, 0), at=(0, -1.2, 2), chest=(0, -8, 0), head=(0, 4, 0), right=(-5, 33, -8), rblade=((0.1, 0.0, -1), UP),
                 step={"r": (0, -1.8, -1)})
    hit = _pose(r, hips=(3, 8, 0), at=(0, -3, -5), spine=(3, 0, 0), chest=(3, 10, 0), head=(-3, -10, 0), right=(-3, 29, -30),
                rblade=((0.06, 0.28, -1), UP), step={"r": (0, 0, -9), "l": (0, 0, -2)})
    after = _pose(r, hips=(4, 9, 0), at=(0, -3.4, -5.6), spine=(3, 0, 0), chest=(4, 11, 0), head=(-3, -11, 0), right=(-3, 28, -31),
                  rblade=((0.05, 0.32, -1), UP), step={"r": (0, 0, -9.5), "l": (0, 0, -2)})
    sink = _pose(r, hips=(5, 9, 0), at=(0, -4.2, -5.8), spine=(4, 0, 0), chest=(5, 10, 0), head=(-6, -9, 0), right=(-3, 27, -29),
                 rblade=((0.05, 0.42, -1), UP), step={"r": (0, 0, -9.5), "l": (0, 0, -2)})  # the point dips as the lunge settles
    ret = _pose(r, at=(0, -2, -2.5), chest=(2, 5, 0), head=(-1, -5, 0), right=(-4, 30, -25), rblade=((0.08, 0.22, -1), UP), step={"r": (0, -1.6, -5), "l": (0, 0, -1)})
    return Move("thrust", "LIGHT", 10, 4, 14, title="Quick Thrust", track=10.0, rng=(0, 4.6), kinds=("near",), damage=1.0, knock=0.4, plant=(),
                hit=[("rapier", TIP, 1.6), ("rapier", (0, 16, 0), 1.6)], lag=ON_TIME, through=sink,
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.6, hit), (1.0, after)],
                        "recover": [(0.48, ret), (0.76, _mix(ret, _pose(r), 0.72))]})


def cut(r):
    """The Cutover: the point flicks up and over to his right shoulder, the blade back behind him; then it cuts down and
    across in front of him to his left hip, his whole body turning through behind it."""
    raise_ = _pose(r, hips=(0, -8, 0), at=(0, -0.5, 1), chest=(-4, -14, -3), head=(2, 8, 0), right=(-11, 48, -8),
                   rblade=((0.25, -0.85, 0.45), (0, 0, -1)), step={"r": (0, 0, -1)})
    hit = _pose(r, hips=(4, 14, 0), at=(0, -2.5, -3), spine=(2, 6, 0), chest=(6, 26, 4), head=(2, -18, 0), right=(6, 30, -20),
                rblade=((0.65, 0.45, -0.6), UP), step={"r": (0, 0, -6), "l": (0, 0, -1.5)})
    after = _pose(r, hips=(4, 18, 0), at=(0, -3, -3.4), spine=(2, 8, 0), chest=(6, 32, 5), head=(2, -22, 0), right=(10, 25, -12),
                  rblade=((0.8, 0.55, -0.15), UP), step={"r": (0, 0, -6), "l": (0, 0, -1.5)})
    return Move("cut", "LIGHT", 12, 4, 16, title="Cutover", track=8.0, rng=(0, 4.4), kinds=("near",), damage=1.1, knock=0.7, plant=(),
                hit=[("rapier", TIP, 2.2), ("rapier", (0, 17, 0), 2.0)], lag=ON_TIME, through=_on(r, after, 0.05),
                stages={"windup": [(0.3, _mix(_pose(r), raise_, 0.35)), (1.0, raise_)], "active": [(0.6, hit), (1.0, after)],
                        "recover": _back(r, after)})


def feint(r):
    """The Feint: he rises with the hilt over his head and the point hanging down at you from above, the dagger forward
    low; he holds it there (you dodge too early), then drops into a long low thrust under your guard."""
    high = _pose(r, hips=(-4, -6, 0), at=(0, -1.5, 2.5), spine=(-3, 0, 0), chest=(-8, -10, 0), head=(6, 12, 0), right=(-3, 52, -2),
                 rblade=((0.05, 0.55, -0.85), UP), left=(7, 30, -11), lblade=((0.1, -0.6, -0.8), (1, 0, 0)))
    strike = _pose(r, hips=(8, 6, 0), at=(0, -7, -7), spine=(8, 0, 0), chest=(10, 8, 0), head=(-8, -6, 0), right=(-2, 20, -33),
                   rblade=((0.03, 0.3, -1), UP), step={"r": (0, 0, -11), "l": (0, 0, -3)})
    after = _pose(r, hips=(8, 7, 0), at=(0, -7.5, -7.6), spine=(8, 0, 0), chest=(11, 9, 0), head=(-8, -7, 0), right=(-2, 19, -34),
                  rblade=((0.03, 0.32, -1), UP), step={"r": (0, 0, -11.5), "l": (0, 0, -3)})
    ret = _pose(r, at=(0, -5, -4), chest=(4, 4, 0), head=(-3, -3, 0), right=(-3, 26, -24), rblade=((0.05, 0.25, -1), UP), step={"r": (0, -1.6, -6), "l": (0, 0, -1.5)})
    return Move("feint", "HEAVY", 16, 5, 20, title="Feint", hold=8, track=6.0, rng=(0, 5.0), kinds=("near",), damage=1.4, knock=1.0, plant=(),
                hit=[("rapier", TIP, 1.8), ("rapier", (0, 16, 0), 1.8)], lag=ON_TIME, through=after,
                stages={"windup": [(0.3, _mix(_pose(r), high, 0.3)), (1.0, high)], "active": [(0.6, strike), (1.0, after)],
                        "recover": [(0.38, ret), (0.72, _mix(ret, _pose(r), 0.75))]})


def lunge(r):
    """The Mirror Lunge (a gap closer): he sinks back onto his back leg with the blade drawn level beside his face, then
    runs at you, the hips squared and the arm straight out ahead so the point arrives first, the dagger arm flung back;
    he lands in a long lunge."""
    load = _pose(r, hips=(-4, -6, 0), at=(0, -3.5, 3), chest=(-4, -8, 0), head=(2, 8, 0), right=(-6, 38, -6), rblade=((0.08, 0.05, -1), UP),
                 left=(12, 34, 6), lblade=((0.3, 0.6, 0.6), (1, 0, 0)), step={"r": (0, -1, 1.5)})
    D = 46.0
    act = []
    n = 8
    for k in range(1, n + 1):
        u = k / n
        c = math.cos(2 * math.pi * 1.5 * u * 2)
        act.append((u, _pose(r, hips=(12, 32, 0), at=(0, -1.2 + 1.0 * c, -2), spine=(4, 0, 0), chest=(8, 6, 0), neck=(0, -10, 0), head=(-12, -26, 0),
                             right=(-1, 33, -30), rblade=((0, 0.12, -1), UP), left=(10, 33, 11), lblade=((0.3, 0.5, 0.8), (1, 0, 0)),
                             step=_run_feet(r, (1.5 * u) % 1.0, D, lift=7.0))))
    land = _pose(r, hips=(6, 10, 0), at=(0, -5, -4), spine=(4, 0, 0), chest=(6, 10, 0), head=(-4, -10, 0), right=(-3, 29, -30), rblade=((0.05, 0.3, -1), UP),
                 left=(10, 33, 8), lblade=((0.3, 0.5, 0.8), (1, 0, 0)), step={"r": (0, 0, -8), "l": (0, 0, -2)})
    return Move("lunge", "HEAVY", 16, 14, 20, title="Mirror Lunge", track=6.0, lock=12, advance=7.0, ability="Mirror Lunge", rng=(5, 14), kinds=("gap",),
                damage=1.3, knock=1.0, plant=(), hit=[("rapier", TIP, 2.0), ("hand_r", (0, 2, 0), 2.5)], lag=ON_TIME, through=land,
                stages={"windup": [(0.3, _mix(_pose(r), load, 0.4)), (1.0, load)], "active": act, "recover": _back(r, land)})


def parry(r):
    """The Perfect Parry: both blades crossed in a high bind before his mask (the rapier across above, the dagger rising
    under it), braced; each blow that lands on them jolts them up."""
    guard = _pose(r, hips=(-3, 12, 0), at=(0, -2, 1.5), chest=(-6, 16, 0), head=(6, -20, 0), right=(-5, 47, -11), rblade=((0.95, -0.3, -0.1), (0, 0, -1)),
                  left=(3, 41, -12), lblade=((-0.55, -0.8, -0.2), (0, 0, -1)), step={"r": (0, 0, 1)})
    catch = _pose(r, hips=(-4, 13, 0), at=(0, -2.8, 2.4), chest=(-8, 17, 0), head=(4, -20, 0), right=(-5, 48.5, -10), rblade=((0.95, -0.34, -0.1), (0, 0, -1)),
                  left=(3, 42.5, -11), lblade=((-0.55, -0.82, -0.15), (0, 0, -1)), step={"r": (0, 0, 1.5)})
    return Move("parry", "NONE", 8, 12, 12, title="Perfect Parry", track=8.0, ability="Perfect Parry", rng=(0, 5.0), kinds=("guard",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=guard,
                stages={"windup": [(0.4, _mix(_pose(r), guard, 0.6)), (1.0, guard)], "active": [(0.25, catch), (0.55, guard), (0.8, catch), (1.0, guard)],
                        "recover": _back(r, guard)})


def riposte(r):
    """The Riposte (after the parry): the blade drops to his left hip, point down, and whips up across in front of him
    to his right shoulder, the back of the hand leading, with a step."""
    low = _pose(r, hips=(2, 10, 0), at=(0, -2, 0), chest=(4, 18, 2), head=(0, -14, 0), right=(6, 24, -9), rblade=((0.5, 0.65, -0.55), UP))
    hit = _pose(r, hips=(-2, -14, 0), at=(0, -1, -3), chest=(-4, -24, -4), head=(-4, 16, 0), right=(-12, 44, -18), rblade=((-0.6, -0.55, -0.6), (0, 0, -1)),
                step={"r": (0, 0, -5)})
    after = _pose(r, hips=(-2, -17, 0), at=(0, -0.8, -3.2), chest=(-4, -30, -5), head=(-4, 20, 0), right=(-14, 47, -12), rblade=((-0.75, -0.6, -0.2), (0, 0, -1)),
                  step={"r": (0, 0, -5)})
    return Move("riposte", "LIGHT", 8, 4, 14, title="Riposte", track=10.0, rng=(0, 4.4), kinds=("near",), damage=1.0, knock=0.8, plant=(),
                hit=[("rapier", TIP, 2.2), ("rapier", (0, 16, 0), 2.0)], lag=ON_TIME, through=_on(r, after, 0.05),
                stages={"windup": [(0.3, _mix(_pose(r), low, 0.35)), (1.0, low)], "active": [(0.6, hit), (1.0, after)], "recover": _back(r, after)})


def back(r):
    """The Blind Thrust (for whoever is behind him): he looks back over his left shoulder, draws the blade across to his
    left hip with the point turned behind him, and thrusts it straight back under his own raised arm."""
    look = dict(chest=(2, -18, 0), neck=(0, -28, 0), head=(0, -66, 0))
    cock = _pose(r, hips=(0, -8, 0), right=(5, 29, -12), rblade=((0.15, 0.0, 1), UP), left=(12, 40, -6), lblade=((0.4, -0.8, -0.4), (1, 0, 0)), **look)
    hit = _pose(r, hips=(0, -14, 0), at=(0, -1.5, 2), chest=(4, -24, 2), neck=(0, -30, 0), head=(0, -70, 0), right=(8, 26, 7), rblade=((0.12, 0.18, 1), UP),
                left=(12, 42, -6), lblade=((0.4, -0.8, -0.4), (1, 0, 0)), step={"r": (0, 0, 1), "l": (0, 0, 2)})
    after = _pose(r, hips=(0, -15, 0), at=(0, -1.8, 2.4), chest=(4, -26, 2), neck=(0, -30, 0), head=(0, -70, 0), right=(8, 25, 9), rblade=((0.12, 0.2, 1), UP),
                  left=(12, 42, -6), lblade=((0.4, -0.8, -0.4), (1, 0, 0)), step={"r": (0, 0, 1), "l": (0, 0, 2)})
    return Move("back", "LIGHT", 10, 4, 14, title="Blind Thrust", track=0.0, rng=(0, 4.2), kinds=("behind",), damage=1.0, knock=0.6, plant=(),
                hit=[("rapier", TIP, 1.8), ("rapier", (0, 16, 0), 1.8)], lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.6, hit), (1.0, after)], "recover": _back(r, after)})


def split(r):
    """The Mirror Split: he squares up and flings both arms out wide, the rapier and the dagger level out to his sides
    like a mirror's wings, turns his mask to the left and to the right, and stamps his front foot: his reflections step
    out of him."""
    wide = lambda yaw=0.0, y=0.0: _pose(r, hips=(-2, 10, 0), at=(0, 0.5 + y, 0), chest=(-6, 24, 0), neck=(0, -4, 0), head=(-8, -26 + yaw, 0),
                                       right=(-22, 40, -4), rblade=((-0.9, -0.3, 0.1), (0, 0, -1)), left=(22, 40, -4), lblade=((0.9, -0.3, 0.1), (0, 0, -1)))
    lift = _pose(r, hips=(-2, 10, 0), at=(0, 1.0, 0), chest=(-6, 24, 0), neck=(0, -4, 0), head=(-8, -26, 0), right=(-22, 41, -4),
                 rblade=((-0.9, -0.3, 0.1), (0, 0, -1)), left=(22, 41, -4), lblade=((0.9, -0.3, 0.1), (0, 0, -1)), step={"r": (0, -3.5, -1)})
    stamp = _pose(r, hips=(-2, 10, 0), at=(0, -1.0, 0), chest=(-4, 24, 0), neck=(0, -4, 0), head=(-6, -26, 0), right=(-22, 38, -4),
                  rblade=((-0.9, -0.25, 0.1), (0, 0, -1)), left=(22, 38, -4), lblade=((0.9, -0.25, 0.1), (0, 0, -1)), step={"r": (0, 0, -1)})
    return Move("split", "NONE", 12, 16, 14, title="Mirror Split", track=0.0, ability="Mirror Split", rng=(0, 99), kinds=("summon",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=wide(),
                stages={"windup": [(0.3, _mix(_pose(r), wide(), 0.35)), (1.0, wide())],
                        "active": [(0.22, wide(22)), (0.44, wide(-22)), (0.58, lift), (0.7, stamp), (1.0, wide(0, -0.4))],
                        "recover": _back(r, wide())})


def glass(r):
    """Through the Glass: he draws himself in small, the blade upright before his mask and the dagger across his breast,
    and slips sideways to his left as through a pane (he is gone, and is somewhere else)."""
    small = _pose(r, hips=(4, 6, 0), at=(0, -3, 0), chest=(6, 10, 0), head=(6, -10, 0), right=(-2, 40, -7), rblade=((0, -1, -0.05), (1, 0, 0)),
                  left=(2, 34, -6), lblade=((-0.8, -0.5, 0), (0, 0, -1)), step={"r": (0, 0, 2)})
    slip = _pose(r, hips=(4, 6, -12), at=(4, -4, 0), chest=(8, 10, -10), head=(6, -10, 6), right=(2, 38, -6), rblade=((0.1, -1, -0.05), (1, 0, 0)),
                 left=(6, 32, -5), lblade=((-0.8, -0.5, 0), (0, 0, -1)), step={"l": (6, 0, -2), "r": (5, -1.5, 1)})
    return Move("glass", "NONE", 10, 8, 14, title="Through the Glass", track=0.0, ability="Through the Glass", rng=(0, 99), kinds=("gap",), damage=0, knock=0,
                plant=(), lag=ON_TIME, through=_mix(slip, small, 0.55),
                stages={"windup": [(0.3, _mix(_pose(r), small, 0.4)), (1.0, small)], "active": [(0.5, slip), (1.0, _mix(slip, small, 0.4))],
                        "recover": _back(r, _mix(slip, small, 0.7))})


SHATTER_AT = (0, 0, 1.2)  # where the glass flies from: the floor where the point goes in (blocks: x, up, forward)


def shatter(r):
    """Shatter (the signature): he takes the rapier in both hands and raises it point-down over his hat, rising tall,
    looking up at it; then drops onto his left knee and drives it into the floor before him, and the palace breaks."""
    two = lambda tgt, spread=1.8: dict(right=tgt, rblade=((0, 1, -0.1), (1, 0, 0)), left=("on", "hand_r", (spread, 1.5, 0)),
                                       lblade=((0.2, -1, 0.3), (1, 0, 0)))
    face = dict(neck=(0, -18, 0))
    grip = _pose(r, hips=(0, 20, 0), at=(0, -1.5, 0.5), chest=(2, 14, 0), head=(6, -20, 0), step={"r": (0, 0, 3)}, **face, **two((-1, 34, -11)))
    up = _pose(r, hips=(-4, 28, 0), at=(0, 1.5, 1), spine=(-4, 0, 0), chest=(-10, 14, 0), head=(-12, -24, 0), step={"r": (0, 0, 4), "l": (0, 0, -2)},
               **face, **two((-1, 58, -7)))
    peak = _pose(r, hips=(-6, 28, 0), at=(0, 2.5, 1.5), spine=(-6, 0, 0), chest=(-14, 14, 0), head=(-20, -24, 0), step={"r": (0, 0, 4), "l": (0, 0, -2)},
                 **face, **two((-1, 63, -6)))
    plunge = _pose(r, hips=(12, 40, 0), at=(0, -9, -2), legs=KNEEL, spine=(8, 0, 0), chest=(10, 0, 0), head=(10, -22, 0), **face, **two((-1, 29, -16)))
    after = _pose(r, hips=(14, 40, 0), at=(0, -9.5, -2.4), legs=KNEEL, spine=(10, 0, 0), chest=(16, 0, 0), head=(22, -22, 0), **face, **two((-1, 28, -16)))
    draw = _pose(r, hips=(8, 40, 0), at=(0, -8.5, -1.5), legs=KNEEL, spine=(4, 0, 0), chest=(4, 0, 0), head=(0, -22, 0), **face, **two((-1, 40, -14)))
    rise = _pose(r, hips=(4, 20, 0), at=(0, -4, 0), chest=(2, 8, 0), head=(2, -12, 0), right=(-4, 32, -14), rblade=((0.1, 0.1, -1), UP),
                 left=(5, 33, -7), lblade=REST_HAND["l"], step={"r": (0, 0, 2)})
    return Move("shatter", "PERILOUS", 30, 12, 30, title="Shatter", signature=True, track=0.0, ability="Shatter", origin=SHATTER_AT, rng=(0, 99),
                kinds=("area", "ranged"), damage=0, knock=0, plant=(), lag=ON_TIME, through=after, hit=[("rapier", TIP, 3.0)],
                ground=[(["rapier"], [("hand_r", 0, 40), ("forearm_r", 0, 30)])], bite=0.4,
                stages={"windup": [(0.3, grip), (0.7, up), (1.0, peak)], "active": [(0.4, plunge), (1.0, after)],
                        "recover": [(0.3, draw), (0.58, rise), (0.78, _mix(rise, _pose(r), 0.7))]})


def wake(r):
    """His intro, "Footsteps that match yours.": he stands straight, heels together and the blade's point down by his
    foot, then salutes: the hilt up to his mask, the blade upright before his face; sweeps it down and out to his side,
    and steps forward into his guard with a stamp."""
    stand = _pose(r, hips=(0, 30, 0), at=(0, 2.2, 0), chest=(-2, 10, 0), neck=(0, -18, 0), head=(-2, -20, 0), right=(-8, 23, -5),
                  rblade=((-0.15, 1, -0.3), UP), left=(7, 23, 1), lblade=((0.1, 1, 0.3), (1, 0, 0)), step={"r": (1, 0, 7), "l": (-2, 0, -6)})
    salute = _pose(r, hips=(0, 30, 0), at=(0, 2.4, 0), chest=(-4, 10, 0), neck=(0, -18, 0), head=(-4, -20, 0), right=(-1, 45, -8),
                   rblade=((0, -1, -0.08), (1, 0, 0)), left=(7, 23, 1), lblade=((0.1, 1, 0.3), (1, 0, 0)), step={"r": (1, 0, 7), "l": (-2, 0, -6)})
    bow = _pose(r, hips=(0, 30, 0), at=(0, 2.0, 0), chest=(2, 10, 0), neck=(4, -18, 0), head=(10, -20, 0), right=(-1, 43, -9),
                rblade=((0, -1, -0.15), (1, 0, 0)), left=(7, 23, 1), lblade=((0.1, 1, 0.3), (1, 0, 0)), step={"r": (1, 0, 7), "l": (-2, 0, -6)})
    sweep = _pose(r, hips=(0, 26, 0), at=(0, 2.0, 0), chest=(0, 6, 0), neck=(0, -14, 0), head=(0, -16, 0), right=(-15, 25, -10),
                  rblade=((-0.6, 0.75, -0.3), UP), left=(7, 23, 1), lblade=((0.1, 1, 0.3), (1, 0, 0)), step={"r": (1, 0, 7), "l": (-2, 0, -6)})
    stepping = _pose(r, hips=(0, 10, 0), at=(0, 0.2, 0), chest=(0, 2, 0), head=(-2, -4, 0), right=(-6, 31, -12), rblade=((0.1, 0.1, -1), UP),
                     step={"r": (0, -2.5, -2), "l": (0, 0, -1)})
    stamp = _pose(r, at=(0, -1.0, 0), chest=(1, 0, 0), more={"hand_r": (-3, 0, 0)})
    return Move("wake", "NONE", 16, 30, 10, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME, through=stamp,
                stages={"windup": [(0.5, _mix(_pose(r), stand, 0.6)), (1.0, stand)],
                        "active": [(0.2, salute), (0.38, bow), (0.6, sweep), (0.82, stepping), (1.0, stamp)],
                        "recover": _back(r, stamp)})


def moves(r):
    return [thrust(r), cut(r), feint(r), lunge(r), parry(r), riposte(r), back(r), split(r), glass(r), shatter(r), wake(r)]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.0, temper="regal")
    plans.biped_reactions(r, weight=1.0)
    idle(r)
    walk(r)
    run(r)
    turns(r)
    reactions(r)
    mv = [_unwrap(m) for m in moves(r)]
    for m in mv:
        stage_clips(r, m)
    # his hands (and what they hold) stay out of the ground between the keys, the wrists turning first
    core.ground_pass(r, [(["rapier"], [("hand_r", 0, 40), ("forearm_r", 0, 30)]), (["dagger"], [("hand_l", 0, 40), ("forearm_l", 0, 30)])],
                     clips=[n for n in r.clips if n != "death"])
    return mv


FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "thrust", "weight": 3, "when": "near", "then": [["cut"]]},
            {"move": "feint", "weight": 2, "when": "near"},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "lunge", "weight": 2, "when": "far", "then": [["thrust"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
        # it splits with the second phase, and slips through the glass, as in GateBestiary
        {"transition": "split", "pool": [
            {"move": "thrust", "weight": 3, "when": "near", "then": [["cut"], ["riposte"]]},
            {"move": "feint", "weight": 2, "when": "near"},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "glass", "weight": 2},
            {"move": "split", "weight": 1},
            {"move": "lunge", "weight": 2, "when": "far", "then": [["thrust"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
        {"transition": "shatter", "pool": [
            {"move": "shatter", "weight": 1},
            {"move": "split", "weight": 1},
            {"move": "glass", "weight": 2},
            {"move": "thrust", "weight": 3, "when": "near", "then": [["cut", "riposte"]]},
            {"move": "feint", "weight": 2, "when": "near"},
            {"move": "parry", "weight": 2, "when": "near", "then": [["riposte"]]},
            {"move": "lunge", "weight": 2, "when": "far", "then": [["thrust", "cut"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}

# His sounds (SPEC §35.8): steel and glass, a light quick foot; no voice (the mask doesn't speak). Chosen by ear on
# paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("item.armor.equip_chain", 1.6, 0.5), ("block.glass.step", 1.4, 0.6)],
    "tell_heavy": [("entity.illusioner.prepare_mirror", 1.2, 0.6), ("block.amethyst_block.chime", 1.6, 0.6)],
    "tell_perilous": [("entity.illusioner.prepare_blindness", 0.8, 0.7), ("block.amethyst_block.chime", 0.8, 0.8)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.7), ("entity.player.attack.strong", 1.5, 0.5)],
    "swing_heavy": [("entity.player.attack.knockback", 1.2, 0.7), ("item.trident.throw", 1.4, 0.5)],
    "cast": [("entity.illusioner.mirror_move", 1.0, 0.8), ("block.amethyst_block.chime", 1.2, 0.6)],
    "impact": [("block.glass.break", 1.0, 0.8), ("block.amethyst_cluster.break", 0.9, 0.7)],
    "roar": [("block.glass.break", 0.7, 0.9), ("entity.illusioner.cast_spell", 0.8, 0.7)],
    "step": [("block.glass.step", 1.2, 0.5), ("item.armor.equip_leather", 1.4, 0.4)],
}
MOVE_SOUNDS = {"parry": {"swing": "", "impact": ""}, "split": {"swing": "cast", "impact": ""}, "glass": {"swing": "cast", "impact": ""},
               "shatter": {"swing": "roar"}, "wake": {"swing": "", "impact": ""}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.55, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where his parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_r", (0, 0, 0)), ("hand_l", (0, 0, 0)), ("rapier", TIP), ("dagger", (0, DAGGER, 0)), ("head", (0, -8, 0)),
                 ("head", MASK), ("hat", (0, -6, 0)), ("plume4", (0, -5, 0)), ("foot_l", (0, 2, 0)), ("foot_r", (0, 2, 0)), ("scabbard", (0, 25, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
