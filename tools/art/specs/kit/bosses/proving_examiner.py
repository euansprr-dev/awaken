"""The Examiner (SPEC §46.6, docs/fights/PLAN.md §4.7): the second Proving's Warden, met when the room you're standing in
turns into the Hall of Reflections. The Registry's examiner as the Signal made it: tall and straight-backed in a long
clerk's coat buttoned to the throat under a high collar, a flat examiner's cap; where its face should be, an oval mirror
in a brass rim, so the only face it has is yours. It carries the Registry's measuring rod, a long staff of black glass
ruled in gold, and behind its shoulders a fan of mirror shards stands like a ruff, turning a little as it moves. Its
left hand is bare, long and pale, and its fingertips light up in your own power's colour when it throws it back.
Bold and simple (Euan, 2026-10-29: no sculpted faces, epic and mysterious).

It steps out of a mirror (its intro) and fights as a duel that reads you: through its first phase it measures how you
meet it, with sweeps and thrusts of the rod in threes, the Measure (the rod brought down overhead, held), a step-in
thrust from afar, the Reading (a shot of your power from its palm) and the Verdict (its signature: the rod driven into
the floor and a line of mirror-glass breaking up toward you, red). When the mirrors wake its copies step out of them;
when the walls close in it reaches to hold you still (a red grab) and cuts right round with the rod. It dies on its
knees, the mirror face going dark.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_examiner"
SCALE = 2.1  # on the player-sized HUMAN model: about 4.2 blocks to the top of its cap

P = plans.Biped(leg=30, thigh=15, foot=(3, 2, 7), pelvis=(8, 4, 5), spine=(6, 5, 4), chest=(12, 11, 7), neck=(3, 3, 3),
                head=(7, 8, 7), arm=(3, 13, 3), forearm=(3, 12, 3), hand=(3, 3, 3), stance=3.0, hunch=0, leg_w=(3, 4))
# the rod's tip, in the rod bone's space (the rod runs down the bone's +y out of the fist)
ROD_TIP = (0, 26, 0)
ROD = dict(weapon=ROD_TIP, weapon_r=1.6)
# how far the rod comes down in its strokes (degrees on its own axis), and how low it bends into them, so every stroke
# meets someone standing at the waist (SPEC §46.6: every damaging blow's lowest point under 1.2 blocks)
ROD_DOWN = {"rule_a": 90, "rule_c": 90, "turn": 100, "crosscut": 100}
LOW_C, LOW_SPIN, DROP_SPIN = 20, 15, 0
# the fan of shards behind its shoulders: (angle from straight up, length, width)
SHARDS = [(-62, 9, 3), (-40, 12, 3), (-18, 14, 4), (0, 15, 4), (18, 14, 4), (40, 12, 3), (62, 9, 3)]


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4, -1, -2.5, 8, 4, 5)
    pel.cube("belt", -4.5, -1.5, -3, 9, 2, 6)
    pel.cube("buckle", -1, -1.5, -3.5, 2, 2, 1)
    # the coat's skirts: front and back to mid-shin, each three lengths on springs, parting round the legs
    for nm, z, tilt in (("front", -3.0, 3), ("back", 2.8, -5)):
        cl = pel.bone(nm + "0", (0, 1, z), (tilt, 0, 0))
        for k, (w, ln) in enumerate(((8, 7), (9, 7), (9, 6))):
            if k:
                cl = cl.bone(f"{nm}{k}", (0, 7, 0), (tilt * 0.4, 0, 0))
            cl.cube(f"{nm}{k}", -w / 2, 0, -0.5, w, ln, 1)
        cl.cube(nm + "_hem", -4.5, 5, -1, 9, 1, 2)
    for side, nm in ((1, "l"), (-1, "r")):
        sk = pel.bone("skirt_" + nm, (side * 4.2, 1, 0), (0, 0, -side * 5))
        sk.cube("skirt_" + nm + "0", -0.5, 0, -2.5, 1, 10, 5)
        sk2 = sk.bone("skirt_" + nm + "1", (0, 10, 0), (0, 0, -side * 2))
        sk2.cube("skirt_" + nm + "1", -0.5, 0, -2.5, 1, 9, 5)
    # legs: long and straight in dark trousers, tall black boots with a brass toe
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 16, 3)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -1.5, 0, -1.5, 3, P.leg - P.thigh - 2, 3)
        sn.cube("boot_" + nm, -2, 5, -2, 4, P.leg - P.thigh - 7, 4)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -1.5, 0, -5, 3, 2, 7)
        ft.cube("toe_" + nm, -1.5, 0, -5.5, 3, 2, 1)
    # a straight, narrow torso: the coat buttoned to the throat, a sash across it with the Registry's seal
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -3, -5, -2, 6, 5, 4)
    ch = sp.bone("chest", (0, -5, 0))
    parts.taper(ch, "chest", 0, 0, 0, ((10, 3, 6), (12, 4, 7), (12, 4, 7)))
    for k in range(4):
        ch.cube(f"button{k}", -0.5, -2 - k * 2.5, -4, 1, 1, 1)
    ch.cube("sash", -6, -9, -4, 2, 9, 1)
    seal = ch.bone("seal", (-4.5, -6, -4.2))
    seal.cube("seal", -1.5, -1.5, -0.5, 3, 3, 1)
    # the collar: high and stiff, open only at the front
    ch.cube("collar_back", -3.5, -14, 1.5, 7, 4, 1)
    ch.cube("collar_l", 2.5, -14, -2.5, 1, 4, 4)
    ch.cube("collar_r", -3.5, -14, -2.5, 1, 4, 4)
    # the fan of mirror shards standing behind the shoulders, each on its own bone so it can turn and sway
    fan = ch.bone("fan", (0, -11, 3.6))
    fan.cube("fan_hub", -2, -2, -0.5, 4, 4, 1)
    for k, (ang, ln, w) in enumerate(SHARDS):
        sh_ = fan.bone(f"shard{k}", (0, 0, 0.3), (0, 0, ang))
        sh_.cube(f"shard{k}", -w / 2, -ln - 2, -0.5, w, ln, 1)
        sh_.cube(f"shard_tip{k}", -(w // 2) / 2, -ln - 3, -0.5, w // 2, 1, 1)
    # arms: long in close sleeves with gold cuffs; the right holds the rod, the left is bare and pale
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 6.0, -10, 0))
        sh.cube("shoulder_" + nm, -2, -1.5, -2, 4, 3, 4)
        sh.cube("epaulet_" + nm, -2.5, -2, -2.5, 5, 1, 5)
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-4, 0, -side * 5))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 13, 3)
        fa = arm.bone("forearm_" + nm, (0, 12, 0), (-10, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 12, 3)
        fa.cube("cuff_" + nm, -2, 8, -2, 4, 2, 4)
        hd_ = fa.bone("hand_" + nm, (0, 11, 0))
        hd_.cube("hand_" + nm, -1.5, 0, -1.5, 3, 3, 3)
    # the left hand's long fingers, lit at the tips when it casts
    fl = r.by_name["hand_l"]
    fl.cube("fingers_l", -1.5, 3, -1, 3, 3, 2)
    # the rod: black glass ruled in gold, brass-capped, a cut-glass point at its working end
    # it rests pointed out ahead of it and down, at whoever is being measured (its +y runs forward-down out of the fist)
    rod = r.by_name["hand_r"].bone("rod", (0, 1.5, 0), (-60, 0, 0))
    rod.cube("rod", -1, -12, -1, 2, 37, 2)
    rod.cube("cap_top", -1.5, -14, -1.5, 3, 2, 3)
    for k in range(3):
        rod.cube(f"rule{k}", -1.5, -4 + k * 9, -1.5, 3, 1, 3)
    rod.cube("rod_collar", -1.5, 23, -1.5, 3, 1, 3)
    parts.taper(rod, "rod_point", 0, 24, 0, ((2, 2, 2), (1, 2, 1)), up=False)
    # the head: the cap, and an oval mirror in a brass rim where the face should be
    nk = ch.bone("neck", (0, -12, -0.5))
    nk.cube("neck", -1.5, -3, -1.5, 3, 3, 3)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("head", -3.5, -8, -3, 7, 8, 7)
    hd.cube("cap", -4, -11, -3.5, 8, 3, 8)
    hd.cube("cap_band", -4.5, -8.5, -4, 9, 1, 9)
    hd.cube("visor", -3.5, -8, -5, 7, 1, 2)
    fc = hd.bone("face", (0, -4, -3.2))
    fc.cube("rim_top", -2.5, -3.5, -0.6, 5, 1, 1)
    fc.cube("rim_bottom", -2.5, 2.5, -0.6, 5, 1, 1)
    fc.cube("rim_l", 2.5, -2.5, -0.6, 1, 5, 1)
    fc.cube("rim_r", -3.5, -2.5, -0.6, 1, 5, 1)
    fc.cube("mirror", -2.5, -2.5, -0.5, 5, 5, 1)
    r.look = [("head", 0.6, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.8
    # what hangs swings (SPEC §35.10): the coat's skirts and the shards (stiff glass: a little, slow)
    r.springs = [(f"{nm}{k}", g, hz, 0.3) for nm in ("front", "back") for k, (g, hz) in enumerate(((0.5, 2.6), (0.4, 2.2), (0.3, 2.0)))] \
        + [(f"skirt_{s}{k}", 0.35, 2.4, 0.3) for s in "lr" for k in "01"] \
        + [(f"shard{k}", 0.08, 4.0, 0.5) for k in range(len(SHARDS))] + [("seal", 0.3, 3.0, 0.3)]
    r.guard = ["rod"]  # the rod never goes into the floor but where a move strikes it
    return r


SWING = [(["rod"], [("hand_r", 0, 60), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]
CLOTH = [(["front2", "front_hem"], [("front0", 0, 70), ("front1", 0, 60)]), (["back2", "back_hem"], [("back0", 0, 70), ("back1", 0, 60)]),
         (["skirt_l1"], [("skirt_l0", 0, 50)]), (["skirt_r1"], [("skirt_r0", 0, 50)])]


def clips(r):
    plans.biped_locomotion(r, P, weight=1.0, temper="regal")
    plans.biped_reactions(r, weight=1.0)
    plans.biped_duel_reactions(r, weight=1.0, lead="r")
    # standing, it holds the rod out at you, its point barely moving, and the shards turn a little
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("rod", [(0, 0, 0, 0), (L * 0.5, -2, 0, 1), (L, 0, 0, 0)])
    idle.rot("fan", [(0, 0, 0, 0), (L * 0.5, 0, 0, 3), (L, 0, 0, 0)])
    # the death: it kneels, the rod falls from its hand, its head bows until the mirror faces the floor
    kneel = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -14, 1)}, "thigh_l": (-86, 0, 0), "shin_l": (88, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (-86, 0, 0), "shin_r": (88, 0, 0), "foot_r": (-2, 0, 0), "spine": (10, 0, 0), "chest": (14, 0, 0),
             "head": (30, 0, 0), "arm_l": (4, 0, -6), "arm_r": (4, 0, 6), "forearm_l": (-4, 0, 0), "forearm_r": (-4, 0, 0), "rod": (40, 0, 0)}
    bowed = dict(kneel, chest=(26, 0, 0), head=(50, 0, 0), spine=(16, 0, 0), fan=(20, 0, 0))
    keyed(r, "death", 2.4, [(0, {}), (0.3, {"chest": (-12, 0, 0), "head": (-24, 0, 0), "arm_l": (-30, 0, -30), "arm_r": (-30, 0, 30)}),
                            (1.0, kneel), (1.7, bowed), (2.4, bowed)])
    mv = [
        # the rod in threes: a sweep, a thrust, a backstroke; the Measure overhead (held); the step-in thrust from afar
        M.sweep(P, "rule_a", side="r", title="Rule Sweep", windup=14, active=4, recover=12, weapon_bone="rod", rng=(0, 5.2), damage=1.0,
                kinds=("combo",), **ROD),
        thrust(),
        M.backhand(P, "rule_c", side="r", title="Backstroke", windup=14, active=4, recover=12, kinds=("combo",), rng=(0, 4.4), damage=0.9,
                   low=LOW_C, weapon_bone="rod", **ROD),
        M.overhead_slam(P, "measure", title="The Measure", windup=20, hold=10, active=5, recover=20, ability="The Measure", rng=(0, 5.0),
                        damage=1.3),
        M.lunge_cut(P, "step", side="r", title="Step In", windup=14, active=5, recover=14, advance=3.5, kinds=("gap",), rng=(4.5, 10),
                    damage=1.0, weapon=ROD_TIP),
        M.palm_cast(P, "reading", side="l", title="Reading", windup=14, active=6, recover=14, cls="LIGHT", ability="Reading", rng=(5, 18)),
        M.spin_cut(P, "turn", side="r", title="Turning Rule", windup=14, active=6, recover=12, rng=(0, 4.6), damage=0.9, weapon_bone="rod",
                   low=LOW_SPIN, drop=DROP_SPIN, **ROD),
        verdict(),
        # when the mirrors wake: its copies step out of them; when the walls close in, Hold Still and the Crosscut
        copies(),
        M.lunge_cut(P, "hold", side="l", title="Hold Still", cls="GRAB", windup=18, active=6, recover=18, advance=4.0, kinds=("gap",),
                    rng=(3, 10), damage=1.2),
        M.spin_cut(P, "crosscut", side="r", title="Crosscut", cls="HEAVY", windup=18, active=10, recover=16, kinds=("area",), rng=(0, 5.0),
                   damage=1.2, weapon_bone="rod", low=LOW_SPIN, drop=DROP_SPIN, **ROD),
        emerge(),
        wake(),
        close(),
    ]
    by = {m.id: m for m in mv}
    # it's twice your height: in its strokes the rod comes down to meet you at the waist, never over your head
    for mid, add in ROD_DOWN.items():
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            rx = pose.get("rod", (0, 0, 0))
            pose["rod"] = (rx[0] + add, rx[1], rx[2])
    # the Crosscut goes once right round it, the rod held out flat
    for k, (f, pose) in enumerate(by["crosscut"].stages["active"]):
        rot = pose.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
        pose["pelvis"] = {"rot": (rot["rot"][0], rot["rot"][1] + 360 * f, rot["rot"][2]), "pos": rot.get("pos", (0, 0, 0))}
    # in the strokes the bare left hand stays back at its hip, ready to cast
    for mid in ("rule_a", "rule_b", "rule_c", "step", "turn"):
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.setdefault("arm_l", (-16, 0, -18))
            pose.setdefault("forearm_l", (-40, 0, 0))
    for m in mv:
        stage_clips(r, m)
    # the Crosscut comes out of its turn facing where it began: the recovery eases on to 360, never back round
    yaw = r.clips["move_crosscut"].tracks[("pelvis", "rot")]
    top = max(range(len(yaw)), key=lambda k: yaw[k][1][1])
    t0, t1 = yaw[top][0], yaw[-1][0]
    for k in range(top + 1, len(yaw)):
        t, (x, y, z), *rest = yaw[k]
        u = (t - t0) / max(1e-6, t1 - t0)
        ease = u * u * (3 - 2 * u)
        yaw[k] = (t, (x, 360 + (yaw[top][1][1] - 360) * (1 - ease), z), *rest)
    for m in mv:
        if m.advance == 0 and m.rise == 0:
            core.lock_feet(r, core.clip_name(m))
    for nm in ("idle", "parried", "reel", "hurt", "hurt_l", "hurt_r"):
        if nm in r.clips:
            core.lock_feet(r, nm)
    # the rod stays out of the floor (the Measure and the Verdict meet it and stop there); the coat lifts off it
    core.ground_pass(r, SWING, clips=[n for n in r.clips if n not in ("death", "kneel", "rise")])
    core.ground_pass(r, CLOTH, clips=list(r.clips), skip=())
    return mv


def thrust():
    """Rule Thrust (LIGHT): it sets its feet and drives the rod's point straight out at chest height, the left hand
    sliding along the shaft behind it."""
    set_ = {"pelvis": {"rot": (4, -18, 0), "pos": (0, -2, 2)}, "chest": (-4, -26, 0), "spine": (0, -8, 0), "head": (0, 22, 0),
            "arm_r": (-30, 20, 30), "forearm_r": (-80, 0, 0), "rod": (60, 0, 0), "arm_l": (-60, -10, 10), "forearm_l": (-50, 0, 0),
            "thigh_l": (-20, 0, 0), "shin_l": (24, 0, 0)}
    drive = {"pelvis": {"rot": (10, 16, 0), "pos": (0, -3, -2)}, "chest": (8, 20, 0), "spine": (4, 8, 0), "head": (0, -14, 0),
             "arm_r": (-88, -6, 0), "forearm_r": (-4, 0, 0), "rod": (60, 0, 0), "arm_l": (-70, -20, -10), "forearm_l": (-20, 0, 0),
             "thigh_l": (-34, 0, 0), "shin_l": (30, 0, 0)}
    return Move("rule_b", "LIGHT", 14, 4, 12, title="Rule Thrust", track=10.0, rng=(0, 5.6), kinds=("combo",), damage=1.0, knock=0.6,
                advance=0.8, hit=[("rod", ROD_TIP, 1.6), ("rod", (0, 16, 0), 1.2)],
                stages={"windup": [(1.0, set_)], "active": [(0.5, drive), (1.0, drive)],
                        "recover": [(0.5, core._scaled(drive, 0.4))]},
                lag={"arm_r": 0.02, "forearm_r": 0.04, "fan": 0.06})


def verdict():
    """The Verdict (its signature, red): it lifts the rod high in both hands, point down, holds it there a moment as the
    mirror face flares, and drives it into the floor; a line of mirror-glass breaks up out of the floor toward you (dash
    through it)."""
    lift = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-14, 0, 0),
            "arm_r": (-170, 0, 12), "forearm_r": (-10, 0, 0), "arm_l": (-170, 0, -12), "forearm_l": (-10, 0, 0), "rod": (-150, 0, 0)}
    drive = {"pelvis": {"rot": (14, 0, 0), "pos": (0, -7, -1)}, "spine": (12, 0, 0), "chest": (24, 0, 0), "head": (8, 0, 0),
             "arm_r": (-70, 10, 0), "forearm_r": (-6, 0, 0), "arm_l": (-70, -10, 0), "forearm_l": (-6, 0, 0), "rod": (116, 0, 0),
             "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (44, 0, 0), "foot_l": (-12, 0, 0),
             "foot_r": (-12, 0, 0)}
    return Move("verdict", "PERILOUS", 24, 8, 20, title="Verdict", track=6.0, ability="Verdict", origin=(0, 0, 2.0), rng=(3, 14),
                kinds=("ranged", "area"), signature=True, damage=0, knock=0, plant=("l", "r"),
                stages={"windup": [(0.4, core._scaled(lift, 0.5)), (1.0, lift)], "active": [(0.3, drive), (1.0, drive)],
                        "recover": [(0.5, core._scaled(drive, 0.5))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "fan": 0.05, "head": 0.04})


def copies():
    """Reflections (when the mirrors wake): it touches the mirror of its face with two fingers and turns its palm out to
    the hall; copies of it step out of the mirrors."""
    touch = {"arm_l": (-130, -30, -20), "forearm_l": (-90, 0, 0), "head": (6, 0, 0), "chest": (4, 0, 0)}
    out = {"arm_l": (-80, 0, -60), "forearm_l": (-10, 0, 0), "head": (-6, -10, 0), "chest": (-6, -6, 0),
           "pelvis": {"rot": (0, -6, 0), "pos": (0, 0, 0)}}
    return Move("copies", "NONE", 12, 10, 12, title="Reflections", track=0.0, ability="Reflections", rng=(0, 30), kinds=("intro",),
                damage=0, knock=0, stages={"windup": [(1.0, touch)], "active": [(0.4, out), (1.0, out)], "recover": [(0.5, core._scaled(out, 0.3))]},
                lag={"arm_l": 0.04, "fan": 0.05})


def emerge():
    """Its intro: it stands still as a reflection, then steps out of the glass, setting the rod down beside it with a
    knock that rings through the hall, and lifts its face to you."""
    still = {"head": (12, 0, 0), "arm_r": (-10, 0, 6), "rod": (40, 0, 0), "chest": (4, 0, 0)}
    step = {"pelvis": {"rot": (4, 0, 0), "pos": (0, -1.5, -2)}, "thigh_l": (-26, 0, 0), "shin_l": (20, 0, 0), "head": (4, 0, 0),
            "arm_r": (-24, 0, 6), "rod": (20, 0, 0)}
    knock = {"pelvis": {"rot": (0, 0, 0), "pos": (0, -1, 0)}, "head": (-10, 0, 0), "arm_r": (-16, 0, 10), "rod": (0, 0, 0)}
    return Move("emerge", "NONE", 10, 14, 16, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=("r",),
                stages={"windup": [(1.0, still)], "active": [(0.5, step), (1.0, knock)], "recover": [(0.6, core._scaled(knock, 0.3))]},
                lag={"fan": 0.06, "head": 0.05})


def wake():
    """The turn at two-thirds: it holds the rod across its body and the shards behind it open wide; the mirrors round the
    hall wake."""
    hold = {"arm_r": (-70, -30, 0), "forearm_r": (-30, 0, 0), "arm_l": (-70, 30, 0), "forearm_l": (-30, 0, 0), "rod": (60, 0, -80),
            "chest": (-6, 0, 0), "head": (-10, 0, 0), "fan": (-10, 0, 0)}
    open_ = dict(hold, chest=(-12, 0, 0), head=(-20, 0, 0), fan=(-24, 0, 0))
    return Move("wake", "NONE", 10, 30, 12, title="The mirrors wake", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, hold)], "active": [(0.3, open_), (1.0, open_)], "recover": [(0.5, core._scaled(hold, 0.3))]},
                lag={"fan": 0.04})


def close():
    """The turn at one-third: it raises the rod overhead in one hand and brings it slowly down to point at you; the walls
    close in."""
    up = {"arm_r": (-170, 0, 10), "forearm_r": (-6, 0, 0), "rod": (60, 0, 0), "chest": (-10, 0, 0), "head": (-16, 0, 0)}
    point = {"arm_r": (-90, 0, 0), "forearm_r": (0, 0, 0), "rod": (60, 0, 0), "chest": (4, 0, 0), "head": (2, 0, 0),
             "pelvis": {"rot": (2, 0, 0), "pos": (0, -1, 0)}}
    return Move("close", "NONE", 12, 30, 12, title="The walls close in", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, up)], "active": [(0.5, point), (1.0, point)], "recover": [(0.5, core._scaled(point, 0.4))]},
                lag={"fan": 0.05, "arm_r": 0.03})


# Its sounds: glass and brass. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.amethyst_block.chime", 1.2, 1.4), ("block.glass.step", 1.0, 0.8)],
    "tell_heavy": [("block.amethyst_block.resonate", 1.6, 0.6), ("block.copper.place", 1.2, 0.6)],
    "tell_perilous": [("block.amethyst_block.resonate", 2.2, 0.45), ("entity.illusioner.prepare_mirror", 1.6, 0.6)],
    "tell_grab": [("entity.illusioner.prepare_blindness", 1.6, 0.7), ("block.glass.hit", 1.4, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.9), ("block.amethyst_cluster.step", 1.4, 1.2)],
    "swing_heavy": [("entity.player.attack.sweep", 1.8, 0.6), ("block.amethyst_block.hit", 1.6, 0.6)],
    "cast": [("entity.illusioner.cast_spell", 1.6, 0.8)],
    "impact": [("block.glass.break", 2.2, 0.6), ("block.amethyst_block.break", 1.8, 0.5)],
    "roar": [("entity.illusioner.mirror_move", 2.4, 0.5), ("block.amethyst_block.resonate", 2.0, 0.5)],
    "step": [("block.glass.step", 0.6, 0.8), ("block.copper.step", 0.5, 1.0)],
}
MOVE_SOUNDS = {"wake": {"swing": "roar"}, "close": {"swing": "roar"}, "copies": {"swing": "cast"}}

FIGHT = {
    # a duel (SPEC §46.3) that reads you (§46.6): through its first phase it counts how you meet it, and from its second
    # what answers your habit comes up more; its shots wear your most-used power's look
    "duel": True,
    "mirrors": True,
    "stance": 300,  # tuned with the sim (SPEC §46.6)
    "intro": {"pose": "idle", "move": "emerge"},
    "phases": [
        # it measures you: the rod in twos and threes, the Measure, the step-in, the Reading, the Verdict
        {"pool": [
            {"move": "rule_a", "weight": 3, "then": [["rule_b"], ["rule_b", "rule_c"]]},
            {"move": "rule_b", "weight": 2, "then": [["rule_a"], ["rule_c", "measure"]]},
            {"move": "measure", "weight": 2, "then": [["rule_a"]]},
            {"move": "step", "weight": 2, "when": "far", "then": [["rule_a", "rule_b"]]},
            {"move": "reading", "weight": 2, "when": "far", "then": [["reading"], ["step"]]},
            {"move": "turn", "weight": 2, "when": "behind", "then": [["rule_a"]]},
            {"move": "verdict", "weight": 1, "then": [["step"]]},
        ]},
        # the mirrors wake: its copies, phrases of three and four
        {"transition": "wake", "pool": [
            {"move": "rule_a", "weight": 3, "then": [["rule_b", "rule_c"], ["rule_b", "rule_c", "measure"]]},
            {"move": "rule_b", "weight": 2, "then": [["rule_a", "rule_c"], ["rule_c", "measure"]]},
            {"move": "measure", "weight": 2, "then": [["rule_a", "rule_b"]]},
            {"move": "step", "weight": 2, "when": "far", "then": [["rule_a", "rule_b", "rule_c"]]},
            {"move": "reading", "weight": 2, "when": "far", "then": [["reading", "step"], ["reading", "verdict"]]},
            {"move": "turn", "weight": 2, "when": "behind", "then": [["rule_a", "rule_b"]]},
            {"move": "verdict", "weight": 2, "then": [["step"], ["reading"]]},
            {"move": "copies", "weight": 1},
        ]},
        # the walls close in: Hold Still, the Crosscut, phrases of four
        {"transition": "close", "pool": [
            {"move": "rule_a", "weight": 3, "then": [["rule_b", "rule_c", "crosscut"], ["rule_b", "rule_c", "measure"]]},
            {"move": "rule_b", "weight": 2, "then": [["rule_a", "rule_c", "crosscut"]]},
            {"move": "crosscut", "weight": 2, "then": [["rule_a", "rule_b"]]},
            {"move": "measure", "weight": 1, "then": [["rule_a", "crosscut"]]},
            {"move": "hold", "weight": 2, "when": "far", "then": [["rule_a", "rule_b"]]},
            {"move": "step", "weight": 1, "when": "far", "then": [["rule_a", "rule_b", "rule_c"]]},
            {"move": "reading", "weight": 2, "when": "far", "then": [["reading", "hold"], ["verdict"]]},
            {"move": "turn", "weight": 2, "when": "behind", "then": [["rule_a", "crosscut"]]},
            {"move": "verdict", "weight": 2, "then": [["hold"], ["step", "rule_a"]]},
            {"move": "copies", "weight": 1},
        ]},
    ],
    "enrage": {"below": 0.12, "speed": 1.2},
}


def paint(r, P_):
    """Four materials in a value order: the mirror brightest (a pale silvered oval that shows a horizon: the face it
    has is yours), then gold (the rule marks, the buttons, the cuffs, the rims), then the shards' cold glass, then the
    coat (a deep Registry blue going black at the hem) and the black glass rod, the darkest thing on it. Its left hand
    is porcelain, its fingertips lit."""
    coat = Ramp("#06080f", "#0b0f1c", "#111830", "#18223f", "#22305a", "#2e4070")
    trouser = Ramp("#07080b", "#0d0f14", "#14171e", "#1c2029")
    gold = Ramp("#3a2a10", "#5c4318", "#8a6624", "#b88d36", "#dcb85a", "#f2dc94")
    glass = Ramp("#1a2230", "#2a3648", "#3d4d63", "#5a6e86", "#8299b3", "#b8cbe0")
    porcelain = Ramp("#6f6a66", "#8e8984", "#aea8a1", "#cdc7bf", "#e6e0d8")
    black = Ramp("#020203", "#06070a", "#0c0e14", "#151923", "#212736")
    light = ("#3a7fd0", "#6aa8ff", "#a8ccff", "#e4f0ff")
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in of("front", "back", "skirt_", "chest", "spine", "pelvis", "arm_", "forearm_", "shoulder_", "collar_"):
        P_.paint(c, coat, 3.0, lambda f, i, j: (-0.7 if i % 4 == 1 else 0.25 if j % 6 == 0 else 0), grad=1.6, noise=0.35, nscale=6)
    for c in of("thigh_", "shin_"):
        P_.paint(c, trouser, 2.2, grad=1.0, noise=0.3)
    for c in of("boot_", "foot_", "belt", "neck", "head", "cap", "visor"):
        P_.paint(c, black, 2.4, lambda f, i, j: (0.8 if j == 0 else 0), grad=1.2, rim=0.6, noise=0.2)
    for c in of("button", "cuff_", "epaulet_", "buckle", "cap_band", "toe_", "rim_", "cap_top", "rule", "rod_collar", "seal", "fan_hub"):
        P_.paint(c, gold, 4.0, lambda f, i, j: (-0.8 if (i + j) % 3 == 0 else 0.3), grad=1.4, rim=1.0, noise=0.25, nscale=3)
    for c in of("sash"):
        P_.paint(c, Ramp("#3a0e12", "#5a161b", "#7a2127", "#9a3036"), 2.4, lambda f, i, j: (0.6 if j % 3 == 0 else 0), grad=1.2)
    # the rod: black glass with a cold sheen down one edge; the point cut glass
    for c in ("rod",):
        P_.paint(c, black, 2.8, lambda f, i, j: (1.6 if i == 0 else 0), grad=0.6, rim=0.8)
        f = P_.face(c, "north")
        for i, j in f.each():
            if j % 3 == 0 and i == f.w - 1:
                f.set(i, j, hexc("#dcb85a", 255))
    for c in of("rod_point"):
        P_.paint(c, glass, 4.0, lambda f, i, j: (1.0 if (i + j) % 2 == 0 else -0.4), rim=1.2)
    # the shards: cold glass with a bright edge, a crack across some
    for c in of("shard"):
        P_.paint(c, glass, 4.2, lambda f, i, j: (1.4 if i == 0 or i == f.w - 1 else -0.2 if (i + j) % 4 == 0 else 0.3), grad=1.8, noise=0.2)
    for k in (1, 3, 5):
        cracks(P_, f"shard{k}", ("north", "south"), glass.at(0), (light[1], light[2], light[3]), n=1, seed=f"shard{k}", alpha=120)
    for c in of("hand_", "fingers_"):
        P_.paint(c, porcelain, 2.4, lambda f, i, j: (0.5 if (i + 2 * j) % 5 == 0 else 0), grad=1.0, noise=0.2)
    # the fingertips lit (the colour it throws back is the shot's own; here a cold white-blue)
    for fname in ("bottom",) + SIDES:
        g = P_.face("fingers_l", fname, glow=True)
        for i, j in g.each():
            if j >= g.h - 1:
                g.set(i, j, hexc(light[2], 210))
    # the mirror: silvered, a soft horizon across it (yours), a highlight in the top corner
    sky = ("#dfe8f2", "#c9d6e4", "#b2c3d6", "#9fb2c7")
    ground = ("#59606a", "#4a5059", "#3c4149")
    P_.paint("mirror", glass, 1.0)
    with P_.eyes():
        for fname in ("north",):
            f = P_.face("mirror", fname)
            g = P_.face("mirror", fname, glow=True)
            for i, j in f.each():
                h = j / max(1, f.h - 1)
                col = sky[min(3, int(h * 6))] if h < 0.58 else ground[min(2, int((h - 0.58) * 7))]
                if (i, j) in ((1, 1), (2, 1), (1, 2)):
                    col = "#ffffff"
                f.set(i, j, hexc(col, 255))
                g.set(i, j, hexc(col, 120))
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
