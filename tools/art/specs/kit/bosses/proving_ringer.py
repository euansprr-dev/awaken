"""The Ringer (SPEC §47.4, docs/fights/PLAN.md §4.7): the first Proving's Warden, met knee-deep in the Drowned Belfry
when the Reading Bell won't stop. A bell-ringer that drowned at its ropes and kept ringing: tall, gaunt and stooped in
a soaked cassock to the ankle, a deep cowl with no face in it, only a small bell hanging where the face should be, its
clapper glowing the drowned teal of the whole body's light. Bell-ropes are knotted across its chest, their sallies
striped red, white and blue; chains wound round both forearms run out of its fists to two bronze hand-bells it swings
as flails. Bold and simple (Euan, 2026-10-29: no sculpted faces, epic and mysterious).

It drops into the light from the bells above (its intro), and fights as a duel: swings of the bells in phrases of two
and three, an overhead clang that sends a ring through the water, a chain lash at range, the Drowned Toll (its
signature: it rings the bell in its hood, a red ring rolling out you dash through), and when the water drains, the
whirl of both bells and the Bell Rope (a red grab: the chain thrown round you). It dies kneeling, the hood bell's
light going out.
"""
import math

from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_ringer"
SCALE = 2.0  # on the player-sized HUMAN model: 1.2 x 3.6 blocks, about 4 to the top of its hood

P = plans.Biped(leg=29, thigh=14, foot=(3, 2, 7), pelvis=(8, 4, 5), spine=(5, 5, 4), chest=(13, 11, 7), neck=(3, 3, 3),
                head=(6, 7, 7), arm=(3, 13, 3), forearm=(3, 12, 3), hand=(3, 3, 3), stance=3.5, hunch=12, leg_w=(3, 4))
# the bell at the end of each chain, in the flail bone's space (the chain hangs down the bone's +y)
BELL_AT = (0, 17, 0)
FLAIL = dict(weapon=BELL_AT, weapon_r=2.6)
# how far each bell comes down in its swings (degrees on its chain's own axis), and how low it bends into its turns, so
# every swing meets someone standing at the waist (SPEC §47.6: every damaging blow's lowest point under 1.2 blocks)
BELL_DOWN = {"swing_a": ("r", 60), "swing_b": ("l", 60), "turn": ("l", 60), "whirl": ("r", 60)}
LOW_SPIN, DROP_SPIN = 10, 0


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4, -1, -2.5, 8, 4, 5)
    pel.cube("cincture", -4.5, -1.5, -3, 9, 2, 6)
    # the cassock: a front panel and a back panel to the ankle, each three lengths of soaked cloth on springs, and a
    # skirt at each side; it parts round the legs so a stride never meets it
    for nm, z, tilt in (("front", -3.2, 4), ("back", 3.0, -6)):
        cl = pel.bone(nm + "0", (0, 1, z), (tilt, 0, 0))
        for k, (w, ln) in enumerate(((8, 9), (9, 9), (9, 8))):
            if k:
                cl = cl.bone(f"{nm}{k}", (0, 9, 0), (tilt * 0.4, 0, 0))
            cl.cube(f"{nm}{k}", -w / 2, 0, -0.5, w, ln, 1)
        cl.cube(nm + "_hem", -4.5, 7, -1, 9, 2, 2)
    for side, nm in ((1, "l"), (-1, "r")):
        sk = pel.bone("skirt_" + nm, (side * 4.2, 1, 0), (0, 0, -side * 6))
        sk.cube("skirt_" + nm + "0", -0.5, 0, -2.5, 1, 12, 5)
        sk2 = sk.bone("skirt_" + nm + "1", (0, 12, 0), (0, 0, -side * 3))
        sk2.cube("skirt_" + nm + "1", -0.5, 0, -2.5, 1, 11, 5)
    # legs: long and thin under the cloth, bare shins wound in rope, flat bare feet
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0), (0, 0, 0) if side > 0 else (-5, 0, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 15, 3)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0), (0, 0, 0) if side > 0 else (8, 0, 0))
        sn.cube("shin_" + nm, -1, 0, -1, 2, P.leg - P.thigh - 2, 2)
        for k in range(3):
            sn.cube(f"binding_{nm}{k}", -1.5, 3 + k * 3.5, -1.5, 3, 1, 3)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0), (0, 0, 0) if side > 0 else (-3, 0, 0))
        ft.cube("foot_" + nm, -1.5, 0, -5, 3, 2, 7)
    # a thin stooped torso: the spine bows, the chest is narrow, the shoulders high and bony
    sp = pel.bone("spine", (0, -1, 0), (P.hunch * 0.4, 0, 0))
    sp.cube("spine", -2.5, -5, -2, 5, 5, 4)
    ch = sp.bone("chest", (0, -5, 0), (P.hunch * 0.6, 0, 0))
    parts.taper(ch, "chest", 0, 0, 0, ((9, 3, 6), (12, 4, 7), (13, 4, 7)))
    ch.cube("scapula", -5.5, -10, 2.5, 11, 4, 2)
    # bell-ropes crossed over the chest, knotted at the sternum; the sallies (the striped woollen grips) hang from the knot
    ch.cube("rope_a", -6, -10, -4, 12, 1, 1)
    ch.cube("rope_b", -6, -6, -4, 12, 1, 1)
    knot = ch.bone("knot", (0, -8, -4.5))
    knot.cube("knot", -1, -1, -1, 2, 2, 2)
    for k, (x, ln) in enumerate(((-1.5, 9), (1.0, 7))):
        sal = knot.bone(f"sally{k}", (x, 1, 0), (0, 0, 4 - 8 * k))
        sal.cube(f"sally{k}", -0.5, 0, -0.5, 1, ln, 1)
    # arms: long and bony in loose wet sleeves; a chain wound round each forearm runs out of the fist to a hand-bell
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 6.2, -10, 0))
        sh.cube("shoulder_" + nm, -2, -1.5, -2, 4, 3, 4)
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-6, 0, -side * 8))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 13, 3)
        slv = arm.bone("sleeve_" + nm, (0, 4, 0))
        slv.cube("sleeve_" + nm, -2.5, 0, -2.5, 5, 9, 5)
        fa = arm.bone("forearm_" + nm, (0, 12, 0), (-14, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 12, 3)
        for k in range(3):
            fa.cube(f"wind_{nm}{k}", -2, 2 + k * 3, -2, 4, 1, 4)
        hd_ = fa.bone("hand_" + nm, (0, 11, 0))
        hd_.cube("hand_" + nm, -1.5, 0, -1.5, 3, 3, 3)
        # the flail: a chain straight out of the fist (taut when swung, hanging when it stands), its links turned each
        # way in turn so it reads as chain and not a rod, and the bell on it: a real bell's shape, the waist narrow and
        # the sound-bow flaring to a heavy lip, its mouth lit
        fl = hd_.bone("flail_" + nm, (0, 3, 0))
        for k in range(6):
            if k % 2:
                fl.cube(f"link_{nm}{k}", -0.5, k * 2, -1, 1, 2, 2)
            else:
                fl.cube(f"link_{nm}{k}", -1, k * 2, -0.5, 2, 2, 1)
        bell = fl.bone("bell_" + nm, (0, 12, 0))
        bell.cube("crown_" + nm, -1, 0, -1, 2, 1, 2)
        parts.taper(bell, "bell_" + nm, 0, 1, 0, ((4, 1, 4), (5, 3, 5), (6, 2, 6)), up=False)
        bell.cube("lip_" + nm, -3.5, 7, -3.5, 7, 1, 7)
        bell.cube("clapper_" + nm, -0.5, 4, -0.5, 1, 4, 1)
    # the cowl: deep and heavy, no face in it, a small bell hanging inside where the face would be
    nk = ch.bone("neck", (0, -11, -1))
    nk.cube("neck", -1.5, -3, -1.5, 3, 3, 3)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("hood_top", -4, -9, -3, 8, 2, 8)
    hd.cube("hood_l", 3, -8, -4, 1, 9, 8)
    hd.cube("hood_r", -4, -8, -4, 1, 9, 8)
    hd.cube("hood_back", -3, -8, 3, 6, 9, 1)
    hd.cube("hood_brow", -3, -8, -4.5, 6, 1, 1)
    hd.cube("hollow", -3, -7, -2, 6, 7, 5)
    tip = hd.bone("hood_tip", (0, -8, 3), (-30, 0, 0))
    tip.cube("hood_tip", -1.5, -1, 0, 3, 2, 5)
    fb = hd.bone("face_bell", (0, -6.5, -2.5))
    fb.cube("face_crown", -0.5, 0, -0.5, 1, 1, 1)
    parts.taper(fb, "face_bell", 0, 1, 0, ((2, 1, 2), (3, 2, 3)), up=False)
    fb.cube("face_clapper", -0.5, 2.5, -0.5, 1, 2, 1)
    # a mantle over the shoulders, open at the front
    for side, nm in ((1, "l"), (-1, "r")):
        mt = ch.bone("mantle_" + nm, (side * 4, -11, 0.5), (0, 0, side * 18))
        mt.cube("mantle_" + nm, -3.5, 0, -3.5, 7, 6, 7)
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.8
    # what hangs swings (SPEC §35.10): the cassock, the skirts, the sallies, the hood's tip, the hood bell and the two
    # hand-bells on their chains (a little: the chain is taut in a swing)
    r.springs = [(f"{nm}{k}", g, hz, 0.3) for nm in ("front", "back") for k, (g, hz) in enumerate(((0.55, 2.4), (0.45, 2.0), (0.35, 1.8)))] \
        + [(f"skirt_{s}{k}", 0.4, 2.2, 0.3) for s in "lr" for k in "01"] \
        + [(f"sally{k}", 0.5, 2.6, 0.25) for k in "01"] + [("hood_tip", 0.3, 3.0, 0.4), ("face_bell", 0.5, 3.2, 0.2)] \
        + [(f"bell_{s}", 0.25, 3.0, 0.3) for s in "lr"] + [(f"mantle_{s}", 0.1, 5.0, 0.5) for s in "lr"]
    r.guard = ["bell_l", "bell_r"]  # the bells never go into the ground but where a move strikes the floor
    return r


SWING = [(["flail_l", "bell_l"], [("hand_l", 0, 60), ("forearm_l", 0, 50), ("arm_l", 0, 45)]),
         (["flail_r", "bell_r"], [("hand_r", 0, 60), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]
CLOTH = [(["front2", "front_hem"], [("front0", 0, 70), ("front1", 0, 60)]), (["back2", "back_hem"], [("back0", 0, 70), ("back1", 0, 60)]),
         (["skirt_l1"], [("skirt_l0", 0, 50)]), (["skirt_r1"], [("skirt_r0", 0, 50)])]


def clips(r):
    plans.biped_locomotion(r, P, weight=0.9, temper="feral")
    plans.biped_reactions(r, weight=0.9)
    plans.biped_duel_reactions(r, weight=0.9, lead="r")
    # standing, the bells hang and turn a little on their chains; the hood bell sways
    idle = r.clips["idle"]
    L = idle.length
    for s, sg in (("l", 1), ("r", -1)):
        idle.rot("flail_" + s, [(0, 0, 0, 0), (L * 0.5, 4, 0, 3 * sg), (L, 0, 0, 0)])
    idle.rot("face_bell", [(0, 0, 0, 0), (L * 0.25, 6, 0, 0), (L * 0.75, -6, 0, 0), (L, 0, 0, 0)])
    # the death: it sinks to its knees, the arms fall, the hood bows until the bell inside touches its chest
    kneel = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -13, 1)}, "thigh_l": (-84, 0, 0), "shin_l": (86, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (-84, 0, 0), "shin_r": (86, 0, 0), "foot_r": (-2, 0, 0), "spine": (16, 0, 0), "chest": (20, 0, 0),
             "head": (28, 0, 0), "arm_l": (6, 0, -8), "arm_r": (6, 0, 8), "forearm_l": (-6, 0, 0), "forearm_r": (-6, 0, 0)}
    bowed = dict(kneel, chest=(34, 0, 0), head=(44, 0, 0), spine=(22, 0, 0), arm_l=(10, 0, -4), arm_r=(10, 0, 4))
    keyed(r, "death", 2.4, [(0, {}), (0.3, {"chest": (-16, 0, 0), "head": (-30, 0, 0), "arm_l": (-40, 0, -50), "arm_r": (-40, 0, 50)}),
                            (1.0, kneel), (1.7, bowed), (2.4, bowed)])
    mv = [
        # the swings: a backhand sweep of the right bell, the left coming round after it, then (in a string of three)
        # the overhead clang of both
        M.sweep(P, "swing_a", side="r", title="Swing", windup=16, active=4, recover=12, weapon_bone="flail_r", rng=(0, 4.6), damage=1.0,
                kinds=("combo",), **FLAIL),
        M.sweep(P, "swing_b", side="l", title="Swing", windup=16, active=4, recover=12, weapon_bone="flail_l", rng=(0, 4.6), damage=1.0,
                kinds=("combo",), **FLAIL),
        M.overhead_slam(P, "clang", title="Clang", windup=20, hold=8, active=5, recover=20, ability="Clang", rng=(0, 4.4), damage=1.3),
        # the lash: a long step and the right chain thrown straight out at you
        M.lunge_cut(P, "lash", side="r", title="Chain Lash", windup=16, active=5, recover=14, advance=3.0, kinds=("gap",), rng=(4.5, 10),
                    damage=0.9, weapon=(0, 20, 0)),
        M.spin_cut(P, "turn", side="l", title="Turning Swing", windup=16, active=6, recover=12, rng=(0, 4.2), damage=0.9,
                   weapon_bone="flail_l", low=LOW_SPIN, drop=DROP_SPIN, **FLAIL),
        toll(),
        peal(),
        # when the water has drained: the whirl (both bells once right round it), the Bell Rope (a red grab)
        M.spin_cut(P, "whirl", side="r", title="Whirl", cls="HEAVY", windup=16, active=12, recover=16, kinds=("area",), rng=(0, 4.4),
                   damage=1.2, weapon_bone="flail_r", low=LOW_SPIN, drop=DROP_SPIN, **FLAIL),
        M.shoulder_charge(P, "rope", side="r", title="Bell Rope", windup=16, active=12, recover=18, advance=5.0, cls="GRAB",
                          kinds=("gap",), rng=(4, 12), damage=1.3),
        drop(),
        drain(),
    ]
    by = {m.id: m for m in mv}
    # it's twice your height: in its swings the bells come down to meet you at the waist, never over your head
    for mid, (side, add) in BELL_DOWN.items():
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            rx = pose.get("flail_" + side, (0, 0, 0))
            pose["flail_" + side] = (rx[0] + add, rx[1], rx[2])
    # the whirl turns once right round: its active stage keys go all the way round
    for k, (f, pose) in enumerate(by["whirl"].stages["active"]):
        rot = pose.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
        pose["pelvis"] = {"rot": (rot["rot"][0], rot["rot"][1] + 360 * f, rot["rot"][2]), "pos": rot.get("pos", (0, 0, 0))}
    # in a sweep the free arm holds its bell back and low, ready for the next swing of the string
    for mid, free in (("swing_a", "l"), ("swing_b", "r")):
        sg = 1 if free == "l" else -1
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.setdefault("arm_" + free, (-20, 0, -26 * sg))
            pose.setdefault("forearm_" + free, (-30, 0, 0))
    # in the Bell Rope it runs in with the left bell swung up behind its head, ready to bring down on whoever it catches;
    # it leans into the run, so its shoulder comes in at your chest, not your hair
    for f, pose in by["rope"].stages["active"]:
        for bone, add in (("spine", 10), ("chest", 6)):
            x = pose.get(bone, (0, 0, 0))
            pose[bone] = (x[0] + add, x[1], x[2])
    nw = len(by["rope"].stages["windup"])
    for k, (f, pose) in enumerate(by["rope"].stages["windup"] + by["rope"].stages["active"]):
        lift = f if k < nw else 1.0  # it comes up evenly through the wind-up and rides there, rocking with the run
        pose["arm_l"] = (-120 * lift - (4 if k % 2 else 0), 0, -24 * lift)
        pose["forearm_l"] = (-30 * lift, 0, 0)
    for m in mv:
        stage_clips(r, m)
    # the whirl comes out of its turn facing where it began, a full turn on: the recovery eases on from where the turn
    # carried it to 360 (the same way it faced), never back round the way it came
    yaw = r.clips["move_whirl"].tracks[("pelvis", "rot")]
    top = max(range(len(yaw)), key=lambda k: yaw[k][1][1])
    t0, t1 = yaw[top][0], yaw[-1][0]
    for k in range(top + 1, len(yaw)):
        t, (x, y, z), *rest = yaw[k]
        u = (t - t0) / max(1e-6, t1 - t0)
        ease = u * u * (3 - 2 * u)
        yaw[k] = (t, (x, 360 + (yaw[top][1][1] - 360) * (1 - ease) - 14 * math.sin(math.pi * ease) * ease, z), *rest)
    # every foot that stands stays where it set down (the moves that travel carry the body: their feet go on as keyed)
    for m in mv:
        if m.advance == 0 and m.rise == 0:
            core.lock_feet(r, core.clip_name(m))
    for nm in ("idle", "parried", "reel", "hurt", "hurt_l", "hurt_r"):
        if nm in r.clips:
            core.lock_feet(r, nm)
    # the bells stay out of the floor (the clang's meet it and stop there); the cloth lifts off the floor everywhere
    core.ground_pass(r, SWING, clips=[n for n in r.clips if n not in ("death", "kneel", "rise")])
    core.ground_pass(r, CLOTH, clips=list(r.clips), skip=())
    return mv


def toll():
    """The Drowned Toll (its signature): it straightens, throws its head back so the bell in its hood swings out, and
    rings it with a jerk of the whole body; the ring rolls out through the water, red (dash through it)."""
    gather = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 0)}, "spine": (-8, 0, 0), "chest": (-14, 0, 0), "head": (-30, 0, 0),
              "arm_l": (-30, 0, -36), "arm_r": (-30, 0, 36), "forearm_l": (-40, 0, 0), "forearm_r": (-40, 0, 0), "face_bell": (-40, 0, 0)}
    ring = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -2, 0)}, "spine": (10, 0, 0), "chest": (18, 0, 0), "head": (20, 0, 0),
            "arm_l": (-10, 0, -60), "arm_r": (-10, 0, 60), "forearm_l": (-10, 0, 0), "forearm_r": (-10, 0, 0), "face_bell": (50, 0, 0)}
    return Move("toll", "PERILOUS", 22, 8, 20, title="Drowned Toll", track=0.0, ability="Drowned Toll", rng=(0, 9), kinds=("area",),
                signature=True, damage=0, knock=0,
                stages={"windup": [(0.4, core._scaled(gather, 0.5)), (1.0, gather)], "active": [(0.25, ring), (1.0, ring)],
                        "recover": [(0.5, {"chest": (6, 0, 0), "head": (6, 0, 0), "face_bell": (-12, 0, 0)})]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "face_bell": 0.03})


def peal():
    """The Peal (its shot, LIGHT): it swings both bells up and out wide, leaning back, and claps them together in front
    of its chest; the ring flies straight at you, white-gold, and a clean deflect sends it back into it (SPEC §47.3)."""
    wide = {"pelvis": {"rot": (-6, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-8, 0, 0),
            "arm_l": (-100, 0, -70), "arm_r": (-100, 0, 70), "forearm_l": (-20, 0, 0), "forearm_r": (-20, 0, 0)}
    clap = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -2, -1)}, "spine": (8, 0, 0), "chest": (14, 0, 0), "head": (6, 0, 0),
            "arm_l": (-84, 0, 18), "arm_r": (-84, 0, -18), "forearm_l": (-30, 0, 0), "forearm_r": (-30, 0, 0)}
    return Move("peal", "LIGHT", 16, 6, 14, title="Peal", track=9.0, ability="Peal", origin=(0, 2.2, 1.3), rng=(5, 18), kinds=("ranged",),
                damage=0, knock=0, plant=("l", "r"),
                stages={"windup": [(0.62, core._scaled(wide, 0.55)), (1.0, wide)], "active": [(0.3, clap), (1.0, clap)],
                        "recover": [(0.5, core._scaled(clap, 0.4))]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "forearm_l": 0.06, "forearm_r": 0.06})


def drop():
    """Its intro: it hangs from the bells above, drops, lands in a crouch with the bells splashing either side, and
    straightens into the light."""
    land = {"pelvis": {"rot": (16, 0, 0), "pos": (0, -9, 1)}, "spine": (12, 0, 0), "chest": (18, 0, 0), "head": (-12, 0, 0),
            "thigh_l": (-60, 0, 0), "shin_l": (70, 0, 0), "foot_l": (-10, 0, 0), "thigh_r": (-40, 0, 0), "shin_r": (60, 0, 0),
            "foot_r": (-20, 0, 0), "arm_l": (-10, 0, -50), "arm_r": (-10, 0, 50), "forearm_l": (-10, 0, 0), "forearm_r": (-10, 0, 0)}
    hang = {"pelvis": {"rot": (0, 0, 0), "pos": (0, 22, 0)}, "arm_l": (-170, 0, -10), "arm_r": (-170, 0, 10), "forearm_l": (0, 0, 0),
            "forearm_r": (0, 0, 0), "head": (14, 0, 0), "thigh_l": (6, 0, 0), "thigh_r": (-4, 0, 0)}
    return Move("drop", "NONE", 8, 12, 16, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, hang)], "active": [(0.4, land), (1.0, land)], "recover": [(0.6, core._scaled(land, 0.3))]},
                lag={"arm_l": 0.05, "arm_r": 0.05, "head": 0.06})


def drain():
    """The turn of the fight: it tolls the hood bell slowly three times, head bowed, and the water goes (the hall drains
    into the pit)."""
    bow = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -2, 0)}, "chest": (16, 0, 0), "head": (34, 0, 0), "arm_l": (4, 0, -10), "arm_r": (4, 0, 10),
           "face_bell": (30, 0, 0)}
    up = dict(bow, head=(10, 0, 0), chest=(4, 0, 0), face_bell=(-30, 0, 0))
    return Move("drain", "NONE", 10, 36, 12, title="The water drains", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, bow)], "active": [(0.17, up), (0.33, bow), (0.5, up), (0.67, bow), (0.84, up), (1.0, bow)],
                        "recover": [(0.5, core._scaled(bow, 0.3))]},
                lag={"face_bell": 0.04})


# Its sounds: bronze and water. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.bell.use", 1.0, 1.4), ("block.chain.step", 1.2, 0.8)],
    "tell_heavy": [("block.bell.resonate", 1.6, 0.8), ("block.chain.place", 1.4, 0.6)],
    "tell_perilous": [("block.bell.resonate", 2.2, 0.5), ("entity.warden.sonic_charge", 1.4, 0.7)],
    "tell_grab": [("block.chain.break", 1.6, 0.6), ("block.bell.use", 1.4, 0.6)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.8), ("block.chain.step", 1.6, 1.2)],
    "swing_heavy": [("entity.player.attack.sweep", 1.8, 0.6), ("block.chain.fall", 1.6, 0.7)],
    "cast": [("block.bell.use", 2.0, 0.5)],
    "impact": [("block.bell.use", 2.4, 0.6), ("entity.generic.splash", 1.6, 0.8)],
    "roar": [("block.bell.resonate", 2.5, 0.45), ("block.bell.use", 2.0, 0.5)],
    "step": [("entity.generic.swim", 0.8, 0.7), ("block.chain.step", 0.6, 1.0)],
}
MOVE_SOUNDS = {"drain": {"swing": "cast"}}

FIGHT = {
    # a duel (SPEC §47.3): it fights in phrases and opens up after each one; a turned-aside bell throws its arm back
    "duel": True,
    "finish": True,  # dies only to a finishing blow: one of the three pilots (SPEC §67.4)
    "stance": 140,  # tuned with the sim (SPEC §47.4)
    "intro": {"pose": "idle", "move": "drop"},
    "phases": [
        # in the water: swings in twos and threes, the clang that rings through the water, the lash at range, the toll
        {"pool": [
            {"move": "swing_a", "weight": 3, "then": [["swing_b"], ["swing_b", "clang"]]},
            {"move": "clang", "weight": 2, "then": [["swing_a"]]},
            {"move": "lash", "weight": 2, "when": "far", "then": [["swing_a", "swing_b"]]},
            {"move": "peal", "weight": 2, "when": "far", "then": [["lash"], ["peal"]]},
            {"move": "turn", "weight": 2, "when": "behind", "then": [["swing_a"]]},
            {"move": "toll", "weight": 1},
        ]},
        # the water drained: longer phrases, the whirl, the Bell Rope
        {"transition": "drain", "pool": [
            {"move": "swing_a", "weight": 3, "then": [["swing_b", "clang"], ["swing_b", "whirl"]]},
            {"move": "whirl", "weight": 2, "then": [["swing_a"]]},
            {"move": "rope", "weight": 2, "when": "far", "then": [["swing_a", "swing_b"]]},
            {"move": "lash", "weight": 1, "when": "far", "then": [["clang"]]},
            {"move": "peal", "weight": 2, "when": "far", "then": [["peal"], ["peal", "rope"]]},
            {"move": "turn", "weight": 2, "when": "behind", "then": [["swing_a", "swing_b"]]},
            {"move": "toll", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def paint(r, P_):
    """Four materials in a value order: the drowned teal light brightest (the hood bell's mouth and clapper, the
    hand-bells' mouths, the cracks along the cloth where water still runs), then verdigris bronze (the bells, the
    chains' wear), then the soaked cassock (a dark slate going black at the hem), then the shadow inside the hood, the
    darkest thing on it. The sallies are its one warm note: faded red, white and blue."""
    bronze = Ramp("#2a3a32", "#3b5244", "#4f6b55", "#6d8763", "#8f9f6a", "#b5a86a", "#d2b878")
    cloth = Ramp("#07090c", "#0d1116", "#141a21", "#1c242d", "#26303b", "#323e4b")
    skin = Ramp("#1a1e1f", "#262c2d", "#343b3b", "#454d4b", "#59615e")
    chain = Ramp("#15191b", "#222829", "#323a3a", "#465050", "#5f6a68")
    teal = ("#0f6b62", "#1fa595", "#4fdcc6", "#a6fff0", "#e9fffb")
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in of("front", "back", "skirt_", "sleeve_", "mantle_", "hood_", "cincture"):
        P_.paint(c, cloth, 3.2, lambda f, i, j: (-0.9 if i % 3 == 1 else 0.35 if j % 5 == 0 else 0), grad=1.8, noise=0.45, nscale=7)
    for c in ("hollow",):
        P_.paint(c, cloth, 0.4, noise=0.1)
    for c in ["pelvis", "spine", "chest0", "chest1", "chest2", "scapula", "neck"] + of("thigh_", "shin_", "foot_", "arm_", "forearm_", "hand_",
                                                                                       "shoulder_"):
        P_.paint(c, skin, 2.6, lambda f, i, j: (0.7 if (i + 2 * j) % 7 == 0 else 0), grad=1.0, noise=0.35)
    # the ropes are soaked dark; the chain wound round each forearm is the flail's own chain
    for c in of("binding_", "rope_", "knot"):
        P_.paint(c, Ramp("#1f1914", "#2e251c", "#3d3226", "#4f4232"), 2.0, lambda f, i, j: (0.7 if (i + j) % 2 == 0 else -0.3))
    for c in of("wind_"):
        P_.paint(c, chain, 2.6, lambda f, i, j: (1.0 if i % 2 == 0 else -0.6), rim=0.8)
    # the sallies: faded stripes of red, white and blue, the one warm thing on it
    stripes = ("#7a2a26", "#cfc6b0", "#2c3f6e")
    for c in of("sally"):
        P_.paint(c, cloth, 3.0)
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                f.set(i, j, hexc(stripes[(j // 2) % 3], 255))
    for c in of("link_"):
        P_.paint(c, chain, 3.0, lambda f, i, j: (0.9 if j == 0 else -0.9 if j == f.h - 1 else 0), rim=1.0)
    # the bells: dark bronze under verdigris that runs down them in streaks, worn bright only at the lip where they strike
    metal = Ramp("#17130e", "#251e15", "#372c1e", "#4d3d27", "#6a5232", "#8e6d3f", "#b8904f")
    verd = ("#264a3e", "#356452", "#4a8268", "#68a585")
    for c in of("bell_", "crown_", "face_bell", "face_crown"):
        P_.paint(c, metal, 2.6, lambda f, i, j: (-0.6 if (i + 2 * j) % 5 == 0 else 0), grad=1.4, rim=0.9, noise=0.3, nscale=4)
        for fname in SIDES:
            f = P_.face(c, fname)
            for i, j in f.each():
                run = (i * 7 + len(c)) % 5  # how far down this column the green has run
                if (i * 5 + 3) % 7 < 3 and j <= run:
                    f.set(i, j, hexc(verd[min(3, 1 + (run - j) // 2 + (i % 2))], 255))
    for c in of("lip_"):
        P_.paint(c, metal, 5.0, lambda f, i, j: (-0.9 if (i * 3) % 5 == 0 else 0), rim=0.8)
    # the light: inside every bell's mouth and on its clapper, the hood bell brightest
    for c in of("clapper_") + ["face_clapper"]:
        P_.paint(c, Ramp("#062a26", "#0c4a42", "#147a6c"), 2.0)
        for fname in ("top", "bottom") + SIDES:
            radial(P_, c, fname, [teal[4], teal[3], teal[2]])
    for c in of("lip_") + ["face_bell1"]:
        g = P_.face(c, "bottom", glow=True)
        for i, j in g.each():
            g.set(i, j, hexc(teal[2] if (i + j) % 2 else teal[1], 230))
    with P_.eyes():
        for c in ("face_bell0", "face_bell1"):
            for fname in ("north", "bottom"):
                g = P_.face(c, fname, glow=True)
                for i, j in g.each():
                    g.set(i, j, hexc(teal[3] if j >= g.h - 1 else teal[1], 200))
    # water still running down the cassock: thin lit cracks
    for c, n in (("front1", 2), ("back1", 1), ("front2", 1)):
        cracks(P_, c, ("north", "south"), cloth.at(1), teal[0:3], n=n, seed=c, alpha=150)
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
