"""Form bodies (SPEC 21.13): the bodies a levelled vanilla creature wears from C to S, on the hero rig toolkit.

    python3 tools/art/build_all.py forms_bodies      textures + src/.../living/forms/FormData.java
    python3 tools/art/specs/forms_bodies.py preview DIR [body ...]
                                                     per rig: four day views, a night view, a strip of key poses

One rig per body and rung (form_<body>_<rung>); each species of the body has its own paint (a husk the zombie's rig, a stray the
skeleton's...), in each biome family (0 moss and bark, 1 sun-baked, 2 rime). Rules (docs/forms/PLAN.md):
  - posture and mass tell the rank before colour (C wary, B hunched and heavy, A massive, S tall and still);
  - growths are the creature's own material at true size; the land's crystal, in the rank's colour, only at A and S;
  - the light lives inside: eyes deep under a brow, thin seams, a heart; never a painted splash;
  - nothing floats or crosses the face at any key pose (check_rig).
Motion follows what makes the Fresh Animations pack's zombie read alive (studied, not copied): a weight-shifting walk where the hips
sway over the planted foot and the head bobs with each step, a slow breath, arms that ease up into a reach when it hunts, a recoil
from a blow, and every creature on its own clock (the life layer's seed).
Axes are the game's: x to the creature's left, y DOWN (up is -y), the front is -z. Ground is y = 24.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402
from fauna_anim import cycle, s, c  # noqa: E402

JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/living/forms/FormData.java")
RANK = {1: "#e4d063", 2: "#f29a48", 3: "#ec5656", 4: "#b07cff", 5: "#8c7bff"}
NAME = {2: "C", 3: "B", 4: "A", 5: "S"}
FAMILIES = ("green", "sun", "frost")


def hexc(h):
    return R.hexc(h)


def ramp_to(col, dark="#0b0a10", n=5, lo=0.05, hi=0.92):
    """A ramp from near-black to a colour (crystal, seams)."""
    a, b = R.hexc(dark), R.hexc(col) if isinstance(col, str) else col
    return R.Ramp(*["#%02x%02x%02x" % R.lerpc(a, b, lo + (hi - lo) * k / (n - 1))[:3] for k in range(n)])


def shard(parent, name, pivot, rot, segs):
    """A growth that tapers: segments of (length, thickness, bend degrees), each from the end of the last (whole pixels)."""
    b = parent.bone(name, pivot, rot)
    node = b
    segs = [(max(1, round(ln)), max(1, round(th)), bend) for ln, th, bend in segs]
    for k, (ln, th, bend) in enumerate(segs):
        if k > 0:
            node = node.bone(f"{name}_n{k + 1}", (0, -segs[k - 1][0] + 0.5, 0), (bend, 0, 0))
        o = -th / 2.0
        node.cube(f"{name}_s{k + 1}", o, -ln, o, th, ln, th)
    return b


def eye_bones(head, xs, y, z):
    """Empty bones at each eye's lit core: the halo stands there (FormRenderer); 'eye' is the life layer's eye role."""
    head.bone("eye_l", (xs[0], y, z))
    head.bone("eye_r", (xs[1], y, z))


# =====================================================================================================================
# The zombie (and the husk): a ghoul's ladder. C wary and lean-shouldered; B hunched, the jaw split, long clawed forearms,
# the ribs through the shirt; A massive, a hump of shoulder, the land's crystal breaking out of its back; S tall and still,
# a crown of crystal and a grave mantle.

Z = {
    #       hunch torso(w,h,d) upper fore  claw  head-fwd  jaw  spurs crystals crown mantle
    2: dict(hunch=7, torso=(9, 12, 5), upper=None, fore=None, claw=0, fwd=0.5, jaw=False, spurs=0, crystal=0, crown=False, mantle=False, stride=1.5),
    3: dict(hunch=15, torso=(10, 12, 6), upper=(4, 7, 4), fore=(3, 8, 3), claw=3, fwd=1.5, jaw=True, spurs=2, crystal=0, crown=False, mantle=False, stride=1.6),
    4: dict(hunch=21, torso=(11, 13, 7), upper=(5, 8, 5), fore=(4, 9, 4), claw=4, fwd=2.0, jaw=True, spurs=2, crystal=4, crown=False, mantle=False, stride=1.7),
    5: dict(hunch=9, torso=(10, 12, 6), upper=(4, 8, 4), fore=(3, 9, 3), claw=4, fwd=1.0, jaw=True, spurs=0, crystal=2, crown=True, mantle=True, stride=1.6),
}


def zombie(rung):
    p = Z[rung]
    r = R.Rig(f"form_zombie_{rung}", (128, 128))
    r.walk_stride, r.run_stride = p["stride"], p["stride"] * 1.9
    r.run_speed = (0.2, 0.32)
    r.shadow = 0.5 + 0.06 * (rung - 2)
    tw, th, td = p["torso"]
    hunch = p["hunch"]
    base = r.bone("base", (0, 24, 0))
    hips = base.bone("hips", (0, -12, 0))
    for side, nm in ((-1, "r"), (1, "l")):
        leg = hips.bone("leg_" + nm, (side * 2.0, 0, 0.5), (0, 0, 0))
        leg.cube("leg_" + nm, -2, 0, -2, 4, 12, 4, mirror=side > 0, share=None if side < 0 else "leg_r")
    torso = hips.bone("torso", (0, 0, 0.5), (hunch, 0, 0))
    torso.cube("torso", -tw / 2, -th, -td / 2, tw, th, td)
    # what grows on the torso hangs on its own bone: the core bones hold only the box the armour is sized from (FormGear)
    gt = torso.bone("geart", (0, 0, 0))
    if rung >= 3:
        # the ribs through the torn shirt, a hand's width under the collar
        gt.cube("ribs", -(tw - 4) / 2, -th + 2, -td / 2 - 1, tw - 4, 6, 1)
    if rung >= 4:
        # the hump: shoulder mass over the back
        gt.cube("hump", -(tw - 2) / 2, -th - 1, -td / 2 + 1, tw - 2, 5, td - 1)
    for k in range(3 if rung >= 3 else 0):  # the spine's knuckles
        gt.cube(f"spine{k}", -1, -th + 2 + k * 3, td / 2, 2, 2, 1)
    # rags: the shirt's hem hangs, front and back (springs in the life layer)
    hem = torso.bone("skirt_f", (0, 0, -td / 2 - 0.01), (0, 0, 0))
    hem.cube("skirt_f", -tw / 2 + 0.5 if tw % 2 else -tw / 2 + 1, 0, 0, tw - 1 if tw % 2 else tw - 2, 4 if rung >= 3 else 3, 0)
    hem_b = torso.bone("skirt_b", (0, 0, td / 2 + 0.01), (0, 0, 0))
    hem_b.cube("skirt_b", -tw / 2 + 0.5 if tw % 2 else -tw / 2 + 1, 0, 0, tw - 1 if tw % 2 else tw - 2, 3 if rung >= 3 else 2, 0)
    # the head, thrust forward on the hunch and turned back up to look ahead
    head = torso.bone("head", (0, -th, -p["fwd"]), (-hunch * 0.85, 0, 0))
    if p["jaw"]:
        head.cube("head", -4, -8, -4, 8, 6, 8)          # the skull
        jaw = head.bone("jaw", (0, -2, 2.5), (9, 0, 0))  # hinged under the ears, hanging a little open
        jaw.cube("jaw", -3.5, 0, -5.5, 7, 2, 6)
        for k, x in enumerate((-2.5, -0.5, 1.5)):        # the lower teeth stand up off the jaw
            jaw.cube(f"tooth{k}", x, -1, -5.3, 1, 1, 1)
        head.bone("gearh", (0, 0, 0)).cube("cheek", -3.5, -2, -4.5, 7, 1, 1)       # the upper teeth's ridge, over the jaw
    else:
        head.cube("head", -4, -8, -4, 8, 8, 8)
    head.bone("gearb", (0, 0, 0)).cube("brow", -4, -5, -5, 8, 1, 1)  # the brow: the eyes sit in its shadow
    eye_bones(head, (1.5, -1.5), -3.5, -4.15)
    if p["crown"]:
        ring = ((0, -3.0, 5), (-2.8, -2.2, 4), (2.8, -2.2, 4), (-3.2, 0.8, 3), (3.2, 0.8, 3), (-1.6, 3.0, 3), (1.6, 3.0, 3))
        for k, (x, z, ln) in enumerate(ring):
            # lean away from the skull's centre, as a crown's points do (+z rotation leans an upright shard toward +x)
            shard(head, f"crown{k}", (x, -7.5, z), (z * 7.0, 0, x * 7.5), [(ln, 2 if k == 0 else 1, 0), (2 if k == 0 else 1, 1, 0)])
    # arms: vanilla's shoulder (the box hangs outside the pivot), hanging straight down from the hunched shoulders
    for side, nm in ((-1, "r"), (1, "l")):
        sx = side * (tw / 2 + 1)
        arm = torso.bone("arm_" + nm, (sx, -th + 2, 0), (-hunch - 8, side * -4, side * -3))
        ox = -3 if side < 0 else -1
        if p["upper"] is None:
            arm.cube("arm_" + nm, ox, -2, -2, 4, 12, 4, mirror=side > 0, share=None if side < 0 else "arm_r")
            arm.bone("hand_" + nm, (ox + 2, 10, 0))
            if side < 0:  # the shackle it died in, the chain broken off
                arm.bone("gearc", (0, 0, 0)).cube("cuff", ox - 0.5, 6, -2.5, 5, 2, 5)
                link = arm.bone("chain0", (ox + 2, 8, -2.5), (0, 0, 0))
                link.cube("chain0", -0.5, 0, -0.5, 1, 3, 1)
                link2 = link.bone("chain1", (0, 2.5, 0), (0, 0, 0))
                link2.cube("chain1", -0.5, 0, -0.5, 1, 3, 1)
        else:
            uw, uh, ud = p["upper"]
            fw, fh, fd = p["fore"]
            uox = -uw + 1 if side < 0 else -1
            arm.cube("arm_" + nm, uox, -2, -ud / 2, uw, uh, ud, mirror=side > 0, share=None if side < 0 else "arm_r")
            cx = uox + uw / 2
            fore = arm.bone("fore_" + nm, (cx, uh - 2, 0), (-10, 0, 0))
            fore.cube("fore_" + nm, -fw / 2, 0, -fd / 2, fw, fh, fd, mirror=side > 0, share=None if side < 0 else "fore_r")
            # the bone of the forearm through the skin, on its outer side
            fore.cube("ulna_" + nm, side * (fw / 2) - (0 if side > 0 else 1), 2, -0.5, 1, fh - 4, 1, share=None if side < 0 else "ulna_r")
            fore.bone("hand_" + nm, (0, fh - 1, 0))
            for k, dx in enumerate((-1, 0, 1)[:3]):
                ln = p["claw"] + (1 if dx == 0 else 0)
                shard(fore, f"claw_{nm}{k}", (dx * (fw / 2 - 0.5), fh - 0.4, -fd / 2 + 0.6), (172, 0, dx * 8),
                      [(ln - 1, 1, 0), (2, 1, -24)])
        # bone spurs breaking the shoulder
        for k in range(p["spurs"]):
            shard(arm, f"spur_{nm}{k}", (ox + 2 + side * (0.5 + k * 0.8), -1.6, -0.5 + k * 1.8), (-28 - k * 14, 0, side * (18 + k * 12)),
                  [(2, 2, 0), (1 + rung // 2, 1, -12)])
    # the land's crystal: out of the back and the shoulder (A and S only), in the rank's colour
    if p["crystal"]:
        spots = [(-tw / 2 + 2.5, -th + 0.5, td / 2 - 1.0, -38, -24, 6), (tw / 2 - 2.5, -th + 1.5, td / 2 - 1.0, -46, 26, 5),
                 (-0.5, -th + 6.5, td / 2 - 0.5, -62, -8, 4), (1.5, -th + 9.5, td / 2 - 0.5, -70, 12, 3)]
        if rung >= 4:  # the hump: the first two break out of it
            spots[0] = (-tw / 2 + 2.5, -th - 0.5, td / 2 - 1.5, -34, -24, 6)
            spots[1] = (tw / 2 - 2.5, -th - 0.5, td / 2 - 1.5, -40, 26, 5)
        for k, (x, y, z, rx, rz, ln) in enumerate(spots[:p["crystal"]]):
            shard(torso, f"xtal{k}", (x, y, z), (rx, 0, rz), [(ln, 2, 0), (max(2, ln - 2), 1, 16)])
    if p["mantle"]:
        gt.cube("collar", -tw / 2 - 1, -th - 1, -td / 2 - 0.5, tw + 2, 3, td + 2)
        mant = torso.bone("cape", (0, -th + 1.5, td / 2 + 1.1), (-4, 0, 0))
        mant.cube("cape", -tw / 2 - 0.5, 0, 0, tw + 1, 9, 1)
        low = mant.bone("cape2", (0, 8.5, 0), (8, 0, 0))
        low.cube("cape2", -tw / 2 - 0.5, 0, 0, tw + 1, 9, 1)
    r.look = [("head", 0.8, 0.7), ("torso", 0.15, 0.1)]
    r.moves = {}
    zombie_clips(r, rung, p)
    r.pack()
    return r


def zombie_clips(r, rung, p):
    has_fore = p["upper"] is not None
    jaw = p["jaw"]
    heavy = 1.0 + 0.15 * (rung - 2)   # the bigger the ghoul, the heavier its motion
    # ---- idle: a slow breath through the shoulders, the arms hanging and drifting a little out of step, the head settling
    idle = r.clip("idle", 4.0, loop=True)
    cycle(idle, "torso", "rot", lambda q: (1.6 * s(q), 0.8 * s(q, 0.3), 0), n=8)
    cycle(idle, "hips", "pos", lambda q: (0, 0.12 * c(q), 0), n=8)
    cycle(idle, "head", "rot", lambda q: (-1.2 * s(q, 0.1), 2.0 * s(q, 0.6), 2.2 * s(q, 0.25)), n=8)
    cycle(idle, "arm_r", "rot", lambda q: (2.5 * s(q, 0.15), 0, 1.5 * c(q, 0.1)), n=8)
    cycle(idle, "arm_l", "rot", lambda q: (2.5 * s(q, 0.55), 0, -1.5 * c(q, 0.5)), n=8)
    if has_fore:
        cycle(idle, "fore_r", "rot", lambda q: (-3 * s(q, 0.3), 0, 0), n=8)
        cycle(idle, "fore_l", "rot", lambda q: (-3 * s(q, 0.7), 0, 0), n=8)
    if jaw:
        cycle(idle, "jaw", "rot", lambda q: (2.5 + 2.5 * s(q, 0.2), 0, 0), n=8)
    # ---- walk: the shamble. Legs swing and lift the foot as it comes forward; the hips sway over the planted foot and dip as
    # the legs part; the torso twists and rolls against the hips; the head bobs twice a stride; the arms swing loose behind.
    walk = r.clip("walk", 1.2, loop=True)
    sw = 26 + 2 * rung
    cycle(walk, "leg_r", "rot", lambda q: (sw * s(q), 0, 0), n=12)
    cycle(walk, "leg_l", "rot", lambda q: (-sw * s(q), 0, 0), n=12)
    cycle(walk, "leg_r", "pos", lambda q: (0, 1.4 * max(0.0, -c(q)), 0), n=12)
    cycle(walk, "leg_l", "pos", lambda q: (0, 1.4 * max(0.0, c(q)), 0), n=12)
    cycle(walk, "hips", "pos", lambda q: (0.7 * heavy * s(q, 0.25), -0.9 * heavy * s(q) ** 2, 0), n=12)
    cycle(walk, "hips", "rot", lambda q: (0, 6 * s(q), 2.5 * heavy * s(q, 0.25)), n=12)
    cycle(walk, "torso", "rot", lambda q: (2 * s(2 * q, 0.1), -9 * s(q), -4 * heavy * s(q, 0.25)), n=12)
    cycle(walk, "head", "rot", lambda q: (-2.5 * c(2 * q, 0.05), 5 * s(q), 4 * s(q, 0.3)), n=12)
    cycle(walk, "arm_r", "rot", lambda q: (-11 * s(q, 0.06), 0, 3 * s(q, 0.3)), n=12)
    cycle(walk, "arm_l", "rot", lambda q: (11 * s(q, 0.06), 0, 3 * s(q, 0.3)), n=12)
    if has_fore:
        cycle(walk, "fore_r", "rot", lambda q: (-6 - 6 * s(q, 0.16), 0, 0), n=12)
        cycle(walk, "fore_l", "rot", lambda q: (-6 + 6 * s(q, 0.16), 0, 0), n=12)
    if jaw:
        cycle(walk, "jaw", "rot", lambda q: (3 * max(0.0, s(2 * q, 0.2)), 0, 0), n=12)
    # ---- run: it lunges along, bent low, arms pumping and reaching
    run = r.clip("run", 0.8, loop=True)
    rs = sw + 14
    cycle(run, "leg_r", "rot", lambda q: (rs * s(q), 0, 0), n=12)
    cycle(run, "leg_l", "rot", lambda q: (-rs * s(q), 0, 0), n=12)
    cycle(run, "leg_r", "pos", lambda q: (0, 2.2 * max(0.0, -c(q)), 0), n=12)
    cycle(run, "leg_l", "pos", lambda q: (0, 2.2 * max(0.0, c(q)), 0), n=12)
    cycle(run, "hips", "pos", lambda q: (0.4 * s(q, 0.25), -1.4 * s(q) ** 2 - 0.6, -0.5), n=12)
    cycle(run, "torso", "rot", lambda q: (12 + 3 * s(2 * q), -10 * s(q), -3 * s(q, 0.25)), n=12)
    cycle(run, "head", "rot", lambda q: (-12 - 3 * c(2 * q), 4 * s(q), 0), n=12)
    cycle(run, "arm_r", "rot", lambda q: (-30 * s(q, 0.08) - 18, 0, 4), n=12)
    cycle(run, "arm_l", "rot", lambda q: (30 * s(q, 0.08) - 18, 0, -4), n=12)
    if has_fore:
        cycle(run, "fore_r", "rot", lambda q: (-14 - 10 * s(q, 0.2), 0, 0), n=12)
        cycle(run, "fore_l", "rot", lambda q: (-14 + 10 * s(q, 0.2), 0, 0), n=12)
    # ---- angry (on top of the idle, and on top of the walk while it hunts): the arms come up into the reach, one ahead of the
    # other and drifting; the head lifts to look; the jaw opens; the claws spread
    angry = r.clip("angry", 4.0, loop=True)
    reach = 62 if not has_fore else 58
    cycle(angry, "arm_r", "rot", lambda q: (-reach - 5 * s(q, 0.1), -6 + 3 * s(q), 4), n=8)
    cycle(angry, "arm_l", "rot", lambda q: (-reach + 6 - 5 * s(q, 0.6), 6 - 3 * s(q, 0.5), -4), n=8)
    cycle(angry, "torso", "rot", lambda q: (3 + 1.5 * s(q, 0.2), 0, 0), n=8)
    cycle(angry, "head", "rot", lambda q: (-6, 0, 2 * s(q, 0.4)), n=8)
    if has_fore:
        cycle(angry, "fore_r", "rot", lambda q: (-14 - 4 * s(q, 0.3), 0, 0), n=8)
        cycle(angry, "fore_l", "rot", lambda q: (-10 - 4 * s(q, 0.8), 0, 0), n=8)
    if jaw:
        cycle(angry, "jaw", "rot", lambda q: (13 + 4 * s(q * 2, 0.1), 0, 0), n=8)
    # ---- the blow (the swing): a raking swipe from the raised reach; from B a second with the other hand, half a second on, as the
    # server deals it (Combos.SECOND_AFTER)
    bite = r.clip("bite", 1.0 if rung >= 3 else 0.6)
    rake = [(0.0, 0, 0, 0), (0.03, -28, -10, 0), (0.1, 62, 18, 0), (0.24, 40, 10, 0), (0.42, 0, 0, 0)]
    bite.rot("arm_r", [(t, x, y, z) for t, x, y, z in rake])
    bite.rot("torso", [(0.0, 0, 0, 0), (0.03, -2, 8, 0), (0.1, 8, -14, 0), (0.3, 3, -6, 0), (0.42, 0, 0, 0)]
             + ([(0.5, -2, 8, 0), (0.6, 8, 14, 0), (0.78, 3, 6, 0), (1.0, 0, 0, 0)] if rung >= 3 else []))
    bite.rot("head", [(0.0, 0, 0, 0), (0.1, 6, -8, 0), (0.42, 0, 0, 0)] + ([(0.6, 6, 8, 0), (1.0, 0, 0, 0)] if rung >= 3 else []))
    if rung >= 3:
        bite.rot("arm_l", [(0.0, 0, 0, 0), (0.42, 0, 0, 0), (0.5, -28, 10, 0), (0.6, 62, -18, 0), (0.74, 40, -10, 0), (1.0, 0, 0, 0)])
        bite.rot("fore_r", [(0.0, 0, 0, 0), (0.03, -18, 0, 0), (0.1, 10, 0, 0), (0.42, 0, 0, 0)])
        bite.rot("fore_l", [(0.0, 0, 0, 0), (0.5, -18, 0, 0), (0.6, 10, 0, 0), (1.0, 0, 0, 0)])
        bite.pos("hips", [(0.0, 0, 0, 0), (0.1, 0, -0.5, -1.2), (0.42, 0, 0, 0), (0.6, 0, -0.5, -1.2), (1.0, 0, 0, 0)])
        if jaw:
            bite.rot("jaw", [(0.0, 0, 0, 0), (0.08, 16, 0, 0), (0.2, 4, 0, 0), (0.58, 16, 0, 0), (0.8, 0, 0, 0)])
    else:
        bite.pos("hips", [(0.0, 0, 0, 0), (0.1, 0, -0.4, -1.0), (0.6, 0, 0, 0)])
    # ---- hurt: the blow rocks it back; the head snaps, the arms fly up, then it leans in again
    hurt = r.clip("hurt", 0.45)
    hurt.rot("torso", [(0.0, 0, 0, 0), (0.07, -10, 4, 3), (0.25, 3, 0, 0), (0.45, 0, 0, 0)])
    hurt.rot("head", [(0.0, 0, 0, 0), (0.07, -14, -6, -5), (0.25, 4, 0, 0), (0.45, 0, 0, 0)])
    hurt.rot("arm_r", [(0.0, 0, 0, 0), (0.08, -16, 0, 12), (0.45, 0, 0, 0)])
    hurt.rot("arm_l", [(0.0, 0, 0, 0), (0.08, -12, 0, -14), (0.45, 0, 0, 0)])
    hurt.pos("hips", [(0.0, 0, 0, 0), (0.07, 0, 0, 1.2), (0.45, 0, 0, 0)])
    if jaw:
        hurt.rot("jaw", [(0.0, 0, 0, 0), (0.07, 14, 0, 0), (0.45, 0, 0, 0)])
    # ---- death: the knees go, it sways, and it falls on its face (the game keeps a fallen body one second)
    death = r.clip("death", 0.95)
    death.pos("hips", [(0.0, 0, 0, 0), (0.18, 0, -2.5, 0), (0.35, 0, -3, 0)])
    death.rot("leg_r", [(0.0, 0, 0, 0), (0.18, -22, 0, 4), (0.35, -10, 0, 4)])
    death.rot("leg_l", [(0.0, 0, 0, 0), (0.18, -18, 0, -4), (0.35, -8, 0, -4)])
    death.rot("base", [(0.0, 0, 0, 0), (0.25, -4, 0, 3), (0.62, 84, 0, 4, "lin"), (0.72, 80, 0, 4), (0.95, 82, 0, 4)])
    death.rot("torso", [(0.0, 0, 0, 0), (0.25, -6, 0, 0), (0.62, 6, 0, 0), (0.95, 6, 0, 0)])
    death.rot("head", [(0.0, 0, 0, 0), (0.3, -14, 8, 0), (0.62, -20, 18, 8), (0.95, -22, 20, 10)])
    death.rot("arm_r", [(0.0, 0, 0, 0), (0.3, -30, 0, 10), (0.62, -100, 0, 20), (0.72, -90, 0, 26), (0.95, -92, 0, 26)])
    death.rot("arm_l", [(0.0, 0, 0, 0), (0.3, -24, 0, -10), (0.62, -96, 0, -18), (0.72, -88, 0, -24), (0.95, -90, 0, -24)])
    if jaw:
        death.rot("jaw", [(0.0, 0, 0, 0), (0.3, 20, 0, 0), (0.95, 26, 0, 0)])
    # ---- roar (an alpha's, once, when it first turns on you): it rears, spreads its arms and throws its head back
    roar = r.clip("roar", 1.6)
    roar.rot("torso", [(0.0, 0, 0, 0), (0.25, 6, 0, 0), (0.5, -12, 0, 0), (1.2, -10, 0, 0), (1.6, 0, 0, 0)])
    roar.rot("head", [(0.0, 0, 0, 0), (0.25, 10, 0, 0), (0.5, -30, 0, 0), (0.8, -26, 4, 0), (1.05, -30, -4, 0), (1.6, 0, 0, 0)])
    roar.rot("arm_r", [(0.0, 0, 0, 0), (0.25, 10, 0, 0), (0.5, -40, 0, 36), (1.2, -36, 0, 32), (1.6, 0, 0, 0)])
    roar.rot("arm_l", [(0.0, 0, 0, 0), (0.25, 10, 0, 0), (0.5, -40, 0, -36), (1.2, -36, 0, -32), (1.6, 0, 0, 0)])
    roar.pos("hips", [(0.0, 0, 0, 0), (0.25, 0, -1, 0), (0.5, 0, 0.6, 0), (1.6, 0, 0, 0)])
    if jaw:
        roar.rot("jaw", [(0.0, 0, 0, 0), (0.45, 6, 0, 0), (0.55, 36, 0, 0), (1.2, 32, 0, 0), (1.6, 0, 0, 0)])
    if has_fore:
        roar.rot("fore_r", [(0.0, 0, 0, 0), (0.5, -30, 0, 0), (1.2, -26, 0, 0), (1.6, 0, 0, 0)])
        roar.rot("fore_l", [(0.0, 0, 0, 0), (0.5, -30, 0, 0), (1.2, -26, 0, 0), (1.6, 0, 0, 0)])
    # ---- the moves (Moves.moveOf): the wind-up is stretched by the game to the move's real tell; the strike follows
    # bash (C's shield bash): the shoulder drops back, then drives through
    w = r.clip("bash_windup", 1.0)
    w.rot("torso", [(0.0, 0, 0, 0), (0.6, 4, 28, -6), (1.0, 6, 32, -7)])
    w.rot("arm_r", [(0.0, 0, 0, 0), (0.6, 30, 0, 18), (1.0, 34, 0, 20)])
    w.rot("arm_l", [(0.0, 0, 0, 0), (0.6, -40, 0, -10), (1.0, -44, 0, -12)])
    w.pos("hips", [(0.0, 0, 0, 0), (0.6, 0, -1.2, 1.2), (1.0, 0, -1.4, 1.4)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, 4, -20, 0)])
    st = r.clip("bash_strike", 0.6)
    st.rot("torso", [(0.0, 6, 32, -7), (0.08, 12, -26, 6), (0.3, 8, -18, 4), (0.6, 0, 0, 0)])
    st.rot("arm_r", [(0.0, 34, 0, 20), (0.08, -64, -20, 0), (0.3, -40, -10, 0), (0.6, 0, 0, 0)])
    st.rot("arm_l", [(0.0, -44, 0, -12), (0.08, 10, 0, -6), (0.6, 0, 0, 0)])
    st.pos("hips", [(0.0, 0, -1.4, 1.4), (0.08, 0, -0.6, -2.4), (0.3, 0, -0.3, -1.4), (0.6, 0, 0, 0)])
    st.rot("head", [(0.0, 4, -20, 0), (0.08, -4, 14, 0), (0.6, 0, 0, 0)])
    # lunge (B's pounce): it sinks, gathers, and springs with its claws spread
    w = r.clip("lunge_windup", 1.0)
    w.pos("hips", [(0.0, 0, 0, 0), (0.5, 0, -3.5, 1.5), (1.0, 0, -4.2, 2.0)])
    w.rot("leg_r", [(0.0, 0, 0, 0), (1.0, -34, 0, 4)])
    w.rot("leg_l", [(0.0, 0, 0, 0), (1.0, -14, 0, -4)])
    w.rot("torso", [(0.0, 0, 0, 0), (0.5, 16, 0, 0), (1.0, 20, 0, 0)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -22, 0, 0)])
    w.rot("arm_r", [(0.0, 0, 0, 0), (0.5, 26, 0, 22), (1.0, 32, 0, 26)])
    w.rot("arm_l", [(0.0, 0, 0, 0), (0.5, 26, 0, -22), (1.0, 32, 0, -26)])
    if jaw:
        w.rot("jaw", [(0.0, 0, 0, 0), (1.0, 22, 0, 0)])
    st = r.clip("lunge_strike", 0.7)
    st.pos("hips", [(0.0, 0, -4.2, 2.0), (0.06, 0, 1.0, -1.5), (0.4, 0, 0.4, -1.0), (0.7, 0, 0, 0)])
    st.rot("leg_r", [(0.0, -34, 0, 4), (0.06, 30, 0, 0), (0.4, 18, 0, 0), (0.7, 0, 0, 0)])
    st.rot("leg_l", [(0.0, -14, 0, -4), (0.06, 26, 0, 0), (0.4, 14, 0, 0), (0.7, 0, 0, 0)])
    st.rot("torso", [(0.0, 20, 0, 0), (0.06, 8, 0, 0), (0.4, 6, 0, 0), (0.7, 0, 0, 0)])
    st.rot("head", [(0.0, -22, 0, 0), (0.06, -8, 0, 0), (0.7, 0, 0, 0)])
    st.rot("arm_r", [(0.0, 32, 0, 26), (0.06, -86, -16, -6), (0.4, -70, -10, 0), (0.7, 0, 0, 0)])
    st.rot("arm_l", [(0.0, 32, 0, -26), (0.06, -86, 16, 6), (0.4, -70, 10, 0), (0.7, 0, 0, 0)])
    if jaw:
        st.rot("jaw", [(0.0, 22, 0, 0), (0.06, 30, 0, 0), (0.7, 0, 0, 0)])
    # summon (S's dread call): the arms rise slowly over the head, palms up; then it brings them down and calls
    w = r.clip("summon_windup", 1.0)
    w.rot("arm_r", [(0.0, 0, 0, 0), (0.7, -150, 0, 20), (1.0, -160, 0, 24)])
    w.rot("arm_l", [(0.0, 0, 0, 0), (0.7, -150, 0, -20), (1.0, -160, 0, -24)])
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, -10, 0, 0)])
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -24, 0, 0)])
    w.pos("hips", [(0.0, 0, 0, 0), (1.0, 0, 0.6, 0)])
    if jaw:
        w.rot("jaw", [(0.0, 0, 0, 0), (1.0, 10, 0, 0)])
    st = r.clip("summon_strike", 0.9)
    st.rot("arm_r", [(0.0, -160, 0, 24), (0.12, -40, 0, 40), (0.6, -30, 0, 34), (0.9, 0, 0, 0)])
    st.rot("arm_l", [(0.0, -160, 0, -24), (0.12, -40, 0, -40), (0.6, -30, 0, -34), (0.9, 0, 0, 0)])
    st.rot("torso", [(0.0, -10, 0, 0), (0.12, 14, 0, 0), (0.6, 10, 0, 0), (0.9, 0, 0, 0)])
    st.rot("head", [(0.0, -24, 0, 0), (0.12, 4, 0, 0), (0.6, 0, 0, 0)])
    st.pos("hips", [(0.0, 0, 0.6, 0), (0.12, 0, -1.2, 0), (0.9, 0, 0, 0)])
    if jaw:
        st.rot("jaw", [(0.0, 10, 0, 0), (0.12, 34, 0, 0), (0.6, 30, 0, 0), (0.9, 0, 0, 0)])
    # rally (a crowned C also has D's groan): the head goes back, a low call
    w = r.clip("rally_windup", 1.0)
    w.rot("head", [(0.0, 0, 0, 0), (1.0, -26, 0, 0)])
    w.rot("torso", [(0.0, 0, 0, 0), (1.0, -6, 0, 0)])
    st = r.clip("rally_strike", 0.8)
    st.rot("head", [(0.0, -26, 0, 0), (0.15, -30, 6, 0), (0.5, -20, -6, 0), (0.8, 0, 0, 0)])
    st.rot("torso", [(0.0, -6, 0, 0), (0.8, 0, 0, 0)])


# ---- paint ---------------------------------------------------------------------------------------------------------

SKIN = {
    "zombie": ["#18261a", "#24382a", "#335038", "#436846", "#568156", "#6d9a66"],
    "husk": ["#2b2116", "#43341f", "#5f4a2b", "#7d6439", "#9a7e4b", "#b69a62"],
}
CLOTH = {
    "zombie": ["#0b1f22", "#103236", "#174a4e", "#1f6468", "#2a7e81"],
    "husk": ["#251b12", "#38291b", "#4f3b27", "#685036", "#836746"],
}
PANTS = {
    "zombie": ["#121334", "#1a1c4b", "#232764", "#2e347c"],
    "husk": ["#1d150e", "#2d2117", "#403022", "#55412f"],
}
# The vanilla look each form starts from (C), in our own palettes
SKIN_HOME = {
    "zombie": ["#15230f", "#213a18", "#2f5223", "#3e692d", "#4f7b39", "#62904a"],
    "husk": ["#3a3122", "#5a4d34", "#86744d", "#a89263", "#c1a66c", "#dabf85"],
}
CLOTH_HOME = {
    "zombie": ["#03302f", "#065a58", "#008684", "#00a2a1", "#22bcb9"],
    "husk": ["#2e2a20", "#4a4433", "#6a614a", "#857a5d", "#a19573"],
}
PANTS_HOME = {
    "zombie": ["#161344", "#221d5c", "#302873", "#3d3690"],
    "husk": ["#2a2419", "#3e3526", "#544834", "#6b5c43"],
}
BONE = ["#3b3428", "#6a5f4b", "#9f9076", "#cdbf9f", "#ece2c8"]
FAMILY_TINT = {0: None, 1: ("#e6cf9a", 0.16), 2: ("#cfe6f2", 0.22)}
MOSS = ["#24391c", "#36552a", "#4b7337", "#62904a"]
RIME = ["#7f98aa", "#a9c2d2", "#d3e5ef", "#f4fbff"]
SAND = ["#8c7449", "#b39867", "#d6bf8c", "#f0dfb2"]


def tint_ramp(cols, fam):
    t = FAMILY_TINT[fam]
    if t is None:
        return R.Ramp(*cols)
    to = R.hexc(t[0])
    return R.Ramp(*["#%02x%02x%02x" % R.lerpc(R.hexc(h), to, t[1] * (0.4 + 0.6 * k / (len(cols) - 1)))[:3] for k, h in enumerate(cols)])


# How far each rank has come from the creature the player knows (Euan 2026-10-28: "from one level to the next it looks like a
# completely different animal"). C still wears the vanilla creature's own colours, darkened a touch; each rank is a step on; S is the
# full form. Our own palettes, read off the vanilla look; no vanilla pixels are used.
DRIFT = {2: 0.18, 3: 0.42, 4: 0.7, 5: 1.0}


def _resample(cols, n):
    cs = [R.hexc(c) for c in cols]
    out = []
    for k in range(n):
        x = k * (len(cs) - 1) / (n - 1)
        a = int(x)
        b = min(a + 1, len(cs) - 1)
        out.append(R.lerpc(cs[a], cs[b], x - a))
    return out


def toward(home, far, rung, lag=0):
    """A palette between the vanilla creature's colours (home) and the form's (far), as far along as the rank (lag holds a part back
    a rank, so it turns later than the rest)."""
    t = DRIFT.get(rung - lag, 0.0)
    n = max(len(home), len(far))
    return ["#%02x%02x%02x" % R.lerpc(h, f, t)[:3] for h, f in zip(_resample(home, n), _resample(far, n))]


def drift(rung, lag=0):
    return DRIFT.get(rung - lag, 0.0)


def rot_pattern(seed, rung):
    """Decay: broad soft patches of darker and lighter skin (two values, never speckle), a little more broken from B."""
    return R.p_blotch(seed, scale=6, amount=0.2 + 0.35 * drift(rung), levels=2)


def weathering(P, rig, fam, rng, cubes, patches):
    """The land on the creature, in clumps where it would settle: moss (green), sand dust (sun) or rime (frost) on what faces up,
    a few pixels creeping over each top edge below a clump. Never single specks."""
    ramp = MOSS if fam == 0 else SAND if fam == 1 else RIME
    for cname in cubes:
        if cname not in P.cubes or not P.own(cname):
            continue
        top = P.face(cname, "top")
        for _ in range(patches):
            cx, cy = rng.randrange(top.w), rng.randrange(top.h)
            rad = 1.2 + rng.random() * 1.3
            for i, j in top.each():
                d = math.hypot(i - cx, (j - cy) * 1.2)
                if d <= rad:
                    top.set(i, j, R.hexc(ramp[2 if d < rad * 0.5 else 1]))
            # over the edge nearest the clump, down the side a pixel or two
            side, i0 = (("north", cx) if cy < top.h / 2 else ("south", top.w - 1 - cx))
            f = P.face(cname, side)
            for di in (-1, 0, 1):
                for j in range(1 + (di == 0) + (rng.random() < 0.4)):
                    f.set(i0 + di, j, R.hexc(ramp[1 if j else 2]))


def socket_eyes(P, head_cube, rung, cols=((1, 2), (5, 6)), row=4, brow_row=3, skin=None):
    """Eyes deep under the brow: the socket dark, the core one pixel toward the nose, hot; the rest a rim in the rank's colour.
    Recorded for the blink (the closed texture puts the lid back)."""
    rank = R.hexc(RANK[rung])
    face = P.face(head_cube, "north")
    sock = R.hexc("#040504")
    core = R.lerpc(rank, R.hexc("#ffffff"), 0.6)
    with P.eyes():
        for (a, b) in cols:
            for i in range(a, b + 1):
                face.set(i, row, sock)
                if row + 1 < face.h and skin is not None:
                    face.set(i, row + 1, R.mul(skin, 0.6))   # the lower lid, shadowed
        g = P.face(head_cube, "north", glow=True)
        for (a, b) in cols:
            inner = b if a < 4 else a
            outer = a if a < 4 else b
            g.set(inner, row, core[:3] + (255,))
            g.set(outer, row, rank[:3] + (150,))


def paint_zombie(r, rung, species, fam):
    p = Z[rung]
    P = R.Painter(r, f"forms|{r.id}|{species}|{fam}")
    import random
    rng = random.Random(R._h(r.id, species, fam))
    skin = tint_ramp(toward(SKIN_HOME[species], SKIN[species], rung), fam)
    cloth = tint_ramp(toward(CLOTH_HOME[species], CLOTH[species], rung), fam)
    pants = tint_ramp(toward(PANTS_HOME[species], PANTS[species], rung), fam)
    bone = tint_ramp(BONE, fam) if fam != 2 else R.Ramp("#3e4752", "#6f7c88", "#a3b1bc", "#d1dde5", "#eef5f9")
    darker = 0.75 * drift(rung)
    rot = rot_pattern(rung * 7 + 3, rung)
    for cu in r.cubes:
        n = cu.name
        if n.startswith("leg_"):
            P.paint(cu, pants, 1.9, R.combine(R.p_strokes(cell=3, length=4, dark=0.5, light=0.3, seed=2, tops=False), rot), noise=0.4)
        elif n == "torso":
            # the shirt, torn: skin shows through where it rotted away (more from B)
            def shirt(face, i, j, n=n):
                # a tear or two down the side and the belly, skin showing in it
                hv = R._h(n, face.name, i, j // 4, rung) % 100
                if face.name in ("west", "east") and hv < 6 + 4 * (rung - 2) and j > 2:
                    return tuple(skin.at(2.0))
                return 0.0
            P.paint(cu, cloth, 2.1 - darker * 0.3, R.combine(shirt, rot), noise=0.5)
        elif n.startswith("skirt"):
            def ragged(face, i, j):
                # a torn hem: strips of different lengths, gaps between them
                strip = R._h("hem", face.cube.name, i // 2) % 4
                if j >= face.h - strip // 2 or (i % 5 == 4 and j > 0):
                    return None
                return -0.3 * (j / max(1, face.h))
            P.paint(cu, cloth, 1.7, ragged, rim=0.0, ao=0.0)
        elif n == "ribs":
            def ribs(face, i, j, w=cu.w):
                if face.name != "north":
                    return tuple(R.hexc("#1a110e"))
                if i == w // 2 or i == (w - 1) // 2:
                    return 0.4 - 0.15 * j          # the breastbone
                if j % 2 == 0 and 0 < i < w - 1 or j % 2 == 0 and j < 4:
                    return 0.2 - 0.25 * abs(i - w / 2.0) / (w / 2.0) - 0.15 * j
                return tuple(R.hexc("#1a110e") if (i + j) % 3 else R.hexc("#2b1813"))
            P.paint(cu, bone, 2.4, ribs, rim=0.3, ao=0.0)
        elif n in ("cuff",) or n.startswith("chain"):
            P.paint(cu, R.Ramp("#1d1a1a", "#34302e", "#4f4843", "#6e645b", "#8d8173"), 2.0,
                    lambda face, i, j: -0.8 if (i + j * 2) % 5 == 0 else 0.0, rim=0.9, ao=0.6)
        elif n == "cheek":
            P.paint(cu, skin, 2.0, None)
            f = P.face(cu, "north")
            for i in range(f.w):
                f.set(i, 0, bone.at(3.2) if i % 2 == 0 else R.hexc("#140c0a"))
        elif n.startswith(("tooth", "claw", "spur", "ulna")) or n.startswith("spine"):
            P.paint(cu, bone, 2.6 if n.startswith(("tooth", "claw")) else 2.2, None, grad=1.2, rim=0.8, ao=0.5)
        elif n.startswith(("xtal", "crown")):
            P.paint(cu, ramp_to(RANK[rung]), 2.2, None, grad=1.6, rim=1.2, ao=1.0)
        elif n in ("cape", "cape2", "collar"):
            low = n == "cape2"

            def tatter(face, i, j, low=low, collar=n == "collar"):
                if collar:  # a rolled band of the same cloth, folds every other pixel
                    return -0.7 if (i + j) % 3 == 0 else 0.3 if j == 0 else 0.0
                if face.name in ("south", "north"):
                    cut = 1 + R._h("cape", i) % 4 if low else 0
                    if j >= face.h - cut or (low and i % 4 == 3 and j > face.h // 2 - 1):
                        return None
                    # long folds down the cloth, lighter on their crests
                    return (0.7 if i % 3 == 1 else -0.6 if i % 3 == 2 else 0.0) - 0.04 * j
                if face.name == "bottom" and low:
                    return None
                return 0.0
            P.paint(cu, R.Ramp("#120d10", "#24181d", "#3a262e", "#523641", "#6b4855"), 2.2, tatter, rim=0.6, ao=0.4)
        elif n == "hump":
            P.paint(cu, skin, 2.4 - darker, R.combine(R.p_strokes(cell=2, length=3, dark=0.6, seed=7), rot), noise=0.6)
        else:  # head, jaw, brow, arms, forearms: skin
            P.paint(cu, skin, 2.9 - darker, rot, noise=0.6)
    # the face: the brow darker than the forehead; a mouth (C) or the gap of the open jaw (B+)
    head = "head"
    fn = P.face(head, "north")
    if not p["jaw"]:
        for i in range(2, 6):
            fn.set(i, 6, R.hexc("#120c0a"))
        fn.set(1, 6, skin.at(1.0))
        fn.set(6, 6, skin.at(1.0))
    fb = P.face("brow", "bottom")
    for i, j in fb.each():
        fb.set(i, j, skin.at(0.5))
    socket_eyes(P, head, rung, skin=skin.at(2.0))
    weathering(P, r, fam, rng, ["head", "torso", "arm_r", "arm_l", "hump"], 1 + (fam > 0))
    # the light inside: from B a thin seam through the ribs; at S the heart behind them
    rank = R.hexc(RANK[rung])
    if rung >= 3:
        g = P.face("ribs", "north", glow=True)
        for j in range(0, 6, 2):
            g.set(g.w // 2, j, rank[:3] + (110 + 20 * (rung - 3),))
    if rung >= 5:
        g = P.face("ribs", "north", glow=True)
        for (i, j) in ((g.w // 2 - 1, 3), (g.w // 2, 3), (g.w // 2, 2), (g.w // 2 - 1, 2)):
            g.set(i, j, R.lerpc(rank, R.hexc("#ffffff"), 0.5)[:3] + (230,))
    # crystal: a light inside, brighter toward the tip, a lit edge
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
    return P.skin, P.glow, P.blink()


# =====================================================================================================================
# The registry: bodies, their species and painters

BODIES = {
    "zombie": (zombie, paint_zombie, ("zombie", "husk")),
}



def bodies():
    """The registry, filled on first use (the other bodies' modules import this one for its helpers)."""
    if len(BODIES) == 1:
        import _forms_hostiles as H
        import _forms_animals as A
        BODIES.update({
            "skeleton": (H.skeleton, H.paint_skeleton, ("skeleton", "stray")),
            "spider": (H.spider, H.paint_spider, ("spider", "cave_spider")),
            "creeper": (H.creeper, H.paint_creeper, ("creeper",)),
            "pig": (A.pig, A.paint_pig, ("pig",)),
            "cow": (A.cow, A.paint_cow, ("cow", "mooshroom")),
            "sheep": (A.sheep, A.paint_sheep, ("sheep",)),
            "chicken": (A.chicken, A.paint_chicken, ("chicken",)),
        })
    return BODIES


def rigs(only=None):
    out = []
    for body, (design, _, _) in bodies().items():
        if only and body not in only:
            continue
        for rung in range(2, 6):
            out.append((body, rung, design(rung)))
    return out


# ---- checks: Euan's placement rules at the key poses ------------------------------------------------------------------

KEY_POSES = [("rest", []), ("idle", [("idle", 1.0, 1.0)]), ("walk", [("idle", 0.6, 0.45), ("walk", 0.3, 1.0)]),
             ("angry", [("idle", 1.0, 1.0), ("angry", 1.0, 1.0)]), ("bite", [("angry", 1.0, 1.0), ("bite", 0.1, 1.0)]),
             ("hurt", [("hurt", 0.07, 1.0)]), ("roar", [("roar", 0.8, 1.0)])]


def world_boxes(rig, pose):
    """Each cube's eight corners in model space for a pose (the renderer's transforms)."""
    tree = rig.tree(pose)
    out = {}

    def walk(n, M, T):
        T2 = tuple(T[i] + R._mv(M, n["pivot"])[i] for i in range(3))
        M2 = R._mm(M, R._rot(*n["rot"]))
        for b in n["boxes"]:
            g = b["grow"]
            pts = []
            for dx in (0, 1):
                for dy in (0, 1):
                    for dz in (0, 1):
                        q = R._mv(M2, (b["x"] - g + dx * (b["w"] + 2 * g), b["y"] - g + dy * (b["h"] + 2 * g), b["z"] - g + dz * (b["d"] + 2 * g)))
                        pts.append(tuple(q[i] + T2[i] for i in range(3)))
            out[b["name"]] = pts
        for ch in n["children"]:
            walk(ch, M2, T2)
    walk(tree, ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return out


def _gap(a, b):
    """Distance between two point clouds' bounding boxes (0 when they overlap)."""
    d = 0.0
    for k in range(3):
        lo1, hi1 = min(p[k] for p in a), max(p[k] for p in a)
        lo2, hi2 = min(p[k] for p in b), max(p[k] for p in b)
        g = max(0.0, lo2 - hi1, lo1 - hi2)
        d += g * g
    return math.sqrt(d)


def check_rig(rig):
    """Attached (every cube touches a cube of its own bone or an ancestor's, at every key pose) and off the face (nothing in front of
    the eyes); the ground (the feet reach it at rest). Returns problems."""
    problems = list(rig.check())
    parent_cubes = {}

    def held(node):  # a bone's cubes and its gear bones' (rigid, at its pivot: the same body part)
        return [c.name for c in node.cubes] + [c.name for g in rig.bones if g.parent is node and g.name.startswith("gear") for c in g.cubes]
    for b in rig.bones:
        anc, node = [], b.parent
        while node is not None:
            anc += held(node)
            node = node.parent
        for c in b.cubes:
            parent_cubes[c.name] = [x.name for x in b.cubes if x is not c] + anc
    for label, layers in KEY_POSES:
        boxes = world_boxes(rig, rig.sample(layers) if layers else {})
        for name, others in parent_cubes.items():
            if not others:
                continue
            g = min(_gap(boxes[name], boxes[o]) for o in others)
            if g > 0.75:
                problems.append(f"{rig.id} {label}: {name} floats {g:.1f}px from what holds it")
    rest = world_boxes(rig, {})
    low = max(p[1] for pts in rest.values() for p in pts)
    if abs(low - 24) > 0.6:
        problems.append(f"{rig.id}: the lowest point is at y={low:.1f}, not on the ground (24)")
    return problems


# ---- build -----------------------------------------------------------------------------------------------------------

def build(out):
    all_rigs = rigs()
    problems = []
    for body, rung, rig in all_rigs:
        problems += check_rig(rig)
    if problems:
        raise SystemExit("form bodies:\n  " + "\n  ".join(problems))
    n = 0
    for body, rung, rig in all_rigs:
        _, painter, species = BODIES[body]
        for sp in species:
            for fam in range(3):
                skin, glow, blink = painter(rig, rung, sp, fam)
                base = f"forms/body/{rig.id}_{sp}"
                out.entity(f"{base}_{fam}", skin)
                import _forms_animals
                for coat, canvas in _forms_animals.COATS.pop((rig.id, sp, fam), {}).items():   # the wool, the saddle (FormRenderer's coat layer)
                    out.entity(f"{base}_{fam}_{coat}", canvas)
                if blink is not None:
                    out.entity(f"{base}_{fam}_blink", blink[0])
                if fam == 0:
                    out.entity(f"{base}_glow", glow)
                    if blink is not None:
                        out.entity(f"{base}_glow_blink", blink[1])
                n += 1
    out.entity("forms/halo", halo())
    java([rig for _, _, rig in all_rigs])


def halo():
    """The eye halo: a soft white disc fading to nothing at its edge (tinted in the game by the rank's colour)."""
    from pixelart import Canvas
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            a = max(0.0, 1.0 - d) ** 2.2
            if a > 0.01:
                cv.set(x, y, (255, 255, 255, int(255 * a)))
    return cv


def java(rigs_):
    src = ["package dev.awaken.living.forms;", "", "import java.util.List;", "",
           "/**",
           " * GENERATED by tools/art/specs/forms_bodies.py: do not edit by hand. The form bodies' rigs (bones, cubes, look bones and",
           " * clips) as {@link dev.awaken.mobs.client.anim.HeroRig} source text, one per body and rung.",
           " */",
           "public final class FormData {",
           "    private FormData() {}", "",
           "    public static final List<String[]> SOURCES = List.<String[]>of("]
    for k, rig in enumerate(rigs_):
        lines = rig.java_lines()
        parts, block, size = [], [], 0
        for ln in lines:
            if size + len(ln) > 24000 and block:
                parts.append(block)
                block, size = [], 0
            block.append(ln)
            size += len(ln) + 1
        if block:
            parts.append(block)
        src.append(f"            // {rig.id}")
        src.append("            new String[] {")
        for pi, blk in enumerate(parts):
            src.append('                """')
            for ln in blk:
                src.append("                " + ln)
            src.append('                """' + ("," if pi < len(parts) - 1 else ""))
        src.append("            }" + ("," if k < len(rigs_) - 1 else ""))
    src.append("    );")
    src.append("}")
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")


# ---- preview ---------------------------------------------------------------------------------------------------------

BG_DAY = (104, 124, 100, 255)
BG_NIGHT = (8, 10, 22, 255)


def preview(outdir, only=None, species_pick=None):
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    for body, rung, rig in rigs(only):
        _, painter, species = BODIES[body]
        for sp in (species if species_pick is None else [x for x in species if x in species_pick]):
            skin, glow, _ = painter(rig, rung, sp, 0)
            sk, gl = skin.image(), glow.image()
            lit = Image.alpha_composite(sk, gl)
            px = 6.0
            cells = []
            for yaw, pitch in ((28, 10), (-35, 10), (90, 6), (200, 12)):
                cells.append((R.render(rig.tree(), lit, yaw=yaw, pitch=pitch, px=px), BG_DAY))
            cells.append((R.render(rig.tree(), sk, yaw=24, pitch=8, px=px, night=gl), BG_NIGHT))
            for label, layers in KEY_POSES[2:] + [("walk2", [("idle", 0.6, 0.45), ("walk", 0.9, 1.0)])]:
                cells.append((R.render(rig.tree(rig.sample(layers)), lit, yaw=60, pitch=8, px=px), BG_DAY))
            W = sum(im.width for im, _ in cells) + 8 * len(cells)
            H = max(im.height for im, _ in cells) + 24
            sheet = Image.new("RGBA", (W, H), (40, 40, 44, 255))
            x = 0
            d = ImageDraw.Draw(sheet)
            for im, bg in cells:
                tile = Image.new("RGBA", (im.width + 8, H - 20), bg)
                tile.alpha_composite(im, (4, H - 24 - im.height))
                sheet.alpha_composite(tile, (x, 20))
                x += im.width + 8
            d.text((6, 4), f"{rig.id} {sp} (rank {NAME[rung]})", fill=(235, 235, 235, 255))
            sheet.save(os.path.join(outdir, f"{rig.id}_{sp}.png"))
            print("wrote", rig.id, sp)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "preview":
        preview(sys.argv[2], sys.argv[3:] or None)
    else:
        from pixelart import Out
        build(Out(os.path.join(ROOT, "src/main/resources")))
