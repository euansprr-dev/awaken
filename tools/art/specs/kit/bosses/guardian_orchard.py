"""The Ossuary King (SPEC §35.11, docs/bosses/ROLLOUT.md): the Bone Orchard's Guardian, a Brute. The Orchard King,
picked from his silhouette round 2026-10-29: "The orchard grew from his ribs."

A giant crowned skeleton, gaunt and tall, an apple tree grown up through him: its roots grip his hips, its trunk rises
out of his pelvis through his ribcage and up his throat into his skull, and its boughs break out through the top of his
skull inside a tarnished crown, spreading over him to his left in a dark crown of leaves hung with glowing red apples.
One apple hangs inside his ribs where his heart was. A rotten fur mantle and a royal cloak gone to rags hang from his
shoulders; he carries a greatsword of bone, serrated down one edge, on his right shoulder, a small skull for its pommel.
The bone is old ivory, mossed at the joints; the apples, his heart and the points of light in his eyes are the only
warm things on him, the brightest; the leaves and the cloak the darkest.

He stands tall with the sword on his shoulder and his left hand hanging, its finger bones loose. He walks like a king
in procession, slow and long, his bones rattling at each step and the cloak swinging; he runs leaning, the blade
dragged low behind him.

Moves (SPEC §35.11): the Royal Cleave (the blade off his shoulder in a long diagonal cut across in front of him, LIGHT)
and the backhand cut that comes back across after it (LIGHT); Bone Crush (both hands on the hilt, the blade swung back
over his right shoulder to hang down his back, held, then brought over and down into the floor before him: HEAVY); a
backhand sweep low round behind him for whoever is there; the Royal March (he leans and strides at you, the blade
trailing low, and heaves it up through you as he arrives: a gap closer); Rise (he drives the sword into the floor,
leans on its pommel, and lifts his left hand slowly, its fingers spreading, and the dead get up); and Rib Spikes (the
signature: he throws his arms wide and his ribs open out from his breastbone like doors, the heart blazing between
them, and snap shut as the spikes fly). He waits for you kneeling with his hands on the pommel of the planted sword,
his crowned head bowed.

He gets up once (RISES): struck down, he falls apart, his bones dropping into a heap with his skull on top, the tree
still standing out of it; and he puts himself back together, the legs first, the skull last, caught back onto his
neck. When he dies he falls apart for good, and the tree bows over the heap.
"""
import math

from kit import core, ik, parts, plans
from kit.core import Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "guardian_orchard"
SCALE = 2.0  # the hitbox on the def's WITHER_SKELETON model at size 0.8 x 2.3: 1.6 x 4.6 blocks; the body is at true size

P = plans.Biped(leg=30, thigh=15, foot=(4, 2, 7), pelvis=(9, 4, 5), spine=(2, 7, 2), chest=(14, 13, 9), neck=(2, 4, 2),
                head=(7, 9, 8), arm=(3, 13, 3), forearm=(3, 12, 3), hand=(4, 5, 3), stance=3.5, shoulder=8.0, leg_w=(2, 2))
DROP = 1.0  # he stands tall: his hips this much lower than straight legs would put them
FOOT = {"l": (4.5, 2.0, 0.5), "r": (-4.5, 2.0, -0.5)}  # the ankles (x, height, z): square under him, the right a hair ahead
TOE = {"l": (0.985, 0, 0.17), "r": (0.985, 0, -0.17)}  # each foot's +x: the toes turned out a little
GRIP = {"r": (-6.5, 48.0, -6.5), "l": (9.5, 27.0, -1.0)}  # the hands at rest (x, height, z): the sword hand at his shoulder
POLE = {"r": (-0.6, 0.75, 0.2), "l": (0.5, 0.1, 1.0)}  # the elbows: the sword arm's down and out, the other's back
CARRY = ((-0.12, -0.62, 0.78), (1.0, 0.0, 0.0))  # the sword on his shoulder: the blade up and back, its flat to the front
HANG = ((0.05, 1.0, -0.12), (0.0, 0.0, -1.0))  # the left hand hanging, its fingers down, the thumb forward
BLADE = 36.0  # the blade's tip from the sword bone (the right fist's middle)
TIP = (0, BLADE, 0)
GRIP2 = (0, -3.0, 0)  # where the left hand takes the hilt two-handed, above the right fist
# the ribs, top to bottom: (height in the chest, half-width, front z); the last pair float (no front bar)
RIBS = ((-11.0, 5, -4), (-8.5, 6, -4), (-6.0, 7, -4), (-3.5, 6, -2))
RIB_TILT = 10.0  # each rib slopes down from his spine to his breastbone
FINGERS = ("finger0_l", "finger1_l", "finger2_l")
ARMS = tuple(f"{b}_{s}" for b in ("arm", "forearm", "hand") for s in "lr")
ON_TIME = {b: 0 for b in ARMS}  # the hands land where they're sent
CARRY_AT = []  # set by body(): the sword hand's rest place, the blade's line, its knuckles and the elbow's pole, in the chest's frame
APPLES = tuple(f"apple{k}" for k in range(8))
LEAVES = ("leaves0", "leaves1", "leaves2", "leaves3")
CLOAK = ("cloak0", "cloak1", "cloak2")


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg + DROP, 0))
    # the pelvis: the sacrum behind, the two wings of the hip bones flaring, the pubic bar in front; a bowl the tree
    # grows out of
    pel.cube("sacrum", -1.5, -3, 1, 3, 4, 2)
    for s, nm in ((1, "l"), (-1, "r")):
        pel.cube("ilium_" + nm, 1.5 if s > 0 else -4.5, -4, -1.5, 3, 4, 4)
    pel.cube("pubis", -2.5, 0, -2.5, 5, 2, 1)
    tr0 = pel.bone("trunk0", (0.5, 0, -0.5))
    tr0.cube("trunk0", -1.5, -9, -1.5, 3, 10, 3)
    tr0.cube("root0", 1, -0.5, -2.5, 3, 1, 1)  # roots over the hip bones
    tr0.cube("root1", -4.5, -1.5, -1, 3, 1, 1)
    tr0.cube("root2", -1, 0.5, -2.8, 2, 1, 1)
    # the legs: the femur with its head in the socket and the knuckles of the knee, the tibia and the thin fibula
    # behind it, the foot's bones splayed on the floor
    for s, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (s * P.stance, 0.5, 0))
        th.cube("fhead_" + nm, -2, -1.5, -2, 4, 3, 4)
        th.cube("femur_" + nm, -1.5, 0, -1.5, 3, 14, 3)
        th.cube("knee_" + nm, -2, 13, -2, 4, 2, 4)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("tibia_" + nm, -1, 0.5, -2, 2, 12, 3)
        sn.cube("fibula_" + nm, 0.5 if s > 0 else -1.5, 1.5, 1.5, 1, 10, 1)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("tarsus_" + nm, -1.5, 0, -1.5, 3, 2, 3)
        ft.cube("toes_" + nm, -2, 1, -6, 4, 1, 5)
        ft.cube("heel_" + nm, -1, 1, 1.5, 2, 1, 1)
    # the spine: the lumbar column (the trunk rises before it), then the ribcage
    sp = pel.bone("spine", (0, -3, 0.5))
    sp.cube("lumbar", -1, -6.5, 0.5, 2, 7, 2)
    sp.cube("lspine", -0.5, -6, 2.5, 1, 6, 1)
    ch = sp.bone("chest", (0, -6, -0.5))
    ch.cube("thoracic", -1, -13, 2.5, 2, 13, 2)
    ch.cube("tspine", -0.5, -12.5, 4.5, 1, 12, 1)
    ch.cube("sternum", -1, -12, -4.6, 2, 7, 1)
    for s, nm in ((1, "l"), (-1, "r")):
        cl = ch.bone("clav_" + nm, (s * 1.0, -12.6, -4.0), (0, -38 * s, 0))  # the collarbone, back to the shoulder
        cl.cube("clav_" + nm, 0 if s > 0 else -7, -0.5, -0.5, 7, 1, 1)
        ch.cube("scap_" + nm, 2 if s > 0 else -6, -12.5, 3.6, 4, 5, 1)
    # the ribs, each pair on its own bones hinged beside the spine (they open like doors for the Rib Spikes): a bar out
    # from the spine, one down his side, one in to the breastbone
    for k, (y, w, f) in enumerate(RIBS):
        for s, nm in ((1, "l"), (-1, "r")):
            rb = ch.bone(f"rib{k}_{nm}", (s * 1.0, y, 3.0), (RIB_TILT, 0, 0))
            x0 = 0 if s > 0 else -(w - 2)
            rb.cube(f"rb{k}_{nm}", x0, -0.5, -0.5, w - 2, 1, 1)
            rb.cube(f"rs{k}_{nm}", w - 2 if s > 0 else -(w - 1), -0.5, f - 2, 1, 1, 2 - f)
            if k < 3:
                rb.cube(f"rf{k}_{nm}", x0, -0.5, f - 3, w - 2, 1, 1)
    # the trunk through his ribcage, leaves pressing out between his ribs, and the apple hanging where his heart was
    tr1 = ch.bone("trunk1", (0.5, 0.5, -0.5))
    tr1.cube("trunk1", -1.5, -14.5, -1.5, 3, 15, 3)
    tr1.cube("sprig0", 1.5, -8, -3, 2, 1, 2)
    tr1.cube("sprig1", -3.5, -4, -2.5, 2, 1, 2)
    hrt = tr1.bone("heart", (1.5, -9.5, -1.5))
    hrt.cube("heart", -1.5, 0, -1.5, 3, 3, 3)
    # the rotten fur mantle round his shoulders, and the royal cloak hanging from it in rags
    ch.cube("mantle", -9.5, -14.5, -1.5, 19, 4, 7)
    ck = ch.bone("cloak0", (0, -12, 5.5), (6, 0, 0))
    ck.cube("cloak0", -8, 0, 0, 16, 11, 1)
    ck = ck.bone("cloak1", (0, 11, 0), (3, 0, 0))
    ck.cube("cloak1", -7.5, 0, 0, 15, 10, 1)
    ck = ck.bone("cloak2", (0, 10, 0), (2, 0, 0))
    ck.cube("rag0", -7.5, 0, 0, 5, 9, 1)
    ck.cube("rag1", -2.5, 0, 0, 5, 12, 1)
    ck.cube("rag2", 2.5, 0, 0, 5, 7, 1)
    # the neck, the trunk up his throat in front of it, and the skull: the cranium, the brow, the cheekbones, the upper
    # jaw; the lower jaw on its own hinge, open between its sides so the trunk goes up through it into his skull
    nk = ch.bone("neck", (0, -13, 2.5))
    nk.cube("cervical", -1, -3.5, -1, 2, 4, 2)
    nk.cube("trunk2", -1.5, -4, -4, 3, 5, 3)
    hd = nk.bone("head", (0, -3.5, 0))
    hd.cube("cranium", -3.5, -9, -5.5, 7, 6, 8)
    hd.cube("brow", -3.5, -6, -6, 7, 1, 1)
    hd.cube("maxilla", -2.5, -3, -5.5, 5, 3, 3)
    hd.cube("cheek_l", 2.5, -4, -5, 1, 1, 3)
    hd.cube("cheek_r", -3.5, -4, -5, 1, 1, 3)
    jw = hd.bone("jaw", (0, -1, -1))
    jw.cube("chin", -2.5, 0, -4.5, 5, 2, 1)
    jw.cube("jaw_l", 2, -1, -4, 1, 3, 4)
    jw.cube("jaw_r", -3, -1, -4, 1, 3, 4)
    # the crown, worn askew: a tarnished band, five points and a red stone
    cr = hd.bone("crown", (0, -8.5, -1.5), (0, 0, -5))
    cr.cube("band", -4, -1, -4.5, 8, 2, 9)
    cr.cube("pt0", -0.5, -4, -5, 1, 3, 1)
    cr.cube("pt1", 2, -3, -5, 1, 2, 1)
    cr.cube("pt2", -3, -3, -5, 1, 2, 1)
    cr.cube("pt3", 3.5, -3, -1, 1, 2, 1)
    cr.cube("pt4", -4.5, -3, -1, 1, 2, 1)
    cr.cube("gem", -1, -0.5, -5, 2, 1, 1)
    # the boughs out of the top of his skull, the crown of leaves over him to his left, the apples hanging
    tips = {}
    for name, at, rot, n in (("bough0", (1.5, -9, 0.5), (0, 0, 30), 13), ("bough1", (-1, -9, 1.5), (-15, 0, -14), 9),
                             ("bough2", (2.5, -8.5, -2.5), (25, 0, 62), 8), ("bough3", (1, -9, 2.5), (-38, 0, 18), 8)):
        b = hd.bone(name, at, rot)
        b.cube(name, -1, -n, -1, 2, n, 2)
        tips[name] = _level(b, n - 0.5)
    lv = tips["bough0"]("leaves0")  # the crown's great mass, high over him to his left
    lv.cube("leaf0a", -7, -3, -6, 14, 6, 12)
    lv.cube("leaf0b", -5, -6, -4.5, 10, 3, 9)
    lv.cube("leaf0c", 4, 1, -6, 6, 4, 6)
    lv.cube("leaf0d", -10, 0, -2, 4, 4, 6)
    for nm, at, big in (("apple0", (-3, 3, -4), True), ("apple1", (5, 5, -2), False), ("apple2", (1, 3, 4), True)):
        _apple(lv, nm, at, big)
    lv = tips["bough1"]("leaves1")  # a smaller one over his right, kept clear of the sword's swing
    lv.cube("leaf1a", -3.5, -3, -4, 7, 5, 8)
    _apple(lv, "apple3", (-1, 2, -1), False)
    lv = tips["bough2"]("leaves2")  # low and forward over his left eye, a spray hanging from it
    lv.cube("leaf2a", -3.5, -2, -3.5, 7, 4, 7)
    lv.cube("leaf2b", 0, 2, -2, 3, 5, 3)
    _apple(lv, "apple4", (-1.5, 2, -1.5), True)
    _apple(lv, "apple5", (2, 7, 0), False)
    lv = tips["bough3"]("leaves3")  # behind, over his left shoulder
    lv.cube("leaf3a", -4, -3, -4, 8, 5, 8)
    _apple(lv, "apple6", (1, 2, 1), False)
    _apple(lv, "apple7", (-2, 2, -2), True)
    # the arms: the humerus with its knob, the two bones of the forearm with a gap between, the elbow's point behind
    for s, nm in ((1, "l"), (-1, "r")):
        arm = ch.bone("arm_" + nm, (s * P.shoulder, -12, 0.5))
        arm.cube("hhead_" + nm, -2, -2, -2, 4, 4, 4)
        arm.cube("humerus_" + nm, -1.5, 0, -1.5, 3, 12, 3)
        fa = arm.bone("forearm_" + nm, (0, 13, 0))
        fa.cube("elbow_" + nm, -1.5, -1.5, -0.5, 3, 3, 3)
        fa.cube("radius_" + nm, -2, 0, -1.5, 1, 12, 2)
        fa.cube("ulna_" + nm, 0.5, 0, -0.5, 1, 12, 2)
        fa.bone("hand_" + nm, (0, 12, 0))
    # the sword hand a fist of knuckle bones round the hilt; the other open, its three long fingers and thumb loose
    hr = r.by_name["hand_r"]
    hr.cube("fist_r", -1.5, 0, -1.5, 3, 5, 3)
    hr.cube("knuck_r", 1.5, 0.5, -1.5, 1, 4, 3)
    hl = r.by_name["hand_l"]
    hl.cube("palm_l", -2, 0, -1, 4, 3, 2)
    for k, x in enumerate((-1.5, 0.0, 1.5)):
        fg = hl.bone(f"finger{k}_l", (x, 3, 0), (4 + 3 * k, 0, 0))
        fg.cube(f"finger{k}_l", -0.5, 0, -0.5, 1, 5 - (k == 2), 1)
    th = hl.bone("thumb_l", (2.0, 0.5, 0), (0, 0, -25))
    th.cube("thumb_l", 0, 0, -0.5, 1, 3, 1)
    # the hands where they rest, the sword hand turned for the blade on his shoulder
    for s in "rl":
        ik.rest_limb(r, "arm_" + s, "forearm_" + s, "hand_" + s, GRIP[s], POLE[s])
    fr = core.frames(r, {})
    r.by_name["hand_r"].rot = ik.orient(r, fr, "hand_r", *CARRY)
    r.by_name["hand_l"].rot = ik.orient(r, fr, "hand_l", *HANG)
    # the greatsword of bone: a little skull for a pommel, a grip bound in rotten leather long enough for both hands,
    # a guard of two vertebrae, the blade a long flat bone tapering, serrated down its outer edge
    sw = hr.bone("sword", (0, 2.5, 0))
    sw.cube("pommel", -1.5, -7, -1.5, 3, 2, 3)
    sw.cube("grip", -1, -5, -1, 2, 9, 2)
    sw.cube("guard", -4.5, 4, -1.5, 9, 2, 3)
    sw.cube("gtip_l", 3.5, 6, -1, 1, 2, 2)
    sw.cube("gtip_r", -4.5, 6, -1, 1, 2, 2)
    sw.cube("blade0", -2.5, 6, -0.5, 5, 12, 1)
    sw.cube("blade1", -2, 18, -0.5, 4, 10, 1)
    sw.cube("blade2", -1.5, 28, -0.5, 3, 6, 1)
    sw.cube("btip", -0.5, 34, -0.5, 1, 2, 1)
    sw.cube("serr", -3.5, 8, -0.5, 1, 18, 1)
    # the feet where they stand, flat on the floor, and the legs' rest from the planter's own solution for them
    fr = core.frames(r, {})
    for s in "rl":
        ik.rest_limb(r, "thigh_" + s, "shin_" + s, "foot_" + s, FOOT[s], (0, 0, -1))
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["foot_" + s].rot = ik.orient(r, fr, "foot_" + s, (0, 1, 0), TOE[s])
    p = core.plant(r, {}, ("l", "r"))
    for s in "lr":
        for b in ("thigh_", "shin_", "foot_"):
            bn = r.by_name[b + s]
            bn.rot = tuple(bn.rot[i] + p[b + s][i] for i in range(3))
    r.look = [("head", 0.4, 0.35), ("neck", 0.2, 0.15)]
    r.size = P.leg / 36.0
    r.shadow = 0.9
    # secondary parts (SPEC §35.10): the crown of leaves sways heavy and slow, the apples swing on their stems, the
    # heart swings in his ribs, the cloak's rags trail him, and his ribs rattle at each step
    r.springs = [(nm, 0.35, 1.5, 0.25) for nm in LEAVES] + [(nm, 0.7, 2.6, 0.16) for nm in APPLES] + [("heart", 0.5, 2.2, 0.2)] \
        + [("cloak0", 0.35, 1.8, 0.3), ("cloak1", 0.45, 2.0, 0.26), ("cloak2", 0.55, 2.3, 0.22)] \
        + [(f"rib{k}_{s}", 0.12, 5.0, 0.18) for k in range(4) for s in "lr"]
    r.guard = ["sword", "hand_r", "hand_l"]  # the blade never goes into the floor (the death drops it there)
    r.under = PLANTS  # but its point is driven into it while he kneels on it
    _REST.clear()
    _REST.update({b.name: tuple(b.rot) for b in r.bones})
    fr = core.frames(r, {})
    global REACH
    REACH = math.dist(fr["arm_r"][1], fr["forearm_r"][1]) + math.dist(fr["forearm_r"][1], fr["hand_r"][1])
    for s in "lr":
        for k, (a, b) in ((s, (f"forearm_{s}", f"hand_{s}")), ("arm_" + s, ("chest", f"arm_{s}"))):
            Ma, Mb = fr[a][0], fr[b][0]
            _WRIST[k] = [[sum(Ma[n][i] * Mb[n][j] for n in range(3)) for j in range(3)] for i in range(3)]
    # the sword hand's place on his shoulder and the blade's line, in the chest's frame (it rides the chest)
    fr = core.frames(r, {})
    Mc, Tc = fr["chest"]
    Mt = core._t(Mc)
    CARRY_AT[:] = [R._mv(Mt, ik.sub(fr["hand_r"][1], Tc)), R._mv(Mt, ik.unit(CARRY[0])), R._mv(Mt, ik.unit(CARRY[1])), R._mv(Mt, POLE["r"])]
    return r


def _level(bough, n):
    """A maker of a bone at the bough's tip (n up it) turned back level with the skull, for a clump of leaves to sit on
    square while it sways from the bough."""
    M = R._rot(*(c * core.DEG for c in bough.rot))
    e = core._euler(core._t(M))
    return lambda name: bough.bone(name, (0, -n, 0), tuple(c / core.DEG for c in e))


def _apple(parent, name, at, big):
    """An apple on a stem, hanging from a clump of leaves (a spring: it swings)."""
    a = parent.bone(name, at)
    a.cube(name + "_s", -0.5, 0, -0.5, 1, 1, 1)
    n = 3 if big else 2
    a.cube(name, -n / 2, 1, -n / 2, n, n, n)


BONE = Ramp("#231c14", "#362c20", "#4c3f2f", "#655641", "#7f6f56", "#9a8a6e", "#b5a688", "#cfc2a4", "#e6dcc2", "#f4eedf")
MOSS = Ramp("#0e1608", "#17240e", "#223415", "#2e451c", "#3c5724", "#4c6a2e")
BARK = Ramp("#120c08", "#1c140e", "#281d14", "#35271b", "#433224", "#523e2d")
LEAF = Ramp("#060f07", "#0b180c", "#112212", "#182e18", "#203b1f", "#2a4a26", "#365a2e", "#456c38", "#577e42")
AUTUMN = Ramp("#3a2a08", "#56400e", "#745a16", "#927420")
APPLE = Ramp("#2c0404", "#4a0808", "#6c0c0a", "#90140e", "#b41e14", "#d4321c", "#ec5228", "#ff8040")
GOLD = Ramp("#22190a", "#352710", "#4c3916", "#644d1e", "#7e6428", "#9a7c36", "#b49548", "#ccae60")
PATINA = Ramp("#1e2a22", "#2c3c30", "#3c5040", "#4e6452")
FUR = Ramp("#1e1d1a", "#2b2a25", "#3a3832", "#4a4840", "#5c5950", "#6e6a5f", "#807b6e")
CLOAK_C = Ramp("#0c0408", "#14070d", "#1e0b14", "#2a101c", "#381626", "#481e31", "#58283d")
TRIM = Ramp("#2a200c", "#3e3014", "#56441e", "#6e5828")
LEATHER = Ramp("#0c0806", "#140e0a", "#1e150f", "#2a1d15", "#37261b")
EYE = ("#3a0604", "#a01e0a", "#ff5a24", "#ffb070")
CLEAR = (0, 0, 0, 0)


def paint(r, P_):
    """Values (SPEC §35.4): the apples, his heart and his eyes brightest (and warm, the only warm things on him), then
    the bone, ivory, darker in its hollows and mossed at the joints; the gold dull between; the bark, the leaves and the
    cloak the darkest."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    h = lambda c, i, j, k=0: R._h(c + str(k), i * 7 + j * 13)
    # bone: old ivory, pitted, a hairline crack now and then, darker toward its ends
    pits = lambda f, i, j: (-1.6 if h(f.cube.name + f.name, i, j) % 23 == 0 else -0.7 if h(f.cube.name + f.name, i, j, 1) % 11 == 0 else 0)
    long_bones = of("femur_", "tibia_", "fibula_", "humerus_", "radius_", "ulna_", "clav_")
    for c in long_bones:
        P_.paint(c, BONE, 5.4, pits, grad=1.2, rim=0.9, ao=1.2, noise=0.25)
    knobs = of("fhead_", "knee_", "hhead_", "elbow_", "tarsus_", "toes_", "heel_", "knuck_", "fist_", "palm_", "finger", "thumb_")
    for c in knobs:
        P_.paint(c, BONE, 5.0, pits, grad=0.8, rim=1.0, ao=1.0, noise=0.3)
    # the toes and the fingers: bones with dark gaps between them
    for c in of("toes_"):
        for fname in ("top", "north"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if i % 2 == 1:
                    f.set(i, j, BONE.at(2.2))
    for c in ("fist_r", "knuck_r"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                if j % 2 == 1:
                    f.set(i, j, BONE.at(2.0 if j < f.h - 1 else 1.6))  # the creases between the knuckles
    # the vertebrae: the columns banded, a disc between each
    for c in ("lumbar", "thoracic", "cervical", "lspine", "tspine"):
        P_.paint(c, BONE, 5.0, lambda f, i, j: (-2.4 if j % 2 == 1 else 0.4), grad=0.6, rim=0.6, ao=0.6, noise=0.2)
    for c in ("sacrum", "pubis") + tuple(of("ilium_", "scap_")):
        P_.paint(c, BONE, 5.2, pits, grad=1.0, rim=1.0, ao=1.0, noise=0.3)
    for c in of("rb", "rs", "rf"):
        P_.paint(c, BONE, 5.6, lambda f, i, j: (-0.8 if i % 5 == 4 else 0), grad=0.4, rim=0.8, ao=0.6, noise=0.2)
    P_.paint("sternum", BONE, 5.2, lambda f, i, j: (-1.8 if j in (2, 5) else 0), rim=0.8, noise=0.2)
    # the skull: a suture across the crown, cracks, the sockets black with a point of light deep in each, the nose a
    # dark hole, the teeth
    P_.paint("cranium", BONE, 5.8, lambda f, i, j: (-2.0 if f.name == "top" and i == f.w // 2 and j % 3 != 1 else
                                                     -1.4 if f.name == "west" and (i + j) % 7 == 3 else pits(f, i, j)), grad=1.0, rim=1.0, ao=1.2, noise=0.25)
    for c in ("brow", "cheek_l", "cheek_r", "maxilla", "chin", "jaw_l", "jaw_r"):
        P_.paint(c, BONE, 5.4, pits, grad=0.6, rim=0.9, ao=0.8, noise=0.2)
    f = P_.face("cranium", "north")
    for i, j in f.each():  # the sockets under the brow: his right at i 1-2, his left at 4-5
        if j >= 4 and i in (1, 2, 4, 5):
            f.set(i, j, BONE.at(0.4 if j == 5 else 0.9))
        elif j == 3 and i in (1, 2, 4, 5):
            f.set(i, j, BONE.at(2.4))
    f = P_.face("maxilla", "north")
    for i, j in f.each():
        if j < 2 and i == 2:
            f.set(i, j, BONE.at(0.6))  # the nose
        elif j == 2:
            f.set(i, j, BONE.at(7.4 if i % 2 == 0 else 1.6))  # the upper teeth
    f = P_.face("chin", "top")
    for i, j in f.each():
        if j == f.h - 1:
            f.set(i, j, BONE.at(7.2 if i % 2 == 1 else 1.6))
    f = P_.face("chin", "north")
    for i, j in f.each():
        if j == 0:
            f.set(i, j, BONE.at(7.2 if i % 2 == 1 else 1.4))  # the lower teeth
    with P_.eyes():
        f = P_.face("cranium", "north")
        g = P_.face("cranium", "north", glow=True)
        for i in (1, 5):
            f.set(i if i == 1 else 4, 5, hexc(EYE[2]))
            g.set(i if i == 1 else 4, 5, hexc(EYE[2], 230))
            g.set(i + 1 if i == 1 else 5, 5, hexc(EYE[1], 150))
            g.set(i if i == 1 else 4, 4, hexc(EYE[1], 110))
    # moss at his joints and on the crown of his skull, where the orchard's damp has settled
    for c in ("hhead_l", "hhead_r", "knee_l", "knee_r", "ilium_l", "ilium_r", "sacrum", "fhead_l", "fhead_r"):
        for fname in ("top", "north", "west", "east"):
            f = P_.face(c, fname)
            for i, j in f.each():
                if h(c + fname, i, j, 3) % 5 < 2 and (fname == "top" or j < 2):
                    f.set(i, j, MOSS.at(3.6 + (h(c, i, j, 4) % 3) * 0.6))
    f = P_.face("cranium", "top")
    for i, j in f.each():
        if h("cr", i, j, 5) % 4 == 0:
            f.set(i, j, MOSS.at(3.0 + (h("cr", i, j, 6) % 3) * 0.7))
    # the bark: dark, grained up its length, a crevice now and then
    bark = lambda f, i, j: (-1.6 if (i + h(f.cube.name, 0, j // 3)) % 3 == 0 else 0.6 if h(f.cube.name + f.name, i, j) % 9 == 0 else 0)
    for c in ("trunk0", "trunk1", "trunk2", "root0", "root1", "root2", "bough0", "bough1", "bough2", "bough3"):
        P_.paint(c, BARK, 3.4, bark, grad=0.8, rim=0.8, ao=0.8, noise=0.2)
    # the leaves: dark masses, lit along their tops, a few gone yellow; their hanging edges ragged (cut out)
    for c in ("leaf0a", "leaf0b", "leaf0c", "leaf0d", "leaf1a", "leaf2a", "leaf2b", "leaf3a", "sprig0", "sprig1"):
        def lp(f, i, j, c=c):
            k = h(c + f.name, i, j, 7)
            if f.name in SIDES and j == f.h - 1 and k % 3 != 0:
                return CLEAR
            if f.name == "bottom" and (i in (0, f.w - 1) or j in (0, f.h - 1)) and k % 2 == 0:
                return CLEAR
            if k % 29 == 0:
                return AUTUMN.at(2.0 + k % 3)
            return (1.4 if k % 7 == 0 else -1.2 if k % 5 == 0 else 0.3 if (i + j) % 3 == 0 else 0)
        P_.paint(c, LEAF, 4.2, lp, grad=1.6, rim=1.2, ao=1.4, noise=0.5)
    # the apples: red, a highlight high on their front, glowing; his heart brightest
    for c in APPLES + ("heart",):
        n = 3 if P_.cubes[c].w == 3 else 2
        P_.paint(c, APPLE, 4.6, lambda f, i, j: (1.6 if f.name in ("north", "top") and i == 0 and j == 0 else 0), grad=1.4, rim=0.8, ao=1.0)
        for fname in ("north", "south", "east", "west", "top"):
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                a = 230 if c == "heart" else 150
                g.set(i, j, (APPLE.c[7] if (i + j) % 2 == 0 else APPLE.c[6])[:3] + (a if j < n - 1 else a - 60,))
        P_.paint(c + "_s", BARK, 2.6, rim=0.4) if c != "heart" else None
    # the crown: tarnished gold gone green in its hollows, the stone red and lit
    for c in ("band", "pt0", "pt1", "pt2", "pt3", "pt4"):
        P_.paint(c, GOLD, 4.0, lambda f, i, j: (PATINA.at(1.8 + h(f.cube.name, i, j) % 3 * 0.6) if h(f.cube.name + f.name, i, j, 2) % 4 == 0 else
                                                1.2 if (i + j) % 3 == 0 else 0), grad=0.8, rim=1.4, ao=0.8)
    P_.paint("gem", APPLE, 5.0, rim=1.0)
    g = P_.face("gem", "north", glow=True)
    for i, j in g.each():
        g.set(i, j, APPLE.c[7][:3] + (200,))
    # the mantle: a fur gone grey and matted
    P_.paint("mantle", FUR, 3.4, lambda f, i, j: (0.9 if (i * 3 + j) % 4 == 0 else -0.8 if h("m" + f.name, i, j) % 5 == 0 else 0), grad=1.0, rim=1.0,
             ao=1.0, noise=0.5)
    # the cloak: a royal purple-red gone to rot, a frayed gold trim along its top, holes, its rags' ends torn
    for c in ("cloak0", "cloak1", "rag0", "rag1", "rag2"):
        def cp(f, i, j, c=c):
            k = h(c + f.name, i, j, 9)
            if f.name in ("north", "south") and k % 31 == 0 and 1 < j < f.h - 2:
                return CLEAR  # a moth hole
            if c.startswith("rag") and f.name in SIDES and j >= f.h - 2 and k % 2 == 0:
                return CLEAR
            if c == "cloak0" and f.name in ("north", "south") and j < 2:
                return TRIM.at(2.6 - j * 0.8 + (k % 3) * 0.3)
            if f.name == "south" and c in ("cloak0", "cloak1") and _arms(c, i, j, f.w):
                return TRIM.at(2.0 + (k % 3) * 0.4) if k % 7 else None  # the orchard's tree, faded gold, worn through here and there
            return (-0.8 if i % 4 == 0 else 0.4 if k % 6 == 0 else 0)
        P_.paint(c, CLOAK_C, 3.6, cp, grad=1.2, rim=0.6, ao=1.0, noise=0.4)
    # the sword: the blade a long bone, a dark marrow groove down its middle, cracked, its saw-teeth cut out of the edge;
    # the grip bound in leather; the guard two knobbed vertebrae; a small skull for the pommel
    for c in ("blade0", "blade1", "blade2", "btip"):
        P_.paint(c, BONE, 5.8, lambda f, i, j: (-2.2 if f.name in ("north", "south") and i == f.w // 2 and c != "btip" else
                                                -1.2 if h(f.cube.name + f.name, i, j) % 17 == 0 else 0.4 if f.name in ("east", "west") else 0),
                 grad=0.8, rim=1.0, ao=0.6, noise=0.2)
    P_.paint("serr", BONE, 6.0, lambda f, i, j: (CLEAR if f.name in ("north", "south", "west") and j % 2 == 1 else 0.6), rim=0.8)
    P_.paint("grip", LEATHER, 3.0, lambda f, i, j: (1.2 if (i + j) % 3 == 0 else 0), rim=0.6, noise=0.2)
    for c in ("guard", "gtip_l", "gtip_r"):
        P_.paint(c, BONE, 5.0, lambda f, i, j: (-2.0 if i % 3 == 2 else 0.5), grad=0.6, rim=1.0, ao=0.8, noise=0.2)
    P_.paint("pommel", BONE, 5.6, pits, rim=1.0, ao=0.8)
    f = P_.face("pommel", "north")
    for i, j in f.each():
        if j == 0 and i in (0, 2):
            f.set(i, j, BONE.at(0.6))  # its eyes
        elif j == 1 and i == 1:
            f.set(i, j, BONE.at(1.2))
    return P_.skin, P_.glow


def _arms(c, i, j, w):
    """The Orchard's arms on the back of his cloak: a tree, its round crown on the upper panel (a few leaves dark in
    it), its trunk and spreading roots on the lower (i across the south face, j down)."""
    x = i - (w - 1) / 2.0
    if c == "cloak0":
        y = j - 6.0
        return 2 <= j and x * x / 22.0 + y * y / 11.0 <= 1.0 and R._h("arms", i * 7 + j * 13) % 5 != 0
    return (abs(x) <= 0.6 and j <= 5) or (j == 5 and abs(x) <= 2) or (j == 6 and 2 <= abs(x) <= 3.5)


# ---- motion ----------------------------------------------------------------------------------------------------------

UP = (0, -1, 0)
RIB_OPEN = (48.0, 54.0, 58.0, 52.0)  # how far each pair of ribs swings out (degrees) when they open fully, top to bottom
RIB_LIFT, RIB_FLARE = 30.0, 20.0  # and how far they tip up and out then: they fan open like claws toward whoever faces him
GRIP_SIDE = 2.6  # the left hand takes the hilt this far off it (sword z), its fingers round it


def _abs(r, bone, rot):
    """A key for a bone given as its whole turn (the rest taken off: a pose holds what it adds to the rest)."""
    rest = r.by_name[bone].rot
    return tuple(rot[i] - rest[i] for i in range(3))


def _in(M, v):
    return R._mv(M, v)


def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), right="carry", left=None,
          rblade=None, lblade=None, rpole=None, lpole=None, legs=None, feet=("l", "r"), step=None, turn=None, jaw=0.0, ribs=0.0, curl=0.0,
          tap=0.0, more=None):
    """A whole-body key over his rest. He turns at the hips (hips: turn, at: x, up, z), the spine, the chest, the neck and
    the head. The feet stay where they stand (step: {side: (dx, dy, dz)} model pixels, y down) unless legs pose them
    (whole turns). right: "carry" (the sword on his shoulder, riding his chest; tap lifts it off it, pixels), None (his
    rest turns), or where the hand goes, (x, height, z) model pixels or ("on", bone, point), its blade along rblade
    (ydir, xdir; model directions, y down). left: None (it hangs as at rest), "grip" (on the hilt above the sword hand,
    two-handed) or a place as for the right, lblade its line. jaw: degrees open; ribs: 0 shut to 1 open wide; curl: his
    left fingers' bend, degrees (negative spreads them)."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    if jaw:
        p["jaw"] = (jaw, 0, 0)
    if ribs:
        for k in range(4):
            p[f"rib{k}_l"] = (-RIB_LIFT * ribs, -RIB_OPEN[k] * ribs, -RIB_FLARE * ribs)
            p[f"rib{k}_r"] = (-RIB_LIFT * ribs, RIB_OPEN[k] * ribs, RIB_FLARE * ribs)
    if curl and left != "grip":
        for k, f in enumerate(FINGERS):
            p[f] = (-curl * (1.0 + 0.12 * k), 0, 0)
        p["thumb_l"] = (-0.4 * curl, 0, 0.3 * curl)
    if legs:
        p.update({b: _abs(r, b, v) for b, v in legs.items()})
        feet = tuple(s for s in feet if "thigh_" + s not in legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
    fr = core.frames(r, core.as_sample(p))
    if right == "carry":
        Mc, Tc = fr["chest"]
        q = ik.v(Tc, _in(Mc, CARRY_AT[0]))
        q = (q[0], q[1] - tap, q[2])
        p.update(ik.limb(r, fr, "arm_r", "forearm_r", "hand_r", q, _in(Mc, CARRY_AT[3])))
        fr = core.frames(r, core.as_sample(p))
        p["hand_r"] = ik.orient(r, fr, "hand_r", _in(Mc, CARRY_AT[1]), _in(Mc, CARRY_AT[2]))
    elif right is not None:
        q = core.point(fr, right[1], right[2]) if right[0] == "on" else ik.model(right)
        _arm(r, p, fr, "r", q, rblade, rpole)
    if left is not None:
        fr = core.frames(r, core.as_sample(p))
        if left == "grip":
            Ms, _ = fr["sword"]
            q = core.point(fr, "sword", (0, GRIP2[1], GRIP_SIDE))
            _arm(r, p, fr, "l", q, (_in(Ms, (0, 0, -1)), _in(Ms, (0, 1, 0))), lpole)
            for k, f in enumerate(FINGERS):
                p[f] = (-78 - 4 * k, 0, 0)
            p["thumb_l"] = (-40, 0, 30)
        else:
            q = core.point(fr, left[1], left[2]) if left[0] == "on" else ik.model(left)
            _arm(r, p, fr, "l", q, lblade, lpole)
    return p


def _turned(A, B):
    """The angle (degrees) of the turn from frame A to frame B."""
    c = (sum(A[j][i] * B[j][i] for i in range(3) for j in range(3)) - 1) / 2
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


def _wrist(fr, fore, hand, rest):
    """How far a joint is turned from its rest (a hand against its forearm, an arm against the chest), degrees."""
    Mf, Mh = fr[fore][0], fr[hand][0]
    rel = [[sum(Mf[k][i] * Mh[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return _turned(rest, rel)


REACH = 0.0  # his arm's full reach, shoulder to hand (pixels), set by body()
_WRIST = {}  # each wrist's rest (its hand's turn in its forearm's frame), set by body()
POLES, ROLLS = 12, 12  # the ways round an elbow and a blade's rolls a key tries
_LAST = {}  # the elbow's pole of each arm's last key (a move's keys are made in the order they play): kept near it


def _fresh():
    """A new move or clip: its first key's elbows are free."""
    _LAST.clear()


def _angle(a, b):
    return math.degrees(math.acos(max(-1.0, min(1.0, ik.dot(ik.unit(a), ik.unit(b))))))


def _arm(r, p, fr, s, q, blade, pole):
    """Sends one hand (side s) to q, its blade along blade (ydir, xdir) when given. With pole None the elbow is free,
    and with xdir None so is the blade's roll (the edge twist on the haft then sets the edge): of the ways round the
    line from his shoulder to his hand, and the rolls, it takes the one that leaves his wrist and shoulder least turned
    from rest (never past what a joint does) and least moved from the key before (a move's keys are made in the order
    they play). So no joint gathers a whole turn over a move, and nothing flips over between keys."""
    arm, fore, hand = f"arm_{s}", f"forearm_{s}", f"hand_{s}"
    if blade is None:
        p.update(ik.limb(r, fr, arm, fore, hand, q, pole or _LAST.get(s, POLE[s])))
        _LAST[s] = _elbow(r, p, s)
        _LAST[hand] = core.frames(r, core.as_sample(p))[hand][0]
        return p
    ydir, xdir = blade
    y = ik.unit(ydir)
    if xdir is None:
        seed = (1.0, 0.0, 0.0) if abs(y[0]) < 0.9 else (0.0, 0.0, 1.0)
        e1 = ik.unit(ik.v(seed, y, -ik.dot(seed, y)))
        e2 = ik.cross(y, e1)
        rolls = [tuple(math.cos(2 * math.pi * k / ROLLS) * e1[i] + math.sin(2 * math.pi * k / ROLLS) * e2[i] for i in range(3)) for k in range(ROLLS)]
    else:
        rolls = [xdir]
    if pole is None:
        sh = fr[arm][1]
        ax = ik.unit(ik.sub(q, sh))
        base = _LAST.get(s, POLE[s])
        g1 = ik.v(base, ax, -ik.dot(base, ax))
        g1 = ik.unit(g1) if ik.dot(g1, g1) > 1e-6 else ik.unit(ik.cross(ax, (0.0, 0.0, 1.0)))
        g2 = ik.cross(ax, g1)
        poles = [tuple(math.cos(2 * math.pi * k / POLES) * g1[i] + math.sin(2 * math.pi * k / POLES) * g2[i] for i in range(3)) for k in range(POLES)]
    else:
        poles = [pole]
    best = None
    for pl in poles:
        t = dict(p)
        t.update(ik.limb(r, fr, arm, fore, hand, q, pl))
        f2 = core.frames(r, core.as_sample(t))
        sho = _wrist(f2, "chest", arm, _WRIST["arm_" + s])
        moved = _angle(_elbow_in(f2, s), _LAST[s]) if s in _LAST else 0.0
        for x in rolls:
            t2 = dict(t)
            t2[hand] = ik.orient(r, f2, hand, y, x)
            if len(rolls) == 1 and len(poles) == 1:
                best = (0, t2)
                break
            f3 = core.frames(r, core.as_sample(t2))
            wr = _wrist(f3, fore, hand, _WRIST[s])
            cost = wr + 0.6 * sho + 3 * max(0.0, wr - 150) + 3 * max(0.0, sho - 160) + 0.3 * moved
            if hand in _LAST:
                cost += 0.3 * _turned(_LAST[hand], f3[hand][0])
            if best is None or cost < best[0]:
                best = (cost, t2)
    p.clear()
    p.update(best[1])
    _LAST[s] = _elbow(r, p, s)
    _LAST[hand] = core.frames(r, core.as_sample(p))[hand][0]
    return p


def _elbow_in(fr, s):
    a, e, h = fr[f"arm_{s}"][1], fr[f"forearm_{s}"][1], fr[f"hand_{s}"][1]
    ax = ik.unit(ik.sub(h, a))
    d = ik.sub(e, a)
    off = ik.v(d, ax, -ik.dot(d, ax))
    n = math.sqrt(ik.dot(off, off))
    return tuple(c / n for c in off) if n > 1e-4 else ax


def _elbow(r, p, s):
    """Which way an arm's elbow points in a pose: off the line from its shoulder to its hand (model direction)."""
    fr = core.frames(r, core.as_sample(p))
    a, e, h = fr[f"arm_{s}"][1], fr[f"forearm_{s}"][1], fr[f"hand_{s}"][1]
    ax = ik.unit(ik.sub(h, a))
    d = ik.sub(e, a)
    off = ik.v(d, ax, -ik.dot(d, ax))
    n = math.sqrt(ik.dot(off, off))
    return tuple(c / n for c in off) if n > 1e-4 else _LAST.get(s, POLE[s])


def _place(r, p, bone, at, ydir, xdir):
    """The pose with one bone set free of its parent: its pivot at at (x, height, z) and turned so its +y lies along
    ydir and its +x as near xdir as that allows (model directions): a skull dropped on a heap, a sword on the floor."""
    p = dict(p)
    fr = core.frames(r, core.as_sample(p))
    b = r.by_name[bone]
    Mp, Tp = fr[b.parent.name]
    d = R._mv(core._t(Mp), ik.sub(ik.model(at), Tp))
    dp = tuple(d[i] - b.pivot[i] for i in range(3))
    rot = ik.orient(r, fr, bone, ydir, xdir)
    p[bone] = {"rot": rot, "pos": (dp[0], -dp[1], dp[2])}
    return p


FLIP = False
_REST = {}  # each bone's rest turn (set by body()): the same turn has two Euler triples, and which is nearer depends on it


def _near(bone, v, ref):
    """The key v for a bone (degrees over its rest) written as near as it goes to ref: each angle by whole turns, and
    the other Euler triple of the same turn when that one is nearer."""
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
                rot = _near(b, v.get("rot", (0, 0, 0)), prev.get(b, (0.0, 0.0, 0.0)))
                prev[b] = rot
                q[b] = dict(v, rot=rot)
                continue
            q[b] = _near(b, v, prev.get(b, (0.0, 0.0, 0.0)))
            prev[b] = q[b]
        out.append(q)
    return out


def _unwrap(mv):
    """A move's keys in the order its clip plays them, each as near as it goes to the one before."""
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
    """A recovery in two steps to his rest (or to settle), so nothing snaps home."""
    home = settle or _pose(r)
    return [(0.45, _mix(end, home, 0.45)), (0.7, _mix(end, home, 0.82))]


def _ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def _rest_feet(r):
    fr = core.frames(r, {})
    return {s: fr["foot_" + s][1] for s in "lr"}


def _stride(r, u, D, lift, stance, x=None):
    """A stride at phase u (the right foot planting at 0, the left at 0.5): each foot planted for a share of the cycle,
    sliding back under him, then swung forward through the air. Offsets from where it stands at rest (model px, y
    down), the body travelling D pixels a cycle; x: the feet's x (else where they stand)."""
    rest = _rest_feet(r)
    out = {}
    half = stance * D / 2
    for s, ph in (("r", 0.0), ("l", 0.5)):
        v = (u - ph) % 1.0
        if v < stance:
            z, dy = -half + 2 * half * v / stance, 0.0
        else:
            k = (v - stance) / (1 - stance)
            z, dy = half - 2 * half * _ease(k), -lift * math.sin(math.pi * k)
        out[s] = ((x[s] - rest[s][0]) if x else 0.0, dy, z - rest[s][2])
    return out


def _lift(r, p, gap=0.3, skip=()):
    """The pose with its hips raised (or lowered) until its lowest part lies on the floor: a heap, a body on its knees."""
    bones = [b.name for b in r.bones if b.name not in skip]
    low = core.lowest(r, core.as_sample(p), bones)
    q = dict(p)
    pv = q.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    pos = pv.get("pos", (0, 0, 0))
    q["pelvis"] = {"rot": pv.get("rot", (0, 0, 0)), "pos": (pos[0], pos[1] + (low - (core.GROUND - gap)), pos[2])}
    return q


# ---- the everyday clips ----------------------------------------------------------------------------------------------

def idle(r):
    """He stands tall with the sword on his shoulder: his dead weight sways slowly, his crowned skull tilts to look you
    over, his jaw falls a little open and clacks shut, the finger bones of his left hand curl and loosen, and now and
    then the blade lifts off his shoulder and taps back down on it."""
    k0 = _pose(r)

    def sway(a, y=0.0, tilt=(0, 0, 0), jaw=0.0, curl=0.0, tap=0.0, ribs=0.0):
        return _pose(r, hips=(0, 2.5 * a, 1.2 * a), at=(0.4 * a, y, 0), spine=(0, 0, -0.6 * a), chest=(0.6, -2.5 * a, -1.0 * a), neck=(0, 2 * a, 0),
                     head=tilt, jaw=jaw, curl=curl, tap=tap, ribs=ribs)
    _keyed(r, "idle", 5.6, [(0, k0), (0.9, sway(1, -0.3, (2, 7, 4), curl=18)), (1.8, sway(0.2, 0, (0, 2, 1), curl=8)),
                           (2.4, sway(-0.8, -0.3, (-5, -8, -5), jaw=9, curl=14)), (2.62, sway(-0.9, -0.3, (-3, -8, -5), curl=22)),
                           (3.4, sway(-0.6, 0, (1, -4, -2), curl=32, ribs=0.04)), (4.1, sway(0, -0.4, (3, 0, 0), curl=10, tap=1.8)),
                           (4.3, sway(0, 0, (1, 0, 0), curl=8, tap=-0.3)), (4.55, sway(0, -0.2, (1, 0, 0), tap=0.9)), (4.75, sway(0.1, 0, (0, 0, 0))),
                           (5.6, k0)], loop=True)


def walk(r):
    """A king in procession: long slow steps, each landing heavy, his hips swinging over the planted foot and his
    shoulders against them; the sword rides his shoulder, his left arm swings loose, its forearm a beat behind."""
    r.walk_stride = 1.5  # blocks a cycle (a step of each foot)
    D = r.walk_stride * 16
    T = 1.5
    keys = []
    n = 16
    for k in range(n + 1):
        u = k / n
        a = math.sin(2 * math.pi * u)
        bob = -1.1 * (0.5 + 0.5 * math.cos(4 * math.pi * u))
        swing = -16 * math.cos(2 * math.pi * u)
        lag = -8 - 10 * max(0.0, math.cos(2 * math.pi * (u - 0.1)))
        keys.append((T * u, _pose(r, at=(0.6 * a, bob, 0), hips=(1.5, 6 * a, -2 * a), spine=(1, 0, 1 * a), chest=(2 + 1.2 * (bob + 1.1), -8 * a, 1.5 * a),
                                  neck=(0, 3 * a, 0), head=(-1.5 * (bob + 1.1), 2 * a, -1 * a), tap=0.5 * (bob + 0.55),
                                  step=_stride(r, u, D, 3.2, 0.6), more={"arm_l": (swing, 0, -2), "forearm_l": (lag, 0, 0)})))
    _keyed(r, "walk", T, keys, loop=True)


def run(r):
    """He runs leaning into it, heavy, the sword bouncing on his shoulder and his left arm pumping, his skull thrust
    forward under the swaying crown of leaves."""
    r.run_stride = 2.6
    D = r.run_stride * 16
    T = 0.8
    keys = []
    n = 12
    for k in range(n + 1):
        u = k / n
        a, c = math.sin(2 * math.pi * u), math.cos(4 * math.pi * u)
        keys.append((T * u, _pose(r, hips=(12, 8 * a, -2 * a), at=(0.8 * a, -2.2 + 1.6 * c, -1.5), spine=(4, 0, 0), chest=(8, -10 * a, 2 * a),
                                  neck=(-4, 4 * a, 0), head=(-12, 3 * a, 0), tap=1.0 + 1.0 * c, step=_stride(r, u, D, 6.5, 0.4),
                                  more={"arm_l": (-26 * math.cos(2 * math.pi * u) - 14, 0, -6), "forearm_l": (-58, 0, 0)})))
    _keyed(r, "run", T, keys, loop=True)
    r.run_speed = (0.16, 0.32)


def turns(r):
    """Turning on the spot: a short step of one foot and then the other."""
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        a = _pose(r, hips=(0, 6 * sgn, 0), at=(0, -0.5, 0), chest=(0, 3 * sgn, 0), step={"l" if sgn > 0 else "r": (1.0 * sgn, -2.4, -1)})
        b = _pose(r, hips=(0, 3 * sgn, 0))
        c = _pose(r, hips=(0, 6 * sgn, 0), at=(0, -0.5, 0), chest=(0, 3 * sgn, 0), step={"r" if sgn > 0 else "l": (1.0 * sgn, -2.0, 1)})
        _keyed(r, nm, 0.44, [(0, _pose(r)), (0.11, a), (0.22, b), (0.33, c), (0.44, _pose(r))], loop=True)


def hurts(r):
    """A blow rattles him: his skull snaps back and away, his jaw flies open and chatters, his ribs shake."""
    for nm, side in (("hurt", 0), ("hurt_l", 1), ("hurt_r", -1)):
        hit = _pose(r, hips=(-3, -7 * side, 0), at=(-0.8 * side, -0.6, 1.4), spine=(-4, -4 * side, -3 * side), chest=(-9, -12 * side, -6 * side),
                    neck=(-4, -6 * side, -2 * side), head=(-16, -20 * side, -12 * side), jaw=24, tap=2.0, curl=-12,
                    more={"arm_l": (-16, 0, -14 - 10 * side), "forearm_l": (-24, 0, 0)})
        back = _pose(r, hips=(1, 2 * side, 0), at=(0, -0.3, -0.3), chest=(3, 4 * side, 2 * side), head=(5, 6 * side, 4 * side), jaw=6, tap=-0.4, curl=16)
        chat = _pose(r, chest=(1, 1 * side, 0), head=(2, 2 * side, 1 * side), jaw=13, curl=10)
        _keyed(r, nm, 0.6, [(0, _pose(r)), (0.07, hit), (0.2, back), (0.3, chat), (0.4, _mix(chat, _pose(r), 0.5)), (0.6, _pose(r))])


# the legs kneeling (whole turns): down on his left knee, the right foot planted ahead
KNEEL = {"thigh_l": (4, 0, -3), "shin_l": (90, 0, 0), "foot_l": (-34, 0, 0), "thigh_r": (-82, 0, 3), "shin_r": (84, 0, 0), "foot_r": (-2, 0, 0)}
PLANTED = ((0, 1, -0.05), (1, 0, 0))  # the sword driven into the floor: point down, its flat to the front
PLANTS = ("kneel", "rise")  # the clips with his sword's point driven into the floor
PLANT_AT = (-1.0, 40.0, -15.0)  # and his sword hand on its hilt then (x, height, z), the point in the floor before him


def _planted(r, hand=PLANT_AT, **kw):
    """Hands on the hilt of the sword stood point-down in the floor before him (the left over the right)."""
    return _pose(r, right=hand, rblade=PLANTED, rpole=(-0.7, 0.4, 0.6), left="grip", lpole=(0.7, 0.4, 0.6), **kw)


def reactions(r):
    """The kneel when his stance breaks (and how he waits for you): down on his left knee, the sword stood point-down
    in the floor before him, both hands on its hilt, his crowned head bowed. The rise: he pushes up on it, pulls it out
    and lays it back on his shoulder."""
    kneel = _planted(r, hand=(-1.0, 31.0, -15.0), hips=(6, 0, 0), at=(0, -12.5, -1), legs=KNEEL, spine=(6, 0, 0), chest=(8, 0, 0), neck=(10, 0, 0),
                     head=(26, 0, 0), jaw=6)
    sag = _pose(r, hips=(4, 0, 0), at=(0, -5, 0), chest=(8, 0, 0), head=(12, 0, 0), right=(-4, 34, -14), rblade=PLANTED, rpole=(-0.7, 0.4, 0.6), left=(8, 28, -6), jaw=8)
    _keyed(r, "kneel", 0.7, [(0, _pose(r)), (0.3, sag), (0.7, kneel)])
    push = _planted(r, hand=(-1.0, 36.0, -15.0), hips=(10, 0, 0), at=(0, -6, -1.5), chest=(10, 0, 0), head=(6, 0, 0), step={"r": (0, 0, -3)})
    pull = _pose(r, hips=(2, -6, 0), at=(0, -1.5, 0), chest=(-4, -10, 0), head=(-4, 6, 0), right=(-9, 46, -12), rblade=((-0.3, -0.85, 0.4), (1, 0, 0)), curl=20)
    _keyed(r, "rise", 1.0, [(0, kneel), (0.35, push), (0.65, pull), (0.85, _pose(r, at=(0, 0.4, 0), tap=1.2)), (1.0, _pose(r))])


# ---- falling apart and getting up ------------------------------------------------------------------------------------

HEAP_SKULL = (1.5, 17.0, -3.0)  # the skull's pivot on top of the heap (x, height, z)
SWORD_DOWN = ((-12.0, 2.0, -8.0), (-0.25, 0.0, -0.97), (0.97, 0.0, -0.25))  # the sword dropped: where, its blade's line, its flat's x


def _heap(r, skull=HEAP_SKULL, tilt=(0.12, 1.0, 0.06), yaw=0.35, jaw=22.0, ribs=0.0, sword=True, sag=1.0):
    """His bones in a heap: the hips on the floor, the legs splayed out flat before him, the ribcage fallen back over
    them, the arms on the floor at his sides, the skull sat upright on top with the tree still standing out of it, the
    sword on the floor. sag: 0 just landed (the cloak still up) to 1 settled."""
    p = {"pelvis": {"rot": (6, -8, 9), "pos": (0.5, -24, 1.5)}, "spine": (-34, 6, -6), "chest": (-34, -8, 9), "neck": (-10, 0, 0),
         "head": (0, 0, 0), "jaw": (jaw, 0, 0),
         "thigh_l": _abs(r, "thigh_l", (-84, -38, 0)), "shin_l": _abs(r, "shin_l", (12, 0, 0)), "foot_l": _abs(r, "foot_l", (-70, 0, 10)),
         "thigh_r": _abs(r, "thigh_r", (-80, 30, 0)), "shin_r": _abs(r, "shin_r", (20, 0, 0)), "foot_r": _abs(r, "foot_r", (-64, 0, -12)),
         "cloak0": (40 * sag, 0, 0), "cloak1": (20 * sag, 0, 0), "cloak2": (10 * sag, 0, 0)}
    if ribs:
        for k in range(4):
            p[f"rib{k}_l"] = (0, -RIB_OPEN[k] * ribs, 6 * ribs)
            p[f"rib{k}_r"] = (0, RIB_OPEN[k] * ribs, -6 * ribs)
    p = _lift(r, p, skip=("head", "jaw", "crown", "sword") + LEAVES + APPLES + tuple(f"bough{k}" for k in range(4)) +
              tuple(f"arm_{s}" for s in "lr") + tuple(f"forearm_{s}" for s in "lr") + tuple(f"hand_{s}" for s in "lr") + FINGERS + ("thumb_l",))
    fr = core.frames(r, core.as_sample(p))
    for s, at in (("l", (13.0, 2.2, -3.0)), ("r", (-14.0, 2.2, 0.0))):
        p.update(ik.limb(r, fr, "arm_" + s, "forearm_" + s, "hand_" + s, ik.model(at), (0.7 if s == "l" else -0.7, -0.6, 0.3)))
    for k, f in enumerate(FINGERS):
        p[f] = (-30 - 8 * k, 0, 0)
    c, sn = math.cos(yaw), math.sin(yaw)
    p = _place(r, p, "head", skull, tilt, (c, 0, sn))
    p["crown"] = (-8, 10, -16)
    if sword:
        p = _place(r, p, "sword", *SWORD_DOWN)
    return p


def _apart(r, f):
    """On the way down, f of the way (0 standing, 1 in the heap): his knees go and his hips drop, his ribcage tips back,
    and his skull, slower than the rest, hangs a moment over them before it drops."""
    h = _heap(r, skull=(HEAP_SKULL[0], HEAP_SKULL[1] + 18 * (1 - f) ** 1.5, HEAP_SKULL[2]), sag=f)
    return _mix(_pose(r), h, _ease(f))


def collapse(r):
    """Struck down, he falls apart: his skull snaps back and his knees go, his hips drop away under him and his legs
    splay, his ribcage falls back over them and the sword clatters down beside him; his skull comes down last onto the
    heap, bounces once, and settles there upright with the tree still standing out of it."""
    struck = _pose(r, hips=(-4, 0, 0), at=(0, -2, 1.5), chest=(-10, 0, 0), neck=(-6, 0, 0), head=(-22, 0, 4), jaw=26, tap=3.0, curl=-16,
                   more={"arm_l": (-30, 0, -26), "forearm_l": (-30, 0, 0)})
    buckle = _pose(r, hips=(10, -4, 4), at=(0, -9, 0), spine=(8, 0, 0), chest=(14, 0, 4), neck=(8, 0, 0), head=(18, 6, 6), jaw=14, tap=-2.0, curl=6)
    land = _heap(r, skull=(HEAP_SKULL[0], HEAP_SKULL[1] + 0.0, HEAP_SKULL[2]), jaw=30, sag=0.7)
    bounce = _heap(r, skull=(HEAP_SKULL[0] + 0.3, HEAP_SKULL[1] + 2.2, HEAP_SKULL[2] - 0.4), tilt=(0.2, 1.0, 0.1), yaw=0.5, jaw=12, sag=0.9)
    rest = _heap(r)
    _keyed(r, "collapse", 1.4, [(0, _pose(r)), (0.1, struck), (0.28, buckle), (0.45, _mix(buckle, _apart(r, 0.7), 0.6)), (0.6, _apart(r, 0.92)),
                               (0.74, land), (0.86, bounce), (1.0, _mix(bounce, rest, 0.7)), (1.12, rest), (1.4, rest)])


def reassemble(r):
    """He puts himself back together: the heap shudders, his legs draw in under him and his hips rise off the floor,
    his ribcage swings up onto them and the sword flies up into his hand, his skull lifts off the heap and hangs over
    his neck, and drops onto it with a twist; he stands, and his jaw clacks shut."""
    heap = _heap(r)
    shudder = _heap(r, tilt=(0.0, 1.0, 0.16), yaw=0.15, jaw=8)
    crouch = _pose(r, hips=(18, 4, -4), at=(0, -13, 2), spine=(14, 0, 0), chest=(22, 6, -4), neck=(10, 0, 0), head=(24, -6, 0),
                   right=(-12, 14, -10), rblade=((-0.3, 0.2, -0.9), (0.95, 0, -0.3)), left=(12, 6, -6), curl=20, jaw=10)
    crouch = _place(r, crouch, "head", (0.5, 52.0, -6.0), (0.05, 1.0, 0.2), (0.9, 0, 0.4))
    up = _pose(r, hips=(6, 2, 0), at=(0, -4, 0.5), spine=(4, 0, 0), chest=(6, 2, 0), neck=(2, 0, 0), right=(-9, 42, -10),
               rblade=((-0.2, -0.8, 0.55), (1, 0, 0)), curl=14, jaw=16)
    up = _place(r, up, "head", (0.0, 57.0, -1.0), (0.0, 1.0, 0.1), (0.95, 0, -0.3))
    caught = _pose(r, hips=(2, 0, 0), at=(0, -2, 0), chest=(4, 0, 0), neck=(6, -10, 0), head=(14, -22, 6), jaw=4, tap=1.6, curl=10)
    _keyed(r, "reassemble", 1.0, [(0, heap), (0.12, shudder), (0.2, heap), (0.42, crouch), (0.62, up), (0.74, caught),
                                  (0.86, _pose(r, at=(0, 0.6, 0), neck=(0, 4, 0), head=(-4, 8, -2), tap=-0.4)), (1.0, _pose(r))])


def death(r):
    """He dies falling apart for good: he staggers, the sword slides off his shoulder and falls, he drops to his knees
    and looks up into his tree, his jaw wide; his ribs fall open, and he comes down into the heap; his skull rolls off it
    onto its side and the tree bows over his bones."""
    stag = _pose(r, hips=(-5, 6, 0), at=(0, -1, 2.5), chest=(-10, 8, -4), head=(-16, 10, -6), jaw=20, tap=2.5, step={"r": (0, 0, 3)})
    knees = {"thigh_l": (2, 0, -4), "shin_l": (92, 0, 0), "foot_l": (-36, 0, 0), "thigh_r": (2, 0, 4), "shin_r": (92, 0, 0), "foot_r": (-36, 0, 0)}
    drop = _pose(r, hips=(2, 0, 0), at=(0, -13, 2), legs=knees, chest=(6, 0, 0), head=(10, 0, 0), right=(-11, 20, -6),
                 rblade=((-0.4, 0.3, -0.85), (0.9, 0, -0.4)), curl=24, jaw=10)
    drop = _place(r, drop, "sword", (-13.0, 6.0, -7.0), (-0.3, 0.35, -0.9), (0.95, 0, -0.3))
    look = _pose(r, hips=(-2, 0, 0), at=(0, -13, 2.5), legs=knees, spine=(-4, 0, 0), chest=(-10, 0, 0), neck=(-14, 0, 0), head=(-34, 6, 0),
                 right=(-13, 14, -2), rblade=((0, 1, 0), (1, 0, 0)), left=(13, 16, -2), curl=10, jaw=34, ribs=0.15)
    look = _place(r, look, "sword", *SWORD_DOWN)
    wide = dict(look)
    for k in range(4):
        wide[f"rib{k}_l"] = (0, -RIB_OPEN[k] * 0.7, 4)
        wide[f"rib{k}_r"] = (0, RIB_OPEN[k] * 0.7, -4)
    heap = _heap(r, jaw=34, ribs=0.7)
    falling = _mix(wide, _heap(r, skull=(HEAP_SKULL[0], HEAP_SKULL[1] + 12, HEAP_SKULL[2]), jaw=30, ribs=0.7, sag=0.5), 0.7)
    bounce = _heap(r, skull=(HEAP_SKULL[0] + 0.4, HEAP_SKULL[1] + 1.8, HEAP_SKULL[2]), tilt=(0.2, 1.0, 0.08), jaw=26, ribs=0.7)
    off = _bowed(r, _heap(r, jaw=30, ribs=0.7))
    _keyed(r, "death", 4.6, [(0, _pose(r)), (0.3, stag), (0.9, drop), (1.4, look), (1.9, wide), (2.4, falling), (2.7, heap), (2.9, bounce),
                            (3.1, heap), (3.7, _mix(heap, off, 0.55)), (4.1, off), (4.6, off)])


def _bowed(r, heap):
    """The skull rolled off the heap onto its left side, the tree bowed over the bones as low as it goes without going
    into the floor."""
    best = heap
    for k in range(1, 31):
        a = k * 3.0 * core.DEG
        tilt = (math.sin(a), math.cos(a), 0.1)
        p = _place(r, heap, "head", (HEAP_SKULL[0] + 4 + 4 * math.sin(a), HEAP_SKULL[1] - 7 * math.sin(a), HEAP_SKULL[2] - 1), tilt, (math.cos(a), -math.sin(a), 0.3))
        under = core.lowest(r, core.as_sample(p), ["head", "jaw", "crown"] + list(LEAVES) + list(APPLES) + [f"bough{j}" for j in range(4)])
        if under > core.GROUND - 0.3:
            break
        best = p
    return best


# ---- the moves -------------------------------------------------------------------------------------------------------

EDGE = ("sword", (0, 20, 0), (-3.5, 20, 0))  # the blade's middle and its saw-toothed edge: the edge leads his cuts
SWORD_HITS = [("sword", (0, 34, 0), 2.2), ("sword", (0, 20, 0), 2.4), ("sword", (0, 8, 0), 2.0)]
GROUND = [(["sword"], [("hand_r", 0, 40), ("forearm_r", 0, 30)])]


def _unit(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def _arc(u1, u2, th):
    """The blade's line th degrees round the arc from u1 toward u2 (model directions, at right angles), its roll left
    free: the keys of a long cut lie on its arc, so the turn between them never takes the short way through his
    side."""
    c, s = math.cos(math.radians(th)), math.sin(math.radians(th))
    return tuple(c * a + s * b for a, b in zip(u1, u2)), None


def _lerp(a, b, f):
    """Pose arguments part way from a to b: turns, places and numbers in between, the rest from the nearer."""
    if isinstance(a, dict) or isinstance(b, dict):
        a, b = a or {}, b or {}
        return {k: _lerp(a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0)), f) for k in set(a) | set(b)}
    if isinstance(a, tuple) and isinstance(b, tuple) and len(a) == len(b):
        return tuple(_lerp(x, y, f) for x, y in zip(a, b))
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return a + (b - a) * f
    return b if f >= 0.5 else a


UP_RIGHT, DOWN_LEFT, AHEAD, DOWN = _unit((-1, -1, 0)), _unit((1, 1, 0)), (0, 0, -1), (0, 1, 0)
RIGHT, BEHIND = (-1, 0, 0), (0, 0, 1)


def cleave(r):
    """The Royal Cleave (LIGHT): the sword comes up off his shoulder, drawn back high over it as he turns away; then it
    comes over the top and down across in front of him in one long diagonal, his left foot stepping in, and ends low on
    his left."""
    A = dict(hips=(0, -10, 0), at=(0, 0.5, 1), spine=(0, 0, 0), chest=(-4, -18, -4), neck=(0, 6, 0), head=(-2, 10, 0), left=(11, 38, -10), curl=-10,
             step={"l": (0, 0, 0)})
    B = dict(hips=(4, 12, 0), at=(0, -3, -3), spine=(4, 6, 0), chest=(8, 24, 4), neck=(2, -8, 0), head=(4, -14, 0), left=(13, 34, 4), curl=14,
             step={"l": (0, 0, -6)})
    C = dict(hips=(5, 16, 0), at=(0, -3.6, -3.4), spine=(4, 8, 0), chest=(10, 30, 5), neck=(2, -10, 0), head=(6, -18, 0), left=(13, 34, 6), curl=16,
             step={"l": (0, 0, -6)})

    def key(f, th, hand, a=A, b=B):
        return _pose(r, right=hand, rblade=_arc(UP_RIGHT, AHEAD, th), **_lerp(a, b, f))
    cock = key(0, -55, (-9, 60, 2))
    over = key(0.2, 5, (-8, 63, -6))
    ahead = key(0.55, 70, (-4, 52, -15))
    hit = key(1.0, 128, (5, 30, -16))
    after = key(1.0, 152, (9, 24, -10), B, C)
    return Move("cleave", "LIGHT", 10, 4, 14, title="Royal Cleave", track=8.0, rng=(0, 4.6), kinds=("near",), damage=1.0, knock=0.6, plant=(),
                hit=SWORD_HITS, lag=ON_TIME, through=_mix(after, _pose(r), -0.06), edge=EDGE,
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.4)), (1.0, cock)], "active": [(0.2, over), (0.45, ahead), (0.75, hit), (1.0, after)],
                        "recover": _back(r, after)})


def cleave_b(r):
    """The backhand after it (LIGHT): from low on his left, the blade drawn back behind him there, it comes back up across
    in front of him to high over his right shoulder, his right foot stepping in; and it comes down onto the shoulder."""
    A = dict(hips=(2, 14, 0), at=(0, -2, 0.5), spine=(0, 0, 0), chest=(6, 24, 3), neck=(0, -8, 0), head=(2, -16, 0), left=(12, 36, 8), curl=12,
             step={"r": (0, 0, 0)})
    B = dict(hips=(-2, -12, 0), at=(0, -1.5, -3), spine=(0, 0, 0), chest=(-6, -22, -4), neck=(0, 6, 0), head=(-4, 12, 0), left=(13, 34, -6), curl=-8,
             step={"r": (0, 0, -5)})
    C = dict(hips=(-3, -15, 0), at=(0, -1.0, -3.2), spine=(0, 0, 0), chest=(-8, -26, -5), neck=(0, 8, 0), head=(-6, 14, 0), left=(13, 34, -6), curl=-6,
             step={"r": (0, 0, -5)})

    def key(f, th, hand, a=A, b=B):
        return _pose(r, right=hand, rblade=_arc(DOWN_LEFT, AHEAD, th), **_lerp(a, b, f))
    low = key(0, -45, (9, 30, -2))
    under = key(0.25, 15, (8, 30, -9))
    ahead = key(0.55, 75, (2, 39, -16))
    hit = key(1.0, 130, (-7, 50, -15))
    after = key(1.0, 190, (-11, 58, -6), B, C)
    return Move("cleave_b", "LIGHT", 8, 4, 14, title="Royal Cleave", track=8.0, rng=(0, 4.4), kinds=("near",), damage=1.0, knock=0.8, plant=(),
                hit=SWORD_HITS, lag=ON_TIME, through=_mix(after, _pose(r), -0.05), edge=EDGE,
                stages={"windup": [(0.3, _mix(_pose(r), low, 0.4)), (1.0, low)], "active": [(0.2, under), (0.45, ahead), (0.75, hit), (1.0, after)],
                        "recover": _back(r, after)})


def crush(r):
    """Bone Crush (HEAVY): both hands on the hilt, he swings the blade back over his right shoulder to hang down his back
    and rises tall, leaning back; holds it there; then brings it over and down into the floor before him, dropping
    into a lunge with it, his crown of leaves thrown forward."""
    gather = _pose(r, hips=(0, -6, 0), at=(0, 0.5, 0.5), chest=(-4, -10, 0), head=(-4, 6, 0), right=(-7, 52, -9), rblade=((-0.1, -0.9, 0.4), (1, 0, 0)),
                   left="grip")
    back = _pose(r, hips=(-4, -8, 0), at=(0, 1.5, 1.5), spine=(-4, 0, 0), chest=(-12, -10, 0), neck=(-2, 4, 0), head=(-8, 8, 0), right=(-9, 62, 3),
                 rblade=((0.05, 0.6, 0.8), (1, 0, 0)), left="grip", step={"l": (0, -0.8, 0), "r": (0, -0.8, 0)})
    peak = _pose(r, hips=(-6, -8, 0), at=(0, 2.2, 2.0), spine=(-6, 0, 0), chest=(-16, -10, 0), neck=(-2, 4, 0), head=(-10, 8, 0), right=(-9, 65, 4),
                 rblade=((0.05, 0.85, 0.5), (1, 0, 0)), left="grip", step={"l": (0, -1.2, 0), "r": (0, -1.2, 0)})
    over = _pose(r, hips=(6, -4, 0), at=(0, -1, -1), spine=(4, 0, 0), chest=(4, -4, 0), head=(2, 2, 0), right=(-5, 62, -10),
                 rblade=((0.0, -0.75, -0.66), (1, 0, 0)), left="grip", step={"l": (0, -2.5, -4)})
    hit = _pose(r, hips=(22, 0, 0), at=(0, -8, -3.5), spine=(10, 0, 0), chest=(18, 0, 0), neck=(4, 0, 0), head=(6, 0, 0), right=(-3, 31, -18),
                rblade=((0.0, 0.76, -0.65), (1, 0, 0)), left="grip", step={"l": (0, 0, -8)})
    held = _pose(r, hips=(24, 0, 0), at=(0, -8.6, -3.8), spine=(11, 0, 0), chest=(20, 0, 0), neck=(5, 0, 0), head=(8, 0, 0), right=(-3, 30, -18.5),
                 rblade=((0.0, 0.78, -0.63), (1, 0, 0)), left="grip", step={"l": (0, 0, -8)})
    wrench = _pose(r, hips=(10, -4, 0), at=(0, -4, -2), chest=(4, -6, 0), head=(0, 4, 0), right=(-5, 42, -14), rblade=((-0.05, -0.2, -0.98), (1, 0, 0)),
                   left="grip", step={"l": (0, 0, -4)})
    return Move("crush", "HEAVY", 16, 6, 20, title="Bone Crush", hold=6, track=4.0, ability="Bone Crush", rng=(0, 4.2), kinds=("area", "near"),
                damage=1.4, knock=1.2, disc=2.6, disc_at="hit", plant=(), hit=SWORD_HITS[:2], lag=ON_TIME, through=held, ground=GROUND, bite=0.85,
                stages={"windup": [(0.3, gather), (0.65, back), (1.0, peak)], "active": [(0.4, over), (0.85, hit), (1.0, held)],
                        "recover": [(0.4, wrench), (0.75, _mix(wrench, _pose(r), 0.7))]})


def _reach(r, th, dip, f=0.92, **kw):
    """A key with his sword arm held out straight from his shoulder and the blade on along its line: th degrees round
    from his right toward his back and dip below level, in his chest's frame, the hand at f of his arm's reach; the
    blade's edge leads round the way th grows. kw: the rest of the key, as _pose."""
    fr = core.frames(r, core.as_sample(_pose(r, right=None, **{k: v for k, v in kw.items() if k not in ("left", "curl")})))
    Mc, Ts = fr["chest"][0], fr["arm_r"][1]
    c, s, cd, sd = (math.cos(math.radians(th)), math.sin(math.radians(th)), math.cos(math.radians(dip)), math.sin(math.radians(dip)))
    d = _in(Mc, tuple(cd * (c * a + s * b) + sd * g for a, b, g in zip(RIGHT, BEHIND, DOWN)))
    edge = _in(Mc, tuple(s * a - c * b for a, b in zip(RIGHT, BEHIND)))
    q = ik.v(Ts, d, f * REACH)
    return _pose(r, right=ik.height(q), rblade=(d, edge), **kw)


def sweep(r):
    """For whoever is behind him (LIGHT): he looks back over his right shoulder and takes the sword off it out to his
    side, his arm straight; then turns into it and sweeps it round low behind him, the blade on along his arm, from his
    right to his back."""
    # (a turn up the spine of + yaw turns him to his right: his front toward his right, his right side back)
    wind = _reach(r, -40, 8, hips=(0, -6, 0), at=(0, 0.3, 0), chest=(0, -8, 0), neck=(0, 18, 0), head=(0, 46, 0), left=(12, 36, -4), curl=8)
    side = _reach(r, 10, 24, hips=(0, 10, 0), at=(0, -2.5, 0.4), spine=(4, 2, 0), chest=(6, 8, 0), neck=(0, 14, 0), head=(0, 40, 0),
                  left=(9, 33, -10), step={"r": (0, 0, 1)})
    hit = _reach(r, 62, 30, hips=(0, 22, 0), at=(0, -4.5, 1), spine=(8, 6, 0), chest=(10, 14, 0), neck=(0, 10, 0), head=(0, 34, 0),
                 left=(6, 31, -13), step={"r": (0, 0, 2)})
    after = _reach(r, 96, 26, hips=(0, 28, 0), at=(0, -4.5, 1.2), spine=(8, 8, 0), chest=(10, 18, 0), neck=(0, 8, 0), head=(0, 30, 0),
                   left=(4, 32, -13), step={"r": (0, 0, 2)})
    return Move("sweep", "LIGHT", 10, 4, 14, title="", track=0.0, rng=(0, 4.2), kinds=("behind",), damage=0.9, knock=1.0, plant=(),
                hit=SWORD_HITS, lag=ON_TIME, through=_mix(after, _pose(r), -0.04),
                stages={"windup": [(0.3, _mix(_pose(r), wind, 0.4)), (1.0, wind)], "active": [(0.3, side), (0.65, hit), (1.0, after)],
                        "recover": [(0.45, _mix(after, wind, 0.4)), (0.75, _mix(wind, _pose(r), 0.75))]})


MARCH = 7.0  # blocks the Royal March covers


def march(r):
    """The Royal March (a gap closer): the sword comes down off his shoulder to trail low behind him and he leans into
    it; then he strides at you, the blade dragging behind him and his left arm pumping, and as he arrives heaves it up
    in both hands through you, from low behind him to high in front."""
    trail = dict(right=(-11, 26, 6), rblade=_arc(DOWN, AHEAD, -73))
    down = _pose(r, hips=(4, -4, 0), at=(0, -1, 1), chest=(4, -6, 0), head=(-4, 4, 0), right=(-11, 38, 7), rblade=_arc(DOWN, AHEAD, -95), left=(10, 36, -8),
                 curl=14)
    load = _pose(r, hips=(8, -6, 0), at=(0, -2.5, 1.5), spine=(4, 0, 0), chest=(8, -8, 0), head=(-6, 6, 0), left=(10, 36, -10), curl=24,
                 step={"r": (0, 0, 3)}, **trail)
    D = MARCH * 16 / 1.6  # px a stride cycle (1.6 cycles over the march)
    act = []
    n = 8
    for k in range(1, n + 1):
        u = 0.76 * k / n
        ph = (1.6 * k / n) % 1.0
        a, c = math.sin(2 * math.pi * ph), math.cos(4 * math.pi * ph)
        act.append((u, _pose(r, hips=(14, 6 * a, 0), at=(0, -2 + 1.4 * c, -1), spine=(4, 0, 0), chest=(10, -8 * a, 0), neck=(-4, 0, 0), head=(-14, 4 * a, 0),
                             left=(10, 34 + 4 * a, -6 - 10 * a), curl=30, step=_stride(r, ph, D, 6.0, 0.4), **trail)))
    under = _pose(r, hips=(18, 0, 0), at=(0, -3.5, -2.5), spine=(6, 0, 0), chest=(12, -4, 0), neck=(-4, 0, 0), head=(-12, 0, 0), right=(-6, 32, -6),
                  rblade=_arc(DOWN, AHEAD, 35), left="grip", step={"l": (0, 0, -6)})
    ahead = _pose(r, hips=(6, -2, 0), at=(0, -2, -3.5), spine=(0, 0, 0), chest=(0, -6, 0), neck=(-2, 0, 0), head=(-10, 2, 0), right=(-4, 36, -17),
                  rblade=_arc(DOWN, AHEAD, 72), left="grip", step={"l": (0, 0, -7)})
    heave = _pose(r, hips=(-4, -4, 0), at=(0, -1, -4), spine=(-4, 0, 0), chest=(-10, -6, 0), neck=(0, 2, 0), head=(-10, 4, 0), right=(-4, 52, -16),
                  rblade=_arc(DOWN, AHEAD, 136), left="grip", step={"l": (0, 0, -7)})
    act += [(0.85, under), (0.92, ahead), (1.0, heave)]
    end = _pose(r, hips=(-6, -5, 0), at=(0, -0.8, -4.4), spine=(-5, 0, 0), chest=(-12, -7, 0), head=(-12, 5, 0), right=(-5, 58, -12),
                rblade=_arc(DOWN, AHEAD, 150), left="grip", step={"l": (0, 0, -7)})
    return Move("march", "HEAVY", 16, 16, 20, title="Royal March", track=6.0, lock=12, advance=MARCH, ability="Royal March", rng=(5, 14), kinds=("gap",),
                damage=1.3, knock=1.4, plant=(), hit=SWORD_HITS[:2] + [("chest", (0, -6, -4), 7.0)], lag=ON_TIME, through=end,
                stages={"windup": [(0.3, _mix(_pose(r), down, 0.55)), (0.55, down), (1.0, load)], "active": act, "recover": _back(r, end)})


def rise(r):
    """Rise (the dead get up): he drives the sword point-down into the floor before him and leans on its hilt; his left
    hand comes up from his side and lifts, slowly, palm up, its fingers spreading, his skull lifting and his jaw
    opening; and it closes into a fist."""
    held = dict(right=PLANT_AT, rblade=PLANTED, rpole=(-0.7, 0.4, 0.6))
    plant = _pose(r, hips=(6, 0, 0), at=(0, -2.5, -0.5), chest=(8, 0, 0), head=(16, 0, 0), jaw=4, left=(11, 31, -7), curl=24, **held)
    palm = ((0.05, 0.2, -1.0), (-1.0, 0, 0))

    def lift(f, curl, jaw):
        return _pose(r, hips=(4 - 6 * f, 0, 0), at=(0, -2 + 2.4 * f, -0.5), chest=(6 - 14 * f, 4 * f, 0), neck=(-4 * f, 0, 0), head=(10 - 26 * f, 6 * f, 0),
                     left=(11, 32 + 26 * f, -11 - 2 * f), lblade=palm, curl=curl, jaw=jaw, ribs=0.12 * f, **held)
    act = [(0.2, lift(0.0, 40, 6)), (0.5, lift(0.55, -6, 18)), (0.78, lift(1.0, -26, 30)), (0.9, lift(1.05, 60, 34)), (1.0, lift(1.0, 78, 22))]
    return Move("rise", "NONE", 12, 26, 14, title="Rise", track=0.0, ability="Rise", rng=(0, 99), kinds=("summon",), damage=0, knock=0, plant=(),
                lag=ON_TIME, through=lift(0.95, 76, 16),
                stages={"windup": [(0.4, _mix(_pose(r), plant, 0.45)), (1.0, plant)], "active": act, "recover": _back(r, lift(0.9, 70, 10))})


def spikes(r):
    """Rib Spikes (the signature): he takes the sword off his shoulder and flings his arms out wide, arching back, his
    jaw opening; his ribs crack open from his breastbone and tremble there, the apple of his heart blazing between
    them; and they burst wide open like doors, the spikes flying out of him, his arms thrown back."""
    def wide(f, ribs, jaw, tr=0.0):
        return _pose(r, hips=(-4 * f, 0, tr), at=(0, 0.6 * f, 1.2 * f), spine=(-3 * f, 0, 0), chest=(-8 * f, 0, tr), neck=(-4 * f, 0, 0), head=(-12 * f, 0, -tr),
                     right=(-9 - 11 * f, 46 - 2 * f, -6 + 4 * f), rblade=((-0.9, -0.3, 0.15), (0.1, 0, -1)), rpole=(-0.3, 0.8, 0.4),
                     left=(10 + 11 * f, 30 + 14 * f, -2 + 2 * f), lblade=((1, 0.2, 0), (0, 0, -1)), lpole=(0.3, 0.8, 0.4), curl=-20 * f, jaw=jaw, ribs=ribs)
    wind = [(0.25, wide(0.4, 0.05, 6)), (0.55, wide(1.0, 0.3, 16)), (0.7, wide(1.0, 0.42, 18, 2)), (0.8, wide(1.0, 0.36, 18, -2)),
            (0.9, wide(1.0, 0.46, 20, 2)), (1.0, wide(1.0, 0.4, 20, -1.5))]
    burst = _pose(r, hips=(-8, 0, 0), at=(0, 1.0, 2.5), spine=(-6, 0, 0), chest=(-16, 0, 0), neck=(-6, 0, 0), head=(-22, 0, 0), right=(-21, 46, 2),
                  rblade=((-0.9, -0.35, 0.25), (0.1, 0, -1)), rpole=(-0.3, 0.8, 0.4), left=(22, 46, 2), lblade=((1, 0.1, 0.2), (0, 0, -1)), lpole=(0.3, 0.8, 0.4),
                  curl=-26, jaw=36, ribs=1.0)
    held = _pose(r, hips=(-6, 0, 0), at=(0, 0.8, 2.0), spine=(-5, 0, 0), chest=(-13, 0, 0), neck=(-5, 0, 0), head=(-18, 0, 0), right=(-21, 45, 1),
                 rblade=((-0.9, -0.3, 0.2), (0.1, 0, -1)), rpole=(-0.3, 0.8, 0.4), left=(22, 45, 1), lblade=((1, 0.15, 0.1), (0, 0, -1)), lpole=(0.3, 0.8, 0.4),
                 curl=-22, jaw=30, ribs=0.96)
    closing = wide(0.6, 0.45, 12)
    return Move("spikes", "PERILOUS", 26, 10, 22, title="Rib Spikes", signature=True, track=4.0, ability="Rib Spikes", rng=(0, 9), kinds=("area", "ranged"),
                damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(held, closing, 0.2),
                stages={"windup": wind, "active": [(0.3, burst), (1.0, held)], "recover": [(0.4, closing), (0.72, _mix(closing, _pose(r), 0.7))]})


def wake(r):
    """His intro, played as he gets up off his knee (the rise carries his body and the sword): his skull lifts and his
    jaw falls open; then, on his feet, he swings the sword off his shoulder in a wide arc out to his right and round in
    front of him, and lays it back on his shoulder with a clack of his jaw."""
    look = _pose(r, neck=(-4, 0, 0), head=(-10, 0, 0), jaw=14)
    roar = _pose(r, at=(0, 0.6, 0.5), chest=(-8, 0, 0), neck=(-6, 0, 0), head=(-20, 0, 0), jaw=32, ribs=0.18, tap=1.5, curl=-20,
                 more={"arm_l": (-10, 0, -26)})
    out = _pose(r, hips=(0, -10, 0), chest=(-2, -18, 0), head=(-4, 14, 0), right=(-20, 44, -8), rblade=((-0.95, 0.0, -0.3), (0.1, 1, 0)),
                rpole=(-0.4, 0.8, 0.4), jaw=12, curl=10)
    across = _pose(r, hips=(2, 10, 0), at=(0, -1, -1), chest=(4, 18, 0), head=(0, -10, 0), right=(4, 40, -17), rblade=((0.8, 0.1, -0.6), (-0.2, 1, 0)),
                   jaw=8, curl=14, step={"l": (0, 0, -3)})
    lay = _pose(r, at=(0, -0.4, 0), tap=2.0, head=(4, 0, 0), jaw=10)
    clack = _pose(r, at=(0, -0.8, 0), tap=-0.6, head=(6, 0, 0))
    return Move("wake", "NONE", 16, 32, 10, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME, through=clack,
                stages={"windup": [(0.5, _mix(_pose(r), look, 0.5)), (1.0, look)],
                        "active": [(0.22, roar), (0.48, out), (0.7, across), (0.86, lay), (1.0, clack)], "recover": _back(r, clack)})


# ---- turning the real way between keys -------------------------------------------------------------------------------

def _quat(M):
    t = M[0][0] + M[1][1] + M[2][2]
    if t > 0:
        w = math.sqrt(1 + t) * 2
        return (0.25 * w, (M[2][1] - M[1][2]) / w, (M[0][2] - M[2][0]) / w, (M[1][0] - M[0][1]) / w)
    i = max(range(3), key=lambda k: M[k][k])
    j, k = (i + 1) % 3, (i + 2) % 3
    w = math.sqrt(1 + M[i][i] - M[j][j] - M[k][k]) * 2
    q = [0.0, 0.0, 0.0, 0.0]
    q[0] = (M[k][j] - M[j][k]) / w
    q[1 + i] = 0.25 * w
    q[1 + j] = (M[j][i] + M[i][j]) / w
    q[1 + k] = (M[k][i] + M[i][k]) / w
    return tuple(q)


def _matrix(q):
    w, x, y, z = q
    return ((1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)),
            (2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)),
            (2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)))


def _euler(M):
    """The turn (x, y, z degrees, applied z, y, x like a model part) of a frame."""
    b = math.asin(max(-1.0, min(1.0, -M[2][0])))
    return math.degrees(math.atan2(M[2][1], M[2][2])), math.degrees(b), math.degrees(math.atan2(M[1][0], M[0][0]))


def _real_turns(r, names):
    """Each clip's turns resampled every tick the real way: the keys stay, but between them a joint turns along the
    shortest way from one to the next (its rest and key as one turn, eased through the keys either side), not along
    the angles, which can swing it a long way round where two nearby turns have far-apart angles (an arm raised
    and swung). Written as angles each as near as they go to the tick before. A track it can't bring back to its own
    last key that way (it would turn a whole way round) is left as it was."""
    for name in names:
        c = r.clips[name]
        n = int(round(c.length * 20))
        for key, keys in list(c.tracks.items()):
            bone, tgt = key
            if tgt != "rot" or len(keys) < 3:
                continue
            rest = r.by_name[bone].rot
            qs = []
            for _, v, _ in keys:
                q = _quat(R._rot(*((rest[i] + v[i]) * core.DEG for i in range(3))))
                if qs and sum(a * b for a, b in zip(q, qs[-1])) < 0:
                    q = tuple(-a for a in q)
                qs.append(q)
            out, prev = [], tuple(keys[0][1])
            for k in range(n + 1):
                t = min(c.length, k / 20)
                i = max(0, min(len(keys) - 2, max(j for j in range(len(keys)) if keys[j][0] <= t + 1e-9) if t >= keys[0][0] else 0))
                t1, t2 = keys[i][0], keys[i + 1][0]
                a = 0.0 if t2 <= t1 else max(0.0, min(1.0, (t - t1) / (t2 - t1)))
                q0, q1, q2, q3 = qs[max(0, i - 1)], qs[i], qs[i + 1], qs[min(len(qs) - 1, i + 2)]
                q = [R._catmull(a, q0[m], q1[m], q2[m], q3[m]) for m in range(4)]
                nq = math.sqrt(sum(x * x for x in q))
                tot = _euler(_matrix(tuple(x / nq for x in q)))
                best = None
                for g in (tot, (tot[0] + 180, 180 - tot[1], tot[2] + 180)):
                    e = tuple(g[m] - rest[m] for m in range(3))
                    e = tuple(e[m] + 360 * round((prev[m] - e[m]) / 360) for m in range(3))
                    d = sum(abs(e[m] - prev[m]) for m in range(3))
                    if best is None or d < best[0]:
                        best = (d, e)
                prev = best[1]
                out.append((t, prev, "cat"))
            if any(abs(out[-1][1][m] - keys[-1][1][m]) > 0.5 for m in range(3)) or any(abs(out[0][1][m] - keys[0][1][m]) > 0.5 for m in range(3)):
                continue
            out[0], out[-1] = (out[0][0], tuple(keys[0][1]), "cat"), (out[-1][0], tuple(keys[-1][1]), "cat")
            c.tracks[key] = out


def moves(r):
    out = []
    for f in (cleave, cleave_b, crush, sweep, march, rise, spikes, wake):
        _fresh()
        out.append(f(r))
    return out


def clips(r):
    plans.biped_locomotion(r, P, weight=1.4, temper="regal")
    for f in (idle, walk, run, turns, hurts, reactions, collapse, reassemble, death):
        _fresh()
        f(r)
    mv = [_unwrap(m) for m in moves(r)]
    for m in mv:
        stage_clips(r, m)
    _real_turns(r, [n for n in r.clips if n not in ("death", "collapse", "reassemble")])
    # his sword (and his hands) stay out of the floor between the keys, the wrist turning first; not while he lies in
    # pieces (those poses put it on the floor themselves) or kneels on it (its point driven in)
    core.ground_pass(r, GROUND, clips=[n for n in r.clips if n not in ("death", "collapse") + PLANTS])
    return mv


FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},  # he waits on one knee, his hands on his planted sword, and gets up
    "phases": [
        {"pool": [
            {"move": "cleave", "weight": 3, "when": "near", "then": [["cleave_b"], ["cleave_b", "crush"]]},
            {"move": "crush", "weight": 2, "when": "near"},
            {"move": "spikes", "weight": 2},
            {"move": "march", "weight": 1, "when": "far", "then": [["cleave"]]},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
        # the dead get up with the second phase, as in DeepBestiary: Rise, and the Royal March more often
        {"transition": "rise", "pool": [
            {"move": "cleave", "weight": 3, "when": "near", "then": [["cleave_b"], ["cleave_b", "crush"]]},
            {"move": "crush", "weight": 2, "when": "near"},
            {"move": "march", "weight": 3, "when": "far", "then": [["cleave"], ["crush"]]},
            {"move": "spikes", "weight": 2},
            {"move": "rise", "weight": 1},
            {"move": "sweep", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}

# His sounds (SPEC §35.8): bone on bone and rattling, the skeleton's and the wither skeleton's voices, the wood and
# leaves of his tree; chosen on paper, not heard in game.
SOUNDS = {
    "tell_light": [("entity.skeleton.ambient", 1.0, 0.6), ("block.bone_block.hit", 0.8, 0.7)],
    "tell_heavy": [("entity.wither_skeleton.ambient", 1.2, 0.6), ("block.bone_block.place", 1.0, 0.5), ("block.azalea_leaves.break", 0.8, 0.7)],
    "tell_perilous": [("entity.skeleton_horse.ambient", 1.0, 0.5), ("block.roots.break", 1.2, 0.5), ("entity.wither_skeleton.ambient", 1.0, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 0.7), ("block.bone_block.step", 0.8, 0.6)],
    "swing_heavy": [("entity.player.attack.knockback", 1.0, 0.6), ("entity.player.attack.sweep", 1.0, 0.5), ("block.bone_block.break", 0.8, 0.6)],
    "cast": [("block.bone_block.break", 1.4, 0.5), ("block.mangrove_roots.break", 1.2, 0.6), ("entity.skeleton.shoot", 1.0, 0.6)],
    "impact": [("item.mace.smash_ground", 1.0, 0.6), ("block.rooted_dirt.break", 1.2, 0.6), ("block.bone_block.break", 0.8, 0.5)],
    "roar": [("entity.wither_skeleton.ambient", 1.6, 0.45), ("entity.skeleton_horse.ambient", 1.0, 0.45), ("block.azalea_leaves.break", 1.0, 0.5)],
    "step": [("block.bone_block.step", 0.6, 0.6), ("entity.wither_skeleton.step", 0.4, 0.7)],
}
# the Rib Spikes is his ribs cracking open (cast), not a blow on the floor
MOVE_SOUNDS = {"spikes": {"swing": "cast", "impact": ""}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.8, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where his parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_r", (0, 0, 0)), ("hand_l", (0, 0, 0)), ("sword", TIP), ("sword", (0, -7, 0)), ("head", (0, -9, 0)),
                 ("leaves0", (0, -7, 0)), ("leaves1", (0, -4, 0)), ("foot_l", (0, 2, 0)), ("foot_r", (0, 2, 0)), ("cloak2", (0, 12, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
