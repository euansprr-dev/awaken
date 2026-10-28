"""Form bodies of the hostiles beside the zombie (SPEC 21.13): the skeleton (and stray), the spider (and cave spider), the creeper.
Shared helpers, rules and the registry live in forms_bodies.py; axes are the game's (x to the creature's left, y down, front -z,
ground y = 24).
"""
import math
import random

import fauna_rig as R
from fauna_anim import cycle, s, c, quad_gait
import forms_bodies as B


# =====================================================================================================================
# The skeleton (and the stray): a bone knight. C an upright marksman with a fused breastbone and one pauldron; B the jaw loose,
# a heart of the land's light in the ribs, both shoulders plated, horns breaking the temples, hip plates; A broad, plated forearms
# and shins, long swept horns, the crystal out of the shoulder; S still and tall, a horned crown and a cloak of bone.

K = {
    #   hunch rib(w,d) fwd  jaw   heart pauldron horn      plate  tasset vamb  crystal crown  cloak  stride
    2: dict(hunch=3, rib=(8, 4), fwd=0.0, jaw=False, heart=False, paul=1, horn=0, plate=1, tasset=False, vamb=False, crystal=0,
            crown=False, cloak=False, stride=1.4),
    3: dict(hunch=6, rib=(8, 4), fwd=0.5, jaw=True, heart=True, paul=2, horn=2, plate=0, tasset=True, vamb=False, crystal=0,
            crown=False, cloak=False, stride=1.5),
    4: dict(hunch=8, rib=(10, 5), fwd=1.0, jaw=True, heart=True, paul=3, horn=3, plate=0, tasset=True, vamb=True, crystal=2,
            crown=False, cloak=False, stride=1.6),
    5: dict(hunch=0, rib=(9, 5), fwd=0.0, jaw=True, heart=True, paul=3, horn=3, plate=0, tasset=True, vamb=True, crystal=1,
            crown=True, cloak=True, stride=1.5),
}


def skeleton(rung):
    p = K[rung]
    r = R.Rig(f"form_skeleton_{rung}", (128, 128))
    r.walk_stride, r.run_stride = p["stride"], p["stride"] * 1.9
    r.run_speed = (0.2, 0.32)
    r.shadow = 0.45 + 0.05 * (rung - 2)
    rw, rd = p["rib"]
    hunch = p["hunch"]
    base = r.bone("base", (0, 24, 0))
    hips = base.bone("hips", (0, -12, 0))
    hips.cube("pelvis", -3, -1, -1.5, 6, 2, 3)
    for side, nm in ((-1, "r"), (1, "l")):
        leg = hips.bone("leg_" + nm, (side * 2.0, 0, 0))
        leg.cube("leg_" + nm, -1, 0, -1, 2, 12, 2, mirror=side > 0, share=None if side < 0 else "leg_r")
        if p["vamb"]:  # a fused plate down the front of the shin
            leg.bone("gearg" + nm, (0, 0, 0)).cube("greave_" + nm, -1.5, 5, -1.6, 3, 5, 1, mirror=side > 0, share=None if side < 0 else "greave_r")
        if p["tasset"]:  # a plate of bone hanging over the thigh (springs with the life layer)
            t = hips.bone("skirt_" + nm, (side * 2.0, 0.5, -1.6), (-4, 0, side * -6))
            t.cube("skirt_" + nm, -1.5, 0, -0.5, 3, 4 + (rung >= 4), 1, mirror=side > 0, share=None if side < 0 else "skirt_r")
    torso = hips.bone("torso", (0, -1, 0), (hunch, 0, 0))
    # the ribcage as real bars (the gaps show the spine and the heart through them); every cube named torso* makes the chest's size
    torso.cube("torso_yoke", -rw / 2, -11, -rd / 2, rw, 2, rd)
    for k in range(3):
        w = rw - (2 if k == 2 else 0)
        torso.cube(f"torso_rib{k}", -w / 2, -8 + 2 * k, -rd / 2, w, 1, rd)
    torso.cube("torso_spine", -1, -9, rd / 2 - 2, 2, 10, 2)
    gt = torso.bone("geart", (0, 0, 0))  # what grows on the torso (the core bone keeps only the chest FormGear sizes armour from)
    gt.cube("neck", -1, -13, -0.5, 2, 2, 2)
    if p["plate"] >= 1:  # the breastbone, fused into a plate
        pw = 2 if p["plate"] == 1 else 4
        gt.cube("plate", -pw / 2, -10, -rd / 2 - 0.6, pw, 6 + (p["plate"] > 1), 1)
    if p["heart"]:  # the land's light where a heart was, seen between the ribs
        gt.cube("heart", -1.5, -8, -1.5, 3, 3, 2)
    # the head, held up on the neck
    head = torso.bone("head", (0, -11, -p["fwd"]), (-hunch * 0.8, 0, 0))
    if p["jaw"]:
        head.cube("head", -4, -8, -4, 8, 6, 8)
        jaw = head.bone("jaw", (0, -2, 2.5), (6, 0, 0))
        jaw.cube("jaw", -3, 0, -6.5, 6, 2, 6)
    else:
        head.cube("head", -4, -8, -4, 8, 8, 8)
    head.bone("gearb", (0, 0, 0)).cube("brow", -4, -5, -5, 8, 1, 1)
    B.eye_bones(head, (1.5, -1.5), -3.5, -4.15)
    if p["horn"]:
        n = p["horn"]
        for side, nm in ((-1, "r"), (1, "l")):
            # out of the temples, sweeping back and up; longer at each rank
            segs = [(3, 2, 0), (3, 2, -32)] if n == 2 else [(4, 3, 0), (3, 2, -28), (3, 1, -36)]
            B.shard(head, "horn_" + nm, (side * 4.2, -6.0, -0.5), (-20, side * -10, side * 58), segs)
    if p["crown"]:
        for k, (x, z, ln) in enumerate(((0, -3.2, 4), (-2.2, -2.6, 3), (2.2, -2.6, 3))):
            B.shard(head, f"crown{k}", (x, -7.5, z), (16, 0, x * 9), [(ln, 1, 0), (1, 1, 0)])
    # arms: vanilla skeleton's thin arm, centred on the shoulder
    for side, nm in ((-1, "r"), (1, "l")):
        arm = torso.bone("arm_" + nm, (side * (rw / 2 + 1), -9.5, 0), (-hunch - 3, 0, side * -2))
        arm.cube("arm_" + nm, -1, -2, -1, 2, 12, 2, mirror=side > 0, share=None if side < 0 else "arm_r")
        arm.bone("hand_" + nm, (0, 10, 0))
        ga = arm.bone("geara" + nm, (0, 0, 0))
        if p["vamb"]:
            ga.cube("vamb_" + nm, -1.5, 4, -1.5, 3, 5, 3, mirror=side > 0, share=None if side < 0 else "vamb_r")
        big = p["paul"] >= 3
        if p["paul"] >= 2 or side > 0:  # C wears one, on its left (drawing) shoulder
            w = 5 if big else 4
            ga.cube("pauldron_" + nm, -w / 2 + side * 0.5, -3.5 - big, -w / 2, w, 3 + big, w, mirror=side > 0,
                     share=None if side < 0 else ("pauldron_r" if p["paul"] >= 2 else None))
            if big:  # two spikes off the pauldron's crown, leaning out
                for k in range(2):
                    B.shard(ga, f"spike_{nm}{k}", (side * (1.0 + k * 1.0), -4.5, -1 + k * 2.0), (-10 - 12 * k, 0, side * (30 + 14 * k)),
                            [(2, 2, 0), (2, 1, 0)])
    if p["crystal"]:
        spots = [(rw / 2 - 1.5, -11, rd / 2 - 0.8, -40, 30, 5), (-rw / 2 + 2.0, -10.5, rd / 2 - 0.8, -50, -26, 4)]
        for k, (x, y, z, rx, rz, ln) in enumerate(spots[:p["crystal"]]):
            B.shard(torso, f"xtal{k}", (x, y, z), (rx, 0, rz), [(ln, 2, 0), (max(2, ln - 2), 1, 14)])
    if p["cloak"]:
        # a cloak of bone plates hung from the yoke, two lengths so it swings
        cl = torso.bone("cape", (0, -10.5, rd / 2 + 0.6), (-3, 0, 0))
        cl.cube("cape", -rw / 2 - 0.5, 0, 0, rw + 1, 8, 1)
        low = cl.bone("cape2", (0, 7.5, 0), (7, 0, 0))
        low.cube("cape2", -rw / 2 - 0.5, 0, 0, rw + 1, 8, 1)
    r.look = [("head", 0.85, 0.75), ("torso", 0.12, 0.08)]
    r.moves = {}
    skeleton_clips(r, rung, p)
    r.pack()
    return r


def skeleton_clips(r, rung, p):
    jaw, cloak = p["jaw"], p["cloak"]
    # ---- idle: no breath to speak of; the bones settle and click. The head ticks to a new angle and holds (a skeleton's stillness
    # is broken by small sharp moves, not a sway); the jaw works now and then
    idle = r.clip("idle", 5.0, loop=True)
    cycle(idle, "torso", "rot", lambda q: (0.8 * s(q), 0.6 * s(q, 0.3), 0), n=10)
    cycle(idle, "head", "rot", lambda q: (-1.5 * s(q, 0.1) + (2.5 if 0.42 < q < 0.58 else 0), 6 * (1 if 0.2 < q < 0.5 else -1 if 0.7 < q < 0.9 else 0) * 0.5,
                                          3 * (1 if 0.45 < q < 0.6 else 0)), n=20)
    cycle(idle, "arm_r", "rot", lambda q: (2 * s(q, 0.15), 0, 1.2 * c(q, 0.1)), n=10)
    cycle(idle, "arm_l", "rot", lambda q: (2 * s(q, 0.55), 0, -1.2 * c(q, 0.5)), n=10)
    if jaw:
        cycle(idle, "jaw", "rot", lambda q: (3 + 4 * max(0.0, s(q * 3, 0.1)) ** 4, 0, 0), n=20)
    if cloak:
        cycle(idle, "cape", "rot", lambda q: (1.5 * s(q, 0.2), 0, 0), n=10)
    # ---- walk: an upright, light step; the bones rattle (a quick tremor through the shoulders at twice the step), the head
    # bobs, the arms swing short and stiff
    walk = r.clip("walk", 1.1, loop=True)
    sw = 30
    cycle(walk, "leg_r", "rot", lambda q: (sw * s(q), 0, 0), n=12)
    cycle(walk, "leg_l", "rot", lambda q: (-sw * s(q), 0, 0), n=12)
    cycle(walk, "leg_r", "pos", lambda q: (0, 1.2 * max(0.0, -c(q)), 0), n=12)
    cycle(walk, "leg_l", "pos", lambda q: (0, 1.2 * max(0.0, c(q)), 0), n=12)
    cycle(walk, "hips", "pos", lambda q: (0.4 * s(q, 0.25), -0.7 * s(q) ** 2, 0), n=12)
    cycle(walk, "hips", "rot", lambda q: (0, 5 * s(q), 1.5 * s(q, 0.25)), n=12)
    cycle(walk, "torso", "rot", lambda q: (1.5 * s(2 * q), -6 * s(q), -2 * s(q, 0.25) + 1.2 * s(4 * q)), n=16)
    cycle(walk, "head", "rot", lambda q: (-2 * c(2 * q, 0.05), 4 * s(q), 2 * s(q, 0.3)), n=12)
    cycle(walk, "arm_r", "rot", lambda q: (-14 * s(q, 0.05), 0, 2 * s(q, 0.3)), n=12)
    cycle(walk, "arm_l", "rot", lambda q: (14 * s(q, 0.05), 0, 2 * s(q, 0.3)), n=12)
    if jaw:
        cycle(walk, "jaw", "rot", lambda q: (2.5 * max(0.0, s(4 * q)), 0, 0), n=16)
    if cloak:
        cycle(walk, "cape", "rot", lambda q: (-5 - 3 * s(2 * q, 0.2), 0, 2 * s(q)), n=12)
        cycle(walk, "cape2", "rot", lambda q: (4 * s(2 * q, 0.35), 0, 0), n=12)
    if p["tasset"]:
        cycle(walk, "skirt_r", "rot", lambda q: (-10 * max(0.0, s(q)), 0, 0), n=12)
        cycle(walk, "skirt_l", "rot", lambda q: (-10 * max(0.0, -s(q)), 0, 0), n=12)
    run = r.clip("run", 0.75, loop=True)
    rs = 44
    cycle(run, "leg_r", "rot", lambda q: (rs * s(q), 0, 0), n=12)
    cycle(run, "leg_l", "rot", lambda q: (-rs * s(q), 0, 0), n=12)
    cycle(run, "leg_r", "pos", lambda q: (0, 2.0 * max(0.0, -c(q)), 0), n=12)
    cycle(run, "leg_l", "pos", lambda q: (0, 2.0 * max(0.0, c(q)), 0), n=12)
    cycle(run, "hips", "pos", lambda q: (0, -1.2 * s(q) ** 2 - 0.4, 0), n=12)
    cycle(run, "torso", "rot", lambda q: (9 + 2 * s(2 * q), -8 * s(q), 0), n=12)
    cycle(run, "head", "rot", lambda q: (-8 - 2 * c(2 * q), 3 * s(q), 0), n=12)
    cycle(run, "arm_r", "rot", lambda q: (-36 * s(q, 0.05), 0, 3), n=12)
    cycle(run, "arm_l", "rot", lambda q: (36 * s(q, 0.05), 0, -3), n=12)
    if cloak:
        cycle(run, "cape", "rot", lambda q: (-22 - 4 * s(2 * q), 0, 0), n=12)
        cycle(run, "cape2", "rot", lambda q: (-8 + 5 * s(2 * q, 0.3), 0, 0), n=12)
    # ---- angry with a bow (vanilla's aim: the bow arm straight at the target, the drawing hand at the string), the bow held steady
    # while the body breathes under it
    angry = r.clip("angry", 4.0, loop=True)
    cycle(angry, "arm_r", "rot", lambda q: (-88 + 1.2 * s(q), -6, 0), n=8)
    cycle(angry, "arm_l", "rot", lambda q: (-88 + 1.2 * s(q), 30 + 1.5 * s(q, 0.3), 0), n=8)
    cycle(angry, "torso", "rot", lambda q: (2, 6, 0), n=4)
    cycle(angry, "head", "rot", lambda q: (-3, -6, 0), n=4)
    # ---- angry with a blade (no bow): the blade arm cocked up and back, the other forward to fend
    am = r.clip("angry_melee", 4.0, loop=True)
    cycle(am, "arm_r", "rot", lambda q: (-118 - 4 * s(q), 0, 12), n=8)
    cycle(am, "arm_l", "rot", lambda q: (-44 - 4 * s(q, 0.5), 8, -6), n=8)
    cycle(am, "torso", "rot", lambda q: (3, -8, 0), n=4)
    cycle(am, "head", "rot", lambda q: (-4, 8, 0), n=4)
    if jaw:
        cycle(am, "jaw", "rot", lambda q: (10 + 3 * s(2 * q), 0, 0), n=8)
    # ---- the blow (a blade skeleton's swing): from the cocked arm, a diagonal cut across, the body turning through it; from B a
    # back-hand cut on the return (the second hit)
    bite = r.clip("bite", 1.0 if rung >= 3 else 0.6)
    bite.rot("arm_r", [(0.0, 0, 0, 0), (0.04, -22, 0, 8), (0.12, 96, -24, -16), (0.3, 70, -16, -10), (0.42, 0, 0, 0)]
             + ([(0.5, 40, 22, 12), (0.6, 70, 34, 20), (0.78, 30, 10, 6), (1.0, 0, 0, 0)] if rung >= 3 else []))
    bite.rot("torso", [(0.0, 0, 0, 0), (0.04, -2, 10, 0), (0.12, 8, -22, 0), (0.3, 4, -12, 0), (0.42, 0, 0, 0)]
             + ([(0.6, 6, 18, 0), (0.78, 2, 8, 0), (1.0, 0, 0, 0)] if rung >= 3 else []))
    bite.pos("hips", [(0.0, 0, 0, 0), (0.12, 0, -0.4, -1.0), (0.42, 0, 0, 0)] + ([(0.6, 0, -0.4, -0.8), (1.0, 0, 0, 0)] if rung >= 3 else []))
    # ---- hurt: the frame rattles; the head knocked aside, the shoulders jolt, the bones shiver back into place
    hurt = r.clip("hurt", 0.45)
    hurt.rot("torso", [(0.0, 0, 0, 0), (0.06, -9, 6, 4), (0.14, -3, -3, -3), (0.22, -5, 2, 2), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0.0, 0, 0, 0), (0.06, -16, -10, -8), (0.16, 2, 6, 4), (0.45, 0, 0, 0)])
    hurt.rot("arm_r", [(0.0, 0, 0, 0), (0.07, -14, 0, 14), (0.18, 4, 0, 4), (0.45, 0, 0, 0)])
    hurt.rot("arm_l", [(0.0, 0, 0, 0), (0.07, -10, 0, -16), (0.18, 4, 0, -4), (0.45, 0, 0, 0)])
    hurt.pos("hips", [(0.0, 0, 0, 0), (0.06, 0, 0, 1.0), (0.45, 0, 0, 0)])
    if jaw:
        hurt.rot("jaw", [(0.0, 0, 0, 0), (0.05, 18, 0, 0), (0.12, 2, 0, 0), (0.2, 10, 0, 0), (0.45, 0, 0, 0)])
    # ---- death: it comes apart. The knees fold, the frame drops into a heap, the skull tips off the neck and rolls
    death = r.clip("death", 0.95)
    death.pos("hips", [(0.0, 0, 0, 0), (0.2, 0, 3, 0), (0.45, 0, 9.5, 1.5, "lin"), (0.55, 0, 9.0, 1.5), (0.95, 0, 9.2, 1.5)])
    death.rot("leg_r", [(0.0, 0, 0, 0), (0.2, -20, 0, 6), (0.45, -84, 14, 16), (0.95, -86, 16, 18)])
    death.rot("leg_l", [(0.0, 0, 0, 0), (0.2, -26, 0, -6), (0.45, -80, -18, -14), (0.95, -82, -20, -16)])
    death.rot("torso", [(0.0, 0, 0, 0), (0.2, 10, 0, 0), (0.45, 38, 8, 6), (0.6, 52, 10, 8), (0.95, 50, 10, 8)])
    death.rot("arm_r", [(0.0, 0, 0, 0), (0.3, -20, 0, 20), (0.55, -40, 0, 70), (0.95, -38, 0, 72)])
    death.rot("arm_l", [(0.0, 0, 0, 0), (0.3, -16, 0, -16), (0.55, -36, 0, -66), (0.95, -34, 0, -68)])
    death.rot("head", [(0.0, 0, 0, 0), (0.35, -10, 10, 0), (0.6, 30, 30, 50), (0.8, 40, 40, 80), (0.95, 40, 42, 84)])
    death.pos("head", [(0.0, 0, 0, 0), (0.35, 0, 0, 0), (0.6, -3, 3, -2), (0.8, -5, 5, -3), (0.95, -5, 5, -3)])
    if jaw:
        death.rot("jaw", [(0.0, 0, 0, 0), (0.3, 16, 0, 0), (0.6, 30, 0, 10), (0.95, 32, 0, 12)])
    if cloak:
        death.rot("cape", [(0.0, 0, 0, 0), (0.45, -30, 0, 0), (0.95, -40, 0, 0)])
    # ---- roar (an alpha's): it straightens, the arms spread with the bow out, the skull thrown back, the jaw wide
    roar = r.clip("roar", 1.6)
    roar.rot("torso", [(0.0, 0, 0, 0), (0.25, 5, 0, 0), (0.5, -12, 0, 0), (1.2, -10, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("head", [(0.0, 0, 0, 0), (0.25, 10, 0, 0), (0.5, -28, 0, 0), (0.8, -24, 6, 0), (1.05, -28, -6, 0), (1.6, 0, 0, 0)])
    roar.rot("arm_r", [(0.0, 0, 0, 0), (0.5, -20, 0, 50), (1.2, -16, 0, 46), (1.6, 0, 0, 0)])
    roar.rot("arm_l", [(0.0, 0, 0, 0), (0.5, -20, 0, -50), (1.2, -16, 0, -46), (1.6, 0, 0, 0)])
    if jaw:
        roar.rot("jaw", [(0.0, 0, 0, 0), (0.45, 6, 0, 0), (0.55, 34, 0, 0), (1.2, 30, 0, 0), (1.6, 0, 0, 0)])
    # ---- the moves, on top of the bow aim. snipe (C's marksman shot): a long, still draw, the body turning into it; the release
    # snaps the drawing hand back past the ear
    w = r.clip("snipe_windup", 1.0)
    w.rot("arm_l", [(0.0, 0, 0, 0), (0.7, -2, 26, 0), (1.0, -2, 30, 0)])
    w.rot("torso", [(0.0, 0, 0, 0), (0.7, 0, 10, 0), (1.0, 0, 12, 0)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, 4, -10, 6)])
    w.pos("hips", [(0.0, 0, 0, 0), (1.0, 0, -0.6, 0.4)])
    st = r.clip("snipe_strike", 0.5)
    st.rot("arm_l", [(0.0, -2, 30, 0), (0.05, -14, 62, 12), (0.25, -6, 40, 4), (0.5, 0, 0, 0)])
    st.rot("arm_r", [(0.0, 0, 0, 0), (0.05, -6, 0, 0), (0.25, 2, 0, 0), (0.5, 0, 0, 0)])
    st.rot("torso", [(0.0, 0, 12, 0), (0.05, -4, 14, 0), (0.5, 0, 0, 0)])
    st.rot("head", [(0.0, 4, -10, 6), (0.5, 0, 0, 0)])
    st.pos("hips", [(0.0, 0, -0.6, 0.4), (0.05, 0, -0.4, 1.0), (0.5, 0, 0, 0)])
    # volley (B, S): the bow tipped up to the sky, three or five quick draws and loosings
    w = r.clip("volley_windup", 1.0)
    w.rot("arm_r", [(0.0, 0, 0, 0), (0.6, -36, 0, 0), (1.0, -40, 0, 0)])
    w.rot("arm_l", [(0.0, 0, 0, 0), (0.6, -36, 16, 0), (1.0, -40, 22, 0)])
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, -10, 0, 0)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -26, 0, 0)])
    st = r.clip("volley_strike", 0.9)
    keys = [(0.0, -40, 22, 0)]
    for k in range(3):
        t0 = k * 0.22
        keys += [(t0 + 0.03, -44, 56, 10), (t0 + 0.14, -40, 22, 0)]
    keys += [(0.9, 0, 0, 0)]
    st.rot("arm_l", keys)
    st.rot("arm_r", [(0.0, -40, 0, 0), (0.66, -40, 0, 0), (0.9, 0, 0, 0)])
    st.rot("torso", [(0.0, -10, 0, 0), (0.66, -10, 0, 0), (0.9, 0, 0, 0)])
    st.rot("head", [(0.0, -26, 0, 0), (0.66, -24, 0, 0), (0.9, 0, 0, 0)])
    # blink (A's wraith step): it folds in on itself and shivers, then is flung upright where it lands
    w = r.clip("blink_windup", 1.0)
    w.pos("hips", [(0.0, 0, 0, 0), (0.6, 0, -2.5, 0.5), (1.0, 0, -3, 0.6)])
    w.rot("leg_r", [(0.0, 0, 0, 0), (1.0, -26, 0, 6)])
    w.rot("leg_l", [(0.0, 0, 0, 0), (1.0, -20, 0, -6)])
    w.rot("torso", [(0.0, 0, 0, 0), (0.5, 22, 0, 0), (0.7, 26, 3, 2), (0.8, 24, -3, -2), (0.9, 26, 3, 2), (1.0, 25, 0, 0)])
    w.rot("arm_r", [(0.0, 0, 0, 0), (1.0, 50, 0, 26)])
    w.rot("arm_l", [(0.0, 0, 0, 0), (1.0, 50, 0, -26)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -20, 0, 0)])
    st = r.clip("blink_strike", 0.6)
    st.pos("hips", [(0.0, 0, -3, 0.6), (0.05, 0, 0.5, 0), (0.6, 0, 0, 0)])
    st.rot("leg_r", [(0.0, -26, 0, 6), (0.05, 6, 0, 0), (0.6, 0, 0, 0)])
    st.rot("leg_l", [(0.0, -20, 0, -6), (0.05, 4, 0, 0), (0.6, 0, 0, 0)])
    st.rot("torso", [(0.0, 25, 0, 0), (0.05, -10, 0, 0), (0.3, -4, 0, 0), (0.6, 0, 0, 0)])
    st.rot("arm_r", [(0.0, 50, 0, 26), (0.05, -30, 0, 40), (0.6, 0, 0, 0)])
    st.rot("arm_l", [(0.0, 50, 0, -26), (0.05, -30, 0, -40), (0.6, 0, 0, 0)])
    st.rot("head", [(0.0, -20, 0, 0), (0.05, 8, 0, 0), (0.6, 0, 0, 0)])


BONES = {
    "skeleton": ["#2a2722", "#57524a", "#8a8476", "#b8b2a1", "#dcd7c7", "#f4f1e6"],
    "stray": ["#20272d", "#46535c", "#71808b", "#9eadb7", "#c7d3da", "#e8f1f4"],
}
RAGS = {"skeleton": ["#1b1714", "#2c2621", "#3e352e", "#51463c"], "stray": ["#141c20", "#22303a", "#334652", "#475f6c"]}


def crystal_glow(P, r, rung):
    """The land's crystal: a light inside, brighter toward the tip, a lit edge (the same on every body)."""
    rank = R.hexc(B.RANK[rung])
    for cu in r.cubes:
        if cu.name.startswith(("xtal", "crown")) and P.own(cu):
            tip = not cu.name.endswith("_s1")
            for fname in ("north", "west", "east", "south", "top"):
                f = P.face(cu, fname, glow=True)
                for i, j in f.each():
                    if tip:
                        f.set(i, j, rank[:3] + (min(235, 150 + 20 * (rung - 3)) if fname != "top" else 235,))
                    elif i == 0 or j == 0:
                        f.set(i, j, rank[:3] + (80,))


def paint_skeleton(r, rung, species, fam):
    p = K[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    rng = random.Random(R._h(r.id, species, fam))
    bone = B.tint_ramp(BONES[species], fam)
    old = R.Ramp(*BONES[species][:5])   # the fused plates and horns: older, darker bone
    rags = B.tint_ramp(RAGS[species], fam)
    cracks = R.p_strokes(cell=5, length=3, dark=0.55, light=0.15, density=0.22, seed=rung, tops=False)

    def suture(face, i, j):
        # fused bone: a zigzag seam where the plates grew together, pitted a little
        if face.w >= 4 and face.h >= 4 and j == (face.h // 2 + (1 if i % 2 else 0)):
            return -1.1
        return 0.0

    for cu in r.cubes:
        n = cu.name
        if n.startswith(("leg_", "arm_")):
            P.paint(cu, bone, 2.5, R.combine(cracks, lambda f, i, j: -0.7 if j in (5, 6) else 0.0), noise=0.4)   # the knuckle of the joint
        elif n.startswith("torso_rib") or n == "torso_yoke":
            P.paint(cu, bone, 2.7, lambda f, i, j: -0.9 if f.name in ("north", "south") and i in (0, f.w - 1) else 0.0, grad=0.6, rim=0.5)
        elif n == "torso_spine":
            P.paint(cu, bone, 1.9, lambda f, i, j: -1.0 if j % 2 == 1 else 0.0)   # vertebrae
        elif n == "pelvis":
            P.paint(cu, bone, 2.2, cracks)
        elif n.startswith(("plate", "pauldron", "vamb", "greave")):
            P.paint(cu, old, 2.6, suture, rim=0.9, ao=0.6, noise=0.2)
        elif n.startswith("skirt"):
            if species == "stray":   # a stray's rags hang where the plates would be
                P.paint(cu, rags, 1.8, lambda f, i, j: None if j >= f.h - R._h("rag", i) % 2 else -0.3 * (i % 2))
            else:
                P.paint(cu, old, 2.2, suture, rim=0.7)
        elif n.startswith(("horn", "spike")):
            P.paint(cu, bone, 3.0, lambda f, i, j: -0.7 if j % 3 == 2 else 0.0, grad=1.2, rim=0.8)   # ringed horn, pale
        elif n.startswith(("xtal", "crown")):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n == "heart":
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.6, None, grad=0.4, rim=0.4, ao=0.2)
        elif n in ("cape", "cape2"):
            low = n == "cape2"

            def shingles(face, i, j, low=low):
                if face.name in ("north", "south"):
                    if low and j >= face.h - (1 + R._h("cl", i // 2) % 3):
                        return None
                    if species == "stray":
                        return -0.6 if i % 3 == 2 else 0.0
                    return -1.0 if j % 3 == 2 else (-0.5 if (i + (j // 3)) % 3 == 0 else 0.0)   # overlapping plates of bone
                return 0.0
            P.paint(cu, rags if species == "stray" else old, 2.0, shingles, rim=0.5)
        elif n == "brow":
            P.paint(cu, bone, 2.0, None)
        else:  # head, jaw
            P.paint(cu, bone, 2.8, R.combine(cracks, lambda f, i, j: -0.5 if f.name == "top" and (i + j) % 7 == 0 else 0.0), noise=0.5)
    # the face: a nose hole, the teeth (C on the skull, from B on the loose jaw)
    fn = P.face("head", "north")
    fn.set(3, 5, R.hexc("#0c0a08"))
    fn.set(4, 5, R.hexc("#0c0a08"))
    if not p["jaw"]:
        for i in range(1, 7):
            fn.set(i, 6, bone.at(3.4) if i % 2 else R.hexc("#100d0b"))
        for i in range(2, 6):
            fn.set(i, 7, R.hexc("#1a1612"))
    else:
        fj = P.face("jaw", "top")
        for i in range(fj.w):
            fj.set(i, 0, bone.at(3.4) if i % 2 == 0 else R.hexc("#100d0b"))
        fs = P.face("jaw", "north")
        for i in range(fs.w):
            fs.set(i, 0, bone.at(3.2) if i % 2 == 0 else bone.at(1.4))
        fh = P.face("head", "bottom")
        for i in range(1, fh.w - 1):   # the upper teeth seen under the skull
            fh.set(i, 0, bone.at(3.2) if i % 2 else R.hexc("#100d0b"))
    fb = P.face("brow", "bottom")
    for i, j in fb.each():
        fb.set(i, j, bone.at(0.8))
    B.socket_eyes(P, "head", rung, skin=bone.at(2.0))
    B.weathering(P, r, fam, rng, ["head", "torso_yoke", "pauldron_r", "pauldron_l", "pelvis"], 1 + (fam > 0))
    rank = R.hexc(B.RANK[rung])
    if p["heart"]:
        for fname in ("north", "west", "east", "south", "top", "bottom"):
            f = P.face("heart", fname, glow=True)
            for i, j in f.each():
                f.set(i, j, R.lerpc(rank, R.hexc("#ffffff"), 0.35 if (i + j) % 2 else 0.1)[:3] + (200 + 15 * (rung - 3),))
    crystal_glow(P, r, rung)
    return P.skin, P.glow, P.blink()


# =====================================================================================================================
# The spider (and the cave spider): a brood stalker that truly grows. Long jointed legs with the knee high; C lean and quick;
# B the brood sac lit under the abdomen; A a plated back and bladed shins, the crystal out of the plates; S an eye crown.

SP = {
    #    thorax head(w,h,d)  abdomen(w,h,d) body-height femur up(deg) drop(deg) fangs  sac    plates blades crystal crown
    2: dict(thor=7, head=(8, 7, 7), abd=(11, 9, 13), hip=10, femur=9, up=34, drop=72, sac=False, plates=0, blades=False, crystal=0, crown=False),
    3: dict(thor=8, head=(9, 8, 8), abd=(13, 10, 15), hip=12, femur=11, up=34, drop=72, sac=True, plates=0, blades=False, crystal=0, crown=False),
    4: dict(thor=9, head=(10, 8, 9), abd=(15, 12, 17), hip=14, femur=12, up=36, drop=74, sac=True, plates=3, blades=True, crystal=2, crown=False),
    5: dict(thor=10, head=(11, 9, 10), abd=(17, 13, 19), hip=16, femur=14, up=36, drop=74, sac=True, plates=3, blades=True, crystal=2, crown=True),
}
# each pair of legs: where it leaves the thorax (fraction of its depth) and how far it fans forward (degrees)
SP_LEGS = ((-0.42, 42), (-0.14, 14), (0.14, -14), (0.42, -42))


def spider(rung):
    p = SP[rung]
    r = R.Rig(f"form_spider_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 1.0 + 0.25 * (rung - 2), 2.2 + 0.4 * (rung - 2)
    r.run_speed = (0.22, 0.34)
    r.shadow = 0.9 + 0.2 * (rung - 2)
    t = p["thor"]
    hw, hh, hd = p["head"]
    aw, ah, ad = p["abd"]
    base = r.bone("base", (0, 24, 0))
    body = base.bone("torso", (0, -p["hip"], 0))
    body.cube("torso", -t / 2, -t / 2, -t / 2, t, t, t)
    head = body.bone("head", (0, 0, -t / 2 + 0.5))
    head.cube("head", -hw / 2, -hh / 2 - 0.5, -hd, hw, hh, hd)
    for side, nm in ((-1, "r"), (1, "l")):   # the fangs, hinged under the face (the life layer works them as jaws)
        f = head.bone("jaw_" + nm, (side * 1.6, hh / 2 - 1.5, -hd + 0.5), (8, 0, side * 6))
        B.shard(f, "fang_" + nm, (0, 0, 0), (180, 0, 0), [(2, 2, 0), (2, 1, 22)])
    B.eye_bones(head, (1.8, -1.8), -1.0, -hd - 0.15)
    if p["crown"]:   # a crown of more eyes over the brow, each on its own raised mound
        gc = head.bone("gearc", (0, 0, 0))
        for k, x in enumerate((-3.5, -1.5, 0.5, 2.5)):
            gc.cube(f"crowneye{k}", x, -hh / 2 - 1.5 - (1 if k in (1, 2) else 0), -hd + 1 + (0 if k in (1, 2) else 1), 1, 1 + (k in (1, 2)), 2)
    ab = body.bone("abdomen", (0, -1, t / 2 - 0.5), (-14, 0, 0))
    ab.cube("abdomen", -aw / 2, -ah / 2, 0, aw, ah, ad)
    ga = ab.bone("geara", (0, 0, 0))
    if p["sac"]:   # the brood sac: a swelling under the abdomen's back half, lit from inside
        ga.cube("sac", -aw / 2 + 2, ah / 2, round(ad * 0.45), aw - 4, 2, round(ad * 0.45))
    for k in range(p["plates"]):   # overlapping plates down the back
        w = aw - 2 - k * 2
        ga.cube(f"plate{k}", -w / 2, -ah / 2 - 1, 1 + k * (ad // 3), w, 1, ad // 3 + 1)
    if p["crystal"]:
        for k, (x, z, rz, ln) in enumerate(((-2.5, ad * 0.35, -24, 5), (2.0, ad * 0.62, 28, 4))[:p["crystal"]]):
            B.shard(ga, f"xtal{k}", (x, -ah / 2 - 1, z), (-34, 0, rz), [(ln, 2, 0), (max(2, ln - 2), 1, -16)])
    spinner = ab.bone("spinner", (0, 0, ad), (0, 0, 0))
    spinner.cube("spinner", -1.5, -1, -0.5, 3, 2, 1)
    # the legs: a fan bone at the thorax (front pairs reach forward), the femur rising to a high knee, the shin falling to the ground
    hip = p["hip"]
    lf = p["femur"]
    up, drop = math.radians(p["up"]), math.radians(p["drop"])
    knee = hip - 0.5 + lf * math.sin(up) - 1.0   # the knee's height, less the shin's half thickness at the foot
    lt = round(knee / math.sin(drop))
    drop = math.degrees(math.asin(min(1.0, knee / lt)))   # the shin's angle so the foot sits on the ground exactly
    for side, nm in ((-1, "r"), (1, "l")):
        for k, (fz, fan) in enumerate(SP_LEGS):
            n = f"{nm}{k + 1}"
            cx = body.bone(f"coxa_{n}", (side * (t / 2 - 0.5), 0.5, fz * t), (0, side * fan, 0))
            fem = cx.bone(f"leg_{n}", (0, 0, 0), (0, 0, -side * p["up"]))
            if side > 0:
                fem.cube(f"leg_{n}", 0, -1, -1, lf, 2, 2)
            else:
                fem.cube(f"leg_{n}", -lf, -1, -1, lf, 2, 2, mirror=True)
            shin = fem.bone(f"shin_{n}", (side * (lf - 0.5), 0, 0), (0, 0, side * (p["up"] + drop)))
            if side > 0:
                shin.cube(f"shin_{n}", 0, -1, -1, lt, 2, 2)
                if p["blades"]:   # a blade along the top of the shin
                    shin.cube(f"blade_{n}", 2, -2, -0.5, lt - 5, 1, 1)
            else:
                shin.cube(f"shin_{n}", -lt, -1, -1, lt, 2, 2, mirror=True)
                if p["blades"]:
                    shin.cube(f"blade_{n}", -lt + 3, -2, -0.5, lt - 5, 1, 1, mirror=True)
    r.look = [("head", 0.6, 0.5)]
    r.moves = {}
    spider_clips(r, rung, p)
    r.pack()
    return r


def spider_clips(r, rung, p):
    legs = [f"{nm}{k}" for nm in "rl" for k in range(1, 5)]
    # the alternating gait: L1 R2 L3 R4 together, then the others
    group = {f"l{k}": (k % 2) * 0.5 for k in range(1, 5)} | {f"r{k}": ((k + 1) % 2) * 0.5 for k in range(1, 5)}
    side = {n: (1 if n[0] == "l" else -1) for n in legs}
    # ---- idle: the legs settle one by one (a foot lifts and sets down), the fangs work, the abdomen breathes
    idle = r.clip("idle", 4.0, loop=True)
    for k, n in enumerate(legs):
        ph = (k * 0.37) % 1.0
        cycle(idle, f"leg_{n}", "rot", lambda q, ph=ph, sd=side[n]: (0, 0, -sd * 5 * max(0.0, s(q, ph)) ** 6), n=16)
        cycle(idle, f"coxa_{n}", "rot", lambda q, ph=ph, sd=side[n]: (0, sd * 2.5 * s(q, ph + 0.1), 0), n=8)
    cycle(idle, "abdomen", "rot", lambda q: (2 * s(q), 0, 1 * s(q, 0.3)), n=8)
    cycle(idle, "abdomen", "scale", lambda q: (1 + 0.012 * s(2 * q), 1 + 0.02 * s(2 * q), 1 + 0.012 * s(2 * q)), n=8)
    cycle(idle, "torso", "pos", lambda q: (0, 0.25 * s(q, 0.2), 0), n=8)
    cycle(idle, "head", "rot", lambda q: (2 * s(q, 0.1), 5 * (1 if 0.3 < q < 0.55 else -1 if 0.75 < q < 0.9 else 0), 0), n=20)
    for nm, sd in (("r", -1), ("l", 1)):
        cycle(idle, "jaw_" + nm, "rot", lambda q, sd=sd: (6 * max(0.0, s(3 * q, 0.1 if sd < 0 else 0.15)) ** 3, 0, 0), n=20)
    # ---- walk: each leg lifts high as it swings forward and pushes back flat on the ground; the body glides, swaying a little
    walk = r.clip("walk", 0.9, loop=True)
    for n in legs:
        ph, sd = group[n], side[n]
        cycle(walk, f"coxa_{n}", "rot", lambda q, ph=ph, sd=sd: (0, sd * 16 * s(q, ph), 0), n=12)
        cycle(walk, f"leg_{n}", "rot", lambda q, ph=ph, sd=sd: (0, 0, -sd * 16 * max(0.0, c(q, ph))), n=12)
        cycle(walk, f"shin_{n}", "rot", lambda q, ph=ph, sd=sd: (0, 0, sd * 8 * max(0.0, c(q, ph))), n=12)
    cycle(walk, "torso", "pos", lambda q: (0.3 * s(q), -0.3 * c(2 * q), 0), n=12)
    cycle(walk, "torso", "rot", lambda q: (0, 3 * s(q), 1.5 * s(q, 0.25)), n=12)
    cycle(walk, "abdomen", "rot", lambda q: (2 * s(2 * q, 0.2), -4 * s(q, 0.15), 0), n=12)
    run = r.clip("run", 0.5, loop=True)
    for n in legs:
        ph, sd = group[n], side[n]
        cycle(run, f"coxa_{n}", "rot", lambda q, ph=ph, sd=sd: (0, sd * 24 * s(q, ph), 0), n=12)
        cycle(run, f"leg_{n}", "rot", lambda q, ph=ph, sd=sd: (0, 0, -sd * 22 * max(0.0, c(q, ph))), n=12)
        cycle(run, f"shin_{n}", "rot", lambda q, ph=ph, sd=sd: (0, 0, sd * 10 * max(0.0, c(q, ph))), n=12)
    cycle(run, "torso", "pos", lambda q: (0, -0.5 * c(2 * q) - 0.6, 0), n=12)
    cycle(run, "abdomen", "rot", lambda q: (4 + 3 * s(2 * q, 0.2), -5 * s(q, 0.15), 0), n=12)
    # ---- angry: it rears; the front pair lifts off the ground and spreads, the fangs open, the abdomen tips up
    angry = r.clip("angry", 3.0, loop=True)
    cycle(angry, "torso", "rot", lambda q: (-12 + 1.5 * s(q), 0, 0), n=8)
    cycle(angry, "torso", "pos", lambda q: (0, -1.2, 0.6), n=4)
    for nm, sd in (("r", -1), ("l", 1)):
        cycle(angry, f"leg_{nm}1", "rot", lambda q, sd=sd: (0, 0, -sd * (30 + 5 * s(q, 0.25 if sd > 0 else 0.0))), n=8)
        cycle(angry, f"coxa_{nm}1", "rot", lambda q, sd=sd: (0, sd * 12, 0), n=4)
        cycle(angry, f"shin_{nm}1", "rot", lambda q, sd=sd: (0, 0, -sd * (62 + 4 * s(q, 0.4))), n=8)   # the foot held up and forward
        cycle(angry, f"leg_{nm}2", "rot", lambda q, sd=sd: (0, 0, -sd * 8), n=4)
        cycle(angry, "jaw_" + nm, "rot", lambda q, sd=sd: (14 + 4 * s(3 * q), 0, sd * 8), n=8)
    cycle(angry, "abdomen", "rot", lambda q: (14 + 2 * s(q, 0.3), 0, 0), n=8)
    cycle(angry, "head", "rot", lambda q: (8, 0, 0), n=4)
    # ---- the bite: the front legs come down like a trap and the fangs strike; from B the second is a stab of a bladed foreleg
    bite = r.clip("bite", 1.0 if rung >= 3 else 0.6)
    bite.pos("torso", [(0.0, 0, 0, 0), (0.06, 0, -0.6, 1.0), (0.14, 0, 0.8, -2.2), (0.42, 0, 0, 0)])
    bite.rot("torso", [(0.0, 0, 0, 0), (0.06, -6, 0, 0), (0.14, 10, 0, 0), (0.42, 0, 0, 0)])
    for nm, sd in (("r", -1), ("l", 1)):
        bite.rot(f"leg_{nm}1", [(0.0, 0, 0, 0), (0.06, 0, -sd * 14, 0), (0.14, 0, 0, sd * 26), (0.42, 0, 0, 0)])
        bite.rot("jaw_" + nm, [(0.0, 0, 0, 0), (0.06, 26, 0, sd * 10), (0.14, -6, 0, 0), (0.3, 6, 0, 0), (0.42, 0, 0, 0)])
    if rung >= 3:
        bite.rot("leg_l1", [(0.42, 0, 0, 0), (0.5, 0, 22, -40), (0.6, 0, -10, 30), (0.78, 0, 0, 6), (1.0, 0, 0, 0)])
        bite.rot("torso", [(0.42, 0, 0, 0), (0.6, 6, -8, 0), (1.0, 0, 0, 0)])
    # ---- hurt: the legs flinch in, the body drops and jerks back
    hurt = r.clip("hurt", 0.4)
    hurt.pos("torso", [(0.0, 0, 0, 0), (0.06, 0, 1.0, 1.2), (0.4, 0, 0, 0)])
    hurt.rot("abdomen", [(0.0, 0, 0, 0), (0.06, -10, 6, 0), (0.4, 0, 0, 0)])
    for n in legs:
        hurt.rot(f"leg_{n}", [(0.0, 0, 0, 0), (0.06, 0, 0, -side[n] * 14), (0.4, 0, 0, 0)])
    # ---- death: it rolls over onto its back, the legs curling in over it, as a dead spider's do
    death = r.clip("death", 0.95)
    lift = p["hip"] + 1 + p["abd"][1] / 2
    death.rot("base", [(0.0, 0, 0, 0), (0.25, 0, 0, 30), (0.5, 0, 0, 120, "lin"), (0.65, 0, 0, 180), (0.95, 0, 0, 180)])
    death.pos("base", [(0.0, 0, 0, 0), (0.25, 0, -2, 0), (0.5, 0, -lift * 0.8, 0), (0.65, 0, -lift, 0), (0.95, 0, -lift, 0)])
    death.rot("abdomen", [(0.0, 0, 0, 0), (0.6, 12, 0, 0), (0.95, 14, 0, 0)])
    for n in legs:
        sd = side[n]
        death.rot(f"leg_{n}", [(0.0, 0, 0, 0), (0.3, 0, 0, sd * 10), (0.7, 0, 0, sd * 26), (0.95, 0, 0, sd * 28)])
        death.rot(f"shin_{n}", [(0.0, 0, 0, 0), (0.4, 0, 0, sd * 30), (0.8, 0, 0, sd * 58), (0.95, 0, 0, sd * 60)])
    # ---- roar (an alpha's): it rears high, every foreleg up and spread, the abdomen raised, the fangs wide
    roar = r.clip("roar", 1.6)
    roar.rot("torso", [(0.0, 0, 0, 0), (0.4, -26, 0, 0), (1.2, -24, 0, 0), (1.6, 0, 0, 0)])
    roar.pos("torso", [(0.0, 0, 0, 0), (0.4, 0, -2.5, 1.5), (1.2, 0, -2.4, 1.5), (1.6, 0, 0, 0)])
    roar.rot("abdomen", [(0.0, 0, 0, 0), (0.4, 30, 0, 0), (1.2, 28, 0, 0), (1.6, 0, 0, 0)])
    for nm, sd in (("r", -1), ("l", 1)):
        for k, amt in ((1, 60), (2, 34)):
            roar.rot(f"leg_{nm}{k}", [(0.0, 0, 0, 0), (0.4, 0, 0, -sd * amt), (0.7, 0, sd * 8, -sd * (amt + 6)), (1.0, 0, -sd * 4, -sd * amt), (1.6, 0, 0, 0)])
        roar.rot("jaw_" + nm, [(0.0, 0, 0, 0), (0.45, 30, 0, sd * 14), (1.2, 26, 0, sd * 12), (1.6, 0, 0, 0)])
    # ---- the moves. lunge (C's drop): it crouches on every leg, then springs, forelegs thrown out
    w = r.clip("lunge_windup", 1.0)
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, 3.0, 1.0)])
    for n in legs:
        w.rot(f"leg_{n}", [(0.0, 0, 0, 0), (1.0, 0, 0, -side[n] * 14)])
    w.rot("abdomen", [(0.0, 0, 0, 0), (1.0, 10, 0, 0)])
    st = r.clip("lunge_strike", 0.7)
    st.pos("torso", [(0.0, 0, 3.0, 1.0), (0.06, 0, -2.0, -3.0), (0.4, 0, -1.0, -1.5), (0.7, 0, 0, 0)])
    for n in legs:
        sd = side[n]
        front = n.endswith("1") or n.endswith("2")
        st.rot(f"leg_{n}", [(0.0, 0, 0, -sd * 14), (0.06, 0, 0, -sd * (40 if front else -6)), (0.4, 0, 0, -sd * (20 if front else 0)), (0.7, 0, 0, 0)])
    # web (a crowned C keeps D's): the abdomen curls under and aims its spinner, then flicks the line out
    w = r.clip("web_windup", 1.0)
    w.rot("abdomen", [(0.0, 0, 0, 0), (1.0, -34, 0, 0)])
    w.rot("spinner", [(0.0, 0, 0, 0), (1.0, -30, 0, 0)])
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, -1.0, 1.0)])
    st = r.clip("web_strike", 0.5)
    st.rot("abdomen", [(0.0, -34, 0, 0), (0.06, -10, 0, 0), (0.5, 0, 0, 0)])
    st.rot("spinner", [(0.0, -30, 0, 0), (0.06, 10, 0, 0), (0.5, 0, 0, 0)])
    st.pos("torso", [(0.0, 0, -1.0, 1.0), (0.5, 0, 0, 0)])
    # spit (B): the head and thorax draw back, the abdomen clenches, then the head snaps forward
    w = r.clip("spit_windup", 1.0)
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, -10, 0, 0)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -14, 0, 0)])
    w.rot("abdomen", [(0.0, 0, 0, 0), (0.6, 18, 0, 0), (1.0, 22, 0, 0)])
    w.scale("abdomen", [(0.0, 1, 1, 1), (1.0, 0.94, 1.06, 0.96)])
    st = r.clip("spit_strike", 0.5)
    st.rot("torso", [(0.0, -10, 0, 0), (0.05, 8, 0, 0), (0.5, 0, 0, 0)])
    st.rot("head", [(0.0, -14, 0, 0), (0.05, 14, 0, 0), (0.5, 0, 0, 0)])
    st.rot("abdomen", [(0.0, 22, 0, 0), (0.08, -4, 0, 0), (0.5, 0, 0, 0)])
    st.scale("abdomen", [(0.0, 0.94, 1.06, 0.96), (0.08, 1.04, 0.96, 1.03), (0.5, 1, 1, 1)])
    for nm, sd in (("r", -1), ("l", 1)):
        st.rot("jaw_" + nm, [(0.0, 0, 0, 0), (0.04, 30, 0, sd * 16), (0.5, 0, 0, 0)])
    # summon (A's brood): the abdomen lifts and swells, pulsing; it shivers, and the brood comes
    w = r.clip("summon_windup", 1.0)
    w.rot("abdomen", [(0.0, 0, 0, 0), (0.5, 24, 0, 0), (0.7, 26, 0, 3), (0.8, 24, 0, -3), (0.9, 26, 0, 3), (1.0, 25, 0, 0)])
    w.scale("abdomen", [(0.0, 1, 1, 1), (0.5, 1.06, 1.08, 1.04), (0.75, 1.02, 1.03, 1.0), (1.0, 1.08, 1.1, 1.05)])
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, 1.5, 0)])
    st = r.clip("summon_strike", 0.8)
    st.rot("abdomen", [(0.0, 25, 0, 0), (0.1, 8, 0, 0), (0.8, 0, 0, 0)])
    st.scale("abdomen", [(0.0, 1.08, 1.1, 1.05), (0.1, 0.95, 0.94, 0.98), (0.8, 1, 1, 1)])
    st.pos("torso", [(0.0, 0, 1.5, 0), (0.8, 0, 0, 0)])
    # pull (S): the forelegs reach out and haul in, hand over hand, while the body braces back
    w = r.clip("pull_windup", 1.0)
    w.pos("torso", [(0.0, 0, 0, 0), (1.0, 0, 0.5, 1.5)])
    for nm, sd in (("r", -1), ("l", 1)):
        w.rot(f"leg_{nm}1", [(0.0, 0, 0, 0), (1.0, 0, sd * 10, -sd * 40)])
        w.rot(f"shin_{nm}1", [(0.0, 0, 0, 0), (1.0, 0, 0, -sd * 40)])
    st = r.clip("pull_strike", 1.2)
    for nm, sd, ph in (("r", -1, 0.0), ("l", 1, 0.25)):
        keys = [(0.0, 0, sd * 10, -sd * 40)]
        for k in range(3):
            t0 = ph + k * 0.3
            keys += [(t0 + 0.1, 0, -sd * 10, -sd * 20), (t0 + 0.25, 0, sd * 10, -sd * 40)]
        keys += [(1.2, 0, 0, 0)]
        st.rot(f"leg_{nm}1", [k_ for k_ in keys if k_[0] <= 1.2])
    st.pos("torso", [(0.0, 0, 0.5, 1.5), (1.0, 0, 0.5, 1.5), (1.2, 0, 0, 0)])


CARAPACE = {
    "spider": ["#0d0b0b", "#1b1716", "#2b2523", "#3d3431", "#524641", "#6a5b54"],
    "cave_spider": ["#071214", "#0d2125", "#153338", "#1f4a50", "#2c6168", "#3c7b82"],
}
MARK = {"spider": ("#5a2a22", "#7d3b2e"), "cave_spider": ("#24504f", "#3b7a77")}


def paint_spider(r, rung, species, fam):
    p = SP[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    rng = random.Random(R._h(r.id, species, fam))
    shell = B.tint_ramp(CARAPACE[species], fam)
    hair = R.p_fur(depth=0.9, every=2, seed=rung, length=3)
    bristle = R.p_strokes(cell=3, length=2, dark=0.45, light=0.3, density=0.5, seed=rung + 5)
    mark = [R.hexc(x) for x in MARK[species]]
    rank = R.hexc(B.RANK[rung])
    for cu in r.cubes:
        n = cu.name
        if n.startswith(("leg_", "shin_")):
            # banded joints, bristles along the legs
            P.paint(cu, shell, 2.0, R.combine(hair, lambda f, i, j: 0.8 if f.name in ("north", "south", "top", "bottom") and i % 5 == 0 else 0.0),
                    noise=0.4)
        elif n.startswith(("blade", "plate")):
            P.paint(cu, shell, 3.0, lambda f, i, j: 0.9 if j == 0 else 0.0, grad=0.4, rim=1.2, ao=0.4)   # hard, polished
        elif n.startswith("fang"):
            P.paint(cu, R.Ramp("#130d0c", "#3a2420", "#6d4036", "#9c6151"), 1.8, None, grad=1.4)
        elif n == "abdomen":
            def pattern(f, i, j):
                # the mark on the back: a pale chevron down the middle (vanilla's is a band), and bristles
                if f.name == "top":
                    mid = f.w / 2.0
                    if abs(i + 0.5 - mid) < 1.0 + (j % 6) * 0.5 and j % 6 < 3 and j > 1:
                        return mark[1] if j % 6 == 1 else mark[0]
                return bristle(f, i, j)
            P.paint(cu, shell, 2.1, pattern, noise=0.5)
        elif n.startswith(("xtal",)):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n == "sac":
            P.paint(cu, R.Ramp("#120c10", "#24161f", "#3a2433", "#563447"), 2.0, lambda f, i, j: -0.6 if (i + j) % 3 == 0 else 0.0)
        else:  # thorax, head, crown ridge, spinner
            P.paint(cu, shell, 2.4, bristle, noise=0.4)
    # the face: eight eyes, two big ones lit with the land's light (the halos stand on them), six small in two rows
    face = P.face("head", "north")
    sock = R.hexc("#040303")
    cx = face.w // 2
    big = ((cx - 3, cx - 2), (cx + 1, cx + 2))
    ey = face.h // 2 - 1
    for a, b in big:
        for i in (a, b):
            face.set(i, ey, sock)
            face.set(i, ey + 1, sock)
    smalls = [(cx - 4, ey - 2), (cx - 1, ey - 2), (cx, ey - 2), (cx + 3, ey - 2), (cx - 5, ey), (cx + 4, ey)]
    for i, j in smalls:
        face.set(i, j, sock)
    g = P.face("head", "north", glow=True)
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    for a, b in big:
        inner = b if a < cx else a
        for i in (a, b):
            g.set(i, ey, (core if i == inner else rank)[:3] + (255 if i == inner else 170,))
            g.set(i, ey + 1, rank[:3] + (120,))
    for i, j in smalls:
        g.set(i, j, rank[:3] + (190,))
    if p["crown"]:   # the crown's eyes: each mound's face lit
        for k in range(4):
            P.face(f"crowneye{k}", "north", glow=True).set(0, 0, core[:3] + (255,))
            P.face(f"crowneye{k}", "north").set(0, 0, sock)
    if p["sac"]:   # the sac, lit from inside through its skin
        for fname in ("bottom", "west", "east", "south"):
            f = P.face("sac", fname, glow=True)
            for i, j in f.each():
                if (i + j) % 3 != 0:
                    f.set(i, j, rank[:3] + (90 + 25 * (rung - 3) if fname == "bottom" else 60,))
    B.weathering(P, r, fam, rng, ["abdomen", "head"], 1 + (fam > 0))
    crystal_glow(P, r, rung)
    return P.skin, P.glow, None


# =====================================================================================================================
# The creeper: a bloom. Petals of its own green skin close round its neck like a bud; when it swells they flare open and the light
# runs up seams from its feet to a core in its chest. C a tight bud; B six petals and the first seams; A eight, taller, the crystal
# in the bloom; S a full flower, a crown of petals on the head, the core bright.

CR = {
    2: dict(leg=6, body=(8, 12, 4), head=8, petals=4, plen=3, seams=False, core=False, crystal=0, crown=False),
    3: dict(leg=7, body=(8, 13, 5), head=8, petals=6, plen=4, seams=True, core=False, crystal=0, crown=False),
    4: dict(leg=7, body=(9, 14, 5), head=9, petals=8, plen=4, seams=True, core=True, crystal=2, crown=False),
    5: dict(leg=8, body=(9, 15, 5), head=9, petals=8, plen=5, seams=True, core=True, crystal=1, crown=True),
}


def creeper(rung):
    p = CR[rung]
    r = R.Rig(f"form_creeper_{rung}", (128, 128))
    r.walk_stride, r.run_stride = 1.3, 2.4
    r.run_speed = (0.22, 0.34)
    r.shadow = 0.5 + 0.05 * (rung - 2)
    bw, bh, bd = p["body"]
    hs = p["head"]
    L = p["leg"]
    base = r.bone("base", (0, 24, 0))
    hips = base.bone("hips", (0, -L, 0))
    for k, (x, z) in enumerate(((-2, -bd / 2 - 2), (2, -bd / 2 - 2), (-2, bd / 2 + 2), (2, bd / 2 + 2))):
        nm = ("fr", "fl", "br", "bl")[k]
        leg = hips.bone("leg_" + nm, (x, 0, z))
        leg.cube("leg_" + nm, -2, 0, -2, 4, L, 4, mirror=x > 0)
    torso = hips.bone("torso", (0, 0, 0))
    torso.cube("torso", -bw / 2, -bh, -bd / 2, bw, bh, bd)
    gt = torso.bone("geart", (0, 0, 0))
    if p["core"]:   # the core: a knot under the skin of the chest, swollen with the light
        gt.cube("core", -1.5, -bh + 4, -bd / 2 - 1, 3, 3, 1)
    head = torso.bone("head", (0, -bh, 0))
    head.cube("head", -hs / 2, -hs, -hs / 2, hs, hs, hs)
    B.eye_bones(head, (1.5, -1.5), -hs + 3.5, -hs / 2 - 0.15)
    # the sepal: a ring of skin at the neck, wider than the head, that the petals rise from
    sw = hs + 2
    gt.cube("sepal", -sw / 2, -bh - 1, -sw / 2, sw, 2, sw)
    # the petals: from the sepal's rim, closed up round the head like a bud (the swell flares them); none in front of the face
    n = p["petals"]
    for k in range(n):
        a = math.radians(62 + 236 * k / (n - 1))   # round the sides and back: the face stays clear
        rad = sw / 2 - 0.5
        x, z = math.sin(a) * rad, -math.cos(a) * rad
        x, z = max(-rad, min(rad, x * 1.25)), max(-rad, min(rad, z * 1.25))   # out to the square ring's edge
        pet = torso.bone(f"petal{k}", (x, -bh - 1, z), (0, math.degrees(-a), 0))
        # each petal: a blade standing against the head, then a tip curling out
        blade = pet.bone(f"petal{k}_b", (0, 0, 0), (12, 0, 0))   # cupped out from the head (+x tips a petal outward)
        blade.cube(f"petal{k}_s1", -1.5, -p["plen"], -0.5, 3, p["plen"], 1)
        tip = blade.bone(f"petal{k}_t", (0, -p["plen"] + 0.5, 0), (22, 0, 0))
        tip.cube(f"petal{k}_s2", -1, -2, -0.5, 2, 2, 1)
    if p["crown"]:   # a second, smaller bloom on the crown of the head
        for k in range(5):
            a = 2 * math.pi * k / 5
            x, z = math.sin(a) * 2.5, -math.cos(a) * 2.5
            pet = head.bone(f"crownp{k}", (x, -hs, z), (0, math.degrees(-a), 0))
            bl = pet.bone(f"crownp{k}_b", (0, 0, 0), (28, 0, 0))
            bl.cube(f"crownp{k}_s1", -1, -3, -0.5, 2, 3, 1)
    if p["crystal"]:
        for k, (x, z, rz) in enumerate(((-1.5, bd / 2 - 0.5, -18), (1.5, bd / 2 - 0.5, 20))[:p["crystal"]]):
            B.shard(gt, f"xtal{k}", (x, -bh + 2, z), (-30, 0, rz), [(4, 2, 0), (2, 1, 12)])
    r.look = [("head", 0.9, 0.7)]
    r.moves = {}
    creeper_clips(r, rung, p)
    r.pack()
    return r


def _petals(clip, n, keys_open, phase_step=0.0):
    """Every petal on the same keys (degrees of opening on the blade, the tip curling with it), a little out of step."""
    for k in range(n):
        clip.rot(f"petal{k}_b", [(t, ang, 0, 0) for t, ang in keys_open])
        clip.rot(f"petal{k}_t", [(t, ang * 0.5, 0, 0) for t, ang in keys_open])


def creeper_clips(r, rung, p):
    n = p["petals"]
    legs = (("fr", 0.0), ("bl", 0.0), ("fl", 0.5), ("br", 0.5))
    # ---- idle: it stands too still, swaying from the feet; the head turns in small slow steps; the bud breathes
    idle = r.clip("idle", 4.5, loop=True)
    cycle(idle, "torso", "rot", lambda q: (1.2 * s(q), 0, 1.5 * s(q, 0.3)), n=8)
    cycle(idle, "head", "rot", lambda q: (-1.5 * s(q, 0.2), 8 * s(q, 0.1), 0), n=8)
    for k in range(n):
        cycle(idle, f"petal{k}_b", "rot", lambda q, k=k: (-2 + 3 * s(q, k / n * 0.5), 0, 0), n=8)
    if p["crown"]:
        for k in range(5):
            cycle(idle, f"crownp{k}_b", "rot", lambda q, k=k: (3 * s(q, k / 5.0), 0, 0), n=8)
    # ---- walk: the four-legged shuffle (diagonal pairs), the body rocking side to side over them, the head held level
    walk = r.clip("walk", 1.0, loop=True)
    for nm, ph in legs:
        cycle(walk, "leg_" + nm, "rot", lambda q, ph=ph: (30 * s(q, ph), 0, 0), n=12)
        cycle(walk, "leg_" + nm, "pos", lambda q, ph=ph: (0, 0.8 * max(0.0, -c(q, ph)), 0), n=12)
    cycle(walk, "torso", "rot", lambda q: (2 * s(2 * q), 4 * s(q), 4 * s(q, 0.25)), n=12)
    cycle(walk, "hips", "pos", lambda q: (0.4 * s(q, 0.25), -0.4 * c(2 * q), 0), n=12)
    cycle(walk, "head", "rot", lambda q: (-2 * s(2 * q), -4 * s(q), -4 * s(q, 0.25)), n=12)
    for k in range(n):
        cycle(walk, f"petal{k}_b", "rot", lambda q, k=k: (3 * s(2 * q, k / n), 0, 0), n=12)
    run = r.clip("run", 0.6, loop=True)
    for nm, ph in legs:
        cycle(run, "leg_" + nm, "rot", lambda q, ph=ph: (44 * s(q, ph), 0, 0), n=12)
        cycle(run, "leg_" + nm, "pos", lambda q, ph=ph: (0, 1.4 * max(0.0, -c(q, ph)), 0), n=12)
    cycle(run, "torso", "rot", lambda q: (8 + 3 * s(2 * q), 5 * s(q), 5 * s(q, 0.25)), n=12)
    cycle(run, "hips", "pos", lambda q: (0, -0.8 * c(2 * q) - 0.4, 0), n=12)
    cycle(run, "head", "rot", lambda q: (-8, -5 * s(q), 0), n=12)
    # ---- angry is the swell: it crouches and trembles, the head bows, and the bloom flares wide open
    angry = r.clip("angry", 1.0, loop=True)
    cycle(angry, "hips", "pos", lambda q: (0.25 * s(5 * q), 1.0, 0.25 * c(5 * q)), n=20)
    cycle(angry, "torso", "rot", lambda q: (4 + 1.5 * s(4 * q), 0, 1.5 * s(5 * q, 0.2)), n=20)
    cycle(angry, "head", "rot", lambda q: (10 + 2 * s(6 * q), 0, 2 * s(5 * q, 0.4)), n=20)
    for nm, _ in legs:
        cycle(angry, "leg_" + nm, "rot", lambda q, nm=nm: (-8 if nm[0] == "f" else 8, 0, 0), n=4)
    for k in range(n):
        cycle(angry, f"petal{k}_b", "rot", lambda q, k=k: (78 + 6 * s(4 * q, k / n), 0, 0), n=12)
        cycle(angry, f"petal{k}_t", "rot", lambda q, k=k: (40, 0, 0), n=4)
    if p["crown"]:
        for k in range(5):
            cycle(angry, f"crownp{k}_b", "rot", lambda q, k=k: (40 + 5 * s(4 * q, k / 5.0), 0, 0), n=8)
    # ---- the blow (it has none of its own: a startled shove with the head, if anything ever swings it)
    bite = r.clip("bite", 0.5)
    bite.rot("torso", [(0.0, 0, 0, 0), (0.08, 10, 0, 0), (0.5, 0, 0, 0)])
    bite.rot("head", [(0.0, 0, 0, 0), (0.08, 14, 0, 0), (0.5, 0, 0, 0)])
    # ---- hurt: it rocks back, the petals snapping shut
    hurt = r.clip("hurt", 0.45)
    hurt.rot("torso", [(0.0, 0, 0, 0), (0.07, -10, 0, 5), (0.25, 3, 0, 0), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0.0, 0, 0, 0), (0.07, -10, 6, 0), (0.45, 0, 0, 0)])
    _petals(hurt, n, [(0.0, 0), (0.06, -12), (0.45, 0)])
    # ---- death: the bloom wilts (the petals droop out and down), the legs fold and it slumps over on its side
    death = r.clip("death", 0.95)
    _petals(death, n, [(0.0, 0), (0.4, 50), (0.95, 100)])
    death.pos("hips", [(0.0, 0, 0, 0), (0.3, 0, p["leg"] * 0.6, 0), (0.95, 0, p["leg"] - 1.5, 0)])
    for nm, _ in legs:
        death.rot("leg_" + nm, [(0.0, 0, 0, 0), (0.3, -30 if nm[0] == "f" else 30, 0, 0), (0.95, -70 if nm[0] == "f" else 70, 0, 0)])
    death.rot("torso", [(0.0, 0, 0, 0), (0.4, 6, 0, 8), (0.75, 10, 0, 70, "lin"), (0.85, 10, 0, 64), (0.95, 10, 0, 66)])
    death.rot("head", [(0.0, 0, 0, 0), (0.5, 20, 0, 0), (0.95, 26, 0, 6)])
    # ---- roar (an alpha's): it rears back on its hind legs and the bloom opens all at once
    roar = r.clip("roar", 1.6)
    roar.rot("torso", [(0.0, 0, 0, 0), (0.35, -14, 0, 0), (1.2, -12, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("head", [(0.0, 0, 0, 0), (0.35, -18, 0, 0), (0.7, -14, 6, 0), (1.0, -18, -6, 0), (1.6, 0, 0, 0)])
    _petals(roar, n, [(0.0, 0), (0.35, 85), (1.2, 80), (1.6, 0)])
    # ---- its moves (one clip each, the server's cue names them). sidestep (C): a quick crouch and a hop to the side
    st = r.clip("sidestep", 0.6)
    st.pos("hips", [(0.0, 0, 0, 0), (0.08, 0, 1.2, 0), (0.2, 0, -1.5, 0), (0.4, 0, 0.4, 0), (0.6, 0, 0, 0)])
    st.rot("torso", [(0.0, 0, 0, 0), (0.08, 0, 0, -6), (0.2, 0, 0, 10), (0.6, 0, 0, 0)])
    for nm, _ in legs:
        st.rot("leg_" + nm, [(0.0, 0, 0, 0), (0.2, 0, 0, 14 if nm[1] == "r" else -14), (0.45, 0, 0, 0)])
    # seed (B): it bows and a pod falls from the bloom; the petals clench, then open
    st = r.clip("seed", 1.0)
    st.rot("torso", [(0.0, 0, 0, 0), (0.25, 16, 0, 0), (0.6, 4, 0, 0), (1.0, 0, 0, 0)])
    st.rot("head", [(0.0, 0, 0, 0), (0.25, 18, 0, 0), (1.0, 0, 0, 0)])
    _petals(st, n, [(0.0, 0), (0.2, -10), (0.35, 40), (1.0, 0)])
    # ignite (A): it shudders and the bloom flashes open toward its kin
    st = r.clip("ignite", 1.0)
    st.rot("torso", [(0.0, 0, 0, 0), (0.1, -4, 8, 4), (0.2, -4, -8, -4), (0.3, -4, 8, 4), (0.45, 0, 0, 0), (1.0, 0, 0, 0)])
    _petals(st, n, [(0.0, 0), (0.15, 70), (0.6, 60), (1.0, 0)])
    st.rot("head", [(0.0, 0, 0, 0), (0.15, -16, 0, 0), (1.0, 0, 0, 0)])
    # pull (S): it braces back on its legs and the bloom gapes, drawing the air in
    st = r.clip("pull", 1.5)
    st.pos("hips", [(0.0, 0, 0, 0), (0.3, 0, 0.8, 1.4), (1.2, 0, 0.8, 1.4), (1.5, 0, 0, 0)])
    st.rot("torso", [(0.0, 0, 0, 0), (0.3, -10, 0, 0), (1.2, -10, 0, 0), (1.5, 0, 0, 0)])
    st.rot("head", [(0.0, 0, 0, 0), (0.3, -12, 0, 0), (1.2, -12, 0, 0), (1.5, 0, 0, 0)])
    _petals(st, n, [(0.0, 0), (0.3, 90), (1.2, 86), (1.5, 0)])


CREEPER = ["#0f2410", "#1b3d1b", "#2a5a28", "#3d7a37", "#55994a", "#76b763"]


def paint_creeper(r, rung, species, fam):
    p = CR[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    rng = random.Random(R._h(r.id, species, fam))
    skin = B.tint_ramp(CREEPER, fam)
    # vanilla's mottle (patches of lighter and darker green), kept but in broader patches
    mottle = R.p_blotch(rung * 5 + 1, scale=3, amount=0.8, levels=3)
    rank = R.hexc(B.RANK[rung])
    petal = B.tint_ramp(["#1d3a14", "#2f5a1e", "#477d2a", "#62a038", "#86c050", "#a9d872"], fam)
    for cu in r.cubes:
        n = cu.name
        if n.startswith(("petal", "crownp")):
            # a petal: younger, yellower green than the hide, veined down the middle, its edge lighter
            P.paint(cu, petal, 2.6, lambda f, i, j: (-0.9 if f.w >= 3 and i == f.w // 2 else 0.5 if i in (0, f.w - 1) else 0.0)
                    if f.name in ("north", "south") else 0.0, grad=1.4, rim=0.6)
        elif n.startswith("xtal"):
            P.paint(cu, B.ramp_to(B.RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n == "core":
            P.paint(cu, skin, 1.4, None)
        elif n == "sepal":
            P.paint(cu, skin, 1.9, lambda f, i, j: -0.6 if (i + j) % 4 == 0 else 0.0, rim=0.8)
        else:
            P.paint(cu, skin, 2.6, mottle, noise=0.3)
    # the face: vanilla's (the eyes two dark squares, the mouth a dark drop) set deep, the eyes lit
    fn = P.face("head", "north")
    w = fn.w
    dark = R.hexc("#060a06")
    ey = 2 if w == 8 else 3
    with P.eyes():
        for (a, b) in ((1, 2), (w - 3, w - 2)):
            for i in (a, b):
                fn.set(i, ey, dark)
                fn.set(i, ey + 1, dark)
    for i in range(w // 2 - 1, w // 2 + 1):
        for j in range(ey + 2, ey + 5):
            fn.set(i, j, dark)
    for i in (w // 2 - 2, w // 2 + 1):
        for j in range(ey + 3, ey + 6):
            fn.set(i, j, dark)
    g = P.face("head", "north", glow=True)
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    for (a, b) in ((1, 2), (w - 3, w - 2)):
        inner = b if a < w // 2 else a
        g.set(inner, ey + 1, core[:3] + (255,))
        g.set(a if inner == b else b, ey + 1, rank[:3] + (150,))
        g.set(inner, ey, rank[:3] + (110,))
    if p["seams"]:
        # the light runs up thin seams from the feet to the chest (two on the front from B, two more on the back at A)
        f = P.face("torso", "north", glow=True)
        top = 4 if p["core"] else 6
        for x in (2, f.w - 3):
            for j in range(top, f.h):
                if (j + x) % 5 != 0:
                    f.set(x, j, rank[:3] + (120 + 10 * (rung - 3),))
            for j in range(top, top + 2):
                f.set(x + (1 if x < f.w // 2 else -1), j, rank[:3] + (90,))
        for nm in ("fr", "fl"):
            lf = P.face("leg_" + nm, "north", glow=True)
            for j in range(0, lf.h, 2):
                lf.set(1 if nm == "fr" else lf.w - 2, j, rank[:3] + (90,))
        if p["core"]:
            for fname in ("north", "west", "east"):
                cf = P.face("core", fname, glow=True)
                for i, j in cf.each():
                    cf.set(i, j, R.lerpc(rank, R.hexc("#ffffff"), 0.4 if (i + j) % 2 == 0 else 0.15)[:3] + (210 + 20 * (rung - 4),))
            b2 = P.face("torso", "south", glow=True)
            for x in (2, b2.w - 3):
                for j in range(6, b2.h):
                    if (j + x) % 4 != 0:
                        b2.set(x, j, rank[:3] + (100,))
        # and on into the bloom: each petal's vein carries it (seen best when the petals flare)
        for cu in r.cubes:
            if cu.name.startswith("petal") and cu.name.endswith("_s1") and P.own(cu):
                for fname in ("north", "south"):
                    f = P.face(cu, fname, glow=True)
                    for j in range(f.h):
                        f.set(f.w // 2, j, rank[:3] + (70 + 15 * (rung - 3),))
    B.weathering(P, r, fam, rng, ["head", "torso"], 1 + (fam > 0))
    crystal_glow(P, r, rung)
    return P.skin, P.glow, P.blink()
