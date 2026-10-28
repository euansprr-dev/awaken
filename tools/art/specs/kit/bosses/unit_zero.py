"""Unit Zero (SPEC §34, docs/bosses/PLAN.md §5): the Precursor gate's last floor. A Duelist about three blocks tall.

A lean construct of pale ceramic plates over dark joints, a square halo behind its head, a visor for a face and a small
core in its chest; its light story is one cold blue running down its seams. It waits on one knee with its halo dark,
rises when you come in, and fights fast: Zero Lance (the palm drawn back, the halo spinning up and tilting at you, a
PERILOUS beam that follows you until the last third of the tell), Phase Step (it blinks beside you already cutting),
Severing (three palm-edge cuts, the third held high for a beat), a spin cut for whoever is behind it, Deploy Coils,
Orbital Mark (the halo rises and marks fall) and Zero Point (it lifts, curls in, then bursts open: the signature).
It dies folding back down to the kneel it started in.
"""
from kit import moves as M
from kit import plans
from kit.core import Move, keyed, stage_clips
from kit.paint import edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "unit_zero"
SCALE = 1.65  # the hitbox on the player-sized HUMAN model: 1 x 3 blocks

P = plans.Biped(leg=22, thigh=11, foot=(5, 3, 8), pelvis=(10, 5, 7), spine=(8, 6, 6), chest=(14, 9, 8), neck=(3, 3, 3),
                head=(8, 8, 8), arm=(4, 10, 4), forearm=(5, 11, 5), hand=(4, 4, 4), stance=3, hunch=0, leg_w=(5, 4))


def body():
    r = plans.biped(ID, P, tex=(256, 256))
    b = r.by_name
    hd = b["head"]
    hd.cube("visor", -4.5, -5, -7, 9, 3, 1)
    hd.cube("crest", -1, -10, -5, 2, 2, 8)
    halo = hd.bone("halo", (0, -4, 5))
    halo.cube("halo_t", -7, -7, 0, 14, 1, 1)
    halo.cube("halo_b", -7, 6, 0, 14, 1, 1)
    halo.cube("halo_l", 6, -6, 0, 1, 12, 1)
    halo.cube("halo_r", -7, -6, 0, 1, 12, 1)
    for k, (x, y) in enumerate(((0, -7), (0, 6), (6, 0), (-7, 0))):
        halo.cube(f"node{k}", x - 0.5, y - 0.5, -0.5, 2, 2, 2)
    halo.cube("spoke", -0.5, -6, 0, 1, 12, 1)
    # the halo rides a stalk off the back of the head (to its spoke), so it never floats
    hd.cube("stalk", -0.5, -5, 1, 1, 1, 4)
    core = b["chest"].bone("core", (0, -5, -5))
    core.cube("core", -1.5, -1.5, -0.5, 3, 3, 1)
    b["chest"].cube("collar", -5, -10, -4, 10, 2, 7)
    for k in (0, 1):
        b["chest"].cube(f"fin{k}", -3 + k * 5, -8, 3, 1, 6, 3)
    b["pelvis"].cube("hip", -5.5, -1, -4, 11, 2, 8)
    for side, nm in ((1, "l"), (-1, "r")):
        mir = side < 0
        b["arm_" + nm].cube("pauldron_" + nm, -2.5, -3, -2.5, 5, 4, 5, mirror=mir)
        b["forearm_" + nm].cube("plating_" + nm, -3, 2, -3, 6, 7, 6, mirror=mir)
        b["shin_" + nm].cube("greave_" + nm, -2.5, 1, -3, 5, 5, 2, mirror=mir)
    r.shadow = 0.7
    return r


def _halo(mv, keys):
    """Adds the halo's spin and tilt to a move's stages: keys = {stage: [(f, (tilt, rise, spin))]}."""
    for stage, ks in keys.items():
        poses = mv.stages.setdefault(stage, [])
        for f, (tilt, rise, spin) in ks:
            hit = next((p for ff, p in poses if abs(ff - f) < 1e-6), None)
            if hit is None:
                hit = {}
                poses.append((f, hit))
                poses.sort(key=lambda e: e[0])
            hit["halo"] = {"rot": (tilt, 0, spin), "pos": (0, rise, 0)}


def clips(r):
    plans.biped_locomotion(r, P, weight=0.8, temper="regal")
    plans.biped_reactions(r, weight=0.8)
    # the halo turns slowly while it stands (its idle) and the death folds it back to the kneel it began in
    idle = r.clips["idle"]
    idle.rot("halo", [(0, 0, 0, 0), (idle.length / 2, 0, 0, 180), (idle.length, 0, 0, 360)], "lin")
    kneel = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -8, 1)}, "thigh_l": (-80, 0, 0), "shin_l": (82, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (8, 0, 0), "shin_r": (90, 0, 0), "foot_r": (60, 0, 0), "spine": (14, 0, 0), "chest": (16, 0, 4),
             "head": (24, 0, 0), "arm_r": (-30, 0, 10), "forearm_r": (-20, 0, 0), "arm_l": (-50, 0, -6), "forearm_l": (-40, 0, 0)}
    slump = dict(kneel)
    slump.update({"chest": (34, 0, 6), "head": (40, 0, 8), "arm_r": (-6, 0, 8), "arm_l": (-10, 0, -4), "forearm_l": (-6, 0, 0),
                  "halo": {"rot": (60, 0, 0), "pos": (0, -6, 0)}})
    keyed(r, "death", 2.2, [(0, {}), (0.25, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-20, 0, -40), "arm_r": (-20, 0, 40)}),
                            (0.9, kneel), (1.5, slump), (2.2, slump)])
    mv = [
        # the lance never fires up close (a beam from a palm at 4 blocks read as a cut you couldn't dodge), and its aim
        # holds still for the last half second of the tell: the line goes bright, then it fires down that line
        M.palm_cast(P, "lance", side="r", title="Zero Lance", windup=22, active=10, recover=14, ability="Zero Lance", kinds=("ranged",),
                    rng=(6, 24), track=9.0, lock=12),
        M.lunge_cut(P, "step", side="r", title="Phase Step", windup=10, active=5, recover=12, advance=1.0, ability="Phase Step",
                    kinds=("gap",), rng=(5, 16), damage=0.85),
        M.chop(P, "sever_a", side="r", windup=9, active=3, recover=6, diagonal=1.0, kinds=("combo",), rng=(0, 2.8), damage=1.75, advance=0.4),
        M.chop(P, "sever_b", side="l", windup=8, active=3, recover=6, diagonal=1.0, kinds=("combo",), rng=(0, 2.8), damage=1.75, advance=0.4),
        M.chop(P, "sever_c", side="r", cls="HEAVY", windup=12, hold=10, active=4, recover=14, diagonal=-1.0, kinds=("combo",),
               rng=(0, 3.0), damage=2.5, advance=0.6),
        M.spin_cut(P, "spin", side="l", windup=10, active=6, recover=12, rng=(0, 3.0), damage=1.5),
        M.sky_call(P, "orbital", title="Orbital Mark", windup=20, active=10, recover=16, ability="Orbital Mark", kinds=("area", "ranged")),
        deploy(),
        zero_point(),
        wake(),
    ]
    by = {m.id: m for m in mv}
    _halo(by["lance"], {"windup": [(0.35, (10, 0, 90)), (1.0, (40, 0, 540))], "active": [(1.0, (40, 0, 900))],
                        "recover": [(0.5, (10, 0, 1000))]})
    _halo(by["orbital"], {"windup": [(1.0, (0, 6, 180))], "active": [(0.3, (0, 16, 300)), (1.0, (0, 16, 540))], "recover": [(0.5, (0, 6, 640))]})
    _halo(by["zero_point"], {"windup": [(1.0, (0, 0, 360))], "active": [(1.0, (0, 0, 900))]})
    _halo(by["wake"], {"windup": [(1.0, (0, 0, 90))], "active": [(1.0, (0, 0, 360))]})
    for m in mv:
        stage_clips(r, m)
    return mv


def deploy():
    """Deploy Coils: it drops to a crouch and presses both palms flat to the floor; the coils rise round it."""
    down = {"pelvis": {"rot": (14, 0, 0), "pos": (0, -6, 1)}, "spine": (14, 0, 0), "chest": (24, 0, 0), "head": (-10, 0, 0),
            "arm_l": (-46, 0, -18), "arm_r": (-46, 0, 18), "forearm_l": (-20, 0, 0), "forearm_r": (-20, 0, 0), "hand_l": (60, 0, 0), "hand_r": (60, 0, 0)}
    gather = {"pelvis": {"rot": (0, 0, 0), "pos": (0, 0.6, 0)}, "chest": (-10, 0, 0), "arm_l": (-90, 0, -30), "arm_r": (-90, 0, 30),
              "forearm_l": (-60, 0, 0), "forearm_r": (-60, 0, 0), "head": (-10, 0, 0)}
    return Move("deploy", "NONE", 12, 10, 12, title="Deploy Coils", track=0.0, ability="Deploy Coils", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, stages={"windup": [(0.5, gather), (1.0, down)], "active": [(1.0, down)], "recover": [(0.5, gather)]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.05})


def zero_point():
    """Zero Point: it lifts off its feet and curls in round the core, the arms crossed, then bursts open (the signature)."""
    curl = {"pelvis": {"rot": (20, 0, 0), "pos": (0, 12, 0)}, "thigh_l": (-70, 0, 0), "shin_l": (90, 0, 0), "thigh_r": (-60, 0, 0),
            "shin_r": (84, 0, 0), "foot_l": (30, 0, 0), "foot_r": (30, 0, 0), "spine": (16, 0, 0), "chest": (20, 0, 0), "head": (24, 0, 0),
            "arm_l": (-70, -30, -10), "arm_r": (-70, 30, 10), "forearm_l": (-100, 0, 0), "forearm_r": (-100, 0, 0)}
    burst = {"pelvis": {"rot": (-10, 0, 0), "pos": (0, 10, 0)}, "thigh_l": (10, 0, -14), "thigh_r": (10, 0, 14), "shin_l": (14, 0, 0),
             "shin_r": (14, 0, 0), "foot_l": (30, 0, 0), "foot_r": (30, 0, 0), "spine": (-8, 0, 0), "chest": (-20, 0, 0), "head": (-30, 0, 0),
             "arm_l": (-30, 0, -100), "arm_r": (-30, 0, 100), "forearm_l": (-6, 0, 0), "forearm_r": (-6, 0, 0)}
    return Move("zero_point", "PERILOUS", 24, 10, 22, title="Zero Point", track=0.0, ability="Zero Point", rng=(0, 10), kinds=("area",),
                signature=True, damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, {"pelvis": {"rot": (6, 0, 0), "pos": (0, -3, 0)}, "chest": (10, 0, 0)}), (1.0, curl)],
                        "active": [(0.25, burst), (1.0, burst)], "recover": [(0.6, {"pelvis": {"rot": (8, 0, 0), "pos": (0, -3, 0)}, "chest": (8, 0, 0)})]},
                lag={"arm_l": 0.03, "arm_r": 0.03, "head": 0.05, "thigh_l": 0.02, "thigh_r": 0.02})


def wake():
    """Its intro, after it rises: the head lifts, the arms open from its sides, the halo spins up."""
    open_ = {"chest": (-8, 0, 0), "head": (-14, 0, 0), "arm_l": (-10, 0, -30), "arm_r": (-10, 0, 30), "forearm_l": (-20, 0, 0),
             "forearm_r": (-20, 0, 0), "hand_l": (0, 0, -20), "hand_r": (0, 0, 20)}
    return Move("wake", "NONE", 10, 24, 12, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, {"head": (10, 0, 0), "chest": (6, 0, 0)})], "active": [(0.4, open_), (1.0, open_)], "recover": []},
                lag={"arm_l": 0.05, "arm_r": 0.05})



# Its sounds (SPEC §35.8): machinery, shutters and air, low rather than beeps. Chosen by ear on paper, not yet heard.
SOUNDS = {
    "tell_light": [("block.vault.close_shutter", 1.4, 0.8)],
    "tell_heavy": [("block.vault.activate", 1.6, 0.7), ("entity.breeze.inhale", 1.4, 0.6)],
    "tell_perilous": [("block.beacon.activate", 2.0, 0.6), ("entity.warden.sonic_charge", 1.6, 0.8)],
    "swing_light": [("entity.player.attack.sweep", 1.4, 0.9), ("entity.breeze.slide", 1.2, 0.8)],
    "swing_heavy": [("entity.player.attack.sweep", 1.8, 0.7), ("entity.wind_charge.wind_burst", 1.4, 0.7)],
    "cast": [("entity.breeze.shoot", 1.6, 0.6)],
    "impact": [("block.heavy_core.fall", 2.0, 0.7), ("entity.wind_charge.wind_burst", 1.8, 0.6)],
    "roar": [("block.beacon.activate", 2.5, 0.5), ("block.trial_spawner.ominous_activate", 2.0, 0.7)],
    "step": [("block.vault.step", 1.2, 0.7), ("entity.iron_golem.step", 0.8, 0.9)],
}
MOVE_SOUNDS = {"deploy": {"swing": "cast"}, "orbital": {"impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "wake"},
    "phases": [
        {"pool": [
            {"move": "lance", "weight": 2},
            {"move": "step", "weight": 2, "when": "far", "then": [["sever_b", "sever_c"]]},
            {"move": "sever_a", "weight": 3, "then": [["sever_b"], ["sever_b", "sever_c"]]},
            {"move": "spin", "weight": 3, "when": "behind"},
        ]},
        # the coils come with the second phase, as in GateBestiary (a summon in the first left it nothing but summons
        # at middle range)
        {"transition": "orbital", "pool": [
            {"move": "lance", "weight": 3},
            {"move": "step", "weight": 2, "when": "far", "then": [["sever_b", "sever_c"]]},
            {"move": "sever_a", "weight": 3, "then": [["sever_b", "sever_c"]]},
            {"move": "spin", "weight": 3, "when": "behind"},
            {"move": "orbital", "weight": 2},
            {"move": "deploy", "weight": 1},
        ]},
        {"transition": "zero_point", "pool": [
            {"move": "lance", "weight": 3, "when": "far", "then": [["step"]]},
            {"move": "step", "weight": 3, "then": [["sever_b", "sever_c"]]},
            {"move": "sever_a", "weight": 2, "then": [["sever_b", "sever_c"]]},
            {"move": "spin", "weight": 3, "when": "behind"},
            {"move": "orbital", "weight": 2},
            {"move": "zero_point", "weight": 2},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.25},
}


def paint(r, P_):
    ceramic = Ramp("#4e5464", "#666d7e", "#828a9a", "#9fa7b6", "#bcc3cf", "#d6dbe3", "#ebeef3")
    joint = Ramp("#0b0d12", "#141720", "#1e222c", "#2a2f3b", "#373d4b")
    blue = ("#3a8cff", "#62b0ff", "#a6d6ff", "#e2f4ff")
    seam = lambda f, i, j: (-1.4 if (f.w > 4 and i == f.w // 2 and f.name in ("north", "south")) else 0.6 if j == 0 else 0)
    plated = ("chest", "pelvis", "head", "arm_l", "arm_r", "plating_l", "plating_r", "pauldron_l", "pauldron_r", "thigh_l", "thigh_r",
              "greave_l", "greave_r", "foot_l", "foot_r", "collar", "hip", "crest")
    for c in plated:
        P_.paint(c, ceramic, 4.0, seam, noise=0.25)
    for c in ("spine", "neck", "forearm_l", "forearm_r", "hand_l", "hand_r", "shin_l", "shin_r", "stalk", "fin0", "fin1"):
        P_.paint(c, joint, 2.6, lambda f, i, j: (0.8 if (i + j) % 4 == 0 else 0))
    # the blue: a line down the front and back of the limbs, the visor, the core, the fins' edges
    for c in ("chest", "arm_l", "arm_r", "thigh_l", "thigh_r", "plating_l", "plating_r"):
        for fname in ("north", "south"):
            g = P_.face(c, fname, glow=True)
            f = P_.face(c, fname)
            mid = g.w // 2
            for j in range(1, g.h - 1):
                f.set(mid, j, joint.at(1))
                g.set(mid, j, hexc(blue[1] if j % 3 else blue[2], 220))
    for c in ("fin0", "fin1", "pauldron_l", "pauldron_r", "greave_l", "greave_r"):
        for fname in SIDES:
            edge_glow(P_, c, fname, blue[0], rows=1, alpha=180, side="top")
    with P_.eyes():
        f = P_.face("visor", "north")
        g = P_.face("visor", "north", glow=True)
        for i, j in f.each():
            f.set(i, j, joint.at(0))
            if j == 1 and 1 <= i <= f.w - 2:
                g.set(i, j, hexc(blue[3] if i in (2, f.w - 3) else blue[1], 255))
    for fname in ("top", "bottom", "west", "east"):
        P_.paint("visor", joint, 1.4, faces=(fname,))
    radial(P_, "core", "north", [blue[3], blue[2], blue[1]], skin=Ramp("#0e1a30", "#1a3060", "#2a50a0"))
    P_.paint("core", joint, 2.0, faces=("top", "bottom", "west", "east"))
    # the halo: dark metal, its inner edge and its nodes lit
    for c in ("halo_t", "halo_b", "halo_l", "halo_r", "spoke"):
        P_.paint(c, joint, 3.2)
        for fname in ("north", "south", "top", "bottom", "west", "east"):
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(blue[1], 150))
    for k in range(4):
        P_.paint(f"node{k}", ceramic, 5.0)
        for fname in ("north", "south"):
            radial(P_, f"node{k}", fname, [blue[3], blue[2]])
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.5, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
