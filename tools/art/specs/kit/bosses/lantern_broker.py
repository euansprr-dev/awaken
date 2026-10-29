"""The Lantern Broker (SPEC §35.11, docs/bosses/ROLLOUT.md): the Lantern Night Market's Lord. The Pedlar, picked from
his silhouette round: a tall, thin travelling merchant in a long coat to the floor, 4.5 blocks to his hat, carrying his
whole shop on his back on a pack frame that towers over him to 6.1 blocks.

Dark lacquer, red silk, brass, paper, a moth's grey and one warm lantern light. A wide stepped hat with four tiny
lanterns swinging from its brim; under it his face is shadow, two old brass cash coins (square holes in them) for
eyes, a silk veil over his mouth. A tall crook staff in his right hand with a lantern on its hook, a sickle in his left
with a chain coiled in a loop from its pommel to a brass weight. The frame is a little roofed stall: a lacquered chest
of drawers at its foot, rolled carpet and scrolls, a merchant's scale, two moth cages and eleven lanterns, paper and
glass, all swinging on their own. He is upright and quick, a showman with a jaunty step (the Abbot's Pilgrim is the
crushed, trudging back-load; this one carries his with pride).

Moves (SPEC §35.10): the Chain Lash (the weighted chain flung out straight, LIGHT) and the crook's sweep after it
(LIGHT), a pack swing for whoever is behind him (he twists and the frame clubs round), Lantern Bolts (the staff raised,
its lantern pumped at you, three or five), the Market Step (he tips his hat in a bow and is somewhere else), Release
the Moths (he reaches back over his shoulder, opens a cage and shrugs the pack), Paper Lanterns (a full turn on his
heel with his arms out, every lantern swinging out) and Lights Out (he plants the staff a stride ahead so its lantern
swings back to his mouth, cups it and blows it out: the signature). He opens with "Lanterns down": the staff planted, a bow with his hand on his heart,
the chain let fall. He dies sitting down against his pack, the hat tipping over his face, and last of all the staff
falls.
"""
import math

from kit import core, ik, parts, plans
from kit import moves as M
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "lantern_broker"
SCALE = 2.0  # the hitbox on the LANTERN model: 1.2 x 4.4 blocks (he stands 4.3 to the hat; the frame rises to 5.7)

P = plans.Biped(leg=34, thigh=17, foot=(3, 2, 8), pelvis=(8, 4, 6), spine=(6, 6, 5), chest=(12, 12, 7), neck=(2, 3, 2),
                head=(7, 7, 7), arm=(3, 14, 3), forearm=(3, 13, 3), hand=(2, 5, 3), stance=2.5, shoulder=7, hunch=0, leg_w=(3, 3))
STAFF_H = 74  # the shaft's top above the ground; the crook rises 5 more
GRIP_R = (-12.5, 30, -3)  # the staff hand at rest (x, height, z): out past the hat's brim
GRIP_L = (10, 29, -4)  # the sickle hand at rest, by his left hip
# the coiled chain: each link's turn on the last (rz), a loop hanging from the pommel and back up to his sash
CHAIN_LOOP = (0, 8, 30, 50, 50, 32, 10)
LAMP = (0, 7, 0)  # the staff lantern's middle, in the lamp bone's space (model y down)
SICKLE_TIP = (0, -8, -6)  # the sickle's point, in the sickle bone's space
WEIGHT = (0, 5, 0)  # the chain weight's middle, in chain6's space

# The lanterns on the pack: (name, bar's y, x, z, size, paper). Bars at y 14, -8, -26, -40 in the pack's space; the side
# ones hang off the bars' ends where they widen him from the front, the rest behind the frame.
PACK_LANTERNS = (("pl0", -6, 13.5, 9, 3, True), ("pl1", -6, -13.5, 9, 3, True), ("pl2", -6, -5, 11, 2, False),
                 ("pl3", -6, 5, 11, 2, False), ("pl4", -24, 13.5, 9, 2, False), ("pl5", -24, -13.5, 9, 2, False),
                 ("pl6", -24, 0, 11, 4, True), ("pl7", -38, -7, 11, 2, True), ("pl8", -38, 7, 11, 2, False),
                 ("pl9", -43, 13, 5, 2, False), ("pl10", -43, -13, 5, 2, False))


def _lantern(parent, name, at, size=3, paper=False, rot=(0, 0, 0)):
    """A lantern on its own bone hung from a hook (a spring swings it): a cord, a brass cap, the lit body (a paper shade,
    or glass in a brass frame), a brass foot. Its cubes are named by part (lcord_, lcap_, lbody_ or pbody_, lfoot_)."""
    b = parent.bone(name, at, rot)
    w = size + 1 if paper else size
    b.cube("lcord_" + name, -0.5, 0, -0.5, 1, 2, 1)
    b.cube("lcap_" + name, -(size + 1) / 2, 2, -(size + 1) / 2, size + 1, 1, size + 1)
    b.cube(("pbody_" if paper else "lbody_") + name, -w / 2, 3, -w / 2, w, size + 1, w)
    b.cube("lfoot_" + name, -(size - 1) / 2 if size > 1 else -0.5, 4 + size, -(size - 1) / 2 if size > 1 else -0.5,
           max(1, size - 1), 1, max(1, size - 1))
    return b


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4, -1, -3, 8, 4, 6)
    # legs: long and thin, black trousers into tall lacquered boots and pointed slippers with curled toes. The coat's
    # skirts hang from the thighs (front, outside and back), so they kick with the knees and part at the vents; the
    # lower half of each is on a spring
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 18, 3)
        # the front panels: 6 wide, the left lapping over the right at the middle; the back ones meet at the vent
        x0, xf = (-2, -3) if side > 0 else (-3, -3)
        zf = -3.3 if side > 0 else -3
        xs = 3 if side > 0 else -4  # the outside panel
        th.cube("coatf_" + nm, xf, -1, zf, 6, 18, 1)
        th.cube("coats_" + nm, xs, -1, -3, 1, 18, 6)
        th.cube("coatb_" + nm, x0, -1, 2, 5, 18, 1)
        sk = th.bone("skirt_" + nm, (0, 17, 0), (0, 0, -side * 6))
        sk.cube("skirtf_" + nm, xf, 0, zf, 6, 14, 1)
        sk.cube("skirts_" + nm, xs, 0, -3, 1, 14, 6)
        sk.cube("skirtb_" + nm, x0, 0, 2, 5, 14, 1)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -1.5, 0, -1.5, 3, 15, 3)
        sn.cube("boot_" + nm, -2, 6, -2, 4, 9, 4)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -1.5, 0, -6, 3, 2, 8)
        ft.cube("toe_" + nm, -1, 1, -8, 2, 1, 2)
        ft.cube("curl_" + nm, -0.5, 0, -8.5, 1, 1, 1)
    # a narrow waist wound in a red silk sash, knotted on his left with its tails hanging
    sp = pel.bone("spine", (0, -2, 0))
    sp.cube("spine", -3, -6, -2.5, 6, 6, 5)
    sp.cube("sash", -3.5, -5, -3, 7, 4, 6)
    sp.cube("knot", 1, -4.5, -3.8, 2, 2, 1)
    st = sp.bone("sash_tail", (2, -2.5, -3.6))
    st.cube("sash_tail0", -1, 0, -0.5, 1, 8, 1)
    st.cube("sash_tail1", 0, 0, -0.5, 1, 10, 1)
    # the coat's body: it widens to square shoulders under a short shoulder cape, open down the front over red silk,
    # with a tall stand-up collar round the jaw; leather straps over the shoulders carry the frame
    ch = sp.bone("chest", (0, -6, 0))
    ch.cube("chest0", -5, -6, -3, 10, 6, 6)
    ch.cube("chest1", -6, -12, -3.5, 12, 6, 7)
    ch.cube("lapel_l", 1, -12, -4.3, 4, 8, 1)
    ch.cube("lapel_r", -5, -12, -4.3, 4, 8, 1)
    ch.cube("cape", -7, -13, -4, 14, 3, 8)
    ch.cube("cape_hem", -7.5, -11, -4.5, 15, 1, 9)
    ch.cube("collar_l", 4, -17, -3, 1, 5, 5)
    ch.cube("collar_r", -5, -17, -3, 1, 5, 5)
    ch.cube("collar_b", -5, -17, 2, 10, 5, 1)
    for nm, x in (("strap_l", 2.5), ("strap_r", -4.5)):
        ch.cube(nm, x, -14, -4.5, 2, 1, 9)
    # long thin arms in wide sleeves turned back in red, long gloved hands
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 7, -11, 0))
        arm = sh.bone("arm_" + nm, (0, 0, 0))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 15, 3)
        fa = arm.bone("forearm_" + nm, (0, 14, 0))
        fa.cube("forearm_" + nm, -1.5, 0, -1.5, 3, 13, 3)
        fa.cube("cuff_" + nm, -2, 8, -2, 4, 5, 4)
        hd_ = fa.bone("hand_" + nm, (0, 13, 0))
        hd_.cube("hand_" + nm, -1, 0, -1.5, 2, 5, 3)
    # the head: shadow under the hat, two old cash coins for eyes, a silk veil over the mouth
    nk = ch.bone("neck", (0, -12, -0.5))
    nk.cube("neck", -1, -3, -1, 2, 3, 2)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("head", -3.5, -7, -3.5, 7, 7, 7)
    hd.cube("coin_l", 0.5, -6, -4.1, 3, 3, 1)
    hd.cube("coin_r", -3.5, -6, -4.1, 3, 3, 1)
    hd.cube("veil", -3.5, -3, -4, 7, 3, 1)
    # the hat: a wide brim stepping up to a crown with a red band and a brass finial, tipped forward over his eyes;
    # four tiny lanterns hang from under the brim (hung plumb against the tip)
    hat = hd.bone("hat", (0, -6, 0), (8, 0, 0))
    hat.cube("brim0", -10, -1, -10, 20, 1, 20)
    hat.cube("brim1", -7, -2, -7, 14, 1, 14)
    hat.cube("crown0", -4.5, -5, -4.5, 9, 3, 9)
    hat.cube("band", -5, -3, -5, 10, 1, 10)
    hat.cube("crown1", -2.5, -7, -2.5, 5, 2, 5)
    hat.cube("finial", -1, -9, -1, 2, 2, 2)
    for k, (x, z) in enumerate(((7, -7), (-7, -7), (7.5, 7), (-7.5, 7))):
        b = hat.bone(f"hatlan{k}", (x, 0, z), (-8, 0, 0))
        b.cube(f"hcord{k}", -0.5, 0, -0.5, 1, 2, 1)
        b.cube(f"hlan{k}", -1, 2, -1, 2, 3, 2)
    # the pack frame: a little roofed stall on his back, leaning back a touch. A lacquered chest of drawers at its
    # foot with a rolled carpet on it and scrolls behind, two uprights and four bars, a red roof with upturned eaves
    pk = ch.bone("pack", (0, -5, 4), (-4, 0, 0))
    pk.cube("box", -8, 2, 0, 16, 12, 8)
    pk.cube("carpet", -9, -1, 1, 18, 3, 3)
    pk.cube("scroll0", 4, -7, 5, 2, 9, 2)
    pk.cube("scroll1", 6.5, -5, 6, 2, 7, 2)
    for nm, x in (("post_l", 11), ("post_r", -13)):
        pk.cube(nm, x, -42, 8, 2, 58, 2)
    for k, y in enumerate((14, -8, -26, -40)):
        pk.cube(f"bar{k}", -14, y, 8, 28, 2, 2)
    pk.cube("roof0", -15, -45, 3, 30, 2, 12)
    pk.cube("roof1", -11, -47, 5, 22, 2, 8)
    pk.cube("ridge", -6, -49, 7, 12, 2, 4)
    pk.cube("spire", -1, -52, 8, 2, 3, 2)
    for k, (x, z) in enumerate(((13.5, 2), (-15.5, 2), (13.5, 14), (-15.5, 14))):
        pk.cube(f"eave{k}", x, -46, z, 2, 1, 1)
    # its lanterns, a merchant's scale and two cages of moths, hung plumb against the frame's lean
    for nm, y, x, z, size, paper in PACK_LANTERNS:
        _lantern(pk, nm, (x, y, z), size, paper, rot=(4, 0, 0))
    sc = pk.bone("scale", (8, -24, 11), (4, 0, 0))  # on the left, the hanging cage on the right
    sc.cube("scale_cord", -0.5, 0, -0.5, 1, 2, 1)
    sc.cube("scale_beam", -4, 2, -0.5, 8, 1, 1)
    sc.cube("pan_l", 1.5, 4, -1.5, 3, 1, 3)
    sc.cube("pan_r", -4.5, 4, -1.5, 3, 1, 3)
    c0 = pk.bone("cage0", (8, -8, 9))  # sat on the second bar, where his left hand reaches it over his shoulder
    c0.cube("cage0_roof", -2.5, -6, -2.5, 5, 1, 5)
    c0.cube("cage0", -2, -5, -2, 4, 5, 3)
    c0.cube("cage0_ring", -0.5, -7, -0.5, 1, 1, 1)
    d0 = c0.bone("door0", (-2, -5, 1.5))
    d0.cube("door0", 0, 0, -0.5, 4, 5, 1)
    c1 = pk.bone("cage1", (-8, -24, 11), (4, 0, 0))  # hung from the third
    c1.cube("cage1_cord", -0.5, 0, -0.5, 1, 2, 1)
    c1.cube("cage1_roof", -2.5, 2, -2.5, 5, 1, 5)
    c1.cube("cage1", -2, 3, -2, 4, 5, 3)
    d1 = c1.bone("door1", (-2, 3, 1.5))
    d1.cube("door1", 0, 0, -0.5, 4, 5, 1)
    # the hands at rest: the right out past the brim with the staff stood upright on the ground, the left by his hip
    ik.rest_limb(r, "arm_r", "forearm_r", "hand_r", GRIP_R, (-1, 0, 0.8))
    ik.rest_limb(r, "arm_l", "forearm_l", "hand_l", GRIP_L, (1, 0, 0.8))
    fr = core.frames(r, {})
    r.by_name["hand_r"].rot = ik.orient(r, fr, "hand_r", (0, 1, 0), (1, 0, 0))
    r.by_name["hand_l"].rot = ik.orient(r, fr, "hand_l", (0, 1, -0.25), (1, 0, 0))
    # the staff: a tall shaft through his fist, brass bands, a ferrule, and a crook curling forward over the top with
    # his lantern on its hook
    sf = r.by_name["hand_r"].bone("staff", (0, 2.5, 0))
    fr = core.frames(r, {})
    g = ik.at(fr, "staff")[1]
    r.staff_g, r.staff_top = g, -(STAFF_H - g)  # the grip's height over the foot; the shaft's top in its space
    top = -(STAFF_H - g)
    sf.cube("shaft", -1, top, -1, 2, STAFF_H, 2)
    sf.cube("ferrule", -1.5, g - 3, -1.5, 3, 3, 3)
    sf.cube("band0", -1.5, top + 9, -1.5, 3, 2, 3)
    sf.cube("band1", -1.5, top + 30, -1.5, 3, 1, 3)
    sf.cube("knob", -1.5, top - 1, -1.5, 3, 2, 3)
    sf.cube("crook0", -1, top - 4, -1, 2, 3, 2)
    sf.cube("crook1", -1, top - 5, -7, 2, 2, 7)
    sf.cube("crook2", -1, top - 3, -8, 2, 3, 2)
    lp = sf.bone("lamp", (0, top - 3, -4.5))
    lp.cube("lamp_cord", -0.5, 0, -0.5, 1, 2, 1)
    lp.cube("lamp_cap", -2.5, 2, -2.5, 5, 1, 5)
    fl = lp.bone("flame", (0, 5.5, 0))  # the lit glass on its own bone: Lights Out shrinks it to nothing
    fl.cube("lamp_glass", -2, -2.5, -2, 4, 5, 4)
    for k, (x, z) in enumerate(((1.5, 1.5), (-2.5, 1.5), (1.5, -2.5), (-2.5, -2.5))):
        lp.cube(f"lamp_post{k}", x, 3, z, 1, 5, 1)
    lp.cube("lamp_base", -2.5, 8, -2.5, 5, 1, 5)
    lp.cube("lamp_drop", -0.5, 9, -0.5, 1, 1, 1)
    # the sickle in his left fist, and from its pommel a chain coiled in a loop to a little brass weight
    sk = r.by_name["hand_l"].bone("sickle", (0, 2.5, 0))
    sk.cube("haft", -0.5, -7, -0.5, 1, 10, 1)  # +y: down to the pommel
    sk.cube("blade0", -0.5, -8, -5, 1, 1, 5)
    sk.cube("blade1", -0.5, -7, -7, 1, 2, 2)
    parts.chain(sk, "chain", (0, 3, 0), 7, link=(2, 4, 1))
    for k, a in enumerate(CHAIN_LOOP):
        r.by_name[f"chain{k}"].rot = (0, 0, a)
    r.by_name["chain6"].cube("weight", -1, 3, -1, 2, 3, 2)
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.8
    # secondary parts (SPEC §35.10): every lantern swings on its hook, slowest the biggest; the skirts, the sash, the
    # chain and the cages trail; the frame itself carries a little weight on his back
    lan = [(nm, 0.75, 1.3 + 0.12 * (4 - size), 0.14) for nm, _, _, _, size, _ in PACK_LANTERNS]
    r.springs = lan + [(f"hatlan{k}", 0.8, 2.0, 0.16) for k in range(4)] + [("lamp", 0.65, 1.3, 0.14), ("scale", 0.6, 1.5, 0.18),
                                                                            ("cage1", 0.6, 1.4, 0.2), ("pack", 0.06, 3.0, 0.45)] \
        + [(f"skirt_{s}", 0.5, 2.4, 0.3) for s in "lr"] + [("sash_tail", 0.6, 2.2, 0.25)] \
        + [(f"chain{k}", 0.35 + 0.05 * k, 2.2, 0.25) for k in range(1, 7)]
    r.guard = ["staff", "sickle", "chain6"]  # the staff stands on its foot; it and the blade never go into the ground
    return r


LACQUER = Ramp("#0b0809", "#130e10", "#1c1418", "#271b21", "#33232b", "#422e37", "#553b46", "#6c4b58")
SILK = Ramp("#2a080a", "#420c10", "#5e1418", "#7c1e22", "#9c2a2c", "#bc3a36", "#d85446")
BRASS = Ramp("#2e220c", "#46340f", "#5e4616", "#7a5e22", "#987a30", "#b8983e", "#d6b658", "#f0d888")
PAPER = Ramp("#5a4e3e", "#6e6250", "#908268", "#b2a282", "#d0c29e", "#e8dcba")
MOTH = Ramp("#2a2622", "#3c3630", "#504840", "#665c52", "#7e7266", "#988a7c")
CLOTH = Ramp("#06050a", "#0c0a10", "#141118", "#1c1822", "#26202e", "#322a3a")
WOOD = Ramp("#120c08", "#1c130c", "#281b11", "#352418", "#452f20", "#573c29")
STEEL = Ramp("#14161a", "#1e2126", "#2a2e34", "#3a3f46", "#50565e", "#6c737c", "#9aa2aa")
LIGHT = ("#b8500c", "#e8781a", "#ffa030", "#ffc860", "#fff0b8")


def paint(r, P_):
    """Values (SPEC §35.4): the lantern light brightest (eleven on the frame, four on the brim, the big one on his
    crook, and his coin eyes), then the paper and the brass, the red silk in the middle, the lacquered coat and hat
    darkest, glossy, and his face the darkest thing on him."""
    of = lambda *ps: parts.cubes_of(r, *ps)
    # the lacquered coat: a long gloss down each side face, a faint woven lozenge in the black, brass thread round the
    # hems with a little coin worked into it every few stitches, the red silk lining showing at the open front
    gloss = lambda f, i, j: (1.3 if f.name in SIDES and f.w >= 3 and i == 1 else
                             -0.5 if (i + j) % 4 == 0 and (i - j) % 4 == 0 else 0)
    coat = of("coat", "skirt", "chest", "cape", "lapel_", "collar_", "arm_", "forearm_")
    for c in coat:
        P_.paint(c, LACQUER, 3.0, gloss, grad=1.0, rim=0.9, ao=0.9, noise=0.2)
    for c in of("skirtf_", "skirts_", "skirtb_") + ["cape_hem"]:
        for fname in SIDES:
            f = P_.face(c, fname)
            rows = (f.h - 2,) if c != "cape_hem" else (0,)
            for i in range(f.w):
                for j in rows:
                    f.set(i, j, BRASS.at(4.6 if i % 3 else 6.0))
                    if c != "cape_hem":
                        f.set(i, j + 1, SILK.at(3.0))
    for c, inner in (("coatf_l", "west"), ("skirtf_l", "west"), ("coatf_r", "east"), ("skirtf_r", "east"),
                     ("lapel_l", "west"), ("lapel_r", "east")):
        P_.paint(c, SILK, 3.6, faces=(inner,))
        if c.startswith("lapel_"):  # the lapels turn back to show a thread of the silk at their edge
            f = P_.face(c, "north")
            i = f.w - 1 if c.endswith("_l") else 0
            for j in range(f.h):
                f.set(i, j, SILK.at(4.2))
    for c in ("lapel_l", "lapel_r"):  # three brass frog toggles down each lapel
        f = P_.face(c, "north")
        i = 0 if c.endswith("_l") else f.w - 1
        for j in (1, 4, 7):
            f.set(i, j, BRASS.at(6.4))
            f.set(i + (1 if c.endswith("_l") else -1), j, BRASS.at(4.4))
    for c in ("collar_l", "collar_r", "collar_b"):  # the collar's inside is silk
        P_.paint(c, SILK, 2.4, faces=("east" if c == "collar_r" else "west" if c == "collar_l" else "north",))
    P_.paint("chest1", SILK, 3.4, faces=("north",), pattern=lambda f, i, j: (-0.4 if j % 4 == 3 else 0))
    for c in of("cuff_"):
        P_.paint(c, SILK, 3.6, lambda f, i, j: (1.2 if j == 0 else -0.8 if j == f.h - 1 else 0), rim=0.8)
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                f.set(i, 1, BRASS.at(5.0))
    # the sash: red silk wound twice (a dark line between the turns), the knot and its tails with brass tassel ends
    P_.paint("sash", SILK, 3.6, lambda f, i, j: (-1.6 if j == 2 else 0.8 if j == 0 else 0), grad=0.6, rim=1.0)
    P_.paint("knot", SILK, 4.4, rim=1.2)
    for c in of("sash_tail"):
        P_.paint(c, SILK, 3.4, lambda f, i, j: (-0.8 if j % 3 == 0 else 0), grad=1.4)
        for fname in SIDES:
            f = P_.face(c, fname)
            f.set(0, f.h - 1, BRASS.at(5.6))
    # trousers, gloves and the leather: plum-black cloth, glossy boots, the straps with brass buckles
    for c in ["pelvis", "spine", "neck"] + of("thigh_", "shin_", "hand_"):
        P_.paint(c, CLOTH, 2.6, lambda f, i, j: (0.8 if (i + 2 * j) % 7 == 0 else 0), noise=0.3)
    for c in of("boot_", "foot_", "toe_", "curl_"):
        P_.paint(c, LACQUER, 3.2, lambda f, i, j: (1.6 if f.name in SIDES and i == 1 else 0), rim=1.4)
    for c in of("boot_"):
        for fname in SIDES:
            f = P_.face(c, fname)
            for i in range(f.w):
                f.set(i, 0, BRASS.at(4.2))
    for c in of("strap_"):
        P_.paint(c, WOOD, 3.4, rim=0.8)
        f = P_.face(c, "top")
        f.set(f.w // 2, 1, BRASS.at(6.0))
    # the face: shadow, the veil darker still; the eyes are old brass cash coins with square holes, lit from within
    P_.paint("head", LACQUER, 0.6, grad=0.3, rim=0.2, noise=0.15)
    P_.paint("veil", LACQUER, 1.6, lambda f, i, j: (SILK.at(2.0) if j == 0 else -0.6 if i % 2 else 0), grad=0.4)
    with P_.eyes():
        for c in ("coin_l", "coin_r"):
            P_.paint(c, BRASS, 5.0, rim=0.8)
            f, g = P_.face(c, "north"), P_.face(c, "north", glow=True)
            for i, j in f.each():
                hole, corner = (i, j) == (1, 1), (i + j) % 2 == 0
                f.set(i, j, LACQUER.at(0) if hole else BRASS.at(5.2 if corner else 6.8))
                if not hole:
                    g.set(i, j, hexc(LIGHT[2] if corner else LIGHT[3], 130 if corner else 210))
    # the hat: lacquer, the brim stepped in rings, its underside in shadow, a red band and a brass finial
    for c in ("brim0", "brim1", "crown0", "crown1"):
        P_.paint(c, LACQUER, 3.4, lambda f, i, j: (1.0 if f.name == "top" and (min(i, j, f.w - 1 - i, f.h - 1 - j) % 3 == 0) else 0),
                 rim=1.0, ao=0.6, noise=0.2)
    for c in ("brim0", "brim1"):
        P_.paint(c, LACQUER, 0.8, faces=("bottom",))
    P_.paint("band", SILK, 3.8, lambda f, i, j: (1.0 if i % 4 == 0 else 0), rim=1.0)
    P_.paint("finial", BRASS, 5.4, rim=1.6)
    # the staff: black lacquered bamboo, a pale node every few px; brass bands, ferrule and knob; the crook bamboo too
    node = lambda f, i, j: (1.6 if f.name in SIDES and j % 7 == 0 else 0.6 if f.name in SIDES and i == 0 else 0)
    for c in ("shaft", "crook0", "crook1", "crook2"):
        P_.paint(c, WOOD, 2.6, node, grad=0.6, rim=1.0, noise=0.3)
    for c in ("ferrule", "band0", "band1", "knob"):
        P_.paint(c, BRASS, 4.6, lambda f, i, j: (1.4 if j == 0 else 0), rim=1.4)
    # the sickle: a black haft wrapped in red, a steel blade with a bright edge; the chain dark iron, the weight brass
    P_.paint("haft", WOOD, 2.4, lambda f, i, j: (SILK.at(3.6) if 3 <= j <= 7 and j % 2 else 0))
    for c in ("blade0", "blade1"):
        P_.paint(c, STEEL, 4.0, rim=1.4)
        f = P_.face(c, "bottom")
        for i, j in f.each():
            f.set(i, j, STEEL.at(6.4))
    for c in of("chain"):
        P_.paint(c, STEEL, 2.6, lambda f, i, j: (1.6 if j == 0 or j == f.h - 1 else 0), rim=1.0)
    P_.paint("weight", BRASS, 4.8, lambda f, i, j: (1.4 if j == 0 else -1.0 if j == f.h - 1 else 0), rim=1.4)
    # the lanterns: brass caps and feet; glass ones lit inside a brass frame, paper ones glowing through ribbed paper
    # with red bands top and bottom
    for c in of("lcap_", "lfoot_", "lamp_cap", "lamp_base", "lamp_post", "lamp_drop", "lcord_", "lamp_cord", "hcord"):
        P_.paint(c, BRASS, 4.4, lambda f, i, j: (1.4 if j == 0 else 0), rim=1.2)

    def glass(c):
        P_.paint(c, BRASS, 4.0)
        for fname in ("top", "bottom") + SIDES:
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                frame = fname in SIDES and (i in (0, f.w - 1) and f.w > 2)
                if frame:
                    f.set(i, j, BRASS.at(5.4))
                    continue
                d = abs(j - (f.h - 1) / 2) / max(1, f.h / 2)
                f.set(i, j, hexc(LIGHT[3] if d < 0.5 else LIGHT[1]))
                g.set(i, j, hexc(LIGHT[3] if d < 0.5 else LIGHT[2], 200 if fname in SIDES else 120))

    def paper(c, ribs=True):
        red = R._h(c, 3) % 2 == 0  # half of them red paper, half cream
        for fname in ("top", "bottom") + SIDES:
            f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
            for i, j in f.each():
                band = fname in SIDES and (j == 0 or j == f.h - 1)
                rib = ribs and fname in SIDES and j % 2 == 0 and not band
                if band:
                    f.set(i, j, SILK.at(3.6))
                    g.set(i, j, SILK.at(5.0)[:3] + (90,))
                    continue
                if red:
                    f.set(i, j, SILK.at(4.4 if rib else 6.0))
                    g.set(i, j, hexc(LIGHT[1] if rib else LIGHT[2], 120 if rib else 170))
                else:
                    f.set(i, j, hexc(LIGHT[2]) if rib else hexc(LIGHT[3]))
                    g.set(i, j, hexc(LIGHT[2] if rib else LIGHT[3], 130 if rib else 190))
    for c in of("lbody_") + ["lamp_glass"]:
        glass(c)
    for c in of("pbody_", "hlan"):
        paper(c, ribs=not c.startswith("hlan"))
    # the pack: the lacquered chest with drawers and brass pulls, a red carpet with a paper fringe, paper scrolls
    # tied in red, black bamboo poles bound in brass, a red roof in rows of tiles with brass tips, the scale in brass
    P_.paint("box", LACQUER, 3.2, lambda f, i, j: (1.0 if f.name in SIDES and j % 4 == 0 else 0), rim=1.0)
    for fname in ("south", "east", "west"):
        f = P_.face("box", fname)
        for j in (2, 6, 10):
            for i in range(f.w // 4, f.w, max(4, f.w // 2)):
                f.set(i, j, BRASS.at(6.2))
    P_.paint("carpet", SILK, 3.4, lambda f, i, j: (1.2 if (i // 2 + j) % 3 == 0 else -0.6 if i % 6 == 0 else 0), rim=0.8)
    for fname in ("west", "east"):
        f = P_.face("carpet", fname)
        for i, j in f.each():
            f.set(i, j, PAPER.at(3.0 + (i + j) % 2))
    for c in of("scroll"):
        P_.paint(c, PAPER, 3.6, lambda f, i, j: (SILK.at(3.4) if j == f.h // 2 else -0.8 if j == 0 else 0), rim=0.8)
    # black bamboo, a pale node every so often, brass bindings only where a bar crosses a post
    for c in of("post_"):
        P_.paint(c, WOOD, 2.4, lambda f, i, j: (BRASS.at(4.6) if j in (1, 2, 33, 34, 55, 56) else BRASS.at(3.6) if j in (15, 16) else
                                                1.2 if j % 9 == 4 else 0), rim=1.0, noise=0.3)
    for c in of("bar"):
        P_.paint(c, WOOD, 2.4, lambda f, i, j: (BRASS.at(4.6) if f.name != "west" and f.name != "east" and i in (2, 3, f.w - 4, f.w - 3) else
                                                1.2 if i % 9 == 4 else 0), rim=1.0, noise=0.3)
    for c in ("roof0", "roof1", "ridge"):
        P_.paint(c, SILK, 3.2, lambda f, i, j: (1.0 if f.name == "top" and j % 3 == 0 else -1.0 if f.name in SIDES and j == f.h - 1 else 0), rim=1.2)
    for c in of("eave", "spire", "scale_", "pan_"):
        P_.paint(c, BRASS, 4.8, rim=1.4)
    # the moth cages: brass bars over the dark inside, a grey moth's wings pressed to them
    for c in of("cage0", "cage1", "door"):
        if c.endswith("_roof") or c.endswith("_ring") or c.endswith("_cord"):
            P_.paint(c, BRASS, 4.4, rim=1.2)
            continue
        for fname in ("top", "bottom") + SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                bar = i % 2 == 0 or j in (0, f.h - 1)
                wing = not bar and 1 <= j <= 3 and fname in SIDES and R._h(c + fname, i) % 2 == 0
                f.set(i, j, BRASS.at(5.0) if bar else MOTH.at(4.2 if wing else 0.6))
    return P_.skin, P_.glow


# ---- motion (SPEC §35.10) --------------------------------------------------------------------------------------------
POLE = {"l": (1, 0, 0.8), "r": (-1, 0, 0.8)}
LHAND = ((0, 1, -0.25), (1, 0, 0))  # the sickle hand at rest: pointing down, a little forward
STAFF_ARM = ("arm_r", "forearm_r", "hand_r", "staff")  # on the chest's time in every move: the staff lands where it's sent
ON_TIME = {b: 0 for b in STAFF_ARM}


def _add(p, bone, rot):
    v = p.get(bone, (0, 0, 0))
    if isinstance(v, dict):
        p[bone] = {"rot": tuple(a + b for a, b in zip(v["rot"], rot)), "pos": v.get("pos", (0, 0, 0))}
    else:
        p[bone] = tuple(a + b for a, b in zip(v, rot))


def _unit(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def _staff(r, p, staff):
    """Lays the staff: the right hand turned so its fist runs down the shaft, then the shaft slid through the grip.
    staff: None stands it upright on its foot (slid so the foot stays on the ground wherever the hand goes); "lift"
    upright and not slid (the foot leaves the ground as the hand rises); "up" upright, slid only to keep the foot out
    of the ground; (ydir, slide[, xdir]) the shaft's foot pointing along ydir (a model direction, y down), slid slide px
    toward the foot ("plant": as far as stands the foot on the ground), turned on its length by xdir (the crook curls
    to the hand's -z: xdir (-1, 0, 0) turns it back toward him)."""
    ydir, slide, xdir = (0, 1, 0), "plant", (1, 0, 0)
    if staff == "lift":
        slide = 0.0
    elif staff == "up":
        slide = "up"
    elif staff is not None:
        ydir, slide = _unit(staff[0]), staff[1]
        xdir = staff[2] if len(staff) > 2 else ((1, 0, 0) if abs(ydir[0]) < 0.8 else (0, 0, -1))
    fr = core.frames(r, core.as_sample(p))
    p["hand_r"] = ik.orient(r, fr, "hand_r", ydir, xdir)
    fr = core.frames(r, core.as_sample(p))
    h = ik.at(fr, "staff")[1]
    uy = max(0.1, ydir[1])
    if slide in ("plant", "up"):
        s = h / uy - r.staff_g  # the foot stands on the ground
        if slide == "up":
            s = min(s, 0.0)
    else:
        s = slide
    if abs(s) > 1e-3:
        d = R._mv(core._t(fr["hand_r"][0]), tuple(s * c for c in ydir))
        p["staff"] = {"rot": (0, 0, 0), "pos": (d[0], -d[1], d[2])}
    else:
        p["staff"] = {"rot": (0, 0, 0), "pos": (0, 0, 0)}
    return p


def _pose(r, hips=(0, 0, 0), at=(0, 0, 0), spine=(0, 0, 0), chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), hat=None, pack=None,
          right=None, staff=None, left=None, lhand=None, chain=None, legs=None, feet=("l", "r"), step=None, more=None):
    """A whole-body key. The body bends at the hips (hips: turn, at: x, up, z), spine, chest, neck and head; the hat
    and the pack frame turn on their own (hat may be {"rot", "pos"}: lifted off). The feet are planted where they stand
    (step: {side: (dx, dy, dz)} model px to put one elsewhere) unless feet is () and legs poses them. right: where the
    staff hand's wrist goes (x, up, z), None to carry it as the body does; staff: how the staff lies (see _staff). left:
    the sickle hand's wrist (x, up, z), or ("on", bone, point) a point on a bone; lhand: (ydir, xdir) for that hand.
    chain: 0 coiled to 1 straight, or the links' extra turns; more: any other bones."""
    p = {"pelvis": {"rot": hips, "pos": at}, "spine": spine, "chest": chest, "neck": neck, "head": head}
    if hat is not None:
        p["hat"] = hat
    if pack is not None:
        p["pack"] = pack
    if legs:
        p.update(legs)
    if more:
        p.update(more)
    if chain is not None:
        turns = [-chain * a for a in CHAIN_LOOP] if isinstance(chain, (int, float)) else chain
        for k, t in enumerate(turns):
            if t:
                _add(p, f"chain{k}", (0, 0, t) if isinstance(t, (int, float)) else t)
    if feet:
        p = core.plant(r, p, feet, at=step)
    fr = core.frames(r, core.as_sample(p))
    for side, tgt in (("l", left), ("r", right)):
        if tgt is None:
            continue
        q = core.point(fr, tgt[1], tgt[2]) if tgt[0] == "on" else ik.model(tgt)
        p.update(ik.limb(r, fr, "arm_" + side, "forearm_" + side, "hand_" + side, q, POLE[side]))
    if left is not None or lhand:
        fr = core.frames(r, core.as_sample(p))
        p["hand_l"] = ik.orient(r, fr, "hand_l", *(lhand or LHAND))
    return _staff(r, p, staff)


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


def _retrack(r, clip, fn):
    """Rewrites a baked clip tick by tick: fn(t, sample) -> {bone: rot or {"rot", "pos"}} (degrees, pos x up z, on
    top of rest); the bones it returns are keyed on every tick."""
    c = r.clips[clip]
    n = int(round(c.length / core.TICK))
    rot, pos = {}, {}
    for k in range(n + 1):
        t = min(c.length, k * core.TICK)
        for b, e in fn(t, core.sample(r, clip, t)).items():
            if isinstance(e, dict):
                rot.setdefault(b, []).append((t, tuple(e["rot"]), "cat"))
                pos.setdefault(b, []).append((t, tuple(e["pos"]), "cat"))
            else:
                rot.setdefault(b, []).append((t, tuple(e), "cat"))
    for b, keys in rot.items():
        c.tracks[(b, "rot")] = keys
    for b, keys in pos.items():
        c.tracks[(b, "pos")] = keys


def _as_key(r, s):
    """A sampled pose (radians, pos y down) back into key form (degrees, pos up)."""
    out = {}
    for b, v in s.items():
        rot = tuple(c / core.DEG for c in v.get("rot", (0, 0, 0)))
        q = v.get("pos", (0, 0, 0))
        out[b] = {"rot": rot, "pos": (q[0], -q[1], q[2])} if any(abs(c) > 1e-6 for c in q) else rot
    return out


def _keep_staff(r, clip, staff=None):
    """The staff stood on its foot through a baked clip (the hurts, the turn steps): the fist turned upright and the
    shaft slid through it every tick, the arm as the clip has it."""
    def fn(t, s):
        p = _staff(r, _as_key(r, s), staff)
        return {"hand_r": p["hand_r"], "staff": p["staff"]}
    _retrack(r, clip, fn)


def _staff_floor(r, clip):
    """Where a turning staff's foot would dip into the ground between keys, the shaft slides up through the fist by
    just that much, tick by tick (the foot drags along the ground instead)."""
    c = r.clips[clip]
    own = list(c.tracks.get(("staff", "pos"), []))
    if not any(core.lowest(r, core.sample(r, clip, min(c.length, k * core.TICK)), ["staff"]) > core.GROUND + 0.05
               for k in range(int(round(c.length / core.TICK)) + 1)):
        return

    def fn(t, s):
        pk = tuple(R._interp(own, t)) if own else (0.0, 0.0, 0.0)
        rk = s.get("staff", {}).get("rot", (0, 0, 0))
        d = core.lowest(r, s, ["staff"]) - core.GROUND
        if d > 0:
            fr = core.frames(r, s)
            M, Mh = fr["staff"][0], fr["hand_r"][0]
            y = R._mv(M, (0, 1, 0))  # the shaft's axis in the model, foot-ward (y down)
            k = d / max(0.3, y[1])
            up = tuple(-k * v for v in y)
            q = tuple(sum(Mh[j][i] * up[j] for j in range(3)) for i in range(3))  # into the hand's frame
            pk = (pk[0] + q[0], pk[1] - q[1], pk[2] + q[2])
        return {"staff": {"rot": tuple(v / core.DEG for v in rk), "pos": pk}}
    _retrack(r, clip, fn)


def _walk_staff(r, P_, plant=(0.25, 0.55), lift=3.0):
    """The walk with a walking staff: it plants as his left foot does and stays where it stood while he walks past it
    (the ground slides back under him a stride a cycle), then lifts and swings on ahead to plant again."""
    c = r.clips["walk"]
    L = c.length
    v = r.walk_stride * 16.0  # px of ground a cycle
    g = r.staff_g
    pa, pb = plant
    gx = GRIP_R[0]
    hand_z = lambda p: -2.5 - 4.0 * math.cos(2 * math.pi * (p - pa))
    fa, fb = -12.0, -12.0 + v * (pb - pa)
    th = lambda fz, hz: math.asin(max(-0.95, min(0.95, (fz - hz) / g)))  # the staff's lean, + its foot behind

    def fn(t, s):
        ph = (t / L) % 1.0
        q = (ph - pa) % 1.0
        hz = hand_z(ph)
        if q <= pb - pa:  # planted: the foot where it stood
            fz = fa + v * q
            a = th(fz, hz)
            gh, up = g * math.cos(a), 0.0
        else:  # swinging on ahead, its foot lifted
            u = (q - (pb - pa)) / (1 - (pb - pa))
            a0, a1 = th(fb, hand_z(pb)), th(fa, hand_z(pa))
            e = 0.5 - 0.5 * math.cos(math.pi * u)
            a = a0 + (a1 - a0) * e
            up = lift * math.sin(math.pi * u)
            gh = g * math.cos(a) + up
        ydir = (0, math.cos(a), math.sin(a))
        wrist = (gx, gh + 2.5 * math.cos(a), hz - 2.5 * math.sin(a))
        p = _as_key(r, s)
        fr = core.frames(r, s)
        p.update(ik.limb(r, fr, "arm_r", "forearm_r", "hand_r", ik.model(wrist), POLE["r"]))
        p = _staff(r, p, (ydir, 0.0))
        return {b: p[b] for b in STAFF_ARM}
    _retrack(r, "walk", fn)


def _run_staff(r):
    """At a run he carries the staff off the ground, its head tipped forward, pumping with his stride."""
    c = r.clips["run"]
    L = c.length

    def fn(t, s):
        ph = (t / L) % 1.0
        sn = math.sin(2 * math.pi * ph)
        a = math.radians(-14 + 6 * sn)
        wrist = (GRIP_R[0] + 1, 36 + 2 * abs(sn), -6 - 4 * sn)
        p = _as_key(r, s)
        p.update(ik.limb(r, core.frames(r, s), "arm_r", "forearm_r", "hand_r", ik.model(wrist), POLE["r"]))
        p = _staff(r, p, ((0, math.cos(a), math.sin(a)), 0.0))
        return {b: p[b] for b in STAFF_ARM}
    _retrack(r, "run", fn)


def _flame(r, mv, keys):
    """The lamp's light in a move's clip: keys [(stage, f, size)], 1 lit, ~0 out."""
    W, A = mv.seconds("windup"), mv.seconds("active")
    at = {"windup": 0.0, "active": W, "recover": W + A}
    c = r.clips[core.clip_name(mv)]
    ks = [(0.0, 1, 1, 1)] + [(at[st] + f * mv.seconds(st), v, v, v) for st, f, v in keys] + [(c.length, 1, 1, 1)]
    tmp = R.Clip("flame", c.length, False)
    tmp.scale("flame", ks, "linear")
    c.tracks[("flame", "scale")] = tmp.tracks[("flame", "scale")]


def _spin(r, mv, turns, start, peak, stop):
    """Whole turns on his heel on the root after staging (a whole turn is no turn: it hands back as it was): still to
    start, fastest at peak, slowing to stop (seconds into the clip)."""
    c = r.clips[core.clip_name(mv)]
    n = int(round(c.length / core.TICK))
    ts = [min(c.length, k * core.TICK) for k in range(n + 1)]
    # a smooth bell of speed (a sine squared, its top at peak): no corner anywhere in how fast it turns
    bell = lambda t: (math.sin(0.5 * math.pi * (t - start) / (peak - start)) if t < peak else
                      math.cos(0.5 * math.pi * (t - peak) / (stop - peak))) ** 2
    speed = [0.0 if t <= start or t >= stop else bell(t) for t in ts]
    tot, acc = sum(speed) or 1.0, 0.0
    old = c.tracks.get(("pelvis", "rot"))
    keys = []
    for t, v in zip(ts, speed):
        acc += v
        base = list(R._interp(old, t)) if old else [0.0, 0.0, 0.0]
        base[1] += 360.0 * turns * acc / tot
        keys.append((t, tuple(base), "cat"))
    c.tracks[("pelvis", "rot")] = keys


def _settle_staff(r, mv, f0, ease=0.15):
    """From f0 of the recovery on, the staff stood back on its foot every tick (eased in over ease seconds), so the
    recovery's settle (which scales the end pose and so would lift or sink a planted staff) ends on the ground."""
    W, A, Rr = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    t0 = W + A + f0 * Rr
    c = r.clips[core.clip_name(mv)]
    own = {b: (list(c.tracks.get((b, "rot"), [])), list(c.tracks.get((b, "pos"), []))) for b in ("hand_r", "staff")}

    def fn(t, s):
        out = {}
        for b in ("hand_r", "staff"):
            rk, pk = own[b]
            rr = tuple(R._interp(rk, t)) if rk else (0.0, 0.0, 0.0)
            pp = tuple(R._interp(pk, t)) if pk else (0.0, 0.0, 0.0)
            out[b] = {"rot": rr, "pos": pp}
        if t < t0:
            return out
        w = min(1.0, (t - t0) / ease)
        w = w * w * (3 - 2 * w)
        p = _staff(r, _as_key(r, s), None)
        for b in ("hand_r", "staff"):
            v = p[b]
            vr = v["rot"] if isinstance(v, dict) else v
            vp = v["pos"] if isinstance(v, dict) else (0, 0, 0)
            o = out[b]
            out[b] = {"rot": tuple(a + (q - a) * w for a, q in zip(o["rot"], vr)), "pos": tuple(a + (q - a) * w for a, q in zip(o["pos"], vp))}
        return out
    _retrack(r, core.clip_name(mv), fn)


def idle(r):
    """Standing: easy on his staff, breathing, a curious tilt of the head at you, a small nod, the left hand turning
    the sickle a little; now and then he lifts the staff and taps it down (the lantern swings on its hook)."""
    k0 = _pose(r)
    tilt = _pose(r, hips=(0, 0, 1.2), at=(0.4, -0.3, 0), chest=(-1.6, 3, 0.8), neck=(0, 4, 0), head=(2, 10, 9), left=(10.5, 30.5, -5), chain=0.08)
    ease = _pose(r, hips=(0, 0, 0.4), at=(0.1, 0, 0), chest=(-0.4, 1, 0), head=(1, 4, 3), left=(10, 29.5, -4.5))
    nod = _pose(r, hips=(0, 0, -1.2), at=(-0.4, -0.3, 0), chest=(-1.4, -3, -0.8), head=(7, -6, -3), left=(9.5, 28.5, -4), chain=-0.05)
    lifted = _pose(r, hips=(0, 0, -0.6), at=(-0.2, -0.1, 0), chest=(-0.6, -1.5, 0), head=(3, -3, -1),
                   right=(GRIP_R[0], GRIP_R[1] + 3, GRIP_R[2] - 0.5), staff="lift")
    tap = _pose(r, hips=(0, 0, -0.3), chest=(0.4, 0, 0), head=(1.5, -1, 0))
    keyed(r, "idle", 4.8, [(0, k0), (1.2, tilt), (2.2, ease), (3.2, nod), (3.85, lifted), (4.05, tap), (4.8, k0)], loop=True)


def reactions(r):
    """The kneel when his stance breaks: down on his right knee, leaning his weight on the staff with the hand high on
    it, his left forearm across his left knee, the head hung. The rise pushes up off the staff. The death: he sits
    down against his pack, the frame tilting back to take him, the hat tipping over his face; then the staff falls."""
    kl = {"thigh_l": (-80, 0, 0), "shin_l": (82, 0, 0), "foot_l": (-2, 0, 0), "thigh_r": (8, 0, 0), "shin_r": (90, 0, 0), "foot_r": (60, 0, 0)}
    kneel = _pose(r, hips=(8, 0, 0), at=(0, -12.8, 1.8), legs=kl, feet=(), spine=(10, 0, 0), chest=(12, -6, 3), neck=(6, 0, 0), head=(20, 4, 0),
                  right=(-11, 30, -9), left=("on", "shin_l", (0.5, -2, -3)), lhand=((-0.6, 0.6, -0.4), (0, 0, -1)), chain=0.2)
    bow = _pose(r, hips=(10, 0, 0), at=(0, -6, 0.5), spine=(8, 0, 0), chest=(14, -4, 2), head=(18, 2, 0), right=(-11, 30, -8), left=(10, 24, -8))
    crouch = _pose(r, hips=(6, 0, 0), at=(0, -4, 0), chest=(6, 0, 0), head=(-6, 0, 0), right=(-12, 32, -6), left=(10, 27, -5))
    keyed(r, "kneel", 0.55, [(0, _pose(r)), (0.2, bow), (0.55, kneel)])
    keyed(r, "rise", 0.75, [(0, kneel), (0.38, crouch), (0.75, _pose(r))])
    stag = _pose(r, at=(0, 0, 1.5), chest=(-10, 6, 0), head=(-16, 0, 0), hat=(-6, 0, 0), left=(14, 36, 8), lhand=((0.5, 0.6, 0.4), (1, 0, 0)), chain=0.4)
    sit = {"thigh_l": (-84, 0, -6), "shin_l": (24, 0, 0), "foot_l": (-20, 0, 0), "thigh_r": (-80, 0, 8), "shin_r": (34, 0, 0), "foot_r": (-24, 0, 0)}
    buckle = _pose(r, hips=(-4, 0, 0), at=(0, -11, 3), chest=(6, 0, 0), head=(14, 0, 0), right=(-12.5, 24, -5), left=(12, 22, -2), chain=0.3)
    sat = _pose(r, hips=(-10, 0, 0), at=(0, -31, 5), legs=sit, feet=(), spine=(-8, 0, 0), chest=(-10, 0, 2), neck=(10, 0, 0), head=(26, -6, 4),
                hat=(26, 0, 0), pack=(-20, 0, 0), right=(-13, 18, -6), left=(12, 4, -6), lhand=((0.6, 0.2, -0.75), (0, 1, 0)), chain=0.5)
    slump = _pose(r, hips=(-11, 0, 0), at=(0, -31, 5), legs=sit, feet=(), spine=(-9, 0, 0), chest=(-12, -4, 4), neck=(12, 0, 0), head=(34, -10, 6),
                  hat=(30, 0, 0), pack=(-22, 0, 0), right=(-13.5, 14, -6), left=(12.5, 3.5, -5), lhand=((0.6, 0.2, -0.75), (0, 1, 0)), chain=0.55)
    tip = dict(slump)
    tip = _pose(r, hips=(-11, 0, 0), at=(0, -31, 5), legs=sit, feet=(), spine=(-9, 0, 0), chest=(-12, -4, 4), neck=(12, 0, 0), head=(34, -10, 6),
                hat=(30, 0, 0), pack=(-22, 0, 0), right=(-15, 9, -6), staff=((0.75, 0.66, 0), -10), left=(12.5, 3.5, -5),
                lhand=((0.6, 0.2, -0.75), (0, 1, 0)), chain=0.55)
    down = _pose(r, hips=(-11, 0, 0), at=(0, -31, 5), legs=sit, feet=(), spine=(-9, 0, 0), chest=(-12, -4, 4), neck=(12, 0, 0), head=(34, -10, 6),
                 hat=(30, 0, 0), pack=(-22, 0, 0), right=(-15, 3, -6), staff=((0.99, 0.02, 0.1), -20), left=(12.5, 3.5, -5),
                 lhand=((0.6, 0.2, -0.75), (0, 1, 0)), chain=0.55)
    keyed(r, "death", 3.4, [(0, _pose(r)), (0.3, stag), (0.9, buckle), (1.5, sat), (2.4, slump), (2.85, tip), (3.1, down), (3.4, down)])


def lash(r):
    """The Chain Lash (LIGHT): he turns away, the sickle hand cocked back over his left shoulder with the chain trailing;
    then unwinds and flings it, the chain running out straight off the pommel and the weight striking 3 blocks out."""
    pre = _pose(r, hips=(0, -4, 0), at=(0, -0.5, 0), chest=(2, -6, 0), head=(0, 6, 0), left=(11, 26, -7), chain=0.1)
    wind = _pose(r, hips=(-2, 14, 0), at=(0, -1, 1.5), spine=(-2, 8, 0), chest=(-6, 22, -4), head=(-4, -26, 4), left=(16, 60, 12),
                 lhand=((0.2, 0.6, 0.8), (1, 0, 0)), chain=0.35)
    hit = _pose(r, hips=(2, -16, 0), at=(0, -2, -2.5), spine=(2, -8, 0), chest=(5, -20, 4), head=(-6, 10, -2), left=(6, 46, -27),
                lhand=((-0.1, 0.25, -1), (1, 0, 0)), chain=1.0)
    after = _pose(r, hips=(2, -18, 0), at=(0, -2, -2.5), spine=(2, -9, 0), chest=(6, -22, 4), head=(-4, 10, -2), left=(5, 41, -26),
                  lhand=((-0.1, 0.45, -0.9), (1, 0, 0)), chain=0.95)
    yank = _pose(r, hips=(1, -8, 0), at=(0, -1, -1), chest=(3, -10, 0), head=(-2, 5, 0), left=(10, 34, -14), lhand=((0, 0.6, -0.8), (1, 0, 0)), chain=0.7)
    lag = dict(ON_TIME, **{f"chain{k}": 0.006 * k for k in range(1, 7)})
    back = _mix(yank, _pose(r), 0.65)
    return Move("lash", "LIGHT", 12, 4, 16, title="Chain Lash", track=10.0, rng=(0, 3.4), kinds=("near",), damage=0.9, knock=0.8, plant=(),
                hit=[("chain6", WEIGHT, 2.5), ("chain3", (0, 4, 0), 2.0)], lag=lag, through=_mix(after, yank, 0.2),
                stages={"windup": [(0.3, pre), (1.0, wind)], "active": [(0.5, hit), (1.0, after)], "recover": [(0.5, yank), (0.72, back)]})


def crook(r):
    """The crook's sweep after the lash (LIGHT): the staff swung up off the ground and loaded across to his left, then
    ripped flat round in front of him at a man's waist, the crook and its lantern leading."""
    load = _pose(r, hips=(2, -18, 0), at=(0, -1.5, 1), spine=(2, -8, 0), chest=(4, -24, 0), head=(0, 20, 0), right=(2, 44, -10),
                 staff=((-0.95, 0.2, -0.25), 0), left=(12, 32, -2), chain=0.2)
    rip = _pose(r, hips=(2, 18, 0), at=(0, -2.5, -1), spine=(2, 10, 0), chest=(8, 26, 0), head=(2, -10, 0), right=(-16, 40, -15),
                staff=((0.15, -0.42, 0.9), 0), left=(12, 30, 2))
    after = _pose(r, hips=(2, 24, 0), at=(0, -2.5, -1), spine=(2, 12, 0), chest=(8, 32, 0), head=(2, -14, 0), right=(-22, 38, -4),
                  staff=((0.9, -0.3, 0.3), 0), left=(12, 30, 3))
    rise = _pose(r, hips=(0, 8, 0), at=(0, -1, 0), chest=(2, 10, 0), head=(0, -4, 0), right=(-15, 36, -5), staff="up")
    return Move("crook", "LIGHT", 9, 4, 16, title="Crook Sweep", track=8.0, rng=(0, 4.4), kinds=("combo",), damage=1.0, knock=1.0, plant=(),
                hit=[("staff", (0, r.staff_top - 3, -5), 4.0), ("staff", (0, r.staff_top + 12, 0), 2.5)], lag=ON_TIME,
                through=_on(r, after, 0.08), stages={"windup": [(0.34, _mix(_pose(r), load, 0.45)), (1.0, load)],
                                                     "active": [(0.5, rip), (1.0, after)], "recover": [(0.5, rise)]})


def pack(r):
    """For whoever is behind him (LIGHT): he looks over his right shoulder, winds the other way, then twists right
    round so the whole frame of his shop clubs across behind him, every lantern flying out."""
    look = _pose(r, hips=(0, -8, 0), chest=(0, -12, 0), head=(0, 46, 0), staff="up")
    wind = _pose(r, hips=(2, -16, 0), at=(0, -2, 0), spine=(0, -14, 0), chest=(-4, -26, -4), head=(0, 58, 0), pack=(0, -6, 0), left=(14, 30, 6),
                 staff="up")
    hit = _pose(r, hips=(2, 20, 0), at=(0, -2.5, 0), spine=(2, 30, 0), chest=(6, 54, 6), head=(0, -30, 0), pack=(0, 12, 4), left=(4, 34, -10),
                staff="up")
    after = _pose(r, hips=(2, 22, 0), at=(0, -2.5, 0), spine=(2, 32, 0), chest=(6, 60, 6), head=(0, -34, 0), pack=(0, 14, 4), left=(4, 34, -10),
                  staff="up")
    return Move("pack", "LIGHT", 12, 4, 16, track=0.0, rng=(0, 3.2), kinds=("behind",), damage=1.0, knock=1.2, plant=(),
                hit=[("pack", (0, -40, 9), 6.0), ("pack", (13, -14, 9), 4.0)], lag=ON_TIME, through=_on(r, after, 0.06),
                stages={"windup": [(0.3, look), (1.0, wind)], "active": [(0.5, hit), (1.0, after)], "recover": _back(r, after)})


def bolts(r):
    """Lantern Bolts: he lifts the staff off the ground and pumps its lantern at you, three times, a bolt flying off
    the lantern each time."""
    up = _pose(r, hips=(-2, 6, 0), at=(0, 0.5, 0.5), chest=(-6, 8, 0), head=(-6, -4, 0), right=(-11, 44, -6), staff=((0, 0.96, 0.28), 0),
               left=(10, 32, -6))
    pump = lambda k: _pose(r, hips=(2, -4, 0), at=(0, -0.5, -1.5), chest=(4, -6 - k, 0), head=(2, 4, 0), right=(-10, 49, -16 - k),
                           staff=((0, 0.78, 0.63), 0), left=(11, 30, -2))
    return Move("bolts", "LIGHT", 12, 14, 14, title="Lantern Bolts", track=9.0, ability="Lantern Bolts", origin=BOLTS_FROM, rng=(4, 18),
                kinds=("ranged",), damage=0, knock=0, plant=(), lag=ON_TIME, through=_mix(pump(2), up, 0.5),
                stages={"windup": [(0.3, _pose(r, chest=(-2, 2, 0), head=(-2, -2, 0), right=(GRIP_R[0] + 0.5, GRIP_R[1] + 6, GRIP_R[2] - 1.5),
                                                staff="lift", left=(10, 31, -5))), (1.0, up)],
                        "active": [(0.15, pump(0)), (0.33, up), (0.5, pump(1)), (0.66, up), (0.83, pump(2)), (1.0, _mix(pump(2), up, 0.3))],
                        "recover": _back(r, _mix(pump(2), up, 0.5))})


def step(r):
    """The Market Step: he takes his hat by the brim and lifts it off in a bow, his right foot drawn back; spins once
    on his heel with it in his hand, and is somewhere else, setting it back on with a flourish."""
    grip = ("on", "hat", (4, 0, -9))
    up = ((0, -0.7, -0.7), (1, 0, 0))
    reach = _pose(r, chest=(-2, 0, 0), head=(-4, 0, 0), left=grip, lhand=up)
    bow = _pose(r, hips=(12, 0, 0), at=(0, -2, 1), spine=(10, 0, 0), chest=(8, 0, 0), head=(10, 0, 0), hat={"rot": (-18, 0, 0), "pos": (0, 2, -1)},
                left=grip, lhand=up, step={"r": (0, 0, 5)})
    spin = _pose(r, at=(0, 1, 0), chest=(-4, 0, 0), head=(-4, 0, 0), hat={"rot": (-6, 0, 0), "pos": (0, 1, 0)}, left=grip, lhand=up,
                 right=(GRIP_R[0], GRIP_R[1] + 2, GRIP_R[2]), staff="lift")
    flourish = _pose(r, hips=(0, -6, 0), chest=(-6, -8, 0), head=(-4, 6, 0), left=(22, 42, -8), lhand=((1, 0.2, -0.3), (0, 1, 0)), chain=0.3)
    return Move("step", "NONE", 12, 10, 14, title="Market Step", track=6.0, ability="Market Step", rng=(4, 16), kinds=("gap",), damage=0, knock=0,
                plant=(), lag=ON_TIME, stages={"windup": [(0.4, reach), (1.0, bow)], "active": [(0.5, spin), (1.0, flourish)],
                                               "recover": _back(r, flourish)})


def moths(r):
    """Release the Moths: he reaches back over his left shoulder to the cage on his frame and opens it, then shrugs
    the whole pack twice so every lantern on it swings, and the moths pour out."""
    reach = _pose(r, chest=(-4, -14, 0), head=(0, -40, 0), left=("on", "cage0", (0, -3, 3)), lhand=((0, -0.5, 0.85), (1, 0, 0)))
    opn = _pose(r, chest=(-6, -16, 0), head=(-4, -46, 0), left=("on", "cage0", (3, -3, 5)), lhand=((0.6, -0.4, 0.7), (0, 1, 0)),
                more={"door0": (0, -110, 0)})
    shrug = lambda k: _pose(r, at=(0, 1.5 - 1.5 * k, 0), chest=(-8 + 4 * k, -6, 0), head=(-6, -10, 0), pack=(-10 + 14 * k, 0, 0),
                            left=(14, 36 - 4 * k, 4), more={"door0": (0, -110, 0)}, chain=0.3)
    return Move("moths", "NONE", 14, 18, 14, title="Release the Moths", track=0.0, ability="Release the Moths", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, plant=(), lag=ON_TIME, stages={"windup": [(0.45, reach), (1.0, opn)],
                                                                 "active": [(0.3, shrug(0)), (0.55, shrug(1)), (0.8, shrug(0)), (1.0, shrug(0.6))],
                                                                 "recover": _back(r, shrug(0.6))})


def paper(r):
    """Paper Lanterns: he draws in, then rises with his arms flung wide, the staff out at his right and the chain
    flying from his left, and turns once right round on his heel; the ring of paper lanterns flies out off him."""
    draw = _pose(r, hips=(4, 0, 0), at=(0, -2, 0), chest=(6, 12, 0), head=(4, -6, 0), left=(5, 40, -8), right=(-10, 34, -6), staff="up", chain=0.2)
    wide = lambda k: _pose(r, at=(0, 1 + 0.8 * k, 0), chest=(-8 - 4 * k, 6 - 10 * k, 0), head=(-8 - 8 * k, -6 + 8 * k, 0), left=(25, 46 + 8 * k, 2 - 6 * k),
                           lhand=((1, 0.15 - 0.3 * k, 0), (0, 1, 0)), right=(-24, 44 + 8 * k, 4 - 8 * k), staff=((0.3 + 0.2 * k, 0.95, 0), 0), chain=0.8)
    return Move("paper", "HEAVY", 16, 12, 18, title="Paper Lanterns", hold=6, track=0.0, ability="Paper Lanterns", rng=(0, 8), kinds=("area",),
                damage=0, knock=0, plant=(), lag=ON_TIME, through=wide(0.1),
                stages={"windup": [(0.3, draw), (1.0, wide(0))], "active": [(0.45, wide(1)), (1.0, wide(0.3))], "recover": _back(r, wide(0.1))})


def lights_out(r):
    """Lights Out (the signature): he lifts the staff and plants it a stride ahead, leaning back to him with its crook
    turned toward his face, his hand walked up the shaft; he cups the lantern in his left hand, draws breath, and
    blows it out. It stays dark till he has the staff back at his side."""
    lift = _pose(r, chest=(-4, 0, 0), head=(-6, 0, 0), right=(-10, 40, -8), staff="lift")
    lean = ((0, 0.84, -0.545), "plant", (-1, 0, 0))  # 33 degrees: the lantern swings back to his mouth
    plant_ = _pose(r, hips=(2, 0, 0), at=(0, -1, -0.5), chest=(2, 0, 0), head=(-10, 0, 0), right=(-6, 48, -26), staff=lean)
    cup = _pose(r, hips=(4, 0, 0), at=(0, -1.5, -1.5), chest=(5, 4, 0), neck=(3, 0, 0), head=(6, 6, 0), hat=(-8, 0, 0), right=(-6, 48, -26), staff=lean,
                left=("on", "lamp", (3, 6, 1)), lhand=((-0.4, 0.1, -0.9), (0, -1, 0)))
    blow = _pose(r, hips=(5, 0, 0), at=(0, -2, -2), chest=(7, 4, 0), neck=(5, 0, 0), head=(8, 6, 0), hat=(-10, 0, 0), right=(-6, 48, -26), staff=lean,
                 left=("on", "lamp", (3, 6, 1)), lhand=((-0.4, 0.1, -0.9), (0, -1, 0)))
    after = _pose(r, hips=(2, 0, 0), at=(0, -1, -1), chest=(4, 0, 0), head=(4, 0, 0), right=(-6, 48, -26), staff=lean, left=(12, 34, -8))
    return Move("lights_out", "PERILOUS", 34, 12, 26, title="Lights Out", track=0.0, ability="Lights Out", rng=(0, 99), kinds=("area",),
                signature=True, damage=0, knock=0, plant=(), lag=ON_TIME, through=_on(r, after, 0.04),
                stages={"windup": [(0.22, lift), (0.5, plant_), (0.78, cup), (1.0, _mix(cup, blow, -0.4))], "active": [(0.35, blow), (1.0, after)],
                        "recover": [(0.45, _pose(r, hips=(1, 0, 0), chest=(2, 0, 0), head=(2, 0, 0), right=(-9, 42, -9), staff="lift", left=(11, 31, -6))),
                                    (0.7, _pose(r, chest=(0.5, 0, 0), right=(GRIP_R[0] + 0.5, GRIP_R[1] + 3, GRIP_R[2] - 1), staff="lift"))]})


def wake(r):
    """His intro, "A duel, then. Lanterns down.": he lifts the staff and sets it down hard, bows with his left hand on
    his heart, and straightening lets the chain fall from its loop to hang from the sickle, ready."""
    lift = _pose(r, chest=(-4, 0, 0), head=(-6, 0, 0), right=(GRIP_R[0], GRIP_R[1] + 9, GRIP_R[2] - 1), staff="lift")
    thump = _pose(r, at=(0, -1.5, 0), chest=(3, 0, 0), head=(2, 0, 0))
    bow = _pose(r, hips=(8, 0, 0), at=(0, -2, 1), spine=(8, 0, 0), chest=(10, 0, 0), head=(10, 0, 0), hat=(-12, 0, 0), left=("on", "chest", (2.5, -9, -5)),
                lhand=((-0.7, -0.6, 0), (0, 0, -1)), right=(-12.5, 28, -6))
    let = _pose(r, chest=(-2, 0, 0), head=(-6, 0, 0), left=(13, 34, -8), lhand=((0, 1, 0), (1, 0, 0)), chain=0.85)
    ready = _pose(r, hips=(0, -6, 0), chest=(-2, -8, 0), head=(-2, 8, 0), left=(16, 34, -6), lhand=((0.2, 1, 0), (1, 0, 0)), chain=0.8)
    return Move("wake", "NONE", 16, 28, 16, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(), lag=ON_TIME,
                stages={"windup": [(0.5, _mix(_pose(r), lift, 0.5)), (1.0, lift)], "active": [(0.15, thump), (0.5, bow), (0.8, let), (1.0, ready)],
                        "recover": _back(r, ready)})


BOLTS_FROM = (-0.65, 4.85, 2.8)  # where the bolts leave the lantern at a pump (blocks: x, up, forward)


def clips(r):
    plans.biped_locomotion(r, P, weight=0.8, temper="regal")
    plans.biped_reactions(r, weight=0.8)
    idle(r)
    reactions(r)
    _walk_staff(r, P)
    _run_staff(r)
    for name in ("hurt", "hurt_l", "hurt_r", "turn_l", "turn_r"):
        _keep_staff(r, name)
    mv = [lash(r), crook(r), pack(r), bolts(r), step(r), moths(r), paper(r), lights_out(r), wake(r)]
    by = {m.id: m for m in mv}
    for m in mv:
        stage_clips(r, m)
    for m in mv:  # and every move hands back with the staff on its foot
        _settle_staff(r, m, 0.8 if m.id in ("lights_out", "step", "bolts", "paper") else 0.7)
    # the turns on his heel, and the lantern blown out and lit again
    W, A, Rr = (by["step"].seconds(s) for s in ("windup", "active", "recover"))
    _spin(r, by["step"], 1, W - 0.05, W + 0.45 * A, W + A + 0.1 * Rr)
    W, A, Rr = (by["paper"].seconds(s) for s in ("windup", "active", "recover"))
    _spin(r, by["paper"], 1, W - 0.1, W + 0.45 * A, W + A + 0.55 * Rr)
    _flame(r, by["lights_out"], [("active", 0.25, 1.0), ("active", 0.5, 0.05), ("recover", 0.75, 0.05), ("recover", 1.0, 1.0)])
    for n in r.clips:
        if n != "death":
            _staff_floor(r, n)
    # the staff, the sickle and the chain stay out of the ground between the keys (the wrist turns first); the
    # skirts lift back off the floor where they would meet it, the death included
    core.ground_pass(r, [(["sickle", "chain6"], [("hand_l", 0, 60), ("forearm_l", 0, 40)])], clips=[n for n in r.clips if n != "death"])
    core.ground_pass(r, [(["skirt_l"], [("skirt_l", 0, 80)]), (["skirt_r"], [("skirt_r", 0, 80)])], clips=list(r.clips), skip=())
    # broken and down on a knee (and back up, and dead), each foot tips at the ankle to lie on the floor, then the knee
    feet = [(["foot_" + s], [("foot_" + s, 0, 50), ("shin_" + s, 0, 30)]) for s in "lr"]
    core.ground_pass(r, feet, clips=["kneel", "rise"])
    core.ground_pass(r, feet, clips=["death"], skip=())
    return mv


FIGHT = {
    "intro": {"pose": "idle", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["crook"]]},
            {"move": "bolts", "weight": 3},
            {"move": "step", "weight": 2, "when": "far", "then": [["lash", "crook"]]},
            {"move": "pack", "weight": 3, "when": "behind"},
        ]},
        # prices go up with the second phase: the moths and the paper lanterns, as in GateBestiary
        {"transition": "moths", "pool": [
            {"move": "lash", "weight": 3, "when": "near", "then": [["crook"]]},
            {"move": "bolts", "weight": 2},
            {"move": "paper", "weight": 2},
            {"move": "moths", "weight": 1},
            {"move": "step", "weight": 2, "when": "far", "then": [["lash", "crook"]]},
            {"move": "pack", "weight": 3, "when": "behind"},
        ]},
        {"transition": "lights_out", "pool": [
            {"move": "lights_out", "weight": 1},
            {"move": "step", "weight": 3, "then": [["lash", "crook"], ["bolts"]]},
            {"move": "bolts", "weight": 3},
            {"move": "lash", "weight": 3, "when": "near", "then": [["crook"]]},
            {"move": "paper", "weight": 2},
            {"move": "pack", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}

# His sounds (SPEC §35.8): brass, chain, paper and the hiss of a flame; he walks, so steps. Chosen by ear on paper,
# not yet heard in game.
SOUNDS = {
    "tell_light": [("block.chain.step", 1.0, 1.2), ("block.lantern.hit", 1.0, 1.0)],
    "tell_heavy": [("block.lantern.place", 1.4, 0.7), ("item.armor.equip_leather", 1.2, 0.8)],
    "tell_perilous": [("block.candle.extinguish", 2.0, 0.6), ("block.bell.resonate", 1.2, 1.4)],
    "swing_light": [("entity.player.attack.sweep", 1.0, 1.3), ("block.chain.break", 1.0, 1.4)],
    "swing_heavy": [("entity.player.attack.sweep", 1.4, 0.8), ("block.lantern.break", 1.2, 0.8)],
    "cast": [("item.firecharge.use", 1.2, 1.2), ("block.lantern.hit", 1.0, 1.4)],
    "impact": [("block.chain.hit", 1.4, 0.8), ("block.lantern.fall", 1.2, 0.9)],
    "roar": [("block.candle.extinguish", 2.4, 0.5), ("entity.illusioner.cast_spell", 1.6, 0.7)],
    "step": [("block.wood.step", 0.6, 1.1), ("block.chain.step", 0.4, 1.3)],
}
MOVE_SOUNDS = {"bolts": {"swing": "cast"}, "moths": {"swing": "cast", "impact": ""}, "paper": {"swing": "cast"},
               "lights_out": {"swing": "roar", "impact": ""}, "step": {"swing": "cast", "impact": ""}}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.6, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where his parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("hand_l", (0, 0, 0)), ("hand_r", (0, 0, 0)), ("staff", (0, 0, 0)), ("lamp", LAMP), ("head", (0, -7, 0)),
                 ("hat", (0, -9, 0)), ("pack", (0, -52, 8)), ("chain6", WEIGHT), ("sickle", SICKLE_TIP), ("shaft", None)):
        if p is None:
            print("staff foot", [round(v, 2) for v in core.body(core.point(fr, "staff", (0, ik.at(fr, "staff")[1], 0)))])
            continue
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))


if __name__ == "__main__":
    probe()
