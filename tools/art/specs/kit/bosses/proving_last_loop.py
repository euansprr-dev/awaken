"""The Last Loop (SPEC §47.10, docs/fights/PLAN.md §4.7): the sixth and last Proving's Warden, met in the Cradle's
antechamber. You, as the Signal was: shaped like you (a player's build, twice your height), all white, with gold seams
running down it like the lines of the Signal, and no face, only a soft light where a face would be. A thin gold ring
turns round its head, and a great ring, the Loop, stands behind its back. It fights with a blade of light, a player's
sword made of the Signal: a white-gold edge with a gold guard. Bold and simple (Euan, 2026-10-29: no sculpted faces, epic
and mysterious).

It appears where you stood three seconds before, down on one knee the way you woke on your first day, and stands up
(its intro). It fights as a duel: the blade in fours, fives and sixes (the Cut, the Recut, the Thrust), the Fall (the
blade overhead in both hands, driven down: a wave), Signal (bolts of light from its open hand), Full Circle for whoever is
behind it, the Rush (red: it crouches with the blade drawn back and hurls itself at you), and the Line (its signature,
red: it draws the blade back low and cuts a line of light along the floor to you). When night falls it lifts the blade to
the dark; then it folds out of where it stands and comes up behind you (Loop), and pushes you back with both hands
(Push). When the loop closes it drives the blade into the floor and turns; then everything you've had falls round you
(red) and it reaches to take you (Taken, a red grab). When the Seed's door turns it looks back at it; then its echoes
step out of it, and it turns with the blade over its head, the Ring of light flying out. It dies on its knees, the way
you woke, its head bowed.
"""
import math

from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_last_loop"
SCALE = 2.5  # on the player-sized HUMAN model: twice your height

P = plans.Biped(leg=26, thigh=13, foot=(4, 2, 5), pelvis=(8, 4, 4), spine=(8, 6, 4), chest=(9, 8, 5), neck=(2, 2, 2),
                head=(8, 8, 8), arm=(3, 12, 3), forearm=(3, 11, 3), hand=(3, 3, 3), stance=2.2, hunch=0, leg_w=(4, 4))
# the blade's point, in the blade bone's space (the edge runs down the bone's +y out of the fist)
BLADE_TIP = (0, 36, 0)
BLADE = dict(weapon=BLADE_TIP, weapon_r=1.6)
# how far the blade comes down in its strokes (degrees on its own axis), so every stroke meets someone standing at the
# waist (SPEC §47.6: a damaging blow's hits come down under a standing player's shoulders)
BLADE_DOWN = {"cut": 80, "recut": 30, "whirl": 80, "thrust": 14}
ARM_DOWN = {"taken": ("arm_l", 24), "cut": ("arm_r", 30), "thrust": ("arm_r", 10)}
LOW_RECUT, LOW_WHIRL = 26, 18
# the gold ring round its head: eight lengths (x, z, yaw)
HALO = [(0, -5.5, 0), (3.9, -3.9, 45), (5.5, 0, 90), (3.9, 3.9, 135), (0, 5.5, 0), (-3.9, 3.9, 45), (-5.5, 0, 90), (-3.9, -3.9, 135)]
# the Loop behind its back: twelve lengths round a circle (angle in degrees)
LOOP_R = 11


def body():
    r = R.Rig(ID, tex=(256, 256))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4, -1, -2, 8, 4, 4)
    pel.cube("belt", -4.5, 0, -2.5, 9, 1, 5)
    # legs: a player's, a little longer, square at the foot
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 2, 0))
        th.cube("thigh_" + nm, -2, -1, -2, 4, P.thigh + 1, 4)
        th.cube("knee_" + nm, -2.5, P.thigh - 2, -2.5, 5, 2, 1)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -2, 0, -2, 4, P.leg - P.thigh - 2, 4)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -2, 0, -3, 4, 2, 5)
    # the body: a player's chest and shoulders
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -4, -6, -2, 8, 6, 4)
    ch = sp.bone("chest", (0, -6, 0))
    ch.cube("chest", -4.5, -8, -2.5, 9, 8, 5)
    ch.cube("collar", -3.5, -9, -2.5, 7, 1, 5)
    # the Loop: a great ring standing behind its back, turning slowly
    lp = ch.bone("loop", (0, -10, 4))
    for k in range(12):
        a = math.radians(k * 30)
        seg = lp.bone(f"loopseg{k}", (round(LOOP_R * math.sin(a), 2), round(-LOOP_R * math.cos(a), 2), 0), (0, 0, k * 30))
        seg.cube(f"loop{k}", -3, -0.5, -0.5, 6, 1, 1)
    # arms: a player's, slim, gold at the wrists; the right holds the blade
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 6, -7, 0))
        sh.cube("shoulder_" + nm, -2, -1.5, -2, 4, 3, 4)
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-4, 0, -side * 5))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 12, 3)
        fa = arm.bone("forearm_" + nm, (0, 11, 0), (-10, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 11, 3)
        fa.cube("band_" + nm, -2, 8, -2, 4, 1, 4)
        hd_ = fa.bone("hand_" + nm, (0, 10, 0))
        hd_.cube("hand_" + nm, -1.5, 0, -1.5, 3, 3, 3)
    # the blade of light: a gold guard and pommel, a white-gold edge as long as it is tall to the hip
    bl = r.by_name["hand_r"].bone("blade", (0, 1.5, 0), (-60, 0, 0))
    bl.cube("pommel", -1, -5, -1, 2, 2, 2)
    bl.cube("grip", -0.5, -3, -0.5, 1, 4, 1)
    bl.cube("guard", -3, 1, -1, 6, 1, 2)
    bl.cube("edge", -1, 2, -0.5, 2, 32, 1)
    bl.cube("point", -0.5, 34, -0.5, 1, 2, 1)
    # the head: a player's, blank; a thin gold ring turns round it
    nk = ch.bone("neck", (0, -9, 0))
    nk.cube("neck", -1, -2, -1, 2, 2, 2)
    hd = nk.bone("head", (0, -2, 0))
    hd.cube("head", -4, -8, -4, 8, 8, 8)
    hd.cube("face", -3, -6, -4.5, 6, 4, 1)
    hl = hd.bone("halo", (0, -10.5, 0), (-14, 0, 0))
    for k, (x, z, yaw) in enumerate(HALO):
        hs = hl.bone(f"haloseg{k}", (x, 0, z), (0, yaw, 0))
        hs.cube(f"halo{k}", -2, -0.5, -0.5, 4, 1, 1)
    r.look = [("head", 0.6, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.8
    r.guard = ["blade"]  # the blade never goes into the floor but where a move strikes it
    r.under = []
    return r


SWING = [(["blade"], [("hand_r", 0, 60), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]


def knelt():
    """Down on one knee, the blade's point to the floor, the head bowed: the way you woke on your first day."""
    return {"pelvis": {"rot": (6, 0, 0), "pos": (0, -9, 1)}, "thigh_l": (-84, 0, 0), "shin_l": (84, 0, 0), "thigh_r": (6, 0, 0),
            "shin_r": (86, 0, 0), "foot_r": (-40, 0, 0), "spine": (10, 0, 0), "chest": (16, 0, 0), "head": (34, 0, 0),
            "arm_r": (-40, 0, 6), "forearm_r": (-10, 0, 0), "blade": (80, 0, 0), "arm_l": (-50, 0, -6), "forearm_l": (-40, 0, 0)}


def clips(r):
    plans.biped_locomotion(r, P, weight=1.0, temper="regal")
    plans.biped_reactions(r, weight=1.0)
    plans.biped_duel_reactions(r, weight=1.0, lead="r")
    # standing, it breathes, the blade's point barely moving; the halo and the Loop turn slowly
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("blade", [(0, 0, 0, 0), (L * 0.5, -3, 0, 0), (L, 0, 0, 0)])
    idle.rot("halo", [(0, 0, 0, 0), (L, 0, 360, 0)])
    idle.rot("loop", [(0, 0, 0, 0), (L, 0, 0, 360)])
    # the death: down on one knee and then both, the blade laid down before it, its head bowed
    kneel = knelt()
    both = dict(kneel, thigh_r=(-84, 0, 0), shin_r=(84, 0, 0), foot_r=(-2, 0, 0), pelvis={"rot": (6, 0, 0), "pos": (0, -12, 1)})
    laid = dict(both, arm_r=(-60, 0, 4), forearm_r=(-10, 0, 0), blade=(150, 0, 0), chest=(30, 0, 0), head=(50, 0, 0), spine=(16, 0, 0))
    keyed(r, "death", 3.0, [(0, {}), (0.3, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-30, 0, -40), "arm_r": (-30, 0, 30)}),
                            (1.0, kneel), (1.6, both), (2.3, laid), (3.0, laid)])
    mv = [
        # the blade in fours, fives and sixes: the Cut, the Recut, the Thrust; the Fall overhead (held)
        M.sweep(P, "cut", side="r", title="Cut", windup=10, active=4, recover=12, weapon_bone="blade", rng=(0, 5.6), damage=1.0,
                kinds=("combo",), **BLADE),
        M.backhand(P, "recut", side="r", title="Recut", windup=10, active=4, recover=12, kinds=("combo",), rng=(0, 4.8), damage=0.9,
                   low=LOW_RECUT, weapon_bone="blade", **BLADE),
        thrust(),
        M.overhead_slam(P, "fall", title="The Fall", windup=18, hold=8, active=5, recover=20, ability="The Fall", rng=(0, 5.2), damage=1.3),
        M.palm_cast(P, "signal", side="l", title="Signal", windup=12, active=6, recover=14, cls="LIGHT", ability="Signal", rng=(5, 20)),
        M.spin_cut(P, "whirl", side="r", title="Full Circle", cls="HEAVY", windup=14, active=10, recover=16, kinds=("area", "behind"),
                   rng=(0, 5.6), damage=1.2, weapon_bone="blade", low=LOW_WHIRL, drop=0, **BLADE),
        rush(),
        line(),
        # when night falls: Loop and Push
        loop(),
        push(),
        # when the loop closes: everything you've had, and Taken (a red grab)
        M.sky_call(P, "every", title="Every Power You've Had", windup=20, active=10, recover=16, ability="Every Power You've Had", rng=(0, 30)),
        M.lunge_cut(P, "taken", side="l", title="Taken", cls="GRAB", windup=18, active=6, recover=18, advance=4.0, kinds=("gap",),
                    rng=(3, 10), damage=1.3),
        # when the Seed's door turns: its echoes, and the Ring
        echoes(),
        ring(),
        wake(),
        night(),
        close(),
        door(),
    ]
    by = {m.id: m for m in mv}
    # it's twice your height: in its strokes the blade comes down to meet you at the waist
    for mid, add in BLADE_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            rx = pose.get("blade", (0, 0, 0))
            pose["blade"] = (rx[0] + add, rx[1], rx[2])
    for mid, (bone, add) in ARM_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            x = pose.get(bone, (0, 0, 0))
            pose[bone] = (x[0] + add, x[1], x[2])
    # Full Circle and the Ring go once right round it
    for mid in ("whirl", "ring"):
        for k, (f, pose) in enumerate(by[mid].stages["active"]):
            rot = pose.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
            pose["pelvis"] = {"rot": (rot["rot"][0], rot["rot"][1] + 360 * f, rot["rot"][2]), "pos": rot.get("pos", (0, 0, 0))}
    # in the strokes the free left hand stays back at its hip, open
    for mid in ("cut", "recut", "thrust", "whirl"):
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.setdefault("arm_l", (-14, 0, -16))
            pose.setdefault("forearm_l", (-36, 0, 0))
    for m in mv:
        stage_clips(r, m)
    # a turn comes out of its turn facing where it began: the recovery eases on to 360, never back round
    for mid in ("whirl", "ring"):
        yaw = r.clips["move_" + mid].tracks[("pelvis", "rot")]
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
    # the blade stays out of the floor (the Fall and the Line meet it and stop there)
    skip = {"death", "kneel", "rise"}
    core.ground_pass(r, SWING, clips=[n for n in r.clips if n not in skip])
    return mv


def thrust():
    """The Thrust (LIGHT): it sets its feet and drives the blade's point straight out at your chest, the left hand back
    at its hip."""
    set_ = {"pelvis": {"rot": (4, -18, 0), "pos": (0, -2, 2)}, "chest": (-4, -26, 0), "spine": (0, -8, 0), "head": (0, 22, 0),
            "arm_r": (-30, 20, 30), "forearm_r": (-80, 0, 0), "blade": (60, 0, 0), "thigh_l": (-20, 0, 0), "shin_l": (24, 0, 0)}
    drive = {"pelvis": {"rot": (10, 16, 0), "pos": (0, -3, -2)}, "chest": (10, 20, 0), "spine": (4, 8, 0), "head": (0, -14, 0),
             "arm_r": (-82, -6, 0), "forearm_r": (-4, 0, 0), "blade": (72, 0, 0), "thigh_l": (-34, 0, 0), "shin_l": (30, 0, 0)}
    return Move("thrust", "LIGHT", 10, 4, 12, title="Thrust", track=10.0, rng=(0, 6.0), kinds=("combo",), damage=1.0, knock=0.6,
                advance=1.0, hit=[("blade", BLADE_TIP, 1.6), ("blade", (0, 20, 0), 1.3)],
                stages={"windup": [(1.0, set_)], "active": [(0.5, drive), (1.0, drive)],
                        "recover": [(0.5, core._scaled(drive, 0.4))]},
                lag={"arm_r": 0.02, "forearm_r": 0.04, "head": 0.03})


def rush():
    """The Rush (red): it crouches with the blade drawn back along its body, holds there, and hurls itself at you along
    the floor point first (dash through it)."""
    crouch = {"pelvis": {"rot": (16, 0, 0), "pos": (0, -6, 2)}, "spine": (10, 0, 0), "chest": (14, 0, 0), "head": (-22, 0, 0),
              "arm_r": (-10, 0, 30), "forearm_r": (-60, 0, 0), "blade": (100, 0, 0), "arm_l": (-50, 0, -30), "forearm_l": (-20, 0, 0),
              "thigh_l": (-50, 0, 0), "shin_l": (70, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (60, 0, 0), "foot_l": (-20, 0, 0)}
    fly = {"pelvis": {"rot": (34, 0, 0), "pos": (0, -3, -3)}, "spine": (14, 0, 0), "chest": (16, 0, 0), "head": (-36, 0, 0),
           "arm_r": (-80, 0, 6), "forearm_r": (-6, 0, 0), "blade": (70, 0, 0), "arm_l": (30, 0, -10), "forearm_l": (-10, 0, 0),
           "thigh_l": (10, 0, 0), "shin_l": (40, 0, 0), "thigh_r": (30, 0, 0), "shin_r": (30, 0, 0)}
    land = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -4, -1)}, "chest": (6, 0, 0), "head": (-6, 0, 0), "arm_r": (-50, 0, 10),
            "blade": (70, 0, 0), "thigh_l": (-40, 0, 0), "shin_l": (50, 0, 0)}
    return Move("rush", "PERILOUS", 18, 10, 22, title="The Rush", track=6.0, lock=12, advance=9.0, ability="The Rush", rng=(6, 18),
                kinds=("gap",), damage=1.4, knock=1.4, plant=(),
                hit=[("blade", BLADE_TIP, 2.0), ("blade", (0, 18, 0), 1.6), ("chest", (0, -4, -2), 6.0)],
                stages={"windup": [(0.4, core._scaled(crouch, 0.5)), (1.0, crouch)], "active": [(0.2, fly), (1.0, fly)],
                        "recover": [(0.4, land)]},
                lag={"head": 0.04, "arm_l": 0.04, "loop": 0.05})


def line():
    """The Line (its signature, red): it draws the blade back low behind it in both hands, holds there, and cuts up and
    through: a line of light runs along the floor to you (dash aside)."""
    draw = {"pelvis": {"rot": (8, 30, 0), "pos": (0, -4, 2)}, "spine": (6, 20, 0), "chest": (10, 30, 0), "head": (0, -40, 0),
            "arm_r": (20, 0, 30), "forearm_r": (-20, 0, 0), "arm_l": (10, 30, -20), "forearm_l": (-40, 0, 0), "blade": (120, 0, 0),
            "thigh_l": (-40, 0, 0), "shin_l": (50, 0, 0), "thigh_r": (-10, 0, 0), "shin_r": (30, 0, 0)}
    cut = {"pelvis": {"rot": (10, -20, 0), "pos": (0, -5, -1)}, "spine": (6, -14, 0), "chest": (10, -24, 0), "head": (0, 10, 0),
           "arm_r": (-110, 0, 10), "forearm_r": (-10, 0, 0), "arm_l": (-100, -10, -10), "forearm_l": (-20, 0, 0), "blade": (90, 0, 0),
           "thigh_l": (-44, 0, 0), "shin_l": (54, 0, 0), "thigh_r": (-14, 0, 0), "shin_r": (34, 0, 0)}
    return Move("line", "PERILOUS", 20, 12, 22, title="The Line", track=6.0, lock=14, ability="The Line", rng=(4, 18),
                kinds=("ranged", "gap"), signature=True, damage=0, knock=0, plant=("l", "r"),
                stages={"windup": [(0.4, core._scaled(draw, 0.5)), (1.0, draw)], "active": [(0.25, cut), (1.0, cut)],
                        "recover": [(0.4, cut), (0.8, core._scaled(cut, 0.4))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.04, "loop": 0.05})


def loop():
    """Loop: it draws the blade across itself and turns away into its own light, gone; and comes up behind you, turning
    back with the blade coming round."""
    gather = {"pelvis": {"rot": (6, 40, 0), "pos": (0, -3, 0)}, "chest": (12, 30, 0), "head": (18, 30, 0), "arm_l": (-40, 30, 20),
              "arm_r": (-60, -30, -20), "forearm_l": (-70, 0, 0), "forearm_r": (-50, 0, 0), "blade": (20, 0, 0)}
    turned = dict(gather, pelvis={"rot": (6, 160, 0), "pos": (0, -4, 0)}, chest=(16, 20, 0))
    back = {"pelvis": {"rot": (4, 0, 0), "pos": (0, -2, 0)}, "chest": (6, 0, 0), "head": (-6, 0, 0), "arm_r": (-50, 0, 30),
            "forearm_r": (-20, 0, 0), "blade": (60, 0, 0), "arm_l": (-30, 0, -30)}
    return Move("loop", "NONE", 12, 14, 12, title="Loop", track=0.0, ability="Loop", rng=(4, 99), kinds=("ranged",),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, gather), (1.0, turned)], "active": [(0.3, turned), (1.0, back)],
                        "recover": [(0.5, core._scaled(back, 0.4))]},
                lag={"head": 0.05, "loop": 0.06})


def push():
    """Push (HEAVY): it draws both hands back to its chest, the blade turned down, holds them there, and thrusts both
    palms out at you: a wave that throws you back (dodge to the side as the hands go out)."""
    gather = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0, 2)}, "chest": (-10, 0, 0), "head": (-6, 0, 0), "arm_l": (-40, 0, -10),
              "forearm_l": (-110, 0, 0), "arm_r": (-40, 0, 10), "forearm_r": (-110, 0, 0), "blade": (150, 0, 0),
              "thigh_r": (-10, 0, 0), "shin_r": (14, 0, 0)}
    out = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -3, -2)}, "chest": (14, 0, 0), "head": (4, 0, 0), "arm_l": (-90, 10, 0),
           "forearm_l": (-6, 0, 0), "arm_r": (-90, -10, 0), "forearm_r": (-6, 0, 0), "blade": (150, 0, 0),
           "thigh_l": (-30, 0, 0), "shin_l": (36, 0, 0)}
    return Move("push", "HEAVY", 16, 6, 18, title="Push", hold=6, track=4.0, ability="Push", origin=(0, 0, 0), rng=(0, 8),
                kinds=("area",), damage=0, knock=0,
                stages={"windup": [(0.5, core._scaled(gather, 0.6)), (1.0, gather)], "active": [(0.4, out), (1.0, out)],
                        "recover": [(0.5, core._scaled(out, 0.5))]},
                lag={"head": 0.04, "arm_l": 0.03, "arm_r": 0.03, "loop": 0.05})


def echoes():
    """Echoes of You: it draws the blade across its chest and bows; then flings both arms out wide and its echoes step
    out of it to either side."""
    bow = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -3, 0)}, "chest": (16, 0, 0), "head": (26, 0, 0), "arm_r": (-70, -30, 0),
           "forearm_r": (-60, 0, 0), "blade": (20, 0, 0), "arm_l": (-70, 30, 0), "forearm_l": (-60, 0, 0)}
    fling = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-14, 0, 0), "head": (-22, 0, 0), "arm_r": (-30, 0, 70),
             "forearm_r": (-10, 0, 0), "arm_l": (-30, 0, -70), "forearm_l": (-10, 0, 0), "blade": (40, 0, 0)}
    return Move("echoes", "NONE", 16, 22, 14, title="Echoes of You", track=0.0, ability="Echoes of You", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0,
                stages={"windup": [(0.5, core._scaled(bow, 0.6)), (1.0, bow)], "active": [(0.2, fling), (1.0, fling)],
                        "recover": [(0.5, core._scaled(fling, 0.4))]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "head": 0.05, "loop": 0.06})


def ring():
    """The Ring (red): it lifts the blade over its head in both hands, holds it, and turns once right round; a ring of
    light flies out from it on every side (dash through the gap)."""
    up = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-10, 0, 0), "head": (-16, 0, 0), "arm_r": (-170, 0, 20),
          "forearm_r": (-20, 0, 0), "arm_l": (-170, 0, -20), "forearm_l": (-20, 0, 0), "blade": (-80, 0, 90)}
    spin = dict(up, pelvis={"rot": (4, 0, 0), "pos": (0, -2, 0)}, chest=(-4, 0, 0))
    return Move("ring", "PERILOUS", 18, 12, 16, title="The Ring", track=0.0, ability="The Ring", rng=(0, 30), kinds=("area", "ranged"),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, core._scaled(up, 0.5)), (1.0, up)], "active": [(0.2, spin), (1.0, spin)],
                        "recover": [(0.5, core._scaled(spin, 0.4))]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "head": 0.05, "loop": 0.06})


def wake():
    """Its intro: down on one knee where you stood, the blade's point to the floor, its head bowed, the way you woke on
    your first day; it stands up and lifts its head to you, the blade coming up to rest."""
    tall = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "spine": (-6, 0, 0), "chest": (-10, 0, 0), "head": (-14, 0, 0),
            "arm_l": (-30, 0, -30), "forearm_l": (-20, 0, 0), "arm_r": (-30, 0, 20), "blade": (40, 0, 0)}
    return Move("wake", "NONE", 20, 24, 14, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.0, knelt()), (1.0, knelt())], "active": [(0.5, core._scaled(knelt(), 0.5)), (1.0, tall)],
                        "recover": [(0.6, core._scaled(tall, 0.3))]},
                lag={"head": 0.06, "arm_l": 0.05, "arm_r": 0.05, "loop": 0.06})


def night():
    """The turn at three quarters: it lifts the blade high in one hand to the dark, its head back; night falls."""
    up = {"arm_r": (-170, 0, 10), "forearm_r": (-6, 0, 0), "blade": (-170, 0, 0), "chest": (-12, 0, 0), "head": (-24, 0, 0),
          "arm_l": (-30, 0, -40), "forearm_l": (-20, 0, 0), "pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}}
    return Move("night", "NONE", 12, 30, 12, title="Night falls", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, core._scaled(up, 0.6))], "active": [(0.3, up), (1.0, up)],
                        "recover": [(0.5, core._scaled(up, 0.4))]},
                lag={"arm_r": 0.03, "head": 0.05, "loop": 0.06})


def close():
    """The turn at half: it lifts the blade in both hands and drives it down into the floor, and turns on it; the loop
    closes."""
    up = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-14, 0, 0),
          "arm_r": (-170, 0, 12), "forearm_r": (-10, 0, 0), "arm_l": (-170, 0, -12), "forearm_l": (-10, 0, 0), "blade": (-150, 0, 0)}
    drive = {"pelvis": {"rot": (14, 0, 0), "pos": (0, -7, -1)}, "spine": (12, 0, 0), "chest": (24, 0, 0), "head": (8, 0, 0),
             "arm_r": (-70, 10, 0), "forearm_r": (-6, 0, 0), "arm_l": (-70, -10, 0), "forearm_l": (-6, 0, 0), "blade": (116, 0, 0),
             "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (44, 0, 0), "foot_l": (-12, 0, 0),
             "foot_r": (-12, 0, 0)}
    return Move("close", "NONE", 16, 24, 14, title="The loop closes", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                plant=("l", "r"), stages={"windup": [(0.4, core._scaled(up, 0.5)), (1.0, up)], "active": [(0.3, drive), (1.0, drive)],
                                          "recover": [(0.5, core._scaled(drive, 0.5))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.04, "loop": 0.05})


def door():
    """The turn at a quarter: the Seed's door is turning behind it; it turns its head and body to look back at it, the
    blade lowered, and turns back to you slowly."""
    look = {"pelvis": {"rot": (0, 50, 0), "pos": (0, -1, 0)}, "spine": (0, 30, 0), "chest": (-6, 40, 0), "head": (-10, 50, 0),
            "arm_r": (-10, 0, 10), "forearm_r": (-10, 0, 0), "blade": (30, 0, 0), "arm_l": (-6, 0, -10)}
    back = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-12, 0, 0), "head": (-20, 0, 0), "arm_l": (-40, 0, -40),
            "forearm_l": (-20, 0, 0), "arm_r": (-30, 0, 20), "blade": (40, 0, 0)}
    return Move("door", "NONE", 14, 30, 14, title="The Seed's door turns", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, look)], "active": [(0.5, look), (1.0, back)],
                        "recover": [(0.5, core._scaled(back, 0.4))]},
                lag={"head": 0.05, "arm_l": 0.04, "loop": 0.06})


# Its sounds: the Signal's light, bells and glass. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.amethyst_block.chime", 1.2, 1.2), ("entity.illusioner.mirror_move", 0.6, 1.4)],
    "tell_heavy": [("block.bell.resonate", 1.6, 0.7), ("block.respawn_anchor.charge", 1.0, 0.8)],
    "tell_perilous": [("block.bell.use", 1.8, 0.6), ("block.beacon.power_select", 1.6, 0.7)],
    "tell_grab": [("block.conduit.activate", 1.6, 1.2), ("block.respawn_anchor.charge", 1.4, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 1.0), ("block.amethyst_block.resonate", 0.8, 1.4)],
    "swing_heavy": [("entity.player.attack.strong", 1.8, 0.7), ("block.amethyst_block.resonate", 1.2, 0.8)],
    "cast": [("entity.illusioner.cast_spell", 1.6, 1.2), ("block.amethyst_block.chime", 1.2, 0.8)],
    "impact": [("block.end_portal_frame.fill", 2.0, 0.6), ("block.glass.break", 1.4, 0.6)],
    "roar": [("block.bell.resonate", 2.4, 0.5), ("block.beacon.activate", 1.8, 0.8)],
    "step": [("block.amethyst_block.chime", 0.4, 1.4), ("block.calcite.step", 0.6, 0.9)],
    "blink": [("entity.illusioner.mirror_move", 1.6, 0.8), ("block.respawn_anchor.set_spawn", 1.0, 1.2)],
}
MOVE_SOUNDS = {"wake": {"swing": "roar"}, "night": {"swing": "roar"}, "close": {"swing": "impact"}, "door": {"swing": "roar"},
               "loop": {"swing": "blink", "impact": ""}, "echoes": {"swing": "blink", "impact": ""}, "line": {"swing": "impact"},
               "ring": {"swing": "roar"}, "rush": {"swing": "swing_heavy"}}

FIGHT = {
    # a duel (SPEC §47.3): phrases of four to six, the blade's reach covering the ground round it
    "duel": True,
    "stance": 260,  # tuned with the sim (SPEC §47.10)
    "intro": {"pose": "stand", "move": "wake"},
    "phases": [
        # the antechamber: the blade in fours, the Fall, Signal, Full Circle, the Rush, the Line
        {"pool": [
            {"move": "cut", "weight": 3, "then": [["recut", "thrust", "cut"], ["recut", "cut", "fall"]]},
            {"move": "recut", "weight": 2, "then": [["cut", "thrust", "recut"], ["thrust", "fall"]]},
            {"move": "fall", "weight": 2, "then": [["cut", "recut", "thrust"]]},
            {"move": "signal", "weight": 2, "when": "far", "then": [["signal", "line"], ["rush", "cut", "recut"]]},
            {"move": "line", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust"]]},
            {"move": "rush", "weight": 2, "when": "far", "then": [["recut", "cut", "thrust"]]},
            {"move": "whirl", "weight": 2, "when": "behind", "then": [["cut", "recut", "thrust"]]},
        ]},
        # night falls: Loop after whoever keeps away, Push for whoever stays close; phrases of four and five
        {"transition": "night", "pool": [
            {"move": "cut", "weight": 3, "then": [["recut", "thrust", "cut", "fall"], ["recut", "cut", "push"]]},
            {"move": "recut", "weight": 2, "then": [["cut", "thrust", "recut", "whirl"], ["thrust", "cut", "fall"]]},
            {"move": "fall", "weight": 2, "then": [["cut", "recut", "thrust"], ["loop"]]},
            {"move": "push", "weight": 1, "then": [["rush"], ["signal", "line"]]},
            {"move": "signal", "weight": 2, "when": "far", "then": [["signal", "loop"], ["line", "cut", "recut"]]},
            {"move": "loop", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "cut"]]},
            {"move": "line", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "fall"]]},
            {"move": "rush", "weight": 2, "when": "far", "then": [["recut", "cut", "thrust", "cut"]]},
            {"move": "whirl", "weight": 2, "when": "behind", "then": [["cut", "recut", "thrust"]]},
        ]},
        # the loop closes: everything you've had round you, Taken for whoever stands off, Push toward the edge; phrases of five
        {"transition": "close", "pool": [
            {"move": "cut", "weight": 3, "then": [["recut", "thrust", "cut", "fall"], ["recut", "cut", "thrust", "push"]]},
            {"move": "recut", "weight": 2, "then": [["cut", "thrust", "recut", "whirl"], ["thrust", "cut", "recut", "taken"]]},
            {"move": "fall", "weight": 1, "then": [["cut", "recut", "thrust", "every"]]},
            {"move": "push", "weight": 2, "then": [["every"], ["rush"], ["signal", "line"]]},
            {"move": "every", "weight": 2, "then": [["loop", "cut", "recut"], ["signal", "line"]]},
            {"move": "signal", "weight": 2, "when": "far", "then": [["signal", "taken"], ["every", "loop"]]},
            {"move": "signal", "weight": 2, "when": "air", "then": [["signal", "signal"]]},
            {"move": "taken", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust"]]},
            {"move": "loop", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "cut"]]},
            {"move": "line", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "fall"]]},
            {"move": "rush", "weight": 1, "when": "far", "then": [["recut", "cut", "thrust", "push"]]},
            {"move": "whirl", "weight": 2, "when": "behind", "then": [["cut", "recut", "thrust", "cut"]]},
        ]},
        # the Seed's door turns: its echoes and the Ring; phrases of five and six
        {"transition": "door", "pool": [
            {"move": "cut", "weight": 3, "then": [["recut", "thrust", "cut", "fall"], ["recut", "cut", "thrust", "cut", "whirl"]]},
            {"move": "recut", "weight": 2, "then": [["cut", "thrust", "recut", "taken"], ["thrust", "cut", "recut", "push", "rush"]]},
            {"move": "fall", "weight": 1, "then": [["cut", "recut", "thrust", "ring"]]},
            {"move": "echoes", "weight": 1, "then": [["cut", "recut", "thrust", "cut"], ["ring"]]},
            {"move": "ring", "weight": 2, "then": [["loop", "cut", "recut"], ["every", "signal"]]},
            {"move": "every", "weight": 2, "then": [["loop", "cut", "recut", "thrust"], ["line"]]},
            {"move": "push", "weight": 2, "then": [["every"], ["rush"]]},
            {"move": "signal", "weight": 2, "when": "far", "then": [["signal", "line"], ["ring"]]},
            {"move": "signal", "weight": 3, "when": "air", "then": [["signal", "ring"]]},
            {"move": "taken", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "cut"]]},
            {"move": "loop", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "cut"]]},
            {"move": "line", "weight": 2, "when": "far", "then": [["cut", "recut", "thrust", "fall"]]},
            {"move": "whirl", "weight": 2, "when": "behind", "then": [["cut", "recut", "thrust", "cut"]]},
        ]},
    ],
    "enrage": {"below": 0.1, "speed": 1.2},
}


def paint(r, P_):
    """Three materials in a value order: the light brightest (the face, the blade's edge, the halo and the Loop), then
    gold (the seams, the bands, the guard, the belt), then the body: a soft white with a cold blue in its shade. Nothing
    on it is dark: it's the Signal, and the Signal is light."""
    white = Ramp("#7d8aa6", "#a3b0c8", "#c6d0e2", "#e2e8f2", "#f4f7fb", "#ffffff")
    gold = Ramp("#5c4318", "#8a6624", "#b88d36", "#dcb85a", "#f2dc94", "#fff2c4")
    light = ("#bfe6ff", "#e8f6ff", "#ffffff")
    of = lambda *ps: parts.cubes_of(r, *ps)
    # the body: white, with a gold seam down the middle of every limb's front and a cold blue in the shade
    for c in of("pelvis", "spine", "chest", "collar", "shoulder_", "arm_", "forearm_", "hand_", "thigh_", "shin_", "foot_", "neck", "head", "knee_"):
        P_.paint(c, white, 3.4, lambda f, i, j: (0.9 if i == f.w // 2 else 0), grad=1.6, rim=1.0, noise=0.12, nscale=5)
        for fname in ("north",):
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                if i == g.w // 2:
                    g.set(i, j, hexc("#f2dc94", 140))
    for c in of("belt", "band_", "guard", "pommel", "grip"):
        P_.paint(c, gold, 4.0, lambda f, i, j: (-0.8 if (i + j) % 3 == 0 else 0.3), grad=1.4, rim=1.0, noise=0.2, nscale=3)
    # the blade's edge, its point, the halo and the Loop: light, glowing
    for c in of("edge", "point", "halo", "loop"):
        P_.paint(c, white, 5.0, lambda f, i, j: 0, rim=1.0)
        for fname in ("top", "bottom") + SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(light[1] if (i + j) % 2 else light[2], 230))
    edge_glow(P_, "edge", "north", light[0], rows=1, alpha=120, side="left")
    # the face: no face, only a soft light where one would be
    with P_.eyes():
        f = P_.face("face", "north")
        g = P_.face("face", "north", glow=True)
        for i, j in f.each():
            f.set(i, j, hexc("#ffffff", 255))
            g.set(i, j, hexc(light[0] if (i in (0, f.w - 1) or j in (0, f.h - 1)) else light[2], 200))
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
