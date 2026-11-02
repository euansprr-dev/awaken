"""The Unmade (SPEC §47.7, docs/fights/PLAN.md §4.7): the third Proving's Warden, met on the Broken Span. The bridge's
north arm, stood up: a masonry colossus six blocks high, built of the bridge and held together by nothing. Its legs are
two squat piers on wide footings; its hips are a span of the bridge with an arch between the legs; its body is courses of
stone brick that widen as they climb to great corbelled shoulders, and in its chest is an arched niche with a lamp still
burning in it, the brightest thing on it. Its head is a keystone, wider at the top, with a lit slit where a face would
be. Its arms are column drums with gaps of nothing between them, its fists keystones bound in iron, and they hang to its
knees. One of the Span's lamp posts still stands on its left shoulder, its lantern swinging; a chain hangs from each
wrist; a few stones float over its head; broken capstones of the parapet ride on its back. Bold and simple (Euan,
2026-10-29: no sculpted faces, epic and mysterious).

It stands up out of the gathering on the bastion's rim (its intro) and fights as a duel: swats left and right with its
long arms, the Keystone (both fists overhead, held, brought down: a wave), the Stamp for whoever is behind it, Pier Run
(down on its fists and charging), and the Fall (its signature: one fist reared back and driven into the bridge, and the
floor breaks up in a line toward you, red). When the floor gives way it sweeps right round with an arm out low; when
the lamps go out it reaches to take you (a red grab) and calls the rubble down out of the dark. It dies kneeling and
falls forward onto its fists, its lamp going out.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_unmade"
SCALE = 3.0  # its hitbox on the player-sized HUMAN model: 2.4 wide, 6 blocks high; the rig is modelled at true size

P = plans.Biped(leg=34, thigh=15, foot=(18, 6, 19), pelvis=(34, 10, 20), spine=(26, 12, 18), chest=(46, 27, 26), neck=(10, 4, 10),
                head=(18, 16, 14), arm=(13, 26, 13), forearm=(17, 24, 17), hand=(20, 16, 20), stance=10.0, hunch=0, leg_w=(14, 13))
# its rest: bent over at the waist under the weight of its shoulders, the arms a little forward and out
LEAN = {"spine": 10, "chest": 12, "neck": -16}
ARM_REST = {"l": (-10, 0, -14), "r": (-10, 0, 14)}
FIST = (0, P.hand[1] + 1, 0)  # the fist's striking face, in the hand bone's space
WEIGHT = 1.4


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    # the hips: a span of the bridge, a keystone on its face, the arch between the legs springing from two corbels
    pel.cube("pelvis", -16, -6, -10, 32, 8, 20)
    pel.cube("keystone_p", -2.5, -7, -10.5, 5, 7, 1)
    pel.cube("spring_l", 1, 2, -8, 2, 2, 16)
    pel.cube("spring_r", -3, 2, -8, 2, 2, 16)
    # legs: two squat piers, a tuff band at the knee, wide footings
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0))
        th.cube("thigh_" + nm, -7, -2, -7, 14, 17, 14, mirror=mir)
        th.cube("band_" + nm, -7.5, 12, -7.5, 15, 3, 15, mirror=mir)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -6.5, 0, -6.5, 13, 13, 13, mirror=mir)
        ft = sn.bone("foot_" + nm, (0, 13, 0))
        ft.cube("foot_" + nm, -9, 0, -11, 18, 6, 19, mirror=mir)
    # the body: lower courses, then four courses widening up to the shoulders, bent over at the waist
    sp = pel.bone("spine", (0, -6, 0), (LEAN["spine"], 0, 0))
    sp.cube("spine", -10, -12, -8, 20, 12, 16)
    ch = sp.bone("chest", (0, -12, 0), (LEAN["chest"], 0, 0))
    parts.taper(ch, "course", 0, 0, 0, ((26, 7, 22), (34, 7, 24), (42, 7, 26), (46, 6, 26)))
    # the niche in its chest: an arch of stones round a dark recess, the lamp burning in it
    ch.cube("niche", -6, -21, -13.5, 12, 14, 1)
    ch.cube("lamp", -2.5, -15, -14.5, 5, 6, 2)
    ch.cube("arch_t", -7, -23, -14, 14, 2, 1)
    ch.cube("arch_l", 5, -21, -14, 2, 14, 1)
    ch.cube("arch_r", -7, -21, -14, 2, 14, 1)
    # the parapet's broken capstones riding on its back
    for k, (x, y, w) in enumerate(((-19, -28, 10), (-5, -29, 10), (9, -28, 10))):
        ch.cube(f"cap{k}", x, y, 7, w, 4 + (k == 1), 6)
    # shoulders: great corbels, a block on a smaller one stepping in under it
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        sh = ch.bone("shoulder_" + nm, (side * 25, -22, 0))
        sh.cube("corbel_" + nm + "0", -8, -6, -12, 16, 9, 24, mirror=mir)
        sh.cube("corbel_" + nm + "1", -6 + side * -2, 3, -9, 10, 4, 18, mirror=mir)
        # arms: column drums with a gap of nothing between them; the forearm's drums bigger, an iron cramp at the wrist
        arm = sh.bone("arm_" + nm, (side * 3, 5, 0), ARM_REST[nm])
        arm.cube("drum_" + nm + "0", -6.5, 0, -6.5, 13, 12, 13, mirror=mir)
        arm.cube("drum_" + nm + "1", -6, 14, -6, 12, 12, 12, mirror=mir)
        fa = arm.bone("forearm_" + nm, (0, 28, 0), (-14, 0, 0))
        fa.cube("fdrum_" + nm + "0", -8, 0, -8, 16, 11, 16, mirror=mir)
        fa.cube("fdrum_" + nm + "1", -8.5, 13, -8.5, 17, 9, 17, mirror=mir)
        fa.cube("cramp_" + nm, -9, 22, -9, 18, 2, 18, mirror=mir)
        # the fist: a keystone, wider at its face, bound in iron
        hd = fa.bone("hand_" + nm, (0, 25, 0))
        parts.taper(hd, "fist_" + nm, 0, 0, 0, ((16, 6, 16), (20, 10, 20)), up=False, mirror=mir)
        hd.cube("bind_" + nm, -10.5, 6, -10.5, 21, 2, 21, mirror=mir)
        # a chain from each wrist
        parts.chain(fa, "chain_" + nm, (side * 9, 23, 5), 4, link=(3, 5, 3))
    # on its left shoulder one of the Span's lamp posts still stands, its lantern swinging from the bracket
    post = r.by_name["shoulder_l"].bone("post", (2, -5, 3))
    post.cube("post", -1.5, -14, -1.5, 3, 14, 3)
    post.cube("post_cap", -2.5, -15, -2.5, 5, 1, 5)
    post.cube("bracket", -1, -14, -7, 2, 2, 6)
    hang = post.bone("hang", (0, -12, -6))
    hang.cube("hook", -0.5, 0, -0.5, 1, 2, 1)
    hang.cube("lantern", -2, 2, -2, 4, 5, 4)
    hang.cube("lantern_top", -1, 1, -1, 2, 1, 2)
    # the head: a keystone, wider at the top, with a lit slit across it; a few stones float over it
    nk = ch.bone("neck", (0, -27, -4), (LEAN["neck"], 0, 0))
    nk.cube("neck", -5, -4, -5, 10, 4, 10)
    hd = nk.bone("head", (0, -4, -1))
    parts.taper(hd, "key", 0, 0, 0, ((12, 5, 12), (15, 5, 13), (18, 6, 14)))
    hd.cube("slit", -5, -9, -7.6, 10, 2, 1)
    # (on purpose off the body: they hang from a bone with nothing on it, so the lab's check for stray parts passes them)
    over = hd.bone("over", (0, -16, 3))
    for k, (x, y, z, s) in enumerate(((-7, -5, -1, 4), (6, -7, 0, 3), (0, -10, 2, 3))):
        fl = over.bone(f"float{k}", (x, y, z))
        fl.cube(f"float{k}", -s / 2, -s / 2, -s / 2, s, s, s)
    r.look = [("head", 0.5, 0.4), ("neck", 0.2, 0.15)]
    r.size = P.leg / 36.0
    r.shadow = 1.8
    # what hangs or floats swings (SPEC §35.10): the chains, the lantern, the stones over its head
    r.springs = [(f"chain_{s}{k}", 0.5, 1.6, 0.25) for s in "lr" for k in range(4)] + [("hang", 0.6, 1.4, 0.18)] \
        + [(f"float{k}", 0.25, 1.0 + 0.3 * k, 0.4) for k in range(3)]
    r.guard = ["hand_l", "hand_r"]  # the fists never go into the floor but where a blow strikes it
    return r


ARMS = [([f"hand_{s}"], [(f"forearm_{s}", 0, 80), (f"arm_{s}", 0, 60)]) for s in "lr"]


def clips(r):
    plans.biped_locomotion(r, P, weight=WEIGHT, temper="regal")
    plans.biped_reactions(r, weight=WEIGHT)
    plans.biped_duel_reactions(r, weight=WEIGHT, lead="r")
    # standing, it breathes through its shoulders and the lamp on its shoulder swings
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("post", [(0, 0, 0, 0), (L * 0.5, 0, 0, -2), (L, 0, 0, 0)])
    # the death: it kneels, falls forward onto its fists, and its head goes down between its shoulders
    kneel = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -14, 1)}, "thigh_l": (-86, 0, 0), "shin_l": (88, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (-86, 0, 0), "shin_r": (88, 0, 0), "foot_r": (-2, 0, 0), "spine": (10, 0, 0), "chest": (10, 0, 0),
             "head": (20, 0, 0), "arm_l": (-10, 0, -16), "arm_r": (-10, 0, 16), "forearm_l": (-10, 0, 0), "forearm_r": (-10, 0, 0)}
    down = dict(kneel, spine=(26, 0, 0), chest=(34, 0, 0), head=(40, 0, 0), arm_l=(-56, 0, -10), arm_r=(-56, 0, 10),
                forearm_l=(-6, 0, 0), forearm_r=(-6, 0, 0), post=(30, 0, 10))
    keyed(r, "death", 2.6, [(0, {}), (0.3, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-40, 0, -40), "arm_r": (-40, 0, 40)}),
                            (1.0, kneel), (1.6, down), (2.6, down)])
    mv = [
        # its arms in twos and threes: swats left and right, the Keystone overhead (held), the Stamp behind it
        M.backhand(P, "swat_l", side="l", title="Swat", windup=13, active=4, recover=12, kinds=("combo",), rng=(0, 5.0), damage=1.0,
                   low=24, arm=(-40, -20, -50)),
        M.backhand(P, "swat_r", side="r", title="Backhand", windup=13, active=4, recover=12, kinds=("combo",), rng=(0, 5.0), damage=1.0,
                   low=24, arm=(-40, -20, -50)),
        M.overhead_slam(P, "keystone", title="Keystone", weight=WEIGHT, windup=22, hold=10, active=5, recover=22, ability="Keystone",
                        rng=(0, 5.5), damage=1.3),
        M.stomp(P, "stomp", side="l", title="Stamp", weight=WEIGHT, windup=16, active=4, recover=16, rng=(0, 3.5), damage=1.1, disc=3.0),
        pier_run(),
        the_fall(),
        # when the floor gives way: the Sweep right round; when the lamps go out: Unmake (a red grab) and the Rubble
        M.spin_cut(P, "sweep", side="r", title="Span Sweep", cls="HEAVY", windup=18, active=8, recover=16, kinds=("area", "behind"),
                   rng=(0, 5.5), damage=1.2, low=30, drop=40),
        M.lunge_cut(P, "unmake", side="l", title="Unmake", cls="GRAB", windup=18, active=6, recover=18, advance=4.0, kinds=("gap",),
                    rng=(3, 10), damage=1.3, weapon=FIST),
        M.sky_call(P, "rubble", title="Rubble", windup=18, active=10, recover=16, ability="Rubble", rng=(0, 30)),
        rise(),
        give(),
        M.roar(P, "dark", title="The lamps go out", weight=WEIGHT),
    ]
    by = {m.id: m for m in mv}
    # it's three times your height: Unmake's reaching hand comes down to meet you, never over your head
    for f, pose in by["unmake"].stages["active"]:
        x = pose.get("arm_l", (0, 0, 0))
        pose["arm_l"] = (x[0] + 40, x[1], x[2])
        pose["chest"] = (pose.get("chest", (0, 0, 0))[0] + 12, pose.get("chest", (0, 0, 0))[1], 0)
    by["unmake"].ground = ARMS  # and stops at the floor
    # its arms already hang splayed: opened wide in the roars they come forward and up, never out flat in a T
    seen = set()
    for f, pose in by["dark"].stages["active"]:
        if id(pose) in seen:
            continue
        seen.add(id(pose))
        for sd, sg in (("l", 1), ("r", -1)):
            x = pose.get("arm_" + sd)
            if x:
                pose["arm_" + sd] = (x[0] - 20, x[1], x[2] + sg * 24)
    for m in mv:
        stage_clips(r, m)
    for m in mv:
        if m.advance == 0 and m.rise == 0:
            core.lock_feet(r, core.clip_name(m))
    for nm in ("idle", "parried", "reel", "hurt", "hurt_l", "hurt_r"):
        if nm in r.clips:
            core.lock_feet(r, nm)
    # the fists stay out of the floor (the Keystone, the Stamp and the Fall meet it and stop there)
    core.ground_pass(r, ARMS, clips=[n for n in r.clips if n != "death"])
    return mv


def pier_run():
    """Pier Run (HEAVY, a gap closer): it drops forward onto its fists and comes at you on all fours like a falling
    pier, shoulders low, the fists and forearms striking as they swing through."""
    dig = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -4, 2)}, "spine": (12, 0, 0), "chest": (14, 0, 0), "head": (-24, 0, 0),
           "arm_l": (-20, 0, -12), "arm_r": (-20, 0, 12), "forearm_l": (-40, 0, 0), "forearm_r": (-40, 0, 0),
           "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (10, 0, 0), "shin_r": (34, 0, 0)}
    run = {"pelvis": {"rot": (12, 0, 0), "pos": (0, -3, 0)}, "spine": (12, 0, 0), "chest": (14, 0, 0), "head": (-24, 0, 0),
           "arm_l": (-56, 0, -8), "arm_r": (24, 0, 8), "forearm_l": (-30, 0, 0), "forearm_r": (-50, 0, 0),
           "thigh_l": (-40, 0, 0), "shin_l": (40, 0, 0), "thigh_r": (30, 0, 0), "shin_r": (50, 0, 0)}
    run2 = dict(run, arm_l=(24, 0, -8), arm_r=(-56, 0, 8), forearm_l=(-50, 0, 0), forearm_r=(-30, 0, 0),
                thigh_l=(30, 0, 0), shin_l=(50, 0, 0), thigh_r=(-40, 0, 0), shin_r=(40, 0, 0))
    skid = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -3, -1)}, "spine": (8, 0, 0), "chest": (6, 0, 0), "head": (-6, 0, 0),
            "arm_l": (-40, 0, -20), "arm_r": (-40, 0, 20)}
    return Move("pier_run", "HEAVY", 16, 12, 18, title="Pier Run", track=4.0, lock=12, advance=7.0, rng=(6, 14), kinds=("gap",),
                damage=1.2, knock=1.2, plant=(),
                hit=[("hand_l", FIST, P.hand[0] / 2 + 1), ("hand_r", FIST, P.hand[0] / 2 + 1), ("forearm_l", (0, 22, 0), 9), ("forearm_r", (0, 22, 0), 9)],
                stages={"windup": [(0.4, {"pelvis": {"rot": (-6, 0, 0), "pos": (0, 0, 2)}, "chest": (-8, 0, 0)}), (1.0, dig)],
                        "active": [(0.25, run), (0.5, run2), (0.75, run), (1.0, run2)],
                        "recover": [(0.35, skid), (0.7, {"chest": (3, 0, 0)})]},
                lag={"head": 0.05, "arm_l": 0.03, "arm_r": 0.03}, ground=ARMS)


def the_fall():
    """The Fall (its signature, red): it rears up and back with its right fist high over its head, holds it there as its
    lamp flares, and drives it down into the bridge; the floor breaks up in a line toward you (dash through it)."""
    rear = {"pelvis": {"rot": (-8, -10, 0), "pos": (0, 1, 2)}, "spine": (-14, -8, 0), "chest": (-20, -16, 6), "head": (-12, 10, 0),
            "arm_r": (-176, 8, -10), "forearm_r": (-30, 0, 0), "arm_l": (-40, 0, -50), "forearm_l": (-30, 0, 0)}
    drive = {"pelvis": {"rot": (14, 6, 0), "pos": (0, -6, -1)}, "spine": (12, 6, 0), "chest": (22, 10, 0), "head": (0, -8, 0),
             "arm_r": (-40, 0, 4), "forearm_r": (-60, 0, 0), "arm_l": (30, 0, -40), "forearm_l": (-40, 0, 0),
             "thigh_l": (-34, 0, 0), "shin_l": (48, 0, 0), "thigh_r": (-20, 0, 0), "shin_r": (40, 0, 0), "foot_l": (-14, 0, 0)}
    return Move("the_fall", "PERILOUS", 24, 8, 22, title="The Fall", track=6.0, ability="The Fall", origin=(0, 0, 2.6), rng=(3, 16),
                kinds=("ranged", "area"), signature=True, damage=0, knock=0, plant=("l", "r"),
                stages={"windup": [(0.4, core._scaled(rear, 0.5)), (1.0, rear)], "active": [(0.3, drive), (1.0, drive)],
                        "recover": [(0.5, core._scaled(drive, 0.5))]},
                lag={"arm_r": 0.03, "forearm_r": 0.05, "head": 0.04, "hang": 0.06},
                ground=[(["hand_r"], [("forearm_r", 0, 50), ("arm_r", 0, 40)])])


def rise():
    """Its intro: down on one knee where the stones fell in, a fist on the bastion, it pushes itself up, straightens to
    its full height, opens its arms and the lamp in its chest flares."""
    push = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -6, 1)}, "spine": (14, 0, 0), "chest": (16, 0, 0), "head": (10, 0, 0),
            "arm_r": (-30, 0, 10), "forearm_r": (-20, 0, 0), "arm_l": (-20, 0, -14)}
    tall = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "spine": (-8, 0, 0), "chest": (-14, 0, 0), "head": (-20, 0, 0),
            "arm_l": (-60, 0, -36), "arm_r": (-60, 0, 36), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0)}
    return Move("rise", "NONE", 14, 18, 16, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, push)], "active": [(0.4, tall), (1.0, tall)], "recover": [(0.6, core._scaled(tall, 0.3))]},
                lag={"arm_l": 0.05, "arm_r": 0.05, "head": 0.06, "hang": 0.08})


def give():
    """The turn at two thirds: it lifts both fists and hammers the bastion, and the ring round it gives way."""
    up = {"pelvis": {"rot": (-6, 0, 0), "pos": (0, 1, 2)}, "spine": (-8, 0, 0), "chest": (-16, 0, 0), "head": (-14, 0, 0),
          "arm_l": (-172, 0, -8), "arm_r": (-172, 0, 8), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0)}
    down = {"pelvis": {"rot": (16, 0, 0), "pos": (0, -7, -1)}, "spine": (14, 0, 0), "chest": (28, 0, 0), "head": (10, 0, 0),
            "arm_l": (-50, 14, -4), "arm_r": (-50, -14, 4), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0),
            "thigh_l": (-34, 0, 0), "thigh_r": (-34, 0, 0), "shin_l": (48, 0, 0), "shin_r": (48, 0, 0), "foot_l": (-14, 0, 0), "foot_r": (-14, 0, 0)}
    return Move("give", "NONE", 16, 24, 14, title="The floor gives way", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, up)], "active": [(0.25, down), (0.5, core._scaled(down, 0.8)), (0.7, down), (1.0, down)],
                        "recover": [(0.5, core._scaled(down, 0.4))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.05, "hang": 0.06},
                ground=[(["hand_l", "hand_r"], [("forearm_l", 0, 50), ("forearm_r", 0, 50)])])


# Its sounds: stone and iron. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.stone.hit", 1.4, 0.6), ("block.chain.step", 1.0, 0.8)],
    "tell_heavy": [("block.deepslate_bricks.hit", 2.0, 0.5), ("block.anvil.place", 0.8, 0.4)],
    "tell_perilous": [("entity.ravager.roar", 1.8, 0.5), ("block.deepslate.break", 2.2, 0.4)],
    "tell_grab": [("block.chain.break", 1.6, 0.6), ("block.stone.break", 1.6, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.6, 0.6), ("block.stone.step", 1.4, 0.5)],
    "swing_heavy": [("entity.player.attack.sweep", 2.0, 0.45), ("block.deepslate.step", 2.0, 0.4)],
    "cast": [("block.stone.break", 2.0, 0.5)],
    "impact": [("block.deepslate_bricks.break", 2.4, 0.5), ("item.mace.smash_ground_heavy", 1.6, 0.6)],
    "roar": [("entity.ravager.roar", 2.6, 0.4), ("block.deepslate_bricks.break", 2.0, 0.4)],
    "step": [("block.deepslate_bricks.step", 1.4, 0.5), ("block.stone.place", 0.8, 0.5)],
}
MOVE_SOUNDS = {"rise": {"swing": "roar"}, "give": {"swing": "impact"}, "dark": {"swing": "roar"}, "rubble": {"swing": "roar"}}

FIGHT = {
    # a duel (SPEC §47.3): phrases of three and four, its long arms covering the ground round it
    "duel": True,
    "stance": 340,  # tuned with the sim (SPEC §47.7)
    "intro": {"pose": "kneel", "move": "rise"},
    "phases": [
        # on the whole bastion: the swats in twos and threes, the Keystone, the Stamp, Pier Run, the Fall
        {"pool": [
            {"move": "swat_l", "weight": 3, "then": [["swat_r"], ["swat_r", "keystone"]]},
            {"move": "swat_r", "weight": 2, "then": [["swat_l"], ["swat_l", "stomp"]]},
            {"move": "keystone", "weight": 2, "then": [["swat_l"]]},
            {"move": "stomp", "weight": 2, "when": "behind", "then": [["swat_r"]]},
            {"move": "pier_run", "weight": 2, "when": "far", "then": [["swat_l", "swat_r"]]},
            {"move": "the_fall", "weight": 1, "then": [["pier_run"]]},
        ]},
        # the floor gives way: the Sweep, phrases of three and four
        {"transition": "give", "pool": [
            {"move": "swat_l", "weight": 3, "then": [["swat_r", "keystone"], ["swat_r", "swat_l", "sweep"]]},
            {"move": "swat_r", "weight": 2, "then": [["swat_l", "sweep"], ["swat_l", "keystone"]]},
            {"move": "keystone", "weight": 2, "then": [["swat_l", "swat_r"]]},
            {"move": "sweep", "weight": 2, "then": [["swat_l"], ["keystone"]]},
            {"move": "stomp", "weight": 2, "when": "behind", "then": [["sweep"]]},
            {"move": "pier_run", "weight": 2, "when": "far", "then": [["swat_l", "swat_r", "keystone"]]},
            {"move": "the_fall", "weight": 2, "then": [["pier_run"], ["the_fall"]]},
        ]},
        # the lamps go out: Unmake, the Rubble, phrases of four
        {"transition": "dark", "pool": [
            {"move": "swat_l", "weight": 3, "then": [["swat_r", "swat_l", "sweep"], ["swat_r", "keystone", "unmake"]]},
            {"move": "swat_r", "weight": 2, "then": [["swat_l", "sweep", "keystone"]]},
            {"move": "sweep", "weight": 2, "then": [["swat_l", "swat_r"]]},
            {"move": "keystone", "weight": 1, "then": [["swat_l", "sweep"]]},
            {"move": "unmake", "weight": 2, "when": "far", "then": [["swat_l", "swat_r"]]},
            {"move": "stomp", "weight": 2, "when": "behind", "then": [["sweep", "swat_l"]]},
            {"move": "pier_run", "weight": 1, "when": "far", "then": [["swat_l", "swat_r", "keystone"]]},
            {"move": "the_fall", "weight": 2, "then": [["unmake"], ["pier_run", "swat_l"]]},
            {"move": "rubble", "weight": 1, "then": [["the_fall"]]},
        ]},
    ],
    "enrage": {"below": 0.12, "speed": 1.2},
}


def paint(r, P_):
    """Four materials in a value order: the lamps brightest (the one in its chest, the lantern on its shoulder, the slit
    of its head: warm lamplight), then the tuff of its bands, keystones and capstones, then the grey stone bricks of
    its courses and drums, coursed and cracked, then the dark iron of its cramps, binding and chains. Mortar lines run
    round every course; here and there a crack shows the same warm light from inside."""
    stone = Ramp("#2a2c30", "#3c3f44", "#53575d", "#6c7077", "#878b92", "#a4a8ae", "#c2c5ca")
    tuff = Ramp("#2c2e2a", "#3f423c", "#565a51", "#6e7367", "#888d80", "#a3a898")
    iron = Ramp("#0d0e10", "#17191c", "#22252a", "#30343a", "#444950")
    dark = Ramp("#050506", "#0a0a0c", "#121215", "#1b1b20")
    warm = ("#6a2a08", "#b8560f", "#f09a2a", "#ffd27a", "#fff2c8")
    of = lambda *ps: parts.cubes_of(r, *ps)

    def coursed(f, i, j):
        # stone bricks: a mortar line every four rows, the joints staggered course by course
        if j % 4 == 3:
            return -1.4
        if (i + (j // 4) * 4) % 8 == 7:
            return -1.1
        return 0.25 if (i * 7 + j * 3) % 11 == 0 else 0
    for c in of("pelvis", "spine", "course", "corbel_", "thigh_", "shin_", "foot_", "neck", "spring_"):
        P_.paint(c, stone, 3.8, coursed, grad=1.4, rim=0.8, noise=0.5, nscale=5)
    # the arms' drums a darker, weathered stone, so the arms read off the body
    for c in of("drum_", "fdrum_"):
        P_.paint(c, stone, 2.7, lambda f, i, j: (-1.3 if j % 6 == 5 else 0.3 if (i * 5 + j) % 9 == 0 else 0), grad=1.6, rim=1.0, noise=0.6, nscale=4)
    for c in of("band_", "keystone_p", "cap", "arch_", "key", "fist_", "float", "post", "bracket"):
        P_.paint(c, tuff, 3.0, lambda f, i, j: (-0.9 if j % 5 == 4 else 0.3 if (i + j) % 6 == 0 else 0), grad=1.2, rim=0.9, noise=0.4)
    for c in of("cramp_", "bind_", "chain_", "hook", "lantern_top"):
        P_.paint(c, iron, 2.6, lambda f, i, j: (1.2 if j == 0 else -0.4 if (i + j) % 3 == 0 else 0), grad=0.8, rim=1.0)
    P_.paint("niche", dark, 1.6, grad=0.4)
    # warm light: the niche lit from the lamp, the lamp, the lantern, the slit; cracks of it across the courses
    radial(P_, "niche", "north", ("#ffb347", "#c8661a", "#6a2a08", "#2a1206"), alpha=230)
    for c in ("lamp", "lantern"):
        for fname in SIDES + ("top", "bottom"):
            radial(P_, c, fname, (warm[4], warm[3], warm[2]), skin=Ramp(warm[1], warm[2], warm[3], warm[4]))
    f = P_.face("slit", "north")
    g = P_.face("slit", "north", glow=True)
    for i, j in f.each():
        f.set(i, j, hexc(warm[3]))
        g.set(i, j, hexc(warm[3], 220))
    for k, c in enumerate(("course1", "course2", "drum_l1", "fdrum_r0", "thigh_l", "corbel_r0")):
        cracks(P_, c, ("north",), hexc("#1a1208"), (warm[1], warm[2], warm[3]), n=1, seed=f"unmade{k}", alpha=170)
    # the keystone head's chevron, cut into its face over the slit
    f = P_.face("key2", "north")
    for i, j in f.each():
        if abs(i - (f.w - 1) / 2) == j + 1 and j < 3:
            f.set(i, j, tuff.at(0.4))
    edge_glow(P_, "arch_t", "north", warm[1], rows=1, alpha=120, side="bottom")
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 1.2, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
