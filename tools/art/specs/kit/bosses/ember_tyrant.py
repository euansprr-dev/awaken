"""The Ember Tyrant (SPEC §34, docs/bosses/PLAN.md §5): a horned brute at true size, about six and a half blocks tall.

A Brute: a hunched basalt body with a molten crystal core in its chest, oversized forearms veined with magma under iron
bracers, iron plates on its shoulders, curling obsidian horns, and a great axe in its right hand. Its light story is the
heat: the core, the eyes, the cracks and the axe's edge all burn the same orange.

Moves: Magma Fist (the overhead two-hand hammer, HEAVY, held on a third of its swings), a left backhand (LIGHT), an axe
chop string (two LIGHTs), a shoulder charge (GRAB), a stomp for whoever is behind it, Eruption (it rears back and slams
both fists, PERILOUS, the signature), Stoke the Imps (its second phase: a roar that calls the imps) and Meltdown (its
third: the core goes white and the ground round it melts).
"""
from kit import moves as M
from kit import plans
from kit.core import stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, p_stone, combine, p_strokes, SIDES

ID = "ember_tyrant"
SCALE = 3.0  # the hitbox (the def's scale on vanilla's piglin brute: 1.8 x 5.85 blocks); the body is modelled at true size

P = plans.Biped(leg=40, thigh=20, foot=(14, 5, 18), pelvis=(28, 12, 20), spine=(26, 12, 18), chest=(46, 26, 28), neck=(12, 6, 12),
                head=(18, 16, 18), arm=(14, 24, 14), forearm=(20, 26, 20), hand=(16, 11, 16), stance=9, hunch=14, leg_w=(14, 12))

AXE_TIP = (0, 6, -36)  # the blade's middle, in the axe bone's space


def body():
    r = plans.biped(ID, P, tex=(512, 512))
    b = r.by_name
    # head: a heavy brow, an underslung jaw with tusks, and horns that curl out, up and forward
    b["head"].cube("brow", -9.5, -12, -12, 19, 4, 4)
    jaw = b["head"].bone("jaw", (0, -2, -3))
    jaw.cube("jaw", -7, 0, -9, 14, 5, 11)
    jaw.cube("tusk_l", 4, -4, -9, 2, 4, 2)
    jaw.cube("tusk_r", -6, -4, -9, 2, 4, 2, mirror=True)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        h1 = b["head"].bone("horn1_" + nm, (side * 8, -12, -2), (0, side * -10, side * -28))
        h1.cube("horn1_" + nm, 0 if side > 0 else -9, -3, -3, 9, 6, 6, mirror=mir)
        h2 = h1.bone("horn2_" + nm, (side * 8, 0, 0), (-10, side * -20, side * -50))
        h2.cube("horn2_" + nm, 0 if side > 0 else -8, -2.5, -2.5, 8, 5, 5, mirror=mir)
        h3 = h2.bone("horn3_" + nm, (side * 7, 0, 0), (-30, side * -30, side * -40))
        h3.cube("horn3_" + nm, 0 if side > 0 else -7, -1.5, -1.5, 7, 3, 3, mirror=mir)
        # shoulder plates ride the upper arm, so they lift with it; each carries a spike
        pl = b["arm_" + nm].bone("plate_" + nm, (0, 0, 0))
        pl.cube("plate_" + nm, -10, -7, -10, 20, 9, 20, mirror=mir)
        pl.cube("spike_" + nm, side * 3 - 2, -13, -2, 4, 6, 4, mirror=mir)
        # iron bracers on the oversized forearms (the magma veins show above and below them)
        b["forearm_" + nm].cube("bracer_" + nm, -11, 6, -11, 22, 11, 22, mirror=mir)
        b["hand_" + nm].cube("knuckle_" + nm, -7, 7, -9, 14, 4, 3, mirror=mir)
    # the chest: the molten core, a ridge of spines down the upper back
    core = b["chest"].bone("core", (0, -14, -15))
    core.cube("core", -6, -6, -2, 12, 12, 3)
    for k, (y, hgt) in enumerate(((-26, 8), (-18, 7), (-10, 6))):
        b["chest"].cube(f"dorsal{k}", -2.5, y, 12, 5, hgt, 5)
    # belt and the hanging tasset at the front
    b["pelvis"].cube("belt", -15, -2, -11, 30, 5, 22)
    ts = b["pelvis"].bone("tasset", (0, 3, -11))
    ts.cube("tasset", -8, 0, -1, 16, 13, 2)
    # the axe: a long haft through the right fist, the blade hanging at the front end
    axe = b["hand_r"].bone("axe", (0, 6, 0), (-52, 0, 0))  # carried low, the haft up and forward
    axe.cube("haft", -1.5, -1.5, -38, 3, 3, 48)
    axe.cube("blade", -1, -3, -42, 2, 19, 12)
    axe.cube("beard", -1, 16, -38, 2, 4, 6)
    axe.cube("pick", -1, -9, -36, 2, 6, 4)
    r.shadow = 2.2
    return r


def clips(r):
    plans.biped_locomotion(r, P, weight=1.6, temper="feral")
    plans.biped_reactions(r, weight=1.6)
    # in its idle the right arm carries the axe's weight: hold the blade off the ground
    mv = [
        M.overhead_slam(P, "hammer", title="Magma Fist", weight=1.4, windup=22, hold=10, active=5, recover=20, ability="Magma Fist",
                        kinds=("area", "near"), rng=(0, 4.6), damage=1.4, knock=1.0),
        M.backhand(P, "backhand", side="l", weight=1.4, windup=10, active=4, recover=12, rng=(0, 4.0), damage=0.8, low=24, arm=(-30, 0, -40)),
        M.chop(P, "chop_a", side="r", weight=1.4, windup=12, active=4, recover=10, weapon=AXE_TIP, weapon_r=8, weapon_bone="axe", diagonal=1.0,
               kinds=("combo",), rng=(0, 5.2), damage=1.0, advance=0.8),
        M.chop(P, "chop_b", side="r", weight=1.4, windup=9, active=4, recover=14, weapon=AXE_TIP, weapon_r=8, weapon_bone="axe", diagonal=-1.0,
               kinds=("combo",), rng=(0, 5.2), damage=1.0, advance=0.6),
        M.shoulder_charge(P, "charge", side="l", weight=1.4, windup=16, active=12, recover=20, advance=7.5, rng=(5, 14), damage=1.2),
        M.stomp(P, "stomp", side="l", weight=1.4, windup=14, active=4, recover=16, kinds=("area", "behind"), rng=(0, 4.2), damage=1.1),
        M.overhead_slam(P, "eruption", title="Eruption", weight=1.6, cls="PERILOUS", windup=26, active=6, recover=26, ability="Eruption",
                        kinds=("area", "ranged"), rng=(0, 16), damage=1.8, knock=1.4, signature=True, rear=1.7),
        M.roar(P, "roar", weight=1.4, windup=12, active=28, recover=14),
        M.roar(P, "stoke", title="Stoke the Imps", weight=1.4, windup=12, active=24, recover=12, ability="Stoke the Imps", kinds=("summon",)),
        M.roar(P, "meltdown", title="Meltdown", weight=1.4, cls="PERILOUS", windup=18, active=30, recover=16, ability="Meltdown",
               kinds=("area",)),
    ]
    for m in mv:
        stage_clips(r, m)
    return mv


FIGHT = {
    "intro": {"pose": "idle", "move": "roar"},
    "phases": [
        {"pool": [
            {"move": "hammer", "weight": 3},
            {"move": "backhand", "weight": 3, "when": "near"},
            {"move": "chop_a", "weight": 3, "then": [["chop_b"], ["chop_b", "hammer"]]},
            {"move": "charge", "weight": 2, "when": "far"},
            {"move": "stomp", "weight": 3, "when": "behind"},
            {"move": "eruption", "weight": 1},
        ]},
        {"transition": "stoke", "pool": [
            {"move": "hammer", "weight": 3},
            {"move": "backhand", "weight": 2, "when": "near", "then": [["chop_a", "chop_b"]]},
            {"move": "chop_a", "weight": 3, "then": [["chop_b"], ["chop_b", "hammer"]]},
            {"move": "charge", "weight": 2, "when": "far"},
            {"move": "stomp", "weight": 3, "when": "behind"},
            {"move": "eruption", "weight": 2},
        ]},
        {"transition": "meltdown", "pool": [
            {"move": "hammer", "weight": 3, "then": [["stomp"]]},
            {"move": "backhand", "weight": 2, "when": "near", "then": [["chop_a", "chop_b"]]},
            {"move": "chop_a", "weight": 3, "then": [["chop_b", "hammer"]]},
            {"move": "charge", "weight": 2, "when": "far", "then": [["eruption"]]},
            {"move": "stomp", "weight": 3, "when": "behind"},
            {"move": "eruption", "weight": 2},
            {"move": "meltdown", "weight": 1},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def paint(r, P_):
    skin = Ramp("#0e0a0a", "#181211", "#231a18", "#30231f", "#3e2d27", "#4e3a31", "#62483c", "#7a5a4a")
    horn = Ramp("#070606", "#110e0e", "#1c1716", "#2a2220", "#3a2e2a", "#4e3e36")
    iron = Ramp("#101115", "#1a1c21", "#26292f", "#33373f", "#434852", "#575d68", "#717884")
    rust = Ramp("#2a140c", "#3e1e10", "#562a14", "#6e3a1c")
    leather = Ramp("#140c08", "#21140c", "#301d12", "#422818", "#563420")
    wood = Ramp("#140c07", "#20140c", "#2e1d12", "#3e2818", "#4e331f")
    magma = ("#ff4a10", "#ff7a1e", "#ffae3a", "#ffd870")
    rng = P_.rng
    rock = combine(p_stone(rng, 0.9, 5), p_strokes(3, 2, 0.35, 0.25, 0.5, seed=7))
    for c in ("pelvis", "spine", "chest", "neck", "head", "arm_l", "arm_r", "forearm_l", "forearm_r", "hand_l", "hand_r",
              "thigh_l", "thigh_r", "shin_l", "shin_r", "jaw"):
        P_.paint(c, skin, 3.4, rock, noise=0.5)
    for c in ("foot_l", "foot_r"):
        P_.paint(c, skin, 2.6, p_stone(rng, 0.6, 3))
    # the magma in it: cracks on the chest, the back, the thighs, and veins all over the forearms
    for c, n in (("chest", 3), ("spine", 2), ("thigh_l", 1), ("thigh_r", 1), ("arm_l", 1), ("arm_r", 1), ("neck", 1), ("pelvis", 1)):
        cracks(P_, c, SIDES, skin.at(0), magma, n=n, seed=c)
    for c in ("forearm_l", "forearm_r", "hand_l", "hand_r"):
        cracks(P_, c, SIDES + ("top",), skin.at(0), magma, n=4, seed=c, alpha=245)
    # the core: white-hot at the middle, a dark crust round it
    radial(P_, "core", "north", ["#fff6d0", "#ffd870", "#ffae3a", "#ff7a1e", "#ff4a10", "#c22a08"], skin=Ramp("#5a1a08", "#8a2c0c", "#c2481a", "#f07a2c"))
    for fname in ("west", "east", "top", "bottom"):
        P_.paint("core", Ramp("#3a1206", "#5a1a08", "#7a280e"), 1.6, faces=(fname,))
        edge_glow(P_, "core", fname, "#ff7a1e", rows=1, alpha=200, side="top")
    # horns: obsidian ridged across, ember at the tips
    for k in (1, 2, 3):
        for nm in ("l", "r"):
            c = f"horn{k}_{nm}"
            P_.paint(c, horn, 2.0 + k * 0.4, lambda f, i, j: (-0.8 if f.name in SIDES and i % 3 == 0 else 0.3))
            if k == 3:
                for fname in SIDES:
                    edge_glow(P_, c, fname, "#ff7a1e", rows=2, alpha=170, side="right" if nm == "l" else "left")
    P_.paint("brow", skin, 2.4, p_stone(rng, 0.8, 3))
    for c in ("tusk_l", "tusk_r"):
        P_.paint(c, Ramp("#6e6050", "#8e806c", "#b0a28a", "#d0c4aa"), 2.0)
    # the face: deep eye sockets under the brow, embers for eyes; a mouth glowing from inside
    f = P_.face("head", "north")
    for i, j in f.each():
        if 7 <= j <= 9 and (2 <= i <= 6 or 11 <= i <= 15):
            f.set(i, j, skin.at(0))
    with P_.eyes():
        for x0 in (3, 12):
            for dx in range(3):
                P_.face("head", "north").set(x0 + dx, 8, hexc("#2a0a04"))
                P_.face("head", "north", glow=True).set(x0 + dx, 8, hexc("#ffd060" if dx == 1 else "#ff8a20", 255))
    jf = P_.face("jaw", "north")
    jg = P_.face("jaw", "north", glow=True)
    for i in range(1, jf.w - 1):
        jf.set(i, 0, skin.at(0))
        jg.set(i, 0, hexc("#ff6a1a", 170))
        if i % 3 == 1:
            jf.set(i, 1, hexc("#b0a28a"))
    # iron: plates, bracers, knuckles, rust on the edges
    ironp = lambda f, i, j: (0.8 if j == 0 else -0.7 if (i + j) % 7 == 0 else 0)
    for c in ("plate_l", "plate_r", "bracer_l", "bracer_r", "knuckle_l", "knuckle_r", "spike_l", "spike_r"):
        P_.paint(c, iron, 3.2, ironp, noise=0.4)
    for c in ("plate_l", "plate_r", "bracer_l", "bracer_r"):
        for fname in SIDES:
            fc = P_.face(c, fname)
            for i, j in fc.each():
                if j == fc.h - 1 and R._h(c, fname, i) % 3 == 0:
                    fc.set(i, j, rust.at(2))
        for k, fname in enumerate(("north", "south")):  # rivets
            fc = P_.face(c, fname)
            for i in range(2, fc.w - 1, 5):
                fc.set(i, 2, iron.at(6))
    for c in ("dorsal0", "dorsal1", "dorsal2"):
        P_.paint(c, horn, 2.6)
        edge_glow(P_, c, "south", "#ff7a1e", rows=1, alpha=150, side="top")
    P_.paint("belt", leather, 3.0, lambda f, i, j: (0.8 if j == 0 else -0.6 if j == f.h - 1 else 0))
    bf = P_.face("belt", "north")
    for i in range(bf.w // 2 - 3, bf.w // 2 + 3):
        for j in range(1, 4):
            bf.set(i, j, iron.at(5 if j == 1 else 4))
    P_.paint("tasset", leather, 2.6, p_strokes(2, 3, 0.4, 0.3, 0.6, seed=3))
    # the axe: a dark haft bound in iron, a black blade with an edge that burns
    P_.paint("haft", wood, 2.6, lambda f, i, j: (1.0 if f.name in SIDES and i % 9 < 2 else 0))
    for c in ("blade", "beard", "pick"):
        P_.paint(c, iron, 2.4, lambda f, i, j: (-0.6 if (i * 3 + j) % 11 == 0 else 0))
    for fname in ("west", "east"):
        edge_glow(P_, "blade", fname, "#ff8a2a", rows=2, alpha=230, side="left" if fname == "west" else "right")
        edge_glow(P_, "beard", fname, "#ff8a2a", rows=1, alpha=200, side="bottom")
    edge_glow(P_, "blade", "north", "#ffb048", rows=12, alpha=230)
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.9}
