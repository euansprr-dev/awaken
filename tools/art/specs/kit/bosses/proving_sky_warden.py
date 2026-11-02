"""The Sky Warden (SPEC §46.8, docs/fights/PLAN.md §4.7): the fourth Proving's Warden, met in the cathedral above the
clouds. The cathedral's own knight: tall, in white plate edged with gold, a white tabard with a gold sun on it falling
to its knees, a closed great helm with a cross cut in the visor and light in the cross, a ring of gold standing behind
its head like a halo. Its wings are the cathedral's windows: two great fans of stained-glass panes, blue and violet and
gold in lead, on arms of gold, held half open and high behind it. It fights with a long spear whose blade is light.
Bold and simple (Euan, 2026-10-29: no sculpted faces, epic and mysterious).

It comes down out of the vault in a column of light and rises from one knee, its wings opening (its intro), and fights
as a duel: the spear in fours and fives (a thrust, a sweep of the haft, a rising cut), Descent (the spear lifted in
both hands overhead as the wings rise, driven down: a wave), the Wingbeat (one great downstroke that throws you back),
the Halo Turn for whoever is behind it, the Lance of Light from its open hand, and the Stoop (its signature: it
crouches, its wings flare, and it hurls itself at you along the floor, red). When the aisles fall it drives the spear
into the nave; when the roof lifts it raises the spear to the sky, and then it reaches to take you up (Ascension, a red
grab) and calls the glass down. It dies on its knees, the wings folding down round it and the halo going dark.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_sky_warden"
SCALE = 2.4  # on the player-sized HUMAN model: about 4.8 blocks to the top of its helm, the wings higher

P = plans.Biped(leg=30, thigh=15, foot=(4, 2, 7), pelvis=(9, 4, 5), spine=(7, 5, 4), chest=(13, 11, 7), neck=(3, 3, 3),
                head=(8, 9, 8), arm=(4, 13, 4), forearm=(3, 12, 3), hand=(3, 3, 3), stance=3.2, hunch=0, leg_w=(4, 4))
# the spear's point, in the spear bone's space (the haft runs down the bone's +y out of the fist)
SPEAR_TIP = (0, 37, 0)
SPEAR = dict(weapon=SPEAR_TIP, weapon_r=1.8)
# how far the spear comes down in its strokes (degrees on its own axis), so every stroke meets someone standing at the
# waist (SPEC §46.6: a damaging blow's hits come down under a standing player's shoulders)
SPEAR_DOWN = {"sweep": 95, "rising": 25, "halo": 100}
# the reaching hand comes down to you too (degrees)
ARM_DOWN = {"ascension": ("arm_l", 22), "sweep": ("arm_r", 24)}
LOW_RISE, LOW_HALO, DROP_HALO = 26, 18, 0
# each wing's panes, inner to outer: (on which arm, along it, length, width, droop in degrees)
PANES = [(0, 4, 9, 4, 34), (0, 9, 11, 4, 22), (0, 14, 13, 4, 10), (1, 3, 15, 4, 2), (1, 7, 17, 4, -8), (1, 11, 18, 3, -18)]
# their glass, inner to outer
PANE_GLASS = ["blue", "violet", "sky", "gold", "sky", "blue"]


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4.5, -1, -2.5, 9, 4, 5)
    pel.cube("belt", -5, -1.5, -3, 10, 2, 6)
    pel.cube("buckle", -1.5, -1.5, -3.5, 3, 2, 1)
    # the faulds: plates over the hips, left and right
    for side, nm in ((1, "l"), (-1, "r")):
        pel.cube("fauld_" + nm, (0 if side > 0 else -5) + side * 0.5, 0.5, -3, 5, 4, 6)
    # the tabard: front and back to the knee, each three lengths on springs, a gold hem
    for nm, z, tilt in (("front", -3.2, 3), ("back", 3.0, -5)):
        cl = pel.bone(nm + "0", (0, 1, z), (tilt, 0, 0))
        for k, (w, ln) in enumerate(((7, 6), (7, 6), (7, 5))):
            if k:
                cl = cl.bone(f"{nm}{k}", (0, 6, 0), (tilt * 0.4, 0, 0))
            cl.cube(f"{nm}{k}", -w / 2, 0, -0.5, w, ln, 1)
        cl.cube(nm + "_hem", -3.5, 4, -1, 7, 1, 2)
    # legs: plate over mail, a knee cop, greaves and pointed sabatons
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0))
        th.cube("thigh_" + nm, -2, -1, -2, 4, 16, 4)
        th.cube("cop_" + nm, -2.5, 13, -2.6, 5, 3, 2)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -2, 0, -2, 4, P.leg - P.thigh - 2, 4)
        sn.cube("greave_" + nm, -2.5, 2, -2.5, 5, P.leg - P.thigh - 6, 5)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -2, 0, -5, 4, 2, 7)
        ft.cube("toe_" + nm, -1, 0, -6.5, 2, 2, 2)
    # the body: a breastplate in steps, a gold sun on it, a gorget at the throat
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -3.5, -5, -2, 7, 5, 4)
    ch = sp.bone("chest", (0, -5, 0))
    parts.taper(ch, "chest", 0, 0, 0, ((11, 3, 6), (13, 4, 7), (14, 4, 8)))
    sun = ch.bone("sun", (0, -6, -4.1))
    sun.cube("sun", -2, -2, -0.5, 4, 4, 1)
    ch.cube("gorget", -3.5, -13, -3, 7, 2, 6)
    # the wings: from the shoulder blades, an arm of gold each in two lengths, the stained-glass panes hung from them
    for side, nm in ((1, "l"), (-1, "r")):
        w0 = ch.bone("wing_" + nm, (side * 2.5, -9, 4.2), (-14, 0, side * 38))
        w0.cube("wing_" + nm + "0", -1, -16, -1, 2, 16, 2)
        w0.cube("wing_hub_" + nm, -1.5, -1.5, -1.5, 3, 3, 3)
        w1 = w0.bone("wing_" + nm + "1", (0, -16, 0), (0, 0, side * 34))
        w1.cube("wing_" + nm + "1", -1, -13, -1, 2, 13, 2)
        w1.cube("wing_tip_" + nm, -1.5, -15, -1.5, 3, 2, 3)
        arms = (w0, w1)
        for k, (on, along, ln, wd, droop) in enumerate(PANES):
            pn = arms[on].bone(f"pane_{nm}{k}", (0, -along, 0.2), (0, 0, side * droop))
            # out from the arm and down: +x on the left, -x on the right
            x0 = 0 if side > 0 else -ln
            pn.cube(f"pane_{nm}{k}", x0, -wd / 2, -0.5, ln, wd, 1)
            pn.cube(f"quill_{nm}{k}", x0, -wd / 2 - 1, -0.5, ln, 1, 1)
    # arms: great round pauldrons, vambraces, gauntlets; the right holds the spear, the left is open to cast
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 6.5, -10, 0))
        sh.cube("shoulder_" + nm, -2.5, -2, -2.5, 5, 4, 5)
        sh.cube("pauldron_" + nm, -3.5, -3, -3.5, 7, 2, 7)
        sh.cube("lame_" + nm, -3, -1, -3, 6, 2, 6)
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-4, 0, -side * 6))
        arm.cube("arm_" + nm, -2, -1, -2, 4, 13, 4)
        fa = arm.bone("forearm_" + nm, (0, 12, 0), (-10, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 12, 3)
        fa.cube("vambrace_" + nm, -2, 4, -2, 4, 6, 4)
        hd_ = fa.bone("hand_" + nm, (0, 11, 0))
        hd_.cube("hand_" + nm, -1.5, 0, -1.5, 3, 3, 3)
        hd_.cube("cuff_" + nm, -2, -1, -2, 4, 1, 4)
    # the spear: a long white haft bound in gold, a gold guard, a leaf of light for a blade
    # it rests pointed out ahead of it and down (its +y runs forward-down out of the fist)
    sp_ = r.by_name["hand_r"].bone("spear", (0, 1.5, 0), (-60, 0, 0))
    sp_.cube("haft", -1, -14, -1, 2, 42, 2)
    sp_.cube("pommel", -1.5, -16, -1.5, 3, 2, 3)
    for k in range(3):
        sp_.cube(f"bind{k}", -1.5, -6 + k * 10, -1.5, 3, 1, 3)
    sp_.cube("guard", -3, 27, -1, 6, 1, 2)
    sp_.cube("blade0", -1.5, 28, -0.5, 3, 6, 1)
    sp_.cube("blade1", -1, 34, -0.5, 2, 2, 1)
    sp_.cube("blade2", -0.5, 36, -0.5, 1, 1, 1)
    # the head: a closed great helm with a cross in the visor, a crest, and the ring of gold standing behind it
    nk = ch.bone("neck", (0, -12, -0.5))
    nk.cube("neck", -1.5, -3, -1.5, 3, 3, 3)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("helm", -4, -9, -3.5, 8, 9, 8)
    hd.cube("crest", -0.5, -12, -3, 1, 3, 7)
    hd.cube("visor", -4, -6, -4.5, 8, 4, 1)
    halo = hd.bone("halo", (0, -6, 5))
    for nm_, x, y, w, h in (("halo_t", -3, -8, 6, 1), ("halo_b", -3, 7, 6, 1), ("halo_l", 7, -3, 1, 6), ("halo_r", -8, -3, 1, 6),
                            ("halo_tl", 3, -7, 2, 2), ("halo_tr", -5, -7, 2, 2), ("halo_bl", 3, 5, 2, 2), ("halo_br", -5, 5, 2, 2),
                            ("halo_tl2", 5, -5, 2, 2), ("halo_tr2", -7, -5, 2, 2), ("halo_bl2", 5, 3, 2, 2), ("halo_br2", -7, 3, 2, 2)):
        halo.cube(nm_, x, y, 0, w, h, 1)
    r.look = [("head", 0.6, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.9
    # what hangs swings (SPEC §35.10): the tabard, and the panes (glass: a little, slow)
    r.springs = [(f"{nm}{k}", g, hz, 0.3) for nm in ("front", "back") for k, (g, hz) in enumerate(((0.5, 2.6), (0.4, 2.2), (0.3, 2.0)))] \
        + [(f"pane_{s}{k}", 0.1, 3.6, 0.5) for s in "lr" for k in range(len(PANES))] + [("halo", 0.04, 2.0, 0.6)]
    r.guard = ["spear"]  # the spear never goes into the floor but where a move strikes it
    return r


SWING = [(["spear"], [("hand_r", 0, 60), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]
CLOTH = [(["front2", "front_hem"], [("front0", 0, 70), ("front1", 0, 60)]), (["back2", "back_hem"], [("back0", 0, 70), ("back1", 0, 60)])]
# the wings' poses: (root x, root z, outer z) added to their rest, mirrored for the right
FOLD = (14, -26, -24)  # folded down behind it
SPREAD = (-6, 22, 18)  # opened wide
HIGH = (-30, -6, 10)  # raised high over its back
BEAT = (20, 30, -6)  # the downstroke's end


def wings(w, k=1.0):
    """A wing pose (FOLD, SPREAD, HIGH, BEAT) as bone rotations for both wings."""
    rx, rz, oz = w
    return {"wing_l": (rx * k, 0, rz * k), "wing_r": (rx * k, 0, -rz * k), "wing_l1": (0, 0, oz * k), "wing_r1": (0, 0, -oz * k)}


def clips(r):
    plans.biped_locomotion(r, P, weight=1.1, temper="regal")
    plans.biped_reactions(r, weight=1.1)
    plans.biped_duel_reactions(r, weight=1.1, lead="r")
    # standing, its wings breathe open and shut a little, the spear's point barely moving, the halo turning slowly
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("spear", [(0, 0, 0, 0), (L * 0.5, -2, 0, 1), (L, 0, 0, 0)])
    idle.rot("wing_l", [(0, 0, 0, 0), (L * 0.5, -3, 0, 4), (L, 0, 0, 0)])
    idle.rot("wing_r", [(0, 0, 0, 0), (L * 0.5, -3, 0, -4), (L, 0, 0, 0)])
    idle.rot("halo", [(0, 0, 0, 0), (L, 0, 0, 30)])
    # the death: it kneels, the spear falls, the wings fold down round it and its head bows
    kneel = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -14, 1)}, "thigh_l": (-86, 0, 0), "shin_l": (88, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (-86, 0, 0), "shin_r": (88, 0, 0), "foot_r": (-2, 0, 0), "spine": (10, 0, 0), "chest": (14, 0, 0),
             "head": (30, 0, 0), "arm_l": (4, 0, -6), "arm_r": (4, 0, 6), "forearm_l": (-4, 0, 0), "forearm_r": (-4, 0, 0), "spear": (40, 0, 0)}
    bowed = dict(kneel, chest=(26, 0, 0), head=(50, 0, 0), spine=(16, 0, 0), halo=(30, 0, 0), **wings(FOLD, 1.2))
    keyed(r, "death", 2.6, [(0, {}), (0.3, dict({"chest": (-12, 0, 0), "head": (-24, 0, 0), "arm_l": (-30, 0, -30), "arm_r": (-30, 0, 30)}, **wings(SPREAD))),
                            (1.0, dict(kneel, **wings(FOLD, 0.5))), (1.8, bowed), (2.6, bowed)])
    mv = [
        # the spear in fours and fives: a thrust, a sweep of the haft, a rising cut; Descent overhead (held)
        thrust(),
        M.sweep(P, "sweep", side="r", title="Haft Sweep", windup=12, active=4, recover=12, weapon_bone="spear", rng=(0, 6.2), damage=1.0,
                kinds=("combo",), **SPEAR),
        M.backhand(P, "rising", side="r", title="Rising Cut", windup=12, active=4, recover=12, kinds=("combo",), rng=(0, 5.2), damage=0.9,
                   low=LOW_RISE, weapon_bone="spear", **SPEAR),
        M.overhead_slam(P, "descent", title="Descent", windup=20, hold=8, active=5, recover=20, ability="Descent", rng=(0, 5.5),
                        damage=1.3),
        wingbeat(),
        M.spin_cut(P, "halo", side="r", title="Halo Turn", cls="HEAVY", windup=16, active=10, recover=16, kinds=("area", "behind"),
                   rng=(0, 6.0), damage=1.2, weapon_bone="spear", low=LOW_HALO, drop=DROP_HALO, **SPEAR),
        M.palm_cast(P, "lance", side="l", title="Lance of Light", windup=12, active=6, recover=14, cls="LIGHT", ability="Lance of Light",
                    rng=(5, 20)),
        stoop(),
        # when the roof lifts: Ascension (a red grab) and the glass called down
        M.lunge_cut(P, "ascension", side="l", title="Ascension", cls="GRAB", windup=18, active=6, recover=18, advance=4.0, kinds=("gap",),
                    rng=(3, 10), damage=1.3),
        M.sky_call(P, "glass", title="Shattered Glass", windup=20, active=10, recover=16, ability="Shattered Glass", rng=(0, 30)),
        descend(),
        break_(),
        lift(),
    ]
    by = {m.id: m for m in mv}
    # it's two and a half times your height: in its strokes the spear comes down to meet you at the waist
    for mid, add in SPEAR_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            rx = pose.get("spear", (0, 0, 0))
            pose["spear"] = (rx[0] + add, rx[1], rx[2])
    for mid, (bone, add) in ARM_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            x = pose.get(bone, (0, 0, 0))
            pose[bone] = (x[0] + add, x[1], x[2])
    # Descent: the wings rise with the spear, and beat down as it drives
    for f, pose in by["descent"].stages["windup"]:
        pose.update(wings(HIGH, f))
    for f, pose in by["descent"].stages["active"]:
        pose.update(wings(BEAT))
    # the Glass: the wings open wide as it calls; Ascension: they open as it reaches
    for mid in ("glass", "ascension", "lance"):
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.update(wings(SPREAD, 0.6 if mid == "lance" else 1.0))
    # the Halo Turn goes once right round it, the spear held out flat
    for k, (f, pose) in enumerate(by["halo"].stages["active"]):
        rot = pose.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
        pose["pelvis"] = {"rot": (rot["rot"][0], rot["rot"][1] + 360 * f, rot["rot"][2]), "pos": rot.get("pos", (0, 0, 0))}
    # in the strokes the open left hand stays back at its hip, ready to cast
    for mid in ("thrust", "sweep", "rising", "halo"):
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.setdefault("arm_l", (-16, 0, -18))
            pose.setdefault("forearm_l", (-40, 0, 0))
    for m in mv:
        stage_clips(r, m)
    # the Halo Turn comes out of its turn facing where it began: the recovery eases on to 360, never back round
    yaw = r.clips["move_halo"].tracks[("pelvis", "rot")]
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
    # the spear stays out of the floor (Descent and the turn at two thirds meet it and stop there); the tabard lifts off it
    core.ground_pass(r, SWING, clips=[n for n in r.clips if n not in ("death", "kneel", "rise")])
    core.ground_pass(r, CLOTH, clips=list(r.clips), skip=())
    return mv


def thrust():
    """Spear Thrust (LIGHT): it sets its feet and drives the spear's point straight out at your chest, the left hand
    sliding along the haft behind it, the wings drawing back."""
    set_ = dict({"pelvis": {"rot": (4, -18, 0), "pos": (0, -2, 2)}, "chest": (-4, -26, 0), "spine": (0, -8, 0), "head": (0, 22, 0),
                 "arm_r": (-30, 20, 30), "forearm_r": (-80, 0, 0), "spear": (60, 0, 0), "arm_l": (-60, -10, 10), "forearm_l": (-50, 0, 0),
                 "thigh_l": (-20, 0, 0), "shin_l": (24, 0, 0)}, **wings(HIGH, 0.4))
    drive = dict({"pelvis": {"rot": (10, 16, 0), "pos": (0, -3, -2)}, "chest": (10, 20, 0), "spine": (4, 8, 0), "head": (0, -14, 0),
                  "arm_r": (-82, -6, 0), "forearm_r": (-4, 0, 0), "spear": (72, 0, 0), "arm_l": (-70, -20, -10), "forearm_l": (-20, 0, 0),
                  "thigh_l": (-34, 0, 0), "shin_l": (30, 0, 0)}, **wings(FOLD, 0.4))
    return Move("thrust", "LIGHT", 12, 4, 12, title="Spear Thrust", track=10.0, rng=(0, 6.6), kinds=("combo",), damage=1.0, knock=0.6,
                advance=1.0, hit=[("spear", SPEAR_TIP, 1.8), ("spear", (0, 22, 0), 1.4)],
                stages={"windup": [(1.0, set_)], "active": [(0.5, drive), (1.0, drive)],
                        "recover": [(0.5, core._scaled(drive, 0.4))]},
                lag={"arm_r": 0.02, "forearm_r": 0.04, "wing_l": 0.06, "wing_r": 0.06})


def wingbeat():
    """The Wingbeat (HEAVY): it lifts its wings high over its back, holds them there a moment, and brings them down in
    one great stroke that throws you back (dodge to the side as they come down)."""
    gather = dict({"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0, 1)}, "chest": (-10, 0, 0), "head": (-12, 0, 0),
                   "arm_l": (-30, 0, -40), "forearm_l": (-30, 0, 0)}, **wings(HIGH))
    down = dict({"pelvis": {"rot": (10, 0, 0), "pos": (0, -4, -1)}, "chest": (16, 0, 0), "head": (6, 0, 0), "arm_l": (-20, 0, -30),
                 "thigh_l": (-24, 0, 0), "shin_l": (30, 0, 0), "thigh_r": (-14, 0, 0), "shin_r": (24, 0, 0)}, **wings(BEAT))
    return Move("wingbeat", "HEAVY", 16, 6, 18, title="Wingbeat", hold=6, track=4.0, ability="Wingbeat", origin=(0, 0, 0), rng=(0, 8),
                kinds=("area",), damage=0, knock=0,
                stages={"windup": [(0.5, core._scaled(gather, 0.6)), (1.0, gather)], "active": [(0.4, down), (1.0, down)],
                        "recover": [(0.5, core._scaled(down, 0.5))]},
                lag={"wing_l1": 0.04, "wing_r1": 0.04, "head": 0.04, "arm_l": 0.03})


def stoop():
    """The Stoop (its signature, red): it crouches with the spear drawn back along its body, its wings flaring wide and
    high, holds there, and hurls itself at you along the floor point first, the wings swept back (dash through it)."""
    crouch = dict({"pelvis": {"rot": (16, 0, 0), "pos": (0, -6, 2)}, "spine": (10, 0, 0), "chest": (14, 0, 0), "head": (-22, 0, 0),
                   "arm_r": (-10, 0, 30), "forearm_r": (-60, 0, 0), "spear": (100, 0, 0), "arm_l": (-50, 0, -30), "forearm_l": (-20, 0, 0),
                   "thigh_l": (-50, 0, 0), "shin_l": (70, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (60, 0, 0), "foot_l": (-20, 0, 0)},
                  **wings(SPREAD, 1.2))
    fly = dict({"pelvis": {"rot": (34, 0, 0), "pos": (0, -3, -3)}, "spine": (14, 0, 0), "chest": (16, 0, 0), "head": (-36, 0, 0),
                "arm_r": (-80, 0, 6), "forearm_r": (-6, 0, 0), "spear": (70, 0, 0), "arm_l": (30, 0, -10), "forearm_l": (-10, 0, 0),
                "thigh_l": (10, 0, 0), "shin_l": (40, 0, 0), "thigh_r": (30, 0, 0), "shin_r": (30, 0, 0)}, **wings(FOLD, 0.8))
    land = dict({"pelvis": {"rot": (8, 0, 0), "pos": (0, -4, -1)}, "chest": (6, 0, 0), "head": (-6, 0, 0), "arm_r": (-50, 0, 10),
                 "spear": (70, 0, 0), "thigh_l": (-40, 0, 0), "shin_l": (50, 0, 0)}, **wings(SPREAD, 0.6))
    return Move("stoop", "PERILOUS", 18, 10, 22, title="Stoop", track=6.0, lock=12, advance=9.0, ability="Stoop", rng=(6, 18),
                kinds=("gap",), signature=True, damage=1.4, knock=1.4, plant=(),
                hit=[("spear", SPEAR_TIP, 2.0), ("spear", (0, 20, 0), 1.6), ("chest", (0, -6, -2), 7.0)],
                stages={"windup": [(0.4, core._scaled(crouch, 0.5)), (1.0, crouch)], "active": [(0.2, fly), (1.0, fly)],
                        "recover": [(0.4, land)]},
                lag={"wing_l1": 0.05, "wing_r1": 0.05, "head": 0.04, "front0": 0.04, "back0": 0.04})


def descend():
    """Its intro: down on one knee where its light came down, the wings folded round it, it rises to its full height,
    the wings opening wide and high, and lifts its head to you."""
    rise_ = dict({"pelvis": {"rot": (6, 0, 0), "pos": (0, -5, 1)}, "spine": (10, 0, 0), "chest": (12, 0, 0), "head": (16, 0, 0),
                  "arm_r": (-20, 0, 10), "spear": (30, 0, 0)}, **wings(FOLD))
    tall = dict({"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-16, 0, 0),
                 "arm_l": (-50, 0, -40), "forearm_l": (-20, 0, 0), "arm_r": (-30, 0, 20), "spear": (40, 0, 0)}, **wings(SPREAD, 1.3))
    return Move("descend", "NONE", 14, 20, 16, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, rise_)], "active": [(0.4, tall), (1.0, tall)], "recover": [(0.6, core._scaled(tall, 0.3))]},
                lag={"wing_l1": 0.06, "wing_r1": 0.06, "head": 0.06, "arm_l": 0.05})


def break_():
    """The turn at two thirds: it lifts the spear in both hands and drives it down into the nave; the aisles break away."""
    up = dict({"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-14, 0, 0),
               "arm_r": (-170, 0, 12), "forearm_r": (-10, 0, 0), "arm_l": (-170, 0, -12), "forearm_l": (-10, 0, 0), "spear": (-150, 0, 0)},
              **wings(HIGH))
    drive = dict({"pelvis": {"rot": (14, 0, 0), "pos": (0, -7, -1)}, "spine": (12, 0, 0), "chest": (24, 0, 0), "head": (8, 0, 0),
                  "arm_r": (-70, 10, 0), "forearm_r": (-6, 0, 0), "arm_l": (-70, -10, 0), "forearm_l": (-6, 0, 0), "spear": (116, 0, 0),
                  "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (44, 0, 0), "foot_l": (-12, 0, 0),
                  "foot_r": (-12, 0, 0)}, **wings(BEAT))
    return Move("break", "NONE", 16, 24, 14, title="The aisles fall", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                plant=("l", "r"), stages={"windup": [(0.4, core._scaled(up, 0.5)), (1.0, up)], "active": [(0.3, drive), (1.0, drive)],
                                          "recover": [(0.5, core._scaled(drive, 0.5))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.04, "wing_l1": 0.05, "wing_r1": 0.05})


def lift():
    """The turn at a third: it raises the spear to the sky in one hand, the wings opening as wide as they go, head back;
    the roof lifts away."""
    up = dict({"arm_r": (-170, 0, 10), "forearm_r": (-6, 0, 0), "spear": (-170, 0, 0), "chest": (-14, 0, 0), "head": (-28, 0, 0),
               "arm_l": (-40, 0, -50), "forearm_l": (-20, 0, 0), "pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}}, **wings(SPREAD, 1.4))
    return Move("lift", "NONE", 12, 34, 12, title="The roof lifts away", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, core._scaled(up, 0.6))], "active": [(0.3, up), (1.0, up)], "recover": [(0.5, core._scaled(up, 0.4))]},
                lag={"wing_l1": 0.05, "wing_r1": 0.05, "arm_r": 0.03, "halo": 0.06})


# Its sounds: glass, bells and wind. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.amethyst_block.chime", 1.4, 1.2), ("block.glass.step", 1.0, 0.8)],
    "tell_heavy": [("block.amethyst_block.resonate", 1.8, 0.6), ("entity.ender_dragon.flap", 1.2, 1.2)],
    "tell_perilous": [("block.bell.resonate", 1.8, 0.7), ("block.beacon.power_select", 1.6, 0.6)],
    "tell_grab": [("block.beacon.activate", 1.6, 0.8), ("block.amethyst_block.resonate", 1.6, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.9), ("item.trident.throw", 1.2, 0.8)],
    "swing_heavy": [("entity.player.attack.sweep", 1.8, 0.6), ("entity.ender_dragon.flap", 1.6, 0.9)],
    "cast": [("block.beacon.power_select", 1.6, 1.0), ("block.amethyst_block.chime", 1.6, 0.8)],
    "impact": [("block.glass.break", 2.2, 0.6), ("block.amethyst_block.break", 1.8, 0.5)],
    "roar": [("block.bell.resonate", 2.4, 0.5), ("block.beacon.activate", 2.0, 0.6)],
    "step": [("block.calcite.step", 0.8, 0.8), ("block.chain.step", 0.5, 1.0)],
}
MOVE_SOUNDS = {"descend": {"swing": "roar"}, "break": {"swing": "impact"}, "lift": {"swing": "roar"}, "glass": {"swing": "roar"}}

FIGHT = {
    # a duel (SPEC §46.3): phrases of four and five, the spear's reach and the wings covering the ground round it
    "duel": True,
    "stance": 380,  # tuned with the sim (SPEC §46.8)
    "intro": {"pose": "kneel", "move": "descend"},
    "phases": [
        # the whole cathedral: the spear in fours, Descent, the Wingbeat, the Halo Turn, the Lance, the Stoop
        {"pool": [
            {"move": "thrust", "weight": 3, "then": [["sweep", "rising", "thrust"], ["sweep", "thrust", "descent"]]},
            {"move": "sweep", "weight": 2, "then": [["thrust", "rising", "sweep"], ["rising", "descent"]]},
            {"move": "descent", "weight": 2, "then": [["thrust", "sweep", "rising"]]},
            {"move": "wingbeat", "weight": 1, "then": [["lance", "stoop"], ["stoop"]]},
            {"move": "lance", "weight": 2, "when": "far", "then": [["lance", "stoop"], ["stoop", "thrust", "sweep"]]},
            {"move": "stoop", "weight": 2, "when": "far", "then": [["thrust", "sweep", "rising"]]},
            {"move": "halo", "weight": 2, "when": "behind", "then": [["thrust", "sweep", "rising"]]},
        ]},
        # the aisles fall: the nave is all there is, and the Wingbeat throws you toward its edges; phrases of four and five
        {"transition": "break", "pool": [
            {"move": "thrust", "weight": 3, "then": [["sweep", "rising", "thrust", "descent"], ["sweep", "thrust", "wingbeat"]]},
            {"move": "sweep", "weight": 2, "then": [["thrust", "rising", "sweep", "halo"], ["rising", "thrust", "descent"]]},
            {"move": "descent", "weight": 2, "then": [["thrust", "sweep", "rising"], ["wingbeat", "stoop"]]},
            {"move": "wingbeat", "weight": 2, "then": [["lance", "stoop"], ["stoop", "thrust"]]},
            {"move": "lance", "weight": 2, "when": "far", "then": [["lance", "stoop"], ["stoop", "thrust", "sweep"]]},
            {"move": "stoop", "weight": 2, "when": "far", "then": [["thrust", "sweep", "rising", "descent"]]},
            {"move": "halo", "weight": 2, "when": "behind", "then": [["thrust", "sweep", "rising"]]},
        ]},
        # the roof lifts: Ascension, the glass, and whoever flies answered; phrases of five
        {"transition": "lift", "pool": [
            {"move": "thrust", "weight": 3, "then": [["sweep", "rising", "thrust", "halo"], ["sweep", "thrust", "rising", "descent"]]},
            {"move": "sweep", "weight": 2, "then": [["thrust", "rising", "sweep", "ascension"]]},
            {"move": "descent", "weight": 1, "then": [["thrust", "sweep", "rising", "wingbeat"]]},
            {"move": "wingbeat", "weight": 2, "then": [["lance", "stoop"], ["glass"]]},
            {"move": "wingbeat", "weight": 2, "when": "air", "then": [["lance", "lance"]]},
            {"move": "lance", "weight": 3, "when": "air", "then": [["lance", "wingbeat"], ["ascension"]]},
            {"move": "lance", "weight": 2, "when": "far", "then": [["lance", "stoop"], ["glass"]]},
            {"move": "ascension", "weight": 2, "when": "far", "then": [["thrust", "sweep", "rising"]]},
            {"move": "stoop", "weight": 2, "when": "far", "then": [["thrust", "sweep", "rising", "halo"]]},
            {"move": "glass", "weight": 2, "then": [["stoop"], ["lance", "ascension"]]},
            {"move": "halo", "weight": 2, "when": "behind", "then": [["thrust", "sweep", "rising"]]},
        ]},
    ],
    "enrage": {"below": 0.12, "speed": 1.2},
}


def paint(r, P_):
    """Five materials in a value order: the light brightest (the cross in the helm, the spear's leaf, the halo), then
    the stained glass of the wings lit from behind, then gold (the trim, the wing arms, the sun), then the white plate
    with cool blue shadows, then the white tabard. The darkest things on it are the lead between the panes and the
    mail at the joints."""
    plate = Ramp("#4a5262", "#6a7486", "#8f99aa", "#b6bfcc", "#d8dee6", "#f2f5f8")
    cloth = Ramp("#5c5a56", "#7e7b75", "#a19d96", "#c6c2ba", "#e4e0d8", "#f8f6f0")
    gold = Ramp("#3a2a10", "#5c4318", "#8a6624", "#b88d36", "#dcb85a", "#f2dc94")
    mail = Ramp("#14161c", "#22262e", "#323844", "#454c5a")
    light = ("#f6d77a", "#fff0b8", "#fffbe8")
    glass = {"blue": ("#0e1d4a", "#1a3480", "#2f56bd", "#5e88e8"), "violet": ("#2a1048", "#46207a", "#6a36b0", "#9a68dc"),
             "sky": ("#0f3a52", "#1c6488", "#3596c0", "#7cc8e6"), "gold": ("#4a3008", "#7a5410", "#b8841c", "#ecc050")}
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in of("chest", "spine", "pauldron_", "shoulder_", "lame_", "arm_", "vambrace_", "thigh_", "greave_", "cop_", "helm", "visor",
                "fauld_", "foot_", "toe_", "gorget", "hand_"):
        P_.paint(c, plate, 3.4, lambda f, i, j: (0.7 if j == 0 else -0.5 if (i + j) % 7 == 0 else 0), grad=1.4, rim=1.0, noise=0.15, nscale=4)
    for c in of("forearm_", "shin_", "pelvis", "neck"):
        P_.paint(c, mail, 2.0, lambda f, i, j: (0.8 if (i + 2 * j) % 3 == 0 else 0), grad=0.8, noise=0.3)
    for c in of("front", "back"):
        P_.paint(c, cloth, 3.6, lambda f, i, j: (-0.6 if i % 5 == 2 else 0), grad=1.6, noise=0.25, nscale=6)
    for c in of("belt", "buckle", "cuff_", "front_hem", "back_hem", "crest", "bind", "guard", "pommel", "wing_", "quill_", "sun"):
        P_.paint(c, gold, 4.0, lambda f, i, j: (-0.8 if (i + j) % 3 == 0 else 0.3), grad=1.4, rim=1.0, noise=0.2, nscale=3)
    # the sun on the breast, lit a little
    radial(P_, "sun", "north", (light[2], light[1], light[0], "#b88d36"), alpha=150)
    # the haft: white, bound in gold
    P_.paint("haft", cloth, 4.0, lambda f, i, j: (0.6 if i == 0 else 0), grad=0.6, rim=0.6)
    # the blade and the halo: light
    for c in of("blade", "halo_"):
        P_.paint(c, gold, 5.0, lambda f, i, j: (0.5 if (i + j) % 2 == 0 else 0), rim=1.2)
        for fname in ("top", "bottom") + SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(light[1] if (i + j) % 2 else light[2], 200 if c.startswith("halo") else 230))
    # the panes: stained glass in lead, lit from behind
    for s in "lr":
        for k in range(len(PANES)):
            col = glass[PANE_GLASS[k]]
            c = f"pane_{s}{k}"
            P_.paint(c, Ramp("#0a0a10", *col), 3.6, lambda f, i, j: (-3.5 if i == 0 or j == 0 or j == f.h - 1 or i % 5 == 4 else 0.4 if (i + j) % 3 == 0 else 0),
                     grad=0.8, rim=0.4, noise=0.2)
            for fname in ("north", "south"):
                g = P_.face(c, fname, glow=True)
                for i, j in g.each():
                    if not (i == 0 or j == 0 or j == g.h - 1 or i % 5 == 4):
                        g.set(i, j, hexc(col[3] if (i + j) % 2 else col[2], 120))
    # the cross in the visor: light through it, the brightest thing on it
    with P_.eyes():
        f = P_.face("visor", "north")
        g = P_.face("visor", "north", glow=True)
        cx = f.w // 2
        for i, j in f.each():
            if i in (cx - 1, cx) or (j == 1 and 1 <= i <= f.w - 2):
                f.set(i, j, hexc(light[2], 255))
                g.set(i, j, hexc(light[1], 255))
            else:
                f.set(i, j, hexc("#1a1d24", 255))
    edge_glow(P_, "blade0", "north", light[2], rows=1, alpha=255, side="left")
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
