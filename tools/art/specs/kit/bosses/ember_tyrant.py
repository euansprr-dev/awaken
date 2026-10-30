"""The Ember Tyrant (SPEC §35.9, docs/bosses/REWORK.md R1): the Living Forge, Euan's pick from the silhouette round.

A walking mountain of basalt, lopsided: a right arm burst into a molten fist, a slighter left arm dragging a great
axe, a carapace with three chimneys on its back, and a horned skull carried low in front. Its light story is the
forge: the core behind its ribs, the vents, the fist's fissures, the eyes and the axe's edge all burn the same orange.

Moves (SPEC §35.10): Magma Fist (the molten fist raised high and brought down alone, HEAVY), the fist's backhand
(LIGHT), the axe string (a one-handed cleave that bites the ground some 7 blocks out, then a flat sweep at waist height,
two LIGHTs), a shoulder charge with the fist's shoulder (GRAB), a stomp for whoever is behind it, Eruption (the fist
again, PERILOUS, the signature), Stoke the Imps and Meltdown (its transitions). The axe stays in the left hand and
never goes into the ground; the fist never holds it.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, p_stone, combine, p_strokes, SIDES

ID = "ember_tyrant"
SCALE = 3.0  # the hitbox (the def's scale on vanilla's piglin brute: 1.8 x 5.85 blocks); the body is modelled at true size

# The Living Forge (REWORK.md R1, Euan's pick C, 2026-10-28): a walking mountain, lopsided. Short thick legs, the torso
# pitched far forward, a rock carapace stepping up off its back with three chimneys, a long neck carrying a horned skull
# low in front like a hunting beast, a right arm burst into a molten fist the size of a cart, and a slighter left arm
# dragging the great axe behind it. Value story: the skull and horns are the lightest thing on it, the heat the
# brightest; the rock is dark, the iron in between.
P = plans.Biped(leg=40, thigh=20, foot=(22, 6, 26), pelvis=(40, 16, 28), spine=(36, 16, 28), chest=(60, 32, 40), neck=(16, 16, 16),
                head=(24, 17, 26), arm=(22, 28, 22), forearm=(34, 30, 34), hand=(40, 22, 38), stance=17, hunch=36, leg_w=(22, 18))
HUNCH = (14, 22)  # the spine's and the chest's rest pitch: the torso leans 36 degrees forward
AXE_TIP = (0, 66, -15)  # the blade's middle, in the axe bone's space
AXE_EDGE = ((0, 66, -15), (0, 66, -27))  # the blade's middle and its edge: the edge faces -z


def body():
    r = R.Rig(ID, tex=(1024, 1024))
    hip = 24 - P.leg
    pel = r.bone("pelvis", (0, hip, 0))
    pel.cube("pelvis", -20, -12, -14, 40, 16, 28)
    pel.cube("belt", -21, -3, -15, 42, 5, 30)
    for k, (x, w, rz) in enumerate(((-13, 12, 8), (0, 12, 0), (13, 12, -8))):  # iron tassets over the hips
        parts.plate(pel, f"tasset{k}", (x, 2, -15), (-6, 0, rz), (-w / 2, 0, -1, w, 12, 2))
    # legs: short, thick and planted, the right one heavier (it carries the fist)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        w = 20 if side > 0 else 24
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 2))
        th.cube("thigh_" + nm, -w / 2, -3, -w / 2, w, 23, w, mirror=mir)
        th.cube("knee_" + nm, -w / 2 + 2, 13, -w / 2 - 3, w - 4, 9, 4, mirror=mir)
        th.cube("hipslab_" + nm, w / 2 - 1 if side > 0 else -w / 2 - 4, -4, -8, 5, 14, 16, mirror=mir)  # a rock slab on the hip
        sn = th.bone("shin_" + nm, (0, 20, 0))
        sw = w - 4
        sn.cube("shin_" + nm, -sw / 2, 0, -sw / 2, sw, 14, sw, mirror=mir)
        sn.cube("greave_" + nm, -sw / 2 + 2, 1, -sw / 2 - 3, sw - 4, 10, 4, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, 14, 0))
        ft.cube("foot_" + nm, -w / 2 - 1, 0, -15, w + 2, 6, 24, mirror=mir)
        ft.cube("ankle_" + nm, -w / 2 + 1, -2, -6, w - 2, 3, 14, mirror=mir)
        for k in range(3):  # three blunt claws
            ft.cube(f"claw{k}_" + nm, -w / 2 + 1 + k * (w - 2) / 3 + (w - 2) / 6 - 2.5, 2, -19, 5, 4, 5, mirror=mir)
        ft.cube("heel_" + nm, -3, 1, 8, 6, 4, 4, mirror=mir)
    # the torso, pitched far forward
    sp = pel.bone("spine", (0, -10, 2), (HUNCH[0], 0, 0))
    sp.cube("spine", -18, -17, -14, 36, 18, 28)
    sp.cube("spineback", -14, -15, 12, 28, 12, 6)
    ch = sp.bone("chest", (0, -16, 0), (HUNCH[1], 0, 0))
    ch.cube("chest", -34, -32, -20, 60, 32, 40)  # heavier on its right (-x), where the fist hangs
    ch.cube("collar", -28, -38, -14, 50, 6, 30)
    # the ribcage: the core burns behind bone bars, framed by two rock slabs, so it reads as a furnace in a chest
    ch.cube("pec_r", -34, -31, -24, 23, 22, 5)
    ch.cube("pec_l", 11, -31, -24, 15, 20, 5)
    core = ch.bone("core", (0, -18, -21))
    core.cube("core", -10, -10, -1, 20, 20, 3)
    parts.bars(ch, "rib", -11, -28, -25, 22, 5, 2, h=2, d=3)
    ch.cube("sternum", -2, -30, -26, 4, 22, 3)
    ch.cube("belly", -26, -6, -22, 48, 6, 6)
    # the carapace: shingled slabs over the back, each lifting off the one below and thicker as it climbs to the
    # shoulders (a humped ridge, the mountain), with three short chimneys standing up out of it
    for k, (w, h, d, y, rx, rz) in enumerate(((50, 14, 8, -2, -6, 3), (58, 14, 10, -12, -10, -2), (62, 14, 12, -22, -14, 2),
                                             (54, 12, 12, -31, -20, -3), (38, 10, 10, -38, -28, 2))):
        parts.plate(ch, f"shell{k}", (-4, y, 18), (rx, 0, rz), (-w / 2, -h, 0, w, h, d))
    for k, (x, y, z, sizes, rz) in enumerate(((-22, -26, 32, ((11, 8, 11), (9, 7, 9), (7, 5, 7)), 10), (-4, -33, 25, ((13, 9, 13), (11, 8, 11), (9, 6, 9)), 0),
                                              (14, -26, 31, ((9, 7, 9), (7, 6, 7), (5, 4, 5)), -10))):
        cb = ch.bone(f"chimney{k}", (x, y, z), (-sum(HUNCH) - 12, 0, rz))  # upright in the world, raked back a little
        parts.taper(cb, f"chimney{k}_", 0, 0, 0, sizes)
        top = sum(sz[1] for sz in sizes)
        cb.cube(f"vent{k}", -sizes[-1][0] / 2 + 1, -top - 2, -sizes[-1][2] / 2 + 1, sizes[-1][0] - 2, 2, sizes[-1][2] - 2)
    # the neck: long and thrust forward out of the chest's top front, a ridge of spines along it
    nk = ch.bone("neck", (-4, -24, -18), (44, 0, 0))
    nk.cube("neck", -8, -20, -8, 16, 22, 16)
    nk.cube("throat", -6, -18, -10, 12, 14, 3)
    for k in range(4):
        nk.cube(f"nspine{k}", -2, -19 + k * 5, 6, 4, 4, 6 - k)
    # the skull: a long horned mask, level, low in front of the body; the lightest value on it
    hd = nk.bone("head", (0, -19, -2), (-HUNCH[0] - HUNCH[1] - 44 + 8, 0, 0))  # level, dipped a little: it hunts
    hd.cube("head", -12, -11, -24, 24, 17, 26)
    for side, nm in ((1, "l"), (-1, "r")):  # the brow in two plates dipping to the middle: it scowls
        parts.plate(hd, "brow_" + nm, (0, -11, -24), (0, 0, side * -12), (0 if side > 0 else -14, -3, -3, 14, 5, 8), mirror=side < 0)
    hd.cube("snout", -8, -2, -30, 16, 7, 7)
    hd.cube("crest", -5, -15, -14, 10, 4, 16)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        hd.cube("cheek_" + nm, side * 12 - (0 if side > 0 else 3), -7, -22, 3, 11, 16, mirror=mir)
        # horns: thick at the root, swept back over the shoulders in stepped segments, the tips turning up
        parts.segments(hd, "horn_" + nm, (side * 8, -11, -6), (-74, side * 8, side * -22), ((8, 12), (7, 12), (5, 11), (4, 9), (2, 6)),
                       (10, 0, side * 6), mirror=mir)
    jaw = hd.bone("jaw", (0, 5, -2))
    jaw.cube("jaw", -10, 0, -25, 20, 7, 24)
    for side, nm in ((1, "l"), (-1, "r")):
        jaw.cube("tusk_" + nm, side * 8 - 1.5, -5, -25, 3, 5, 3, mirror=side < 0)
        jaw.cube("fang_" + nm, side * 4 - 1, -2, -24, 2, 2, 2, mirror=side < 0)
    # the right arm: burst into a molten fist the size of a cart, a broken shackle on its wrist
    shr = ch.bone("shoulder_r", (-32, -26, 0), (-sum(HUNCH) - 14, 0, 16))  # the fist hangs out and forward of the body
    parts.taper(shr, "pauldron_r", -2, 8, 0, ((30, 12, 30), (26, 8, 26), (18, 6, 18)))
    shr.cube("band_r", -18, -1, -16, 32, 5, 32)
    parts.plate(shr, "spaul_r", (-12, -10, 0), (0, 0, 24), (-6, -14, -10, 12, 14, 20))
    ar = shr.bone("arm_r", (-6, 2, 0))
    ar.cube("arm_r", -11, -2, -11, 22, 28, 22, mirror=True)
    far = ar.bone("forearm_r", (0, 26, 0))
    far.cube("forearm_r", -17, -1, -17, 34, 30, 34, mirror=True)
    parts.plate(far, "fplate_r", (-17, 2, 0), (0, 0, 6), (-4, 0, -14, 5, 22, 28), mirror=True)
    far.cube("ftop_r", -13, -3, -15, 26, 3, 30, mirror=True)
    far.cube("shackle_r", -19, 21, -19, 38, 6, 38, mirror=True)
    parts.chain(far, "chain_r", (-12, 27, -12), 4, link=(3, 6, 3))
    hr = far.bone("hand_r", (0, 29, 0))
    hr.cube("hand_r", -20, 0, -17, 40, 18, 36, mirror=True)
    for k in range(4):  # the knuckles stand proud along the front, the folded fingers tuck under them
        hr.cube(f"knuckle{k}_r", -19 + k * 9.75, 8, -22, 9, 10, 8, mirror=True)
        hr.cube(f"finger{k}_r", -18.5 + k * 9.75, 18, -19, 8, 5, 14, mirror=True)
    hr.cube("thumb_r", 17, 6, -21, 7, 9, 15, mirror=True)
    # the left arm: slighter, a spiked pauldron, an iron bracer, and the great axe in its fist
    shl = ch.bone("shoulder_l", (22, -26, 2), (-sum(HUNCH), 0, -8))
    parts.taper(shl, "pauldron_l", 2, 6, 0, ((22, 10, 22), (16, 6, 16)))
    for k, (x, z) in enumerate(((0, -4), (5, 5))):
        parts.segments(shl, f"pspike{k}_", (x + 2, -10, z), (-10, 0, -20 - k * 10), ((4, 6), (2, 5)), (0, 0, -10))
    al = shl.bone("arm_l", (4, 2, 0))
    al.cube("arm_l", -8, -2, -8, 16, 24, 16)
    fal = al.bone("forearm_l", (0, 22, 0))
    fal.cube("forearm_l", -9, -1, -9, 18, 24, 18)
    fal.cube("bracer_l", -10, 9, -10, 20, 11, 20)
    hl = fal.bone("hand_l", (0, 23, 0))
    hl.cube("hand_l", -8, 0, -8, 16, 12, 16)
    hl.cube("knuckles_l", -7, 7, -10, 14, 4, 3)
    # the great axe: a long haft trailing back and down from the left fist, the head's blade scoring the ground behind
    # the grip holds the axe's tilt; the axe under it turns on its own haft (its y), so a blow can turn the edge into
    # the swing (Move.edge)
    grip = hl.bone("grip_l", (0, 6, 0), (84, 0, 0))
    axe = grip.bone("axe", (0, 0, 0))
    axe.cube("haft", -2.5, -8, -2.5, 5, 80, 5)
    axe.cube("pommel", -3.5, -12, -3.5, 7, 5, 7)
    axe.cube("grip", -3, 2, -3, 6, 14, 6)
    for k, (h, z0, d) in enumerate(((40, -2, 10), (30, -12, 8), (18, -20, 6))):  # the blade in stepped tiers
        axe.cube(f"blade{k}", -2, 70 - h / 2 - 4, z0 - d, 4, h, d)
    axe.cube("edge", -1.5, 57, -27, 3, 18, 1)
    axe.cube("backspike", -2, 60, 2, 4, 8, 10)
    axe.cube("collar_axe", -3.5, 48, -3.5, 7, 5, 7)
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 2.6
    # secondary parts that trail the body (SPEC §35.10): the shackle's chain swings, the tassets flap, the shell shifts
    r.guard = ["axe"]  # the great axe never goes into the ground, in any clip (core.check)
    r.springs = [(f"chain_r{k}", 1.0, 2.2, 0.18) for k in range(4)] + [(f"tasset{k}", 0.6, 3.5, 0.3) for k in range(3)] \
        + [(f"shell{k}", 0.08, 6.0, 0.5) for k in range(5)]
    return r


def clips(r):
    plans.biped_locomotion(r, P, weight=1.6, temper="feral")
    plans.biped_reactions(r, weight=1.6)
    mv = [
        M.fist_slam(P, "hammer", side="r", title="Magma Fist", weight=1.4, windup=22, hold=10, active=5, recover=20, ability="Magma Fist",
                    kinds=("area", "near"), rng=(0, 4.6), damage=0.7, knock=1.0, weapon_bone="axe", disc=2.8),
        M.backhand(P, "backhand", side="r", weight=1.4, windup=10, active=4, recover=12, rng=(0, 4.0), damage=0.8, low=24, arm=(-30, 0, -40)),
        M.cleave(P, "chop_a", side="l", weight=1.4, windup=12, active=4, recover=18, weapon=AXE_TIP, weapon_r=8, weapon_bone="axe", edge=AXE_EDGE,
                 kinds=("combo",), rng=(1.5, 6.5), damage=1.0, advance=0.8),
        M.sweep(P, "chop_b", side="l", weight=1.4, windup=9, active=4, recover=16, weapon=AXE_TIP, weapon_r=8, weapon_bone="axe", edge=AXE_EDGE,
                kinds=("combo",), rng=(0, 6.5), damage=1.0, advance=0.6),
        M.shoulder_charge(P, "charge", side="r", weight=1.4, windup=16, active=12, recover=20, advance=7.5, rng=(5, 14), damage=1.2),
        M.stomp(P, "stomp", side="l", weight=1.4, windup=14, active=4, recover=16, kinds=("area", "behind"), rng=(0, 4.2), damage=1.1),
        M.fist_slam(P, "eruption", side="r", title="Eruption", weight=1.6, cls="PERILOUS", windup=26, active=6, recover=26, ability="Eruption",
                    kinds=("area", "ranged"), rng=(0, 16), damage=0.8, knock=1.4, signature=True, rear=1.7, weapon_bone="axe"),
        M.roar(P, "roar", weight=1.4, windup=12, active=28, recover=14),
        M.roar(P, "stoke", title="Stoke the Imps", weight=1.4, windup=12, active=24, recover=12, ability="Stoke the Imps", kinds=("summon",)),
        M.roar(P, "meltdown", title="Meltdown", weight=1.4, cls="PERILOUS", windup=18, active=30, recover=16, ability="Meltdown",
               kinds=("area",)),
    ]
    for m in mv:
        stage_clips(r, m)
    # the great axe stays out of the ground in every clip but the fall: the wrist turns first, then the elbow and shoulder
    own = {core.clip_name(m) for m in mv if m.ground}  # those had their own pass
    core.ground_pass(r, [(r.guard, [("hand_l", 0, 70), ("forearm_l", 0, 40), ("arm_l", 0, 45)])],
                     clips=[n for n in r.clips if n not in own and n != "death"])
    return mv



# Its sounds (SPEC §35.8), each set a few vanilla layers played as one: low and heavy, metal and fire. Chosen by ear on
# paper, not yet heard in game.
SOUNDS = {
    "tell_light": [("entity.piglin_brute.angry", 1.6, 0.7)],
    "tell_heavy": [("entity.hoglin.angry", 2.0, 0.6), ("block.lava.pop", 1.5, 0.6)],
    "tell_perilous": [("entity.ravager.roar", 2.5, 0.6), ("item.firecharge.use", 1.5, 0.5)],
    "tell_grab": [("entity.piglin_brute.angry", 2.0, 0.55), ("entity.ravager.step", 1.5, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.6, 0.6), ("item.mace.smash_air", 1.2, 0.6)],
    "swing_heavy": [("entity.player.attack.sweep", 2.0, 0.45), ("entity.wind_charge.throw", 1.5, 0.5), ("item.mace.smash_air", 1.5, 0.5)],
    "cast": [("item.firecharge.use", 2.0, 0.6), ("block.lava.pop", 1.5, 0.5)],
    "impact": [("item.mace.smash_ground_heavy", 2.5, 0.6), ("entity.generic.explode", 2.0, 0.6), ("entity.warden.attack_impact", 2.0, 0.5)],
    "roar": [("entity.ravager.roar", 3.0, 0.55), ("entity.piglin_brute.angry", 2.5, 0.5)],
    "step": [("entity.warden.step", 1.4, 0.6), ("entity.ravager.step", 1.0, 0.5)],
}
MOVE_SOUNDS = {}

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
    """Four materials in a value order: heat brightest, then bone (the skull, horns, ribs and claws lead the eye), then
    iron, then the dark rock of the body. Rock is cut into slabs by darker seams so the big faces never read flat."""
    rock = Ramp("#0c0908", "#141010", "#1d1715", "#271f1c", "#332824", "#40322c", "#4f3e36", "#614c41")
    shell = Ramp("#120e0d", "#1c1715", "#28211e", "#352b27", "#443832", "#56473f", "#6b5a50", "#857163")
    bone = Ramp("#2e2620", "#463a30", "#5e5042", "#786856", "#93826c", "#ad9c84", "#c6b69c", "#ddd0b6")
    iron = Ramp("#0f1013", "#181a1f", "#23262c", "#2f333a", "#3d424b", "#4f555f", "#666d78")
    rust = Ramp("#2a140c", "#3e1e10", "#562a14", "#6e3a1c")
    wood = Ramp("#120b07", "#1d130c", "#2a1b11", "#382417", "#46301e")
    heat = ("#ff4a10", "#ff7a1e", "#ffae3a", "#ffd870")
    rng = P_.rng
    of = lambda *ps: parts.cubes_of(r, *ps)
    seams = lambda cell: combine(p_stone(rng, 0.7, cell), p_strokes(4, 3, 0.35, 0.2, 0.35, seed=7))
    body_rock = ["pelvis", "spine", "spineback", "chest", "collar", "pec_r", "pec_l", "belly", "neck", "arm_r", "forearm_r", "hand_r",
                 "arm_l", "forearm_l", "hand_l", "ftop_r", "thumb_r"] + of("thigh_", "shin_", "foot_", "ankle_", "heel_", "hipslab_", "knuckle", "finger", "pauldron_", "fplate_r", "spaul_r", "knee_", "greave_", "throat")
    for c in body_rock:
        if c in P_.cubes:
            P_.paint(c, rock, 3.0, seams(13), grad=1.6, rim=1.2, ao=1.2, noise=0.9, nscale=10)
    for c in of("shell"):
        # the carapace: weathered slabs, ash-pale on their upper edges, dark underneath
        P_.paint(c, shell, 3.4, combine(p_stone(rng, 1.2, 5), lambda f, i, j: (1.2 if f.name == "top" else 0.6 if j <= 1 else 0)), noise=0.6)
    # bone: the skull mask, horns, ribs, claws, spines; banded where horn grows
    for c in ["head", "brow_l", "brow_r", "snout", "crest", "rib0", "rib1", "rib2", "rib3", "rib4", "sternum", "jaw"] + of("cheek_", "tusk_", "fang_", "claw", "nspine"):
        if c in P_.cubes:
            P_.paint(c, bone, 4.2 if c not in ("jaw", "sternum", "throat") else 3.2, p_strokes(5, 2, 0.5, 0.3, 0.3, seed=11), grad=1.4, rim=1.0, ao=1.4, noise=0.7, nscale=8)
    for c in of("horn_", "pspike"):
        P_.paint(c, bone, 3.4, lambda f, i, j: (-0.9 if f.name in SIDES and j % 4 == 0 else 0.2))
    # iron: banded, riveted, rusted at the bottom edges
    ironp = lambda f, i, j: (0.8 if j == 0 else -0.7 if (i + j) % 7 == 0 else 0)
    for c in ["belt", "band_r", "shackle_r", "bracer_l", "knuckles_l", "collar_axe", "pommel", "grip"] + of("tasset", "chain_r", "chimney"):
        P_.paint(c, iron, 3.0, ironp, noise=0.4)
        for fname in SIDES:
            fc = P_.face(c, fname)
            for i, j in fc.each():
                if j == fc.h - 1 and R._h(c, fname, i) % 3 == 0:
                    fc.set(i, j, rust.at(2))
    for c in ("belt", "band_r", "shackle_r", "bracer_l"):
        for fname in ("north", "south", "east", "west"):
            fc = P_.face(c, fname)
            for i in range(2, fc.w - 1, 6):
                fc.set(i, 1, iron.at(6))
    # heat: the fist has burst (deep fissures all over it), the chest and the right leg are cracked, the chimneys vent
    for c in ("forearm_r", "hand_r", "fplate_r", "ftop_r") + tuple(of("knuckle")):
        cracks(P_, c, SIDES + ("top",), rock.at(0), heat, n=3 if c in ("forearm_r", "hand_r") else 1, seed=c, alpha=245)
    for c, n in (("pec_r", 1), ("thigh_r", 1), ("arm_r", 1)):
        cracks(P_, c, ("north", "west"), rock.at(0), heat, n=n, seed=c, alpha=200)
    for k in range(3):
        v = f"vent{k}"
        P_.paint(v, Ramp("#5a1a08", "#8a2c0c"), 1.0)
        for fname in ("top",) + SIDES:
            radial(P_, v, fname, ["#fff2c0", "#ffd870", "#ffae3a", "#ff7a1e"], alpha=255)
        for fname in SIDES:
            edge_glow(P_, f"chimney{k}_2", fname, "#ff7a1e", rows=2, alpha=190, side="top")
    # the core behind the ribs: white-hot in the middle
    radial(P_, "core", "north", ["#fff6d0", "#ffe08a", "#ffb648", "#ff8a24", "#ff5a14", "#c2300a"], skin=Ramp("#5a1a08", "#8a2c0c", "#c2481a", "#f07a2c"))
    for fname in ("west", "east", "top", "bottom"):
        P_.paint("core", Ramp("#3a1206", "#5a1a08", "#7a280e"), 1.6, faces=(fname,))
    # light leaks from the furnace onto the rib bars' undersides
    for k in range(5):
        edge_glow(P_, f"rib{k}", "north", "#ff8a2a", rows=1, alpha=150, side="bottom")
    # the face: two embers deep under the brow, the mouth lit from inside
    f = P_.face("head", "north")
    for i, j in f.each():
        if j <= 3:
            f.set(i, j, bone.at(0))  # the brow's shadow
        elif (2 <= i <= 9 or 14 <= i <= 21) and 3 <= j <= 7:
            f.set(i, j, rock.at(0))  # deep sockets
        elif 11 <= i <= 12 and j >= 3:
            f.set(i, j, bone.at(6))  # the bridge between them catches the light
    with P_.eyes():
        for x0 in (3, 15):
            for dx in range(6):
                for dy in (4, 5, 6):
                    if (dx in (0, 5)) and dy != 5:
                        continue
                    P_.face("head", "north").set(x0 + dx, dy, hexc("#1a0602"))
                    hot = dx in (2, 3) and dy == 5
                    P_.face("head", "north", glow=True).set(x0 + dx, dy, hexc("#fff0a0" if hot else "#ffae3a" if dy == 5 else "#ff6a1a", 255))
    for c in ("snout", "jaw"):
        fc, gc = P_.face(c, "north"), P_.face(c, "north", glow=True)
        row = fc.h - 1 if c == "snout" else 0
        for i in range(fc.w):
            fc.set(i, row, rock.at(0))
            gc.set(i, row, hexc("#ff6a1a", 170))
    # the axe: a dark haft bound in iron, a black stepped blade whose edge burns
    P_.paint("haft", wood, 2.6, lambda f, i, j: (1.4 if f.name in SIDES and j % 12 < 2 else 0))
    for c in of("blade", "backspike"):
        P_.paint(c, iron, 2.6, lambda f, i, j: (-0.7 if (i * 3 + j) % 11 == 0 else 0.6 if j == 0 else 0))
    P_.paint("edge", iron, 4.0)
    for fname in ("top", "bottom") + SIDES:
        edge_glow(P_, "edge", fname, "#ffb048", rows=99, alpha=240)
    for fname in ("west", "east"):
        edge_glow(P_, "blade2", fname, "#ff8a2a", rows=2, alpha=200, side="left" if fname == "west" else "right")
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.9, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
