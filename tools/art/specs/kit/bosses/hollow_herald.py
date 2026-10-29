"""The Hollow Herald (SPEC §35.11, docs/bosses/ROLLOUT.md): the Abyss gate's Lord. The Ring-Head, picked 2026-10-29:
"Nothing. No sound at all."

Very tall and thin, five blocks to the top of its head, in a long blue-black tailcoat cut away at the front, its tails
hanging to the backs of its knees and lined in violet. Where its head should be there is a ring of violet-black glass
standing on its high collar, lit along its inner edge, with nothing inside it: you see straight through its head. Its
legs are long and thin, in black trousers with a violet stripe and white spats over pointed shoes; its arms are longer
than its legs, the right one hanging so low that the fingers of its white gloves nearly touch the floor. The left one
is folded up at the elbow like an insect's, holding a scroll to its breast: a long sheet of parchment, blank, with a
black wax seal on a ribbon. A herald's pewter chain lies across its shoulders, with an empty ring for its badge.

It is the only boss that stands entirely still between moves and makes no sound when it walks. Moves (SPEC §35.11):
the long rake of its right hand across you (LIGHT) and the backhand back again (LIGHT); the Long Step (it rears and
takes one enormous stride across the room, its hand reaching for you along the floor); the upper body turning right
round on its hips for whoever is behind it; Draw Near (the arm held out to you, palm up, the fingers beckoning); Void
Bolts (the blank scroll held up to you and the other hand pointing, the ring pulsing with each bolt); Unlit (it covers
its ring with its hands); Spawn of Nothing (it bows until its ring faces the floor, and they drop out of it); Collapse
(its arms spread high and wide, held, then brought together in a clap that makes no sound); Void Halo (it lifts the ring
off its own collar and holds it overhead, flat, like a halo); and the Event Horizon (the signature: arms wide, leaning
back, the ring growing until it is twice the size of its body). It dies on its knees; the ring slides off its collar,
rolls away across the floor and falls flat.
"""
import math

from kit import core, ik, parts, plans
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "hollow_herald"
SCALE = 1.4  # the hitbox on the SHADE model at size 0.85 x 3.65: 1.2 x 5.1 blocks (to the top of the ring)

P = plans.Biped(leg=42, thigh=20, foot=(3, 2, 8), pelvis=(7, 4, 5), spine=(6, 8, 4), chest=(10, 11, 5), neck=(6, 4, 5),
                head=(16, 16, 2), arm=(3, 21, 3), forearm=(2, 18, 2), hand=(4, 4, 2), stance=2.0, shoulder=6.0, leg_w=(3, 2))
FOOT = {"r": (-2.2, 2.0, -0.8), "l": (2.2, 2.0, 0.8)}  # the ankles at rest (x, height, z)
GRIP = {"r": (-7.5, 22.0, -1.0), "l": (3.5, 50.0, -7.0)}  # the wrists at rest: the right hanging, the left at its breast
POLE = {"r": (0.0, 0.2, 1.0), "l": (0.15, 0.5, 1.0)}  # the elbows: the right back, the left folded down and out
RING = 8.0  # the ring's outer radius (px)
ARMS = tuple(f"{b}_{s}" for b in ("arm", "forearm", "hand", "fingers") for s in "lr")
ON_TIME = {b: 0 for b in ARMS + ("ring", "neck")}  # for the moves whose hands hold the ring
TAILS = ("tail_l0", "tail_l1", "tail_r0", "tail_r1")


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -3.5, -1, -2.5, 7, 4, 5)
    pel.cube("skirt_l", 3, -1, -2.5, 1, 6, 5)  # the coat's sides over the hips, sweeping back into the tails
    pel.cube("skirt_r", -4, -1, -2.5, 1, 6, 5)
    # long thin legs in black trousers, white spats over long pointed shoes
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 21, 3)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -1, 0, -1, 2, 20, 2)
        sn.cube("spat_" + nm, -1.5, 17, -1.5, 3, 3, 3)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -1.5, 0, -6, 3, 2, 8)
        ft.cube("toe_" + nm, -0.5, 0.5, -7.5, 1, 1, 2)
    # the tails of its coat, from the back of its waist to the backs of its knees, lined in violet
    for side, nm in ((1, "l"), (-1, "r")):
        t0 = pel.bone(f"tail_{nm}0", (side * 1.7, -1, 2.6), (8, 0, -side * 4))
        t0.cube(f"tail_{nm}0", -1.5, 0, -0.5, 3, 11, 1)
        t1 = t0.bone(f"tail_{nm}1", (0, 11, 0), (5, 0, 0))
        t1.cube(f"tail_{nm}1", -1.5, 0, -0.5, 3, 10, 1)
        t1.cube(f"tailtip_{nm}", -1, 10, -0.5, 2, 2, 1)
    # the waistcoat, then the coat over a narrow chest, cut away at the front; sloped shoulders, violet lapels, the
    # herald's chain lying across the shoulders with its badge (an empty ring)
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -3, -8, -2, 6, 8, 4)
    ch = sp.bone("chest", (0, -8, 0))
    ch.cube("chest", -5, -11, -2.5, 10, 11, 5)
    ch.cube("shoulder_l", 4, -11, -2.5, 3, 2, 5)
    ch.cube("shoulder_r", -7, -11, -2.5, 3, 2, 5)
    ch.cube("lapel_l", 1, -11, -3.2, 2, 7, 1)
    ch.cube("lapel_r", -3, -11, -3.2, 2, 7, 1)
    ch.cube("chain", -5.5, -11.6, -3, 11, 1, 6)
    ch.cube("medal", -1.5, -7.5, -3.6, 3, 3, 1)
    # the high standing collar with a white stock, and on it the ring: violet-black glass, nothing inside it
    nk = ch.bone("neck", (0, -11, 0))
    nk.cube("collar", -3, -4, -2.5, 6, 4, 5)
    nk.cube("stock", -1.5, -3.5, -3.1, 3, 3, 1)
    rg = nk.bone("ring", (0, -4 - RING, 0))
    rg.cube("ring_t", -4, -8, -1, 8, 2, 2)
    rg.cube("ring_b", -4, 6, -1, 8, 2, 2)
    rg.cube("ring_l", 6, -4, -1, 2, 8, 2)
    rg.cube("ring_r", -8, -4, -1, 2, 8, 2)
    rg.cube("ring_tl", 3, -7, -1, 4, 4, 2)
    rg.cube("ring_tr", -7, -7, -1, 4, 4, 2)
    rg.cube("ring_bl", 3, 3, -1, 4, 4, 2)
    rg.cube("ring_br", -7, 3, -1, 4, 4, 2)
    # arms longer than its legs, in the coat's sleeves; white shirt cuffs and white gloves with long fingers
    for side, nm in ((1, "l"), (-1, "r")):
        arm = ch.bone("arm_" + nm, (side * P.shoulder, -10, 0))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 21, 3)
        fa = arm.bone("forearm_" + nm, (0, 20, 0))
        fa.cube("forearm_" + nm, -1, 0, -1, 2, 18, 2)
        fa.cube("cuff_" + nm, -1.5, 17, -1.5, 3, 3, 3)
        hn = fa.bone("hand_" + nm, (0, 20, 0))
        hn.cube("hand_" + nm, -2, 0, -1, 4, 4, 2)
        hn.cube("thumb_" + nm, 2, 1, -0.5, 1, 4, 1)
        fg = hn.bone("fingers_" + nm, (0, 4, 0), (-35 if nm == "l" else 12, 0, 0))
        for k, (x, ln) in enumerate(((-2, 5), (-1, 7), (0, 6), (1, 5))):
            fg.cube(f"fing_{nm}{k}", x, 0, -0.5, 1, ln, 1)
    # the scroll in its left hand: the roller across its fist, the blank sheet hanging behind its fingers, the lower
    # roller and a black wax seal on a violet ribbon
    sc = r.by_name["hand_l"].bone("scroll", (0, 3, 0))
    sc.cube("roll", -5, -1, -1, 10, 2, 2)
    sc.cube("knob_l", 5, -1.5, -1.5, 1, 3, 3)
    sc.cube("knob_r", -6, -1.5, -1.5, 1, 3, 3)
    s0 = sc.bone("sheet0", (0, 1, 1.4))
    s0.cube("sheet0", -4, 0, -0.5, 8, 9, 1)
    s1 = s0.bone("sheet1", (0, 9, 0))
    s1.cube("sheet1", -4, 0, -0.5, 8, 8, 1)
    s1.cube("roll_b", -4.5, 8, -1, 9, 2, 2)
    se = s1.bone("seal", (1.5, 10, -0.2))
    se.cube("ribbon", -0.5, 0, -0.5, 1, 4, 1)
    se.cube("seal", -1.5, 3, -0.5, 3, 3, 1)
    # the arms where they rest, the hands set, the feet where they stand (and the legs' rest from the planter's own
    # solution, so a planted key and the rest are the same legs)
    for s in "rl":
        ik.rest_limb(r, "arm_" + s, "forearm_" + s, "hand_" + s, GRIP[s], POLE[s])
    fr = core.frames(r, {})
    r.by_name["hand_r"].rot = ik.orient(r, fr, "hand_r", (0.04, 1, -0.12), (0, 0, -1))
    r.by_name["hand_l"].rot = ik.orient(r, fr, "hand_l", (0, 1, 0), (1, 0, 0))
    for s in "rl":
        ik.rest_limb(r, "thigh_" + s, "shin_" + s, "foot_" + s, FOOT[s], (0, 0, -1))
    fr = core.frames(r, {})
    for s in "rl":
        r.by_name["foot_" + s].rot = ik.orient(r, fr, "foot_" + s, (0, 1, 0), (1, 0, 0))
    p = core.plant(r, {}, ("l", "r"))
    for s in "lr":
        for b in ("thigh_", "shin_", "foot_"):
            bn = r.by_name[b + s]
            bn.rot = tuple(bn.rot[i] + p[b + s][i] for i in range(3))
    r.look = [("neck", 0.35, 0.3), ("ring", 0.45, 0.4)]  # the ring turns to find you
    r.size = P.leg / 36.0
    r.shadow = 0.6
    # the tails, the sheet and its seal swing on springs
    r.springs = [("tail_l0", 0.45, 2.2, 0.24), ("tail_r0", 0.45, 2.2, 0.24), ("tail_l1", 0.55, 2.6, 0.28), ("tail_r1", 0.55, 2.6, 0.28),
                 ("sheet0", 0.4, 2.0, 0.22), ("sheet1", 0.5, 2.4, 0.26), ("seal", 0.6, 3.0, 0.3)]
    r.guard = ["hand_r", "fingers_r"]
    _REST.clear()
    _REST.update({b.name: tuple(b.rot) for b in r.bones})
    return r


COAT = Ramp("#030409", "#070912", "#0b0f1d", "#10162a", "#161e39", "#1e284a", "#28345e", "#344274")
LINING = Ramp("#0e0619", "#170b28", "#22113a", "#2f184e", "#3e2164", "#502c7c", "#643a94")
WAIST = Ramp("#0b0810", "#120e1a", "#1b1526", "#251d33", "#302641", "#3c3050")
WHITE = Ramp("#4e525e", "#6c717e", "#8d929f", "#adb2bd", "#cacdd6", "#e2e4ea", "#f3f4f7")
PEWTER = Ramp("#22242b", "#353841", "#4b4f5a", "#666b78", "#848a97", "#a4a9b5", "#c4c8d1", "#e0e2e8")
CLOTH = Ramp("#030305", "#07070a", "#0c0c11", "#121219", "#1a1a23", "#23232e")
STRIPE = Ramp("#1c1428", "#281c38", "#35264a", "#44315c", "#543e70")
PATENT = Ramp("#020203", "#050507", "#09090d", "#111117", "#1c1c25", "#2e2e3a", "#4a4a5a")
GLASS = Ramp("#040208", "#090512", "#0f081d", "#170d2a", "#21123a", "#2d194d", "#3b2162", "#4d2b7c", "#63389a")
VOID = ("#2e1458", "#7a4ae0", "#c8aaff")
PARCH = Ramp("#5e5544", "#7a705a", "#968b70", "#b2a686", "#cabe9c", "#ded4b4", "#ece4c8")
WOOD = Ramp("#0c0705", "#160d08", "#22140c", "#301c11", "#402617")
WAX = Ramp("#040306", "#09070d", "#110d17", "#1b1523", "#272031")

INNER = {"ring_t": ("bottom",), "ring_b": ("top",), "ring_l": ("west",), "ring_r": ("east",), "ring_tl": ("west", "bottom"),
         "ring_tr": ("east", "bottom"), "ring_bl": ("west", "top"), "ring_br": ("east", "top")}  # the faces toward its middle


def paint(r, P_):
    """Values (SPEC §35.4): the gloves, the spats, the stock and the scroll brightest; the ring's inner edge the one light
    on it; the lining, the waistcoat and the pewter in the middle; the coat, the trousers and the shoes the darkest."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    twill = lambda f, i, j: (0.3 if (i + 2 * j) % 5 == 0 else -0.15 if (i - j) % 4 == 0 else 0)
    # the coat: blue-black with a fine twill, two rows of pewter buttons down its front and a closing between them
    for c in of("chest", "shoulder_", "arm_", "forearm_", "skirt_", "pelvis"):
        P_.paint(c, COAT, 3.0, twill, grad=0.9, rim=0.8, ao=0.8, noise=0.15)
    f = P_.face("chest", "north")
    for i, j in f.each():
        u = i - f.w / 2 + 0.5
        if abs(abs(u) - 2.5) < 0.6 and j in (6, 8, 10):
            f.set(i, j, PEWTER.at(5.4 if u < 0 else 4.8))
        elif abs(u) < 0.6 and j >= 7:
            f.set(i, j, COAT.at(1.2))
    # the shoulders' seams lit along the top
    for c in ("shoulder_l", "shoulder_r"):
        f = P_.face(c, "top")
        for i, j in f.each():
            f.set(i, j, COAT.at(4.6 if j in (0, f.h - 1) else 4.0))
    # the waistcoat between the coat's fronts: dark plum, a column of small pewter buttons, the coat round its sides
    P_.paint("spine", COAT, 3.0, twill, grad=0.8, rim=0.4, ao=0.8, noise=0.15)
    P_.paint("spine", WAIST, 3.4, lambda f, i, j: (2.2 if i in (2, 3) and j % 3 == 1 else 0.4 if (i + j) % 4 == 0 else 0),
             faces=("north",), grad=0.6, rim=0.6, ao=0.7, noise=0.1)
    f = P_.face("spine", "north")
    for i, j in f.each():
        if i in (2, 3) and j % 3 == 1:
            f.set(i, j, PEWTER.at(4.6))
    # the lapels: violet facings with a pewter edge along their outer side
    for c in ("lapel_l", "lapel_r"):
        P_.paint(c, LINING, 3.8, lambda f, i, j: (1.6 if f.name == "north" and i == (f.w - 1 if c == "lapel_l" else 0) else 0),
                 grad=0.8, rim=0.8, ao=0.4)
    # the chain of office: pewter links round the shoulders; the badge an empty pewter ring, black at its middle
    P_.paint("chain", PEWTER, 4.4, lambda f, i, j: (1.4 if (i + j) % 2 == 0 else -1.2), rim=0.4, ao=0.2)
    P_.paint("medal", PEWTER, 4.8, lambda f, i, j: (-6.0 if f.name == "north" and (i, j) == (1, 1) else 1.0 if (i + j) % 2 == 0 else 0),
             rim=0.6, ao=0.2)
    # the collar: the coat's, standing high; inside it there is nothing, a black with violet at its edge
    P_.paint("collar", COAT, 3.2, twill, grad=0.6, rim=0.9, ao=0.6, noise=0.1)
    f, g = P_.face("collar", "top"), P_.face("collar", "top", glow=True)
    for i, j in f.each():
        edge = i in (0, f.w - 1) or j in (0, f.h - 1)
        f.set(i, j, LINING.at(2.4) if edge else hexc("#000000"))
        if edge:
            g.set(i, j, hexc(VOID[0], 90))
    P_.paint("stock", WHITE, 4.6, lambda f, i, j: (-1.0 if j == 1 and f.name == "north" else 0.4 if i == 1 else 0), rim=0.6, ao=0.4)
    # the tails: the coat outside with two pewter buttons at the waist, violet lining toward its legs
    for c in of("tail_", "tailtip_"):
        P_.paint(c, COAT, 2.8, twill, grad=1.0, rim=0.6, ao=0.6, noise=0.15)
        P_.paint(c, LINING, 3.2, lambda f, i, j: (0.5 if j % 4 == 0 else 0), faces=("north",), grad=0.8, rim=0.3, ao=0.5)
    for nm in "lr":
        f = P_.face(f"tail_{nm}0", "south")
        f.set(1, 1, PEWTER.at(5.2))
        f.set(1, 3, PEWTER.at(4.6))
    # the shirt cuffs and the gloves: white, the backs stitched in three dark lines, dark seams between the fingers
    for c in of("cuff_"):
        P_.paint(c, WHITE, 4.6, lambda f, i, j: (-1.2 if j == f.h - 1 else 0.3 if j == 0 else 0), rim=0.6, ao=0.4)
    for c in of("hand_", "thumb_"):
        P_.paint(c, WHITE, 4.8, lambda f, i, j: (-1.4 if c.startswith("hand_") and f.name in ("north", "south") and i in (1, 2) and j >= 1 else 0),
                 grad=0.6, rim=0.6, ao=0.5, noise=0.06)
    for c in of("fing_"):
        P_.paint(c, WHITE, 4.8, lambda f, i, j: (-1.6 if f.name in ("west", "east") else -0.8 if j == f.h - 1 else 0.2),
                 grad=0.8, rim=0.5, ao=0.3)
    # the trousers: black with a violet satin stripe down each outer side; white spats buttoned on the outside
    for c in of("thigh_", "shin_"):
        outer = "east" if c.endswith("_l") else "west"
        P_.paint(c, CLOTH, 2.6, lambda f, i, j: (0.4 if (i + j) % 6 == 0 else 0), grad=0.8, rim=0.4, ao=0.6, noise=0.12)
        f = P_.face(c, outer)
        for i, j in f.each():
            if i == f.w // 2:
                f.set(i, j, STRIPE.at(3.4 - 0.6 * j / f.h))
    for c in of("spat_"):
        P_.paint(c, WHITE, 4.4, lambda f, i, j: (-0.8 if j == f.h - 1 else 0), rim=0.6, ao=0.3)
        outer = "east" if c.endswith("_l") else "west"
        f = P_.face(c, outer)
        f.set(1, 0, PEWTER.at(3.6))
        f.set(1, 2, PEWTER.at(3.6))
    # the shoes: black patent, a highlight along the top of each
    for c in of("foot_", "toe_"):
        P_.paint(c, PATENT, 2.6, lambda f, i, j: (2.6 if f.name == "top" and i == f.w // 2 and 1 <= j <= 4 else 0), rim=0.8, ao=0.3)
    # the ring: violet-black glass, a thin cold highlight on its front, lit along its whole inner edge
    for c in of("ring_"):
        inner = INNER[c]
        P_.paint(c, GLASS, 3.0, lambda f, i, j: (3.0 if f.name in inner else 1.0 if f.name in ("north",) and (i + j) % 7 == 0 else 0),
                 grad=0.8, rim=0.6, ao=0.4, noise=0.1)
        for fname in inner:
            P_.glow_face(c, fname, lambda f, i, j: hexc(VOID[1] if (i + j) % 3 else VOID[2], 190))
    # the ring's front and back faces glow along the edge that borders its hole
    for c in of("ring_"):
        for fname in ("north", "south"):
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            cb = P_.cubes[c]
            for i, j in f.each():
                # the face's pixel in the ring's plane (north faces see it from the front: x runs from its right)
                x = (cb.x + cb.w - 1 - i) if fname == "north" else (cb.x + i)
                y = cb.y + j
                rx, ry = x + 0.5, y + 0.5
                if _ring_inner(rx, ry):
                    f.set(i, j, GLASS.at(7.4))
                    g.set(i, j, hexc(VOID[1], 150))
    # the scroll: a blank parchment, its edges aged darker; wooden finials; a black wax seal pressed with an empty ring
    P_.paint("sheet0", PARCH, 4.8, lambda f, i, j: (-1.4 if i in (0, f.w - 1) else -0.5 if j == 0 else 0), grad=0.4, rim=0.3, ao=0.2, noise=0.25)
    P_.paint("sheet1", PARCH, 4.6, lambda f, i, j: (-1.4 if i in (0, f.w - 1) else -0.8 if j == f.h - 1 else 0), grad=0.4, rim=0.2, ao=0.2, noise=0.25)
    for c in ("roll", "roll_b"):
        P_.paint(c, PARCH, 4.4, lambda f, i, j: (-1.6 if f.name in SIDES and (i + 2 * j) % 5 == 0 else 0), rim=0.6, ao=0.4)
    for c in ("knob_l", "knob_r"):
        P_.paint(c, WOOD, 2.6, lambda f, i, j: (1.4 if (i, j) == (1, 0) else 0), rim=0.8, ao=0.4)
    P_.paint("ribbon", LINING, 4.0, lambda f, i, j: (0.8 if i == 0 else 0), rim=0.4)
    P_.paint("seal", WAX, 2.4, lambda f, i, j: (2.2 if f.name == "north" and (i, j) != (1, 1) else 0), rim=0.6, ao=0.2)
    return P_.skin, P_.glow


def _ring_inner(x, y):
    """Is this point of the ring's plane on the edge of its hole (the pixels next to the opening)?"""
    ax, ay = abs(x), abs(y)
    hole = (ax < 6 and ay < 3) or (ax < 3 and ay < 6)
    if hole:
        return False
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        bx, by = abs(x + dx), abs(y + dy)
        if (bx < 6 and by < 3) or (bx < 3 and by < 6):
            return True
    return False


# ---- motion ----------------------------------------------------------------------------------------------------------

_REST = {}  # each bone's rest turn (set by body())


def _abs(r, bone, rot):
    """A key for a bone given as its whole turn (the rest taken off)."""
    rest = r.by_name[bone].rot
    return tuple(rot[i] - rest[i] for i in range(3))


def _near(bone, v, ref):
    """The key v (degrees over rest) written as near as it goes to ref, by whole turns and the other Euler triple of the
    same turn: IK solves each key on its own, and two keys a little apart must not spin the long way round."""
    rest = _REST.get(bone, (0.0, 0.0, 0.0))
    f = tuple(rest[i] + v[i] for i in range(3))
    best, bd = None, None
    for g in (f, (f[0] + 180, 180 - f[1], f[2] + 180)):
        w = tuple(g[i] - rest[i] for i in range(3))
        w = tuple(w[i] + 360 * round((ref[i] - w[i]) / 360) for i in range(3))
        d = sum(abs(w[i] - ref[i]) for i in range(3))
        if bd is None or d < bd - 1e-6:
            best, bd = w, d
    return best


def _chain(keys, start=None):
    """Keys [pose] rewritten in order, each bone's turn as near as it goes to the key before (dict keys too)."""
    prev = dict(start or {})
    out = []
    for p in keys:
        q = {}
        for b, v in p.items():
            if isinstance(v, dict):
                rot = _near(b, v["rot"], prev.get(b, (0.0, 0.0, 0.0)))
                q[b] = {"rot": rot, "pos": v.get("pos", (0, 0, 0))}
                prev[b] = rot
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
    # the strike on written back from the rest it returns to: the two ways of writing a turn agree at rest, and the
    # recovery must land on rest's own (the strike is where the arm moves fastest, so the two meet there)
    n = len(w)
    fixed = fixed[:n] + _chain(fixed[n:][::-1])[::-1]
    k = 0
    for st, ks in (("windup", w), ("active", a)):
        mv.stages[st] = [(f, fixed[k + i]) for i, (f, _) in enumerate(ks)]
        k += len(ks)
    if mv.through is not None:
        mv.through = fixed[k]
        k += 1
    mv.stages["recover"] = [(f, fixed[k + i]) for i, (f, _) in enumerate(rc)]
    return mv


def _keyed(r, name, length, keys, loop=False, lag=None):
    ps = _chain([p for _, p in keys])
    keyed(r, name, length, [(t, p) for (t, _), p in zip(keys, ps)], loop=loop, lag=lag)


def _mix(a, b, f):
    """Between two keys, f of the way from a to b (b's turns taken as near as they go to a's)."""
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
    """A recovery in two steps to rest (or to settle), so nothing snaps home."""
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


def _horiz(v):
    n = math.hypot(v[0], v[2]) or 1.0
    return (v[0] / n, 0.0, v[2] / n)


def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), ring=None, right=None, left=None,
          rhand=None, lhand="plumb", rf=0.0, lf=0.0, sheet=(0, 1, 0), legs=None, feet=("l", "r"), step=None, turn=None, poles=None,
          more=None):
    """A whole-body key over the rest. It turns at the hips (hips: turn, at: x, up, z), the spine, the chest and the neck;
    ring: the ring's own turn on its collar (or a {"rot", "pos"} key, _ring_at). The feet stay where they stand
    (step: {side: (dx, dy, dz)} model pixels, y down) unless legs pose them (whole turns). right, left: where that wrist
    goes, (x, height, z) model pixels or ("on", bone, point), the elbow along poles[side]; rhand: (ydir, xdir) the right
    hand's line; lhand: "plumb" keeps the scroll's roller level across its body, or (ydir, xdir); rf, lf curl the
    fingers (+ closed); sheet: the way the scroll's sheet hangs (plumb, or blown)."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck}
    if ring is not None:
        p["ring"] = ring
    if legs:
        p.update({b: _abs(r, b, v) for b, v in legs.items()})
        feet = tuple(s for s in feet if "thigh_" + s not in legs)
    if more:
        p.update(more)
    if feet:
        p = core.plant(r, p, feet, at=step, turn=turn)
    poles = poles or {}
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, poles.get(side, POLE[side])))
    fr = core.frames(r, core.as_sample(p))
    if rhand:  # no xdir: the hand keeps its forearm's twist (a hinge at the wrist, never a spin)
        p["hand_r"] = ik.orient(r, fr, "hand_r", rhand[0], rhand[1] or R._mv(fr["forearm_r"][0], (1, 0, 0)))
    if lhand == "plumb":
        across = _horiz(R._mv(fr["chest"][0], (1, 0, 0)))
        p["hand_l"] = ik.orient(r, fr, "hand_l", (0, 1, 0), across)
    elif lhand:
        p["hand_l"] = ik.orient(r, fr, "hand_l", *lhand)
    fr = core.frames(r, core.as_sample(p))
    p["sheet0"] = ik.orient(r, fr, "sheet0", sheet, R._mv(fr["scroll"][0], (1, 0, 0)))
    if rf:
        p["fingers_r"] = (rf, 0, 0)
    if lf:
        p["fingers_l"] = (lf, 0, 0)
    return p


def _ring_at(r, pose, centre, M=None):
    """The ring's key ({"rot", "pos"} on its collar) that puts its middle at centre (x, height, z) turned by M (a model
    rotation; level and facing forward when None), the rest of the body posed as in pose."""
    fr = core.frames(r, core.as_sample(pose))
    Mn, Tn = fr["neck"]
    b = r.by_name["ring"]
    W = ik.model(centre)
    local = R._mv(core._t(Mn), tuple(W[i] - Tn[i] for i in range(3)))
    d = tuple(local[i] - b.pivot[i] for i in range(3))
    Mw = M or ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    e = core._euler(R._mm(core._t(Mn), Mw))
    return {"rot": tuple(c / DEG for c in e), "pos": (d[0], -d[1], d[2])}


def _rx(deg):
    return R._rot(deg * DEG, 0, 0)


def _rz(deg):
    return R._rot(0, 0, deg * DEG)


def _ry(deg):
    return R._rot(0, deg * DEG, 0)


def _gait(r, u, D, lift, stance):
    """A stride at phase u (the right foot planting at 0, the left at 0.5): each foot planted for a share of the cycle,
    sliding back under it, then swung forward high through the air. Offsets from rest (model px, y down)."""
    rest = _rest_feet(r)
    out = {}
    half = stance * D / 2
    for s, ph in (("r", 0.0), ("l", 0.5)):
        v = (u - ph) % 1.0
        if v < stance:
            z, dy = -half + 2 * half * v / stance, 0.0
        else:
            k = (v - stance) / (1 - stance)
            z, dy = half - 2 * half * _ease(k), -lift * math.sin(math.pi * k) ** 0.8
        out[s] = (0.0, dy, z - rest[s][2])
    return out


def idle(r):
    """It stands entirely still, very tall; it sways a little, like something tall in a wind, and the ring turns to look
    about and tilts to one side the way a bird's head does; now and then the fingers of its hanging hand curl, one
    twitch, and open again."""
    def sway(x, tilt, look, rf, y=0.0):
        return _pose(r, hips=(0, 0, x * 0.6), at=(x * 0.5, y, 0), spine=(0, 0, -x * 0.4), chest=(0, look * 0.2, -x * 0.5),
                     neck=(0, look, tilt), ring=(0, look * 0.5, tilt * 0.6), rf=rf)
    k0 = sway(0, 0, 0, 2)
    _keyed(r, "idle", 6.0, [(0, k0), (0.9, sway(1.4, 0, 0, 8)), (1.8, sway(0.7, 13, -9, 4)), (2.3, sway(0.3, 16, -11, 32)), (2.6, sway(0.2, 15, -11, 6)),
                           (3.6, sway(-1.3, 2, 5, 10)), (4.4, sway(-0.9, -7, 14, 4, -0.2)), (5.2, sway(-0.3, -2, 6, 18)), (6.0, k0)], loop=True)


def walk(r):
    """A long slow stride with its knees lifting high, as if wading; the body glides level and the ring stays where it
    is, looking ahead, whatever the legs do; the hanging arm swings late and slow, its fingers trailing."""
    r.walk_stride = 2.6
    D = r.walk_stride * 16
    T = 1.6
    keys = []
    n = 16
    for k in range(n + 1):
        u = k / n
        s = math.sin(2 * math.pi * u)
        lag = math.sin(2 * math.pi * (u - 0.12))
        bob = 0.8 * math.cos(4 * math.pi * (u - 0.3)) - 0.8
        keys.append((T * u, _pose(r, hips=(2, 4 * s, 1.5 * s), at=(0, bob, 0), spine=(3, -2 * s, 0), chest=(2, -3 * s, -1 * s), neck=(-5, s, 0),
                                  step=_gait(r, u, D, lift=8.0, stance=0.6), rf=10 + 8 * math.sin(2 * math.pi * (u - 0.25)),
                                  more={"arm_r": (-14 * lag, 0, 1.5), "forearm_r": (-8 - 5 * max(0.0, -lag), 0, 0), "arm_l": (3 * s, 0, 0)})))
    _keyed(r, "walk", T, keys, loop=True)


def run(r):
    """The lope: bent forward, enormous strides, the hanging arm thrown out behind it and the fingers skimming the
    floor, the scroll clutched to its breast; the ring held level."""
    r.run_stride = 4.6
    D = r.run_stride * 16
    T = 0.9
    keys = []
    n = 12
    for k in range(n + 1):
        u = k / n
        s, c = math.sin(2 * math.pi * u), math.cos(4 * math.pi * u)
        keys.append((T * u, _pose(r, hips=(12, 6 * s, 2 * s), at=(0, -2.5 + 1.4 * c, -1), spine=(8, -3 * s, 0), chest=(6, -4 * s, 0), neck=(-22, 0, 0),
                                  ring=(-4, 0, 0), step=_gait(r, u, D, lift=10.0, stance=0.38), rf=-6,
                                  more={"arm_r": (38 + 8 * s, 0, 10), "forearm_r": (-10, 0, 0), "arm_l": (-6, 0, 0)})))
    _keyed(r, "run", T, keys, loop=True)


def turns(r):
    """Turning on the spot: one long foot set round and then the other, the ring turning first."""
    for nm, sgn in (("turn_l", 1), ("turn_r", -1)):
        a = _pose(r, hips=(0, -6 * sgn, 0), at=(0, -0.4, 0), neck=(0, -8 * sgn, 0), step={"r": (-1.5 * sgn, -4, -1)})
        b = _pose(r, hips=(0, -3 * sgn, 0), neck=(0, -5 * sgn, 0))
        c = _pose(r, hips=(0, -6 * sgn, 0), at=(0, -0.4, 0), neck=(0, -8 * sgn, 0), step={"l": (1.5 * sgn, -4, 1)})
        _keyed(r, nm, 0.6, [(0, _pose(r)), (0.15, a), (0.3, b), (0.45, c), (0.6, _pose(r))], loop=True)


KNEE_Z = -1.7  # where its knees come down (z), both of them, whatever the hips do


def _knees(hp, tw=-12.0):
    """On both knees: the legs (whole turns) and where the hips go (at) for hips pitched hp and the thighs tw from the
    vertical (-12 kneeling up, about -60 sat back on its heels); the shins along the floor behind, the soles up."""
    a = tw * DEG
    legs = {}
    for s, sg in (("l", 1), ("r", -1)):
        legs.update({"thigh_" + s: (tw - hp, 0, -3 * sg), "shin_" + s: (88 - tw, 0, 0), "foot_" + s: (90, 0, 0)})
    return legs, (0, 1.5 + P.thigh * math.cos(a) + 1 - P.leg, KNEE_Z - P.thigh * math.sin(a))


def flinches(r):
    """Struck, it jolts like a puppet: the ring knocked back on its collar, the hanging arm swinging; from the side, its
    upper body twists away and the ring tilts."""
    jolt = _pose(r, at=(0, -0.5, 1), spine=(-4, 0, 0), chest=(-8, 0, 0), neck=(-14, 0, 0), ring=(-10, 0, 0), rf=-20,
                 more={"arm_r": (-16, 0, -6), "forearm_r": (-12, 0, 0)})
    back_ = _pose(r, chest=(2, 0, 0), neck=(4, 0, 0), ring=(3, 0, 0), rf=6, more={"arm_r": (6, 0, 2)})
    _keyed(r, "hurt", 0.5, [(0, _pose(r)), (0.07, jolt), (0.26, back_), (0.5, _pose(r))])
    for nm, sgn in (("hurt_l", 1), ("hurt_r", -1)):
        # struck from its left it twists to its right (and bends away)
        tw = _pose(r, hips=(0, 4 * sgn, 0), at=(-sgn * 0.8, -0.4, 0), spine=(0, 6 * sgn, -4 * sgn), chest=(-4, 16 * sgn, -8 * sgn),
                   neck=(-6, 12 * sgn, -10 * sgn), ring=(0, 6 * sgn, -12 * sgn), rf=-16, more={"arm_r": (-8, 0, 14 * sgn)})
        ret = _pose(r, chest=(1, -4 * sgn, 2 * sgn), neck=(2, -3 * sgn, 3 * sgn), ring=(0, 0, 3 * sgn), rf=4)
        _keyed(r, nm, 0.55, [(0, _pose(r)), (0.08, tw), (0.3, ret), (0.55, _pose(r))])


def reactions(r):
    """The kneel when its stance breaks: it folds down onto both knees like a folding rule, bowed, the ring hanging
    before it, its right hand flat on the floor; the rise unfolds it to its height. The death: it goes rigid on its toes,
    the ring turning as if looking for something; its knees go; it bows until its collar points at the floor, and the
    ring slides off it, rolls away across the floor and falls flat."""
    palm = ((0, 0.15, -1), None)
    # the right hand turns forward as it reaches down, so its fingers never meet the floor on the way
    half = _pose(r, hips=(6, 0, 0), at=(0, -9, 1), spine=(8, 0, 0), chest=(6, 0, 0), neck=(10, 0, 0), right=(-9, 11, -11),
                 rhand=((0, 0.7, -0.7), None), rf=4)
    lg, kat = _knees(10)
    kneel = _pose(r, hips=(10, 0, 0), at=kat, legs=lg, spine=(14, 0, 0), chest=(12, 0, 0), neck=(26, 0, 0), ring=(8, 0, 0),
                  right=(-9, 2.5, -16), rhand=palm, rf=-8, left=(8, 33, -10))
    reach = _mix(half, kneel, 0.55)
    reach.update({b: v for b, v in _pose(r, hips=(8, 0, 0), at=_mix({"a": {"rot": (0, 0, 0), "pos": (0, -9, 1)}}, {"a": {"rot": (0, 0, 0), "pos": kat}}, 0.55)["a"]["pos"],
                                         legs={b: tuple(0.55 * c for c in v) for b, v in lg.items()}, spine=(11, 0, 0), chest=(9, 0, 0), neck=(18, 0, 0),
                                         right=(-9, 6.5, -15), rhand=((0, 0.35, -0.95), None), rf=-2).items() if b.startswith(("arm_r", "forearm_r", "hand_r", "fingers_r"))})
    _keyed(r, "kneel", 0.8, [(0, _pose(r)), (0.35, half), (0.58, reach), (0.8, kneel)])
    push = _pose(r, hips=(4, 0, 0), at=(0, -8, 0.5), spine=(4, 0, 0), chest=(2, 0, 0), neck=(4, 0, 0), right=(-9, 14, -10),
                 rhand=((0, 0.6, -0.8), None), rf=6, left=(6, 40, -8))
    _keyed(r, "rise", 1.1, [(0, kneel), (0.45, push), (1.1, _pose(r))])
    rigid = _pose(r, at=(0, 0.6, 0), chest=(-4, 0, 0), neck=(-12, 0, 0), ring=(-8, 0, 0), right=(-12, 24, -2), rf=-24)
    look = _pose(r, at=(0, 0.4, 0), chest=(-2, 8, 0), neck=(-4, 26, 14), ring=(0, 10, 10), right=(-11, 23, -2), rf=-14)
    lg, kat = _knees(4)
    knees = _pose(r, hips=(4, 0, 0), at=kat, legs=lg, spine=(4, 0, 0), chest=(2, 6, 0), neck=(10, 14, 6), ring=(0, 6, 4),
                  right=(-10, 13, -4), rf=6, left=(8, 34, -9))
    lg, kat = _knees(30, -30)
    bow_kw = dict(hips=(30, 0, 0), at=kat, legs=lg, spine=(24, 0, 0), chest=(18, 0, 0), neck=(36, 0, 0), right=(-10, 2.5, -18), rhand=palm, rf=-4)
    bow = _pose(r, left=(10, 30, -8), **bow_kw)
    tip = _pose(r, left=(13, 16, -7), sheet=(1, 0.55, -0.3), **bow_kw)  # the scroll swinging out as its hand goes down
    lg, kat = _knees(52, -58)
    down = _pose(r, hips=(52, 0, 0), at=kat, legs=lg, spine=(28, 0, 6), chest=(20, 0, 8), neck=(20, 0, 0), right=(-12, 3.5, -24),
                 rhand=palm, rf=-6, left=(9, 6, -6), lhand=((0.3, 0.6, -0.7), (1, 0, 0)), sheet=(1, 0.0, -0.3))
    # the ring off its collar: sliding forward off it, falling onto its edge, rolling away to its left and falling flat
    slide = dict(tip)
    slide["ring"] = _ring_at(r, tip, (0, 18, -22), _rx(150))
    drop = dict(down)
    drop["ring"] = _ring_at(r, down, (2, RING + 1.2, -26), _rx(174))
    roll = dict(down)  # over on its face now, so its roll to its left turns the other way about its middle
    roll["ring"] = _ring_at(r, down, (12, RING + 1.6, -27), R._mm(_rx(176), _rz(-10 / RING / DEG)))
    roll2 = dict(down)
    roll2["ring"] = _ring_at(r, down, (20, RING + 2.0, -27.5), R._mm(_rx(194), _rz(-18 / RING / DEG)))
    flat = dict(down)
    flat["ring"] = _ring_at(r, down, (23, 1.6, -28), R._mm(_rx(270), _rz(-21 / RING / DEG)))
    wob = dict(down)
    wob["ring"] = _ring_at(r, down, (23, 2.4, -28), R._mm(_rx(262), _rz(-21 / RING / DEG)))
    _keyed(r, "death", 4.8, [(0, _pose(r)), (0.35, rigid), (1.0, look), (1.6, knees), (2.2, bow), (2.55, slide), (2.85, drop), (3.3, roll),
                            (3.75, roll2), (4.1, flat), (4.35, wob), (4.8, flat)])


# ---- the moves -------------------------------------------------------------------------------------------------------

def swipe(r):
    """The rake: its body turns away, the long right arm drawn back low behind it with the fingers gathered; then the
    arm sweeps round across you at the height of your waist, the fingers spread like the tines of a rake, a step after
    it."""
    cock = _pose(r, hips=(0, 10, 0), at=(0, -1.5, 1), spine=(0, 12, 0), chest=(-4, 20, -4), neck=(0, -28, 0), right=(-24, 34, 12),
                 rhand=((-0.3, 0.55, 0.75), (0, 0, -1)), rf=34, poles={"r": (0, 0.3, 1)})
    hit = _pose(r, hips=(2, -10, 0), at=(0, -3, -3), spine=(4, -12, 0), chest=(6, -22, 6), neck=(0, 28, 0), right=(8, 36, -26),
                rhand=((0.65, 0.2, -0.75), (0, -1, 0)), rf=-16, step={"r": (0, 0, -7)}, poles={"r": (0, 1, 0.3)})
    held = _pose(r, hips=(2, -13, 0), at=(0, -3.4, -3.4), spine=(4, -14, 0), chest=(6, -28, 8), neck=(0, 32, 0), right=(13, 38, -20),
                 rhand=((0.75, 0.15, -0.6), (0, -1, 0)), rf=-18, step={"r": (0, 0, -7)}, poles={"r": (0, 1, 0.3)})
    return Move("swipe", "LIGHT", 10, 4, 14, track=8.0, rng=(0, 5.4), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(),
                hit=[("fingers_r", (0, 7, 0), 2.2), ("hand_r", (0, 2, 0), 2.2), ("forearm_r", (0, 10, 0), 1.6)], through=_on(r, held, 0.08),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.55, hit), (1.0, held)], "recover": _back(r, held)})


def swipe_b(r):
    """The backhand: the arm drawn across its body to its left shoulder, high, the fingers curled; then flung back
    across you to its right, the back of the hand leading, with a step of its other foot."""
    cock = _pose(r, hips=(0, -10, 0), at=(0, -1, 1), spine=(0, -10, 0), chest=(-6, -24, 6), neck=(0, 30, 0), right=(12, 54, -10),
                 rf=44, step={"l": (0, 0, -2)}, poles={"r": (0, 1, 0.2)})
    hit = _pose(r, hips=(2, 12, 0), at=(0, -2.5, -3), spine=(4, 12, 0), chest=(6, 24, -6), neck=(0, -30, 0), right=(-20, 42, -32),
                rf=-20, step={"l": (0, 0, -7), "r": (0, 0, 1)}, poles={"r": (-0.2, 1, 0.3)})
    held = _pose(r, hips=(2, 15, 0), at=(0, -2.8, -3.3), spine=(4, 14, 0), chest=(6, 30, -8), neck=(0, -34, 0), right=(-28, 40, -24),
                 rf=-22, step={"l": (0, 0, -7), "r": (0, 0, 1)}, poles={"r": (-0.2, 1, 0.3)})
    return Move("swipe_b", "LIGHT", 10, 4, 14, track=8.0, rng=(0, 5.4), kinds=("combo", "near"), damage=1.0, knock=0.9, plant=(),
                hit=[("fingers_r", (0, 7, 0), 2.2), ("hand_r", (0, 2, 0), 2.2), ("forearm_r", (0, 10, 0), 1.6)], through=_on(r, held, 0.08),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.55, hit), (1.0, held)], "recover": _back(r, held)})


def stride(r):
    """The Long Step (a gap closer): it rears back with one knee drawn up high and its arm drawn back over its shoulder,
    then takes one enormous stride across the room, leaning down into it with its hand reaching along the floor for
    you, and the fingers close."""
    rear = _pose(r, hips=(-8, 0, 0), at=(0, 0.5, 3), spine=(-6, 0, 0), chest=(-6, 6, 0), neck=(-6, -6, 0), right=(-14, 58, 18), rf=22,
                 poles={"r": (0, 0.5, 1)}, step={"r": (0, -16, -6)})
    reach = _pose(r, hips=(14, 0, 0), at=(0, -4, -8), spine=(10, 0, 0), chest=(8, 0, 0), neck=(-14, 0, 0), right=(-6, 26, -40),
                  rhand=((0, 0.3, -1), None), rf=-22, step={"r": (0, -6, -26), "l": (0, 0, 10)}, poles={"r": (0, 1, 0)})
    land = _pose(r, hips=(20, 0, 0), at=(0, -12, -10), spine=(16, 0, 0), chest=(12, 0, 0), neck=(-24, 0, 0), right=(-6, 14, -42),
                 rhand=((0, 0.5, -1), None), rf=62, step={"r": (0, 0, -24), "l": (0, 0, 14)}, poles={"r": (0, 1, 0)})
    grab = _pose(r, hips=(21, 0, 0), at=(0, -13, -10.5), spine=(17, 0, 0), chest=(13, 0, 0), neck=(-25, 0, 0), right=(-6, 13, -41),
                 rhand=((0, 0.55, -1), None), rf=78, step={"r": (0, 0, -24), "l": (0, 0, 14)}, poles={"r": (0, 1, 0)})
    return Move("stride", "HEAVY", 16, 10, 20, title="The Long Step", track=6.0, lock=12, advance=8.0, rng=(5, 14), kinds=("gap",), damage=1.3,
                knock=1.0, plant=(), hit=[("fingers_r", (0, 7, 0), 2.6), ("hand_r", (0, 2, 0), 2.6)], through=grab,
                stages={"windup": [(0.3, _mix(_pose(r), rear, 0.35)), (1.0, rear)], "active": [(0.4, reach), (1.0, land)], "recover": _back(r, grab)})


def back(r):
    """For whoever is behind it: it does not turn round. Its upper body turns right round on its hips, the ring with it,
    and the long arm sweeps across behind it; then it turns back."""
    cock = _pose(r, hips=(0, 16, 0), spine=(0, 48, 0), chest=(0, 52, 0), neck=(0, 10, 0), right=(-24, 38, 4), rf=30, poles={"r": (0, 1, 0)})
    hit = _pose(r, hips=(0, 20, 0), at=(0, -1, 0), spine=(4, 64, 0), chest=(4, 70, 0), neck=(0, 16, 0), right=(10, 36, 28), rf=-16,
                poles={"r": (0, 1, 0)})
    held = _pose(r, hips=(0, 22, 0), at=(0, -1.2, 0), spine=(4, 68, 0), chest=(4, 76, 0), neck=(0, 18, 0), right=(18, 38, 22), rf=-18,
                 poles={"r": (0, 1, 0)})
    return Move("back", "LIGHT", 10, 4, 14, track=0.0, rng=(0, 5.0), kinds=("behind",), damage=1.0, knock=0.8, plant=("l", "r"),
                hit=[("fingers_r", (0, 7, 0), 2.2), ("hand_r", (0, 2, 0), 2.2), ("forearm_r", (0, 10, 0), 1.6)], through=_on(r, held, 0.06),
                stages={"windup": [(0.3, _mix(_pose(r), cock, 0.35)), (1.0, cock)], "active": [(0.55, hit), (1.0, held)], "recover": _back(r, held)})


PALM_UP = ((0, 0, -1), (-1, 0, 0))  # the right hand held out, its palm up (the fingers curl up)


def draw(r):
    """Draw Near: it holds its right arm straight out to you, palm up, and slowly curls its long fingers to beckon you,
    three times, leaning in a little; the ring tilts as if to see you better."""
    def reach(z, rf, lean, s=0.0):  # s: the sway with each beckon, the knees giving and the hips turning toward you
        return _pose(r, hips=(3 + abs(s), 5 * s, 0), at=(0.4 * s, -1 - 1.6 * abs(s), -1.5), spine=(3, 3 * s, 0), chest=(4 + lean, -4 + 4 * s, 0),
                     neck=(8 - 3 * s, -3 * s, 6 - 4 * s), ring=(0, 5 * s, 4 - 9 * s), right=(-5, 58, z), rhand=PALM_UP, rf=rf,
                     left=(4 + 2 * s, 49 - 2 * abs(s), -8 + 2 * s), poles={"r": (-0.4, 1, 0)})
    out = reach(-38, 10, 0)
    return Move("draw", "NONE", 12, 24, 14, title="Draw Near", track=6.0, ability="Draw Near", origin=(-0.3, 3.6, 2.4), rng=(4, 16), kinds=("ranged",),
                damage=0, knock=0, plant=("l", "r"), through=reach(-36, 30, -1),
                stages={"windup": [(0.3, _mix(_pose(r), out, 0.4)), (1.0, out)],
                        "active": [(0.18, reach(-33, 88, -3, 1)), (0.36, reach(-38, 12, 0, -0.7)), (0.54, reach(-33, 88, -3, 1)),
                                   (0.72, reach(-38, 12, 0, -0.7)), (0.9, reach(-32, 90, -4, 1.2)), (1.0, reach(-34, 60, -2, 0.3))],
                        "recover": _back(r, reach(-36, 30, -1))})


def bolts(r):
    """Void Bolts: it holds the blank scroll up and out to its side at the height of its ring, showing it to you, and
    points at you with its other hand; the ring pulses with each of the five bolts, the pointing hand jabbing."""
    def show(jab=0.0, tilt=0.0):
        return _pose(r, at=(0, 0.3, 0), chest=(-3 + 2 * jab, -4, 0), neck=(-2, -10, tilt), ring=(0, -4, tilt), left=(12, 66, -10),
                     right=(-6, 65 - jab, -34 - 4 * jab), rhand=((0, 0.08, -1), (-1, 0, 0)), rf=-10, lf=-6,
                     poles={"l": (0.8, 0.8, 0.3), "r": (-0.3, 1, 0)})
    keys = []
    for k in range(5):
        keys += [((k + 0.3) / 5, show(1.0, 4 if k % 2 else -4)), ((k + 0.85) / 5, show(0.0, 0))]
    return Move("bolts", "NONE", 12, 26, 16, title="Void Bolts", track=8.0, ability="Void Bolts", origin=(0, 4.6, 0.3), rng=(3, 20), kinds=("ranged",),
                damage=0, knock=0, plant=("l", "r"), through=show(),
                stages={"windup": [(0.3, _mix(_pose(r), show(), 0.4)), (1.0, show())], "active": keys, "recover": _back(r, show())})


def unlit(r):
    """Unlit: it brings both hands up and covers its ring with them, bowing its collar into them like someone hiding
    their face; behind its hands it rocks slowly; then it takes them away."""
    def cover(k=0.0):
        p = _pose(r, hips=(5 + 2 * k, 0, 0), at=(0, -2 - 0.8 * k, 0), spine=(7, 0, 0), chest=(9 + 4 * k, 0, 0), neck=(14 + 4 * k, 0, 0),
                  ring=(0, 0, 8 * k))
        fr_ = core.frames(r, core.as_sample(p))
        q = _pose(r, hips=(5 + 2 * k, 0, 0), at=(0, -2 - 0.8 * k, 0), spine=(7, 0, 0), chest=(9 + 4 * k, 0, 0), neck=(14 + 4 * k, 0, 0),
                  ring=(0, 0, 8 * k), right=("on", "ring", (-2, 1, -5)), left=("on", "ring", (4, 2, -6)),
                  rhand=(_ring_dir(fr_, (0, -1, 0)), _ring_dir(fr_, (-1, 0, 0))), rf=14, lf=-30, poles={"r": (-0.6, 1, 0), "l": (0.8, 1, 0)})
        return q
    return Move("unlit", "NONE", 12, 24, 14, title="Unlit", track=0.0, ability="Unlit", origin=(0, 0, 0), rng=(0, 99), kinds=("area",), damage=0,
                knock=0, plant=("l", "r"), lag=ON_TIME, through=cover(),
                stages={"windup": [(0.3, _mix(_pose(r), cover(), 0.3)), (1.0, cover())],
                        "active": [(0.25, cover(1.0)), (0.5, cover(-0.6)), (0.75, cover(1.0)), (1.0, cover(-0.4))], "recover": _back(r, cover())})


def _ring_dir(fr, d):
    """A direction in the ring's own space, in the model's."""
    return R._mv(fr["ring"][0], d)


def summon(r):
    """Spawn of Nothing: it bows from the hips until its ring faces the floor before it, its right hand spread flat on
    the floor; the ring shudders three times, and each time something drops out of it."""
    palm = ((0, 0.15, -1), None)

    def bow(j=0.0):  # j: a shudder, one way or the other; its hips jolt, its back flexes, the scroll arm swings back
        return _pose(r, hips=(38 + 3 * abs(j), 4 * j, 0), at=(0.5 * j, -4.5 - 1.8 * abs(j), 5), spine=(24 - 5 * abs(j), 3 * j, 0), chest=(14 + 3 * abs(j), 0, 4 * j),
                     neck=(28 + 6 * abs(j), 0, 0), ring=(22, 0, 12 * j), right=(-10, 3, -22), rhand=palm, rf=-10 + 8 * abs(j),
                     more={"arm_l": (-14 * abs(j), 0, 6 * abs(j)), "forearm_l": (-10 * abs(j), 0, 0)})
    return Move("summon", "NONE", 14, 26, 16, title="Spawn of Nothing", track=0.0, ability="Spawn of Nothing", origin=(0, 2.0, 1.4), rng=(0, 99),
                kinds=("summon",), damage=0, knock=0, plant=("l", "r"), through=bow(),
                stages={"windup": [(0.3, _mix(_pose(r), bow(), 0.3)), (0.7, _mix(_pose(r), bow(), 0.8)), (1.0, bow())],
                        "active": [(0.15, bow(1)), (0.3, bow(0)), (0.48, bow(-1)), (0.63, bow(0)), (0.8, bow(1)), (1.0, bow(0))],
                        "recover": _back(r, bow())})


def collapse(r):
    """Collapse (HEAVY): it rises to its height and spreads its arms high and wide, the fingers splayed, arching back;
    holds them there; then brings its hands together before its ring in a clap that makes no sound."""
    spread = _pose(r, at=(0, 0.5, 0.5), spine=(-4, 0, 0), chest=(-8, 0, 0), neck=(-10, 0, 0), ring=(-6, 0, 0), right=(-34, 76, -6),
                   left=(34, 76, -6), rf=-24, poles={"r": (0, 1, 0.6), "l": (0, 1, 0.6)})
    clap = _pose(r, hips=(8, 0, 0), at=(0, -3, -2), spine=(6, 0, 0), chest=(10, 0, 0), neck=(6, 0, 0), right=(-1.5, 56, -21), left=(1.5, 56, -21),
                 rhand=((0, -1, -0.15), (0, 0, 1)), rf=-6, poles={"r": (-1, 0.6, 0.3), "l": (1, 0.6, 0.3)})
    after = _pose(r, hips=(9, 0, 0), at=(0, -3.6, -2.4), spine=(7, 0, 0), chest=(12, 0, 0), neck=(8, 0, 0), right=(-1.5, 54, -22), left=(1.5, 54, -22),
                  rhand=((0, -1, -0.2), (0, 0, 1)), rf=-6, poles={"r": (-1, 0.6, 0.3), "l": (1, 0.6, 0.3)})
    return Move("collapse", "HEAVY", 18, 6, 24, title="Collapse", hold=10, track=4.0, ability="Collapse", origin=(0, 0, 0), rng=(0, 13), kinds=("area",),
                damage=1.2, knock=1.4, plant=("l", "r"), hit=[("hand_r", (0, 2, 0), 4.0), ("hand_l", (0, 2, 0), 4.0)], through=after,
                stages={"windup": [(0.3, _mix(_pose(r), spread, 0.35)), (0.7, _mix(_pose(r), spread, 0.85)), (1.0, spread)],
                        "active": [(0.5, clap), (1.0, after)], "recover": _back(r, after)})


def horizon(r):
    """The Event Horizon (PERILOUS, the signature): it spreads its arms wide and leans back as the ring grows behind and
    over it, turning, until it is twice the size of its body; its tails and the scroll stream toward it; then it snaps
    shut and the Herald jerks forward."""
    def wide(k, shake=0.0, turn=0.0):  # shake: it strains against the pull, its knees giving, its arms and tails shivering
        q = abs(shake)
        return _pose(r, hips=(-2 * q, 3 * shake, 0), at=(0.4 * shake, 0.3 - 1.4 * q, 1.2 * k), spine=(-6 * k, 2 * shake, 0),
                     chest=(-12 * k + 4 * shake, 0, 5 * shake), neck=(-14 * k + 3 * q, 0, -4 * shake),
                     ring={"rot": (-10 * k, 0, turn), "pos": (0, 14 * k ** 1.2, 2 * k)},
                     right=(-22 - 12 * k - 2 * shake, 40 + 16 * k + 3 * shake, -10 + 6 * k), left=(22 + 12 * k - 2 * shake, 42 + 16 * k - 3 * shake, -10 + 6 * k),
                     rf=-30 * k + 12 * q, lf=-20 * k, sheet=(0.3 * shake, 0.6, -0.8 * k), poles={"r": (0, 1, 0.5), "l": (0, 1, 0.5)},
                     more={"tail_l0": (-40 * k - 8 * q, 0, -18 * k + 6 * shake), "tail_r0": (-40 * k - 8 * q, 0, 18 * k + 6 * shake),
                           "tail_l1": (-20 * k + 10 * shake, 0, 0), "tail_r1": (-20 * k - 10 * shake, 0, 0)})
    snap = _pose(r, hips=(10, 0, 0), at=(0, -3, -2.5), spine=(10, 0, 0), chest=(14, 0, 0), neck=(18, 0, 0), ring={"rot": (6, 0, 90), "pos": (0, 2, 0)},
                 right=(-6, 40, -18), left=(8, 46, -14), rf=34, lf=10, poles={"r": (-1, 0.5, 0.3), "l": (1, 0.5, 0.3)})
    return Move("horizon", "PERILOUS", 30, 16, 30, title="Event Horizon", signature=True, track=0.0, ability="Event Horizon", origin=(0, 4.6, 0),
                rng=(0, 99), kinds=("area",), damage=0, knock=0, plant=("l", "r"), through=_on(r, snap, 0.15),
                stages={"windup": [(0.3, wide(0.35, 0.3, 14)), (0.55, wide(0.7, -0.4, 28)), (0.8, wide(0.9, 0.5, 38)), (1.0, wide(1.0, -0.6, 45))],
                        "active": [(0.2, wide(1.0, 1.0, 56)), (0.4, wide(1.0, -1.0, 66)), (0.6, wide(1.0, 1.0, 75)), (0.8, wide(1.0, -1.2, 84)),
                                   (1.0, snap)],
                        "recover": [(0.45, _mix(snap, _pose(r), 0.45)), (0.7, _mix(snap, _pose(r), 0.82))]})


HALO_AT = (0, 92, -2)  # where it holds the ring over its collar (x, height, z)


def halo(r):
    """Void Halo: it takes hold of its ring by its side and lifts it off its collar, holds it up high over itself laid
    flat like a halo, turning, and sixteen bolts burst out of it all round; then it sets it back on its collar."""
    def holding(centre, M, s=0.0):  # s: the sway under it, the scroll arm thrown out with a burst
        base = dict(hips=(-abs(s), 4 * s, 0), chest=(-6 - 3 * abs(s), 5 * s, 2 * s), spine=(-3, 3 * s, 0), at=(0.5 * s, 0.4 - 1.5 * abs(s), 0),
                    neck=(-6 * abs(s), 0, -3 * s), left=(16 + 8 * abs(s), 52 + 6 * abs(s), -8 + 3 * s), lf=-16 * abs(s),
                    poles={"r": (-1, 0.3, 0.3), "l": (0.8, 1, 0.3)})
        body_ = _pose(r, **base)
        ring = _ring_at(r, body_, centre, M)
        p = _pose(r, ring=ring, rhand=None, rf=50, **base)
        fr_ = core.frames(r, core.as_sample(p))
        q = _pose(r, ring=ring, right=("on", "ring", (-7.5, 0, -1.4)), rhand=(_ring_dir(fr_, (0, -1, 0)), _ring_dir(fr_, (0, 0, -1))), rf=50, **base)
        return q
    pre = _pose(r, chest=(-2, 0, 0), neck=(-4, 0, 0), right=("on", "ring", (-7.5, 0, -1.4)), rf=46, poles={"r": (-1, 0.4, 0.2)})
    up = holding(HALO_AT, _rx(90))
    turn = [holding((HALO_AT[0], HALO_AT[1] + h, HALO_AT[2]), R._mm(_ry(a), _rx(90)), s)
            for h, a, s in ((1.2, 15, 1.0), (0.4, 30, -0.8), (1.4, 45, 1.0), (0.8, 60, -0.5))]
    half = holding((0, 87, -3), _rx(50), 0.2)
    lower = holding((0, 80, -2), _rx(10))
    return Move("halo", "NONE", 14, 22, 18, title="Void Halo", track=0.0, ability="Void Halo", origin=(0, 5.8, 0.1), rng=(0, 99), kinds=("ranged", "area"),
                damage=0, knock=0, plant=("l", "r"), lag=ON_TIME, through=half,
                stages={"windup": [(0.3, _mix(_pose(r), pre, 0.6)), (0.55, pre), (1.0, up)],
                        "active": [(0.22, turn[0]), (0.48, turn[1]), (0.74, turn[2]), (1.0, turn[3])],
                        "recover": [(0.5, lower), (0.7, _mix(lower, _pose(r), 0.6))]})


def wake(r):
    """Its intro, "Nothing. No sound at all.": it stands bowed and folded, the ring hanging; it straightens slowly to its
    full height, holds out the scroll to you with its other hand open beside it (there is nothing written on it), tilts
    its ring at you, and lowers the scroll."""
    bowed = _pose(r, hips=(10, 0, 0), at=(0, -3, 1.5), spine=(14, 0, 0), chest=(14, 0, 0), neck=(26, 0, 0), ring=(10, 0, 0), right=(-8, 14, -6), rf=-4)
    sunk = _pose(r, hips=(12, 3, 0), at=(0.3, -4.2, 1.8), spine=(16, 0, 0), chest=(17, 0, 3), neck=(32, 0, 0), ring=(16, 0, -6), right=(-8, 12, -5), rf=-10)
    tall = _pose(r, at=(0, 0.5, 0), chest=(-3, 0, 0), neck=(-6, 0, 0), ring=(-2, 0, 0), rf=4)
    show = lambda tilt: _pose(r, at=(0, 0.3, 0), chest=(-2, 0, 0), neck=(2, 0, tilt), ring=(0, 0, tilt * 0.6), left=(2, 60, -17), right=(-14, 54, -14),
                              rhand=((0, -0.3, -1), (-1, 0, 0)), rf=-20, poles={"l": (0.6, 1, 0.2), "r": (-0.6, 1, 0.2)})
    return Move("wake", "NONE", 20, 36, 14, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=("l", "r"), through=tall,
                stages={"windup": [(0.3, _mix(_pose(r), bowed, 0.4)), (0.7, bowed), (1.0, sunk)],
                        "active": [(0.25, _mix(bowed, tall, 0.7)), (0.4, tall), (0.58, show(0)), (0.72, show(14)), (0.86, show(-6)),
                                   (1.0, _mix(show(4), tall, 0.6))],
                        "recover": _back(r, tall)})


def moves(r):
    return [swipe(r), swipe_b(r), stride(r), back(r), draw(r), bolts(r), unlit(r), summon(r), collapse(r), horizon(r), halo(r), wake(r)]


def _ring_tracks(r, mv):
    """The ring's swell: a pulse with each Void Bolt and the Halo's burst; the Event Horizon grows it to nearly three
    times its size and lets it snap back."""
    T = core.TICK
    by = {m.id: m for m in mv}

    def put(mid, pts):
        m = by[mid]
        W, A, Rc = m.windup * T, m.active * T, m.recover * T
        c = r.clips[core.clip_name(m)]
        c.scale("ring", [(f(W, A, Rc), s, s, s) for f, s in pts], "cat")
    pulses = [(lambda W, A, R_: 0.0, 1.0), (lambda W, A, R_: W, 1.0)]
    for k in range(5):
        pulses += [(lambda W, A, R_, k=k: W + A * (k + 0.3) / 5, 1.16), (lambda W, A, R_, k=k: W + A * (k + 0.75) / 5, 1.0)]
    pulses += [(lambda W, A, R_: W + A + R_, 1.0)]
    put("bolts", pulses)
    put("horizon", [(lambda W, A, R_: 0.0, 1.0), (lambda W, A, R_: 0.3 * W, 1.4), (lambda W, A, R_: 0.7 * W, 2.1), (lambda W, A, R_: W, 2.7),
                    (lambda W, A, R_: W + 0.3 * A, 2.95), (lambda W, A, R_: W + 0.6 * A, 2.75), (lambda W, A, R_: W + 0.8 * A, 2.95),
                    (lambda W, A, R_: W + A, 0.8), (lambda W, A, R_: W + A + 0.15 * R_, 1.06), (lambda W, A, R_: W + A + 0.4 * R_, 1.0),
                    (lambda W, A, R_: W + A + R_, 1.0)])
    put("halo", [(lambda W, A, R_: 0.0, 1.0), (lambda W, A, R_: W, 1.0)]
        + [(lambda W, A, R_, f=f: W + f * A, v) for f, v in ((0.22, 1.3), (0.36, 1.05), (0.48, 1.25), (0.62, 1.05), (0.74, 1.3), (0.88, 1.05))]
        + [(lambda W, A, R_: W + A, 1.1), (lambda W, A, R_: W + A + R_, 1.0)])


def clips(r):
    r.run_speed = (0.14, 0.3)
    idle(r)
    walk(r)
    run(r)
    turns(r)
    flinches(r)
    reactions(r)
    mv = [_unwrap(m) for m in moves(r)]
    for m in mv:
        stage_clips(r, m)
    _ring_tracks(r, mv)
    core.ground_pass(r, [(["fingers_r"], [("hand_r", 0, 40), ("forearm_r", 0, 30)])], clips=[n for n in r.clips if n != "death"])
    # broken and down on a knee (and back up, and dead), each foot tips at the ankle to lie on the floor, then the knee
    feet = [(["foot_" + s], [("foot_" + s, 0, 50), ("shin_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise"])
    core.ground_pass(r, feet, clips=["death"], skip=())
    return mv


FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["swipe_b"]]},
            {"move": "bolts", "weight": 3, "when": "far"},
            {"move": "draw", "weight": 2, "when": "far", "then": [["swipe"]]},
            {"move": "stride", "weight": 2, "when": "far", "then": [["swipe"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
        # the shrine empties with the second phase, as in GateBestiary
        {"transition": "summon", "pool": [
            {"move": "swipe", "weight": 3, "when": "near", "then": [["swipe_b"]]},
            {"move": "unlit", "weight": 2},
            {"move": "summon", "weight": 1},
            {"move": "bolts", "weight": 2, "when": "far"},
            {"move": "stride", "weight": 2, "when": "far", "then": [["swipe", "swipe_b"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
        {"transition": "horizon", "pool": [
            {"move": "horizon", "weight": 1},
            {"move": "collapse", "weight": 2, "when": "near"},
            {"move": "halo", "weight": 2},
            {"move": "draw", "weight": 1, "when": "far", "then": [["collapse"]]},
            {"move": "swipe", "weight": 3, "when": "near", "then": [["swipe_b"]]},
            {"move": "stride", "weight": 2, "when": "far", "then": [["swipe", "swipe_b"]]},
            {"move": "back", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}

# Its sounds (SPEC §35.8): almost none. Glass that rings, a void that hums, cloth; no voice and no footsteps to speak of.
# Chosen by ear on paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("block.amethyst_block.resonate", 0.8, 0.5), ("item.armor.equip_leather", 0.6, 0.4)],
    "tell_heavy": [("block.beacon.deactivate", 0.7, 0.6), ("block.amethyst_block.resonate", 0.7, 0.4)],
    "tell_perilous": [("block.end_portal.spawn", 0.5, 0.5), ("entity.warden.sonic_charge", 0.6, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 0.7, 0.5), ("entity.phantom.flap", 0.8, 0.4)],
    "swing_heavy": [("entity.player.attack.knockback", 0.6, 0.5), ("block.respawn_anchor.deplete", 0.7, 0.6)],
    "cast": [("block.amethyst_block.chime", 0.7, 0.5), ("entity.illusioner.cast_spell", 0.5, 0.5)],
    "impact": [("block.amethyst_cluster.break", 0.6, 0.6), ("block.respawn_anchor.deplete", 0.6, 0.5)],
    "roar": [("block.end_portal.spawn", 0.6, 0.6), ("block.beacon.deactivate", 0.6, 0.5)],
    "step": [("block.wool.step", 0.35, 0.4)],
}
MOVE_SOUNDS = {"draw": {"swing": "cast", "impact": ""}, "bolts": {"swing": "cast"}, "unlit": {"swing": "cast", "impact": ""},
               "summon": {"swing": "cast", "impact": ""}, "horizon": {"swing": "roar"}, "halo": {"swing": "cast"},
               "wake": {"swing": "", "impact": ""}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.6, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where its parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_r", (0, 0, 0)), ("fingers_r", (0, 7, 0)), ("hand_l", (0, 0, 0)), ("ring", (0, -RING, 0)), ("ring", (0, 0, 0)),
                 ("foot_l", (0, 2, 0)), ("foot_r", (0, 2, 0)), ("sheet1", (0, 10, 0)), ("seal", (0, 6, 0)), ("tail_l1", (0, 12, 0)),
                 ("arm_r", (0, 0, 0)), ("forearm_l", (0, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
