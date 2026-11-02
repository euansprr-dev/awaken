"""The Keeper's Shadow (SPEC §46.9, docs/fights/PLAN.md §4.7): the fifth Proving's Warden, met at the Court's threshold.
The last of the Keepers who went down to hold the Seed: the one left at the Court's door, still testing whoever comes.
Tall and gaunt in a deep black hood and a robe that falls to the floor in long strips, a mantle over its shoulders and a
cape behind; in the hood no face at all, only the dark and two small gold eyes. On the hood a crown of eight gold points,
one for each Sovereign. Eight small gold keys hang from its belt, one for each door. Its sleeves are long and bell at
the wrist; its hands are thin. It fights with a great key as long as a spear: a gold ring of a bow, a black iron shaft
banded in gold, and a bit whose teeth glow violet. Bold and simple (Euan, 2026-10-29: no sculpted faces, epic and
mysterious).

It stands up out of your shadow (its intro: up out of the floor, bowed, then tall, the key coming up to rest) and fights
as a duel: the key in fours and fives (Turn the Key, a thrust that turns in the wound; Bar the Door, a sweep; Unlock, a
rising cut), the Lock (the key lifted in both hands and driven down: a wave), Keyhole (dark bolts from its open hand),
the Turnabout for whoever is behind it, and Shut the Door (its signature: it drives the key's point into the floor and
turns it with both hands, and the line to you breaks open, red). When the doors open it lifts the key and turns it in
the air; then it steps into its own shadow and comes up behind you (Through the Door). When the dark comes in it drives
the key into the floor; then it raises the key to the dome (the Dark Rises) and reaches to keep you (Kept, a red grab).
When the Court's door opens it staggers to one knee and rises; then its echoes step out of it and it turns with the key
over its head, the eight doors' bolts flying out. It dies on its knees, the key laid down before it, and sinks back into
the floor.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "proving_keepers_shadow"
SCALE = 2.7  # on the player-sized HUMAN model: about 5.6 blocks to the top of its crown

P = plans.Biped(leg=32, thigh=16, foot=(3, 2, 6), pelvis=(8, 4, 5), spine=(6, 6, 4), chest=(11, 10, 6), neck=(3, 3, 3),
                head=(7, 8, 7), arm=(3, 15, 3), forearm=(3, 14, 3), hand=(3, 4, 3), stance=2.8, hunch=0, leg_w=(3, 3))
# the key's bit, in the key bone's space (the shaft runs down the bone's +y out of the fist)
KEY_TIP = (0, 38, 0)
KEY = dict(weapon=KEY_TIP, weapon_r=1.8)
# how far the key comes down in its strokes (degrees on its own axis), so every stroke meets someone standing at the
# waist (SPEC §46.6: a damaging blow's hits come down under a standing player's shoulders)
KEY_DOWN = {"bar": 100, "unlock": 30, "turnabout": 105, "turn": 14}
# the reaching hand comes down to you too (degrees)
ARM_DOWN = {"kept": ("arm_l", 26), "bar": ("arm_r", 26), "turn": ("arm_r", 12)}
LOW_UNLOCK, LOW_TURN = 28, 20
# the robe's four panels round the hips: (name, x, z, width, depth, tilt), each three lengths to the ankle
ROBE = [("robe_f", 0, -3.0, 10, 1, 8), ("robe_b", 0, 3.0, 10, 1, -8), ("robe_l", 4.6, 0, 1, 8, 0), ("robe_r", -4.6, 0, 1, 8, 0)]
ROBE_LEN = (9, 9, 10)
# the eight keys on the belt: (x, z, yaw)
KEYS = [(-3.5, -3.2, 0), (-1.2, -3.4, 0), (1.2, -3.4, 0), (3.5, -3.2, 0), (4.8, -1, 90), (4.8, 1.5, 90), (-4.8, -1, -90), (-4.8, 1.5, -90)]
# the crown's eight points round the hood's top: (x, z)
CROWN = [(0, -4.5), (3.2, -3.2), (4.5, 0), (3.2, 3.2), (0, 4.5), (-3.2, 3.2), (-4.5, 0), (-3.2, -3.2)]
# under the floor: where it waits before it stands up, and where it sinks to (pos y, model pixels)
SUNK = -78


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -4, -1, -2.5, 8, 4, 5)
    pel.cube("belt", -4.5, -1.5, -3, 9, 2, 6)
    pel.cube("clasp", -1.5, -1.5, -3.5, 3, 2, 1)
    # the eight keys on the belt, each on its own spring
    for k, (x, z, yaw) in enumerate(KEYS):
        kb = pel.bone(f"bkey{k}", (x, 0.5, z), (0, yaw, 0))
        kb.cube(f"bkey{k}", -0.5, 0, -0.5, 1, 5, 1)
        kb.cube(f"bbow{k}", -1, -1, -0.5, 2, 1, 1)
        kb.cube(f"bbit{k}", 0.5, 3.5, -0.5, 1, 1, 1)
    # the robe: four panels to the ankle, each three lengths on springs, the last one tattered into strips
    for nm, x, z, w, d, tilt in ROBE:
        side = nm[-1] in "lr"
        cl = pel.bone(nm + "0", (x, 1, z), (tilt, 0, (-8 if nm == "robe_l" else 8) if side else 0))
        for k, ln in enumerate(ROBE_LEN):
            if k:
                cl = cl.bone(f"{nm}{k}", (0, ROBE_LEN[k - 1], 0), (tilt * 0.3, 0, ((-3 if nm == "robe_l" else 3) if side else 0)))
            if k < 2:
                cl.cube(f"{nm}{k}", -w / 2, 0, -d / 2, w, ln, d)
            else:
                # the hem in strips of different lengths
                n = 5 if w > d else 2
                for s in range(n):
                    sw = w / n if w > d else w
                    sd = d if w > d else d / n
                    sx = -w / 2 + s * sw if w > d else -w / 2
                    sz = -d / 2 if w > d else -d / 2 + s * sd
                    cl.cube(f"{nm}{k}_{s}", sx, 0, sz, int(sw), ln - (s % 2) * 3 - (s == 2) * 1, int(sd))
    # legs: thin and dark under the robe, small pointed feet
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 1, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 16, 3)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0))
        sn.cube("shin_" + nm, -1.5, 0, -1.5, 3, P.leg - P.thigh - 2, 3)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0))
        ft.cube("foot_" + nm, -1.5, 0, -4.5, 3, 2, 6)
    # the body: gaunt under the robe, a mantle over the shoulders, a cape behind to the knee
    sp = pel.bone("spine", (0, -1, 0))
    sp.cube("spine", -3, -6, -2, 6, 6, 4)
    ch = sp.bone("chest", (0, -6, 0))
    parts.taper(ch, "chest", 0, 0, 0, ((9, 3, 5), (11, 4, 6), (12, 3, 6)))
    ch.cube("mantle", -7.5, -12, -4, 15, 4, 9)
    ch.cube("mantle_f", -6, -9, -4.5, 12, 4, 1)
    cape = ch.bone("cape0", (0, -10, 4.2), (6, 0, 0))
    for k, ln in enumerate((11, 11, 10)):
        if k:
            cape = cape.bone(f"cape{k}", (0, (11, 11, 10)[k - 1], 0), (2, 0, 0))
        cape.cube(f"cape{k}", -6.5 + k * 0.5, 0, -0.5, 13 - k, ln, 1)
    # arms: long and thin in long sleeves that bell at the wrist, gold bands at the wrists; the right holds the key
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 6, -10, 0))
        sh.cube("shoulder_" + nm, -2, -2, -2, 4, 4, 4)
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-4, 0, -side * 5))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 15, 3)
        arm.cube("sleeve_" + nm, -2.5, 0, -2.5, 5, 13, 5)
        fa = arm.bone("forearm_" + nm, (0, 14, 0), (-10, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 14, 3)
        fa.cube("cuff_" + nm, -3, 6, -3, 6, 6, 6)
        fa.cube("band_" + nm, -2, 11, -2, 4, 1, 4)
        hd_ = fa.bone("hand_" + nm, (0, 13, 0))
        hd_.cube("hand_" + nm, -1.5, 0, -1, 3, 3, 2)
        hd_.cube("fingers_" + nm, -1.5, 3, -0.5, 3, 3, 1)
    # the great key: a gold ring of a bow under the fist, a black iron shaft banded in gold, the bit's teeth at its end
    # it rests pointed out ahead of it and down (its +y runs forward-down out of the fist)
    ky = r.by_name["hand_r"].bone("key", (0, 1.5, 0), (-60, 0, 0))
    ky.cube("shaft", -1, -9, -1, 2, 38, 2)
    for nm_, x, y, w, h in (("bow_t", -3.5, -17, 7, 1), ("bow_b", -3.5, -11, 7, 1), ("bow_l", 2.5, -16, 1, 5), ("bow_r", -3.5, -16, 1, 5)):
        ky.cube(nm_, x, y, -0.5, w, h, 1)
    for k, y in enumerate((-9, 9, 26)):
        ky.cube(f"kband{k}", -1.5, y, -1.5, 3, 1, 3)
    ky.cube("bit0", 1, 29, -0.5, 4, 2, 1)
    ky.cube("bit1", 1, 32, -0.5, 3, 2, 1)
    ky.cube("bit2", 1, 35, -0.5, 5, 2, 1)
    ky.cube("ktip", -1, 36, -1, 2, 2, 2)
    # the head: a deep peaked hood, the dark in it with two gold eyes, the crown of eight points on it
    nk = ch.bone("neck", (0, -12, -0.5))
    nk.cube("neck", -1.5, -3, -1.5, 3, 3, 3)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("void", -3.5, -8, -3.5, 7, 8, 7)
    hd.cube("face", -3.5, -7.5, -4.2, 7, 7, 1)
    hd.cube("hood_t", -5, -11, -4.5, 10, 2, 10)
    hd.cube("hood_l", 4, -9, -4.5, 1, 10, 10)
    hd.cube("hood_r", -5, -9, -4.5, 1, 10, 10)
    hd.cube("hood_b", -5, -9, 4.5, 10, 10, 1)
    hd.cube("hood_peak", -2.5, -12.5, -5.5, 5, 2, 3)
    cr = hd.bone("crown", (0, -11.5, 0))
    cr.cube("circlet_f", -4.5, -1, -4.5, 9, 1, 1)
    cr.cube("circlet_b", -4.5, -1, 3.5, 9, 1, 1)
    cr.cube("circlet_l", 3.5, -1, -3.5, 1, 1, 7)
    cr.cube("circlet_r", -4.5, -1, -3.5, 1, 1, 7)
    for k, (x, z) in enumerate(CROWN):
        h = 4 if k % 2 == 0 else 3
        cr.cube(f"point{k}", x - 0.5, -1 - h, z - 0.5, 1, h, 1)
    r.look = [("head", 0.6, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.9
    # what hangs swings (SPEC §35.10): the robe, the cape, the keys on the belt
    r.springs = [(f"{nm}{k}", g, hz, 0.3) for nm, *_ in ROBE for k, (g, hz) in enumerate(((0.4, 2.4), (0.35, 2.1), (0.3, 1.9)))] \
        + [(f"cape{k}", g, hz, 0.3) for k, (g, hz) in enumerate(((0.45, 2.2), (0.4, 2.0), (0.35, 1.8)))] \
        + [(f"bkey{k}", 0.25, 3.4, 0.4) for k in range(len(KEYS))]
    r.guard = ["key"]  # the key never goes into the floor but where a move strikes it
    r.under = ["move_emerge", "move_doorway"]  # up out of the floor, and down into its own shadow
    return r


SWING = [(["key"], [("hand_r", 0, 60), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]
CLOTH = [([nm + "2"], [(nm + "0", 0 if nm in ("robe_f", "robe_b") else 2, 70), (nm + "1", 0 if nm in ("robe_f", "robe_b") else 2, 60)])
         for nm, *_ in ROBE] + [(["cape2"], [("cape0", 0, 70), ("cape1", 0, 60)])]


def sunk(f=1.0, bow=1.0):
    """Under the floor (f 1) or coming up out of it, bowed, the arms hanging, the key trailing."""
    return {"pelvis": {"rot": (14 * bow, 0, 0), "pos": (0, SUNK * f, 2 * bow)}, "spine": (14 * bow, 0, 0), "chest": (20 * bow, 0, 0),
            "head": (34 * bow, 0, 0), "arm_l": (6, 0, -8), "arm_r": (6, 0, 8), "forearm_l": (-6, 0, 0), "forearm_r": (-6, 0, 0),
            "key": (70, 0, 0), "thigh_l": (-30 * bow, 0, 0), "shin_l": (40 * bow, 0, 0), "thigh_r": (-20 * bow, 0, 0), "shin_r": (30 * bow, 0, 0)}


def clips(r):
    plans.biped_locomotion(r, P, weight=1.05, temper="regal")
    plans.biped_reactions(r, weight=1.05)
    plans.biped_duel_reactions(r, weight=1.05, lead="r")
    # standing, it breathes slowly, the key's point barely moving, the head turning a little as if listening
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("key", [(0, 0, 0, 0), (L * 0.5, -3, 0, 0), (L, 0, 0, 0)])
    idle.rot("crown", [(0, 0, 0, 0), (L * 0.5, 0, 6, 0), (L, 0, 0, 0)])
    # the death: down on its knees, the key laid down before it, its head bowed; then it sinks back into the floor
    kneel = {"pelvis": {"rot": (6, 0, 0), "pos": (0, -15, 1)}, "thigh_l": (-86, 0, 0), "shin_l": (88, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (-86, 0, 0), "shin_r": (88, 0, 0), "foot_r": (-2, 0, 0), "spine": (10, 0, 0), "chest": (16, 0, 0),
             "head": (34, 0, 0), "arm_l": (-20, 0, -6), "arm_r": (-40, 0, 6), "forearm_l": (-10, 0, 0), "forearm_r": (-20, 0, 0), "key": (90, 0, 0)}
    laid = dict(kneel, arm_r=(-60, 0, 4), forearm_r=(-10, 0, 0), key=(150, 0, 0), chest=(30, 0, 0), head=(54, 0, 0), spine=(16, 0, 0))
    gone = dict(laid, pelvis={"rot": (6, 0, 0), "pos": (0, SUNK, 1)})
    keyed(r, "death", 3.2, [(0, {}), (0.3, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-30, 0, -40), "arm_r": (-30, 0, 30)}),
                            (1.0, kneel), (1.7, laid), (2.3, laid), (3.2, gone)])
    mv = [
        # the key in fours and fives: Turn the Key, Bar the Door, Unlock; the Lock overhead (held)
        turn(),
        M.sweep(P, "bar", side="r", title="Bar the Door", windup=11, active=4, recover=12, weapon_bone="key", rng=(0, 6.2), damage=1.0,
                kinds=("combo",), **KEY),
        M.backhand(P, "unlock", side="r", title="Unlock", windup=11, active=4, recover=12, kinds=("combo",), rng=(0, 5.2), damage=0.9,
                   low=LOW_UNLOCK, weapon_bone="key", **KEY),
        M.overhead_slam(P, "lock", title="The Lock", windup=20, hold=8, active=5, recover=20, ability="The Lock", rng=(0, 5.5), damage=1.3),
        M.palm_cast(P, "keyhole", side="l", title="Keyhole", windup=12, active=6, recover=14, cls="LIGHT", ability="Keyhole", rng=(5, 20)),
        M.spin_cut(P, "turnabout", side="r", title="Turnabout", cls="HEAVY", windup=15, active=10, recover=16, kinds=("area", "behind"),
                   rng=(0, 6.0), damage=1.2, weapon_bone="key", low=LOW_TURN, drop=0, **KEY),
        shut(),
        # when the doors open: Through the Door
        doorway(),
        # when the dark comes in: the Dark Rises, and Kept (a red grab)
        M.sky_call(P, "dark", title="The Dark Rises", windup=20, active=10, recover=16, ability="The Dark Rises", rng=(0, 30)),
        M.lunge_cut(P, "kept", side="l", title="Kept", cls="GRAB", windup=18, active=6, recover=18, advance=4.0, kinds=("gap",),
                    rng=(3, 10), damage=1.3),
        # when the Court's door opens: its echoes, and the Eight Doors
        echoes(),
        eight(),
        emerge(),
        doors(),
        darkfall(),
        last(),
    ]
    by = {m.id: m for m in mv}
    # it's nearly three times your height: in its strokes the key comes down to meet you at the waist
    for mid, add in KEY_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            rx = pose.get("key", (0, 0, 0))
            pose["key"] = (rx[0] + add, rx[1], rx[2])
    for mid, (bone, add) in ARM_DOWN.items():
        seen = set()
        for f, pose in by[mid].stages["windup"][-1:] + by[mid].stages["active"]:
            if id(pose) in seen:
                continue
            seen.add(id(pose))
            x = pose.get(bone, (0, 0, 0))
            pose[bone] = (x[0] + add, x[1], x[2])
    # the Turnabout and the Eight Doors go once right round it
    for mid in ("turnabout", "eight"):
        for k, (f, pose) in enumerate(by[mid].stages["active"]):
            rot = pose.get("pelvis", {"rot": (0, 0, 0), "pos": (0, 0, 0)})
            pose["pelvis"] = {"rot": (rot["rot"][0], rot["rot"][1] + 360 * f, rot["rot"][2]), "pos": rot.get("pos", (0, 0, 0))}
    # in the strokes the free left hand stays back at its hip, open
    for mid in ("turn", "bar", "unlock", "turnabout"):
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            pose.setdefault("arm_l", (-14, 0, -16))
            pose.setdefault("forearm_l", (-36, 0, 0))
    for m in mv:
        stage_clips(r, m)
    # a turn comes out of its turn facing where it began: the recovery eases on to 360, never back round
    for mid in ("turnabout", "eight"):
        yaw = r.clips["move_" + mid].tracks[("pelvis", "rot")]
        top = max(range(len(yaw)), key=lambda k: yaw[k][1][1])
        t0, t1 = yaw[top][0], yaw[-1][0]
        for k in range(top + 1, len(yaw)):
            t, (x, y, z), *rest = yaw[k]
            u = (t - t0) / max(1e-6, t1 - t0)
            ease = u * u * (3 - 2 * u)
            yaw[k] = (t, (x, 360 + (yaw[top][1][1] - 360) * (1 - ease), z), *rest)
    under = ("emerge", "doorway")  # moves that go through the floor: their feet aren't on it
    for m in mv:
        if m.advance == 0 and m.rise == 0 and m.id not in under:
            core.lock_feet(r, core.clip_name(m))
    for nm in ("idle", "parried", "reel", "hurt", "hurt_l", "hurt_r"):
        if nm in r.clips:
            core.lock_feet(r, nm)
    # the key stays out of the floor (the Lock and Shut the Door meet it and stop there); the robe lifts off it
    skip = {"death", "kneel", "rise"} | {"move_" + m for m in under}
    core.ground_pass(r, SWING, clips=[n for n in r.clips if n not in skip])
    core.ground_pass(r, CLOTH, clips=[n for n in r.clips if n not in skip], skip=())
    return mv


def turn():
    """Turn the Key (LIGHT): it sets its feet and drives the key's bit straight out at your chest, then turns its wrist
    as if in a lock, the left hand back at its hip."""
    set_ = {"pelvis": {"rot": (4, -18, 0), "pos": (0, -2, 2)}, "chest": (-4, -26, 0), "spine": (0, -8, 0), "head": (0, 22, 0),
            "arm_r": (-30, 20, 30), "forearm_r": (-80, 0, 0), "key": (60, 0, 0), "thigh_l": (-20, 0, 0), "shin_l": (24, 0, 0)}
    drive = {"pelvis": {"rot": (10, 16, 0), "pos": (0, -3, -2)}, "chest": (10, 20, 0), "spine": (4, 8, 0), "head": (0, -14, 0),
             "arm_r": (-82, -6, 0), "forearm_r": (-4, 0, 0), "key": (72, 0, 0), "thigh_l": (-34, 0, 0), "shin_l": (30, 0, 0)}
    turned = dict(drive, hand_r=(0, 0, 80))
    return Move("turn", "LIGHT", 11, 4, 12, title="Turn the Key", track=10.0, rng=(0, 6.6), kinds=("combo",), damage=1.0, knock=0.6,
                advance=1.0, hit=[("key", KEY_TIP, 1.8), ("key", (0, 22, 0), 1.4)],
                stages={"windup": [(1.0, set_)], "active": [(0.5, drive), (1.0, turned)],
                        "recover": [(0.5, core._scaled(turned, 0.4))]},
                lag={"arm_r": 0.02, "forearm_r": 0.04, "cape0": 0.05, "head": 0.03})


def shut():
    """Shut the Door (its signature, red): it lifts the key in both hands, point down, holds it there over the floor in
    front of it, drives the point in, and turns it with its whole body: the line to you breaks open (dash aside)."""
    lift = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-10, 0, 0), "head": (-6, 0, 0),
            "arm_r": (-150, 0, 14), "forearm_r": (-30, 0, 0), "arm_l": (-150, 0, -14), "forearm_l": (-30, 0, 0), "key": (-110, 0, 0)}
    drive = {"pelvis": {"rot": (16, 0, 0), "pos": (0, -8, -1)}, "spine": (12, 0, 0), "chest": (26, 0, 0), "head": (10, 0, 0),
             "arm_r": (-64, 8, 0), "forearm_r": (-8, 0, 0), "arm_l": (-64, -8, 0), "forearm_l": (-8, 0, 0), "key": (104, 0, 0),
             "thigh_l": (-34, 0, 0), "shin_l": (46, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (44, 0, 0), "foot_l": (-12, 0, 0), "foot_r": (-12, 0, 0)}
    turned = dict(drive, chest=(26, 34, 0), spine=(12, 14, 0), head=(10, -20, 0), hand_r=(0, 0, 90), arm_l=(-60, -24, 0),
                  pelvis={"rot": (16, 10, 0), "pos": (0, -8, -1)})
    return Move("shut", "PERILOUS", 22, 12, 22, title="Shut the Door", track=6.0, lock=14, ability="Shut the Door", rng=(4, 18),
                kinds=("ranged", "gap"), signature=True, damage=0, knock=0, plant=("l", "r"),
                stages={"windup": [(0.4, core._scaled(lift, 0.5)), (1.0, lift)], "active": [(0.25, drive), (0.6, drive), (1.0, turned)],
                        "recover": [(0.4, turned), (0.8, core._scaled(turned, 0.4))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.04, "cape0": 0.05})


def doorway():
    """Through the Door: it draws its arms in and sinks into its own shadow, gone; and comes up out of the floor behind
    you, rising tall with the key coming up."""
    gather = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -4, 0)}, "chest": (14, 0, 0), "head": (24, 0, 0), "arm_l": (-30, 0, 20),
              "arm_r": (-30, 0, -20), "forearm_l": (-70, 0, 0), "forearm_r": (-50, 0, 0), "key": (100, 0, 0)}
    return Move("doorway", "NONE", 14, 14, 12, title="Through the Door", track=0.0, ability="Through the Door", rng=(4, 99), kinds=("ranged",),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, gather), (1.0, sunk(1.0))], "active": [(0.3, sunk(1.0)), (0.75, sunk(0.35, 0.8)), (1.0, sunk(0.0, 0.4))],
                        "recover": [(0.5, sunk(0.0, 0.15))]},
                lag={"head": 0.05, "cape0": 0.06})


def echoes():
    """Echoes of the Court: it draws the key across its chest and bows; then flings both arms out wide and its echoes
    step out of it to either side."""
    bow = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -3, 0)}, "chest": (16, 0, 0), "head": (26, 0, 0), "arm_r": (-70, -30, 0),
           "forearm_r": (-60, 0, 0), "key": (20, 0, 0), "arm_l": (-70, 30, 0), "forearm_l": (-60, 0, 0)}
    fling = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-14, 0, 0), "head": (-22, 0, 0), "arm_r": (-30, 0, 70),
             "forearm_r": (-10, 0, 0), "arm_l": (-30, 0, -70), "forearm_l": (-10, 0, 0), "key": (40, 0, 0)}
    return Move("echoes", "NONE", 16, 22, 14, title="Echoes of the Court", track=0.0, ability="Echoes of the Court", rng=(0, 99),
                kinds=("summon",), damage=0, knock=0,
                stages={"windup": [(0.5, core._scaled(bow, 0.6)), (1.0, bow)], "active": [(0.2, fling), (1.0, fling)],
                        "recover": [(0.5, core._scaled(fling, 0.4))]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "head": 0.05, "cape0": 0.06})


def eight():
    """The Eight Doors (red): it lifts the key over its head in both hands, holds it, and turns once right round; a bolt
    flies from it toward each of the doors (dash through the gap)."""
    up = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-10, 0, 0), "head": (-16, 0, 0), "arm_r": (-170, 0, 20),
          "forearm_r": (-20, 0, 0), "arm_l": (-170, 0, -20), "forearm_l": (-20, 0, 0), "key": (-80, 0, 90)}
    spin = dict(up, pelvis={"rot": (4, 0, 0), "pos": (0, -2, 0)}, chest=(-4, 0, 0))
    return Move("eight", "PERILOUS", 18, 12, 16, title="Eight Doors", track=0.0, ability="Eight Doors", rng=(0, 30), kinds=("area", "ranged"),
                damage=0, knock=0, plant=(),
                stages={"windup": [(0.4, core._scaled(up, 0.5)), (1.0, up)], "active": [(0.2, spin), (1.0, spin)],
                        "recover": [(0.5, core._scaled(spin, 0.4))]},
                lag={"arm_l": 0.04, "arm_r": 0.04, "head": 0.05, "cape0": 0.06, "cape1": 0.06})


def emerge():
    """Its intro: it comes up out of the pool your shadow made, bowed, its arms hanging, the key trailing; it stands to
    its full height and lifts its head to you, the key coming up to rest."""
    tall = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "spine": (-6, 0, 0), "chest": (-10, 0, 0), "head": (-14, 0, 0),
            "arm_l": (-30, 0, -30), "forearm_l": (-20, 0, 0), "arm_r": (-30, 0, 20), "key": (40, 0, 0)}
    return Move("emerge", "NONE", 20, 24, 14, title="", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.0, sunk(1.0)), (1.0, sunk(0.55, 1.0))], "active": [(0.5, sunk(0.0, 0.7)), (1.0, tall)],
                        "recover": [(0.6, core._scaled(tall, 0.3))]},
                lag={"head": 0.06, "arm_l": 0.05, "arm_r": 0.05, "cape0": 0.06})


def doors():
    """The turn at three quarters: it lifts the key high in one hand and turns it in the air, as in a great lock; the
    doors open."""
    up = {"arm_r": (-170, 0, 10), "forearm_r": (-6, 0, 0), "key": (-170, 0, 0), "chest": (-12, 0, 0), "head": (-24, 0, 0),
          "arm_l": (-30, 0, -40), "forearm_l": (-20, 0, 0), "pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}}
    turned = dict(up, hand_r=(0, 0, 90))
    return Move("doors", "NONE", 12, 30, 12, title="The doors open", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, core._scaled(up, 0.6))], "active": [(0.3, up), (0.7, turned), (1.0, turned)],
                        "recover": [(0.5, core._scaled(turned, 0.4))]},
                lag={"arm_r": 0.03, "head": 0.05, "cape0": 0.06})


def darkfall():
    """The turn at half: it lifts the key in both hands and drives it down into the floor; the dark comes in."""
    up = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 1, 1)}, "spine": (-6, 0, 0), "chest": (-12, 0, 0), "head": (-14, 0, 0),
          "arm_r": (-170, 0, 12), "forearm_r": (-10, 0, 0), "arm_l": (-170, 0, -12), "forearm_l": (-10, 0, 0), "key": (-150, 0, 0)}
    drive = {"pelvis": {"rot": (14, 0, 0), "pos": (0, -8, -1)}, "spine": (12, 0, 0), "chest": (24, 0, 0), "head": (8, 0, 0),
             "arm_r": (-70, 10, 0), "forearm_r": (-6, 0, 0), "arm_l": (-70, -10, 0), "forearm_l": (-6, 0, 0), "key": (116, 0, 0),
             "thigh_l": (-30, 0, 0), "shin_l": (44, 0, 0), "thigh_r": (-30, 0, 0), "shin_r": (44, 0, 0), "foot_l": (-12, 0, 0),
             "foot_r": (-12, 0, 0)}
    return Move("darkfall", "NONE", 16, 24, 14, title="The dark comes in", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                plant=("l", "r"), stages={"windup": [(0.4, core._scaled(up, 0.5)), (1.0, up)], "active": [(0.3, drive), (1.0, drive)],
                                          "recover": [(0.5, core._scaled(drive, 0.5))]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.04, "cape0": 0.05})


def last():
    """The turn at a quarter: the Court's door is opening behind it; it staggers down onto one knee, the key's point to
    the floor, and gets up again slowly, its head lifting."""
    knee = {"pelvis": {"rot": (8, 0, 0), "pos": (0, -10, 1)}, "thigh_l": (-80, 0, 0), "shin_l": (40, 0, 0), "thigh_r": (10, 0, 0),
            "shin_r": (80, 0, 0), "foot_r": (-30, 0, 0), "spine": (10, 0, 0), "chest": (18, 0, 0), "head": (30, 0, 0),
            "arm_r": (-50, 0, 6), "forearm_r": (-10, 0, 0), "key": (70, 0, 0), "arm_l": (-10, 0, -10)}
    up = {"pelvis": {"rot": (-4, 0, 0), "pos": (0, 0.5, 0)}, "chest": (-12, 0, 0), "head": (-20, 0, 0), "arm_l": (-40, 0, -40),
          "forearm_l": (-20, 0, 0), "arm_r": (-30, 0, 20), "key": (40, 0, 0)}
    return Move("last", "NONE", 10, 34, 14, title="The Court's door opens", track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                plant=(), stages={"windup": [(1.0, knee)], "active": [(0.4, knee), (0.75, core._scaled(knee, 0.5)), (1.0, up)],
                                  "recover": [(0.5, core._scaled(up, 0.4))]},
                lag={"head": 0.05, "arm_l": 0.04, "cape0": 0.06})


# Its sounds: bells, chains and the dark under the floor. Chosen on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.chain.step", 1.2, 0.7), ("block.sculk.step", 1.0, 0.8)],
    "tell_heavy": [("block.bell.resonate", 1.6, 0.5), ("block.sculk_shrieker.shriek", 0.8, 0.6)],
    "tell_perilous": [("block.bell.use", 1.8, 0.45), ("block.beacon.power_select", 1.4, 0.5)],
    "tell_grab": [("entity.warden.heartbeat", 1.8, 0.7), ("block.sculk.charge", 1.6, 0.5)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.7), ("block.chain.break", 0.8, 0.8)],
    "swing_heavy": [("entity.player.attack.sweep", 1.8, 0.5), ("block.iron_door.close", 1.2, 0.5)],
    "cast": [("block.sculk_catalyst.bloom", 1.6, 0.8), ("block.amethyst_block.chime", 1.2, 0.5)],
    "impact": [("block.iron_door.close", 2.0, 0.4), ("block.deepslate_bricks.break", 1.8, 0.5)],
    "roar": [("block.bell.resonate", 2.4, 0.35), ("entity.warden.emerge", 1.6, 0.8)],
    "step": [("block.sculk.step", 0.8, 0.7), ("block.chain.step", 0.5, 0.9)],
    "blink": [("entity.warden.dig", 1.4, 1.0), ("block.sculk.spread", 1.2, 0.6)],
}
MOVE_SOUNDS = {"emerge": {"swing": "roar"}, "doors": {"swing": "roar"}, "darkfall": {"swing": "impact"}, "last": {"swing": "roar"},
               "doorway": {"swing": "blink", "impact": ""}, "echoes": {"swing": "blink", "impact": ""}, "shut": {"swing": "impact"},
               "eight": {"swing": "roar"}}

FIGHT = {
    # a duel (SPEC §46.3): phrases of four and five, the key's reach covering the ground round it
    "duel": True,
    "stance": 420,  # tuned with the sim (SPEC §46.9)
    "intro": {"pose": "stand", "move": "emerge"},
    "phases": [
        # the court: the key in fours, the Lock, Keyhole, the Turnabout, Shut the Door
        {"pool": [
            {"move": "turn", "weight": 3, "then": [["bar", "unlock", "turn"], ["bar", "turn", "lock"]]},
            {"move": "bar", "weight": 2, "then": [["turn", "unlock", "bar"], ["unlock", "lock"]]},
            {"move": "lock", "weight": 2, "then": [["turn", "bar", "unlock"]]},
            {"move": "keyhole", "weight": 2, "when": "far", "then": [["keyhole", "shut"], ["shut", "turn", "bar"]]},
            {"move": "shut", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock"]]},
            {"move": "turnabout", "weight": 2, "when": "behind", "then": [["turn", "bar", "unlock"]]},
        ]},
        # the doors open: Through the Door after whoever keeps away; phrases of four and five
        {"transition": "doors", "pool": [
            {"move": "turn", "weight": 3, "then": [["bar", "unlock", "turn", "lock"], ["bar", "turn", "unlock"]]},
            {"move": "bar", "weight": 2, "then": [["turn", "unlock", "bar", "turnabout"], ["unlock", "turn", "lock"]]},
            {"move": "lock", "weight": 2, "then": [["turn", "bar", "unlock"], ["doorway"]]},
            {"move": "keyhole", "weight": 2, "when": "far", "then": [["keyhole", "doorway"], ["shut", "turn", "bar"]]},
            {"move": "doorway", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "turn"]]},
            {"move": "shut", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "lock"]]},
            {"move": "turnabout", "weight": 2, "when": "behind", "then": [["turn", "bar", "unlock"]]},
        ]},
        # the dark comes in: the Dark Rises under you, and Kept for whoever stands off; phrases of five
        {"transition": "darkfall", "pool": [
            {"move": "turn", "weight": 3, "then": [["bar", "unlock", "turn", "lock"], ["bar", "turn", "unlock", "kept"]]},
            {"move": "bar", "weight": 2, "then": [["turn", "unlock", "bar", "turnabout"], ["unlock", "turn", "bar", "lock"]]},
            {"move": "lock", "weight": 1, "then": [["turn", "bar", "unlock", "dark"]]},
            {"move": "dark", "weight": 2, "then": [["doorway", "turn", "bar"], ["keyhole", "shut"]]},
            {"move": "keyhole", "weight": 2, "when": "far", "then": [["keyhole", "kept"], ["dark", "doorway"]]},
            {"move": "keyhole", "weight": 2, "when": "air", "then": [["keyhole", "keyhole"]]},
            {"move": "kept", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock"]]},
            {"move": "doorway", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "turn"]]},
            {"move": "shut", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "lock"]]},
            {"move": "turnabout", "weight": 2, "when": "behind", "then": [["turn", "bar", "unlock", "turn"]]},
        ]},
        # the Court's door opens: its echoes and the Eight Doors; phrases of five and six
        {"transition": "last", "pool": [
            {"move": "turn", "weight": 3, "then": [["bar", "unlock", "turn", "lock"], ["bar", "turn", "unlock", "turn", "turnabout"]]},
            {"move": "bar", "weight": 2, "then": [["turn", "unlock", "bar", "kept"], ["unlock", "turn", "bar", "lock"]]},
            {"move": "lock", "weight": 1, "then": [["turn", "bar", "unlock", "eight"]]},
            {"move": "echoes", "weight": 1, "then": [["turn", "bar", "unlock", "turn"], ["eight"]]},
            {"move": "eight", "weight": 2, "then": [["doorway", "turn", "bar"], ["dark", "keyhole"]]},
            {"move": "dark", "weight": 2, "then": [["doorway", "turn", "bar", "unlock"], ["shut"]]},
            {"move": "keyhole", "weight": 2, "when": "far", "then": [["keyhole", "shut"], ["eight"]]},
            {"move": "keyhole", "weight": 3, "when": "air", "then": [["keyhole", "eight"]]},
            {"move": "kept", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "turn"]]},
            {"move": "doorway", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "turn"]]},
            {"move": "shut", "weight": 2, "when": "far", "then": [["turn", "bar", "unlock", "lock"]]},
            {"move": "turnabout", "weight": 2, "when": "behind", "then": [["turn", "bar", "unlock", "turn"]]},
        ]},
    ],
    "enrage": {"below": 0.1, "speed": 1.2},
}


def paint(r, P_):
    """Four materials in a value order: the light brightest (the eyes in the hood, the key's teeth, a violet glow), then
    gold (the crown, the keys, the belt, the bands, the key's bow), then the black iron of the key's shaft, then the
    cloth: a black with violet in its folds. The darkest thing on it is the void in its hood."""
    cloth = Ramp("#07060b", "#0f0d17", "#1a1626", "#2a2338", "#3c3150", "#55476e")
    gold = Ramp("#3a2a10", "#5c4318", "#8a6624", "#b88d36", "#dcb85a", "#f2dc94")
    iron = Ramp("#0c0c10", "#16161c", "#22222a", "#33333d", "#474754")
    skin = Ramp("#0a0a0e", "#121218", "#1c1c24", "#282832")
    violet = ("#6a4fb0", "#a080f0", "#e0d0ff")
    of = lambda *ps: parts.cubes_of(r, *ps)
    for c in of("robe_", "cape", "mantle", "sleeve_", "cuff_", "chest", "spine", "pelvis", "hood_", "shoulder_", "arm_"):
        P_.paint(c, cloth, 3.2, lambda f, i, j: (-0.7 if i % 4 == 1 else 0.3 if i % 4 == 3 else 0), grad=1.8, rim=0.8, noise=0.25, nscale=6)
    for c in of("thigh_", "shin_", "foot_", "forearm_", "hand_", "fingers_", "neck"):
        P_.paint(c, skin, 2.0, lambda f, i, j: 0, grad=0.8, noise=0.2)
    for c in of("belt", "clasp", "band_", "kband", "bow_", "circlet_", "point", "bkey", "bbow", "bbit"):
        P_.paint(c, gold, 4.0, lambda f, i, j: (-0.8 if (i + j) % 3 == 0 else 0.3), grad=1.4, rim=1.0, noise=0.2, nscale=3)
    P_.paint("shaft", iron, 3.0, lambda f, i, j: (0.6 if i == 0 else 0), grad=0.8, rim=0.8, noise=0.15)
    # the hem's strips and the cape's end fade to the darkest
    for c in of("robe_f2", "robe_b2", "robe_l2", "robe_r2", "cape2"):
        P_.paint(c, Ramp("#030305", "#07060b", "#0f0d17", "#1a1626"), 2.6, lambda f, i, j: (-1.0 if j >= f.h - 3 else 0), grad=2.2, noise=0.3)
    # the void in the hood: black, and nothing else
    for c in of("void", "face"):
        P_.paint(c, Ramp("#000000", "#020203", "#040406"), 1.0, lambda f, i, j: 0, noise=0.05)
    # the key's teeth and its tip: violet light
    for c in of("bit", "ktip"):
        P_.paint(c, iron, 4.0, lambda f, i, j: 0, rim=1.0)
        for fname in ("top", "bottom") + SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(violet[1] if (i + j) % 2 else violet[2], 220))
    edge_glow(P_, "shaft", "north", violet[0], rows=1, alpha=90, side="left")
    # the eyes: two small gold lights in the dark of the hood
    with P_.eyes():
        f = P_.face("face", "north")
        g = P_.face("face", "north", glow=True)
        for i, j in f.each():
            f.set(i, j, hexc("#000000", 255))
        for ex in (1, f.w - 2):
            for ey in (2,):
                f.set(ex, ey, hexc("#fff0b8", 255))
                g.set(ex, ey, hexc("#f6d77a", 255))
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
