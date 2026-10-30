"""Unit Zero (SPEC §35.11, docs/bosses/ROLLOUT.md): the Precursor gate's last floor, a Duelist. The Sentinel, Euan's pick
from its silhouette round: a cathedral statue that moves, 3.6 blocks to the helm and 4.3 with its halo.

Pale ceramic plates over black joints, a slate tabard and split coat-tails, hands fused into long flat blades whose
edges run down the little finger's side, and a great square halo stood on its point behind it with twelve dial marks
round its inner frame (it counts down). Its light story is one cold blue: the edges, the visor, the core slit, the
halo's frame and dial, the cracks in its glaze. It waits on one knee, rises, salutes with its right blade, and fights
fast: Zero Lance (the blade drawn back along the arm, the halo spinning up and tilting at you, a PERILOUS beam down
the blade's line that follows you until the last third of the tell), Phase Step (it sinks with its blade pointed at you
and is beside you already cutting), Severing (three cuts with the blades' edges, the third held high for a beat, the
blade not cutting always held out in guard), a spin cut at waist height for whoever is behind it, Deploy Coils (it
sets both points to the floor), Orbital Mark (the halo rises and marks fall) and Zero Point (it lifts, curls in, then
bursts open: the signature). It dies folding back down to the kneel it started in.
"""
from kit import moves as M
from kit import core, parts, plans
from kit.core import Move, keyed, stage_clips
from kit.paint import cracks, edge_glow, radial
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "unit_zero"
SCALE = 1.85  # the hitbox on the player-sized HUMAN model: 1.1 x 3.3 blocks (the Sentinel stands 3.6 to the helm)

# The Sentinel (docs/bosses/ROLLOUT.md, Euan's pick A, 2026-10-28): a cathedral statue that moves. Tall and slender,
# pale ceramic over black joints, weight on its left leg. A small faceless helm with one blue line for a visor and a crest
# swept back; flat pauldrons stepping up into spires; a narrow slate tabard in front and split coat-tails behind to the
# ankle; hands fused into long flat blades; a great square halo stood on its point behind it, framing the head and both
# shoulders, on a stalk from its back. Value story: the blue (the edges, the visor, the core, the halo's dial) is the
# brightest, the ceramic the lightest surface, the slate cloth in the middle, the joints the darkest.
P = plans.Biped(leg=30, thigh=15, foot=(3, 2, 8), pelvis=(7, 4, 5), spine=(4, 5, 4), chest=(15, 11, 7), neck=(2, 3, 2),
                head=(5, 7, 6), arm=(3, 11, 3), forearm=(3, 10, 3), hand=(2, 3, 3), stance=3.5, hunch=0, leg_w=(3, 4))
BLADE_TIP = (0, 14, 1)  # the blade's point, in the blade bone's space
# the blade's middle and its edge: the edge runs down the back of the hand (the little finger's side, as a hand-edge cut
# leads), the stepped spine down the front
BLADE_EDGE = ((0, 7, 0), (0, 7, 3))
BLADE_IN_HAND = (0, 17, 1)  # the point in the hand's space (the blade sits 3 px down the hand)


def body():
    r = R.Rig(ID, tex=(512, 512))
    pel = r.bone("pelvis", (0, 24 - P.leg, 0))
    pel.cube("pelvis", -3.5, -1, -2.5, 7, 4, 5)
    pel.cube("belt", -4.5, -2, -3, 9, 2, 6)
    pel.cube("buckle", -1, -1.5, -3.5, 2, 1, 1)
    # the tabard in front (between the legs, so a stride never meets it) and the coat-tails behind, each a short chain
    # of cloth on springs, to the ankle
    tb = pel.bone("tabard0", (0, 0, -3.5))
    tb.cube("tabard0", -1.5, 0, -0.5, 3, 9, 1)
    tb = tb.bone("tabard1", (0, 9, 0), (2, 0, 0))
    tb.cube("tabard1", -1.5, 0, -0.5, 3, 9, 1)
    tb.cube("tabard_tip", -0.5, 9, -0.5, 1, 2, 1)
    for side, nm in ((1, "l"), (-1, "r")):
        ct = pel.bone("coat_" + nm + "0", (side * 1.6, 0, 3.4), (8, 0, -side * 3))
        for k, ln in enumerate((9, 9, 7)):
            if k:
                ct = ct.bone(f"coat_{nm}{k}", (0, 9, 0), (4, 0, 0))
            ct.cube(f"coat_{nm}{k}", -1.5, 0, -0.5, 3, ln, 1)
        ct.cube("coat_tip_" + nm, -0.5, 7, -0.5, 1, 2, 1)
        parts.plate(pel, "tasset_" + nm, (side * 5, -1, 0), (0, 0, -side * 12), (-1, 0, -3, 2, 7, 6))
    # legs: long and slim, a ceramic cuisse over a black thigh, a knee cop, a greave stepping in, a pointed sabaton;
    # the right knee eased, the weight on the left
    for side, nm in ((1, "l"), (-1, "r")):
        th = pel.bone("thigh_" + nm, (side * P.stance, 0, 0), (0, 0, 0) if side > 0 else (-6, 0, 0))
        th.cube("thigh_" + nm, -1.5, -1, -1.5, 3, 16, 3)
        th.cube("cuisse_" + nm, -2 + side * 0.5, 1, -2.5, 4, 10, 3)
        sn = th.bone("shin_" + nm, (0, P.thigh, 0), (0, 0, 0) if side > 0 else (10, 0, 0))
        parts.taper(sn, "greave_" + nm, 0, 0, 0, ((4, 8, 4), (3, 5, 3)), up=False)
        sn.cube("poleyn_" + nm, -2, -1.5, -3, 4, 3, 2)
        ft = sn.bone("foot_" + nm, (0, P.leg - P.thigh - P.foot[1], 0), (0, 0, 0) if side > 0 else (-4, 0, 0))
        ft.cube("foot_" + nm, -1.5, 0, -6, 3, 2, 8)
        ft.cube("toe_" + nm, -1, 1, -9, 2, 1, 3)
    # a narrow waist, banded
    sp = pel.bone("spine", (0, -2, 0))
    sp.cube("spine", -2, -5, -2, 4, 5, 4)
    sp.cube("abs", -1.5, -4, -2.8, 3, 3, 1)
    # the chest widens upward in three steps, a V; the core is a slit of blue down the sternum; a slate plate on the
    # back carries the halo's stalk
    ch = sp.bone("chest", (0, -5, 0))
    parts.taper(ch, "chest", 0, 0, 0, ((8, 3, 6), (12, 4, 7), (15, 4, 7)))
    ch.cube("gorget", -3, -12, -2.5, 6, 1, 5)
    ch.cube("backplate", -5, -10, 3, 10, 7, 1)
    ch.cube("stalk", -1, -14, 4, 2, 10, 2)
    core = ch.bone("core", (0, -6, -3.5))
    core.cube("core", -0.5, -4, -1, 1, 9, 1)
    core.cube("core_bar", -2.5, -4, -1, 5, 1, 1)
    # shoulders: flat lames stepping up into a spire (they follow the arm a little, see _follow); long slim arms, the
    # hands fused into flat blades with one lit edge
    for side, nm in ((1, "l"), (-1, "r")):
        sh = ch.bone("shoulder_" + nm, (side * 7.5, -10, 0))
        pa = sh.bone("pauldron_" + nm, (0, 0, 0), (0, 0, side * 10))  # the lames slope down off the shoulder
        pa.cube("pauldron_" + nm + "0", -4.5 + side, -2, -4, 9, 3, 8)
        pa.cube("pauldron_" + nm + "1", -3 + side * 0.5, -4, -3.5, 6, 2, 7)
        spr = pa.bone("spire_" + nm, (side * 2.5, -4, 0), (0, 0, -side * 14))  # a pinnacle standing up off it
        parts.taper(spr, "spire_" + nm, 0, 0, 0.5, ((3, 3, 4), (2, 5, 3), (1, 4, 1)))
        arm = sh.bone("arm_" + nm, (side * 0.5, 1, 0), (-4, 0, -side * 6))
        arm.cube("arm_" + nm, -1.5, -1, -1.5, 3, 12, 3)
        arm.cube("rerebrace_" + nm, -2, 2, -2, 4, 6, 4)
        arm.cube("couter_" + nm, -1.5, 9, 1, 3, 3, 2)
        fa = arm.bone("forearm_" + nm, (0, 11, 0), (-10, 0, 0))
        fa.cube("forearm_" + nm, -1.5, -1, -1.5, 3, 11, 3)
        fa.cube("cuff_" + nm, -2, 3, -2, 4, 4, 4)
        hd_ = fa.bone("hand_" + nm, (0, 10, 0))
        hd_.cube("hand_" + nm, -1, 0, -1.5, 2, 3, 3)
        bl = hd_.bone("blade_" + nm, (0, 3, 0))
        for k, (w, y, h, z, d) in enumerate(((2, 0, 5, -2.5, 4), (2, 5, 5, -1.5, 3), (1, 10, 3, -0.5, 2), (1, 13, 2, 0.5, 1))):
            bl.cube(f"blade_{nm}{k}", -w / 2, y, z, w, h, d)  # the spine steps in; the edge runs straight
        bl.cube("edge_" + nm, -0.5, 1, 1.5, 1, 13, 1)
    # the helm: small and faceless, a brow over one blue line, a crest swept back
    nk = ch.bone("neck", (0, -11, -0.5))
    nk.cube("neck", -1, -3, -1, 2, 3, 2)
    hd = nk.bone("head", (0, -3, 0))
    hd.cube("head", -2.5, -7, -3.5, 5, 7, 6)
    hd.cube("faceplate", -2, -3, -4.5, 4, 3, 1)
    hd.cube("visor", -2.5, -4, -4.5, 5, 1, 1)
    hd.cube("brow", -2.5, -6, -4.5, 5, 2, 1)
    hd.cube("aventail", -2.5, -2, 2.5, 5, 3, 1)
    cr = hd.bone("crest", (0, -7, 0), (-30, 0, 0))
    cr.cube("crest0", -0.5, -3, -2.5, 1, 3, 7)
    cr.cube("crest1", -0.5, -5, 1, 1, 3, 6)
    cr.cube("crest2", -0.5, -6, 6, 1, 2, 4)
    # the halo: a square stood on its point, an outer frame, an inner frame of light, twelve dial marks between them
    # (it counts down), nodes on the points, a hub on the stalk and a stem down to its lowest point
    halo = ch.bone("halo", (0, -14, 6), (0, 0, 45))
    halo.cube("halo_t", -11.5, -12, -0.5, 23, 1, 1)
    halo.cube("halo_b", -11.5, 11, -0.5, 23, 1, 1)
    halo.cube("halo_l", 10.5, -11, -0.5, 1, 22, 1)
    halo.cube("halo_r", -11.5, -11, -0.5, 1, 22, 1)
    halo.cube("ring_t", -9.5, -10, -0.5, 19, 1, 1)
    halo.cube("ring_b", -9.5, 9, -0.5, 19, 1, 1)
    halo.cube("ring_l", 8.5, -9, -0.5, 1, 18, 1)
    halo.cube("ring_r", -9.5, -9, -0.5, 1, 18, 1)
    k = 0
    for u in (-5, 0, 5):
        halo.cube(f"dial{k}", u - 0.5, -11, -0.5, 1, 3, 1)
        halo.cube(f"dial{k + 1}", u - 0.5, 8, -0.5, 1, 3, 1)
        halo.cube(f"dial{k + 2}", -11, u - 0.5, -0.5, 3, 1, 1)
        halo.cube(f"dial{k + 3}", 8, u - 0.5, -0.5, 3, 1, 1)
        k += 4
    for k, (x, y) in enumerate(((11, 11), (-11, 11), (11, -11), (-11, -11))):
        halo.cube(f"node{k}", x - 1.5, y - 1.5, -1, 3, 3, 2)
    halo.cube("hub", -1.5, -1.5, -1, 3, 3, 2)
    stem = halo.bone("stem", (0, 0, 0), (0, 0, -45))
    stem.cube("stem", -0.5, 1.5, -0.5, 1, 13, 1)
    r.look = [("head", 0.55, 0.5), ("neck", 0.25, 0.2)]
    r.size = P.leg / 36.0
    r.shadow = 0.8
    # secondary parts (SPEC §35.10): the cloth trails, the tassets flap, the pauldrons and the halo carry a little weight
    r.springs = [("tabard0", 0.5, 2.4, 0.3), ("tabard1", 0.4, 2.0, 0.3)] \
        + [(f"coat_{s}{k}", g, hz, 0.28) for s in "lr" for k, (g, hz) in enumerate(((0.6, 2.4), (0.45, 2.0), (0.35, 1.8)))] \
        + [(f"tasset_{s}", 0.5, 3.5, 0.3) for s in "lr"] + [(f"pauldron_{s}", 0.08, 6.0, 0.5) for s in "lr"] + [("halo", 0.06, 4.0, 0.4)]
    r.guard = ["blade_l", "blade_r"]  # the blades never go into the ground, in any clip (core.check)
    return r


def _follow(r, bone, leader, share):
    """A part that follows another's turn by a share in every clip (a pauldron rides a little way with the arm)."""
    from kit.core import sample, TICK
    for name, c in r.clips.items():
        if (leader, "rot") not in c.tracks:
            continue
        n = max(1, int(round(c.length / TICK)))
        keys = []
        for k in range(n + 1):
            t = min(c.length, k * TICK)
            v = sample(r, name, t).get(leader, {}).get("rot", (0, 0, 0))
            keys.append((t, tuple(share * x / core.DEG for x in v), "cat"))
        c.tracks[(bone, "rot")] = keys


def _halo(r, mv, keys):
    """The halo's own track in a move's clip, written after the body's: keys = [(stage, f, (tilt, rise, spin))]. It
    starts and ends at rest; its last turn eases on to the nearest whole turn (a whole turn is no turn), so it hands
    over to the idle without spinning back."""
    W, A = mv.seconds("windup"), mv.seconds("active")
    at = {"windup": 0.0, "active": W, "recover": W + A}
    c = r.clips[core.clip_name(mv)]
    ks = [(0.0, 0.0, 0.0, 0.0)] + [(at[st] + f * mv.seconds(st), tilt, rise, spin) for st, f, (tilt, rise, spin) in keys]
    ks.append((c.length, 0.0, 0.0, 360.0 * round(ks[-1][3] / 360.0)))
    tmp = R.Clip("halo", c.length, False)
    tmp.rot("halo", [(t, x, 0, z) for t, x, _, z in ks])
    tmp.pos("halo", [(t, 0, y, 0) for t, _, y, _ in ks])
    core.bake(tmp)
    c.tracks[("halo", "rot")] = tmp.tracks[("halo", "rot")]
    c.tracks[("halo", "pos")] = tmp.tracks[("halo", "pos")]


BLADES = [(["blade_l"], [("hand_l", 0, 70), ("forearm_l", 0, 50), ("arm_l", 0, 45)]),
          (["blade_r"], [("hand_r", 0, 70), ("forearm_r", 0, 50), ("arm_r", 0, 45)])]
CLOTH = [(["coat_l2"], [("coat_l0", 0, 80), ("coat_l1", 0, 60)]), (["coat_r2"], [("coat_r0", 0, 80), ("coat_r1", 0, 60)]),
         (["tabard1"], [("tabard0", 0, 70)])]


def clips(r):
    plans.biped_locomotion(r, P, weight=0.8, temper="regal")
    plans.biped_reactions(r, weight=0.8)
    # standing, the halo stays on its point and rocks a little on its stalk; it spins only when it works
    idle = r.clips["idle"]
    L = idle.length
    idle.rot("halo", [(0, 0, 0, 0), (L * 0.25, 1.5, 0, 3), (L * 0.5, 0, 0, 0), (L * 0.75, 1.5, 0, -3), (L, 0, 0, 0)])
    # the death folds it back to the kneel it began in, then the halo tips forward over its bowed head
    kneel = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -13.5, 1)}, "thigh_l": (-80, 0, 0), "shin_l": (82, 0, 0), "foot_l": (-2, 0, 0),
             "thigh_r": (8, 0, 0), "shin_r": (90, 0, 0), "foot_r": (60, 0, 0), "spine": (14, 0, 0), "chest": (16, 0, 4),
             "head": (24, 0, 0), "arm_r": (-30, 0, 10), "forearm_r": (-20, 0, 0), "arm_l": (-50, 0, -6), "forearm_l": (-40, 0, 0)}
    slump = dict(kneel)
    slump.update({"chest": (30, 0, 6), "head": (40, 0, 8), "arm_r": (-6, 0, 8), "arm_l": (-10, 0, -4), "forearm_l": (-6, 0, 0),
                  "halo": {"rot": (40, 0, 0), "pos": (0, -4, 0)}})
    keyed(r, "death", 2.2, [(0, {}), (0.25, {"chest": (-14, 0, 0), "head": (-26, 0, 0), "arm_l": (-20, 0, -40), "arm_r": (-20, 0, 40)}),
                            (0.9, kneel), (1.5, slump), (2.2, slump)])
    blade = dict(weapon=BLADE_TIP, weapon_r=2.5, edge=BLADE_EDGE)
    mv = [
        # the lance never fires up close (a beam at 4 blocks read as a cut you couldn't dodge), and its aim holds still
        # for the last half second of the tell: the line goes bright, then it fires down the blade's line
        M.palm_cast(P, "lance", side="r", title="Zero Lance", windup=22, active=10, recover=14, ability="Zero Lance", kinds=("ranged",),
                    rng=(6, 24), track=9.0, lock=12),
        M.lunge_cut(P, "step", side="r", title="Phase Step", windup=10, active=5, recover=12, advance=1.0, ability="Phase Step",
                    kinds=("gap",), rng=(5, 16), damage=0.85, weapon=BLADE_IN_HAND),
        # Severing: three cuts with the blades' edges, the third held high for a beat
        M.chop(P, "sever_a", side="r", windup=9, active=3, recover=10, diagonal=1.0, kinds=("combo",), rng=(0, 2.8), damage=1.75,
               advance=0.4, weapon_bone="blade_r", **blade),
        M.chop(P, "sever_b", side="l", windup=8, active=3, recover=10, diagonal=1.0, kinds=("combo",), rng=(0, 2.8), damage=1.75,
               advance=0.4, weapon_bone="blade_l", **blade),
        M.chop(P, "sever_c", side="r", cls="HEAVY", windup=12, hold=10, active=4, recover=14, diagonal=-1.0, kinds=("combo",),
               rng=(0, 3.0), damage=2.5, advance=0.6, weapon_bone="blade_r", **blade),
        M.spin_cut(P, "spin", side="l", windup=10, active=6, recover=12, rng=(0, 3.0), damage=1.5, weapon_bone="blade_l", **blade),
        M.sky_call(P, "orbital", title="Orbital Mark", windup=20, active=10, recover=16, ability="Orbital Mark", kinds=("area", "ranged")),
        deploy(),
        zero_point(),
        wake(),
    ]
    by = {m.id: m for m in mv}
    # the spin cuts at a standing player's waist: out flat at its shoulder the blade passed over their head, so it
    # sinks and sweeps the blade angled down
    for f, pose in by["spin"].stages["active"]:
        x, y, z = pose["arm_l"]
        pose["arm_l"] = (-46, y, z - 24)
        pose["pelvis"] = {"rot": pose["pelvis"]["rot"], "pos": (0, -6, 0)}
    by["orbital"].stages["windup"][0] = (0.3, by["orbital"].stages["windup"][0][1])
    # two blades: whichever isn't cutting is held out forward in guard, so in a string the next cut rises while the
    # last blade is still out, not from rest; after a cut the blade stays out low a moment before it comes back
    guard = {"l": {"arm_l": (-44, -18, -12), "forearm_l": (-14, 0, 0)}, "r": {"arm_r": (-44, 18, 12), "forearm_r": (-14, 0, 0)}}
    for mid, side in (("sever_a", "r"), ("sever_b", "l"), ("sever_c", "r")):
        sg = 1 if side == "l" else -1
        other = "r" if side == "l" else "l"
        for f, pose in by[mid].stages["windup"] + by[mid].stages["active"]:
            for b, v in guard[other].items():
                pose[b] = tuple(c * (0.6 if f < 0.5 and pose is not by[mid].stages["active"][0][1] else 1.0) for c in v)
        by[mid].stages["recover"] = [(0.5, dict({"chest": (10, 14 * sg, 0), "spine": (4, 4 * sg, 0), "forearm_" + side: (-14, 0, 0),
                                                 "pelvis": {"rot": (4, 4 * sg, 0), "pos": (0, -2, 0)}}, **guard[side]))]
    # the step sinks with its blade already pointed at you, most of the way down in the first third (the sink is its
    # anticipation: from a lance it goes straight on into the step instead of standing back up first)
    low = by["step"].stages["windup"][-1][1]
    low["arm_r"], low["forearm_r"] = (-60, low["arm_r"][1], low["arm_r"][2]), (-60, 0, 0)
    by["step"].stages["windup"] = [(0.3, core._scaled(low, 0.7)), (1.0, low)]
    by["step"].stages["recover"] = [(0.5, {"pelvis": {"rot": (6, 0, 0), "pos": (0, -2, 0)}, "arm_r": (-52, 0, 0), "forearm_r": (-16, 0, 0)})]
    # the lance: the blade draws back along the arm, then drives out level, its point on you
    for stage in ("windup", "active"):
        for f, pose in by["lance"].stages[stage]:
            if "hand_r" in pose:
                pose["hand_r"] = (-10, 0, 0) if stage == "windup" else (8, 0, 0)
    for m in mv:
        stage_clips(r, m)
    # the halo: it spins up and tilts at you through the lance's tell, rises over the Orbital Mark, winds up through
    # Zero Point's curl, and wakes with a turn
    _halo(r, by["lance"], [("windup", 0.4, (6, 0, 60)), ("windup", 1.0, (40, 0, 540)), ("active", 1.0, (40, 0, 900)),
                           ("recover", 0.6, (8, 0, 1040))])
    _halo(r, by["orbital"], [("windup", 1.0, (0, 6, 180)), ("active", 0.3, (0, 16, 300)), ("active", 1.0, (0, 16, 540)),
                             ("recover", 0.6, (0, 5, 680))])
    _halo(r, by["zero_point"], [("windup", 1.0, (0, 0, 360)), ("active", 1.0, (0, 0, 900))])
    _halo(r, by["wake"], [("windup", 1.0, (0, 0, 90)), ("active", 1.0, (0, 0, 360))])
    # the blades stay out of the ground in every clip but the fall (the wrist turns first, then the elbow and shoulder);
    # the cloth lifts back off the floor where it would meet it, the fall included; the pauldrons ride a third of the arm
    own = {core.clip_name(m) for m in mv if m.ground}
    core.ground_pass(r, BLADES, clips=[n for n in r.clips if n not in own and n != "death"])
    core.ground_pass(r, CLOTH, clips=list(r.clips), skip=())
    for side in "lr":
        _follow(r, "pauldron_" + side, "arm_" + side, 0.3)
    return mv


def deploy():
    """Deploy Coils: it sinks low and sets the points of both blades to the floor either side of it; the coils rise
    where they touch."""
    down = {"pelvis": {"rot": (10, 0, 0), "pos": (0, -12.5, 1)}, "spine": (10, 0, 0), "chest": (16, 0, 0), "head": (-8, 0, 0),
            "arm_l": (-24, 0, -34), "arm_r": (-24, 0, 34), "forearm_l": (-16, 0, 0), "forearm_r": (-16, 0, 0), "hand_l": (4, 0, 0), "hand_r": (4, 0, 0)}
    press = dict(down, chest=(24, 0, 0), head=(-18, 0, 0), spine=(14, 0, 0))  # it bears down on the blades as the coils rise
    gather = {"pelvis": {"rot": (0, 0, 0), "pos": (0, 0.6, 0)}, "chest": (-10, 0, 0), "arm_l": (-70, 0, -40), "arm_r": (-70, 0, 40),
              "forearm_l": (-50, 0, 0), "forearm_r": (-50, 0, 0), "head": (-10, 0, 0)}
    return Move("deploy", "NONE", 12, 10, 12, title="Deploy Coils", track=0.0, ability="Deploy Coils", rng=(0, 99), kinds=("summon",),
                damage=0, knock=0, stages={"windup": [(0.5, gather), (1.0, down)], "active": [(0.5, press), (1.0, down)], "recover": [(0.5, gather)]},
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
    """Its intro, after it rises: the head lifts, then the right blade comes up before its face in a salute (the pose
    from its silhouette round) while the halo spins up, and it lowers it to fight."""
    salute = {"chest": (-6, 0, 0), "head": (-10, 0, 0), "arm_r": (-20, 0, -14), "forearm_r": (-102, 0, 18), "hand_r": (0, 0, 0),
              "arm_l": (-4, 0, -10), "forearm_l": (-8, 0, 0)}
    high = dict(salute, chest=(-10, 0, 0), head=(-16, 0, 0), forearm_r=(-110, 0, 20), arm_r=(-26, 0, -14))  # it draws itself up
    return Move("wake", "NONE", 10, 24, 12, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0,
                stages={"windup": [(1.0, {"head": (10, 0, 0), "chest": (6, 0, 0)})], "active": [(0.4, salute), (1.0, high)], "recover": []},
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
    """Four materials in a value order: the cold blue brightest (the blades' edges, the visor, the core slit, the halo's
    inner frame and dial), then glazed ceramic (the lightest surface: helm, pauldrons and chest lead the eye, the legs
    weather darker toward the floor), then slate cloth, then the black joints and blades. The ceramic is cut into panels
    by dark seams and crazed with a few hairline cracks that hold the same blue light."""
    ceramic = Ramp("#3e4452", "#555c6c", "#6e7686", "#8a92a2", "#a7aebb", "#c3c9d3", "#dce0e7", "#eff1f5")
    joint = Ramp("#07080c", "#0e1016", "#161920", "#20242d", "#2c313c", "#3a404d")
    slate = Ramp("#14171e", "#1c2029", "#262b36", "#323846", "#414858", "#525a6c")
    blue = ("#2a6cf0", "#4c94ff", "#86c0ff", "#c8e6ff", "#f2faff")
    of = lambda *ps: parts.cubes_of(r, *ps)
    # glazed: smooth, one panel line across only the wide plates (a lit edge on every small plate reads as stripes)
    seam = lambda f, i, j: (-1.3 if (f.w >= 9 and f.name in SIDES and j == f.h // 2 and f.h >= 4) else 0)
    # the ceramic, lightest high on the body and weathering darker toward the feet
    tiers = ((5.4, ["head", "brow", "faceplate", "aventail"] + of("crest", "pauldron_", "spire_")),
             (5.1, of("chest", "gorget", "rerebrace_", "couter_", "cuff_", "forearm_") + ["abs"]),
             (4.7, ["belt"] + of("tasset_", "cuisse_", "poleyn_")),
             (4.3, of("greave_", "foot_", "toe_")))
    for base, cs in tiers:
        for c in cs:
            P_.paint(c, ceramic, base, seam, grad=0.9, rim=0.4, ao=0.9, noise=0.15, nscale=9)
    # the joints and the blades: black, a dull sheen on the top edges
    for c in ["pelvis", "spine", "neck", "stalk", "stem"] + of("thigh_", "arm_", "hand_"):
        P_.paint(c, joint, 2.4, lambda f, i, j: (0.9 if (i + 2 * j) % 5 == 0 else 0), noise=0.3)
    for c in of("blade_"):
        P_.paint(c, joint, 3.0, lambda f, i, j: (1.2 if j == 0 else -0.6 if (i + j) % 6 == 0 else 0), rim=1.4)
    # the slate cloth: long folds down it, darker at the hem
    for c in of("tabard", "coat_") + ["backplate"]:
        P_.paint(c, slate, 3.0, lambda f, i, j: (-0.9 if i % 3 == 1 else 0.3), grad=1.6, noise=0.4)
    # cracks in the glaze that hold the light: the chest, a pauldron, a cuisse, the helm's brow
    for c, n in (("chest2", 1), ("chest1", 1), ("cuisse_l", 1)):
        cracks(P_, c, ("north",), ceramic.at(1), blue[1:4], n=n, seed=c, alpha=200)
    # the blue: the blades' edges, the core slit and its bar, a seam down the front of each greave
    for c in of("edge_"):
        P_.paint(c, joint, 3.0)
        for fname in ("top", "bottom") + SIDES:
            edge_glow(P_, c, fname, blue[3], rows=99, alpha=245)
    for c in ("core", "core_bar"):
        P_.paint(c, Ramp("#0a1838", "#14306a", "#2050a8"), 2.0)
        for fname in ("north", "west", "east", "top", "bottom"):
            radial(P_, c, fname, [blue[4], blue[3], blue[2]])
    for c in ("greave_l0", "greave_r0"):
        g, f = P_.face(c, "north", glow=True), P_.face(c, "north")
        for j in range(1, g.h - 1):
            f.set(g.w // 2, j, joint.at(1))
            g.set(g.w // 2, j, hexc(blue[1] if j % 3 else blue[2], 210))
    P_.paint("buckle", ceramic, 5.0)
    radial(P_, "buckle", "north", [blue[3], blue[1]])
    # the face: one blue line under a heavy brow
    P_.paint("visor", joint, 1.2)
    with P_.eyes():
        f, g = P_.face("visor", "north"), P_.face("visor", "north", glow=True)
        for i, j in f.each():
            f.set(i, j, joint.at(0))
            g.set(i, j, hexc(blue[4] if i in (1, f.w - 2) else blue[2], 255))
    # the halo: dark metal frames with a lit rim, the inner frame and the twelve dial marks in light, ceramic nodes
    for c in ("halo_t", "halo_b", "halo_l", "halo_r"):
        P_.paint(c, joint, 3.4, lambda f, i, j: (1.0 if (i + j) % 4 == 0 else 0), rim=1.4)
    for c in of("ring_", "dial"):
        P_.paint(c, Ramp("#0a1838", "#14306a", "#2050a8"), 2.0)
        for fname in ("top", "bottom") + SIDES:
            g = P_.face(c, fname, glow=True)
            for i, j in g.each():
                g.set(i, j, hexc(blue[2] if c.startswith("dial") else blue[1], 235 if c.startswith("dial") else 170))
    for c in of("node") + ["hub"]:
        P_.paint(c, ceramic, 5.0)
        for fname in ("north", "south"):
            radial(P_, c, fname, [blue[4], blue[3]])
    return P_.skin, P_.glow


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": P, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 0.52, "sounds": SOUNDS, "move_sounds": MOVE_SOUNDS}
